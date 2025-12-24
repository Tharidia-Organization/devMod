package com.devmod.endurance.analytics;

import java.util.Map;

/**
 * Summary of a completed wave for analytics and feedback.
 * Provided during wave transitions for targeted suggestions.
 */
public record WaveSummary(
        /** Wave number that completed */
        int waveNumber,
        /** Time to complete wave in milliseconds */
        long completionTimeMs,
        /** Total damage dealt during wave */
        float damageDealt,
        /** Total damage taken during wave */
        float damageTaken,
        /** Mobs killed in wave */
        int kills,
        /** Player deaths during wave */
        int deaths,
        /** Critical hits landed */
        int criticalHits,
        /** Total hits landed */
        int hitsLanded,
        /** Best combo achieved during wave */
        int maxCombo,
        /** Best combo rank achieved */
        String maxComboRank,
        /** Most used weapon during wave */
        String primaryWeapon,
        /** Body part hit distribution */
        Map<String, Integer> bodyPartHits
) {
    /**
     * Calculates DPS for this wave.
     */
    public float getDPS() {
        if (completionTimeMs <= 0) return 0f;
        return damageDealt / (completionTimeMs / 1000f);
    }

    /**
     * Calculates critical hit rate for this wave.
     */
    public float getCritRate() {
        if (hitsLanded <= 0) return 0f;
        return (float) criticalHits / hitsLanded;
    }

    /**
     * Calculates headshot rate for this wave.
     */
    public float getHeadshotRate() {
        if (hitsLanded <= 0) return 0f;
        int headshots = bodyPartHits.getOrDefault("HEAD", 0);
        return (float) headshots / hitsLanded;
    }

    /**
     * Checks if this was a clean wave (no deaths).
     */
    public boolean isCleanWave() {
        return deaths == 0;
    }

    /**
     * Checks if player struggled on this wave.
     */
    public boolean wasStruggling() {
        return deaths >= 2 || (damageTaken > damageDealt * 0.7f && damageDealt > 0);
    }

    /**
     * Gets performance grade for this wave.
     * @return Letter grade S/A/B/C/D/F
     */
    public String getGrade() {
        float dps = getDPS();
        float critRate = getCritRate();
        boolean clean = isCleanWave();

        int score = 0;

        // DPS scoring (up to 40 points)
        if (dps >= 20) score += 40;
        else if (dps >= 15) score += 35;
        else if (dps >= 10) score += 25;
        else if (dps >= 5) score += 15;
        else score += 5;

        // Clean wave bonus (20 points)
        if (clean) score += 20;
        else score -= deaths * 10;

        // Crit rate bonus (up to 20 points)
        score += (int)(critRate * 20);

        // Combo bonus (up to 20 points)
        score += switch (maxComboRank) {
            case "SSS" -> 20;
            case "SS" -> 17;
            case "S" -> 15;
            case "A" -> 12;
            case "B" -> 8;
            case "C" -> 4;
            default -> 0;
        };

        // Convert to grade
        if (score >= 90) return "S";
        if (score >= 80) return "A";
        if (score >= 65) return "B";
        if (score >= 50) return "C";
        if (score >= 35) return "D";
        return "F";
    }

    /**
     * Creates an empty summary for initialization.
     */
    public static WaveSummary empty(int waveNumber) {
        return new WaveSummary(
                waveNumber, 0L, 0f, 0f, 0, 0, 0, 0, 0, "D", "minecraft:air", Map.of()
        );
    }
}
