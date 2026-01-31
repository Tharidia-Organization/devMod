package com.devmod.client.overlay;

import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.neoforged.fml.loading.FMLEnvironment;

import com.devmod.client.ClientVFXProxy;
import com.devmod.combat.HitHelper;
import com.devmod.damage.DamageBreakdown;
import com.devmod.integration.ModIntegrationManager;

public final class ImpactHudService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImpactHudService.class);

    private ImpactHudService() {}

    public static ImpactData createAndStoreImpactData(
            UUID ownerId,
            LivingEntity victim,
            HitHelper.BodyPart part,
            float multiplier,
            DamageBreakdown breakdown,
            String attackSource,
            boolean isRanged,
            @Nullable Vec3 hitPoint,
            @Nullable Vec3 slashDirection) {
        ImpactData impactData = new ImpactData(
            ownerId,
            victim,
            part,
            multiplier,
            breakdown,
            attackSource,
            isRanged,
            hitPoint,
            slashDirection
        );
        ImpactData.store(impactData);
        ImpactHistory.record(impactData);

        // Route through controller for context-aware display
        ImpactHudController.INSTANCE.onImpact(impactData);

        return impactData;
    }

    public static ImpactData createAndStoreImpactData(
            LivingEntity attacker,
            LivingEntity victim,
            HitHelper.BodyPart part,
            float multiplier,
            DamageBreakdown breakdown,
            String attackSource,
            boolean isRanged,
            @Nullable Vec3 hitPoint,
            @Nullable Vec3 slashDirection) {
        // Auto-detect Epic Fight state from attacker
        String epicFightAnim = null;
        boolean epicFightActive = false;
        if (ModIntegrationManager.isEpicFightLoaded()) {
            epicFightActive = ModIntegrationManager.isEpicFightCombatActive(attacker);
            epicFightAnim = ModIntegrationManager.getEpicFightAnimationName(attacker);
        }

        ImpactData impactData = new ImpactData(
            attacker.getUUID(),
            victim,
            part,
            multiplier,
            breakdown,
            attackSource,
            isRanged,
            hitPoint,
            slashDirection,
            epicFightAnim,
            epicFightActive
        );
        ImpactData.store(impactData);
        ImpactHistory.record(impactData);

        // Route through controller for context-aware display
        ImpactHudController.INSTANCE.onImpact(impactData);

        return impactData;
    }

    public static void triggerImpactVfx(ImpactData impactData, @Nullable Vec3 hitPoint,
                                        @Nullable Vec3 vfxDirection, LivingEntity victim) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Impact VFX: dist.isClient={}, hitPoint={}, target={}",
                FMLEnvironment.dist.isClient(), hitPoint, victim.getName().getString());
        }
        if (hitPoint == null || vfxDirection == null) {
            return;
        }
        ClientVFXProxy.addImpactVFX(hitPoint, vfxDirection, impactData);
    }

    public static void triggerDamageShakeIfApplicable(LivingEntity victim, HitHelper.BodyPart part,
                                                      float multiplier, float newDamage,
                                                      @Nullable Vec3 hitPoint) {
        if (victim instanceof Player && hitPoint != null) {
            boolean isCritical = multiplier > 1.5f;
            boolean isHeadshot = part == HitHelper.BodyPart.HEAD;
            ClientVFXProxy.addDamageShake(hitPoint, newDamage, isCritical, isHeadshot);
        }
    }
}
