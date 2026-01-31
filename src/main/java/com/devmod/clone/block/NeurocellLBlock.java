package com.devmod.clone.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.clone.CloneItems;
import com.devmod.clone.block.entity.NeurocellLBlockEntity;
import com.devmod.clone.item.BioscannerItem;
/**
 * NeurocellL block - Large cloning chamber (2x2x2).
 * Can render larger entities than the standard Neurocell.
 * Features dual texture states (active/inactive) like the standard Neurocell.
 */
public final class NeurocellLBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<NeurocellLBlock> CODEC = simpleCodec(p -> new NeurocellLBlock());
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    /**
     * Enum representing the 8 positions in the 2x2x2 structure.
     * CENTER is the master block with the block entity.
     * Offsets are defined for NORTH facing and rotated based on actual facing.
     */
    public enum MultiBlockPart implements StringRepresentable {
        // Lower layer (Y=0) - 4 blocks
        // Base offsets are for NORTH facing (model extends +X, +Z)
        CENTER("center", 0, 0, 0),          // Master block with block entity
        LOWER_E("lower_e", 0, 1, 0),        // +X from center (NORTH facing)
        LOWER_S("lower_s", 0, 0, 1),        // +Z from center (NORTH facing)
        LOWER_SE("lower_se", 0, 1, 1),      // +X,+Z from center (NORTH facing)

        // Upper layer (Y=1) - 4 blocks
        UPPER_C("upper_c", 1, 0, 0),        // Above center
        UPPER_E("upper_e", 1, 1, 0),        // Above lower_e
        UPPER_S("upper_s", 1, 0, 1),        // Above lower_s
        UPPER_SE("upper_se", 1, 1, 1);      // Above lower_se

        private final String name;
        private final int yOffset;
        private final int baseXOffset;  // Base offset for NORTH facing
        private final int baseZOffset;  // Base offset for NORTH facing

        MultiBlockPart(String name, int yOffset, int xOffset, int zOffset) {
            this.name = name;
            this.yOffset = yOffset;
            this.baseXOffset = xOffset;
            this.baseZOffset = zOffset;
        }

        @Override
        @Nonnull
        public String getSerializedName() {
            return Objects.requireNonNull(name);
        }

        public int getYOffset() { return yOffset; }

        /**
         * Get the rotated X offset based on facing direction.
         * The model rotates around the block center (0.5, 0.5), causing position shifts:
         * - NORTH (0°):   (x, z) → (x, z)   - no change
         * - SOUTH (180°): (x, z) → (-x, -z) - both axes flip
         * - EAST (90° CW):  (x, z) → (-z, x)  - axes swap with Z negation
         * - WEST (270° CW): (x, z) → (z, -x)  - axes swap with X negation
         */
        public int getXOffset(Direction facing) {
            return switch (facing) {
                case NORTH -> baseXOffset;
                case SOUTH -> -baseXOffset;
                case EAST -> -baseZOffset;    // X becomes -Z
                case WEST -> baseZOffset;     // X becomes Z
                default -> baseXOffset;
            };
        }

        public int getZOffset(Direction facing) {
            return switch (facing) {
                case NORTH -> baseZOffset;
                case SOUTH -> -baseZOffset;
                case EAST -> baseXOffset;     // Z becomes X
                case WEST -> -baseXOffset;    // Z becomes -X
                default -> baseZOffset;
            };
        }

        public BlockPos getOffsetFromCenter(BlockPos centerPos, Direction facing) {
            return centerPos.offset(getXOffset(facing), yOffset, getZOffset(facing));
        }

        public BlockPos getCenterFromThis(BlockPos thisPos, Direction facing) {
            return thisPos.offset(-getXOffset(facing), -yOffset, -getZOffset(facing));
        }

        public boolean isCenter() {
            return this == CENTER;
        }

        public boolean isLowerLayer() {
            return yOffset == 0;
        }
    }

    public static final EnumProperty<MultiBlockPart> PART = EnumProperty.create("part", MultiBlockPart.class);

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return Objects.requireNonNull(CODEC);
    }

    public NeurocellLBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(Objects.requireNonNull(MapColor.COLOR_LIGHT_BLUE))
            .strength(4.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .lightLevel(state -> state.getValue(Objects.requireNonNull(ACTIVE)) ? 12 : 5)
            .isValidSpawn((state, level, pos, type) -> false)
            .isRedstoneConductor((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false));
        registerDefaultState(Objects.requireNonNull(stateDefinition.any()
            .setValue(Objects.requireNonNull(PART), MultiBlockPart.CENTER)
            .setValue(Objects.requireNonNull(FACING), Objects.requireNonNull(Direction.NORTH))
            .setValue(Objects.requireNonNull(ACTIVE), false)
            .setValue(Objects.requireNonNull(LINKED), false)));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING, ACTIVE, LINKED);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        // Only create block entity for center block
        return state.getValue(Objects.requireNonNull(PART)).isCenter() ? new NeurocellLBlockEntity(pos, state) : null;
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
        MultiBlockPart part = state.getValue(Objects.requireNonNull(PART));
        Direction facing = state.getValue(Objects.requireNonNull(FACING));

        // Calculate the bounding box start position based on facing
        // The structure extends in different directions for each facing:
        // - NORTH: (0,0) to (2,2) - extends +X, +Z
        // - SOUTH: (-1,-1) to (1,1) - extends -X, -Z
        // - EAST: (-1,0) to (1,2) - extends -X, +Z
        // - WEST: (0,-1) to (2,1) - extends +X, -Z
        double xStart = (facing == Direction.SOUTH || facing == Direction.EAST) ? -1.0 : 0.0;
        double zStart = (facing == Direction.SOUTH || facing == Direction.WEST) ? -1.0 : 0.0;

        // Return shape offset by the part's position within the structure
        return Objects.requireNonNull(Shapes.box(
            xStart - part.getXOffset(facing),
            0.0 - part.getYOffset(),
            zStart - part.getZOffset(facing),
            xStart + 2.0 - part.getXOffset(facing),
            2.0 - part.getYOffset(),
            zStart + 2.0 - part.getZOffset(facing)
        ));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        Direction facing = Objects.requireNonNull(context.getHorizontalDirection().getOpposite());

        // Check if all 8 positions are available
        for (MultiBlockPart part : MultiBlockPart.values()) {
            BlockPos partPos = Objects.requireNonNull(part.getOffsetFromCenter(pos, facing));
            if (partPos.getY() >= level.getMaxBuildHeight() || !level.getBlockState(partPos).canBeReplaced(context)) {
                return null;
            }
        }

        return Objects.requireNonNull(defaultBlockState()
            .setValue(Objects.requireNonNull(PART), MultiBlockPart.CENTER)
            .setValue(Objects.requireNonNull(FACING), facing)
            .setValue(Objects.requireNonNull(ACTIVE), false));
    }

    @Override
    public void setPlacedBy(
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull BlockState state,
        @Nullable LivingEntity placer,
        @Nonnull ItemStack stack
    ) {
        Direction facing = state.getValue(Objects.requireNonNull(FACING));
        EnumProperty<MultiBlockPart> partProp = Objects.requireNonNull(PART);
        // Place all 8 blocks
        for (MultiBlockPart part : MultiBlockPart.values()) {
            if (part.isCenter()) continue; // Center is already placed

            BlockPos partPos = Objects.requireNonNull(part.getOffsetFromCenter(pos, facing));
            level.setBlock(partPos, Objects.requireNonNull(state.setValue(partProp, part)), 3);
        }

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof NeurocellLBlockEntity neurocell) {
                neurocell.updateLinkedState();
            }
        }
    }

    @Override
    protected void neighborChanged(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull Block block,
        @Nonnull BlockPos fromPos,
        boolean isMoving
    ) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }

        BlockPos centerPos = getCenterPos(state, pos);
        BlockEntity be = level.getBlockEntity(centerPos);
        if (be instanceof NeurocellLBlockEntity neurocell) {
            neurocell.updateLinkedState();
        }
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
        EnumProperty<MultiBlockPart> partProp = Objects.requireNonNull(PART);
        DirectionProperty facingProp = Objects.requireNonNull(FACING);
        // If any part of the structure is broken, this block should break too
        MultiBlockPart part = state.getValue(partProp);
        Direction facing = state.getValue(facingProp);
        BlockPos centerPos = Objects.requireNonNull(part.getCenterFromThis(pos, facing));

        // Check if center still exists
        if (!part.isCenter()) {
            BlockState centerState = level.getBlockState(centerPos);
            if (!centerState.is(this) || centerState.getValue(partProp) != MultiBlockPart.CENTER) {
                return Objects.requireNonNull(Blocks.AIR.defaultBlockState());
            }
        }

        return state;
    }

    @Override
    protected boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        EnumProperty<MultiBlockPart> partProp = Objects.requireNonNull(PART);
        MultiBlockPart part = state.getValue(partProp);
        if (part.isCenter()) {
            return true;
        }

        Direction facing = state.getValue(Objects.requireNonNull(FACING));
        BlockPos centerPos = Objects.requireNonNull(part.getCenterFromThis(pos, facing));
        BlockState centerState = level.getBlockState(centerPos);
        return centerState.is(this) && centerState.getValue(partProp) == MultiBlockPart.CENTER;
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

    @Nonnull
    private BlockPos getCenterPos(BlockState state, BlockPos pos) {
        Direction facing = state.getValue(Objects.requireNonNull(FACING));
        return Objects.requireNonNull(state.getValue(Objects.requireNonNull(PART)).getCenterFromThis(pos, facing));
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

        BlockPos centerPos = getCenterPos(state, pos);
        BlockEntity be = level.getBlockEntity(centerPos);

        if (!(be instanceof NeurocellLBlockEntity neurocell)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Insert bioscanner
        boolean isBioscanner = stack.is(Objects.requireNonNull(CloneItems.BIOSCANNER.get()));
        boolean hasData = BioscannerItem.hasData(stack);

        if (isBioscanner && hasData) {
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

        BlockPos centerPos = getCenterPos(state, pos);
        BlockEntity be = level.getBlockEntity(centerPos);

        if (be instanceof NeurocellLBlockEntity neurocell) {
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
            EnumProperty<MultiBlockPart> partProp = Objects.requireNonNull(PART);
            DirectionProperty facingProp = Objects.requireNonNull(FACING);
            MultiBlockPart part = state.getValue(partProp);
            Direction facing = state.getValue(facingProp);
            BlockPos centerPos = Objects.requireNonNull(part.getCenterFromThis(pos, facing));

            // Drop contents from center block
            if (part.isCenter()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof NeurocellLBlockEntity neurocell) {
                    ItemStack bioscanner = Objects.requireNonNull(neurocell.extractBioscanner());
                    if (!bioscanner.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), bioscanner);
                    }
                }
            }

            // Remove all other parts of the structure
            for (MultiBlockPart otherPart : MultiBlockPart.values()) {
                if (otherPart == part) continue;

                BlockPos otherPos = Objects.requireNonNull(otherPart.getOffsetFromCenter(centerPos, facing));
                BlockState otherState = level.getBlockState(otherPos);
                if (otherState.is(this)) {
                    level.setBlock(otherPos, Objects.requireNonNull(Blocks.AIR.defaultBlockState()), 35);
                    level.levelEvent(null, 2001, otherPos, Block.getId(otherState));
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public boolean hasAnalogOutputSignal(@Nonnull BlockState state) {
        return state.getValue(Objects.requireNonNull(PART)).isCenter();
    }

    @Override
    public int getAnalogOutputSignal(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos) {
        if (!state.getValue(Objects.requireNonNull(PART)).isCenter()) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof NeurocellLBlockEntity neurocell) {
            return neurocell.getProgressPercent() * 15 / 100;
        }
        return 0;
    }
}
