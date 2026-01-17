package com.devmod.area.builder;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaShape;

/**
 * Generates block positions for various geometric shapes.
 */
public final class AreaShapeGenerator {
    private AreaShapeGenerator() {}

    /**
     * Generates floor positions for the given shape.
     *
     * @param shape  The shape type
     * @param center The center position
     * @param dims   The dimensions
     * @return Set of block positions that form the floor
     */
    @Nonnull
    public static Set<BlockPos> generateFloor(@Nonnull AreaShape shape, @Nonnull BlockPos center,
            @Nonnull AreaDimensions dims) {
        return generateFloor(shape, center, dims, null);
    }

    /**
     * Generates floor positions for the given shape with optional custom NBT data.
     *
     * @param shape          The shape type
     * @param center         The center position
     * @param dims           The dimensions
     * @param customShapeNbt The custom shape NBT data (required for CUSTOM_NBT shape)
     * @return Set of block positions that form the floor
     */
    @Nonnull
    public static Set<BlockPos> generateFloor(@Nonnull AreaShape shape, @Nonnull BlockPos center,
            @Nonnull AreaDimensions dims, @Nullable CompoundTag customShapeNbt) {
        // M-01 fix: Validate dimensions to prevent degenerate areas
        if (dims.width() <= 0 || dims.length() <= 0) {
            return Objects.requireNonNull(Set.of());
        }
        return Objects.requireNonNull(switch (shape) {
            case RECTANGULAR -> generateRectangular(center, dims);
            case CIRCULAR -> generateCircular(center, dims);
            case HEXAGONAL -> generateHexagonal(center, dims);
            case OCTAGONAL -> generateOctagonal(center, dims);
            case CUSTOM_NBT -> generateCustomFloor(center, dims, customShapeNbt);
            case PATH -> generatePathFloor(center, dims, customShapeNbt);
        });
    }

    /**
     * Generates wall positions (perimeter) for the given shape.
     * Optimized: calculates perimeter directly instead of checking all floor positions.
     *
     * @param shape  The shape type
     * @param center The center position
     * @param dims   The dimensions
     * @return Set of block positions that form the walls
     */
    @Nonnull
    public static Set<BlockPos> generateWalls(@Nonnull AreaShape shape, @Nonnull BlockPos center,
            @Nonnull AreaDimensions dims) {
        return generateWalls(shape, center, dims, null);
    }

    /**
     * Generates wall positions (perimeter) for the given shape with optional custom NBT data.
     * Optimized: calculates perimeter directly instead of checking all floor positions.
     *
     * @param shape          The shape type
     * @param center         The center position
     * @param dims           The dimensions
     * @param customShapeNbt The custom shape NBT data (required for CUSTOM_NBT shape)
     * @return Set of block positions that form the walls
     */
    @Nonnull
    public static Set<BlockPos> generateWalls(@Nonnull AreaShape shape, @Nonnull BlockPos center,
            @Nonnull AreaDimensions dims, @Nullable CompoundTag customShapeNbt) {
        // M-01 fix: Validate dimensions to prevent degenerate perimeters
        if (dims.width() <= 0 || dims.length() <= 0 || dims.height() <= 0) {
            return Objects.requireNonNull(Set.of());
        }
        Set<BlockPos> perimeter = generatePerimeter(shape, center, dims, customShapeNbt);
        Set<BlockPos> walls = new HashSet<>(perimeter.size() * dims.height());

        // Extrude perimeter upward to create walls
        for (BlockPos perimPos : perimeter) {
            for (int y = 1; y < dims.height(); y++) {
                walls.add(perimPos.above(y));
            }
        }
        return walls;
    }

    /**
     * Generates just the perimeter (edge) positions at floor level.
     * O(perimeter) instead of O(area) - much faster for large areas.
     */
    @Nonnull
    public static Set<BlockPos> generatePerimeter(@Nonnull AreaShape shape, @Nonnull BlockPos center,
            @Nonnull AreaDimensions dims) {
        return generatePerimeter(shape, center, dims, null);
    }

    /**
     * Generates just the perimeter (edge) positions at floor level with optional custom NBT data.
     * O(perimeter) instead of O(area) - much faster for large areas.
     *
     * @param shape          The shape type
     * @param center         The center position
     * @param dims           The dimensions
     * @param customShapeNbt The custom shape NBT data (required for CUSTOM_NBT shape)
     * @return Set of block positions that form the perimeter
     */
    @Nonnull
    public static Set<BlockPos> generatePerimeter(@Nonnull AreaShape shape, @Nonnull BlockPos center,
            @Nonnull AreaDimensions dims, @Nullable CompoundTag customShapeNbt) {
        // M-01 fix: Validate dimensions to prevent degenerate areas
        if (dims.width() <= 0 || dims.length() <= 0) {
            return Objects.requireNonNull(Set.of());
        }
        return Objects.requireNonNull(switch (shape) {
            case RECTANGULAR -> generateRectangularPerimeter(center, dims);
            case CIRCULAR -> generateCircularPerimeter(center, dims);
            case HEXAGONAL -> generateHexagonalPerimeter(center, dims);
            case OCTAGONAL -> generateOctagonalPerimeter(center, dims);
            case CUSTOM_NBT -> generateCustomPerimeter(center, dims, customShapeNbt);
            case PATH -> generatePathPerimeter(center, dims, customShapeNbt);
        });
    }

