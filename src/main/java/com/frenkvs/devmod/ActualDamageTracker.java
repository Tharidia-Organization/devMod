package com.frenkvs.devmod;

import static com.frenkvs.devmod.DevMod.MODID;
import com.frenkvs.devmod.hud.ImpactData;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Traccia il danno REALE inflitto ai mob usando l'API NeoForge.
 *
 * LivingDamageEvent.Post fornisce:
 * - getNewDamage(): "the amount of health this entity lost during this sequence"
 * - getOriginalDamage(): "the original damage when LivingEntity#hurt was invoked"
 * - getBlockedDamage(): "the amount of damage reduced by a blocking action"
 * - getReduction(type): riduzione per tipo (ARMOR, ENCHANTMENTS, MOB_EFFECTS, ABSORPTION)
 *
 * Questo è il danno REALE già calcolato da Minecraft, non un ricalcolo.
 */
@EventBusSubscriber(modid = MODID)
public class ActualDamageTracker {

    /**
     * POST: Capture the REAL damage after Minecraft has applied all reductions.
     * Uses event.getNewDamage() directly which is the final value calculated by the game.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;

        int entityId = entity.getId();

        // === DATI REALI DALL'API NEOFORGE ===
        // getNewDamage() = "the amount of health this entity lost during this sequence"
        float actualDamage = event.getNewDamage();

        // Calculate health before/after from real damage
        float healthAfter = entity.getHealth();
        float healthBefore = healthAfter + actualDamage;

        // If the entity is dead, healthAfter will be 0 and healthBefore was the real damage
        if (entity.isDeadOrDying()) {
            healthBefore = actualDamage; // The real damage was all its life
        }

        // Update ImpactData if present and matches this entity
        ImpactData impact = ImpactData.get();
        if (impact != null) {
            LivingEntity target = impact.getTarget();
            if (target != null && target.getId() == entityId) {
                impact.setActualDamage(healthBefore, healthAfter, actualDamage);
            }
        }
    }
}
