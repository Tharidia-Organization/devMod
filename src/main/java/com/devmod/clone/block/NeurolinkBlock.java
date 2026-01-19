package com.devmod.clone.block;

import java.util.Objects;

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
public final class NeurolinkBlock extends Block {

    public static final MapCodec<NeurolinkBlock> CODEC = simpleCodec(p -> new NeurolinkBlock());

    public static final BooleanProperty NORTH = Objects.requireNonNull(PipeBlock.NORTH);
    public static final BooleanProperty EAST = Objects.requireNonNull(PipeBlock.EAST);
    public static final BooleanProperty SOUTH = Objects.requireNonNull(PipeBlock.SOUTH);
    public static final BooleanProperty WEST = Objects.requireNonNull(PipeBlock.WEST);
    public static final BooleanProperty UP = Objects.requireNonNull(PipeBlock.UP);
    public static final BooleanProperty DOWN = Objects.requireNonNull(PipeBlock.DOWN);

    // VoxelShapes aligned with JSON models (Y=0-6 for horizontal, connectors at ends)
    private static final VoxelShape CORE = Objects.requireNonNull(Block.box(6, 0, 6, 10, 6, 10));
    private static final VoxelShape NORTH_SHAPE = Objects.requireNonNull(Block.box(5, 0, 0, 11, 6, 6));
    private static final VoxelShape SOUTH_SHAPE = Objects.requireNonNull(Block.box(5, 0, 10, 11, 6, 16));
    private static final VoxelShape EAST_SHAPE = Objects.requireNonNull(Block.box(10, 0, 5, 16, 6, 11));
    private static final VoxelShape WEST_SHAPE = Objects.requireNonNull(Block.box(0, 0, 5, 6, 6, 11));
    private static final VoxelShape UP_SHAPE = Objects.requireNonNull(Block.box(5, 5, 5, 11, 16, 11));
    private static final VoxelShape DOWN_SHAPE = Objects.requireNonNull(Block.box(6, 0, 6, 10, 1, 10));

    @Override
    @Nonnull
    protected MapCodec<? extends Block> codec() {
        return Objects.requireNonNull(CODEC);
    }

    public NeurolinkBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(1.5f)
            .noOcclusion()
            .lightLevel(state -> 3));
        registerDefaultState(Objects.requireNonNull(
            Objects.requireNonNull(
                Objects.requireNonNull(
                    Objects.requireNonNull(
                        Objects.requireNonNull(
                            Objects.requireNonNull(
                                Objects.requireNonNull(stateDefinition.any())
                                    .setValue(Objects.requireNonNull(NORTH), false)
                            ).setValue(Objects.requireNonNull(EAST), false)
                        ).setValue(Objects.requireNonNull(SOUTH), false)
                    ).setValue(Objects.requireNonNull(WEST), false)
                ).setValue(Objects.requireNonNull(UP), false)
            ).setValue(Objects.requireNonNull(DOWN), false)
        ));
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
        return Objects.requireNonNull(getConnectionState(level, pos));
    }

    private BlockState getConnectionState(LevelAccessor level, BlockPos pos) {
        return Objects.requireNonNull(
            Objects.requireNonNull(
                Objects.requireNonNull(
                    Objects.requireNonNull(
                        Objects.requireNonNull(
                            Objects.requireNonNull(
                                Objects.requireNonNull(defaultBlockState())
                                    .setValue(Objects.requireNonNull(NORTH), canConnect(level, pos, Objects.requireNonNull(Direction.NORTH)))
                            ).setValue(Objects.requireNonNull(EAST), canConnect(level, pos, Objects.requireNonNull(Direction.EAST)))
                        ).setValue(Objects.requireNonNull(SOUTH), canConnect(level, pos, Objects.requireNonNull(Direction.SOUTH)))
                    ).setValue(Objects.requireNonNull(WEST), canConnect(level, pos, Objects.requireNonNull(Direction.WEST)))
                ).setValue(Objects.requireNonNull(UP), canConnect(level, pos, Objects.requireNonNull(Direction.UP)))
            ).setValue(Objects.requireNonNull(DOWN), canConnect(level, pos, Objects.requireNonNull(Direction.DOWN)))
        );
    }

    private boolean canConnect(LevelAccessor level, BlockPos pos, Direction dir) {
        BlockPos neighborPos = Objects.requireNonNull(pos.relative(Objects.requireNonNull(dir)));
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighbor = neighborState.getBlock();

        // Connect to other neurolinks
        if (neighbor instanceof NeurolinkBlock) {
            return true;
        }

        // Connect to clone system blocks
        return neighbor == CloneBlocks.NEUROCELL.get()
            || neighbor == CloneBlocks.NEUROCELL_L.get()
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
        VoxelShape shape = Objects.requireNonNull(CORE);
        if (state.getValue(Objects.requireNonNull(NORTH))) {
            shape = Objects.requireNonNull(Shapes.or(Objects.requireNonNull(shape), Objects.requireNonNull(NORTH_SHAPE)));
        }
        if (state.getValue(Objects.requireNonNull(SOUTH))) {
            shape = Objects.requireNonNull(Shapes.or(Objects.requireNonNull(shape), Objects.requireNonNull(SOUTH_SHAPE)));
        }
        if (state.getValue(Objects.requireNonNull(EAST))) {
            shape = Objects.requireNonNull(Shapes.or(Objects.requireNonNull(shape), Objects.requireNonNull(EAST_SHAPE)));
        }
        if (state.getValue(Objects.requireNonNull(WEST))) {
            shape = Objects.requireNonNull(Shapes.or(Objects.requireNonNull(shape), Objects.requireNonNull(WEST_SHAPE)));
        }
        if (state.getValue(Objects.requireNonNull(UP))) {
            shape = Objects.requireNonNull(Shapes.or(Objects.requireNonNull(shape), Objects.requireNonNull(UP_SHAPE)));
        }
        if (state.getValue(Objects.requireNonNull(DOWN))) {
            shape = Objects.requireNonNull(Shapes.or(Objects.requireNonNull(shape), Objects.requireNonNull(DOWN_SHAPE)));
        }
        return shape;
    }
}
