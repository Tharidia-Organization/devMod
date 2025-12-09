package com.frenkvs.devmod.client;

import com.frenkvs.devmod.HitHelper.BodyPart;
import com.frenkvs.devmod.effects.ShakeEffect;
import com.frenkvs.devmod.effects.ShakeManager;
import com.frenkvs.devmod.hud.DamageBreakdown;
import com.frenkvs.devmod.hud.HeadshotFlashEffect;
import com.frenkvs.devmod.hud.Impact3DPanelManager;
import com.frenkvs.devmod.hud.ImpactData;
import com.frenkvs.devmod.hud.ImpactVFX;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only helper for VFX operations.
 * This class is only loaded on the client side and contains all Minecraft.getInstance() calls.
 * Called via DistExecutor from common code.
 */
public class ClientVFXHelper {

    /**
     * Spawns an evasion panel for arrow attacks on client.
     * Called from ArrowEvents via DistExecutor.
     */
    public static void spawnArrowEvasionPanel(Player shooter, LivingEntity target, Vec3 hitPos, Vec3 savedTargetPos) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            DamageBreakdown evasionBreakdown = new DamageBreakdown(
                shooter.getMainHandItem(),
                target,
                0f,
                0f,
                0f
            );

            ImpactData evasionData = new ImpactData(
                shooter.getUUID(),
                target,
                BodyPart.BODY,
                0f,
                evasionBreakdown,
                "§c§lEVADED! (Arrow)",
                true,
                savedTargetPos,
                shooter.getLookAngle()
            );

            ImpactData.store(evasionData);
            Impact3DPanelManager.INSTANCE.spawnPanelFromImpact(evasionData);
        });
    }

    /**
     * Spawns an evasion panel for melee attacks on client.
     * Called from DamageHandler via DistExecutor.
     */
    public static void spawnMeleeEvasionPanel(Player player, LivingEntity target, Vec3 targetPos, Vec3 lookDir) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            DamageBreakdown evasionBreakdown = new DamageBreakdown(
                player.getMainHandItem(),
                target,
                0f,
                0f,
                0f
            );

            ImpactData evasionData = new ImpactData(
                player.getUUID(),
                target,
                BodyPart.BODY,
                0f,
                evasionBreakdown,
                "§c§lEVADED!",
                false,
                targetPos,
                lookDir
            );

            ImpactData.store(evasionData);
            Impact3DPanelManager.INSTANCE.spawnPanelFromImpact(evasionData);
        });
    }

    /**
     * Adds impact VFX at the specified location.
     * Called from DamageHandler via DistExecutor.
     */
    public static void addImpactVFX(Vec3 hitPoint, Vec3 slashDirection, ImpactData impactData) {
        // Trigger headshot flash effect if hit was to the head
        if (impactData != null && impactData.bodyPart == BodyPart.HEAD) {
            HeadshotFlashEffect.trigger();
        }

        if (hitPoint != null) {
            ImpactVFX.addImpact(hitPoint, slashDirection, impactData);
        } else {
            Impact3DPanelManager.INSTANCE.spawnPanelFromImpact(impactData);
        }
    }

    /**
     * Adds a screen shake effect based on damage.
     * Called from DamageHandler via ClientVFXProxy.
     *
     * @param hitPoint Position of the hit
     * @param damage Amount of damage dealt
     * @param isCritical Whether it was a critical hit
     * @param isHeadshot Whether it hit the head
     */
    public static void addDamageShake(Vec3 hitPoint, float damage, boolean isCritical, boolean isHeadshot) {
        if (!ShakeManager.INSTANCE.isEnabled()) return;

        ShakeEffect shake = ShakeManager.createDamageShake(hitPoint, damage, isCritical, isHeadshot);
        ShakeManager.INSTANCE.addShake(shake);
    }
}
