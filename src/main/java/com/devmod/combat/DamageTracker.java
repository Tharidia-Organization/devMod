package com.devmod.combat;

import java.lang.reflect.Method;
import java.util.UUID;

import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.devmod.DevMod;

import static com.devmod.DevMod.MODID;

@EventBusSubscriber(modid = MODID)
public class DamageTracker {

    /**
     * POST: Capture the REAL damage after Minecraft has applied all reductions.
     * Uses event.getNewDamage() directly which is the final value calculated by the game.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();

        int entityId = entity.getId();

        // === DATI REALI DALL'API NEOFORGE ===
        // getNewDamage() = "the amount of health this entity lost during this sequence"
        float actualDamage = event.getNewDamage();
        float originalDamage = event.getOriginalDamage();
        float blockedDamage = event.getBlockedDamage();

        // Calculate health before/after from real damage
        float healthAfter = entity.getHealth();
        float healthBefore = healthAfter + actualDamage;

        // If the entity is dead, healthAfter will be 0 and healthBefore was the real damage
        if (entity.isDeadOrDying()) {
            healthBefore = actualDamage; // The real damage was all its life
        }

        // BUG-003 FIX: Calculate total reduction from original vs actual
        // Note: Detailed breakdown by type (armor/enchants/effects) not available in this API version
        // We estimate based on entity armor value as a rough approximation
        float totalReduction = originalDamage - actualDamage - blockedDamage;
        float estimatedArmorReduction = 0f;
        float estimatedOtherReduction = 0f;

        if (totalReduction > 0 && entity.getArmorValue() > 0) {
            // Rough estimate: assume armor reduces proportionally to armor value
            // Vanilla formula: damage * (1 - min(20, armor) / 25)
            float armorValue = Math.min(20, entity.getArmorValue());
            float armorReductionPercent = armorValue / 25f;
            estimatedArmorReduction = Math.min(totalReduction, originalDamage * armorReductionPercent);
            estimatedOtherReduction = totalReduction - estimatedArmorReduction;
        } else {
            estimatedOtherReduction = Math.max(0, totalReduction);
        }

        // Update ImpactData if present and matches this entity (client-only)
        if (FMLEnvironment.dist.isClient()) {
            updateImpactDataClientSafe(entityId, healthBefore, healthAfter, actualDamage,
                estimatedArmorReduction, estimatedOtherReduction, blockedDamage);
        }
    }

    // ========== Client-safe helpers ==========

    private static Method impactDataGetMethod;
    private static Method impactDataGetTargetMethod;
    private static Method impactDataSetActualDamageMethod;
    private static Method impactDataSetBreakdownMethod;
    private static Method impactDpsRecordMethod;
    private static java.lang.reflect.Field attackerUUIDField;
    private static boolean clientMethodsInitialized = false;
    private static boolean clientUpdateErrorLogged = false;

    /**
     * Updates ImpactData on client side using reflection to avoid class loading on server.
     */
    private static void updateImpactDataClientSafe(int entityId, float healthBefore, float healthAfter,
            float actualDamage, float armorReduction, float otherReduction, float blockedDamage) {
        try {
            if (!clientMethodsInitialized) {
                initClientMethods();
            }

            if (impactDataGetMethod == null) return;

            Object impact = impactDataGetMethod.invoke(null);
            if (impact == null) return;

            Object target = impactDataGetTargetMethod.invoke(impact);
            if (target == null) return;

            int targetId = ((LivingEntity) target).getId();
            if (targetId != entityId) return;

            impactDataSetActualDamageMethod.invoke(impact, healthBefore, healthAfter, actualDamage);
            impactDataSetBreakdownMethod.invoke(impact, armorReduction, otherReduction, 0f, 0f, blockedDamage);

            UUID attackerUUID = (UUID) attackerUUIDField.get(impact);
            if (attackerUUID != null && impactDpsRecordMethod != null) {
                impactDpsRecordMethod.invoke(null, attackerUUID, actualDamage);
            }
        } catch (Exception e) {
            if (!clientUpdateErrorLogged) {
                clientUpdateErrorLogged = true;
                DevMod.LOGGER.debug("[DamageTracker] Failed to update ImpactData via reflection", e);
            }
        }
    }

    private static void initClientMethods() {
        clientMethodsInitialized = true;
        try {
            Class<?> impactDataClass = Class.forName("com.devmod.client.overlay.ImpactData");
            impactDataGetMethod = impactDataClass.getMethod("get");
            impactDataGetTargetMethod = impactDataClass.getMethod("getTarget");
            impactDataSetActualDamageMethod = impactDataClass.getMethod("setActualDamage", float.class, float.class, float.class);
            impactDataSetBreakdownMethod = impactDataClass.getMethod("setDamageReductionBreakdown", float.class, float.class, float.class, float.class, float.class);
            attackerUUIDField = impactDataClass.getField("attackerUUID");

            Class<?> dpsTrackerClass = Class.forName("com.devmod.client.overlay.ImpactDpsTracker");
            impactDpsRecordMethod = dpsTrackerClass.getMethod("recordDamage", UUID.class, float.class);
        } catch (Exception e) {
            DevMod.LOGGER.debug("[DamageTracker] ImpactData client hooks unavailable", e);
        }
    }
}
