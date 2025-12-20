package com.devmod.arena.cleanup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ArenaCleanupExecutor.
 * DD37: Verifies 4-phase cleanup order and result tracking.
 */
class ArenaCleanupExecutorTest {

    private MockLevelAccess mockLevel;
    private ArenaCleanupExecutor executor;
    private ArenaCleanupExecutor.ArenaBounds testBounds;

    @BeforeEach
    void setUp() {
        mockLevel = new MockLevelAccess();
        executor = new ArenaCleanupExecutor(mockLevel);
        testBounds = new ArenaCleanupExecutor.ArenaBounds(0, 64, 0, 100, 128, 100);
    }

    @Test
    @DisplayName("DD37: Cleanup executes 4 phases in correct order")
    void testCleanupPhaseOrder() {
        // Given
        mockLevel.entitiesInBounds = 10;
        mockLevel.blockEntitiesInBounds = 5;
        mockLevel.scheduledTicksInBounds = 20;
        mockLevel.blocksInBounds = 1000;

        // When
        CleanupResult result = executor.execute(testBounds);

        // Then
        assertTrue(result.isComplete(), "Cleanup should complete successfully");
        assertEquals(4, mockLevel.phaseOrder.size(), "All 4 phases should execute");

        // Verify order: ENTITIES -> BLOCK_ENTITIES -> SCHEDULED_TICKS -> BLOCKS
        assertEquals("entities", mockLevel.phaseOrder.get(0));
        assertEquals("blockEntities", mockLevel.phaseOrder.get(1));
        assertEquals("scheduledTicks", mockLevel.phaseOrder.get(2));
        assertEquals("blocks", mockLevel.phaseOrder.get(3));
    }

    @Test
    @DisplayName("CleanupResult tracks all counters correctly")
    void testCleanupResultCounters() {
        // Given
        mockLevel.entitiesInBounds = 15;
        mockLevel.blockEntitiesInBounds = 8;
        mockLevel.scheduledTicksInBounds = 30;
        mockLevel.blocksInBounds = 5000;

        // When
        CleanupResult result = executor.execute(testBounds);

        // Then
        assertEquals(15, result.entitiesRemoved());
        assertEquals(8, result.blockEntitiesRemoved());
        assertEquals(30, result.scheduledTicksCancelled());
        assertEquals(5000, result.blocksRemoved());
    }

