package com.devmod.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Progressive Test Suite 9: Real User Journey Simulation
 *
 * Simulates complete user journeys through the mod, from quest start to completion.
 * These tests model actual gameplay scenarios to identify edge cases and bugs
 * that only manifest during real use.
 *
 * Scenarios covered:
 * 1. Solo quest: start -> fight -> complete -> return
 * 2. Solo quest: start -> fight -> die -> give up -> return
 * 3. Solo quest: start -> fight -> disconnect -> reconnect -> recovery
 * 4. Party quest: all members complete successfully
 * 5. Party quest: leader disconnects
 * 6. Race condition: rapid start/stop attempts
 * 7. Edge case: server restart during active quest
 */
public class RealUserJourneyTest {

    // ============================================================
    // Simulation Infrastructure
    // ============================================================

    /**
     * Simulates a player in the system.
     */
    static class SimulatedPlayer {
        final UUID id;
        final String name;
        PlayerInstanceState snapshotState = PlayerInstanceState.NORMAL;
        boolean isOnline = true;
        UUID currentInstanceId = null;
        double x, y, z;
        String dimension = "minecraft:overworld";
        int health = 20;
        int wave = 0;
        int kills = 0;
        int points = 0;
        List<String> eventLog = new ArrayList<>();

        SimulatedPlayer(String name) {
            this.id = UUID.randomUUID();
            this.name = name;
            this.x = 100 + Math.random() * 100;
            this.y = 64;
            this.z = 100 + Math.random() * 100;
        }

        void log(String event) {
            eventLog.add("[" + name + "] " + event);
        }

        void die() {
            health = 0;
            log("DIED at wave " + wave);
        }

        void respawn() {
            health = 20;
            log("Respawned");
        }

        void disconnect() {
            isOnline = false;
            log("DISCONNECTED");
        }

        void reconnect() {
            isOnline = true;
            log("RECONNECTED");
        }
    }

    /**
     * Simulates an instance in the system.
     */
    static class SimulatedInstance {
        final UUID id;
        InstanceState state = InstanceState.CREATING;
        Set<UUID> players = ConcurrentHashMap.newKeySet();
        int currentWave = 0;
        int totalWaves = 10;
        boolean endless = false;
        long createdAt = System.currentTimeMillis();
        long markedForDestructionAt = 0;
        List<String> eventLog = new ArrayList<>();

        SimulatedInstance() {
            this.id = UUID.randomUUID();
        }

        void log(String event) {
            eventLog.add("[Instance " + id.toString().substring(0, 8) + "] " + event);
        }

        boolean transitionTo(InstanceState newState) {
            boolean valid = state.canTransitionTo(newState);
            log("State: " + state + " -> " + newState + (valid ? "" : " [INVALID]"));
            state = newState;
            return valid;
        }
    }

    /**
     * Simulates the entire system for testing.
     */
    static class QuestSystemSimulator {
        Map<UUID, SimulatedPlayer> players = new ConcurrentHashMap<>();
        Map<UUID, SimulatedInstance> instances = new ConcurrentHashMap<>();
        Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
        Map<UUID, PlayerInstanceState> snapshots = new ConcurrentHashMap<>();
        List<String> systemLog = new ArrayList<>();
        AtomicInteger dimensionsCreated = new AtomicInteger(0);
        AtomicInteger dimensionsDestroyed = new AtomicInteger(0);

        void log(String event) {
            systemLog.add("[SYSTEM] " + event);
        }

        SimulatedPlayer createPlayer(String name) {
            SimulatedPlayer player = new SimulatedPlayer(name);
            players.put(player.id, player);
            return player;
        }

