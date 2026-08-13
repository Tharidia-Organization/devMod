package com.devmod.area.snapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.devmod.area.aesthetic.AreaBuilderSounds;
import com.devmod.area.builder.AreaBuildTaskManager;
import com.devmod.area.data.AreaDefinition;

/**
 * Manages snapshot capture and restore tasks.
 * Similar to AreaBuildTaskManager - handles multi-tick operations with progress tracking.
 */
public final class AreaSnapshotManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaSnapshotManager.class);

    public static final AreaSnapshotManager INSTANCE = new AreaSnapshotManager();

    /** Maximum concurrent capture tasks */
    public static final int MAX_CONCURRENT_CAPTURES = 2;

    /** Maximum concurrent restore tasks */
    public static final int MAX_CONCURRENT_RESTORES = 2;

    /** Upper bound on the off-thread snapshot read, so a stalled disk cannot pin a snapshot id */
    private static final long MAX_SNAPSHOT_READ_MS = 30_000L;

    /** Active capture tasks by area ID (only one capture per area at a time) */
    private final Map<UUID, ActiveCapture> activeCaptures = new ConcurrentHashMap<>();

    /** Active restore tasks by snapshot ID */
    private final Map<UUID, ActiveRestore> activeRestores = new ConcurrentHashMap<>();

    /** Restores whose snapshot file is being read off-thread, by snapshot ID */
    private final Map<UUID, PendingRestore> pendingRestores = new ConcurrentHashMap<>();

    private AreaSnapshotManager() {}

    // ========================================================================
    // Capture Operations
    // ========================================================================

    /**
     * Starts capturing an area snapshot.
     *
     * @param level       The server level
     * @param area        The area to capture
     * @param description User description for the snapshot
     * @param player      The player initiating capture (for feedback)
     * @return The snapshot UUID, or null if capture couldn't start
     */
    @Nullable
    public UUID startCapture(
        @Nonnull ServerLevel level,
        @Nonnull AreaDefinition area,
        @Nonnull String description,
        @Nullable ServerPlayer player
    ) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(area);
        Objects.requireNonNull(description);

        // Check if already capturing this area
        if (activeCaptures.containsKey(area.id())) {
            LOGGER.warn("[Snapshot] Capture already in progress for area: {}", area.id());
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.capture_in_progress")), true);
                AreaBuilderSounds.playError(level, player);
            }
            return null;
        }

        // Check concurrent limit
        if (activeCaptures.size() >= MAX_CONCURRENT_CAPTURES) {
            LOGGER.warn("[Snapshot] Maximum concurrent captures reached");
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.capture_busy")), true);
                AreaBuilderSounds.playError(level, player);
            }
            return null;
        }

        UUID snapshotId = Objects.requireNonNull(UUID.randomUUID());

        AreaSnapshotCaptureTask task = new AreaSnapshotCaptureTask(
            level,
            area,
            snapshotId,
            description,
            player,
            this::onCaptureComplete
        );

        activeCaptures.put(area.id(), new ActiveCapture(
            task,
            player != null ? player.getUUID() : null,
            0
        ));

        if (player != null) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("area.snapshot.capture_started")), true);
        }

        LOGGER.info("[Snapshot] Started capture for area '{}' (snapshot {})",
            area.name(), snapshotId);
        return snapshotId;
    }

    /**
     * Called when a capture task completes.
     */
    private void onCaptureComplete(@Nonnull AreaSnapshotCaptureTask.CaptureResult result) {
        activeCaptures.remove(result.areaId());

        // Get server for file operations
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.error("[Snapshot] Cannot save snapshot - server is null");
            return;
        }

        // Write snapshot file
        Path worldFolder = server.getWorldPath(Objects.requireNonNull(LevelResource.ROOT));
        AreaSnapshot snapshot = new AreaSnapshot(
            result.snapshotId(),
            result.areaId(),
            result.areaName(),
            result.description(),
            result.creatorId(),
            result.creatorName(),
            System.currentTimeMillis(),
            result.getBlockCount(),
            0L, // Will update after writing
            result.dimensions(),
            result.center(),
            result.shape(),
            result.dimensionKey(),
            result.customShapeNbt()
        );

        Path snapshotFile = Objects.requireNonNull(worldFolder.resolve(snapshot.getFilePath()));

        try {
            AreaSnapshotData.write(
                snapshotFile,
                result.areaId(),
                result.blocks(),
                result.blockEntities(),
                Objects.requireNonNull(server.registryAccess())
            );

            // Update with actual file size
            long fileSize = AreaSnapshotData.getFileSize(snapshotFile);
            snapshot = snapshot.withFileSize(fileSize);

            // Register in registry
            AreaSnapshotRegistry registry = AreaSnapshotRegistry.get(server);
            registry.registerSnapshot(snapshot);

            LOGGER.info("[Snapshot] Saved snapshot {} ({} blocks, {})",
                snapshot.id(), snapshot.blockCount(), snapshot.getFormattedFileSize());

            // Notify player
            @Nullable UUID creatorId = result.creatorId();
            if (creatorId != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(creatorId);
                if (player != null) {
                    player.displayClientMessage(
                        Objects.requireNonNull(Component.translatable("area.snapshot.capture_complete", result.getBlockCount())), false);
                }
            }

        } catch (IOException e) {
            LOGGER.error("[Snapshot] Failed to write snapshot file: {}", e.getMessage());

            // Notify player of failure
            @Nullable UUID failedCreatorId = result.creatorId();
            if (failedCreatorId != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(failedCreatorId);
                if (player != null) {
                    player.displayClientMessage(
                        Objects.requireNonNull(Component.translatable("area.snapshot.capture_failed")), false);
                }
            }
        }
    }

    // ========================================================================
    // Restore Operations
    // ========================================================================

    /**
     * Starts restoring from a snapshot.
     *
     * <p>The snapshot file is read on the IO pool, so this only reports whether the request
     * passed validation and was accepted. A read failure surfaces afterwards through the
     * initiating player's feedback message and the log, the same channel every other restore
     * failure already uses; callers that need to observe the pending window can poll
     * {@link #isRestoring(UUID)}, which covers the read as well as the placement task.
     *
     * @param level      The server level
     * @param snapshotId The snapshot to restore
     * @param player     The player initiating restore (for feedback)
     * @return true if the restore request was accepted
     */
    public boolean startRestore(
        @Nonnull ServerLevel level,
        @Nonnull UUID snapshotId,
        @Nullable ServerPlayer player
    ) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(snapshotId);

        // Check if already restoring this snapshot
        if (isRestoring(snapshotId)) {
            LOGGER.warn("[Snapshot] Restore already in progress for snapshot: {}", snapshotId);
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.restore_in_progress")), true);
                AreaBuilderSounds.playError(level, player);
            }
            return false;
        }

        // Check concurrent limit
        if (activeRestores.size() + pendingRestores.size() >= MAX_CONCURRENT_RESTORES) {
            LOGGER.warn("[Snapshot] Maximum concurrent restores reached");
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.restore_busy")), true);
                AreaBuilderSounds.playError(level, player);
            }
            return false;
        }

        MinecraftServer server = level.getServer();
        AreaSnapshotRegistry registry = AreaSnapshotRegistry.get(server);

        // Get snapshot metadata
        AreaSnapshot snapshot = registry.getSnapshot(snapshotId).orElse(null);
        if (snapshot == null) {
            LOGGER.warn("[Snapshot] Snapshot not found: {}", snapshotId);
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.not_found")), true);
                AreaBuilderSounds.playError(level, player);
            }
            return false;
        }

        // HIGH-05 fix: Verify dimension matches where snapshot was captured
        if (!level.dimension().equals(snapshot.dimensionKey())) {
            LOGGER.warn("[Snapshot] Dimension mismatch: snapshot from {} but restoring to {}",
                snapshot.dimensionKey().location(), level.dimension().location());
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.dimension_mismatch",
                        snapshot.dimensionKey().location().toString())), true);
                AreaBuilderSounds.playError(level, player);
            }
            return false;
        }

        // MED-RESTORE fix: Check if there's an active build on this area
        // Restoring while building would cause block conflicts and inconsistent state
        UUID areaId = snapshot.areaId();
        if (AreaBuildTaskManager.INSTANCE.isBuildingArea(areaId)) {
            LOGGER.warn("[Snapshot] Cannot restore snapshot {} - build in progress for area {}",
                snapshotId, areaId);
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.build_in_progress")), true);
                AreaBuilderSounds.playError(level, player);
            }
            return false;
        }

        // SEC-08 fix: Block restore if a build is queued for this area
        if (AreaBuildTaskManager.INSTANCE.isAreaQueued(Objects.requireNonNull(areaId))) {
            LOGGER.warn("[Snapshot] Cannot restore snapshot {} - build queued for area {}",
                snapshotId, areaId);
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.build_in_progress")), true);
                AreaBuilderSounds.playError(level, player);
            }
            return false;
        }

        // SEC-04 fix: Check if there's already a restore in progress for this area
        // Multiple concurrent restores on the same area would cause block placement conflicts
        if (isRestoringArea(Objects.requireNonNull(areaId))) {
            LOGGER.warn("[Snapshot] Cannot restore snapshot {} - another restore already in progress for area {}",
                snapshotId, areaId);
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.restore_in_progress")), true);
                AreaBuilderSounds.playError(level, player);
            }
            return false;
        }

        // Load snapshot data off-thread: NbtIo.readCompressed on a max-size snapshot blocks
        // for far too long to run inside a network handler or command on the tick thread.
        Path worldFolder = server.getWorldPath(Objects.requireNonNull(LevelResource.ROOT));
        Path snapshotFile = Objects.requireNonNull(worldFolder.resolve(snapshot.getFilePath()));
        HolderLookup.Provider registries = Objects.requireNonNull(server.registryAccess());

        PendingRestore pending = new PendingRestore(
            level,
            Objects.requireNonNull(areaId),
            player != null ? player.getUUID() : null
        );
        pendingRestores.put(snapshotId, pending);

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return AreaSnapshotData.read(snapshotFile, registries);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, Util.ioPool())
            .orTimeout(MAX_SNAPSHOT_READ_MS, TimeUnit.MILLISECONDS)
            .whenComplete((data, throwable) -> server.execute(
                () -> onSnapshotRead(server, snapshot, data, throwable)));

        if (player != null) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("area.snapshot.restore_started")), true);
        }

        return true;
    }

    /**
     * Turns a completed off-thread snapshot read into a restore task, on the server thread.
     */
    private void onSnapshotRead(
        @Nonnull MinecraftServer server,
        @Nonnull AreaSnapshot snapshot,
        @Nullable AreaSnapshotData.SnapshotReadResult data,
        @Nullable Throwable throwable
    ) {
        UUID snapshotId = Objects.requireNonNull(snapshot.id());
        PendingRestore pending = pendingRestores.remove(snapshotId);
        if (pending == null) {
            // Cancelled while the read was in flight (player left, area deleted, level unloaded).
            return;
        }

        if (throwable != null || data == null) {
            LOGGER.error("[Snapshot] Failed to read snapshot file for {}: {}",
                snapshotId, throwable != null ? throwable.getMessage() : "empty result");
            @Nullable UUID playerId = pending.playerId;
            if (playerId != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    player.displayClientMessage(
                        Objects.requireNonNull(Component.translatable("area.snapshot.restore_failed")), true);
                    AreaBuilderSounds.playError(pending.level, player);
                }
            }
            return;
        }

        AreaSnapshotRestoreTask task = new AreaSnapshotRestoreTask(
            pending.level,
            snapshot,
            data,
            this::onRestoreComplete
        );

        activeRestores.put(snapshotId, new ActiveRestore(
            task,
            pending.level,
            pending.areaId,
            pending.playerId,
            0
        ));

        LOGGER.info("[Snapshot] Started restore for snapshot {} ({} blocks)",
            snapshotId, data.getBlockCount());
    }

    /**
     * Called when a restore task completes.
     * LOW-01 fix: Only log here - tickRestores handles removal and player notification
     * to avoid duplicate notifications.
     */
    private void onRestoreComplete(@Nonnull AreaSnapshotRestoreTask.RestoreResult result) {
        // LOW-01 fix: Only log here - don't remove or notify
        // The tickRestores method handles entry removal and player notification
        // This callback is primarily for logging and any future extension points
        LOGGER.info("[Snapshot] Restore complete for snapshot {}: {} blocks",
            result.snapshotId(), result.blocksRestored());
    }

    // ========================================================================
    // Tick Processing
    // ========================================================================

    /**
     * Ticks all active capture/restore tasks.
     * Called from server tick event.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        INSTANCE.tick(event.getServer());
    }

    /**
     * Processes one tick of all active tasks.
     */
    public void tick(@Nullable MinecraftServer server) {
        if (server == null) {
            return;
        }

        // Skip if nothing to do
        if (activeCaptures.isEmpty() && activeRestores.isEmpty()) {
            return;
        }

        // Tick capture tasks
        tickCaptures(server);

        // Tick restore tasks
        tickRestores(server);
    }

    private void tickCaptures(@Nonnull MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveCapture>> iterator = activeCaptures.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveCapture> entry = iterator.next();
            ActiveCapture capture = entry.getValue();

            try {
                boolean completed = capture.task.tick();

                // Send progress feedback
                @Nullable UUID capturePlayerId = capture.playerId;
                if (!completed && capturePlayerId != null) {
                    int progress = capture.task.getProgressPercent();
                    if (progress > capture.lastProgress && progress % 25 == 0) {
                        ServerPlayer player = server.getPlayerList().getPlayer(capturePlayerId);
                        if (player != null) {
                            player.displayClientMessage(
                                Objects.requireNonNull(Component.translatable("area.snapshot.progress", progress)), true);
                        }
                        capture.lastProgress = progress;
                    }
                }

                if (completed) {
                    iterator.remove();
                }
            } catch (Exception e) {
                LOGGER.error("[Snapshot] Error ticking capture task for area {}: {}",
                    entry.getKey(), e.getMessage());
                iterator.remove();
            }
        }
    }

    private void tickRestores(@Nonnull MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveRestore>> iterator = activeRestores.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveRestore> entry = iterator.next();
            ActiveRestore restore = entry.getValue();

            try {
                boolean completed = restore.task.tick();

                // Send progress feedback
                @Nullable UUID restorePlayerId = restore.playerId;
                if (!completed && restorePlayerId != null) {
                    int progress = restore.task.getProgressPercent();
                    if (progress > restore.lastProgress && progress % 25 == 0) {
                        ServerPlayer player = server.getPlayerList().getPlayer(restorePlayerId);
                        if (player != null) {
                            player.displayClientMessage(
                                Objects.requireNonNull(Component.translatable("area.snapshot.progress", progress)), true);
                        }
                        restore.lastProgress = progress;
                    }
                }

                if (completed) {
                    iterator.remove();

                    // Notify player of completion
                    @Nullable UUID completedPlayerId = restore.playerId;
                    if (completedPlayerId != null) {
                        ServerPlayer player = server.getPlayerList().getPlayer(completedPlayerId);
                        if (player != null) {
                            player.displayClientMessage(
                                Objects.requireNonNull(Component.translatable("area.snapshot.restore_complete",
                                    restore.task.getRestoredCount())), false);
                            AreaBuilderSounds.playBuildComplete(restore.level, player);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[Snapshot] Error ticking restore task for snapshot {}: {}",
                    entry.getKey(), e.getMessage());
                iterator.remove();
            }
        }
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    /**
     * Checks if a capture is in progress for an area.
     */
    public boolean isCapturing(@Nonnull UUID areaId) {
        return activeCaptures.containsKey(areaId);
    }

    /**
     * Checks if a restore is in progress for a snapshot, including the file read that
     * precedes the placement task.
     */
    public boolean isRestoring(@Nonnull UUID snapshotId) {
        return activeRestores.containsKey(snapshotId) || pendingRestores.containsKey(snapshotId);
    }

    /**
     * SEC-04 fix: Checks if any restore is in progress for the given area.
     * This prevents concurrent restores of different snapshots for the same area.
     *
     * @param areaId The area ID to check
     * @return true if a restore is in progress for this area
     */
    public boolean isRestoringArea(@Nonnull UUID areaId) {
        Objects.requireNonNull(areaId);
        for (ActiveRestore restore : activeRestores.values()) {
            if (areaId.equals(restore.areaId)) {
                return true;
            }
        }
        for (PendingRestore pending : pendingRestores.values()) {
            if (areaId.equals(pending.areaId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets capture progress for an area (0-100), or -1 if not capturing.
     */
    public int getCaptureProgress(@Nonnull UUID areaId) {
        ActiveCapture capture = activeCaptures.get(areaId);
        return capture != null ? capture.task.getProgressPercent() : -1;
    }

    /**
     * Gets restore progress for a snapshot (0-100), or -1 if not restoring.
     */
    public int getRestoreProgress(@Nonnull UUID snapshotId) {
        ActiveRestore restore = activeRestores.get(snapshotId);
        return restore != null ? restore.task.getProgressPercent() : -1;
    }

    /**
     * Gets the number of active capture tasks.
     */
    public int getActiveCaptureCount() {
        return activeCaptures.size();
    }

    /**
     * Gets the number of active restore tasks.
     */
    public int getActiveRestoreCount() {
        return activeRestores.size();
    }

    /**
     * Cancels all active tasks initiated by a specific player.
     * Called on player disconnect.
     */
    public void cancelPlayerTasks(@Nullable UUID playerId) {
        if (playerId == null) {
            return;
        }

        activeCaptures.entrySet().removeIf(entry -> {
            if (playerId.equals(entry.getValue().playerId)) {
                LOGGER.info("[Snapshot] Cancelled capture for disconnected player: {}", playerId);
                return true;
            }
            return false;
        });

        activeRestores.entrySet().removeIf(entry -> {
            if (playerId.equals(entry.getValue().playerId)) {
                LOGGER.info("[Snapshot] Cancelled restore for disconnected player: {}", playerId);
                return true;
            }
            return false;
        });

        pendingRestores.entrySet().removeIf(entry -> playerId.equals(entry.getValue().playerId));
    }

    /**
     * Cancels the capture and any restores targeting an area.
     *
     * <p>Called when the area is deleted: a capture allowed to finish afterwards writes an
     * orphan .nbt file and registry entry that the cascade delete has already run past.
     *
     * @param areaId The area being deleted
     * @return Number of tasks cancelled
     */
    public int cancelTasksForArea(@Nullable UUID areaId) {
        if (areaId == null) {
            return 0;
        }
        int cancelled = activeCaptures.remove(areaId) != null ? 1 : 0;
        // activeRestores is keyed by snapshot id, so match on the tracked areaId instead.
        int before = activeRestores.size() + pendingRestores.size();
        activeRestores.entrySet().removeIf(entry -> areaId.equals(entry.getValue().areaId));
        pendingRestores.entrySet().removeIf(entry -> areaId.equals(entry.getValue().areaId));
        cancelled += before - activeRestores.size() - pendingRestores.size();
        if (cancelled > 0) {
            LOGGER.info("[Snapshot] Cancelled {} tasks for deleted area {}", cancelled, areaId);
        }
        return cancelled;
    }

    /**
     * Cancels every task bound to a level that is being unloaded.
     *
     * <p>Both task types hold a hard ServerLevel reference, so a task left running past the
     * unload keeps reading/writing a discarded level and pins it.
     *
     * @param level The level being unloaded
     * @return Number of tasks cancelled
     */
    public int cancelTasksForLevel(@Nonnull ServerLevel level) {
        Objects.requireNonNull(level);
        ResourceKey<Level> dimension = level.dimension();
        int before = activeCaptures.size() + activeRestores.size() + pendingRestores.size();
        activeCaptures.entrySet().removeIf(entry -> dimension.equals(entry.getValue().task.getDimension()));
        activeRestores.entrySet().removeIf(entry -> dimension.equals(entry.getValue().level.dimension()));
        pendingRestores.entrySet().removeIf(entry -> dimension.equals(entry.getValue().level.dimension()));
        return before - activeCaptures.size() - activeRestores.size() - pendingRestores.size();
    }

    /**
     * Clears all active tasks (used on server shutdown).
     */
    public void cleanup() {
        int captureCount = activeCaptures.size();
        int restoreCount = activeRestores.size() + pendingRestores.size();
        activeCaptures.clear();
        activeRestores.clear();
        pendingRestores.clear();
        if (captureCount > 0 || restoreCount > 0) {
            LOGGER.info("[Snapshot] Cleaned up {} capture and {} restore tasks",
                captureCount, restoreCount);
        }
    }

    // ========================================================================
    // Internal State Classes
    // ========================================================================

    private static class ActiveCapture {
        final @Nonnull AreaSnapshotCaptureTask task;
        final @Nullable UUID playerId;
        volatile int lastProgress;

        ActiveCapture(@Nonnull AreaSnapshotCaptureTask task,
                      @Nullable UUID playerId, int lastProgress) {
            this.task = Objects.requireNonNull(task);
            this.playerId = playerId;
            this.lastProgress = lastProgress;
        }
    }

    /** A restore that has passed validation and is waiting on its snapshot file read. */
    private static class PendingRestore {
        final @Nonnull ServerLevel level;
        final @Nonnull UUID areaId;
        final @Nullable UUID playerId;

        PendingRestore(@Nonnull ServerLevel level, @Nonnull UUID areaId, @Nullable UUID playerId) {
            this.level = Objects.requireNonNull(level);
            this.areaId = Objects.requireNonNull(areaId);
            this.playerId = playerId;
        }
    }

    private static class ActiveRestore {
        final @Nonnull AreaSnapshotRestoreTask task;
        final @Nonnull ServerLevel level;
        final @Nonnull UUID areaId;  // SEC-04 fix: Track areaId to prevent concurrent restores on same area
        final @Nullable UUID playerId;
        volatile int lastProgress;

        ActiveRestore(@Nonnull AreaSnapshotRestoreTask task, @Nonnull ServerLevel level,
                      @Nonnull UUID areaId, @Nullable UUID playerId, int lastProgress) {
            this.task = Objects.requireNonNull(task);
            this.level = Objects.requireNonNull(level);
            this.areaId = Objects.requireNonNull(areaId);
            this.playerId = playerId;
            this.lastProgress = lastProgress;
        }
    }
}
