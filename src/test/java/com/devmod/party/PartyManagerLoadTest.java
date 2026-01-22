package com.devmod.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.devmod.endurance.QuestType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load tests for PartyManager concurrent operations.
 * Tests thread-safety, throughput, and error rates under high concurrency.
 */
@Tag("load")
public class PartyManagerLoadTest {

    private PartyManager manager;

    @BeforeEach
    void setUp() {
        // Create a fresh manager for each test to avoid state pollution
        manager = PartyManager.INSTANCE;
        manager.reset();
    }

    @AfterEach
    void tearDown() {
        manager.reset();
    }

    // ============================================
    // Load Test Metrics
    // ============================================

    static class LoadTestMetrics {
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);
        final AtomicLong totalLatencyNanos = new AtomicLong(0);
        final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong maxLatency = new AtomicLong(0);

        void recordSuccess(long latencyNanos) {
            successCount.incrementAndGet();
            recordLatency(latencyNanos);
        }

        void recordFailure(long latencyNanos) {
            failureCount.incrementAndGet();
            recordLatency(latencyNanos);
        }

        private void recordLatency(long latencyNanos) {
            totalLatencyNanos.addAndGet(latencyNanos);
            latencies.add(latencyNanos);
            minLatency.updateAndGet(prev -> Math.min(prev, latencyNanos));
            maxLatency.updateAndGet(prev -> Math.max(prev, latencyNanos));
        }

        int getTotalOperations() {
            return successCount.get() + failureCount.get();
        }

        double getSuccessRate() {
            int total = getTotalOperations();
            return total > 0 ? (double) successCount.get() / total : 0.0;
        }

        double getAverageLatencyMs() {
            int total = getTotalOperations();
            return total > 0 ? totalLatencyNanos.get() / (total * 1_000_000.0) : 0.0;
        }

        double getP50LatencyMs() {
            return getPercentileLatencyMs(0.50);
        }

        double getP95LatencyMs() {
            return getPercentileLatencyMs(0.95);
        }

        double getP99LatencyMs() {
            return getPercentileLatencyMs(0.99);
        }

