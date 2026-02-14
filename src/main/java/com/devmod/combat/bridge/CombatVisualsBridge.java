package com.devmod.combat.bridge;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.devmod.combat.HitHelper;
import com.devmod.damage.DamageBreakdown;

/**
 * Bridge interface decoupling combat-package code from client-only classes.
 * <p>
 * On a dedicated server every method is a no-op (the default implementation).
 * On the client the real implementation is registered via {@link #setInstance}.
 * <p>
 * Thread-safe: the singleton is stored in an {@link AtomicReference}.
 */
public interface CombatVisualsBridge {

    // -------- singleton holder --------

    // Use Holder class to break initialization cycle (ErrorProne ClassInitializationDeadlock).
    final class Holder {
        private static final AtomicReference<CombatVisualsBridge> INSTANCE =
                new AtomicReference<>(new NoOp());
        private Holder() {}
    }

    static CombatVisualsBridge get() {
        return Holder.INSTANCE.get();
    }

    static void setInstance(CombatVisualsBridge impl) {
        Holder.INSTANCE.set(impl);
    }

    // -------- ImpactHudService methods --------

    /**
     * Creates and stores impact data for a melee/ranged hit (attacker variant).
     * Returns an opaque handle that can be passed to {@link #triggerImpactVfx}.
     */
    @Nullable
    default Object createAndStoreImpactData(LivingEntity attacker, LivingEntity victim,
            HitHelper.BodyPart part, float multiplier, DamageBreakdown breakdown,
            String attackSource, boolean isRanged,
            @Nullable Vec3 hitPoint, @Nullable Vec3 slashDirection) {
        return null;
    }

    /**
     * Creates and stores impact data for environmental / UUID-keyed hits.
     */
    @Nullable
    default Object createAndStoreImpactData(UUID ownerId, LivingEntity victim,
            HitHelper.BodyPart part, float multiplier, DamageBreakdown breakdown,
            String attackSource, boolean isRanged,
            @Nullable Vec3 hitPoint, @Nullable Vec3 slashDirection) {
        return null;
    }

    /** Triggers impact VFX at the given location. */
    default void triggerImpactVfx(Object impactData, @Nullable Vec3 hitPoint,
            @Nullable Vec3 vfxDirection, LivingEntity victim) {}

    /** Triggers screen shake when the player takes a hit. */
    default void triggerDamageShakeIfApplicable(LivingEntity victim, HitHelper.BodyPart part,
            float multiplier, float damage, @Nullable Vec3 hitPoint) {}

    // -------- ImpactData / DpsTracker methods --------

    /**
     * Gets the current (local player's) impact data handle, or null.
     */
    @Nullable
    default Object getImpactData() { return null; }

    /** Gets the target entity from an impact data handle. */
    @Nullable
    default LivingEntity getImpactTarget(Object impactData) { return null; }

    /** Gets the attacker UUID from an impact data handle. */
    @Nullable
    default UUID getImpactAttackerUUID(Object impactData) { return null; }

    /** Updates the actual damage values on an impact data handle. */
    default void setImpactActualDamage(Object impactData, float healthBefore,
            float healthAfter, float actualDamage) {}

    /** Updates the damage reduction breakdown on an impact data handle. */
    default void setImpactDamageReductionBreakdown(Object impactData, float armor,
            float enchantments, float mobEffects, float absorption, float blocked) {}

    /** Records a damage sample for DPS tracking. */
    default void recordDpsDamage(UUID attackerId, float damage) {}

    // -------- VFX methods --------

    /** Spawns a melee evasion panel (Enderman dodge visualization). */
    default void spawnMeleeEvasionPanel(Player player, LivingEntity target,
            Vec3 position, Vec3 lookDir) {}

    /** Records a shield impact for visual feedback. */
    default void recordShieldImpact(Vec3 impactPoint, float damage) {}

    // -------- Ranged weapon stats --------

    /** Holds ranged weapon override values resolved from client editor data. */
    record RangedStatsSnapshot(float baseDamage, float projectileSpeed,
            float critChance, float critDamage) {
        public static final RangedStatsSnapshot DEFAULTS =
                new RangedStatsSnapshot(0f, 1f, 0f, 1.5f);
    }

    /** Resolves ranged weapon stats from client-side editor data. */
    default RangedStatsSnapshot resolveRangedStats(ItemStack weapon) {
        return RangedStatsSnapshot.DEFAULTS;
    }

    // -------- no-op implementation --------

    final class NoOp implements CombatVisualsBridge {}
}
