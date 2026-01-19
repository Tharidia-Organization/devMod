package com.devmod.foundry.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import com.devmod.foundry.block.entity.FoundryStencilTableBlockEntity;

/**
 * Stencil Table block for carving blank stencils into specific patterns.
 * Part of the TConstruct-style workflow: Blank Stencil → Stencil Table → Pattern
 */
public final class FoundryStencilTableBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<FoundryStencilTableBlock> CODEC = simpleCodec(p -> new FoundryStencilTableBlock());
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return Objects.requireNonNull(CODEC);
    }

    @SuppressWarnings("this-escape")
    public FoundryStencilTableBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(2.5f, 5.0f)
            .sound(Objects.requireNonNull(SoundType.WOOD))
            .noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Objects.requireNonNull(Direction.NORTH)));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        Direction facing = Objects.requireNonNull(context.getHorizontalDirection().getOpposite());
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new FoundryStencilTableBlockEntity(pos, state);
    }

    @Override
    @Nonnull
    protected InteractionResult useWithoutItem(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull Player player,
        @Nonnull BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FoundryStencilTableBlockEntity stencilTable) {
            player.openMenu(stencilTable);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
