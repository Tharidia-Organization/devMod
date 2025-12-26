package com.devmod.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Progressive Test Suite 4: Multiplayer Concurrency Tests
 *
 * Tests thread-safety and race conditions in multiplayer scenarios.
 * Validates that concurrent operations don't cause data corruption.
 *
 * Focus areas:
 * 1. Concurrent instance creation
 * 2. Party system with multiple players
 * 3. Concurrent state transitions
 * 4. Thread-safe map operations
 */
public class MultiplayerConcurrencyTest {

    // ============================================================
    // TEST SUITE 1: Concurrent Instance Creation
    // ============================================================
    @Nested
    @DisplayName("Concurrent Instance Creation")
    class ConcurrentCreationTests {

        @Test
        @DisplayName("Multiple players create instances simultaneously")
        void testSimultaneousInstanceCreation() throws Exception {
            Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
            Map<UUID, String> instanceStates = new ConcurrentHashMap<>();
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            int playerCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(playerCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(playerCount);

            List<UUID> playerIds = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                UUID playerId = UUID.randomUUID();
                playerIds.add(playerId);

                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for signal

                        // Simulate instance creation
                        UUID instanceId = UUID.randomUUID();

                        // Check if player already has instance
                        UUID existing = playerToInstance.putIfAbsent(playerId, instanceId);
                        if (existing == null) {
                            // Successfully created
                            instanceStates.put(instanceId, "ACTIVE");
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // Start all threads
            completionLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(playerCount, successCount.get(),
                "All players should create instances successfully");
            assertEquals(0, failureCount.get());
            assertEquals(playerCount, playerToInstance.size());
            assertEquals(playerCount, instanceStates.size());
        }

        @Test
        @DisplayName("Same player cannot create multiple instances")
        void testDuplicateInstancePrevention() throws Exception {
            Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
            UUID playerId = UUID.randomUUID();
            AtomicInteger successCount = new AtomicInteger(0);

            int attemptCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(attemptCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(attemptCount);

            for (int i = 0; i < attemptCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        UUID instanceId = UUID.randomUUID();
                        UUID existing = playerToInstance.putIfAbsent(playerId, instanceId);
                        if (existing == null) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            completionLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(1, successCount.get(),
                "Only one instance should be created for the same player");
            assertEquals(1, playerToInstance.size());
        }

        @RepeatedTest(5)
        @DisplayName("No race condition in bidirectional map updates")
        void testBidirectionalMapConsistency() throws Exception {
            Map<UUID, UUID> arenaToInstance = new ConcurrentHashMap<>();
            Map<UUID, UUID> instanceToArena = new ConcurrentHashMap<>();

            int operations = 100;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(operations);

            for (int i = 0; i < operations; i++) {
                executor.submit(() -> {
                    try {
                        UUID arenaId = UUID.randomUUID();
                        UUID instanceId = UUID.randomUUID();

                        // Add to both maps atomically (as much as possible)
                        arenaToInstance.put(arenaId, instanceId);
                        instanceToArena.put(instanceId, arenaId);

                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Verify consistency
            assertEquals(arenaToInstance.size(), instanceToArena.size(),
                "Both maps should have same size");

            // Verify bidirectional lookup
            for (Map.Entry<UUID, UUID> entry : arenaToInstance.entrySet()) {
                UUID arenaId = entry.getKey();
                UUID instanceId = entry.getValue();
                assertEquals(arenaId, instanceToArena.get(instanceId),
                    "Reverse lookup should match");
            }
        }
    }

    // ============================================================
    // TEST SUITE 2: Party System Concurrency
    // ============================================================
    @Nested
    @DisplayName("Party System Concurrency")
    class PartySystemTests {

        @Test
        @DisplayName("Party members join instance atomically")
        void testPartyJoinAtomic() throws Exception {
            Set<UUID> instancePlayers = ConcurrentHashMap.newKeySet();
            int maxPlayers = 4;
            AtomicInteger joinedCount = new AtomicInteger(0);

            UUID leader = UUID.randomUUID();
            List<UUID> members = Arrays.asList(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
            );

            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(4);

            // Leader joins
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (instancePlayers.size() < maxPlayers) {
                        instancePlayers.add(leader);
                        joinedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    completionLatch.countDown();
                }
            });

            // Members join
            for (UUID member : members) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (instancePlayers.size() < maxPlayers) {
                            instancePlayers.add(member);
                            joinedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            completionLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(4, joinedCount.get(), "All party members should join");
            assertEquals(4, instancePlayers.size());
            assertTrue(instancePlayers.contains(leader));
            for (UUID member : members) {
                assertTrue(instancePlayers.contains(member));
            }
        }

        @Test
        @DisplayName("Party size limit enforced under concurrent joins")
        void testPartySizeLimitEnforced() throws Exception {
            Set<UUID> instancePlayers = ConcurrentHashMap.newKeySet();
            int maxPlayers = 4;
            AtomicInteger successCount = new AtomicInteger(0);

            // Try to add 10 players to 4-player party
            int attemptCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(attemptCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(attemptCount);

            for (int i = 0; i < attemptCount; i++) {
                UUID playerId = UUID.randomUUID();
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        // Synchronized check-then-add
                        synchronized (instancePlayers) {
                            if (instancePlayers.size() < maxPlayers) {
                                instancePlayers.add(playerId);
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            completionLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(maxPlayers, successCount.get(),
                "Only max players should be allowed");
            assertEquals(maxPlayers, instancePlayers.size());
        }

        @Test
        @DisplayName("Concurrent leave and join don't corrupt player list")
        void testConcurrentLeaveJoin() throws Exception {
            Set<UUID> instancePlayers = ConcurrentHashMap.newKeySet();

            // Initial players
            List<UUID> initialPlayers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                UUID pid = UUID.randomUUID();
                initialPlayers.add(pid);
                instancePlayers.add(pid);
            }

            ExecutorService executor = Executors.newFixedThreadPool(8);
            CountDownLatch latch = new CountDownLatch(8);

            // 4 threads removing, 4 threads adding
            for (int i = 0; i < 4; i++) {
                final int index = i;

                // Remove thread
                executor.submit(() -> {
                    try {
                        instancePlayers.remove(initialPlayers.get(index));
                    } finally {
                        latch.countDown();
                    }
                });

                // Add thread
                executor.submit(() -> {
                    try {
                        instancePlayers.add(UUID.randomUUID());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Should have 4 new players (old removed, new added)
            assertEquals(4, instancePlayers.size(),
                "Should maintain consistent player count");

            // None of initial players should remain
            for (UUID initial : initialPlayers) {
                assertFalse(instancePlayers.contains(initial),
                    "Initial players should be removed");
            }
        }
    }

    // ============================================================
    // TEST SUITE 3: Concurrent State Transitions
    // ============================================================
    @Nested
    @DisplayName("Concurrent State Transitions")
    class StateTransitionTests {

        @Test
        @DisplayName("Only one thread can transition state at a time")
        void testAtomicStateTransition() throws Exception {
            AtomicReference<InstanceState> state = new AtomicReference<>(InstanceState.CREATING);
            AtomicInteger transitionCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(5);

            for (int i = 0; i < 5; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        // Try to transition CREATING -> READY
                        boolean success = state.compareAndSet(
                            InstanceState.CREATING,
                            InstanceState.READY
                        );
                        if (success) {
                            transitionCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            completionLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(1, transitionCount.get(),
                "Only one thread should succeed in transition");
            assertEquals(InstanceState.READY, state.get());
        }

        @Test
        @DisplayName("Sequential state transitions are ordered correctly")
        void testSequentialTransitions() throws Exception {
            AtomicReference<InstanceState> state = new AtomicReference<>(InstanceState.CREATING);
            List<InstanceState> transitionLog = Collections.synchronizedList(new ArrayList<>());

            InstanceState[] expectedOrder = {
                InstanceState.READY,
                InstanceState.ACTIVE,
                InstanceState.COMPLETING,
                InstanceState.DESTROYING,
                InstanceState.DESTROYED
            };

            for (InstanceState nextState : expectedOrder) {
                InstanceState current = state.get();
                boolean valid = isValidTransition(current, nextState);
                assertTrue(valid, "Transition " + current + " -> " + nextState + " should be valid");

                state.set(nextState);
                transitionLog.add(nextState);
            }

            assertArrayEquals(expectedOrder, transitionLog.toArray(),
                "Transitions should be in correct order");
        }

        private boolean isValidTransition(InstanceState from, InstanceState to) {
            return switch (from) {
                case CREATING -> to == InstanceState.READY || to == InstanceState.DESTROYING;
                case READY -> to == InstanceState.ACTIVE || to == InstanceState.DESTROYING;
                case ACTIVE -> to == InstanceState.COMPLETING;
                case COMPLETING -> to == InstanceState.DESTROYING;
                case DESTROYING -> to == InstanceState.DESTROYED;
                case DESTROYED -> false;
            };
        }
    }

    // ============================================================
    // TEST SUITE 4: Thread-Safe Registry Operations
    // ============================================================
    @Nested
    @DisplayName("Thread-Safe Registry Operations")
    class RegistryOperationsTests {

        @Test
        @DisplayName("Concurrent reads and writes don't cause ConcurrentModificationException")
        void testNoConcurrentModificationException() throws Exception {
            Map<UUID, String> registry = new ConcurrentHashMap<>();

            // Pre-populate
            for (int i = 0; i < 100; i++) {
                registry.put(UUID.randomUUID(), "value" + i);
            }

            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(1000);
            AtomicInteger exceptionCount = new AtomicInteger(0);

            // Readers
            for (int i = 0; i < 500; i++) {
                executor.submit(() -> {
                    try {
                        for (UUID key : registry.keySet()) {
                            registry.get(key);
                        }
                    } catch (ConcurrentModificationException e) {
                        exceptionCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Writers
            for (int i = 0; i < 500; i++) {
                executor.submit(() -> {
                    try {
                        registry.put(UUID.randomUUID(), "new_value");
                    } catch (ConcurrentModificationException e) {
                        exceptionCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(0, exceptionCount.get(),
                "No ConcurrentModificationException should occur");
        }

        @Test
        @DisplayName("Iterator remains consistent during modifications")
        void testIteratorConsistency() throws Exception {
            Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();

            // Pre-populate
            for (int i = 0; i < 50; i++) {
                playerToInstance.put(UUID.randomUUID(), UUID.randomUUID());
            }

            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch latch = new CountDownLatch(100);
            AtomicInteger iterationErrors = new AtomicInteger(0);

            // Iterating threads
            for (int i = 0; i < 50; i++) {
                executor.submit(() -> {
                    try {
                        for (Map.Entry<UUID, UUID> entry : playerToInstance.entrySet()) {
                            // Access values
                            assertNotNull(entry.getKey());
                            // Value might be null if removed during iteration - that's OK for ConcurrentHashMap
                        }
                    } catch (Exception e) {
                        iterationErrors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Modifying threads
            for (int i = 0; i < 50; i++) {
                executor.submit(() -> {
                    try {
                        playerToInstance.put(UUID.randomUUID(), UUID.randomUUID());
                        // Remove random entry
                        playerToInstance.keySet().stream()
                            .findFirst()
                            .ifPresent(playerToInstance::remove);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(0, iterationErrors.get(),
                "No errors during concurrent iteration");
        }

        @RepeatedTest(3)
        @DisplayName("Cleanup operations are thread-safe")
        void testThreadSafeCleanup() throws Exception {
            Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
            Map<UUID, Set<UUID>> instanceToPlayers = new ConcurrentHashMap<>();

            // Setup: 5 instances with 4 players each
            for (int i = 0; i < 5; i++) {
                UUID instanceId = UUID.randomUUID();
                Set<UUID> players = ConcurrentHashMap.newKeySet();

                for (int j = 0; j < 4; j++) {
                    UUID playerId = UUID.randomUUID();
                    players.add(playerId);
                    playerToInstance.put(playerId, instanceId);
                }
                instanceToPlayers.put(instanceId, players);
            }

            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch latch = new CountDownLatch(5);
            AtomicInteger cleanupErrors = new AtomicInteger(0);

            // Cleanup each instance concurrently
            for (UUID instanceId : new ArrayList<>(instanceToPlayers.keySet())) {
                executor.submit(() -> {
                    try {
                        Set<UUID> players = instanceToPlayers.remove(instanceId);
                        if (players != null) {
                            for (UUID playerId : players) {
                                playerToInstance.remove(playerId);
                            }
                        }
                    } catch (Exception e) {
                        cleanupErrors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(0, cleanupErrors.get());
            assertTrue(instanceToPlayers.isEmpty(),
                "All instances should be cleaned up");
            assertTrue(playerToInstance.isEmpty(),
                "All player mappings should be cleaned up");
        }
    }
}