    /**
     * Generates rectangular perimeter - O(2*(w+l)) instead of O(w*l).
     */
    @Nonnull
    private static Set<BlockPos> generateRectangularPerimeter(@Nonnull BlockPos center, @Nonnull AreaDimensions dims) {
        Set<BlockPos> perimeter = new HashSet<>();
        int minX = minOffset(dims.width());
        int maxX = maxOffset(dims.width());
        int minZ = minOffset(dims.length());
        int maxZ = maxOffset(dims.length());
        int y = dims.floorY();
        int cx = center.getX();
        int cz = center.getZ();

        // Top and bottom edges (along X)
        for (int x = minX; x <= maxX; x++) {
            perimeter.add(new BlockPos(cx + x, y, cz + minZ)); // North edge
            perimeter.add(new BlockPos(cx + x, y, cz + maxZ)); // South edge
        }
        // Left and right edges (along Z), excluding corners (already added)
        for (int z = minZ + 1; z < maxZ; z++) {
            perimeter.add(new BlockPos(cx + minX, y, cz + z)); // West edge
            perimeter.add(new BlockPos(cx + maxX, y, cz + z)); // East edge
        }
        return perimeter;
    }

    /**
     * Generates elliptical perimeter inside the filled ellipse.
     * HIGH-03 fix: Supports width != length for elliptical shapes.
     * Uses the ellipse equation: (x/a)² + (z/b)² <= 1
     * O(max(width,length)) by sampling max extents per row/column.
     */
    @Nonnull
    private static Set<BlockPos> generateCircularPerimeter(@Nonnull BlockPos center, @Nonnull AreaDimensions dims) {
        Set<BlockPos> perimeter = new HashSet<>();
        // HIGH-03 fix: Use separate radii for width (X) and length (Z)
        int radiusX = dims.width() / 2;
        int radiusZ = dims.length() / 2;
        int minX = minOffset(dims.width());
        int maxX = maxOffset(dims.width());
        int minZ = minOffset(dims.length());
        int maxZ = maxOffset(dims.length());
        int y = dims.floorY();
        int cx = center.getX();
        int cz = center.getZ();

        // For each X, find the max Z that satisfies ellipse equation
        for (int x = minX; x <= maxX; x++) {
            // Ellipse: (x/radiusX)² + (z/radiusZ)² = 1
            // Solve for z: z = radiusZ * sqrt(1 - (x/radiusX)²)
            double xNorm = radiusX > 0 ? (double) x / radiusX : 0;
            double zMaxD = radiusZ * Math.sqrt(Math.max(0, 1.0 - xNorm * xNorm));
            int zMax = (int) Math.floor(zMaxD);
            int zTop = Math.min(zMax, maxZ);
            int zBottom = Math.max(-zMax, minZ);
            perimeter.add(new BlockPos(cx + x, y, cz + zTop));
            perimeter.add(new BlockPos(cx + x, y, cz + zBottom));
        }

        // For each Z, find the max X that satisfies ellipse equation
        for (int z = minZ; z <= maxZ; z++) {
            double zNorm = radiusZ > 0 ? (double) z / radiusZ : 0;
            double xMaxD = radiusX * Math.sqrt(Math.max(0, 1.0 - zNorm * zNorm));
            int xMax = (int) Math.floor(xMaxD);
            int xEast = Math.min(xMax, maxX);
            int xWest = Math.max(-xMax, minX);
            perimeter.add(new BlockPos(cx + xEast, y, cz + z));
            perimeter.add(new BlockPos(cx + xWest, y, cz + z));
        }

        return perimeter;
    }

