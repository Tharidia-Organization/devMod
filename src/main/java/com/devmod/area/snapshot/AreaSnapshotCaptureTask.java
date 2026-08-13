package com.devmod.area.snapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.area.builder.AreaShapeGenerator;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaShape;

/**
 * Multi-tick task for capturing block states from an area.
 * Spreads block reading over multiple ticks to avoid server lag.
 * Captures all non-air blocks within the area volume, plus block entity data.
 */
public class AreaSnapshotCaptureTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaSnapshotCaptureTask.class);

    /** Blocks to capture per tick - higher than build since reading is faster than writing */
    public static final int DEFAULT_BLOCKS_PER_TICK = 1000;

    private final ServerLevel level;
    private final AreaDefinition area;
    private final UUID snapshotId;
    private final String description;
    @Nullable
    private final UUID creatorId;
    @Nullable
    private final String creatorName;
    private final Consumer<CaptureResult> onComplete;

    private final Map<BlockPos, BlockState> capturedBlocks = new HashMap<>();
    private final Map<BlockPos, CompoundTag> capturedBlockEntities = new HashMap<>();

    private final Iterator<BlockPos> positionIterator;
    private final int totalPositions;
    private int processedCount = 0;
    private boolean completed = false;

    /**
     * Creates a capture task.
     *
     * @param level       The server level to capture from
     * @param area        The area definition
     * @param snapshotId  UUID for the new snapshot
     * @param description User description for the snapshot
     * @param creator     The player creating the snapshot (may be null)
     * @param onComplete  Callback when capture completes
     */
    public AreaSnapshotCaptureTask(
        @Nonnull ServerLevel level,
        @Nonnull AreaDefinition area,
        @Nonnull UUID snapshotId,
        @Nonnull String description,
        @Nullable ServerPlayer creator,
        @Nonnull Consumer<CaptureResult> onComplete
    ) {
        this.level = Objects.requireNonNull(level);
        this.area = Objects.requireNonNull(area);
        this.snapshotId = Objects.requireNonNull(snapshotId);
        this.description = Objects.requireNonNull(description);
        this.creatorId = creator != null ? creator.getUUID() : null;
        this.creatorName = creator != null ? creator.getName().getString() : null;
        this.onComplete = Objects.requireNonNull(onComplete);

        // Generate all positions within the area volume
        Set<BlockPos> positions = generateAreaVolume(area);
        this.totalPositions = positions.size();
        this.positionIterator = positions.iterator();

        LOGGER.info("[Snapshot] Starting capture for area '{}' ({} positions)",
            area.name(), totalPositions);
    }

    /**
     * Generates all positions within the area's 3D volume.
     * For each floor position, extends upward through the full height.
     */
    @Nonnull
    private static Set<BlockPos> generateAreaVolume(@Nonnull AreaDefinition area) {
        AreaShape shape = area.shape();
        BlockPos center = area.centerPosition();
        AreaDimensions dims = area.dimensions();

        // Get floor positions
        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
            Objects.requireNonNull(shape), Objects.requireNonNull(center),
            Objects.requireNonNull(dims), area.customShapeNbt()
        );

        // Expand to full volume (floor + height)
        Set<BlockPos> volume = new HashSet<>(floorPositions.size() * dims.height());
        for (BlockPos floorPos : floorPositions) {
            for (int y = 0; y <= dims.height(); y++) {
                volume.add(floorPos.above(y));
            }
        }

        return volume;
    }

    /**
     * Processes one tick of capturing.
     *
     * @return true if capture is complete
     */
    public boolean tick() {
        if (completed) {
            return true;
        }

        int capturedThisTick = 0;
        while (capturedThisTick < DEFAULT_BLOCKS_PER_TICK && positionIterator.hasNext()) {
            BlockPos pos = Objects.requireNonNull(positionIterator.next());
            BlockState state = level.getBlockState(pos);

            // Skip air blocks to save space
            if (!state.isAir()) {
                capturedBlocks.put(pos.immutable(), state);

                // Capture block entity data if present
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    try {
                        CompoundTag beData = be.saveWithoutMetadata(Objects.requireNonNull(level.registryAccess()));
                        capturedBlockEntities.put(pos.immutable(), beData);
                    } catch (Exception e) {
                        LOGGER.warn("[Snapshot] Failed to save block entity at {}: {}",
                            pos, e.getMessage());
                    }
                }
            }

            processedCount++;
            capturedThisTick++;
        }

        // Check if complete
        if (!positionIterator.hasNext()) {
            completed = true;
            LOGGER.info("[Snapshot] Capture complete for area '{}': {} blocks, {} block entities",
                area.name(), capturedBlocks.size(), capturedBlockEntities.size());

            onComplete.accept(new CaptureResult(
                snapshotId,
                area.id(),
                area.name(),
                description,
                creatorId,
                creatorName,
                capturedBlocks,
                capturedBlockEntities,
                area.dimensions(),
                area.centerPosition(),
                area.shape(),
                level.dimension(),
                area.customShapeNbt()
            ));
        }

        return completed;
    }

    /**
     * Gets the current progress as a percentage (0.0 to 1.0).
     */
    public float getProgress() {
        if (completed) {
            return 1.0f;
        }
        return totalPositions > 0 ? (float) processedCount / totalPositions : 0f;
    }

    /**
     * Gets the current progress as a percentage (0 to 100).
     */
    public int getProgressPercent() {
        return (int) (getProgress() * 100);
    }

    /**
     * Gets the number of positions processed so far.
     */
    public int getProcessedCount() {
        return processedCount;
    }

    /**
     * Gets the total number of positions to process.
     */
    public int getTotalPositions() {
        return totalPositions;
    }

    /**
     * Gets the number of non-air blocks captured so far.
     */
    public int getCapturedBlockCount() {
        return capturedBlocks.size();
    }

    /**
     * Checks if the capture is complete.
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Gets the snapshot ID.
     */
    @Nonnull
    public UUID getSnapshotId() {
        return Objects.requireNonNull(snapshotId);
    }

    /**
     * Gets the dimension this task is capturing from.
     */
    @Nonnull
    public ResourceKey<Level> getDimension() {
        return Objects.requireNonNull(level.dimension());
    }

    /**
     * Gets the area ID being captured.
     */
    @Nonnull
    public UUID getAreaId() {
        return Objects.requireNonNull(area.id());
    }

    /**
     * Gets the area name.
     */
    @Nonnull
    public String getAreaName() {
        return Objects.requireNonNull(area.name());
    }

    /**
     * Result of a completed capture task.
     */
    public record CaptureResult(
        @Nonnull UUID snapshotId,
        @Nonnull UUID areaId,
        @Nonnull String areaName,
        @Nonnull String description,
        @Nullable UUID creatorId,
        @Nullable String creatorName,
        @Nonnull Map<BlockPos, BlockState> blocks,
        @Nonnull Map<BlockPos, CompoundTag> blockEntities,
        @Nonnull AreaDimensions dimensions,
        @Nonnull BlockPos center,
        @Nonnull AreaShape shape,
        @Nonnull ResourceKey<Level> dimensionKey,
        @Nullable CompoundTag customShapeNbt
    ) {
        public CaptureResult {
            Objects.requireNonNull(snapshotId);
            Objects.requireNonNull(areaId);
            Objects.requireNonNull(areaName);
            Objects.requireNonNull(description);
            Objects.requireNonNull(blocks);
            Objects.requireNonNull(blockEntities);
            Objects.requireNonNull(dimensions);
            Objects.requireNonNull(center);
            Objects.requireNonNull(shape);
            Objects.requireNonNull(dimensionKey);
        }

        /**
         * Gets the total number of captured blocks.
         */
        public int getBlockCount() {
            return blocks.size();
        }

        /**
         * Gets the number of block entities.
         */
        public int getBlockEntityCount() {
            return blockEntities.size();
        }
    }
}
