package com.devmod.events;

import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import com.devmod.DevMod;
import com.devmod.config.UsableConfigManager;
import com.devmod.stats.UsableStats;

@EventBusSubscriber(modid = DevMod.MODID)
public class UsableEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Called when an entity starts using an item.
     * Modifies the use duration based on UsableStats.
     */
    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        ItemStack stack = event.getItem();
        if (stack.isEmpty()) return;

        UsableStats stats = UsableConfigManager.getStats(stack);
        if (stats == null || stats.isDefault()) return;

        // Only modify if useDuration is explicitly set
        if (stats.useDuration > 0) {
            event.setDuration(stats.useDuration);
            LOGGER.debug("[UsableEvents] Modified use duration for {} to {} ticks",
                stack.getHoverName().getString(), stats.useDuration);
        }
    }

    /**
     * Called when an entity finishes using an item.
     * Applies cooldown based on UsableStats.
     */
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        ItemStack stack = event.getItem();
        if (stack.isEmpty()) return;

        if (!(event.getEntity() instanceof Player player)) return;

        UsableStats stats = UsableConfigManager.getStats(stack);
        if (stats == null || stats.isDefault()) return;

        // Apply cooldown if set
        if (stats.cooldownDuration > 0) {
            @Nonnull Item safeItem = Objects.requireNonNull(stack.getItem(), "item");
            player.getCooldowns().addCooldown(
                safeItem,
                stats.cooldownDuration);
            LOGGER.debug("[UsableEvents] Applied cooldown of {} ticks to {} for player {}",
                stats.cooldownDuration, stack.getHoverName().getString(), player.getName().getString());
        }
    }

    /**
     * Called every tick while an entity is using an item.
     * Can be used for additional modifications during use.
     */
    @SubscribeEvent
    public static void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
        // Currently no-op, but can be extended for continuous effects
    }

    /**
     * Called when an entity stops using an item before finishing.
     * Useful for items like bows that can be released early.
     */
    @SubscribeEvent
    public static void onItemUseStop(LivingEntityUseItemEvent.Stop event) {
        // Currently no-op, but can be extended for early-release mechanics
    }
}