    /**
     * Generates hexagonal perimeter.
     * HIGH-03 fix: Supports width != length for scaled hexagons.
     * L-09 fix: Optimized to O(perimeter) instead of O(radius²).
     */
    @Nonnull
    private static Set<BlockPos> generateHexagonalPerimeter(@Nonnull BlockPos center, @Nonnull AreaDimensions dims) {
        Set<BlockPos> perimeter = new HashSet<>();
        // HIGH-03 fix: Use separate radii for width (X) and length (Z)
        int radiusX = dims.width() / 2;
        int radiusZ = dims.length() / 2;
        int y = dims.floorY();
        int cx = center.getX();
        int cz = center.getZ();

        // Trace the hexagon edges directly
        // For each X, find the max Z that satisfies the scaled hexagon equation
        // Original: absX + (absZ * 3) / 4 <= radius
        // Scaled: (absX/radiusX) + (absZ/radiusZ) * 0.75 <= 1
        for (int x = -radiusX; x <= radiusX; x++) {
            int absX = Math.abs(x);
            // Solve for maxAbsZ: (absX/radiusX) + (absZ/radiusZ) * 0.75 = 1
            // absZ = radiusZ * (1 - absX/radiusX) / 0.75 = radiusZ * (1 - absX/radiusX) * 4/3
            double xRatio = radiusX > 0 ? (double) absX / radiusX : 0;
            int maxAbsZ = (int) (radiusZ * (1.0 - xRatio) * 4.0 / 3.0);

            // Top and bottom edges of hexagon at this X
            if (maxAbsZ >= 0) {
                perimeter.add(new BlockPos(cx + x, y, cz + maxAbsZ));
                perimeter.add(new BlockPos(cx + x, y, cz - maxAbsZ));
            }
        }

        // Also trace along Z for the side edges
        for (int z = -radiusZ; z <= radiusZ; z++) {
            int absZ = Math.abs(z);
            // Solve for maxAbsX: (absX/radiusX) + (absZ/radiusZ) * 0.75 = 1
            // absX = radiusX * (1 - (absZ/radiusZ) * 0.75)
            double zRatio = radiusZ > 0 ? (double) absZ / radiusZ : 0;
            int maxAbsX = (int) (radiusX * (1.0 - zRatio * 0.75));

            if (maxAbsX >= 0) {
                perimeter.add(new BlockPos(cx + maxAbsX, y, cz + z));
                perimeter.add(new BlockPos(cx - maxAbsX, y, cz + z));
            }
        }

        return perimeter;
    }

    /**
     * Generates octagonal perimeter.
     * HIGH-03 fix: Supports width != length for scaled octagons.
     * L-09 fix: Optimized to O(perimeter) instead of O(radius²).
     */
    @Nonnull
    private static Set<BlockPos> generateOctagonalPerimeter(@Nonnull BlockPos center, @Nonnull AreaDimensions dims) {
        Set<BlockPos> perimeter = new HashSet<>();
        // HIGH-03 fix: Use separate radii for width (X) and length (Z)
        int radiusX = dims.width() / 2;
        int radiusZ = dims.length() / 2;
        int y = dims.floorY();
        int cx = center.getX();
        int cz = center.getZ();
        int cornerCutX = radiusX / 3;
        int cornerCutZ = radiusZ / 3;

        // Trace the 8 edges of the octagon directly:
        // 4 straight edges (top, bottom, left, right) and 4 diagonal corners

        // Straight edge half-lengths along each axis
        int straightEdgeHalfX = radiusX - cornerCutX;
        int straightEdgeHalfZ = radiusZ - cornerCutZ;

        // Top and bottom straight edges (along X axis at max/min Z)
        for (int x = -straightEdgeHalfX; x <= straightEdgeHalfX; x++) {
            perimeter.add(new BlockPos(cx + x, y, cz + radiusZ));  // Top
            perimeter.add(new BlockPos(cx + x, y, cz - radiusZ));  // Bottom
        }

        // Left and right straight edges (along Z axis at max/min X)
        for (int z = -straightEdgeHalfZ; z <= straightEdgeHalfZ; z++) {
            perimeter.add(new BlockPos(cx + radiusX, y, cz + z));  // Right
            perimeter.add(new BlockPos(cx - radiusX, y, cz + z));  // Left
        }

        // Diagonal corners: scaled diagonal constraint
        // For X from straightEdgeHalfX+1 to radiusX, compute Z on diagonal
        for (int x = straightEdgeHalfX + 1; x <= radiusX; x++) {
            // Normalized diagonal: (x - straightEdgeHalfX) / cornerCutX = (radiusZ - z) / cornerCutZ
            // Solve for z: z = radiusZ - cornerCutZ * (x - straightEdgeHalfX) / cornerCutX
            int z = cornerCutX > 0 ? radiusZ - cornerCutZ * (x - straightEdgeHalfX) / cornerCutX : 0;
            if (z >= 0 && z < radiusZ) {
                perimeter.add(new BlockPos(cx + x, y, cz + z));   // Top-right
                perimeter.add(new BlockPos(cx + x, y, cz - z));   // Bottom-right
                perimeter.add(new BlockPos(cx - x, y, cz + z));   // Top-left
                perimeter.add(new BlockPos(cx - x, y, cz - z));   // Bottom-left
            }
        }

        return perimeter;
    }

    /**
     * Generates perimeter for CUSTOM_NBT shape.
     * A block is on the perimeter if at least one of its 4 horizontal neighbors
     * is not in the shape.
     *
     * @param center   The center position
     * @param dims     The dimensions
     * @param shapeNbt The custom shape NBT data
     * @return Set of block positions that form the perimeter
     */
    @Nonnull
    private static Set<BlockPos> generateCustomPerimeter(@Nonnull BlockPos center,
            @Nonnull AreaDimensions dims, @Nullable CompoundTag shapeNbt) {
        Set<BlockPos> floorPositions = generateCustomFloor(center, dims, shapeNbt);
        if (floorPositions.isEmpty()) {
            return Objects.requireNonNull(Set.of());
        }

        Set<BlockPos> perimeter = new HashSet<>();
        for (BlockPos pos : floorPositions) {
            // Check 4 horizontal neighbors - if any is missing, this is on perimeter
            if (!floorPositions.contains(pos.north()) ||
                !floorPositions.contains(pos.south()) ||
                !floorPositions.contains(pos.east()) ||
                !floorPositions.contains(pos.west())) {
                perimeter.add(pos);
            }
        }
        return perimeter;
    }

