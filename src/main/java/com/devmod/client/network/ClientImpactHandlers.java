package com.devmod.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.overlay.ImpactData;
import com.devmod.client.overlay.ImpactHistory;
import com.devmod.combat.HitHelper;
import com.devmod.damage.DamageBreakdown;
import com.devmod.network.ImpactSyncPayload;

/**
 * Client-side handlers for impact-related network payloads.
 * Processes ImpactSyncPayload to create and display ImpactData on the HUD.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientImpactHandlers {

    private ClientImpactHandlers() {}

    /**
     * Handles ImpactSyncPayload from the server.
     * Creates ImpactData and stores it for HUD display.
     */
    public static void handleImpactSync(ImpactSyncPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        // Get the victim entity from the world
        Entity entity = mc.level.getEntity(payload.victimEntityId());
        if (!(entity instanceof LivingEntity victim)) {
            return;
        }

        // Reconstruct body part from ordinal
        HitHelper.BodyPart bodyPart = HitHelper.BodyPart.values()[payload.bodyPartOrdinal()];

        // Reconstruct damage breakdown
        DamageBreakdown breakdown = new DamageBreakdown(
            payload.baseWeaponDamage(),
            payload.enchantBonus(),
            payload.pehkuiBonus(),
            payload.bodyPartMultiplier(),
            payload.armorPenBonus(),
            payload.finalDamage()
        );

        // Reconstruct hit point and slash direction
        Vec3 hitPoint = payload.getHitPoint();
        Vec3 slashDirection = payload.getSlashDirection();

        // Create ImpactData using the local player as attacker
        ImpactData impactData = new ImpactData(
            mc.player.getUUID(),
            victim,
            bodyPart,
            payload.multiplier(),
            breakdown,
            payload.attackSource(),
            payload.isRanged(),
            hitPoint,
            slashDirection
        );

        // Set actual damage if available
        if (payload.actualDamage() >= 0) {
            float healthBefore = victim.getHealth() + payload.actualDamage();
            float healthAfter = victim.getHealth();
            impactData.setActualDamage(healthBefore, healthAfter, payload.actualDamage());
        }

        // Store the impact data
        ImpactData.store(impactData);
        ImpactHistory.record(impactData);

        // Trigger VFX if hit point available
        if (hitPoint != null) {
            triggerImpactVfx(impactData, hitPoint, slashDirection, victim);
        }
    }

    /**
     * Triggers visual effects for the impact.
     */
    private static void triggerImpactVfx(ImpactData impactData, Vec3 hitPoint, Vec3 slashDirection, LivingEntity victim) {
        try {
            // Use reflection to call ImpactHudService.triggerImpactVfx
            Class<?> hudServiceClass = Class.forName("com.devmod.client.overlay.ImpactHudService");
            Class<?> impactDataClass = Class.forName("com.devmod.client.overlay.ImpactData");

            java.lang.reflect.Method vfxMethod = hudServiceClass.getMethod("triggerImpactVfx",
                impactDataClass, Vec3.class, Vec3.class, LivingEntity.class);
            vfxMethod.invoke(null, impactData, hitPoint, slashDirection, victim);

            // Trigger damage shake
            java.lang.reflect.Method shakeMethod = hudServiceClass.getMethod("triggerDamageShakeIfApplicable",
                LivingEntity.class, HitHelper.BodyPart.class, float.class, float.class, Vec3.class);
            shakeMethod.invoke(null, victim, impactData.bodyPart, impactData.bodyPartMultiplier,
                impactData.breakdown.finalDamage, hitPoint);
        } catch (Exception e) {
            // VFX failed, but impact data is still stored
        }
    }
}
