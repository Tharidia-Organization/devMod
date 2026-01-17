package com.devmod.area.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.mojang.serialization.DataResult;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import com.devmod.area.builder.AreaShapeGenerator;
import com.devmod.area.snapshot.AreaSnapshotRegistry;
import com.devmod.zone.data.ZoneRegistry;

/**
 * Registry for storing and managing area definitions.
 * Persisted as SavedData in the overworld.
 *
 * <h2>Thread Safety and Lock Ordering (ARCH-03)</h2>
 * <p>The area module uses the following locks (ordered from outermost to innermost):
 *
 * <ol>
 *   <li><b>AreaRegistry (this)</b> - CRUD operations on areas. Acquired via synchronized methods.
 *       This is the primary coordination lock.</li>
 *   <li><b>ZoneRegistry (that)</b> - Zone linking operations. Called from within AreaRegistry
 *       during createArea/deleteArea, so acquired AFTER AreaRegistry lock.</li>
 *   <li><b>AreaBuildStateRegistry</b> - Build state persistence. Called from AreaRegistry.createArea(),
 *       so acquired AFTER AreaRegistry lock.</li>
 *   <li><b>AreaSnapshotRegistry.registrationLock</b> - Snapshot registration/deletion.
 *       Independent of AreaRegistry but may be called from same thread.</li>
 *   <li><b>AreaBuildTaskManager (internal)</b> - Task queue management via ConcurrentHashMap.</li>
 *   <li><b>AreaBuildTask.tickLock</b> - Individual build task tick operations.</li>
 * </ol>
 *
 * <p><b>Deadlock Prevention Rules:</b>
 * <ul>
 *   <li>Never call AreaRegistry methods while holding AreaBuildTask.tickLock</li>
 *   <li>Never call ZoneRegistry methods while holding AreaSnapshotRegistry.registrationLock</li>
 *   <li>AreaNetworkHandler rate limiting (class lock) is independent and short-lived</li>
 * </ul>
 */