    @Test
    @DisplayName("CleanupResult.isComplete() returns true when no warnings")
    void testIsCompleteNoWarnings() {
        // Given clean level (all operations succeed, verification passes)
        mockLevel.entitiesInBounds = 5;
        mockLevel.blockEntitiesInBounds = 2;
        mockLevel.scheduledTicksInBounds = 0;
        mockLevel.blocksInBounds = 100;

        // When
        CleanupResult result = executor.execute(testBounds);

        // Then
        assertTrue(result.isComplete());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    @DisplayName("CleanupResult.isComplete() returns false with warnings")
    void testIsCompleteWithWarnings() {
        // Given level that fails verification
        mockLevel.entitiesInBounds = 5;
        mockLevel.verifyEntitiesRemaining = 2; // Some entities remain

        // When
        CleanupResult result = executor.execute(testBounds);

        // Then
        assertFalse(result.isComplete());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    @DisplayName("Cleanup clears Container.clearContent() before remove")
    void testContainerClearBeforeRemove() {
        // Given config with clearContainersBeforeRemove = true (default)
        mockLevel.blockEntitiesInBounds = 10;

        // When
        executor.execute(testBounds);

        // Then
        assertTrue(mockLevel.clearContainersCalled,
            "Containers should be cleared before removal");
    }

    @Test
    @DisplayName("Cleanup preserves players when configured")
    void testPreservePlayers() {
        // Given default config (preservePlayers = true)
        mockLevel.entitiesInBounds = 10;

        // When
        executor.execute(testBounds);

        // Then
        assertTrue(mockLevel.preservePlayersCalled,
            "Players should be preserved during cleanup");
    }

    @Test
    @DisplayName("Cleanup respects excluded entities")
    void testExcludedEntities() {
        // Given
        UUID excludedEntity = UUID.randomUUID();
        ArenaCleanupExecutor executorWithExclusions = new ArenaCleanupExecutor(
            ArenaCleanupExecutor.CleanupConfig.defaults(),
            Set.of(excludedEntity),
            mockLevel
        );
        mockLevel.entitiesInBounds = 10;

        // When
        executorWithExclusions.execute(testBounds);

        // Then
        assertTrue(mockLevel.excludedEntitiesReceived.contains(excludedEntity),
            "Excluded entity should be passed to level access");
    }

    @Test
    @DisplayName("ArenaBounds.volume() calculates correctly")
    void testBoundsVolume() {
        // Given: 10x10x10 cube
        ArenaCleanupExecutor.ArenaBounds bounds =
            new ArenaCleanupExecutor.ArenaBounds(0, 0, 0, 9, 9, 9);

        // When
        int volume = bounds.volume();

        // Then
        assertEquals(1000, volume, "10x10x10 = 1000 blocks");
    }

    @Test
    @DisplayName("Verification detects remaining entities")
    void testVerificationDetectsRemainingEntities() {
        // Given
        mockLevel.verifyEntitiesRemaining = 3;

        // When
        CleanupVerification verification = executor.verify(testBounds);

        // Then
        assertFalse(verification.isFullyClean());
        assertTrue(verification.violations().stream()
            .anyMatch(v -> v.contains("entities")));
    }

    @Test
    @DisplayName("Verification detects remaining block entities")
    void testVerificationDetectsRemainingBlockEntities() {
        // Given
        mockLevel.verifyBlockEntitiesRemaining = 2;

        // When
        CleanupVerification verification = executor.verify(testBounds);

        // Then
        assertFalse(verification.isFullyClean());
        assertTrue(verification.violations().stream()
            .anyMatch(v -> v.contains("block entities")));
    }

    @Test
    @DisplayName("Verification detects remaining blocks")
    void testVerificationDetectsRemainingBlocks() {
        // Given
        mockLevel.verifyBlocksRemaining = 100;

        // When
        CleanupVerification verification = executor.verify(testBounds);

        // Then
        assertFalse(verification.isFullyClean());
        assertTrue(verification.violations().stream()
            .anyMatch(v -> v.contains("blocks")));
    }

    // ========== Mock Implementation ==========

    private static class MockLevelAccess implements ArenaCleanupExecutor.LevelAccess {

        int entitiesInBounds = 0;
        int blockEntitiesInBounds = 0;
        int scheduledTicksInBounds = 0;
        int blocksInBounds = 0;

        int verifyEntitiesRemaining = 0;
        int verifyBlockEntitiesRemaining = 0;
        int verifyBlocksRemaining = 0;

        boolean clearContainersCalled = false;
        boolean preservePlayersCalled = false;
        Set<UUID> excludedEntitiesReceived = Set.of();

        java.util.List<String> phaseOrder = new java.util.ArrayList<>();

        @Override
        public int removeEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                           boolean preservePlayers, Set<UUID> excludedEntities) {
            phaseOrder.add("entities");
            preservePlayersCalled = preservePlayers;
            excludedEntitiesReceived = excludedEntities;
            return entitiesInBounds;
        }

        @Override
        public int removeBlockEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                                boolean clearContainers) {
            phaseOrder.add("blockEntities");
            clearContainersCalled = clearContainers;
            return blockEntitiesInBounds;
        }

        @Override
        public int cancelScheduledTicksInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            phaseOrder.add("scheduledTicks");
            return scheduledTicksInBounds;
        }

        @Override
        public int setBlocksToAirInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                           int maxBlocksPerTick) {
            phaseOrder.add("blocks");
            return blocksInBounds;
        }

        @Override
        public int countEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                          boolean excludePlayers, Set<UUID> excludedEntities) {
            return verifyEntitiesRemaining;
        }

        @Override
        public int countBlockEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return verifyBlockEntitiesRemaining;
        }

        @Override
        public int countNonAirBlocksInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return verifyBlocksRemaining;
        }
    }
}
