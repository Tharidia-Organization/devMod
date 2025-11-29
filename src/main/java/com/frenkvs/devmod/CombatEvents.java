package com.frenkvs.devmod;

import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = "devmod", bus = EventBusSubscriber.Bus.GAME)
public class CombatEvents {

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        // Intercettiamo i danni ricevuti dal giocatore (o da chiunque altro)
        // Verifichiamo che la fonte sia un Mob
        if (event.getSource().getEntity() instanceof Mob attacker) {

            // Cerchiamo se abbiamo impostato un reach personalizzato
            double customReach = 0;
            if (attacker.getAttribute(Attributes.ENTITY_INTERACTION_RANGE) != null) {
                customReach = attacker.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            }

            // SE IL REACH È 0 (Default Vanilla), significa che non lo abbiamo modificato.
            // In questo caso lasciamo fare a Minecraft normale e usciamo.
            if (customReach <= 0.0) return;

            // --- CALCOLO DISTANZA REALE ---
            // Calcoliamo la distanza al quadrato tra i piedi del mostro e i piedi del giocatore
            double distanceSqr = attacker.distanceToSqr(event.getEntity());

            // Il mostro colpisce dal bordo del suo corpo, non dal centro.
            // Quindi dobbiamo considerare la larghezza del mostro nel calcolo.
            // Formula approssimativa: (Reach + LarghezzaMostro)^2
            double effectiveReach = customReach + (attacker.getBbWidth() / 2.0);
            double allowedDistanceSqr = effectiveReach * effectiveReach;

            // Aggiungiamo un piccolo margine di tolleranza (0.5 blocchi) per il lag
            allowedDistanceSqr += 0.5;

            // Se è un attacco fisico (Pugno/Spada)
            if (event.getSource().is(DamageTypes.MOB_ATTACK)) {

                // Debug console (Togli i // all'inizio se vuoi vedere i numeri in console)
                // System.out.println("Colpo da " + attacker.getName().getString() + " | Dist: " + Math.sqrt(distanceSqr) + " | Max: " + Math.sqrt(allowedDistanceSqr));

                if (distanceSqr > allowedDistanceSqr) {
                    // SEI TROPPO LONTANO! ANNULLA IL DANNO
                    event.setCanceled(true);
                }
            }
        }
    }
}