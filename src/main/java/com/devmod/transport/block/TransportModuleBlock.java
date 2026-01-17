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
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportEnhancement;
import com.devmod.transport.block.entity.TransportCoreBlockEntity;

/**
 * Transport module block that provides enhancements when placed adjacent to a Warp Core.
 *
 * <p>Module types:
 * <ul>
 *   <li>CHROMATIC - Allows color customization</li>
 *   <li>RANGE_AMPLIFIER - Increases teleport range (+100 blocks)</li>
 *   <li>GATE - Enables cross-dimensional transport</li>
 *   <li>FLUX_CAPACITOR - Instant charging (0 charge time)</li>
 *   <li>NETWORK_RELAY - Enables network mode</li>
 *   <li>MEMORY_CORE - Auto-save return points</li>
 *   <li>PARTY_BEACON - Enables party synchronization</li>
 * </ul>
 *
 * <p>From Bibbia Estetica Regola 3: Modules connect to sides of Core (N/S/E/W), NEVER above/below.
 */
public class TransportModuleBlock extends Block {

    /** The enhancement type this module provides. */
    private final TransportEnhancement moduleType;

    /** Whether this module is connected to an adjacent core. */
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    /** Color property for Chromatic Lens module. */
    public static final EnumProperty<TransportColor> COLOR =
        EnumProperty.create("color", TransportColor.class);

    /** Shape for module blocks. */
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

    @SuppressWarnings("this-escape")
    public TransportModuleBlock(TransportEnhancement moduleType, BlockBehaviour.Properties properties) {
        super(properties);
        this.moduleType = moduleType;

        // Register default state
        BlockState defaultState = stateDefinition.any()
            .setValue(CONNECTED, false);

        // Add color for chromatic modules
        if (moduleType == TransportEnhancement.CHROMATIC && defaultState.hasProperty(COLOR)) {
            defaultState = defaultState.setValue(COLOR, TransportColor.CYAN);
        }

        registerDefaultState(defaultState);
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTED);
        // Note: Chromatic module subclass should override this to add COLOR
    }

    /**
     * Returns the enhancement type this module provides.
     */
    @Nonnull
    public TransportEnhancement getModuleType() {
        return moduleType;
    }

    /**
     * Gets the module type from block state (for generic module detection).
     */
    @Nullable
    public TransportEnhancement getModuleType(BlockState state) {
        return moduleType;
    }

    @Override
    @Nonnull
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return SHAPE;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        BlockState state = defaultBlockState();

        // Check if adjacent to a core
        boolean connected = isAdjacentToCore(context.getLevel(), context.getClickedPos());
        state = state.setValue(CONNECTED, connected);

        return state;
    }

    /**
     * Checks if this position is horizontally adjacent to a TransportCoreBlock.
     */
    private boolean isAdjacentToCore(LevelAccessor level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacentPos = pos.relative(dir);
            BlockState adjacentState = level.getBlockState(adjacentPos);
            if (adjacentState.getBlock() instanceof TransportCoreBlock) {
                return true;
            }
        }
        return false;
    }

    /**
     * Notifies all adjacent cores when this module is placed or removed.
     */
    private void notifyAdjacentCores(Level level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacentPos = pos.relative(dir);
            BlockState adjacentState = level.getBlockState(adjacentPos);
            if (adjacentState.getBlock() instanceof TransportCoreBlock) {
                BlockEntity be = level.getBlockEntity(adjacentPos);
                if (be instanceof TransportCoreBlockEntity coreBe) {
                    coreBe.scanAdjacentModules();
                }
            }
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
            notifyAdjacentCores(level, pos);
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
            notifyAdjacentCores(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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

        // Update connected state
        boolean connected = isAdjacentToCore(level, pos);
        if (state.getValue(CONNECTED) != connected) {
            level.setBlock(pos, state.setValue(CONNECTED, connected), Block.UPDATE_ALL);
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
        // Only update for horizontal neighbors
        if (direction.getAxis().isHorizontal()) {
            boolean connected = isAdjacentToCore(level, pos);
            return state.setValue(CONNECTED, connected);
        }
        return state;
    }

    // === Light Emission ===

    /**
     * Modules emit light when connected.
     */
    public static int getLightEmission(BlockState state) {
        return state.getValue(CONNECTED) ? 7 : 2;
    }
}
