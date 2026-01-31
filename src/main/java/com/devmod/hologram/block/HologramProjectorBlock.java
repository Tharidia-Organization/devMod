package com.devmod.hologram.block;

import java.util.EnumSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.hologram.block.entity.HologramProjectorBlockEntity;
import com.devmod.hologram.data.EntityFilterType;
import com.devmod.hologram.data.HologramFilter;
import com.devmod.hologram.network.HologramOpenScreenPayload;
import com.devmod.network.NetworkHandler;

/**
 * A block that projects a 3D holographic miniature of nearby terrain.
 * Right-click to cycle settings (rotation, transparency, size).
 */
public final class HologramProjectorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<HologramProjectorBlock> CODEC = simpleCodec(HologramProjectorBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    private static int filtersToInt(EnumSet<HologramFilter> filters) {
        int result = 0;
        for (HologramFilter filter : filters) {
            result |= (1 << filter.ordinal());
        }
        return result;
    }

    public HologramProjectorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new HologramProjectorBlockEntity(pos, state);
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nonnull
    protected VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                                   @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected float getShadeBrightness(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return 1.0f;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                        @Nonnull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HologramProjectorBlockEntity projector) {
                projector.setupScanRegion();
            }
        }
    }

    @Override
    @Nonnull
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
                                               @Nonnull BlockPos pos, @Nonnull Player player,
                                               @Nonnull BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HologramProjectorBlockEntity projector) {
                // Send packet to open config screen on client
                HologramOpenScreenPayload payload = new HologramOpenScreenPayload(
                    pos,
                    projector.getScanSize(),
                    projector.getBlockSize(),
                    projector.isRotationEnabled(),
                    projector.isTransparentMode(),
                    filtersToInt(projector.getActiveFilters()),
                    projector.isFilterHighlightOnly(),
                    projector.isTexturedMode(),
                    projector.isShowEntities(),
                    projector.isFullEntityModels(),
                    projector.getMaxEntityModels(),
                    EntityFilterType.toBitmask(projector.getActiveEntityFilters()),
                    projector.isYSliceEnabled(),
                    projector.getYSliceLevel(),
                    projector.getYSliceThickness()
                );
                NetworkHandler.sendHologramOpenScreen(serverPlayer, payload);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
