package com.frenkvs.devmod.stress;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L5 - Stress, Performance, and Soak Tests
 *
 * PURPOSE: Validate stability under extended load and verify memory cleanup
 *
 * Test Categories:
 * 1. Memory Leak Detection - Verify cleanup services prevent unbounded growth
 * 2. Soak Tests - Extended operation simulation
 * 3. Resource Exhaustion - System behavior at limits
 * 4. Cleanup Verification - Manager shutdown correctness
 *
 * PASS CRITERIA:
 * - No unbounded memory growth after cleanup cycles
 * - All cleanup operations remove tracked data
 * - Shutdown methods release all resources
 * - No stale references after cleanup
 */
@DisplayName("L5: Stress, Performance & Soak Tests")
public class L5MemoryAndSoakTest {

    // =========================================================================
    // SIMULATED MEMORY CLEANUP SERVICE (mirrors MemoryCleanupService behavior)
    // =========================================================================

    static class SimMemoryCleanupService {
        private final Map<UUID, Long> entityLastSeen = new ConcurrentHashMap<>();
        private final Map<UUID, SimEntityData> entityData = new ConcurrentHashMap<>();
        private final AtomicLong totalCleaned = new AtomicLong(0);
        private final long staleThresholdMs;
        private final int maxEntries;

        SimMemoryCleanupService(long staleThresholdMs, int maxEntries) {
            this.staleThresholdMs = staleThresholdMs;
            this.maxEntries = maxEntries;
        }

        void markEntitySeen(UUID entityId) {
            entityLastSeen.put(entityId, System.currentTimeMillis());
        }

        void addEntityData(UUID entityId, SimEntityData data) {
            entityData.put(entityId, data);
            markEntitySeen(entityId);
        }

        void markEntityRemoved(UUID entityId) {
            entityLastSeen.remove(entityId);
            entityData.remove(entityId);
        }

        long performCleanup() {
            long now = System.currentTimeMillis();
            long threshold = now - staleThresholdMs;
            long count = 0;

            // Remove stale entities
            Iterator<Map.Entry<UUID, Long>> iter = entityLastSeen.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<UUID, Long> entry = iter.next();
                if (entry.getValue() < threshold) {
                    entityData.remove(entry.getKey());
                    iter.remove();
                    count++;
                }
            }

            // Trim if over limit
            if (entityLastSeen.size() > maxEntries) {
                List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(entityLastSeen.entrySet());
                sorted.sort(Comparator.comparingLong(Map.Entry::getValue));

                int toRemove = entityLastSeen.size() - maxEntries;
                for (int i = 0; i < toRemove && i < sorted.size(); i++) {
                    UUID id = sorted.get(i).getKey();
                    entityLastSeen.remove(id);
                    entityData.remove(id);
                    count++;
                }
            }

            totalCleaned.addAndGet(count);
            return count;
        }

        void clearAll() {
            entityLastSeen.clear();
            entityData.clear();
        }

