package com.devmod.arena.spawn;

import java.util.UUID;

/**
 * Represents a spawn slot position in the arena.
 */
public record SpawnSlot(
    UUID id,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    SpawnSlotType type,
    int teamIndex
) {
    /**
     * Create a spawn slot with auto-generated ID.
     */
    public SpawnSlot(double x, double y, double z, float yaw, float pitch, SpawnSlotType type, int teamIndex) {
        this(UUID.randomUUID(), x, y, z, yaw, pitch, type, teamIndex);
    }

    /**
     * Create a simple spawn slot at a position.
     */
    public static SpawnSlot at(double x, double y, double z) {
        return new SpawnSlot(x, y, z, 0.0f, 0.0f, SpawnSlotType.PLAYER, 0);
    }

    /**
     * Create a team spawn slot.
     */
    public static SpawnSlot forTeam(double x, double y, double z, int teamIndex) {
        return new SpawnSlot(x, y, z, 0.0f, 0.0f, SpawnSlotType.TEAM, teamIndex);
    }

    /**
     * Calculate distance to another spawn slot.
     */
    public double distanceTo(SpawnSlot other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Calculate horizontal distance (ignoring Y) to another spawn slot.
     */
    public double horizontalDistanceTo(SpawnSlot other) {
        double dx = this.x - other.x;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Get position as an array [x, y, z].
     */
    public double[] getPosition() {
        return new double[]{x, y, z};
    }

    /**
     * Get rotation as an array [yaw, pitch].
     */
    public float[] getRotation() {
        return new float[]{yaw, pitch};
    }

    /**
     * Create a copy with different position.
     */
    public SpawnSlot withPosition(double newX, double newY, double newZ) {
        return new SpawnSlot(id, newX, newY, newZ, yaw, pitch, type, teamIndex);
    }

    /**
     * Create a copy with different rotation.
     */
    public SpawnSlot withRotation(float newYaw, float newPitch) {
        return new SpawnSlot(id, x, y, z, newYaw, newPitch, type, teamIndex);
    }

    /**
     * Create a copy with different team.
     */
    public SpawnSlot withTeam(int newTeamIndex) {
        return new SpawnSlot(id, x, y, z, yaw, pitch, type, newTeamIndex);
    }

    /**
     * Types of spawn slots.
     */
    public enum SpawnSlotType {
        /** Individual player spawn */
        PLAYER,
        /** Team spawn area */
        TEAM,
        /** Spectator spawn */
        SPECTATOR,
        /** Waiting area spawn */
        WAITING,
        /** VIP/special spawn */
        VIP
    }
}
