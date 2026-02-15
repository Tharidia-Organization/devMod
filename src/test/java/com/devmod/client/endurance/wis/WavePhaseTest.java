package com.devmod.client.endurance.wis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WavePhaseTest {

    @Test
    void enumHasFourValues() {
        assertEquals(4, WavePhase.values().length);
    }

    @Test
    void preBrief_hasFixedDuration() {
        assertTrue(WavePhase.PRE_BRIEF.hasFixedDuration());
        assertEquals(200, WavePhase.PRE_BRIEF.getDefaultDurationTicks());
    }

    @Test
    void combat_hasVariableDuration() {
        assertFalse(WavePhase.COMBAT.hasFixedDuration());
        assertEquals(-1, WavePhase.COMBAT.getDefaultDurationTicks());
    }

    @Test
    void debrief_hasVariableDuration() {
        assertFalse(WavePhase.DEBRIEF.hasFixedDuration());
        assertEquals(-1, WavePhase.DEBRIEF.getDefaultDurationTicks());
    }

    @Test
    void idle_hasVariableDuration() {
        assertFalse(WavePhase.IDLE.hasFixedDuration());
        assertEquals(-1, WavePhase.IDLE.getDefaultDurationTicks());
    }

    @Test
    void displayNames_areNonEmpty() {
        for (WavePhase phase : WavePhase.values()) {
            assertNotNull(phase.getDisplayName());
            assertFalse(phase.getDisplayName().isEmpty(), phase + " should have a display name");
        }
    }

    @Test
    void preBriefDuration_is10Seconds() {
        // 10 seconds * 20 ticks/sec = 200 ticks
        assertEquals(200, WavePhase.PRE_BRIEF.getDefaultDurationTicks());
    }
}
