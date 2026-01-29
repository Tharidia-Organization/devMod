package com.devmod.endurance.combat.scoring;

import java.util.UUID;

import javax.annotation.Nullable;

import com.devmod.endurance.ComboSystem.StyleRank;
import com.devmod.endurance.config.EnduranceConfigManager;

/**
 * Resolves style rank from score using configuration thresholds.
 *
 * <p>This class extracts the rank resolution logic from {@link StyleRank#fromScore}
 * to decouple the enum from the configuration system and enable easier testing.</p>
 *
 * <p>Threshold lookup priority:</p>
 * <ol>
 *   <li>Quest-specific config (via questId)</li>
 *   <li>Default config values</li>
 *   <li>Fallback to enum default thresholds</li>
 * </ol>
 */
public final class StyleRankResolver {

    private final EnduranceConfigManager config;

    /**
     * Create resolver with default config manager.
     */
    public StyleRankResolver() {
        this(EnduranceConfigManager.INSTANCE);
    }

    /**
     * Create resolver with specific config manager (for testing).
     *
     * @param config The configuration manager to use
     */
    public StyleRankResolver(EnduranceConfigManager config) {
        this.config = config;
    }

    /**
     * Resolve style rank from score using config thresholds.
     *
     * @param styleScore The current style score
     * @param questId The quest ID for config lookup (may be null)
     * @return The resolved style rank
     */
    public StyleRank resolve(int styleScore, @Nullable UUID questId) {
        if (questId == null) {
            return resolveWithDefaults(styleScore);
        }

        // Check thresholds from highest to lowest
        if (styleScore >= config.getStyleRankSSSThreshold(questId)) return StyleRank.SSS;
        if (styleScore >= config.getStyleRankSSThreshold(questId)) return StyleRank.SS;
        if (styleScore >= config.getStyleRankSThreshold(questId)) return StyleRank.S;
        if (styleScore >= config.getStyleRankAThreshold(questId)) return StyleRank.A;
        if (styleScore >= config.getStyleRankBThreshold(questId)) return StyleRank.B;
        if (styleScore >= config.getStyleRankCThreshold(questId)) return StyleRank.C;
        return StyleRank.D;
    }

    /**
     * Resolve style rank using enum default thresholds.
     * Used when no quest context is available.
     *
     * @param styleScore The current style score
     * @return The resolved style rank
     */
    public StyleRank resolveWithDefaults(int styleScore) {
        StyleRank result = StyleRank.D;
        for (StyleRank rank : StyleRank.values()) {
            if (styleScore >= rank.getThreshold()) {
                result = rank;
            }
        }
        return result;
    }

    /**
     * Get threshold for a specific rank in quest context.
     *
     * @param rank The rank to get threshold for
     * @param questId The quest ID for config lookup (may be null)
     * @return The threshold score for this rank
     */
    public int getThreshold(StyleRank rank, @Nullable UUID questId) {
        if (questId == null) {
            return rank.getThreshold();
        }

        return switch (rank) {
            case D -> config.getStyleRankDThreshold(questId);
            case C -> config.getStyleRankCThreshold(questId);
            case B -> config.getStyleRankBThreshold(questId);
            case A -> config.getStyleRankAThreshold(questId);
            case S -> config.getStyleRankSThreshold(questId);
            case SS -> config.getStyleRankSSThreshold(questId);
            case SSS -> config.getStyleRankSSSThreshold(questId);
        };
    }

    /**
     * Calculate progress to next rank (0.0 - 1.0).
     *
     * @param styleScore Current style score
     * @param currentRank Current rank
     * @param questId Quest ID for config lookup
     * @return Progress percentage (0.0 = just reached current, 1.0 = about to reach next)
     */
    public float getProgressToNextRank(int styleScore, StyleRank currentRank, @Nullable UUID questId) {
        StyleRank nextRank = currentRank.getNext();
        if (currentRank == nextRank) {
            return 1.0f; // Already max rank
        }

        int currentThreshold = getThreshold(currentRank, questId);
        int nextThreshold = getThreshold(nextRank, questId);
        int range = nextThreshold - currentThreshold;

        if (range <= 0) {
            return 0f;
        }

        return Math.min(1.0f, (float) (styleScore - currentThreshold) / range);
    }
}
