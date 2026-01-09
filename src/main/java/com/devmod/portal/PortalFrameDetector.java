package com.devmod.portal;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.DevMod;
/**
 * Detects valid portal frames using a DFS-based algorithm.
 * Supports any block type as frame material (configurable).
 *
 * <p>A valid frame must be:
 * <ul>
 *   <li>Rectangular (no irregular shapes)</li>
 *   <li>Between 3x3 and 23x23 in size</li>
 *   <li>Completely enclosed (no gaps)</li>
 *   <li>Have a hollow interior</li>
 * </ul>
 */
public class PortalFrameDetector {
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 23;

    private final BiPredicate<BlockGetter, BlockPos> isFrameBlock;

    /**
     * Creates a detector that accepts any solid block as frame material.
     */
    public PortalFrameDetector() {
        this((level, pos) -> level.getBlockState(pos).isCollisionShapeFullBlock(level, pos));
    }

    /**
     * Creates a detector with a custom frame block predicate.
     */
    public PortalFrameDetector(@Nonnull BiPredicate<BlockGetter, BlockPos> isFrameBlock) {
        this.isFrameBlock = Objects.requireNonNull(isFrameBlock, "isFrameBlock");
    }

    /**
     * Attempts to detect a valid portal frame from the given interior position.
     *
     * @param level The world to check
     * @param interiorPos A position that should be inside the portal frame
     * @param axis The axis the portal should face (X or Z)
     * @return The detected frame, or empty if no valid frame found
     */
    @Nonnull
    public Optional<FrameResult> detectFrame(@Nonnull BlockGetter level, @Nonnull BlockPos interiorPos, @Nonnull Direction.Axis axis) {
        if (axis == Direction.Axis.Y) {
            return Optional.empty(); // Only support horizontal portals
        }

        // Determine scan directions based on axis
        Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction heightDir = Direction.UP;

        // Find frame boundaries by scanning outward
        int minWidth = findFrameEdge(level, interiorPos, widthDir.getOpposite());
        int maxWidth = findFrameEdge(level, interiorPos, widthDir);
        int minHeight = findFrameEdge(level, interiorPos, heightDir.getOpposite());
        int maxHeight = findFrameEdge(level, interiorPos, heightDir);

        DevMod.LOGGER.debug("[FrameDetector] axis={} edges: minW={} maxW={} minH={} maxH={}",
            axis, minWidth, maxWidth, minHeight, maxHeight);

        if ((minWidth == 0 && maxWidth == 0) || (minHeight == 0 && maxHeight == 0)) {
            DevMod.LOGGER.debug("[FrameDetector] FAIL: no frame edges found (width or height both 0)");
            return Optional.empty(); // No frame found
        }

        int width = minWidth + maxWidth + 1;
        int height = minHeight + maxHeight + 1;

        DevMod.LOGGER.debug("[FrameDetector] interior size: {}x{}", width, height);

        // Validate size
        if (width < MIN_SIZE || width > MAX_SIZE || height < MIN_SIZE || height > MAX_SIZE) {
            DevMod.LOGGER.debug("[FrameDetector] FAIL: size out of range (min={}, max={})", MIN_SIZE, MAX_SIZE);
            return Optional.empty();
        }

        // Calculate frame corner positions
        BlockPos bottomLeft = interiorPos
            .relative(widthDir.getOpposite(), minWidth)
            .relative(heightDir.getOpposite(), minHeight);

        // Validate frame completeness
        if (!validateFrame(level, bottomLeft, width, height, axis)) {
            DevMod.LOGGER.debug("[FrameDetector] FAIL: frame validation failed at bottomLeft={}", bottomLeft);
            return Optional.empty();
        }

        // Calculate interior positions
        Set<BlockPos> interiorPositions = calculateInterior(bottomLeft, width, height, axis);

        // Verify interior is empty (or contains only replaceable blocks)
        for (BlockPos pos : interiorPositions) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced()) {
                DevMod.LOGGER.debug("[FrameDetector] FAIL: interior blocked at {} by {}", pos, state);
                return Optional.empty();
            }
        }

        // Calculate frame positions
        Set<BlockPos> framePositions = calculateFramePositions(bottomLeft, width, height, axis);

        DevMod.LOGGER.info("[FrameDetector] SUCCESS: detected {}x{} frame at {}", width, height, bottomLeft);

        return Optional.of(new FrameResult(
            bottomLeft,
            width,
            height,
            axis,
            framePositions,
            interiorPositions
        ));
    }

    /**
     * Finds the distance to the frame edge in the given direction.
     * Returns 0 if already at frame or no frame found within MAX_SIZE.
     */
    private int findFrameEdge(@Nonnull BlockGetter level, @Nonnull BlockPos start, @Nonnull Direction dir) {
        BlockPos.MutableBlockPos pos = start.mutable();
        int distance = 0;

        for (int i = 1; i <= MAX_SIZE; i++) {
            pos.move(dir);
            BlockState state = level.getBlockState(pos);

            if (isFrameBlock.test(level, pos)) {
                return distance;
            }

            if (!state.isAir() && !state.canBeReplaced()) {
                return 0; // Blocked by non-air, non-frame block
            }

            distance++;
        }

        return 0; // No frame found within range
    }

    /**
     * Validates that the frame is complete (no gaps).
     */
    private boolean validateFrame(@Nonnull BlockGetter level, @Nonnull BlockPos bottomLeft,
                                   int width, int height, @Nonnull Direction.Axis axis) {
        Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;

        // Check bottom edge
        for (int w = 0; w < width; w++) {
            BlockPos pos = bottomLeft.relative(widthDir, w).below();
            if (!isFrameBlock.test(level, pos)) {
                return false;
            }
        }

        // Check top edge
        for (int w = 0; w < width; w++) {
            BlockPos pos = bottomLeft.relative(widthDir, w).above(height - 1).above();
            if (!isFrameBlock.test(level, pos)) {
                return false;
            }
        }

        // Check left edge
        for (int h = 0; h < height; h++) {
            BlockPos pos = bottomLeft.relative(widthDir, -1).above(h);
            if (!isFrameBlock.test(level, pos)) {
                return false;
            }
        }

        // Check right edge
        for (int h = 0; h < height; h++) {
            BlockPos pos = bottomLeft.relative(widthDir, width).above(h);
            if (!isFrameBlock.test(level, pos)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Calculates the interior positions of the portal.
     */
    @Nonnull
    private Set<BlockPos> calculateInterior(@Nonnull BlockPos bottomLeft, int width, int height, @Nonnull Direction.Axis axis) {
        Set<BlockPos> interior = new HashSet<>();
        Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;

        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                interior.add(bottomLeft.relative(widthDir, w).above(h));
            }
        }

        return interior;
    }

    /**
     * Calculates the frame block positions (excluding corners for 4x5+ frames).
     */
    @Nonnull
    private Set<BlockPos> calculateFramePositions(@Nonnull BlockPos bottomLeft, int width, int height, @Nonnull Direction.Axis axis) {
        Set<BlockPos> frame = new HashSet<>();
        Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;

        // Bottom and top edges
        for (int w = 0; w < width; w++) {
            frame.add(bottomLeft.relative(widthDir, w).below());
            frame.add(bottomLeft.relative(widthDir, w).above(height));
        }

        // Left and right edges
        for (int h = 0; h < height; h++) {
            frame.add(bottomLeft.relative(widthDir, -1).above(h));
            frame.add(bottomLeft.relative(widthDir, width).above(h));
        }

        // Corners
        frame.add(bottomLeft.relative(widthDir, -1).below());
        frame.add(bottomLeft.relative(widthDir, width).below());
        frame.add(bottomLeft.relative(widthDir, -1).above(height));
        frame.add(bottomLeft.relative(widthDir, width).above(height));

        return frame;
    }

    /**
     * Result of a successful frame detection.
     */
    public record FrameResult(
        @Nonnull BlockPos bottomLeft,
        int width,
        int height,
        @Nonnull Direction.Axis axis,
        @Nonnull Set<BlockPos> framePositions,
        @Nonnull Set<BlockPos> interiorPositions
    ) {
        /**
         * Returns the number of frame blocks.
         */
        public int frameBlockCount() {
            return framePositions.size();
        }

        /**
         * Returns the center position of the portal interior (for teleportation).
         */
        @Nonnull
        public BlockPos centerPosition() {
            Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            return bottomLeft.relative(widthDir, width / 2).above(height / 2);
        }
    }
}
