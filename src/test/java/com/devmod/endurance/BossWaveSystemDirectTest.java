package com.devmod.endurance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossWaveSystemDirectTest {

    @Test
    @DisplayName("Boss waves trigger every 5 waves when no tension state is present")
    void bossWavesEveryFiveWhenNoTensionState() {
        BossWaveSystem system = BossWaveSystem.INSTANCE;

        assertFalse(system.isBossWave(1, null));
        assertFalse(system.isBossWave(4, null));
        assertTrue(system.isBossWave(5, null));
        assertTrue(system.isBossWave(10, null));
    }
}
