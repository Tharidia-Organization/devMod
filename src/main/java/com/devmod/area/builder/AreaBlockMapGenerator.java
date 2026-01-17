package com.devmod.area.builder;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaPalette;
import com.devmod.runtime.NexusPalette;

/**
 * Shared utility for generating block placement maps.
 * Used by both AreaBuilder (synchronous) and AreaBuildTask (multi-tick).
 * Ensures consistent building logic across both approaches.
 */
public final class AreaBlockMapGenerator {
    private AreaBlockMapGenerator() {}

    /**
     * Generates floor blocks with checkerboard pattern.
     *
     * @param definition The area definition
     * @return Map of positions to block states for the floor
     */
    @Nonnull
    public static Map<BlockPos, BlockState> generateFloorBlocks(@Nonnull AreaDefinition definition) {
        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
            Objects.requireNonNull(definition.shape()),
            Objects.requireNonNull(definition.centerPosition()),
            Objects.requireNonNull(definition.dimensions()),
            definition.customShapeNbt()
        );

        AreaPalette palette = definition.palette();
        BlockState floor = palette.getBlockState(Objects.requireNonNull(NexusPalette.Key.FLOOR.id()));
        BlockState floorAlt = palette.getBlockState(Objects.requireNonNull(NexusPalette.Key.FLOOR_ALT.id()));

