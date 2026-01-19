package com.devmod.foundry.item;

import javax.annotation.Nonnull;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Foundry guidebook item that opens the in-game manual.
 */
public class FoundryGuideItem extends Item {
    public FoundryGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(
        @Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.devmod.foundry.client.screen.FoundryGuideScreen.open();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
