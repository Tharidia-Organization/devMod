package com.devmod.endurance.tide;

import com.devmod.endurance.EnduranceColors;

/**
 * Tide Level - Global threat severity stages.
 *
 * The Tide is a server-wide threat system that rises with player failures
 * and falls with successes, creating dynamic difficulty and community events.
 */
public enum TideLevel {

    /**
     * Normal gameplay, no threat modifiers.
     * Tide: 0-99
     */
    CALM(0, 100, EnduranceColors.Tide.CALM, "Calm", 0f, false, false, 0),

    /**
     * Tension building, mobs slightly stronger.
     * Tide: 100-299
     */
    RISING(100, 300, EnduranceColors.Tide.RISING, "Rising", 0.10f, false, false, 0),

    /**
     * Dangerous conditions, curse mutators appear.
     * Tide: 300-499
     */
    HIGH(300, 500, EnduranceColors.Tide.HIGH, "High", 0.20f, true, false, 0),

    /**
     * Storm conditions, boss every 3 waves.
     * Tide: 500-799
     */
    STORM(500, 800, EnduranceColors.Tide.STORM, "Storm", 0.30f, true, true, 3),

    /**
     * Apocalypse - Tide Boss event imminent.
     * Tide: 800-1000
     */
    APOCALYPSE(800, 1000, EnduranceColors.Tide.APOCALYPSE, "Apocalypse", 0.50f, true, true, 2);

    private final int minTide;
    private final int maxTide;
    private final int color;
    private final String displayName;
    private final float statBonus;         // Mob stat multiplier
    private final boolean curseMutators;   // Enable curse mutators
    private final boolean forcedBosses;    // Force boss spawns
    private final int bossWaveInterval;    // Spawn boss every N waves (0 = disabled)

    TideLevel(int minTide, int maxTide, int color, String displayName,
              float statBonus, boolean curseMutators, boolean forcedBosses, int bossWaveInterval) {
        this.minTide = minTide;
        this.maxTide = maxTide;
        this.color = color;
        this.displayName = displayName;
        this.statBonus = statBonus;
        this.curseMutators = curseMutators;
        this.forcedBosses = forcedBosses;
        this.bossWaveInterval = bossWaveInterval;
    }

    public int getMinTide() { return minTide; }
    public int getMaxTide() { return maxTide; }
    public int getColor() { return color; }
    public String getDisplayName() { return displayName; }
    public float getStatBonus() { return statBonus; }
    public boolean isCurseMutators() { return curseMutators; }
    public boolean isForcedBosses() { return forcedBosses; }
    public int getBossWaveInterval() { return bossWaveInterval; }

    /**
     * Get the TideLevel for a given tide value.
     */
    public static TideLevel fromTide(int tide) {
        for (TideLevel level : values()) {
            if (tide >= level.minTide && tide < level.maxTide) {
                return level;
            }
        }
        // At max tide
        return APOCALYPSE;
    }

    /**
     * Get progress through this level (0.0 to 1.0).
     */
    public float getProgress(int tide) {
        if (tide < minTide) return 0f;
        if (tide >= maxTide) return 1f;
        return (float) (tide - minTide) / (maxTide - minTide);
    }

    /**
     * Check if at the Tide Boss threshold.
     */
    public static boolean isTideBossThreshold(int tide) {
        return tide >= 1000;
    }

    /**
     * Get the next level, or null if at max.
     */
    public TideLevel getNext() {
        int nextOrdinal = ordinal() + 1;
        if (nextOrdinal >= values().length) {
            return null;
        }
        return values()[nextOrdinal];
    }

    /**
     * Get display string with color formatting.
     */
    public String getFormattedName() {
        return "\u00A7" + getColorCode() + displayName + "\u00A7r";
    }

    private char getColorCode() {
        return switch (this) {
            case CALM -> 'a';      // Green
            case RISING -> 'e';    // Yellow
            case HIGH -> '6';      // Gold
            case STORM -> 'c';     // Red
            case APOCALYPSE -> '4'; // Dark Red
        };
    }
}
