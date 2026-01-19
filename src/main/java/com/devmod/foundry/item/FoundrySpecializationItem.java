package com.devmod.foundry.item;

import javax.annotation.Nonnull;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.devmod.foundry.progression.FoundryPlayerProgress;
import com.devmod.foundry.progression.FoundryProgressAttachment;
import com.devmod.foundry.progression.FoundrySpecialization;

/**
 * Item that lets a player choose a foundry specialization once.
 */
public class FoundrySpecializationItem extends Item {
    private final FoundrySpecialization specialization;

    public FoundrySpecializationItem(FoundrySpecialization specialization, Item.Properties properties) {
        super(properties.stacksTo(1));
        this.specialization = specialization;
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            FoundryPlayerProgress progress = FoundryProgressAttachment.get(player);
            if (progress.hasSpecialization()) {
                player.displayClientMessage(Component.translatable("message.devmod.foundry.specialization.already"), true);
                return InteractionResultHolder.fail(stack);
            }
            progress.setSpecialization(specialization.getId());
            progress.tryAdvanceTier();
            FoundryProgressAttachment.sync(player);
            player.displayClientMessage(Component.translatable(
                "message.devmod.foundry.specialization.chosen",
                specialization.getDisplayName()
            ), true);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public FoundrySpecialization getSpecialization() {
        return specialization;
    }
}
