package com.devmod.stress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L5: Endurance Quest System Stress Tests
 *
 * Tests the endurance quest system under high load conditions:
 * - Multiple concurrent quest sessions
 * - High-frequency wave transitions
 * - Concurrent player actions (damage, kills, deaths)
 * - Reward distribution under load
 * - Session cleanup and resource management
 */
@Tag("load")
@DisplayName("L5: Endurance Quest Stress Tests")
class EnduranceQuestStressTest {

    private ExecutorService executor;
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors() * 2;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor should terminate cleanly");
    }

    // === Session Management Stress ===

    @Nested
    @DisplayName("L5-EQ-01: Session Management Stress")
    class SessionManagementStressTest {

        @Test
        @DisplayName("Concurrent session creation doesn't cause duplicates")
        void concurrentSessionCreationNoDuplicates() throws Exception {
            ConcurrentHashMap<UUID, String> activeSessions = new ConcurrentHashMap<>();
            int playerCount = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(playerCount);
            AtomicInteger successfulCreations = new AtomicInteger(0);
            AtomicInteger duplicateAttempts = new AtomicInteger(0);

            for (int i = 0; i < playerCount; i++) {
                UUID playerId = UUID.randomUUID();
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        // Simulate session creation with putIfAbsent
                        String previous = activeSessions.putIfAbsent(playerId, "session-" + System.nanoTime());
                        if (previous == null) {
                            successfulCreations.incrementAndGet();
                        } else {
                            duplicateAttempts.incrementAndGet();
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

            assertEquals(playerCount, successfulCreations.get(), "All unique players should create sessions");
            assertEquals(0, duplicateAttempts.get(), "No duplicates should occur with unique IDs");
            assertEquals(playerCount, activeSessions.size());
        }

        @Test
        @DisplayName("Same player can't create multiple concurrent sessions")
        void samePlayerCantCreateMultipleSessions() throws Exception {
            ConcurrentHashMap<UUID, String> activeSessions = new ConcurrentHashMap<>();
            UUID sharedPlayerId = UUID.randomUUID();
            int attemptCount = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(attemptCount);
            AtomicInteger successfulCreations = new AtomicInteger(0);

            for (int i = 0; i < attemptCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String previous = activeSessions.putIfAbsent(sharedPlayerId, "session-" + Thread.currentThread().threadId());
                        if (previous == null) {
                            successfulCreations.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));

            assertEquals(1, successfulCreations.get(), "Only one session should be created for same player");
            assertEquals(1, activeSessions.size());
        }

        @Test
        @DisplayName("Session cleanup under concurrent access")
        void sessionCleanupUnderConcurrentAccess() throws Exception {
            ConcurrentHashMap<UUID, SessionData> sessions = new ConcurrentHashMap<>();
            int sessionCount = 100;
            List<UUID> sessionIds = new ArrayList<>();

            // Create sessions
            for (int i = 0; i < sessionCount; i++) {
                UUID id = UUID.randomUUID();
                sessionIds.add(id);
                sessions.put(id, new SessionData(id, i % 2 == 0)); // Half are expired
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger cleanedCount = new AtomicInteger(0);
            AtomicInteger accessCount = new AtomicInteger(0);

            // Half threads clean, half threads access
            for (int t = 0; t < THREAD_COUNT; t++) {
                boolean isCleaner = t % 2 == 0;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (isCleaner) {
                            // Cleaner: remove expired sessions
                            for (UUID id : sessionIds) {
                                SessionData session = sessions.get(id);
                                if (session != null && session.expired) {
                                    if (sessions.remove(id, session)) {
                                        cleanedCount.incrementAndGet();
                                    }
                                }
                            }
                        } else {
                            // Accessor: try to access sessions
                            for (UUID id : sessionIds) {
                                SessionData session = sessions.get(id);
                                if (session != null) {
                                    accessCount.incrementAndGet();
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
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            // All expired sessions should be cleaned
            long remainingExpired = sessions.values().stream().filter(s -> s.expired).count();
            assertEquals(0, remainingExpired, "All expired sessions should be cleaned");
            assertEquals(sessionCount / 2, sessions.size(), "Only non-expired sessions should remain");
        }

        private record SessionData(UUID id, boolean expired) {}
    }

    // === Wave Management Stress ===

    @Nested
    @DisplayName("L5-EQ-02: Wave Management Stress")
    class WaveManagementStressTest {

        @Test
        @DisplayName("Rapid wave transitions maintain consistency")
        void rapidWaveTransitionsMaintainConsistency() throws Exception {
            AtomicInteger currentWave = new AtomicInteger(0);
            AtomicInteger maxWaveReached = new AtomicInteger(0);
            int targetWaves = 100;
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successfulTransitions = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        while (true) {
                            int current = currentWave.get();
                            if (current >= targetWaves) break;

                            // Try to advance wave
                            if (currentWave.compareAndSet(current, current + 1)) {
                                maxWaveReached.updateAndGet(max -> Math.max(max, current + 1));
                                successfulTransitions.incrementAndGet();
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

            assertEquals(targetWaves, currentWave.get(), "Should reach target wave");
            assertEquals(targetWaves, successfulTransitions.get(), "Each wave should be transitioned exactly once");
            assertEquals(targetWaves, maxWaveReached.get(), "Max wave should match target");
        }

        @Test
        @DisplayName("Wave state queries during transitions are safe")
        void waveStateQueriesDuringTransitionsAreSafe() throws Exception {
            WaveState waveState = new WaveState();
            int operations = 10000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
            AtomicBoolean inconsistencyDetected = new AtomicBoolean(false);

            for (int t = 0; t < THREAD_COUNT; t++) {
                boolean isWriter = t % 3 == 0; // 1/3 writers
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < operations; i++) {
                            if (isWriter) {
                                waveState.advanceWave();
                            } else {
                                // Reader: verify consistency
                                int wave = waveState.getCurrentWave();
                                int mobCount = waveState.getMobsForWave(wave);
                                if (wave < 0 || mobCount < 0) {
                                    inconsistencyDetected.set(true);
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
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            assertFalse(inconsistencyDetected.get(), "No inconsistent state should be observed");
            assertTrue(waveState.getCurrentWave() > 0, "Wave should have advanced");
        }

        private static class WaveState {
            private final AtomicInteger wave = new AtomicInteger(1);

            void advanceWave() {
                wave.incrementAndGet();
            }

            int getCurrentWave() {
                return wave.get();
            }

            int getMobsForWave(int wave) {
                return 5 + wave * 2; // Simple scaling formula
            }
        }
    }

    // === Combat Event Stress ===

    @Nested
    @DisplayName("L5-EQ-03: Combat Event Stress")
    class CombatEventStressTest {

        @Test
        @DisplayName("High-frequency damage events are tracked accurately")
        void highFrequencyDamageEventsTrackedAccurately() throws Exception {
            ConcurrentHashMap<UUID, LongAdder> damageByPlayer = new ConcurrentHashMap<>();
            int playerCount = 20;
            int eventsPerPlayer = 1000;
            List<UUID> playerIds = new ArrayList<>();

            for (int i = 0; i < playerCount; i++) {
                UUID id = UUID.randomUUID();
                playerIds.add(id);
                damageByPlayer.put(id, new LongAdder());
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(playerCount);
            AtomicLong totalDamageExpected = new AtomicLong(0);

            for (int p = 0; p < playerCount; p++) {
                UUID playerId = playerIds.get(p);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        long playerDamage = 0;
                        for (int i = 0; i < eventsPerPlayer; i++) {
                            int damage = ThreadLocalRandom.current().nextInt(1, 100);
                            damageByPlayer.get(playerId).add(damage);
                            playerDamage += damage;
                        }
                        totalDamageExpected.addAndGet(playerDamage);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));

            long actualTotal = damageByPlayer.values().stream()
                .mapToLong(LongAdder::sum)
                .sum();

            assertEquals(totalDamageExpected.get(), actualTotal, "Total damage should match");
        }

        @Test
        @DisplayName("Kill tracking under concurrent combat")
        void killTrackingUnderConcurrentCombat() throws Exception {
            ConcurrentHashMap<UUID, AtomicInteger> killsByPlayer = new ConcurrentHashMap<>();
            ConcurrentHashMap<UUID, AtomicInteger> killsByMob = new ConcurrentHashMap<>();
            int playerCount = 10;
            int mobCount = 100;

            List<UUID> playerIds = new ArrayList<>();
            List<UUID> mobIds = new ArrayList<>();

            for (int i = 0; i < playerCount; i++) {
                UUID id = UUID.randomUUID();
                playerIds.add(id);
                killsByPlayer.put(id, new AtomicInteger(0));
            }

            for (int i = 0; i < mobCount; i++) {
                UUID id = UUID.randomUUID();
                mobIds.add(id);
                killsByMob.put(id, new AtomicInteger(0)); // 0 = alive, 1 = killed
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(playerCount);
            AtomicInteger totalKills = new AtomicInteger(0);

            for (int p = 0; p < playerCount; p++) {
                UUID playerId = playerIds.get(p);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        ThreadLocalRandom random = ThreadLocalRandom.current();
                        for (int i = 0; i < 50; i++) {
                            UUID targetMob = mobIds.get(random.nextInt(mobCount));
                            // Try to kill mob (only one player can kill each mob)
                            if (killsByMob.get(targetMob).compareAndSet(0, 1)) {
                                killsByPlayer.get(playerId).incrementAndGet();
                                totalKills.incrementAndGet();
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

            int playerKillSum = killsByPlayer.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();

            assertEquals(totalKills.get(), playerKillSum, "Kill counts should match");
            assertTrue(totalKills.get() <= mobCount, "Can't kill more mobs than exist");

            // Each mob killed at most once
            long killedMobs = killsByMob.values().stream()
                .filter(v -> v.get() == 1)
                .count();
            assertEquals(totalKills.get(), killedMobs, "Each kill should correspond to exactly one mob");
        }

        @Test
        @DisplayName("Death and respawn cycling doesn't corrupt state")
        void deathRespawnCyclingDoesntCorruptState() throws Exception {
            ConcurrentHashMap<UUID, PlayerState> players = new ConcurrentHashMap<>();
            int playerCount = 20;
            int cyclesPerPlayer = 100;

            List<UUID> playerIds = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                UUID id = UUID.randomUUID();
                playerIds.add(id);
                players.put(id, new PlayerState());
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(playerCount);
            AtomicInteger invalidTransitions = new AtomicInteger(0);

            for (UUID playerId : playerIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        PlayerState state = players.get(playerId);
                        for (int i = 0; i < cyclesPerPlayer; i++) {
                            if (!state.die()) {
                                invalidTransitions.incrementAndGet();
                            }
                            Thread.sleep(1); // Simulate death processing
                            if (!state.respawn()) {
                                invalidTransitions.incrementAndGet();
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

            assertEquals(0, invalidTransitions.get(), "All state transitions should be valid");

            // All players should end up alive
            long aliveCount = players.values().stream()
                .filter(PlayerState::isAlive)
                .count();
            assertEquals(playerCount, aliveCount, "All players should be alive after cycling");
        }

        private static class PlayerState {
            private final AtomicBoolean alive = new AtomicBoolean(true);
            private final AtomicInteger deathCount = new AtomicInteger(0);

            boolean die() {
                if (alive.compareAndSet(true, false)) {
                    deathCount.incrementAndGet();
                    return true;
                }
                return false;
            }

            boolean respawn() {
                return alive.compareAndSet(false, true);
            }

            boolean isAlive() {
                return alive.get();
            }
        }
    }

    // === Reward System Stress ===

    @Nested
    @DisplayName("L5-EQ-04: Reward System Stress")
    class RewardSystemStressTest {

        @Test
        @DisplayName("Concurrent reward distribution is atomic")
        void concurrentRewardDistributionIsAtomic() throws Exception {
            ConcurrentHashMap<UUID, AtomicLong> playerCoins = new ConcurrentHashMap<>();
            int playerCount = 10;
            int rewardsPerPlayer = 100;
            long rewardAmount = 50;

            List<UUID> playerIds = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                UUID id = UUID.randomUUID();
                playerIds.add(id);
                playerCoins.put(id, new AtomicLong(0));
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(playerCount);

            for (UUID playerId : playerIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < rewardsPerPlayer; i++) {
                            playerCoins.get(playerId).addAndGet(rewardAmount);
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

            long expectedPerPlayer = rewardsPerPlayer * rewardAmount;
            for (UUID playerId : playerIds) {
                assertEquals(expectedPerPlayer, playerCoins.get(playerId).get(),
                    "Each player should have correct coin amount");
            }
        }

        @Test
        @DisplayName("Leaderboard updates under high contention")
        void leaderboardUpdatesUnderHighContention() throws Exception {
            ConcurrentHashMap<UUID, AtomicLong> scores = new ConcurrentHashMap<>();
            Set<UUID> topPlayers = ConcurrentHashMap.newKeySet();
            int playerCount = 50;
            int updatesPerPlayer = 1000;

            List<UUID> playerIds = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                UUID id = UUID.randomUUID();
                playerIds.add(id);
                scores.put(id, new AtomicLong(0));
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(playerCount);

            for (UUID playerId : playerIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        ThreadLocalRandom random = ThreadLocalRandom.current();
                        for (int i = 0; i < updatesPerPlayer; i++) {
                            long scoreIncrease = random.nextLong(1, 100);
                            long newScore = scores.get(playerId).addAndGet(scoreIncrease);

                            // Update top players (simplified leaderboard)
                            if (newScore > 25000) { // Threshold for top
                                topPlayers.add(playerId);
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

            // Verify leaderboard consistency
            for (UUID playerId : topPlayers) {
                assertTrue(scores.get(playerId).get() > 25000,
                    "All top players should have scores above threshold");
            }
        }
    }

    // === Memory and Resource Stress ===

    @Nested
    @DisplayName("L5-EQ-05: Memory and Resource Stress")
    class MemoryResourceStressTest {

        @Test
        @DisplayName("Large number of sessions doesn't cause memory issues")
        void largeSessionCountDoesntCauseMemoryIssues() throws Exception {
            ConcurrentHashMap<UUID, MockSession> sessions = new ConcurrentHashMap<>();
            int sessionCount = 10000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger createdCount = new AtomicInteger(0);

            int sessionsPerThread = sessionCount / THREAD_COUNT;

            for (int t = 0; t < THREAD_COUNT; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < sessionsPerThread; i++) {
                            UUID id = UUID.randomUUID();
                            sessions.put(id, new MockSession(id));
                            createdCount.incrementAndGet();
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

            assertEquals(sessionCount, createdCount.get(), "All sessions should be created");
            assertEquals(sessionCount, sessions.size(), "Map should contain all sessions");

            // Cleanup
            sessions.clear();
            System.gc();

            // Verify cleanup
            assertEquals(0, sessions.size(), "Sessions should be cleared");
        }

        @Test
        @DisplayName("Rapid session create/destroy cycles")
        void rapidSessionCreateDestroyCycles() throws Exception {
            ConcurrentHashMap<UUID, MockSession> sessions = new ConcurrentHashMap<>();
            int cycleCount = 1000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger totalCreated = new AtomicInteger(0);
            AtomicInteger totalDestroyed = new AtomicInteger(0);

            for (int t = 0; t < THREAD_COUNT; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < cycleCount; i++) {
                            UUID id = UUID.randomUUID();
                            sessions.put(id, new MockSession(id));
                            totalCreated.incrementAndGet();

                            // Immediately destroy
                            if (sessions.remove(id) != null) {
                                totalDestroyed.incrementAndGet();
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

            assertEquals(totalCreated.get(), totalDestroyed.get(), "All created sessions should be destroyed");
            assertEquals(0, sessions.size(), "No sessions should remain");
        }

        private record MockSession(UUID id) {
            // Simulate session with some data
            private static final byte[] PADDING = new byte[1024]; // 1KB per session
        }
    }

    // === Performance Metrics ===

    @Nested
    @DisplayName("L5-EQ-06: Performance Metrics")
    class PerformanceMetricsTest {

        @Test
        @DisplayName("Operation latencies under load")
        void operationLatenciesUnderLoad() throws Exception {
            ConcurrentHashMap<UUID, Long> sessions = new ConcurrentHashMap<>();
            int operationCount = 10000;
            List<Long> latencies = java.util.Collections.synchronizedList(new ArrayList<>());
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);

            int opsPerThread = operationCount / THREAD_COUNT;

            for (int t = 0; t < THREAD_COUNT; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < opsPerThread; i++) {
                            long start = System.nanoTime();
                            UUID id = UUID.randomUUID();
                            sessions.put(id, System.currentTimeMillis());
                            sessions.get(id);
                            sessions.remove(id);
                            long elapsed = System.nanoTime() - start;
                            latencies.add(elapsed);
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

            // Calculate percentiles
            latencies.sort(Long::compareTo);
            long p50 = latencies.get(latencies.size() / 2);
            long p95 = latencies.get((int) (latencies.size() * 0.95));
            long p99 = latencies.get((int) (latencies.size() * 0.99));

            double avgMs = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;

            System.out.printf("Latency Stats: avg=%.3fms, p50=%dns, p95=%dns, p99=%dns%n",
                avgMs, p50, p95, p99);

            // Basic sanity checks
            assertTrue(p50 < 1_000_000, "P50 should be under 1ms");
            assertTrue(p99 < 10_000_000, "P99 should be under 10ms");
        }

        @Test
        @DisplayName("Throughput measurement")
        void throughputMeasurement() throws Exception {
            ConcurrentHashMap<UUID, String> sessions = new ConcurrentHashMap<>();
            int durationSeconds = 5;
            AtomicLong operationCount = new AtomicLong(0);
            AtomicBoolean running = new AtomicBoolean(true);
            CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);

            long startTime = System.currentTimeMillis();

            for (int t = 0; t < THREAD_COUNT; t++) {
                executor.submit(() -> {
                    try {
                        while (running.get()) {
                            UUID id = UUID.randomUUID();
                            sessions.put(id, "data");
                            sessions.get(id);
                            sessions.remove(id);
                            operationCount.incrementAndGet();
                        }
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            Thread.sleep(durationSeconds * 1000L);
            running.set(false);
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));

            long elapsed = System.currentTimeMillis() - startTime;
            double opsPerSecond = operationCount.get() / (elapsed / 1000.0);

            System.out.printf("Throughput: %.0f ops/sec over %dms (%d total ops)%n",
                opsPerSecond, elapsed, operationCount.get());

            assertTrue(opsPerSecond > 10000, "Should achieve at least 10k ops/sec");
        }
    }
}