    /**
     * Generates ceiling positions for the given shape.
     */
    @Nonnull
    public static Set<BlockPos> generateCeiling(@Nonnull AreaShape shape, @Nonnull BlockPos center,
            @Nonnull AreaDimensions dims) {
        return generateCeiling(shape, center, dims, null);
    }

    /**
     * Generates ceiling positions for the given shape with optional custom NBT data.
     */
    @Nonnull
    public static Set<BlockPos> generateCeiling(@Nonnull AreaShape shape, @Nonnull BlockPos center,
            @Nonnull AreaDimensions dims, @Nullable CompoundTag customShapeNbt) {
        // M-01 fix: Validate dimensions
        if (dims.width() <= 0 || dims.length() <= 0) {
            return Objects.requireNonNull(Set.of());
        }
        Set<BlockPos> ceiling = new HashSet<>();
        for (BlockPos floorPos : generateFloor(shape, center, dims, customShapeNbt)) {
            ceiling.add(floorPos.above(dims.height()));
        }
        return ceiling;
    }

    /**
     * Calculates the bounding box for the given shape.
     */
    @Nonnull
    public static BoundingBox calculateBounds(@Nonnull AreaShape shape, @Nonnull BlockPos center,
            @Nonnull AreaDimensions dims) {
        int minX = minOffset(dims.width());
        int maxX = maxOffset(dims.width());
        int minZ = minOffset(dims.length());
        int maxZ = maxOffset(dims.length());

        return new BoundingBox(
            center.getX() + minX,
            dims.floorY(),
            center.getZ() + minZ,
            center.getX() + maxX,
            dims.floorY() + dims.height(),
            center.getZ() + maxZ
        );
    }

    // ========================================================================
    // Shape Generators
    // ========================================================================

