package com.devmod.stress;

import java.util.ArrayList;
import java.util.Collections;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.devmod.runtime.InstanceState;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for Instance management system.
 * Tests concurrent operations on instance lifecycle, player mappings,
 * registry operations, and state transitions under load.
 */
@Tag("load")
@DisplayName("L5: Instance Manager Stress Tests")
class InstanceManagerStressTest {

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
    // L5-IM-01: Instance Registry Concurrent Operations
    // =========================================================================

    @Test
    @DisplayName("IM-01: Concurrent instance registration doesn't lose data")
    void registry_concurrentInstanceRegistrationDoesNotLoseData() throws Exception {
        ConcurrentHashMap<UUID, MockInstanceData> registry = new ConcurrentHashMap<>();
        int threadCount = 20;
        int instancesPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Set<UUID> allIds = ConcurrentHashMap.newKeySet();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < instancesPerThread; i++) {
                        UUID instanceId = UUID.randomUUID();
                        UUID ownerId = UUID.randomUUID();
                        MockInstanceData data = new MockInstanceData(instanceId, ownerId);
                        registry.put(instanceId, data);
                        allIds.add(instanceId);
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

        assertEquals(allIds.size(), registry.size(), "All instances should be registered");
        assertEquals(threadCount * instancesPerThread, registry.size());
    }

