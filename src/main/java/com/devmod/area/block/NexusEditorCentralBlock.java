package com.devmod.area.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.devmod.area.block.entity.NexusEditorCentralBlockEntity;
import com.devmod.area.network.AreaNetworkHandler;

/**
 * Nexus Editor Central block - spawns automatically in the Nexus dimension.
 * Indestructible and accessible to all players.
 * Opens the Nexus Editor Central menu for area management.
 */
public class NexusEditorCentralBlock extends BaseEntityBlock {
    public static final MapCodec<NexusEditorCentralBlock> CODEC = simpleCodec(NexusEditorCentralBlock::new);

    public NexusEditorCentralBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return Objects.requireNonNull(CODEC);
    }

    @Override
    @Nonnull
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new NexusEditorCentralBlockEntity(pos, state);
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

        // All players can interact with the central editor
        AreaNetworkHandler.openEditorCentralScreen(serverPlayer);
        serverPlayer.displayClientMessage(
            Objects.requireNonNull(Component.translatable("area.editor_central.opening")), true);

        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull RandomSource random) {
        // More prominent ambient particles for the central editor
        for (int i = 0; i < 2; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 0.5 + random.nextDouble() * 0.3;
            double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
            double y = pos.getY() + 0.8 + random.nextDouble() * 0.4;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * radius;

            if (random.nextBoolean()) {
                level.addParticle(Objects.requireNonNull(ParticleTypes.END_ROD), x, y, z, 0, 0.03, 0);
            } else {
                level.addParticle(Objects.requireNonNull(ParticleTypes.PORTAL), x, y, z, 0, 0.02, 0);
            }
        }
    }
}
