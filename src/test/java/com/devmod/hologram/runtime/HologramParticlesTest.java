package com.devmod.hologram.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HologramParticles")
class HologramParticlesTest {

    @Test
    @DisplayName("DEFAULT_INTERVAL is positive")
    void defaultIntervalPositive() {
        assertTrue(HologramParticles.DEFAULT_INTERVAL > 0);
    }

    @Test
    @DisplayName("spawn tolerates all-null arguments without throwing")
    void spawnNullSafe() {
        assertDoesNotThrow(() -> HologramParticles.spawn(null, null, null, 0));
    }

    @Test
    @DisplayName("spawnSpawnEffect tolerates null arguments")
    void spawnEffectNullSafe() {
        assertDoesNotThrow(() -> HologramParticles.spawnSpawnEffect(null, null));
    }

    @Test
    @DisplayName("spawnRemoveEffect tolerates null arguments")
    void removeEffectNullSafe() {
        assertDoesNotThrow(() -> HologramParticles.spawnRemoveEffect(null, null));
    }
}
