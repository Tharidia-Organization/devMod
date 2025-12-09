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
     * POST: Cattura il danno REALE dopo che Minecraft ha applicato tutte le riduzioni.
     * Usa direttamente event.getNewDamage() che è il valore finale calcolato dal gioco.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;

        int entityId = entity.getId();

        // === DATI REALI DALL'API NEOFORGE ===
        // getNewDamage() = "the amount of health this entity lost during this sequence"
        float actualDamage = event.getNewDamage();

        // Calcola la salute prima/dopo dal danno reale
        float healthAfter = entity.getHealth();
        float healthBefore = healthAfter + actualDamage;

        // Se l'entità è morta, healthAfter sarà 0 e healthBefore era il danno reale
        if (entity.isDeadOrDying()) {
            healthBefore = actualDamage; // Il danno reale era tutta la sua vita
        }

        // Aggiorna ImpactData se presente e corrisponde a questa entità
        ImpactData impact = ImpactData.get();
        if (impact != null) {
            LivingEntity target = impact.getTarget();
            if (target != null && target.getId() == entityId) {
                impact.setActualDamage(healthBefore, healthAfter, actualDamage);
            }
        }
    }
}
