package com.devmod.arena;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProgressFlagsTest {

    @Test
    void flagValuesArePowersOfTwo() {
        assertEquals(0, ProgressFlags.NONE);
        assertEquals(1, ProgressFlags.PHASE_CHANGED);
        assertEquals(2, ProgressFlags.COMPLETE);
        assertEquals(4, ProgressFlags.FAILED);
        assertEquals(8, ProgressFlags.PAUSED);
    }

    @Test
    void flagsAreBitIndependent() {
        int combined = ProgressFlags.PHASE_CHANGED | ProgressFlags.COMPLETE;
        assertTrue((combined & ProgressFlags.PHASE_CHANGED) != 0);
        assertTrue((combined & ProgressFlags.COMPLETE) != 0);
        assertEquals(0, combined & ProgressFlags.FAILED);
        assertEquals(0, combined & ProgressFlags.PAUSED);
    }

    @Test
    void noneHasNoFlags() {
        assertEquals(0, ProgressFlags.NONE & ProgressFlags.PHASE_CHANGED);
        assertEquals(0, ProgressFlags.NONE & ProgressFlags.COMPLETE);
        assertEquals(0, ProgressFlags.NONE & ProgressFlags.FAILED);
        assertEquals(0, ProgressFlags.NONE & ProgressFlags.PAUSED);
    }

    @Test
    void allFlagsCombine() {
        int all = ProgressFlags.PHASE_CHANGED | ProgressFlags.COMPLETE
                | ProgressFlags.FAILED | ProgressFlags.PAUSED;
        assertEquals(15, all);
    }
}
