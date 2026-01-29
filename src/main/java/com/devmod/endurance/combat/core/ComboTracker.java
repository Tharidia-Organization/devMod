package com.devmod.endurance.combat.core;

import java.util.HashSet;
import java.util.Set;

import com.devmod.endurance.ComboSystem.ActionType;

/**
 * Tracks combo count and timeout state.
 *
 * <p>Single Responsibility: Only manages combo counter, max combo,
 * timeout detection, and variety tracking. Does not handle style,
 * stats, or other concerns.</p>
 *
 * <p>Thread Safety: Not thread-safe. Should be accessed from a single
 * thread (server tick thread).</p>
 */
public final class ComboTracker {

    // Configuration
    private final long comboTimeoutMs;

    // State
    private int currentCombo = 0;
    private int maxCombo = 0;
    private long lastHitTime = 0;

    // Variety tracking (rewards different attack types)
    private final Set<ActionType> actionsUsedThisCombo = new HashSet<>();
    private int varietyBonus = 0;

    /**
     * Create tracker with default timeout (3 seconds).
     */
    public ComboTracker() {
        this(3000L);
    }

    /**
     * Create tracker with custom timeout.
     *
     * @param comboTimeoutMs Combo timeout in milliseconds
     */
    public ComboTracker(long comboTimeoutMs) {
        this.comboTimeoutMs = comboTimeoutMs;
        this.lastHitTime = System.currentTimeMillis();
    }

    /**
     * Increment combo counter and track action variety.
     *
     * @param action The action type performed
     * @return true if this was a new action type (variety bonus applies)
     */
    public boolean incrementCombo(ActionType action) {
        long now = System.currentTimeMillis();

        // Check for timeout before incrementing
        if (isTimedOut(now)) {
            breakCombo();
        }

        currentCombo++;
        if (currentCombo > maxCombo) {
            maxCombo = currentCombo;
        }
        lastHitTime = now;

        // Track variety
        boolean isNewAction = actionsUsedThisCombo.add(action);
        if (isNewAction) {
            varietyBonus += 25;
        }

        return isNewAction;
    }

    /**
     * Check if combo has timed out.
     *
     * @return true if combo has timed out
     */
    public boolean isTimedOut() {
        return isTimedOut(System.currentTimeMillis());
    }

    /**
     * Check if combo has timed out at given time.
     *
     * @param currentTimeMs Current time in milliseconds
     * @return true if combo has timed out
     */
    public boolean isTimedOut(long currentTimeMs) {
        return currentCombo > 0 && (currentTimeMs - lastHitTime) > comboTimeoutMs;
    }

    /**
     * Break the current combo, resetting counter and variety.
     *
     * @return The combo count that was lost (0 if no combo active)
     */
    public int breakCombo() {
        int lostCombo = currentCombo;
        currentCombo = 0;
        actionsUsedThisCombo.clear();
        varietyBonus = 0;
        return lostCombo;
    }

    /**
     * Apply damage penalty to combo.
     * Reduces combo by half.
     *
     * @return The combo count that was lost
     */
    public int applyDamagePenalty() {
        int previousCombo = currentCombo;
        currentCombo = Math.max(0, currentCombo / 2);
        return previousCombo - currentCombo;
    }

    /**
     * Apply reduced damage penalty (for grace period).
     * Reduces combo by 25%.
     *
     * @return The combo count that was lost
     */
    public int applyReducedDamagePenalty() {
        int previousCombo = currentCombo;
        currentCombo = Math.max(0, currentCombo * 3 / 4);
        return previousCombo - currentCombo;
    }

    /**
     * Check if combo is currently active.
     */
    public boolean isActive() {
        return currentCombo > 0;
    }

    /**
     * Get current combo count.
     */
    public int getCurrentCombo() {
        return currentCombo;
    }

    /**
     * Get maximum combo achieved.
     */
    public int getMaxCombo() {
        return maxCombo;
    }

    /**
     * Get time since last hit in milliseconds.
     */
    public long getTimeSinceLastHit() {
        return System.currentTimeMillis() - lastHitTime;
    }

    /**
     * Get current variety bonus points.
     */
    public int getVarietyBonus() {
        return varietyBonus;
    }

    /**
     * Get variety multiplier (1.0 + varietyBonus * 0.01).
     */
    public float getVarietyMultiplier() {
        return 1.0f + (varietyBonus * 0.01f);
    }

    /**
     * Get number of unique actions used in current combo.
     */
    public int getUniqueActionCount() {
        return actionsUsedThisCombo.size();
    }

    /**
     * Check if a specific milestone has been reached.
     *
     * @param milestone The combo count milestone (5, 10, 25, 50, 100)
     * @return true if current combo exactly matches the milestone
     */
    public boolean hasReachedMilestone(int milestone) {
        return currentCombo == milestone;
    }

    /**
     * Get the next milestone to reach, or -1 if at max milestone.
     */
    public int getNextMilestone() {
        if (currentCombo < 5) return 5;
        if (currentCombo < 10) return 10;
        if (currentCombo < 25) return 25;
        if (currentCombo < 50) return 50;
        if (currentCombo < 100) return 100;
        return -1;
    }

    /**
     * Get progress to next milestone (0.0 - 1.0).
     */
    public float getMilestoneProgress() {
        int next = getNextMilestone();
        if (next == -1) return 1.0f;

        int previous = switch (next) {
            case 5 -> 0;
            case 10 -> 5;
            case 25 -> 10;
            case 50 -> 25;
            case 100 -> 50;
            default -> 0;
        };

        int range = next - previous;
        return (float) (currentCombo - previous) / range;
    }
}
