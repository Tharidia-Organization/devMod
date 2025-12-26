package com.devmod.endurance;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComboSystemDirectTest {

    @Test
    @DisplayName("Style rank progresses with style score thresholds")
    void styleRankProgressesWithStyleScoreThresholds() {
        ComboSystem.ComboSession session = new ComboSystem.ComboSession(UUID.randomUUID(), null);

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
            assertEquals(ranks[i], session.getCurrentRank());
        }

        assertEquals(ComboSystem.StyleRank.SSS, session.getHighestRank());
    }
}
