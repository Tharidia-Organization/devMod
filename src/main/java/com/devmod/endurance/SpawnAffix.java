package com.devmod.endurance;

/**
 * Per-spawn affix applied by the WaveDirector to shape wave beats.
 * These are mechanical modifiers (HP/DMG/Speed) applied on top of baseline scaling.
 */
public enum SpawnAffix {
    BASE(1.0f, 1.0f, 1.0f, false),
    RUSH(0.85f, 1.05f, 1.25f, false),
    BRUTE(1.5f, 1.1f, 0.85f, false),
    SNIPER(0.9f, 1.25f, 1.0f, false),
    ELITE(1.0f, 1.0f, 1.0f, true),
    OBJECTIVE_ELITE(1.1f, 1.1f, 1.05f, true);

    public final float hpMultiplier;
    public final float damageMultiplier;
    public final float speedMultiplier;
    public final boolean elite;

    SpawnAffix(float hpMultiplier, float damageMultiplier, float speedMultiplier, boolean elite) {
        this.hpMultiplier = hpMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.elite = elite;
    }
}
