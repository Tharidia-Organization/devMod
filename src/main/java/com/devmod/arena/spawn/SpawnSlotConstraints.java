package com.devmod.arena.spawn;

/**
 * Constraints for spawn slot placement.
 * DD47: SpawnSlots Distance - melee 3-15, ranged 12-30, LOS+ground+forbidden.
 *
 * @param minDistance Minimum distance from other spawn slots
 * @param maxDistance Maximum distance from other spawn slots
 * @param requireLineOfSight Whether line of sight is required between spawns
 * @param requireGroundCheck Whether ground validation is required
 * @param checkForbiddenZones Whether to check against forbidden zones
 */
public record SpawnSlotConstraints(
    double minDistance,
    double maxDistance,
    boolean requireLineOfSight,
    boolean requireGroundCheck,
    boolean checkForbiddenZones
) {
    /**
     * Default constraints for melee combat.
     * DD47: melee 3-15 blocks distance.
     */
    public static final SpawnSlotConstraints MELEE_DEFAULTS = new SpawnSlotConstraints(
        3.0,   // minDistance
        15.0,  // maxDistance
        true,  // requireLineOfSight
        true,  // requireGroundCheck
        true   // checkForbiddenZones
    );

    /**
     * Default constraints for ranged combat.
     * DD47: ranged 12-30 blocks distance.
     */
    public static final SpawnSlotConstraints RANGED_DEFAULTS = new SpawnSlotConstraints(
        12.0,  // minDistance
        30.0,  // maxDistance
        true,  // requireLineOfSight
        true,  // requireGroundCheck
        true   // checkForbiddenZones
    );

    /**
     * Relaxed constraints for team spawns (same team, can be closer).
     */
    public static final SpawnSlotConstraints TEAM_SPAWN = new SpawnSlotConstraints(
        2.0,   // minDistance
        10.0,  // maxDistance
        false, // requireLineOfSight
        true,  // requireGroundCheck
        true   // checkForbiddenZones
    );

    /**
     * Constraints for spectator spawn (no combat requirements).
     */
    public static final SpawnSlotConstraints SPECTATOR = new SpawnSlotConstraints(
        1.0,   // minDistance
        50.0,  // maxDistance
        false, // requireLineOfSight
        false, // requireGroundCheck
        false  // checkForbiddenZones
    );

    /**
     * Validate that constraints are sensible.
     */
    public SpawnSlotConstraints {
        if (minDistance < 0) {
            throw new IllegalArgumentException("minDistance cannot be negative: " + minDistance);
        }
        if (maxDistance < 0) {
            throw new IllegalArgumentException("maxDistance cannot be negative: " + maxDistance);
        }
        if (minDistance > maxDistance) {
            throw new IllegalArgumentException(
                "minDistance (" + minDistance + ") cannot be greater than maxDistance (" + maxDistance + ")"
            );
        }
    }

    /**
     * Check if a given distance is within the allowed range.
     */
    public boolean isDistanceValid(double distance) {
        return distance >= minDistance && distance <= maxDistance;
    }

    /**
     * Create constraints for a specific combat type.
     */
    public static SpawnSlotConstraints forCombatType(CombatType type) {
        return switch (type) {
            case MELEE -> MELEE_DEFAULTS;
            case RANGED -> RANGED_DEFAULTS;
            case MIXED -> new SpawnSlotConstraints(
                5.0,   // Compromise between melee and ranged
                25.0,
                true,
                true,
                true
            );
        };
    }

    /**
     * Create a builder for custom constraints.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new constraints instance with modified min distance.
     */
    public SpawnSlotConstraints withMinDistance(double newMinDistance) {
        return new SpawnSlotConstraints(
            newMinDistance, maxDistance, requireLineOfSight, requireGroundCheck, checkForbiddenZones
        );
    }

    /**
     * Create a new constraints instance with modified max distance.
     */
    public SpawnSlotConstraints withMaxDistance(double newMaxDistance) {
        return new SpawnSlotConstraints(
            minDistance, newMaxDistance, requireLineOfSight, requireGroundCheck, checkForbiddenZones
        );
    }

    /**
     * Create a new constraints instance with modified LOS requirement.
     */
    public SpawnSlotConstraints withLineOfSight(boolean required) {
        return new SpawnSlotConstraints(
            minDistance, maxDistance, required, requireGroundCheck, checkForbiddenZones
        );
    }

    /**
     * Combat type enum for spawn configuration.
     */
    public enum CombatType {
        MELEE,
        RANGED,
        MIXED
    }

    /**
     * Builder for creating custom SpawnSlotConstraints.
     */
    public static class Builder {
        private double minDistance = 3.0;
        private double maxDistance = 15.0;
        private boolean requireLineOfSight = true;
        private boolean requireGroundCheck = true;
        private boolean checkForbiddenZones = true;

        public Builder minDistance(double distance) {
            this.minDistance = distance;
            return this;
        }

        public Builder maxDistance(double distance) {
            this.maxDistance = distance;
            return this;
        }

        public Builder requireLineOfSight(boolean required) {
            this.requireLineOfSight = required;
            return this;
        }

        public Builder requireGroundCheck(boolean required) {
            this.requireGroundCheck = required;
            return this;
        }

        public Builder checkForbiddenZones(boolean check) {
            this.checkForbiddenZones = check;
            return this;
        }

        public Builder fromCombatType(CombatType type) {
            SpawnSlotConstraints defaults = SpawnSlotConstraints.forCombatType(type);
            this.minDistance = defaults.minDistance();
            this.maxDistance = defaults.maxDistance();
            this.requireLineOfSight = defaults.requireLineOfSight();
            this.requireGroundCheck = defaults.requireGroundCheck();
            this.checkForbiddenZones = defaults.checkForbiddenZones();
            return this;
        }

        public SpawnSlotConstraints build() {
            return new SpawnSlotConstraints(
                minDistance, maxDistance, requireLineOfSight, requireGroundCheck, checkForbiddenZones
            );
        }
    }
}
