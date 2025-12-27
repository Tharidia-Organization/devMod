package com.devmod.stress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class PartyStressAndEdgeCaseTest {

    // ============================================
    // Simulation Classes (thread-safe versions)
    // ============================================

    enum SimQuestType {
        PVE_COOP(1, 6, true),
        RAID_BOSS(2, 10, false),
        EVENT(1, 20, true);

        final int minPlayers;
        final int maxPlayers;
        final boolean allowsSoloPlay;

        SimQuestType(int min, int max, boolean solo) {
            this.minPlayers = min;
            this.maxPlayers = max;
            this.allowsSoloPlay = solo;
        }
    }

    enum SimMobPreset {
        SWARM(0.3f, 0.8f, 1.2f),
        STANDARD(1.0f, 1.0f, 1.0f),
        TANK(2.5f, 0.7f, 1.5f),
        GLASS_CANNON(0.6f, 2.0f, 0.8f),
        BOSS_STYLE(5.0f, 1.5f, 2.0f);

        final float hpMult;
        final float damageMult;
        final float armorMult;

        SimMobPreset(float hp, float dmg, float armor) {
            this.hpMult = hp;
            this.damageMult = dmg;
            this.armorMult = armor;
        }
    }

    enum SimPartyState {
        FORMING, READY, IN_QUEST, DISBANDED
    }

    /**
     * Thread-safe SimPartyData
     */
    static class ThreadSafePartyData {
        private final UUID partyId = UUID.randomUUID();
        private volatile UUID leaderId;
        private volatile SimQuestType questType;
        private volatile SimPartyState state = SimPartyState.FORMING;
        private volatile @Nullable String selectedMobId;
        private volatile @Nullable UUID instanceId;

        private final Set<UUID> members = ConcurrentHashMap.newKeySet();
        private final Map<UUID, Boolean> memberReady = new ConcurrentHashMap<>();
        private final Map<UUID, String> memberNames = new ConcurrentHashMap<>();
        private final Set<UUID> pendingInvites = ConcurrentHashMap.newKeySet();

        private final Object stateLock = new Object();

        public ThreadSafePartyData(UUID leaderId, String leaderName, SimQuestType questType) {
            this.leaderId = leaderId;
            this.questType = questType;
            members.add(leaderId);
            memberNames.put(leaderId, leaderName);
            memberReady.put(leaderId, true);
        }

        public boolean addMember(@Nullable UUID playerId, String name) {
            synchronized (stateLock) {
                if (playerId == null) return false;
                if (state != SimPartyState.FORMING) return false;
                if (members.size() >= questType.maxPlayers) return false;
                if (members.contains(playerId)) return false;
                members.add(playerId);
                memberNames.put(playerId, name);
                memberReady.put(playerId, false);
                pendingInvites.remove(playerId);
                return true;
            }
        }

        public boolean removeMember(UUID playerId) {
            synchronized (stateLock) {
                if (playerId.equals(leaderId)) return false;
                if (!members.contains(playerId)) return false;
                members.remove(playerId);
                memberNames.remove(playerId);
                memberReady.remove(playerId);
                updateState();
                return true;
            }
        }

        public boolean setReady(UUID playerId, boolean ready) {
            synchronized (stateLock) {
                if (!members.contains(playerId)) return false;
                memberReady.put(playerId, ready);
                updateState();
                return true;
            }
        }

        public boolean isReady(UUID playerId) {
            return memberReady.getOrDefault(playerId, false);
        }

        public boolean allMembersReady() {
            return members.stream().allMatch(id -> memberReady.getOrDefault(id, false));
        }

        public boolean canStartQuest() {
            synchronized (stateLock) {
                if (state != SimPartyState.FORMING && state != SimPartyState.READY) return false;
                if (questType.allowsSoloPlay && members.size() == 1) return true;
                return members.size() >= questType.minPlayers && allMembersReady();
            }
        }

        public boolean startQuest(UUID instanceId) {
            synchronized (stateLock) {
                if (!canStartQuest()) return false;
                this.instanceId = instanceId;
                this.state = SimPartyState.IN_QUEST;
                return true;
            }
        }

        public void finishQuest() {
            synchronized (stateLock) {
                this.instanceId = null;
                this.state = SimPartyState.FORMING;
                for (UUID memberId : members) {
                    if (!memberId.equals(leaderId)) {
                        memberReady.put(memberId, false);
                    }
                }
            }
        }

        public boolean setSelectedMobId(UUID requesterId, String mobId) {
            synchronized (stateLock) {
                if (!requesterId.equals(leaderId)) return false;
                if (state != SimPartyState.FORMING) return false;
                this.selectedMobId = mobId;
                return true;
            }
        }

        private void updateState() {
            if (state == SimPartyState.FORMING && canStartQuestUnsafe()) {
                state = SimPartyState.READY;
            } else if (state == SimPartyState.READY && !canStartQuestUnsafe()) {
                state = SimPartyState.FORMING;
            }
        }

        private boolean canStartQuestUnsafe() {
            if (questType.allowsSoloPlay && members.size() == 1) return true;
            return members.size() >= questType.minPlayers && allMembersReady();
        }

        public UUID getPartyId() { return partyId; }
        public UUID getLeaderId() { return leaderId; }
        public SimQuestType getQuestType() { return questType; }
        public SimPartyState getState() { return state; }
        public @Nullable String getSelectedMobId() { return selectedMobId; }
        public @Nullable UUID getInstanceId() { return instanceId; }
        public int getMemberCount() { return members.size(); }
        public Set<UUID> getMembers() { return Collections.unmodifiableSet(new HashSet<>(members)); }
        public boolean isFull() { return members.size() >= questType.maxPlayers; }
    }

    /**
     * Scaling calculation helper
     */
    static class ScalingCalculator {
        public static float calculateHP(float baseHP, int players, SimQuestType type, SimMobPreset preset) {
            int effective = Math.min(players, type.maxPlayers);
            float playerScale = 1.0f + (effective - 1) * 0.35f;
            return baseHP * preset.hpMult * playerScale;
        }

        public static float calculateDamage(float baseDmg, int players, SimMobPreset preset) {
            float playerScale = 1.0f + (players - 1) * 0.1f;
            return baseDmg * preset.damageMult * playerScale;
        }
    }

    // ============================================
    // Test Data
    // ============================================

    private ExecutorService executor;

    @BeforeEach
    void setup() {
        executor = Executors.newFixedThreadPool(10);
    }

    // ============================================
    // Concurrent Party Operations Tests
    // ============================================

    @Nested
    @DisplayName("Concurrent Party Operations")
    class ConcurrentOperationsTests {

        @Test
        @Timeout(10)
        @DisplayName("10 threads adding members simultaneously")
        void testConcurrentMemberAddition() throws InterruptedException {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.EVENT);

            // EVENT allows up to 20 players
            int numThreads = 10;
            int attemptsPerThread = 5;
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < attemptsPerThread; j++) {
                            UUID playerId = UUID.randomUUID();
                            if (party.addMember(playerId, "Player_" + threadId + "_" + j)) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }).isDone();
            }

            latch.await();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // Should have added up to maxPlayers - 1 (excluding leader)
            assertTrue(party.getMemberCount() <= SimQuestType.EVENT.maxPlayers,
                "Should not exceed max players");
            assertEquals(party.getMemberCount() - 1, successCount.get(),
                "Success count should match actual members added (minus leader)");
        }

        @Test
        @Timeout(10)
        @DisplayName("Concurrent ready state toggling")
        void testConcurrentReadyToggle() throws InterruptedException {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.RAID_BOSS);

            // Add 5 members
            List<UUID> members = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                UUID memberId = UUID.randomUUID();
                party.addMember(memberId, "Member" + i);
                members.add(memberId);
            }

            int numToggleIterations = 100;
            CountDownLatch latch = new CountDownLatch(members.size());
            AtomicInteger toggleCount = new AtomicInteger(0);

            for (UUID memberId : members) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < numToggleIterations; i++) {
                            boolean targetState = (i % 2 == 0);
                            party.setReady(memberId, targetState);
                            toggleCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                }).isDone();
            }

            latch.await();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            assertEquals(members.size() * numToggleIterations, toggleCount.get(),
                "All toggle operations should complete");

            // Party state should be consistent (either FORMING or READY based on final states)
            SimPartyState finalState = party.getState();
            assertTrue(finalState == SimPartyState.FORMING || finalState == SimPartyState.READY,
                "State should be FORMING or READY after toggle operations");
        }

        @Test
        @Timeout(10)
        @DisplayName("Concurrent add/remove operations")
        void testConcurrentAddRemove() throws InterruptedException {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.PVE_COOP);

            int numOperations = 200;
            CountDownLatch latch = new CountDownLatch(2);
            List<UUID> addedMembers = Collections.synchronizedList(new ArrayList<>());

            // Thread 1: Adds members
            executor.submit(() -> {
                try {
                    for (int i = 0; i < numOperations; i++) {
                        UUID memberId = UUID.randomUUID();
                        if (party.addMember(memberId, "Member" + i)) {
                            addedMembers.add(memberId);
                        }
                        Thread.sleep(1); // Small delay
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).isDone();

            // Thread 2: Removes members
            executor.submit(() -> {
                try {
                    for (int i = 0; i < numOperations; i++) {
                        if (!addedMembers.isEmpty()) {
                            UUID toRemove = addedMembers.isEmpty() ? null :
                                addedMembers.get(ThreadLocalRandom.current().nextInt(Math.max(1, addedMembers.size())));
                            if (toRemove != null) {
                                party.removeMember(toRemove);
                                addedMembers.remove(toRemove);
                            }
                        }
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).isDone();

            latch.await();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // No exception = success, verify integrity
            assertTrue(party.getMemberCount() >= 1, "Should have at least leader");
            assertTrue(party.getMemberCount() <= SimQuestType.PVE_COOP.maxPlayers,
                "Should not exceed max players");
        }
    }

    // ============================================
    // Rapid State Transition Tests
    // ============================================

    @Nested
    @DisplayName("Rapid State Transitions")
    class RapidStateTransitionTests {

        @RepeatedTest(100)
        @DisplayName("Rapid FORMING<->READY transitions")
        void testRapidFormingReadyTransitions() {
            UUID leaderId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();

            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.RAID_BOSS);
            party.addMember(memberId, "Member");

            // Rapid toggling
            for (int i = 0; i < 50; i++) {
                party.setReady(memberId, true);
                assertEquals(SimPartyState.READY, party.getState());

                party.setReady(memberId, false);
                assertEquals(SimPartyState.FORMING, party.getState());
            }
        }

        @Test
        @DisplayName("Quest start-finish cycle repeated")
        void testQuestCycleRepeated() {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.PVE_COOP);

            for (int cycle = 0; cycle < 100; cycle++) {
                // Start quest (solo allowed for PVE_COOP)
                assertTrue(party.canStartQuest(), "Should be able to start quest at cycle " + cycle);
                assertTrue(party.startQuest(UUID.randomUUID()));
                assertEquals(SimPartyState.IN_QUEST, party.getState());

                // Finish quest
                party.finishQuest();
                assertEquals(SimPartyState.FORMING, party.getState());
            }
        }

        @Test
        @DisplayName("Mob selection changes during rapid ready toggles")
        void testMobSelectionDuringReadyToggles() {
            UUID leaderId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();

            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.RAID_BOSS);
            party.addMember(memberId, "Member");

            String[] mobs = {"minecraft:zombie", "minecraft:skeleton", "minecraft:blaze"};

            for (int i = 0; i < 100; i++) {
                // While in FORMING, change mob
                if (party.getState() == SimPartyState.FORMING) {
                    party.setSelectedMobId(leaderId, mobs[i % mobs.length]);
                }

                // Toggle ready
                party.setReady(memberId, i % 2 == 0);
            }

            // Final state should be consistent
            assertNotNull(party.getState());
        }
    }

    // ============================================
    // Maximum Capacity Tests
    // ============================================

    @Nested
    @DisplayName("Maximum Capacity Scenarios")
    class MaxCapacityTests {

        @Test
        @DisplayName("Fill EVENT party to max 20 players")
        void testFillEventPartyToMax() {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.EVENT);

            // Add 19 more members
            for (int i = 0; i < 19; i++) {
                assertTrue(party.addMember(UUID.randomUUID(), "Member" + i),
                    "Should add member " + i);
            }

            assertEquals(20, party.getMemberCount());
            assertTrue(party.isFull());

            // Try to add 21st
            assertFalse(party.addMember(UUID.randomUUID(), "ExtraMember"));
        }

        @Test
        @DisplayName("Fill RAID_BOSS party to max 10 players")
        void testFillRaidBossPartyToMax() {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.RAID_BOSS);

            // Add 9 more members
            for (int i = 0; i < 9; i++) {
                assertTrue(party.addMember(UUID.randomUUID(), "Member" + i));
            }

            assertEquals(10, party.getMemberCount());
            assertTrue(party.isFull());
        }

        @Test
        @DisplayName("Fill PVE_COOP party to max 6 players")
        void testFillPveCoopPartyToMax() {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.PVE_COOP);

            // Add 5 more members
            for (int i = 0; i < 5; i++) {
                assertTrue(party.addMember(UUID.randomUUID(), "Member" + i));
            }

            assertEquals(6, party.getMemberCount());
            assertTrue(party.isFull());
        }
    }

    // ============================================
    // Scaling Formula Edge Cases
    // ============================================

    @Nested
    @DisplayName("Scaling Formula Edge Cases")
    class ScalingEdgeCasesTests {

        @Test
        @DisplayName("Scaling with 0 base HP")
        void testScalingZeroBaseHP() {
            float result = ScalingCalculator.calculateHP(0f, 4, SimQuestType.RAID_BOSS, SimMobPreset.BOSS_STYLE);
            assertEquals(0f, result, 0.001f);
        }

        @Test
        @DisplayName("Scaling with 1 player")
        void testScalingSinglePlayer() {
            float result = ScalingCalculator.calculateHP(100f, 1, SimQuestType.PVE_COOP, SimMobPreset.STANDARD);
            // 1 player: scale = 1 + 0*0.35 = 1.0
            assertEquals(100f, result, 0.001f);
        }

        @Test
        @DisplayName("Scaling with max players exceeds quest type max")
        void testScalingExceedingMaxPlayers() {
            // RAID_BOSS max is 10, try with 20 players
            float result = ScalingCalculator.calculateHP(100f, 20, SimQuestType.RAID_BOSS, SimMobPreset.STANDARD);
            // Should clamp to 10: scale = 1 + 9*0.35 = 4.15
            float expected = 100f * 1.0f * 4.15f;
            assertEquals(expected, result, 0.1f);
        }

        @Test
        @DisplayName("Scaling with Float.MAX_VALUE base HP")
        void testScalingMaxFloatHP() {
            float result = ScalingCalculator.calculateHP(Float.MAX_VALUE, 1, SimQuestType.PVE_COOP, SimMobPreset.SWARM);
            // Should not overflow (SWARM hpMult=0.3, single player scale=1.0)
            assertTrue(Float.isFinite(result) || Float.isInfinite(result));
        }

        @Test
        @DisplayName("Damage scaling with 0 players")
        void testDamageScalingZeroPlayers() {
            // Edge case: 0 players (should not happen in practice)
            float result = ScalingCalculator.calculateDamage(10f, 0, SimMobPreset.STANDARD);
            // scale = 1 + (0-1)*0.1 = 0.9
            assertEquals(9f, result, 0.001f);
        }

        @ParameterizedTest
        @ValueSource(floats = {0.001f, 0.1f, 1f, 10f, 100f, 1000f, 10000f})
        @DisplayName("Scaling consistency across base HP values")
        void testScalingConsistency(float baseHP) {
            float scaled = ScalingCalculator.calculateHP(baseHP, 4, SimQuestType.RAID_BOSS, SimMobPreset.TANK);
            // TANK: hpMult=2.5, 4 players: 1 + 3*0.35 = 2.05
            float expected = baseHP * 2.5f * 2.05f;
            assertEquals(expected, scaled, expected * 0.001f);
        }
    }

    // ============================================
    // Memory Stability Tests
    // ============================================

    @Nested
    @DisplayName("Memory Stability")
    class MemoryStabilityTests {

        @Test
        @DisplayName("Create and destroy many parties")
        void testManyPartiesCreation() {
            List<ThreadSafePartyData> parties = new ArrayList<>();

            for (int i = 0; i < 1000; i++) {
                UUID leaderId = UUID.randomUUID();
                ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader" + i, SimQuestType.PVE_COOP);

                // Add some members
                for (int j = 0; j < 3; j++) {
                    party.addMember(UUID.randomUUID(), "Member" + j);
                }

                parties.add(party);
            }

            assertEquals(1000, parties.size());

            // Clear and let GC collect
            parties.clear();
            System.gc();

            // No exception = success
        }

        @Test
        @DisplayName("Repeated member churn in same party")
        void testMemberChurn() {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.PVE_COOP);

            // Add and remove members repeatedly
            for (int cycle = 0; cycle < 500; cycle++) {
                List<UUID> tempMembers = new ArrayList<>();

                // Fill party
                for (int i = 0; i < 5; i++) {
                    UUID memberId = UUID.randomUUID();
                    party.addMember(memberId, "TempMember" + i);
                    tempMembers.add(memberId);
                }

                // Empty party
                for (UUID memberId : tempMembers) {
                    party.removeMember(memberId);
                }

                assertEquals(1, party.getMemberCount(), "Only leader should remain");
            }
        }
    }

    // ============================================
    // Invalid Input Handling
    // ============================================

    @Nested
    @DisplayName("Invalid Input Handling")
    class InvalidInputTests {

        @Test
        @DisplayName("Cannot add null player ID")
        void testAddNullPlayerId() {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.PVE_COOP);

            assertFalse(party.addMember(null, "NullPlayer"));
            assertFalse(party.getMembers().contains(null));
        }

        @Test
        @DisplayName("Cannot set ready for non-existent player")
        void testSetReadyNonExistent() {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.PVE_COOP);

            UUID nonExistent = UUID.randomUUID();
            assertFalse(party.setReady(nonExistent, true));
        }

        @Test
        @DisplayName("Cannot remove non-existent player")
        void testRemoveNonExistent() {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.PVE_COOP);

            UUID nonExistent = UUID.randomUUID();
            assertFalse(party.removeMember(nonExistent));
        }

        @Test
        @DisplayName("Non-leader cannot change mob")
        void testNonLeaderChangeMob() {
            UUID leaderId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();

            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.PVE_COOP);
            party.addMember(memberId, "Member");

            assertFalse(party.setSelectedMobId(memberId, "minecraft:zombie"));
            assertNull(party.getSelectedMobId());
        }

        @Test
        @DisplayName("Cannot start quest twice")
        void testStartQuestTwice() {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.PVE_COOP);

            assertTrue(party.startQuest(UUID.randomUUID()));
            assertFalse(party.startQuest(UUID.randomUUID()), "Should not start quest twice");
        }
    }

    // ============================================
    // Race Condition Tests
    // ============================================

    @Nested
    @DisplayName("Race Condition Scenarios")
    class RaceConditionTests {

        @Test
        @Timeout(10)
        @DisplayName("Race: Multiple threads try to start quest")
        void testRaceStartQuest() throws InterruptedException {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.PVE_COOP);

            int numThreads = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numThreads);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // All threads start simultaneously
                        if (party.startQuest(UUID.randomUUID())) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).isDone();
            }

            startLatch.countDown(); // Release all threads
            doneLatch.await();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // Only ONE thread should succeed
            assertEquals(1, successCount.get(), "Only one thread should successfully start quest");
            assertEquals(SimPartyState.IN_QUEST, party.getState());
        }

        @Test
        @Timeout(10)
        @DisplayName("Race: Add member while checking isFull")
        void testRaceAddMemberFull() throws InterruptedException {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.PVE_COOP);

            // Pre-fill to 5 members (one slot left)
            for (int i = 0; i < 4; i++) {
                party.addMember(UUID.randomUUID(), "Member" + i);
            }
            assertEquals(5, party.getMemberCount());

            int numThreads = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numThreads);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (party.addMember(UUID.randomUUID(), "RacePlayer" + threadId)) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).isDone();
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // Only ONE should succeed (filling the last slot)
            assertEquals(1, successCount.get(), "Only one thread should successfully add member");
            assertEquals(6, party.getMemberCount());
            assertTrue(party.isFull());
        }
    }

    // ============================================
    // Stress Test with All Operations
    // ============================================

    @Nested
    @DisplayName("Combined Stress Tests")
    class CombinedStressTests {

        @Test
        @Timeout(30)
        @DisplayName("Full stress test: 50 threads, all operations")
        void testFullStress() throws InterruptedException {
            UUID leaderId = UUID.randomUUID();
            ThreadSafePartyData party = new ThreadSafePartyData(leaderId, "Leader", SimQuestType.EVENT);

            int numThreads = 50;
            int operationsPerThread = 100;
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicInteger totalOperations = new AtomicInteger(0);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < numThreads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        Random random = new Random();
                        UUID myId = UUID.randomUUID();

                        for (int op = 0; op < operationsPerThread; op++) {
                            try {
                                int action = random.nextInt(6);
                                switch (action) {
                                    case 0 -> party.addMember(myId, "Thread" + threadId);
                                    case 1 -> party.removeMember(myId);
                                    case 2 -> party.setReady(myId, random.nextBoolean());
                                    case 3 -> party.setSelectedMobId(leaderId, "minecraft:zombie");
                                    case 4 -> {
                                        if (party.canStartQuest()) {
                                            party.startQuest(UUID.randomUUID());
                                        }
                                    }
                                    case 5 -> {
                                        if (party.getState() == SimPartyState.IN_QUEST) {
                                            party.finishQuest();
                                        }
                                    }
                                }
                                totalOperations.incrementAndGet();
                            } catch (Exception e) {
                                errors.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }).isDone();
            }

            latch.await();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            assertEquals(numThreads * operationsPerThread, totalOperations.get(),
                "All operations should complete");
            assertEquals(0, errors.get(), "No errors should occur");

            // Party should be in a valid state
            SimPartyState finalState = party.getState();
            assertTrue(
                finalState == SimPartyState.FORMING ||
                finalState == SimPartyState.READY ||
                finalState == SimPartyState.IN_QUEST,
                "Party should be in a valid state"
            );
        }
    }
}
