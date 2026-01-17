package com.devmod.area.snapshot;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.area.builder.AreaBlockMapGenerator;
import com.devmod.area.builder.AreaShapeGenerator;

/**
 * Multi-tick task for restoring block states from a snapshot.
 * Similar to AreaBuildTask but reads from snapshot data.
 * Spreads block placement over multiple ticks to avoid server lag.
 */
public class AreaSnapshotRestoreTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaSnapshotRestoreTask.class);

    /** Blocks to restore per tick - same as AreaBuildTask */
    public static final int DEFAULT_BLOCKS_PER_TICK = 500;

    /**
     * Block update flag for restoration.
     * Uses UPDATE_CLIENTS (2) to sync to clients without triggering expensive neighbor updates.
     * Same as AreaBuildTask for consistency.
     */
    private static final int RESTORE_FLAGS = Block.UPDATE_CLIENTS;

    /**
     * HIGH-03 fix: Restore phases for true rollback behavior.
     */
    private enum Phase {
        /** Clear all blocks in the area to air */
        CLEAR,
        /** Restore blocks from snapshot */
        RESTORE,
        /** Task completed */
        COMPLETE
    }

    private final ServerLevel level;
    private final AreaSnapshot snapshot;
    private final Consumer<RestoreResult> onComplete;

    private final Iterator<Map.Entry<BlockPos, BlockState>> blockIterator;
    @Nullable
    private final Map<BlockPos, CompoundTag> blockEntities;
    private final int totalBlocks;
    private int restoredCount = 0;
    private boolean completed = false;

    // HIGH-03 fix: Clear phase fields
    private Phase currentPhase = Phase.CLEAR;
    private final List<BlockPos> clearPositions;
    private int clearIndex = 0;

    /**
     * Creates a restore task from loaded snapshot data.
     *
     * @param level      The server level to restore to
     * @param snapshot   The snapshot metadata
     * @param data       The loaded snapshot block data
     * @param onComplete Callback when restore completes
     */
    public AreaSnapshotRestoreTask(
        @Nonnull ServerLevel level,
        @Nonnull AreaSnapshot snapshot,
        @Nonnull AreaSnapshotData.SnapshotReadResult data,
        @Nonnull Consumer<RestoreResult> onComplete
    ) {
        this.level = Objects.requireNonNull(level);
        this.snapshot = Objects.requireNonNull(snapshot);
        this.onComplete = Objects.requireNonNull(onComplete);
        Objects.requireNonNull(data);

        this.blockIterator = data.blocks().entrySet().iterator();
        this.blockEntities = data.blockEntities();
        this.totalBlocks = data.blocks().size();

        // HIGH-04 fix: Generate all positions in the area volume to clear
        // Use the snapshot's actual shape for correct restore behavior
        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
            Objects.requireNonNull(snapshot.shape()),
            Objects.requireNonNull(snapshot.center()),
            Objects.requireNonNull(snapshot.dimensions()),
            snapshot.customShapeNbt()
        );

        // Expand floor to full volume (all Y levels)
        java.util.List<BlockPos> volumePositions = new java.util.ArrayList<>();
        int height = snapshot.dimensions().height();
        for (BlockPos floorPos : floorPositions) {
            for (int y = 0; y <= height; y++) {
                volumePositions.add(floorPos.above(y));
            }
        }

        // Sort for deterministic ordering
        this.clearPositions = AreaBlockMapGenerator.sortPositionsDeterministically(
            new java.util.HashSet<>(volumePositions));

        LOGGER.info("[Snapshot] Starting restore for snapshot {} ({} blocks to restore, {} positions to clear)",
            snapshot.id(), totalBlocks, clearPositions.size());
    }

    /**
     * Processes one tick of restoration.
     * HIGH-03 fix: Two-phase restore - first clear the area, then restore blocks.
     *
     * @return true if restore is complete
     */
    public boolean tick() {
        if (completed) {
            return true;
        }

        switch (currentPhase) {
            case CLEAR -> tickClearPhase();
            case RESTORE -> tickRestorePhase();
            case COMPLETE -> { /* Nothing to do */ }
        }

        return completed;
    }

    /**
     * HIGH-03 fix: Clear phase - set all positions in the area to air.
     */
    private void tickClearPhase() {
        int clearedThisTick = 0;
        BlockState air = Objects.requireNonNull(Blocks.AIR.defaultBlockState());

        while (clearedThisTick < DEFAULT_BLOCKS_PER_TICK && clearIndex < clearPositions.size()) {
            BlockPos pos = Objects.requireNonNull(clearPositions.get(clearIndex));
            level.setBlock(pos, air, RESTORE_FLAGS);
            clearIndex++;
            clearedThisTick++;
        }

        // Check if clear phase is complete
        if (clearIndex >= clearPositions.size()) {
            LOGGER.debug("[Snapshot] Clear phase complete: {} positions cleared", clearIndex);
            currentPhase = Phase.RESTORE;
        }
    }

    /**
     * Restore phase - place saved blocks from snapshot.
     */
    private void tickRestorePhase() {
        int restoredThisTick = 0;
        while (restoredThisTick < DEFAULT_BLOCKS_PER_TICK && blockIterator.hasNext()) {
            Map.Entry<BlockPos, BlockState> entry = blockIterator.next();
            BlockPos pos = Objects.requireNonNull(entry.getKey());
            BlockState state = Objects.requireNonNull(entry.getValue());

            // Place the block
            level.setBlock(pos, state, RESTORE_FLAGS);

            // Restore block entity data if present
            if (blockEntities != null) {
                CompoundTag beData = blockEntities.get(pos);
                if (beData != null) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be != null) {
                        try {
                            be.loadWithComponents(beData, Objects.requireNonNull(level.registryAccess()));
                            be.setChanged();
                        } catch (Exception e) {
                            LOGGER.warn("[Snapshot] Failed to restore block entity at {}: {}",
                                pos, e.getMessage());
                        }
                    }
                }
            }

            restoredCount++;
            restoredThisTick++;
        }

        // Check if restore phase is complete
        if (!blockIterator.hasNext()) {
            completed = true;
            currentPhase = Phase.COMPLETE;
            LOGGER.info("[Snapshot] Restore complete for snapshot {}: {} blocks restored",
                snapshot.id(), restoredCount);

            onComplete.accept(new RestoreResult(
                snapshot.id(),
                snapshot.areaId(),
                restoredCount,
                true,
                null
            ));
        }
    }

    /**
     * Gets the current progress as a percentage (0.0 to 1.0).
     */
    public float getProgress() {
        if (completed) {
            return 1.0f;
        }
        return totalBlocks > 0 ? (float) restoredCount / totalBlocks : 0f;
    }

    /**
     * Gets the current progress as a percentage (0 to 100).
     */
    public int getProgressPercent() {
        return (int) (getProgress() * 100);
    }

    /**
     * Gets the number of blocks restored so far.
     */
    public int getRestoredCount() {
        return restoredCount;
    }

    /**
     * Gets the total number of blocks to restore.
     */
    public int getTotalBlocks() {
        return totalBlocks;
    }

    /**
     * Checks if the restore is complete.
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Gets the snapshot being restored.
     */
    @Nonnull
    public AreaSnapshot getSnapshot() {
        return Objects.requireNonNull(snapshot);
    }

    /**
     * Gets the snapshot ID.
     */
    @Nonnull
    public UUID getSnapshotId() {
        return Objects.requireNonNull(snapshot.id());
    }

    /**
     * Gets the area ID being restored.
     */
    @Nonnull
    public UUID getAreaId() {
        return Objects.requireNonNull(snapshot.areaId());
    }

    /**
     * Result of a completed restore task.
     */
    public record RestoreResult(
        @Nonnull UUID snapshotId,
        @Nonnull UUID areaId,
        int blocksRestored,
        boolean success,
        @Nullable String errorMessage
    ) {
        public RestoreResult {
            Objects.requireNonNull(snapshotId);
            Objects.requireNonNull(areaId);
        }

        /**
         * Creates a failed result.
         */
        @Nonnull
        public static RestoreResult failed(@Nonnull UUID snapshotId, @Nonnull UUID areaId, @Nonnull String error) {
            return new RestoreResult(snapshotId, areaId, 0, false, error);
        }
    }
}
