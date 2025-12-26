package com.devmod.client;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;

import com.devmod.client.overlay.ImpactData;

public final class ClientVFXProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientVFXProxy.class);

    // Cached reflection methods (initialized lazily on client only)
    private static boolean initialized = false;
    private static Method addImpactVFXMethod;
    private static Method spawnMeleeEvasionMethod;
    private static Method spawnArrowEvasionMethod;
    private static Method addDamageShakeMethod;

    private ClientVFXProxy() {}

    /**
     * Initialize reflection methods on client side only.
     */
    @OnlyIn(Dist.CLIENT)
    private static void initClient() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> helperClass = Class.forName("com.devmod.client.ClientVFXHelper");

            addImpactVFXMethod = helperClass.getMethod("addImpactVFX",
                    Vec3.class, Vec3.class, ImpactData.class);

            spawnMeleeEvasionMethod = helperClass.getMethod("spawnMeleeEvasionPanel",
                    Player.class, LivingEntity.class, Vec3.class, Vec3.class);

            spawnArrowEvasionMethod = helperClass.getMethod("spawnArrowEvasionPanel",
                    Player.class, LivingEntity.class, Vec3.class, Vec3.class);

            addDamageShakeMethod = helperClass.getMethod("addDamageShake",
                    Vec3.class, float.class, boolean.class, boolean.class);

            LOGGER.debug("[ClientVFXProxy] Initialized client VFX methods via reflection");
        } catch (Exception e) {
            LOGGER.error("[ClientVFXProxy] Failed to initialize client VFX methods", e);
        }
    }

    /**
     * Adds impact VFX at the specified location.
     * Safe to call from any side - only executes on client.
     */
    public static void addImpactVFX(Vec3 hitPoint, Vec3 slashDirection, ImpactData impactData) {
        if (!FMLEnvironment.dist.isClient()) return;

        try {
            initClient();
            if (addImpactVFXMethod != null) {
                addImpactVFXMethod.invoke(null, hitPoint, slashDirection, impactData);
            }
        } catch (Exception e) {
            LOGGER.error("[ClientVFXProxy] Error calling addImpactVFX", e);
        }
    }

    /**
     * Spawns an evasion panel for melee attacks.
     * Safe to call from any side - only executes on client.
     */
    public static void spawnMeleeEvasionPanel(Player player, LivingEntity target,
            Vec3 targetPos, Vec3 lookDir) {
        if (!FMLEnvironment.dist.isClient()) return;

        try {
            initClient();
            if (spawnMeleeEvasionMethod != null) {
                spawnMeleeEvasionMethod.invoke(null, player, target, targetPos, lookDir);
            }
        } catch (Exception e) {
            LOGGER.error("[ClientVFXProxy] Error calling spawnMeleeEvasionPanel", e);
        }
    }

    /**
     * Spawns an evasion panel for arrow attacks.
     * Safe to call from any side - only executes on client.
     */
    public static void spawnArrowEvasionPanel(Player player, LivingEntity target,
            Vec3 hitPos, Vec3 savedTargetPos) {
        if (!FMLEnvironment.dist.isClient()) return;

        try {
            initClient();
            if (spawnArrowEvasionMethod != null) {
                spawnArrowEvasionMethod.invoke(null, player, target, hitPos, savedTargetPos);
            }
        } catch (Exception e) {
            LOGGER.error("[ClientVFXProxy] Error calling spawnArrowEvasionPanel", e);
        }
    }

    /**
     * Adds a screen shake effect based on damage.
     * Safe to call from any side - only executes on client.
     *
     * @param hitPoint Position of the hit
     * @param damage Amount of damage dealt
     * @param isCritical Whether it was a critical hit
     * @param isHeadshot Whether it hit the head
     */
    public static void addDamageShake(Vec3 hitPoint, float damage, boolean isCritical, boolean isHeadshot) {
        if (!FMLEnvironment.dist.isClient()) return;

        try {
            initClient();
            if (addDamageShakeMethod != null) {
                addDamageShakeMethod.invoke(null, hitPoint, damage, isCritical, isHeadshot);
            }
        } catch (Exception e) {
            LOGGER.error("[ClientVFXProxy] Error calling addDamageShake", e);
        }
    }
}
