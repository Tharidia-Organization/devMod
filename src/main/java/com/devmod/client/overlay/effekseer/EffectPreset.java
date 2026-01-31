package com.devmod.client.overlay.effekseer;

/**
 * Preset configurations for impact VFX intensity and style.
 *
 * <p>Each preset defines:
 * <ul>
 *   <li>Base scale multiplier for all effects</li>
 *   <li>Whether to enable secondary/layered effects</li>
 *   <li>Particle density multiplier</li>
 *   <li>Whether to enable screen shake</li>
 *   <li>Effect duration multiplier</li>
 * </ul>
 */
public enum EffectPreset {
    /**
     * Minimal effects - subtle feedback only.
     * Good for competitive play where visibility matters.
     */
    MINIMAL(
        "Minimal",
        0.5f,   // scale
        false,  // secondary effects
        0.3f,   // particle density
        false,  // screen shake
        0.7f,   // duration
        1       // max simultaneous
    ),

    /**
     * Clean effects - balanced visibility and feedback.
     * Default for most players.
     */
    CLEAN(
        "Clean",
        0.8f,
        false,
        0.6f,
        false,
        0.9f,
        2
    ),

    /**
     * Standard effects - full VFX experience.
     * Recommended for immersive gameplay.
     */
    STANDARD(
        "Standard",
        1.0f,
        true,
        1.0f,
        true,
        1.0f,
        3
    ),

    /**
     * Intense effects - enhanced feedback for action-focused play.
     * More particles, bigger effects.
     */
    INTENSE(
        "Intense",
        1.3f,
        true,
        1.5f,
        true,
        1.1f,
        4
    ),

    /**
     * Epic effects - maximum visual impact.
     * For players who want spectacular combat.
     */
    EPIC(
        "Epic",
        1.6f,
        true,
        2.0f,
        true,
        1.3f,
        5
    ),

    /**
     * Cinematic effects - dramatic, slower effects.
     * Great for content creation and screenshots.
     */
    CINEMATIC(
        "Cinematic",
        1.8f,
        true,
        1.2f,
        true,
        1.8f,
        6
    );

    private final String displayName;
    private final float scaleMultiplier;
    private final boolean enableSecondaryEffects;
    private final float particleDensity;
    private final boolean enableScreenShake;
    private final float durationMultiplier;
    private final int maxSimultaneousEffects;

    EffectPreset(String displayName, float scaleMultiplier, boolean enableSecondaryEffects,
                 float particleDensity, boolean enableScreenShake, float durationMultiplier,
                 int maxSimultaneousEffects) {
        this.displayName = displayName;
        this.scaleMultiplier = scaleMultiplier;
        this.enableSecondaryEffects = enableSecondaryEffects;
        this.particleDensity = particleDensity;
        this.enableScreenShake = enableScreenShake;
        this.durationMultiplier = durationMultiplier;
        this.maxSimultaneousEffects = maxSimultaneousEffects;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getScaleMultiplier() {
        return scaleMultiplier;
    }

    public boolean enableSecondaryEffects() {
        return enableSecondaryEffects;
    }

    public float getParticleDensity() {
        return particleDensity;
    }

    public boolean enableScreenShake() {
        return enableScreenShake;
    }

    public float getDurationMultiplier() {
        return durationMultiplier;
    }

    public int getMaxSimultaneousEffects() {
        return maxSimultaneousEffects;
    }

    /**
     * Calculates the final scale for an effect.
     *
     * @param baseScale The effect's base scale
     * @param intensityMultiplier The context intensity (from damage, combo, etc.)
     * @return Final scale to apply
     */
    public float calculateFinalScale(float baseScale, float intensityMultiplier) {
        return baseScale * scaleMultiplier * (0.7f + intensityMultiplier * 0.3f);
    }

    /**
     * Calculates particle count based on preset density.
     *
     * @param baseCount Base particle count from effect
     * @return Adjusted particle count
     */
    public int calculateParticleCount(int baseCount) {
        return Math.max(1, (int) (baseCount * particleDensity));
    }

    /**
     * Gets preset from ordinal, with fallback to STANDARD.
     */
    public static EffectPreset fromOrdinal(int ordinal) {
        EffectPreset[] values = values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return STANDARD;
    }

    /**
     * Gets preset from name, with fallback to STANDARD.
     */
    public static EffectPreset fromName(String name) {
        for (EffectPreset preset : values()) {
            if (preset.name().equalsIgnoreCase(name) ||
                preset.displayName.equalsIgnoreCase(name)) {
                return preset;
            }
        }
        return STANDARD;
    }
}
