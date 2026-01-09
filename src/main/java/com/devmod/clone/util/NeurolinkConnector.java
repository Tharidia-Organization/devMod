package com.devmod.clone.util;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.clone.CloneBlocks;
import com.devmod.clone.block.entity.NeurocellBlockEntity;

/**
 * Utility for finding connected blocks via NEUROLINK cables.
 * Uses BFS to traverse cable network up to a maximum distance.
 */
public final class NeurolinkConnector {
    private NeurolinkConnector() {}

    private static final int MAX_SEARCH_DISTANCE = 16;

    /**
     * Find a connected NEUROCELL with ready data via NEUROLINK cables.
     *
     * @param level The world
     * @param startPos Starting position (typically REFORMER position)
     * @return The first connected NEUROCELL with ready data, or null if none found
     */
    @Nullable
    public static NeurocellBlockEntity findConnectedNeurocell(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        // Start with adjacent positions
        for (Direction dir : Direction.values()) {
            queue.add(startPos.relative(dir));
        }
        visited.add(startPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            // Skip if already visited or too far
            if (visited.contains(current)) {
                continue;
            }
            if (current.distManhattan(startPos) > MAX_SEARCH_DISTANCE) {
                continue;
            }

            visited.add(current);

            BlockState state = level.getBlockState(current);
            Block block = state.getBlock();

            // Check if it's a NEUROCELL with ready data
            if (block == CloneBlocks.NEUROCELL.get()) {
                BlockEntity be = level.getBlockEntity(current);
                if (be instanceof NeurocellBlockEntity neurocell && neurocell.isDataReady()) {
                    return neurocell;
                }
            }

            // If it's a NEUROLINK, continue searching through it
            if (block == CloneBlocks.NEUROLINK.get()) {
                for (Direction dir : Direction.values()) {
                    BlockPos nextPos = current.relative(dir);
                    if (!visited.contains(nextPos)) {
                        queue.add(nextPos);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Check if a REFORMER is connected to any NEUROCELL via NEUROLINK.
     */
    public static boolean hasConnectedNeurocell(Level level, BlockPos reformerPos) {
        return findConnectedNeurocell(level, reformerPos) != null;
    }
}
