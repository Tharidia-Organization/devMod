package com.devmod.client.overlay;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.overlay.effekseer.ComboTracker;
import com.devmod.client.overlay.effekseer.EffectContext;
import com.devmod.client.overlay.effekseer.EffectOrchestrator;
import com.devmod.client.overlay.effekseer.EffectPreset;
import com.devmod.client.vfx.effekseer.api.ParticleEmitter;
import com.devmod.config.Config;

/**
 * Effekseer-based impact visual effects system.
 *
 * <p>This is the main entry point for GPU-accelerated particle effects using Effekseer.
 * It integrates with the combo tracking system and effect orchestrator to provide
 * rich, context-aware combat feedback.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Context-Aware Effects</b> - Automatically selects appropriate effects based on
 *       hit type (headshot, critical, kill, combo, etc.)</li>
 *   <li><b>Combo System</b> - Tracks consecutive hits and increases effect intensity</li>
 *   <li><b>Effect Layering</b> - Multiple effects can play simultaneously for big moments</li>
 *   <li><b>Preset System</b> - Configurable intensity from Minimal to Epic</li>
 *   <li><b>Performance Scaling</b> - Automatically adjusts based on active effect count</li>
 * </ul>
 *
 * <h2>Effect Files</h2>
 * Effects are loaded from: {@code assets/devmod/effeks/impact/}
 * <ul>
 *   <li>{@code hit.efkefc} - Generic hit feedback</li>
 *   <li>{@code critical.efkefc} - Flash/spark for critical zone hits</li>
 *   <li>{@code headshot.efkefc} - Gold particles for headshots</li>
 *   <li>{@code kill.efkefc} - Soul/spirit rising for kills</li>
 *   <li>{@code combo.efkefc} - Chain hit effect</li>
 *   <li>{@code backstab.efkefc} - Sneak attack from behind</li>
 *   <li>{@code block.efkefc} - Blocked/absorbed damage</li>
 *   <li>{@code finisher.efkefc} - Epic combo finisher kill</li>
 *   <li>{@code milestone.efkefc} - Combo milestone celebration</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * // Simple usage - automatic context detection
 * ImpactEffekseerVFX.playImpactEffect(hitPoint, impactData);
 *
 * // With kill detection
 * ImpactEffekseerVFX.playImpactEffect(hitPoint, impactData, true);
 *
 * // Direct effect methods
 * ImpactEffekseerVFX.playHeadshot(hitPoint);
 * ImpactEffekseerVFX.playKill(hitPoint, 2.5f);
 * </pre>
 *
 * @see EffectOrchestrator
 * @see ComboTracker
 * @see EffectPreset
 */
