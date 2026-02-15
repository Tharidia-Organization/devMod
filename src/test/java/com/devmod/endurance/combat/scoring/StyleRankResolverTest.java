package com.devmod.endurance.combat.scoring;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.endurance.ComboSystem.StyleRank;
import com.devmod.endurance.config.EnduranceConfigManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StyleRankResolverTest {

    @Test
    @DisplayName("resolveWithDefaults returns D for zero score")
    void resolveDefaultsZero() {
        StyleRankResolver resolver = new StyleRankResolver(mock(EnduranceConfigManager.class));
        assertEquals(StyleRank.D, resolver.resolveWithDefaults(0));
    }

    @Test
    @DisplayName("resolveWithDefaults returns correct ranks from enum thresholds")
    void resolveDefaultsAllRanks() {
        StyleRankResolver resolver = new StyleRankResolver(mock(EnduranceConfigManager.class));
        // Each rank should be resolved at exactly its threshold
        for (StyleRank rank : StyleRank.values()) {
            StyleRank resolved = resolver.resolveWithDefaults(rank.getThreshold());
            // At exactly the threshold, should get at least this rank
            assertTrue(resolved.ordinal() >= rank.ordinal(),
                "At threshold " + rank.getThreshold() + " expected at least " + rank + " but got " + resolved);
        }
    }

    @Test
    @DisplayName("resolveWithDefaults returns highest qualifying rank")
    void resolveDefaultsHighestQualifying() {
        StyleRankResolver resolver = new StyleRankResolver(mock(EnduranceConfigManager.class));
        // Very high score should yield SSS
        StyleRank result = resolver.resolveWithDefaults(999999);
        assertEquals(StyleRank.SSS, result);
    }

    @Test
    @DisplayName("resolve with null questId falls back to defaults")
    void resolveNullQuestIdFallsBack() {
        StyleRankResolver resolver = new StyleRankResolver(mock(EnduranceConfigManager.class));
        // Should not throw, should use default thresholds
        StyleRank result = resolver.resolve(0, null);
        assertEquals(StyleRank.D, result);
    }

    @Test
    @DisplayName("resolve with questId uses config thresholds")
    void resolveWithQuestIdUsesConfig() {
        EnduranceConfigManager config = mock(EnduranceConfigManager.class);
        UUID questId = UUID.randomUUID();

        // Set up thresholds via config mock
        when(config.getStyleRankDThreshold(questId)).thenReturn(0);
        when(config.getStyleRankCThreshold(questId)).thenReturn(100);
        when(config.getStyleRankBThreshold(questId)).thenReturn(200);
        when(config.getStyleRankAThreshold(questId)).thenReturn(300);
        when(config.getStyleRankSThreshold(questId)).thenReturn(400);
        when(config.getStyleRankSSThreshold(questId)).thenReturn(500);
        when(config.getStyleRankSSSThreshold(questId)).thenReturn(600);

        StyleRankResolver resolver = new StyleRankResolver(config);
        assertEquals(StyleRank.D, resolver.resolve(50, questId));
        assertEquals(StyleRank.C, resolver.resolve(100, questId));
        assertEquals(StyleRank.B, resolver.resolve(250, questId));
        assertEquals(StyleRank.A, resolver.resolve(300, questId));
        assertEquals(StyleRank.S, resolver.resolve(450, questId));
        assertEquals(StyleRank.SS, resolver.resolve(550, questId));
        assertEquals(StyleRank.SSS, resolver.resolve(600, questId));
    }

    @Test
    @DisplayName("getThreshold with null questId returns enum default")
    void getThresholdNullQuest() {
        StyleRankResolver resolver = new StyleRankResolver(mock(EnduranceConfigManager.class));
        for (StyleRank rank : StyleRank.values()) {
            assertEquals(rank.getThreshold(), resolver.getThreshold(rank, null));
        }
    }

    @Test
    @DisplayName("getThreshold with questId queries config for all ranks")
    void getThresholdWithQuestIdAllRanks() {
        EnduranceConfigManager config = mock(EnduranceConfigManager.class);
        UUID questId = UUID.randomUUID();

        when(config.getStyleRankDThreshold(questId)).thenReturn(10);
        when(config.getStyleRankCThreshold(questId)).thenReturn(110);
        when(config.getStyleRankBThreshold(questId)).thenReturn(210);
        when(config.getStyleRankAThreshold(questId)).thenReturn(310);
        when(config.getStyleRankSThreshold(questId)).thenReturn(410);
        when(config.getStyleRankSSThreshold(questId)).thenReturn(510);
        when(config.getStyleRankSSSThreshold(questId)).thenReturn(610);

        StyleRankResolver resolver = new StyleRankResolver(config);
        assertEquals(10, resolver.getThreshold(StyleRank.D, questId));
        assertEquals(110, resolver.getThreshold(StyleRank.C, questId));
        assertEquals(210, resolver.getThreshold(StyleRank.B, questId));
        assertEquals(310, resolver.getThreshold(StyleRank.A, questId));
        assertEquals(410, resolver.getThreshold(StyleRank.S, questId));
        assertEquals(510, resolver.getThreshold(StyleRank.SS, questId));
        assertEquals(610, resolver.getThreshold(StyleRank.SSS, questId));
    }

    @Test
    @DisplayName("getProgressToNextRank returns 1.0 for max rank")
    void progressAtMaxRank() {
        StyleRankResolver resolver = new StyleRankResolver(mock(EnduranceConfigManager.class));
        float progress = resolver.getProgressToNextRank(999, StyleRank.SSS, null);
        assertEquals(1.0f, progress, 0.001f);
    }

    @Test
    @DisplayName("getProgressToNextRank calculates fraction between thresholds")
    void progressFraction() {
        EnduranceConfigManager config = mock(EnduranceConfigManager.class);
        UUID questId = UUID.randomUUID();
        when(config.getStyleRankCThreshold(questId)).thenReturn(100);
        when(config.getStyleRankBThreshold(questId)).thenReturn(200);

        StyleRankResolver resolver = new StyleRankResolver(config);
        // At 150, between C(100) and B(200): (150-100)/(200-100) = 0.5
        float progress = resolver.getProgressToNextRank(150, StyleRank.C, questId);
        assertEquals(0.5f, progress, 0.001f);
    }

    @Test
    @DisplayName("getProgressToNextRank clamps to 1.0")
    void progressClampedTo1() {
        EnduranceConfigManager config = mock(EnduranceConfigManager.class);
        UUID questId = UUID.randomUUID();
        when(config.getStyleRankCThreshold(questId)).thenReturn(100);
        when(config.getStyleRankBThreshold(questId)).thenReturn(200);

        StyleRankResolver resolver = new StyleRankResolver(config);
        float progress = resolver.getProgressToNextRank(250, StyleRank.C, questId);
        assertEquals(1.0f, progress, 0.001f);
    }
}