    @Nonnull
    private static Set<BlockPos> generateRectangular(@Nonnull BlockPos center, @Nonnull AreaDimensions dims) {
        Set<BlockPos> positions = new HashSet<>();
        int minX = minOffset(dims.width());
        int maxX = maxOffset(dims.width());
        int minZ = minOffset(dims.length());
        int maxZ = maxOffset(dims.length());
        int y = dims.floorY();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                positions.add(new BlockPos(center.getX() + x, y, center.getZ() + z));
            }
        }
        return positions;
    }

    /**
     * HIGH-03 fix: Generates elliptical floor using both width (X) and length (Z).
     * Uses the ellipse equation: (x/a)² + (z/b)² <= 1
     */
    @Nonnull
    private static Set<BlockPos> generateCircular(@Nonnull BlockPos center, @Nonnull AreaDimensions dims) {
        Set<BlockPos> positions = new HashSet<>();
        // HIGH-03 fix: Use separate radii for width (X) and length (Z)
        int radiusX = dims.width() / 2;
        int radiusZ = dims.length() / 2;
        int minX = minOffset(dims.width());
        int maxX = maxOffset(dims.width());
        int minZ = minOffset(dims.length());
        int maxZ = maxOffset(dims.length());
        int y = dims.floorY();

        // Precompute squared radii for ellipse check
        double radiusXSq = radiusX * radiusX;
        double radiusZSq = radiusZ * radiusZ;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Ellipse equation: (x/radiusX)² + (z/radiusZ)² <= 1
                // Equivalent to: x²/radiusX² + z²/radiusZ² <= 1
                // Or: x² * radiusZ² + z² * radiusX² <= radiusX² * radiusZ²
                double ellipseVal = (radiusXSq > 0 ? (x * x) / radiusXSq : 0) +
                                   (radiusZSq > 0 ? (z * z) / radiusZSq : 0);
                if (ellipseVal <= 1.0) {
                    positions.add(new BlockPos(center.getX() + x, y, center.getZ() + z));
                }
            }
        }
        return positions;
    }

    /**
     * HIGH-03 fix: Generates hexagonal floor using both width (X) and length (Z).
     * Uses scaled hexagon equation: (absX/radiusX) + (absZ/radiusZ) * 0.75 <= 1
     */
    @Nonnull
    private static Set<BlockPos> generateHexagonal(@Nonnull BlockPos center, @Nonnull AreaDimensions dims) {
        Set<BlockPos> positions = new HashSet<>();
        // HIGH-03 fix: Use separate radii for width (X) and length (Z)
        int radiusX = dims.width() / 2;
        int radiusZ = dims.length() / 2;
        int minX = minOffset(dims.width());
        int maxX = maxOffset(dims.width());
        int minZ = minOffset(dims.length());
        int maxZ = maxOffset(dims.length());
        int y = dims.floorY();

        // Hexagon: scaled x + z distance check
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int absX = Math.abs(x);
                int absZ = Math.abs(z);

                // Scaled hexagonal equation: (absX/radiusX) + (absZ/radiusZ) * 0.75 <= 1
                double xRatio = radiusX > 0 ? (double) absX / radiusX : 0;
                double zRatio = radiusZ > 0 ? (double) absZ / radiusZ : 0;
                if (xRatio + zRatio * 0.75 <= 1.0) {
                    positions.add(new BlockPos(center.getX() + x, y, center.getZ() + z));
                }
            }
        }
        return positions;
    }

    /**
     * HIGH-03 fix: Generates octagonal floor using both width (X) and length (Z).
     * Uses scaled rectangle with corner cuts.
     */
    @Nonnull
    private static Set<BlockPos> generateOctagonal(@Nonnull BlockPos center, @Nonnull AreaDimensions dims) {
        Set<BlockPos> positions = new HashSet<>();
        // HIGH-03 fix: Use separate radii for width (X) and length (Z)
        int radiusX = dims.width() / 2;
        int radiusZ = dims.length() / 2;
        int minX = minOffset(dims.width());
        int maxX = maxOffset(dims.width());
        int minZ = minOffset(dims.length());
        int maxZ = maxOffset(dims.length());
        int y = dims.floorY();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int absX = Math.abs(x);
                int absZ = Math.abs(z);

                // Octagonal: rectangular with scaled corner cuts
                boolean inRect = absX <= radiusX && absZ <= radiusZ;
                // Scaled corner cut: (absX/radiusX) + (absZ/radiusZ) <= 1 + cornerCutRatio
                // Where cornerCutRatio = 1/3, so threshold = 1 + 1/3 = 4/3
                double xRatio = radiusX > 0 ? (double) absX / radiusX : 0;
                double zRatio = radiusZ > 0 ? (double) absZ / radiusZ : 0;
                boolean cornerCutOk = xRatio + zRatio <= 4.0 / 3.0;

                if (inRect && cornerCutOk) {
                    positions.add(new BlockPos(center.getX() + x, y, center.getZ() + z));
                }
            }
        }
        return positions;
    }

    // ========================================================================
    // Utility Methods
    // ========================================================================

    /**
     * Checks if a position is inside the given shape.
     * HIGH-03 fix: Uses both width and length for non-rectangular shapes.
     */
    public static boolean isInside(@Nonnull BlockPos pos, @Nonnull AreaShape shape,
            @Nonnull BlockPos center, @Nonnull AreaDimensions dims) {
        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();
        int minX = minOffset(dims.width());
        int maxX = maxOffset(dims.width());
        int minZ = minOffset(dims.length());
        int maxZ = maxOffset(dims.length());
        // HIGH-03 fix: Use separate radii for width (X) and length (Z)
        int radiusX = dims.width() / 2;
        int radiusZ = dims.length() / 2;

        return switch (shape) {
            case RECTANGULAR -> dx >= minX && dx <= maxX && dz >= minZ && dz <= maxZ;
            case CIRCULAR -> {
                // HIGH-03 fix: Ellipse equation
                if (dx < minX || dx > maxX || dz < minZ || dz > maxZ) {
                    yield false;
                }
                double radiusXSq = radiusX * radiusX;
                double radiusZSq = radiusZ * radiusZ;
                double ellipseVal = (radiusXSq > 0 ? (dx * dx) / radiusXSq : 0) +
                                   (radiusZSq > 0 ? (dz * dz) / radiusZSq : 0);
                yield ellipseVal <= 1.0;
            }
            case HEXAGONAL -> {
                // HIGH-03 fix: Scaled hexagon equation
                if (dx < minX || dx > maxX || dz < minZ || dz > maxZ) {
                    yield false;
                }
                double xRatio = radiusX > 0 ? (double) Math.abs(dx) / radiusX : 0;
                double zRatio = radiusZ > 0 ? (double) Math.abs(dz) / radiusZ : 0;
                yield xRatio + zRatio * 0.75 <= 1.0;
            }
            case OCTAGONAL -> {
                // HIGH-03 fix: Scaled octagon equation
                if (dx < minX || dx > maxX || dz < minZ || dz > maxZ) {
                    yield false;
                }
                int absX = Math.abs(dx);
                int absZ = Math.abs(dz);
                boolean inRect = absX <= radiusX && absZ <= radiusZ;
                double xRatio = radiusX > 0 ? (double) absX / radiusX : 0;
                double zRatio = radiusZ > 0 ? (double) absZ / radiusZ : 0;
                yield inRect && xRatio + zRatio <= 4.0 / 3.0;
            }
            case CUSTOM_NBT, PATH -> false; // Requires NBT data for accurate check
        };
    }

    /**
     * Gets the approximate block count for a shape.
     * HIGH-03 fix: Uses both width and length for ellipse calculation.
     */
    public static int estimateBlockCount(@Nonnull AreaShape shape, @Nonnull AreaDimensions dims) {
        int width = dims.width();
        int length = dims.length();

        return switch (shape) {
            case RECTANGULAR -> width * length;
            // HIGH-03 fix: Ellipse area = π * a * b where a = width/2, b = length/2
            case CIRCULAR -> (int) (Math.PI * (width / 2.0) * (length / 2.0));
            case HEXAGONAL -> (int) (width * length * 0.75);
            case OCTAGONAL -> (int) (width * length * 0.85);
            case CUSTOM_NBT -> 0;
            case PATH -> 0; // Requires NBT data for accurate estimate
        };
    }

    // ========================================================================
    // CUSTOM_NBT Shape Support
    // ========================================================================

    /**
     * Returns the minimum offset for centering a size around origin.
     */
    public static int minOffset(int size) {
        return -size / 2;
    }

    /**
     * Returns the maximum offset for centering a size around origin.
     */
    public static int maxOffset(int size) {
        return size - (size / 2) - 1;
    }

    /**
     * NBT key for the positions list in custom shape data.
     */
    public static final String NBT_POSITIONS = "positions";

    /**
     * Parses custom shape positions from NBT data.
     * Expected format:
     * <pre>
     * {
     *     "positions": [
     *         {x: 0, y: 0, z: 0},
     *         {x: 1, y: 0, z: 0},
     *         ...
     *     ]
     * }
     * </pre>
     *
     * @param center   The center position to offset relative positions
     * @param shapeNbt The NBT tag containing positions (may be null)
     * @return Set of absolute block positions, empty if nbt is null or invalid
     */
    @Nonnull
    public static Set<BlockPos> parseCustomNbtPositions(@Nonnull BlockPos center, @Nullable CompoundTag shapeNbt) {
        Objects.requireNonNull(center, "center");

        if (shapeNbt == null || !shapeNbt.contains(NBT_POSITIONS, Tag.TAG_LIST)) {
            return Objects.requireNonNull(Set.of());
        }

        ListTag positionsList = shapeNbt.getList(NBT_POSITIONS, Tag.TAG_COMPOUND);
        Set<BlockPos> result = new HashSet<>(positionsList.size());

        for (int i = 0; i < positionsList.size(); i++) {
            CompoundTag posTag = positionsList.getCompound(i);
            int x = posTag.getInt("x");
            int y = posTag.getInt("y");
            int z = posTag.getInt("z");
            result.add(center.offset(x, y, z));
        }

        return result;
    }

    /**
     * Creates NBT data for a custom shape from a set of positions.
     * Positions are stored relative to the center.
     *
     * @param center    The center position for relative calculation
     * @param positions The absolute positions to encode
     * @return CompoundTag containing the positions list
     */
    @Nonnull
    public static CompoundTag createCustomNbtPositions(@Nonnull BlockPos center, @Nonnull Set<BlockPos> positions) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(positions, "positions");

        CompoundTag result = new CompoundTag();
        ListTag positionsList = new ListTag();

        for (BlockPos pos : positions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX() - center.getX());
            posTag.putInt("y", pos.getY() - center.getY());
            posTag.putInt("z", pos.getZ() - center.getZ());
            positionsList.add(posTag);
        }

        result.put(NBT_POSITIONS, positionsList);
        return result;
    }

    /**
     * Generates floor positions for CUSTOM_NBT shape using the provided NBT data.
     *
     * @param center   The center position
     * @param dims     The dimensions (for bounds checking)
     * @param shapeNbt The custom shape NBT data
     * @return Set of floor positions from the custom shape
     */
    @Nonnull
    public static Set<BlockPos> generateCustomFloor(@Nonnull BlockPos center, @Nonnull AreaDimensions dims,
                                                     @Nullable CompoundTag shapeNbt) {
        Set<BlockPos> positions = parseCustomNbtPositions(center, shapeNbt);

        // Filter to only floor level (y = floorY)
        int floorY = dims.floorY();
        Set<BlockPos> floorPositions = new HashSet<>();
        for (BlockPos pos : positions) {
            if (pos.getY() == floorY) {
                floorPositions.add(pos);
            }
        }

        return floorPositions;
    }

    /**
     * Estimates block count for a custom NBT shape.
     *
     * @param shapeNbt The custom shape NBT data
     * @return Number of positions in the shape, or 0 if null/invalid
     */
    public static int estimateCustomBlockCount(@Nullable CompoundTag shapeNbt) {
        if (shapeNbt == null || !shapeNbt.contains(NBT_POSITIONS, Tag.TAG_LIST)) {
            return 0;
        }
        return shapeNbt.getList(NBT_POSITIONS, Tag.TAG_COMPOUND).size();
    }

    // ========================================================================
    // HIGH-02 fix: Bounds validation for CUSTOM_NBT shapes
    // ========================================================================

    /**
     * Validates that all positions in a custom NBT shape fall within the specified dimensions.
     * This prevents custom shapes from exceeding their declared area bounds.
     *
     * @param center   The center position
     * @param dims     The area dimensions
     * @param shapeNbt The custom shape NBT data
     * @return true if all positions are within bounds, false otherwise
     */
    public static boolean validateCustomNbtBounds(@Nonnull BlockPos center, @Nonnull AreaDimensions dims,
                                                   @Nullable CompoundTag shapeNbt) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(dims, "dims");

        if (shapeNbt == null) {
            return true; // No positions = valid (empty shape)
        }

        Set<BlockPos> positions = parseCustomNbtPositions(center, shapeNbt);
        if (positions.isEmpty()) {
            return true;
        }

        // Calculate bounds
        int minX = center.getX() + minOffset(dims.width());
        int maxX = center.getX() + maxOffset(dims.width());
        int minZ = center.getZ() + minOffset(dims.length());
        int maxZ = center.getZ() + maxOffset(dims.length());
        int minY = dims.floorY();
        int maxY = dims.floorY() + dims.height();

        // Check each position
        for (BlockPos pos : positions) {
            if (pos.getX() < minX || pos.getX() > maxX ||
                pos.getZ() < minZ || pos.getZ() > maxZ ||
                pos.getY() < minY || pos.getY() > maxY) {
                return false; // Position out of bounds
            }
        }

        return true;
    }

    /**
     * Finds positions in a custom NBT shape that fall outside the specified dimensions.
     * Useful for error reporting.
     *
     * @param center   The center position
     * @param dims     The area dimensions
     * @param shapeNbt The custom shape NBT data
     * @return List of out-of-bounds positions (empty if all valid)
     */
    @Nonnull
    public static java.util.List<BlockPos> findOutOfBoundsPositions(@Nonnull BlockPos center,
                                                                      @Nonnull AreaDimensions dims,
                                                                      @Nullable CompoundTag shapeNbt) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(dims, "dims");

        java.util.List<BlockPos> outOfBounds = new java.util.ArrayList<>();

        if (shapeNbt == null) {
            return outOfBounds;
        }

        Set<BlockPos> positions = parseCustomNbtPositions(center, shapeNbt);

        int minX = center.getX() + minOffset(dims.width());
        int maxX = center.getX() + maxOffset(dims.width());
        int minZ = center.getZ() + minOffset(dims.length());
        int maxZ = center.getZ() + maxOffset(dims.length());
        int minY = dims.floorY();
        int maxY = dims.floorY() + dims.height();

        for (BlockPos pos : positions) {
            if (pos.getX() < minX || pos.getX() > maxX ||
                pos.getZ() < minZ || pos.getZ() > maxZ ||
                pos.getY() < minY || pos.getY() > maxY) {
                outOfBounds.add(pos);
            }
        }

        return outOfBounds;
    }

    // ========================================================================
    // PATH Shape Support (UX-09 fix)
    // ========================================================================

    /**
     * NBT key for waypoints list in path shape data.
     */
    public static final String NBT_WAYPOINTS = "waypoints";

    /**
     * NBT key for path width (corridor half-width).
     */
    public static final String NBT_PATH_WIDTH = "pathWidth";

    /**
     * Generates floor positions for PATH shape using waypoints from NBT.
     * Creates a corridor of specified width around the path defined by waypoints.
     *
     * <p>UX-09 fix: Added to support race track and corridor use cases.
     *
     * @param center   The center position (first waypoint is relative to this)
     * @param dims     The dimensions (width is used as corridor width)
     * @param pathNbt  The path NBT data containing waypoints
     * @return Set of floor positions forming the path corridor
     */
    @Nonnull
    private static Set<BlockPos> generatePathFloor(@Nonnull BlockPos center, @Nonnull AreaDimensions dims,
                                                    @Nullable CompoundTag pathNbt) {
        java.util.List<BlockPos> waypoints = parsePathWaypoints(center, pathNbt);
        if (waypoints.size() < 2) {
            // Need at least 2 waypoints to form a path
            return Objects.requireNonNull(Set.of());
        }

        // Use width as corridor half-width, or get from NBT
        int corridorHalfWidth = dims.width() / 2;
        if (pathNbt != null && pathNbt.contains(NBT_PATH_WIDTH)) {
            corridorHalfWidth = pathNbt.getInt(NBT_PATH_WIDTH);
        }

        Set<BlockPos> positions = new HashSet<>();
        int y = dims.floorY();

        // Generate corridor between consecutive waypoints
        for (int i = 0; i < waypoints.size() - 1; i++) {
            BlockPos from = waypoints.get(i);
            BlockPos to = waypoints.get(i + 1);
            addCorridorPositions(positions, from, to, corridorHalfWidth, y);
        }

        return positions;
    }

    /**
     * Generates perimeter positions for PATH shape.
     * The perimeter consists of the outer edges of the corridor.
     *
     * @param center  The center position
     * @param dims    The dimensions
     * @param pathNbt The path NBT data
     * @return Set of perimeter positions
     */
    @Nonnull
    private static Set<BlockPos> generatePathPerimeter(@Nonnull BlockPos center, @Nonnull AreaDimensions dims,
                                                        @Nullable CompoundTag pathNbt) {
        Set<BlockPos> floorPositions = generatePathFloor(center, dims, pathNbt);
        if (floorPositions.isEmpty()) {
            return Objects.requireNonNull(Set.of());
        }

        // Use same perimeter logic as CUSTOM_NBT - blocks with missing neighbors
        Set<BlockPos> perimeter = new HashSet<>();
        for (BlockPos pos : floorPositions) {
            if (!floorPositions.contains(pos.north()) ||
                !floorPositions.contains(pos.south()) ||
                !floorPositions.contains(pos.east()) ||
                !floorPositions.contains(pos.west())) {
                perimeter.add(pos);
            }
        }
        return perimeter;
    }

    /**
     * Parses waypoints from path NBT data.
     *
     * @param center  The center position for relative offset
     * @param pathNbt The NBT containing waypoints
     * @return List of waypoint positions (absolute coordinates)
     */
    @Nonnull
    public static java.util.List<BlockPos> parsePathWaypoints(@Nonnull BlockPos center, @Nullable CompoundTag pathNbt) {
        Objects.requireNonNull(center, "center");

        java.util.List<BlockPos> waypoints = new java.util.ArrayList<>();

        if (pathNbt == null || !pathNbt.contains(NBT_WAYPOINTS, Tag.TAG_LIST)) {
            return waypoints;
        }

        ListTag waypointsList = pathNbt.getList(NBT_WAYPOINTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < waypointsList.size(); i++) {
            CompoundTag wpTag = waypointsList.getCompound(i);
            int x = wpTag.getInt("x");
            int y = wpTag.getInt("y");
            int z = wpTag.getInt("z");
            waypoints.add(center.offset(x, y, z));
        }

        return waypoints;
    }

    /**
     * Creates NBT data for a path shape from waypoints.
     *
     * @param center           The center position for relative calculation
     * @param waypoints        The absolute waypoint positions
     * @param corridorHalfWidth The corridor half-width
     * @return CompoundTag containing the path data
     */
    @Nonnull
    public static CompoundTag createPathNbt(@Nonnull BlockPos center, @Nonnull java.util.List<BlockPos> waypoints,
                                             int corridorHalfWidth) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(waypoints, "waypoints");

        CompoundTag result = new CompoundTag();
        ListTag waypointsList = new ListTag();

        for (BlockPos wp : waypoints) {
            CompoundTag wpTag = new CompoundTag();
            wpTag.putInt("x", wp.getX() - center.getX());
            wpTag.putInt("y", wp.getY() - center.getY());
            wpTag.putInt("z", wp.getZ() - center.getZ());
            waypointsList.add(wpTag);
        }

        result.put(NBT_WAYPOINTS, waypointsList);
        result.putInt(NBT_PATH_WIDTH, corridorHalfWidth);
        return result;
    }

    /**
     * Adds corridor positions between two waypoints using Bresenham-like line drawing.
     * Each point on the line is expanded to a circle of the specified radius.
     *
     * @param positions        The set to add positions to
     * @param from             Starting waypoint
     * @param to               Ending waypoint
     * @param corridorHalfWidth Half-width of the corridor
     * @param y                Y level for floor
     */
    private static void addCorridorPositions(Set<BlockPos> positions, BlockPos from, BlockPos to,
                                              int corridorHalfWidth, int y) {
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int sx = from.getX() < to.getX() ? 1 : -1;
        int sz = from.getZ() < to.getZ() ? 1 : -1;
        int err = dx - dz;

        int x = from.getX();
        int z = from.getZ();

        while (true) {
            // Add a filled circle at this point
            addCircleAtPoint(positions, x, z, corridorHalfWidth, y);

            if (x == to.getX() && z == to.getZ()) {
                break;
            }

            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
    }

    /**
     * Adds a filled circle of positions at the given point.
     */
    private static void addCircleAtPoint(Set<BlockPos> positions, int cx, int cz, int radius, int y) {
        int radiusSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radiusSq) {
                    positions.add(new BlockPos(cx + dx, y, cz + dz));
                }
            }
        }
    }

    /**
     * Estimates block count for a path shape.
     *
     * @param pathNbt The path NBT data
     * @param dims    The dimensions (for corridor width)
     * @return Estimated number of blocks
     */
    public static int estimatePathBlockCount(@Nullable CompoundTag pathNbt, @Nonnull AreaDimensions dims) {
        if (pathNbt == null || !pathNbt.contains(NBT_WAYPOINTS, Tag.TAG_LIST)) {
            return 0;
        }

        ListTag waypointsList = pathNbt.getList(NBT_WAYPOINTS, Tag.TAG_COMPOUND);
        if (waypointsList.size() < 2) {
            return 0;
        }

        int corridorHalfWidth = dims.width() / 2;
        if (pathNbt.contains(NBT_PATH_WIDTH)) {
            corridorHalfWidth = pathNbt.getInt(NBT_PATH_WIDTH);
        }

        // Estimate: path length * corridor width * π/4 (for circular cross-section)
        int totalLength = 0;
        for (int i = 0; i < waypointsList.size() - 1; i++) {
            CompoundTag wp1 = waypointsList.getCompound(i);
            CompoundTag wp2 = waypointsList.getCompound(i + 1);
            int dx = wp2.getInt("x") - wp1.getInt("x");
            int dz = wp2.getInt("z") - wp1.getInt("z");
            totalLength += (int) Math.sqrt(dx * dx + dz * dz);
        }

        // Area ≈ length * width * π/4 (circular corridor)
        return (int) (totalLength * corridorHalfWidth * 2 * Math.PI / 4);
    }
}
