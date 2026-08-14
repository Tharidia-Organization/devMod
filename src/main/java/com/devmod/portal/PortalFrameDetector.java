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

    /** Widest legal interior: the frame ring costs one block on each side of {@link #MAX_SIZE}. */
    public static final int MAX_INTERIOR_SIZE = MAX_SIZE - 2;

    /**
     * Most portal blocks a legal portal can hold, reached by a square interior of
     * {@link #MAX_INTERIOR_SIZE}. Flood fills over portal blocks bound themselves by this, so
     * the bound and the shape the detector accepts cannot drift apart.
     */
    public static final int MAX_INTERIOR_BLOCKS = MAX_INTERIOR_SIZE * MAX_INTERIOR_SIZE;

    private static final int NO_EDGE = -1;

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

        if (minWidth == NO_EDGE || maxWidth == NO_EDGE || minHeight == NO_EDGE || maxHeight == NO_EDGE) {
            DevMod.LOGGER.debug("[FrameDetector] FAIL: no frame edges found");
            return Optional.empty();
        }

        int width = minWidth + maxWidth + 1;
        int height = minHeight + maxHeight + 1;
        int frameWidth = width + 2;
        int frameHeight = height + 2;

        DevMod.LOGGER.debug("[FrameDetector] interior size: {}x{}, frame size: {}x{}",
            width, height, frameWidth, frameHeight);

        // Validate size
        if (frameWidth < MIN_SIZE || frameWidth > MAX_SIZE || frameHeight < MIN_SIZE || frameHeight > MAX_SIZE) {
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

        DevMod.LOGGER.info("[FrameDetector] SUCCESS: detected frame {}x{} (interior {}x{}) at {}",
            frameWidth, frameHeight, width, height, bottomLeft);

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
     * Returns 0 if already at frame; returns {@link #NO_EDGE} if no frame found within MAX_SIZE.
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
                return NO_EDGE; // Blocked by non-air, non-frame block
            }

            distance++;
        }

        return NO_EDGE; // No frame found within range
    }

    /**
     * Validates that the frame is complete (no gaps).
     */
    private boolean validateFrame(@Nonnull BlockGetter level, @Nonnull BlockPos bottomLeft,
                                   int width, int height, @Nonnull Direction.Axis axis) {
        Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockPos frameBottomLeft = bottomLeft.relative(widthDir, -1).below();
        int frameWidth = width + 2;
        int frameHeight = height + 2;

        // Check bottom edge
        for (int w = 0; w < frameWidth; w++) {
            BlockPos pos = frameBottomLeft.relative(widthDir, w);
            if (!isFrameBlock.test(level, pos)) {
                return false;
            }
        }

        // Check top edge
        BlockPos frameTopLeft = frameBottomLeft.above(frameHeight - 1);
        for (int w = 0; w < frameWidth; w++) {
            BlockPos pos = frameTopLeft.relative(widthDir, w);
            if (!isFrameBlock.test(level, pos)) {
                return false;
            }
        }

        // Check left edge
        for (int h = 0; h < frameHeight; h++) {
            BlockPos pos = frameBottomLeft.above(h);
            if (!isFrameBlock.test(level, pos)) {
                return false;
            }
        }

        // Check right edge
        BlockPos frameBottomRight = frameBottomLeft.relative(widthDir, frameWidth - 1);
        for (int h = 0; h < frameHeight; h++) {
            BlockPos pos = frameBottomRight.above(h);
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
     * Calculates the frame block positions.
     */
    @Nonnull
    private Set<BlockPos> calculateFramePositions(@Nonnull BlockPos bottomLeft, int width, int height, @Nonnull Direction.Axis axis) {
        Set<BlockPos> frame = new HashSet<>();
        Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockPos frameBottomLeft = bottomLeft.relative(widthDir, -1).below();
        int frameWidth = width + 2;
        int frameHeight = height + 2;

        // Bottom and top edges
        BlockPos frameTopLeft = frameBottomLeft.above(frameHeight - 1);
        for (int w = 0; w < frameWidth; w++) {
            frame.add(frameBottomLeft.relative(widthDir, w));
            frame.add(frameTopLeft.relative(widthDir, w));
        }

        // Left and right edges
        BlockPos frameBottomRight = frameBottomLeft.relative(widthDir, frameWidth - 1);
        for (int h = 0; h < frameHeight; h++) {
            frame.add(frameBottomLeft.above(h));
            frame.add(frameBottomRight.above(h));
        }

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
