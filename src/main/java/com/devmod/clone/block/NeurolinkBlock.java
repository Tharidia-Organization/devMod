package com.devmod.clone.block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.clone.CloneBlocks;

/**
 * Neurolink block - connection cables for the cloning system.
 * Connects NEUROCELL to REFORMER blocks.
 *
 * <p>Automatically connects to adjacent clone blocks.
 */
public class NeurolinkBlock extends Block {

    public static final MapCodec<NeurolinkBlock> CODEC = simpleCodec(p -> new NeurolinkBlock());

    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty UP = PipeBlock.UP;
    public static final BooleanProperty DOWN = PipeBlock.DOWN;

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape NORTH_SHAPE = Block.box(5, 5, 0, 11, 11, 5);
    private static final VoxelShape SOUTH_SHAPE = Block.box(5, 5, 11, 11, 11, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(11, 5, 5, 16, 11, 11);
    private static final VoxelShape WEST_SHAPE = Block.box(0, 5, 5, 5, 11, 11);
    private static final VoxelShape UP_SHAPE = Block.box(5, 11, 5, 11, 16, 11);
    private static final VoxelShape DOWN_SHAPE = Block.box(5, 0, 5, 11, 5, 11);

    @Override
    @Nonnull
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public NeurolinkBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(1.5f)
            .noOcclusion()
            .lightLevel(state -> 3));
        registerDefaultState(stateDefinition.any()
            .setValue(NORTH, false)
            .setValue(EAST, false)
            .setValue(SOUTH, false)
            .setValue(WEST, false)
            .setValue(UP, false)
            .setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        return getConnectionState(context.getLevel(), context.getClickedPos());
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
        return getConnectionState(level, pos);
    }

    private BlockState getConnectionState(LevelAccessor level, BlockPos pos) {
        return defaultBlockState()
            .setValue(NORTH, canConnect(level, pos, Direction.NORTH))
            .setValue(EAST, canConnect(level, pos, Direction.EAST))
            .setValue(SOUTH, canConnect(level, pos, Direction.SOUTH))
            .setValue(WEST, canConnect(level, pos, Direction.WEST))
            .setValue(UP, canConnect(level, pos, Direction.UP))
            .setValue(DOWN, canConnect(level, pos, Direction.DOWN));
    }

    private boolean canConnect(LevelAccessor level, BlockPos pos, Direction dir) {
        BlockPos neighborPos = pos.relative(dir);
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighbor = neighborState.getBlock();

        // Connect to other neurolinks
        if (neighbor instanceof NeurolinkBlock) {
            return true;
        }

        // Connect to clone system blocks
        return neighbor == CloneBlocks.NEUROCELL.get()
            || neighbor == CloneBlocks.REFORMER.get();
    }

    @Override
    @Nonnull
    public VoxelShape getShape(
        @Nonnull BlockState state,
        @Nonnull BlockGetter level,
        @Nonnull BlockPos pos,
        @Nonnull CollisionContext context
    ) {
        VoxelShape shape = CORE;
        if (state.getValue(NORTH)) shape = Shapes.or(shape, NORTH_SHAPE);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, SOUTH_SHAPE);
        if (state.getValue(EAST)) shape = Shapes.or(shape, EAST_SHAPE);
        if (state.getValue(WEST)) shape = Shapes.or(shape, WEST_SHAPE);
        if (state.getValue(UP)) shape = Shapes.or(shape, UP_SHAPE);
        if (state.getValue(DOWN)) shape = Shapes.or(shape, DOWN_SHAPE);
        return shape;
    }
}
