package com.devmod.client.combat;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import com.devmod.DevMod;
import com.devmod.combat.signature.SoulImprint;
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class SignatureWeaponEvents {

    /**
     * Add Soul Imprint information to weapon tooltips.
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        // Check if this item has a Soul Imprint
        if (!SoulImprint.hasImprint(stack)) {
            return;
        }

        // Build and add tooltip lines
        List<Component> imprintTooltip = SignatureWeaponTooltip.buildTooltip(stack);
        if (!imprintTooltip.isEmpty()) {
            event.getToolTip().addAll(imprintTooltip);
        }
    }
}
