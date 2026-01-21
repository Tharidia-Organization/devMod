package com.devmod.clone.block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.block.entity.ImprinterBlockEntity;

/**
 * Imprinter block for automatic entity scanning.
 * Detects entities within range and fills nearby empty BIOSCANNERs.
 *
 * <p>Features:
 * <ul>
 *   <li>Scans living entities within 5 block radius</li>
 *   <li>Auto-fills empty BIOSCANNER items in adjacent inventories</li>
 *   <li>Visual particles during scanning</li>
 *   <li>Redstone output when scanning</li>
 * </ul>
 */
public final class ImprinterBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ImprinterBlock> CODEC = simpleCodec(p -> new ImprinterBlock());
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public ImprinterBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(3.5f)
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .lightLevel(state -> state.getValue(ACTIVE) ? 12 : 3));
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @Nonnull
    public VoxelShape getShape(
        @Nonnull BlockState state,
        @Nonnull BlockGetter level,
        @Nonnull BlockPos pos,
        @Nonnull CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new ImprinterBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level,
        @Nonnull BlockState state,
        @Nonnull BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }
        return type == CloneBlockEntities.IMPRINTER.get()
            ? (lvl, pos, st, be) -> {
                ImprinterBlockEntity imprinter = (ImprinterBlockEntity) be;
                if (imprinter.needsTicking()) {
                    imprinter.tick();
                }
            }
            : null;
    }

    /**
     * Called when an entity is inside the block's collision box.
     * Used to detect entities standing on the imprinter.
     */
    @Override
    protected void entityInside(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull Entity entity
    ) {
        if (!level.isClientSide && entity instanceof LivingEntity) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ImprinterBlockEntity imprinter) {
                imprinter.setHasEntity(true);
            }
        }
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
        if (be instanceof ImprinterBlockEntity imprinter) {
            imprinter.triggerManualScan(player);
        }

        return InteractionResult.CONSUME;
    }

    /**
     * Sets the active state of the imprinter block.
     */
    public static void setActive(Level level, BlockPos pos, BlockState state, boolean active) {
        if (state.getValue(ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(@Nonnull BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ImprinterBlockEntity imprinter) {
            return imprinter.isScanning() ? 15 : 0;
        }
        return 0;
    }
}
