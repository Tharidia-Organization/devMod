package com.devmod.endurance.combat.api;

import java.util.UUID;

import com.devmod.endurance.ComboSystem.ActionType;
import com.devmod.endurance.ComboSystem.StyleRank;

/**
 * Sealed hierarchy of combo system events.
 *
 * <p>Using sealed interfaces ensures type-safety and exhaustive pattern matching
 * when handling events. All events are immutable records.</p>
 *
 * <p>Example usage with pattern matching:</p>
 * <pre>{@code
 * switch (event) {
 *     case ComboIncreased ci -> handleComboIncrease(ci);
 *     case ComboBreak cb -> handleComboBreak(cb);
 *     case RankChanged rc -> handleRankChange(rc);
 *     case MilestoneReached mr -> handleMilestone(mr);
 *     case SpecialAction sa -> handleSpecialAction(sa);
 * }
 * }</pre>
 */
public sealed interface ComboEvent {

    /**
     * Player this event belongs to.
     */
    UUID playerId();

    /**
     * Quest this event occurred in.
     */
    UUID questId();

    /**
     * Timestamp when event occurred.
     */
    long timestamp();

    // === Event Types ===

    /**
     * Fired when combo count increases.
     */
    record ComboIncreased(
        UUID playerId,
        UUID questId,
        long timestamp,
        int previousCombo,
        int newCombo
    ) implements ComboEvent {}

    /**
     * Fired when combo breaks (timeout or damage taken).
     */
    record ComboBreak(
        UUID playerId,
        UUID questId,
        long timestamp,
        int lostCombo,
        BreakReason reason,
        float damageIfHit
    ) implements ComboEvent {

        public enum BreakReason {
            TIMEOUT,
            DAMAGE_TAKEN,
            SESSION_END
        }
    }

    /**
     * Fired when style rank changes (up or down).
     */
    record RankChanged(
        UUID playerId,
        UUID questId,
        long timestamp,
        StyleRank oldRank,
        StyleRank newRank,
        int currentScore,
        boolean isPromotion
    ) implements ComboEvent {

        public static RankChanged promotion(UUID playerId, UUID questId,
                                             StyleRank oldRank, StyleRank newRank, int score) {
            return new RankChanged(playerId, questId, System.currentTimeMillis(),
                oldRank, newRank, score, true);
        }

        public static RankChanged demotion(UUID playerId, UUID questId,
                                            StyleRank oldRank, StyleRank newRank, int score) {
            return new RankChanged(playerId, questId, System.currentTimeMillis(),
                oldRank, newRank, score, false);
        }
    }

    /**
     * Fired when a combo milestone is reached (5, 10, 25, 50, 100 hits).
     */
    record MilestoneReached(
        UUID playerId,
        UUID questId,
        long timestamp,
        ActionType milestone,
        int comboCount,
        int styleEarned
    ) implements ComboEvent {}

    /**
     * Fired when a special action is performed (high style value actions).
     */
    record SpecialAction(
        UUID playerId,
        UUID questId,
        long timestamp,
        ActionType action,
        int basePoints,
        int styleEarned,
        int currentCombo
    ) implements ComboEvent {}

    /**
     * Fired when flow state changes.
     */
    record FlowStateChanged(
        UUID playerId,
        UUID questId,
        long timestamp,
        com.devmod.endurance.FlowStateTracker.FlowState oldState,
        com.devmod.endurance.FlowStateTracker.FlowState newState
    ) implements ComboEvent {}

    /**
     * Fired when session starts.
     */
    record SessionStarted(
        UUID playerId,
        UUID questId,
        long timestamp
    ) implements ComboEvent {}

    /**
     * Fired when session ends.
     */
    record SessionEnded(
        UUID playerId,
        UUID questId,
        long timestamp,
        int finalScore,
        int maxCombo,
        StyleRank highestRank
    ) implements ComboEvent {}
}
