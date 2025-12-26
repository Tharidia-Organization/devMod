package com.devmod.endurance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DifficultyScalerDirectTest {

    @Test
    @DisplayName("Mob count scaling uses sqrt and clamps player count")
    void scaleMobCountUsesSqrtAndClamp() {
        DifficultyScaler scaler = DifficultyScaler.INSTANCE;

        int scaled = scaler.scaleMobCount(10, 4, QuestType.PVE_COOP);
        assertEquals(20, scaled);

        int clamped = scaler.scaleMobCount(10, 100, QuestType.PVE_COOP);
        assertEquals(25, clamped);
    }

    @Test
    @DisplayName("Wave multiplier scales progressively and ramps in endless mode")
    void waveMultiplierScalesProgressively() {
        DifficultyScaler scaler = DifficultyScaler.INSTANCE;

        assertEquals(1.0f, scaler.getWaveMultiplier(1, 10), 0.0001f);
        assertEquals(1.0f, scaler.getWaveMultiplier(2, 10), 0.0001f);
        assertEquals(1.05f, scaler.getWaveMultiplier(3, 10), 0.0001f);

        assertEquals(1.48f, scaler.getWaveMultiplier(11, 0), 0.0001f);
    }
}
