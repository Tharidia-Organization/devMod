package com.devmod.foundry.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.block.entity.FoundryFaucetBlockEntity;

/**
 * Foundry faucet block, pulls molten fluid from drain/tank and pours into casting blocks.
 */
public final class FoundryFaucetBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<FoundryFaucetBlock> CODEC = simpleCodec(p -> new FoundryFaucetBlock());
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return Objects.requireNonNull(CODEC);
    }

    @SuppressWarnings("this-escape")
    public FoundryFaucetBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(2.0f, 6.0f)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops()
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
        Direction facing = Objects.requireNonNull(context.getHorizontalDirection());
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new FoundryFaucetBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }
        return type == FoundryBlockEntities.FOUNDRY_FAUCET.get()
            ? (lvl, pos, st, be) -> ((FoundryFaucetBlockEntity) be).tickServer()
            : null;
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nonnull
    protected VoxelShape getShape(@Nonnull BlockState state, @Nonnull net.minecraft.world.level.BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return Objects.requireNonNull(net.minecraft.world.level.block.Block.box(4, 4, 0, 12, 12, 8));
    }
}