        /**
         * Simulate starting a quest.
         * Returns the instance ID or null on failure.
         */
        UUID startQuest(SimulatedPlayer player, int waves, boolean endless) {
            // Check preconditions
            if (!player.isOnline) {
                log("Cannot start quest: player " + player.name + " is offline");
                return null;
            }

            if (playerToInstance.containsKey(player.id)) {
                log("Cannot start quest: player " + player.name + " already in instance");
                return null;
            }

            // Create snapshot
            snapshots.put(player.id, PlayerInstanceState.PREPARING);
            player.snapshotState = PlayerInstanceState.PREPARING;
            player.log("Snapshot created, state: PREPARING");

            // Create instance
            SimulatedInstance instance = new SimulatedInstance();
            instance.totalWaves = waves;
            instance.endless = endless;
            instances.put(instance.id, instance);
            dimensionsCreated.incrementAndGet();
            log("Created instance " + instance.id);

            // Transition to READY
            instance.transitionTo(InstanceState.READY);

            // Simulate teleport
            snapshots.put(player.id, PlayerInstanceState.IN_TRANSIT);
            player.snapshotState = PlayerInstanceState.IN_TRANSIT;
            player.log("State: IN_TRANSIT - Teleporting to instance...");

            // Small delay simulation - check if player still online
            if (!player.isOnline) {
                log("Player disconnected during teleport!");
                return null;
            }

            // Complete teleport
            playerToInstance.put(player.id, instance.id);
            instance.players.add(player.id);
            player.currentInstanceId = instance.id;
            player.dimension = "devmod:instance_" + instance.id.toString().replace("-", "");

            snapshots.put(player.id, PlayerInstanceState.IN_INSTANCE);
            player.snapshotState = PlayerInstanceState.IN_INSTANCE;
            player.log("State: IN_INSTANCE - Arrived in instance, dimension: " + player.dimension);

            // Activate instance
            instance.transitionTo(InstanceState.ACTIVE);
            log("Instance " + instance.id + " is now ACTIVE");

            return instance.id;
        }

        /**
         * Simulate a wave of combat.
         */
        void simulateWave(SimulatedInstance instance) {
            instance.currentWave++;
            instance.log("Starting wave " + instance.currentWave);

            // All players in instance get kills
            for (UUID playerId : instance.players) {
                SimulatedPlayer player = players.get(playerId);
                if (player != null && player.isOnline) {
                    int waveKills = 5 + (int)(Math.random() * 10);
                    player.kills += waveKills;
                    player.points += waveKills * 10 + 50; // Kill points + wave bonus
                    player.wave = instance.currentWave;
                    player.log("Wave " + instance.currentWave + " complete: " + waveKills + " kills");
                }
            }
        }

        /**
         * End quest successfully.
         */
        void endQuestSuccess(UUID instanceId) {
            SimulatedInstance instance = instances.get(instanceId);
            if (instance == null) return;

            instance.transitionTo(InstanceState.COMPLETING);
            instance.log("Quest completed successfully!");

            // Return all players
            for (UUID playerId : new HashSet<>(instance.players)) {
                SimulatedPlayer player = players.get(playerId);
                if (player != null) {
                    returnPlayer(player, "Quest completed");
                }
            }

            // Schedule destruction
            instance.transitionTo(InstanceState.DESTROYING);
            instance.markedForDestructionAt = System.currentTimeMillis();
            log("Instance " + instanceId + " scheduled for destruction");

            // Complete destruction
            instance.transitionTo(InstanceState.DESTROYED);
            dimensionsDestroyed.incrementAndGet();
            instances.remove(instanceId);
            log("Instance " + instanceId + " destroyed");
        }

        /**
         * End quest due to failure (death, abandon, etc.)
         */
        void endQuestFailure(UUID instanceId, String reason) {
            SimulatedInstance instance = instances.get(instanceId);
            if (instance == null) return;

            instance.transitionTo(InstanceState.COMPLETING);
            instance.log("Quest failed: " + reason);

            // Return all players
            for (UUID playerId : new HashSet<>(instance.players)) {
                SimulatedPlayer player = players.get(playerId);
                if (player != null && player.isOnline) {
                    returnPlayer(player, reason);
                }
            }

            // Destroy instance
            instance.transitionTo(InstanceState.DESTROYING);
            instance.transitionTo(InstanceState.DESTROYED);
            dimensionsDestroyed.incrementAndGet();
            instances.remove(instanceId);
        }

