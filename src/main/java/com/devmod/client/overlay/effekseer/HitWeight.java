package com.devmod.client.overlay.effekseer;

/**
 * Determines the "weight" of a hit for VFX purposes.
 *
 * <h2>Game Design Philosophy</h2>
 * <p>Different hits should FEEL different. A 2-damage poke should be snappy and quick.
 * A 20-damage critical should feel HEAVY with lingering impact. This creates:
 * <ul>
 *   <li><b>Readability</b> - Players instantly know hit strength from VFX</li>
 *   <li><b>Satisfaction</b> - Big hits feel rewarding, small hits feel responsive</li>
 *   <li><b>Clarity</b> - No confusion about damage dealt</li>
 * </ul>
 *
 * <h2>Weight Categories</h2>
 * <pre>
 * GLANCING (0-2 dmg):   Quick spark, minimal screen effect
 * LIGHT (3-5 dmg):      Snappy hit, brief flash
 * MEDIUM (6-10 dmg):    Solid impact, noticeable shake
 * HEAVY (11-15 dmg):    Powerful hit, screen punch
 * CRUSHING (16-25 dmg): Devastating, lingering effect
 * OBLITERATING (26+):   Maximum impact, screen crack effect
 * </pre>
 */
public enum HitWeight {
    /**
     * Barely scratched them (0-2 damage).
     * VFX: Quick spark, no shake, 100ms duration
     */
    GLANCING(0, 2, 0.3f, 0.0f, 100, "spark"),

    /**
     * Light hit (3-5 damage).
     * VFX: Snappy impact, micro shake, 150ms duration
     */
    LIGHT(3, 5, 0.5f, 0.1f, 150, "hit_light"),

    /**
     * Solid hit (6-10 damage).
     * VFX: Clear impact, light shake, 200ms duration
     */
    MEDIUM(6, 10, 0.75f, 0.3f, 200, "hit_medium"),

    /**
     * Powerful hit (11-15 damage).
     * VFX: Strong impact, screen punch, 300ms duration
     */
    HEAVY(11, 15, 1.0f, 0.6f, 300, "hit_heavy"),

    /**
     * Devastating hit (16-25 damage).
     * VFX: Major impact, strong shake, chromatic aberration, 400ms duration
     */
    CRUSHING(16, 25, 1.3f, 0.85f, 400, "hit_crushing"),

    /**
     * Maximum damage (26+ damage).
     * VFX: Screen crack, maximum shake, slow-mo feel, 500ms duration
     */
    OBLITERATING(26, Integer.MAX_VALUE, 1.6f, 1.0f, 500, "hit_obliterating");

    private final int minDamage;
    private final int maxDamage;
    private final float effectScale;
    private final float shakeIntensity;
    private final int effectDurationMs;
    private final String effectSuffix;

    HitWeight(int minDamage, int maxDamage, float effectScale, float shakeIntensity,
              int effectDurationMs, String effectSuffix) {
        this.minDamage = minDamage;
        this.maxDamage = maxDamage;
        this.effectScale = effectScale;
        this.shakeIntensity = shakeIntensity;
        this.effectDurationMs = effectDurationMs;
        this.effectSuffix = effectSuffix;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATIC FACTORY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the weight category for a damage value.
     */
    public static HitWeight fromDamage(float damage) {
        int dmg = (int) damage;
        for (HitWeight weight : values()) {
            if (dmg >= weight.minDamage && dmg <= weight.maxDamage) {
                return weight;
            }
        }
        return OBLITERATING; // Fallback for extreme damage
    }

    /**
     * Gets normalized weight (0-1) for smooth interpolation.
     * Useful for shader uniforms.
     */
    public static float getNormalizedWeight(float damage) {
        // Normalize: 0 damage = 0.0, 30+ damage = 1.0
        return Math.min(1.0f, Math.max(0.0f, damage / 30.0f));
    }

    /**
     * Calculates screen shake intensity for damage.
     * Combines weight category with precise damage value.
     */
    public static float calculateShakeIntensity(float damage, boolean isCritical, boolean isHeadshot) {
        HitWeight weight = fromDamage(damage);
        float base = weight.shakeIntensity;

        // Modifiers
        if (isCritical) base *= 1.3f;
        if (isHeadshot) base *= 1.5f;

        // Fine-tune based on exact damage within category
        float categoryProgress = getCategoryProgress(damage, weight);
        base *= (0.85f + categoryProgress * 0.3f); // 85% to 115% within category

        return Math.min(base, 1.5f); // Cap at 150%
    }

    /**
     * Gets progress through current weight category (0-1).
     */
    private static float getCategoryProgress(float damage, HitWeight weight) {
        if (weight.maxDamage == Integer.MAX_VALUE) return 1.0f;
        float range = weight.maxDamage - weight.minDamage;
        if (range <= 0) return 1.0f;
        return (damage - weight.minDamage) / range;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════════════

    public int getMinDamage() { return minDamage; }
    public int getMaxDamage() { return maxDamage; }
    public float getEffectScale() { return effectScale; }
    public float getShakeIntensity() { return shakeIntensity; }
    public int getEffectDurationMs() { return effectDurationMs; }
    public String getEffectSuffix() { return effectSuffix; }

    /**
     * Gets the effect path for this weight.
     * Example: "impact/hit_heavy" for HEAVY weight
     */
    public String getEffectPath() {
        return "impact/" + effectSuffix;
    }

    /**
     * Should this weight trigger chromatic aberration?
     */
    public boolean triggersChromaticAberration() {
        return this == CRUSHING || this == OBLITERATING;
    }

    /**
     * Should this weight trigger screen vignette?
     */
    public boolean triggersVignette() {
        return ordinal() >= HEAVY.ordinal();
    }

    /**
     * Should this weight trigger slow-motion feel?
     */
    public boolean triggersHitlag() {
        return this == OBLITERATING;
    }

    /**
     * Gets particle count multiplier for this weight.
     */
    public float getParticleMultiplier() {
        return switch (this) {
            case GLANCING -> 0.3f;
            case LIGHT -> 0.5f;
            case MEDIUM -> 0.8f;
            case HEAVY -> 1.0f;
            case CRUSHING -> 1.4f;
            case OBLITERATING -> 2.0f;
        };
    }

    /**
     * Gets color tint for this weight (affects glow color).
     * Heavier hits trend toward warmer colors.
     */
    public int getColorTint() {
        return switch (this) {
            case GLANCING -> com.devmod.client.ui.editor.core.DesignTokens.Combat.HitWeight.GLANCING;    // White
            case LIGHT -> com.devmod.client.ui.editor.core.DesignTokens.Combat.HitWeight.LIGHT;       // Warm white
            case MEDIUM -> com.devmod.client.ui.editor.core.DesignTokens.Combat.HitWeight.MEDIUM;      // Light yellow
            case HEAVY -> com.devmod.client.ui.editor.core.DesignTokens.Combat.HitWeight.HEAVY;       // Orange-yellow
            case CRUSHING -> com.devmod.client.ui.editor.core.DesignTokens.Combat.HitWeight.CRUSHING;    // Orange
            case OBLITERATING -> com.devmod.client.ui.editor.core.DesignTokens.Combat.HitWeight.OBLITERATING; // Red-orange
        };
    }
}
