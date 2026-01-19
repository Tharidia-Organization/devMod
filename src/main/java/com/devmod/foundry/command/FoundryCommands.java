package com.devmod.foundry.command;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.foundry.FoundryBlocks;
import com.devmod.foundry.block.FoundryControllerBlock;
import com.devmod.foundry.block.FoundryFaucetBlock;

/**
 * Foundry debug/testing commands.
 */
public final class FoundryCommands {
    private FoundryCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .then(Commands.literal("foundry")
                    .then(Commands.literal("spawn")
                        .requires(source -> source.hasPermission(2))
                        .executes(FoundryCommands::spawnFoundry))
                    .then(Commands.literal("help")
                        .executes(FoundryCommands::help)))
        );
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§b=== DevMod Foundry ===")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7/§fdevmod foundry spawn §7- spawn a test foundry structure")), false);
        return 1;
    }

    /**
     * Spawns a complete 5x5x5 foundry structure (outer dimensions) for testing.
     * Inner dimensions are 3x3x3.
     * Structure includes: controller, drain, tanks, faucet, casting table.
     */
    private static int spawnFoundry(@Nonnull CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("§cPlayer required")));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        // Spawn foundry 2 blocks in front of player, at their feet level
        Direction facing = player.getDirection();
        BlockPos origin = playerPos.relative(facing, 2);

        // Structure layout (5x5x5 outer, 3x3x3 inner hollow):
        // Floor at Y+0: 5x5 bricks
        // Walls Y+1 to Y+3: bricks with controller, drain, tanks
        // Interior Y+1 to Y+3: air (hollow)
        // Top Y+4: open (no blocks)

        int minX = origin.getX();
        int minZ = origin.getZ();
        int baseY = origin.getY();

        BlockState bricks = Objects.requireNonNull(FoundryBlocks.FOUNDRY_BRICKS.get()).defaultBlockState();
        BlockState tank = Objects.requireNonNull(FoundryBlocks.FOUNDRY_TANK.get()).defaultBlockState();
        BlockState drain = Objects.requireNonNull(FoundryBlocks.FOUNDRY_DRAIN.get()).defaultBlockState();
        // Controller faces INTO the structure (toward the player)
        BlockState controller = Objects.requireNonNull(FoundryBlocks.FOUNDRY_CONTROLLER.get()).defaultBlockState()
            .setValue(FoundryControllerBlock.FACING, facing);
        BlockState faucet = Objects.requireNonNull(FoundryBlocks.FOUNDRY_FAUCET.get()).defaultBlockState()
            .setValue(FoundryFaucetBlock.FACING, facing);
        BlockState castingTable = Objects.requireNonNull(FoundryBlocks.FOUNDRY_CASTING_TABLE.get()).defaultBlockState();

        // Build the structure
        for (int y = 0; y <= 3; y++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    BlockPos pos = new BlockPos(minX + x, baseY + y, minZ + z);
                    boolean isFloor = (y == 0);
                    boolean isWall = (x == 0 || x == 4 || z == 0 || z == 4);
                    boolean isInterior = !isFloor && !isWall;

                    if (isFloor) {
                        // Floor: all bricks
                        level.setBlock(pos, bricks, 3);
                    } else if (isWall) {
                        // Wall: bricks by default
                        level.setBlock(pos, bricks, 3);
                    } else if (isInterior) {
                        // Interior: air (clear any existing blocks)
                        level.removeBlock(pos, false);
                    }
                }
            }
        }

        // Place special blocks on the walls
        // Controller on the side facing the player (at Y+1)
        BlockPos controllerPos = getWallCenter(minX, minZ, baseY + 1, facing.getOpposite());
        level.setBlock(controllerPos, controller, 3);

        // Drain on the opposite side (at Y+1) - for fluid extraction
        BlockPos drainPos = getWallCenter(minX, minZ, baseY + 1, facing);
        level.setBlock(drainPos, drain, 3);

        // Tanks on the sides (at Y+2) for extra capacity
        BlockPos tankLeft = getWallCenter(minX, minZ, baseY + 2, facing.getClockWise());
        BlockPos tankRight = getWallCenter(minX, minZ, baseY + 2, facing.getCounterClockWise());
        level.setBlock(tankLeft, tank, 3);
        level.setBlock(tankRight, tank, 3);

        // More tanks at Y+3
        BlockPos tankTop1 = getWallCenter(minX, minZ, baseY + 3, facing.getClockWise());
        BlockPos tankTop2 = getWallCenter(minX, minZ, baseY + 3, facing.getCounterClockWise());
        level.setBlock(tankTop1, tank, 3);
        level.setBlock(tankTop2, tank, 3);

        // Faucet and casting table outside the structure
        BlockPos faucetPos = drainPos.relative(facing);
        BlockPos tablePos = faucetPos.below();
        level.setBlock(faucetPos, faucet, 3);
        level.setBlock(tablePos, castingTable, 3);

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§aFoundry spawned at " + origin.toShortString())), true);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Controller facing §f" + facing.getOpposite().getName() + "§7, Drain + Faucet facing §f" + facing.getName())), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("§7Right-click the controller to open the GUI and start melting!")), false);

        return 1;
    }

    /**
     * Gets the center position of a wall face for a 5x5 structure.
     */
    private static BlockPos getWallCenter(int minX, int minZ, int y, Direction wallFacing) {
        return switch (wallFacing) {
            case NORTH -> new BlockPos(minX + 2, y, minZ);
            case SOUTH -> new BlockPos(minX + 2, y, minZ + 4);
            case WEST -> new BlockPos(minX, y, minZ + 2);
            case EAST -> new BlockPos(minX + 4, y, minZ + 2);
            default -> new BlockPos(minX + 2, y, minZ + 2);
        };
    }
}
