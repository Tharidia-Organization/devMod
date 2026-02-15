package com.devmod.arena;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BuildPhaseTest {

    @Test
    void allPhasesExist() {
        BuildPhase[] phases = BuildPhase.values();
        assertEquals(7, phases.length);
    }

    @Test
    void valueOfRoundTrips() {
        for (BuildPhase phase : BuildPhase.values()) {
            assertEquals(phase, BuildPhase.valueOf(phase.name()));
        }
    }

    @Test
    void ordinalOrder() {
        assertTrue(BuildPhase.INITIALIZING.ordinal() < BuildPhase.LOADING_CHUNKS.ordinal());
        assertTrue(BuildPhase.LOADING_CHUNKS.ordinal() < BuildPhase.PLACING_BLOCKS.ordinal());
        assertTrue(BuildPhase.PLACING_BLOCKS.ordinal() < BuildPhase.SPAWNING_ENTITIES.ordinal());
        assertTrue(BuildPhase.SPAWNING_ENTITIES.ordinal() < BuildPhase.FINALIZING.ordinal());
        assertTrue(BuildPhase.FINALIZING.ordinal() < BuildPhase.COMPLETE.ordinal());
        assertTrue(BuildPhase.COMPLETE.ordinal() < BuildPhase.FAILED.ordinal());
    }

    @Test
    void invalidValueOfThrows() {
        assertThrows(IllegalArgumentException.class, () -> BuildPhase.valueOf("NONEXISTENT"));
    }
}
