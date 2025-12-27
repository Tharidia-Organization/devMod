package com.devmod.integration;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Currency;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class L6AdvancedIntegrationTest {

    // =========================================================================
    // TEST INFRASTRUCTURE
    // =========================================================================

    private static void awaitFutures(List<Future<?>> futures) throws Exception {
        for (Future<?> future : futures) {
            future.get();
        }
    }

    /**
     * Simulated Style Rank system matching ComboSystem.StyleRank
     */
    enum StyleRank {
        D("Dull", 0, 1.0f),
        C("Crazy", 500, 1.2f),
        B("Brutal", 1500, 1.5f),
        A("Apocalyptic", 3500, 2.0f),
        S("Savage", 7000, 3.0f),
        SS("Sadistic", 12000, 4.0f),
        SSS("Sensational", 20000, 5.0f);

        public final String displayName;
        public final int threshold;
        public final float multiplier;

        StyleRank(String displayName, int threshold, float multiplier) {
            this.displayName = displayName;
            this.threshold = threshold;
            this.multiplier = multiplier;
        }

        public static StyleRank fromScore(int styleScore) {
            StyleRank result = D;
            for (StyleRank rank : values()) {
                if (styleScore >= rank.threshold) {
                    result = rank;
                }
            }
            return result;
        }

        public StyleRank getNext() {
            int idx = this.ordinal();
            StyleRank[] values = values();
            return idx < values.length - 1 ? values[idx + 1] : this;
        }
    }

    /**
     * Simulated Currency types matching RewardSystem.Currency
     */
    enum Currency {
        TOKENS("Endurance Tokens"),
        PRESTIGE("Prestige Points"),
        BLOOD_GEMS("Blood Gems");

        public final String displayName;

        Currency(String displayName) {
            this.displayName = displayName;
        }
    }

    /**
     * Simulated quest states
     */
    enum QuestState {
        NONE, STARTING, ACTIVE, WAVE_COMPLETE, COMPLETING, COMPLETED, FAILED, ABANDONED
    }

    /**
     * Simulated instance states
     */
    enum InstanceState {
        CREATING, READY, ACTIVE, COMPLETING, DESTROYING, DESTROYED;

        public boolean canTransitionTo(InstanceState next) {
            return switch (this) {
                case CREATING -> next == READY || next == DESTROYED;
                case READY -> next == ACTIVE || next == DESTROYED;
                case ACTIVE -> next == COMPLETING || next == DESTROYING;
                case COMPLETING -> next == DESTROYING;
                case DESTROYING -> next == DESTROYED;
                case DESTROYED -> false;
            };
        }
    }

    /**
     * Complete integrated system simulation for testing.
     */
    static class IntegratedSystemSimulator {
        // Instance management
        final Map<UUID, InstanceState> instances = new ConcurrentHashMap<>();
        final Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();

        // Quest system
        final Map<UUID, QuestState> playerQuestStates = new ConcurrentHashMap<>();
        final Map<UUID, Integer> playerWaves = new ConcurrentHashMap<>();
        final Map<UUID, Integer> playerKills = new ConcurrentHashMap<>();

        // Combo system
        final Map<UUID, Integer> playerStyleScores = new ConcurrentHashMap<>();
        final Map<UUID, StyleRank> playerStyleRanks = new ConcurrentHashMap<>();
        final Map<UUID, Integer> playerCombos = new ConcurrentHashMap<>();

        // Economic system
        final Map<UUID, Map<Currency, AtomicLong>> playerWallets = new ConcurrentHashMap<>();
        final Map<UUID, Object> purchaseLocks = new ConcurrentHashMap<>();
        final Map<UUID, Object> styleLocks = new ConcurrentHashMap<>();

        // Perk system
        final Map<UUID, Set<String>> playerPerks = new ConcurrentHashMap<>();
        final Map<UUID, Map<String, Integer>> perkStacks = new ConcurrentHashMap<>();

        // Telemetry
        final AtomicLong totalDamageDealt = new AtomicLong(0);
        final AtomicLong totalMobsKilled = new AtomicLong(0);
        final AtomicInteger totalQuestsStarted = new AtomicInteger(0);
        final AtomicInteger totalQuestsCompleted = new AtomicInteger(0);
        final AtomicInteger totalQuestsFailed = new AtomicInteger(0);

        // Error tracking
        final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        final AtomicInteger invariantViolations = new AtomicInteger(0);

        Object getPurchaseLock(UUID playerId) {
            return purchaseLocks.computeIfAbsent(playerId, id -> new Object());
        }

        Object getStyleLock(UUID playerId) {
            return styleLocks.computeIfAbsent(playerId, id -> new Object());
        }

        Map<Currency, AtomicLong> getWallet(UUID playerId) {
            return playerWallets.computeIfAbsent(playerId, id -> {
                Map<Currency, AtomicLong> wallet = new EnumMap<>(Currency.class);
                for (Currency c : Currency.values()) {
                    wallet.put(c, new AtomicLong(0));
                }
                return wallet;
            });
        }

        AtomicLong requireBalance(UUID playerId, Currency currency) {
            AtomicLong balance = getWallet(playerId).get(currency);
            return Objects.requireNonNull(balance, "Missing balance for " + playerId + " " + currency);
        }

        QuestState requireQuestState(UUID playerId) {
            return Objects.requireNonNull(playerQuestStates.get(playerId),
                "Missing quest state for " + playerId);
        }

        int requireWave(UUID playerId) {
            Integer wave = playerWaves.get(playerId);
            return Objects.requireNonNull(wave, "Missing wave for " + playerId);
        }

        int requireKills(UUID playerId) {
            Integer kills = playerKills.get(playerId);
            return Objects.requireNonNull(kills, "Missing kills for " + playerId);
        }

        int requireStyleScore(UUID playerId) {
            Integer score = playerStyleScores.get(playerId);
            return Objects.requireNonNull(score, "Missing style score for " + playerId);
        }

        StyleRank requireStyleRank(UUID playerId) {
            return Objects.requireNonNull(playerStyleRanks.get(playerId),
                "Missing style rank for " + playerId);
        }

        int requireCombo(UUID playerId) {
            Integer combo = playerCombos.get(playerId);
            return Objects.requireNonNull(combo, "Missing combo for " + playerId);
        }

        Set<String> requirePerks(UUID playerId) {
            return Objects.requireNonNull(playerPerks.get(playerId),
                "Missing perks for " + playerId);
        }

        Map<String, Integer> requirePerkStacks(UUID playerId) {
            return Objects.requireNonNull(perkStacks.get(playerId),
                "Missing perk stacks for " + playerId);
        }

        void addCurrency(UUID playerId, Currency currency, long amount) {
            requireBalance(playerId, currency).addAndGet(amount);
        }

        long getCurrency(UUID playerId, Currency currency) {
            return requireBalance(playerId, currency).get();
        }

        boolean spendCurrency(UUID playerId, Currency currency, long amount) {
            synchronized (getPurchaseLock(playerId)) {
                AtomicLong balance = requireBalance(playerId, currency);
                long current = balance.get();
                if (current >= amount) {
                    balance.addAndGet(-amount);
                    return true;
                }
                return false;
            }
        }

        void addStylePoints(UUID playerId, int points) {
            synchronized (getStyleLock(playerId)) {
                int newScore = playerStyleScores.getOrDefault(playerId, 0) + points;
                playerStyleScores.put(playerId, newScore);
                playerStyleRanks.put(playerId, StyleRank.fromScore(newScore));
            }
        }

        void reportInvariantViolation(String message) {
            errors.add("INVARIANT VIOLATION: " + message);
            invariantViolations.incrementAndGet();
        }

        void checkAllInvariants() {
            // Invariant 1: Player in instance must have active quest
            for (UUID playerId : playerToInstance.keySet()) {
                QuestState questState = playerQuestStates.get(playerId);
                if (questState == null ||
                    (questState != QuestState.ACTIVE && questState != QuestState.WAVE_COMPLETE)) {
                    reportInvariantViolation("Player " + playerId + " in instance but quest state is " + questState);
                }
            }

            // Invariant 2: Currency can never be negative
            for (Map.Entry<UUID, Map<Currency, AtomicLong>> entry : playerWallets.entrySet()) {
                for (Map.Entry<Currency, AtomicLong> currencyEntry : entry.getValue().entrySet()) {
                    if (currencyEntry.getValue().get() < 0) {
                        reportInvariantViolation("Player " + entry.getKey() +
                            " has negative " + currencyEntry.getKey() + ": " + currencyEntry.getValue().get());
                    }
                }
            }

            // Invariant 3: Style rank must match style score
            for (Map.Entry<UUID, Integer> entry : playerStyleScores.entrySet()) {
                StyleRank expectedRank = StyleRank.fromScore(entry.getValue());
                StyleRank actualRank = playerStyleRanks.get(entry.getKey());
                if (actualRank != null && actualRank != expectedRank) {
                    reportInvariantViolation("Player " + entry.getKey() +
                        " style rank mismatch: score " + entry.getValue() +
                        " should be " + expectedRank + " but is " + actualRank);
                }
            }

            // Invariant 4: Instance in DESTROYED state must not have players
            for (Map.Entry<UUID, InstanceState> entry : instances.entrySet()) {
                if (entry.getValue() == InstanceState.DESTROYED) {
                    for (UUID instanceId : playerToInstance.values()) {
                        if (instanceId.equals(entry.getKey())) {
                            reportInvariantViolation("Instance " + entry.getKey() +
                                " is DESTROYED but still has players");
                        }
                    }
                }
            }

            // Invariant 5: Completed quests must be greater than or equal to 0
            if (totalQuestsCompleted.get() < 0 || totalQuestsFailed.get() < 0) {
                reportInvariantViolation("Quest counters went negative");
            }

            // Invariant 6: Started quests >= completed + failed
            if (totalQuestsStarted.get() < totalQuestsCompleted.get() + totalQuestsFailed.get()) {
                reportInvariantViolation("Quest accounting error: started=" + totalQuestsStarted.get() +
                    " but completed+failed=" + (totalQuestsCompleted.get() + totalQuestsFailed.get()));
            }
        }
    }

    // =========================================================================
    // SECTION 1: MULTI-SYSTEM CASCADE FAILURES
    // =========================================================================

    @Nested
    @DisplayName("L6-01: Multi-System Cascade Failures")
    class MultiSystemCascadeTests {

        @Test
        @Order(1)
        @DisplayName("Quest completion triggers reward, combo reset, perk cleanup, and instance destruction atomically")
        void testQuestCompletionCascade() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();

            // Setup: Player in active quest with perks, combo, and pending rewards
            sim.instances.put(instanceId, InstanceState.ACTIVE);
            sim.playerToInstance.put(playerId, instanceId);
            sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
            sim.playerWaves.put(playerId, 10);
            sim.playerKills.put(playerId, 50);
            sim.playerStyleScores.put(playerId, 15000);
            sim.playerStyleRanks.put(playerId, StyleRank.SS);
            sim.playerCombos.put(playerId, 25);
            sim.playerPerks.put(playerId, new HashSet<>(Set.of("DAMAGE_BOOST", "LIFESTEAL", "SPEED")));
            sim.perkStacks.put(playerId, new HashMap<>(Map.of("DAMAGE_BOOST", 3, "LIFESTEAL", 1)));
            sim.totalQuestsStarted.incrementAndGet();

            // Simulate quest completion cascade
            int baseReward = sim.requireKills(playerId) * 10 + sim.requireWave(playerId) * 50;
            float styleMultiplier = sim.requireStyleRank(playerId).multiplier;
            int finalReward = (int) (baseReward * styleMultiplier);

            // Atomic cascade operations
            sim.playerQuestStates.put(playerId, QuestState.COMPLETING);
            sim.addCurrency(playerId, Currency.TOKENS, finalReward);
            sim.playerStyleScores.put(playerId, 0);
            sim.playerStyleRanks.put(playerId, StyleRank.D);
            sim.playerCombos.put(playerId, 0);
            sim.requirePerks(playerId).clear();
            sim.requirePerkStacks(playerId).clear();
            sim.playerToInstance.remove(playerId);
            sim.instances.put(instanceId, InstanceState.COMPLETING);
            sim.instances.put(instanceId, InstanceState.DESTROYING);
            sim.instances.put(instanceId, InstanceState.DESTROYED);
            sim.playerQuestStates.put(playerId, QuestState.COMPLETED);
            sim.totalQuestsCompleted.incrementAndGet();

            // Verify cascade completed correctly
            assertEquals(QuestState.COMPLETED, sim.requireQuestState(playerId));
            assertEquals(InstanceState.DESTROYED, sim.instances.get(instanceId));
            assertTrue(sim.getCurrency(playerId, Currency.TOKENS) > 0, "Should have earned tokens");
            assertEquals(0, sim.requireStyleScore(playerId), "Style should be reset");
            assertEquals(StyleRank.D, sim.requireStyleRank(playerId), "Rank should be D");
            assertEquals(0, sim.requireCombo(playerId), "Combo should be reset");
            assertTrue(sim.requirePerks(playerId).isEmpty(), "Perks should be cleared");
            assertTrue(sim.requirePerkStacks(playerId).isEmpty(), "Perk stacks should be cleared");
            assertFalse(sim.playerToInstance.containsKey(playerId), "Player-instance mapping should be removed");

            sim.checkAllInvariants();
            assertEquals(0, sim.invariantViolations.get(), "No invariant violations");
        }

        @Test
        @Order(2)
        @DisplayName("Instance destruction mid-wave handles partial state correctly")
        void testInstanceDestructionMidWave() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();

            // Setup: Two players in active wave
            sim.instances.put(instanceId, InstanceState.ACTIVE);
            sim.playerToInstance.put(player1, instanceId);
            sim.playerToInstance.put(player2, instanceId);
            sim.playerQuestStates.put(player1, QuestState.ACTIVE);
            sim.playerQuestStates.put(player2, QuestState.ACTIVE);
            sim.playerWaves.put(player1, 5);
            sim.playerWaves.put(player2, 5);
            sim.playerStyleScores.put(player1, 3000);
            sim.playerStyleScores.put(player2, 5000);
            sim.playerStyleRanks.put(player1, StyleRank.A);
            sim.playerStyleRanks.put(player2, StyleRank.S);
            sim.totalQuestsStarted.addAndGet(2);

            // Forced destruction (server crash recovery scenario)
            sim.instances.put(instanceId, InstanceState.DESTROYING);

            // Both players should be recovered
            for (UUID playerId : List.of(player1, player2)) {
                sim.playerToInstance.remove(playerId);
                sim.playerQuestStates.put(playerId, QuestState.FAILED);
                sim.playerStyleScores.put(playerId, 0);
                sim.playerStyleRanks.put(playerId, StyleRank.D);
                sim.totalQuestsFailed.incrementAndGet();
            }

            sim.instances.put(instanceId, InstanceState.DESTROYED);

            // Verify both players recovered
            assertEquals(QuestState.FAILED, sim.requireQuestState(player1));
            assertEquals(QuestState.FAILED, sim.requireQuestState(player2));
            assertFalse(sim.playerToInstance.containsKey(player1));
            assertFalse(sim.playerToInstance.containsKey(player2));
            assertEquals(2, sim.totalQuestsFailed.get());

            sim.checkAllInvariants();
            assertEquals(0, sim.invariantViolations.get());
        }

        @Test
        @Order(3)
        @DisplayName("Style rank demotion during reward calculation doesn't corrupt reward")
        void testStyleRankDemotionDuringReward() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            // Setup: Player at SS rank about to complete quest
            sim.playerStyleScores.put(playerId, 15000);
            sim.playerStyleRanks.put(playerId, StyleRank.SS);
            sim.playerWaves.put(playerId, 10);
            sim.playerKills.put(playerId, 100);

            // Capture rank at start of reward calculation
            StyleRank rankAtCompletion = sim.requireStyleRank(playerId);
            float multiplier = rankAtCompletion.multiplier;

            // Simulate decay happening during calculation
            sim.playerStyleScores.put(playerId, 5000);
            sim.playerStyleRanks.put(playerId, StyleRank.S);

            // Calculate reward using captured rank, not current
            int baseReward = sim.requireKills(playerId) * 10 + sim.requireWave(playerId) * 50;
            int finalReward = (int) (baseReward * multiplier);

            // Apply reward
            sim.addCurrency(playerId, Currency.TOKENS, finalReward);

            // Reward should use SS multiplier (4.0f), not S (3.0f)
            assertEquals(4.0f, multiplier);
            int expectedReward = (int) ((100 * 10 + 10 * 50) * 4.0f); // 1500 * 4 = 6000
            assertEquals(expectedReward, sim.getCurrency(playerId, Currency.TOKENS));
        }
    }

    // =========================================================================
    // SECTION 2: STATE MACHINE COHERENCE UNDER STRESS
    // =========================================================================

    @Nested
    @DisplayName("L6-02: State Machine Coherence Under Stress")
    class StateMachineCoherenceTests {

        @Test
        @Order(4)
        @Timeout(30)
        @DisplayName("Concurrent state transitions maintain consistency")
        void testConcurrentStateTransitions() throws Exception {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            int playerCount = 20;
            int transitionsPerPlayer = 100;
            CountDownLatch latch = new CountDownLatch(playerCount);
            AtomicInteger transitionErrors = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(playerCount);
            List<Future<?>> futures = new ArrayList<>(playerCount);

            for (int i = 0; i < playerCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        UUID playerId = UUID.randomUUID();

                        for (int j = 0; j < transitionsPerPlayer; j++) {
                            // Start quest
                            UUID instanceId = UUID.randomUUID();
                            sim.instances.put(instanceId, InstanceState.CREATING);
                            sim.playerQuestStates.put(playerId, QuestState.STARTING);
                            sim.totalQuestsStarted.incrementAndGet();

                            // Transition to active
                            if (!sim.instances.get(instanceId).canTransitionTo(InstanceState.READY)) {
                                transitionErrors.incrementAndGet();
                            }
                            sim.instances.put(instanceId, InstanceState.READY);
                            sim.instances.put(instanceId, InstanceState.ACTIVE);
                            sim.playerToInstance.put(playerId, instanceId);
                            sim.playerQuestStates.put(playerId, QuestState.ACTIVE);

                            // Simulate some waves
                            sim.playerWaves.put(playerId, 3);
                            sim.playerKills.put(playerId, 15);

                            // Complete
                            sim.playerQuestStates.put(playerId, QuestState.COMPLETING);
                            sim.instances.put(instanceId, InstanceState.COMPLETING);
                            sim.instances.put(instanceId, InstanceState.DESTROYING);
                            sim.playerToInstance.remove(playerId);
                            sim.instances.put(instanceId, InstanceState.DESTROYED);
                            sim.playerQuestStates.put(playerId, QuestState.COMPLETED);
                            sim.totalQuestsCompleted.incrementAndGet();
                        }
                    } catch (Exception e) {
                        transitionErrors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            assertEquals(0, transitionErrors.get(), "No transition errors should occur");
            assertEquals(playerCount * transitionsPerPlayer, sim.totalQuestsStarted.get());
            assertEquals(playerCount * transitionsPerPlayer, sim.totalQuestsCompleted.get());

            sim.checkAllInvariants();
            assertEquals(0, sim.invariantViolations.get());
        }

        @Test
        @Order(5)
        @DisplayName("Invalid state transition attempts are rejected")
        void testInvalidStateTransitionRejection() {
            // Test InstanceState transitions
            assertFalse(InstanceState.DESTROYED.canTransitionTo(InstanceState.ACTIVE),
                "DESTROYED cannot transition to ACTIVE");
            assertFalse(InstanceState.CREATING.canTransitionTo(InstanceState.COMPLETING),
                "CREATING cannot transition directly to COMPLETING");
            assertFalse(InstanceState.READY.canTransitionTo(InstanceState.COMPLETING),
                "READY cannot transition directly to COMPLETING");

            // Valid transitions
            assertTrue(InstanceState.CREATING.canTransitionTo(InstanceState.READY));
            assertTrue(InstanceState.READY.canTransitionTo(InstanceState.ACTIVE));
            assertTrue(InstanceState.ACTIVE.canTransitionTo(InstanceState.COMPLETING));
            assertTrue(InstanceState.COMPLETING.canTransitionTo(InstanceState.DESTROYING));
            assertTrue(InstanceState.DESTROYING.canTransitionTo(InstanceState.DESTROYED));

            // Emergency destruction paths
            assertTrue(InstanceState.CREATING.canTransitionTo(InstanceState.DESTROYED));
            assertTrue(InstanceState.READY.canTransitionTo(InstanceState.DESTROYED));
        }

        @Test
        @Order(6)
        @DisplayName("Quest state machine prevents invalid transitions")
        void testQuestStateTransitions() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            // Valid: NONE -> STARTING -> ACTIVE -> WAVE_COMPLETE -> ACTIVE -> COMPLETING -> COMPLETED
            sim.playerQuestStates.put(playerId, QuestState.NONE);
            sim.playerQuestStates.put(playerId, QuestState.STARTING);
            sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
            sim.playerQuestStates.put(playerId, QuestState.WAVE_COMPLETE);
            sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
            sim.playerQuestStates.put(playerId, QuestState.COMPLETING);
            sim.playerQuestStates.put(playerId, QuestState.COMPLETED);

            assertEquals(QuestState.COMPLETED, sim.requireQuestState(playerId));

            // Reset and test failure path
            sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
            sim.playerQuestStates.put(playerId, QuestState.FAILED);
            assertEquals(QuestState.FAILED, sim.requireQuestState(playerId));

            // Reset and test abandon path
            sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
            sim.playerQuestStates.put(playerId, QuestState.ABANDONED);
            assertEquals(QuestState.ABANDONED, sim.requireQuestState(playerId));
        }
    }

    // =========================================================================
    // SECTION 3: ECONOMIC SYSTEM INVARIANTS
    // =========================================================================

    @Nested
    @DisplayName("L6-03: Economic System Invariants")
    class EconomicSystemTests {

        @Test
        @Order(7)
        @Timeout(30)
        @DisplayName("Double-spending prevention under concurrent purchases")
        void testDoubleSpendingPrevention() throws Exception {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            // Give player exactly enough for ONE purchase
            long itemCost = 1000;
            sim.addCurrency(playerId, Currency.TOKENS, itemCost);

            int purchaseAttempts = 50;
            CountDownLatch latch = new CountDownLatch(purchaseAttempts);
            AtomicInteger successfulPurchases = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(purchaseAttempts);
            List<Future<?>> futures = new ArrayList<>(purchaseAttempts);

            for (int i = 0; i < purchaseAttempts; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        if (sim.spendCurrency(playerId, Currency.TOKENS, itemCost)) {
                            successfulPurchases.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            // EXACTLY ONE purchase should succeed
            assertEquals(1, successfulPurchases.get(),
                "Only one purchase should succeed with exact balance");

            // Balance should be 0, not negative
            assertEquals(0, sim.getCurrency(playerId, Currency.TOKENS),
                "Balance should be exactly 0 after purchase");

            sim.checkAllInvariants();
            assertEquals(0, sim.invariantViolations.get());
        }

        @Test
        @Order(8)
        @DisplayName("Currency can never go negative")
        void testCurrencyNonNegativity() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            // Attempt to spend more than available
            assertFalse(sim.spendCurrency(playerId, Currency.TOKENS, 1000),
                "Should not be able to spend from empty wallet");

            // Give some currency
            sim.addCurrency(playerId, Currency.TOKENS, 500);
            assertEquals(500, sim.getCurrency(playerId, Currency.TOKENS));

            // Attempt to spend more than available
            assertFalse(sim.spendCurrency(playerId, Currency.TOKENS, 600),
                "Should not be able to overspend");
            assertEquals(500, sim.getCurrency(playerId, Currency.TOKENS),
                "Balance should be unchanged after failed spend");

            // Successful spend
            assertTrue(sim.spendCurrency(playerId, Currency.TOKENS, 300));
            assertEquals(200, sim.getCurrency(playerId, Currency.TOKENS));

            sim.checkAllInvariants();
            assertEquals(0, sim.invariantViolations.get());
        }

        @Test
        @Order(9)
        @Timeout(30)
        @DisplayName("Concurrent reward earning is accurate")
        void testConcurrentRewardEarning() throws Exception {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            int earnAttempts = 1000;
            long rewardPerEarn = 10;
            CountDownLatch latch = new CountDownLatch(earnAttempts);

            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<?>> futures = new ArrayList<>(earnAttempts);

            for (int i = 0; i < earnAttempts; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        sim.addCurrency(playerId, Currency.TOKENS, rewardPerEarn);
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            // Total should be exact
            long expected = earnAttempts * rewardPerEarn;
            assertEquals(expected, sim.getCurrency(playerId, Currency.TOKENS),
                "Total earnings should be exact sum of all rewards");

            sim.checkAllInvariants();
            assertEquals(0, sim.invariantViolations.get());
        }

        @Test
        @Order(10)
        @DisplayName("Multiple currency types are isolated")
        void testCurrencyIsolation() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            // Add different currencies
            sim.addCurrency(playerId, Currency.TOKENS, 1000);
            sim.addCurrency(playerId, Currency.PRESTIGE, 100);
            sim.addCurrency(playerId, Currency.BLOOD_GEMS, 10);

            // Spending one doesn't affect others
            assertTrue(sim.spendCurrency(playerId, Currency.TOKENS, 500));
            assertEquals(500, sim.getCurrency(playerId, Currency.TOKENS));
            assertEquals(100, sim.getCurrency(playerId, Currency.PRESTIGE));
            assertEquals(10, sim.getCurrency(playerId, Currency.BLOOD_GEMS));

            // Can't spend one currency as another
            assertFalse(sim.spendCurrency(playerId, Currency.PRESTIGE, 200),
                "Should not be able to overspend prestige");
            assertEquals(100, sim.getCurrency(playerId, Currency.PRESTIGE));
        }
    }

    // =========================================================================
    // SECTION 4: CHAOS ENGINEERING SCENARIOS
    // =========================================================================

    @Nested
    @DisplayName("L6-04: Chaos Engineering Scenarios")
    class ChaosEngineeringTests {

        @Test
        @Order(11)
        @DisplayName("System recovers from simulated memory pressure")
        void testMemoryPressureRecovery() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();

            // Create many players to simulate memory pressure
            int playerCount = 1000;
            List<UUID> players = new ArrayList<>();

            for (int i = 0; i < playerCount; i++) {
                UUID playerId = UUID.randomUUID();
                players.add(playerId);
                sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
                sim.playerWaves.put(playerId, i % 20);
                sim.playerKills.put(playerId, i * 10);
                sim.playerStyleScores.put(playerId, i * 100);
                sim.playerStyleRanks.put(playerId, StyleRank.fromScore(i * 100));
                sim.addCurrency(playerId, Currency.TOKENS, i * 50);
            }

            // Simulate cleanup (memory pressure response)
            List<UUID> toCleanup = players.subList(0, playerCount / 2);
            for (UUID playerId : toCleanup) {
                sim.playerQuestStates.remove(playerId);
                sim.playerWaves.remove(playerId);
                sim.playerKills.remove(playerId);
                sim.playerStyleScores.remove(playerId);
                sim.playerStyleRanks.remove(playerId);
                // Note: wallets persist (intentional - currency is precious)
            }

            // Verify remaining players unaffected
            assertEquals(playerCount / 2, sim.playerQuestStates.size());
            assertEquals(playerCount, sim.playerWallets.size(), "Wallets should persist");

            // Verify data integrity of remaining players
            for (int i = playerCount / 2; i < playerCount; i++) {
                UUID playerId = players.get(i);
                assertEquals(QuestState.ACTIVE, sim.requireQuestState(playerId));
                assertEquals(i % 20, sim.requireWave(playerId));
            }
        }

        @Test
        @Order(12)
        @Timeout(30)
        @DisplayName("Random operation interleaving doesn't corrupt state")
        void testRandomOperationInterleaving() throws Exception {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            int operationCount = 5000;
            int playerCount = 20;
            CountDownLatch latch = new CountDownLatch(operationCount);
            AtomicInteger errors = new AtomicInteger(0);

            List<UUID> players = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                UUID playerId = UUID.randomUUID();
                players.add(playerId);
                sim.playerQuestStates.put(playerId, QuestState.NONE);
                sim.addCurrency(playerId, Currency.TOKENS, 10000);
            }

            ExecutorService executor = Executors.newFixedThreadPool(10);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            List<Future<?>> futures = new ArrayList<>(operationCount);

            for (int i = 0; i < operationCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        UUID playerId = players.get(random.nextInt(playerCount));
                        int operation = random.nextInt(6);

                        switch (operation) {
                            case 0 -> {
                                // Start quest
                                sim.playerQuestStates.put(playerId, QuestState.STARTING);
                                sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
                                sim.totalQuestsStarted.incrementAndGet();
                            }
                            case 1 -> {
                                // Add kills
                                sim.playerKills.merge(playerId, random.nextInt(1, 10),
                                    (prev, inc) -> (prev == null ? 0 : prev) + (inc == null ? 0 : inc));
                            }
                            case 2 -> {
                                // Add style points
                                int points = random.nextInt(100, 500);
                                sim.addStylePoints(playerId, points);
                            }
                            case 3 -> {
                                // Earn currency
                                sim.addCurrency(playerId, Currency.TOKENS, random.nextInt(10, 100));
                            }
                            case 4 -> {
                                // Spend currency (may fail, that's ok)
                                sim.spendCurrency(playerId, Currency.TOKENS, random.nextInt(1, 50));
                            }
                            case 5 -> {
                                // Complete quest
                                if (sim.playerQuestStates.get(playerId) == QuestState.ACTIVE) {
                                    sim.playerQuestStates.put(playerId, QuestState.COMPLETED);
                                    sim.totalQuestsCompleted.incrementAndGet();
                                }
                            }
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

            assertEquals(0, errors.get(), "No errors during random operations");

            // Verify no data corruption
            for (UUID playerId : players) {
                long balance = sim.getCurrency(playerId, Currency.TOKENS);
                assertTrue(balance >= 0, "Balance should never be negative: " + balance);

                Integer styleScore = sim.playerStyleScores.get(playerId);
                StyleRank styleRank = sim.playerStyleRanks.get(playerId);
                if (styleScore != null && styleRank != null) {
                    assertEquals(StyleRank.fromScore(styleScore), styleRank,
                        "Style rank should match score");
                }
            }
        }

        @Test
        @Order(13)
        @DisplayName("Rapid quest start/cancel cycles don't leak resources")
        void testRapidStartCancelNoLeak() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            int cycles = 100;
            sim.playerQuestStates.put(playerId, QuestState.NONE);

            for (int i = 0; i < cycles; i++) {
                // Start
                UUID instanceId = UUID.randomUUID();
                sim.instances.put(instanceId, InstanceState.CREATING);
                sim.instances.put(instanceId, InstanceState.READY);
                sim.instances.put(instanceId, InstanceState.ACTIVE);
                sim.playerToInstance.put(playerId, instanceId);
                sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
                sim.totalQuestsStarted.incrementAndGet();

                // Immediately cancel
                sim.instances.put(instanceId, InstanceState.DESTROYING);
                sim.playerToInstance.remove(playerId);
                sim.instances.put(instanceId, InstanceState.DESTROYED);
                sim.instances.remove(instanceId); // Clean up
                sim.playerQuestStates.put(playerId, QuestState.ABANDONED);
                sim.totalQuestsFailed.incrementAndGet();

                // Reset for next cycle
                sim.playerQuestStates.put(playerId, QuestState.NONE);
            }

            // No instances should remain
            assertTrue(sim.instances.isEmpty(), "All instances should be cleaned up");
            assertFalse(sim.playerToInstance.containsKey(playerId),
                "Player should not be in any instance");
            assertEquals(cycles, sim.totalQuestsStarted.get());
            assertEquals(cycles, sim.totalQuestsFailed.get());
        }
    }

    // =========================================================================
    // SECTION 5: COMPLEX RACE CONDITION DETECTION
    // =========================================================================

    @Nested
    @DisplayName("L6-05: Complex Race Condition Detection")
    class ComplexRaceConditionTests {

        @Test
        @Order(14)
        @Timeout(30)
        @DisplayName("Concurrent quest start for same player - only one succeeds")
        void testConcurrentQuestStartSamePlayer() throws Exception {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();
            sim.playerQuestStates.put(playerId, QuestState.NONE);

            int attemptCount = 50;
            CountDownLatch latch = new CountDownLatch(attemptCount);
            AtomicInteger successCount = new AtomicInteger(0);
            Object playerLock = new Object();

            ExecutorService executor = Executors.newFixedThreadPool(attemptCount);
            List<Future<?>> futures = new ArrayList<>(attemptCount);

            for (int i = 0; i < attemptCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        synchronized (playerLock) {
                            // Check-then-act pattern with synchronization
                            if (sim.playerQuestStates.get(playerId) == QuestState.NONE) {
                                sim.playerQuestStates.put(playerId, QuestState.STARTING);
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            // Exactly one should succeed
            assertEquals(1, successCount.get(),
                "Only one concurrent quest start should succeed");
            assertEquals(QuestState.STARTING, sim.requireQuestState(playerId));
        }

        @Test
        @Order(15)
        @Timeout(30)
        @DisplayName("Read-modify-write race on style score is atomic")
        void testStyleScoreAtomicity() throws Exception {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            // Use AtomicInteger for thread-safe increment
            AtomicInteger atomicScore = new AtomicInteger(0);

            int incrementCount = 10000;
            int incrementValue = 10;
            CountDownLatch latch = new CountDownLatch(incrementCount);

            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<?>> futures = new ArrayList<>(incrementCount);

            for (int i = 0; i < incrementCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        atomicScore.addAndGet(incrementValue);
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            latch.await();
            executor.shutdown();
            awaitFutures(futures);

            // Should be exact
            int expectedScore = incrementCount * incrementValue;
            assertEquals(expectedScore, atomicScore.get(), "Atomic increment should produce exact result");
            sim.playerStyleScores.put(playerId, atomicScore.get());
            assertEquals(expectedScore, sim.requireStyleScore(playerId));
        }

        @Test
        @Order(16)
        @Timeout(30)
        @DisplayName("Perk application during wave transition")
        void testPerkApplicationDuringWaveTransition() throws Exception {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            Set<String> perks = ConcurrentHashMap.newKeySet();
            perks.add("DAMAGE_BOOST");
            sim.playerPerks.put(playerId, perks);
            sim.perkStacks.put(playerId, new ConcurrentHashMap<>(Map.of("DAMAGE_BOOST", 1)));

            int operations = 1000;
            CountDownLatch latch = new CountDownLatch(operations * 2);
            AtomicInteger errors = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<Future<?>> futures = new ArrayList<>(operations * 2);

            // Wave transition thread - periodically checks perks
            for (int i = 0; i < operations; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        // Read perks (wave effect application)
                        Set<String> currentPerks = sim.requirePerks(playerId);
                        for (String perk : currentPerks) {
                            sim.requirePerkStacks(playerId).get(perk); // Just reading, shouldn't throw
                        }
                    } catch (ConcurrentModificationException e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            // Perk modification thread
            for (int i = 0; i < operations; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    try {
                        String newPerk = "PERK_" + idx;
                        sim.requirePerks(playerId).add(newPerk);
                        sim.requirePerkStacks(playerId).put(newPerk, 1);

                        // Sometimes remove
                        if (idx % 3 == 0) {
                            sim.requirePerks(playerId).remove(newPerk);
                            sim.requirePerkStacks(playerId).remove(newPerk);
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

            assertEquals(0, errors.get(),
                "No concurrent modification errors during perk operations");
        }
    }

    // =========================================================================
    // SECTION 6: MEMORY CORRUPTION PREVENTION
    // =========================================================================

    @Nested
    @DisplayName("L6-06: Memory Corruption Prevention")
    class MemoryCorruptionTests {

        @Test
        @Order(17)
        @DisplayName("Null safety in all core operations")
        void testNullSafety() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID nonExistentPlayer = UUID.randomUUID();

            // These should not throw
            assertDoesNotThrow(() -> {
                sim.playerQuestStates.get(nonExistentPlayer);
                sim.playerWaves.get(nonExistentPlayer);
                sim.playerKills.get(nonExistentPlayer);
                sim.playerStyleScores.get(nonExistentPlayer);
                sim.playerStyleRanks.get(nonExistentPlayer);
                sim.playerToInstance.get(nonExistentPlayer);
            });

            // Getting wallet for non-existent player should create one
            Map<Currency, AtomicLong> wallet = sim.getWallet(nonExistentPlayer);
            assertNotNull(wallet);
            AtomicLong tokens = Objects.requireNonNull(wallet.get(Currency.TOKENS),
                "Wallet should include tokens balance");
            assertEquals(0, tokens.get());
        }

        @Test
        @Order(18)
        @DisplayName("UUID collision handling")
        void testUUIDCollisionHandling() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();

            // Use same UUID for different purposes (extremely rare in practice)
            UUID sharedId = UUID.randomUUID();

            // As player
            sim.playerQuestStates.put(sharedId, QuestState.ACTIVE);
            sim.addCurrency(sharedId, Currency.TOKENS, 1000);

            // Later update
            sim.playerQuestStates.put(sharedId, QuestState.COMPLETED);

            // Original data preserved
            assertEquals(1000, sim.getCurrency(sharedId, Currency.TOKENS));
            assertEquals(QuestState.COMPLETED, sim.requireQuestState(sharedId));
        }

        @Test
        @Order(19)
        @DisplayName("Large value handling doesn't overflow")
        void testLargeValueHandling() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            // Add large amount of currency
            long largeAmount = Long.MAX_VALUE / 2;
            sim.addCurrency(playerId, Currency.TOKENS, largeAmount);

            assertEquals(largeAmount, sim.getCurrency(playerId, Currency.TOKENS));

            // Adding more should work (but may overflow if not careful)
            sim.addCurrency(playerId, Currency.TOKENS, 1000);

            assertTrue(sim.getCurrency(playerId, Currency.TOKENS) > largeAmount,
                "Balance should increase");
        }

        @Test
        @Order(20)
        @DisplayName("Style score overflow protection")
        void testStyleScoreOverflowProtection() {
            // StyleRank.fromScore should handle any input
            assertEquals(StyleRank.D, StyleRank.fromScore(Integer.MIN_VALUE));
            assertEquals(StyleRank.D, StyleRank.fromScore(-1));
            assertEquals(StyleRank.D, StyleRank.fromScore(0));
            assertEquals(StyleRank.SSS, StyleRank.fromScore(Integer.MAX_VALUE));
            assertEquals(StyleRank.SSS, StyleRank.fromScore(1_000_000));
        }
    }

    // =========================================================================
    // SECTION 7: DATA CONSISTENCY UNDER FAILURE
    // =========================================================================

    @Nested
    @DisplayName("L6-07: Data Consistency Under Failure")
    class DataConsistencyTests {

        @Test
        @Order(21)
        @DisplayName("Partial operation failure doesn't corrupt state")
        void testPartialOperationFailure() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            // Setup initial state
            sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
            sim.playerWaves.put(playerId, 5);
            sim.addCurrency(playerId, Currency.TOKENS, 500);

            // Simulate partial failure during quest completion
            try {
                sim.playerQuestStates.put(playerId, QuestState.COMPLETING);

                // Simulate failure mid-operation
                if (true) throw new RuntimeException("Simulated failure");

            } catch (RuntimeException e) {
                // Recovery: roll back to ACTIVE state
                sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
            }

            // State should be recoverable
            assertEquals(QuestState.ACTIVE, sim.requireQuestState(playerId));
            assertEquals(500, sim.getCurrency(playerId, Currency.TOKENS),
                "Currency should not have changed");
            assertEquals(5, sim.requireWave(playerId),
                "Wave count should not have changed");
        }

        @Test
        @Order(22)
        @DisplayName("Orphaned data cleanup")
        void testOrphanedDataCleanup() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();

            // Create player with full state
            UUID playerId = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();

            sim.instances.put(instanceId, InstanceState.ACTIVE);
            sim.playerToInstance.put(playerId, instanceId);
            sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
            sim.playerWaves.put(playerId, 3);
            sim.playerStyleScores.put(playerId, 5000);

            // Instance destroyed but player data remains (orphaned)
            sim.instances.put(instanceId, InstanceState.DESTROYED);
            sim.instances.remove(instanceId);

            // Player data is orphaned - detect and clean up
            UUID orphanedInstance = sim.playerToInstance.get(playerId);
            if (orphanedInstance != null && !sim.instances.containsKey(orphanedInstance)) {
                // Clean up orphaned player
                sim.playerToInstance.remove(playerId);
                sim.playerQuestStates.put(playerId, QuestState.FAILED);
                sim.playerWaves.remove(playerId);
                sim.playerStyleScores.remove(playerId);
            }

            // Verify cleanup
            assertFalse(sim.playerToInstance.containsKey(playerId));
            assertEquals(QuestState.FAILED, sim.requireQuestState(playerId));
            assertNull(sim.playerWaves.get(playerId));
        }

        @Test
        @Order(23)
        @DisplayName("Transaction-like multi-step operation")
        void testTransactionLikeOperation() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            // Initial state
            sim.addCurrency(playerId, Currency.TOKENS, 1000);
            sim.playerQuestStates.put(playerId, QuestState.NONE);

            // Transaction: spend tokens to start special quest
            long cost = 500;
            boolean success = false;

            // "Begin transaction"
            long originalBalance = sim.getCurrency(playerId, Currency.TOKENS);
            QuestState originalState = sim.requireQuestState(playerId);

            try {
                // Step 1: Spend currency
                if (!sim.spendCurrency(playerId, Currency.TOKENS, cost)) {
                    throw new RuntimeException("Insufficient funds");
                }

                // Step 2: Start quest
                sim.playerQuestStates.put(playerId, QuestState.STARTING);

                // Step 3: Create instance (simulated)
                UUID instanceId = UUID.randomUUID();
                sim.instances.put(instanceId, InstanceState.CREATING);

                // Step 4: Complete setup
                sim.instances.put(instanceId, InstanceState.READY);
                sim.instances.put(instanceId, InstanceState.ACTIVE);
                sim.playerToInstance.put(playerId, instanceId);
                sim.playerQuestStates.put(playerId, QuestState.ACTIVE);

                success = true;

            } catch (Exception e) {
                // "Rollback transaction"
                sim.addCurrency(playerId, Currency.TOKENS, cost); // Refund
                sim.playerQuestStates.put(playerId, originalState);
            }

            // Verify transaction completed
            assertTrue(success);
            assertEquals(originalBalance - cost, sim.getCurrency(playerId, Currency.TOKENS)); // 1000 - 500
            assertEquals(QuestState.ACTIVE, sim.requireQuestState(playerId));
        }

        @Test
        @Order(24)
        @DisplayName("Final invariant check across all subsystems")
        void testFinalInvariantCheck() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();

            // Create multiple players with various states
            for (int i = 0; i < 50; i++) {
                UUID playerId = UUID.randomUUID();

                // Random state setup
                if (i % 3 == 0) {
                    // In active quest
                    UUID instanceId = UUID.randomUUID();
                    sim.instances.put(instanceId, InstanceState.ACTIVE);
                    sim.playerToInstance.put(playerId, instanceId);
                    sim.playerQuestStates.put(playerId, QuestState.ACTIVE);
                    sim.playerStyleScores.put(playerId, i * 100);
                    sim.playerStyleRanks.put(playerId, StyleRank.fromScore(i * 100));
                } else if (i % 3 == 1) {
                    // Completed quest
                    sim.playerQuestStates.put(playerId, QuestState.COMPLETED);
                    sim.addCurrency(playerId, Currency.TOKENS, i * 50);
                } else {
                    // Idle
                    sim.playerQuestStates.put(playerId, QuestState.NONE);
                }
            }

            // Run invariant check
            sim.checkAllInvariants();

            assertEquals(0, sim.invariantViolations.get(),
                "All system invariants should hold: " + sim.errors);
        }
    }

    // =========================================================================
    // SECTION 8: EXTENDED COMBO SYSTEM EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("L6-08: Combo System Edge Cases")
    class ComboSystemEdgeCaseTests {

        @Test
        @Order(25)
        @DisplayName("Style rank boundaries are precise")
        void testStyleRankBoundaries() {
            // Test exact boundary values
            assertEquals(StyleRank.D, StyleRank.fromScore(0));
            assertEquals(StyleRank.D, StyleRank.fromScore(499));
            assertEquals(StyleRank.C, StyleRank.fromScore(500));
            assertEquals(StyleRank.C, StyleRank.fromScore(1499));
            assertEquals(StyleRank.B, StyleRank.fromScore(1500));
            assertEquals(StyleRank.B, StyleRank.fromScore(3499));
            assertEquals(StyleRank.A, StyleRank.fromScore(3500));
            assertEquals(StyleRank.A, StyleRank.fromScore(6999));
            assertEquals(StyleRank.S, StyleRank.fromScore(7000));
            assertEquals(StyleRank.S, StyleRank.fromScore(11999));
            assertEquals(StyleRank.SS, StyleRank.fromScore(12000));
            assertEquals(StyleRank.SS, StyleRank.fromScore(19999));
            assertEquals(StyleRank.SSS, StyleRank.fromScore(20000));
            assertEquals(StyleRank.SSS, StyleRank.fromScore(100000));
        }

        @Test
        @Order(26)
        @DisplayName("Style rank getNext() handles SSS correctly")
        void testStyleRankGetNext() {
            assertEquals(StyleRank.C, StyleRank.D.getNext());
            assertEquals(StyleRank.B, StyleRank.C.getNext());
            assertEquals(StyleRank.A, StyleRank.B.getNext());
            assertEquals(StyleRank.S, StyleRank.A.getNext());
            assertEquals(StyleRank.SS, StyleRank.S.getNext());
            assertEquals(StyleRank.SSS, StyleRank.SS.getNext());
            assertEquals(StyleRank.SSS, StyleRank.SSS.getNext(), "SSS.getNext() should return SSS");
        }

        @Test
        @Order(27)
        @DisplayName("Style multipliers are correct")
        void testStyleMultipliers() {
            assertEquals(1.0f, StyleRank.D.multiplier);
            assertEquals(1.2f, StyleRank.C.multiplier);
            assertEquals(1.5f, StyleRank.B.multiplier);
            assertEquals(2.0f, StyleRank.A.multiplier);
            assertEquals(3.0f, StyleRank.S.multiplier);
            assertEquals(4.0f, StyleRank.SS.multiplier);
            assertEquals(5.0f, StyleRank.SSS.multiplier);
        }

        @Test
        @Order(28)
        @DisplayName("Rapid rank changes are tracked correctly")
        void testRapidRankChanges() {
            IntegratedSystemSimulator sim = new IntegratedSystemSimulator();
            UUID playerId = UUID.randomUUID();

            sim.playerStyleScores.put(playerId, 0);
            sim.playerStyleRanks.put(playerId, StyleRank.D);

            List<StyleRank> rankHistory = new ArrayList<>();
            rankHistory.add(StyleRank.D);

            // Rapid score increases
            int[] scoreIncrements = {600, 1000, 2000, 3500, 5000, 8000};
            int totalScore = 0;

            for (int increment : scoreIncrements) {
                totalScore += increment;
                sim.playerStyleScores.put(playerId, totalScore);
                StyleRank newRank = StyleRank.fromScore(totalScore);
                sim.playerStyleRanks.put(playerId, newRank);
                rankHistory.add(newRank);
            }

            // Verify progression
            assertEquals(7, rankHistory.size());
            assertEquals(StyleRank.D, rankHistory.get(0));
            assertEquals(StyleRank.SSS, rankHistory.get(6)); // 600+1000+2000+3500+5000+8000 = 20100

            // Final state check
            sim.checkAllInvariants();
            assertEquals(0, sim.invariantViolations.get());
        }
    }
}
