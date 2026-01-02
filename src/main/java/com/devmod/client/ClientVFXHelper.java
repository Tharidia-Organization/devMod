package com.devmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.effects.ShakeEffect;
import com.devmod.client.effects.ShakeManager;
import com.devmod.client.overlay.HeadshotFlashVFX;
import com.devmod.client.overlay.Impact3DPanelManager;
import com.devmod.client.overlay.ImpactData;
import com.devmod.client.overlay.ImpactVFX;
import com.devmod.combat.HitHelper.BodyPart;
import com.devmod.damage.DamageBreakdown;

public final class ClientVFXHelper {

    private ClientVFXHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Spawns an evasion panel for arrow attacks on client.
     * Called from ArrowEvents via DistExecutor.
     */
    public static void spawnArrowEvasionPanel(Player shooter, LivingEntity target, Vec3 hitPos, Vec3 savedTargetPos) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            DamageBreakdown evasionBreakdown = new DamageBreakdown(
                shooter.getMainHandItem(),
                shooter,  // Attacker (shooter) for Pehkui scale
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
                player,  // Attacker (player) for Pehkui scale
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
     * Note: Panel spawning is now handled by ImpactHudController.
     */
    public static void addImpactVFX(Vec3 hitPoint, Vec3 slashDirection, ImpactData impactData) {
        // Trigger headshot flash effect if hit was to the head
        if (impactData != null && impactData.bodyPart == BodyPart.HEAD && isLocalAttacker(impactData)) {
            HeadshotFlashVFX.trigger();
        }

        // Only add VFX effects if we have a hit point
        if (hitPoint != null) {
            ImpactVFX.addImpact(hitPoint, slashDirection, impactData);
        }
        // Panel spawning is handled by ImpactHudController.onImpact()
    }

    /**
     * Ensures visuals tied to the attacker run only for the local player.
     */
    private static boolean isLocalAttacker(ImpactData impactData) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
            && impactData.attackerUUID != null
            && impactData.attackerUUID.equals(mc.player.getUUID());
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
