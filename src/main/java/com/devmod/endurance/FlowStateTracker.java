package com.devmod.endurance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;


import com.devmod.endurance.ComboSystem.ActionType;

public class FlowStateTracker {
    // Configuration
    private static final int STALE_THRESHOLD = 3;           // Actions before STALE triggers
    private static final int HISTORY_SIZE = 10;             // Actions to remember
    private static final int VIRTUOSO_THRESHOLD = 5;        // Unique actions for VIRTUOSO
    private static final float STALE_PENALTY = 0.5f;        // 50% reduction
    private static final float FRESH_BONUS = 1.25f;         // 25% bonus
    private static final float VIRTUOSO_MULTIPLIER = 2.0f;  // 2x multiplier
    private static final long FRESH_DURATION_MS = 2000;     // 2 seconds

    // State tracking
    private final Deque<ActionType> actionHistory = new ArrayDeque<>(HISTORY_SIZE);
    private final Set<ActionType> uniqueActionsThisCombo = EnumSet.noneOf(ActionType.class);

    private FlowState currentState = FlowState.NEUTRAL;
    private long freshUntilTime = 0;
    private int consecutiveSameAction = 0;
    private ActionType lastAction = null;

    /**
     * Flow states that affect style point gains.
     */
    public enum FlowState {
        STALE("STALE", EnduranceColors.Flow.STALE, STALE_PENALTY, "Boring! Try something new!"),
        NEUTRAL("", EnduranceColors.Flow.NEUTRAL, 1.0f, ""),
        FRESH("FRESH!", EnduranceColors.Flow.FRESH, FRESH_BONUS, "Nice variety!"),
        VIRTUOSO("VIRTUOSO!", EnduranceColors.Flow.VIRTUOSO, VIRTUOSO_MULTIPLIER, "Incredible style!");

        private final String displayName;
        private final int color;
        private final float multiplier;
        private final String message;

        FlowState(String displayName, int color, float multiplier, String message) {
            this.displayName = displayName;
            this.color = color;
            this.multiplier = multiplier;
            this.message = message;
        }

        public String getDisplayName() { return displayName; }
        public int getColor() { return color; }
        public float getMultiplier() { return multiplier; }
        public String getMessage() { return message; }
    }

    /**
     * Result of processing an action through the flow state system.
     */
    public record FlowResult(
        FlowState state,
        float styleMultiplier,
        boolean stateChanged,
        boolean isVirtuoso
    ) {}

    /**
     * Process an action and return the current flow state multiplier.
     * Call this BEFORE calculating style points for an action.
     */
    public FlowResult processAction(ActionType action) {
        long now = System.currentTimeMillis();
        FlowState previousState = currentState;

        // Track action history
        if (actionHistory.size() >= HISTORY_SIZE) {
            actionHistory.pollFirst();
        }
        actionHistory.addLast(action);

        // Track unique actions
        boolean isNewAction = uniqueActionsThisCombo.add(action);

        // Check for consecutive same action (STALE)
        if (action == lastAction) {
            consecutiveSameAction++;
        } else {
            consecutiveSameAction = 1;
            lastAction = action;
        }

        // Determine flow state
        FlowState newState = calculateFlowState(isNewAction, now);
        boolean stateChanged = newState != previousState;
        currentState = newState;

        // Check for VIRTUOSO
        boolean isVirtuoso = uniqueActionsThisCombo.size() >= VIRTUOSO_THRESHOLD;

        // Calculate final multiplier
        float multiplier = newState.multiplier;
        if (isVirtuoso && newState != FlowState.STALE) {
            multiplier = VIRTUOSO_MULTIPLIER;
        }

        return new FlowResult(newState, multiplier, stateChanged, isVirtuoso);
    }

    /**
     * Calculate current flow state based on action patterns.
     */
    private FlowState calculateFlowState(boolean isNewAction, long now) {
        // STALE takes priority - punish repetition
        if (consecutiveSameAction >= STALE_THRESHOLD) {
            return FlowState.STALE;
        }

        // FRESH for new actions (temporary)
        if (isNewAction) {
            freshUntilTime = now + FRESH_DURATION_MS;
            return FlowState.FRESH;
        }

        // FRESH still active?
        if (now < freshUntilTime) {
            return FlowState.FRESH;
        }

        return FlowState.NEUTRAL;
    }

    /**
     * Reset tracker when combo ends.
     */
    public void onComboEnd() {
        uniqueActionsThisCombo.clear();
        consecutiveSameAction = 0;
        lastAction = null;
        freshUntilTime = 0;
        currentState = FlowState.NEUTRAL;
        // Keep action history for pattern analysis
    }

    /**
     * Full reset when session ends.
     */
    public void reset() {
        actionHistory.clear();
        uniqueActionsThisCombo.clear();
        consecutiveSameAction = 0;
        lastAction = null;
        freshUntilTime = 0;
        currentState = FlowState.NEUTRAL;
    }

    /**
     * Check if currently in VIRTUOSO state.
     */
    public boolean isVirtuoso() {
        return uniqueActionsThisCombo.size() >= VIRTUOSO_THRESHOLD && currentState != FlowState.STALE;
    }

    /**
     * Check if currently STALE.
     */
    public boolean isStale() {
        return currentState == FlowState.STALE;
    }

    /**
     * Get current flow state.
     */
    public FlowState getCurrentState() {
        return currentState;
    }

    /**
     * Get number of unique actions in current combo.
     */
    public int getUniqueActionCount() {
        return uniqueActionsThisCombo.size();
    }

    /**
     * Get consecutive same action count.
     */
    public int getConsecutiveSameAction() {
        return consecutiveSameAction;
    }

    /**
     * Get progress towards VIRTUOSO (0.0 - 1.0).
     */
    public float getVirtuosoProgress() {
        return Math.min(1.0f, (float) uniqueActionsThisCombo.size() / VIRTUOSO_THRESHOLD);
    }

    /**
     * Get progress towards STALE (0.0 - 1.0), inverted so 1.0 = about to go stale.
     */
    public float getStaleRisk() {
        if (consecutiveSameAction >= STALE_THRESHOLD) return 1.0f;
        return (float) consecutiveSameAction / STALE_THRESHOLD;
    }
}
