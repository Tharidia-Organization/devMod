package com.devmod.endurance;

import com.devmod.arena.api.ArenaHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * Runtime arena context derived from an ArenaHandle plus the active ServerLevel.
 * Replaces legacy ArenaManager.Arena adapter usage.
 */
public final class ArenaContext {

    private final ServerLevel level;
    private final ArenaHandle handle;
    private final AABB bounds;
    private final BlockPos center;
    private final int size;

    public ArenaContext(ServerLevel level, ArenaHandle handle) {
        this.level = Objects.requireNonNull(level, "level");
        this.handle = Objects.requireNonNull(handle, "handle");
        ArenaHandle.AABB handleBounds = Objects.requireNonNull(handle.bounds(), "handle.bounds");
        this.bounds = new AABB(
            handleBounds.minX(), handleBounds.minY(), handleBounds.minZ(),
            handleBounds.maxX() + 1, handleBounds.maxY() + 1, handleBounds.maxZ() + 1
        );
        this.center = new BlockPos(
            (handleBounds.minX() + handleBounds.maxX()) / 2,
            (handleBounds.minY() + handleBounds.maxY()) / 2,
            (handleBounds.minZ() + handleBounds.maxZ()) / 2
        );
        this.size = Math.max(handleBounds.sizeX(), handleBounds.sizeZ());
    }

    public UUID getId() {
        return handle.arenaId();
    }

    public ServerLevel getLevel() {
        return level;
    }

    public ArenaHandle getHandle() {
        return handle;
    }

    public BlockPos getCenter() {
        return center;
    }

    public int getSize() {
        return size;
    }

    public AABB getBounds() {
        return bounds;
    }

    public boolean contains(Vec3 pos) {
        return bounds.contains(Objects.requireNonNull(pos));
    }

    public boolean contains(BlockPos pos) {
        BlockPos safePos = Objects.requireNonNull(pos);
        return bounds.contains(safePos.getX(), safePos.getY(), safePos.getZ());
    }
}
