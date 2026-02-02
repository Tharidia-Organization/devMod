package com.devmod.client.overlay.effekseer;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.phys.Vec3;

import com.devmod.client.overlay.ImpactData;
import com.devmod.client.overlay.effekseer.ComboTracker.ComboInfo;
import com.devmod.client.overlay.effekseer.ComboTracker.ComboTier;
import com.devmod.combat.HitHelper.BodyPart;

/**
 * Rich context object for VFX decision-making.
 *
 * <h2>Game Design: Effect Priority Hierarchy</h2>
 * <p>Effects are prioritized by emotional impact, not just damage:
 * <pre>
 * Priority 100: FINISHER   - Combo kill (5+ hits) = peak gameplay moment
 * Priority 90:  KILL       - Any kill = major achievement
 * Priority 80:  OBLITERATE - 26+ damage = rare massive hit
 * Priority 70:  HEADSHOT   - Precision reward
 * Priority 60:  CRUSHING   - 16-25 damage = powerful hit
 * Priority 50:  CRITICAL   - Body part multiplier > 1.5
 * Priority 40:  COMBO_TIER - Based on combo tier (GREAT+)
 * Priority 30:  BACKSTAB   - Tactical positioning
 * Priority 20:  HEAVY      - 11-15 damage
 * Priority 10:  MEDIUM     - 6-10 damage
 * Priority 5:   LIGHT      - 3-5 damage
 * Priority 1:   GLANCING   - 0-2 damage
 * </pre>
 *
 * <h2>Layering Rules</h2>
 * <ul>
 *   <li>Primary effect: Highest priority category</li>
 *   <li>Secondary effects: Non-conflicting modifiers (combo tier, weight)</li>
 *   <li>Never layer conflicting effects (e.g., KILL + normal HIT)</li>
 * </ul>
 */
