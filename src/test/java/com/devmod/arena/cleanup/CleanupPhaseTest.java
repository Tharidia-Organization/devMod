package com.devmod.arena.cleanup;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CleanupPhaseTest {

    @Test
    void correctPhaseCount() {
        assertEquals(4, CleanupPhase.values().length);
    }

    @Test
    void orderIsSequential() {
        assertEquals(1, CleanupPhase.ENTITIES.getOrder());
        assertEquals(2, CleanupPhase.BLOCK_ENTITIES.getOrder());
        assertEquals(3, CleanupPhase.SCHEDULED_TICKS.getOrder());
        assertEquals(4, CleanupPhase.BLOCKS.getOrder());
    }

    @Test
    void displayNames() {
        assertEquals("Entities", CleanupPhase.ENTITIES.getDisplayName());
        assertEquals("Block Entities", CleanupPhase.BLOCK_ENTITIES.getDisplayName());
        assertEquals("Scheduled Ticks", CleanupPhase.SCHEDULED_TICKS.getDisplayName());
        assertEquals("Blocks", CleanupPhase.BLOCKS.getDisplayName());
    }

    @Test
    void nextPhase() {
        assertEquals(CleanupPhase.BLOCK_ENTITIES, CleanupPhase.ENTITIES.next());
        assertEquals(CleanupPhase.SCHEDULED_TICKS, CleanupPhase.BLOCK_ENTITIES.next());
        assertEquals(CleanupPhase.BLOCKS, CleanupPhase.SCHEDULED_TICKS.next());
        assertNull(CleanupPhase.BLOCKS.next());
    }

    @Test
    void isBefore() {
        assertTrue(CleanupPhase.ENTITIES.isBefore(CleanupPhase.BLOCK_ENTITIES));
        assertTrue(CleanupPhase.ENTITIES.isBefore(CleanupPhase.BLOCKS));
        assertFalse(CleanupPhase.BLOCKS.isBefore(CleanupPhase.ENTITIES));
        assertFalse(CleanupPhase.ENTITIES.isBefore(CleanupPhase.ENTITIES));
    }
}
