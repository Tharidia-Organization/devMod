package com.devmod.foundry.block.entity;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.block.FoundryDrainBlock;

/**
 * Drain block entity for foundry fluid IO.
 */
public class FoundryDrainBlockEntity extends FoundryComponentBlockEntity {
    public FoundryDrainBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_DRAIN.get()), pos, state);
    }

    @Override
    public void setControllerPos(@Nullable BlockPos pos) {
        super.setControllerPos(pos);
        updateInStructureState(pos != null);
    }

    private void updateInStructureState(boolean inStructure) {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(FoundryDrainBlock.IN_STRUCTURE) && state.getValue(FoundryDrainBlock.IN_STRUCTURE) != inStructure) {
            level.setBlock(worldPosition, state.setValue(FoundryDrainBlock.IN_STRUCTURE, inStructure), 3);
        }
    }

    public InteractionResult handleBucketInteraction(@Nonnull Player player, @Nonnull InteractionHand hand) {
        Level level = Objects.requireNonNull(getLevel());
        ItemStack held = player.getItemInHand(hand);
        FoundryControllerBlockEntity controller = getController(level);
        if (controller == null) {
            return InteractionResult.PASS;
        }

        if (held.is(Objects.requireNonNull(Items.BUCKET))) {
            FluidStack drained = controller.drainMolten(1000, true);
            if (!drained.isEmpty()) {
                ItemStack filled = Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(drained.getFluid()).getBucket()).getDefaultInstance());
                held.shrink(1);
                if (held.isEmpty()) {
                    player.setItemInHand(hand, filled);
                } else if (!player.getInventory().add(filled)) {
                    player.drop(filled, false);
                }
                return InteractionResult.CONSUME;
            }
        } else {
            var bucketItem = held.getItem();
            if (bucketItem instanceof net.minecraft.world.item.BucketItem bucket && bucket.content != net.minecraft.world.level.material.Fluids.EMPTY) {
                FluidStack toFill = new FluidStack(Objects.requireNonNull(bucket.content), 1000);
                int filled = controller.fillMolten(toFill, true);
                if (filled > 0) {
                    ItemStack emptyBucket = new ItemStack(Objects.requireNonNull(Items.BUCKET));
                    if (!player.isCreative()) {
                        held.shrink(1);
                    }
                    if (held.isEmpty()) {
                        player.setItemInHand(hand, emptyBucket);
                    } else if (!player.getInventory().add(emptyBucket)) {
                        player.drop(emptyBucket, false);
                    }
                    return InteractionResult.CONSUME;
                }
            }
        }

        return InteractionResult.PASS;
    }
}