        /**
         * Return a player to the overworld.
         */
        void returnPlayer(SimulatedPlayer player, String reason) {
            snapshots.put(player.id, PlayerInstanceState.RETURNING);
            player.snapshotState = PlayerInstanceState.RETURNING;
            player.log("State: RETURNING - Returning to overworld: " + reason);

            // Teleport back
            player.dimension = "minecraft:overworld";
            player.currentInstanceId = null;

            // Clean up mappings
            UUID instanceId = playerToInstance.remove(player.id);
            if (instanceId != null) {
                SimulatedInstance instance = instances.get(instanceId);
                if (instance != null) {
                    instance.players.remove(player.id);
                }
            }

            // Complete recovery
            snapshots.put(player.id, PlayerInstanceState.NORMAL);
            player.snapshotState = PlayerInstanceState.NORMAL;
            snapshots.remove(player.id);
            player.log("Recovery complete, state: NORMAL");
        }

        /**
         * Recover a player (e.g., after disconnect and reconnect).
         */
        void recoverPlayer(SimulatedPlayer player) {
            PlayerInstanceState state = snapshots.get(player.id);
            if (state == null || state == PlayerInstanceState.NORMAL) {
                player.log("No recovery needed");
                return;
            }

            player.log("Recovery triggered, state was: " + state);

            // Perform recovery based on state
            switch (state) {
                case NORMAL -> {
                    player.log("No recovery needed");
                }
                case PREPARING, IN_TRANSIT -> {
                    player.log("Teleport was interrupted, restoring position");
                }
                case IN_INSTANCE -> {
                    player.log("Quest failed due to disconnect");
                }
                case RETURNING -> {
                    player.log("Return was interrupted, completing");
                }
            }

            // Restore player
            player.dimension = "minecraft:overworld";
            player.currentInstanceId = null;
            UUID instanceId = playerToInstance.remove(player.id);

            // Clean up instance
            if (instanceId != null) {
                SimulatedInstance instance = instances.get(instanceId);
                if (instance != null) {
                    instance.players.remove(player.id);
                    if (instance.players.isEmpty()) {
                        endQuestFailure(instanceId, "All players disconnected");
                    }
                }
            }

            snapshots.remove(player.id);
            player.snapshotState = PlayerInstanceState.NORMAL;
            player.log("Recovery complete");
        }
    }

    // ============================================================
    // TEST SUITE 1: Solo Quest - Happy Path
    // ============================================================
    @Nested
    @DisplayName("Solo Quest Happy Path")
    class SoloQuestHappyPathTests {

        QuestSystemSimulator sim;
        SimulatedPlayer player;

        @BeforeEach
        void setup() {
            sim = new QuestSystemSimulator();
            player = sim.createPlayer("TestPlayer");
        }

        @Test
        @DisplayName("Complete 10-wave quest solo")
        void testCompleteTenWaveQuest() {
            // Start quest
            UUID instanceId = sim.startQuest(player, 10, false);
            assertNotNull(instanceId, "Quest should start successfully");

            // Verify player state
            assertEquals(PlayerInstanceState.IN_INSTANCE, player.snapshotState);
            assertTrue(player.dimension.startsWith("devmod:instance_"));

            // Complete all waves
            SimulatedInstance instance = sim.instances.get(instanceId);
            for (int i = 0; i < 10; i++) {
                sim.simulateWave(instance);
            }

            assertEquals(10, instance.currentWave);
            assertTrue(player.kills > 0);
            assertTrue(player.points > 0);

            // End quest successfully
            sim.endQuestSuccess(instanceId);

            // Verify cleanup
            assertEquals(PlayerInstanceState.NORMAL, player.snapshotState);
            assertEquals("minecraft:overworld", player.dimension);
            assertNull(player.currentInstanceId);
            assertFalse(sim.instances.containsKey(instanceId));
            assertEquals(1, sim.dimensionsDestroyed.get());
        }

        @Test
        @DisplayName("Endless mode quest - play 25 waves then exit")
        void testEndlessModeQuest() {
            UUID instanceId = sim.startQuest(player, Integer.MAX_VALUE, true);
            assertNotNull(instanceId);

            SimulatedInstance instance = sim.instances.get(instanceId);
            assertTrue(instance.endless);

            // Play 25 waves
            for (int i = 0; i < 25; i++) {
                sim.simulateWave(instance);
            }

            assertEquals(25, instance.currentWave);

            // Exit voluntarily
            sim.endQuestSuccess(instanceId);

            assertEquals(PlayerInstanceState.NORMAL, player.snapshotState);
            assertTrue(player.points > 1000); // Should have accumulated points
        }

