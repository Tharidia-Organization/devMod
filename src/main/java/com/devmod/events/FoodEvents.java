package com.devmod.events;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import com.devmod.DevMod;
import com.devmod.config.FoodConfigManager;
import com.devmod.stats.FoodStats;
@EventBusSubscriber(modid = DevMod.MODID)
public class FoodEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Called when an entity starts using an item.
     * Modifies the use duration based on custom consumptionTime.
     */
    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        ItemStack stack = event.getItem();
        if (stack.isEmpty() || !FoodConfigManager.isFood(stack)) {
            return;
        }

        FoodStats stats = FoodConfigManager.getStats(stack);
        if (stats.consumptionTime > 0 && stats.consumptionTime != 32) {
            // Modify the use duration
            event.setDuration(stats.consumptionTime);

            if (event.getEntity() instanceof Player) {
                LOGGER.debug("[FoodEvents] Modified consumption time to {} ticks for {}",
                    stats.consumptionTime, stack.getItem());
            }
        }
    }

    /**
     * Called when an entity finishes using an item.
     * Applies custom nutrition, saturation, and effects.
     */
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        ItemStack stack = event.getItem();
        LivingEntity entity = event.getEntity();

        if (stack.isEmpty() || !FoodConfigManager.isFood(stack)) {
            return;
        }

        // Only apply to players for now
        if (!(entity instanceof Player player)) {
            return;
        }

        FoodStats stats = FoodConfigManager.getStats(stack);

        // Check if we have custom stats (non-default values)
        boolean hasCustomStats = stats.nutrition > 0 ||
                                stats.saturation > 0 ||
                                !stats.effects.isEmpty();

        if (!hasCustomStats) {
            return; // Let vanilla handle it
        }

        // Note: We need to handle this carefully to not double-apply
        // The vanilla system already applies the food, so we apply the difference
        // For now, we'll just apply effects since nutrition is complex

        // Apply custom effects
        for (FoodStats.FoodEffect effectData : stats.effects) {
            if (effectData.effectId == null || effectData.effectId.isEmpty()) {
                continue;
            }

            // Check probability
            if (effectData.probability < 1.0f && player.getRandom().nextFloat() > effectData.probability) {
                continue;
            }

            // Get the effect from registry
            Optional<Holder.Reference<MobEffect>> effectHolder = getEffectHolder(effectData.effectId);
            if (effectHolder.isPresent()) {
                MobEffectInstance instance = new MobEffectInstance(
                    Objects.requireNonNull(effectHolder.get()),
                    effectData.duration,
                    effectData.amplifier
                );
                player.addEffect(instance);

                LOGGER.debug("[FoodEvents] Applied effect {} (duration={}, amp={}) to {}",
                    effectData.effectId, effectData.duration, effectData.amplifier, player.getName().getString());
            } else {
                LOGGER.warn("[FoodEvents] Unknown effect: {}", effectData.effectId);
            }
        }
    }

    /**
     * Get a MobEffect holder from the registry by resource location string.
     */
    private static Optional<Holder.Reference<MobEffect>> getEffectHolder(String effectId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(Objects.requireNonNull(effectId));
            return BuiltInRegistries.MOB_EFFECT.getHolder(Objects.requireNonNull(loc));
        } catch (Exception e) {
            LOGGER.warn("[FoodEvents] Failed to parse effect ID: {}", effectId, e);
            return Optional.empty();
        }
    }
}
