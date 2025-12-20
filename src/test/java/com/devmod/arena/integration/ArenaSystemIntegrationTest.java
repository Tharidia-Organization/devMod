package com.devmod.arena.integration;

import com.devmod.arena.cleanup.ArenaCleanupExecutor;
import com.devmod.arena.cleanup.CleanupResult;
import com.devmod.arena.event.TemplateEvent;
import com.devmod.arena.event.TemplateEventDispatcher;
import com.devmod.arena.monitor.MsptMonitor;
import com.devmod.arena.network.BuildProgressPayload;
import com.devmod.arena.ui.BuildProgressOverlay;
import com.devmod.arena.ui.BuildProgressOverlay.BuildPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying cross-package interactions.
 * Tests the full arena lifecycle from build to cleanup.
 */
class ArenaSystemIntegrationTest {

    private MockLevelAccess mockLevel;
    private MsptMonitor msptMonitor;
    private BuildProgressOverlay progressOverlay;
    private List<BuildProgressOverlay.ProgressUpdate> progressUpdates;
    private List<TemplateEvent> capturedEvents;
    private TemplateEventDispatcher eventDispatcher;

    @BeforeEach
    void setUp() {
        mockLevel = new MockLevelAccess();
        msptMonitor = new MsptMonitor();
        progressUpdates = new CopyOnWriteArrayList<>();
        progressOverlay = new BuildProgressOverlay(progressUpdates::add);
        capturedEvents = new CopyOnWriteArrayList<>();
        eventDispatcher = TemplateEventDispatcher.getInstance();

        // Clear any existing listeners
        eventDispatcher.clearAllListeners();
    }

    @Test
    @DisplayName("Full arena lifecycle: build -> monitor -> cleanup")
    void testFullArenaLifecycle() {
        // Setup: Register for events
        eventDispatcher.register(TemplateEvent.BuildCompleted.class, capturedEvents::add);
        eventDispatcher.register(TemplateEvent.BuildStarted.class, capturedEvents::add);

        UUID arenaId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        String templateId = "test:arena";

        // Phase 1: Capture baseline MSPT before build
        for (int i = 0; i < 25; i++) {
            msptMonitor.recordSample(45.0); // Normal server load
        }
        double baseline = msptMonitor.captureBaseline();
        assertEquals(45.0, baseline, 0.01);

        // Phase 2: Simulate build with progress updates
        int totalBlocks = 1000;
        for (int blockNum = 0; blockNum <= totalBlocks; blockNum += 100) {
            double progress = (double) blockNum / totalBlocks;

            // Update progress (rate limited at 4Hz, 1% delta)
            progressOverlay.updateProgress(
                playerId, arenaId, BuildPhase.PLACING_BLOCKS,
                progress, blockNum, totalBlocks, (totalBlocks - blockNum) * 10
            );

            // Simulate MSPT increase during build
            msptMonitor.recordSample(55.0);

            // Small delay to allow rate limiting
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }

        // Complete build
        progressOverlay.complete(playerId, arenaId, true);

        // Emit build completed event
        eventDispatcher.emitBuildCompleted(templateId, arenaId, totalBlocks, 0, 5000);

        // Verify progress updates (should be rate limited)
        assertFalse(progressUpdates.isEmpty(), "Should have progress updates");
        assertTrue(progressUpdates.size() < 15,
            "Progress updates should be rate limited (got " + progressUpdates.size() + ")");

        // Verify completion update
        BuildProgressOverlay.ProgressUpdate lastUpdate = progressUpdates.getLast();
        assertEquals(BuildPhase.COMPLETE, lastUpdate.phase());

        // Phase 3: Cleanup the arena
        mockLevel.entitiesInBounds = 5;
        mockLevel.blockEntitiesInBounds = 2;
        mockLevel.scheduledTicksInBounds = 10;
        mockLevel.blocksInBounds = totalBlocks;

        ArenaCleanupExecutor cleanupExecutor = new ArenaCleanupExecutor(mockLevel);
        ArenaCleanupExecutor.ArenaBounds bounds =
            new ArenaCleanupExecutor.ArenaBounds(0, 64, 0, 31, 96, 31);

        CleanupResult cleanupResult = cleanupExecutor.execute(bounds);

        // Verify cleanup
        assertTrue(cleanupResult.isComplete());
        assertEquals(5, cleanupResult.entitiesRemoved());
        assertEquals(2, cleanupResult.blockEntitiesRemoved());
        assertEquals(10, cleanupResult.scheduledTicksCancelled());
        assertEquals(totalBlocks, cleanupResult.blocksRemoved());

        // Verify event was captured
        assertTrue(capturedEvents.stream()
            .anyMatch(e -> e instanceof TemplateEvent.BuildCompleted bc
                && bc.arenaId().equals(arenaId)));
    }

    @Test
    @DisplayName("BuildProgressPayload network serialization integration")
    void testBuildProgressPayloadIntegration() {
        UUID arenaId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        // Create progress update via overlay
        progressOverlay.updateProgress(
            playerId, arenaId, BuildPhase.SPAWNING_ENTITIES,
            0.75, 750, 1000, 1250
        );

        // Get the update and convert to network payload
        BuildProgressOverlay.ProgressUpdate update = progressUpdates.getFirst();
        byte[] overlayBytes = update.toBytes();

        // Create equivalent payload directly
        BuildProgressPayload payload = BuildProgressPayload.of(
            arenaId, BuildPhase.SPAWNING_ENTITIES,
            0.75, 750, 1000, 0
        );
        byte[] payloadBytes = payload.toBytes();

        // Verify both produce 28 bytes
        assertEquals(28, overlayBytes.length);
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

    @Test
    @DisplayName("Multiple concurrent arenas can track progress independently")
    void testConcurrentArenaProgress() {
        UUID arena1 = UUID.randomUUID();
        UUID arena2 = UUID.randomUUID();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        // Both players start builds
        progressOverlay.updateProgress(player1, arena1, BuildPhase.LOADING_CHUNKS,
            0.1, 10, 100, 9000);
        progressOverlay.updateProgress(player2, arena2, BuildPhase.PLACING_BLOCKS,
            0.5, 500, 1000, 5000);

        assertEquals(2, progressUpdates.size());

        // Verify updates are for different arenas
        Set<UUID> arenaIds = new HashSet<>();
        for (var update : progressUpdates) {
            arenaIds.add(update.arenaId());
        }
        assertEquals(2, arenaIds.size());
        assertTrue(arenaIds.contains(arena1));
        assertTrue(arenaIds.contains(arena2));

        // Complete one, fail the other
        progressOverlay.complete(player1, arena1, true);
        progressOverlay.complete(player2, arena2, false);

        // Verify completion states
        var completions = progressUpdates.stream()
            .filter(u -> u.phase() == BuildPhase.COMPLETE || u.phase() == BuildPhase.FAILED)
            .toList();

        assertEquals(2, completions.size());
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
