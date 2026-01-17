package com.devmod.transport.block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.transport.TransportColor;
import com.devmod.transport.block.entity.TransportCoreBlockEntity;

/**
 * Frame segment block for visual portal progression.
 *
 * <p>From Bibbia Estetica Regola 3: Frame builds ALWAYS above the Core.
 *
 * <p>Frame levels (based on segment count):
 * <ul>
 *   <li>0 = Solo core (particles on pad)</li>
 *   <li>1 = 1-3 frame segments (mini rift 1x2)</li>
 *   <li>2 = 4-7 frame segments (partial portal 2x2)</li>
 *   <li>3 = 8 frame segments (complete portal 2x3)</li>
 * </ul>
 */
public class TransportFrameBlock extends Block {

    /** Color property matching the core's color. */
    public static final EnumProperty<TransportColor> COLOR =
        EnumProperty.create("color", TransportColor.class);

    /** Whether this frame segment is connected to a core below. */
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    /** Whether there's another frame segment below. */
    public static final BooleanProperty BELOW = BooleanProperty.create("below");

    /** Whether there's another frame segment above. */
    public static final BooleanProperty ABOVE = BooleanProperty.create("above");

    /** Shape for frame segment (thin vertical frame). */
    private static final VoxelShape SHAPE = Shapes.or(
        // Vertical pillar
        Block.box(6, 0, 6, 10, 16, 10),
        // Outer frame
        Block.box(2, 2, 2, 14, 14, 14)
    );

    /** Connected shape (more visible frame). */
    private static final VoxelShape SHAPE_CONNECTED = Shapes.or(
        Block.box(4, 0, 4, 12, 16, 12),
        Block.box(1, 1, 1, 15, 15, 15)
    );