        // Sort positions for deterministic iteration order (CRIT-01 fix: resume reproducibility)
        List<BlockPos> sortedPositions = sortPositionsDeterministically(floorPositions);

        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>(sortedPositions.size());
        for (BlockPos pos : sortedPositions) {
            // Checkerboard pattern
            boolean useAlt = ((pos.getX() + pos.getZ()) % 2 == 0);
            blocks.put(pos, useAlt ? floorAlt : floor);
        }
        return blocks;
    }

    /**
     * Generates wall blocks with support for different wall styles.
     *
     * @param definition The area definition
     * @return Map of positions to block states for the walls
     */
    @Nonnull
    public static Map<BlockPos, BlockState> generateWallBlocks(@Nonnull AreaDefinition definition) {
        Set<BlockPos> wallPositions = AreaShapeGenerator.generateWalls(
            Objects.requireNonNull(definition.shape()),
            Objects.requireNonNull(definition.centerPosition()),
            Objects.requireNonNull(definition.dimensions()),
            definition.customShapeNbt()
        );

        AreaPalette palette = definition.palette();
        AreaOptions options = definition.options();
        AreaOptions.WallStyle style = options.wallStyle();

        // MED-03 fix: Log warning when corner-based style used with non-rectangular shape
        com.devmod.area.data.AreaShape shape = definition.shape();
        if (shape != com.devmod.area.data.AreaShape.RECTANGULAR &&
            (style == AreaOptions.WallStyle.OPEN_CORNERS || style == AreaOptions.WallStyle.PILLARS_ONLY)) {
            org.slf4j.LoggerFactory.getLogger(AreaBlockMapGenerator.class).warn(
                "[Area] Wall style '{}' not supported for shape '{}', using SOLID for area '{}'",
                style.getSerializedName(), shape.getSerializedName(), definition.name());
        }

        BlockState wall = palette.getBlockState(Objects.requireNonNull(NexusPalette.Key.WALL.id()));
        BlockState frame = palette.getBlockState(Objects.requireNonNull(NexusPalette.Key.WALL_FRAME.id()));
        BlockState window = palette.getBlockState(Objects.requireNonNull(NexusPalette.Key.WINDOW.id()));

        // Sort positions for deterministic iteration order (CRIT-01 fix: resume reproducibility)
        List<BlockPos> sortedPositions = sortPositionsDeterministically(wallPositions);

        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>(sortedPositions.size());
        for (BlockPos pos : sortedPositions) {
            BlockState state = getWallBlockForStyle(Objects.requireNonNull(pos), definition, Objects.requireNonNull(style), wall, frame, window);
            if (state != null) {
                blocks.put(pos, state);
            }
        }
        return blocks;
    }

    /**
     * Gets the appropriate wall block based on position and style.
     */
    @Nullable
    private static BlockState getWallBlockForStyle(
            @Nonnull BlockPos pos,
            @Nonnull AreaDefinition definition,
            @Nonnull AreaOptions.WallStyle style,
            @Nonnull BlockState wall,
            @Nonnull BlockState frame,
            @Nonnull BlockState window) {

        AreaDimensions dims = definition.dimensions();
        int relativeY = pos.getY() - dims.floorY();
        int height = dims.height();

        // MED-03 fix: Corner-based styles only work correctly for RECTANGULAR shape
        // For other shapes, fall back to SOLID
        com.devmod.area.data.AreaShape shape = definition.shape();
        boolean isRectangular = (shape == com.devmod.area.data.AreaShape.RECTANGULAR);

        return switch (style) {
            case SOLID -> wall;
            case WINDOWED -> {
                // Windows in the middle section
                if (relativeY > 1 && relativeY < height - 1) {
                    yield window;
                }
                yield wall;
            }
            case OPEN_CORNERS -> {
                // MED-03 fix: Only apply corner logic for rectangular shapes
                if (!isRectangular) {
                    yield wall; // Fall back to solid for non-rectangular
                }
                // Open at corners
                int cornerDist = getCornerDistance(pos, definition);
                if (cornerDist < 3) {
                    yield null; // Air
                }
                yield wall;
            }
            case PILLARS_ONLY -> {
                // MED-03 fix: Only apply corner logic for rectangular shapes
                if (!isRectangular) {
                    yield wall; // Fall back to solid for non-rectangular
                }
                // Only build at corners (pillars)
                if (isAtCorner(pos, definition)) {
                    yield frame;
                }
                yield null; // Air
            }
        };
    }

    /**
     * Calculates distance to nearest corner for wall positions.
     * A corner is where both X and Z are at the perimeter edge.
     * Returns small values near corners, large values near edge centers.
     */
    private static int getCornerDistance(@Nonnull BlockPos pos, @Nonnull AreaDefinition definition) {
        BlockPos center = definition.centerPosition();
        AreaDimensions dims = definition.dimensions();
        int minX = AreaShapeGenerator.minOffset(dims.width());
        int maxX = AreaShapeGenerator.maxOffset(dims.width());
        int minZ = AreaShapeGenerator.minOffset(dims.length());
        int maxZ = AreaShapeGenerator.maxOffset(dims.length());

        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();

        // Distance from edge (0 at edge, grows inward)
        int distFromXEdge = Math.min(dx - minX, maxX - dx);
        int distFromZEdge = Math.min(dz - minZ, maxZ - dz);

        // Corner distance = max of both distances
        // - At corners: both are 0, max = 0 (near corner)
        // - At edge centers: one is 0, other is large, max = large (not corner)
        return Math.max(distFromXEdge, distFromZEdge);
    }

    /**
     * Checks if position is at a corner.
     */
    private static boolean isAtCorner(@Nonnull BlockPos pos, @Nonnull AreaDefinition definition) {
        BlockPos center = definition.centerPosition();
        AreaDimensions dims = definition.dimensions();
        int minX = AreaShapeGenerator.minOffset(dims.width());
        int maxX = AreaShapeGenerator.maxOffset(dims.width());
        int minZ = AreaShapeGenerator.minOffset(dims.length());
        int maxZ = AreaShapeGenerator.maxOffset(dims.length());

        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();

        boolean nearXEdge = dx <= minX + 1 || dx >= maxX - 1;
        boolean nearZEdge = dz <= minZ + 1 || dz >= maxZ - 1;
        return nearXEdge && nearZEdge;
    }

    /**
     * Generates ceiling blocks with light grid pattern.
     *
     * @param definition The area definition
     * @return Map of positions to block states for the ceiling
     */
    @Nonnull
    public static Map<BlockPos, BlockState> generateCeilingBlocks(@Nonnull AreaDefinition definition) {
        Set<BlockPos> ceilingPositions = AreaShapeGenerator.generateCeiling(
            Objects.requireNonNull(definition.shape()),
            Objects.requireNonNull(definition.centerPosition()),
            Objects.requireNonNull(definition.dimensions()),
            definition.customShapeNbt()
        );

        AreaPalette palette = definition.palette();
        // H-13 fix: Use CEILING key instead of FLOOR for proper ceiling material
        BlockState ceiling = palette.getBlockState(Objects.requireNonNull(NexusPalette.Key.CEILING.id()));
        BlockState light = palette.getBlockState(Objects.requireNonNull(NexusPalette.Key.LIGHT.id()));

        // Sort positions for deterministic iteration order (CRIT-01 fix: resume reproducibility)
        List<BlockPos> sortedPositions = sortPositionsDeterministically(ceilingPositions);

        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>(sortedPositions.size());
        for (BlockPos pos : sortedPositions) {
            // Light grid pattern: lights every 8 blocks
            boolean isLightPos = (pos.getX() % 8 == 0) && (pos.getZ() % 8 == 0);
            blocks.put(pos, isLightPos ? light : ceiling);
        }
        return blocks;
    }

    /**
     * Estimates the total block count for an area.
     * Useful for deciding between synchronous and multi-tick building.
     *
     * @param definition The area definition
     * @return Estimated total block count
     */
    public static int estimateTotalBlocks(@Nonnull AreaDefinition definition) {
        AreaOptions options = definition.options();
        int total = 0;

        // SEC-03 fix: For CUSTOM_NBT and PATH shapes, compute actual floor size from NBT
        int floorEstimate;
        com.devmod.area.data.AreaShape shape = definition.shape();
        if (shape == com.devmod.area.data.AreaShape.CUSTOM_NBT) {
            floorEstimate = AreaShapeGenerator.estimateCustomBlockCount(definition.customShapeNbt());
        } else if (shape == com.devmod.area.data.AreaShape.PATH) {
            // PATH requires computing actual floor positions from waypoints
            floorEstimate = AreaShapeGenerator.generateFloor(
                shape,
                Objects.requireNonNull(definition.centerPosition()),
                Objects.requireNonNull(definition.dimensions()),
                definition.customShapeNbt()
            ).size();
        } else {
            floorEstimate = AreaShapeGenerator.estimateBlockCount(
                Objects.requireNonNull(shape),
                Objects.requireNonNull(definition.dimensions())
            );
        }

        if (options.hasFloor()) {
            total += floorEstimate;
        }
        if (options.hasWalls()) {
            // SEC-10 fix: For PATH shapes, estimate walls based on corridor length not declared dimensions
            int perimeter;
            if (shape == com.devmod.area.data.AreaShape.PATH) {
                // PATH corridor perimeter ≈ 2 * (length + width) where length ≈ floor/width
                // Use pathWidth from NBT (corridor half-width) when available.
                CompoundTag pathNbt = definition.customShapeNbt();
                int corridorHalfWidth = Math.max(1, definition.dimensions().width() / 2);
                if (pathNbt != null && pathNbt.contains(AreaShapeGenerator.NBT_PATH_WIDTH)) {
                    corridorHalfWidth = pathNbt.getInt(AreaShapeGenerator.NBT_PATH_WIDTH);
                }
                int corridorWidth = Math.max(1, corridorHalfWidth * 2 + 1);
                int corridorLength = floorEstimate > 0 ? (floorEstimate / corridorWidth) : 0;
                perimeter = 2 * (corridorLength + corridorWidth);
            } else {
                perimeter = 2 * (definition.dimensions().width() + definition.dimensions().length());
            }
            total += perimeter * definition.dimensions().height();
        }
        if (options.hasCeiling()) {
            total += floorEstimate;
        }

        return total;
    }

    /**
     * Estimates total blocks for biome terrain generation.
     * Biome builds fill the full volume (floor area * height), not just surfaces.
     * This is essential for accurate resume validation and multi-tick threshold decisions.
     *
     * @param definition The area definition (must have biome config)
     * @return Estimated biome block count (volume-based)
     */
    public static int estimateBiomeBlocks(@Nonnull AreaDefinition definition) {
        // For biome builds, we fill the entire volume: floor area * height
        // SEC-05 fix: Handle PATH shapes like CUSTOM_NBT - compute actual floor size
        int floorEstimate;
        com.devmod.area.data.AreaShape shape = definition.shape();
        if (shape == com.devmod.area.data.AreaShape.CUSTOM_NBT) {
            floorEstimate = AreaShapeGenerator.estimateCustomBlockCount(definition.customShapeNbt());
        } else if (shape == com.devmod.area.data.AreaShape.PATH) {
            // PATH requires computing actual floor positions from waypoints
            floorEstimate = AreaShapeGenerator.generateFloor(
                shape,
                Objects.requireNonNull(definition.centerPosition()),
                Objects.requireNonNull(definition.dimensions()),
                definition.customShapeNbt()
            ).size();
        } else {
            floorEstimate = AreaShapeGenerator.estimateBlockCount(
                Objects.requireNonNull(shape),
                Objects.requireNonNull(definition.dimensions())
            );
        }

        // Biome terrain fills the full height with terrain + caves + features
        // Multiply floor area by height for volume estimate
        int height = definition.dimensions().height();
        return floorEstimate * height;
    }

    // ========================================================================
    // Clear (Air) Block Generation (MED-02 fix: async clear for large areas)
    // ========================================================================

    /**
     * Generates air blocks for clearing an area volume.
     * Used by multi-tick builds to clear the area asynchronously instead of
     * synchronously, preventing lag spikes for large areas.
     *
     * @param definition The area definition
     * @return Map of positions to AIR block states for the entire volume
     */
    @Nonnull
    public static Map<BlockPos, BlockState> generateClearBlocks(@Nonnull AreaDefinition definition) {
        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
            Objects.requireNonNull(definition.shape()),
            Objects.requireNonNull(definition.centerPosition()),
            Objects.requireNonNull(definition.dimensions()),
            definition.customShapeNbt()
        );

        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        int height = definition.dimensions().height();

        // Estimate capacity: floor positions * (height + 1)
        int capacity = floorPositions.size() * (height + 1);
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>(capacity);

        // Generate all positions in deterministic order
        List<BlockPos> sortedFloor = sortPositionsDeterministically(floorPositions);
        for (BlockPos floorPos : sortedFloor) {
            for (int y = 0; y <= height; y++) {
                blocks.put(floorPos.above(y), air);
            }
        }

        return blocks;
    }

    /**
     * Estimates the number of blocks to clear for an area.
     * Used to decide between sync/async clearing.
     *
     * @param definition The area definition
     * @return Estimated clear block count (volume)
     */
    public static int estimateClearBlocks(@Nonnull AreaDefinition definition) {
        // SEC-06 fix: Handle PATH shapes like CUSTOM_NBT - compute actual floor size
        int floorEstimate;
        com.devmod.area.data.AreaShape shape = definition.shape();
        if (shape == com.devmod.area.data.AreaShape.CUSTOM_NBT) {
            floorEstimate = AreaShapeGenerator.estimateCustomBlockCount(definition.customShapeNbt());
        } else if (shape == com.devmod.area.data.AreaShape.PATH) {
            // PATH requires computing actual floor positions from waypoints
            floorEstimate = AreaShapeGenerator.generateFloor(
                shape,
                Objects.requireNonNull(definition.centerPosition()),
                Objects.requireNonNull(definition.dimensions()),
                definition.customShapeNbt()
            ).size();
        } else {
            floorEstimate = AreaShapeGenerator.estimateBlockCount(
                Objects.requireNonNull(shape),
                Objects.requireNonNull(definition.dimensions())
            );
        }
        // Volume = floor area * (height + 1) to include all Y levels
        return floorEstimate * (definition.dimensions().height() + 1);
    }

    // ========================================================================
    // Deterministic Ordering Helpers (CRIT-01 fix)
    // ========================================================================

    /**
     * Comparator for deterministic BlockPos ordering.
     * Orders by X, then Z, then Y to ensure consistent iteration across JVM runs.
     */
    private static final Comparator<BlockPos> POSITION_COMPARATOR = Comparator
        .<BlockPos>comparingInt(BlockPos::getX)
        .thenComparingInt(BlockPos::getZ)
        .thenComparingInt(BlockPos::getY);

    /**
     * Sorts a set of block positions into a deterministic order.
     * Essential for build resume functionality - ensures the same positions
     * are processed in the same order across builds and server restarts.
     *
     * @param positions The set of positions to sort
     * @return A list of positions in deterministic order (X, then Z, then Y)
     */
    @Nonnull
    public static List<BlockPos> sortPositionsDeterministically(@Nonnull Set<BlockPos> positions) {
        return Objects.requireNonNull(positions.stream()
            .sorted(POSITION_COMPARATOR)
            .toList());
    }
}
