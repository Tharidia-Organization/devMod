package com.frenkvs.devmod;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = "devmod", bus = EventBusSubscriber.Bus.GAME)
public class GlobalMobEvents {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // Lavoriamo solo lato Server (dove si decidono le statistiche)
        if (event.getLevel().isClientSide) return;

        // Se l'entità è un Mob (Mostro/Animale)
        if (event.getEntity() instanceof Mob mob) {

            // Controlliamo se abbiamo salvato delle statistiche globali per questo tipo
            if (MobConfigManager.hasConfig(mob.getType())) {
                MobConfigManager.SavedStats stats = MobConfigManager.getGlobalStats(mob.getType());

                // APPLICA TUTTO
                if (stats != null) {
                    setAttribute(mob, Attributes.FOLLOW_RANGE, stats.range());
                    setAttribute(mob, Attributes.ATTACK_DAMAGE, stats.damage());
                    setAttribute(mob, Attributes.ARMOR, stats.armor());
                    setAttribute(mob, Attributes.LUCK, stats.attackReach()); // Usiamo Luck per il Reach

                    // Gestione speciale per la VITA
                    AttributeInstance hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
                    if (hpAttr != null) {
                        // Se la vita salvata è diversa da quella attuale base
                        if (hpAttr.getBaseValue() != stats.maxHealth()) {
                            hpAttr.setBaseValue(stats.maxHealth());
                            // IMPORTANTE: Curiamo il mob, altrimenti spawna con 20/100 vita
                            mob.setHealth(mob.getMaxHealth());
                        }
                    }
                }
            }
        }
    }

    private static void setAttribute(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double val) {
        AttributeInstance instance = mob.getAttribute(attr);
        if (instance != null) {
            instance.setBaseValue(val);
        }
    }
}