package com.devmod.client.combat;

import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.ClientVFXProxy;
import com.devmod.client.overlay.ImpactData;
import com.devmod.client.overlay.ImpactDpsTracker;
import com.devmod.client.overlay.ImpactHudService;
import com.devmod.client.rendering.shield.EnergyShieldRenderer;
import com.devmod.client.ui.editor.RangedWeaponModule;
import com.devmod.combat.HitHelper;
import com.devmod.combat.bridge.CombatVisualsBridge;
import com.devmod.damage.DamageBreakdown;

/**
 * Client-side implementation of {@link CombatVisualsBridge}.
 * Makes direct calls to client-only classes that were previously invoked via reflection.
 */
public final class ClientCombatVisuals implements CombatVisualsBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCombatVisuals.class);

    // -------- ImpactHudService --------

    @Override
    @Nullable
    public Object createAndStoreImpactData(LivingEntity attacker, LivingEntity victim,
            HitHelper.BodyPart part, float multiplier, DamageBreakdown breakdown,
            String attackSource, boolean isRanged,
            @Nullable Vec3 hitPoint, @Nullable Vec3 slashDirection) {
        try {
            return ImpactHudService.createAndStoreImpactData(
                    attacker, victim, part, multiplier, breakdown,
                    attackSource, isRanged, hitPoint, slashDirection);
        } catch (Exception e) {
            LOGGER.debug("Failed to create impact data: {}", e.getMessage());
            return null;
        }
    }

    @Override
    @Nullable
    public Object createAndStoreImpactData(UUID ownerId, LivingEntity victim,
            HitHelper.BodyPart part, float multiplier, DamageBreakdown breakdown,
            String attackSource, boolean isRanged,
            @Nullable Vec3 hitPoint, @Nullable Vec3 slashDirection) {
        try {
            return ImpactHudService.createAndStoreImpactData(
                    ownerId, victim, part, multiplier, breakdown,
                    attackSource, isRanged, hitPoint, slashDirection);
        } catch (Exception e) {
            LOGGER.debug("Failed to create environmental impact data: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void triggerImpactVfx(Object impactData, @Nullable Vec3 hitPoint,
            @Nullable Vec3 vfxDirection, LivingEntity victim) {
        if (!(impactData instanceof ImpactData data)) return;
        try {
            ImpactHudService.triggerImpactVfx(data, hitPoint, vfxDirection, victim);
        } catch (Exception e) {
            LOGGER.debug("Failed to trigger impact VFX: {}", e.getMessage());
        }
    }

    @Override
    public void triggerDamageShakeIfApplicable(LivingEntity victim, HitHelper.BodyPart part,
            float multiplier, float damage, @Nullable Vec3 hitPoint) {
        try {
            ImpactHudService.triggerDamageShakeIfApplicable(victim, part, multiplier, damage, hitPoint);
        } catch (Exception e) {
            LOGGER.debug("Failed to trigger damage shake: {}", e.getMessage());
        }
    }

    // -------- ImpactData / DpsTracker --------

    @Override
    @Nullable
    public Object getImpactData() {
        try {
            return ImpactData.get();
        } catch (Exception e) {
            LOGGER.debug("Failed to get impact data: {}", e.getMessage());
            return null;
        }
    }

    @Override
    @Nullable
    public LivingEntity getImpactTarget(Object impactData) {
        if (!(impactData instanceof ImpactData data)) return null;
        return data.getTarget();
    }

    @Override
    @Nullable
    public UUID getImpactAttackerUUID(Object impactData) {
        if (!(impactData instanceof ImpactData data)) return null;
        return data.getAttackerUUID();
    }

    @Override
    public void setImpactActualDamage(Object impactData, float healthBefore,
            float healthAfter, float actualDamage) {
        if (!(impactData instanceof ImpactData data)) return;
        data.setActualDamage(healthBefore, healthAfter, actualDamage);
    }

    @Override
    public void setImpactDamageReductionBreakdown(Object impactData, float armor,
            float enchantments, float mobEffects, float absorption, float blocked) {
        if (!(impactData instanceof ImpactData data)) return;
        data.setDamageReductionBreakdown(armor, enchantments, mobEffects, absorption, blocked);
    }

    @Override
    public void recordDpsDamage(UUID attackerId, float damage) {
        try {
            ImpactDpsTracker.recordDamage(attackerId, damage);
        } catch (Exception e) {
            LOGGER.debug("Failed to record DPS damage: {}", e.getMessage());
        }
    }

    // -------- VFX --------

    @Override
    public void spawnMeleeEvasionPanel(Player player, LivingEntity target,
            Vec3 position, Vec3 lookDir) {
        try {
            ClientVFXProxy.spawnMeleeEvasionPanel(player, target, position, lookDir);
        } catch (Exception e) {
            LOGGER.debug("Failed to spawn evasion VFX: {}", e.getMessage());
        }
    }

    @Override
    public void recordShieldImpact(LivingEntity shieldOwner, Vec3 impactPoint, float damage) {
        if (shieldOwner == null) return;
        try {
            EnergyShieldRenderer.recordImpact(shieldOwner.getId(), impactPoint, damage);
        } catch (Exception e) {
            LOGGER.debug("Failed to record shield impact: {}", e.getMessage());
        }
    }

    // -------- Ranged Stats --------

    @Override
    public RangedStatsSnapshot resolveRangedStats(ItemStack weapon) {
        try {
            RangedWeaponModule.RangedStats stats = RangedWeaponModule.getStats(weapon);
            if (stats == null) {
                return RangedStatsSnapshot.DEFAULTS;
            }
            return new RangedStatsSnapshot(
                    stats.baseDamage,
                    stats.projectileSpeed,
                    stats.critChance,
                    stats.critDamage
            );
        } catch (Exception e) {
            LOGGER.debug("Failed to resolve ranged stats: {}", e.getMessage());
            return RangedStatsSnapshot.DEFAULTS;
        }
    }
}