@OnlyIn(Dist.CLIENT)
public final class ImpactEffekseerVFX {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImpactEffekseerVFX.class);

    private ImpactEffekseerVFX() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Plays the appropriate Effekseer effect(s) for an impact.
     *
     * <p>This is the primary method for triggering impact VFX. It:
     * <ol>
     *   <li>Records the hit in the combo tracker</li>
     *   <li>Builds a rich effect context from impact data</li>
     *   <li>Delegates to the orchestrator for effect selection and playback</li>
     * </ol>
     *
     * @param hitPoint World position of the impact
     * @param data Impact data for context (nullable - will use defaults)
     * @return The primary spawned emitter (may be dummy if disabled/missing)
     */
    public static ParticleEmitter playImpactEffect(@Nullable Vec3 hitPoint, @Nullable ImpactData data) {
        return playImpactEffect(hitPoint, data, false);
    }

    /**
     * Plays impact effect with kill detection.
     *
     * @param hitPoint World position of the impact
     * @param data Impact data for context
     * @param isKill Whether this hit killed the target
     * @return The primary spawned emitter
     */
    public static ParticleEmitter playImpactEffect(
        @Nullable Vec3 hitPoint,
        @Nullable ImpactData data,
        boolean isKill
    ) {
        LOGGER.debug("[ImpactEffekseerVFX] playImpactEffect called: hitPoint={}, data={}, isKill={}, thread={}",
            hitPoint, data != null ? data.getTarget() : "null", isKill, Thread.currentThread().getName());

        if (hitPoint == null) {
            LOGGER.debug("[ImpactEffekseerVFX] hitPoint is null, returning dummy");
            return ParticleEmitter.dummy(ParticleEmitter.Type.WORLD);
        }

        if (!isEnabled()) {
            LOGGER.debug("[ImpactEffekseerVFX] Effekseer disabled in config, returning dummy");
            return ParticleEmitter.dummy(ParticleEmitter.Type.WORLD);
        }

        // Get combo info
        ComboTracker.ComboInfo comboInfo = ComboTracker.ComboInfo.NONE;

        if (data != null && data.getAttackerUUID() != null) {
            LivingEntity target = data.getTarget();
            if (target != null) {
                float damage = data.hasActualDamage()
                    ? data.getActualDamageDealt()
                    : data.getBreakdown().getFinalDamage();
                comboInfo = isKill
                    ? ComboTracker.recordKill(data.getAttackerUUID(), target, damage)
                    : ComboTracker.recordHit(data.getAttackerUUID(), target, damage);
            }
        }

        // Build effect context
        EffectContext context;
        if (data != null) {
            context = EffectContext.from(data, hitPoint, isKill, comboInfo);
        } else {
            com.devmod.client.overlay.effekseer.HitWeight weight =
                com.devmod.client.overlay.effekseer.HitWeight.fromDamage(5.0f);
            context = new EffectContext(
                hitPoint,
                null,
                null,
                com.devmod.combat.HitHelper.BodyPart.BODY,
                5.0f,
                weight,
                1.0f,
                false,
                false,
                isKill,
                false,
                false,
                comboInfo,
                "unknown",
                false
            );
        }

        // Play through orchestrator
        List<ParticleEmitter> emitters = EffectOrchestrator.play(context);
        LOGGER.debug("[ImpactEffekseerVFX] Orchestrator returned {} emitters, first exists={}",
            emitters.size(), !emitters.isEmpty() && emitters.get(0).exists());
        return emitters.isEmpty()
            ? ParticleEmitter.dummy(ParticleEmitter.Type.WORLD)
            : emitters.get(0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Plays a headshot effect at the specified position.
     *
     * @param hitPoint World position
     * @return The spawned emitter
     */
    public static ParticleEmitter playHeadshot(@Nonnull Vec3 hitPoint) {
        return playWithContext(hitPoint, builder -> builder
            .bodyPart(com.devmod.combat.HitHelper.BodyPart.HEAD)
            .isHeadshot(true)
            .bodyPartMultiplier(2.0f)
            .damage(15.0f)
        );
    }

    /**
     * Plays a critical hit effect at the specified position.
     *
     * @param hitPoint World position
     * @return The spawned emitter
     */
    public static ParticleEmitter playCritical(@Nonnull Vec3 hitPoint) {
        return playWithContext(hitPoint, builder -> builder
            .isCritical(true)
            .bodyPartMultiplier(1.8f)
            .damage(12.0f)
        );
    }

    /**
     * Plays a kill effect at the specified position.
     *
     * @param hitPoint World position
     * @param damageScale Scale factor (higher = bigger effect, clamped to 3.0)
     * @return The spawned emitter
     */
    public static ParticleEmitter playKill(@Nonnull Vec3 hitPoint, float damageScale) {
        float clampedScale = Math.min(damageScale, 3.0f);
        return playWithContext(hitPoint, builder -> builder
            .isKill(true)
            .damage(clampedScale * 20.0f)
            .damageScale(clampedScale)
        );
    }

    /**
     * Plays a generic hit effect at the specified position.
     *
     * @param hitPoint World position
     * @return The spawned emitter
     */
    public static ParticleEmitter playHit(@Nonnull Vec3 hitPoint) {
        return playWithContext(hitPoint, builder -> builder
            .damage(5.0f)
            .damageScale(0.25f)
        );
    }

    /**
     * Plays a combo effect at the specified position.
     *
     * @param hitPoint World position
     * @param comboCount Current combo count
     * @return The spawned emitter
     */
    public static ParticleEmitter playCombo(@Nonnull Vec3 hitPoint, int comboCount) {
        return playWithContext(hitPoint, builder -> builder
            .comboCount(comboCount)
            .comboMultiplier(ComboTracker.getComboMultiplier(comboCount))
            .damage(8.0f * ComboTracker.getComboMultiplier(comboCount))
        );
    }

    /**
     * Plays a backstab effect at the specified position.
     *
     * @param hitPoint World position
     * @param direction Attack direction
     * @return The spawned emitter
     */
    public static ParticleEmitter playBackstab(@Nonnull Vec3 hitPoint, @Nullable Vec3 direction) {
        return playWithContext(hitPoint, builder -> builder
            .isBackstab(true)
            .hitDirection(direction)
            .damage(18.0f)
            .bodyPartMultiplier(1.5f)
        );
    }

    /**
     * Plays a blocked attack effect.
     *
     * @param hitPoint World position
     * @param blockedAmount Amount of damage blocked
     * @return The spawned emitter
     */
    public static ParticleEmitter playBlock(@Nonnull Vec3 hitPoint, float blockedAmount) {
        return playWithContext(hitPoint, builder -> builder
            .isBlocked(true)
            .damage(blockedAmount)
            .damageScale(Math.min(blockedAmount / 10.0f, 1.0f))
        );
    }

    /**
     * Plays an epic finisher effect (kill with high combo).
     *
     * @param hitPoint World position
     * @param comboCount Final combo count
     * @return The spawned emitter
     */
    public static ParticleEmitter playFinisher(@Nonnull Vec3 hitPoint, int comboCount) {
        return playWithContext(hitPoint, builder -> builder
            .isKill(true)
            .comboCount(Math.max(comboCount, 5))
            .comboMultiplier(ComboTracker.getComboMultiplier(comboCount))
            .damage(30.0f)
            .damageScale(1.5f)
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Plays an effect with a builder pattern for context.
     */
    private static ParticleEmitter playWithContext(
        @Nonnull Vec3 hitPoint,
        java.util.function.Consumer<ContextBuilder> configurator
    ) {
        if (!isEnabled()) {
            return ParticleEmitter.dummy(ParticleEmitter.Type.WORLD);
        }

        ContextBuilder builder = new ContextBuilder(hitPoint);
        configurator.accept(builder);

        List<ParticleEmitter> emitters = EffectOrchestrator.play(builder.build());
        return emitters.isEmpty()
            ? ParticleEmitter.dummy(ParticleEmitter.Type.WORLD)
            : emitters.get(0);
    }

    /**
     * Checks if Effekseer effects are enabled.
     */
    private static boolean isEnabled() {
        try {
            return Config.IMPACT_VFX_USE_EFFEKSEER.get();
        } catch (Exception e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Stops all active effects. Call on world change or disconnect.
     */
    public static void stopAll() {
        EffectOrchestrator.stopAll();
        ComboTracker.clearAll();
    }

    /**
     * Clears combo for a specific player.
     */
    public static void clearCombo(java.util.UUID playerUUID) {
        ComboTracker.clearForPlayer(playerUUID);
    }

    /**
     * Gets current active effect count for debugging.
     */
    public static int getActiveEffectCount() {
        return EffectOrchestrator.getActiveCount();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTEXT BUILDER
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Builder for creating EffectContext with fluent API.
     * All builder methods are provided for API completeness even if not currently used.
     */
    @SuppressWarnings("unused") // Builder methods provided for API completeness
    private static final class ContextBuilder {
        private final Vec3 hitPoint;
        @Nullable private Vec3 hitDirection = null;
        @Nullable private Vec3 slashDirection = null;
        private com.devmod.combat.HitHelper.BodyPart bodyPart = com.devmod.combat.HitHelper.BodyPart.BODY;
        private float damage = 5.0f;
        private float damageScale = 0.25f;
        private com.devmod.client.overlay.effekseer.HitWeight weight =
            com.devmod.client.overlay.effekseer.HitWeight.fromDamage(5.0f);
        private float bodyPartMultiplier = 1.0f;
        private boolean isHeadshot = false;
        private boolean isCritical = false;
        private boolean isKill = false;
        private boolean isBackstab = false;
        private boolean isBlocked = false;
        private ComboTracker.ComboInfo comboInfo = ComboTracker.ComboInfo.NONE;
        private String attackSource = "melee";
        private boolean isRanged = false;

        ContextBuilder(Vec3 hitPoint) {
            this.hitPoint = hitPoint;
        }

        ContextBuilder hitDirection(@Nullable Vec3 dir) { this.hitDirection = dir; return this; }
        ContextBuilder slashDirection(@Nullable Vec3 dir) { this.slashDirection = dir; return this; }
        ContextBuilder bodyPart(com.devmod.combat.HitHelper.BodyPart part) { this.bodyPart = part; return this; }
        ContextBuilder damage(float dmg) {
            this.damage = dmg;
            this.damageScale = Math.min(dmg / 20.0f, 1.0f);
            this.weight = com.devmod.client.overlay.effekseer.HitWeight.fromDamage(dmg);
            return this;
        }
        ContextBuilder damageScale(float scale) { this.damageScale = scale; return this; }
        ContextBuilder bodyPartMultiplier(float mult) { this.bodyPartMultiplier = mult; return this; }
        ContextBuilder isHeadshot(boolean v) { this.isHeadshot = v; return this; }
        ContextBuilder isCritical(boolean v) { this.isCritical = v; return this; }
        ContextBuilder isKill(boolean v) { this.isKill = v; return this; }
        ContextBuilder isBackstab(boolean v) { this.isBackstab = v; return this; }
        ContextBuilder isBlocked(boolean v) { this.isBlocked = v; return this; }
        ContextBuilder comboCount(int count) { this.comboInfo = buildComboInfo(count, comboInfo.multiplier()); return this; }
        ContextBuilder comboMultiplier(float mult) { this.comboInfo = buildComboInfo(comboInfo.count(), mult); return this; }
        ContextBuilder comboInfo(ComboTracker.ComboInfo info) { this.comboInfo = info; return this; }
        ContextBuilder attackSource(String src) { this.attackSource = src; return this; }
        ContextBuilder isRanged(boolean v) { this.isRanged = v; return this; }

        EffectContext build() {
            float effectiveDamage = Math.max(0.1f, Math.max(damage, damageScale * 20.0f));
            weight = com.devmod.client.overlay.effekseer.HitWeight.fromDamage(effectiveDamage);
            return new EffectContext(
                hitPoint,
                hitDirection,
                slashDirection,
                bodyPart,
                effectiveDamage,
                weight,
                bodyPartMultiplier,
                isHeadshot,
                isCritical,
                isKill,
                isBackstab,
                isBlocked,
                comboInfo,
                attackSource,
                isRanged
            );
        }

        private ComboTracker.ComboInfo buildComboInfo(int count, float mult) {
            if (count <= 0) {
                return ComboTracker.ComboInfo.NONE;
            }
            return new ComboTracker.ComboInfo(
                count,
                mult,
                ComboTracker.getComboTier(count),
                0,
                0,
                damage,
                true
            );
        }
    }
}
