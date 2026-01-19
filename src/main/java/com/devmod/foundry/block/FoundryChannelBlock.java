package com.devmod.foundry.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.block.entity.FoundryChannelBlockEntity;

/**
 * Foundry channel block for routing molten fluids.
 */
public class FoundryChannelBlock extends Block implements EntityBlock {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    @SuppressWarnings("this-escape")
    public FoundryChannelBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(3.0f, 6.0f)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops()
            .noOcclusion());
        registerDefaultState(stateDefinition.any()
            .setValue(NORTH, false)
            .setValue(SOUTH, false)
            .setValue(EAST, false)
            .setValue(WEST, false)
            .setValue(UP, false)
            .setValue(DOWN, false));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new FoundryChannelBlockEntity(pos, state);
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nonnull
    public BlockState getStateForPlacement(@Nonnull net.minecraft.world.item.context.BlockPlaceContext context) {
        return updateConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    @Nonnull
    public BlockState updateShape(
        @Nonnull BlockState state,
        @Nonnull Direction direction,
        @Nonnull BlockState neighborState,
        @Nonnull LevelAccessor level,
        @Nonnull BlockPos pos,
        @Nonnull BlockPos neighborPos
    ) {
        return updateConnections(level, pos, state);
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
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FoundryChannelBlockEntity channel) {
            // Cycle valve state: NONE -> IN -> OUT -> NONE
            ChannelConnectionState newState = channel.cycleValve(hit.getDirection());
            String directionName = hit.getDirection().getName();
            player.displayClientMessage(
                Component.translatable(
                    "devmod.foundry.channel.valve_state",
                    directionName,
                    Component.translatable("devmod.foundry.channel.state." + newState.getSerializedName())
                ),
                true
            );
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
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
        if (!(be instanceof FoundryChannelBlockEntity channel)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player.isShiftKeyDown() && stack.is(Items.BUCKET)) {
            if (channel.clearFilter()) {
                player.displayClientMessage(Component.translatable("devmod.foundry.channel.filter_cleared"), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()
            && stack.getItem() instanceof BucketItem bucket
            && bucket.content != Fluids.EMPTY) {
            if (channel.setFilter(bucket.content)) {
                Component fluidName = Component.translatable(bucket.content.getFluidType().getDescriptionId());
                player.displayClientMessage(
                    Component.translatable("devmod.foundry.channel.filter_set", fluidName),
                    true
                );
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }
        return type == FoundryBlockEntities.FOUNDRY_CHANNEL.get()
            ? (lvl, pos, st, be) -> ((FoundryChannelBlockEntity) be).tickServer()
            : null;
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    private BlockState updateConnections(LevelAccessor level, BlockPos pos, BlockState state) {
        return state
            .setValue(NORTH, canConnectTo(level, pos, Direction.NORTH))
            .setValue(SOUTH, canConnectTo(level, pos, Direction.SOUTH))
            .setValue(EAST, canConnectTo(level, pos, Direction.EAST))
            .setValue(WEST, canConnectTo(level, pos, Direction.WEST))
            .setValue(UP, canConnectTo(level, pos, Direction.UP))
            .setValue(DOWN, canConnectTo(level, pos, Direction.DOWN));
    }

    private boolean canConnectTo(LevelAccessor level, BlockPos pos, Direction direction) {
        BlockState neighborState = level.getBlockState(pos.relative(direction));
        Block neighbor = neighborState.getBlock();
        if (neighbor instanceof FoundryChannelBlock) {
            return true;
        }
        if (direction == Direction.UP && neighbor instanceof FoundryFaucetBlock) {
            return true;
        }
        if (direction == Direction.DOWN
            && (neighbor instanceof FoundryCastingTableBlock || neighbor instanceof FoundryCastingBasinBlock)) {
            return true;
        }
        return false;
    }
}
