package com.devmod.arena.spawn;

import java.util.UUID;

/**
 * Represents a forbidden zone where spawning is not allowed.
 * DD47: Forbidden zones are checked during spawn slot resolution.
 */
public record ForbiddenZone(
    UUID id,
    double minX,
    double minY,
    double minZ,
    double maxX,
    double maxY,
    double maxZ,
    String description  // DD50: renamed from 'reason' to 'description'
) {
    /**
     * Create a forbidden zone with auto-generated ID.
     */
    public ForbiddenZone(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, String description) {
        this(UUID.randomUUID(), minX, minY, minZ, maxX, maxY, maxZ, description);
    }

    /**
     * Create a forbidden zone with default reason.
     */
    public static ForbiddenZone box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new ForbiddenZone(minX, minY, minZ, maxX, maxY, maxZ, "forbidden zone");
    }

    /**
     * Create a cylindrical forbidden zone (approximated as a box).
     */
    public static ForbiddenZone cylinder(double centerX, double centerZ, double radius, double minY, double maxY, String description) {
        return new ForbiddenZone(
            centerX - radius, minY, centerZ - radius,
            centerX + radius, maxY, centerZ + radius,
            description
        );
    }

    /**
     * Create a spherical forbidden zone (approximated as a box).
     */
    public static ForbiddenZone sphere(double centerX, double centerY, double centerZ, double radius, String description) {
        return new ForbiddenZone(
            centerX - radius, centerY - radius, centerZ - radius,
            centerX + radius, centerY + radius, centerZ + radius,
            description
        );
    }

    /**
     * Validate that the zone bounds are correct.
     */
    public ForbiddenZone {
        // Normalize bounds (swap if necessary)
        if (minX > maxX) {
            double temp = minX;
            minX = maxX;
            maxX = temp;
        }
        if (minY > maxY) {
            double temp = minY;
            minY = maxY;
            maxY = temp;
        }
        if (minZ > maxZ) {
            double temp = minZ;
            minZ = maxZ;
            maxZ = temp;
        }
    }

    /**
     * Check if a point is inside this forbidden zone.
     */
    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }

    /**
     * Check if a spawn slot is inside this forbidden zone.
     */
    public boolean contains(SpawnSlot slot) {
        return contains(slot.x(), slot.y(), slot.z());
    }

    /**
     * Check if this zone overlaps with another zone.
     */
    public boolean overlaps(ForbiddenZone other) {
        return this.minX <= other.maxX && this.maxX >= other.minX &&
               this.minY <= other.maxY && this.maxY >= other.minY &&
               this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }

    /**
     * Get the center of this zone.
     */
    public double[] getCenter() {
        return new double[]{
            (minX + maxX) / 2,
            (minY + maxY) / 2,
            (minZ + maxZ) / 2
        };
    }

    /**
     * Get the volume of this zone.
     */
    public double getVolume() {
        return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
    }

    /**
     * Expand the zone by a margin.
     */
    public ForbiddenZone expand(double margin) {
        return new ForbiddenZone(
            id,
            minX - margin, minY - margin, minZ - margin,
            maxX + margin, maxY + margin, maxZ + margin,
            description
        );
    }
}