public record EffectContext(
    @Nonnull Vec3 hitPoint,
    @Nullable Vec3 hitDirection,
    @Nullable Vec3 slashDirection,
    @Nonnull BodyPart bodyPart,
    float damage,
    @Nonnull HitWeight weight,
    float bodyPartMultiplier,
    boolean isHeadshot,
    boolean isCritical,
    boolean isKill,
    boolean isBackstab,
    boolean isBlocked,
    @Nonnull ComboInfo comboInfo,
    @Nonnull String attackSource,
    boolean isRanged
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates context from ImpactData with full combo integration.
     */
    public static EffectContext from(
        @Nonnull ImpactData data,
        @Nonnull Vec3 hitPoint,
        boolean isKill,
        @Nonnull ComboInfo comboInfo
    ) {
        float damage = data.hasActualDamage()
            ? data.getActualDamageDealt()
            : data.getBreakdown().getFinalDamage();

        HitWeight weight = HitWeight.fromDamage(damage);
        boolean isHeadshot = data.getBodyPart() == BodyPart.HEAD;
        boolean isCritical = data.getBodyPartMultiplier() > 1.5f;
        boolean isBlocked = data.getBlockedDamage() > 0;

        Vec3 hitDirection = data.getSlashDirection() != null
            ? data.getSlashDirection().normalize()
            : null;

        return new EffectContext(
            hitPoint,
            hitDirection,
            data.getSlashDirection(),
            data.getBodyPart(),
            damage,
            weight,
            data.getBodyPartMultiplier(),
            isHeadshot,
            isCritical,
            isKill,
            false, // backstab detection requires entity facing
            isBlocked,
            comboInfo,
            data.getAttackSource(),
            data.isRanged()
        );
    }

    /**
     * Creates a minimal context for direct effect calls.
     */
    public static EffectContext minimal(@Nonnull Vec3 hitPoint, float damage) {
        return new EffectContext(
            hitPoint,
            null,
            null,
            BodyPart.BODY,
            damage,
            HitWeight.fromDamage(damage),
            1.0f,
            false,
            false,
            false,
            false,
            false,
            ComboInfo.NONE,
            "unknown",
            false
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIORITY CALCULATION (Game Design Core)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the primary effect type based on priority hierarchy.
     */
    public EffectType getPrimaryEffectType() {
        // Priority 100: Combo finisher (5+ hit kill)
        if (isKill && comboInfo.count() >= 5) {
            return EffectType.FINISHER;
        }

        // Priority 90: Any kill
        if (isKill) {
            return EffectType.KILL;
        }

        // Priority 80: Obliterating damage (26+)
        if (weight == HitWeight.OBLITERATING) {
            return EffectType.OBLITERATE;
        }

        // Priority 70: Headshot (precision reward)
        if (isHeadshot) {
            return EffectType.HEADSHOT;
        }

        // Priority 60: Crushing damage (16-25)
        if (weight == HitWeight.CRUSHING) {
            return EffectType.CRUSHING;
        }

        // Priority 50: Critical zone hit
        if (isCritical) {
            return EffectType.CRITICAL;
        }

        // Priority 40: High combo tier (visual feedback)
        if (comboInfo.tier().ordinal() >= ComboTier.GREAT.ordinal()) {
            return EffectType.COMBO;
        }

        // Priority 30: Backstab
        if (isBackstab) {
            return EffectType.BACKSTAB;
        }

        // Priority 20-: Weight-based effects
        return switch (weight) {
            case HEAVY -> EffectType.HEAVY;
            case MEDIUM -> EffectType.MEDIUM;
            case LIGHT -> EffectType.LIGHT;
            case GLANCING -> EffectType.GLANCING;
            default -> EffectType.MEDIUM;
        };
    }

    /**
     * Gets secondary effects to layer (non-conflicting).
     */
    public List<EffectType> getSecondaryEffects() {
        List<EffectType> secondary = new ArrayList<>(3);
        EffectType primary = getPrimaryEffectType();

        // Add combo indicator if significant and not already primary
        if (comboInfo.tier().ordinal() >= ComboTier.NICE.ordinal() &&
            primary != EffectType.COMBO && primary != EffectType.FINISHER) {
            secondary.add(EffectType.COMBO_INDICATOR);
        }

        // Add weight flash if heavy+ and not damage-based primary
        if (weight.ordinal() >= HitWeight.HEAVY.ordinal() &&
            primary != EffectType.HEAVY && primary != EffectType.CRUSHING &&
            primary != EffectType.OBLITERATE) {
            secondary.add(EffectType.WEIGHT_FLASH);
        }

        // Add block effect if blocked (always shows)
        if (isBlocked) {
            secondary.add(EffectType.BLOCK);
        }

        return secondary;
    }

    /**
     * Gets effect priority score (for sorting/overriding).
     */
    public int getPriority() {
        return getPrimaryEffectType().getPriority();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTENSITY & SCALING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets overall intensity multiplier for effects.
     * Combines damage weight, combo, and context.
     */
    public float getIntensityMultiplier() {
        float base = weight.getEffectScale();

        // Combo bonus (up to +50% at high combos)
        float comboBonus = 1.0f + (comboInfo.multiplier() - 1.0f) * 0.5f;

        // Context bonuses
        float contextBonus = 1.0f;
        if (isHeadshot) contextBonus += 0.15f;
        if (isCritical) contextBonus += 0.1f;
        if (isKill) contextBonus += 0.25f;

        return Math.min(base * comboBonus * contextBonus, 2.5f);
    }

    /**
     * Gets screen shake intensity.
     */
    public float getShakeIntensity() {
        return HitWeight.calculateShakeIntensity(damage, isCritical, isHeadshot);
    }

    /**
     * Gets effect duration in milliseconds.
     */
    public int getEffectDurationMs() {
        int base = weight.getEffectDurationMs();

        // Kill effects linger longer
        if (isKill) base = (int) (base * 1.5f);

        // Combo finishers are dramatic
        if (isKill && comboInfo.count() >= 5) base = (int) (base * 2.0f);

        return base;
    }

    /**
     * Gets urgency factor from combo (0-1).
     * Used for pulsing/warning effects as combo timer runs out.
     */
    public float getComboUrgency() {
        return comboInfo.getUrgency();
    }

    /**
     * Should this hit trigger chromatic aberration?
     */
    public boolean shouldTriggerChromaticAberration() {
        return weight.triggersChromaticAberration() || isKill;
    }

    /**
     * Should this hit trigger vignette effect?
     */
    public boolean shouldTriggerVignette() {
        return weight.triggersVignette() || isKill;
    }

    /**
     * Should this hit trigger hitlag (freeze-frame feel)?
     */
    public boolean shouldTriggerHitlag() {
        return weight.triggersHitlag() || (isKill && comboInfo.count() >= 5);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EFFECT TYPE ENUM
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Effect types with priority and path.
     */
    public enum EffectType {
        // Damage-based (weight) - all use the generic hit effect with different scales
        GLANCING(1, "impact/hit"),
        LIGHT(5, "impact/hit"),
        MEDIUM(10, "impact/hit"),
        HEAVY(20, "impact/hit"),
        CRUSHING(60, "impact/hit"),
        OBLITERATE(80, "impact/hit"),

        // Context-based
        BACKSTAB(30, "impact/backstab"),
        CRITICAL(50, "impact/critical"),
        HEADSHOT(70, "impact/headshot"),
        COMBO(40, "impact/combo"),
        KILL(90, "impact/kill"),
        FINISHER(100, "impact/finisher"),

        // Secondary/modifier effects
        COMBO_INDICATOR(0, "impact/combo"),
        WEIGHT_FLASH(0, "impact/hit"),
        BLOCK(0, "impact/block");

        private final int priority;
        private final String path;

        EffectType(int priority, String path) {
            this.priority = priority;
            this.path = path;
        }

        public int getPriority() { return priority; }
        public String getPath() { return path; }

        /**
         * Is this a primary effect (vs modifier)?
         */
        public boolean isPrimary() {
            return priority > 0;
        }
    }
}
