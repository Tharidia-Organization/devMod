package com.devmod.foundry.structure;

import java.util.Set;

import net.minecraft.core.BlockPos;

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
) {}
