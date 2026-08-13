package com.devmod.clone.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.block.entity.ClonePulverizerBlockEntity;
/**
 * Clone Pulverizer block - industrial crusher machine.
 * Accepts items dropped from above, pulverizes them, and ejects results.
 *
 * <p>Features:
 * <ul>
 *   <li>GeckoLib animated model with deploy and active animations</li>
 *   <li>AABB item capture from items dropped above</li>
 *   <li>Physical item ejection from discharge chute</li>
 *   <li>No GUI - purely automatic processing</li>
 *   <li>Hopper compatible for automation</li>
 * </ul>
 */
public final class ClonePulverizerBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ClonePulverizerBlock> CODEC = simpleCodec(p -> new ClonePulverizerBlock());
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // Collision shape - slightly reduced to allow items to fall through hopper opening
    private static final VoxelShape SHAPE = Shapes.box(0.0625, 0, 0.0625, 0.9375, 1.0, 0.9375);

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return Objects.requireNonNull(CODEC);
    }

    public ClonePulverizerBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(Objects.requireNonNull(MapColor.METAL))
                .strength(4.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion() // Allows seeing through transparent parts
                .dynamicShape()); // Shape can change (for GeckoLib)
        registerDefaultState(Objects.requireNonNull(stateDefinition.any()
                .setValue(Objects.requireNonNull(FACING), Objects.requireNonNull(Direction.NORTH))));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        Direction facing = Objects.requireNonNull(context.getHorizontalDirection().getOpposite());
        return defaultBlockState().setValue(Objects.requireNonNull(FACING), facing);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new ClonePulverizerBlockEntity(pos, state);
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        // Use entity rendering for GeckoLib animated model
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @Nonnull
    protected VoxelShape getShape(
            @Nonnull BlockState state,
            @Nonnull BlockGetter level,
            @Nonnull BlockPos pos,
            @Nonnull CollisionContext context
    ) {
        return Objects.requireNonNull(SHAPE);
    }

    @Override
    protected float getShadeBrightness(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return 1.0f; // Full brightness for better visibility
    }

    @Override
    public boolean propagatesSkylightDown(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return true; // Allow light through transparent parts
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @Nonnull Level level,
            @Nonnull BlockState state,
            @Nonnull BlockEntityType<T> type
    ) {
        // Verify correct block entity type
        if (type != CloneBlockEntities.CLONE_PULVERIZER.get()) {
            return null;
        }

        // Only server needs tick - GeckoLib handles animations client-side automatically
        if (!level.isClientSide) {
            return (lvl, pos, st, be) -> {
                if (be instanceof ClonePulverizerBlockEntity pulverizer) {
                    pulverizer.serverTick();
                }
            };
        }
        return null;
    }

    /**
     * Right-click without item: Shift+click to extract grinders, otherwise collect output.
     */
    @Override
    @Nonnull
    protected InteractionResult useWithoutItem(
            @Nonnull BlockState state,
            @Nonnull Level level,
            @Nonnull BlockPos pos,
            @Nonnull Player player,
            @Nonnull BlockHitResult hitResult
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ClonePulverizerBlockEntity pulverizer) {
            // Try grinder interaction first (shift+click to extract)
            InteractionResult grinderResult = pulverizer.handleGrinderInteraction(player, ItemStack.EMPTY);
            if (grinderResult != InteractionResult.PASS) {
                return Objects.requireNonNull(grinderResult);
            }

            // Otherwise, collect output items
            ItemStack output = pulverizer.extractOutput();
            if (!output.isEmpty()) {
                // Give item to player
                if (!player.getInventory().add(output)) {
                    // Inventory full - drop at player's feet
                    player.drop(output, false);
                }
                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * Right-click with item: Insert grinders if holding one.
     */
    @Override
    @Nonnull
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            @Nonnull ItemStack stack,
            @Nonnull BlockState state,
            @Nonnull Level level,
            @Nonnull BlockPos pos,
            @Nonnull Player player,
            @Nonnull net.minecraft.world.InteractionHand hand,
            @Nonnull BlockHitResult hitResult
    ) {
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ClonePulverizerBlockEntity pulverizer) {
            // Try grinder interaction (insert if holding grinder)
            InteractionResult grinderResult = pulverizer.handleGrinderInteraction(player, stack);
            if (grinderResult == InteractionResult.SUCCESS) {
                return net.minecraft.world.ItemInteractionResult.SUCCESS;
            } else if (grinderResult == InteractionResult.CONSUME) {
                return net.minecraft.world.ItemInteractionResult.CONSUME;
            }
        }

        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Drops the block entity's contents when the block is removed.
     *
     * <p>Without this the input, output and both grinder slots were silently
     * voided on break — there is no loot table for this block either.
     */
    @Override
    protected void onRemove(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull BlockState newState,
        boolean isMoving
    ) {
        if (!state.is(Objects.requireNonNull(newState.getBlock()))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ClonePulverizerBlockEntity pulverizer) {
                Containers.dropContents(level, pos, pulverizer.getInventory());
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public boolean hasAnalogOutputSignal(@Nonnull BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ClonePulverizerBlockEntity pulverizer) {
            // Output signal proportional to progress (0-15)
            return (int) (pulverizer.getProgressPercent() * 15);
        }
        return 0;
    }
}
