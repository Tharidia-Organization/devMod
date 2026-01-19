package com.devmod.foundry.block;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.devmod.foundry.block.entity.FoundryCastingBasinBlockEntity;

/**
 * Foundry casting basin block.
 */
public class FoundryCastingBasinBlock extends Block implements EntityBlock {
    public FoundryCastingBasinBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(3.0f, 6.0f)
            .sound(Objects.requireNonNull(SoundType.STONE))
            .requiresCorrectToolForDrops()
            .noOcclusion());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new FoundryCastingBasinBlockEntity(pos, state);
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }
        return type == com.devmod.foundry.FoundryBlockEntities.FOUNDRY_CASTING_BASIN.get()
            ? (lvl, pos, st, be) -> ((FoundryCastingBasinBlockEntity) be).tickServer()
            : null;
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
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FoundryCastingBasinBlockEntity casting) {
            InteractionResult result = casting.handleInteraction(player, hand);
            if (result == InteractionResult.CONSUME) {
                return ItemInteractionResult.CONSUME;
            }
            if (result == InteractionResult.SUCCESS) {
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