        int getTrackedCount() { return entityLastSeen.size(); }
        int getDataCount() { return entityData.size(); }
        long getTotalCleaned() { return totalCleaned.get(); }
    }

    static class SimEntityData {
        final UUID id;
        final byte[] payload; // Simulates memory allocation

        SimEntityData(UUID id, int payloadSize) {
            this.id = id;
            this.payload = new byte[payloadSize];
        }
    }

    // =========================================================================
    // SIMULATED INSTANCE MANAGER (mirrors InstanceManager behavior)
    // =========================================================================

    static class SimInstanceManager {
        private final Map<UUID, SimInstance> instances = new ConcurrentHashMap<>();
        private final Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
        private final Map<UUID, SimPendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
        private final AtomicBoolean initialized = new AtomicBoolean(false);
        private final AtomicInteger instancesCreated = new AtomicInteger(0);
        private final AtomicInteger instancesDestroyed = new AtomicInteger(0);

        void initialize() {
            initialized.set(true);
        }

        void shutdown() {
            // Cancel pending teleports
            pendingTeleports.clear();

            // Destroy all instances
            for (UUID instanceId : new ArrayList<>(instances.keySet())) {
                destroyInstance(instanceId);
            }

            instances.clear();
            playerToInstance.clear();
            initialized.set(false);
        }

        UUID createInstance(UUID playerId, String arenaId) {
            if (!initialized.get()) return null;

            UUID instanceId = UUID.randomUUID();
            SimInstance instance = new SimInstance(instanceId, arenaId, playerId);
            instances.put(instanceId, instance);
            playerToInstance.put(playerId, instanceId);
            instancesCreated.incrementAndGet();
            return instanceId;
        }

        boolean destroyInstance(UUID instanceId) {
            SimInstance instance = instances.remove(instanceId);
            if (instance == null) return false;

            // Clean up player mappings
            playerToInstance.entrySet().removeIf(e -> e.getValue().equals(instanceId));

            // Clean up any pending teleports for this instance
            pendingTeleports.entrySet().removeIf(e -> e.getValue().instanceId.equals(instanceId));

            instancesDestroyed.incrementAndGet();
            return true;
        }

        void addPendingTeleport(UUID playerId, UUID instanceId) {
            pendingTeleports.put(playerId, new SimPendingTeleport(playerId, instanceId));
        }

        void completeTeleport(UUID playerId) {
            pendingTeleports.remove(playerId);
        }

        int getInstanceCount() { return instances.size(); }
        int getPlayerMappingCount() { return playerToInstance.size(); }
        int getPendingTeleportCount() { return pendingTeleports.size(); }
        int getTotalCreated() { return instancesCreated.get(); }
        int getTotalDestroyed() { return instancesDestroyed.get(); }
        boolean isInitialized() { return initialized.get(); }
    }

    static class SimInstance {
        final UUID id;
        final String arenaId;
        final UUID ownerId;
        final Set<UUID> players = ConcurrentHashMap.newKeySet();
        final long createdAt = System.currentTimeMillis();

        SimInstance(UUID id, String arenaId, UUID ownerId) {
            this.id = id;
            this.arenaId = arenaId;
            this.ownerId = ownerId;
            this.players.add(ownerId);
        }
    }

    static class SimPendingTeleport {
        final UUID playerId;
        final UUID instanceId;
        final long createdAt = System.currentTimeMillis();

        SimPendingTeleport(UUID playerId, UUID instanceId) {
            this.playerId = playerId;
            this.instanceId = instanceId;
        }
    }

    // =========================================================================
    // L5-01: MEMORY LEAK DETECTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("L5-01: Memory Leak Detection")
    class MemoryLeakTests {

        @Test
        @DisplayName("Cleanup prevents unbounded entity growth")
        @Timeout(30)
        void cleanupPreventsUnboundedGrowth() {
            SimMemoryCleanupService cleanup = new SimMemoryCleanupService(100, 1000);

            // Add entities in waves
            for (int wave = 0; wave < 10; wave++) {
                // Add 500 entities per wave
                for (int i = 0; i < 500; i++) {
                    UUID id = UUID.randomUUID();
                    cleanup.addEntityData(id, new SimEntityData(id, 1024)); // 1KB each
                }

                // Simulate time passing (make entities stale)
                try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                // Run cleanup
                cleanup.performCleanup();

                // After cleanup, should be bounded
                assertTrue(cleanup.getTrackedCount() <= 1000,
                    "Tracked count should be bounded: " + cleanup.getTrackedCount());
            }

            // Final cleanup should have processed entries
            assertTrue(cleanup.getTotalCleaned() > 0, "Should have cleaned some entries");
        }

        @Test
        @DisplayName("Entity removal cleans up all associated data")
        void entityRemovalCleansAllData() {
            SimMemoryCleanupService cleanup = new SimMemoryCleanupService(5000, 10000);

            // Add entities
            List<UUID> entities = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                UUID id = UUID.randomUUID();
                entities.add(id);
                cleanup.addEntityData(id, new SimEntityData(id, 512));
            }

            assertEquals(100, cleanup.getTrackedCount());
            assertEquals(100, cleanup.getDataCount());

            // Remove half
            for (int i = 0; i < 50; i++) {
                cleanup.markEntityRemoved(entities.get(i));
            }

            assertEquals(50, cleanup.getTrackedCount(), "Tracked count should decrease");
            assertEquals(50, cleanup.getDataCount(), "Data count should decrease");
        }

        @Test
        @DisplayName("ClearAll removes all tracked data")
        void clearAllRemovesAllData() {
            SimMemoryCleanupService cleanup = new SimMemoryCleanupService(5000, 10000);

            // Add many entities
            for (int i = 0; i < 1000; i++) {
                UUID id = UUID.randomUUID();
                cleanup.addEntityData(id, new SimEntityData(id, 256));
            }

            assertEquals(1000, cleanup.getTrackedCount());

            // Clear all
            cleanup.clearAll();

            assertEquals(0, cleanup.getTrackedCount(), "All entities should be cleared");
            assertEquals(0, cleanup.getDataCount(), "All data should be cleared");
        }

        @Test
        @DisplayName("Max entries limit is enforced")
        void maxEntriesLimitEnforced() {
            int maxEntries = 500;
            SimMemoryCleanupService cleanup = new SimMemoryCleanupService(Long.MAX_VALUE, maxEntries);

            // Add more than max
            for (int i = 0; i < 1000; i++) {
                UUID id = UUID.randomUUID();
                cleanup.addEntityData(id, new SimEntityData(id, 128));
            }

            // Cleanup should trim to max
            cleanup.performCleanup();

            assertTrue(cleanup.getTrackedCount() <= maxEntries,
                "Should be trimmed to max: " + cleanup.getTrackedCount());
        }
    }

    // =========================================================================
    // L5-02: SOAK TESTS (Extended Operation)
    // =========================================================================

    @Nested
    @DisplayName("L5-02: Soak Tests")
    class SoakTests {

        @Test
        @DisplayName("Extended instance lifecycle (1000 create-destroy cycles)")
        @Timeout(60)
        void extendedInstanceLifecycle() {
            SimInstanceManager manager = new SimInstanceManager();
            manager.initialize();

            int cycles = 1000;
            int concurrentInstances = 10;

            for (int cycle = 0; cycle < cycles; cycle++) {
                // Create batch of instances
                List<UUID> instanceIds = new ArrayList<>();
                for (int i = 0; i < concurrentInstances; i++) {
                    UUID playerId = UUID.randomUUID();
                    UUID instanceId = manager.createInstance(playerId, "arena_" + i);
                    assertNotNull(instanceId);
                    instanceIds.add(instanceId);
                }

                assertEquals(concurrentInstances, manager.getInstanceCount());

                // Destroy all
                for (UUID instanceId : instanceIds) {
                    assertTrue(manager.destroyInstance(instanceId));
                }

                assertEquals(0, manager.getInstanceCount(), "All instances should be destroyed");
                assertEquals(0, manager.getPlayerMappingCount(), "Player mappings should be cleared");
            }

            assertEquals(cycles * concurrentInstances, manager.getTotalCreated());
            assertEquals(cycles * concurrentInstances, manager.getTotalDestroyed());

            manager.shutdown();
        }

        @Test
        @DisplayName("Memory cleanup over extended session (100 cleanup cycles)")
        @Timeout(30)
        void extendedCleanupSession() {
            SimMemoryCleanupService cleanup = new SimMemoryCleanupService(50, 500);

            AtomicLong totalAdded = new AtomicLong(0);

            // Simulate 100 cleanup cycles with continuous entity addition
            for (int cycle = 0; cycle < 100; cycle++) {
                // Add some entities
                int toAdd = ThreadLocalRandom.current().nextInt(10, 50);
                for (int i = 0; i < toAdd; i++) {
                    UUID id = UUID.randomUUID();
                    cleanup.addEntityData(id, new SimEntityData(id, 512));
                    totalAdded.incrementAndGet();
                }

                // Small delay to make some entities stale
                try { Thread.sleep(60); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                // Perform cleanup
                cleanup.performCleanup();

                // Memory should stay bounded
                assertTrue(cleanup.getTrackedCount() <= 600, // Some buffer
                    "Cycle " + cycle + ": Tracked count unbounded: " + cleanup.getTrackedCount());
            }

            // Should have cleaned a significant portion
            assertTrue(cleanup.getTotalCleaned() > totalAdded.get() / 2,
                "Should have cleaned entries: " + cleanup.getTotalCleaned() + " of " + totalAdded.get());
        }

        @RepeatedTest(5)
        @DisplayName("Rapid create-destroy cycles don't leak")
        @Timeout(10)
        void rapidCreateDestroyCycles() {
            SimInstanceManager manager = new SimInstanceManager();
            manager.initialize();

            int rapidCycles = 500;

            for (int i = 0; i < rapidCycles; i++) {
                UUID playerId = UUID.randomUUID();
                UUID instanceId = manager.createInstance(playerId, "rapid_arena");
                manager.destroyInstance(instanceId);
            }

            assertEquals(0, manager.getInstanceCount(), "No instances should remain");
            assertEquals(0, manager.getPlayerMappingCount(), "No player mappings should remain");
            assertEquals(rapidCycles, manager.getTotalCreated());
            assertEquals(rapidCycles, manager.getTotalDestroyed());

            manager.shutdown();
        }
    }

    // =========================================================================
    // L5-03: RESOURCE EXHAUSTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("L5-03: Resource Exhaustion")
    class ResourceExhaustionTests {

        @Test
        @DisplayName("Handle 10000 concurrent player mappings")
        @Timeout(30)
        void handleManyPlayerMappings() {
            SimInstanceManager manager = new SimInstanceManager();
            manager.initialize();

            int playerCount = 10000;
            Map<UUID, UUID> createdMappings = new ConcurrentHashMap<>();

            // Create many instances (one per player)
            for (int i = 0; i < playerCount; i++) {
                UUID playerId = UUID.randomUUID();
                UUID instanceId = manager.createInstance(playerId, "stress_arena");
                createdMappings.put(playerId, instanceId);
            }

            assertEquals(playerCount, manager.getInstanceCount());
            assertEquals(playerCount, manager.getPlayerMappingCount());

            // Destroy all
            for (UUID instanceId : createdMappings.values()) {
                manager.destroyInstance(instanceId);
            }

            assertEquals(0, manager.getInstanceCount());
            assertEquals(0, manager.getPlayerMappingCount());

            manager.shutdown();
        }

        @Test
        @DisplayName("Handle pending teleport queue overflow")
        @Timeout(10)
        void handlePendingTeleportQueueOverflow() {
            SimInstanceManager manager = new SimInstanceManager();
            manager.initialize();

            UUID instanceId = manager.createInstance(UUID.randomUUID(), "teleport_test");

            // Add many pending teleports
            int teleportCount = 5000;
            List<UUID> players = new ArrayList<>();

            for (int i = 0; i < teleportCount; i++) {
                UUID playerId = UUID.randomUUID();
                players.add(playerId);
                manager.addPendingTeleport(playerId, instanceId);
            }

            assertEquals(teleportCount, manager.getPendingTeleportCount());

            // Complete half
            for (int i = 0; i < teleportCount / 2; i++) {
                manager.completeTeleport(players.get(i));
            }

            assertEquals(teleportCount / 2, manager.getPendingTeleportCount());

            // Shutdown should clear all
            manager.shutdown();

            assertEquals(0, manager.getPendingTeleportCount());
        }

        @Test
        @DisplayName("Concurrent stress: 8 threads, 1000 ops each")
        @Timeout(30)
        void concurrentStress() throws InterruptedException {
            SimInstanceManager manager = new SimInstanceManager();
            manager.initialize();

            int threadCount = 8;
            int opsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            UUID playerId = UUID.randomUUID();
                            UUID instanceId = manager.createInstance(playerId, "concurrent_arena");
                            if (instanceId != null) {
                                manager.destroyInstance(instanceId);
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(25, TimeUnit.SECONDS), "All threads should complete");
            executor.shutdown();

            assertEquals(0, errors.get(), "No errors during concurrent operations");
            assertEquals(0, manager.getInstanceCount(), "No instances should remain");

            manager.shutdown();
        }
    }

    // =========================================================================
    // L5-04: CLEANUP VERIFICATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("L5-04: Cleanup Verification")
    class CleanupVerificationTests {

        @Test
        @DisplayName("Manager shutdown releases all resources")
        void shutdownReleasesAllResources() {
            SimInstanceManager manager = new SimInstanceManager();
            manager.initialize();

            // Create various resources
            for (int i = 0; i < 50; i++) {
                UUID playerId = UUID.randomUUID();
                UUID instanceId = manager.createInstance(playerId, "shutdown_test");
                manager.addPendingTeleport(UUID.randomUUID(), instanceId);
            }

            assertTrue(manager.getInstanceCount() > 0);
            assertTrue(manager.getPendingTeleportCount() > 0);
            assertTrue(manager.isInitialized());

            // Shutdown
            manager.shutdown();

            assertEquals(0, manager.getInstanceCount(), "Instances should be cleared");
            assertEquals(0, manager.getPlayerMappingCount(), "Player mappings should be cleared");
            assertEquals(0, manager.getPendingTeleportCount(), "Pending teleports should be cleared");
            assertFalse(manager.isInitialized(), "Should be deinitialized");
        }

        @Test
        @DisplayName("Cleanup statistics are accurate")
        void cleanupStatisticsAccurate() {
            SimMemoryCleanupService cleanup = new SimMemoryCleanupService(1, 100); // 1ms stale, max 100

            // Add entities
            int added = 200;
            for (int i = 0; i < added; i++) {
                UUID id = UUID.randomUUID();
                cleanup.addEntityData(id, new SimEntityData(id, 64));
            }

            // Wait for all to become stale
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // Cleanup should remove all
            long cleaned = cleanup.performCleanup();

            // Should have cleaned most entries
            assertTrue(cleaned >= added - 100, // May keep up to max
                "Should clean most entries: cleaned " + cleaned + " of " + added);
            assertTrue(cleanup.getTotalCleaned() > 0, "Total cleaned should be tracked");
        }

        @Test
        @DisplayName("No stale references after multiple cleanup cycles")
        void noStaleReferencesAfterCleanup() {
            SimMemoryCleanupService cleanup = new SimMemoryCleanupService(10, 500);

            Set<UUID> allCreated = ConcurrentHashMap.newKeySet();

            // Multiple cycles
            for (int cycle = 0; cycle < 20; cycle++) {
                // Add entities
                for (int i = 0; i < 100; i++) {
                    UUID id = UUID.randomUUID();
                    allCreated.add(id);
                    cleanup.addEntityData(id, new SimEntityData(id, 128));
                }

                // Manually remove some
                int removed = 0;
                for (UUID id : new ArrayList<>(allCreated)) {
                    if (removed >= 30) break;
                    cleanup.markEntityRemoved(id);
                    allCreated.remove(id);
                    removed++;
                }

                // Small delay
                try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                // Cleanup
                cleanup.performCleanup();
            }

            // Verify tracked count matches data count
            assertEquals(cleanup.getTrackedCount(), cleanup.getDataCount(),
                "Tracked count should equal data count - no orphaned data");
        }

        @Test
        @DisplayName("Initialize/Shutdown cycle can be repeated")
        void initShutdownCycleRepeatable() {
            SimInstanceManager manager = new SimInstanceManager();

            for (int cycle = 0; cycle < 10; cycle++) {
                // Initialize
                manager.initialize();
                assertTrue(manager.isInitialized(), "Cycle " + cycle + ": should be initialized");

                // Create some resources
                for (int i = 0; i < 20; i++) {
                    manager.createInstance(UUID.randomUUID(), "cycle_" + cycle);
                }

                assertEquals(20, manager.getInstanceCount());

                // Shutdown
                manager.shutdown();
                assertFalse(manager.isInitialized(), "Cycle " + cycle + ": should be shut down");
                assertEquals(0, manager.getInstanceCount(), "Cycle " + cycle + ": instances should be cleared");
            }
        }
    }

    // =========================================================================
    // L5-05: WAVE/QUEST EXTENDED SIMULATION
    // =========================================================================

    @Nested
    @DisplayName("L5-05: Extended Quest Simulation")
    class ExtendedQuestSimulationTests {

        static class SimQuestSession {
            final UUID playerId;
            int currentWave = 0;
            int totalKills = 0;
            int totalDeaths = 0;
            long startTime = System.currentTimeMillis();
            boolean completed = false;
            boolean failed = false;

            SimQuestSession(UUID playerId) {
                this.playerId = playerId;
            }

            void advanceWave() {
                currentWave++;
            }

            void recordKill() {
                totalKills++;
            }

            void recordDeath() {
                totalDeaths++;
            }

            void complete() {
                completed = true;
            }

            void fail() {
                failed = true;
            }

            long getDurationMs() {
                return System.currentTimeMillis() - startTime;
            }
        }

        @Test
        @DisplayName("Simulate 100-wave endless quest")
        @Timeout(30)
        void simulate100WaveEndlessQuest() {
            SimQuestSession session = new SimQuestSession(UUID.randomUUID());
            Random random = new Random();

            int targetWaves = 100;

            for (int wave = 1; wave <= targetWaves; wave++) {
                session.advanceWave();
                assertEquals(wave, session.currentWave);

                // Simulate kills per wave (increasing with wave)
                int killsThisWave = 5 + wave / 10;
                for (int k = 0; k < killsThisWave; k++) {
                    session.recordKill();
                }

                // Occasional death
                if (random.nextInt(10) == 0) {
                    session.recordDeath();
                }
            }

            assertEquals(targetWaves, session.currentWave);
            assertTrue(session.totalKills > 500, "Should have many kills: " + session.totalKills);
            assertTrue(session.totalDeaths < 20, "Should have few deaths: " + session.totalDeaths);

            session.complete();
            assertTrue(session.completed);
        }

        @Test
        @DisplayName("Multiple concurrent quest sessions")
        @Timeout(30)
        void multipleConcurrentQuestSessions() throws InterruptedException {
            int sessionCount = 50;
            int wavesPerSession = 20;

            Map<UUID, SimQuestSession> sessions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(sessionCount);

            for (int s = 0; s < sessionCount; s++) {
                executor.submit(() -> {
                    try {
                        UUID playerId = UUID.randomUUID();
                        SimQuestSession session = new SimQuestSession(playerId);
                        sessions.put(playerId, session);

                        Random random = new Random();
                        for (int wave = 0; wave < wavesPerSession; wave++) {
                            session.advanceWave();

                            // Simulate wave gameplay
                            int kills = random.nextInt(10) + 5;
                            for (int k = 0; k < kills; k++) {
                                session.recordKill();
                            }

                            // Small delay to simulate gameplay
                            Thread.sleep(5);
                        }

                        session.complete();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(25, TimeUnit.SECONDS), "All sessions should complete");
            executor.shutdown();

            assertEquals(sessionCount, sessions.size());

            // Verify all sessions completed correctly
            for (SimQuestSession session : sessions.values()) {
                assertTrue(session.completed, "Session should be completed");
                assertEquals(wavesPerSession, session.currentWave);
                assertTrue(session.totalKills > 0, "Should have kills");
            }
        }

        @Test
        @DisplayName("Quest session cleanup after completion")
        void questSessionCleanupAfterCompletion() {
            Map<UUID, SimQuestSession> activeSessions = new ConcurrentHashMap<>();

            // Create sessions
            for (int i = 0; i < 100; i++) {
                UUID playerId = UUID.randomUUID();
                activeSessions.put(playerId, new SimQuestSession(playerId));
            }

            assertEquals(100, activeSessions.size());

            // Complete and clean up
            for (UUID playerId : new ArrayList<>(activeSessions.keySet())) {
                SimQuestSession session = activeSessions.get(playerId);
                session.complete();
                activeSessions.remove(playerId);
            }

            assertEquals(0, activeSessions.size(), "All sessions should be cleaned up");
        }
    }
}
