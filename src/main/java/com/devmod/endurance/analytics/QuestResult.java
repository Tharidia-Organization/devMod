package com.devmod.endurance.analytics;

import java.util.List;
import java.util.Map;
import java.util.UUID;
public record QuestResult(
        // === Quest identification ===
        /** Unique quest attempt ID */
        UUID questId,
        /** Player UUID */
        UUID playerId,
        /** Quest definition ID */
        String questDefinitionId,
        /** Mob type for this quest */
        String mobType,

        // === Outcome ===
        /** Whether quest was completed successfully */
        boolean success,
        /** Final wave reached */
        int finalWave,
        /** Total waves in quest */
        int totalWaves,
        /** Reason for failure (null if success) */
        String failureReason,

        // === Time metrics ===
        /** Total quest duration in milliseconds */
        long totalDurationMs,
        /** Time per wave */
        List<Long> waveTimesMs,

        // === Combat totals ===
        /** Total damage dealt */
        float totalDamageDealt,
        /** Total damage taken */
        float totalDamageTaken,
        /** Total kills */
        int totalKills,
        /** Total deaths */
        int deaths,
        /** Total hits landed */
        int totalHits,
        /** Total critical hits */
        int criticalHits,

        // === Performance metrics ===
        /** Overall DPS */
        float overallDPS,
        /** Critical hit rate */
        float critRate,
        /** Headshot rate */
        float headshotRate,
        /** Max combo achieved */
        int maxCombo,
        /** Max combo rank achieved */
        String maxComboRank,

        // === Weapon analysis ===
        /** Most used weapon */
        String primaryWeapon,
        /** Most effective weapon (highest DPS) */
        String mostEffectiveWeapon,
        /** Damage by weapon */
        Map<String, Float> weaponDamageMap,

        // === Per-wave summaries ===
        /** Summary of each wave */
        List<WaveSummary> waveSummaries,

        // === Rewards earned ===
        /** XP earned from this quest */
        int xpEarned,
        /** Coins earned */
        int coinsEarned,
        /** Achievements unlocked */
        List<String> achievementsUnlocked
) {
    /**
     * Gets completion percentage (waves completed / total).
     */
    public float getCompletionPercent() {
        if (totalWaves <= 0) return 0f;
        return (float) finalWave / totalWaves * 100f;
    }

    /**
     * Calculates efficiency score based on multiple factors.
     * @return Score 0-100
     */
    public int getEfficiencyScore() {
        int score = 0;

        // Completion bonus (up to 50 points)
        score += (int)(getCompletionPercent() / 2);

        // DPS bonus (up to 20 points)
        score += Math.min(20, (int)(overallDPS * 2));

        // Survival bonus (up to 15 points, -5 per death)
        score += Math.max(0, 15 - (deaths * 5));

        // Crit rate bonus (up to 10 points)
        score += (int)(critRate * 10);

        // Combo bonus (up to 5 points)
        score += switch (maxComboRank) {
            case "SSS" -> 5;
            case "SS" -> 4;
            case "S" -> 3;
            case "A" -> 2;
            default -> 1;
        };

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Gets letter grade for this quest attempt.
     */
    public String getGrade() {
        int score = getEfficiencyScore();

        if (success && deaths == 0 && score >= 90) return "S+";
        if (success && score >= 85) return "S";
        if (success && score >= 75) return "A";
        if (success && score >= 60) return "B";
        if (success) return "C";

        // Failed quests
        if (score >= 50) return "D";
        return "F";
    }

    /**
     * Checks if this was a personal best.
     * Caller must compare with stored records.
     */
    public boolean isPotentialPersonalBest() {
        return success && deaths <= 1 && critRate >= 0.25f;
    }

    /**
     * Gets primary weakness for feedback.
     */
    public String getPrimaryWeakness() {
        if (deaths >= 3) return "SURVIVABILITY";
        if (headshotRate < 0.15f) return "ACCURACY";
        if (critRate < 0.15f) return "CRITICAL_HITS";
        if (overallDPS < 5f) return "DAMAGE_OUTPUT";
        if (maxCombo < 10) return "COMBO_MAINTENANCE";
        return "NONE";
    }

    /**
     * Gets primary strength for positive feedback.
     */
    public String getPrimaryStrength() {
        if (deaths == 0 && success) return "SURVIVABILITY";
        if (headshotRate >= 0.4f) return "PRECISION";
        if (critRate >= 0.3f) return "CRITICAL_MASTERY";
        if (maxCombo >= 50) return "COMBO_MASTER";
        if (overallDPS >= 15f) return "HIGH_DPS";
        return "PERSEVERANCE";
    }

    /**
     * Creates a failure result.
     */
    public static QuestResult failure(UUID questId, UUID playerId, String questDefId,
            String mobType, int waveReached, int totalWaves, String reason,
            long duration, float damageDealt, float damageTaken, int kills, int deaths) {
        return new QuestResult(
                questId, playerId, questDefId, mobType,
                false, waveReached, totalWaves, reason,
                duration, List.of(),
                damageDealt, damageTaken, kills, deaths, 0, 0,
                damageDealt / Math.max(1, duration / 1000f), 0f, 0f, 0, "D",
                "unknown", "unknown", Map.of(),
                List.of(),
                0, 0, List.of()
        );
    }
}
