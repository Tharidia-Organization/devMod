package com.devmod.arena.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record ArenaHandle(
    UUID arenaId,
    UUID instanceId,
    String templateId,
    int templateVersion,
    String policyId,
    int policyVersion,
    AABB bounds,
    int originX,
    int originY,
    int originZ,
    List<BlockPos> playerSpawnPositions,
    List<BlockPos> mobSpawnPositions,
    Instant createdAt
) {
    /**
     * Returns the primary player spawn position.
     * Falls back to ZERO if no spawn positions defined.
     */
    public BlockPos primaryPlayerSpawn() {
        return playerSpawnPositions.isEmpty() ? BlockPos.ZERO : playerSpawnPositions.get(0);
    }

    /**
     * Returns the first mob spawn position, or null if none.
     */
    public BlockPos primaryMobSpawn() {
        return mobSpawnPositions.isEmpty() ? null : mobSpawnPositions.get(0);
    }

    /**
     * Checks if a position is within the arena bounds.
     */
    public boolean contains(int x, int y, int z) {
        return bounds.contains(x, y, z);
    }

    /**
     * Returns the center of the arena bounds.
     */
    public BlockPos center() {
        return new BlockPos(
            (bounds.minX() + bounds.maxX()) / 2,
            (bounds.minY() + bounds.maxY()) / 2,
            (bounds.minZ() + bounds.maxZ()) / 2
        );
    }

    /**
     * Returns the volume of the arena in blocks.
     */
    public int volume() {
        return bounds.volume();
    }

    /**
     * Axis-aligned bounding box for arena bounds.
     * Compatible with Minecraft's AABB concept.
     */
    public record AABB(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {
        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeY() {
            return maxY - minY + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }

        public int volume() {
            return sizeX() * sizeY() * sizeZ();
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
        }

        public AABB expand(int amount) {
            return new AABB(
                minX - amount,
                minY - amount,
                minZ - amount,
                maxX + amount,
                maxY + amount,
                maxZ + amount
            );
        }
    }

    /**
     * Block position with x, y, z coordinates.
     * Compatible with Minecraft's BlockPos concept.
     */
    public record BlockPos(int x, int y, int z) {
        public static final BlockPos ZERO = new BlockPos(0, 0, 0);

        public long asLong() {
            return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        }

        public static BlockPos fromLong(long packed) {
            int x = (int)(packed >> 38);
            int z = (int)(packed << 26 >> 38);
            int y = (int)(packed << 52 >> 52);
            return new BlockPos(x, y, z);
        }

        public double distanceSquared(BlockPos other) {
            int dx = x - other.x;
            int dy = y - other.y;
            int dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }

        public double distance(BlockPos other) {
            return Math.sqrt(distanceSquared(other));
        }

        public BlockPos offset(int dx, int dy, int dz) {
            return new BlockPos(x + dx, y + dy, z + dz);
        }

        public BlockPos above() {
            return new BlockPos(x, y + 1, z);
        }

        public BlockPos below() {
            return new BlockPos(x, y - 1, z);
        }
    }

    /**
     * Builder for ArenaHandle.
     */
    public static class Builder {
        private UUID arenaId;
        private UUID instanceId;
        private String templateId;
        private int templateVersion;
        private String policyId;
        private int policyVersion;
        private AABB bounds;
        private int originX;
        private int originY;
        private int originZ;
        private List<BlockPos> playerSpawnPositions = List.of();
        private List<BlockPos> mobSpawnPositions = List.of();
        private Instant createdAt = Instant.now();

        public Builder arenaId(UUID arenaId) {
            this.arenaId = arenaId;
            return this;
        }

        public Builder instanceId(UUID instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder templateVersion(int templateVersion) {
            this.templateVersion = templateVersion;
            return this;
        }

        public Builder policyId(String policyId) {
            this.policyId = policyId;
            return this;
        }

        public Builder policyVersion(int policyVersion) {
            this.policyVersion = policyVersion;
            return this;
        }

        public Builder bounds(AABB bounds) {
            this.bounds = bounds;
            return this;
        }

        public Builder bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            return this;
        }

        public Builder origin(int originX, int originY, int originZ) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            return this;
        }

        public Builder playerSpawnPositions(List<BlockPos> positions) {
            this.playerSpawnPositions = positions;
            return this;
        }

        public Builder mobSpawnPositions(List<BlockPos> positions) {
            this.mobSpawnPositions = positions;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ArenaHandle build() {
            if (arenaId == null) throw new IllegalStateException("arenaId required");
            if (instanceId == null) throw new IllegalStateException("instanceId required");
            if (templateId == null) throw new IllegalStateException("templateId required");
            if (policyId == null) throw new IllegalStateException("policyId required");
            if (bounds == null) throw new IllegalStateException("bounds required");

            return new ArenaHandle(
                arenaId,
                instanceId,
                templateId,
                templateVersion,
                policyId,
                policyVersion,
                bounds,
                originX,
                originY,
                originZ,
                playerSpawnPositions,
                mobSpawnPositions,
                createdAt
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
