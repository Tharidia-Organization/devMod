package com.devmod.arena.zone;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Represents a distinct zone within an arena with its own environmental conditions.
 * Each zone can have different biome, floor, lighting, and time settings.
 */
public record ArenaZone(
    String name,
    ZoneShape shape,
    ZoneBounds bounds,
    ZoneEnvironment environment,
    List<ResourceLocation> assignedMobs,
    int priority
) {
    /**
     * Creates a simple rectangular zone with default environment.
     */
    public static ArenaZone rectangular(String name, int x1, int z1, int x2, int z2) {
        return new ArenaZone(
            name,
            ZoneShape.RECTANGULAR,
            new ZoneBounds(x1, z1, x2, z2, Optional.empty()),
            ZoneEnvironment.DEFAULT,
            List.of(),
            0
        );
    }

    /**
     * Creates a circular zone centered at origin.
     */
    public static ArenaZone circular(String name, int radius) {
        return new ArenaZone(
            name,
            ZoneShape.CIRCULAR,
            new ZoneBounds(-radius, -radius, radius, radius, Optional.of(radius)),
            ZoneEnvironment.DEFAULT,
            List.of(),
            0
        );
    }

    /**
     * Creates a zone covering the entire arena.
     */
    public static ArenaZone fullArena(String name, int arenaRadius) {
        return circular(name, arenaRadius);
    }

    /**
     * Returns a copy of this zone with the specified environment.
     */
    public ArenaZone withEnvironment(ZoneEnvironment env) {
        return new ArenaZone(name, shape, bounds, env, assignedMobs, priority);
    }

    /**
     * Returns a copy of this zone with assigned mobs.
     */
    public ArenaZone withMobs(List<ResourceLocation> mobs) {
        return new ArenaZone(name, shape, bounds, environment, mobs, priority);
    }

    /**
     * Returns a copy of this zone with the specified priority.
     */
    public ArenaZone withPriority(int newPriority) {
        return new ArenaZone(name, shape, bounds, environment, assignedMobs, newPriority);
    }

    /**
     * Checks if a position is within this zone.
     */
    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getZ());
    }

    /**
     * Checks if coordinates are within this zone.
     */
    public boolean contains(double x, double z) {
        return switch (shape) {
            case RECTANGULAR -> bounds.containsRectangular(x, z);
            case CIRCULAR -> bounds.containsCircular(x, z);
            case RING -> bounds.containsRing(x, z);
        };
    }

    /**
     * Gets the center position of this zone.
     */
    public BlockPos getCenter() {
        return bounds.getCenter();
    }

    /**
     * Gets the area of this zone in blocks squared.
     */
    public double getArea() {
        return switch (shape) {
            case RECTANGULAR -> bounds.getRectangularArea();
            case CIRCULAR -> bounds.getCircularArea();
            case RING -> bounds.getRingArea();
        };
    }

    /**
     * Gets a random position within this zone.
     */
    public BlockPos getRandomPosition(java.util.Random random, int yLevel) {
        return switch (shape) {
            case RECTANGULAR -> bounds.getRandomRectangular(random, yLevel);
            case CIRCULAR -> bounds.getRandomCircular(random, yLevel);
            case RING -> bounds.getRandomRing(random, yLevel);
        };
    }

    /**
     * Converts this zone to an AABB for collision/rendering.
     */
    public AABB toAABB(int minY, int maxY) {
        return new AABB(
            bounds.x1(), minY, bounds.z1(),
            bounds.x2(), maxY, bounds.z2()
        );
    }

    // ============== Nested Types ==============

    /**
     * Shape of the zone.
     */
    public enum ZoneShape {
        RECTANGULAR,
        CIRCULAR,
        RING  // Donut shape (outer radius minus inner radius)
    }

    /**
     * Bounds definition for a zone.
     */
    public record ZoneBounds(
        int x1, int z1,
        int x2, int z2,
        Optional<Integer> radius,      // For circular/ring shapes (outer radius)
        Optional<Integer> innerRadius  // For ring shapes only (configurable inner radius)
    ) {
        /**
         * Canonical constructor that defaults innerRadius to empty if not provided.
         */
        public ZoneBounds(int x1, int z1, int x2, int z2, Optional<Integer> radius) {
            this(x1, z1, x2, z2, radius, Optional.empty());
        }
        /**
         * Gets the center of the bounds.
         */
        public BlockPos getCenter() {
            return new BlockPos((x1 + x2) / 2, 0, (z1 + z2) / 2);
        }

        /**
         * Gets the width of the bounds.
         */
        public int getWidth() {
            return Math.abs(x2 - x1);
        }

        /**
         * Gets the depth of the bounds.
         */
        public int getDepth() {
            return Math.abs(z2 - z1);
        }

        public boolean containsRectangular(double x, double z) {
            return x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }

        public boolean containsCircular(double x, double z) {
            if (radius.isEmpty()) return containsRectangular(x, z);
            BlockPos center = getCenter();
            double dx = x - center.getX();
            double dz = z - center.getZ();
            return dx * dx + dz * dz <= radius.get() * radius.get();
        }

        public boolean containsRing(double x, double z) {
            // Ring uses radius as outer, innerRadius as inner (defaults to outer/2 if not set)
            if (radius.isEmpty()) return containsRectangular(x, z);
            BlockPos center = getCenter();
            double dx = x - center.getX();
            double dz = z - center.getZ();
            double distSq = dx * dx + dz * dz;
            int outer = radius.get();
            int inner = innerRadius.orElse(outer / 2);
            return distSq <= outer * outer && distSq >= inner * inner;
        }

        public double getRectangularArea() {
            return (double) getWidth() * getDepth();
        }

        public double getCircularArea() {
            if (radius.isEmpty()) return getRectangularArea();
            return Math.PI * radius.get() * radius.get();
        }

        public double getRingArea() {
            if (radius.isEmpty()) return getRectangularArea();
            int outer = radius.get();
            int inner = innerRadius.orElse(outer / 2);
            return Math.PI * (outer * outer - inner * inner);
        }

        public BlockPos getRandomRectangular(java.util.Random random, int y) {
            // Use long arithmetic to prevent integer overflow with large coordinates
            long xLong = (long) x1 + random.nextInt(Math.max(1, getWidth()));
            long zLong = (long) z1 + random.nextInt(Math.max(1, getDepth()));
            // Clamp to valid int range
            int x = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, xLong));
            int z = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, zLong));
            return new BlockPos(x, y, z);
        }

        public BlockPos getRandomCircular(java.util.Random random, int y) {
            if (radius.isEmpty()) return getRandomRectangular(random, y);
            BlockPos center = getCenter();
            double angle = random.nextDouble() * 2 * Math.PI;
            double r = Math.sqrt(random.nextDouble()) * radius.get();
            int x = center.getX() + (int) (r * Math.cos(angle));
            int z = center.getZ() + (int) (r * Math.sin(angle));
            return new BlockPos(x, y, z);
        }

        public BlockPos getRandomRing(java.util.Random random, int y) {
            if (radius.isEmpty()) return getRandomRectangular(random, y);
            BlockPos center = getCenter();
            int outer = radius.get();
            int inner = innerRadius.orElse(outer / 2);
            double angle = random.nextDouble() * 2 * Math.PI;
            // Uniform distribution in ring
            double r = Math.sqrt(random.nextDouble() * (outer * outer - inner * inner) + inner * inner);
            int x = center.getX() + (int) (r * Math.cos(angle));
            int z = center.getZ() + (int) (r * Math.sin(angle));
            return new BlockPos(x, y, z);
        }

        /**
         * Creates rectangular bounds.
         */
        public static ZoneBounds rect(int x1, int z1, int x2, int z2) {
            return new ZoneBounds(
                Math.min(x1, x2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(z1, z2),
                Optional.empty()
            );
        }

        /**
         * Creates circular bounds centered at origin.
         */
        public static ZoneBounds circle(int radius) {
            return new ZoneBounds(-radius, -radius, radius, radius, Optional.of(radius));
        }

        /**
         * Creates circular bounds centered at a point.
         */
        public static ZoneBounds circle(int centerX, int centerZ, int radius) {
            return new ZoneBounds(
                centerX - radius, centerZ - radius,
                centerX + radius, centerZ + radius,
                Optional.of(radius)
            );
        }

        /**
         * Creates ring bounds centered at origin with configurable inner radius.
         *
         * @param outerRadius The outer radius of the ring
         * @param innerRadius The inner radius of the ring (must be less than outerRadius)
         * @return ZoneBounds configured as a ring shape
         */
        public static ZoneBounds ring(int outerRadius, int innerRadius) {
            return new ZoneBounds(
                -outerRadius, -outerRadius,
                outerRadius, outerRadius,
                Optional.of(outerRadius),
                Optional.of(Math.min(innerRadius, outerRadius - 1))
            );
        }

        /**
         * Creates ring bounds centered at a point with configurable inner radius.
         *
         * @param centerX The X coordinate of the ring center
         * @param centerZ The Z coordinate of the ring center
         * @param outerRadius The outer radius of the ring
         * @param innerRadius The inner radius of the ring (must be less than outerRadius)
         * @return ZoneBounds configured as a ring shape
         */
        public static ZoneBounds ring(int centerX, int centerZ, int outerRadius, int innerRadius) {
            return new ZoneBounds(
                centerX - outerRadius, centerZ - outerRadius,
                centerX + outerRadius, centerZ + outerRadius,
                Optional.of(outerRadius),
                Optional.of(Math.min(innerRadius, outerRadius - 1))
            );
        }
    }
}
