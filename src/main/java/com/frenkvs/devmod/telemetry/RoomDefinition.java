package com.frenkvs.devmod.telemetry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public record RoomDefinition(String id, String dimension, BlockPos min, BlockPos max) {
    /**
     * Constructor without dimension (defaults to overworld for backwards compatibility)
     */
    public RoomDefinition(String id, BlockPos min, BlockPos max) {
        this(id, "minecraft:overworld", min, max);
    }

    public boolean contains(ServerLevel level, BlockPos pos) {
        // Check dimension if specified
        if (dimension != null && !dimension.isEmpty()) {
            String levelDim = level.dimension().location().toString();
            if (!dimension.equals(levelDim)) return false;
        }
        return pos.getX() >= Math.min(min.getX(), max.getX()) && pos.getX() <= Math.max(min.getX(), max.getX())
                && pos.getY() >= Math.min(min.getY(), max.getY()) && pos.getY() <= Math.max(min.getY(), max.getY())
                && pos.getZ() >= Math.min(min.getZ(), max.getZ()) && pos.getZ() <= Math.max(min.getZ(), max.getZ());
    }
}
