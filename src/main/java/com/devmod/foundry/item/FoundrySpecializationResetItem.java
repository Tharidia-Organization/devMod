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
import com.devmod.foundry.tool.FoundryToolBuilder;
import com.devmod.foundry.tool.FoundryToolData;
import com.devmod.foundry.tool.FoundryToolDefinitionRegistry;

/**
 * Item that clears the player's foundry specialization.
 */
public class FoundrySpecializationResetItem extends Item {
    public FoundrySpecializationResetItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            FoundryPlayerProgress progress = FoundryProgressAttachment.get(player);
            if (!progress.hasSpecialization()) {
                player.displayClientMessage(Component.translatable("message.devmod.foundry.specialization.none"), true);
                return InteractionResultHolder.fail(stack);
            }
            progress.setSpecialization(null);
            clearToolSpecializations(player);
            FoundryProgressAttachment.sync(player);
            player.displayClientMessage(Component.translatable("message.devmod.foundry.specialization.reset"), true);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void clearToolSpecializations(Player player) {
        clearToolSpecializations(player.getInventory().items);
        clearToolSpecializations(player.getInventory().armor);
        clearToolSpecializations(player.getInventory().offhand);
    }

    private static void clearToolSpecializations(Iterable<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            clearToolSpecialization(stack);
        }
    }

    private static boolean clearToolSpecialization(ItemStack stack) {
        FoundryToolData data = FoundryToolData.fromStack(stack).orElse(null);
        if (data == null || data.specialization() == null) {
            return false;
        }
        var definition = FoundryToolDefinitionRegistry.get(data.toolId());
        if (definition == null) {
            return false;
        }
        FoundryToolData updated = data.withSpecialization(null);
        updated.writeToStack(stack);
        var stats = FoundryToolBuilder.computeStats(
            definition,
            definition.parts(),
            updated.materials(),
            updated.modifiersWithEmbossment(),
            updated.quality()
        );
        FoundryToolBuilder.applyStats(stack, definition.kind(), stats);
        return true;
    }
}
