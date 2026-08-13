package com.devmod.zone.data;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.DataResult;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Registry for storing and managing zone definitions.
 * Persisted as SavedData in the overworld.
 * Replaces hardcoded zones from NexusZoneLayout, NexusSpawnManager.
 */
public class ZoneRegistry extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneRegistry.class);
    private static final String DATA_NAME = "devmod_zone_registry";
    private static final int DATA_VERSION = 1;

    private static final String TAG_VERSION = "version";
    private static final String TAG_ZONES = "zones";
    private static final String TAG_ALIASES = "aliases";
    private static final String TAG_MARKERS = "markers";
    private static final String TAG_INITIALIZED = "initialized";
    private static final String TAG_ZONE_TO_AREAS = "zoneToAreas";

    /** Zones by UUID */
    private final Map<UUID, ZoneDefinition> zones = new ConcurrentHashMap<>();

    /** Zone ID (string) to UUID mapping for fast lookup */
    private final Map<String, UUID> zoneIdToUuid = new ConcurrentHashMap<>();

    /** Aliases mapping (alias -> zoneId) */
    private final Map<String, String> aliases = new ConcurrentHashMap<>();

    /** Marker positions to zone UUID mapping */
    private final Map<BlockPos, UUID> markerPositions = new ConcurrentHashMap<>();

    /** Zone ID (string) to linked Area UUIDs mapping - bidirectional sync with AreaRegistry */
    private final Map<String, Set<UUID>> zoneToAreas = new ConcurrentHashMap<>();

    /** Whether legacy zones have been initialized */
    private boolean initialized = false;

    /**
     * Version counter incremented on any modification.
     * Used by ZoneMarkerBlockEntity to detect stale caches.
     */
    private final AtomicLong modificationVersion = new AtomicLong();

    // ========================================================================
    // Static Access
    // ========================================================================

    /**
     * Gets the ZoneRegistry instance for the server.
     * Data is stored in the overworld.
     */
    @Nonnull
    public static ZoneRegistry get(@Nonnull MinecraftServer server) {
        return Objects.requireNonNull(Objects.requireNonNull(server).overworld().getDataStorage()
            .computeIfAbsent(Objects.requireNonNull(factory()), DATA_NAME));
    }

    private static Factory<ZoneRegistry> factory() {
        return new Factory<>(
            ZoneRegistry::new,
            ZoneRegistry::load
        );
    }

    /**
     * Gets the current modification version.
     * Markers can compare this with their cached version to detect staleness.
     */
    public long getModificationVersion() {
        return modificationVersion.get();
    }

    // ========================================================================
    // CRUD Operations
    // ========================================================================

    /**
     * Registers a new zone and returns its UUID.
     * Synchronized to prevent duplicate zone ID registration via concurrent calls
     * (consistent with {@link #updateZone(UUID, ZoneDefinition)}).
     */
    @Nonnull
    public synchronized UUID registerZone(@Nonnull ZoneDefinition zone) {
        Objects.requireNonNull(zone, "zone");

        // Check for duplicate zoneId
        if (zoneIdToUuid.containsKey(zone.zoneId())) {
            throw new IllegalArgumentException("Zone ID already exists: " + zone.zoneId());
        }

        zones.put(zone.id(), zone);
        zoneIdToUuid.put(zone.zoneId(), zone.id());
        modificationVersion.incrementAndGet();
        setDirty();

        LOGGER.debug("[Zone] Registered zone: {} ({})", zone.zoneId(), zone.id());
        return zone.id();
    }

    /**
     * Creates a new zone (alias of registerZone for API symmetry).
     */
    @Nonnull
    public UUID createZone(@Nonnull ZoneDefinition zone) {
        return registerZone(zone);
    }

    /**
     * Updates an existing zone.
     *
     * @param id   The zone UUID
     * @param zone The new zone definition
     * @return True if updated, false if not found
     */
    public synchronized boolean updateZone(@Nonnull UUID id, @Nonnull ZoneDefinition zone) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(zone, "zone");

        ZoneDefinition existing = zones.get(id);
        if (existing == null) {
            LOGGER.warn("[Zone] Update failed: zone {} not found", id);
            return false;
        }

        // If zoneId changed, update the mapping atomically
        if (!existing.zoneId().equals(zone.zoneId())) {
            // Check new ID doesn't conflict
            if (zoneIdToUuid.containsKey(zone.zoneId())) {
                LOGGER.warn("[Zone] Update failed: new zoneId {} already exists", zone.zoneId());
                return false;
            }
            zoneIdToUuid.remove(existing.zoneId());
            zoneIdToUuid.put(zone.zoneId(), id);
            remapZoneIdReferences(existing.zoneId(), zone.zoneId());
        }

        zones.put(id, zone);
        modificationVersion.incrementAndGet();
        setDirty();

        LOGGER.debug("[Zone] Updated zone: {} ({})", zone.zoneId(), id);
        return true;
    }

    /**
     * Repoints the indexes that store the string zoneId after a rename.
     *
     * <p>{@code aliases} holds zoneIds as values and {@code zoneToAreas} holds them as keys;
     * both are stored lowercased, so they are matched and rewritten in that form.
     */
    private void remapZoneIdReferences(@Nonnull String oldZoneId, @Nonnull String newZoneId) {
        String oldNormalized = oldZoneId.toLowerCase(Locale.ROOT);
        String newNormalized = newZoneId.toLowerCase(Locale.ROOT);
        if (oldNormalized.equals(newNormalized)) {
            return;
        }

        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (oldNormalized.equals(entry.getValue())) {
                entry.setValue(newNormalized);
            }
        }

        Set<UUID> linkedAreas = zoneToAreas.remove(oldNormalized);
        if (linkedAreas != null) {
            zoneToAreas.put(newNormalized, linkedAreas);
        }
    }

    /**
     * Unregisters a zone by UUID.
     * Synchronized to ensure consistent removal across multiple indexes
     * (consistent with {@link #registerZone(ZoneDefinition)} and {@link #updateZone(UUID, ZoneDefinition)}).
     *
     * @param id The zone UUID
     * @return True if removed, false if not found
     */
    public synchronized boolean unregisterZone(@Nonnull UUID id) {
        Objects.requireNonNull(id, "id");

        ZoneDefinition removed = zones.remove(id);
        if (removed != null) {
            zoneIdToUuid.remove(removed.zoneId());
            // Remove any markers pointing to this zone
            markerPositions.values().removeIf(zoneId -> zoneId.equals(id));
            // Remove any aliases pointing to this zone
            aliases.values().removeIf(targetId -> targetId.equals(removed.zoneId()));
            modificationVersion.incrementAndGet();
            setDirty();

            LOGGER.debug("[Zone] Unregistered zone: {} ({})", removed.zoneId(), id);
            return true;
        }
        return false;
    }

    /**
     * Deletes a zone by UUID (alias of unregisterZone for API symmetry).
     */
    public boolean deleteZone(@Nonnull UUID id) {
        return unregisterZone(id);
    }

    // ========================================================================
    // Query Operations
    // ========================================================================

    /**
     * Gets a zone by UUID.
     */
    @Nonnull
    public Optional<ZoneDefinition> getZone(@Nonnull UUID id) {
        return Objects.requireNonNull(Optional.ofNullable(zones.get(Objects.requireNonNull(id))));
    }

    /**
     * Gets a zone by its string ID (e.g., "combat", "arena").
     */
    @Nonnull
    public Optional<ZoneDefinition> getZoneById(@Nonnull String zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        UUID uuid = zoneIdToUuid.get(zoneId.toLowerCase(Locale.ROOT));
        return Objects.requireNonNull(uuid != null ? getZone(uuid) : Optional.empty());
    }

    /**
     * Legacy alias for getZoneById (string ID lookup).
     */
    @Nonnull
    public Optional<ZoneDefinition> getZoneByStringId(@Nonnull String zoneId) {
        return getZoneById(zoneId);
    }

    /**
     * Gets all zones as an unmodifiable collection.
     */
    @Nonnull
    public Collection<ZoneDefinition> getAllZones() {
        return Objects.requireNonNull(Collections.unmodifiableCollection(zones.values()));
    }

    /**
     * Gets all teleportable zones (sorted by zoneId).
     */
    @Nonnull
    public List<ZoneDefinition> getTeleportableZones() {
        return Objects.requireNonNull(zones.values().stream()
            .filter(ZoneDefinition::hasTeleport)
            .sorted(Comparator.comparing(ZoneDefinition::zoneId))
            .collect(Collectors.toList()));
    }

    /**
     * Gets the number of registered zones.
     */
    public int getZoneCount() {
        return zones.size();
    }

    /**
     * Checks if a zone with the given ID exists.
     */
    public boolean hasZoneId(@Nonnull String zoneId) {
        return zoneIdToUuid.containsKey(zoneId.toLowerCase(Locale.ROOT));
    }

    // ========================================================================
    // Zone Resolution
    // ========================================================================

    /**
     * Resolves which zone contains a position.
     * Zones are checked in priority order (higher priority first).
     *
     * @param pos    The position to check
     * @param origin The hub origin (for relative bounds calculation if needed)
     * @return The zone containing the position, or empty if outside all zones
     */
    @Nonnull
    public Optional<ZoneDefinition> resolveZone(@Nonnull BlockPos pos, @Nonnull BlockPos origin) {
        Objects.requireNonNull(pos, "pos");

        // Sort by priority (descending) and check bounds
        return Objects.requireNonNull(zones.values().stream()
            .sorted(Comparator.comparingInt(ZoneDefinition::priority).reversed())
            .filter(zone -> zone.containsPosition(pos))
            .findFirst());
    }

    /**
     * Resolves a zone by alias (e.g., "home" -> "hub").
     *
     * @param alias The alias to resolve
     * @return The zone the alias points to, or empty if not found
     */
    @Nonnull
    public Optional<ZoneDefinition> resolveByAlias(@Nonnull String alias) {
        Objects.requireNonNull(alias, "alias");
        String normalized = alias.toLowerCase(Locale.ROOT);

        // First check aliases
        String targetZoneId = aliases.get(normalized);
        if (targetZoneId != null) {
            return getZoneById(targetZoneId);
        }

        // Then check if it's a direct zoneId
        return getZoneById(Objects.requireNonNull(normalized));
    }

    /**
     * Resolves an alias to its target zone ID (string), or null if not found.
     */
    @Nullable
    public String resolveAlias(@Nonnull String alias) {
        Objects.requireNonNull(alias, "alias");
        return aliases.get(alias.toLowerCase(Locale.ROOT));
    }

    // ========================================================================
    // Alias Management
    // ========================================================================

    /**
     * Registers an alias for a zone.
     *
     * @param alias  The alias (e.g., "home")
     * @param zoneId The target zone ID (e.g., "hub")
     */
    public void registerAlias(@Nonnull String alias, @Nonnull String zoneId) {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(zoneId, "zoneId");

        String normalizedAlias = alias.toLowerCase(Locale.ROOT);
        String normalizedTarget = zoneId.toLowerCase(Locale.ROOT);

        aliases.put(normalizedAlias, normalizedTarget);
        modificationVersion.incrementAndGet();
        setDirty();

        LOGGER.debug("[Zone] Registered alias: {} -> {}", normalizedAlias, normalizedTarget);
    }

    /**
     * Unregisters an alias.
     *
     * @param alias The alias to remove
     * @return True if removed, false if not found
     */
    public boolean unregisterAlias(@Nonnull String alias) {
        Objects.requireNonNull(alias, "alias");

        String removed = aliases.remove(alias.toLowerCase(Locale.ROOT));
        if (removed != null) {
            modificationVersion.incrementAndGet();
            setDirty();
            LOGGER.debug("[Zone] Unregistered alias: {}", alias);
            return true;
        }
        return false;
    }

    /**
     * Gets all registered aliases.
     */
    @Nonnull
    public Map<String, String> getAllAliases() {
        return Objects.requireNonNull(Collections.unmodifiableMap(aliases));
    }

    // ========================================================================
    // Marker Management
    // ========================================================================

    /**
     * Registers a marker position for a zone.
     */
    public void registerMarker(@Nonnull BlockPos pos, @Nonnull UUID zoneId) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(zoneId, "zoneId");

        markerPositions.put(pos, zoneId);
        modificationVersion.incrementAndGet();
        setDirty();

        LOGGER.debug("[Zone] Registered marker at {} for zone {}", pos, zoneId);
    }

    /**
     * Unregisters a marker position.
     */
    public void unregisterMarker(@Nonnull BlockPos pos) {
        Objects.requireNonNull(pos, "pos");

        UUID removed = markerPositions.remove(pos);
        if (removed != null) {
            modificationVersion.incrementAndGet();
            setDirty();
            LOGGER.debug("[Zone] Unregistered marker at {}", pos);
        }
    }

    /**
     * Gets the zone UUID for a marker position.
     */
    @Nonnull
    public Optional<UUID> getMarkerZone(@Nonnull BlockPos pos) {
        return Objects.requireNonNull(Optional.ofNullable(markerPositions.get(Objects.requireNonNull(pos))));
    }

    /**
     * Gets all marker positions linked to a specific zone UUID.
     */
    @Nonnull
    public Set<BlockPos> getMarkersForZone(@Nonnull UUID zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        Set<BlockPos> result = new HashSet<>();
        for (Map.Entry<BlockPos, UUID> entry : markerPositions.entrySet()) {
            if (zoneId.equals(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return Objects.requireNonNull(Collections.unmodifiableSet(result));
    }

    // ========================================================================
    // Area Linking (Bidirectional Sync with AreaRegistry)
    // ========================================================================

    /**
     * Links an area to a zone.
     * Called when an AreaDefinition with linkedZoneId is saved in AreaRegistry.
     *
     * @param areaId The area UUID
     * @param zoneId The zone string ID
     */
    public void linkAreaToZone(@Nonnull UUID areaId, @Nonnull String zoneId) {
        Objects.requireNonNull(areaId, "areaId");
        Objects.requireNonNull(zoneId, "zoneId");

        String normalizedZoneId = zoneId.toLowerCase(Locale.ROOT);
        zoneToAreas.computeIfAbsent(normalizedZoneId, k -> ConcurrentHashMap.newKeySet()).add(areaId);
        modificationVersion.incrementAndGet();
        setDirty();

        LOGGER.debug("[Zone] Linked area {} to zone {}", areaId, normalizedZoneId);
    }

    /**
     * Unlinks an area from a zone.
     * Called when an Area is deleted or its linkedZoneId changes.
     *
     * @param areaId The area UUID
     * @param zoneId The zone string ID
     */
    public void unlinkAreaFromZone(@Nonnull UUID areaId, @Nonnull String zoneId) {
        Objects.requireNonNull(areaId, "areaId");
        Objects.requireNonNull(zoneId, "zoneId");

        String normalizedZoneId = zoneId.toLowerCase(Locale.ROOT);
        Set<UUID> areas = zoneToAreas.get(normalizedZoneId);
        if (areas != null) {
            areas.remove(areaId);
            if (areas.isEmpty()) {
                zoneToAreas.remove(normalizedZoneId);
            }
            modificationVersion.incrementAndGet();
            setDirty();

            LOGGER.debug("[Zone] Unlinked area {} from zone {}", areaId, normalizedZoneId);
        }
    }

    /**
     * Gets all area UUIDs linked to a zone.
     *
     * @param zoneId The zone string ID
     * @return Unmodifiable set of area UUIDs, empty if none
     */
    @Nonnull
    public Set<UUID> getAreasForZone(@Nonnull String zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");

        String normalizedZoneId = zoneId.toLowerCase(Locale.ROOT);
        Set<UUID> areas = zoneToAreas.get(normalizedZoneId);
        return Objects.requireNonNull(areas != null ? Set.copyOf(areas) : Set.of());
    }

    /**
     * Checks if a zone has any linked areas.
     *
     * @param zoneId The zone string ID
     * @return True if the zone has at least one linked area
     */
    public boolean hasLinkedAreas(@Nonnull String zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");

        String normalizedZoneId = zoneId.toLowerCase(Locale.ROOT);
        Set<UUID> areas = zoneToAreas.get(normalizedZoneId);
        return areas != null && !areas.isEmpty();
    }

    // ========================================================================
    // Legacy Migration
    // ========================================================================

    /**
     * Checks if the registry has been initialized with legacy zones.
     */
    public boolean isInitialized() {
        return initialized;
    }
    /**
     * Initializes the registry with legacy zones from ZonePresets.
     * Only runs once; subsequent calls are ignored.
     *
     * @param origin The hub origin position
     */
    public void initializeWithLegacyZones(@Nonnull BlockPos origin) {
        if (initialized) {
            LOGGER.debug("[Zone] Already initialized, skipping legacy zone creation");
            return;
        }

        LOGGER.info("[Zone] Initializing with legacy zones...");

        // Register legacy zones
        List<ZoneDefinition> legacyZones = ZonePresets.createLegacyZones(origin);
        for (ZoneDefinition zone : legacyZones) {
            try {
                registerZone(Objects.requireNonNull(zone));
            } catch (Exception e) {
                LOGGER.error("[Zone] Failed to register legacy zone: {}", zone.zoneId(), e);
            }
        }

        // Register default aliases
        Map<String, String> defaultAliases = ZonePresets.getDefaultAliases();
        for (Map.Entry<String, String> entry : defaultAliases.entrySet()) {
            registerAlias(Objects.requireNonNull(entry.getKey()), Objects.requireNonNull(entry.getValue()));
        }

        markInitialized();

        LOGGER.info("[Zone] Initialized {} legacy zones and {} aliases",
            legacyZones.size(), defaultAliases.size());
    }

    /**
     * Marks the registry as initialized.
     * Used when zones are created programmatically (e.g., from slot sync)
     * rather than from legacy presets.
     */
    public void markInitialized() {
        if (!initialized) {
            initialized = true;
            modificationVersion.incrementAndGet();
            setDirty();
            LOGGER.debug("[Zone] Registry marked as initialized");
        }
    }

    // ========================================================================
    // Persistence
    // ========================================================================

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, DATA_VERSION);
        tag.putBoolean(TAG_INITIALIZED, initialized);

        // Save zones
        ListTag zoneList = new ListTag();
        for (ZoneDefinition zone : zones.values()) {
            DataResult<Tag> result = ZoneDefinition.CODEC.encodeStart(NbtOps.INSTANCE, zone);
            result.resultOrPartial(err -> LOGGER.error("[Zone] Failed to encode zone {}: {}", zone.zoneId(), err))
                .ifPresent(zoneList::add);
        }
        tag.put(TAG_ZONES, zoneList);

        // Save aliases
        CompoundTag aliasTag = new CompoundTag();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            aliasTag.putString(Objects.requireNonNull(entry.getKey()), Objects.requireNonNull(entry.getValue()));
        }
        tag.put(TAG_ALIASES, aliasTag);

        // Save markers
        ListTag markerList = new ListTag();
        for (Map.Entry<BlockPos, UUID> entry : markerPositions.entrySet()) {
            CompoundTag markerTag = new CompoundTag();
            markerTag.putLong("pos", entry.getKey().asLong());
            markerTag.putUUID("zone", Objects.requireNonNull(entry.getValue()));
            markerList.add(markerTag);
        }
        tag.put(TAG_MARKERS, markerList);

        // Save zoneToAreas mapping
        ListTag zoneToAreasTag = new ListTag();
        for (Map.Entry<String, Set<UUID>> entry : zoneToAreas.entrySet()) {
            CompoundTag mappingTag = new CompoundTag();
            mappingTag.putString("zoneId", Objects.requireNonNull(entry.getKey()));
            ListTag areaIdsTag = new ListTag();
            for (UUID areaId : entry.getValue()) {
                CompoundTag areaIdTag = new CompoundTag();
                areaIdTag.putUUID("id", Objects.requireNonNull(areaId));
                areaIdsTag.add(areaIdTag);
            }
            mappingTag.put("areaIds", areaIdsTag);
            zoneToAreasTag.add(mappingTag);
        }
        tag.put(TAG_ZONE_TO_AREAS, zoneToAreasTag);

        LOGGER.debug("[Zone] Saved {} zones, {} aliases, {} markers, {} zone-area links",
            zones.size(), aliases.size(), markerPositions.size(), zoneToAreas.size());

        return tag;
    }

    @Nonnull
    private static ZoneRegistry load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        ZoneRegistry registry = new ZoneRegistry();

        int version = tag.getInt(TAG_VERSION);
        if (version != DATA_VERSION) {
            LOGGER.warn("[Zone] Data version mismatch: expected {}, got {}. Migration may be needed.",
                DATA_VERSION, version);
        }

        registry.initialized = tag.getBoolean(TAG_INITIALIZED);

        // Load zones
        ListTag zoneList = tag.getList(TAG_ZONES, Tag.TAG_COMPOUND);
        for (Tag zoneTag : zoneList) {
            DataResult<ZoneDefinition> result = ZoneDefinition.CODEC.parse(NbtOps.INSTANCE, zoneTag);
            result.resultOrPartial(err -> LOGGER.error("[Zone] Failed to decode zone: {}", err))
                .ifPresent(zone -> {
                    registry.zones.put(zone.id(), zone);
                    registry.zoneIdToUuid.put(zone.zoneId(), zone.id());
                });
        }

        // Load aliases
        if (tag.contains(TAG_ALIASES, Tag.TAG_COMPOUND)) {
            CompoundTag aliasTag = tag.getCompound(TAG_ALIASES);
            for (String key : aliasTag.getAllKeys()) {
                registry.aliases.put(Objects.requireNonNull(key), Objects.requireNonNull(aliasTag.getString(key)));
            }
        }

        // Load markers
        if (tag.contains(TAG_MARKERS, Tag.TAG_LIST)) {
            ListTag markerList = tag.getList(TAG_MARKERS, Tag.TAG_COMPOUND);
            for (Tag markerTag : markerList) {
                CompoundTag markerCompound = (CompoundTag) markerTag;
                BlockPos pos = BlockPos.of(markerCompound.getLong("pos"));
                UUID zoneId = markerCompound.getUUID("zone");
                registry.markerPositions.put(pos, zoneId);
            }
        }

        // Load zoneToAreas mapping
        if (tag.contains(TAG_ZONE_TO_AREAS, Tag.TAG_LIST)) {
            ListTag zoneToAreasTag = tag.getList(TAG_ZONE_TO_AREAS, Tag.TAG_COMPOUND);
            for (int i = 0; i < zoneToAreasTag.size(); i++) {
                CompoundTag mappingTag = zoneToAreasTag.getCompound(i);
                String zoneId = mappingTag.getString("zoneId");
                ListTag areaIdsTag = mappingTag.getList("areaIds", Tag.TAG_COMPOUND);
                Set<UUID> areas = ConcurrentHashMap.newKeySet();
                for (int j = 0; j < areaIdsTag.size(); j++) {
                    CompoundTag areaIdTag = areaIdsTag.getCompound(j);
                    areas.add(areaIdTag.getUUID("id"));
                }
                if (!areas.isEmpty()) {
                    registry.zoneToAreas.put(zoneId, areas);
                }
            }
        }

        LOGGER.info("[Zone] Loaded {} zones, {} aliases, {} markers, {} zone-area links",
            registry.zones.size(), registry.aliases.size(), registry.markerPositions.size(),
            registry.zoneToAreas.size());

        return registry;
    }
}