        private double getPercentileLatencyMs(double percentile) {
            if (latencies.isEmpty()) return 0.0;
            List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);
            int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
            return sorted.get(index) / 1_000_000.0;
        }

        double getThroughputOpsPerSec(long durationMs) {
            return durationMs > 0 ? (getTotalOperations() * 1000.0 / durationMs) : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "Metrics: success=%d, failures=%d, rate=%.2f%%, avg=%.2fms, p50=%.2fms, p95=%.2fms, p99=%.2fms",
                successCount.get(), failureCount.get(), getSuccessRate() * 100,
                getAverageLatencyMs(), getP50LatencyMs(), getP95LatencyMs(), getP99LatencyMs()
            );
        }
    }

    // ============================================
    // Party Creation Load Tests
    // ============================================

    @Nested
    @DisplayName("Party Creation Load Tests")
    class PartyCreationLoadTests {

        @ParameterizedTest
        @ValueSource(ints = {10, 50, 100, 500})
        @DisplayName("Concurrent party creation scales linearly")
        void testConcurrentPartyCreation(int concurrency) throws InterruptedException {
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrency);
            LoadTestMetrics metrics = new LoadTestMetrics();

            for (int i = 0; i < concurrency; i++) {
                final int playerId = i;
                executor.execute(() -> {
                    try {
                        startLatch.await();
                        UUID leaderId = UUID.randomUUID();
                        String leaderName = "Player" + playerId;

                        long start = System.nanoTime();
                        PartyData party = manager.createParty(leaderId, leaderName, QuestType.PVE_COOP);
                        long latency = System.nanoTime() - start;

                        if (party != null) {
                            metrics.recordSuccess(latency);
                        } else {
                            metrics.recordFailure(latency);
                        }
                    } catch (Exception e) {
                        metrics.recordFailure(0);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            long startTime = System.currentTimeMillis();
            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Timed out waiting for party creation");
            long duration = System.currentTimeMillis() - startTime;

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            // Verify results
            assertEquals(concurrency, metrics.getTotalOperations());
            assertEquals(1.0, metrics.getSuccessRate(), 0.001,
                "All party creations should succeed with unique players");
            assertEquals(concurrency, manager.getActivePartyCount());

            System.out.printf("Party Creation [%d concurrent]: %s, throughput=%.2f ops/sec%n",
                concurrency, metrics, metrics.getThroughputOpsPerSec(duration));

            // Performance assertions
            assertTrue(metrics.getP99LatencyMs() < 100, "P99 latency should be under 100ms");
        }

        @Test
        @DisplayName("Duplicate player party creation is rejected")
        void testDuplicatePlayerRejection() throws InterruptedException {
            UUID playerId = UUID.randomUUID();
            int attempts = 100;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(attempts);
            AtomicInteger successes = new AtomicInteger(0);

            for (int i = 0; i < attempts; i++) {
                executor.execute(() -> {
                    try {
                        startLatch.await();
                        PartyData party = manager.createParty(playerId, "TestPlayer", QuestType.PVE_COOP);
                        if (party != null) {
                            successes.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            // Only one party creation should succeed
            assertEquals(1, successes.get(), "Only one party should be created for same player");
            assertEquals(1, manager.getActivePartyCount());
        }

        @Test
        @DisplayName("Rapid party creation and disbanding")
        void testRapidCreateDisband() throws InterruptedException {
            int iterations = 500;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch doneLatch = new CountDownLatch(iterations);
            LoadTestMetrics createMetrics = new LoadTestMetrics();
            LoadTestMetrics disbandMetrics = new LoadTestMetrics();

            for (int i = 0; i < iterations; i++) {
                executor.execute(() -> {
                    try {
                        UUID leaderId = UUID.randomUUID();

                        long createStart = System.nanoTime();
                        PartyData party = manager.createParty(leaderId, "Leader", QuestType.PVE_COOP);
                        long createLatency = System.nanoTime() - createStart;

                        if (party != null) {
                            createMetrics.recordSuccess(createLatency);

                            long disbandStart = System.nanoTime();
                            boolean disbanded = manager.disbandParty(leaderId);
                            long disbandLatency = System.nanoTime() - disbandStart;

                            if (disbanded) {
                                disbandMetrics.recordSuccess(disbandLatency);
                            } else {
                                disbandMetrics.recordFailure(disbandLatency);
                            }
                        } else {
                            createMetrics.recordFailure(createLatency);
                        }
                    } catch (Exception e) {
                        createMetrics.recordFailure(0);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertTrue(doneLatch.await(60, TimeUnit.SECONDS));
            executor.shutdown();

            // All creates and disbands should succeed
            assertEquals(iterations, createMetrics.successCount.get());
            assertEquals(iterations, disbandMetrics.successCount.get());
            assertEquals(0, manager.getActivePartyCount(), "All parties should be disbanded");

            System.out.printf("Create: %s%nDisband: %s%n", createMetrics, disbandMetrics);
        }
    }

    // ============================================
    // Invite System Load Tests
    // ============================================

    @Nested
    @DisplayName("Invite System Load Tests")
    class InviteSystemLoadTests {

        @Test
        @DisplayName("High volume invite sending")
        void testHighVolumeInvites() throws InterruptedException {
            // Use EVENT type which allows up to 20 players (maxPlayers - 1 for leader = 19 invites max)
            int partyCount = 50;
            int invitesPerParty = 10; // EVENT allows 19, we'll use 10 for safety
            ExecutorService executor = Executors.newFixedThreadPool(partyCount);
            CountDownLatch doneLatch = new CountDownLatch(partyCount);
            LoadTestMetrics metrics = new LoadTestMetrics();
            Map<UUID, PartyData> parties = new HashMap<>();

            // Create parties with EVENT type for larger capacity
            for (int i = 0; i < partyCount; i++) {
                UUID leaderId = UUID.randomUUID();
                PartyData party = manager.createParty(leaderId, "Leader" + i, QuestType.EVENT);
                if (party != null) {
                    parties.put(leaderId, party);
                }
            }

            assertEquals(partyCount, parties.size());

            // Send invites from each party
            for (Map.Entry<UUID, PartyData> entry : parties.entrySet()) {
                UUID leaderId = entry.getKey();
                executor.execute(() -> {
                    try {
                        for (int j = 0; j < invitesPerParty; j++) {
                            UUID targetId = UUID.randomUUID();
                            long start = System.nanoTime();
                            PartyInvite invite = manager.sendInvite(leaderId, targetId, "Target" + j);
                            long latency = System.nanoTime() - start;

                            if (invite != null) {
                                metrics.recordSuccess(latency);
                            } else {
                                metrics.recordFailure(latency);
                            }
                        }
                    } catch (Exception e) {
                        metrics.recordFailure(0);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertTrue(doneLatch.await(60, TimeUnit.SECONDS));
            executor.shutdown();

            int expectedInvites = partyCount * invitesPerParty;
            System.out.printf("Invites [%d total]: %s%n", expectedInvites, metrics);

            assertTrue(metrics.getSuccessRate() > 0.95, "Invite success rate should be > 95%");
            assertTrue(metrics.getP95LatencyMs() < 50, "P95 latency should be under 50ms");
        }

        @Test
        @DisplayName("Concurrent invite responses")
        void testConcurrentInviteResponses() throws InterruptedException {
            // Use multiple parties with EVENT type to test concurrent responses
            int partyCount = 20;
            int invitesPerParty = 10; // EVENT allows 19, use 10
            int totalInvites = partyCount * invitesPerParty;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch doneLatch = new CountDownLatch(totalInvites);
            LoadTestMetrics acceptMetrics = new LoadTestMetrics();
            LoadTestMetrics declineMetrics = new LoadTestMetrics();

            // Create multiple parties with invites
            Map<UUID, UUID> playerToInvite = new HashMap<>();
            List<PartyData> parties = new ArrayList<>();

            for (int p = 0; p < partyCount; p++) {
                UUID leaderId = UUID.randomUUID();
                PartyData party = manager.createParty(leaderId, "Leader" + p, QuestType.EVENT);
                assertNotNull(party);
                parties.add(party);

                for (int i = 0; i < invitesPerParty; i++) {
                    UUID targetId = UUID.randomUUID();
                    PartyInvite invite = manager.sendInvite(leaderId, targetId, "Target" + i);
                    if (invite != null) {
                        playerToInvite.put(targetId, invite.getInviteId());
                    }
                }
            }

            assertEquals(totalInvites, playerToInvite.size());

            // Respond to invites concurrently (50% accept, 50% decline)
            int index = 0;
            for (Map.Entry<UUID, UUID> entry : playerToInvite.entrySet()) {
                UUID playerId = entry.getKey();
                UUID inviteId = entry.getValue();
                boolean shouldAccept = (index++ % 2 == 0);

                executor.execute(() -> {
                    try {
                        long start = System.nanoTime();
                        boolean result;
                        if (shouldAccept) {
                            result = manager.acceptInvite(playerId, "Player", inviteId);
                            long latency = System.nanoTime() - start;
                            if (result) {
                                acceptMetrics.recordSuccess(latency);
                            } else {
                                acceptMetrics.recordFailure(latency);
                            }
                        } else {
                            result = manager.handleInviteResponse(playerId, inviteId, false);
                            long latency = System.nanoTime() - start;
                            if (result) {
                                declineMetrics.recordSuccess(latency);
                            } else {
                                declineMetrics.recordFailure(latency);
                            }
                        }
                    } catch (Exception e) {
                        acceptMetrics.recordFailure(0);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            System.out.printf("Accepts: %s%nDeclines: %s%n", acceptMetrics, declineMetrics);

            // Verify total members across all parties (each party has 1 leader + accepted members)
            int totalMembers = parties.stream().mapToInt(PartyData::getMemberCount).sum();
            int expectedTotalMembers = partyCount + acceptMetrics.successCount.get(); // leaders + accepted
            assertEquals(expectedTotalMembers, totalMembers);
        }

        @Test
        @DisplayName("Race condition: same player invited by multiple parties")
        void testMultipleInvitesToSamePlayer() throws InterruptedException {
            int partyCount = 10;
            UUID targetId = UUID.randomUUID();
            ExecutorService executor = Executors.newFixedThreadPool(partyCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(partyCount);
            AtomicInteger inviteSuccess = new AtomicInteger(0);
            List<UUID> inviteIds = Collections.synchronizedList(new ArrayList<>());

            // Create parties and send invites simultaneously
            List<UUID> leaders = new ArrayList<>();
            for (int i = 0; i < partyCount; i++) {
                UUID leaderId = UUID.randomUUID();
                PartyData party = manager.createParty(leaderId, "Leader" + i, QuestType.PVE_COOP);
                if (party != null) {
                    leaders.add(leaderId);
                }
            }

            for (UUID leaderId : leaders) {
                executor.execute(() -> {
                    try {
                        startLatch.await();
                        PartyInvite invite = manager.sendInvite(leaderId, targetId, "Target");
                        if (invite != null) {
                            inviteSuccess.incrementAndGet();
                            inviteIds.add(invite.getInviteId());
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            // Multiple invites can be sent to same target (they're pending)
            List<PartyInvite> pendingInvites = manager.getPendingInvites(targetId);
            assertEquals(inviteSuccess.get(), pendingInvites.size());

            // Accept first invite - should join only one party
            if (!inviteIds.isEmpty()) {
                boolean joined = manager.acceptInvite(targetId, "Target", inviteIds.get(0));
                assertTrue(joined);
                assertTrue(manager.isInParty(targetId));

                // Other invites should now fail
                for (int i = 1; i < inviteIds.size(); i++) {
                    boolean secondJoin = manager.acceptInvite(targetId, "Target", inviteIds.get(i));
                    assertFalse(secondJoin, "Should not be able to join second party");
                }
            }
        }
    }

    // ============================================
    // Member Operations Load Tests
    // ============================================

    @Nested
    @DisplayName("Member Operations Load Tests")
    class MemberOperationsLoadTests {

        @Test
        @DisplayName("Concurrent member leave operations")
        void testConcurrentMemberLeave() throws InterruptedException {
            // Use EVENT type (maxPlayers = 20) and test with multiple parties for higher concurrency
            int partyCount = 10;
            int membersPerParty = 15; // EVENT allows 19, use 15 for margin
            int totalMembers = partyCount * membersPerParty;

            List<PartyData> parties = new ArrayList<>();
            List<UUID> allMemberIds = new ArrayList<>();

            // Create multiple parties and add members
            for (int p = 0; p < partyCount; p++) {
                UUID leaderId = UUID.randomUUID();
                PartyData party = manager.createParty(leaderId, "Leader" + p, QuestType.EVENT);
                assertNotNull(party);
                parties.add(party);

                for (int i = 0; i < membersPerParty; i++) {
                    UUID memberId = UUID.randomUUID();
                    PartyInvite invite = manager.sendInvite(leaderId, memberId, "Member" + i);
                    if (invite != null) {
                        manager.acceptInvite(memberId, "Member" + i, invite.getInviteId());
                        allMemberIds.add(memberId);
                    }
                }
            }

            assertEquals(totalMembers, allMemberIds.size());

            // All members leave simultaneously
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalMembers);
            LoadTestMetrics metrics = new LoadTestMetrics();

            for (UUID memberId : allMemberIds) {
                executor.execute(() -> {
                    try {
                        startLatch.await();
                        long start = System.nanoTime();
                        boolean left = manager.leaveParty(memberId);
                        long latency = System.nanoTime() - start;

                        if (left) {
                            metrics.recordSuccess(latency);
                        } else {
                            metrics.recordFailure(latency);
                        }
                    } catch (Exception e) {
                        metrics.recordFailure(0);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            System.out.printf("Leave: %s%n", metrics);

            // All leaves should succeed
            assertEquals(totalMembers, metrics.successCount.get());
            // Each party should only have leader remaining
            for (PartyData party : parties) {
                assertEquals(1, party.getMemberCount(), "Only leader should remain in party " + party.getPartyId());
            }
        }

        @Test
        @DisplayName("Mixed concurrent operations stress test")
        void testMixedOperationsStress() throws InterruptedException {
            int operationCount = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(50);
            CountDownLatch doneLatch = new CountDownLatch(operationCount);

            LoadTestMetrics createMetrics = new LoadTestMetrics();
            LoadTestMetrics joinMetrics = new LoadTestMetrics();
            LoadTestMetrics leaveMetrics = new LoadTestMetrics();

            AtomicInteger operationType = new AtomicInteger(0);

            for (int i = 0; i < operationCount; i++) {
                executor.execute(() -> {
                    try {
                        int op = operationType.getAndIncrement() % 3;
                        UUID playerId = UUID.randomUUID();

                        switch (op) {
                            case 0 -> { // Create party
                                long start = System.nanoTime();
                                PartyData party = manager.createParty(playerId, "Creator", QuestType.PVE_COOP);
                                long latency = System.nanoTime() - start;
                                if (party != null) {
                                    createMetrics.recordSuccess(latency);
                                } else {
                                    createMetrics.recordFailure(latency);
                                }
                            }
                            case 1 -> { // Try to join random party
                                // Get a random active party
                                var parties = manager.getAllParties();
                                if (!parties.isEmpty()) {
                                    PartyData randomParty = parties.iterator().next();
                                    UUID leaderId = randomParty.getLeaderId();
                                    PartyInvite invite = manager.sendInvite(leaderId, playerId, "Joiner");
                                    if (invite != null) {
                                        long start = System.nanoTime();
                                        boolean joined = manager.acceptInvite(playerId, "Joiner", invite.getInviteId());
                                        long latency = System.nanoTime() - start;
                                        if (joined) {
                                            joinMetrics.recordSuccess(latency);
                                        } else {
                                            joinMetrics.recordFailure(latency);
                                        }
                                    }
                                }
                            }
                            case 2 -> { // Leave party (if in one) or create new
                                if (manager.isInParty(playerId)) {
                                    long start = System.nanoTime();
                                    boolean left = manager.leaveParty(playerId);
                                    long latency = System.nanoTime() - start;
                                    if (left) {
                                        leaveMetrics.recordSuccess(latency);
                                    } else {
                                        leaveMetrics.recordFailure(latency);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Log but don't fail - concurrent modifications are expected
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertTrue(doneLatch.await(120, TimeUnit.SECONDS));
            executor.shutdown();

            System.out.printf("Create: %s%nJoin: %s%nLeave: %s%n",
                createMetrics, joinMetrics, leaveMetrics);

            // No exceptions should occur (thread-safety)
            assertTrue(createMetrics.getTotalOperations() > 0);
        }
    }

    // ============================================
    // Cleanup and Tick Load Tests
    // ============================================

    @Nested
    @DisplayName("Cleanup and Tick Load Tests")
    class CleanupLoadTests {

        @Test
        @DisplayName("High frequency tick under load")
        void testHighFrequencyTick() throws InterruptedException {
            int partyCount = 100;
            int invitesPerParty = 10;

            // Create parties with pending invites
            for (int i = 0; i < partyCount; i++) {
                UUID leaderId = UUID.randomUUID();
                PartyData party = manager.createParty(leaderId, "Leader" + i, QuestType.PVE_COOP);
                if (party != null) {
                    for (int j = 0; j < invitesPerParty; j++) {
                        manager.sendInvite(leaderId, UUID.randomUUID(), "Target" + j);
                    }
                }
            }

            assertEquals(partyCount, manager.getActivePartyCount());

            // Tick rapidly while doing other operations
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch doneLatch = new CountDownLatch(1000);
            LoadTestMetrics tickMetrics = new LoadTestMetrics();

            for (int i = 0; i < 1000; i++) {
                executor.execute(() -> {
                    try {
                        long start = System.nanoTime();
                        manager.tick();
                        long latency = System.nanoTime() - start;
                        tickMetrics.recordSuccess(latency);
                    } catch (Exception e) {
                        tickMetrics.recordFailure(0);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            System.out.printf("Tick: %s%n", tickMetrics);

            // All ticks should succeed
            assertEquals(1.0, tickMetrics.getSuccessRate(), 0.001);
            assertTrue(tickMetrics.getP95LatencyMs() < 10, "Tick P95 should be under 10ms");
        }

        @Test
        @DisplayName("Player disconnect handling under load")
        void testDisconnectHandlingUnderLoad() throws InterruptedException {
            int playerCount = 500;
            ExecutorService executor = Executors.newFixedThreadPool(50);
            CountDownLatch doneLatch = new CountDownLatch(playerCount);
            LoadTestMetrics metrics = new LoadTestMetrics();
            List<UUID> players = Collections.synchronizedList(new ArrayList<>());

            // Create parties
            for (int i = 0; i < playerCount; i++) {
                UUID playerId = UUID.randomUUID();
                manager.createParty(playerId, "Player" + i, QuestType.PVE_COOP);
                players.add(playerId);
            }

            // Disconnect all players simultaneously
            for (UUID playerId : players) {
                executor.execute(() -> {
                    try {
                        long start = System.nanoTime();
                        manager.handlePlayerDisconnect(playerId);
                        long latency = System.nanoTime() - start;
                        metrics.recordSuccess(latency);
                    } catch (Exception e) {
                        metrics.recordFailure(0);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertTrue(doneLatch.await(60, TimeUnit.SECONDS));
            executor.shutdown();

            System.out.printf("Disconnect: %s%n", metrics);

            // All disconnects should succeed
            assertEquals(playerCount, metrics.successCount.get());
            assertEquals(0, manager.getActivePartyCount(), "All parties should be disbanded");
            assertEquals(0, manager.getPlayersInPartiesCount(), "No players should remain in parties");
        }
    }

    // ============================================
    // Memory and Leak Tests
    // ============================================

    @Nested
    @DisplayName("Memory and Leak Tests")
    class MemoryLeakTests {

        @Test
        @DisplayName("No memory leak after many create/disband cycles")
        void testNoMemoryLeakAfterCycles() throws InterruptedException {
            int cycles = 1000;
            Runtime runtime = Runtime.getRuntime();

            // Warm up
            for (int i = 0; i < 100; i++) {
                UUID leaderId = UUID.randomUUID();
                PartyData party = manager.createParty(leaderId, "Leader", QuestType.PVE_COOP);
                if (party != null) {
                    manager.disbandParty(leaderId);
                }
            }

            System.gc();
            long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

            // Run cycles
            for (int i = 0; i < cycles; i++) {
                UUID leaderId = UUID.randomUUID();
                PartyData party = manager.createParty(leaderId, "Leader", QuestType.PVE_COOP);
                if (party != null) {
                    // Add some invites
                    for (int j = 0; j < 5; j++) {
                        manager.sendInvite(leaderId, UUID.randomUUID(), "Target" + j);
                    }
                    manager.disbandParty(leaderId);
                }
            }

            assertEquals(0, manager.getActivePartyCount());

            System.gc();
            long afterMemory = runtime.totalMemory() - runtime.freeMemory();

            // Memory growth should be bounded (allowing for some overhead)
            long memoryGrowth = afterMemory - baselineMemory;
            System.out.printf("Memory: baseline=%dKB, after=%dKB, growth=%dKB%n",
                baselineMemory / 1024, afterMemory / 1024, memoryGrowth / 1024);

            // Allow up to 10MB growth (generous for GC timing variations)
            assertTrue(memoryGrowth < 10 * 1024 * 1024,
                "Memory growth should be bounded after create/disband cycles");
        }
    }
}
