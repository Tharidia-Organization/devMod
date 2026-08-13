package com.devmod.client.network;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.overlay.ImpactData;
import com.devmod.client.overlay.ImpactHistory;
import com.devmod.client.overlay.ImpactHudService;
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
        final var level = mc.level;
        final var player = mc.player;
        if (player == null || level == null) {
            return;
        }

        // Get the victim entity from the world
        Entity entity = level.getEntity(payload.victimEntityId());
        if (!(entity instanceof LivingEntity victim)) {
            return;
        }

        // Reconstruct body part from ordinal (raw wire value, must be range-checked)
        HitHelper.BodyPart[] bodyParts = HitHelper.BodyPart.values();
        int bodyPartOrdinal = payload.bodyPartOrdinal();
        if (bodyPartOrdinal < 0 || bodyPartOrdinal >= bodyParts.length) {
            return;
        }
        HitHelper.BodyPart bodyPart = bodyParts[bodyPartOrdinal];

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
            player.getUUID(),
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
     * Both this class and ImpactHudService are @OnlyIn(Dist.CLIENT), so direct calls are safe.
     */
    private static void triggerImpactVfx(ImpactData impactData, Vec3 hitPoint,
                                         @Nullable Vec3 slashDirection, LivingEntity victim) {
        ImpactHudService.triggerImpactVfx(impactData, hitPoint, slashDirection, victim);
        ImpactHudService.triggerDamageShakeIfApplicable(
            victim,
            impactData.getBodyPart(),
            impactData.getBodyPartMultiplier(),
            impactData.getBreakdown().getFinalDamage(),
            hitPoint
        );
    }
}
