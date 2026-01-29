package com.devmod.endurance.combat.core;

import java.util.UUID;

import javax.annotation.Nullable;

import com.devmod.endurance.ComboSystem.StyleRank;
import com.devmod.endurance.combat.scoring.StyleRankResolver;

/**
 * Tracks style score and rank progression.
 *
 * <p>Single Responsibility: Only manages style score, rank transitions,
 * decay logic, and rank history. Does not handle combo, stats, or
 * other concerns.</p>
 *
 * <p>Thread Safety: Not thread-safe. Should be accessed from a single
 * thread (server tick thread).</p>
 */
public final class StyleTracker {

    // Configuration
    private final StyleRankResolver resolver;
    private final UUID questId;
    private final long styleDecayIntervalMs;
    private final int styleDecayRate;

    // State
    private int styleScore = 0;
    private int totalStyleEarned = 0;
    private StyleRank currentRank = StyleRank.D;
    private StyleRank highestRank = StyleRank.D;
    private long lastStyleGainTime;

    // Rank change tracking
    private StyleRank previousRank = StyleRank.D;
    private boolean rankChangedThisTick = false;

    /**
     * Create tracker with default configuration.
     *
     * @param questId Quest ID for config lookup
     */
    public StyleTracker(@Nullable UUID questId) {
        this(questId, new StyleRankResolver(), 1000L, 50);
    }

    /**
     * Create tracker with custom configuration.
     *
     * @param questId Quest ID for config lookup
     * @param resolver Style rank resolver
     * @param styleDecayIntervalMs Interval between decay ticks
     * @param styleDecayRate Points lost per decay interval
     */
    public StyleTracker(@Nullable UUID questId, StyleRankResolver resolver,
                         long styleDecayIntervalMs, int styleDecayRate) {
        this.questId = questId;
        this.resolver = resolver;
        this.styleDecayIntervalMs = styleDecayIntervalMs;
        this.styleDecayRate = styleDecayRate;
        this.lastStyleGainTime = System.currentTimeMillis();
    }

    /**
     * Add style points and update rank.
     *
     * @param points Points to add
     * @return true if rank changed (promotion)
     */
    public boolean addStyle(int points) {
        if (points <= 0) return false;

        styleScore += points;
        totalStyleEarned += points;
        lastStyleGainTime = System.currentTimeMillis();

        return updateRank();
    }

    /**
     * Apply damage penalty to style score.
     *
     * @param damage Damage taken
     * @param reducedPenalty Whether to apply reduced penalty (grace period)
     * @return true if rank changed (demotion)
     */
    public boolean applyDamagePenalty(float damage, boolean reducedPenalty) {
        int penalty = (int) (damage * 10);
        if (reducedPenalty) {
            penalty = penalty / 4; // 75% reduction during grace
        }

        styleScore = Math.max(0, styleScore - penalty);
        return updateRank();
    }

    /**
     * Process style decay based on time since last style gain.
     *
     * @return Points decayed
     */
    public int processDecay() {
        long now = System.currentTimeMillis();
        long timeSinceGain = now - lastStyleGainTime;

        if (timeSinceGain <= styleDecayIntervalMs) {
            return 0;
        }

        int decayAmount = (int) (timeSinceGain / styleDecayIntervalMs * styleDecayRate);
        int previousScore = styleScore;
        styleScore = Math.max(0, styleScore - decayAmount);

        // Update last decay time to prevent over-decay
        lastStyleGainTime = now - (timeSinceGain % styleDecayIntervalMs);

        // Update rank after decay
        updateRank();

        return previousScore - styleScore;
    }

    /**
     * Add bonus points directly (for wave completion bonuses, etc.)
     *
     * @param points Points to add
     * @return true if rank changed
     */
    public boolean addBonusPoints(int points) {
        styleScore += points;
        totalStyleEarned += points;
        return updateRank();
    }

    /**
     * Update current rank based on score.
     *
     * @return true if rank changed
     */
    private boolean updateRank() {
        previousRank = currentRank;
        currentRank = resolver.resolve(styleScore, questId);

        if (currentRank.ordinal() > highestRank.ordinal()) {
            highestRank = currentRank;
        }

        rankChangedThisTick = currentRank != previousRank;
        return rankChangedThisTick;
    }

    /**
     * Check and clear rank change flag.
     *
     * @return true if rank changed since last check
     */
    public boolean consumeRankChange() {
        boolean changed = rankChangedThisTick;
        rankChangedThisTick = false;
        return changed;
    }

    /**
     * Check if rank was promoted (not demoted).
     */
    public boolean wasPromoted() {
        return currentRank.ordinal() > previousRank.ordinal();
    }

    /**
     * Check if rank was demoted.
     */
    public boolean wasDemoted() {
        return currentRank.ordinal() < previousRank.ordinal();
    }

    // === Getters ===

    public int getStyleScore() {
        return styleScore;
    }

    public int getTotalStyleEarned() {
        return totalStyleEarned;
    }

    public StyleRank getCurrentRank() {
        return currentRank;
    }

    public StyleRank getHighestRank() {
        return highestRank;
    }

    public StyleRank getPreviousRank() {
        return previousRank;
    }

    /**
     * Get multiplier for current rank.
     */
    public float getRankMultiplier() {
        return currentRank.getMultiplier();
    }

    /**
     * Get progress to next rank (0.0 - 1.0).
     */
    public float getProgressToNextRank() {
        return resolver.getProgressToNextRank(styleScore, currentRank, questId);
    }

    /**
     * Get threshold for next rank.
     */
    public int getNextRankThreshold() {
        StyleRank next = currentRank.getNext();
        if (next == currentRank) {
            return styleScore; // Already max rank
        }
        return resolver.getThreshold(next, questId);
    }

    /**
     * Get time since last style gain in milliseconds.
     */
    public long getTimeSinceLastGain() {
        return System.currentTimeMillis() - lastStyleGainTime;
    }

    /**
     * Check if currently decaying (no style gain recently).
     */
    public boolean isDecaying() {
        return getTimeSinceLastGain() > styleDecayIntervalMs;
    }
}
