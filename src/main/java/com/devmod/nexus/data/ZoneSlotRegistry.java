package com.devmod.nexus.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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

import com.devmod.area.data.AreaRegistry;
import com.devmod.zone.data.ZoneBounds;

/**
 * Registry for zone slots in the Nexus hub.
 * Persisted as SavedData in the overworld.
 *
 * <p>Thread Safety:
 * Uses ConcurrentHashMap for thread-safe storage.
 * CRUD operations are synchronized to ensure atomicity.
 *
 * <p>Design:
 * <ul>
 *   <li>Slots define physical locations in the Nexus hub</li>
 *   <li>Each slot can be linked to an AreaDefinition for content</li>
 *   <li>Slots can also be linked to a ZoneDefinition for tracking</li>
 * </ul>
 */
public class ZoneSlotRegistry extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneSlotRegistry.class);
    private static final String DATA_NAME = "devmod_nexus_slots";
    private static final int DATA_VERSION = 1;

    private static final String TAG_VERSION = "version";
    private static final String TAG_SLOTS = "slots";
    private static final String TAG_INITIALIZED = "initialized";

    // Thread-safe storage
    private final Map<String, ZoneSlot> slots = new ConcurrentHashMap<>();
    private final Map<UUID, String> areaToSlot = new ConcurrentHashMap<>();
    private final Map<UUID, String> zoneToSlot = new ConcurrentHashMap<>();

    // Version tracking for cache invalidation
    private final AtomicLong modificationVersion = new AtomicLong();

    // Whether default slots have been initialized
    private volatile boolean initialized = false;

    @Nullable
    private MinecraftServer server = null;

    // ========================================================================
    // Factory / Instance Access
    // ========================================================================

    /**
     * Gets the ZoneSlotRegistry instance for the server.
     * Data is stored in the overworld.
     */
    @Nonnull
    public static ZoneSlotRegistry get(@Nonnull MinecraftServer server) {
        Objects.requireNonNull(server);
        ZoneSlotRegistry registry = Objects.requireNonNull(
            server.overworld().getDataStorage()
                .computeIfAbsent(Objects.requireNonNull(factory()), DATA_NAME)
        );
        synchronized (registry) {
            registry.server = server;
        }
        return registry;
    }

    private static Factory<ZoneSlotRegistry> factory() {
        return new Factory<>(
            ZoneSlotRegistry::new,
            ZoneSlotRegistry::load,
            net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
    }

    /**
     * Gets the current modification version.
     * Useful for detecting changes and invalidating caches.
     */
    public long getModificationVersion() {
        return modificationVersion.get();
    }

    /**
     * Check if default slots have been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    // ========================================================================
    // CRUD Operations
    // ========================================================================

    /**
     * Registers a new slot.
     * Fails if a slot with the same ID already exists.
     *
     * @param slot the slot to register
     * @throws IllegalArgumentException if slot ID already exists
     */
    public synchronized void registerSlot(@Nonnull ZoneSlot slot) {
        Objects.requireNonNull(slot, "slot cannot be null");

        if (slots.containsKey(slot.slotId())) {
            throw new IllegalArgumentException("Slot ID already exists: " + slot.slotId());
        }

        // Validate no overlapping bounds with existing slots
        for (ZoneSlot existing : slots.values()) {
            if (slot.overlaps(Objects.requireNonNull(existing))) {
                throw new IllegalArgumentException("Slot " + slot.slotId() +
                    " overlaps with existing slot " + existing.slotId());
            }
        }

        slots.put(slot.slotId(), slot);

        // Update reverse indices
        if (slot.linkedAreaId() != null) {
            areaToSlot.put(slot.linkedAreaId(), slot.slotId());
        }
        if (slot.linkedZoneId() != null) {
            zoneToSlot.put(slot.linkedZoneId(), slot.slotId());
        }

        modificationVersion.incrementAndGet();
        setDirty();
        LOGGER.debug("[Nexus] Registered slot: {} ({})", slot.slotId(), slot.displayName());
    }

    /**
     * Updates an existing slot.
     *
     * @param slot the updated slot (must have same slotId as existing)
     * @return true if update succeeded, false if slot not found
     */
    public synchronized boolean updateSlot(@Nonnull ZoneSlot slot) {
        Objects.requireNonNull(slot, "slot cannot be null");

        ZoneSlot existing = slots.get(slot.slotId());
        if (existing == null) {
            LOGGER.warn("[Nexus] Update failed: slot {} not found", slot.slotId());
            return false;
        }

        // Validate no overlapping bounds with other slots (exclude self)
        if (!slot.bounds().equals(existing.bounds())) {
            for (ZoneSlot other : slots.values()) {
                if (!other.slotId().equals(slot.slotId()) && slot.overlaps(Objects.requireNonNull(other))) {
                    LOGGER.warn("[Nexus] Update failed: slot {} would overlap with {}",
                        slot.slotId(), other.slotId());
                    return false;
                }
            }
        }

        // Update reverse indices
        // Remove old mappings
        if (existing.linkedAreaId() != null) {
            areaToSlot.remove(existing.linkedAreaId());
        }
        if (existing.linkedZoneId() != null) {
            zoneToSlot.remove(existing.linkedZoneId());
        }

        // Add new mappings
        if (slot.linkedAreaId() != null) {
            areaToSlot.put(slot.linkedAreaId(), slot.slotId());
        }
        if (slot.linkedZoneId() != null) {
            zoneToSlot.put(slot.linkedZoneId(), slot.slotId());
        }

        slots.put(slot.slotId(), slot);
        modificationVersion.incrementAndGet();
        setDirty();
        LOGGER.debug("[Nexus] Updated slot: {}", slot.slotId());
        return true;
    }

    /**
     * Deletes a slot.
     * Only TEST_AREA slots can be deleted.
     *
     * @param slotId the slot ID to delete
     * @return true if deleted, false if not found or not removable
     */
    public synchronized boolean deleteSlot(@Nonnull String slotId) {
        Objects.requireNonNull(slotId, "slotId cannot be null");

        ZoneSlot slot = slots.get(slotId);
        if (slot == null) {
            LOGGER.warn("[Nexus] Delete failed: slot {} not found", slotId);
            return false;
        }

        if (!slot.isRemovable()) {
            LOGGER.warn("[Nexus] Delete failed: slot {} is not removable (type: {})",
                slotId, slot.type());
            return false;
        }

        // Remove from indices
        if (slot.linkedAreaId() != null) {
            areaToSlot.remove(slot.linkedAreaId());
        }
        if (slot.linkedZoneId() != null) {
            zoneToSlot.remove(slot.linkedZoneId());
        }

        slots.remove(slotId);
        modificationVersion.incrementAndGet();
        setDirty();
        LOGGER.debug("[Nexus] Deleted slot: {}", slotId);
        return true;
    }

    // ========================================================================
    // Linking Operations
    // ========================================================================

    /**
     * Links an area to a slot.
     *
     * @param slotId the slot ID
     * @param areaId the area ID to link
     * @return true if linked successfully
     */
    public synchronized boolean linkArea(@Nonnull String slotId, @Nonnull UUID areaId) {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Objects.requireNonNull(areaId, "areaId cannot be null");

        ZoneSlot slot = slots.get(slotId);
        if (slot == null) {
            LOGGER.warn("[Nexus] Link failed: slot {} not found", slotId);
            return false;
        }

        // Validate area exists in AreaRegistry
        if (server != null) {
            AreaRegistry areaRegistry = AreaRegistry.get(server);
            if (areaRegistry.getArea(areaId).isEmpty()) {
                LOGGER.warn("[Nexus] Link failed: area {} not found in AreaRegistry", areaId);
                return false;
            }
        }

        // Check if area is already linked to another slot
        String existingSlot = areaToSlot.get(areaId);
        if (existingSlot != null && !existingSlot.equals(slotId)) {
            LOGGER.warn("[Nexus] Link failed: area {} already linked to slot {}",
                areaId, existingSlot);
            return false;
        }

        // Remove old area link if any
        if (slot.linkedAreaId() != null) {
            areaToSlot.remove(slot.linkedAreaId());
        }

        // Update slot and indices
        ZoneSlot updated = slot.withLinkedArea(areaId);
        slots.put(slotId, updated);
        areaToSlot.put(areaId, slotId);

        modificationVersion.incrementAndGet();
        setDirty();
        LOGGER.debug("[Nexus] Linked area {} to slot {}", areaId, slotId);
        return true;
    }

    /**
     * Unlinks an area from a slot.
     *
     * @param slotId the slot ID
     * @return true if unlinked successfully
     */
    public synchronized boolean unlinkArea(@Nonnull String slotId) {
        Objects.requireNonNull(slotId, "slotId cannot be null");

        ZoneSlot slot = slots.get(slotId);
        if (slot == null) {
            LOGGER.warn("[Nexus] Unlink failed: slot {} not found", slotId);
            return false;
        }

        if (slot.linkedAreaId() == null) {
            LOGGER.debug("[Nexus] Slot {} has no linked area", slotId);
            return true; // Already unlinked
        }

        areaToSlot.remove(slot.linkedAreaId());
        ZoneSlot updated = slot.unlinked();
        slots.put(slotId, updated);

        modificationVersion.incrementAndGet();
        setDirty();
        LOGGER.debug("[Nexus] Unlinked area from slot {}", slotId);
        return true;
    }

    /**
     * Links a zone to a slot.
     *
     * @param slotId the slot ID
     * @param zoneId the zone ID to link
     * @return true if linked successfully
     */
    public synchronized boolean linkZone(@Nonnull String slotId, @Nonnull UUID zoneId) {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Objects.requireNonNull(zoneId, "zoneId cannot be null");

        ZoneSlot slot = slots.get(slotId);
        if (slot == null) {
            LOGGER.warn("[Nexus] Link zone failed: slot {} not found", slotId);
            return false;
        }

        // Remove old zone link if any
        if (slot.linkedZoneId() != null) {
            zoneToSlot.remove(slot.linkedZoneId());
        }

        ZoneSlot updated = slot.withLinkedZone(zoneId);
        slots.put(slotId, updated);
        zoneToSlot.put(zoneId, slotId);

        modificationVersion.incrementAndGet();
        setDirty();
        LOGGER.debug("[Nexus] Linked zone {} to slot {}", zoneId, slotId);
        return true;
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    /**
     * Gets a slot by ID.
     */
    @Nonnull
    public Optional<ZoneSlot> getSlot(@Nonnull String slotId) {
        return Objects.requireNonNull(Optional.ofNullable(slots.get(Objects.requireNonNull(slotId))));
    }

    /**
     * Gets a slot by linked area ID.
     */
    @Nonnull
    public Optional<ZoneSlot> getSlotByArea(@Nonnull UUID areaId) {
        String slotId = areaToSlot.get(Objects.requireNonNull(areaId));
        return Objects.requireNonNull(slotId != null ? getSlot(slotId) : Optional.empty());
    }

    /**
     * Gets a slot by linked zone ID.
     */
    @Nonnull
    public Optional<ZoneSlot> getSlotByZone(@Nonnull UUID zoneId) {
        String slotId = zoneToSlot.get(Objects.requireNonNull(zoneId));
        return Objects.requireNonNull(slotId != null ? getSlot(slotId) : Optional.empty());
    }

    /**
     * Gets all slots.
     */
    @Nonnull
    public Collection<ZoneSlot> getAllSlots() {
        return new ArrayList<>(slots.values());
    }

    /**
     * Gets all slots of a specific type.
     */
    @Nonnull
    public List<ZoneSlot> getSlotsByType(@Nonnull SlotType type) {
        Objects.requireNonNull(type);
        List<ZoneSlot> result = new ArrayList<>();
        for (ZoneSlot slot : slots.values()) {
            if (Objects.requireNonNull(slot).type() == type) {
                result.add(slot);
            }
        }
        return result;
    }

    /**
     * Gets all editable slots (type = EDITABLE).
     */
    @Nonnull
    public List<ZoneSlot> getEditableSlots() {
        return getSlotsByType(SlotType.EDITABLE);
    }

    /**
     * Gets all linked slots (have an area linked).
     */
    @Nonnull
    public List<ZoneSlot> getLinkedSlots() {
        List<ZoneSlot> result = new ArrayList<>();
        for (ZoneSlot slot : slots.values()) {
            if (Objects.requireNonNull(slot).hasLinkedArea()) {
                result.add(slot);
            }
        }
        return result;
    }

    /**
     * Gets all empty slots (no area linked).
     */
    @Nonnull
    public List<ZoneSlot> getEmptySlots() {
        List<ZoneSlot> result = new ArrayList<>();
        for (ZoneSlot slot : slots.values()) {
            if (Objects.requireNonNull(slot).isEmpty()) {
                result.add(slot);
            }
        }
        return result;
    }

    /**
     * Gets the number of slots.
     */
    public int getSlotCount() {
        return slots.size();
    }

    // ========================================================================
    // Spatial Queries
    // ========================================================================

    /**
     * Gets the slot at a position.
     * Returns the highest priority slot if multiple slots contain the position.
     */
    @Nonnull
    public Optional<ZoneSlot> getSlotAt(@Nonnull BlockPos pos) {
        Objects.requireNonNull(pos);
        ZoneSlot best = null;
        for (ZoneSlot slot : slots.values()) {
            ZoneSlot s = Objects.requireNonNull(slot);
            if (s.contains(pos)) {
                if (best == null || s.priority() > best.priority()) {
                    best = s;
                }
            }
        }
        return Objects.requireNonNull(Optional.ofNullable(best));
    }

    /**
     * Checks if a position is inside any slot.
     */
    public boolean isPositionInSlot(@Nonnull BlockPos pos) {
        return getSlotAt(pos).isPresent();
    }

    /**
     * Checks if bounds would overlap with any existing slot.
     *
     * @param bounds the bounds to check
     * @param excludeSlotId optional slot ID to exclude from check (for updates)
     */
    public boolean boundsOverlap(@Nonnull ZoneBounds bounds, @Nullable String excludeSlotId) {
        Objects.requireNonNull(bounds);
        for (ZoneSlot slot : slots.values()) {
            if (excludeSlotId != null && slot.slotId().equals(excludeSlotId)) {
                continue;
            }
            if (slot.bounds().overlaps(bounds)) {
                return true;
            }
        }
        return false;
    }

    // ========================================================================
    // Initialization
    // ========================================================================

    /**
     * Initializes default slots from presets.
     * Called once when the hub is first created.
     *
     * @return true if initialized, false if already initialized
     */
    public synchronized boolean initializeDefaultSlots() {
        if (initialized) {
            LOGGER.debug("[Nexus] Slots already initialized");
            return false;
        }

        LOGGER.info("[Nexus] Initializing default slots...");
        List<ZoneSlot> defaultSlots = ZoneSlotPresets.createDefaultSlots();

        for (ZoneSlot slot : defaultSlots) {
            try {
                registerSlot(Objects.requireNonNull(slot));
            } catch (Exception e) {
                LOGGER.error("[Nexus] Failed to register default slot {}: {}",
                    slot.slotId(), e.getMessage());
            }
        }

        initialized = true;
        setDirty();
        LOGGER.info("[Nexus] Initialized {} default slots", defaultSlots.size());
        return true;
    }

    // ========================================================================
    // Persistence
    // ========================================================================

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        tag.putInt(TAG_VERSION, DATA_VERSION);
        tag.putBoolean(TAG_INITIALIZED, initialized);

        // Save slots
        ListTag slotsList = new ListTag();
        var slotsSnapshot = new ArrayList<>(slots.values());
        for (ZoneSlot slot : slotsSnapshot) {
            DataResult<Tag> result = ZoneSlot.CODEC.encodeStart(NbtOps.INSTANCE, slot);
            result.result().ifPresent(slotsList::add);
        }
        tag.put(TAG_SLOTS, slotsList);

        return tag;
    }

    @Nonnull
    public static ZoneSlotRegistry load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        ZoneSlotRegistry registry = new ZoneSlotRegistry();

        int version = tag.getInt(TAG_VERSION);
        if (version > DATA_VERSION) {
            LOGGER.warn("[Nexus] Loading data from newer version {} (current: {})",
                version, DATA_VERSION);
        }

        registry.initialized = tag.getBoolean(TAG_INITIALIZED);

        // Load slots
        ListTag slotsList = tag.getList(TAG_SLOTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < slotsList.size(); i++) {
            final int index = i;
            CompoundTag slotTag = slotsList.getCompound(i);
            DataResult<ZoneSlot> result = ZoneSlot.CODEC.parse(NbtOps.INSTANCE, slotTag);
            result.result().ifPresent(slot -> {
                registry.slots.put(slot.slotId(), slot);
                // Rebuild indices
                if (slot.linkedAreaId() != null) {
                    registry.areaToSlot.put(slot.linkedAreaId(), slot.slotId());
                }
                if (slot.linkedZoneId() != null) {
                    registry.zoneToSlot.put(slot.linkedZoneId(), slot.slotId());
                }
            });
            result.error().ifPresent(error ->
                LOGGER.warn("[Nexus] Failed to load slot {}: {}", index, error.message()));
        }

        LOGGER.info("[Nexus] Loaded {} slots (initialized: {})",
            registry.slots.size(), registry.initialized);
        return registry;
    }
}
