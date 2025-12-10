package com.frenkvs.devmod.integration;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L6 - End-to-End Flow Testing Suite
 *
 * Simulates complete user journeys through all integrated systems,
 * from quest discovery to completion with full reward processing.
 *
 * Categories:
 * 1. Complete Solo Quest Journey
 * 2. Complete Party Quest Journey
 * 3. Multi-Session Progression
 * 4. Error Recovery Journeys
 * 5. Stress Test Journeys
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class L6EndToEndFlowTest {

    // =========================================================================
    // COMPREHENSIVE GAME SIMULATION
    // =========================================================================

    /**
     * Complete game state simulation for E2E testing
     */
    static class GameSimulation {
        // Player data
        final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();

        // Instance management
        final Map<UUID, InstanceData> instances = new ConcurrentHashMap<>();
        final Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();

        // Quest system
        final Map<UUID, QuestSession> activeSessions = new ConcurrentHashMap<>();

        // Economy
        final Map<UUID, WalletData> wallets = new ConcurrentHashMap<>();
        final Map<UUID, Object> purchaseLocks = new ConcurrentHashMap<>();

        // Telemetry
        final List<TelemetryEvent> eventLog = Collections.synchronizedList(new ArrayList<>());

        // Statistics
        final AtomicInteger totalQuestsStarted = new AtomicInteger(0);
        final AtomicInteger totalQuestsCompleted = new AtomicInteger(0);
        final AtomicInteger totalQuestsFailed = new AtomicInteger(0);
        final AtomicLong totalTokensEarned = new AtomicLong(0);
        final AtomicLong totalTokensSpent = new AtomicLong(0);

        void log(String event) {
            eventLog.add(new TelemetryEvent(System.currentTimeMillis(), event));
        }

        PlayerData createPlayer(String name) {
            PlayerData player = new PlayerData(UUID.randomUUID(), name);
            players.put(player.id, player);
            wallets.put(player.id, new WalletData());
            log("Player " + name + " joined");
            return player;
        }

        Object getPurchaseLock(UUID playerId) {
            return purchaseLocks.computeIfAbsent(playerId, id -> new Object());
        }
    }

    static class PlayerData {
        final UUID id;
        final String name;
        PlayerState state = PlayerState.NORMAL;
        String dimension = "minecraft:overworld";
        double x = 0, y = 64, z = 0;
        int health = 20;
        int maxHealth = 20;
        final Set<String> activePerks = ConcurrentHashMap.newKeySet();
        final Map<String, Integer> perkStacks = new ConcurrentHashMap<>();
        int totalQuestsCompleted = 0;
        int totalKills = 0;
        long totalPlayTime = 0;
        StyleRank highestRankEver = StyleRank.D;

        PlayerData(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static class InstanceData {
        final UUID id;
        InstanceState state = InstanceState.CREATING;
        final Set<UUID> players = ConcurrentHashMap.newKeySet();
        int currentWave = 0;
        int totalWaves = 10;
        boolean endless = false;
        long createdAt = System.currentTimeMillis();

        InstanceData(UUID id) {
            this.id = id;
        }
    }

    static class QuestSession {
        final UUID playerId;
        final UUID instanceId;
        QuestState state = QuestState.STARTING;
        int currentWave = 0;
        int kills = 0;
        int points = 0;
        int styleScore = 0;
        StyleRank currentRank = StyleRank.D;
        StyleRank peakRank = StyleRank.D;
        int currentCombo = 0;
        int maxCombo = 0;
        float damageDealt = 0;
        float damageTaken = 0;
        long startTime = System.currentTimeMillis();
        long endTime = 0;

        QuestSession(UUID playerId, UUID instanceId) {
            this.playerId = playerId;
            this.instanceId = instanceId;
        }

        long getDuration() {
            return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        }
    }

    static class WalletData {
        final AtomicLong tokens = new AtomicLong(0);
        final AtomicLong prestige = new AtomicLong(0);
        final AtomicLong bloodGems = new AtomicLong(0);

        void addTokens(long amount) {
            tokens.addAndGet(amount);
        }

        boolean spendTokens(long amount) {
            while (true) {
                long current = tokens.get();
                if (current < amount) return false;
                if (tokens.compareAndSet(current, current - amount)) return true;
            }
        }
    }

    record TelemetryEvent(long timestamp, String message) {}

    enum PlayerState {
        NORMAL, PREPARING, IN_TRANSIT, IN_INSTANCE, RETURNING
    }

    enum InstanceState {
        CREATING, READY, ACTIVE, COMPLETING, DESTROYING, DESTROYED
    }

    enum QuestState {
        STARTING, ACTIVE, WAVE_COMPLETE, BOSS_WAVE, COMPLETING, COMPLETED, FAILED, ABANDONED
    }

    enum StyleRank {
        D(0, 1.0f), C(500, 1.2f), B(1500, 1.5f), A(3500, 2.0f),
        S(7000, 3.0f), SS(12000, 4.0f), SSS(20000, 5.0f);

        final int threshold;
        final float multiplier;

        StyleRank(int threshold, float multiplier) {
            this.threshold = threshold;
            this.multiplier = multiplier;
        }

        static StyleRank fromScore(int score) {
            StyleRank result = D;
            for (StyleRank rank : values()) {
                if (score >= rank.threshold) result = rank;
            }
            return result;
        }
    }

    // =========================================================================
    // E2E FLOW HELPERS
    // =========================================================================

    /**
     * Complete quest start flow
     */
    static UUID startQuest(GameSimulation sim, PlayerData player, int waves, boolean endless) {
        // Check preconditions
        if (sim.playerToInstance.containsKey(player.id)) {
            sim.log("BLOCKED: " + player.name + " already in quest");
            return null;
        }

        sim.log(player.name + " starting " + (endless ? "endless" : waves + "-wave") + " quest");

        // Create snapshot (player state backup)
        player.state = PlayerState.PREPARING;

        // Create instance
        UUID instanceId = UUID.randomUUID();
        InstanceData instance = new InstanceData(instanceId);
        instance.totalWaves = waves;
        instance.endless = endless;
        sim.instances.put(instanceId, instance);

        // Transition instance to READY
        instance.state = InstanceState.READY;

        // Create quest session
        QuestSession session = new QuestSession(player.id, instanceId);
        sim.activeSessions.put(player.id, session);

        // Teleport player
        player.state = PlayerState.IN_TRANSIT;
        player.dimension = "devmod:instance_" + instanceId.toString().substring(0, 8);

        // Complete teleport
        sim.playerToInstance.put(player.id, instanceId);
        instance.players.add(player.id);
        player.state = PlayerState.IN_INSTANCE;

        // Activate instance
        instance.state = InstanceState.ACTIVE;
        session.state = QuestState.ACTIVE;

        sim.totalQuestsStarted.incrementAndGet();
        sim.log(player.name + " entered instance " + instanceId.toString().substring(0, 8));

        return instanceId;
    }

    /**
     * Simulate a wave of combat
     */
    static void simulateWave(GameSimulation sim, PlayerData player, QuestSession session, boolean isBoss) {
        InstanceData instance = sim.instances.get(session.instanceId);
        if (instance == null) return;

        instance.currentWave++;
        session.currentWave = instance.currentWave;
        session.state = isBoss ? QuestState.BOSS_WAVE : QuestState.ACTIVE;

        sim.log(player.name + " starting " + (isBoss ? "BOSS " : "") + "wave " + instance.currentWave);

        // Simulate kills
        int baseKills = isBoss ? 1 : 5 + (int)(Math.random() * 10);
        int killPoints = isBoss ? 500 : 10;

        for (int i = 0; i < baseKills; i++) {
            session.kills++;
            session.points += killPoints;
            session.currentCombo++;
            session.maxCombo = Math.max(session.maxCombo, session.currentCombo);

            // Style points per kill
            int styleGain = (int)(50 * (1 + session.currentCombo * 0.1));
            session.styleScore += styleGain;

            StyleRank newRank = StyleRank.fromScore(session.styleScore);
            if (newRank.ordinal() > session.currentRank.ordinal()) {
                session.currentRank = newRank;
                sim.log(player.name + " reached rank " + newRank.name());
            }
            session.peakRank = session.currentRank.ordinal() > session.peakRank.ordinal()
                ? session.currentRank : session.peakRank;

            session.damageDealt += 10 + Math.random() * 20;
        }

        // Simulate damage taken (maybe)
        if (Math.random() < 0.3) {
            float damage = 2 + (float)(Math.random() * 5);
            session.damageTaken += damage;
            player.health -= (int) damage;
            if (player.health < 0) player.health = 0;

            // Combo break on damage
            session.currentCombo = 0;
        }

        // Wave complete bonus
        session.points += 50 + (instance.currentWave * 10);

        session.state = QuestState.WAVE_COMPLETE;
        sim.log(player.name + " completed wave " + instance.currentWave +
            " (kills: " + session.kills + ", combo: " + session.maxCombo + ")");
    }

    /**
     * Select a perk at checkpoint
     */
    static void selectPerk(GameSimulation sim, PlayerData player, String perkName) {
        player.activePerks.add(perkName);
        player.perkStacks.merge(perkName, 1, (prev, inc) -> (prev == null ? 0 : prev) + (inc == null ? 0 : inc));
        sim.log(player.name + " selected perk: " + perkName +
            " (stack: " + player.perkStacks.get(perkName) + ")");
    }

    /**
     * Complete quest successfully
     */
    static QuestRewards completeQuest(GameSimulation sim, PlayerData player, QuestSession session) {
        session.state = QuestState.COMPLETING;
        session.endTime = System.currentTimeMillis();

        InstanceData instance = sim.instances.get(session.instanceId);
        if (instance != null) {
            instance.state = InstanceState.COMPLETING;
        }

        // Calculate rewards
        QuestRewards rewards = new QuestRewards();
        rewards.baseTokens = session.points;
        rewards.styleMultiplier = session.peakRank.multiplier;
        rewards.waveBonus = session.currentWave * 50;
        rewards.noHitBonus = session.damageTaken < 1.0f ? 1.5f : 1.0f;
        rewards.speedBonus = session.getDuration() < 60000 * session.currentWave ? 1.2f : 1.0f;

        rewards.totalTokens = (int)(
            (rewards.baseTokens + rewards.waveBonus) *
            rewards.styleMultiplier *
            rewards.noHitBonus *
            rewards.speedBonus
        );

        // Award tokens
        WalletData wallet = sim.wallets.get(player.id);
        wallet.addTokens(rewards.totalTokens);
        sim.totalTokensEarned.addAndGet(rewards.totalTokens);

        // Prestige for completing all waves
        if (instance != null && !instance.endless && session.currentWave >= instance.totalWaves) {
            int prestige = instance.totalWaves / 5;
            wallet.prestige.addAndGet(prestige);
            rewards.prestigeEarned = prestige;
        }

        // Update player stats
        player.totalQuestsCompleted++;
        player.totalKills += session.kills;
        player.totalPlayTime += session.getDuration();
        if (session.peakRank.ordinal() > player.highestRankEver.ordinal()) {
            player.highestRankEver = session.peakRank;
        }

        // Cleanup
        returnPlayer(sim, player, session, "Quest completed");
        session.state = QuestState.COMPLETED;
        sim.totalQuestsCompleted.incrementAndGet();

        sim.log(player.name + " completed quest: " + rewards.totalTokens + " tokens earned");
        return rewards;
    }

    /**
     * Fail quest (death, abandon, etc.)
     */
    static void failQuest(GameSimulation sim, PlayerData player, QuestSession session, String reason) {
        session.state = QuestState.FAILED;
        session.endTime = System.currentTimeMillis();

        // Partial rewards (50% of earned)
        int partialTokens = session.points / 2;
        WalletData wallet = sim.wallets.get(player.id);
        wallet.addTokens(partialTokens);
        sim.totalTokensEarned.addAndGet(partialTokens);

        // Update stats
        player.totalKills += session.kills;
        player.totalPlayTime += session.getDuration();

        returnPlayer(sim, player, session, reason);
        sim.totalQuestsFailed.incrementAndGet();

        sim.log(player.name + " failed quest: " + reason + " (partial: " + partialTokens + " tokens)");
    }

    /**
     * Return player to overworld
     */
    static void returnPlayer(GameSimulation sim, PlayerData player, QuestSession session, String reason) {
        player.state = PlayerState.RETURNING;

        // Clear perks
        player.activePerks.clear();
        player.perkStacks.clear();

        // Remove from instance
        UUID instanceId = sim.playerToInstance.remove(player.id);
        if (instanceId != null) {
            InstanceData instance = sim.instances.get(instanceId);
            if (instance != null) {
                instance.players.remove(player.id);

                // If instance empty, destroy it
                if (instance.players.isEmpty()) {
                    instance.state = InstanceState.DESTROYING;
                    instance.state = InstanceState.DESTROYED;
                    sim.instances.remove(instanceId);
                    sim.log("Instance " + instanceId.toString().substring(0, 8) + " destroyed");
                }
            }
        }

        // Restore player
        player.dimension = "minecraft:overworld";
        player.health = player.maxHealth;
        player.state = PlayerState.NORMAL;

        // Remove session
        sim.activeSessions.remove(player.id);
    }

    static class QuestRewards {
        int baseTokens;
        int waveBonus;
        float styleMultiplier = 1.0f;
        float noHitBonus = 1.0f;
        float speedBonus = 1.0f;
        int totalTokens;
        int prestigeEarned;
    }

    // =========================================================================
    // SECTION 1: COMPLETE SOLO QUEST JOURNEY
    // =========================================================================

    @Nested
    @DisplayName("L6-E2E-01: Complete Solo Quest Journey")
    class SoloQuestJourneyTests {

        @Test
        @Order(1)
        @DisplayName("Full 10-wave quest with all milestones")
        void testComplete10WaveQuest() {
            GameSimulation sim = new GameSimulation();
            PlayerData player = sim.createPlayer("SoloPlayer");

            // Start quest
            UUID instanceId = startQuest(sim, player, 10, false);
            assertNotNull(instanceId);

            QuestSession session = sim.activeSessions.get(player.id);
            assertNotNull(session);

            // Wave 1-4
            for (int i = 0; i < 4; i++) {
                simulateWave(sim, player, session, false);
            }
            assertEquals(4, session.currentWave);

            // Wave 5 - checkpoint, select perk
            simulateWave(sim, player, session, false);
            selectPerk(sim, player, "DAMAGE_BOOST");
            assertEquals(1, player.perkStacks.get("DAMAGE_BOOST"));

            // Wave 6-9
            for (int i = 0; i < 4; i++) {
                simulateWave(sim, player, session, false);
            }

            // Wave 10 - boss wave, select another perk
            simulateWave(sim, player, session, true);
            selectPerk(sim, player, "DAMAGE_BOOST"); // Stack it
            assertEquals(2, player.perkStacks.get("DAMAGE_BOOST"));

            // Complete
            QuestRewards rewards = completeQuest(sim, player, session);

            // Verify final state
            assertEquals(PlayerState.NORMAL, player.state);
            assertEquals("minecraft:overworld", player.dimension);
            assertTrue(player.activePerks.isEmpty());
            assertTrue(player.perkStacks.isEmpty());
            assertEquals(1, player.totalQuestsCompleted);
            assertTrue(rewards.totalTokens > 0);
            assertEquals(QuestState.COMPLETED, session.state);
            assertEquals(1, sim.totalQuestsCompleted.get());

            // Instance should be destroyed
            assertFalse(sim.instances.containsKey(instanceId));
        }

        @Test
        @Order(2)
        @DisplayName("Endless mode reaching wave 25")
        void testEndlessModeQuest() {
            GameSimulation sim = new GameSimulation();
            PlayerData player = sim.createPlayer("EndlessPlayer");

            UUID instanceId = startQuest(sim, player, Integer.MAX_VALUE, true);
            assertNotNull(instanceId);

            QuestSession session = sim.activeSessions.get(player.id);
            InstanceData instance = sim.instances.get(instanceId);
            assertTrue(instance.endless);

            // Play 25 waves with bosses every 10
            for (int wave = 1; wave <= 25; wave++) {
                boolean isBoss = (wave % 10 == 0);
                simulateWave(sim, player, session, isBoss);

                // Perk at checkpoints (every 5 waves)
                if (wave % 5 == 0) {
                    selectPerk(sim, player, "PERK_" + wave);
                }
            }

            assertEquals(25, session.currentWave);
            assertEquals(5, player.activePerks.size()); // 5 perks selected

            // Exit
            QuestRewards rewards = completeQuest(sim, player, session);
            assertTrue(rewards.totalTokens > 1000);
        }

        @Test
        @Order(3)
        @DisplayName("Death and give up flow")
        void testDeathGiveUpFlow() {
            GameSimulation sim = new GameSimulation();
            PlayerData player = sim.createPlayer("DeathPlayer");

            startQuest(sim, player, 10, false);
            QuestSession session = sim.activeSessions.get(player.id);

            // Play 3 waves
            for (int i = 0; i < 3; i++) {
                simulateWave(sim, player, session, false);
            }

            // Die
            player.health = 0;

            // Give up
            failQuest(sim, player, session, "Player gave up after death");

            assertEquals(PlayerState.NORMAL, player.state);
            assertEquals(QuestState.FAILED, session.state);
            assertEquals(1, sim.totalQuestsFailed.get());

            // Should have partial rewards
            assertTrue(sim.wallets.get(player.id).tokens.get() > 0);
        }

        @Test
        @Order(4)
        @DisplayName("SSS rank achievement during quest")
        void testSSSRankAchievement() {
            GameSimulation sim = new GameSimulation();
            PlayerData player = sim.createPlayer("StyleMaster");

            startQuest(sim, player, 20, false);
            QuestSession session = sim.activeSessions.get(player.id);

            // Play many waves to build up style
            for (int wave = 1; wave <= 20; wave++) {
                simulateWave(sim, player, session, wave % 10 == 0);

                // Boost style artificially for test
                session.styleScore += 2000;
                session.currentRank = StyleRank.fromScore(session.styleScore);
                session.peakRank = session.currentRank.ordinal() > session.peakRank.ordinal()
                    ? session.currentRank : session.peakRank;
            }

            assertEquals(StyleRank.SSS, session.peakRank);

            QuestRewards rewards = completeQuest(sim, player, session);

            // SSS multiplier is 5.0f
            assertTrue(rewards.styleMultiplier >= 5.0f);
            assertEquals(StyleRank.SSS, player.highestRankEver);
        }
    }

    // =========================================================================
    // SECTION 2: COMPLETE PARTY QUEST JOURNEY
    // =========================================================================

    @Nested
    @DisplayName("L6-E2E-02: Complete Party Quest Journey")
    class PartyQuestJourneyTests {

        @Test
        @Order(5)
        @DisplayName("4-player party completes quest")
        void testFullPartyCompletion() {
            GameSimulation sim = new GameSimulation();

            PlayerData leader = sim.createPlayer("Leader");
            PlayerData member1 = sim.createPlayer("Member1");
            PlayerData member2 = sim.createPlayer("Member2");
            PlayerData member3 = sim.createPlayer("Member3");

            // Leader starts quest
            UUID instanceId = startQuest(sim, leader, 5, false);
            assertNotNull(instanceId);

            // Add members to same instance
            InstanceData instance = sim.instances.get(instanceId);

            for (PlayerData member : List.of(member1, member2, member3)) {
                // Create session for member
                QuestSession memberSession = new QuestSession(member.id, instanceId);
                sim.activeSessions.put(member.id, memberSession);
                sim.playerToInstance.put(member.id, instanceId);
                instance.players.add(member.id);
                member.state = PlayerState.IN_INSTANCE;
                member.dimension = leader.dimension;
            }

            assertEquals(4, instance.players.size());

            // All players complete waves together
            for (int wave = 1; wave <= 5; wave++) {
                for (PlayerData p : List.of(leader, member1, member2, member3)) {
                    QuestSession s = sim.activeSessions.get(p.id);
                    simulateWave(sim, p, s, wave == 5);
                }
            }

            // Complete for all
            List<QuestRewards> allRewards = new ArrayList<>();
            for (PlayerData p : List.of(leader, member1, member2, member3)) {
                QuestSession s = sim.activeSessions.get(p.id);
                allRewards.add(completeQuest(sim, p, s));
            }

            // All should have earned tokens
            for (QuestRewards r : allRewards) {
                assertTrue(r.totalTokens > 0);
            }

            // Instance should be destroyed
            assertFalse(sim.instances.containsKey(instanceId));
            assertEquals(4, sim.totalQuestsCompleted.get());
        }

        @Test
        @Order(6)
        @DisplayName("Member disconnect doesn't end quest for others")
        void testMemberDisconnect() {
            GameSimulation sim = new GameSimulation();

            PlayerData leader = sim.createPlayer("Leader");
            PlayerData member = sim.createPlayer("Member");

            UUID instanceId = startQuest(sim, leader, 10, false);
            InstanceData instance = sim.instances.get(instanceId);

            // Add member
            QuestSession memberSession = new QuestSession(member.id, instanceId);
            sim.activeSessions.put(member.id, memberSession);
            sim.playerToInstance.put(member.id, instanceId);
            instance.players.add(member.id);
            member.state = PlayerState.IN_INSTANCE;

            // Play 2 waves
            for (int i = 0; i < 2; i++) {
                simulateWave(sim, leader, sim.activeSessions.get(leader.id), false);
                simulateWave(sim, member, memberSession, false);
            }

            // Member disconnects (simulate by failing their quest)
            failQuest(sim, member, memberSession, "Disconnected");

            // Leader should still be in quest
            assertTrue(sim.activeSessions.containsKey(leader.id));
            assertTrue(instance.players.contains(leader.id));
            assertEquals(InstanceState.ACTIVE, instance.state);

            // Leader continues and completes
            for (int i = 0; i < 8; i++) {
                simulateWave(sim, leader, sim.activeSessions.get(leader.id), i == 7);
            }

            completeQuest(sim, leader, sim.activeSessions.get(leader.id));

            assertEquals(1, sim.totalQuestsCompleted.get());
            assertEquals(1, sim.totalQuestsFailed.get());
        }
    }

    // =========================================================================
    // SECTION 3: MULTI-SESSION PROGRESSION
    // =========================================================================

    @Nested
    @DisplayName("L6-E2E-03: Multi-Session Progression")
    class MultiSessionProgressionTests {

        @Test
        @Order(7)
        @DisplayName("Player progression across multiple quests")
        void testMultiQuestProgression() {
            GameSimulation sim = new GameSimulation();
            PlayerData player = sim.createPlayer("ProgressPlayer");

            int questCount = 5;
            List<Long> tokenHistory = new ArrayList<>();

            for (int q = 0; q < questCount; q++) {
                startQuest(sim, player, 5, false);
                QuestSession session = sim.activeSessions.get(player.id);

                for (int wave = 1; wave <= 5; wave++) {
                    simulateWave(sim, player, session, wave == 5);
                }

                QuestRewards rewards = completeQuest(sim, player, session);
                tokenHistory.add((long) rewards.totalTokens);
            }

            // Verify progression
            assertEquals(questCount, player.totalQuestsCompleted);
            assertTrue(player.totalKills > 0, "Player should have kills");
            // Note: totalPlayTime may be 0 in fast tests since start/end times are nearly identical
            assertTrue(player.totalPlayTime >= 0, "Play time should not be negative");

            // Total tokens should be sum
            long totalTokens = tokenHistory.stream().mapToLong(Long::longValue).sum();
            assertEquals(totalTokens, sim.wallets.get(player.id).tokens.get());

            sim.log("Progression complete: " + questCount + " quests, " +
                player.totalKills + " kills, " + totalTokens + " tokens");
        }

        @Test
        @Order(8)
        @DisplayName("Shop purchases between quests")
        void testShopPurchasesBetweenQuests() {
            GameSimulation sim = new GameSimulation();
            PlayerData player = sim.createPlayer("Shopper");

            // First quest to earn tokens
            UUID instance1 = startQuest(sim, player, 5, false);
            assertNotNull(instance1);
            QuestSession session1 = sim.activeSessions.get(player.id);
            for (int i = 0; i < 5; i++) {
                simulateWave(sim, player, session1, i == 4);
            }
            completeQuest(sim, player, session1);

            long balanceAfterQuest = sim.wallets.get(player.id).tokens.get();
            assertTrue(balanceAfterQuest > 0);

            // Make a purchase
            long itemCost = balanceAfterQuest / 2;
            synchronized (sim.getPurchaseLock(player.id)) {
                boolean purchased = sim.wallets.get(player.id).spendTokens(itemCost);
                assertTrue(purchased);
                sim.totalTokensSpent.addAndGet(itemCost);
            }

            long balanceAfterPurchase = sim.wallets.get(player.id).tokens.get();
            assertEquals(balanceAfterQuest - itemCost, balanceAfterPurchase);

            // Second quest
            UUID instance2 = startQuest(sim, player, 5, false);
            assertNotNull(instance2);
            QuestSession session2 = sim.activeSessions.get(player.id);
            for (int i = 0; i < 5; i++) {
                simulateWave(sim, player, session2, i == 4);
            }
            completeQuest(sim, player, session2);

            // Balance should have increased
            assertTrue(sim.wallets.get(player.id).tokens.get() > balanceAfterPurchase);
        }
    }

    // =========================================================================
    // SECTION 4: ERROR RECOVERY JOURNEYS
    // =========================================================================

    @Nested
    @DisplayName("L6-E2E-04: Error Recovery Journeys")
    class ErrorRecoveryJourneyTests {

        @Test
        @Order(9)
        @DisplayName("Recovery after disconnect during quest")
        void testDisconnectRecovery() {
            GameSimulation sim = new GameSimulation();
            PlayerData player = sim.createPlayer("DisconnectPlayer");

            startQuest(sim, player, 10, false);
            QuestSession session = sim.activeSessions.get(player.id);

            // Play some waves
            for (int i = 0; i < 5; i++) {
                simulateWave(sim, player, session, false);
            }

            int killsBeforeDisconnect = session.kills;

            // Simulate disconnect (fail quest)
            failQuest(sim, player, session, "Disconnected");

            // Player should be recovered
            assertEquals(PlayerState.NORMAL, player.state);
            assertEquals("minecraft:overworld", player.dimension);

            // Partial rewards should be given
            assertTrue(sim.wallets.get(player.id).tokens.get() > 0);

            // Stats should include kills before disconnect
            assertEquals(killsBeforeDisconnect, player.totalKills);

            // Can start new quest
            UUID newInstance = startQuest(sim, player, 5, false);
            assertNotNull(newInstance);
        }

        @Test
        @Order(10)
        @DisplayName("Multiple consecutive failures then success")
        void testFailureThenSuccess() {
            GameSimulation sim = new GameSimulation();
            PlayerData player = sim.createPlayer("PersistentPlayer");

            // Fail 3 times
            for (int attempt = 1; attempt <= 3; attempt++) {
                startQuest(sim, player, 10, false);
                QuestSession session = sim.activeSessions.get(player.id);

                // Get partway through
                for (int i = 0; i < attempt + 1; i++) {
                    simulateWave(sim, player, session, false);
                }

                failQuest(sim, player, session, "Death attempt " + attempt);
            }

            assertEquals(3, sim.totalQuestsFailed.get());
            assertEquals(0, sim.totalQuestsCompleted.get());

            // Finally succeed
            startQuest(sim, player, 10, false);
            QuestSession session = sim.activeSessions.get(player.id);
            for (int i = 0; i < 10; i++) {
                simulateWave(sim, player, session, i == 9);
            }
            completeQuest(sim, player, session);

            assertEquals(1, sim.totalQuestsCompleted.get());
            assertEquals(1, player.totalQuestsCompleted);
        }
    }

    // =========================================================================
    // SECTION 5: STRESS TEST JOURNEYS
    // =========================================================================

    @Nested
    @DisplayName("L6-E2E-05: Stress Test Journeys")
    class StressTestJourneyTests {

        @Test
        @Order(11)
        @Timeout(60)
        @DisplayName("20 concurrent players completing quests")
        void testConcurrentPlayers() throws Exception {
            GameSimulation sim = new GameSimulation();
            int playerCount = 20;
            CountDownLatch latch = new CountDownLatch(playerCount);
            AtomicInteger errors = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(playerCount);

            for (int i = 0; i < playerCount; i++) {
                final int playerNum = i;
                executor.submit(() -> {
                    try {
                        PlayerData player = sim.createPlayer("Player" + playerNum);

                        UUID instanceId = startQuest(sim, player, 5, false);
                        if (instanceId == null) {
                            errors.incrementAndGet();
                            return;
                        }

                        QuestSession session = sim.activeSessions.get(player.id);
                        for (int wave = 0; wave < 5; wave++) {
                            simulateWave(sim, player, session, wave == 4);
                        }

                        completeQuest(sim, player, session);

                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(0, errors.get(), "No errors during concurrent execution");
            assertEquals(playerCount, sim.totalQuestsCompleted.get());
            assertTrue(sim.instances.isEmpty(), "All instances should be cleaned up");
        }

        @Test
        @Order(12)
        @Timeout(60)
        @DisplayName("Rapid quest start/complete cycles")
        void testRapidQuestCycles() throws Exception {
            GameSimulation sim = new GameSimulation();
            PlayerData player = sim.createPlayer("RapidPlayer");

            int cycles = 50;

            for (int i = 0; i < cycles; i++) {
                UUID instanceId = startQuest(sim, player, 2, false);
                assertNotNull(instanceId, "Cycle " + i + " failed to start");

                QuestSession session = sim.activeSessions.get(player.id);
                simulateWave(sim, player, session, false);
                simulateWave(sim, player, session, true);

                completeQuest(sim, player, session);

                // Brief pause between cycles
                Thread.sleep(10);
            }

            assertEquals(cycles, sim.totalQuestsCompleted.get());
            assertEquals(cycles, player.totalQuestsCompleted);
            assertTrue(sim.instances.isEmpty());
            assertFalse(sim.activeSessions.containsKey(player.id));
        }

        @Test
        @Order(13)
        @DisplayName("Token economy consistency under load")
        void testTokenEconomyConsistency() throws Exception {
            GameSimulation sim = new GameSimulation();
            int playerCount = 10;
            int questsPerPlayer = 5;

            CountDownLatch latch = new CountDownLatch(playerCount);
            ExecutorService executor = Executors.newFixedThreadPool(playerCount);

            for (int i = 0; i < playerCount; i++) {
                final int playerNum = i;
                executor.submit(() -> {
                    try {
                        PlayerData player = sim.createPlayer("EconPlayer" + playerNum);

                        for (int q = 0; q < questsPerPlayer; q++) {
                            UUID instanceId = startQuest(sim, player, 3, false);
                            if (instanceId == null) continue;

                            QuestSession session = sim.activeSessions.get(player.id);
                            for (int w = 0; w < 3; w++) {
                                simulateWave(sim, player, session, w == 2);
                            }

                            completeQuest(sim, player, session);

                            // Spend some tokens
                            WalletData wallet = sim.wallets.get(player.id);
                            long balance = wallet.tokens.get();
                            if (balance > 100) {
                                synchronized (sim.getPurchaseLock(player.id)) {
                                    if (wallet.spendTokens(50)) {
                                        sim.totalTokensSpent.addAndGet(50);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // Verify economy consistency
            long totalWalletTokens = sim.wallets.values().stream()
                .mapToLong(w -> w.tokens.get())
                .sum();

            long netTokens = sim.totalTokensEarned.get() - sim.totalTokensSpent.get();
            assertEquals(netTokens, totalWalletTokens,
                "Token economy should be consistent: earned=" + sim.totalTokensEarned.get() +
                " spent=" + sim.totalTokensSpent.get() + " wallets=" + totalWalletTokens);

            // No negative balances
            for (WalletData wallet : sim.wallets.values()) {
                assertTrue(wallet.tokens.get() >= 0, "No wallet should have negative balance");
            }
        }

        @Test
        @Order(14)
        @DisplayName("Full telemetry log verification")
        void testTelemetryLogVerification() {
            GameSimulation sim = new GameSimulation();
            PlayerData player = sim.createPlayer("TelemetryPlayer");

            startQuest(sim, player, 5, false);
            QuestSession session = sim.activeSessions.get(player.id);

            for (int i = 0; i < 5; i++) {
                simulateWave(sim, player, session, i == 4);
            }

            selectPerk(sim, player, "TEST_PERK");
            completeQuest(sim, player, session);

            // Verify telemetry captured all events
            List<String> eventMessages = sim.eventLog.stream()
                .map(TelemetryEvent::message)
                .toList();

            assertTrue(eventMessages.stream().anyMatch(m -> m.contains("joined")));
            assertTrue(eventMessages.stream().anyMatch(m -> m.contains("starting")));
            assertTrue(eventMessages.stream().anyMatch(m -> m.contains("entered instance")));
            assertTrue(eventMessages.stream().anyMatch(m -> m.contains("wave 1")));
            assertTrue(eventMessages.stream().anyMatch(m -> m.contains("BOSS wave 5")));
            assertTrue(eventMessages.stream().anyMatch(m -> m.contains("selected perk")));
            assertTrue(eventMessages.stream().anyMatch(m -> m.contains("completed quest")));
            assertTrue(eventMessages.stream().anyMatch(m -> m.contains("destroyed")));

            sim.log("Telemetry verified: " + sim.eventLog.size() + " events recorded");
        }
    }
}
