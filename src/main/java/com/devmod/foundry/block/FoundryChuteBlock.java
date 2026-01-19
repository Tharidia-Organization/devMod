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
import net.minecraft.world.level.block.RenderShape;
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

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.block.entity.FoundryChuteBlockEntity;

/**
 * Foundry chute block for automated item input.
 */
public class FoundryChuteBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty IN_STRUCTURE = BooleanProperty.create("in_structure");

    @SuppressWarnings("this-escape")
    public FoundryChuteBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(4.0f, 6.0f)
            .sound(Objects.requireNonNull(SoundType.STONE))
            .requiresCorrectToolForDrops());
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(IN_STRUCTURE, false));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, IN_STRUCTURE);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(IN_STRUCTURE, false);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new FoundryChuteBlockEntity(pos, state);
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }
        return type == FoundryBlockEntities.FOUNDRY_CHUTE.get()
            ? (lvl, pos, st, be) -> ((FoundryChuteBlockEntity) be).tickServer()
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
        if (!(be instanceof FoundryChuteBlockEntity chute)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Shift+right-click to set filter
        if (player.isShiftKeyDown()) {
            chute.setFilter(stack);
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.devmod.foundry.chute_filter_set", stack.getHoverName()),
                true
            );
            return ItemInteractionResult.SUCCESS;
        }
        ItemStack remaining = chute.insertManual(stack);
        if (remaining.getCount() != stack.getCount()) {
            player.setItemInHand(hand, remaining);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
        if (be instanceof FoundryChuteBlockEntity chute) {
            // Shift+right-click with empty hand to clear filter
            if (player.isShiftKeyDown()) {
                if (!chute.getFilter().isEmpty()) {
                    chute.clearFilter();
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.devmod.foundry.chute_filter_cleared"),
                        true
                    );
                    return InteractionResult.SUCCESS;
                }
            }
            ItemStack extracted = chute.extractManual();
            if (!extracted.isEmpty()) {
                if (!player.getInventory().add(extracted)) {
                    player.drop(extracted, false);
                }
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }
}
