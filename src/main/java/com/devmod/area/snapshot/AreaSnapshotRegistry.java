package com.devmod.area.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Registry for area snapshot metadata.
 * Stores snapshot metadata as SavedData, while actual block data is in separate files.
 * Implements automatic pruning to prevent unbounded growth.
 */
public class AreaSnapshotRegistry extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaSnapshotRegistry.class);
    private static final String DATA_NAME = "devmod_area_snapshots";
    private static final int DATA_VERSION = 1;

    private static final String TAG_VERSION = "version";
    private static final String TAG_SNAPSHOTS = "snapshots";

    /** Maximum snapshots per area to prevent unbounded growth */
    public static final int MAX_SNAPSHOTS_PER_AREA = 10;

    /** Maximum total snapshots across all areas */
    public static final int MAX_TOTAL_SNAPSHOTS = 100;

    /** All snapshots by ID */
    private final Map<UUID, AreaSnapshot> snapshots = new ConcurrentHashMap<>();

    /** Index: area ID -> set of snapshot IDs for fast lookup */
    private final Map<UUID, Set<UUID>> snapshotsByArea = new ConcurrentHashMap<>();

    /** Reference to server for world folder access */
    @Nullable
    private MinecraftServer server = null;

    /**
     * Gets the AreaSnapshotRegistry instance for the server.
     */
    @Nonnull
    public static AreaSnapshotRegistry get(@Nonnull MinecraftServer server) {
        Objects.requireNonNull(server);
        AreaSnapshotRegistry registry = Objects.requireNonNull(
            server.overworld().getDataStorage()
                .computeIfAbsent(Objects.requireNonNull(factory()), DATA_NAME)
        );
        registry.server = server;
        return registry;
    }

    private static Factory<AreaSnapshotRegistry> factory() {
        return new Factory<>(
            AreaSnapshotRegistry::new,
            AreaSnapshotRegistry::load,
            net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
    }

    // ========================================================================
    // Registration and Pruning
    // ========================================================================

    /** Lock object for thread-safe registration (C-06 fix) */
    private final Object registrationLock = new Object();

    /**
     * Registers a new snapshot. Automatically prunes oldest if limits exceeded.
     * Thread-safe: synchronized to ensure atomicity of compound operations (C-06 fix).
     *
     * @param snapshot The snapshot metadata to register
     */
    public void registerSnapshot(@Nonnull AreaSnapshot snapshot) {
        Objects.requireNonNull(snapshot);

        synchronized (registrationLock) {
            // Prune to make room (limit - 1 to make room for new one)
            pruneOldestForArea(Objects.requireNonNull(snapshot.areaId()), MAX_SNAPSHOTS_PER_AREA - 1);
            pruneOldestGlobal(MAX_TOTAL_SNAPSHOTS - 1);

            // Register - both operations must complete atomically (C-06 fix)
            snapshots.put(snapshot.id(), snapshot);
            snapshotsByArea.computeIfAbsent(snapshot.areaId(), k -> ConcurrentHashMap.newKeySet())
                .add(snapshot.id());

            setDirty();
        }
        LOGGER.info("[Snapshot] Registered snapshot {} for area {} ({} blocks)",
            snapshot.id(), snapshot.areaId(), snapshot.blockCount());
    }

    /**
     * Prunes oldest snapshots for an area to stay within limit.
     *
     * @param areaId   The area to prune snapshots for
     * @param maxCount Maximum snapshots to keep
     */
    private void pruneOldestForArea(@Nonnull UUID areaId, int maxCount) {
        List<AreaSnapshot> areaSnapshots = getSnapshotsForArea(areaId);
        if (areaSnapshots.size() <= maxCount) {
            return;
        }

        // Sort by creation time (oldest first)
        areaSnapshots.sort(Comparator.comparingLong(AreaSnapshot::createdAt));

        // Delete oldest until within limit
        int toDelete = areaSnapshots.size() - maxCount;
        for (int i = 0; i < toDelete; i++) {
            AreaSnapshot old = areaSnapshots.get(i);
            deleteSnapshotInternal(Objects.requireNonNull(old.id()), getWorldFolder());
            LOGGER.info("[Snapshot] Auto-pruned old snapshot {} for area {} (per-area limit)",
                old.id(), areaId);
        }
    }

    /**
     * Prunes oldest snapshots globally to stay within total limit.
     *
     * @param maxCount Maximum total snapshots to keep
     */
    private void pruneOldestGlobal(int maxCount) {
        if (snapshots.size() <= maxCount) {
            return;
        }

        // Get all snapshots sorted by creation time (oldest first)
        List<AreaSnapshot> all = new ArrayList<>(snapshots.values());
        all.sort(Comparator.comparingLong(AreaSnapshot::createdAt));

        // Delete oldest until within limit
        int toDelete = all.size() - maxCount;
        for (int i = 0; i < toDelete; i++) {
            AreaSnapshot old = all.get(i);
            deleteSnapshotInternal(Objects.requireNonNull(old.id()), getWorldFolder());
            LOGGER.info("[Snapshot] Auto-pruned old snapshot {} (global limit)", old.id());
        }
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    /**
     * Gets a snapshot by ID.
     */
    @Nonnull
    public Optional<AreaSnapshot> getSnapshot(@Nonnull UUID snapshotId) {
        return Objects.requireNonNull(Optional.ofNullable(snapshots.get(Objects.requireNonNull(snapshotId))));
    }

    /**
     * Gets all snapshots for an area, sorted by creation time (newest first).
     */
    @Nonnull
    public List<AreaSnapshot> getSnapshotsForArea(@Nonnull UUID areaId) {
        Objects.requireNonNull(areaId);
        Set<UUID> ids = snapshotsByArea.get(areaId);
        if (ids == null || ids.isEmpty()) {
            return Objects.requireNonNull(List.of());
        }

        List<AreaSnapshot> result = new ArrayList<>();
        for (UUID id : ids) {
            AreaSnapshot snapshot = snapshots.get(id);
            if (snapshot != null) {
                result.add(snapshot);
            }
        }

        // Sort newest first
        result.sort(Comparator.comparingLong(AreaSnapshot::createdAt).reversed());
        return result;
    }

    /**
     * Gets the most recent snapshot for an area.
     */
    @Nonnull
    public Optional<AreaSnapshot> getLatestSnapshot(@Nonnull UUID areaId) {
        List<AreaSnapshot> areaSnapshots = getSnapshotsForArea(areaId);
        if (areaSnapshots.isEmpty()) {
            return Objects.requireNonNull(Optional.empty());
        }
        return Objects.requireNonNull(Optional.of(areaSnapshots.get(0)));
    }

    /**
     * Gets the total number of snapshots.
     */
    public int getSnapshotCount() {
        return snapshots.size();
    }

    /**
     * Gets the number of snapshots for a specific area.
     */
    public int getSnapshotCountForArea(@Nonnull UUID areaId) {
        Set<UUID> ids = snapshotsByArea.get(Objects.requireNonNull(areaId));
        return ids != null ? ids.size() : 0;
    }

    /**
     * Checks if an area has reached its snapshot limit.
     */
    public boolean isAtLimit(@Nonnull UUID areaId) {
        return getSnapshotCountForArea(areaId) >= MAX_SNAPSHOTS_PER_AREA;
    }

    // ========================================================================
    // Deletion
    // ========================================================================

    /**
     * Deletes a snapshot and its file.
     *
     * @param snapshotId  The snapshot ID to delete
     * @param worldFolder The world folder path
     * @return true if deleted, false if not found
     */
    public boolean deleteSnapshot(@Nonnull UUID snapshotId, @Nullable Path worldFolder) {
        Objects.requireNonNull(snapshotId);
        return deleteSnapshotInternal(snapshotId, worldFolder);
    }

    /**
     * Internal delete method used by both public delete and pruning.
     * Thread-safe: synchronized to ensure atomicity of index updates (C-06 fix).
     * M-01 fix: Only removes metadata if file deletion succeeds (or file doesn't exist).
     */
    private boolean deleteSnapshotInternal(@Nonnull UUID snapshotId, @Nullable Path worldFolder) {
        AreaSnapshot snapshot;
        synchronized (registrationLock) {
            snapshot = snapshots.get(snapshotId);
            if (snapshot == null) {
                return false;
            }

            // M-01 fix: Delete file FIRST, only remove metadata if file deletion succeeds
            if (worldFolder != null) {
                Path filePath = worldFolder.resolve(snapshot.getFilePath());
                try {
                    if (Files.exists(filePath)) {
                        Files.delete(filePath);
                        LOGGER.debug("[Snapshot] Deleted snapshot file: {}", filePath);
                    }
                } catch (IOException e) {
                    // M-01 fix: Don't remove metadata if file deletion failed - prevents orphan files
                    LOGGER.error("[Snapshot] Failed to delete snapshot file, keeping metadata: {}", filePath, e);
                    return false;
                }

                // L-06 fix: Directory cleanup is best-effort - don't fail deletion if this fails
                // Also skip symlinks to avoid unexpected behavior
                Path parentDir = filePath.getParent();
                if (parentDir != null && Files.isDirectory(parentDir) && !Files.isSymbolicLink(parentDir)) {
                    try (var stream = Files.list(parentDir)) {
                        if (stream.findAny().isEmpty()) {
                            Files.delete(parentDir);
                            LOGGER.debug("[Snapshot] Deleted empty directory: {}", parentDir);
                        }
                    } catch (IOException e) {
                        // Log but don't fail - the snapshot file is already deleted
                        LOGGER.debug("[Snapshot] Could not cleanup empty directory (non-critical): {}", parentDir);
                    }
                }
            }

            // Now safe to remove metadata - file is already deleted
            snapshots.remove(snapshotId);

            // Remove from area index - must be atomic with snapshots.remove() (C-06 fix)
            Set<UUID> areaIds = snapshotsByArea.get(snapshot.areaId());
            if (areaIds != null) {
                areaIds.remove(snapshotId);
                if (areaIds.isEmpty()) {
                    snapshotsByArea.remove(snapshot.areaId());
                }
            }

            setDirty();
        }

        LOGGER.info("[Snapshot] Deleted snapshot {}", snapshotId);
        return true;
    }

    /**
     * Deletes all snapshots for an area.
     *
     * @param areaId      The area ID
     * @param worldFolder The world folder path
     * @return Number of snapshots deleted
     */
    public int deleteSnapshotsForArea(@Nonnull UUID areaId, @Nullable Path worldFolder) {
        Objects.requireNonNull(areaId);
        List<AreaSnapshot> toDelete = getSnapshotsForArea(areaId);
        int count = 0;
        for (AreaSnapshot snapshot : toDelete) {
            if (deleteSnapshotInternal(Objects.requireNonNull(snapshot.id()), worldFolder)) {
                count++;
            }
        }
        return count;
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Gets the world folder path, or null if server not available.
     */
    @Nullable
    private Path getWorldFolder() {
        MinecraftServer srv = server;
        return srv != null ? srv.getWorldPath(Objects.requireNonNull(net.minecraft.world.level.storage.LevelResource.ROOT)) : null;
    }

    /**
     * Gets the full file path for a snapshot.
     */
    @Nullable
    public Path getSnapshotFilePath(@Nonnull UUID snapshotId) {
        AreaSnapshot snapshot = snapshots.get(Objects.requireNonNull(snapshotId));
        MinecraftServer srv = server;
        if (snapshot == null || srv == null) {
            return null;
        }
        return srv.getWorldPath(Objects.requireNonNull(net.minecraft.world.level.storage.LevelResource.ROOT))
            .resolve(snapshot.getFilePath());
    }

    // ========================================================================
    // Serialization
    // ========================================================================

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        tag.putInt(TAG_VERSION, DATA_VERSION);

        ListTag snapshotsList = new ListTag();
        for (AreaSnapshot snapshot : snapshots.values()) {
            AreaSnapshot.CODEC.encodeStart(NbtOps.INSTANCE, snapshot)
                .result()
                .ifPresent(snapshotsList::add);
        }
        tag.put(TAG_SNAPSHOTS, snapshotsList);

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
        LOGGER.info("[Snapshot] Migrating data from version {} to {}", fromVersion, DATA_VERSION);

        int currentVersion = fromVersion;

        // Migration: v0 -> v1 (initial version, no migration needed)
        if (currentVersion < 1) {
            currentVersion = 1;
        }

        // Future migrations would be added here:
        // if (currentVersion < 2) {
        //     tag = migrateV1ToV2(tag);
        //     currentVersion = 2;
        // }

        tag.putInt(TAG_VERSION, DATA_VERSION);

        LOGGER.info("[Snapshot] Migration complete, now at version {}", DATA_VERSION);
        return tag;
    }

    @Nonnull
    public static AreaSnapshotRegistry load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        AreaSnapshotRegistry registry = new AreaSnapshotRegistry();

        int version = tag.getInt(TAG_VERSION);
        if (version > DATA_VERSION) {
            LOGGER.warn("[Snapshot] Loading data from newer version {} (current: {})", version, DATA_VERSION);
        }

        // Apply migrations if needed (version < current)
        if (version < DATA_VERSION) {
            tag = migrateData(tag, version);
        }

        ListTag snapshotsList = tag.getList(TAG_SNAPSHOTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < snapshotsList.size(); i++) {
            CompoundTag snapshotTag = snapshotsList.getCompound(i);
            AreaSnapshot.CODEC.parse(NbtOps.INSTANCE, snapshotTag)
                .result()
                .ifPresent(snapshot -> {
                    registry.snapshots.put(snapshot.id(), snapshot);
                    registry.snapshotsByArea
                        .computeIfAbsent(snapshot.areaId(), k -> ConcurrentHashMap.newKeySet())
                        .add(snapshot.id());
                });
        }

        LOGGER.info("[Snapshot] Loaded {} snapshots", registry.snapshots.size());
        return registry;
    }
}
