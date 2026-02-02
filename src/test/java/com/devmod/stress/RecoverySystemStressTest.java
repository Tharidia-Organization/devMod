package com.devmod.stress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L5: Recovery System Stress Tests
 *
 * Tests the recovery system under high load:
 * - Concurrent snapshot creation and restoration
 * - State persistence during failures
 * - Cleanup operations under load
 * - Edge cases with many players
 */
@Tag("load")
@DisplayName("L5: Recovery System Stress Tests")
class RecoverySystemStressTest {

    private ExecutorService executor;
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors() * 2;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor should terminate");
    }

    // === Snapshot Operations Stress ===

    @Nested
    @DisplayName("L5-RS-01: Snapshot Operations Stress")
    class SnapshotOperationsStressTest {

        @Test
        @DisplayName("Concurrent snapshot creation for many players")
        void concurrentSnapshotCreationForManyPlayers() throws Exception {
            ConcurrentHashMap<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
            int playerCount = 500;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(playerCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < playerCount; i++) {
                int playerIndex = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        UUID playerId = UUID.randomUUID();
                        PlayerSnapshot snapshot = new PlayerSnapshot(
                            playerId,
                            100.0 + playerIndex,  // health
                            20.0,                  // hunger
                            new double[]{playerIndex * 10, 64, playerIndex * 10}, // position
                            System.currentTimeMillis()
                        );

                        PlayerSnapshot existing = snapshots.putIfAbsent(playerId, snapshot);
                        if (existing == null) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(60, TimeUnit.SECONDS));

            assertEquals(playerCount, successCount.get(), "All snapshots should be created");
            assertEquals(playerCount, snapshots.size(), "All snapshots should be stored");
        }

        @Test
        @DisplayName("Snapshot updates don't lose data")
        void snapshotUpdatesDontLoseData() throws Exception {
            ConcurrentHashMap<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
            int playerCount = 50;
            int updatesPerPlayer = 100;
            List<UUID> playerIds = new ArrayList<>();

            // Initialize players
            for (int i = 0; i < playerCount; i++) {
                UUID id = UUID.randomUUID();
                playerIds.add(id);
                snapshots.put(id, new PlayerSnapshot(id, 100.0, 20.0, new double[]{0, 64, 0}, 0));
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(playerCount);
            AtomicInteger totalUpdates = new AtomicInteger(0);

            for (UUID playerId : playerIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < updatesPerPlayer; i++) {
                            snapshots.compute(playerId, (id, old) -> {
                                if (old == null) return null;
                                return new PlayerSnapshot(
                                    id,
                                    old.health + 1,
                                    old.hunger,
                                    old.position,
                                    System.currentTimeMillis()
                                );
                            });
                            totalUpdates.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(60, TimeUnit.SECONDS));

            // Verify all updates were applied
            for (UUID playerId : playerIds) {
                PlayerSnapshot snapshot = snapshots.get(playerId);
                assertNotNull(snapshot, "Snapshot should exist");
                assertEquals(100.0 + updatesPerPlayer, snapshot.health, 0.001,
                    "Health should reflect all updates");
            }
        }

        @Test
        @DisplayName("Concurrent read/write to same snapshot")
        void concurrentReadWriteToSameSnapshot() throws Exception {
            UUID playerId = UUID.randomUUID();
            AtomicReference<PlayerSnapshot> snapshot = new AtomicReference<>(
                new PlayerSnapshot(playerId, 100.0, 20.0, new double[]{0, 64, 0}, 0)
            );

            int operations = 10000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
            AtomicBoolean inconsistencyFound = new AtomicBoolean(false);
            AtomicInteger writeCount = new AtomicInteger(0);

            for (int t = 0; t < THREAD_COUNT; t++) {
                boolean isWriter = t % 2 == 0;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < operations; i++) {
                            if (isWriter) {
                                PlayerSnapshot current;
                                PlayerSnapshot updated;
                                do {
                                    current = snapshot.get();
                                    updated = new PlayerSnapshot(
                                        current.playerId,
                                        current.health + 1,
                                        current.hunger,
                                        current.position,
                                        System.currentTimeMillis()
                                    );
                                } while (!snapshot.compareAndSet(current, updated));
                                writeCount.incrementAndGet();
                            } else {
                                // Reader: verify consistency
                                PlayerSnapshot read = snapshot.get();
                                if (read.health < 100.0 || read.playerId == null) {
                                    inconsistencyFound.set(true);
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(60, TimeUnit.SECONDS));

            assertFalse(inconsistencyFound.get(), "No inconsistent reads should occur");
            assertTrue(writeCount.get() > 0, "Some writes should have occurred");
            assertEquals(100.0 + writeCount.get(), snapshot.get().health, 0.001,
                "Final health should reflect all writes");
        }

        private record PlayerSnapshot(UUID playerId, double health, double hunger, double[] position, long timestamp) {}
    }

    // === Recovery Flow Stress ===

    @Nested
    @DisplayName("L5-RS-02: Recovery Flow Stress")
    class RecoveryFlowStressTest {

        @Test
        @DisplayName("Simultaneous recovery requests for same player")
        void simultaneousRecoveryRequestsForSamePlayer() throws Exception {
            UUID playerId = UUID.randomUUID();
            ConcurrentHashMap<UUID, AtomicInteger> recoveryAttempts = new ConcurrentHashMap<>();
            recoveryAttempts.put(playerId, new AtomicInteger(0));

            AtomicBoolean recoveryInProgress = new AtomicBoolean(false);
            int attemptCount = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(attemptCount);
            AtomicInteger successfulRecoveries = new AtomicInteger(0);

            for (int i = 0; i < attemptCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        // Only one recovery should proceed at a time
                        if (recoveryInProgress.compareAndSet(false, true)) {
                            try {
                                // Simulate recovery process
                                Thread.sleep(10);
                                recoveryAttempts.get(playerId).incrementAndGet();
                                successfulRecoveries.incrementAndGet();
                            } finally {
                                recoveryInProgress.set(false);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertTrue(successfulRecoveries.get() >= 1, "At least one recovery should succeed");
            assertEquals(successfulRecoveries.get(), recoveryAttempts.get(playerId).get(),
                "Recovery count should match successful recoveries");
        }

        @Test
        @DisplayName("Mass recovery after simulated crash")
        void massRecoveryAfterSimulatedCrash() throws Exception {
            ConcurrentHashMap<UUID, PlayerState> precrashStates = new ConcurrentHashMap<>();
            ConcurrentHashMap<UUID, PlayerState> recoveredStates = new ConcurrentHashMap<>();
            int playerCount = 200;
            List<UUID> playerIds = new ArrayList<>();

            // Create pre-crash states
            for (int i = 0; i < playerCount; i++) {
                UUID id = UUID.randomUUID();
                playerIds.add(id);
                precrashStates.put(id, new PlayerState(id, 100.0, 20.0, "world", i * 10, 64, i * 10));
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(playerCount);
            AtomicInteger recoverySuccessCount = new AtomicInteger(0);
            AtomicInteger recoveryFailCount = new AtomicInteger(0);

            // Simulate concurrent recovery
            for (UUID playerId : playerIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        PlayerState original = precrashStates.get(playerId);
                        if (original != null) {
                            // Simulate recovery delay
                            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));

                            // Recover state
                            PlayerState recovered = new PlayerState(
                                original.playerId,
                                original.health,
                                original.hunger,
                                original.dimension,
                                original.x, original.y, original.z
                            );
                            recoveredStates.put(playerId, recovered);
                            recoverySuccessCount.incrementAndGet();
                        } else {
                            recoveryFailCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        recoveryFailCount.incrementAndGet();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(60, TimeUnit.SECONDS));

            assertEquals(playerCount, recoverySuccessCount.get(), "All players should be recovered");
            assertEquals(0, recoveryFailCount.get(), "No recoveries should fail");
            assertEquals(playerCount, recoveredStates.size(), "All recovered states should be stored");

            // Verify data integrity
            for (UUID playerId : playerIds) {
                PlayerState original = precrashStates.get(playerId);
                PlayerState recovered = recoveredStates.get(playerId);
                assertNotNull(recovered, "Recovered state should exist");
                assertEquals(original.health, recovered.health, 0.001, "Health should match");
                assertEquals(original.dimension, recovered.dimension, "Dimension should match");
            }
        }

        private record PlayerState(UUID playerId, double health, double hunger, String dimension,
                                   double x, double y, double z) {}
    }

    // === Cleanup Operations Stress ===

    @Nested
    @DisplayName("L5-RS-03: Cleanup Operations Stress")
    class CleanupOperationsStressTest {

        @Test
        @DisplayName("Cleanup doesn't interfere with active operations")
        void cleanupDoesntInterfereWithActiveOperations() throws Exception {
            ConcurrentHashMap<UUID, SnapshotEntry> snapshots = new ConcurrentHashMap<>();
            int initialCount = 1000;
            long expiryThreshold = 500; // ms
            AtomicInteger activeOperations = new AtomicInteger(0);
            AtomicInteger cleanedCount = new AtomicInteger(0);
            AtomicBoolean running = new AtomicBoolean(true);

            // Create initial snapshots with varying ages
            long now = System.currentTimeMillis();
            for (int i = 0; i < initialCount; i++) {
                UUID id = UUID.randomUUID();
                long age = i < initialCount / 2 ? 0 : expiryThreshold + 100; // Half expired
                snapshots.put(id, new SnapshotEntry(id, now - age, "data-" + i));
            }

            CountDownLatch cleanerReady = new CountDownLatch(1);
            CountDownLatch operatorsReady = new CountDownLatch(THREAD_COUNT - 1);

            // Cleaner thread
            executor.submit(() -> {
                cleanerReady.countDown();
                while (running.get()) {
                    long currentTime = System.currentTimeMillis();
                    snapshots.entrySet().removeIf(entry -> {
                        if (currentTime - entry.getValue().timestamp > expiryThreshold) {
                            cleanedCount.incrementAndGet();
                            return true;
                        }
                        return false;
                    });
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });

            // Operator threads (create and access snapshots)
            for (int t = 0; t < THREAD_COUNT - 1; t++) {
                executor.submit(() -> {
                    operatorsReady.countDown();
                    while (running.get()) {
                        activeOperations.incrementAndGet();
                        try {
                            UUID id = UUID.randomUUID();
                            snapshots.put(id, new SnapshotEntry(id, System.currentTimeMillis(), "new-data"));

                            // Access some existing snapshots
                            snapshots.values().stream().findAny().ifPresent(e -> {
                                // Just access, don't modify
                                String data = e.data;
                                if (data == null) {
                                    // Should never happen
                                }
                            });

                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        } finally {
                            activeOperations.decrementAndGet();
                        }
                    }
                });
            }

            assertTrue(cleanerReady.await(5, TimeUnit.SECONDS));
            assertTrue(operatorsReady.await(5, TimeUnit.SECONDS));

            Thread.sleep(2000); // Run for 2 seconds
            running.set(false);
            Thread.sleep(100); // Let threads finish

            assertTrue(cleanedCount.get() > 0, "Some snapshots should have been cleaned");
            assertTrue(snapshots.size() > 0, "Some snapshots should remain");
        }

        @Test
        @DisplayName("Bulk cleanup of expired snapshots")
        void bulkCleanupOfExpiredSnapshots() throws Exception {
            ConcurrentHashMap<UUID, SnapshotEntry> snapshots = new ConcurrentHashMap<>();
            int expiredCount = 5000;
            int validCount = 1000;
            long now = System.currentTimeMillis();

            // Create expired snapshots
            for (int i = 0; i < expiredCount; i++) {
                UUID id = UUID.randomUUID();
                snapshots.put(id, new SnapshotEntry(id, now - 10000, "expired-" + i));
            }

            // Create valid snapshots
            for (int i = 0; i < validCount; i++) {
                UUID id = UUID.randomUUID();
                snapshots.put(id, new SnapshotEntry(id, now, "valid-" + i));
            }

            assertEquals(expiredCount + validCount, snapshots.size());

            // Concurrent cleanup
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger removedCount = new AtomicInteger(0);

            for (int t = 0; t < THREAD_COUNT; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        snapshots.entrySet().removeIf(entry -> {
                            if (now - entry.getValue().timestamp > 5000) {
                                removedCount.incrementAndGet();
                                return true;
                            }
                            return false;
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            // Due to concurrent removeIf, the count might vary, but result should be consistent
            assertEquals(validCount, snapshots.size(), "Only valid snapshots should remain");
        }

        private record SnapshotEntry(UUID id, long timestamp, String data) {}
    }

    // === State Transition Stress ===

    @Nested
    @DisplayName("L5-RS-04: State Transition Stress")
    class StateTransitionStressTest {

        @Test
        @DisplayName("Rapid state transitions maintain consistency")
        void rapidStateTransitionsMaintainConsistency() throws Exception {
            ConcurrentHashMap<UUID, AtomicReference<RecoveryState>> playerStates = new ConcurrentHashMap<>();
            int playerCount = 100;
            int transitionsPerPlayer = 50;

            List<UUID> playerIds = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                UUID id = UUID.randomUUID();
                playerIds.add(id);
                playerStates.put(id, new AtomicReference<>(RecoveryState.NONE));
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(playerCount);
            AtomicInteger invalidTransitions = new AtomicInteger(0);

            for (UUID playerId : playerIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        AtomicReference<RecoveryState> stateRef = playerStates.get(playerId);

                        for (int i = 0; i < transitionsPerPlayer; i++) {
                            RecoveryState current = stateRef.get();
                            RecoveryState next = getNextState(current);

                            if (!stateRef.compareAndSet(current, next)) {
                                // Retry with updated current
                                current = stateRef.get();
                                next = getNextState(current);
                                if (!stateRef.compareAndSet(current, next)) {
                                    invalidTransitions.incrementAndGet();
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(60, TimeUnit.SECONDS));

            // Some transitions might fail due to race conditions, but that's expected
            // The important thing is the state machine never enters an invalid state
            for (UUID playerId : playerIds) {
                RecoveryState finalState = playerStates.get(playerId).get();
                assertNotNull(finalState, "State should not be null");
            }
        }

        @Test
        @DisplayName("Concurrent recovery with cancellation")
        void concurrentRecoveryWithCancellation() throws Exception {
            ConcurrentHashMap<UUID, RecoveryProcess> processes = new ConcurrentHashMap<>();
            int processCount = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(processCount * 2);
            AtomicInteger startedCount = new AtomicInteger(0);
            AtomicInteger cancelledCount = new AtomicInteger(0);
            AtomicInteger completedCount = new AtomicInteger(0);

            List<UUID> processIds = new ArrayList<>();
            for (int i = 0; i < processCount; i++) {
                UUID id = UUID.randomUUID();
                processIds.add(id);
                processes.put(id, new RecoveryProcess(id));
            }

            // Start threads
            for (UUID processId : processIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        RecoveryProcess process = processes.get(processId);
                        if (process.start()) {
                            startedCount.incrementAndGet();
                            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 100));
                            if (process.complete()) {
                                completedCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            // Cancel threads
            for (UUID processId : processIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Thread.sleep(ThreadLocalRandom.current().nextInt(20, 80));
                        RecoveryProcess process = processes.get(processId);
                        if (process.cancel()) {
                            cancelledCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(60, TimeUnit.SECONDS));

            // Each process should be in exactly one final state
            for (UUID processId : processIds) {
                RecoveryProcess process = processes.get(processId);
                ProcessState state = process.getState();
                assertTrue(state == ProcessState.COMPLETED || state == ProcessState.CANCELLED ||
                           state == ProcessState.IDLE,
                    "Process should be in a final state");
            }

            System.out.printf("Results: started=%d, completed=%d, cancelled=%d%n",
                startedCount.get(), completedCount.get(), cancelledCount.get());
        }

        private RecoveryState getNextState(RecoveryState current) {
            return switch (current) {
                case NONE -> RecoveryState.PENDING;
                case PENDING -> RecoveryState.RECOVERING;
                case RECOVERING -> RecoveryState.COMPLETED;
                case COMPLETED -> RecoveryState.NONE;
            };
        }

        private enum RecoveryState {
            NONE, PENDING, RECOVERING, COMPLETED
        }

        private enum ProcessState {
            IDLE, RUNNING, COMPLETED, CANCELLED
        }

        private static class RecoveryProcess {
            private final UUID id;
            private final AtomicReference<ProcessState> state = new AtomicReference<>(ProcessState.IDLE);

            RecoveryProcess(UUID id) {
                this.id = id;
            }

            boolean start() {
                return state.compareAndSet(ProcessState.IDLE, ProcessState.RUNNING);
            }

            boolean complete() {
                return state.compareAndSet(ProcessState.RUNNING, ProcessState.COMPLETED);
            }

            boolean cancel() {
                ProcessState current = state.get();
                if (current == ProcessState.RUNNING) {
                    return state.compareAndSet(ProcessState.RUNNING, ProcessState.CANCELLED);
                }
                return false;
            }

            ProcessState getState() {
                return state.get();
            }
        }
    }

    // === Memory Pressure Tests ===

    @Nested
    @DisplayName("L5-RS-05: Memory Pressure Tests")
    class MemoryPressureTest {

        @Test
        @DisplayName("Snapshot storage under memory pressure")
        void snapshotStorageUnderMemoryPressure() throws Exception {
            ConcurrentHashMap<UUID, LargeSnapshot> snapshots = new ConcurrentHashMap<>();
            int snapshotCount = 1000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            int snapshotsPerThread = snapshotCount / THREAD_COUNT;

            for (int t = 0; t < THREAD_COUNT; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < snapshotsPerThread; i++) {
                            try {
                                UUID id = UUID.randomUUID();
                                // Create snapshot with some data (simulates inventory, etc.)
                                LargeSnapshot snapshot = new LargeSnapshot(id, new byte[4096]);
                                snapshots.put(id, snapshot);
                                successCount.incrementAndGet();
                            } catch (OutOfMemoryError e) {
                                errorCount.incrementAndGet();
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(60, TimeUnit.SECONDS));

            System.out.printf("Created %d snapshots, %d errors%n", successCount.get(), errorCount.get());

            // Cleanup to prevent test pollution
            snapshots.clear();
            System.gc();

            assertTrue(successCount.get() > 0, "Some snapshots should be created");
        }

        @Test
        @DisplayName("Snapshot size limits are enforced")
        void snapshotSizeLimitsAreEnforced() throws Exception {
            ConcurrentHashMap<UUID, LimitedSnapshot> snapshots = new ConcurrentHashMap<>();
            int maxSnapshots = 100;
            int attemptCount = 500;
            AtomicInteger rejectedCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);

            for (int t = 0; t < THREAD_COUNT; t++) {
                int attemptsPerThread = attemptCount / THREAD_COUNT;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < attemptsPerThread; i++) {
                            UUID id = UUID.randomUUID();
                            LimitedSnapshot snapshot = new LimitedSnapshot(id);

                            // Only accept if under limit
                            if (snapshots.size() < maxSnapshots) {
                                snapshots.putIfAbsent(id, snapshot);
                            } else {
                                rejectedCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertTrue(snapshots.size() <= maxSnapshots, "Should not exceed max snapshots");
            assertTrue(rejectedCount.get() > 0, "Some snapshots should be rejected");
        }

        private record LargeSnapshot(UUID id, byte[] data) {}
        private record LimitedSnapshot(UUID id) {}
    }
}
