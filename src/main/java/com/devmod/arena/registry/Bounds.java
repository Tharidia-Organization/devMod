package com.devmod.arena.registry;

/**
 * Simple axis-aligned bounds helper for arena validation.
 */
public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int originX, int originZ) {

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public boolean contains(int[] pos) {
        return pos != null && pos.length == 3 && contains(pos[0], pos[1], pos[2]);
    }

    public boolean containsAabb(int[] min, int[] max) {
        if (min == null || max == null || min.length != 3 || max.length != 3) return false;
        return min[0] >= minX && max[0] <= maxX
            && min[1] >= minY && max[1] <= maxY
            && min[2] >= minZ && max[2] <= maxZ;
    }

    /**
     * Half of the maximum horizontal span (used for hazard radius checks).
     */
    public int maxHorizontalRadius() {
        int spanX = maxX - minX;
        int spanZ = maxZ - minZ;
        return Math.max(spanX, spanZ) / 2;
    }
}
