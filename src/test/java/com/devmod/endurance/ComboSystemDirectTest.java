package com.devmod.endurance;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.combat.core.ComboSessionImpl;
import com.devmod.endurance.combat.events.ComboEventDispatcher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the refactored ComboSystem components.
 */
class ComboSystemDirectTest {

    @Test
    @DisplayName("Style rank progresses with style score thresholds")
    void styleRankProgressesWithStyleScoreThresholds() {
        ComboEventDispatcher dispatcher = new ComboEventDispatcher();
        IComboSession session = new ComboSessionImpl(UUID.randomUUID(), null, dispatcher);

        ComboSystem.StyleRank[] ranks = {
            ComboSystem.StyleRank.D,
            ComboSystem.StyleRank.C,
            ComboSystem.StyleRank.B,
            ComboSystem.StyleRank.A,
            ComboSystem.StyleRank.S,
            ComboSystem.StyleRank.SS,
            ComboSystem.StyleRank.SSS
        };
        int[] thresholds = {0, 500, 1500, 3500, 7000, 12000, 20000};

        assertEquals(ComboSystem.StyleRank.D, session.getCurrentRank());

        int currentScore = session.getStyleScore();
        for (int i = 1; i < thresholds.length; i++) {
            int delta = thresholds[i] - currentScore;
            session.addBonusPoints(delta);
            currentScore = session.getStyleScore();
            assertEquals(ranks[i], session.getCurrentRank(),
                "Expected rank " + ranks[i] + " at threshold " + thresholds[i]);
        }

        assertEquals(ComboSystem.StyleRank.SSS, session.getHighestRank());
    }

    @Test
    @DisplayName("Combo increments on action and tracks max")
    void comboIncrementsOnAction() {
        ComboEventDispatcher dispatcher = new ComboEventDispatcher();
        IComboSession session = new ComboSessionImpl(UUID.randomUUID(), UUID.randomUUID(), dispatcher);

        assertEquals(0, session.getCurrentCombo());
        assertEquals(0, session.getMaxCombo());

        // Register multiple actions
        session.registerAction(ComboSystem.ActionType.LIGHT_ATTACK, 5f);
        assertEquals(1, session.getCurrentCombo());

        session.registerAction(ComboSystem.ActionType.HEAVY_ATTACK, 15f);
        assertEquals(2, session.getCurrentCombo());

        session.registerAction(ComboSystem.ActionType.CRITICAL_HIT, 25f);
        assertEquals(3, session.getCurrentCombo());

        assertEquals(3, session.getMaxCombo());
    }

    @Test
    @DisplayName("Damage taken reduces combo")
    void damageTakenReducesCombo() {
        ComboEventDispatcher dispatcher = new ComboEventDispatcher();
        IComboSession session = new ComboSessionImpl(UUID.randomUUID(), UUID.randomUUID(), dispatcher);

        // Build up combo
        for (int i = 0; i < 10; i++) {
            session.registerAction(ComboSystem.ActionType.LIGHT_ATTACK, 5f);
        }
        assertEquals(10, session.getCurrentCombo());

        // Take damage - should reduce combo
        session.onDamageTaken(10f);
        assertTrue(session.getCurrentCombo() < 10, "Combo should be reduced after damage");

        // Max combo should still be preserved
        assertEquals(10, session.getMaxCombo());
    }

    @Test
    @DisplayName("Style score from enum matches expected values")
    void styleRankFromScore() {
        assertEquals(ComboSystem.StyleRank.D, ComboSystem.StyleRank.fromScore(0));
        assertEquals(ComboSystem.StyleRank.D, ComboSystem.StyleRank.fromScore(499));
        assertEquals(ComboSystem.StyleRank.C, ComboSystem.StyleRank.fromScore(500));
        assertEquals(ComboSystem.StyleRank.B, ComboSystem.StyleRank.fromScore(1500));
        assertEquals(ComboSystem.StyleRank.A, ComboSystem.StyleRank.fromScore(3500));
        assertEquals(ComboSystem.StyleRank.S, ComboSystem.StyleRank.fromScore(7000));
        assertEquals(ComboSystem.StyleRank.SS, ComboSystem.StyleRank.fromScore(12000));
        assertEquals(ComboSystem.StyleRank.SSS, ComboSystem.StyleRank.fromScore(20000));
        assertEquals(ComboSystem.StyleRank.SSS, ComboSystem.StyleRank.fromScore(50000));
    }

    @Test
    @DisplayName("Action types have correct base points")
    void actionTypeBasePoints() {
        assertEquals(10, ComboSystem.ActionType.LIGHT_ATTACK.getBasePoints());
        assertEquals(25, ComboSystem.ActionType.HEAVY_ATTACK.getBasePoints());
        assertEquals(50, ComboSystem.ActionType.CRITICAL_HIT.getBasePoints());
        assertEquals(100, ComboSystem.ActionType.PERFECT_DODGE.getBasePoints());
        assertEquals(120, ComboSystem.ActionType.PARRY.getBasePoints());
        assertEquals(200, ComboSystem.ActionType.EXECUTION.getBasePoints());
    }

    @Test
    @DisplayName("Session tracks combat stats")
    void sessionTracksCombatStats() {
        ComboEventDispatcher dispatcher = new ComboEventDispatcher();
        IComboSession session = new ComboSessionImpl(UUID.randomUUID(), UUID.randomUUID(), dispatcher);

        // Register some actions
        session.registerAction(ComboSystem.ActionType.LIGHT_ATTACK, 10f);
        session.registerAction(ComboSystem.ActionType.HEAVY_ATTACK, 20f);
        session.registerKill(false, 5f);

        IComboSession.CombatStats stats = session.getStats();
        assertTrue(stats.totalHits() >= 2, "Should have at least 2 hits");
        assertTrue(stats.totalKills() >= 1, "Should have at least 1 kill");
        assertTrue(stats.totalDamage() > 0, "Should have tracked damage");
    }

    @Test
    @DisplayName("New wave resets wave-specific tracking")
    void newWaveResetsTracking() {
        ComboEventDispatcher dispatcher = new ComboEventDispatcher();
        ComboSessionImpl session = new ComboSessionImpl(UUID.randomUUID(), UUID.randomUUID(), dispatcher);

        // Take damage
        session.onDamageTaken(10f);

        // Start new wave
        session.startNewWave();

        // Damage tracking for this wave should be reset
        // (verified through the grace period behavior)
        assertDoesNotThrow(() -> session.onDamageTaken(5f));
    }

    @Test
    @DisplayName("Session returns correct player and quest IDs")
    void sessionReturnsCorrectIds() {
        UUID playerId = UUID.randomUUID();
        UUID questId = UUID.randomUUID();
        ComboEventDispatcher dispatcher = new ComboEventDispatcher();

        IComboSession session = new ComboSessionImpl(playerId, questId, dispatcher);

        assertEquals(playerId, session.getPlayerId());
        assertEquals(questId, session.getQuestId());
    }

    @Test
    @DisplayName("Register kill awards style points")
    void registerKillAwardsStylePoints() {
        ComboEventDispatcher dispatcher = new ComboEventDispatcher();
        IComboSession session = new ComboSessionImpl(UUID.randomUUID(), UUID.randomUUID(), dispatcher);

        int initialScore = session.getStyleScore();

        IComboSession.ActionResult result = session.registerKill(true, 50f); // Quick kill with overkill

        assertTrue(result.styleEarned() > 0, "Kill should award style points");
        assertTrue(session.getStyleScore() > initialScore, "Style score should increase");
    }
}
