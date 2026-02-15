package com.devmod.endurance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.endurance.ComboSystem.ActionType;
import com.devmod.endurance.FlowStateTracker.FlowResult;
import com.devmod.endurance.FlowStateTracker.FlowState;

import static org.junit.jupiter.api.Assertions.*;

class FlowStateTrackerTest {

    private FlowStateTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new FlowStateTracker();
    }

    @Test
    @DisplayName("New tracker starts in NEUTRAL state")
    void initialStateIsNeutral() {
        assertEquals(FlowState.NEUTRAL, tracker.getCurrentState());
        assertEquals(0, tracker.getUniqueActionCount());
        assertEquals(0, tracker.getConsecutiveSameAction());
        assertFalse(tracker.isVirtuoso());
        assertFalse(tracker.isStale());
    }

    @Test
    @DisplayName("First action of a type triggers FRESH state")
    void firstActionTriggersFresh() {
        FlowResult result = tracker.processAction(ActionType.LIGHT_ATTACK);
        assertEquals(FlowState.FRESH, result.state());
        assertTrue(result.stateChanged());
        assertEquals(FlowStateTracker.FlowState.FRESH.getMultiplier(), result.styleMultiplier(), 0.001f);
    }

    @Test
    @DisplayName("Repeating the same action 3+ times triggers STALE")
    void repeatedActionTriggersStale() {
        tracker.processAction(ActionType.LIGHT_ATTACK); // 1st
        tracker.processAction(ActionType.LIGHT_ATTACK); // 2nd
        FlowResult result = tracker.processAction(ActionType.LIGHT_ATTACK); // 3rd = STALE threshold
        assertEquals(FlowState.STALE, result.state());
        assertTrue(tracker.isStale());
        assertEquals(FlowState.STALE.getMultiplier(), result.styleMultiplier(), 0.001f);
    }

    @Test
    @DisplayName("Mixing actions resets consecutive counter")
    void mixedActionsResetConsecutive() {
        tracker.processAction(ActionType.LIGHT_ATTACK);
        tracker.processAction(ActionType.LIGHT_ATTACK);
        // Switch action type before hitting STALE threshold
        FlowResult result = tracker.processAction(ActionType.HEAVY_ATTACK);
        assertNotEquals(FlowState.STALE, result.state());
        assertEquals(1, tracker.getConsecutiveSameAction());
    }

    @Test
    @DisplayName("5+ unique actions triggers VIRTUOSO multiplier")
    void fiveUniqueActionsTriggersVirtuoso() {
        ActionType[] actions = ActionType.values();
        // Process 5 different action types
        for (int i = 0; i < 5 && i < actions.length; i++) {
            tracker.processAction(actions[i]);
        }
        assertTrue(tracker.isVirtuoso());
        assertEquals(5, tracker.getUniqueActionCount());
    }

    @Test
    @DisplayName("VIRTUOSO multiplier is 2.0 when not STALE")
    void virtuosoMultiplierIs2x() {
        ActionType[] actions = ActionType.values();
        FlowResult result = null;
        for (int i = 0; i < 5 && i < actions.length; i++) {
            result = tracker.processAction(actions[i]);
        }
        assertNotNull(result);
        assertTrue(result.isVirtuoso());
        assertEquals(2.0f, result.styleMultiplier(), 0.001f);
    }

    @Test
    @DisplayName("STALE overrides VIRTUOSO multiplier")
    void staleOverridesVirtuoso() {
        ActionType[] actions = ActionType.values();
        // Get 5 unique actions first
        for (int i = 0; i < 5 && i < actions.length; i++) {
            tracker.processAction(actions[i]);
        }
        assertTrue(tracker.isVirtuoso());
        // Now spam last action to go STALE
        ActionType lastUsed = actions[Math.min(4, actions.length - 1)];
        tracker.processAction(lastUsed); // 2nd consecutive
        FlowResult result = tracker.processAction(lastUsed); // 3rd consecutive = STALE
        assertEquals(FlowState.STALE, result.state());
        assertEquals(FlowState.STALE.getMultiplier(), result.styleMultiplier(), 0.001f);
        // isVirtuoso is based on unique action count (still >= threshold),
        // but STALE overrides the multiplier — that's the key behavior
        assertTrue(result.isVirtuoso(), "isVirtuoso reflects unique action count, not multiplier");
    }

    @Test
    @DisplayName("onComboEnd clears unique actions but keeps history")
    void comboEndClearsUniqueActions() {
        tracker.processAction(ActionType.LIGHT_ATTACK);
        tracker.processAction(ActionType.HEAVY_ATTACK);
        assertEquals(2, tracker.getUniqueActionCount());

        tracker.onComboEnd();

        assertEquals(0, tracker.getUniqueActionCount());
        assertEquals(FlowState.NEUTRAL, tracker.getCurrentState());
        assertEquals(0, tracker.getConsecutiveSameAction());
    }

    @Test
    @DisplayName("Full reset clears everything")
    void fullResetClearsAll() {
        tracker.processAction(ActionType.LIGHT_ATTACK);
        tracker.processAction(ActionType.HEAVY_ATTACK);
        tracker.reset();

        assertEquals(0, tracker.getUniqueActionCount());
        assertEquals(FlowState.NEUTRAL, tracker.getCurrentState());
        assertFalse(tracker.isVirtuoso());
        assertFalse(tracker.isStale());
    }

    @Test
    @DisplayName("Virtuoso progress scales from 0.0 to 1.0")
    void virtuosoProgressScales() {
        assertEquals(0.0f, tracker.getVirtuosoProgress(), 0.001f);

        tracker.processAction(ActionType.LIGHT_ATTACK);
        assertEquals(0.2f, tracker.getVirtuosoProgress(), 0.001f); // 1/5

        tracker.processAction(ActionType.HEAVY_ATTACK);
        assertEquals(0.4f, tracker.getVirtuosoProgress(), 0.001f); // 2/5
    }

    @Test
    @DisplayName("Stale risk scales from 0.0 to 1.0")
    void staleRiskScales() {
        assertEquals(0.0f, tracker.getStaleRisk(), 0.001f);

        tracker.processAction(ActionType.LIGHT_ATTACK); // 1st
        float riskAfterFirst = tracker.getStaleRisk();
        assertTrue(riskAfterFirst > 0f);

        tracker.processAction(ActionType.LIGHT_ATTACK); // 2nd
        float riskAfterSecond = tracker.getStaleRisk();
        assertTrue(riskAfterSecond > riskAfterFirst);

        tracker.processAction(ActionType.LIGHT_ATTACK); // 3rd = STALE
        assertEquals(1.0f, tracker.getStaleRisk(), 0.001f);
    }

    @Test
    @DisplayName("FlowState enum has correct network IDs for all states")
    void flowStateNetworkIds() {
        assertEquals(FlowState.STALE, FlowState.fromNetworkId(0));
        assertEquals(FlowState.NEUTRAL, FlowState.fromNetworkId(1));
        assertEquals(FlowState.FRESH, FlowState.fromNetworkId(2));
        assertEquals(FlowState.VIRTUOSO, FlowState.fromNetworkId(3));
        // Invalid ID returns NEUTRAL
        assertEquals(FlowState.NEUTRAL, FlowState.fromNetworkId(99));
    }

    @Test
    @DisplayName("FlowState multipliers are ordered correctly")
    void flowStateMultipliersOrdered() {
        assertTrue(FlowState.STALE.getMultiplier() < FlowState.NEUTRAL.getMultiplier());
        assertEquals(1.0f, FlowState.NEUTRAL.getMultiplier(), 0.001f);
        assertTrue(FlowState.FRESH.getMultiplier() > FlowState.NEUTRAL.getMultiplier());
        assertTrue(FlowState.VIRTUOSO.getMultiplier() > FlowState.FRESH.getMultiplier());
    }
}