    @SuppressWarnings("this-escape")
    public TransportFrameBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(COLOR, TransportColor.CYAN)
            .setValue(CONNECTED, false)
            .setValue(BELOW, false)
            .setValue(ABOVE, false));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR, CONNECTED, BELOW, ABOVE);
    }

    @Override
    @Nonnull
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return state.getValue(CONNECTED) ? SHAPE_CONNECTED : SHAPE;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        // Determine connection state
        boolean connected = hasCoreBelow(level, pos);
        boolean below = hasFrameBelow(level, pos);
        boolean above = hasFrameAbove(level, pos);

        // Get color from core below or default
        TransportColor color = getCoreColorBelow(level, pos);

        return defaultBlockState()
            .setValue(COLOR, color)
            .setValue(CONNECTED, connected)
            .setValue(BELOW, below)
            .setValue(ABOVE, above);
    }

    /**
     * Checks if there's a TransportCoreBlock somewhere below this position.
     */
    private boolean hasCoreBelow(LevelAccessor level, BlockPos pos) {
        for (int y = 1; y <= 10; y++) {
            BlockPos checkPos = pos.below(y);
            BlockState state = level.getBlockState(checkPos);

            if (state.getBlock() instanceof TransportCoreBlock) {
                return true;
            } else if (!(state.getBlock() instanceof TransportFrameBlock) && !state.isAir()) {
                // Hit a non-frame, non-air block
                return false;
            }
        }
        return false;
    }

    /**
     * Checks if there's a frame segment directly below.
     */
    private boolean hasFrameBelow(LevelAccessor level, BlockPos pos) {
        BlockState belowState = level.getBlockState(pos.below());
        return belowState.getBlock() instanceof TransportFrameBlock;
    }

    /**
     * Checks if there's a frame segment directly above.
     */
    private boolean hasFrameAbove(LevelAccessor level, BlockPos pos) {
        BlockState aboveState = level.getBlockState(pos.above());
        return aboveState.getBlock() instanceof TransportFrameBlock;
    }

    /**
     * Gets the color from the core block below.
     */
    @Nonnull
    private TransportColor getCoreColorBelow(LevelAccessor level, BlockPos pos) {
        for (int y = 1; y <= 10; y++) {
            BlockPos checkPos = pos.below(y);
            BlockState state = level.getBlockState(checkPos);

            if (state.getBlock() instanceof TransportCoreBlock) {
                return state.getValue(TransportCoreBlock.COLOR);
            } else if (!(state.getBlock() instanceof TransportFrameBlock) && !state.isAir()) {
                break;
            }
        }
        return TransportColor.CYAN;
    }

    /**
     * Finds the core block entity below this frame.
     */
    @Nullable
    private TransportCoreBlockEntity findCoreBelow(Level level, BlockPos pos) {
        for (int y = 1; y <= 10; y++) {
            BlockPos checkPos = pos.below(y);
            BlockState state = level.getBlockState(checkPos);

            if (state.getBlock() instanceof TransportCoreBlock) {
                BlockEntity be = level.getBlockEntity(checkPos);
                if (be instanceof TransportCoreBlockEntity coreBe) {
                    return coreBe;
                }
                return null;
            } else if (!(state.getBlock() instanceof TransportFrameBlock) && !state.isAir()) {
                break;
            }
        }
        return null;
    }

    /**
     * Notifies the core below when frame segments change.
     */
    private void notifyCoreBelow(Level level, BlockPos pos) {
        TransportCoreBlockEntity core = findCoreBelow(level, pos);
        if (core != null) {
            core.scanAdjacentModules();
        }
    }

    // === Placement ===

    @Override
    public void setPlacedBy(
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull BlockState state,
        @Nullable LivingEntity placer,
        @Nonnull ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide) {
            notifyCoreBelow(level, pos);
            // Update neighbors
            updateNeighborFrames(level, pos);
        }
    }

    // === Removal ===

    @Override
    protected void onRemove(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull BlockState newState,
        boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            notifyCoreBelow(level, pos);
            updateNeighborFrames(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Updates the above/below state of neighbor frames.
     */
    private void updateNeighborFrames(Level level, BlockPos pos) {
        // Update block below
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (belowState.getBlock() instanceof TransportFrameBlock) {
            boolean hasAbove = level.getBlockState(belowPos.above()).getBlock() instanceof TransportFrameBlock;
            if (belowState.getValue(ABOVE) != hasAbove) {
                level.setBlock(belowPos, belowState.setValue(ABOVE, hasAbove), Block.UPDATE_ALL);
            }
        }

        // Update block above
        BlockPos abovePos = pos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        if (aboveState.getBlock() instanceof TransportFrameBlock) {
            boolean hasBelow = level.getBlockState(abovePos.below()).getBlock() instanceof TransportFrameBlock;
            if (aboveState.getValue(BELOW) != hasBelow) {
                level.setBlock(abovePos, aboveState.setValue(BELOW, hasBelow), Block.UPDATE_ALL);
            }
        }
    }

    // === Block State Updates ===

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
        if (direction == Direction.UP) {
            boolean above = neighborState.getBlock() instanceof TransportFrameBlock;
            return state.setValue(ABOVE, above);
        } else if (direction == Direction.DOWN) {
            boolean below = neighborState.getBlock() instanceof TransportFrameBlock;
            boolean connected = hasCoreBelow(level, pos);
            TransportColor color = getCoreColorBelow(level, pos);
            return state
                .setValue(BELOW, below)
                .setValue(CONNECTED, connected)
                .setValue(COLOR, color);
        }
        return state;
    }

    // === Neighbor Changed ===

    @Override
    protected void neighborChanged(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull Block neighborBlock,
        @Nonnull BlockPos neighborPos,
        boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);

        if (level.isClientSide) {
            return;
        }

        // Recalculate connection state
        boolean connected = hasCoreBelow(level, pos);
        boolean below = hasFrameBelow(level, pos);
        boolean above = hasFrameAbove(level, pos);
        TransportColor color = getCoreColorBelow(level, pos);

        BlockState newState = state
            .setValue(CONNECTED, connected)
            .setValue(BELOW, below)
            .setValue(ABOVE, above)
            .setValue(COLOR, color);

        if (!state.equals(newState)) {
            level.setBlock(pos, newState, Block.UPDATE_ALL);
        }
    }

    // === Light Emission ===

    /**
     * Frame segments emit light when connected.
     */
    public static int getLightEmission(BlockState state) {
        return state.getValue(CONNECTED) ? 12 : 4;
    }
}