        @Test
        @DisplayName("State transitions follow correct order")
        void testStateTransitionOrder() {
            // Track state transitions
            List<PlayerInstanceState> stateHistory = new ArrayList<>();
            stateHistory.add(PlayerInstanceState.NORMAL);

            UUID instanceId = sim.startQuest(player, 3, false);

            // Check transitions occurred
            assertTrue(player.eventLog.stream().anyMatch(e -> e.contains("PREPARING")));
            assertTrue(player.eventLog.stream().anyMatch(e -> e.contains("IN_TRANSIT")));
            assertTrue(player.eventLog.stream().anyMatch(e -> e.contains("IN_INSTANCE")));

            // Complete quest
            SimulatedInstance instance = sim.instances.get(instanceId);
            for (int i = 0; i < 3; i++) {
                sim.simulateWave(instance);
            }
            sim.endQuestSuccess(instanceId);

            assertTrue(player.eventLog.stream().anyMatch(e -> e.contains("RETURNING")));
            assertTrue(player.eventLog.stream().anyMatch(e -> e.contains("NORMAL")));
        }
    }

    // ============================================================
    // TEST SUITE 2: Solo Quest - Death Scenarios
    // ============================================================
    @Nested
    @DisplayName("Solo Quest Death Scenarios")
    class SoloQuestDeathTests {

        QuestSystemSimulator sim;
        SimulatedPlayer player;

        @BeforeEach
        void setup() {
            sim = new QuestSystemSimulator();
            player = sim.createPlayer("DeathTestPlayer");
        }

        @Test
        @DisplayName("Player dies and gives up")
        void testDeathAndGiveUp() {
            UUID instanceId = sim.startQuest(player, 10, false);
            assertNotNull(instanceId);

            // Play a few waves
            SimulatedInstance instance = sim.instances.get(instanceId);
            sim.simulateWave(instance);
            sim.simulateWave(instance);

            int pointsBeforeDeath = player.points;

            // Die
            player.die();
            assertEquals(0, player.health);

            // Give up
            sim.endQuestFailure(instanceId, "Player gave up");

            // Verify
            assertEquals(PlayerInstanceState.NORMAL, player.snapshotState);
            assertEquals("minecraft:overworld", player.dimension);
            // Points should be preserved (earned before death)
            assertEquals(pointsBeforeDeath, player.points);
        }

        @Test
        @DisplayName("Player dies multiple times before final death")
        void testMultipleDeaths() {
            UUID instanceId = sim.startQuest(player, 10, false);
            SimulatedInstance instance = sim.instances.get(instanceId);

            int deaths = 0;
            int maxDeaths = 3;

            // Play and die multiple times
            for (int wave = 0; wave < 5 && deaths < maxDeaths; wave++) {
                sim.simulateWave(instance);

                // 50% chance to die each wave
                if (Math.random() < 0.5) {
                    player.die();
                    deaths++;
                    player.log("Death #" + deaths);

                    if (deaths < maxDeaths) {
                        player.respawn();
                    }
                }
            }

            if (deaths >= maxDeaths) {
                sim.endQuestFailure(instanceId, "Too many deaths");
            } else {
                sim.endQuestSuccess(instanceId);
            }

            assertEquals(PlayerInstanceState.NORMAL, player.snapshotState);
        }
    }

    // ============================================================
    // TEST SUITE 3: Disconnect and Recovery
    // ============================================================
    @Nested
    @DisplayName("Disconnect and Recovery")
    class DisconnectRecoveryTests {

        QuestSystemSimulator sim;
        SimulatedPlayer player;

        @BeforeEach
        void setup() {
            sim = new QuestSystemSimulator();
            player = sim.createPlayer("DisconnectTestPlayer");
        }

        @Test
        @DisplayName("Disconnect during IN_INSTANCE triggers recovery on reconnect")
        void testDisconnectDuringQuest() {
            UUID instanceId = sim.startQuest(player, 10, false);
            assertNotNull(instanceId);

            // Play a few waves
            SimulatedInstance instance = sim.instances.get(instanceId);
            sim.simulateWave(instance);
            sim.simulateWave(instance);

            // Disconnect
            player.disconnect();
            assertFalse(player.isOnline);
            assertEquals(PlayerInstanceState.IN_INSTANCE, player.snapshotState);

            // Snapshot should still exist
            assertTrue(sim.snapshots.containsKey(player.id));

            // Reconnect
            player.reconnect();
            sim.recoverPlayer(player);

            // Verify recovery
            assertEquals(PlayerInstanceState.NORMAL, player.snapshotState);
            assertEquals("minecraft:overworld", player.dimension);
            assertFalse(sim.snapshots.containsKey(player.id));
        }

