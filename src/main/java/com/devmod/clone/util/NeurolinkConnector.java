package com.devmod.clone.util;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import com.devmod.clone.CloneBlocks;
import com.devmod.clone.block.NeurocellBlock;
import com.devmod.clone.block.NeurocellLBlock;
import com.devmod.clone.block.entity.NeurocellAccess;

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
    public static NeurocellAccess findConnectedNeurocell(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        // Start with adjacent positions
        for (Direction dir : Direction.values()) {
            queue.add(startPos.relative(Objects.requireNonNull(dir)));
        }
        visited.add(startPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            // Skip if already visited or too far
            if (visited.contains(current)) {
                continue;
            }
            if (current.distManhattan(Objects.requireNonNull(startPos)) > MAX_SEARCH_DISTANCE) {
                continue;
            }

            visited.add(current);

            BlockState state = level.getBlockState(current);
            Block block = state.getBlock();

            NeurocellAccess neurocell = resolveNeurocellAccess(level, current, state);
            if (neurocell != null && neurocell.isDataReady()) {
                return neurocell;
            }

            // If it's a NEUROLINK, continue searching through it
            if (block == CloneBlocks.NEUROLINK.get()) {
                for (Direction dir : Direction.values()) {
                    BlockPos nextPos = current.relative(Objects.requireNonNull(dir));
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

    /**
     * Find a connected NEUROCELL with an EMPTY bioscanner via NEUROLINK cables.
     * Used by the Imprinter to find a target for scanning.
     *
     * @param level The world
     * @param startPos Starting position (typically IMPRINTER position)
     * @return The first connected NEUROCELL with empty bioscanner, or null if none found
     */
    @Nullable
    public static NeurocellAccess findConnectedNeurocellWithEmptyBioscanner(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        // Start with adjacent positions
        for (Direction dir : Direction.values()) {
            queue.add(startPos.relative(Objects.requireNonNull(dir)));
        }
        visited.add(startPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            // Skip if already visited or too far
            if (visited.contains(current)) {
                continue;
            }
            if (current.distManhattan(Objects.requireNonNull(startPos)) > MAX_SEARCH_DISTANCE) {
                continue;
            }

            visited.add(current);

            BlockState state = level.getBlockState(current);
            Block block = state.getBlock();

            NeurocellAccess neurocell = resolveNeurocellAccess(level, current, state);
            if (neurocell != null && neurocell.hasEmptyBioscanner()) {
                return neurocell;
            }

            // If it's a NEUROLINK, continue searching through it
            if (block == CloneBlocks.NEUROLINK.get()) {
                for (Direction dir : Direction.values()) {
                    BlockPos nextPos = current.relative(Objects.requireNonNull(dir));
                    if (!visited.contains(nextPos)) {
                        queue.add(nextPos);
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private static NeurocellAccess resolveNeurocellAccess(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block == CloneBlocks.NEUROCELL.get()) {
            if (state.hasProperty(Objects.requireNonNull(NeurocellBlock.HALF))
                && state.getValue(Objects.requireNonNull(NeurocellBlock.HALF)) == DoubleBlockHalf.UPPER) {
                pos = pos.below();
            }
            BlockEntity be = level.getBlockEntity(Objects.requireNonNull(pos));
            return be instanceof NeurocellAccess access ? access : null;
        }

        if (block == CloneBlocks.NEUROCELL_L.get()) {
            BlockPos centerPos = pos;
            if (state.hasProperty(Objects.requireNonNull(NeurocellLBlock.PART)) && state.hasProperty(Objects.requireNonNull(NeurocellLBlock.FACING))) {
                NeurocellLBlock.MultiBlockPart part = state.getValue(Objects.requireNonNull(NeurocellLBlock.PART));
                Direction facing = state.getValue(Objects.requireNonNull(NeurocellLBlock.FACING));
                centerPos = part.getCenterFromThis(pos, facing);
            }
            BlockEntity be = level.getBlockEntity(Objects.requireNonNull(centerPos));
            return be instanceof NeurocellAccess access ? access : null;
        }

        return null;
    }
}
