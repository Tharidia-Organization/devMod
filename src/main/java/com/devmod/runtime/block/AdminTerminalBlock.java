package com.devmod.runtime.block;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.clone.block.CloneMachineBlock;
import com.devmod.config.Config;
import com.devmod.runtime.network.AdminInstanceNetworkHandler;

/**
 * Admin Terminal block for monitoring and controlling instance dimensions.
 * Right-click to open the Admin Instance Panel.
 * Requires configured permission level to interact.
 */
public class AdminTerminalBlock extends CloneMachineBlock {
    public static final MapCodec<AdminTerminalBlock> CODEC = simpleCodec(AdminTerminalBlock::new);

    public AdminTerminalBlock() {
        this(createAdminProperties(), Block.box(0, 0, 0, 16, 16, 16));
    }

    public AdminTerminalBlock(Properties properties) {
        this(properties, Block.box(0, 0, 0, 16, 16, 16));
    }

    public AdminTerminalBlock(Properties properties, VoxelShape shape) {
        super(properties, shape);
    }

    private static Properties createAdminProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(Objects.requireNonNull(MapColor.COLOR_RED))
            .strength(5.0F, 1200.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .lightLevel(state -> 8)
            .noOcclusion()
            .requiresCorrectToolForDrops();
    }

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return Objects.requireNonNull(CODEC);
    }

    @Override
    @Nonnull
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull BlockHitResult hitResult) {

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        // Check permission level from config
        int requiredPermission = Config.ADMIN_PANEL_PERMISSION_LEVEL.get();
        if (!serverPlayer.hasPermissions(requiredPermission)) {
            serverPlayer.displayClientMessage(
                Objects.requireNonNull(Component.translatable("devmod.admin.terminal.no_permission")), true);
            return InteractionResult.FAIL;
        }

        // Send sync data to player - client will open the screen
        AdminInstanceNetworkHandler.sendSyncToPlayer(serverPlayer);

        serverPlayer.displayClientMessage(
            Objects.requireNonNull(Component.translatable("devmod.admin.terminal.opening")), true);

        return InteractionResult.CONSUME;
    }
}
