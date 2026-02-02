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
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for Transport System components.
 * Tests concurrent operations on transport registry, charging states,
 * cooldowns, network routing, and indices under load.
 */
@Tag("load")
@DisplayName("L5: Transport System Stress Tests")
class TransportSystemStressTest {

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
    // L5-TS-01: Transport Registry Node Operations
    // =========================================================================

    @Test
    @DisplayName("TS-01: Concurrent node registration doesn't lose data")
    void registry_concurrentNodeRegistrationDoesNotLoseData() throws Exception {
        Map<UUID, MockTransportNode> nodes = new ConcurrentHashMap<>();
        int threadCount = 20;
        int nodesPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Set<UUID> allIds = ConcurrentHashMap.newKeySet();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < nodesPerThread; i++) {
                        UUID nodeId = UUID.randomUUID();
                        MockTransportNode node = new MockTransportNode(nodeId, "Node_" + i, MockBlockPos.random());
                        nodes.put(nodeId, node);
                        allIds.add(nodeId);
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

        assertEquals(allIds.size(), nodes.size(), "All nodes should be registered");
        assertEquals(threadCount * nodesPerThread, nodes.size());
    }

    @Test
    @DisplayName("TS-01: Concurrent node registration and unregistration")
    void registry_concurrentNodeRegistrationAndUnregistration() throws Exception {
        Map<UUID, MockTransportNode> nodes = new ConcurrentHashMap<>();
        int nodeCount = 2000;
        List<UUID> nodeIds = new ArrayList<>();

        for (int i = 0; i < nodeCount / 2; i++) {
            UUID nodeId = UUID.randomUUID();
            nodeIds.add(nodeId);
            nodes.put(nodeId, new MockTransportNode(nodeId, "Node", MockBlockPos.random()));
        }
        for (int i = 0; i < nodeCount / 2; i++) {
            nodeIds.add(UUID.randomUUID());
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder registerOps = new LongAdder();
        LongAdder unregisterOps = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        UUID nodeId = nodeIds.get(random.nextInt(nodeIds.size()));

                        if (random.nextBoolean()) {
                            nodes.put(nodeId, new MockTransportNode(nodeId, "Node", MockBlockPos.random()));
                            registerOps.increment();
                        } else {
                            if (nodes.remove(nodeId) != null) {
                                unregisterOps.increment();
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

        assertTrue(registerOps.sum() > 0, "Should have register operations");
        assertTrue(unregisterOps.sum() > 0, "Should have unregister operations");
    }

    @Test
    @DisplayName("TS-01: Node update with index consistency")
    void registry_nodeUpdateWithIndexConsistency() throws Exception {
        Map<UUID, MockTransportNode> nodes = new ConcurrentHashMap<>();
        Map<MockBlockPos, UUID> positionIndex = new ConcurrentHashMap<>();
        int nodeCount = 500;
        List<UUID> nodeIds = new ArrayList<>();

        for (int i = 0; i < nodeCount; i++) {
            UUID nodeId = UUID.randomUUID();
            MockBlockPos pos = MockBlockPos.random();
            MockTransportNode node = new MockTransportNode(nodeId, "Node_" + i, pos);
            nodes.put(nodeId, node);
            positionIndex.put(pos, nodeId);
            nodeIds.add(nodeId);
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean indexInconsistent = new AtomicBoolean(false);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 200; i++) {
                        UUID nodeId = nodeIds.get(random.nextInt(nodeIds.size()));
                        MockTransportNode existing = nodes.get(nodeId);

                        if (existing != null) {
                            MockBlockPos newPos = MockBlockPos.random();

                            nodes.compute(nodeId, (k, v) -> {
                                if (v != null) {
                                    positionIndex.remove(v.position);
                                    MockTransportNode updated = new MockTransportNode(k, v.name, newPos);
                                    positionIndex.put(newPos, k);
                                    return updated;
                                }
                                return null;
                            });
                        }
                    }
                } catch (Exception e) {
                    indexInconsistent.set(true);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));

        assertFalse(indexInconsistent.get(), "Index should remain consistent");

        for (MockTransportNode node : nodes.values()) {
            UUID indexedId = positionIndex.get(node.position);
            if (indexedId != null && !indexedId.equals(node.id)) {
                fail("Position index inconsistency detected");
            }
        }
    }

    // =========================================================================
    // L5-TS-02: Position Index Operations
    // =========================================================================

    @Test
    @DisplayName("TS-02: Concurrent position lookups are thread-safe")
    void positionIndex_concurrentLookupsAreThreadSafe() throws Exception {
        Map<MockBlockPos, UUID> positionIndex = new ConcurrentHashMap<>();
        int positionCount = 5000;
        List<MockBlockPos> positions = new ArrayList<>();

        for (int i = 0; i < positionCount; i++) {
            MockBlockPos pos = new MockBlockPos(i, 64, i * 2);
            positions.add(pos);
            positionIndex.put(pos, UUID.randomUUID());
        }

        int threadCount = 20;
        int lookupsPerThread = 10000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder successfulLookups = new LongAdder();
        AtomicBoolean lookupFailed = new AtomicBoolean(false);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < lookupsPerThread; i++) {
                        MockBlockPos pos = positions.get(random.nextInt(positions.size()));
                        UUID id = positionIndex.get(pos);
                        if (id != null) {
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
        assertEquals(threadCount * lookupsPerThread, successfulLookups.sum());
    }

    @Test
    @DisplayName("TS-02: Position index updates during concurrent reads")
    void positionIndex_updatesDuringConcurrentReads() throws Exception {
        Map<MockBlockPos, UUID> positionIndex = new ConcurrentHashMap<>();
        int initialCount = 1000;
        List<MockBlockPos> positions = new ArrayList<>();

        for (int i = 0; i < initialCount; i++) {
            MockBlockPos pos = new MockBlockPos(i, 64, i);
            positions.add(pos);
            positionIndex.put(pos, UUID.randomUUID());
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 500; i++) {
                        MockBlockPos newPos = MockBlockPos.random();
                        positionIndex.put(newPos, UUID.randomUUID());

                        if (!positions.isEmpty()) {
                            ThreadLocalRandom random = ThreadLocalRandom.current();
                            int idx = random.nextInt(positions.size());
                            positionIndex.remove(positions.get(idx));
                        }
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
                    for (int i = 0; i < 1000; i++) {
                        int count = 0;
                        for (MockBlockPos pos : positionIndex.keySet()) {
                            UUID id = positionIndex.get(pos);
                            if (id != null) count++;
                        }
                        if (count < 0) throw new RuntimeException("Unreachable");
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
    }

    // =========================================================================
    // L5-TS-03: Network Index Operations
    // =========================================================================

    @Test
    @DisplayName("TS-03: Concurrent network index updates")
    void networkIndex_concurrentUpdates() throws Exception {
        Map<String, Set<UUID>> networkIndex = new ConcurrentHashMap<>();
        int networkCount = 20;
        List<String> networks = new ArrayList<>();

        for (int i = 0; i < networkCount; i++) {
            String network = "network_" + i;
            networks.add(network);
            networkIndex.put(network, ConcurrentHashMap.newKeySet());
        }

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder addOps = new LongAdder();
        LongAdder removeOps = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        String network = networks.get(random.nextInt(networks.size()));
                        Set<UUID> netNodes = networkIndex.get(network);

                        if (netNodes != null) {
                            if (random.nextBoolean()) {
                                netNodes.add(UUID.randomUUID());
                                addOps.increment();
                            } else if (!netNodes.isEmpty()) {
                                netNodes.stream().findFirst().ifPresent(id -> {
                                    netNodes.remove(id);
                                    removeOps.increment();
                                });
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

        assertTrue(addOps.sum() > 0, "Should have add operations");
        assertTrue(removeOps.sum() > 0, "Should have remove operations");
    }

    @Test
    @DisplayName("TS-03: Network destination selection under concurrent modifications")
    void networkIndex_destinationSelectionUnderConcurrentMods() throws Exception {
        Map<String, Set<UUID>> networkIndex = new ConcurrentHashMap<>();
        Map<UUID, MockTransportNode> nodes = new ConcurrentHashMap<>();
        String networkName = "test_network";
        networkIndex.put(networkName, ConcurrentHashMap.newKeySet());

        for (int i = 0; i < 50; i++) {
            UUID nodeId = UUID.randomUUID();
            nodes.put(nodeId, new MockTransportNode(nodeId, "Node_" + i, MockBlockPos.random()));
            networkIndex.get(networkName).add(nodeId);
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder selectionCount = new LongAdder();
        AtomicBoolean selectionFailed = new AtomicBoolean(false);

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        Set<UUID> networkNodes = networkIndex.get(networkName);
                        if (networkNodes != null && !networkNodes.isEmpty()) {
                            List<UUID> candidates = new ArrayList<>(networkNodes);
                            if (!candidates.isEmpty()) {
                                UUID selected = candidates.get(random.nextInt(candidates.size()));
                                if (nodes.get(selected) != null) {
                                    selectionCount.increment();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    selectionFailed.set(true);
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

                    for (int i = 0; i < 200; i++) {
                        Set<UUID> networkNodes = networkIndex.get(networkName);
                        if (networkNodes != null) {
                            if (random.nextBoolean()) {
                                UUID newId = UUID.randomUUID();
                                nodes.put(newId, new MockTransportNode(newId, "New", MockBlockPos.random()));
                                networkNodes.add(newId);
                            } else if (!networkNodes.isEmpty()) {
                                networkNodes.stream().findFirst().ifPresent(id -> {
                                    networkNodes.remove(id);
                                    nodes.remove(id);
                                });
                            }
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

        assertFalse(selectionFailed.get(), "Selections should not fail");
        assertTrue(selectionCount.sum() > 0, "Should have successful selections");
    }

    @Test
    @DisplayName("TS-03: Round-robin counter consistency")
    void networkIndex_roundRobinCounterConsistency() throws Exception {
        Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();
        String networkName = "robin_network";
        int networkSize = 10;
        roundRobinCounters.put(networkName, new AtomicInteger(0));

        int threadCount = 20;
        int selectionsPerThread = 1000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Map<Integer, LongAdder> selectionCounts = new ConcurrentHashMap<>();

        for (int i = 0; i < networkSize; i++) {
            selectionCounts.put(i, new LongAdder());
        }

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < selectionsPerThread; i++) {
                        AtomicInteger counter = roundRobinCounters.get(networkName);
                        int index = counter.getAndUpdate(v -> (v + 1) % networkSize);
                        selectionCounts.get(index).increment();
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

        long totalSelections = threadCount * selectionsPerThread;
        long expectedPerNode = totalSelections / networkSize;

        for (int i = 0; i < networkSize; i++) {
            long count = selectionCounts.get(i).sum();
            assertTrue(count >= expectedPerNode * 0.8 && count <= expectedPerNode * 1.2,
                "Node " + i + " selection count should be near expected: " + count + " vs " + expectedPerNode);
        }
    }

    // =========================================================================
    // L5-TS-04: Charging State Management
    // =========================================================================

    @Test
    @DisplayName("TS-04: Concurrent charging state operations")
    void chargingState_concurrentOperations() throws Exception {
        Map<UUID, MockChargingState> chargingStates = new ConcurrentHashMap<>();
        int playerCount = 100;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            players.add(UUID.randomUUID());
        }

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder startOps = new LongAdder();
        LongAdder stopOps = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));

                        if (random.nextBoolean()) {
                            UUID nodeId = UUID.randomUUID();
                            chargingStates.put(playerId, new MockChargingState(playerId, nodeId, 0, 100));
                            startOps.increment();
                        } else {
                            if (chargingStates.remove(playerId) != null) {
                                stopOps.increment();
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

        assertTrue(startOps.sum() > 0);
        assertTrue(stopOps.sum() > 0);
    }

    @Test
    @DisplayName("TS-04: Charging progress tick updates")
    void chargingState_progressTickUpdates() throws Exception {
        Map<UUID, MockChargingState> chargingStates = new ConcurrentHashMap<>();
        int playerCount = 50;

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            chargingStates.put(playerId, new MockChargingState(playerId, UUID.randomUUID(), 0, 100));
        }

        LongAdder tickCount = new LongAdder();
        AtomicBoolean tickFailed = new AtomicBoolean(false);
        List<UUID> completedPlayers = new ArrayList<>();

        for (int tick = 0; tick < 150; tick++) {
            try {
                var iterator = chargingStates.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    MockChargingState state = entry.getValue();

                    MockChargingState updated = state.tick();
                    chargingStates.put(entry.getKey(), updated);
                    tickCount.increment();

                    if (updated.currentTicks >= updated.requiredTicks) {
                        iterator.remove();
                        synchronized (completedPlayers) {
                            completedPlayers.add(entry.getKey());
                        }
                    }
                }
            } catch (Exception e) {
                tickFailed.set(true);
                break;
            }
        }

        assertFalse(tickFailed.get(), "Tick processing should not fail");
        assertEquals(playerCount, completedPlayers.size(), "All players should complete charging");
        assertTrue(chargingStates.isEmpty(), "All charging states should be removed");
    }

    @Test
    @DisplayName("TS-04: Charging state sync tracking")
    void chargingState_syncTracking() throws Exception {
        Map<UUID, Long> lastChargeSync = new ConcurrentHashMap<>();
        Set<UUID> chargingPlayersLastTick = ConcurrentHashMap.newKeySet();
        int playerCount = 100;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            if (i < playerCount / 2) {
                chargingPlayersLastTick.add(playerId);
                lastChargeSync.put(playerId, System.currentTimeMillis());
            }
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));

                        if (chargingPlayersLastTick.contains(playerId)) {
                            lastChargeSync.put(playerId, System.currentTimeMillis());
                        } else {
                            lastChargeSync.remove(playerId);
                        }

                        if (random.nextBoolean()) {
                            chargingPlayersLastTick.add(playerId);
                        } else {
                            chargingPlayersLastTick.remove(playerId);
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

        assertFalse(failed.get(), "Sync tracking should not fail");
    }

    // =========================================================================
    // L5-TS-05: Cooldown Management
    // =========================================================================

    @Test
    @DisplayName("TS-05: Concurrent cooldown set and check operations")
    void cooldown_concurrentSetAndCheckOperations() throws Exception {
        Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
        int playerCount = 100;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            cooldowns.put(playerId, new ConcurrentHashMap<>());
        }

        String[] cooldownTypes = {"ARRIVAL", "GLOBAL", "NODE_SPECIFIC"};
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder setOps = new LongAdder();
        LongAdder checkOps = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        String cooldownType = cooldownTypes[random.nextInt(cooldownTypes.length)];
                        Map<String, Long> playerCooldowns = cooldowns.get(playerId);

                        if (playerCooldowns != null) {
                            if (random.nextBoolean()) {
                                playerCooldowns.put(cooldownType, System.currentTimeMillis() + 5000);
                                setOps.increment();
                            } else {
                                Long expiry = playerCooldowns.get(cooldownType);
                                if (expiry != null) {
                                    boolean onCooldown = System.currentTimeMillis() < expiry;
                                    checkOps.increment();
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
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));

        assertTrue(setOps.sum() > 0);
        assertTrue(checkOps.sum() > 0);
    }

    @Test
    @DisplayName("TS-05: Cooldown expiration cleanup")
    void cooldown_expirationCleanup() throws Exception {
        Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        int playerCount = 100;

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            Map<String, Long> playerCooldowns = new ConcurrentHashMap<>();

            if (i % 2 == 0) {
                playerCooldowns.put("ARRIVAL", now - 5000);
            } else {
                playerCooldowns.put("ARRIVAL", now + 5000);
            }

            cooldowns.put(playerId, playerCooldowns);
        }

        int cleanedCount = 0;
        for (Map<String, Long> playerCooldowns : cooldowns.values()) {
            var iterator = playerCooldowns.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (entry.getValue() < now) {
                    iterator.remove();
                    cleanedCount++;
                }
            }
        }

        assertEquals(playerCount / 2, cleanedCount, "Should clean expired cooldowns");

        for (Map<String, Long> playerCooldowns : cooldowns.values()) {
            for (Long expiry : playerCooldowns.values()) {
                assertTrue(expiry >= now, "All remaining should be active");
            }
        }
    }

    @Test
    @DisplayName("TS-05: Per-player cooldown cleanup on disconnect")
    void cooldown_perPlayerCleanupOnDisconnect() throws Exception {
        Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
        int playerCount = 50;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            players.add(playerId);
            Map<String, Long> playerCooldowns = new ConcurrentHashMap<>();
            playerCooldowns.put("ARRIVAL", System.currentTimeMillis() + 10000);
            playerCooldowns.put("GLOBAL", System.currentTimeMillis() + 5000);
            cooldowns.put(playerId, playerCooldowns);
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder cleanupCount = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = threadId; i < playerCount; i += threadCount) {
                        UUID playerId = players.get(i);
                        if (cooldowns.remove(playerId) != null) {
                            cleanupCount.increment();
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

        assertEquals(playerCount, cleanupCount.sum(), "All players should be cleaned up");
        assertTrue(cooldowns.isEmpty(), "No cooldowns should remain");
    }

    // =========================================================================
    // L5-TS-06: Return Point Management
    // =========================================================================

    @Test
    @DisplayName("TS-06: Concurrent return point operations")
    void returnPoint_concurrentOperations() throws Exception {
        Map<UUID, UUID> playerReturnPoints = new ConcurrentHashMap<>();
        Map<UUID, MockTransportNode> nodes = new ConcurrentHashMap<>();
        int playerCount = 100;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            players.add(UUID.randomUUID());
        }

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder recordOps = new LongAdder();
        LongAdder clearOps = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));

                        if (random.nextBoolean()) {
                            UUID returnNodeId = UUID.randomUUID();
                            MockTransportNode returnNode = new MockTransportNode(returnNodeId, "Return", MockBlockPos.random());

                            UUID oldReturnId = playerReturnPoints.get(playerId);
                            if (oldReturnId != null) {
                                nodes.remove(oldReturnId);
                            }

                            nodes.put(returnNodeId, returnNode);
                            playerReturnPoints.put(playerId, returnNodeId);
                            recordOps.increment();
                        } else {
                            UUID returnId = playerReturnPoints.remove(playerId);
                            if (returnId != null) {
                                nodes.remove(returnId);
                                clearOps.increment();
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

        assertTrue(recordOps.sum() > 0);
        assertTrue(clearOps.sum() > 0);

        for (var entry : playerReturnPoints.entrySet()) {
            UUID returnId = entry.getValue();
            assertNotNull(nodes.get(returnId), "Return point node should exist");
        }
    }

    @Test
    @DisplayName("TS-06: Return point retrieval under concurrent modifications")
    void returnPoint_retrievalUnderConcurrentModifications() throws Exception {
        Map<UUID, UUID> playerReturnPoints = new ConcurrentHashMap<>();
        Map<UUID, MockTransportNode> nodes = new ConcurrentHashMap<>();
        int playerCount = 50;
        List<UUID> players = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            UUID returnId = UUID.randomUUID();
            players.add(playerId);
            nodes.put(returnId, new MockTransportNode(returnId, "Return", MockBlockPos.random()));
            playerReturnPoints.put(playerId, returnId);
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder retrievalCount = new LongAdder();
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threadCount / 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < 500; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));
                        UUID returnId = playerReturnPoints.get(playerId);
                        if (returnId != null) {
                            MockTransportNode node = nodes.get(returnId);
                            if (node != null) {
                                retrievalCount.increment();
                            }
                        }
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

                    for (int i = 0; i < 200; i++) {
                        UUID playerId = players.get(random.nextInt(players.size()));

                        UUID oldId = playerReturnPoints.get(playerId);
                        UUID newId = UUID.randomUUID();

                        nodes.put(newId, new MockTransportNode(newId, "Updated", MockBlockPos.random()));
                        playerReturnPoints.put(playerId, newId);

                        if (oldId != null) {
                            nodes.remove(oldId);
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

        assertFalse(failed.get(), "Retrieval should not fail");
        assertTrue(retrievalCount.sum() > 0, "Should have successful retrievals");
    }

    // =========================================================================
    // L5-TS-07: Performance Metrics
    // =========================================================================

    @Test
    @DisplayName("TS-07: Registry lookup throughput")
    void performance_registryLookupThroughput() throws Exception {
        Map<UUID, MockTransportNode> nodes = new ConcurrentHashMap<>();
        Map<MockBlockPos, UUID> positionIndex = new ConcurrentHashMap<>();
        int nodeCount = 10000;
        List<MockBlockPos> positions = new ArrayList<>();

        for (int i = 0; i < nodeCount; i++) {
            UUID nodeId = UUID.randomUUID();
            MockBlockPos pos = new MockBlockPos(i, 64, i * 2);
            positions.add(pos);
            nodes.put(nodeId, new MockTransportNode(nodeId, "Node", pos));
            positionIndex.put(pos, nodeId);
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
                        MockBlockPos pos = positions.get(random.nextInt(positions.size()));
                        UUID nodeId = positionIndex.get(pos);
                        if (nodeId != null && nodes.get(nodeId) != null) {
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
        assertTrue(opsPerSecond > 500000, "Should achieve >500K lookups/sec, achieved " + (int) opsPerSecond);
    }

    @Test
    @DisplayName("TS-07: Charging state update throughput")
    void performance_chargingStateUpdateThroughput() throws Exception {
        Map<UUID, MockChargingState> chargingStates = new ConcurrentHashMap<>();
        int playerCount = 1000;

        for (int i = 0; i < playerCount; i++) {
            UUID playerId = UUID.randomUUID();
            chargingStates.put(playerId, new MockChargingState(playerId, UUID.randomUUID(), 0, 100));
        }

        long startTime = System.nanoTime();
        int tickCount = 100;

        for (int tick = 0; tick < tickCount; tick++) {
            for (var entry : chargingStates.entrySet()) {
                chargingStates.computeIfPresent(entry.getKey(), (k, v) -> v.tick());
            }
        }

        long elapsed = System.nanoTime() - startTime;
        long totalUpdates = (long) tickCount * playerCount;
        double updatesPerSecond = totalUpdates / (elapsed / 1_000_000_000.0);

        assertTrue(updatesPerSecond > 100000, "Should achieve >100K updates/sec, achieved " + (int) updatesPerSecond);
    }

    @Test
    @DisplayName("TS-07: Network routing throughput")
    void performance_networkRoutingThroughput() throws Exception {
        Map<String, Set<UUID>> networkIndex = new ConcurrentHashMap<>();
        Map<UUID, MockTransportNode> nodes = new ConcurrentHashMap<>();
        int networkCount = 50;
        int nodesPerNetwork = 20;
        List<String> networks = new ArrayList<>();

        for (int n = 0; n < networkCount; n++) {
            String network = "network_" + n;
            networks.add(network);
            Set<UUID> networkNodes = ConcurrentHashMap.newKeySet();

            for (int i = 0; i < nodesPerNetwork; i++) {
                UUID nodeId = UUID.randomUUID();
                nodes.put(nodeId, new MockTransportNode(nodeId, "Node", MockBlockPos.random()));
                networkNodes.add(nodeId);
            }
            networkIndex.put(network, networkNodes);
        }

        int threadCount = 10;
        int routingsPerThread = 10000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        LongAdder routingCount = new LongAdder();

        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int i = 0; i < routingsPerThread; i++) {
                        String network = networks.get(random.nextInt(networks.size()));
                        Set<UUID> networkNodes = networkIndex.get(network);

                        if (networkNodes != null && !networkNodes.isEmpty()) {
                            List<UUID> candidates = new ArrayList<>(networkNodes);
                            UUID selected = candidates.get(random.nextInt(candidates.size()));
                            if (nodes.get(selected) != null) {
                                routingCount.increment();
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
        double routingsPerSecond = routingCount.sum() / (elapsed / 1_000_000_000.0);

        assertTrue(routingsPerSecond > 100000, "Should achieve >100K routings/sec, achieved " + (int) routingsPerSecond);
    }

    // =========================================================================
    // Helper Classes
    // =========================================================================

    private static class MockTransportNode {
        final UUID id;
        final String name;
        final MockBlockPos position;

        MockTransportNode(UUID id, String name, MockBlockPos position) {
            this.id = id;
            this.name = name;
            this.position = position;
        }
    }

    private static class MockBlockPos {
        final int x, y, z;

        MockBlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static MockBlockPos random() {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            return new MockBlockPos(
                r.nextInt(-30000000, 30000000),
                r.nextInt(0, 320),
                r.nextInt(-30000000, 30000000)
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MockBlockPos that)) return false;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }

    private static class MockChargingState {
        final UUID playerId;
        final UUID nodeId;
        final int currentTicks;
        final int requiredTicks;

        MockChargingState(UUID playerId, UUID nodeId, int currentTicks, int requiredTicks) {
            this.playerId = playerId;
            this.nodeId = nodeId;
            this.currentTicks = currentTicks;
            this.requiredTicks = requiredTicks;
        }

        MockChargingState tick() {
            return new MockChargingState(playerId, nodeId, currentTicks + 1, requiredTicks);
        }
    }
}
