package com.devmod.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.effects.ShakeEffect;
import com.devmod.client.effects.ShakeManager;
import com.devmod.client.overlay.HeadshotFlashVFX;
import com.devmod.client.overlay.Impact3DPanelManager;
import com.devmod.client.overlay.ImpactData;
import com.devmod.client.overlay.ImpactEffekseerVFX;
import com.devmod.client.overlay.ImpactVFX;
import com.devmod.combat.HitHelper.BodyPart;
import com.devmod.config.Config;
import com.devmod.damage.DamageBreakdown;

public final class ClientVFXHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientVFXHelper.class);

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
                "\u00A7c\u00A7lEVADED! (Arrow)",
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
                "\u00A7c\u00A7lEVADED!",
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
        LOGGER.debug("[ClientVFXHelper] addImpactVFX called: hitPoint={}, thread={}",
            hitPoint, Thread.currentThread().getName());

        // Only add VFX effects if we have a hit point
        if (hitPoint == null) {
            LOGGER.debug("[ClientVFXHelper] hitPoint is null, skipping VFX");
            return;
        }

        // Schedule VFX on render thread (required for Effekseer OpenGL calls)
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            LOGGER.debug("[ClientVFXHelper] Executing on render thread: effekseerEnabled={}",
                isEffekseerEnabled());

            // Trigger headshot audio feedback if hit was to the head
            if (impactData != null && impactData.getBodyPart() == BodyPart.HEAD && isLocalAttacker(impactData)) {
                HeadshotFlashVFX.trigger();
            }

            if (isEffekseerEnabled()) {
                // Use Effekseer particle system
                ImpactEffekseerVFX.playImpactEffect(hitPoint, impactData);
            } else {
                // Use legacy CPU/GPU VFX
                ImpactVFX.addImpact(hitPoint, slashDirection, impactData);
            }
        });
        // Panel spawning is handled by ImpactHudController.onImpact()
    }

    /**
     * Checks if Effekseer VFX is enabled in config.
     */
    private static boolean isEffekseerEnabled() {
        try {
            return Config.IMPACT_VFX_USE_EFFEKSEER.get();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ensures visuals tied to the attacker run only for the local player.
     */
    private static boolean isLocalAttacker(ImpactData impactData) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        return player != null
            && impactData.getAttackerUUID() != null
            && impactData.getAttackerUUID().equals(player.getUUID());
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
