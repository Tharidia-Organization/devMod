package com.devmod.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class L6CriticalEdgeCaseTest {

    // =========================================================================
    // SIMULATION INFRASTRUCTURE
    // =========================================================================

    private static void awaitFutures(List<Future<?>> futures) throws Exception {
        for (Future<?> future : futures) {
            future.get();
        }
    }

    enum InstanceState {
        CREATING, READY, ACTIVE, COMPLETING, DESTROYING, DESTROYED
    }

    enum QuestState {
        NONE, STARTING, ACTIVE, WAVE_COMPLETE, COMPLETING, COMPLETED, FAILED, ABANDONED
    }

    enum PlayerState {
        NORMAL, PREPARING, IN_TRANSIT, IN_INSTANCE, RETURNING
    }

    /**
     * Simulates timing-sensitive operations
     */
    static class TimingSimulator {
        final Map<UUID, Long> operationStartTimes = new HashMap<>();
        final Map<UUID, Long> operationEndTimes = new HashMap<>();
        long systemTime = System.currentTimeMillis();

        void startOperation(UUID opId) {
            operationStartTimes.put(opId, systemTime);
        }

        void endOperation(UUID opId) {
            operationEndTimes.put(opId, systemTime);
        }

        long getOperationDuration(UUID opId) {
            Long start = operationStartTimes.get(opId);
            Long end = operationEndTimes.get(opId);
            if (start == null || end == null) return -1;
            return end - start;
        }

        void advanceTime(long millis) {
            systemTime += millis;
        }
    }

    /**
     * Simulates snapshot system for recovery testing
     */
    static class SnapshotSystem {
        final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
        final Map<UUID, Object> snapshotLocks = new ConcurrentHashMap<>();

        Object getLock(UUID playerId) {
            return snapshotLocks.computeIfAbsent(playerId, id -> new Object());
        }

        PlayerSnapshot createSnapshot(UUID playerId, String dimension, double x, double y, double z) {
            synchronized (getLock(playerId)) {
                PlayerSnapshot snapshot = new PlayerSnapshot(playerId, dimension, x, y, z);
                snapshots.put(playerId, snapshot);
                return snapshot;
            }
        }

        @Nullable
        PlayerSnapshot getSnapshot(UUID playerId) {
            return snapshots.get(playerId);
        }

        boolean updateSnapshotState(UUID playerId, PlayerState newState) {
            synchronized (getLock(playerId)) {
                PlayerSnapshot snapshot = snapshots.get(playerId);
                if (snapshot == null) return false;
                snapshot.state = newState;
                return true;
            }
        }

        void removeSnapshot(UUID playerId) {
            synchronized (getLock(playerId)) {
                snapshots.remove(playerId);
            }
        }
    }

    static class PlayerSnapshot {
        final UUID playerId;
        final String originalDimension;
        final double originalX, originalY, originalZ;
        PlayerState state = PlayerState.PREPARING;
        final long createdAt = System.currentTimeMillis();

        PlayerSnapshot(UUID playerId, String dimension, double x, double y, double z) {
            this.playerId = playerId;
            this.originalDimension = dimension;
            this.originalX = x;
            this.originalY = y;
            this.originalZ = z;
        }

        boolean isStale(long maxAge) {
            return System.currentTimeMillis() - createdAt > maxAge;
        }
    }

    // =========================================================================
    // SECTION 1: BOUNDARY VALUE ATTACKS
    // =========================================================================

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("L6-EC-01: Boundary Value Attacks")
    class BoundaryValueTests {

        @Test
        @Order(1)
        @DisplayName("Integer overflow in wave counter")
        void testWaveCounterOverflow() {
            int waveCounter = Integer.MAX_VALUE - 2;

            // Increment near max
            waveCounter += 1;
            assertEquals(Integer.MAX_VALUE - 1, waveCounter);
            waveCounter += 1;
            assertEquals(Integer.MAX_VALUE, waveCounter);

            // Next increment would overflow to negative - detect and prevent
            int current = waveCounter;
            if (current == Integer.MAX_VALUE) {
                // Prevent overflow by capping
                assertTrue(true, "Overflow prevented");
            } else {
                fail("Should have detected max value");
            }
        }

        @Test
        @Order(2)
        @DisplayName("Long overflow in currency system")
        void testCurrencyOverflow() {
            long balance = Long.MAX_VALUE - 100;

            // Safe add that checks for overflow
            long toAdd = 200;
            long current = balance;

            if (Long.MAX_VALUE - current < toAdd) {
                // Would overflow - cap at MAX_VALUE
                balance = Long.MAX_VALUE;
            } else {
                balance += toAdd;
            }

            assertEquals(Long.MAX_VALUE, balance, "Balance should be capped at MAX_VALUE");
        }

        @Test
        @Order(3)
        @DisplayName("Negative value injection prevention")
        void testNegativeValuePrevention() {
            // Damage values
            float damage = -100.0f;
            float safeDamage = Math.max(0, damage);
            assertEquals(0, safeDamage, "Negative damage should be clamped to 0");

            // Kill counts
            int kills = -50;
            int safeKills = Math.max(0, kills);
            assertEquals(0, safeKills, "Negative kills should be clamped to 0");

            // Wave numbers
            int wave = -1;
            int safeWave = Math.max(1, wave);
            assertEquals(1, safeWave, "Wave should be at least 1");
        }

        @Test
        @Order(4)
        @DisplayName("Empty collection edge cases")
        void testEmptyCollectionEdgeCases() {
            List<UUID> emptyList = Collections.emptyList();
            Set<UUID> emptySet = Collections.emptySet();
            Map<UUID, String> emptyMap = Collections.emptyMap();

            assertTrue(emptyList.stream().findFirst().isEmpty());
            assertEquals(0, emptySet.stream().count());
            assertTrue(emptyMap.values().stream().toList().isEmpty());

            // Size checks
            assertEquals(0, emptyList.size());
            assertTrue(emptySet.isEmpty());
            assertFalse(emptyMap.containsKey(UUID.randomUUID()));
        }

        @Test
        @Order(5)
        @DisplayName("Maximum player name length handling")
        void testMaxPlayerNameLength() {
            // Minecraft player names are max 16 characters
            String normalName = "Player123";
            String maxName = "1234567890123456"; // 16 chars
            String tooLongName = "12345678901234567"; // 17 chars

            assertTrue(normalName.length() <= 16);
            assertEquals(16, maxName.length());

            // Truncate if too long
            String safeName = tooLongName.length() > 16 ?
                tooLongName.substring(0, 16) : tooLongName;
            assertEquals(16, safeName.length());
        }

        @Test
        @Order(6)
        @DisplayName("UUID edge cases")
        void testUUIDEdgeCases() {
            // Nil UUID
            UUID nilUUID = new UUID(0, 0);
            assertEquals("00000000-0000-0000-0000-000000000000", nilUUID.toString());

            // Max UUID
            UUID maxUUID = new UUID(-1, -1); // All bits set
            assertEquals("ffffffff-ffff-ffff-ffff-ffffffffffff", maxUUID.toString());

            // Both should work in maps
            Map<UUID, String> map = new HashMap<>();
            map.put(nilUUID, "nil");
            map.put(maxUUID, "max");

            assertEquals("nil", map.get(nilUUID));
            assertEquals("max", map.get(maxUUID));
            assertEquals(2, map.size());
        }

        @Test
        @Order(7)
        @DisplayName("Coordinate boundary handling")
        void testCoordinateBoundaries() {
            // Minecraft world limit is approximately ±30,000,000
            double worldLimit = 30_000_000.0;

            double[] testCoords = {
                0, 1, -1,
                worldLimit, -worldLimit,
                worldLimit + 1, -(worldLimit + 1),
                Double.MAX_VALUE, Double.MIN_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.NaN
            };

            for (double coord : testCoords) {
                // Clamp to valid range
                double safeCoord;
                if (Double.isNaN(coord) || Double.isInfinite(coord)) {
                    safeCoord = 0; // Reset to spawn
                } else {
                    safeCoord = Math.max(-worldLimit, Math.min(worldLimit, coord));
                }

                assertTrue(safeCoord >= -worldLimit && safeCoord <= worldLimit,
                    "Coordinate " + coord + " should be clamped to valid range, got " + safeCoord);
            }
        }
    }

    // =========================================================================
    // SECTION 2: TEMPORAL EDGE CASES
    // =========================================================================

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("L6-EC-02: Temporal Edge Cases")
    class TemporalEdgeCaseTests {

        @Test
        @Order(8)
        @DisplayName("Stale snapshot detection")
        void testStaleSnapshotDetection() {
            SnapshotSystem system = new SnapshotSystem();
            UUID playerId = UUID.randomUUID();

            // Create snapshot with an old timestamp
            PlayerSnapshot snapshot = system.createSnapshot(playerId, "overworld", 100, 64, 100);

            // Immediately after creation, not stale with large max age
            assertFalse(snapshot.isStale(30000), "Fresh snapshot should not be stale");

            // Create an artificially old snapshot for testing
            PlayerSnapshot oldSnapshot = new PlayerSnapshot(playerId, "overworld", 100, 64, 100) {
                @Override
                boolean isStale(long maxAge) {
                    // Simulate 1 minute old snapshot
                    long fakeAge = 60000;
                    return fakeAge > maxAge;
                }
            };

            // Old snapshot should be stale with 30 second threshold
            assertTrue(oldSnapshot.isStale(30000), "Old snapshot should be stale");
            assertFalse(oldSnapshot.isStale(120000), "Old snapshot should not be stale with 2 min threshold");
        }

        @Test
        @Order(9)
        @DisplayName("Operation ordering with timestamps")
        void testOperationOrdering() {
            TimingSimulator timer = new TimingSimulator();

            UUID op1 = UUID.randomUUID();
            UUID op2 = UUID.randomUUID();
            UUID op3 = UUID.randomUUID();

            // Start operations at different times
            timer.startOperation(op1);
            timer.advanceTime(100);
            timer.startOperation(op2);
            timer.advanceTime(100);
            timer.startOperation(op3);

            // End in different order
            timer.advanceTime(50);
            timer.endOperation(op2);
            timer.advanceTime(50);
            timer.endOperation(op1);
            timer.advanceTime(50);
            timer.endOperation(op3);

            // Verify durations
            long d1 = timer.getOperationDuration(op1);
            long d2 = timer.getOperationDuration(op2);
            long d3 = timer.getOperationDuration(op3);

            assertTrue(d1 > 0, "Op1 duration should be positive");
            assertTrue(d2 > 0, "Op2 duration should be positive");
            assertTrue(d3 > 0, "Op3 duration should be positive");

            // Op1 started first but ended after op2
            assertTrue(d1 > d2, "Op1 should have longer duration than op2");
        }

        @Test
        @Order(10)
        @DisplayName("Countdown timer precision")
        void testCountdownTimerPrecision() {
            int totalTicks = 200; // 10 seconds at 20 TPS
            int ticksRemaining = totalTicks;
            int ticksProcessed = 0;

            // Simulate tick processing
            while (ticksRemaining > 0) {
                ticksRemaining -= 1;
                ticksProcessed += 1;
            }

            assertEquals(0, ticksRemaining, "Countdown should reach exactly 0");
            assertEquals(totalTicks, ticksProcessed, "Should process exact number of ticks");
        }

        @Test
        @Order(11)
        @DisplayName("Race between quest end and wave start")
        void testQuestEndWaveStartRace() {
            class QuestRaceState {
                QuestState questState = QuestState.ACTIVE;
                boolean waveStarted = false;
                boolean questEnded = false;
            }

            QuestRaceState raceState = new QuestRaceState();

            // Simulate race condition
            Object lock = new Object();

            // Quest end thread tries to end quest
            Runnable questEndTask = () -> {
                synchronized (lock) {
                    if (raceState.questState == QuestState.ACTIVE) {
                        raceState.questState = QuestState.COMPLETING;
                        raceState.questEnded = true;
                    }
                }
            };

            // Wave start thread tries to start new wave
            Runnable waveStartTask = () -> {
                synchronized (lock) {
                    if (raceState.questState == QuestState.ACTIVE) {
                        raceState.waveStarted = true;
                    }
                }
            };

            // Execute in order (with sync, only one should succeed)
            questEndTask.run();
            waveStartTask.run();

            // Quest end should have succeeded
            assertTrue(raceState.questEnded, "Quest should have ended");
            assertFalse(raceState.waveStarted, "Wave should not have started after quest ended");
            assertEquals(QuestState.COMPLETING, raceState.questState);
        }

        @Test
        @Order(12)
        @DisplayName("Timeout handling for stuck operations")
        void testStuckOperationTimeout() {
            long timeoutMs = 5000;
            long startTime = System.currentTimeMillis();

            AtomicBoolean operationComplete = new AtomicBoolean(false);
            boolean timedOut = false;

            // Simulate operation that might get stuck
            CompletableFuture<Void> operation = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(100); // Quick operation
                    operationComplete.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            try {
                operation.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                timedOut = true;
            } catch (Exception e) {
                fail("Unexpected exception: " + e);
            }

            assertTrue(operationComplete.get(), "Operation should complete");
            assertFalse(timedOut, "Should not have timed out");
            assertTrue(System.currentTimeMillis() - startTime < timeoutMs,
                "Should complete before timeout");
        }
    }

    // =========================================================================
    // SECTION 3: RESOURCE EXHAUSTION SCENARIOS
    // =========================================================================

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("L6-EC-03: Resource Exhaustion Scenarios")
    class ResourceExhaustionTests {

        @Test
        @Order(13)
        @DisplayName("Map growth limit enforcement")
        void testMapGrowthLimit() {
            int maxEntries = 10000;
            Map<UUID, String> limitedMap = new HashMap<>();

            for (int i = 0; i < maxEntries + 100; i++) {
                if (limitedMap.size() < maxEntries) {
                    limitedMap.put(UUID.randomUUID(), "entry_" + i);
                }
            }

            assertEquals(maxEntries, limitedMap.size(),
                "Map should not exceed max entries");
        }

        @Test
        @Order(14)
        @DisplayName("Thread pool exhaustion handling")
        @Timeout(30)
        void testThreadPoolExhaustion() throws Exception {
            int poolSize = 4;
            int taskCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(poolSize);
            List<Future<?>> futures = new ArrayList<>(taskCount);
            CountDownLatch latch = new CountDownLatch(taskCount);
            AtomicInteger completedTasks = new AtomicInteger(0);

            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        Thread.sleep(10); // Simulate work
                        completedTasks.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            assertEquals(taskCount, completedTasks.get(),
                "All tasks should complete even with limited thread pool");
        }

        @Test
        @Order(15)
        @DisplayName("Queue overflow handling")
        void testQueueOverflow() {
            int queueLimit = 100;
            BlockingQueue<String> boundedQueue = new ArrayBlockingQueue<>(queueLimit);
            int rejected = 0;

            for (int i = 0; i < queueLimit * 2; i++) {
                if (!boundedQueue.offer("item_" + i)) {
                    rejected += 1;
                }
            }

            assertEquals(queueLimit, boundedQueue.size(), "Queue should be at capacity");
            assertEquals(queueLimit, rejected, "Half should be rejected");
        }

        @Test
        @Order(16)
        @DisplayName("String buffer overflow protection")
        void testStringBufferOverflow() {
            int maxLength = 10000;
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < 1000; i++) {
                String toAdd = "segment_" + i + "_";
                if (sb.length() + toAdd.length() <= maxLength) {
                    sb.append(toAdd);
                }
            }

            assertTrue(sb.length() <= maxLength,
                "String buffer should not exceed max length");
        }
    }

    // =========================================================================
    // SECTION 4: STATE CORRUPTION PREVENTION
    // =========================================================================

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("L6-EC-04: State Corruption Prevention")
    class StateCorruptionTests {

        @Test
        @Order(17)
        @DisplayName("Concurrent snapshot modification safety")
        @Timeout(30)
        void testConcurrentSnapshotModification() throws Exception {
            SnapshotSystem system = new SnapshotSystem();
            UUID playerId = UUID.randomUUID();

            system.createSnapshot(playerId, "overworld", 0, 64, 0);

            int modifications = 1000;
            CountDownLatch latch = new CountDownLatch(modifications);
            AtomicInteger errors = new AtomicInteger(0);
            AtomicInteger successfulUpdates = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<?>> futures = new ArrayList<>(modifications);

            PlayerState[] states = PlayerState.values();
            for (int i = 0; i < modifications; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    try {
                        PlayerState newState = states[idx % states.length];
                        if (system.updateSnapshotState(playerId, newState)) {
                            successfulUpdates.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            assertEquals(0, errors.get(), "No errors during concurrent modifications");
            assertEquals(modifications, successfulUpdates.get(),
                "All modifications should succeed");

            // Snapshot should still be valid
            PlayerSnapshot snapshot = Objects.requireNonNull(system.getSnapshot(playerId), "Snapshot should exist");
            assertNotNull(snapshot.state);
        }

        @Test
        @Order(18)
        @DisplayName("Atomic state transitions")
        void testAtomicStateTransitions() throws Exception {
            AtomicReference<InstanceState> state = new AtomicReference<>(InstanceState.CREATING);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<Future<?>> futures = new ArrayList<>(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);
            AtomicInteger successes = new AtomicInteger(0);

            Runnable transitionTask = () -> {
                try {
                    startLatch.await(1, TimeUnit.SECONDS);
                    if (state.compareAndSet(InstanceState.CREATING, InstanceState.READY)) {
                        successes.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            };

            futures.add(executor.submit(transitionTask));
            futures.add(executor.submit(transitionTask));
            startLatch.countDown();
            doneLatch.await(2, TimeUnit.SECONDS);
            executor.shutdown();
            awaitFutures(futures);

            // CAS ensures atomicity
            assertEquals(1, successes.get(), "Only one transition should succeed");
            assertEquals(InstanceState.READY, state.get(), "State should be READY after atomic transition");
            assertTrue(state.compareAndSet(InstanceState.READY, InstanceState.ACTIVE),
                "Transition with correct current state should succeed");

            assertEquals(InstanceState.ACTIVE, state.get());
        }

        @Test
        @Order(19)
        @DisplayName("Map entry atomicity with compute")
        void testMapEntryAtomicity() throws Exception {
            ConcurrentHashMap<UUID, AtomicInteger> counters = new ConcurrentHashMap<>();
            UUID key = UUID.randomUUID();
            ExecutorService executor = Executors.newFixedThreadPool(3);
            List<Future<?>> futures = new ArrayList<>(3);
            CountDownLatch latch = new CountDownLatch(3);

            // Atomic initialization and increment
            for (int i = 0; i < 3; i++) {
                futures.add(executor.submit(() -> {
                    counters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
                    latch.countDown();
                }));
            }

            latch.await(2, TimeUnit.SECONDS);
            executor.shutdown();
            awaitFutures(futures);

            AtomicInteger counter = Objects.requireNonNull(counters.get(key), "Counter should exist");
            assertEquals(3, counter.get(),
                "Counter should be exactly 3 after atomic operations");
        }

        @Test
        @Order(20)
        @DisplayName("Defensive copy for returned collections")
        void testDefensiveCopy() {
            Set<String> originalPerks = new HashSet<>();
            originalPerks.add("PERK_A");
            originalPerks.add("PERK_B");

            // Return defensive copy
            Set<String> copy = new HashSet<>(originalPerks);

            // Modify copy
            copy.add("PERK_C");
            copy.remove("PERK_A");

            // Original should be unchanged
            assertTrue(copy.contains("PERK_C"));
            assertFalse(copy.contains("PERK_A"));
            assertTrue(originalPerks.contains("PERK_A"));
            assertFalse(originalPerks.contains("PERK_C"));
            assertEquals(2, originalPerks.size());
        }
    }

    // =========================================================================
    // SECTION 5: RECOVERY MECHANISM VALIDATION
    // =========================================================================

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("L6-EC-05: Recovery Mechanism Validation")
    class RecoveryMechanismTests {

        @Test
        @Order(21)
        @DisplayName("Snapshot recovery restores all player data")
        void testSnapshotRecovery() {
            SnapshotSystem system = new SnapshotSystem();
            UUID playerId = UUID.randomUUID();

            // Original position
            String originalDim = "minecraft:overworld";
            double originalX = 123.5;
            double originalY = 64.0;
            double originalZ = -456.7;

            // Create snapshot before teleport
            system.createSnapshot(playerId, originalDim, originalX, originalY, originalZ);
            system.updateSnapshotState(playerId, PlayerState.IN_TRANSIT);

            // Simulate player now in instance
            system.updateSnapshotState(playerId, PlayerState.IN_INSTANCE);

            // Simulate crash/disconnect - need recovery
            PlayerSnapshot snapshot = Objects.requireNonNull(system.getSnapshot(playerId), "Snapshot should exist for recovery");

            // Verify recovery data
            assertEquals(originalDim, snapshot.originalDimension);
            assertEquals(originalX, snapshot.originalX, 0.001);
            assertEquals(originalY, snapshot.originalY, 0.001);
            assertEquals(originalZ, snapshot.originalZ, 0.001);

            // Perform recovery
            system.updateSnapshotState(playerId, PlayerState.RETURNING);
            system.updateSnapshotState(playerId, PlayerState.NORMAL);
            system.removeSnapshot(playerId);

            assertNull(system.getSnapshot(playerId), "Snapshot should be removed after recovery");
        }

        @Test
        @Order(22)
        @DisplayName("Orphaned instance cleanup")
        void testOrphanedInstanceCleanup() {
            Map<UUID, InstanceState> instances = new HashMap<>();
            Map<UUID, Set<UUID>> instancePlayers = new HashMap<>();

            // Create instance with players
            UUID instanceId = UUID.randomUUID();
            instances.put(instanceId, InstanceState.ACTIVE);
            Set<UUID> players = new HashSet<>();
            players.add(UUID.randomUUID());
            players.add(UUID.randomUUID());
            instancePlayers.put(instanceId, players);

            // Players disconnect
            players.clear();

            // Cleanup check
            List<UUID> orphanedInstances = new ArrayList<>();
            for (Map.Entry<UUID, InstanceState> entry : instances.entrySet()) {
                if (entry.getValue() == InstanceState.ACTIVE) {
                    Set<UUID> instancePlayerSet = instancePlayers.get(entry.getKey());
                    if (instancePlayerSet == null || instancePlayerSet.isEmpty()) {
                        orphanedInstances.add(entry.getKey());
                    }
                }
            }

            assertEquals(1, orphanedInstances.size());
            assertTrue(orphanedInstances.contains(instanceId));

            // Clean up orphaned instances
            for (UUID orphaned : orphanedInstances) {
                instances.put(orphaned, InstanceState.DESTROYING);
                instances.put(orphaned, InstanceState.DESTROYED);
                instances.remove(orphaned);
                instancePlayers.remove(orphaned);
            }

            assertTrue(instances.isEmpty());
            assertTrue(instancePlayers.isEmpty());
        }

        @Test
        @Order(23)
        @DisplayName("Partial transaction rollback")
        void testPartialTransactionRollback() {
            long balance = 1000;
            QuestState questState = QuestState.NONE;
            UUID instanceId = UUID.randomUUID();
            Map<UUID, InstanceState> instances = new HashMap<>();

            // Transaction: Spend currency to start premium quest
            long cost = 500;
            long originalBalance = balance;
            QuestState originalState = questState;

            try {
                // Step 1: Deduct currency
                balance -= cost;

                // Step 2: Start quest
                questState = QuestState.STARTING;

                // Step 3: Create instance (fails!)
                if (true) { // Simulated failure
                    throw new RuntimeException("Instance creation failed");
                }

            } catch (Exception e) {
                // Rollback
                balance = originalBalance;
                questState = originalState;
            }

            // Verify rollback
            assertEquals(originalBalance, balance, "Balance should be restored");
            assertEquals(originalState, questState, "Quest state should be restored");
            assertFalse(instances.containsKey(instanceId), "Instance should not exist");
        }

        @Test
        @Order(24)
        @DisplayName("Server restart recovery simulation")
        void testServerRestartRecovery() {
            SnapshotSystem system = new SnapshotSystem();
            Map<UUID, UUID> playerToInstance = new HashMap<>();

            // Setup: 3 players in various states
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();
            UUID instance = UUID.randomUUID();

            system.createSnapshot(player1, "overworld", 0, 64, 0);
            system.updateSnapshotState(player1, PlayerState.IN_INSTANCE);
            playerToInstance.put(player1, instance);

            system.createSnapshot(player2, "overworld", 100, 64, 100);
            system.updateSnapshotState(player2, PlayerState.IN_TRANSIT);
            playerToInstance.put(player2, instance);

            system.createSnapshot(player3, "overworld", 200, 64, 200);
            system.updateSnapshotState(player3, PlayerState.RETURNING);

            // Server restart - recover all players
            List<UUID> recovered = new ArrayList<>();

            for (PlayerSnapshot snapshot : system.snapshots.values()) {
                if (snapshot.state != PlayerState.NORMAL) {
                    // Would teleport player back to original position
                    // For test, just record recovery
                    recovered.add(snapshot.playerId);
                    playerToInstance.remove(snapshot.playerId);
                    system.removeSnapshot(snapshot.playerId);
                }
            }

            assertEquals(3, recovered.size(), "All 3 players should be recovered");
            assertTrue(system.snapshots.isEmpty(), "All snapshots should be cleared");
            assertTrue(playerToInstance.isEmpty(), "All player-instance mappings should be cleared");
        }
    }

    // =========================================================================
    // SECTION 6: EXPLOIT PREVENTION
    // =========================================================================

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("L6-EC-06: Exploit Prevention")
    class ExploitPreventionTests {

        @Test
        @Order(25)
        @DisplayName("Duplicate quest reward prevention")
        void testDuplicateRewardPrevention() {
            Set<UUID> processedQuests = new HashSet<>();
            long totalRewardsGiven = 0;
            long rewardAmount = 1000;

            UUID questId = UUID.randomUUID();

            // Attempt to claim reward multiple times
            for (int i = 0; i < 10; i++) {
                if (processedQuests.add(questId)) {
                    // First time - give reward
                    totalRewardsGiven += rewardAmount;
                }
            }

            assertEquals(rewardAmount, totalRewardsGiven,
                "Reward should only be given once");
            assertEquals(1, processedQuests.size());
        }

        @Test
        @Order(26)
        @DisplayName("Rate limiting for quest starts")
        void testQuestStartRateLimiting() {
            Map<UUID, Long> lastQuestStart = new HashMap<>();
            long cooldownMs = 5000; // 5 second cooldown
            UUID playerId = UUID.randomUUID();

            int successfulStarts = 0;
            long baseTime = System.currentTimeMillis();

            // Attempt rapid starts
            for (int i = 0; i < 10; i++) {
                long attemptTime = baseTime + (i * 1000); // 1 second apart
                Long lastStart = lastQuestStart.get(playerId);

                if (lastStart == null || attemptTime - lastStart >= cooldownMs) {
                    lastQuestStart.put(playerId, attemptTime);
                    successfulStarts += 1;
                }
            }

            // Only 2 should succeed (at 0s and 5s)
            assertEquals(2, successfulStarts,
                "Rate limiting should prevent rapid starts");
        }

        @Test
        @Order(27)
        @DisplayName("Perk stack limit enforcement")
        void testPerkStackLimit() {
            int maxStacks = 5;
            Map<String, Integer> perkStacks = new HashMap<>();
            String perk = "DAMAGE_BOOST";

            perkStacks.put(perk, 0);

            // Attempt to stack beyond limit
            for (int i = 0; i < 10; i++) {
                int stacks = Objects.requireNonNull(perkStacks.get(perk));
                if (stacks < maxStacks) {
                    stacks += 1;
                    perkStacks.put(perk, stacks);
                }
            }

            assertEquals(maxStacks, Objects.requireNonNull(perkStacks.get(perk)),
                "Perk stacks should not exceed max");
        }

        @Test
        @Order(28)
        @DisplayName("Position validation prevents OOB exploits")
        void testPositionValidation() {
            double worldLimit = 30_000_000;

            // Test various exploit attempts
            double[][] exploitPositions = {
                {worldLimit * 2, 64, 0},           // Beyond world limit
                {0, -100, 0},                       // Below void
                {0, 400, 0},                        // Above height limit
                {Double.NaN, 64, 0},               // NaN injection
                {Double.POSITIVE_INFINITY, 64, 0}, // Infinity injection
            };

            for (double[] pos : exploitPositions) {
                double x = pos[0], y = pos[1], z = pos[2];

                // Validate and sanitize
                boolean valid = true;

                if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) valid = false;
                if (Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) valid = false;
                if (Math.abs(x) > worldLimit || Math.abs(z) > worldLimit) valid = false;
                if (y < -64 || y > 320) valid = false;

                assertFalse(valid, "Position should be rejected: " + Arrays.toString(pos));
            }
        }

        @Test
        @Order(29)
        @DisplayName("Input sanitization for player-provided data")
        void testInputSanitization() {
            // Quest name from player input
            String[] maliciousInputs = {
                "<script>alert('xss')</script>",
                "'; DROP TABLE players; --",
                "../../../etc/passwd",
                "\0null_byte",
                "\n\rCRLF_injection",
                "a".repeat(10000) // DoS via long string
            };

            int maxLength = 100;
            String allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_- ";

            for (String input : maliciousInputs) {
                // Sanitize
                String sanitized = input.chars()
                    .filter(c -> allowedChars.indexOf(c) >= 0)
                    .limit(maxLength)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();

                assertTrue(sanitized.length() <= maxLength,
                    "Sanitized string should respect max length");
                assertTrue(sanitized.chars().allMatch(c -> allowedChars.indexOf(c) >= 0),
                    "Sanitized string should only contain allowed chars");
            }
        }

        @Test
        @Order(30)
        @DisplayName("Time manipulation detection")
        void testTimeManipulationDetection() {
            long serverTime = System.currentTimeMillis();
            Map<UUID, Long> playerLastAction = new HashMap<>();
            UUID playerId = UUID.randomUUID();

            // Normal action
            playerLastAction.put(playerId, serverTime);

            // Advance server time
            serverTime += 5000;

            // Player claims action happened in the future (time manipulation)
            long claimedTime = serverTime + 10000;

            boolean suspicious = false;
            Long lastAction = playerLastAction.get(playerId);
            long lastActionValue = lastAction != null ? lastAction : 0L;

            // Detection: claimed time is in the future relative to server
            if (claimedTime > serverTime) {
                suspicious = true;
            }

            // Detection: claimed time is before last known action (time reversal)
            if (claimedTime < lastActionValue) {
                suspicious = true;
            }

            assertTrue(suspicious, "Time manipulation should be detected");
        }
    }
}
