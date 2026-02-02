package com.devmod.stress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for Dialog System components.
 * Tests concurrent operations on dialog sessions, registry,
 * rate limiting, and event bus under load.
 */
@Tag("load")
@DisplayName("L5: Dialog System Stress Tests")
class DialogSystemStressTest {

    private static final int MAX_ACTIONS_PER_MINUTE = 60;
    private static final long MAX_SESSION_DURATION_MS = 600_000; // 10 minutes

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
    // L5-DS-01: Dialog Session Management
    // =========================================================================

    @Test
    @DisplayName("DS-01: Concurrent session creation doesn't lose data")
    void session_concurrentCreationDoesNotLoseData() throws Exception {
        Map<UUID, MockDialogSession> activeSessions = new ConcurrentHashMap<>();
        int threadCount = 20;
        int sessionsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Set<UUID> allPlayers = ConcurrentHashMap.newKeySet();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < sessionsPerThread; i++) {
                        UUID playerId = UUID.randomUUID();
                        UUID npcId = UUID.randomUUID();
                        MockDialogSession session = new MockDialogSession(playerId, npcId, "dialog_" + i, "node_0");
                        activeSessions.put(playerId, session);
                        allPlayers.add(playerId);
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

        assertEquals(allPlayers.size(), activeSessions.size(), "All sessions should be created");
    }