    @Test
    @DisplayName("IM-01: Concurrent instance unregistration is safe")
    void registry_concurrentInstanceUnregistrationIsSafe() throws Exception {
        ConcurrentHashMap<UUID, MockInstanceData> registry = new ConcurrentHashMap<>();
        int instanceCount = 5000;
        List<UUID> instanceIds = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < instanceCount; i++) {
            UUID id = UUID.randomUUID();
            registry.put(id, new MockInstanceData(id, UUID.randomUUID()));
            instanceIds.add(id);
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger removedCount = new AtomicInteger(0);

        int chunkSize = instanceCount / threadCount;
        for (int t = 0; t < threadCount; t++) {
            int start = t * chunkSize;
            int end = (t == threadCount - 1) ? instanceCount : start + chunkSize;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = start; i < end; i++) {
                        if (registry.remove(instanceIds.get(i)) != null) {
                            removedCount.incrementAndGet();
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

        assertEquals(instanceCount, removedCount.get(), "All instances should be removed exactly once");
        assertTrue(registry.isEmpty(), "Registry should be empty");
    }

    @Test
    @DisplayName("IM-01: Concurrent registration and lookup operations")
    void registry_concurrentRegistrationAndLookup() throws Exception {
        ConcurrentHashMap<UUID, MockInstanceData> registry = new ConcurrentHashMap<>();
        int writerCount = 5;
        int readerCount = 10;
        int writesPerWriter = 200;
        int readsPerReader = 1000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(writerCount + readerCount);
        AtomicInteger successfulReads = new AtomicInteger(0);

        for (int w = 0; w < writerCount; w++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < writesPerWriter; i++) {
                        UUID id = UUID.randomUUID();
                        registry.put(id, new MockInstanceData(id, UUID.randomUUID()));
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        for (int r = 0; r < readerCount; r++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < readsPerReader; i++) {
                        if (!registry.isEmpty()) {
                            UUID[] keys = registry.keySet().toArray(UUID[]::new);
                            if (keys.length > 0) {
                                UUID randomKey = keys[ThreadLocalRandom.current().nextInt(keys.length)];
                                MockInstanceData data = registry.get(randomKey);
                                if (data != null) {
                                    successfulReads.incrementAndGet();
                                }
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

        assertTrue(successfulReads.get() > 0, "Should have successful reads");
        assertEquals(writerCount * writesPerWriter, registry.size(), "All writes should complete");
    }

    // =========================================================================
    // L5-IM-02: Player Mapping Operations
    // =========================================================================

    @Test
    @DisplayName("IM-02: Concurrent player-to-instance mapping is thread-safe")
    void playerMapping_concurrentMappingIsThreadSafe() throws Exception {
        ConcurrentHashMap<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
        int threadCount = 20;
        int mappingsPerThread = 500;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Set<UUID> allPlayers = ConcurrentHashMap.newKeySet();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < mappingsPerThread; i++) {
                        UUID playerId = UUID.randomUUID();
                        UUID instanceId = UUID.randomUUID();
                        playerToInstance.put(playerId, instanceId);
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

        assertEquals(allPlayers.size(), playerToInstance.size());
        assertEquals(threadCount * mappingsPerThread, playerToInstance.size());
    }

    @Test
    @DisplayName("IM-02: Concurrent player map and unmap operations")
    void playerMapping_concurrentMapAndUnmapOperations() throws Exception {
        ConcurrentHashMap<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
        int playerCount = 1000;
        List<UUID> players = new ArrayList<>();
        UUID instanceId = UUID.randomUUID();

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            if (i < playerCount / 2) {
                playerToInstance.put(playerId, instanceId);
            }
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder mapOperations = new LongAdder();
        LongAdder unmapOperations = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < 500; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        if (random.nextBoolean()) {
                            playerToInstance.put(playerId, instanceId);
                            mapOperations.increment();
                        } else {
                            playerToInstance.remove(playerId);
                            unmapOperations.increment();
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

        assertTrue(mapOperations.sum() > 0, "Should have map operations");
        assertTrue(unmapOperations.sum() > 0, "Should have unmap operations");
        for (UUID playerId : playerToInstance.keySet()) {
            assertEquals(instanceId, playerToInstance.get(playerId));
        }
    }

    @Test
    @DisplayName("IM-02: Player lookup by instance under concurrent modifications")
    void playerMapping_lookupByInstanceUnderConcurrentModifications() throws Exception {
        ConcurrentHashMap<UUID, Set<UUID>> instanceToPlayers = new ConcurrentHashMap<>();
        int instanceCount = 10;
        List<UUID> instances = new ArrayList<>();

        for (int i = 0; i < instanceCount; i++) {
            UUID instId = UUID.randomUUID();
            instances.add(instId);
            instanceToPlayers.put(instId, ConcurrentHashMap.newKeySet());
        }

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean lookupFailed = new AtomicBoolean(false);

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < 500; i++) {
                        UUID instId = instances.get(random.nextInt(instances.size()));
                        Set<UUID> players = instanceToPlayers.get(instId);
                        if (players != null) {
                            if (random.nextBoolean()) {
                                players.add(UUID.randomUUID());
                            } else if (!players.isEmpty()) {
                                players.stream().findFirst().ifPresent(players::remove);
                            }
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
                    for (int i = 0; i < 500; i++) {
                        UUID instId = instances.get(random.nextInt(instances.size()));
                        Set<UUID> players = instanceToPlayers.get(instId);
                        if (players != null) {
                            int count = 0;
                            for (UUID ignored : players) {
                                count++;
                            }
                            if (count < 0) throw new RuntimeException("Unreachable");
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
    }

    // =========================================================================
    // L5-IM-03: Instance State Transitions
    // =========================================================================

    @Test
    @DisplayName("IM-03: Atomic state transition with CAS")
    void stateTransition_atomicWithCAS() throws Exception {
        AtomicReference<InstanceState> state = new AtomicReference<>(InstanceState.CREATING);
        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (state.compareAndSet(InstanceState.CREATING, InstanceState.READY)) {
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
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        assertEquals(1, successCount.get(), "Only one thread should succeed");
        assertEquals(InstanceState.READY, state.get());
    }

    @Test
    @DisplayName("IM-03: Sequential state machine transitions under load")
    void stateTransition_sequentialUnderLoad() throws Exception {
        int instanceCount = 100;
        List<AtomicReference<InstanceState>> instances = new ArrayList<>();

        for (int i = 0; i < instanceCount; i++) {
            instances.add(new AtomicReference<>(InstanceState.CREATING));
        }

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder transitionCount = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        AtomicReference<InstanceState> instance = instances.get(random.nextInt(instances.size()));

                        InstanceState current = instance.get();
                        InstanceState next = getNextState(current);
                        if (next != null && instance.compareAndSet(current, next)) {
                            transitionCount.increment();
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

        assertTrue(transitionCount.sum() > 0, "Should have successful transitions");

        for (AtomicReference<InstanceState> instance : instances) {
            assertNotNull(instance.get());
        }
    }

    @Test
    @DisplayName("IM-03: Concurrent state reads with single writer")
    void stateTransition_concurrentReadsWithSingleWriter() throws Exception {
        AtomicReference<InstanceState> state = new AtomicReference<>(InstanceState.CREATING);
        int readerCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(readerCount + 1);
        AtomicBoolean writerDone = new AtomicBoolean(false);
        LongAdder readCount = new LongAdder();
        AtomicBoolean sawInvalidState = new AtomicBoolean(false);

        executor.submit(() -> {
            try {
                startLatch.await();
                InstanceState[] sequence = {
                    InstanceState.READY,
                    InstanceState.ACTIVE,
                    InstanceState.COMPLETING,
                    InstanceState.DESTROYING,
                    InstanceState.DESTROYED
                };
                for (InstanceState newState : sequence) {
                    state.set(newState);
                    Thread.sleep(10);
                }
                writerDone.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endLatch.countDown();
            }
        });

        for (int r = 0; r < readerCount; r++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (!writerDone.get()) {
                        InstanceState observed = state.get();
                        if (observed == null) {
                            sawInvalidState.set(true);
                        }
                        readCount.increment();
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

        assertFalse(sawInvalidState.get(), "Should never observe null state");
        assertTrue(readCount.sum() > 0, "Should have reads");
        assertEquals(InstanceState.DESTROYED, state.get());
    }

    // =========================================================================
    // L5-IM-04: Destruction Queue Operations
    // =========================================================================

    @Test
    @DisplayName("IM-04: Concurrent destruction scheduling is safe")
    void destructionQueue_concurrentSchedulingIsSafe() throws Exception {
        Set<UUID> pendingDestruction = ConcurrentHashMap.newKeySet();
        Map<UUID, Long> destructionTimestamps = new ConcurrentHashMap<>();
        int instanceCount = 500;
        List<UUID> instances = new ArrayList<>();

        for (int i = 0; i < instanceCount; i++) {
            instances.add(UUID.randomUUID());
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < 200; i++) {
                        UUID instId = instances.get(random.nextInt(instances.size()));
                        if (pendingDestruction.add(instId)) {
                            destructionTimestamps.put(instId, System.currentTimeMillis());
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

        assertEquals(pendingDestruction.size(), destructionTimestamps.size());
        for (UUID id : pendingDestruction) {
            assertTrue(destructionTimestamps.containsKey(id));
        }
    }

    @Test
    @DisplayName("IM-04: Concurrent schedule and cancel operations")
    void destructionQueue_concurrentScheduleAndCancel() throws Exception {
        Set<UUID> pendingDestruction = ConcurrentHashMap.newKeySet();
        int instanceCount = 200;
        List<UUID> instances = new ArrayList<>();

        for (int i = 0; i < instanceCount; i++) {
            instances.add(UUID.randomUUID());
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder scheduleOps = new LongAdder();
        LongAdder cancelOps = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < 500; i++) {
                        UUID instId = instances.get(random.nextInt(instances.size()));
                        if (random.nextBoolean()) {
                            pendingDestruction.add(instId);
                            scheduleOps.increment();
                        } else {
                            pendingDestruction.remove(instId);
                            cancelOps.increment();
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

        assertTrue(scheduleOps.sum() > 0);
        assertTrue(cancelOps.sum() > 0);
        assertTrue(pendingDestruction.size() <= instanceCount);
    }

    @Test
    @DisplayName("IM-04: Processing destruction queue under concurrent modifications")
    void destructionQueue_processingUnderConcurrentModifications() throws Exception {
        Set<UUID> pendingDestruction = ConcurrentHashMap.newKeySet();
        Map<UUID, Long> timestamps = new ConcurrentHashMap<>();
        int instanceCount = 100;

        long now = System.currentTimeMillis();
        for (int i = 0; i < instanceCount; i++) {
            UUID id = UUID.randomUUID();
            pendingDestruction.add(id);
            timestamps.put(id, now - (i < instanceCount / 2 ? 10000 : -10000));
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(3);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicBoolean processingFailed = new AtomicBoolean(false);

        executor.submit(() -> {
            try {
                startLatch.await();
                long currentTime = System.currentTimeMillis();
                for (UUID id : pendingDestruction) {
                    Long timestamp = timestamps.get(id);
                    if (timestamp != null && currentTime >= timestamp + 5000) {
                        if (pendingDestruction.remove(id)) {
                            timestamps.remove(id);
                            processedCount.incrementAndGet();
                        }
                    }
                }
            } catch (Exception e) {
                processingFailed.set(true);
            } finally {
                endLatch.countDown();
            }
        });

        for (int t = 0; t < 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 50; i++) {
                        UUID newId = UUID.randomUUID();
                        pendingDestruction.add(newId);
                        timestamps.put(newId, System.currentTimeMillis());
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

        assertFalse(processingFailed.get(), "Processing should not fail");
        assertTrue(processedCount.get() > 0, "Should process some destructions");
    }

    // =========================================================================
    // L5-IM-05: Teleport Request Management
    // =========================================================================

    @Test
    @DisplayName("IM-05: Concurrent teleport request registration")
    void teleportRequest_concurrentRegistration() throws Exception {
        Map<UUID, MockTeleportRequest> pendingTeleports = new ConcurrentHashMap<>();
        int threadCount = 20;
        int requestsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Set<UUID> allPlayers = ConcurrentHashMap.newKeySet();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < requestsPerThread; i++) {
                        UUID playerId = UUID.randomUUID();
                        UUID instId = UUID.randomUUID();
                        pendingTeleports.put(playerId, new MockTeleportRequest(playerId, instId, 200));
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

        assertEquals(allPlayers.size(), pendingTeleports.size());
    }

    @Test
    @DisplayName("IM-05: Teleport countdown tick processing under load")
    void teleportRequest_countdownTickProcessingUnderLoad() throws Exception {
        Map<UUID, MockTeleportRequest> pendingTeleports = new ConcurrentHashMap<>();
        int requestCount = 500;

        for (int i = 0; i < requestCount; i++) {
            UUID playerId = UUID.randomUUID();
            int ticks = 10 + (i % 100);
            pendingTeleports.put(playerId, new MockTeleportRequest(playerId, UUID.randomUUID(), ticks));
        }

        AtomicInteger teleportedCount = new AtomicInteger(0);
        AtomicBoolean tickFailed = new AtomicBoolean(false);

        for (int tick = 0; tick < 150; tick++) {
            try {
                var iterator = pendingTeleports.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    MockTeleportRequest request = entry.getValue();
                    request.ticksRemaining--;

                    if (request.ticksRemaining <= 0) {
                        iterator.remove();
                        teleportedCount.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                tickFailed.set(true);
                break;
            }
        }

        assertFalse(tickFailed.get(), "Tick processing should not fail");
        assertEquals(requestCount, teleportedCount.get(), "All requests should be processed");
        assertTrue(pendingTeleports.isEmpty(), "No pending teleports should remain");
    }

    @Test
    @DisplayName("IM-05: Stale request cleanup")
    void teleportRequest_staleCleanup() throws Exception {
        Map<UUID, MockTeleportRequest> pendingTeleports = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        int requestCount = 100;
        int staleCount = 0;

        for (int i = 0; i < requestCount; i++) {
            UUID playerId = UUID.randomUUID();
            MockTeleportRequest request = new MockTeleportRequest(playerId, UUID.randomUUID(), 200);
            if (i < requestCount / 2) {
                request.createdAt = now - 60000;
                staleCount++;
            } else {
                request.createdAt = now - 5000;
            }
            pendingTeleports.put(playerId, request);
        }

        final int expectedStale = staleCount;
        AtomicInteger cleanedCount = new AtomicInteger(0);

        var iterator = pendingTeleports.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isStale()) {
                iterator.remove();
                cleanedCount.incrementAndGet();
            }
        }

        assertEquals(expectedStale, cleanedCount.get(), "Should clean stale requests");
        assertEquals(requestCount - expectedStale, pendingTeleports.size());
    }

    // =========================================================================
    // L5-IM-06: Instance Data Serialization Under Load
    // =========================================================================

    @Test
    @DisplayName("IM-06: Concurrent toMap serialization is thread-safe")
    void serialization_concurrentToMapIsThreadSafe() throws Exception {
        MockInstanceData instance = new MockInstanceData(UUID.randomUUID(), UUID.randomUUID());
        instance.state.set(InstanceState.ACTIVE);

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean serializationFailed = new AtomicBoolean(false);

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 100; i++) {
                        instance.currentPlayers.add(UUID.randomUUID());
                        if (i % 10 == 0 && !instance.currentPlayers.isEmpty()) {
                            instance.currentPlayers.stream().findFirst().ifPresent(instance.currentPlayers::remove);
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
                    for (int i = 0; i < 100; i++) {
                        try {
                            Map<String, Object> map = instance.toMap();
                            assertNotNull(map.get("instanceId"));
                            assertNotNull(map.get("state"));
                        } catch (Exception e) {
                            serializationFailed.set(true);
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

        assertFalse(serializationFailed.get(), "Serialization should not fail");
    }

    @Test
    @DisplayName("IM-06: Batch serialization performance")
    void serialization_batchPerformance() throws Exception {
        int instanceCount = 1000;
        List<MockInstanceData> instances = new ArrayList<>();

        for (int i = 0; i < instanceCount; i++) {
            MockInstanceData data = new MockInstanceData(UUID.randomUUID(), UUID.randomUUID());
            data.state.set(InstanceState.values()[i % InstanceState.values().length]);
            for (int p = 0; p < (i % 4) + 1; p++) {
                data.currentPlayers.add(UUID.randomUUID());
            }
            instances.add(data);
        }

        long startTime = System.nanoTime();
        List<Map<String, Object>> serialized = new ArrayList<>();

        for (MockInstanceData inst : instances) {
            serialized.add(inst.toMap());
        }

        long elapsed = System.nanoTime() - startTime;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsed);

        assertEquals(instanceCount, serialized.size());
        assertTrue(elapsedMs < 5000, "Serialization should complete within 5 seconds, took " + elapsedMs + "ms");
    }

    // =========================================================================
    // L5-IM-07: Performance Metrics
    // =========================================================================

    @Test
    @DisplayName("IM-07: Registry lookup performance under load")
    void performance_registryLookupUnderLoad() throws Exception {
        ConcurrentHashMap<UUID, MockInstanceData> registry = new ConcurrentHashMap<>();
        int instanceCount = 10000;
        List<UUID> instanceIds = new ArrayList<>();

        for (int i = 0; i < instanceCount; i++) {
            UUID id = UUID.randomUUID();
            instanceIds.add(id);
            registry.put(id, new MockInstanceData(id, UUID.randomUUID()));
        }

        int threadCount = 10;
        int lookupsPerThread = 10000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder lookupCount = new LongAdder();
        AtomicLong totalTime = new AtomicLong(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    long threadStart = System.nanoTime();

                    for (int i = 0; i < lookupsPerThread; i++) {
                        UUID id = instanceIds.get(random.nextInt(instanceIds.size()));
                        MockInstanceData data = registry.get(id);
                        if (data != null) {
                            lookupCount.increment();
                        }
                    }

                    totalTime.addAndGet(System.nanoTime() - threadStart);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(60, TimeUnit.SECONDS));

        assertEquals(threadCount * lookupsPerThread, lookupCount.sum());
        long avgNsPerLookup = totalTime.get() / lookupCount.sum();
        assertTrue(avgNsPerLookup < 10000, "Average lookup should be under 10us, was " + avgNsPerLookup + "ns");
    }

    @Test
    @DisplayName("IM-07: Player mapping throughput")
    void performance_playerMappingThroughput() throws Exception {
        ConcurrentHashMap<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
        int threadCount = 10;
        int operationsPerThread = 50000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder operations = new LongAdder();

        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        UUID playerId = UUID.randomUUID();
                        UUID instId = UUID.randomUUID();
                        playerToInstance.put(playerId, instId);
                        operations.increment();
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
        double opsPerSecond = operations.sum() / (elapsed / 1_000_000_000.0);

        assertTrue(opsPerSecond > 100000, "Should achieve >100K ops/sec, achieved " + (int) opsPerSecond);
    }

    @Test
    @DisplayName("IM-07: Instance lifecycle throughput")
    void performance_instanceLifecycleThroughput() throws Exception {
        ConcurrentHashMap<UUID, MockInstanceData> registry = new ConcurrentHashMap<>();
        int threadCount = 10;
        int cyclesPerThread = 1000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder completedCycles = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < cyclesPerThread; i++) {
                        UUID instId = UUID.randomUUID();
                        MockInstanceData data = new MockInstanceData(instId, UUID.randomUUID());
                        registry.put(instId, data);

                        data.state.set(InstanceState.READY);
                        data.state.set(InstanceState.ACTIVE);

                        data.currentPlayers.add(UUID.randomUUID());

                        data.state.set(InstanceState.COMPLETING);
                        data.state.set(InstanceState.DESTROYING);
                        data.state.set(InstanceState.DESTROYED);

                        registry.remove(instId);
                        completedCycles.increment();
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

        assertEquals(threadCount * cyclesPerThread, completedCycles.sum());
        assertTrue(registry.isEmpty(), "Registry should be empty after all cycles");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private InstanceState getNextState(InstanceState current) {
        return switch (current) {
            case CREATING -> InstanceState.READY;
            case READY -> InstanceState.ACTIVE;
            case ACTIVE -> InstanceState.COMPLETING;
            case COMPLETING -> InstanceState.DESTROYING;
            case DESTROYING -> InstanceState.DESTROYED;
            case DESTROYED -> null;
        };
    }

    // =========================================================================
    // Helper Classes
    // =========================================================================

    private static class MockInstanceData {
        final UUID instanceId;
        final UUID ownerId;
        final long createdAt;
        final AtomicReference<InstanceState> state;
        final Set<UUID> currentPlayers;

        MockInstanceData(UUID instanceId, UUID ownerId) {
            this.instanceId = instanceId;
            this.ownerId = ownerId;
            this.createdAt = System.currentTimeMillis();
            this.state = new AtomicReference<>(InstanceState.CREATING);
            this.currentPlayers = ConcurrentHashMap.newKeySet();
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new ConcurrentHashMap<>();
            map.put("instanceId", instanceId.toString());
            map.put("ownerId", ownerId.toString());
            map.put("createdAt", createdAt);
            map.put("state", state.get().name());

            List<String> players = new ArrayList<>();
            for (UUID p : currentPlayers) {
                players.add(p.toString());
            }
            map.put("players", players);

            return map;
        }
    }

    private static class MockTeleportRequest {
        final UUID playerId;
        final UUID instanceId;
        long createdAt;
        int ticksRemaining;
        static final long MAX_AGE_MS = 30_000;

        MockTeleportRequest(UUID playerId, UUID instanceId, int ticksRemaining) {
            this.playerId = playerId;
            this.instanceId = instanceId;
            this.ticksRemaining = ticksRemaining;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isStale() {
            return System.currentTimeMillis() - createdAt > MAX_AGE_MS;
        }
    }
}
