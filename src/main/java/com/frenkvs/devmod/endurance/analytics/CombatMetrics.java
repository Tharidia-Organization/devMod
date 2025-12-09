package com.frenkvs.devmod.endurance.analytics;

import java.util.Map;

/**
 * Immutable snapshot of current combat metrics for analytics hooks.
 * Updated every second during active Endurance Quest.
 *
 * All values are computed at snapshot time - no live references.
 */
public record CombatMetrics(
        // === Time metrics ===
        /** Session duration in milliseconds */
        long sessionDurationMs,
        /** Current wave number */
        int currentWave,
        /** Time spent on current wave in milliseconds */
        long currentWaveDurationMs,

        // === Damage metrics ===
        /** Total damage dealt this session */
        float totalDamageDealt,
        /** Total damage taken this session */
        float totalDamageTaken,
        /** Damage dealt in current wave */
        float waveDamageDealt,
        /** Damage taken in current wave */
        float waveDamageTaken,
        /** Current DPS (damage per second) */
        float currentDPS,
        /** Rolling average DPS over last 10 seconds */
        float rollingDPS,

        // === Combat efficiency ===
        /** Total hits landed */
        int totalHits,
        /** Total hits taken */
        int hitsTaken,
        /** Critical hit count */
        int criticalHits,
        /** Critical hit rate (0.0-1.0) */
        float critRate,
        /** Average damage per hit */
        float avgDamagePerHit,

        // === Kill metrics ===
        /** Total kills this session */
        int totalKills,
        /** Kills in current wave */
        int waveKills,
        /** Mobs remaining in current wave */
        int mobsRemaining,
        /** Average time to kill (ms) */
        float avgKillTimeMs,

        // === Player state ===
        /** Player death count */
        int deaths,
        /** Current player health (0.0-1.0 of max) */
        float healthPercent,

        // === Combo system ===
        /** Current combo count */
        int currentCombo,
        /** Current combo rank (D, C, B, A, S, SS, SSS) */
        String comboRank,
        /** Highest combo achieved this session */
        int maxCombo,

        // === Weapon effectiveness ===
        /** Currently equipped weapon ID */
        String currentWeapon,
        /** Damage dealt with current weapon */
        float currentWeaponDamage,
        /** Most effective weapon this session */
        String bestWeapon,
        /** Damage by weapon ID */
        Map<String, Float> weaponDamageMap,

        // === Body part targeting ===
        /** Headshot count */
        int headshots,
        /** Headshot rate (0.0-1.0) */
        float headshotRate,
        /** Hit distribution by body part */
        Map<String, Integer> bodyPartHits
) {
    /**
     * Creates an empty metrics snapshot for initialization.
     */
    public static CombatMetrics empty() {
        return new CombatMetrics(
                0L, 1, 0L,
                0f, 0f, 0f, 0f, 0f, 0f,
                0, 0, 0, 0f, 0f,
                0, 0, 0, 0f,
                0, 1.0f,
                0, "D", 0,
                "minecraft:air", 0f, "minecraft:air", Map.of(),
                0, 0f, Map.of()
        );
    }

    /**
     * Checks if player is struggling based on metrics thresholds.
     */
    public boolean isStruggling() {
        // Multiple deaths, low DPS, or high damage taken relative to dealt
        return deaths >= 2 ||
               (sessionDurationMs > 30000 && currentDPS < 5.0f) ||
               (totalDamageTaken > totalDamageDealt * 0.5f && totalDamageDealt > 0);
    }

    /**
     * Checks if player is performing well.
     */
    public boolean isPerformingWell() {
        return deaths == 0 &&
               critRate > 0.2f &&
               headshotRate > 0.3f &&
               comboRank.startsWith("S");
    }

    /**
     * Gets a performance score (0-100) for ranking purposes.
     */
    public int getPerformanceScore() {
        int score = 50; // Base score

        // DPS contribution (up to +20)
        score += Math.min(20, (int)(currentDPS * 2));

        // Crit rate contribution (up to +15)
        score += (int)(critRate * 15);

        // Headshot rate contribution (up to +10)
        score += (int)(headshotRate * 10);

        // Combo rank contribution
        score += switch (comboRank) {
            case "SSS" -> 15;
            case "SS" -> 12;
            case "S" -> 10;
            case "A" -> 7;
            case "B" -> 5;
            case "C" -> 2;
            default -> 0;
        };

        // Death penalty
        score -= deaths * 10;

        // Damage taken penalty (if taking more than dealing)
        if (totalDamageDealt > 0 && totalDamageTaken > totalDamageDealt) {
            score -= 10;
        }

        return Math.max(0, Math.min(100, score));
    }
}
