package com.devmod.telemetry;

import java.util.Objects;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.devmod.DevMod;

@EventBusSubscriber(modid = DevMod.MODID)
public class EnchantmentSkillTracker {

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW, receiveCanceled = true)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!TelemetryService.INSTANCE.getSettings().skillTrackingEnabled()) return;

        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();

        // Track attacker enchantments (Fire Aspect, Sharpness, etc.)
        if (source.getEntity() instanceof LivingEntity attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            if (!weapon.isEmpty()) {
                trackWeaponEnchantments(attacker, weapon, victim);
            }
        }

        // Track victim's Thorns enchantment
        if (source.getEntity() instanceof LivingEntity attacker) {
            trackThornsEnchantment(victim, attacker);
        }

        // Track Fire damage from Fire Aspect
        if (source.type().msgId().contains("fire") && source.getEntity() instanceof LivingEntity attacker) {
            String skillId = "enchant_fire_aspect";
            TelemetryService.INSTANCE.logSkillHit(attacker, skillId);
        }
    }

    private static void trackWeaponEnchantments(LivingEntity attacker, ItemStack weapon, LivingEntity victim) {
        // Use modern DataComponents API instead of deprecated getEnchantments()
        ItemEnchantments enchantments = Objects.requireNonNull(weapon.getOrDefault(Objects.requireNonNull(DataComponents.ENCHANTMENTS), Objects.requireNonNull(ItemEnchantments.EMPTY)));

        for (var entry : enchantments.entrySet()) {
            int level = entry.getIntValue();

            // Use registry key instead of deprecated description()
            String enchantId = entry.getKey().getRegisteredName().replace(":", "_");
            String skillId = "enchant_" + enchantId + "_" + level;

            // Cast: Using weapon with enchantment
            TelemetryService.INSTANCE.logSkillCast(attacker, skillId);

            // Hit: Enchantment applied damage/effect
            // For damage enchants (Sharpness, Smite, etc.), always mark as hit since they boost damage
            if (enchantId.contains("sharpness") || enchantId.contains("smite") ||
                enchantId.contains("bane") || enchantId.contains("power")) {
                TelemetryService.INSTANCE.logSkillHit(attacker, skillId);
            }

            // Fire Aspect: Mark as cast, hit will be tracked on burn damage
            if (enchantId.contains("fire_aspect")) {
                // Hit tracked separately when fire damage occurs
            }

            // Knockback: Mark as hit if target is alive (knockback applied)
            if (enchantId.contains("knockback") && victim.isAlive()) {
                TelemetryService.INSTANCE.logSkillHit(attacker, skillId);
            }
        }
    }

    private static void trackThornsEnchantment(LivingEntity victim, LivingEntity attacker) {
        // Check all armor pieces for Thorns
        for (ItemStack armor : victim.getArmorSlots()) {
            if (armor.isEmpty()) continue;

            // Use modern DataComponents API instead of deprecated getEnchantments()
            ItemEnchantments enchantments = Objects.requireNonNull(armor.getOrDefault(Objects.requireNonNull(DataComponents.ENCHANTMENTS), Objects.requireNonNull(ItemEnchantments.EMPTY)));
            for (var entry : enchantments.entrySet()) {
                // Use registry key instead of deprecated description()
                String enchantId = entry.getKey().getRegisteredName();

                if (enchantId.contains("thorns")) {
                    int level = entry.getIntValue();
                    String skillId = "enchant_thorns_" + level;

                    // Cast: Being attacked while wearing Thorns
                    TelemetryService.INSTANCE.logSkillCast(victim, skillId);

                    // Hit: Thorns damage dealt (30% + 15% per level chance)
                    // We'll mark as hit probabilistically or wait for damage event
                    // For simplicity, mark as hit if attacker takes damage after this
                    if (attacker.getHealth() < attacker.getMaxHealth()) {
                        TelemetryService.INSTANCE.logSkillHit(victim, skillId);
                    }
                }
            }
        }
    }
}
