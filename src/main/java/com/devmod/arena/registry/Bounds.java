package com.devmod.arena.registry;

import javax.annotation.Nullable;

/**
 * Bounds helper for arena validation supporting rectangular, circular, and ring shapes.
 */
public record Bounds(
    int minX, int minY, int minZ,
    int maxX, int maxY, int maxZ,
    int originX, int originZ,
    ArenaTemplate.ArenaShape shape,
    @Nullable Integer ringInnerRadius
) {
    /**
     * Canonical constructor for rectangular bounds (backwards compatibility).
     */
    public Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int originX, int originZ) {
        this(minX, minY, minZ, maxX, maxY, maxZ, originX, originZ,
             ArenaTemplate.ArenaShape.RECTANGULAR, null);
    }

    /**
     * Checks if a position is within the bounds, respecting the arena shape.
     */
    public boolean contains(int x, int y, int z) {
        // Y bounds are always rectangular
        if (y < minY || y > maxY) {
            return false;
        }

        // Handle shape-aware horizontal bounds
        ArenaTemplate.ArenaShape effectiveShape = shape != null ? shape : ArenaTemplate.ArenaShape.RECTANGULAR;

        return switch (effectiveShape) {
            case RECTANGULAR -> containsRectangular(x, z);
            case CIRCULAR -> containsCircular(x, z);
            case RING -> containsRing(x, z);
        };
    }

    private boolean containsRectangular(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private boolean containsCircular(int x, int z) {
        int dx = x - originX;
        int dz = z - originZ;
        int radius = maxHorizontalRadius();
        return (dx * dx + dz * dz) <= (radius * radius);
    }

    private boolean containsRing(int x, int z) {
        int dx = x - originX;
        int dz = z - originZ;
        int distSq = dx * dx + dz * dz;
        int outerRadius = maxHorizontalRadius();
        int innerRadius = ringInnerRadius != null ? ringInnerRadius : outerRadius / 2;
        return distSq <= (outerRadius * outerRadius) && distSq >= (innerRadius * innerRadius);
    }

    public boolean contains(int[] pos) {
        return pos != null && pos.length == 3 && contains(pos[0], pos[1], pos[2]);
    }

    public boolean containsAabb(int[] min, int[] max) {
        if (min == null || max == null || min.length != 3 || max.length != 3) return false;
        // For AABB containment, use rectangular bounds (safe enclosure)
        return min[0] >= minX && max[0] <= maxX
            && min[1] >= minY && max[1] <= maxY
            && min[2] >= minZ && max[2] <= maxZ;
    }

    /**
     * Half of the maximum horizontal span (used for hazard radius checks).
     */
    public int maxHorizontalRadius() {
        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;
        return Math.max(spanX, spanZ) / 2;
    }

    /**
     * Creates rectangular bounds.
     */
    public static Bounds rectangular(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int originX, int originZ) {
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ, originX, originZ,
                          ArenaTemplate.ArenaShape.RECTANGULAR, null);
    }

    /**
     * Creates circular bounds.
     */
    public static Bounds circular(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int originX, int originZ) {
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ, originX, originZ,
                          ArenaTemplate.ArenaShape.CIRCULAR, null);
    }

    /**
     * Creates ring bounds with specified inner radius.
     */
    public static Bounds ring(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int originX, int originZ, int innerRadius) {
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ, originX, originZ,
                          ArenaTemplate.ArenaShape.RING, innerRadius);
    }

    /**
     * Creates bounds from an ArenaTemplate.
     */
    public static Bounds fromTemplate(ArenaTemplate template, int originX, int originY, int originZ) {
        Integer sizeXVal = template.sizeX();
        Integer sizeZVal = template.sizeZ();
        int sizeX = sizeXVal != null ? sizeXVal : template.size();
        int sizeZ = sizeZVal != null ? sizeZVal : template.size();
        int minX = originX - (sizeX / 2);
        int maxX = originX + (sizeX - (sizeX / 2) - 1);
        int minZ = originZ - (sizeZ / 2);
        int maxZ = originZ + (sizeZ - (sizeZ / 2) - 1);

        int minY = originY;
        int maxY = originY;
        if (template.floor() != null) {
            minY = template.floor().y();
        }
        if (template.ceiling() != null && template.ceiling().enabled()) {
            maxY = template.ceiling().y() + template.ceiling().thickness();
        } else if (template.walls() != null && template.walls().enabled()) {
            maxY = template.walls().startY() + template.walls().height();
        } else {
            maxY = minY + 64; // Default height
        }

        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ, originX, originZ, shape, template.ringInnerRadius());
    }
}
