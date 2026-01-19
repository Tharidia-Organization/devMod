package com.devmod.foundry.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import com.devmod.foundry.block.entity.FoundryCastingBasinBlockEntity;

/**
 * Foundry casting basin block.
 */
public class FoundryCastingBasinBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty HAS_ITEM = BooleanProperty.create("has_item");

    public FoundryCastingBasinBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(3.0f, 6.0f)
            .sound(Objects.requireNonNull(SoundType.STONE))
            .requiresCorrectToolForDrops()
            .noOcclusion());
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HAS_ITEM, false));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new FoundryCastingBasinBlockEntity(pos, state);
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        return Objects.requireNonNull(defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(HAS_ITEM, false));
    }

    @Override
    @Nonnull
    protected BlockState rotate(@Nonnull BlockState state, @Nonnull Rotation rotation) {
        return Objects.requireNonNull(state.setValue(FACING, rotation.rotate(state.getValue(FACING))));
    }

    @Override
    @Nonnull
    protected BlockState mirror(@Nonnull BlockState state, @Nonnull Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_ITEM);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }
        return type == com.devmod.foundry.FoundryBlockEntities.FOUNDRY_CASTING_BASIN.get()
            ? (lvl, pos, st, be) -> ((FoundryCastingBasinBlockEntity) be).tickServer()
            : null;
    }

    @Override
    @Nonnull
    protected ItemInteractionResult useItemOn(
        @Nonnull ItemStack stack,
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull Player player,
        @Nonnull InteractionHand hand,
        @Nonnull BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FoundryCastingBasinBlockEntity casting) {
            InteractionResult result = casting.handleInteraction(player, hand);
            if (result == InteractionResult.CONSUME) {
                return ItemInteractionResult.CONSUME;
            }
            if (result == InteractionResult.SUCCESS) {
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
