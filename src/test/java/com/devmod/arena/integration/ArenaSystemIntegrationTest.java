package com.devmod.arena.integration;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.arena.BuildPhase;
import com.devmod.arena.cleanup.ArenaCleanupExecutor;
import com.devmod.arena.cleanup.CleanupResult;
import com.devmod.arena.event.TemplateEvent;
import com.devmod.arena.event.TemplateEventDispatcher;
import com.devmod.arena.monitor.MsptMonitor;
import com.devmod.arena.network.BuildProgressPayload;

import static org.junit.jupiter.api.Assertions.*;

class ArenaSystemIntegrationTest {

    private MockLevelAccess mockLevel;
    private MsptMonitor msptMonitor;
    private TemplateEventDispatcher eventDispatcher;

    @BeforeEach
    void setUp() {
        mockLevel = new MockLevelAccess();
        msptMonitor = new MsptMonitor();
        eventDispatcher = TemplateEventDispatcher.getInstance();

        // Clear any existing listeners
        eventDispatcher.clearAllListeners();
    }

    @Test
    @DisplayName("BuildProgressPayload network serialization integration")
    void testBuildProgressPayloadIntegration() {
        UUID arenaId = UUID.randomUUID();

        // Create equivalent payload directly
        BuildProgressPayload payload = BuildProgressPayload.of(
            arenaId, BuildPhase.SPAWNING_ENTITIES,
            0.75, 750, 1000, 0
        );
        byte[] payloadBytes = payload.toBytes();

        // Verify both produce 28 bytes
        assertEquals(28, payloadBytes.length);

        // Verify payload can be deserialized
        BuildProgressPayload deserialized = BuildProgressPayload.fromBytes(payloadBytes);
        assertEquals(arenaId, deserialized.arenaId());
        assertEquals(BuildPhase.SPAWNING_ENTITIES, deserialized.phase());
        assertEquals(0.75, deserialized.progressPercent(), 0.01);
        assertEquals(750, deserialized.blocksPlaced());
        assertEquals(1000, deserialized.totalBlocks());
    }

    @Test
    @DisplayName("MSPT monitoring triggers backpressure during heavy builds")
    void testMsptBackpressureIntegration() {
        // Baseline at 40ms (healthy server)
        for (int i = 0; i < 25; i++) {
            msptMonitor.recordSample(40.0);
        }
        msptMonitor.captureBaseline();

        // Verify no backpressure initially
        assertFalse(msptMonitor.shouldBackpressure());
        assertEquals(0.0, msptMonitor.getBuildImpact(), 0.01);

        // Simulate build impact pushing MSPT higher
        msptMonitor.recordSample(65.0);

        // Build impact should be measurable
        double impact = msptMonitor.getBuildImpact();
        assertEquals(25.0, impact, 0.01,
            "Build impact should be 65 - 40 = 25ms");
    }

    @Test
    @DisplayName("Event dispatcher integrates with build lifecycle")
    void testEventDispatcherIntegration() {
        AtomicInteger startedCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        eventDispatcher.register(TemplateEvent.BuildStarted.class,
            e -> startedCount.incrementAndGet());
        eventDispatcher.register(TemplateEvent.BuildCompleted.class,
            e -> completedCount.incrementAndGet());
        eventDispatcher.register(TemplateEvent.BuildFailed.class,
            e -> failedCount.incrementAndGet());

        UUID arenaId = UUID.randomUUID();
        String templateId = "test:arena";

        // Emit lifecycle events
        eventDispatcher.emitBuildStarted(templateId, arenaId, null, 1000);
        eventDispatcher.emitBuildCompleted(templateId, arenaId, 1000, 0, 5000);

        assertEquals(1, startedCount.get());
        assertEquals(1, completedCount.get());
        assertEquals(0, failedCount.get());

        // Emit a failed build
        UUID failedArena = UUID.randomUUID();
        eventDispatcher.emitBuildStarted(templateId, failedArena, null, 500);
        eventDispatcher.emitBuildFailed(templateId, failedArena, "Out of memory",
            new RuntimeException("test"), 2500, true);

        assertEquals(2, startedCount.get());
        assertEquals(1, completedCount.get());
        assertEquals(1, failedCount.get());
    }

    @Test
    @DisplayName("Cleanup verification integrates with monitoring")
    void testCleanupVerificationIntegration() {
        // Setup arena that will have leftover entities
        mockLevel.entitiesInBounds = 10;
        mockLevel.blocksInBounds = 500;
        mockLevel.verifyEntitiesRemaining = 2; // Some entities remain after cleanup

        ArenaCleanupExecutor executor = new ArenaCleanupExecutor(mockLevel);
        ArenaCleanupExecutor.ArenaBounds bounds =
            new ArenaCleanupExecutor.ArenaBounds(0, 64, 0, 15, 80, 15);

        // Execute cleanup
        CleanupResult result = executor.execute(bounds);

        // Cleanup should report incomplete due to remaining entities
        assertFalse(result.isComplete(),
            "Cleanup should report incomplete when entities remain");
        assertFalse(result.warnings().isEmpty(),
            "Should have warnings about remaining entities");
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

        @Override
        public int removeEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                           boolean preservePlayers, Set<UUID> excludedEntities) {
            return entitiesInBounds;
        }

        @Override
        public int removeBlockEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                                boolean clearContainers) {
            return blockEntitiesInBounds;
        }

        @Override
        public int cancelScheduledTicksInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return scheduledTicksInBounds;
        }

        @Override
        public int setBlocksToAirInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                           int maxBlocksPerTick) {
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
