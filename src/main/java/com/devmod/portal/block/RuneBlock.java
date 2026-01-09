package com.devmod.portal.block;

import javax.annotation.Nonnull;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.portal.RuneType;

/**
 * Base block class for portal runes.
 * Rune blocks are wall-mounted and can attach to floor, ceiling, or walls.
 *
 * <p>Each rune type provides specific effects:
 * <ul>
 *   <li>HASTE - Instant teleportation</li>
 *   <li>GATE - Cross-dimension linking</li>
 *   <li>ENHANCER - Extended linking range (1000 blocks)</li>
 *   <li>STRONG_ENHANCER - Very extended linking range (10000 blocks)</li>
 *   <li>INFINITY - Unlimited linking range</li>
 * </ul>
 */
public final class RuneBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final MapCodec<RuneBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            RuneType.CODEC.fieldOf("rune_type").forGetter(RuneBlock::getRuneType),
            propertiesCodec()
        ).apply(instance, RuneBlock::new)
    );

    // Block state properties
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;

    // Shapes for floor/ceiling (1 pixel high, full width)
    public static final VoxelShape FLOOR_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);
    public static final VoxelShape CEILING_SHAPE = Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    // Shapes for wall mounting (1 pixel thick, full height/width)
    public static final VoxelShape NORTH_SHAPE = Block.box(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D);
    public static final VoxelShape SOUTH_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D);
    public static final VoxelShape EAST_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D);
    public static final VoxelShape WEST_SHAPE = Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    private final RuneType runeType;

    public RuneBlock(RuneType runeType, Properties properties) {
        super(properties);
        this.runeType = runeType;
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.WALL));
    }

    @Override
    @Nonnull
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    /**
     * Returns the rune type this block represents.
     */
    @Nonnull
    public RuneType getRuneType() {
        return runeType;
    }

    @Override
    @Nonnull
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        return switch (face) {
            case FLOOR -> FLOOR_SHAPE;
            case CEILING -> CEILING_SHAPE;
            case WALL -> switch (facing) {
                case NORTH -> NORTH_SHAPE;
                case SOUTH -> SOUTH_SHAPE;
                case EAST -> EAST_SHAPE;
                case WEST -> WEST_SHAPE;
                default -> NORTH_SHAPE;
            };
        };
    }

    /**
     * Returns the direction to the block this rune is mounted on.
     *
     * @param face the attachment face (FLOOR, CEILING, or WALL)
     * @param facing the horizontal facing direction
     * @return the direction toward the mounted block
     */
    @Nonnull
    public static Direction getMountedOffset(@Nonnull AttachFace face, @Nonnull Direction facing) {
        return switch (face) {
            case FLOOR -> Direction.DOWN;
            case CEILING -> Direction.UP;
            case WALL -> facing.getOpposite();
        };
    }

    @Override
    public void animateTick(@Nonnull BlockState state, @Nonnull Level level,
                           @Nonnull BlockPos pos, @Nonnull RandomSource random) {
        if (random.nextInt(5) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 0.5 + random.nextDouble() * 0.5;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;

            level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0, 0.1, 0);
        }
    }

    @Override
    public int getLightEmission(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return 7;
    }
}
