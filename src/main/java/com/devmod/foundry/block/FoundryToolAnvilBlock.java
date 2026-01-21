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
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.block.entity.FoundryToolAnvilBlockEntity;

/**
 * Foundry tool anvil block for applying modifiers.
 */
public final class FoundryToolAnvilBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<FoundryToolAnvilBlock> CODEC = simpleCodec(p -> new FoundryToolAnvilBlock());
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return Objects.requireNonNull(CODEC);
    }

    @SuppressWarnings("this-escape")
    public FoundryToolAnvilBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(Objects.requireNonNull(MapColor.METAL))
            .strength(4.0f, 8.0f)
            .sound(Objects.requireNonNull(SoundType.ANVIL))
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
        return new FoundryToolAnvilBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }
        return type == FoundryBlockEntities.FOUNDRY_TOOL_ANVIL.get()
            ? (lvl, pos, st, be) -> ((FoundryToolAnvilBlockEntity) be).tickServer()
            : null;
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
        if (be instanceof FoundryToolAnvilBlockEntity anvil) {
            player.openMenu(anvil);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
