package com.devmod.events;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;

import com.devmod.DevMod;
import com.devmod.config.handler.impl.FuelConfigHandler;
import com.devmod.stats.FuelStats;

@EventBusSubscriber(modid = DevMod.MODID)
public class FuelEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Called when the game checks the burn time of an item.
     * Applies custom burn time from FuelStats if configured.
     */
    @SubscribeEvent
    public static void onFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        // Check if this item has custom fuel stats
        FuelStats stats = FuelConfigHandler.INSTANCE.getStats(stack);

        // Only modify if override is enabled or we have custom component/global settings
        if (!stats.isOverrideDefault() && stats.getBurnTime() == 0) {
            return; // Let vanilla handle it
        }

        // Apply custom burn time with efficiency multiplier
        int effectiveBurnTime = stats.getEffectiveBurnTime();

        if (effectiveBurnTime > 0 || stats.isOverrideDefault()) {
            event.setBurnTime(effectiveBurnTime);

            LOGGER.debug("[FuelEvents] Set burn time to {} ticks for {} (base={}, efficiency={})",
                effectiveBurnTime, stack.getItem(), stats.getBurnTime(), stats.getEfficiencyMultiplier());
        }
    }
}
