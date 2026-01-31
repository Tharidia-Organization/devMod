package com.devmod.clone.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.clone.CloneItems;
import com.devmod.clone.block.entity.NeurocellBlockEntity;
import com.devmod.clone.item.BioscannerItem;
/**
 * Neurocell block - the cloning chamber.
 * This is a double-height block (2 blocks tall) like doors.
 *
 * <p>Features:
 * <ul>
 *   <li>Insert BIOSCANNER to start cloning process</li>
 *   <li>Visual entity preview during processing</li>
 *   <li>300 tick processing time</li>
 *   <li>Outputs processed data to connected REFORMER</li>
 * </ul>
 */
public final class NeurocellBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<NeurocellBlock> CODEC = simpleCodec(p -> new NeurocellBlock());
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    // Hitbox shapes - lower half extends 2 blocks up
    private static final VoxelShape SHAPE_LOWER = Shapes.box(0.0, 0.0, 0.0, 1.0, 2.0, 1.0);
    private static final VoxelShape SHAPE_UPPER = Shapes.box(0.0, -1.0, 0.0, 1.0, 1.0, 1.0);

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return Objects.requireNonNull(CODEC);
    }

    public NeurocellBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(Objects.requireNonNull(MapColor.COLOR_LIGHT_BLUE))
            .strength(4.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .lightLevel(state -> state.getValue(Objects.requireNonNull(ACTIVE)) ? 10 : 0)
            .isValidSpawn((state, level, pos, type) -> false)
            .isRedstoneConductor((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false));
        registerDefaultState(Objects.requireNonNull(stateDefinition.any()
            .setValue(Objects.requireNonNull(HALF), DoubleBlockHalf.LOWER)
            .setValue(Objects.requireNonNull(FACING), Objects.requireNonNull(Direction.NORTH))
            .setValue(Objects.requireNonNull(ACTIVE), false)));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING, ACTIVE);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        // Only create block entity for lower half
        return state.getValue(Objects.requireNonNull(HALF)) == DoubleBlockHalf.LOWER ? new NeurocellBlockEntity(pos, state) : null;
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected float getShadeBrightness(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return 1.0f;
    }

    @Override
    @Nonnull
    protected VoxelShape getShape(
        @Nonnull BlockState state,
        @Nonnull BlockGetter level,
        @Nonnull BlockPos pos,
        @Nonnull CollisionContext context
    ) {
        return Objects.requireNonNull(state.getValue(Objects.requireNonNull(HALF)) == DoubleBlockHalf.LOWER ? SHAPE_LOWER : SHAPE_UPPER);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        Direction facing = Objects.requireNonNull(context.getHorizontalDirection().getOpposite());

        // Check if there's room for upper half
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(Objects.requireNonNull(pos.above())).canBeReplaced(context)) {
            return defaultBlockState()
                .setValue(Objects.requireNonNull(HALF), DoubleBlockHalf.LOWER)
                .setValue(Objects.requireNonNull(FACING), facing)
                .setValue(Objects.requireNonNull(ACTIVE), false);
        }
        return null;
    }

    @Override
    public void setPlacedBy(
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull BlockState state,
        @Nullable LivingEntity placer,
        @Nonnull ItemStack stack
    ) {
        // Place upper half with same ACTIVE state
        EnumProperty<DoubleBlockHalf> halfProp = Objects.requireNonNull(HALF);
        BooleanProperty activeProp = Objects.requireNonNull(ACTIVE);
        level.setBlock(Objects.requireNonNull(pos.above()), Objects.requireNonNull(state.setValue(halfProp, DoubleBlockHalf.UPPER).setValue(activeProp, Objects.requireNonNull(state.getValue(activeProp)))), 3);
    }

    @Override
    @Nonnull
    protected BlockState updateShape(
        @Nonnull BlockState state,
        @Nonnull Direction direction,
        @Nonnull BlockState neighborState,
        @Nonnull LevelAccessor level,
        @Nonnull BlockPos pos,
        @Nonnull BlockPos neighborPos
    ) {
        EnumProperty<DoubleBlockHalf> halfProp = Objects.requireNonNull(HALF);
        DoubleBlockHalf half = state.getValue(halfProp);

        if (direction.getAxis() == Direction.Axis.Y) {
            // Lower half checks upper
            if (half == DoubleBlockHalf.LOWER && direction == Direction.UP) {
                return Objects.requireNonNull(neighborState.is(this) && neighborState.getValue(halfProp) == DoubleBlockHalf.UPPER
                    ? state
                    : Blocks.AIR.defaultBlockState());
            }
            // Upper half checks lower
            if (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN) {
                return Objects.requireNonNull(neighborState.is(this) && neighborState.getValue(halfProp) == DoubleBlockHalf.LOWER
                    ? state
                    : Blocks.AIR.defaultBlockState());
            }
        }

        return Objects.requireNonNull(super.updateShape(state, direction, neighborState, level, pos, neighborPos));
    }

    @Override
    protected boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        EnumProperty<DoubleBlockHalf> halfProp = Objects.requireNonNull(HALF);
        if (state.getValue(halfProp) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(Objects.requireNonNull(pos.below()));
            return below.is(this) && below.getValue(halfProp) == DoubleBlockHalf.LOWER;
        }
        return true;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level,
        @Nonnull BlockState state,
        @Nonnull BlockEntityType<T> type
    ) {
        return null;
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

        // Get the lower position (where the block entity is)
        EnumProperty<DoubleBlockHalf> halfProp = Objects.requireNonNull(HALF);
        BlockPos lowerPos = state.getValue(halfProp) == DoubleBlockHalf.LOWER ? pos : Objects.requireNonNull(pos.below());
        BlockEntity be = level.getBlockEntity(lowerPos);

        if (!(be instanceof NeurocellBlockEntity neurocell)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Try to insert bioscanner
        boolean isBioscanner = stack.is(Objects.requireNonNull(CloneItems.BIOSCANNER.get()));
        boolean hasData = BioscannerItem.hasData(stack);

        if (isBioscanner && hasData) {
            // Check if entity is too large before attempting insertion
            float maxDim = BioscannerItem.getEntityMaxDimension(stack);
            if (maxDim > NeurocellBlockEntity.MAX_ENTITY_SIZE) {
                // Entity too large - show message
                player.displayClientMessage(
                    Objects.requireNonNull(Objects.requireNonNull(Component.translatable("message.devmod.entity_too_large"))
                        .withStyle(ChatFormatting.RED)),
                    true
                );
                return ItemInteractionResult.FAIL;
            }

            boolean inserted = neurocell.insertBioscanner(Objects.requireNonNull(stack.copy()));
            if (inserted) {
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                return ItemInteractionResult.CONSUME;
            }
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
            return Objects.requireNonNull(InteractionResult.sidedSuccess(true));
        }

        // Get the lower position (where the block entity is)
        EnumProperty<DoubleBlockHalf> halfProp = Objects.requireNonNull(HALF);
        BlockPos lowerPos = state.getValue(halfProp) == DoubleBlockHalf.LOWER ? pos : Objects.requireNonNull(pos.below());
        BlockEntity be = level.getBlockEntity(lowerPos);

        if (be instanceof NeurocellBlockEntity neurocell) {
            // Open the GUI
            player.openMenu(neurocell);
        }

        return Objects.requireNonNull(InteractionResult.sidedSuccess(false));
    }

    @Override
    protected void onRemove(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull BlockState newState,
        boolean isMoving
    ) {
        if (!state.is(Objects.requireNonNull(newState.getBlock()))) {
            EnumProperty<DoubleBlockHalf> halfProp = Objects.requireNonNull(HALF);
            DoubleBlockHalf half = state.getValue(halfProp);

            // Drop contents from lower half
            if (half == DoubleBlockHalf.LOWER) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof NeurocellBlockEntity neurocell) {
                    // Drop inventory contents
                    Containers.dropContents(level, pos, neurocell.getInventory());
                }
            }

            // Remove the other half
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? Objects.requireNonNull(pos.above()) : Objects.requireNonNull(pos.below());
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(halfProp) != half) {
                level.setBlock(otherPos, Objects.requireNonNull(Blocks.AIR.defaultBlockState()), 35);
                level.levelEvent(null, 2001, otherPos, Block.getId(otherState));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public boolean hasAnalogOutputSignal(@Nonnull BlockState state) {
        return state.getValue(Objects.requireNonNull(HALF)) == DoubleBlockHalf.LOWER;
    }

    @Override
    public int getAnalogOutputSignal(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos) {
        if (state.getValue(Objects.requireNonNull(HALF)) != DoubleBlockHalf.LOWER) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof NeurocellBlockEntity neurocell) {
            return neurocell.getProgressPercent() * 15 / 100;
        }
        return 0;
    }
}
