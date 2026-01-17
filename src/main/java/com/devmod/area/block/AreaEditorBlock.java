package com.devmod.area.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

import com.devmod.area.block.entity.AreaEditorBlockEntity;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaRegistry;
import com.devmod.area.network.AreaNetworkHandler;

/**
 * Area Editor block - allows OP 2+ players to create and configure areas.
 * When placed, registers itself with AreaRegistry.
 * On interaction, opens the Area Builder GUI.
 */
public class AreaEditorBlock extends BaseEntityBlock {
    public static final MapCodec<AreaEditorBlock> CODEC = simpleCodec(AreaEditorBlock::new);

    public AreaEditorBlock(Properties properties) {
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
        return new AreaEditorBlockEntity(pos, state);
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

        // Require OP level 2 to use
        if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.displayClientMessage(
                Objects.requireNonNull(Component.translatable("area.editor.no_permission")), true);
            return InteractionResult.FAIL;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AreaEditorBlockEntity editorBE) {
            var server = serverPlayer.getServer();
            if (server == null) {
                return InteractionResult.FAIL;
            }

            // Validate and clear orphan references (area was deleted)
            editorBE.validateAndClearOrphan();

            // Get existing area if linked (using validated method)
            AreaDefinition existingArea = editorBE.getLinkedArea().orElse(null);

            // Open Area Builder GUI via network
            AreaNetworkHandler.openBuilderScreen(serverPlayer, existingArea, pos,
                existingArea != null && existingArea.isMainHub());
            serverPlayer.displayClientMessage(
                Objects.requireNonNull(Component.translatable("area.editor.opening")), true);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    protected void onPlace(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AreaEditorBlockEntity editorBE) {
                var areaId = editorBE.getValidatedAreaId();
                if (areaId != null) {
                    AreaRegistry registry = AreaRegistry.get(serverLevel.getServer());
                    registry.registerEditor(Objects.requireNonNull(serverLevel.dimension().location()), pos, areaId);
                }
            }
        }
    }

    @Override
    protected void onRemove(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull BlockState newState, boolean movedByPiston) {
        if (!state.is(Objects.requireNonNull(newState.getBlock()))) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                AreaRegistry registry = AreaRegistry.get(serverLevel.getServer());
                registry.unregisterEditor(Objects.requireNonNull(serverLevel.dimension().location()), pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull RandomSource random) {
        // Ambient particles
        if (random.nextInt(5) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double y = pos.getY() + 1.2;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            level.addParticle(Objects.requireNonNull(ParticleTypes.END_ROD), x, y, z, 0, 0.02, 0);
        }
    }
}
