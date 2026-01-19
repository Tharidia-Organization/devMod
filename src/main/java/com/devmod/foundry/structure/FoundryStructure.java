package com.devmod.foundry.structure;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Data for a formed foundry multiblock.
 */
public record FoundryStructure(
    BlockPos minPos,
    BlockPos maxPos,
    int innerWidth,
    int innerLength,
    int innerHeight,
    int interiorVolume,
    Set<BlockPos> tankPositions,
    Set<BlockPos> drainPositions,
    Set<BlockPos> ductPositions,
    Set<BlockPos> chutePositions
) {
    /**
     * Gets the AABB representing the interior space of the foundry.
     * This excludes the walls - only the hollow interior where fluids and entities can exist.
     * @return AABB of the interior space
     */
    public AABB getInteriorBounds() {
        // Interior is one block inside the walls
        return new AABB(
            minPos.getX() + 1,
            minPos.getY() + 1,
            minPos.getZ() + 1,
            maxPos.getX(),      // maxPos is already the last wall block
            maxPos.getY() + 1,  // +1 because AABB maxY is exclusive
            maxPos.getZ()
        );
    }

    /**
     * Gets the full AABB of the structure including walls.
     * @return AABB of the entire structure
     */
    public AABB getBounds() {
        return new AABB(
            minPos.getX(),
            minPos.getY(),
            minPos.getZ(),
            maxPos.getX() + 1,
            maxPos.getY() + 1,
            maxPos.getZ() + 1
        );
    }
}
