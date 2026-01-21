package com.devmod.foundry.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.block.entity.FoundryFaucetBlockEntity;

/**
 * Foundry faucet block, pulls molten fluid from drain/tank and pours into casting blocks.
 * Supports horizontal facing (north/south/east/west) and vertical facing (down).
 */
public final class FoundryFaucetBlock extends Block implements EntityBlock {
    public static final MapCodec<FoundryFaucetBlock> CODEC = simpleCodec(p -> new FoundryFaucetBlock());
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    // Voxel shapes for different facings
    private static final VoxelShape SHAPE_NORTH = Block.box(4, 4, 8, 12, 12, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(4, 4, 0, 12, 12, 8);
    private static final VoxelShape SHAPE_WEST = Block.box(8, 4, 4, 16, 12, 12);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 4, 4, 8, 12, 12);
    private static final VoxelShape SHAPE_DOWN = Block.box(4, 8, 4, 12, 16, 12);
    private static final VoxelShape SHAPE_UP = Block.box(4, 0, 4, 12, 8, 12);

    @Override
    @Nonnull
    protected MapCodec<? extends Block> codec() {
        return Objects.requireNonNull(CODEC);
    }

    @SuppressWarnings("this-escape")
    public FoundryFaucetBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(Objects.requireNonNull(MapColor.METAL))
            .strength(2.0f, 6.0f)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops()
            .noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Objects.requireNonNull(Direction.NORTH)));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        // If placed on the bottom of a block, face down (vertical faucet)
        if (clickedFace == Direction.DOWN) {
            return defaultBlockState().setValue(FACING, Direction.DOWN);
        }
        // If placed on top of a block, also use down-facing (vertical)
        if (clickedFace == Direction.UP) {
            return defaultBlockState().setValue(FACING, Direction.DOWN);
        }
        // Otherwise use horizontal facing opposite to player look direction
        Direction facing = context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new FoundryFaucetBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }
        return type == FoundryBlockEntities.FOUNDRY_FAUCET.get()
            ? (lvl, pos, st, be) -> ((FoundryFaucetBlockEntity) be).tickServer()
            : null;
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nonnull
    protected VoxelShape getShape(@Nonnull BlockState state, @Nonnull net.minecraft.world.level.BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            case DOWN -> SHAPE_DOWN;
            case UP -> SHAPE_UP;
        };
    }
}
