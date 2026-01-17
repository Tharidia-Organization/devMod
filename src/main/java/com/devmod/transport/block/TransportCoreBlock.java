package com.devmod.transport.block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.transport.TransportColor;
import com.devmod.transport.block.entity.TransportCoreBlockEntity;

/**
 * Warp Core block - the central transport node block.
 * Handles player charging when standing on it and provides configuration GUI.
 *
 * <p>Features:
 * <ul>
 *   <li>COLOR property - 16 transport colors</li>
 *   <li>ACTIVE property - whether the node is active/linked</li>
 *   <li>FRAME_LEVEL property - visual progression based on frame segments (0-3)</li>
 * </ul>
 *
 * <p>Frame levels:
 * <ul>
 *   <li>0 = Solo core (particles on pad)</li>
 *   <li>1 = 1-3 frame segments (mini rift 1x2)</li>
 *   <li>2 = 4-7 frame segments (partial portal 2x2)</li>
 *   <li>3 = 8 frame segments (complete portal 2x3)</li>
 * </ul>
 */
public class TransportCoreBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<TransportCoreBlock> CODEC = simpleCodec(TransportCoreBlock::new);

    /** Color property for the 16 transport colors. */
    public static final EnumProperty<TransportColor> COLOR =
        EnumProperty.create("color", TransportColor.class);

    /** Whether the transport node is active (has valid destination). */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** Frame level 0-3 based on adjacent frame segment count. */
    public static final IntegerProperty FRAME_LEVEL = IntegerProperty.create("frame_level", 0, 3);

    /** Shape for the core block (slightly recessed pad). */
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(0, 0, 0, 16, 2, 16),   // Base plate
        Block.box(2, 2, 2, 14, 4, 14)    // Core
    );

    @SuppressWarnings("this-escape")
    public TransportCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(COLOR, TransportColor.CYAN)
            .setValue(ACTIVE, false)
            .setValue(FRAME_LEVEL, 0));
    }

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOR, ACTIVE, FRAME_LEVEL);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(COLOR, TransportColor.CYAN)
            .setValue(ACTIVE, false)
            .setValue(FRAME_LEVEL, 0);
    }

    @Override
    @Nonnull
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return SHAPE;
    }

    @Override
    @Nonnull
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                                        @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return SHAPE;
    }

    // === Block Entity ===

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new TransportCoreBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> blockEntityType
    ) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof TransportCoreBlockEntity coreBe) {
                coreBe.tick();
            }
        };
    }

    // === Interactions ===

    /**
     * Shift+Click opens configuration GUI.
     * Normal click does nothing special.
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

        // Shift+Click opens config GUI
        if (player.isShiftKeyDown() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TransportCoreBlockEntity coreBe) {
                coreBe.openConfigGui(serverPlayer);
                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * Handle item interactions (e.g., dye to change color).
     */
    @Override
    @Nonnull
    protected ItemInteractionResult useItemOn(
        @Nonnull ItemStack stack,
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull Player player,
        @Nonnull InteractionHand hand,
        @Nonnull BlockHitResult hitResult
    ) {
        // Could add dye color changing here
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // === Entity Inside (Charging) ===

    /**
     * When a player stands on the core, start charging for teleport.
     * Follows TelepadBlockEntity pattern.
     */
    @Override
    public void entityInside(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull Entity entity
    ) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }

        // Check if player is actually on top of the block
        if (!isPlayerOnCore(player, pos)) {
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TransportCoreBlockEntity coreBe) {
            coreBe.onPlayerStanding(player);
        }
    }

    /**
     * Checks if player is standing on top of the core block.
     */
    private boolean isPlayerOnCore(Player player, BlockPos corePos) {
        BlockPos playerPos = player.blockPosition();
        return playerPos.getX() == corePos.getX()
            && playerPos.getZ() == corePos.getZ()
            && Math.abs(playerPos.getY() - corePos.getY()) <= 1;
    }

    // === Neighbor Updates (Module Detection) ===

    /**
     * When a neighbor block changes, rescan for adjacent modules.
     */
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

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TransportCoreBlockEntity coreBe) {
            coreBe.scanAdjacentModules();
        }
    }

    // === Placement/Removal ===

    @Override
    public void setPlacedBy(
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull BlockState state,
        @Nullable LivingEntity placer,
        @Nonnull ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (level.isClientSide) {
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TransportCoreBlockEntity coreBe) {
            if (placer instanceof Player player) {
                coreBe.setCreatorId(player.getUUID());
            }
            coreBe.scanAdjacentModules();
            coreBe.updateRegistry();
        }
    }

    @Override
    @Nonnull
    public BlockState playerWillDestroy(
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull BlockState state,
        @Nonnull Player player
    ) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TransportCoreBlockEntity coreBe) {
                coreBe.removeFromRegistry();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull BlockState newState,
        boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TransportCoreBlockEntity coreBe) {
                coreBe.removeFromRegistry();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // === Light Emission ===

    /**
     * Emit light based on active state and color.
     */
    public static int getLightEmission(BlockState state) {
        if (state.getValue(ACTIVE)) {
            return 10;
        }
        return 4;
    }

    // === Frame Level Utilities ===

    /**
     * Calculates frame level from segment count.
     *
     * @param segmentCount number of frame segments above the core
     * @return frame level 0-3
     */
    public static int calculateFrameLevel(int segmentCount) {
        if (segmentCount == 0) {
            return 0;
        } else if (segmentCount <= 3) {
            return 1;
        } else if (segmentCount <= 7) {
            return 2;
        } else {
            return 3;
        }
    }
}