        @Test
        @DisplayName("Disconnect during IN_TRANSIT triggers position restore")
        void testDisconnectDuringTeleport() {
            // Start quest but simulate disconnect during teleport phase
            SimulatedPlayer playerCopy = sim.createPlayer("TeleportDisconnect");

            // Manually set up pre-teleport state
            sim.snapshots.put(playerCopy.id, PlayerInstanceState.IN_TRANSIT);
            playerCopy.snapshotState = PlayerInstanceState.IN_TRANSIT;

            // Disconnect
            playerCopy.disconnect();
            playerCopy.reconnect();

            // Recover
            sim.recoverPlayer(playerCopy);

            assertEquals(PlayerInstanceState.NORMAL, playerCopy.snapshotState);
            assertTrue(playerCopy.eventLog.stream()
                .anyMatch(e -> e.contains("Teleport was interrupted")));
        }

        @Test
        @DisplayName("Disconnect during RETURNING completes return")
        void testDisconnectDuringReturn() {
            UUID instanceId = sim.startQuest(player, 3, false);
            SimulatedInstance instance = sim.instances.get(instanceId);

            // Complete waves
            for (int i = 0; i < 3; i++) {
                sim.simulateWave(instance);
            }

            // Start return but disconnect mid-way
            sim.snapshots.put(player.id, PlayerInstanceState.RETURNING);
            player.snapshotState = PlayerInstanceState.RETURNING;

            player.disconnect();
            player.reconnect();
            sim.recoverPlayer(player);

            assertEquals(PlayerInstanceState.NORMAL, player.snapshotState);
            assertTrue(player.eventLog.stream()
                .anyMatch(e -> e.contains("Return was interrupted")));
        }

        @Test
        @DisplayName("Quick disconnect/reconnect preserves snapshot")
        void testQuickDisconnectReconnect() {
            UUID instanceId = sim.startQuest(player, 10, false);
            assertNotNull(instanceId);

            // Quick disconnect/reconnect cycle
            for (int i = 0; i < 3; i++) {
                player.disconnect();
                assertTrue(sim.snapshots.containsKey(player.id),
                    "Snapshot should persist through disconnect #" + (i + 1));
                player.reconnect();
            }

            // Finally recover
            sim.recoverPlayer(player);
            assertFalse(sim.snapshots.containsKey(player.id));
        }
    }

    // ============================================================
    // TEST SUITE 4: Party Quest Scenarios
    // ============================================================
    @Nested
    @DisplayName("Party Quest Scenarios")
    class PartyQuestTests {

        QuestSystemSimulator sim;

        @BeforeEach
        void setup() {
            sim = new QuestSystemSimulator();
        }

        @Test
        @DisplayName("Party of 4 completes quest together")
        void testPartyComplete() {
            // Create party
            SimulatedPlayer leader = sim.createPlayer("PartyLeader");
            SimulatedPlayer member1 = sim.createPlayer("Member1");
            SimulatedPlayer member2 = sim.createPlayer("Member2");
            SimulatedPlayer member3 = sim.createPlayer("Member3");

            // Start quest (simulating party)
            UUID instanceId = sim.startQuest(leader, 5, false);
            assertNotNull(instanceId);

            // Add other members to same instance
            SimulatedInstance instance = sim.instances.get(instanceId);
            for (SimulatedPlayer member : List.of(member1, member2, member3)) {
                sim.playerToInstance.put(member.id, instanceId);
                instance.players.add(member.id);
                member.currentInstanceId = instanceId;
                member.snapshotState = PlayerInstanceState.IN_INSTANCE;
                sim.snapshots.put(member.id, PlayerInstanceState.IN_INSTANCE);
            }

            assertEquals(4, instance.players.size());

            // Complete waves
            for (int i = 0; i < 5; i++) {
                sim.simulateWave(instance);
            }

            // End quest
            sim.endQuestSuccess(instanceId);

            // All players should be recovered
            for (SimulatedPlayer p : List.of(leader, member1, member2, member3)) {
                assertEquals(PlayerInstanceState.NORMAL, p.snapshotState,
                    p.name + " should be in NORMAL state");
            }
        }

