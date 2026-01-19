package com.devmod.foundry.quality;

/**
 * Calculates material quality based on crafting conditions.
 */
public final class QualityCalculator {

    private QualityCalculator() {
    }

    /**
     * Calculates the quality of a melted material.
     *
     * @param purity          Material purity (0.0 - 1.0)
     * @param tempAccuracy    How close to optimal temp (0.0 = far, 1.0 = perfect)
     * @param hadIncidents    Whether any incidents occurred during processing
     * @return The resulting quality tier
     */
    public static MaterialQuality calculateMeltingQuality(
            float purity,
            float tempAccuracy,
            boolean hadIncidents
    ) {
        if (hadIncidents) {
            // Incidents cap quality at STANDARD
            if (purity < 0.6f) return MaterialQuality.CRUDE;
            return MaterialQuality.STANDARD;
        }

        float score = (purity * 0.7f) + (tempAccuracy * 0.3f);

        if (score >= 0.98f && purity >= 0.98f) {
            return MaterialQuality.MASTERWORK;
        } else if (score >= 0.90f && purity >= 0.90f) {
            return MaterialQuality.PRISTINE;
        } else if (score >= 0.80f && purity >= 0.80f) {
            return MaterialQuality.REFINED;
        } else if (score >= 0.60f) {
            return MaterialQuality.STANDARD;
        } else {
            return MaterialQuality.CRUDE;
        }
    }

    /**
     * Calculates the quality of a cast part.
     *
     * @param fluidQuality    Quality of the molten fluid used
     * @param patternQuality  Quality bonus from the pattern (0.8 - 1.3)
     * @param coolingAccuracy How close to optimal cooling time (0.0 - 1.0)
     * @return The resulting quality tier
     */
    public static MaterialQuality calculateCastingQuality(
            MaterialQuality fluidQuality,
            float patternQuality,
            float coolingAccuracy
    ) {
        // Start with fluid quality as base
        float baseScore = fluidQuality.getTier() / 4f;

        // Pattern quality affects outcome
        float patternBonus = (patternQuality - 0.8f) / 0.5f * 0.2f; // 0-0.2 range

        // Cooling accuracy matters
        float coolingBonus = coolingAccuracy * 0.1f; // 0-0.1 range

        float totalScore = baseScore + patternBonus + coolingBonus;

        // Cannot exceed source fluid quality by more than 1 tier
        int maxTier = Math.min(fluidQuality.getTier() + 1, MaterialQuality.MASTERWORK.getTier());

        if (totalScore >= 0.95f) {
            return MaterialQuality.fromTier(Math.min(4, maxTier));
        } else if (totalScore >= 0.80f) {
            return MaterialQuality.fromTier(Math.min(3, maxTier));
        } else if (totalScore >= 0.65f) {
            return MaterialQuality.fromTier(Math.min(2, maxTier));
        } else if (totalScore >= 0.45f) {
            return MaterialQuality.fromTier(Math.min(1, maxTier));
        } else {
            return MaterialQuality.CRUDE;
        }
    }

    /**
     * Calculates the quality of an assembled tool.
     *
     * @param partQualities   Array of part qualities
     * @param assemblySkill   Player's assembly skill (0.0 - 1.0, future feature)
     * @return The resulting tool quality
     */
    public static MaterialQuality calculateToolQuality(
            MaterialQuality[] partQualities,
            float assemblySkill
    ) {
        if (partQualities == null || partQualities.length == 0) {
            return MaterialQuality.CRUDE;
        }

        // Tool quality is primarily determined by the lowest quality part
        int minTier = Integer.MAX_VALUE;
        int totalTier = 0;

        for (MaterialQuality quality : partQualities) {
            minTier = Math.min(minTier, quality.getTier());
            totalTier += quality.getTier();
        }

        // Average tier with penalty for low parts
        float avgTier = (float) totalTier / partQualities.length;
        float effectiveTier = (minTier * 0.6f) + (avgTier * 0.4f);

        // Assembly skill can boost by up to 0.5 tier
        effectiveTier += assemblySkill * 0.5f;

        int finalTier = Math.round(effectiveTier);
        finalTier = Math.max(0, Math.min(4, finalTier));

        return MaterialQuality.fromTier(finalTier);
    }

    /**
     * Calculates temperature accuracy for quality purposes.
     *
     * @param currentTemp  The actual temperature
     * @param optimalLow   Lower bound of optimal range
     * @param optimalHigh  Upper bound of optimal range
     * @return Accuracy from 0.0 (way off) to 1.0 (perfect)
     */
    public static float calculateTempAccuracy(float currentTemp, float optimalLow, float optimalHigh) {
        if (currentTemp >= optimalLow && currentTemp <= optimalHigh) {
            return 1.0f; // Perfect - within optimal range
        }

        float optimalRange = (optimalHigh - optimalLow) / 2f;

        float distance;
        if (currentTemp < optimalLow) {
            distance = optimalLow - currentTemp;
        } else {
            distance = currentTemp - optimalHigh;
        }

        // Accuracy drops off with distance from optimal
        // At 2x optimal range away, accuracy is 0
        float maxDistance = optimalRange * 4f;
        return Math.max(0f, 1f - (distance / maxDistance));
    }
}
