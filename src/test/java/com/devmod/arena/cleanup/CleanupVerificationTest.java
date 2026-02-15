package com.devmod.arena.cleanup;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class CleanupVerificationTest {

    @Test
    void successIsFullyClean() {
        CleanupVerification v = CleanupVerification.success();
        assertTrue(v.isFullyClean());
        assertFalse(v.hasViolations());
        assertTrue(v.entitiesClean());
        assertTrue(v.blockEntitiesClean());
        assertTrue(v.scheduledTicksClean());
        assertTrue(v.blocksClean());
    }

    @Test
    void nullViolationsBecomesEmpty() {
        CleanupVerification v = new CleanupVerification(true, true, true, true, null);
        assertNotNull(v.violations());
        assertTrue(v.violations().isEmpty());
    }

    @Test
    void violationsDefensivelyCopied() {
        var violations = new java.util.ArrayList<>(List.of("v1"));
        CleanupVerification v = new CleanupVerification(true, true, true, true, violations);
        assertThrows(UnsupportedOperationException.class, () -> v.violations().add("v2"));
    }

    @Test
    void builderViolationSetsPhaseFlag() {
        CleanupVerification v = CleanupVerification.builder()
            .addViolation(CleanupPhase.ENTITIES, "entity leaked")
            .addViolation(CleanupPhase.BLOCKS, "blocks remain")
            .build();

        assertFalse(v.entitiesClean());
        assertTrue(v.blockEntitiesClean());
        assertTrue(v.scheduledTicksClean());
        assertFalse(v.blocksClean());
        assertFalse(v.isFullyClean());
        assertTrue(v.hasViolations());
        assertEquals(2, v.violations().size());
    }

    @Test
    void builderAddViolationForAllPhases() {
        CleanupVerification v = CleanupVerification.builder()
            .addViolation(CleanupPhase.ENTITIES, "e")
            .addViolation(CleanupPhase.BLOCK_ENTITIES, "be")
            .addViolation(CleanupPhase.SCHEDULED_TICKS, "st")
            .addViolation(CleanupPhase.BLOCKS, "b")
            .build();

        assertFalse(v.entitiesClean());
        assertFalse(v.blockEntitiesClean());
        assertFalse(v.scheduledTicksClean());
        assertFalse(v.blocksClean());
        assertEquals(4, v.violations().size());
    }

    @Test
    void toStringClean() {
        assertEquals("CleanupVerification[CLEAN]", CleanupVerification.success().toString());
    }

    @Test
    void toStringDirty() {
        CleanupVerification v = CleanupVerification.builder()
            .entitiesClean(false)
            .addViolation("test violation")
            .build();
        String str = v.toString();
        assertTrue(str.contains("DIRTY"));
        assertTrue(str.contains("violations=1"));
    }
}
