package com.devmod.arena.cleanup;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class CleanupResultTest {

    @Test
    void emptyResult() {
        CleanupResult result = CleanupResult.empty();
        assertEquals(0, result.entitiesRemoved());
        assertEquals(0, result.blockEntitiesRemoved());
        assertEquals(0, result.scheduledTicksCancelled());
        assertEquals(0, result.blocksRemoved());
        assertEquals(0, result.chunksUnloaded());
        assertEquals(0L, result.durationMs());
        assertTrue(result.isComplete());
        assertFalse(result.hasChanges());
        assertEquals(0, result.totalCleaned());
        assertFalse(result.hasResiduals());
    }

    @Test
    void failedResult() {
        CleanupResult result = CleanupResult.failed("Something went wrong");
        assertFalse(result.isComplete());
        assertEquals(1, result.warnings().size());
        assertEquals("Something went wrong", result.warnings().get(0));
    }

    @Test
    void hasChangesDetectsAnyActivity() {
        assertTrue(new CleanupResult(1, 0, 0, 0, 0, 0, 0, 0, List.of()).hasChanges());
        assertTrue(new CleanupResult(0, 1, 0, 0, 0, 0, 0, 0, List.of()).hasChanges());
        assertTrue(new CleanupResult(0, 0, 1, 0, 0, 0, 0, 0, List.of()).hasChanges());
        assertTrue(new CleanupResult(0, 0, 0, 1, 0, 0, 0, 0, List.of()).hasChanges());
        assertTrue(new CleanupResult(0, 0, 0, 0, 1, 0, 0, 0, List.of()).hasChanges());
    }

    @Test
    void totalCleaned() {
        CleanupResult result = new CleanupResult(5, 3, 2, 10, 1, 100, 0, 0, List.of());
        assertEquals(21, result.totalCleaned());
    }

    @Test
    void residualDetection() {
        assertTrue(new CleanupResult(0, 0, 0, 0, 0, 0, 1, 0, List.of()).hasResiduals());
        assertTrue(new CleanupResult(0, 0, 0, 0, 0, 0, 0, 1, List.of()).hasResiduals());
        assertFalse(new CleanupResult(0, 0, 0, 0, 0, 0, 0, 0, List.of()).hasResiduals());
    }

    @Test
    void nullWarningsBecomesEmptyList() {
        CleanupResult result = new CleanupResult(0, 0, 0, 0, 0, 0, 0, 0, null);
        assertNotNull(result.warnings());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void warningsDefensivelyCopied() {
        var warnings = new java.util.ArrayList<>(List.of("w1", "w2"));
        CleanupResult result = new CleanupResult(0, 0, 0, 0, 0, 0, 0, 0, warnings);
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("w3"));
    }

    @Test
    void builderAccumulates() {
        CleanupResult result = CleanupResult.builder()
            .entitiesRemoved(5)
            .addEntitiesRemoved(3)
            .blockEntitiesRemoved(2)
            .addBlockEntitiesRemoved(1)
            .scheduledTicksCancelled(4)
            .addScheduledTicksCancelled(2)
            .blocksRemoved(100)
            .addBlocksRemoved(50)
            .chunksUnloaded(3)
            .addChunksUnloaded(1)
            .entitiesResidual(2)
            .blocksResidual(5)
            .addWarning("warn1")
            .addWarnings(List.of("warn2", "warn3"))
            .durationMs(500)
            .build();

        assertEquals(8, result.entitiesRemoved());
        assertEquals(3, result.blockEntitiesRemoved());
        assertEquals(6, result.scheduledTicksCancelled());
        assertEquals(150, result.blocksRemoved());
        assertEquals(4, result.chunksUnloaded());
        assertEquals(2, result.entitiesResidual());
        assertEquals(5, result.blocksResidual());
        assertEquals(500, result.durationMs());
        assertEquals(3, result.warnings().size());
    }

    @Test
    void toStringContainsAllFields() {
        CleanupResult result = new CleanupResult(1, 2, 3, 4, 5, 100, 6, 7, List.of("w"));
        String str = result.toString();
        assertTrue(str.contains("entities=1"));
        assertTrue(str.contains("blockEntities=2"));
        assertTrue(str.contains("blocks=4"));
        assertTrue(str.contains("warnings=1"));
    }
}
