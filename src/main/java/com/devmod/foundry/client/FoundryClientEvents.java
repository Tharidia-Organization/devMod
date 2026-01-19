package com.devmod.foundry.client;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import com.devmod.DevMod;
import com.devmod.foundry.quality.FoundryItemQuality;
import com.devmod.foundry.quality.MaterialQuality;

/**
 * Client-side tooltip hooks for foundry metadata.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class FoundryClientEvents {
    private FoundryClientEvents() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !FoundryItemQuality.hasQuality(stack)) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        MaterialQuality quality = FoundryItemQuality.getQuality(stack);
        tooltip.add(quality.getColoredDisplayName());
        float purity = FoundryItemQuality.getPurity(stack);
        tooltip.add(Component.translatable("tooltip.devmod.foundry.purity", String.format("%.0f%%", purity * 100)));
    }
}
