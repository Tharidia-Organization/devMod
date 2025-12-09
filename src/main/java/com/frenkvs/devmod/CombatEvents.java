package com.frenkvs.devmod;

import com.frenkvs.devmod.panels.context.ContextDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Client-side combat event handler for ContextDetector notifications.
 * Uses Minecraft.getInstance() which is only available on client.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class CombatEvents {

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        // === FASE 2 UX: Notifica ContextDetector per eventi di combattimento ===
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            LivingEntity target = event.getEntity();
            // Se il player ha inflitto danno
            if (event.getSource().getEntity() == mc.player && target != null) {
                ContextDetector.INSTANCE.onDamageDealt(target, event.getAmount());
            }
            // Se il player ha ricevuto danno
            if (target == mc.player && event.getSource().getEntity() instanceof LivingEntity attacker) {
                ContextDetector.INSTANCE.onDamageReceived(attacker, event.getAmount());
            }
        }

        // Verifica che l'attaccante sia un Mob
        if (!(event.getSource().getEntity() instanceof Mob attacker)) {
            return;
        }

        // Verifica che il target esista
        if (event.getEntity() == null) {
            return;
        }

        // Ottieni attributo ENTITY_INTERACTION_RANGE
        var reachAttr = attacker.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (reachAttr == null) {
            return; // Attributo non presente
        }

        double customReach = reachAttr.getValue();
        if (customReach <= 0.0) {
            return; // Reach non configurato o invalido
        }

        // Calcola distanza effettiva
        double distanceSqr = attacker.distanceToSqr(event.getEntity());
        double effectiveReach = customReach + (attacker.getBbWidth() / 2.0);
        double allowedDistanceSqr = (effectiveReach * effectiveReach) + 0.5;

        // Valida solo attacchi diretti da mob
        if (event.getSource().is(DamageTypes.MOB_ATTACK)) {
            if (distanceSqr > allowedDistanceSqr) {
                event.setCanceled(true);
            }
        }
    }
}