public class AreaRegistry extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaRegistry.class);
    private static final String DATA_NAME = "devmod_area_registry";
    private static final int DATA_VERSION = 1;

    private static final String TAG_VERSION = "version";
    private static final String TAG_AREAS = "areas";
    private static final String TAG_EDITORS = "editors";
    private static final String TAG_MAIN_HUB = "mainHub";

    private final Map<UUID, AreaDefinition> areas = new ConcurrentHashMap<>();
    private final Map<EditorKey, UUID> editorPositions = new ConcurrentHashMap<>();
    // CRIT-04 fix: volatile ensures visibility across threads for non-synchronized read methods
    @Nullable
    private volatile UUID mainHubId = null;
    @Nullable
    private MinecraftServer server = null;

    public record EditorKey(ResourceLocation dimensionId, BlockPos pos) {}

    /**
     * Version counter incremented on any modification.
     * Used by AreaEditorBlockEntity to detect stale caches.
     */
    private final AtomicLong modificationVersion = new AtomicLong();

    /**
     * Gets the current modification version.
     * Editors can compare this with their cached version to detect staleness.
     */
    public long getModificationVersion() {
        return modificationVersion.get();
    }

    /**
     * Gets the AreaRegistry instance for the server.
     * Data is stored in the overworld.
     */
    @Nonnull
    public static AreaRegistry get(@Nonnull MinecraftServer server) {
        Objects.requireNonNull(server);
        AreaRegistry registry = Objects.requireNonNull(
            server.overworld().getDataStorage()
                .computeIfAbsent(Objects.requireNonNull(factory()), DATA_NAME)
        );
        // Store server reference for zone linking operations
        registry.server = server;
        return registry;
    }

    private static Factory<AreaRegistry> factory() {
        return new Factory<>(
            AreaRegistry::new,
            AreaRegistry::load,
            net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
    }

    // ========================================================================
    // CRUD Operations
    // ========================================================================

    /**
     * Creates a new area and returns its ID.
     * This operation is synchronized to ensure atomicity of the compound operation.
     * Zone linking is done BEFORE storing to prevent inconsistent state if linking fails.
     */
    @Nonnull
    public synchronized UUID createArea(@Nonnull AreaDefinition def) {
        Objects.requireNonNull(def);

        // MED-10 fix: Clean up any stale build state for this UUID before creating
        // This handles the extremely rare case of UUID collision/reuse
        if (server != null && def.id() != null) {
            com.devmod.area.builder.AreaBuildStateRegistry.get(Objects.requireNonNull(server)).removeState(Objects.requireNonNull(def.id()));
        }

        // Link to zone FIRST if specified (fail-fast before storing area)
        if (def.linkedZoneId() != null && server != null) {
            ZoneRegistry zoneRegistry = ZoneRegistry.get(Objects.requireNonNull(server));
            try {
                zoneRegistry.linkAreaToZone(Objects.requireNonNull(def.id()), Objects.requireNonNull(def.linkedZoneId()));
            } catch (Exception e) {
                LOGGER.error("[Area] Failed to link area {} to zone {}: {}",
                    def.id(), def.linkedZoneId(), e.getMessage());
                throw new IllegalStateException("Zone linking failed for area " + def.id(), e);
            }
        }

        // Only store after successful zone link
        areas.put(def.id(), def);
        if (def.isMainHub() && mainHubId == null) {
            mainHubId = def.id();
        }

        modificationVersion.incrementAndGet();
        setDirty();
        LOGGER.debug("[Area] Created area: {} ({})", def.name(), def.id());
        return Objects.requireNonNull(def.id());
    }

    /**
     * Updates an existing area with optimistic locking.
     * This operation is synchronized to ensure atomicity of the compound operation.
     * Also handles zone link changes if linkedZoneId changed.
     *
     * @param id               The area ID
     * @param def              The new definition
     * @param expectedRevision The expected revision (for optimistic locking)
     * @return True if update succeeded, false if revision mismatch or not found
     */
    public synchronized boolean updateArea(@Nonnull UUID id, @Nonnull AreaDefinition def, int expectedRevision) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(def);

        AreaDefinition existing = areas.get(id);
        if (existing == null) {
            LOGGER.warn("[Area] Update failed: area {} not found", id);
            return false;
        }
        if (existing.revision() != expectedRevision) {
            LOGGER.warn("[Area] Update failed: revision mismatch for area {} (expected {}, got {})",
                id, expectedRevision, existing.revision());
            return false;
        }

        // Handle zone link changes with rollback semantics (DATA-02 fix)
        if (server != null) {
            String oldZone = existing.linkedZoneId();
            String newZone = def.linkedZoneId();

            if (!Objects.equals(oldZone, newZone)) {
                ZoneRegistry zoneRegistry = ZoneRegistry.get(Objects.requireNonNull(server));
                boolean newZoneLinked = false;

                // Link NEW zone FIRST (fail-fast before modifying old state)
                if (newZone != null) {
                    try {
                        zoneRegistry.linkAreaToZone(id, Objects.requireNonNull(newZone));
                        newZoneLinked = true;
                    } catch (Exception e) {
                        LOGGER.error("[Area] Failed to link area {} to new zone {}: {}",
                            id, newZone, e.getMessage());
                        throw new IllegalStateException("Zone linking failed", e);
                    }
                }

                // Unlink OLD zone with rollback on failure
                if (oldZone != null) {
                    try {
                        zoneRegistry.unlinkAreaFromZone(id, Objects.requireNonNull(oldZone));
                    } catch (Exception e) {
                        // Rollback: unlink the new zone we just linked
                        if (newZoneLinked) {
                            try {
                                zoneRegistry.unlinkAreaFromZone(id, Objects.requireNonNull(newZone));
                            } catch (Exception rollbackE) {
                                // DATA-02 fix: Enhanced error for inconsistent state
                                // Area is now linked to BOTH zones - manual intervention required
                                LOGGER.error("[Area] CRITICAL: Rollback failed for zone {}, area {} is now linked to both {} AND {}. Manual intervention required: {}",
                                    newZone, id, oldZone, newZone, rollbackE.getMessage());
                            }
                        }
                        LOGGER.error("[Area] Failed to unlink area {} from old zone {}: {}",
                            id, oldZone, e.getMessage());
                        throw new IllegalStateException("Zone unlinking failed", e);
                    }
                }
            }
        }

        if (def.isMainHub()) {
            if (mainHubId == null || mainHubId.equals(id)) {
                mainHubId = id;
            }
        } else if (id.equals(mainHubId)) {
            mainHubId = null;
        }

        areas.put(id, def);
        modificationVersion.incrementAndGet();
        setDirty();
        LOGGER.debug("[Area] Updated area: {} (rev {})", def.name(), def.revision());
        return true;
    }

    /**
     * Deletes an area by ID.
     * This operation is synchronized to ensure atomicity of the compound operation.
     * Also removes zone link if present.
     *
     * CRIT-01 fix: Cleanup snapshots and build state BEFORE removing area from map
     * to prevent race conditions where concurrent reads see area removed but
     * snapshots/state still present.
     */
    public synchronized void deleteArea(@Nonnull UUID id) {
        Objects.requireNonNull(id);

        // Check if area exists before cleanup
        AreaDefinition existing = areas.get(id);
        if (existing == null) {
            return;
        }

        // CRIT-01 fix: Cleanup associated resources BEFORE removing area from map
        // This prevents race conditions where concurrent threads see area removed
        // but snapshots/build state still present
        MinecraftServer srv = this.server;
        if (srv != null) {
            // Cleanup snapshots first (DATA-01 fix: cascading delete)
            AreaSnapshotRegistry snapshotRegistry = AreaSnapshotRegistry.get(srv);
            java.nio.file.Path worldFolder = srv.getWorldPath(Objects.requireNonNull(net.minecraft.world.level.storage.LevelResource.ROOT));
            int deleted = snapshotRegistry.deleteSnapshotsForArea(id, worldFolder);
            if (deleted > 0) {
                LOGGER.debug("[Area] Deleted {} snapshots for area {}", deleted, id);
            }

            // MED-10 fix: Cleanup build task state
            com.devmod.area.builder.AreaBuildStateRegistry.get(srv).removeState(id);

            // M-04 fix: Cleanup area build cooldown
            com.devmod.area.network.AreaNetworkHandler.clearAreaBuildCooldown(id);
        }

        // Now remove the area from the map
        AreaDefinition removed = areas.remove(id);
        if (removed != null) {
            // MED-02 fix: Use entrySet().removeIf() for safer concurrent iteration
            editorPositions.entrySet().removeIf(e -> e.getValue().equals(id));
            if (id.equals(mainHubId)) {
                mainHubId = null;
            }

            // Cleanup zone link if present
            if (removed.linkedZoneId() != null && server != null) {
                ZoneRegistry zoneRegistry = ZoneRegistry.get(Objects.requireNonNull(server));
                zoneRegistry.unlinkAreaFromZone(id, Objects.requireNonNull(removed.linkedZoneId()));
            }

            modificationVersion.incrementAndGet();
            setDirty();
            LOGGER.debug("[Area] Deleted area: {} ({})", removed.name(), id);
        }
    }

    /**
     * Gets an area by ID.
     */
    @Nonnull
    public Optional<AreaDefinition> getArea(@Nonnull UUID id) {
        return Objects.requireNonNull(Optional.ofNullable(areas.get(Objects.requireNonNull(id))));
    }

    /**
     * Gets all areas as an unmodifiable collection.
     */
    @Nonnull
    public Collection<AreaDefinition> getAllAreas() {
        return Objects.requireNonNull(Collections.unmodifiableCollection(areas.values()));
    }

    /**
     * Gets the number of registered areas.
     */
    public int getAreaCount() {
        return areas.size();
    }

    /**
     * Gets all areas linked to a specific zone.
     *
     * @param zoneId The zone ID to search for
     * @return List of areas linked to the zone (never null, may be empty)
     */
    @Nonnull
    public List<AreaDefinition> getAreasByZone(@Nonnull String zoneId) {
        Objects.requireNonNull(zoneId);
        return Objects.requireNonNull(areas.values().stream()
            .filter(a -> zoneId.equals(a.linkedZoneId()))
            .toList());
    }

    // ========================================================================
    // Main Hub
    // ========================================================================

    /**
     * Gets the main hub area if one exists.
     */
    @Nonnull
    public Optional<AreaDefinition> getMainHub() {
        return mainHubId != null ? getArea(mainHubId) : Objects.requireNonNull(Optional.empty());
    }

    /**
     * Returns true if a main hub has been registered.
     */
    public boolean hasMainHub() {
        return mainHubId != null && areas.containsKey(mainHubId);
    }

    /**
     * Returns the main hub area ID, or null if none.
     */
    @Nullable
    public UUID getMainHubId() {
        return mainHubId;
    }

    /**
     * Sets the main hub area ID.
     * @param hubId The area ID to set as main hub
     * @return true if successfully set, false if area doesn't exist
     */
    public synchronized boolean setMainHub(@Nonnull UUID hubId) {
        Objects.requireNonNull(hubId);
        if (!areas.containsKey(hubId)) {
            LOGGER.warn("[Area] setMainHub failed: area {} not found", hubId);
            return false;
        }
        this.mainHubId = hubId;
        setDirty();
        LOGGER.debug("[Area] Set main hub to: {}", hubId);
        return true;
    }

    // ========================================================================
    // Editor Position Tracking
    // ========================================================================

    /**
     * Registers an editor block position linked to an area in a specific dimension.
     */
    public void registerEditor(@Nonnull ResourceLocation dimensionId, @Nonnull BlockPos pos, @Nonnull UUID areaId) {
        EditorKey key = new EditorKey(Objects.requireNonNull(dimensionId), Objects.requireNonNull(pos));
        editorPositions.put(key, Objects.requireNonNull(areaId));
        setDirty();
    }

    /**
     * Unregisters an editor block position in a specific dimension.
     */
    public void unregisterEditor(@Nonnull ResourceLocation dimensionId, @Nonnull BlockPos pos) {
        EditorKey key = new EditorKey(Objects.requireNonNull(dimensionId), Objects.requireNonNull(pos));
        if (editorPositions.remove(key) != null) {
            setDirty();
        }
    }

    /**
     * Gets the area ID at an editor position in a specific dimension.
     */
    @Nonnull
    public Optional<UUID> getAreaAtEditor(@Nonnull ResourceLocation dimensionId, @Nonnull BlockPos pos) {
        EditorKey key = new EditorKey(Objects.requireNonNull(dimensionId), Objects.requireNonNull(pos));
        return Objects.requireNonNull(Optional.ofNullable(editorPositions.get(key)));
    }

    /**
     * Gets the area definition at an editor position in a specific dimension.
     */
    @Nonnull
    public Optional<AreaDefinition> getAreaDefinitionAtEditor(@Nonnull ResourceLocation dimensionId, @Nonnull BlockPos pos) {
        return Objects.requireNonNull(getAreaAtEditor(dimensionId, pos).flatMap(this::getArea));
    }

    /**
     * Returns all editor positions (dimension + position).
     * ARCH-01 fix: Returns a snapshot copy to prevent concurrent modification issues.
     */
    @Nonnull
    public Set<EditorKey> getEditorPositions() {
        return new java.util.HashSet<>(editorPositions.keySet());
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    /**
     * Finds areas that contain a given position.
     */
    @Nonnull
    public List<AreaDefinition> findAreasContaining(@Nonnull BlockPos pos) {
        Objects.requireNonNull(pos);
        List<AreaDefinition> result = new ArrayList<>();
        for (AreaDefinition area : areas.values()) {
            if (isInsideArea(pos, Objects.requireNonNull(area))) {
                result.add(area);
            }
        }
        return result;
    }

    /**
     * Checks if a position is inside an area.
     */
    public boolean isInsideArea(@Nonnull BlockPos pos, @Nonnull AreaDefinition area) {
        Objects.requireNonNull(pos);
        Objects.requireNonNull(area);

        BlockPos center = area.centerPosition();
        AreaDimensions dims = area.dimensions();
        int dy = pos.getY() - dims.floorY();

        // SEC-11 fix: Y bounds check - include ceiling layer (dy == height is valid)
        if (dy < 0 || dy > dims.height()) {
            return false;
        }

        // Shape-specific check
        return switch (area.shape()) {
            case RECTANGULAR, CIRCULAR, HEXAGONAL, OCTAGONAL -> AreaShapeGenerator.isInside(
                pos, Objects.requireNonNull(area.shape()), Objects.requireNonNull(center), dims);
            case CUSTOM_NBT, PATH -> {
                // SEC-02 fix: Use generateFloor which correctly dispatches to generateCustomFloor or generatePathFloor
                var floorPositions = AreaShapeGenerator.generateFloor(
                    Objects.requireNonNull(area.shape()), Objects.requireNonNull(center), dims, area.customShapeNbt());
                if (floorPositions.isEmpty()) {
                    yield false;
                }
                BlockPos floorPos = new BlockPos(pos.getX(), dims.floorY(), pos.getZ());
                yield floorPositions.contains(floorPos);
            }
        };
    }

    /**
     * Checks if a proposed area would overlap with any existing areas.
     * Only checks areas in the same dimension - areas in different dimensions cannot overlap.
     */
    public boolean wouldOverlap(@Nonnull AreaDefinition proposed) {
        Objects.requireNonNull(proposed);
        for (AreaDefinition existing : areas.values()) {
            if (existing.id().equals(proposed.id())) {
                continue; // Skip self
            }
            // Areas in different dimensions cannot overlap
            if (!existing.dimensionId().equals(proposed.dimensionId())) {
                continue;
            }
            if (areasOverlap(proposed, existing)) {
                return true;
            }
        }
        return false;
    }

    private boolean areasOverlap(@Nonnull AreaDefinition a, @Nonnull AreaDefinition b) {
        AreaDimensions dimsA = a.dimensions();
        AreaDimensions dimsB = b.dimensions();

        // Check Y overlap first (cheapest check)
        int minYA = dimsA.floorY();
        int maxYA = minYA + dimsA.height();
        int minYB = dimsB.floorY();
        int maxYB = minYB + dimsB.height();
        if (!(minYA <= maxYB && maxYA >= minYB)) {
            return false; // No Y overlap, can't intersect
        }

        // SEC-07 fix: For PATH shapes, compute actual bounds from floor positions
        // PATH corridors can extend beyond declared dimensions via waypoints
        boolean aIsPath = a.shape() == AreaShape.PATH;
        boolean bIsPath = b.shape() == AreaShape.PATH;

        if (aIsPath || bIsPath) {
            // For PATH shapes, do floor intersection check for accuracy
            Set<BlockPos> floorA = AreaShapeGenerator.generateFloor(
                Objects.requireNonNull(a.shape()),
                Objects.requireNonNull(a.centerPosition()),
                dimsA,
                a.customShapeNbt()
            );
            Set<BlockPos> floorB = AreaShapeGenerator.generateFloor(
                Objects.requireNonNull(b.shape()),
                Objects.requireNonNull(b.centerPosition()),
                dimsB,
                b.customShapeNbt()
            );

            // SEC-09 fix: Check XZ overlap ignoring Y (Y overlap already confirmed above)
            // Floor positions have different Y values if floorY differs, so compare only XZ
            Set<Long> xzPositionsB = new HashSet<>();
            for (BlockPos pos : floorB) {
                xzPositionsB.add(((long) pos.getX() << 32) | (pos.getZ() & 0xFFFFFFFFL));
            }
            for (BlockPos pos : floorA) {
                long xz = ((long) pos.getX() << 32) | (pos.getZ() & 0xFFFFFFFFL);
                if (xzPositionsB.contains(xz)) {
                    return true;
                }
            }
            return false;
        }

        // Standard AABB overlap check for non-PATH shapes
        BlockPos centerA = a.centerPosition();
        BlockPos centerB = b.centerPosition();

        int minXA = centerA.getX() + AreaShapeGenerator.minOffset(dimsA.width());
        int maxXA = centerA.getX() + AreaShapeGenerator.maxOffset(dimsA.width());
        int minZA = centerA.getZ() + AreaShapeGenerator.minOffset(dimsA.length());
        int maxZA = centerA.getZ() + AreaShapeGenerator.maxOffset(dimsA.length());

        int minXB = centerB.getX() + AreaShapeGenerator.minOffset(dimsB.width());
        int maxXB = centerB.getX() + AreaShapeGenerator.maxOffset(dimsB.width());
        int minZB = centerB.getZ() + AreaShapeGenerator.minOffset(dimsB.length());
        int maxZB = centerB.getZ() + AreaShapeGenerator.maxOffset(dimsB.length());

        // Check XZ overlap
        boolean xOverlap = minXA <= maxXB && maxXA >= minXB;
        boolean zOverlap = minZA <= maxZB && maxZA >= minZB;

        return xOverlap && zOverlap;
    }

    // ========================================================================
    // Serialization
    // ========================================================================

    /**
     * Validates a loaded area definition and returns any warnings.
     * Areas are kept even with warnings to avoid data loss.
     *
     * @param area The area to validate
     * @return List of warning messages (empty if valid)
     */
    @Nonnull
    private static List<String> validateLoadedArea(@Nonnull AreaDefinition area) {
        List<String> warnings = new ArrayList<>();

        // Validate name
        if (area.name() == null || area.name().isBlank()) {
            warnings.add("name is empty");
        } else if (area.name().length() > 64) {
            warnings.add("name exceeds 64 characters");
        }

        // HIGH-01 fix: Validate dimensions with null check
        AreaDimensions dims = area.dimensions();
        if (dims == null) {
            warnings.add("dimensions is null");
            return warnings; // Can't validate further without dimensions
        }
        if (dims.width() < AreaDimensions.MIN_SIZE || dims.width() > AreaDimensions.MAX_SIZE) {
            warnings.add("width " + dims.width() + " outside valid range [" +
                AreaDimensions.MIN_SIZE + "-" + AreaDimensions.MAX_SIZE + "]");
        }
        if (dims.length() < AreaDimensions.MIN_SIZE || dims.length() > AreaDimensions.MAX_SIZE) {
            warnings.add("length " + dims.length() + " outside valid range [" +
                AreaDimensions.MIN_SIZE + "-" + AreaDimensions.MAX_SIZE + "]");
        }
        if (dims.height() < AreaDimensions.MIN_HEIGHT || dims.height() > AreaDimensions.MAX_HEIGHT) {
            warnings.add("height " + dims.height() + " outside valid range [" +
                AreaDimensions.MIN_HEIGHT + "-" + AreaDimensions.MAX_HEIGHT + "]");
        }

        // Validate floorY within typical world bounds (-64 to 320)
        if (dims.floorY() < -64 || dims.floorY() > 320) {
            warnings.add("floorY " + dims.floorY() + " outside typical world bounds [-64 to 320]");
        }

        // Validate custom generation has palette
        if (area.isCustomGeneration() && area.palette() == null) {
            warnings.add("CUSTOM generation but palette is null");
        }

        // Validate biome generation has config
        if (area.generationType() == AreaGenerationType.BIOME && area.biomeConfig() == null) {
            warnings.add("BIOME generation but biomeConfig is null");
        }

        // Validate CUSTOM_NBT shape has NBT data
        if (area.shape() == AreaShape.CUSTOM_NBT && area.customShapeNbt() == null) {
            warnings.add("CUSTOM_NBT shape but customShapeNbt is null");
        }

        return warnings;
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        tag.putInt(TAG_VERSION, DATA_VERSION);

        // Save areas - ARCH-01 fix: Create snapshot to prevent concurrent modification during save
        ListTag areasList = new ListTag();
        var areasSnapshot = new java.util.ArrayList<>(areas.values());
        for (AreaDefinition area : areasSnapshot) {
            DataResult<Tag> result = AreaDefinition.CODEC.encodeStart(NbtOps.INSTANCE, area);
            result.result().ifPresent(areasList::add);
        }
        tag.put(TAG_AREAS, areasList);

        // Save editor positions - ARCH-01 fix: Create snapshot to prevent concurrent modification
        CompoundTag editorsTag = new CompoundTag();
        var editorsSnapshot = new java.util.HashMap<>(editorPositions);
        for (Map.Entry<EditorKey, UUID> entry : editorsSnapshot.entrySet()) {
            EditorKey editorKey = entry.getKey();
            BlockPos pos = editorKey.pos();
            String key = editorKey.dimensionId() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            editorsTag.putUUID(key, Objects.requireNonNull(entry.getValue()));
        }
        tag.put(TAG_EDITORS, editorsTag);

        // Save main hub ID
        if (mainHubId != null) {
            tag.putUUID(TAG_MAIN_HUB, mainHubId);
        }

        return tag;
    }

    // ========================================================================
    // Data Migration
    // ========================================================================

    /**
     * Migrates data from an older version to the current version.
     * Applies migrations incrementally (v1 -> v2 -> v3 -> ... -> current).
     *
     * @param tag         The original NBT data
     * @param fromVersion The version the data was saved with
     * @return The migrated tag (may be the same object if no changes needed)
     */
    @Nonnull
    private static CompoundTag migrateData(@Nonnull CompoundTag tag, int fromVersion) {
        LOGGER.info("[Area] Migrating data from version {} to {}", fromVersion, DATA_VERSION);

        // Apply migrations incrementally
        // Example: if fromVersion=1 and DATA_VERSION=3, applies v1->v2, then v2->v3
        int currentVersion = fromVersion;

        // Migration: v0 -> v1 (initial version, no migration needed)
        if (currentVersion < 1) {
            // Version 0 didn't exist in production, but handle gracefully
            currentVersion = 1;
        }

        // Future migrations would be added here:
        // if (currentVersion < 2) {
        //     tag = migrateV1ToV2(tag);
        //     currentVersion = 2;
        // }
        // if (currentVersion < 3) {
        //     tag = migrateV2ToV3(tag);
        //     currentVersion = 3;
        // }

        // Update version in tag after migration
        tag.putInt(TAG_VERSION, DATA_VERSION);

        LOGGER.info("[Area] Migration complete, now at version {}", DATA_VERSION);
        return tag;
    }

    @Nonnull
    public static AreaRegistry load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        AreaRegistry registry = new AreaRegistry();

        int version = tag.getInt(TAG_VERSION);
        if (version > DATA_VERSION) {
            LOGGER.warn("[Area] Loading data from newer version {} (current: {}). Some features may not work correctly.", version, DATA_VERSION);
        }

        // Apply migrations if needed (version < current)
        if (version < DATA_VERSION) {
            tag = migrateData(tag, version);
        }

        // Load areas with validation
        ListTag areasList = tag.getList(TAG_AREAS, Tag.TAG_COMPOUND);
        for (int i = 0; i < areasList.size(); i++) {
            final int index = i;
            CompoundTag areaTag = areasList.getCompound(i);
            DataResult<AreaDefinition> result = AreaDefinition.CODEC.parse(NbtOps.INSTANCE, areaTag);
            result.result().ifPresent(area -> {
                List<String> warnings = validateLoadedArea(Objects.requireNonNull(area));
                if (!warnings.isEmpty()) {
                    LOGGER.warn("[Area] Validation warnings for area '{}' ({}): {}",
                        area.name(), area.id(), String.join("; ", warnings));
                }
                registry.areas.put(area.id(), area);
            });
            result.error().ifPresent(error ->
                LOGGER.warn("[Area] Failed to load area {}: {}", index, error.message()));
        }

        // Load editor positions
        CompoundTag editorsTag = tag.getCompound(TAG_EDITORS);
        for (String key : editorsTag.getAllKeys()) {
            String dimensionPart = null;
            String posPart = key;
            int sepIndex = key.indexOf('|');
            if (sepIndex >= 0) {
                dimensionPart = key.substring(0, sepIndex);
                posPart = key.substring(sepIndex + 1);
            }

            List<String> parts = Splitter.on(',').splitToList(Objects.requireNonNull(posPart));
            if (parts.size() == 3) {
                try {
                    BlockPos pos = new BlockPos(
                        Integer.parseInt(parts.get(0)),
                        Integer.parseInt(parts.get(1)),
                        Integer.parseInt(parts.get(2))
                    );
                    ResourceLocation dimensionId = dimensionPart != null
                        ? ResourceLocation.tryParse(dimensionPart)
                        : ResourceLocation.withDefaultNamespace("overworld");
                    if (dimensionId == null) {
                        LOGGER.warn("[Area] Invalid editor dimension in key: {}", key);
                        continue;
                    }
                    // HIGH-01 fix: Safely retrieve UUID with exception handling
                    // getUUID() throws if UUID tags don't exist or are malformed
                    UUID editorAreaId;
                    try {
                        editorAreaId = editorsTag.getUUID(key);
                    } catch (Exception e) {
                        LOGGER.warn("[Area] Failed to read UUID for editor position {}: {}", key, e.getMessage());
                        continue;
                    }
                    // MED-02 fix: Validate that referenced area exists
                    if (!registry.areas.containsKey(editorAreaId)) {
                        LOGGER.warn("[Area] Editor at {} references non-existent area {}, skipping", key, editorAreaId);
                        continue;
                    }
                    registry.editorPositions.put(new EditorKey(dimensionId, pos), editorAreaId);
                } catch (NumberFormatException e) {
                    LOGGER.warn("[Area] Invalid editor position key: {}", key);
                }
            }
        }

        // Load main hub ID with validation
        if (tag.hasUUID(TAG_MAIN_HUB)) {
            UUID hubId = tag.getUUID(TAG_MAIN_HUB);
            if (registry.areas.containsKey(hubId)) {
                registry.mainHubId = hubId;
            } else {
                LOGGER.warn("[Area] Main hub ID {} refers to non-existent area, clearing", hubId);
            }
        }

        LOGGER.info("[Area] Loaded {} areas, {} editors", registry.areas.size(), registry.editorPositions.size());
        return registry;
    }
}
