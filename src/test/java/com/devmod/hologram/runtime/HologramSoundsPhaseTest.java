package com.devmod.hologram.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;

@DisplayName("HologramSounds.Phase")
class HologramSoundsPhaseTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Test
    @DisplayName("all phases have non-null sound events")
    void allHaveSounds() {
        for (HologramSounds.Phase phase : HologramSounds.Phase.values()) {
            assertNotNull(phase.getSound(), phase.name() + " should have a sound");
        }
    }

    @Test
    @DisplayName("all phases have positive base volume")
    void positiveVolume() {
        for (HologramSounds.Phase phase : HologramSounds.Phase.values()) {
            assertTrue(phase.getBaseVolume() > 0,
                phase.name() + " should have positive volume");
        }
    }

    @Test
    @DisplayName("all phases have positive base pitch")
    void positivePitch() {
        for (HologramSounds.Phase phase : HologramSounds.Phase.values()) {
            assertTrue(phase.getBasePitch() > 0,
                phase.name() + " should have positive pitch");
        }
    }

    @Test
    @DisplayName("has exactly 9 phases")
    void phaseCount() {
        assertEquals(9, HologramSounds.Phase.values().length);
    }

    @Test
    @DisplayName("playPhase tolerates all-null arguments without throwing")
    void playPhaseNullSafe() {
        assertDoesNotThrow(() -> HologramSounds.playPhase(null, null, null));
    }

    @Test
    @DisplayName("playPhaseLocal tolerates all-null arguments without throwing")
    void playPhaseLocalNullSafe() {
        assertDoesNotThrow(() -> HologramSounds.playPhaseLocal(null, null, null));
    }
}
