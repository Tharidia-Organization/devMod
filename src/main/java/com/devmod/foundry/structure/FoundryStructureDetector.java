package com.devmod.foundry.structure;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.config.Config;
import com.devmod.foundry.FoundryBlocks;

/**
 * Foundry multiblock detection.
 * Detects hollow cuboids with open top and solid floor/walls.
 */
public final class FoundryStructureDetector {
    private FoundryStructureDetector() {}

    public static FoundryStructureResult detect(@Nonnull Level level, @Nonnull BlockPos controllerPos) {
        BlockState controllerState = level.getBlockState(controllerPos);
        BlockPos origin = controllerPos.relative(controllerState.getValue(com.devmod.foundry.block.FoundryControllerBlock.FACING).getOpposite());

        if (!isInteriorBlock(level.getBlockState(origin))) {
            return new FoundryStructureResult(null,
                Component.translatable("devmod.foundry.error.no_inner_space"),
                origin);
        }

        int maxSize = Config.FOUNDRY_MAX_INNER_SIZE.get();
        int minSize = Config.FOUNDRY_MIN_INNER_SIZE.get();
        int maxHeight = Config.FOUNDRY_MAX_HEIGHT.get();

        int minX = findWall(level, origin, -1, 0, maxSize);
        int maxX = findWall(level, origin, 1, 0, maxSize);
        int minZ = findWall(level, origin, 0, -1, maxSize);
        int maxZ = findWall(level, origin, 0, 1, maxSize);

        if (minX == Integer.MIN_VALUE || maxX == Integer.MIN_VALUE || minZ == Integer.MIN_VALUE || maxZ == Integer.MIN_VALUE) {
            return new FoundryStructureResult(null,
                Component.translatable("devmod.foundry.error.missing_walls"),
                origin);
        }

        int innerWidth = maxX - minX - 1;
        int innerLength = maxZ - minZ - 1;
        if (innerWidth < minSize || innerLength < minSize) {
            return new FoundryStructureResult(null,
                Component.translatable("devmod.foundry.error.too_small", minSize, minSize),
                origin);
        }
        if (innerWidth > maxSize || innerLength > maxSize) {
            return new FoundryStructureResult(null,
                Component.translatable("devmod.foundry.error.too_large", maxSize, maxSize),
                origin);
        }

        int floorY = controllerPos.getY();
        int maxY = floorY;
        int heightChecked = 0;
        while (heightChecked < maxHeight) {
            if (isWallRingValid(level, minX, maxX, minZ, maxZ, maxY)) {
                maxY++;
                heightChecked++;
            } else {
                break;
            }
        }
        maxY = maxY - 1;

        int innerHeight = maxY - floorY - 1;
        if (innerHeight < 1) {
            return new FoundryStructureResult(null,
                Component.translatable("devmod.foundry.error.too_short"),
                origin);
        }

        if (!isFloorValid(level, minX, maxX, minZ, maxZ, floorY)) {
            return new FoundryStructureResult(null,
                Component.translatable("devmod.foundry.error.invalid_floor"),
                new BlockPos(minX + 1, floorY, minZ + 1));
        }

        Set<BlockPos> tanks = new HashSet<>();
        Set<BlockPos> drains = new HashSet<>();
        for (int y = floorY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean wall = x == minX || x == maxX || z == minZ || z == maxZ;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (wall) {
                        if (!isWallBlock(state)) {
                            return new FoundryStructureResult(null,
                                Component.translatable("devmod.foundry.error.invalid_wall"),
                                pos);
                        }
                        if (state.is(Objects.requireNonNull(FoundryBlocks.FOUNDRY_TANK.get()))) {
                            tanks.add(pos);
                        } else if (state.is(Objects.requireNonNull(FoundryBlocks.FOUNDRY_DRAIN.get()))) {
                            drains.add(pos);
                        }
                    } else if (y == floorY) {
                        if (!isWallBlock(state)) {
                            return new FoundryStructureResult(null,
                                Component.translatable("devmod.foundry.error.invalid_floor"),
                                pos);
                        }
                    } else if (!isInteriorBlock(state)) {
                        return new FoundryStructureResult(null,
                            Component.translatable("devmod.foundry.error.invalid_inner"),
                            pos);
                    }
                }
            }
        }

        int interiorVolume = innerWidth * innerLength * innerHeight;
        FoundryStructure structure = new FoundryStructure(
            new BlockPos(minX, floorY, minZ),
            new BlockPos(maxX, maxY, maxZ),
            innerWidth,
            innerLength,
            innerHeight,
            interiorVolume,
            tanks,
            drains
        );

        return new FoundryStructureResult(structure, null, null);
    }

    private static int findWall(Level level, BlockPos origin, int stepX, int stepZ, int maxSize) {
        int count = 0;
        int x = origin.getX();
        int z = origin.getZ();
        while (count <= maxSize + 1) {
            x += stepX;
            z += stepZ;
            BlockPos pos = new BlockPos(x, origin.getY(), z);
            BlockState state = level.getBlockState(pos);
            if (isWallBlock(state)) {
                return stepX != 0 ? x : z;
            }
            if (!isInteriorBlock(state)) {
                return Integer.MIN_VALUE;
            }
            count++;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isFloorValid(Level level, int minX, int maxX, int minZ, int maxZ, int floorY) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockState state = level.getBlockState(new BlockPos(x, floorY, z));
                if (!isWallBlock(state)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isWallRingValid(Level level, int minX, int maxX, int minZ, int maxZ, int y) {
        for (int x = minX; x <= maxX; x++) {
            if (!isWallBlock(level.getBlockState(new BlockPos(x, y, minZ)))) {
                return false;
            }
            if (!isWallBlock(level.getBlockState(new BlockPos(x, y, maxZ)))) {
                return false;
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            if (!isWallBlock(level.getBlockState(new BlockPos(minX, y, z)))) {
                return false;
            }
            if (!isWallBlock(level.getBlockState(new BlockPos(maxX, y, z)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWallBlock(BlockState state) {
        return state.is(Objects.requireNonNull(FoundryBlocks.FOUNDRY_BRICKS.get()))
            || state.is(Objects.requireNonNull(FoundryBlocks.FOUNDRY_CONTROLLER.get()))
            || state.is(Objects.requireNonNull(FoundryBlocks.FOUNDRY_DRAIN.get()))
            || state.is(Objects.requireNonNull(FoundryBlocks.FOUNDRY_TANK.get()));
    }

    private static boolean isInteriorBlock(BlockState state) {
        return state.isAir() || !state.getFluidState().isEmpty();
    }
}