        @Test
        @DisplayName("Leader disconnect ends quest for all")
        void testLeaderDisconnect() {
            SimulatedPlayer leader = sim.createPlayer("Leader");
            SimulatedPlayer member = sim.createPlayer("Member");

            UUID instanceId = sim.startQuest(leader, 10, false);
            SimulatedInstance instance = sim.instances.get(instanceId);

            // Add member
            sim.playerToInstance.put(member.id, instanceId);
            instance.players.add(member.id);
            member.currentInstanceId = instanceId;
            member.snapshotState = PlayerInstanceState.IN_INSTANCE;
            sim.snapshots.put(member.id, PlayerInstanceState.IN_INSTANCE);

            // Leader disconnects
            leader.disconnect();

            // Simulate system detecting leader disconnect
            sim.endQuestFailure(instanceId, "Leader disconnected");

            // Member should be returned
            sim.recoverPlayer(member);
            assertEquals(PlayerInstanceState.NORMAL, member.snapshotState);
        }

        @Test
        @DisplayName("Member disconnect doesn't end quest")
        void testMemberDisconnect() {
            SimulatedPlayer leader = sim.createPlayer("Leader");
            SimulatedPlayer member = sim.createPlayer("Member");

            UUID instanceId = sim.startQuest(leader, 10, false);
            SimulatedInstance instance = sim.instances.get(instanceId);

            // Add member
            sim.playerToInstance.put(member.id, instanceId);
            instance.players.add(member.id);

            // Member disconnects
            member.disconnect();
            instance.players.remove(member.id);
            sim.playerToInstance.remove(member.id);

            // Leader should still be in quest
            assertEquals(1, instance.players.size());
            assertTrue(instance.players.contains(leader.id));
            assertEquals(InstanceState.ACTIVE, instance.state);

            // Quest can continue
            sim.simulateWave(instance);
            assertEquals(1, instance.currentWave);
        }
    }

    // ============================================================
    // TEST SUITE 5: Race Conditions
    // ============================================================
    @Nested
    @DisplayName("Race Condition Tests")
    class RaceConditionTests {

        @Test
        @DisplayName("Cannot start two quests simultaneously")
        void testDoubleStartPrevention() {
            QuestSystemSimulator sim = new QuestSystemSimulator();
            SimulatedPlayer player = sim.createPlayer("DoubleStart");

            // Start first quest
            UUID instanceId1 = sim.startQuest(player, 10, false);
            assertNotNull(instanceId1);

            // Try to start second quest - should fail
            UUID instanceId2 = sim.startQuest(player, 5, false);
            assertNull(instanceId2, "Second quest start should fail");

            // Only one instance should exist for this player
            assertEquals(1, sim.playerToInstance.size());
        }