    @Test
    @DisplayName("DS-01: Session open/close cycles under concurrent load")
    void session_openCloseCyclesUnderConcurrentLoad() throws Exception {
        Map<UUID, MockDialogSession> activeSessions = new ConcurrentHashMap<>();
        int playerCount = 100;
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            players.add(UUID.randomUUID());
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder openCount = new LongAdder();
        LongAdder closeCount = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));

                        if (random.nextBoolean()) {
                            UUID npcId = UUID.randomUUID();
                            activeSessions.put(playerId, new MockDialogSession(playerId, npcId, "dialog", "entry"));
                            openCount.increment();
                        } else {
                            if (activeSessions.remove(playerId) != null) {
                                closeCount.increment();
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

        assertTrue(openCount.sum() > 0, "Should have opens");
        assertTrue(closeCount.sum() > 0, "Should have closes");
        for (UUID playerId : activeSessions.keySet()) {
            assertNotNull(activeSessions.get(playerId));
        }
    }

    @Test
    @DisplayName("DS-01: Session lookup during concurrent modifications")
    void session_lookupDuringConcurrentModifications() throws Exception {
        Map<UUID, MockDialogSession> activeSessions = new ConcurrentHashMap<>();
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean lookupFailed = new AtomicBoolean(false);
        LongAdder successfulLookups = new LongAdder();

        List<UUID> playerIds = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            UUID playerId = UUID.randomUUID();
            playerIds.add(playerId);
            activeSessions.put(playerId, new MockDialogSession(playerId, UUID.randomUUID(), "dialog", "node"));
        }

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < 500; i++) {
                        UUID playerId = playerIds.get(random.nextInt(playerIds.size()));
                        if (random.nextBoolean()) {
                            activeSessions.put(playerId, new MockDialogSession(playerId, UUID.randomUUID(), "updated", "updated_node"));
                        } else {
                            activeSessions.remove(playerId);
                        }
                    }
                } catch (Exception e) {
                    lookupFailed.set(true);
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
                    for (int i = 0; i < 1000; i++) {
                        UUID playerId = playerIds.get(random.nextInt(playerIds.size()));
                        MockDialogSession session = activeSessions.get(playerId);
                        if (session != null && session.playerId != null) {
                            successfulLookups.increment();
                        }
                    }
                } catch (Exception e) {
                    lookupFailed.set(true);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));

        assertFalse(lookupFailed.get(), "Lookups should not fail");
        assertTrue(successfulLookups.sum() > 0, "Should have some successful lookups");
    }

    @Test
    @DisplayName("DS-01: Session state updates with node transitions")
    void session_stateUpdatesWithNodeTransitions() throws Exception {
        Map<UUID, MockDialogSession> activeSessions = new ConcurrentHashMap<>();
        int sessionCount = 200;

        for (int i = 0; i < sessionCount; i++) {
            UUID playerId = UUID.randomUUID();
            activeSessions.put(playerId, new MockDialogSession(playerId, UUID.randomUUID(), "dialog", "node_0"));
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder transitionCount = new LongAdder();

        List<UUID> playerIds = new ArrayList<>(activeSessions.keySet());

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        UUID playerId = playerIds.get(random.nextInt(playerIds.size()));
                        MockDialogSession current = activeSessions.get(playerId);

                        if (current != null) {
                            String newNode = "node_" + random.nextInt(10);
                            activeSessions.compute(playerId, (k, v) -> {
                                if (v != null) {
                                    transitionCount.increment();
                                    return v.withNode(newNode);
                                }
                                return null;
                            });
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

        assertTrue(transitionCount.sum() > 0, "Should have transitions");
    }

    // =========================================================================
    // L5-DS-02: Rate Limiting Under Load
    // =========================================================================

    @Test
    @DisplayName("DS-02: Rate limiter is thread-safe with synchronized check-then-act")
    void rateLimit_threadSafeWithSynchronizedCheckThenAct() throws Exception {
        Map<UUID, List<Long>> actionTimestamps = new ConcurrentHashMap<>();
        UUID playerId = UUID.randomUUID();
        actionTimestamps.put(playerId, Collections.synchronizedList(new ArrayList<>()));

        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger acceptedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 10; i++) {
                        if (checkRateLimit(actionTimestamps, playerId, MAX_ACTIONS_PER_MINUTE)) {
                            acceptedCount.incrementAndGet();
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
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        assertEquals(MAX_ACTIONS_PER_MINUTE, acceptedCount.get(), "Should accept exactly rate limit");
        assertEquals(threadCount * 10 - MAX_ACTIONS_PER_MINUTE, rejectedCount.get(), "Should reject excess actions");
    }

    @Test
    @DisplayName("DS-02: Multiple players rate limited independently")
    void rateLimit_multiplePlayersIndependently() throws Exception {
        Map<UUID, List<Long>> actionTimestamps = new ConcurrentHashMap<>();
        int playerCount = 10;
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            actionTimestamps.put(playerId, Collections.synchronizedList(new ArrayList<>()));
        }

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Map<UUID, AtomicInteger> acceptedPerPlayer = new ConcurrentHashMap<>();
        players.forEach(p -> acceptedPerPlayer.put(p, new AtomicInteger(0)));

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < 100; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        if (checkRateLimit(actionTimestamps, playerId, MAX_ACTIONS_PER_MINUTE)) {
                            acceptedPerPlayer.get(playerId).incrementAndGet();
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

        for (UUID playerId : players) {
            assertTrue(acceptedPerPlayer.get(playerId).get() <= MAX_ACTIONS_PER_MINUTE, "Player should not exceed rate limit");
        }
    }

    @Test
    @DisplayName("DS-02: Rate limit cleanup removes old timestamps")
    void rateLimit_cleanupRemovesOldTimestamps() throws Exception {
        Map<UUID, List<Long>> actionTimestamps = new ConcurrentHashMap<>();
        int playerCount = 50;

        long now = System.currentTimeMillis();
        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            List<Long> timestamps = Collections.synchronizedList(new ArrayList<>());

            for (int j = 0; j < 100; j++) {
                if (j < 50) {
                    timestamps.add(now - 120_000); // 2 minutes ago (old)
                } else {
                    timestamps.add(now - 30_000); // 30 seconds ago (fresh)
                }
            }
            actionTimestamps.put(playerId, timestamps);
        }

        long oneMinuteAgo = now - 60_000;
        for (List<Long> timestamps : actionTimestamps.values()) {
            synchronized (timestamps) {
                timestamps.removeIf(t -> t < oneMinuteAgo);
            }
        }

        for (List<Long> timestamps : actionTimestamps.values()) {
            synchronized (timestamps) {
                assertEquals(50, timestamps.size(), "Should have removed old timestamps");
                for (Long t : timestamps) {
                    assertTrue(t >= oneMinuteAgo, "All remaining should be fresh");
                }
            }
        }
    }

    // =========================================================================
    // L5-DS-03: Dialog Registry Operations
    // =========================================================================

    @Test
    @DisplayName("DS-03: Concurrent dialog set registration")
    void registry_concurrentDialogSetRegistration() throws Exception {
        Map<String, MockDialogSet> customDialogs = new ConcurrentHashMap<>();
        int threadCount = 20;
        int dialogsPerThread = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Set<String> allIds = ConcurrentHashMap.newKeySet();

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < dialogsPerThread; i++) {
                        String dialogId = "dialog_t" + threadId + "_" + i;
                        customDialogs.put(dialogId, new MockDialogSet(dialogId, "Dialog " + i, 1));
                        allIds.add(dialogId);
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

        assertEquals(allIds.size(), customDialogs.size());
        assertEquals(threadCount * dialogsPerThread, customDialogs.size());
    }

    @Test
    @DisplayName("DS-03: Concurrent read and write to dialog registry")
    void registry_concurrentReadAndWrite() throws Exception {
        Map<String, MockDialogSet> registry = new ConcurrentHashMap<>();

        for (int i = 0; i < 100; i++) {
            registry.put("dialog_" + i, new MockDialogSet("dialog_" + i, "Dialog " + i, 1));
        }

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);
        LongAdder reads = new LongAdder();
        LongAdder writes = new LongAdder();

        for (int t = 0; t < threadCount / 2; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 100; i++) {
                        String dialogId = "new_dialog_t" + threadId + "_" + i;
                        registry.put(dialogId, new MockDialogSet(dialogId, "New Dialog", 1));
                        writes.increment();
                    }
                } catch (Exception e) {
                    failed.set(true);
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
                        String dialogId = "dialog_" + random.nextInt(100);
                        MockDialogSet dialog = registry.get(dialogId);
                        if (dialog != null) {
                            reads.increment();
                        }
                        int count = 0;
                        for (String ignored : registry.keySet()) {
                            count++;
                            if (count > 50) break;
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

        assertFalse(failed.get(), "Operations should not fail");
        assertTrue(reads.sum() > 0);
        assertTrue(writes.sum() > 0);
    }

    @Test
    @DisplayName("DS-03: Double-checked locking for lazy initialization")
    void registry_doubleCheckedLockingForLazyInit() throws Exception {
        AtomicBoolean loaded = new AtomicBoolean(false);
        Object loadLock = new Object();
        AtomicInteger loadCount = new AtomicInteger(0);
        Map<String, MockDialogSet> registry = new ConcurrentHashMap<>();

        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    if (!loaded.get()) {
                        synchronized (loadLock) {
                            if (!loaded.get()) {
                                Thread.sleep(10);
                                for (int i = 0; i < 10; i++) {
                                    registry.put("loaded_" + i, new MockDialogSet("loaded_" + i, "Loaded", 1));
                                }
                                loadCount.incrementAndGet();
                                loaded.set(true);
                            }
                        }
                    }

                    assertTrue(registry.containsKey("loaded_0"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));

        assertEquals(1, loadCount.get(), "Load should happen exactly once");
        assertEquals(10, registry.size());
    }

    @Test
    @DisplayName("DS-03: Dialog revision conflict handling")
    void registry_revisionConflictHandling() throws Exception {
        Map<String, MockDialogSet> registry = new ConcurrentHashMap<>();
        String dialogId = "shared_dialog";
        registry.put(dialogId, new MockDialogSet(dialogId, "Original", 1));

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successfulUpdates = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int attempt = 0; attempt < 5; attempt++) {
                        MockDialogSet current = registry.get(dialogId);
                        int expectedRevision = current.revision;

                        Thread.sleep(1);

                        boolean updated = registry.compute(dialogId, (k, v) -> {
                            if (v != null && v.revision == expectedRevision) {
                                return new MockDialogSet(dialogId, "Updated by " + threadId, v.revision + 1);
                            }
                            return v;
                        }).revision != expectedRevision;

                        if (updated) {
                            successfulUpdates.incrementAndGet();
                        } else {
                            conflictCount.incrementAndGet();
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

        assertTrue(successfulUpdates.get() > 0, "Should have some successful updates");
        assertTrue(conflictCount.get() > 0, "Should have some conflicts");

        MockDialogSet finalDialog = registry.get(dialogId);
        assertEquals(1 + successfulUpdates.get(), finalDialog.revision, "Final revision should match initial + successful updates");
    }

    // =========================================================================
    // L5-DS-04: Dialog Event Bus
    // =========================================================================

    @Test
    @DisplayName("DS-04: CopyOnWriteArrayList allows concurrent iteration and modification")
    void eventBus_copyOnWriteAllowsConcurrentIterationAndMod() throws Exception {
        List<Consumer<MockDialogEvent>> listeners = new CopyOnWriteArrayList<>();
        int listenerCount = 20;
        LongAdder eventCount = new LongAdder();

        for (int i = 0; i < listenerCount; i++) {
            listeners.add(event -> eventCount.increment());
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean iterationFailed = new AtomicBoolean(false);

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 100; i++) {
                        MockDialogEvent event = new MockDialogEvent(UUID.randomUUID(), UUID.randomUUID());
                        for (Consumer<MockDialogEvent> listener : listeners) {
                            try {
                                listener.accept(event);
                            } catch (Exception e) {
                                iterationFailed.set(true);
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

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 50; i++) {
                        listeners.add(event -> eventCount.increment());
                        if (!listeners.isEmpty()) {
                            listeners.remove(listeners.size() - 1);
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

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));

        assertFalse(iterationFailed.get(), "Iteration should not fail during modification");
        assertTrue(eventCount.sum() > 0, "Events should be processed");
    }

    @Test
    @DisplayName("DS-04: Event bus handles listener exceptions gracefully")
    void eventBus_handlesListenerExceptionsGracefully() throws Exception {
        List<Consumer<MockDialogEvent>> listeners = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                listeners.add(event -> successCount.incrementAndGet());
            } else {
                listeners.add(event -> {
                    exceptionCount.incrementAndGet();
                    throw new RuntimeException("Test exception");
                });
            }
        }

        int eventCountNum = 100;
        CountDownLatch done = new CountDownLatch(1);

        executor.submit(() -> {
            try {
                for (int i = 0; i < eventCountNum; i++) {
                    MockDialogEvent event = new MockDialogEvent(UUID.randomUUID(), UUID.randomUUID());
                    for (Consumer<MockDialogEvent> listener : listeners) {
                        try {
                            listener.accept(event);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS));

        assertEquals(eventCountNum * 5, successCount.get(), "Good listeners should all be called");
        assertEquals(eventCountNum * 5, exceptionCount.get(), "Bad listeners should all throw");
    }

    @Test
    @DisplayName("DS-04: High-throughput event posting")
    void eventBus_highThroughputEventPosting() throws Exception {
        List<Consumer<MockDialogEvent>> listeners = new CopyOnWriteArrayList<>();
        LongAdder processedEvents = new LongAdder();

        for (int i = 0; i < 5; i++) {
            listeners.add(event -> processedEvents.increment());
        }

        int threadCount = 10;
        int eventsPerThread = 10000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicLong totalTime = new AtomicLong(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();

                    for (int i = 0; i < eventsPerThread; i++) {
                        MockDialogEvent event = new MockDialogEvent(UUID.randomUUID(), UUID.randomUUID());
                        for (Consumer<MockDialogEvent> listener : listeners) {
                            listener.accept(event);
                        }
                    }

                    totalTime.addAndGet(System.nanoTime() - start);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(60, TimeUnit.SECONDS));

        long expectedEvents = (long) threadCount * eventsPerThread * 5;
        assertEquals(expectedEvents, processedEvents.sum());

        double avgNsPerEvent = (double) totalTime.get() / processedEvents.sum();
        assertTrue(avgNsPerEvent < 10000, "Average should be under 10us per event delivery");
    }

    @Test
    @DisplayName("DS-04: Typed event filtering performance")
    void eventBus_typedEventFilteringPerformance() throws Exception {
        List<Consumer<MockDialogEvent>> listeners = new CopyOnWriteArrayList<>();
        AtomicInteger openedCount = new AtomicInteger(0);
        AtomicInteger closedCount = new AtomicInteger(0);
        AtomicInteger selectedCount = new AtomicInteger(0);

        listeners.add(event -> {
            if ("opened".equals(event.type)) openedCount.incrementAndGet();
        });
        listeners.add(event -> {
            if ("closed".equals(event.type)) closedCount.incrementAndGet();
        });
        listeners.add(event -> {
            if ("selected".equals(event.type)) selectedCount.incrementAndGet();
        });

        int eventCountNum = 30000;
        String[] types = {"opened", "closed", "selected"};

        long start = System.nanoTime();
        for (int i = 0; i < eventCountNum; i++) {
            String type = types[i % 3];
            MockDialogEvent event = new MockDialogEvent(UUID.randomUUID(), UUID.randomUUID(), type);
            for (Consumer<MockDialogEvent> listener : listeners) {
                listener.accept(event);
            }
        }
        long elapsed = System.nanoTime() - start;

        assertEquals(eventCountNum / 3, openedCount.get());
        assertEquals(eventCountNum / 3, closedCount.get());
        assertEquals(eventCountNum / 3, selectedCount.get());

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsed);
        assertTrue(elapsedMs < 5000, "Should complete in under 5 seconds, took " + elapsedMs + "ms");
    }

    // =========================================================================
    // L5-DS-05: Stale Session Cleanup
    // =========================================================================

    @Test
    @DisplayName("DS-05: Cleanup removes only stale sessions")
    void cleanup_removesOnlyStaleSessions() throws Exception {
        Map<UUID, MockDialogSession> sessions = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        int totalCount = 100;
        int staleCount = 0;

        for (int i = 0; i < totalCount; i++) {
            UUID playerId = UUID.randomUUID();
            long createdAt = (i < totalCount / 2)
                ? now - MAX_SESSION_DURATION_MS - 60_000
                : now - 60_000;

            MockDialogSession session = new MockDialogSession(playerId, UUID.randomUUID(), "dialog", "node", createdAt);
            sessions.put(playerId, session);

            if (i < totalCount / 2) staleCount++;
        }

        int expectedStale = staleCount;

        AtomicInteger removedCount = new AtomicInteger(0);
        sessions.entrySet().removeIf(entry -> {
            boolean stale = now - entry.getValue().createdAt > MAX_SESSION_DURATION_MS;
            if (stale) removedCount.incrementAndGet();
            return stale;
        });

        assertEquals(expectedStale, removedCount.get(), "Should remove stale sessions");
        assertEquals(totalCount - expectedStale, sessions.size(), "Fresh sessions should remain");
    }

    @Test
    @DisplayName("DS-05: Cleanup under concurrent session activity")
    void cleanup_underConcurrentSessionActivity() throws Exception {
        Map<UUID, MockDialogSession> sessions = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            UUID playerId = UUID.randomUUID();
            sessions.put(playerId, new MockDialogSession(playerId, UUID.randomUUID(), "dialog", "node",
                now - MAX_SESSION_DURATION_MS - 60_000));
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(3);
        AtomicBoolean cleanupFailed = new AtomicBoolean(false);
        AtomicInteger cleanedCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(10);

                long currentTime = System.currentTimeMillis();
                sessions.entrySet().removeIf(entry -> {
                    boolean stale = currentTime - entry.getValue().createdAt > MAX_SESSION_DURATION_MS;
                    if (stale) cleanedCount.incrementAndGet();
                    return stale;
                });
            } catch (Exception e) {
                cleanupFailed.set(true);
            } finally {
                endLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 100; i++) {
                    UUID playerId = UUID.randomUUID();
                    sessions.put(playerId, new MockDialogSession(playerId, UUID.randomUUID(), "new_dialog", "node",
                        System.currentTimeMillis()));
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 200; i++) {
                    int count = 0;
                    for (MockDialogSession ignored : sessions.values()) {
                        count++;
                    }
                    if (count < 0) throw new RuntimeException("Unreachable");
                }
            } catch (Exception e) {
                cleanupFailed.set(true);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));

        assertFalse(cleanupFailed.get(), "Cleanup should not fail");
        assertTrue(cleanedCount.get() > 0, "Should have cleaned some sessions");
    }

    // =========================================================================
    // L5-DS-06: Performance Metrics
    // =========================================================================

    @Test
    @DisplayName("DS-06: Session lookup throughput")
    void performance_sessionLookupThroughput() throws Exception {
        Map<UUID, MockDialogSession> sessions = new ConcurrentHashMap<>();
        int sessionCount = 10000;
        List<UUID> playerIds = new ArrayList<>();

        for (int i = 0; i < sessionCount; i++) {
            UUID playerId = UUID.randomUUID();
            playerIds.add(playerId);
            sessions.put(playerId, new MockDialogSession(playerId, UUID.randomUUID(), "dialog_" + i, "node"));
        }

        int threadCount = 10;
        int lookupsPerThread = 50000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder lookupCount = new LongAdder();

        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < lookupsPerThread; i++) {
                        UUID playerId = playerIds.get(random.nextInt(playerIds.size()));
                        MockDialogSession session = sessions.get(playerId);
                        if (session != null) {
                            lookupCount.increment();
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
        double opsPerSecond = lookupCount.sum() / (elapsed / 1_000_000_000.0);

        assertEquals(threadCount * lookupsPerThread, lookupCount.sum());
        assertTrue(opsPerSecond > 1_000_000, "Should achieve >1M lookups/sec, achieved " + (int) opsPerSecond);
    }

    @Test
    @DisplayName("DS-06: Dialog action processing throughput")
    void performance_dialogActionProcessingThroughput() throws Exception {
        Map<UUID, MockDialogSession> sessions = new ConcurrentHashMap<>();
        Map<UUID, List<Long>> actionTimestamps = new ConcurrentHashMap<>();
        int playerCount = 100;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            sessions.put(playerId, new MockDialogSession(playerId, UUID.randomUUID(), "dialog", "node"));
            actionTimestamps.put(playerId, Collections.synchronizedList(new ArrayList<>()));
        }

        int threadCount = 10;
        int actionsPerThread = 5000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder processedActions = new LongAdder();

        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < actionsPerThread; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));

                        MockDialogSession session = sessions.get(playerId);
                        if (session != null) {
                            List<Long> timestamps = actionTimestamps.get(playerId);
                            synchronized (timestamps) {
                                timestamps.add(System.currentTimeMillis());
                                if (timestamps.size() > 100) {
                                    timestamps.remove(0);
                                }
                            }

                            sessions.compute(playerId, (k, v) ->
                                v != null ? v.withNode("node_" + random.nextInt(10)) : null);

                            processedActions.increment();
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
        double opsPerSecond = processedActions.sum() / (elapsed / 1_000_000_000.0);

        assertTrue(opsPerSecond > 10000, "Should achieve >10K actions/sec, achieved " + (int) opsPerSecond);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private boolean checkRateLimit(Map<UUID, List<Long>> timestamps, UUID playerId, int limit) {
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60_000;

        List<Long> list = timestamps.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (list) {
            list.removeIf(t -> t < oneMinuteAgo);
            if (list.size() >= limit) {
                return false;
            }
            list.add(now);
            return true;
        }
    }

    // =========================================================================
    // Helper Classes
    // =========================================================================

    private static class MockDialogSession {
        final UUID playerId;
        final UUID npcId;
        final String dialogSetId;
        final String currentNodeId;
        final long createdAt;

        MockDialogSession(UUID playerId, UUID npcId, String dialogSetId, String currentNodeId) {
            this(playerId, npcId, dialogSetId, currentNodeId, System.currentTimeMillis());
        }

        MockDialogSession(UUID playerId, UUID npcId, String dialogSetId, String currentNodeId, long createdAt) {
            this.playerId = playerId;
            this.npcId = npcId;
            this.dialogSetId = dialogSetId;
            this.currentNodeId = currentNodeId;
            this.createdAt = createdAt;
        }

        MockDialogSession withNode(String newNodeId) {
            return new MockDialogSession(playerId, npcId, dialogSetId, newNodeId, createdAt);
        }
    }

    private static class MockDialogSet {
        final String id;
        final String name;
        final int revision;

        MockDialogSet(String id, String name, int revision) {
            this.id = id;
            this.name = name;
            this.revision = revision;
        }
    }

    private static class MockDialogEvent {
        final UUID playerId;
        final UUID npcId;
        final String type;

        MockDialogEvent(UUID playerId, UUID npcId) {
            this(playerId, npcId, "generic");
        }

        MockDialogEvent(UUID playerId, UUID npcId, String type) {
            this.playerId = playerId;
            this.npcId = npcId;
            this.type = type;
        }
    }
}
