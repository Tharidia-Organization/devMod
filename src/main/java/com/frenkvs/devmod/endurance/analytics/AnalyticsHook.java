package com.frenkvs.devmod.endurance.analytics;

/**
 * Interface for receiving real-time analytics updates during Endurance Quests.
 *
 * Implementations can use these callbacks to:
 * - Display gamified prompts/suggestions
 * - Trigger adaptive difficulty adjustments
 * - Generate personalized feedback
 * - Track performance patterns
 *
 * Thread-safety: Callbacks are invoked on the server tick thread.
 * Implementations should not perform blocking operations.
 */
public interface AnalyticsHook {

    /**
     * Called periodically (every second) with updated combat metrics.
     * Use for real-time displays and trend analysis.
     *
     * @param metrics Current combat metrics snapshot (immutable)
     */
    void onCombatMetricsUpdate(CombatMetrics metrics);

    /**
     * Called when transitioning between waves (during pause state).
     * Ideal timing for suggestions and feedback.
     *
     * @param waveNumber The wave that just completed
     * @param waveSummary Summary of the completed wave
     * @param nextWaveNumber The upcoming wave number
     */
    void onWaveTransition(int waveNumber, WaveSummary waveSummary, int nextWaveNumber);

    /**
     * Called when player performance indicates struggling.
     * Triggers: low DPS, repeated deaths, high damage taken.
     *
     * @param indicator The type of struggle detected
     * @param severity 0.0-1.0 scale of severity
     * @param context Additional context about the struggle
     */
    void onStruggleDetected(StruggleIndicator indicator, float severity, String context);

    /**
     * Called when an opportunity for positive feedback is detected.
     * Triggers: high combo, boss near death, perfect dodge streak.
     *
     * @param opportunity The type of opportunity
     * @param context Additional context
     */
    void onOpportunityDetected(OpportunityIndicator opportunity, String context);

    /**
     * Called when quest ends (success or failure).
     * Use for final analytics and reward calculation.
     *
     * @param result The quest completion result
     */
    void onQuestComplete(QuestResult result);

    /**
     * Types of struggle indicators for targeted feedback.
     */
    enum StruggleIndicator {
        /** DPS is significantly below expected for current wave */
        LOW_DPS,
        /** Player has died multiple times in current wave */
        REPEATED_DEATHS,
        /** Player is taking excessive damage */
        HIGH_DAMAGE_TAKEN,
        /** Player is not hitting enemies efficiently */
        LOW_ACCURACY,
        /** Player is not using effective weapons */
        SUBOPTIMAL_WEAPON,
        /** Player is not targeting weak points */
        NO_HEADSHOTS,
        /** Combo keeps breaking */
        COMBO_BREAKING
    }

    /**
     * Types of opportunities for positive reinforcement.
     */
    enum OpportunityIndicator {
        /** Player achieved high combo rank (S, SS, SSS) */
        HIGH_COMBO,
        /** Boss is at low health */
        BOSS_NEAR_DEATH,
        /** Player avoided damage for extended period */
        PERFECT_DODGE_STREAK,
        /** Player is dealing high burst damage */
        BURST_DAMAGE,
        /** Player eliminated wave quickly */
        FAST_WAVE_CLEAR,
        /** Player achieved critical hit streak */
        CRIT_STREAK,
        /** Player using optimal weapon for enemy type */
        OPTIMAL_WEAPON
    }
}
