package com.devmod.clone.block;

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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.CloneItems;
import com.devmod.clone.block.entity.NeurocellLBlockEntity;
import com.devmod.clone.item.BioscannerItem;

/**
 * NeurocellL block - Large cloning chamber (3x3x3).
 * Can render larger entities than the standard Neurocell.
 */
public class NeurocellLBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<NeurocellLBlock> CODEC = simpleCodec(p -> new NeurocellLBlock());
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /**
     * Enum representing the 27 positions in the 3x3x3 structure.
     * Format: Y_XZ where Y is layer (BOTTOM/MIDDLE/TOP), X is x-offset, Z is z-offset
     * CENTER is the master block with the block entity.
     */
    public enum MultiBlockPart implements StringRepresentable {
        // Bottom layer (Y=0) - 9 blocks
        CENTER("center", 0, 0, 0),           // Master block with block entity
        BOTTOM_N("bottom_n", 0, 0, -1),
        BOTTOM_S("bottom_s", 0, 0, 1),
        BOTTOM_E("bottom_e", 0, 1, 0),
        BOTTOM_W("bottom_w", 0, -1, 0),
        BOTTOM_NE("bottom_ne", 0, 1, -1),
        BOTTOM_NW("bottom_nw", 0, -1, -1),
        BOTTOM_SE("bottom_se", 0, 1, 1),
        BOTTOM_SW("bottom_sw", 0, -1, 1),

        // Middle layer (Y=1) - 9 blocks
        MIDDLE_C("middle_c", 1, 0, 0),
        MIDDLE_N("middle_n", 1, 0, -1),
        MIDDLE_S("middle_s", 1, 0, 1),
        MIDDLE_E("middle_e", 1, 1, 0),
        MIDDLE_W("middle_w", 1, -1, 0),
        MIDDLE_NE("middle_ne", 1, 1, -1),
        MIDDLE_NW("middle_nw", 1, -1, -1),
        MIDDLE_SE("middle_se", 1, 1, 1),
        MIDDLE_SW("middle_sw", 1, -1, 1),

        // Top layer (Y=2) - 9 blocks
        TOP_C("top_c", 2, 0, 0),
        TOP_N("top_n", 2, 0, -1),
        TOP_S("top_s", 2, 0, 1),
        TOP_E("top_e", 2, 1, 0),
        TOP_W("top_w", 2, -1, 0),
        TOP_NE("top_ne", 2, 1, -1),
        TOP_NW("top_nw", 2, -1, -1),
        TOP_SE("top_se", 2, 1, 1),
        TOP_SW("top_sw", 2, -1, 1);

        private final String name;
        private final int yOffset;
        private final int xOffset;
        private final int zOffset;

        MultiBlockPart(String name, int yOffset, int xOffset, int zOffset) {
            this.name = name;
            this.yOffset = yOffset;
            this.xOffset = xOffset;
            this.zOffset = zOffset;
        }

        @Override
        @Nonnull
        public String getSerializedName() {
            return name;
        }

        public int getYOffset() { return yOffset; }
        public int getXOffset() { return xOffset; }
        public int getZOffset() { return zOffset; }

        public BlockPos getOffsetFromCenter(BlockPos centerPos) {
            return centerPos.offset(xOffset, yOffset, zOffset);
        }

        public BlockPos getCenterFromThis(BlockPos thisPos) {
            return thisPos.offset(-xOffset, -yOffset, -zOffset);
        }

        public boolean isCenter() {
            return this == CENTER;
        }
    }

    public static final EnumProperty<MultiBlockPart> PART = EnumProperty.create("part", MultiBlockPart.class);

    // Hitbox - each block returns a shape that covers the full 3x3x3 from its perspective
    private static final VoxelShape SHAPE_CENTER = Shapes.box(-1.0, 0.0, -1.0, 2.0, 3.0, 2.0);

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public NeurocellLBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(4.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .lightLevel(state -> 12)
            .isValidSpawn((state, level, pos, type) -> false)
            .isRedstoneConductor((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false));
        registerDefaultState(stateDefinition.any()
            .setValue(PART, MultiBlockPart.CENTER)
            .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        // Only create block entity for center block
        return state.getValue(PART).isCenter() ? new NeurocellLBlockEntity(pos, state) : null;
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        // Only render the center block's model, others are invisible
        return state.getValue(PART).isCenter() ? RenderShape.MODEL : RenderShape.INVISIBLE;
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
        MultiBlockPart part = state.getValue(PART);
        // Return shape offset to cover the full 3x3x3 structure
        return Shapes.box(
            -1.0 - part.getXOffset(),
            0.0 - part.getYOffset(),
            -1.0 - part.getZOffset(),
            2.0 - part.getXOffset(),
            3.0 - part.getYOffset(),
            2.0 - part.getZOffset()
        );
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection().getOpposite();

        // Check if all 27 positions are available
        for (MultiBlockPart part : MultiBlockPart.values()) {
            BlockPos partPos = part.getOffsetFromCenter(pos);
            if (partPos.getY() >= level.getMaxBuildHeight() || !level.getBlockState(partPos).canBeReplaced(context)) {
                return null;
            }
        }

        return defaultBlockState()
            .setValue(PART, MultiBlockPart.CENTER)
            .setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull BlockState state,
        @Nullable LivingEntity placer,
        @Nonnull ItemStack stack
    ) {
        Direction facing = state.getValue(FACING);

        // Place all 27 blocks
        for (MultiBlockPart part : MultiBlockPart.values()) {
            if (part.isCenter()) continue; // Center is already placed

            BlockPos partPos = part.getOffsetFromCenter(pos);
            level.setBlock(partPos, state.setValue(PART, part), 3);
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
        // If any part of the structure is broken, this block should break too
        MultiBlockPart part = state.getValue(PART);
        BlockPos centerPos = part.getCenterFromThis(pos);

        // Check if center still exists
        if (!part.isCenter()) {
            BlockState centerState = level.getBlockState(centerPos);
            if (!centerState.is(this) || centerState.getValue(PART) != MultiBlockPart.CENTER) {
                return Blocks.AIR.defaultBlockState();
            }
        }

        return state;
    }

    @Override
    protected boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        MultiBlockPart part = state.getValue(PART);
        if (part.isCenter()) {
            return true;
        }

        BlockPos centerPos = part.getCenterFromThis(pos);
        BlockState centerState = level.getBlockState(centerPos);
        return centerState.is(this) && centerState.getValue(PART) == MultiBlockPart.CENTER;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level,
        @Nonnull BlockState state,
        @Nonnull BlockEntityType<T> type
    ) {
        if (level.isClientSide || !state.getValue(PART).isCenter()) {
            return null;
        }
        return type == CloneBlockEntities.NEUROCELL_L.get()
            ? (lvl, pos, st, be) -> ((NeurocellLBlockEntity) be).tick()
            : null;
    }

    private BlockPos getCenterPos(BlockState state, BlockPos pos) {
        return state.getValue(PART).getCenterFromThis(pos);
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
        boolean isBioscanner = stack.is(CloneItems.BIOSCANNER.get());
        boolean hasData = BioscannerItem.hasData(stack);

        if (isBioscanner && hasData) {
            boolean inserted = neurocell.insertBioscanner(stack.copy());
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
            return InteractionResult.SUCCESS;
        }

        BlockPos centerPos = getCenterPos(state, pos);
        BlockEntity be = level.getBlockEntity(centerPos);

        if (be instanceof NeurocellLBlockEntity neurocell) {
            ItemStack extracted = neurocell.extractBioscanner();
            if (!extracted.isEmpty()) {
                if (!player.addItem(extracted)) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY() + 1, pos.getZ(), extracted);
                }
                return InteractionResult.CONSUME;
            }

            neurocell.showStatus(player);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onRemove(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull BlockState newState,
        boolean isMoving
    ) {
        if (!state.is(newState.getBlock())) {
            MultiBlockPart part = state.getValue(PART);
            BlockPos centerPos = part.getCenterFromThis(pos);

            // Drop contents from center block
            if (part.isCenter()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof NeurocellLBlockEntity neurocell) {
                    ItemStack bioscanner = neurocell.extractBioscanner();
                    if (!bioscanner.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), bioscanner);
                    }
                }
            }

            // Remove all other parts of the structure
            for (MultiBlockPart otherPart : MultiBlockPart.values()) {
                if (otherPart == part) continue;

                BlockPos otherPos = otherPart.getOffsetFromCenter(centerPos);
                BlockState otherState = level.getBlockState(otherPos);
                if (otherState.is(this)) {
                    level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(null, 2001, otherPos, Block.getId(otherState));
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public boolean hasAnalogOutputSignal(@Nonnull BlockState state) {
        return state.getValue(PART).isCenter();
    }

    @Override
    public int getAnalogOutputSignal(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos) {
        if (!state.getValue(PART).isCenter()) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof NeurocellLBlockEntity neurocell) {
            return neurocell.getProgressPercent() * 15 / 100;
        }
        return 0;
    }
}