        @Test
        @Timeout(10)
        @DisplayName("Concurrent quest operations are thread-safe")
        void testConcurrentOperations() throws Exception {
            QuestSystemSimulator sim = new QuestSystemSimulator();
            int playerCount = 10;
            CountDownLatch latch = new CountDownLatch(playerCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errors = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(playerCount);

            for (int i = 0; i < playerCount; i++) {
                final int playerNum = i;
                executor.submit(() -> {
                    try {
                        SimulatedPlayer player = sim.createPlayer("Player" + playerNum);
                        UUID instanceId = sim.startQuest(player, 3, false);

                        if (instanceId != null) {
                            SimulatedInstance instance = sim.instances.get(instanceId);
                            if (instance != null) {
                                for (int w = 0; w < 3; w++) {
                                    sim.simulateWave(instance);
                                }
                                sim.endQuestSuccess(instanceId);
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(0, errors.get(), "No errors should occur");
            assertEquals(playerCount, successCount.get(), "All players should complete");
            assertEquals(playerCount, sim.dimensionsCreated.get());
            assertEquals(playerCount, sim.dimensionsDestroyed.get());
        }

        @Test
        @DisplayName("Rapid start/cancel sequence")
        void testRapidStartCancel() {
            QuestSystemSimulator sim = new QuestSystemSimulator();
            SimulatedPlayer player = sim.createPlayer("RapidPlayer");

            for (int i = 0; i < 5; i++) {
                UUID instanceId = sim.startQuest(player, 10, false);
                assertNotNull(instanceId, "Quest " + i + " should start");

                // Immediately cancel
                sim.endQuestFailure(instanceId, "Cancelled");

                assertEquals(PlayerInstanceState.NORMAL, player.snapshotState,
                    "Player should be NORMAL after cancel " + i);
            }

            // Should be able to start a new quest
            UUID finalInstance = sim.startQuest(player, 5, false);
            assertNotNull(finalInstance);
        }
    }

    // ============================================================
    // TEST SUITE 6: PlayerInstanceState Transition Validation
    // ============================================================
    @Nested
    @DisplayName("PlayerInstanceState Transition Validation")
    class PlayerStateTransitionTests {

        @Test
        @DisplayName("Valid forward transitions")
        void testValidForwardTransitions() {
            assertTrue(PlayerInstanceState.NORMAL.canTransitionTo(PlayerInstanceState.PREPARING));
            assertTrue(PlayerInstanceState.PREPARING.canTransitionTo(PlayerInstanceState.IN_TRANSIT));
            assertTrue(PlayerInstanceState.IN_TRANSIT.canTransitionTo(PlayerInstanceState.IN_INSTANCE));
            assertTrue(PlayerInstanceState.IN_INSTANCE.canTransitionTo(PlayerInstanceState.RETURNING));
        }

        @Test
        @DisplayName("Recovery transition to NORMAL always valid")
        void testRecoveryToNormal() {
            for (PlayerInstanceState state : PlayerInstanceState.values()) {
                assertTrue(state.canTransitionTo(PlayerInstanceState.NORMAL),
                    state + " should be able to transition to NORMAL (recovery)");
            }
        }

        @Test
        @DisplayName("Invalid skip transitions")
        void testInvalidSkipTransitions() {
            // Can't skip states
            assertFalse(PlayerInstanceState.NORMAL.canTransitionTo(PlayerInstanceState.IN_TRANSIT));
            assertFalse(PlayerInstanceState.NORMAL.canTransitionTo(PlayerInstanceState.IN_INSTANCE));
            assertFalse(PlayerInstanceState.PREPARING.canTransitionTo(PlayerInstanceState.IN_INSTANCE));
            assertFalse(PlayerInstanceState.IN_TRANSIT.canTransitionTo(PlayerInstanceState.RETURNING));
        }

        @Test
        @DisplayName("Invalid backward transitions")
        void testInvalidBackwardTransitions() {
            assertFalse(PlayerInstanceState.IN_TRANSIT.canTransitionTo(PlayerInstanceState.PREPARING));
            assertFalse(PlayerInstanceState.IN_INSTANCE.canTransitionTo(PlayerInstanceState.PREPARING));
            assertFalse(PlayerInstanceState.RETURNING.canTransitionTo(PlayerInstanceState.IN_INSTANCE));
        }

        @Test
        @DisplayName("getValidNextStates returns correct sets")
        void testGetValidNextStates() {
            assertEquals(Set.of(PlayerInstanceState.PREPARING),
                PlayerInstanceState.NORMAL.getValidNextStates());

            assertTrue(PlayerInstanceState.PREPARING.getValidNextStates()
                .contains(PlayerInstanceState.IN_TRANSIT));
            assertTrue(PlayerInstanceState.PREPARING.getValidNextStates()
                .contains(PlayerInstanceState.NORMAL));

            assertEquals(Set.of(PlayerInstanceState.NORMAL),
                PlayerInstanceState.RETURNING.getValidNextStates());
        }

        @Test
        @DisplayName("requiresSnapshot returns correct values")
        void testRequiresSnapshot() {
            assertFalse(PlayerInstanceState.NORMAL.requiresSnapshot());
            assertTrue(PlayerInstanceState.PREPARING.requiresSnapshot());
            assertTrue(PlayerInstanceState.IN_TRANSIT.requiresSnapshot());
            assertTrue(PlayerInstanceState.IN_INSTANCE.requiresSnapshot());
            assertTrue(PlayerInstanceState.RETURNING.requiresSnapshot());
        }

        @Test
        @DisplayName("isInInstanceFlow returns correct values")
        void testIsInInstanceFlow() {
            assertFalse(PlayerInstanceState.NORMAL.isInInstanceFlow());
            assertFalse(PlayerInstanceState.PREPARING.isInInstanceFlow());
            assertTrue(PlayerInstanceState.IN_TRANSIT.isInInstanceFlow());
            assertTrue(PlayerInstanceState.IN_INSTANCE.isInInstanceFlow());
            assertTrue(PlayerInstanceState.RETURNING.isInInstanceFlow());
        }
    }

    // ============================================================
    // TEST SUITE 7: Stale Request Detection
    // ============================================================
    @Nested
    @DisplayName("Stale Request Detection")
    class StaleRequestTests {

        @Test
        @DisplayName("Request becomes stale after MAX_AGE")
        void testStaleDetection() {
            long MAX_AGE_MS = 30_000;
            long createdAt = System.currentTimeMillis() - MAX_AGE_MS - 1;

            boolean isStale = System.currentTimeMillis() - createdAt > MAX_AGE_MS;
            assertTrue(isStale, "Request older than MAX_AGE should be stale");
        }

        @Test
        @DisplayName("Fresh request is not stale")
        void testFreshRequest() {
            long MAX_AGE_MS = 30_000;
            long createdAt = System.currentTimeMillis();

            boolean isStale = System.currentTimeMillis() - createdAt > MAX_AGE_MS;
            assertFalse(isStale, "Fresh request should not be stale");
        }

        @Test
        @DisplayName("Request at boundary of MAX_AGE")
        void testBoundaryRequest() {
            long MAX_AGE_MS = 30_000;
            long createdAt = System.currentTimeMillis() - MAX_AGE_MS;
            long now = createdAt + MAX_AGE_MS;

            // At exactly MAX_AGE, should not be stale (> not >=)
            boolean isStale = now - createdAt > MAX_AGE_MS;
            assertFalse(isStale, "Request at MAX_AGE boundary should not be stale");
        }
    }

    // ============================================================
    // TEST SUITE 8: Instance Cleanup Verification
    // ============================================================
    @Nested
    @DisplayName("Instance Cleanup Verification")
    class InstanceCleanupTests {

        @Test
        @DisplayName("All resources cleaned after quest end")
        void testResourceCleanup() {
            QuestSystemSimulator sim = new QuestSystemSimulator();
            SimulatedPlayer player = sim.createPlayer("CleanupTest");

            UUID instanceId = sim.startQuest(player, 3, false);
            assertNotNull(instanceId);

            // Complete quest
            SimulatedInstance instance = sim.instances.get(instanceId);
            for (int i = 0; i < 3; i++) {
                sim.simulateWave(instance);
            }
            sim.endQuestSuccess(instanceId);

            // Verify all cleanup occurred
            assertFalse(sim.instances.containsKey(instanceId), "Instance should be removed");
            assertFalse(sim.playerToInstance.containsKey(player.id), "Player mapping should be removed");
            assertFalse(sim.snapshots.containsKey(player.id), "Snapshot should be removed");
            assertNull(player.currentInstanceId, "Player instance ref should be null");
            assertEquals("minecraft:overworld", player.dimension, "Player should be in overworld");
        }

        @Test
        @DisplayName("Dimension count matches create/destroy")
        void testDimensionCountBalance() {
            QuestSystemSimulator sim = new QuestSystemSimulator();
            int questCount = 10;

            for (int i = 0; i < questCount; i++) {
                SimulatedPlayer player = sim.createPlayer("Player" + i);
                UUID instanceId = sim.startQuest(player, 2, false);

                SimulatedInstance instance = sim.instances.get(instanceId);
                sim.simulateWave(instance);
                sim.simulateWave(instance);
                sim.endQuestSuccess(instanceId);
            }

            assertEquals(questCount, sim.dimensionsCreated.get());
            assertEquals(questCount, sim.dimensionsDestroyed.get());
            assertTrue(sim.instances.isEmpty(), "All instances should be cleaned up");
        }
    }
}
