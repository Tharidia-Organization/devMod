package com.devmod.client.events;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.devmod.DevMod;
import com.devmod.client.panels.context.ContextDetector;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class CombatEvents {

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        // === PHASE 2 UX: Notify ContextDetector for combat events ===
        Minecraft mc = Minecraft.getInstance();
        LivingEntity target = event.getEntity();
        if (mc.player != null) {
            // If the player dealt damage
            if (event.getSource().getEntity() == mc.player && target != null) {
                ContextDetector.INSTANCE.onDamageDealt(target, event.getAmount());
            }
            // If the player received damage
            if (target == mc.player && event.getSource().getEntity() instanceof LivingEntity attacker) {
                ContextDetector.INSTANCE.onDamageReceived(attacker, event.getAmount());
            }
        }

        // Check that the attacker is a Mob
        if (!(event.getSource().getEntity() instanceof Mob attacker)) {
            return;
        }

        // Check that the target exists
        if (target == null) {
            return;
        }
        final LivingEntity nonNullTarget = target;

        // Get ENTITY_INTERACTION_RANGE attribute
        var reachAttr = attacker.getAttribute(Objects.requireNonNull(Attributes.ENTITY_INTERACTION_RANGE));
        if (reachAttr == null) {
            return; // Attribute not present
        }

        double customReach = reachAttr.getValue();
        if (customReach <= 0.0) {
            return; // Reach not configured or invalid
        }

        // Calculate effective distance
        double distanceSqr = attacker.distanceToSqr(nonNullTarget);
        double effectiveReach = customReach + (attacker.getBbWidth() / 2.0);
        double allowedDistanceSqr = (effectiveReach * effectiveReach) + 0.5;

        // Validate only direct attacks from mobs
        if (event.getSource().is(Objects.requireNonNull(DamageTypes.MOB_ATTACK))) {
            if (distanceSqr > allowedDistanceSqr) {
                event.setCanceled(true);
            }
        }
    }
}
