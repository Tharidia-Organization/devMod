package com.devmod.blocks;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NexusPortalBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<NexusPortalColor> COLOR = EnumProperty.create("color", NexusPortalColor.class);

    private static final VoxelShape X_SHAPE = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape Z_SHAPE = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    public NexusPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(Objects.requireNonNull(stateDefinition.any()
            .setValue(Objects.requireNonNull(AXIS), Direction.Axis.X)
            .setValue(Objects.requireNonNull(COLOR), NexusPortalColor.COMBAT)));
    }

    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        return defaultBlockState().setValue(Objects.requireNonNull(AXIS),
            Objects.requireNonNull(context.getHorizontalDirection().getAxis()));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, COLOR);
    }

    @Override
    @Nonnull
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        if (state.getValue(Objects.requireNonNull(AXIS)) == Direction.Axis.Z) {
            return Objects.requireNonNull(Z_SHAPE);
        }
        return Objects.requireNonNull(X_SHAPE);
    }

    @Override
    @Nonnull
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                                        @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return Objects.requireNonNull(Shapes.empty());
    }
}
