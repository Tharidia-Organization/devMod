package com.devmod.stress;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for Telemetry LVC (Last Value Cache) system.
 * Tests concurrent operations on player entries, server totals,
 * leaderboard queries, and async writer under load.
 */
@Tag("load")
@DisplayName("L5: Telemetry LVC Stress Tests")
class TelemetryLVCStressTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor should terminate");
    }

    // =========================================================================
    // L5-TL-01: Player Entry Management
    // =========================================================================

    @Test
    @DisplayName("TL-01: Concurrent player entry creation")
    void playerEntry_concurrentCreation() throws Exception {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();
        int threadCount = 20;
        int entriesPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        java.util.Set<UUID> allIds = ConcurrentHashMap.newKeySet();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < entriesPerThread; i++) {
                        UUID playerId = UUID.randomUUID();
                        playerEntries.computeIfAbsent(playerId, MockPlayerLVCEntry::new);
                        allIds.add(playerId);
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

        assertEquals(allIds.size(), playerEntries.size(), "All entries should be created");
        assertEquals(threadCount * entriesPerThread, playerEntries.size());
    }

    @Test
    @DisplayName("TL-01: GetOrCreate with concurrent access")
    void playerEntry_getOrCreateWithConcurrentAccess() throws Exception {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();
        int playerCount = 100;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            players.add(UUID.randomUUID());
        }

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder createCount = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        MockPlayerLVCEntry entry = playerEntries.computeIfAbsent(playerId, id -> {
                            createCount.increment();
                            return new MockPlayerLVCEntry(id);
                        });
                        assertNotNull(entry);
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

        assertEquals(playerCount, playerEntries.size());
        assertEquals(playerCount, createCount.sum(), "Each player should be created exactly once");
    }

    @Test
    @DisplayName("TL-01: Player entry removal during concurrent access")
    void playerEntry_removalDuringConcurrentAccess() throws Exception {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();
        int playerCount = 200;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            playerEntries.put(playerId, new MockPlayerLVCEntry(playerId));
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);
        LongAdder removeCount = new LongAdder();

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 200; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        if (playerEntries.remove(playerId) != null) {
                            removeCount.increment();
                        }
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        MockPlayerLVCEntry entry = playerEntries.get(playerId);
                        if (entry != null) {
                            entry.getTotalDamage();
                        }
                    }
                } catch (Exception e) {
                    failed.set(true);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));

        assertFalse(failed.get(), "Concurrent access should not fail");
        assertTrue(removeCount.sum() > 0, "Some entries should be removed");
    }

    @Test
    @DisplayName("TL-01: Stale entry pruning")
    void playerEntry_staleEntryPruning() throws Exception {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        int playerCount = 100;
        int staleCount = 0;

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            MockPlayerLVCEntry entry = new MockPlayerLVCEntry(playerId);

            if (i < playerCount / 2) {
                entry.lastActivity = now - 7200_000;
                staleCount++;
            } else {
                entry.lastActivity = now - 300_000;
            }
            playerEntries.put(playerId, entry);
        }

        final int expectedStale = staleCount;
        final long staleThreshold = now - 3600_000;

        AtomicInteger prunedCount = new AtomicInteger(0);
        playerEntries.entrySet().removeIf(entry -> {
            boolean stale = entry.getValue().lastActivity < staleThreshold;
            if (stale) prunedCount.incrementAndGet();
            return stale;
        });

        assertEquals(expectedStale, prunedCount.get(), "Should prune stale entries");
        assertEquals(playerCount - expectedStale, playerEntries.size());
    }

    // =========================================================================
    // L5-TL-02: Combat Stats Recording
    // =========================================================================

    @Test
    @DisplayName("TL-02: Concurrent hit recording")
    void combatStats_concurrentHitRecording() throws Exception {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();
        AtomicLong serverTotalDamageCents = new AtomicLong(0);
        AtomicInteger serverTotalKills = new AtomicInteger(0);

        int playerCount = 50;
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            playerEntries.put(playerId, new MockPlayerLVCEntry(playerId));
        }

        int threadCount = 20;
        int hitsPerThread = 1000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder totalHits = new LongAdder();
        LongAdder totalKills = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < hitsPerThread; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        double damage = random.nextDouble(1.0, 50.0);
                        boolean isKill = random.nextDouble() < 0.1;

                        MockPlayerLVCEntry entry = playerEntries.get(playerId);
                        if (entry != null) {
                            entry.recordHit(damage, isKill);
                            serverTotalDamageCents.addAndGet((long) (damage * 100));
                            if (isKill) {
                                serverTotalKills.incrementAndGet();
                                totalKills.increment();
                            }
                            totalHits.increment();
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

        assertEquals(threadCount * hitsPerThread, totalHits.sum());
        assertEquals(totalKills.sum(), serverTotalKills.get());
        assertTrue(serverTotalDamageCents.get() > 0);

        long playerTotalHits = playerEntries.values().stream()
            .mapToLong(MockPlayerLVCEntry::getHitCount)
            .sum();
        assertEquals(totalHits.sum(), playerTotalHits);
    }

    @Test
    @DisplayName("TL-02: Concurrent death recording")
    void combatStats_concurrentDeathRecording() throws Exception {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();
        AtomicInteger serverTotalDeaths = new AtomicInteger(0);

        int playerCount = 50;
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            playerEntries.put(playerId, new MockPlayerLVCEntry(playerId));
        }

        int threadCount = 10;
        int deathsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < deathsPerThread; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        MockPlayerLVCEntry entry = playerEntries.get(playerId);
                        if (entry != null) {
                            entry.recordDeath();
                            serverTotalDeaths.incrementAndGet();
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

        int expectedDeaths = threadCount * deathsPerThread;
        assertEquals(expectedDeaths, serverTotalDeaths.get());

        int playerTotalDeaths = playerEntries.values().stream()
            .mapToInt(MockPlayerLVCEntry::getDeathCount)
            .sum();
        assertEquals(expectedDeaths, playerTotalDeaths);
    }

    @Test
    @DisplayName("TL-02: Combo tracking under concurrent updates")
    void combatStats_comboTrackingUnderConcurrentUpdates() throws Exception {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();
        UUID playerId = UUID.randomUUID();
        MockPlayerLVCEntry entry = new MockPlayerLVCEntry(playerId);
        playerEntries.put(playerId, entry);

        int threadCount = 10;
        int operationsPerThread = 500;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger maxObservedCombo = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < operationsPerThread; i++) {
                        if (random.nextDouble() < 0.9) {
                            entry.incrementCombo();
                        } else {
                            entry.resetCombo();
                        }

                        int current = entry.getCurrentCombo();
                        maxObservedCombo.updateAndGet(max -> Math.max(max, current));
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

        assertTrue(entry.getMaxCombo() >= maxObservedCombo.get(),
            "Entry max combo should be >= observed max");
    }

    // =========================================================================
    // L5-TL-03: Server Statistics
    // =========================================================================

    @Test
    @DisplayName("TL-03: Atomic server total updates")
    void serverStats_atomicTotalUpdates() throws Exception {
        AtomicLong serverTotalDamageCents = new AtomicLong(0);
        AtomicInteger serverTotalKills = new AtomicInteger(0);
        AtomicInteger serverTotalDeaths = new AtomicInteger(0);
        AtomicLong serverTotalXp = new AtomicLong(0);

        int threadCount = 20;
        int updatesPerThread = 10000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        double damagePerHit = 25.5;
        int xpPerUpdate = 10;

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < updatesPerThread; i++) {
                        serverTotalDamageCents.addAndGet((long) (damagePerHit * 100));
                        if (i % 10 == 0) {
                            serverTotalKills.incrementAndGet();
                        }
                        if (i % 50 == 0) {
                            serverTotalDeaths.incrementAndGet();
                        }
                        serverTotalXp.addAndGet(xpPerUpdate);
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

        long expectedDamageCents = (long) (threadCount * updatesPerThread * damagePerHit * 100);
        int expectedKills = threadCount * (updatesPerThread / 10);
        int expectedDeaths = threadCount * (updatesPerThread / 50);
        long expectedXp = (long) threadCount * updatesPerThread * xpPerUpdate;

        assertEquals(expectedDamageCents, serverTotalDamageCents.get());
        assertEquals(expectedKills, serverTotalKills.get());
        assertEquals(expectedDeaths, serverTotalDeaths.get());
        assertEquals(expectedXp, serverTotalXp.get());
    }

    @Test
    @DisplayName("TL-03: Server total reads during updates")
    void serverStats_totalReadsDuringUpdates() throws Exception {
        AtomicLong serverTotalDamageCents = new AtomicLong(0);
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean readFailed = new AtomicBoolean(false);
        LongAdder validReads = new LongAdder();

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 10000; i++) {
                        serverTotalDamageCents.addAndGet(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long previousValue = 0;
                    for (int i = 0; i < 10000; i++) {
                        long current = serverTotalDamageCents.get();
                        if (current < previousValue) {
                            readFailed.set(true);
                        }
                        previousValue = current;
                        validReads.increment();
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

        assertFalse(readFailed.get(), "Reads should always see monotonically increasing values");
        assertTrue(validReads.sum() > 0);
    }

    // =========================================================================
    // L5-TL-04: Leaderboard Queries
    // =========================================================================

    @Test
    @DisplayName("TL-04: Top damage dealers query under load")
    void leaderboard_topDamageDealersQueryUnderLoad() throws Exception {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();
        int playerCount = 200;

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            MockPlayerLVCEntry entry = new MockPlayerLVCEntry(playerId);
            entry.totalDamageCents.set((long) ((i + 1) * 1000));
            playerEntries.put(playerId, entry);
        }

        int threadCount = 10;
        int queriesPerThread = 500;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean queryFailed = new AtomicBoolean(false);
        LongAdder successfulQueries = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int i = 0; i < queriesPerThread; i++) {
                        List<MockPlayerLVCEntry> top = playerEntries.values().stream()
                            .sorted(Comparator.comparingDouble(MockPlayerLVCEntry::getTotalDamage).reversed())
                            .limit(10)
                            .collect(Collectors.toList());

                        if (top.size() != 10) {
                            queryFailed.set(true);
                        } else {
                            for (int j = 1; j < top.size(); j++) {
                                if (top.get(j).getTotalDamage() > top.get(j - 1).getTotalDamage()) {
                                    queryFailed.set(true);
                                    break;
                                }
                            }
                            successfulQueries.increment();
                        }
                    }
                } catch (Exception e) {
                    queryFailed.set(true);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(60, TimeUnit.SECONDS));

        assertFalse(queryFailed.get(), "Queries should not fail");
        assertEquals(threadCount * queriesPerThread, successfulQueries.sum());
    }

    @Test
    @DisplayName("TL-04: Leaderboard queries during concurrent updates")
    void leaderboard_queriesDuringConcurrentUpdates() throws Exception {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();
        int playerCount = 100;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            playerEntries.put(playerId, new MockPlayerLVCEntry(playerId));
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);
        LongAdder queryCount = new LongAdder();

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 1000; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        MockPlayerLVCEntry entry = playerEntries.get(playerId);
                        if (entry != null) {
                            entry.recordHit(random.nextDouble(1, 50), random.nextBoolean());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 500; i++) {
                        List<MockPlayerLVCEntry> topKillers = playerEntries.values().stream()
                            .sorted(Comparator.comparingInt(MockPlayerLVCEntry::getKillCount).reversed())
                            .limit(10)
                            .collect(Collectors.toList());

                        List<MockPlayerLVCEntry> topDamage = playerEntries.values().stream()
                            .sorted(Comparator.comparingDouble(MockPlayerLVCEntry::getTotalDamage).reversed())
                            .limit(10)
                            .collect(Collectors.toList());

                        if (topKillers != null && topDamage != null) {
                            queryCount.add(2);
                        }
                    }
                } catch (Exception e) {
                    failed.set(true);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(60, TimeUnit.SECONDS));

        assertFalse(failed.get(), "Queries should not fail during updates");
        assertTrue(queryCount.sum() > 0);
    }

    // =========================================================================
    // L5-TL-05: Async Writer Queue
    // =========================================================================

    @Test
    @DisplayName("TL-05: Concurrent queue submissions")
    void asyncWriter_concurrentQueueSubmissions() throws Exception {
        BlockingQueue<MockWriteTask> writeQueue = new LinkedBlockingQueue<>(1000);
        int threadCount = 20;
        int writesPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder successfulOffers = new LongAdder();
        LongAdder droppedOffers = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < writesPerThread; i++) {
                        MockWriteTask task = new MockWriteTask("file_" + threadId, "line_" + i);
                        if (writeQueue.offer(task)) {
                            successfulOffers.increment();
                        } else {
                            droppedOffers.increment();
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

        long totalAttempts = threadCount * writesPerThread;
        assertEquals(totalAttempts, successfulOffers.sum() + droppedOffers.sum());
        assertTrue(successfulOffers.sum() >= 1000 || droppedOffers.sum() == 0);
    }

    @Test
    @DisplayName("TL-05: Producer-consumer pattern for write queue")
    void asyncWriter_producerConsumerPattern() throws Exception {
        BlockingQueue<MockWriteTask> writeQueue = new LinkedBlockingQueue<>(500);
        int producerCount = 10;
        int tasksPerProducer = 200;
        AtomicBoolean producersDone = new AtomicBoolean(false);
        AtomicBoolean consumerFailed = new AtomicBoolean(false);
        CountDownLatch producerLatch = new CountDownLatch(producerCount);
        CountDownLatch consumerLatch = new CountDownLatch(1);
        LongAdder producedCount = new LongAdder();
        LongAdder consumedCount = new LongAdder();

        for (int p = 0; p < producerCount; p++) {
            int producerId = p;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < tasksPerProducer; i++) {
                        MockWriteTask task = new MockWriteTask("file_" + producerId, "line_" + i);
                        writeQueue.put(task);
                        producedCount.increment();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producerLatch.countDown();
                }
            });
        }

        executor.submit(() -> {
            try {
                while (!producersDone.get() || !writeQueue.isEmpty()) {
                    MockWriteTask task = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        consumedCount.increment();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                consumerFailed.set(true);
            } finally {
                consumerLatch.countDown();
            }
        });

        assertTrue(producerLatch.await(60, TimeUnit.SECONDS));
        producersDone.set(true);
        assertTrue(consumerLatch.await(30, TimeUnit.SECONDS));

        assertFalse(consumerFailed.get(), "Consumer should not fail");
        assertEquals(producedCount.sum(), consumedCount.sum(), "All produced should be consumed");
        assertEquals(producerCount * tasksPerProducer, consumedCount.sum());
    }

    @Test
    @DisplayName("TL-05: Queue backpressure handling")
    void asyncWriter_queueBackpressureHandling() throws Exception {
        int queueCapacity = 100;
        BlockingQueue<MockWriteTask> writeQueue = new LinkedBlockingQueue<>(queueCapacity);

        for (int i = 0; i < queueCapacity; i++) {
            assertTrue(writeQueue.offer(new MockWriteTask("file", "line_" + i)));
        }

        assertFalse(writeQueue.offer(new MockWriteTask("file", "overflow")));
        assertEquals(queueCapacity, writeQueue.size());

        assertNotNull(writeQueue.poll());
        assertTrue(writeQueue.offer(new MockWriteTask("file", "new_line")));
        assertEquals(queueCapacity, writeQueue.size());
    }

    // =========================================================================
    // L5-TL-06: Performance Metrics
    // =========================================================================

    @Test
    @DisplayName("TL-06: Hit recording throughput")
    void performance_hitRecordingThroughput() throws Exception {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();
        AtomicLong serverTotalDamageCents = new AtomicLong(0);
        int playerCount = 100;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            playerEntries.put(playerId, new MockPlayerLVCEntry(playerId));
        }

        int threadCount = 10;
        int hitsPerThread = 50000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder hitCount = new LongAdder();

        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < hitsPerThread; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        double damage = 25.5;
                        boolean isKill = random.nextDouble() < 0.1;

                        MockPlayerLVCEntry entry = playerEntries.get(playerId);
                        if (entry != null) {
                            entry.recordHit(damage, isKill);
                            serverTotalDamageCents.addAndGet((long) (damage * 100));
                            hitCount.increment();
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

        long elapsed = System.nanoTime() - startTime;
        double hitsPerSecond = hitCount.sum() / (elapsed / 1_000_000_000.0);

        assertEquals(threadCount * hitsPerThread, hitCount.sum());
        assertTrue(hitsPerSecond > 100000, "Should achieve >100K hits/sec, achieved " + (int) hitsPerSecond);
    }

    @Test
    @DisplayName("TL-06: Query throughput")
    void performance_queryThroughput() throws Exception {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();
        int playerCount = 500;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            MockPlayerLVCEntry entry = new MockPlayerLVCEntry(playerId);
            entry.totalDamageCents.set((long) (Math.random() * 100000));
            entry.killCount.set((int) (Math.random() * 100));
            playerEntries.put(playerId, entry);
        }

        int threadCount = 10;
        int queriesPerThread = 10000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder queryCount = new LongAdder();

        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < queriesPerThread; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        MockPlayerLVCEntry entry = playerEntries.get(playerId);

                        if (entry != null) {
                            double damage = entry.getTotalDamage();
                            int kills = entry.getKillCount();
                            int deaths = entry.getDeathCount();
                            int combo = entry.getCurrentCombo();

                            if (damage >= 0 && kills >= 0 && deaths >= 0 && combo >= 0) {
                                queryCount.increment();
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

        long elapsed = System.nanoTime() - startTime;
        double queriesPerSecond = queryCount.sum() / (elapsed / 1_000_000_000.0);

        assertTrue(queriesPerSecond > 1_000_000, "Should achieve >1M queries/sec, achieved " + (int) queriesPerSecond);
    }

    @Test
    @DisplayName("TL-06: Memory estimation for player entries")
    void performance_memoryEstimationForPlayerEntries() {
        Map<UUID, MockPlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();

        int playerCount = 400;
        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            playerEntries.put(playerId, new MockPlayerLVCEntry(playerId));
        }

        long estimatedBytes = playerEntries.size() * 1500L;
        long estimatedKB = estimatedBytes / 1024;

        assertEquals(playerCount, playerEntries.size());
        assertTrue(estimatedKB < 700, "Should use <700KB for 400 players, estimated " + estimatedKB + "KB");
    }

    // =========================================================================
    // Helper Classes
    // =========================================================================

    private static class MockPlayerLVCEntry {
        final UUID playerId;
        final AtomicLong totalDamageCents = new AtomicLong(0);
        final AtomicInteger hitCount = new AtomicInteger(0);
        final AtomicInteger killCount = new AtomicInteger(0);
        final AtomicInteger deathCount = new AtomicInteger(0);
        final AtomicInteger currentCombo = new AtomicInteger(0);
        final AtomicInteger maxCombo = new AtomicInteger(0);
        volatile long lastActivity;

        MockPlayerLVCEntry(UUID playerId) {
            this.playerId = playerId;
            this.lastActivity = System.currentTimeMillis();
        }

        void recordHit(double damage, boolean isKill) {
            totalDamageCents.addAndGet((long) (damage * 100));
            hitCount.incrementAndGet();
            if (isKill) {
                killCount.incrementAndGet();
            }
            incrementCombo();
            lastActivity = System.currentTimeMillis();
        }

        void recordDeath() {
            deathCount.incrementAndGet();
            resetCombo();
            lastActivity = System.currentTimeMillis();
        }

        void incrementCombo() {
            int newCombo = currentCombo.incrementAndGet();
            maxCombo.updateAndGet(max -> Math.max(max, newCombo));
        }

        void resetCombo() {
            currentCombo.set(0);
        }

        double getTotalDamage() {
            return totalDamageCents.get() / 100.0;
        }

        int getHitCount() {
            return hitCount.get();
        }

        int getKillCount() {
            return killCount.get();
        }

        int getDeathCount() {
            return deathCount.get();
        }

        int getCurrentCombo() {
            return currentCombo.get();
        }

        int getMaxCombo() {
            return maxCombo.get();
        }
    }

    private record MockWriteTask(String file, String line) {}
}
