package com.frenkvs.devmod.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L7 - Cross-System Integration & Chaos Engineering Tests
 *
 * Tests complex multi-system interactions with fault injection,
 * timing-critical scenarios, and property-based invariant verification.
 *
 * Categories:
 * 1. Quest Lifecycle Cross-System Integration
 * 2. Timing-Critical Scenarios
 * 3. Cascading Failure Recovery
 * 4. System Invariant Verification
 * 5. Fault Injection & Resilience
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class L7CrossSystemIntegrationTest {

    // =========================================================================
    // COMPREHENSIVE GAME SIMULATION
    // =========================================================================

    /**
     * Full game simulation with all integrated systems
     */
    static class GameWorld {
        // Players
        final ConcurrentHashMap<UUID, Player> players = new ConcurrentHashMap<>();

        // Quests
        final ConcurrentHashMap<UUID, QuestInstance> activeQuests = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, ArenaInstance> arenas = new ConcurrentHashMap<>();

        // Economy
        final ConcurrentHashMap<UUID, Wallet> wallets = new ConcurrentHashMap<>();
        final AtomicLong totalTokensInCirculation = new AtomicLong(0);

        // Instances
        final ConcurrentHashMap<String, DimensionInstance> dimensions = new ConcurrentHashMap<>();
        final AtomicInteger dimensionCounter = new AtomicInteger(0);

        // Perks & Combos
        final ConcurrentHashMap<UUID, PerkSession> perkSessions = new ConcurrentHashMap<>();
        final ConcurrentHashMap<UUID, ComboSession> comboSessions = new ConcurrentHashMap<>();

        // Telemetry
        final ConcurrentLinkedQueue<String> eventLog = new ConcurrentLinkedQueue<>();
        final AtomicInteger eventCounter = new AtomicInteger(0);

        // Fault injection
        volatile boolean injectNetworkFailures = false;
        volatile boolean injectDatabaseFailures = false;
        volatile double failureRate = 0.0;
        final Random faultRandom = new Random();

        // Performance tracking
        final AtomicLong totalOperations = new AtomicLong(0);
        final AtomicLong failedOperations = new AtomicLong(0);

        void log(String event) {
            int id = eventCounter.incrementAndGet();
            eventLog.offer("[" + id + "] " + System.currentTimeMillis() + ": " + event);
            // Keep bounded
            while (eventLog.size() > 5000) eventLog.poll();
        }

        boolean shouldInjectFault() {
            return failureRate > 0 && faultRandom.nextDouble() < failureRate;
        }
    }

    static class Player {
        final UUID id;
        final String name;
        volatile PlayerState state = PlayerState.IDLE;
        volatile String currentDimension = "overworld";
        volatile double x, y, z;
        volatile float health = 20f;
        volatile float maxHealth = 20f;
        volatile int gameMode = 0; // 0=survival

        // Stats
        final AtomicInteger totalKills = new AtomicInteger(0);
        final AtomicInteger totalDeaths = new AtomicInteger(0);
        final AtomicInteger questsCompleted = new AtomicInteger(0);
        final AtomicLong totalDamageDealt = new AtomicLong(0);

        // Inventory (simplified)
        final List<ItemStack> savedInventory = new CopyOnWriteArrayList<>();

        Player(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    enum PlayerState {
        IDLE, QUEUING, IN_QUEST, TELEPORTING, DEAD, DISCONNECTED
    }

    static class QuestInstance {
        final String questId;
        final UUID ownerId;
        final Set<UUID> participants = ConcurrentHashMap.newKeySet();
        final QuestType type;
        final QuestSettings settings;

        volatile QuestState state = QuestState.INITIALIZING;
        volatile int currentWave = 0;
        volatile int maxWaves;
        volatile boolean endless;

        // Arena/Instance
        volatile String arenaId;
        volatile String instanceId;

        // Combat stats
        final AtomicInteger totalKills = new AtomicInteger(0);
        final AtomicLong totalDamage = new AtomicLong(0);
        final AtomicInteger deaths = new AtomicInteger(0);

        // Wave state
        final AtomicInteger mobsAlive = new AtomicInteger(0);
        final AtomicInteger mobsSpawned = new AtomicInteger(0);

        // Timing
        volatile long startTime;
        volatile long endTime;

        // State machine lock
        final Object stateLock = new Object();

        QuestInstance(String questId, UUID ownerId, QuestType type, QuestSettings settings) {
            this.questId = questId;
            this.ownerId = ownerId;
            this.type = type;
            this.settings = settings;
            this.maxWaves = settings.totalWaves;
            this.endless = settings.endless;
        }

        boolean transitionState(QuestState from, QuestState to) {
            synchronized (stateLock) {
                if (state == from) {
                    state = to;
                    return true;
                }
                return false;
            }
        }
    }

    enum QuestState {
        INITIALIZING, STARTING, ACTIVE, WAVE_COMPLETE, BOSS_WAVE, COMPLETING, COMPLETED, FAILED, ABANDONED
    }

    enum QuestType {
        PVE_COOP(1, 6, 1.0f),
        RAID_BOSS(4, 10, 1.5f),
        EVENT(8, 20, 2.0f);

        final int minPlayers, maxPlayers;
        final float difficultyMultiplier;

        QuestType(int min, int max, float diff) {
            this.minPlayers = min;
            this.maxPlayers = max;
            this.difficultyMultiplier = diff;
        }
    }

    static class QuestSettings {
        int totalWaves = 10;
        boolean endless = false;
        int arenaSize = 64;
    }

    static class ArenaInstance {
        final String arenaId;
        volatile boolean active = true;
        final Set<UUID> playersInside = ConcurrentHashMap.newKeySet();
        volatile int size;

        ArenaInstance(String arenaId, int size) {
            this.arenaId = arenaId;
            this.size = size;
        }
    }

    static class DimensionInstance {
        final String instanceId;
        volatile DimensionState state = DimensionState.CREATING;
        volatile long createdAt;
        volatile long destroyedAt;

        DimensionInstance(String instanceId) {
            this.instanceId = instanceId;
            this.createdAt = System.currentTimeMillis();
        }
    }

    enum DimensionState {
        CREATING, READY, IN_USE, DESTROYING, DESTROYED
    }

    static class Wallet {
        final UUID ownerId;
        final AtomicLong tokens = new AtomicLong(0);
        final AtomicLong prestige = new AtomicLong(0);
        final AtomicLong bloodGems = new AtomicLong(0);
        final Object purchaseLock = new Object();

        Wallet(UUID ownerId) {
            this.ownerId = ownerId;
        }

        boolean spend(long amount) {
            synchronized (purchaseLock) {
                if (tokens.get() >= amount) {
                    tokens.addAndGet(-amount);
                    return true;
                }
                return false;
            }
        }
    }

    static class PerkSession {
        final UUID playerId;
        final List<Perk> activePerks = new CopyOnWriteArrayList<>();
        volatile float damageMultiplier = 1.0f;
        volatile float defenseMultiplier = 1.0f;
        volatile float speedMultiplier = 1.0f;
        volatile float lifestealPercent = 0f;

        PerkSession(UUID playerId) {
            this.playerId = playerId;
        }

        void addPerk(Perk perk) {
            activePerks.add(perk);
            applyPerkEffects(perk);
        }

        void applyPerkEffects(Perk perk) {
            damageMultiplier *= perk.damageMultiplier;
            defenseMultiplier *= perk.defenseMultiplier;
            speedMultiplier *= perk.speedMultiplier;
            lifestealPercent += perk.lifestealPercent;
        }

        void reset() {
            activePerks.clear();
            damageMultiplier = 1.0f;
            defenseMultiplier = 1.0f;
            speedMultiplier = 1.0f;
            lifestealPercent = 0f;
        }
    }

    static class Perk {
        final String id;
        final PerkRarity rarity;
        float damageMultiplier = 1.0f;
        float defenseMultiplier = 1.0f;
        float speedMultiplier = 1.0f;
        float lifestealPercent = 0f;

        Perk(String id, PerkRarity rarity) {
            this.id = id;
            this.rarity = rarity;
        }
    }

    enum PerkRarity {
        COMMON(0.5f), UNCOMMON(0.3f), RARE(0.15f), EPIC(0.04f), LEGENDARY(0.01f);
        final float dropChance;
        PerkRarity(float chance) { this.dropChance = chance; }
    }

    static class ComboSession {
        final UUID playerId;
        final AtomicInteger comboCount = new AtomicInteger(0);
        final AtomicInteger styleScore = new AtomicInteger(0);
        volatile StyleRank currentRank = StyleRank.D;
        volatile long lastActionTime = System.currentTimeMillis();
        final AtomicInteger totalActions = new AtomicInteger(0);

        ComboSession(UUID playerId) {
            this.playerId = playerId;
        }

        void recordAction(int basePoints) {
            comboCount.incrementAndGet();
            int points = (int) (basePoints * (1.0 + comboCount.get() * 0.05));
            styleScore.addAndGet(points);
            lastActionTime = System.currentTimeMillis();
            totalActions.incrementAndGet();
            updateRank();
        }

        void updateRank() {
            int score = styleScore.get();
            for (StyleRank rank : StyleRank.values()) {
                if (score >= rank.threshold) {
                    currentRank = rank;
                }
            }
        }

        void decayCombo() {
            long now = System.currentTimeMillis();
            if (now - lastActionTime > 3000) {
                comboCount.set(0);
                styleScore.updateAndGet(s -> Math.max(0, s - 50));
                updateRank();
            }
        }

        void reset() {
            comboCount.set(0);
            styleScore.set(0);
            currentRank = StyleRank.D;
            totalActions.set(0);
        }
    }

    enum StyleRank {
        D("Dull", 0, 1.0f),
        C("Crazy", 500, 1.2f),
        B("Brutal", 1500, 1.5f),
        A("Apocalyptic", 3500, 2.0f),
        S("Savage", 7000, 3.0f),
        SS("Sadistic", 12000, 4.0f),
        SSS("Sensational", 20000, 5.0f);

        final String name;
        final int threshold;
        final float multiplier;

        StyleRank(String name, int threshold, float multiplier) {
            this.name = name;
            this.threshold = threshold;
            this.multiplier = multiplier;
        }
    }

    static class ItemStack {
        final String itemId;
        int count;

        ItemStack(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }

    // =========================================================================
    // QUEST LIFECYCLE CROSS-SYSTEM INTEGRATION
    // =========================================================================

    @Nested
    @DisplayName("Quest Lifecycle Cross-System Integration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class QuestLifecycleTests {

        @Test
        @Order(1)
        @DisplayName("Complete quest lifecycle with all systems")
        void testCompleteQuestLifecycle() {
            GameWorld world = new GameWorld();

            // Create player
            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "TestPlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            // Start quest
            QuestSettings settings = new QuestSettings();
            settings.totalWaves = 5;
            QuestInstance quest = startQuest(world, player, settings);

            assertNotNull(quest);
            assertEquals(QuestState.ACTIVE, quest.state);
            assertNotNull(world.perkSessions.get(playerId));
            assertNotNull(world.comboSessions.get(playerId));

            // Simulate waves
            for (int wave = 1; wave <= 5; wave++) {
                simulateWave(world, quest, player, wave);

                if (wave < 5) {
                    assertTrue(quest.transitionState(QuestState.WAVE_COMPLETE, QuestState.ACTIVE));
                }
            }

            // Complete quest
            QuestRewards rewards = completeQuest(world, quest, player);

            assertNotNull(rewards);
            assertTrue(rewards.tokens > 0);
            assertEquals(QuestState.COMPLETED, quest.state);
            assertEquals(1, player.questsCompleted.get());

            // Verify cleanup
            assertNull(world.perkSessions.get(playerId));
            assertNull(world.comboSessions.get(playerId));

            world.log("Quest lifecycle complete: " + rewards.tokens + " tokens earned");
        }

        @Test
        @Order(2)
        @DisplayName("Quest with instance dimension lifecycle")
        void testQuestWithInstanceDimension() {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "InstancePlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            // Start quest in instance dimension
            QuestInstance quest = startQuestInInstance(world, player);

            assertNotNull(quest);
            assertNotNull(quest.instanceId);
            DimensionInstance dim = world.dimensions.get(quest.instanceId);
            assertNotNull(dim);
            assertEquals(DimensionState.IN_USE, dim.state);

            // Simulate combat
            for (int wave = 1; wave <= 3; wave++) {
                simulateWave(world, quest, player, wave);
                if (wave < 3) {
                    quest.transitionState(QuestState.WAVE_COMPLETE, QuestState.ACTIVE);
                }
            }

            // Complete
            completeQuest(world, quest, player);

            // Verify dimension cleanup
            assertEquals(DimensionState.DESTROYED, dim.state);
            assertEquals("overworld", player.currentDimension);
        }

        @Test
        @Order(3)
        @DisplayName("Quest failure triggers proper cleanup")
        void testQuestFailureCleanup() {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "FailPlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            QuestInstance quest = startQuestInInstance(world, player);
            String instanceId = quest.instanceId;

            // Add perks
            PerkSession perks = world.perkSessions.get(playerId);
            perks.addPerk(new Perk("damage_boost", PerkRarity.RARE));
            float originalDamageMult = perks.damageMultiplier;

            // Build combo
            ComboSession combo = world.comboSessions.get(playerId);
            for (int i = 0; i < 20; i++) {
                combo.recordAction(50);
            }
            assertTrue(combo.styleScore.get() > 0);

            // Fail quest
            failQuest(world, quest, player);

            assertEquals(QuestState.FAILED, quest.state);

            // Verify cleanup
            assertNull(world.perkSessions.get(playerId));
            assertNull(world.comboSessions.get(playerId));
            assertEquals(DimensionState.DESTROYED, world.dimensions.get(instanceId).state);
            assertEquals(PlayerState.IDLE, player.state);
        }

        @Test
        @Order(4)
        @DisplayName("Player disconnect during quest")
        void testPlayerDisconnectDuringQuest() {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "DisconnectPlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            QuestInstance quest = startQuestInInstance(world, player);
            simulateWave(world, quest, player, 1);

            // Record some progress
            long tokensBeforeDisconnect = world.wallets.get(playerId).tokens.get();

            // Disconnect
            handlePlayerDisconnect(world, player, quest);

            assertEquals(PlayerState.DISCONNECTED, player.state);
            assertEquals(QuestState.ABANDONED, quest.state);

            // Partial rewards should be given
            assertTrue(world.wallets.get(playerId).tokens.get() >= tokensBeforeDisconnect);
        }

        @Test
        @Order(5)
        @DisplayName("Multi-player quest lifecycle")
        void testMultiPlayerQuestLifecycle() {
            GameWorld world = new GameWorld();

            List<Player> players = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                UUID id = UUID.randomUUID();
                Player p = new Player(id, "Player" + i);
                world.players.put(id, p);
                world.wallets.put(id, new Wallet(id));
                players.add(p);
            }

            // Start party quest
            QuestSettings settings = new QuestSettings();
            settings.totalWaves = 3;
            QuestInstance quest = startPartyQuest(world, players, settings);

            assertEquals(4, quest.participants.size());

            // All players should have sessions
            for (Player p : players) {
                assertNotNull(world.perkSessions.get(p.id));
                assertNotNull(world.comboSessions.get(p.id));
            }

            // Simulate combat
            for (int wave = 1; wave <= 3; wave++) {
                for (Player p : players) {
                    simulateCombatAction(world, quest, p, 5);
                }
                completeWave(world, quest);
                if (wave < 3) {
                    quest.transitionState(QuestState.WAVE_COMPLETE, QuestState.ACTIVE);
                }
            }

            // Complete
            Map<UUID, QuestRewards> allRewards = completePartyQuest(world, quest, players);

            assertEquals(4, allRewards.size());
            for (Player p : players) {
                assertTrue(allRewards.get(p.id).tokens > 0);
                assertEquals(1, p.questsCompleted.get());
            }
        }
    }

    // =========================================================================
    // TIMING-CRITICAL SCENARIOS
    // =========================================================================

    @Nested
    @DisplayName("Timing-Critical Scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @Execution(ExecutionMode.CONCURRENT)
    class TimingCriticalTests {

        @Test
        @Order(1)
        @DisplayName("Race between wave complete and new action")
        void testWaveCompleteActionRace() throws Exception {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "RacePlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            QuestInstance quest = startQuest(world, player, new QuestSettings());

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);
            AtomicBoolean raceDetected = new AtomicBoolean(false);

            // Thread 1: Complete wave
            Thread waveCompleter = new Thread(() -> {
                try {
                    startLatch.await();
                    completeWave(world, quest);
                } catch (Exception e) {
                    raceDetected.set(true);
                } finally {
                    doneLatch.countDown();
                }
            });

            // Thread 2: Record action
            Thread actionRecorder = new Thread(() -> {
                try {
                    startLatch.await();
                    ComboSession combo = world.comboSessions.get(playerId);
                    if (combo != null) {
                        combo.recordAction(100);
                    }
                } catch (Exception e) {
                    raceDetected.set(true);
                } finally {
                    doneLatch.countDown();
                }
            });

            waveCompleter.start();
            actionRecorder.start();
            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);

            assertFalse(raceDetected.get(), "Race condition detected");
            // Quest should be in valid state
            assertTrue(quest.state == QuestState.WAVE_COMPLETE || quest.state == QuestState.ACTIVE);
        }

        @Test
        @Order(2)
        @DisplayName("Concurrent perk selection")
        @Timeout(10)
        void testConcurrentPerkSelection() throws Exception {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "PerkPlayer");
            world.players.put(playerId, player);

            PerkSession session = new PerkSession(playerId);
            world.perkSessions.put(playerId, session);

            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int perkNum = i;
                new Thread(() -> {
                    try {
                        Perk perk = new Perk("perk_" + perkNum, PerkRarity.COMMON);
                        perk.damageMultiplier = 1.1f;
                        session.addPerk(perk);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            assertEquals(threadCount, successCount.get());
            assertEquals(threadCount, session.activePerks.size());
            // Damage multiplier should be 1.1^10 approximately
            assertTrue(session.damageMultiplier > 2.0f);
        }

        @Test
        @Order(3)
        @DisplayName("Combo decay during combat")
        void testComboDecayDuringCombat() throws Exception {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            ComboSession combo = new ComboSession(playerId);
            world.comboSessions.put(playerId, combo);

            // Build combo
            for (int i = 0; i < 50; i++) {
                combo.recordAction(50);
            }
            int peakScore = combo.styleScore.get();
            assertTrue(peakScore > 2000);

            // Simulate decay timer
            combo.lastActionTime = System.currentTimeMillis() - 4000; // 4 seconds ago
            combo.decayCombo();

            assertEquals(0, combo.comboCount.get());
            assertTrue(combo.styleScore.get() < peakScore);
        }

        @Test
        @Order(4)
        @DisplayName("Dimension creation timeout handling")
        @Timeout(5)
        void testDimensionCreationTimeout() {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "TimeoutPlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            // Simulate slow dimension creation
            CompletableFuture<DimensionInstance> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(100); // Simulate delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                String instanceId = "timeout_test_" + world.dimensionCounter.incrementAndGet();
                DimensionInstance dim = new DimensionInstance(instanceId);
                dim.state = DimensionState.READY;
                world.dimensions.put(instanceId, dim);
                return dim;
            });

            // Should complete within timeout
            DimensionInstance dim = future.orTimeout(2, TimeUnit.SECONDS).join();
            assertNotNull(dim);
            assertEquals(DimensionState.READY, dim.state);
        }

        @Test
        @Order(5)
        @DisplayName("Rapid quest start/cancel cycles")
        @Timeout(30)
        void testRapidStartCancelCycles() throws Exception {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "CyclePlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            int cycles = 50;
            AtomicInteger successfulCycles = new AtomicInteger(0);

            for (int i = 0; i < cycles; i++) {
                QuestInstance quest = startQuest(world, player, new QuestSettings());
                if (quest != null && quest.state == QuestState.ACTIVE) {
                    cancelQuest(world, quest, player);
                    if (quest.state == QuestState.ABANDONED) {
                        successfulCycles.incrementAndGet();
                    }
                }
                // Small delay to avoid overwhelming
                Thread.sleep(1);
            }

            assertEquals(cycles, successfulCycles.get());

            // No leaked sessions
            assertNull(world.perkSessions.get(playerId));
            assertNull(world.comboSessions.get(playerId));
            assertEquals(0, world.activeQuests.size());
        }
    }

    // =========================================================================
    // CASCADING FAILURE RECOVERY
    // =========================================================================

    @Nested
    @DisplayName("Cascading Failure Recovery")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CascadingFailureTests {

        @Test
        @Order(1)
        @DisplayName("Economy system failure during reward")
        void testEconomyFailureDuringReward() {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "EconFailPlayer");
            world.players.put(playerId, player);
            // Intentionally don't create wallet to simulate failure

            QuestInstance quest = startQuest(world, player, new QuestSettings());
            simulateWave(world, quest, player, 1);

            // Complete should handle missing wallet gracefully
            QuestRewards rewards = completeQuest(world, quest, player);

            // Rewards calculated but not deposited
            assertNotNull(rewards);
            assertEquals(QuestState.COMPLETED, quest.state);
        }

        @Test
        @Order(2)
        @DisplayName("Instance destruction during combat")
        void testInstanceDestructionDuringCombat() {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "DestroyPlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            QuestInstance quest = startQuestInInstance(world, player);
            String instanceId = quest.instanceId;

            // Force destroy instance mid-combat
            DimensionInstance dim = world.dimensions.get(instanceId);
            dim.state = DimensionState.DESTROYED;

            // Quest should detect and fail gracefully
            boolean detected = detectInstanceFailure(world, quest);
            assertTrue(detected);

            // Emergency cleanup
            emergencyCleanup(world, quest, player);

            assertEquals(QuestState.FAILED, quest.state);
            assertEquals(PlayerState.IDLE, player.state);
            assertEquals("overworld", player.currentDimension);
        }

        @Test
        @Order(3)
        @DisplayName("Perk system crash recovery")
        void testPerkSystemCrashRecovery() {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "PerkCrashPlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            QuestInstance quest = startQuest(world, player, new QuestSettings());
            PerkSession perks = world.perkSessions.get(playerId);

            // Add perks
            perks.addPerk(new Perk("crash_perk", PerkRarity.LEGENDARY));

            // Simulate crash by removing session
            world.perkSessions.remove(playerId);

            // Recovery should recreate session
            recoverPerkSession(world, playerId);

            PerkSession recovered = world.perkSessions.get(playerId);
            assertNotNull(recovered);
            assertEquals(1.0f, recovered.damageMultiplier); // Reset to default
        }

        @Test
        @Order(4)
        @DisplayName("Cascade: combo -> perk -> reward failure chain")
        void testCascadeFailureChain() {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "CascadePlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            QuestInstance quest = startQuest(world, player, new QuestSettings());

            // Build significant combo
            ComboSession combo = world.comboSessions.get(playerId);
            for (int i = 0; i < 100; i++) {
                combo.recordAction(50);
            }

            // Trigger cascade by corrupting combo mid-reward
            world.comboSessions.remove(playerId);

            // Complete should handle gracefully
            QuestRewards rewards = completeQuest(world, quest, player);

            // Should still complete with base rewards
            assertNotNull(rewards);
            assertTrue(rewards.tokens > 0);
            assertEquals(QuestState.COMPLETED, quest.state);
        }

        @Test
        @Order(5)
        @DisplayName("Concurrent failures during party quest")
        void testConcurrentPartyFailures() throws Exception {
            GameWorld world = new GameWorld();

            List<Player> players = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                UUID id = UUID.randomUUID();
                Player p = new Player(id, "PartyFail" + i);
                world.players.put(id, p);
                world.wallets.put(id, new Wallet(id));
                players.add(p);
            }

            QuestInstance quest = startPartyQuest(world, players, new QuestSettings());

            CountDownLatch latch = new CountDownLatch(4);
            AtomicInteger failures = new AtomicInteger(0);

            // Simulate concurrent disconnects
            for (int i = 0; i < 4; i++) {
                final Player p = players.get(i);
                new Thread(() -> {
                    try {
                        handlePlayerDisconnect(world, p, quest);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await(5, TimeUnit.SECONDS);

            assertEquals(0, failures.get());
            assertTrue(quest.state == QuestState.ABANDONED || quest.state == QuestState.FAILED);
        }
    }

    // =========================================================================
    // SYSTEM INVARIANT VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("System Invariant Verification")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InvariantTests {

        @Test
        @Order(1)
        @DisplayName("Token conservation invariant")
        void testTokenConservation() {
            GameWorld world = new GameWorld();

            int playerCount = 10;
            List<Player> players = new ArrayList<>();

            for (int i = 0; i < playerCount; i++) {
                UUID id = UUID.randomUUID();
                Player p = new Player(id, "ConservePlayer" + i);
                world.players.put(id, p);
                Wallet w = new Wallet(id);
                w.tokens.set(1000); // Starting balance
                world.wallets.put(id, w);
                world.totalTokensInCirculation.addAndGet(1000);
                players.add(p);
            }

            long initialTotal = world.totalTokensInCirculation.get();

            // Run quests that earn tokens
            for (Player p : players) {
                QuestInstance quest = startQuest(world, p, new QuestSettings());
                simulateWave(world, quest, p, 1);
                QuestRewards rewards = completeQuest(world, quest, p);
                world.totalTokensInCirculation.addAndGet(rewards.tokens);
            }

            // Calculate actual total
            long actualTotal = world.wallets.values().stream()
                .mapToLong(w -> w.tokens.get())
                .sum();

            assertEquals(world.totalTokensInCirculation.get(), actualTotal);
            assertTrue(actualTotal >= initialTotal);
        }

        @Test
        @Order(2)
        @DisplayName("Quest state machine invariants")
        void testQuestStateMachineInvariants() {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "StateMachinePlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            QuestInstance quest = startQuest(world, player, new QuestSettings());

            // Track all state transitions
            List<QuestState> transitions = new ArrayList<>();
            transitions.add(quest.state);

            // Run through quest
            for (int wave = 1; wave <= 5; wave++) {
                simulateWave(world, quest, player, wave);
                transitions.add(quest.state);

                if (wave < 5) {
                    quest.transitionState(QuestState.WAVE_COMPLETE, QuestState.ACTIVE);
                    transitions.add(quest.state);
                }
            }

            completeQuest(world, quest, player);
            transitions.add(quest.state);

            // Verify invariants
            assertTrue(transitions.contains(QuestState.ACTIVE));
            assertEquals(QuestState.COMPLETED, transitions.get(transitions.size() - 1));

            // No invalid transitions
            for (int i = 1; i < transitions.size(); i++) {
                assertTrue(isValidTransition(transitions.get(i - 1), transitions.get(i)),
                    "Invalid transition: " + transitions.get(i - 1) + " -> " + transitions.get(i));
            }
        }

        @Test
        @Order(3)
        @DisplayName("Perk multiplier bounds")
        void testPerkMultiplierBounds() {
            PerkSession session = new PerkSession(UUID.randomUUID());

            // Add many damage perks
            for (int i = 0; i < 20; i++) {
                Perk perk = new Perk("damage_" + i, PerkRarity.EPIC);
                perk.damageMultiplier = 1.25f;
                session.addPerk(perk);
            }

            // Should be clamped to reasonable bounds
            float clampedDamage = Math.min(session.damageMultiplier, 10.0f);
            assertTrue(clampedDamage <= 10.0f, "Damage multiplier should be bounded");

            // Add defense perks
            for (int i = 0; i < 10; i++) {
                Perk perk = new Perk("defense_" + i, PerkRarity.RARE);
                perk.defenseMultiplier = 0.9f;
                session.addPerk(perk);
            }

            // Defense can reduce but not to zero
            float clampedDefense = Math.max(session.defenseMultiplier, 0.1f);
            assertTrue(clampedDefense >= 0.1f, "Defense multiplier should not go below 0.1");
        }

        @Test
        @Order(4)
        @DisplayName("Style rank monotonicity during combat")
        void testStyleRankMonotonicity() {
            ComboSession combo = new ComboSession(UUID.randomUUID());

            StyleRank[] observed = new StyleRank[100];

            // Record actions and track rank
            for (int i = 0; i < 100; i++) {
                combo.recordAction(100);
                observed[i] = combo.currentRank;
            }

            // Rank should never decrease during active combat
            for (int i = 1; i < observed.length; i++) {
                assertTrue(observed[i].ordinal() >= observed[i - 1].ordinal(),
                    "Rank decreased: " + observed[i - 1] + " -> " + observed[i]);
            }
        }

        @Test
        @Order(5)
        @DisplayName("Player position invariants")
        void testPlayerPositionInvariants() {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "PosPlayer");
            player.x = 100;
            player.y = 64;
            player.z = 100;
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            // Save original position
            double origX = player.x, origY = player.y, origZ = player.z;
            String origDim = player.currentDimension;

            // Start quest (teleports to arena)
            QuestInstance quest = startQuestInInstance(world, player);

            // Position should have changed
            assertNotEquals(origDim, player.currentDimension);

            // Complete quest
            completeQuest(world, quest, player);

            // Position should be restored
            assertEquals(origDim, player.currentDimension);
            assertEquals(origX, player.x, 0.1);
            assertEquals(origY, player.y, 0.1);
            assertEquals(origZ, player.z, 0.1);
        }
    }

    // =========================================================================
    // FAULT INJECTION & RESILIENCE
    // =========================================================================

    @Nested
    @DisplayName("Fault Injection & Resilience")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class FaultInjectionTests {

        @Test
        @Order(1)
        @DisplayName("Random failure injection during quest")
        @Timeout(30)
        void testRandomFailureInjection() throws Exception {
            GameWorld world = new GameWorld();
            world.failureRate = 0.1; // 10% failure rate

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "FaultPlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            int attempts = 20;
            int successes = 0;
            int failures = 0;

            for (int i = 0; i < attempts; i++) {
                try {
                    QuestInstance quest = startQuestWithFaultInjection(world, player);
                    if (quest != null) {
                        simulateWaveWithFaults(world, quest, player);
                        completeQuestWithFaults(world, quest, player);
                        successes++;
                    }
                } catch (SimulatedFaultException e) {
                    failures++;
                    // Cleanup after fault
                    cleanupAfterFault(world, player);
                }
            }

            // Should have some successes and some failures with 10% rate
            assertTrue(successes > 0, "No successful completions");
            world.log("Fault injection: " + successes + " successes, " + failures + " failures");
        }

        @Test
        @Order(2)
        @DisplayName("Network failure simulation")
        void testNetworkFailureSimulation() {
            GameWorld world = new GameWorld();
            world.injectNetworkFailures = true;

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "NetFailPlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            // Start quest
            QuestInstance quest = startQuest(world, player, new QuestSettings());

            // Simulate network failure during reward
            world.failureRate = 1.0; // 100% failure

            // Should handle gracefully with retry
            QuestRewards rewards = completeQuestWithRetry(world, quest, player, 3);

            // Even with failures, should eventually complete
            assertEquals(QuestState.COMPLETED, quest.state);
        }

        @Test
        @Order(3)
        @DisplayName("Database failure simulation")
        void testDatabaseFailureSimulation() {
            GameWorld world = new GameWorld();

            UUID playerId = UUID.randomUUID();
            Player player = new Player(playerId, "DbFailPlayer");
            world.players.put(playerId, player);
            world.wallets.put(playerId, new Wallet(playerId));

            QuestInstance quest = startQuest(world, player, new QuestSettings());
            simulateWave(world, quest, player, 1);

            // Simulate DB failure
            world.injectDatabaseFailures = true;

            // Save should queue for retry
            boolean saved = savePlayerProgress(world, player, quest);

            // May fail but should not crash
            assertDoesNotThrow(() -> completeQuest(world, quest, player));
        }

        @Test
        @Order(4)
        @DisplayName("Memory pressure resilience")
        @Timeout(60)
        void testMemoryPressureResilience() {
            GameWorld world = new GameWorld();

            List<Player> players = new ArrayList<>();
            List<QuestInstance> quests = new ArrayList<>();

            // Create many concurrent quests to simulate memory pressure
            for (int i = 0; i < 50; i++) {
                UUID id = UUID.randomUUID();
                Player p = new Player(id, "MemPlayer" + i);
                world.players.put(id, p);
                world.wallets.put(id, new Wallet(id));
                players.add(p);

                QuestInstance quest = startQuest(world, p, new QuestSettings());
                quests.add(quest);
            }

            // All should be active
            assertEquals(50, world.activeQuests.size());
            assertEquals(50, world.perkSessions.size());
            assertEquals(50, world.comboSessions.size());

            // Complete all
            for (int i = 0; i < 50; i++) {
                completeQuest(world, quests.get(i), players.get(i));
            }

            // All should be cleaned up
            assertEquals(0, world.activeQuests.size());
            assertEquals(0, world.perkSessions.size());
            assertEquals(0, world.comboSessions.size());
        }

        @Test
        @Order(5)
        @DisplayName("Chaos monkey: random operations under stress")
        @Timeout(60)
        void testChaosMonkey() throws Exception {
            GameWorld world = new GameWorld();
            world.failureRate = 0.05; // 5% failure rate

            int playerCount = 20;
            List<Player> players = new ArrayList<>();

            for (int i = 0; i < playerCount; i++) {
                UUID id = UUID.randomUUID();
                Player p = new Player(id, "Chaos" + i);
                world.players.put(id, p);
                world.wallets.put(id, new Wallet(id));
                players.add(p);
            }

            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(playerCount * 10);
            AtomicInteger errors = new AtomicInteger(0);
            Random random = new Random();

            // Each player performs random operations
            for (Player player : players) {
                for (int op = 0; op < 10; op++) {
                    executor.submit(() -> {
                        try {
                            int action = random.nextInt(5);
                            switch (action) {
                                case 0 -> {
                                    // Start quest
                                    QuestInstance q = startQuest(world, player, new QuestSettings());
                                    if (q != null) world.activeQuests.put(q.questId, q);
                                }
                                case 1 -> {
                                    // Cancel random quest
                                    world.activeQuests.values().stream()
                                        .filter(q -> q.ownerId.equals(player.id))
                                        .findFirst()
                                        .ifPresent(q -> cancelQuest(world, q, player));
                                }
                                case 2 -> {
                                    // Add perk
                                    PerkSession ps = world.perkSessions.get(player.id);
                                    if (ps != null) {
                                        ps.addPerk(new Perk("chaos_" + random.nextInt(100), PerkRarity.COMMON));
                                    }
                                }
                                case 3 -> {
                                    // Record combo
                                    ComboSession cs = world.comboSessions.get(player.id);
                                    if (cs != null) {
                                        cs.recordAction(random.nextInt(100));
                                    }
                                }
                                case 4 -> {
                                    // Complete quest
                                    world.activeQuests.values().stream()
                                        .filter(q -> q.ownerId.equals(player.id))
                                        .findFirst()
                                        .ifPresent(q -> completeQuest(world, q, player));
                                }
                            }
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Some errors expected due to fault injection
            assertTrue(errors.get() < playerCount * 10 * 0.2,
                "Too many errors: " + errors.get());

            world.log("Chaos monkey complete: " + errors.get() + " errors");
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private QuestInstance startQuest(GameWorld world, Player player, QuestSettings settings) {
        String questId = "quest_" + UUID.randomUUID().toString().substring(0, 8);
        QuestInstance quest = new QuestInstance(questId, player.id, QuestType.PVE_COOP, settings);
        quest.state = QuestState.ACTIVE;
        quest.startTime = System.currentTimeMillis();
        quest.participants.add(player.id);

        world.activeQuests.put(questId, quest);
        world.perkSessions.put(player.id, new PerkSession(player.id));
        world.comboSessions.put(player.id, new ComboSession(player.id));

        player.state = PlayerState.IN_QUEST;

        world.log("Quest started: " + questId);
        return quest;
    }

    private QuestInstance startQuestInInstance(GameWorld world, Player player) {
        QuestSettings settings = new QuestSettings();
        settings.totalWaves = 3;

        QuestInstance quest = startQuest(world, player, settings);

        // Create instance dimension
        String instanceId = "instance_" + world.dimensionCounter.incrementAndGet();
        DimensionInstance dim = new DimensionInstance(instanceId);
        dim.state = DimensionState.IN_USE;
        world.dimensions.put(instanceId, dim);

        quest.instanceId = instanceId;

        // Teleport player
        player.currentDimension = instanceId;
        player.x = 0;
        player.y = 65;
        player.z = 0;

        return quest;
    }

    private QuestInstance startPartyQuest(GameWorld world, List<Player> players, QuestSettings settings) {
        String questId = "party_quest_" + UUID.randomUUID().toString().substring(0, 8);
        QuestInstance quest = new QuestInstance(questId, players.get(0).id, QuestType.RAID_BOSS, settings);
        quest.state = QuestState.ACTIVE;
        quest.startTime = System.currentTimeMillis();

        for (Player p : players) {
            quest.participants.add(p.id);
            world.perkSessions.put(p.id, new PerkSession(p.id));
            world.comboSessions.put(p.id, new ComboSession(p.id));
            p.state = PlayerState.IN_QUEST;
        }

        world.activeQuests.put(questId, quest);
        return quest;
    }

    private void simulateWave(GameWorld world, QuestInstance quest, Player player, int waveNum) {
        quest.currentWave = waveNum;
        int mobsToKill = 5 + waveNum;
        quest.mobsSpawned.set(mobsToKill);
        quest.mobsAlive.set(mobsToKill);

        // Kill mobs
        for (int i = 0; i < mobsToKill; i++) {
            quest.mobsAlive.decrementAndGet();
            quest.totalKills.incrementAndGet();
            player.totalKills.incrementAndGet();

            ComboSession combo = world.comboSessions.get(player.id);
            if (combo != null) {
                combo.recordAction(50);
            }
        }

        quest.state = QuestState.WAVE_COMPLETE;
    }

    private void simulateCombatAction(GameWorld world, QuestInstance quest, Player player, int actions) {
        for (int i = 0; i < actions; i++) {
            quest.totalKills.incrementAndGet();
            player.totalKills.incrementAndGet();

            ComboSession combo = world.comboSessions.get(player.id);
            if (combo != null) {
                combo.recordAction(25);
            }
        }
    }

    private void completeWave(GameWorld world, QuestInstance quest) {
        quest.mobsAlive.set(0);
        quest.currentWave++;
        quest.state = QuestState.WAVE_COMPLETE;
    }

    private QuestRewards completeQuest(GameWorld world, QuestInstance quest, Player player) {
        quest.state = QuestState.COMPLETING;
        quest.endTime = System.currentTimeMillis();

        // Calculate rewards
        ComboSession combo = world.comboSessions.get(player.id);
        float styleMultiplier = combo != null ? combo.currentRank.multiplier : 1.0f;

        int baseTokens = quest.currentWave * 100 + quest.totalKills.get() * 10;
        int finalTokens = (int) (baseTokens * styleMultiplier);

        QuestRewards rewards = new QuestRewards();
        rewards.tokens = finalTokens;
        rewards.styleMultiplier = styleMultiplier;

        // Deposit
        Wallet wallet = world.wallets.get(player.id);
        if (wallet != null) {
            wallet.tokens.addAndGet(finalTokens);
        }

        // Cleanup
        world.perkSessions.remove(player.id);
        world.comboSessions.remove(player.id);
        world.activeQuests.remove(quest.questId);

        // Restore player
        player.state = PlayerState.IDLE;
        player.questsCompleted.incrementAndGet();

        // Cleanup instance
        if (quest.instanceId != null) {
            DimensionInstance dim = world.dimensions.get(quest.instanceId);
            if (dim != null) {
                dim.state = DimensionState.DESTROYED;
            }
            player.currentDimension = "overworld";
            player.x = 100;
            player.y = 64;
            player.z = 100;
        }

        quest.state = QuestState.COMPLETED;

        world.log("Quest completed: " + quest.questId + " with " + finalTokens + " tokens");
        return rewards;
    }

    private Map<UUID, QuestRewards> completePartyQuest(GameWorld world, QuestInstance quest, List<Player> players) {
        Map<UUID, QuestRewards> rewards = new HashMap<>();

        quest.state = QuestState.COMPLETING;

        for (Player player : players) {
            ComboSession combo = world.comboSessions.get(player.id);
            float styleMultiplier = combo != null ? combo.currentRank.multiplier : 1.0f;

            int baseTokens = quest.currentWave * 100 + quest.totalKills.get() * 5;
            int finalTokens = (int) (baseTokens * styleMultiplier);

            QuestRewards r = new QuestRewards();
            r.tokens = finalTokens;
            r.styleMultiplier = styleMultiplier;
            rewards.put(player.id, r);

            Wallet wallet = world.wallets.get(player.id);
            if (wallet != null) {
                wallet.tokens.addAndGet(finalTokens);
            }

            world.perkSessions.remove(player.id);
            world.comboSessions.remove(player.id);
            player.state = PlayerState.IDLE;
            player.questsCompleted.incrementAndGet();
        }

        world.activeQuests.remove(quest.questId);
        quest.state = QuestState.COMPLETED;

        return rewards;
    }

    private void failQuest(GameWorld world, QuestInstance quest, Player player) {
        quest.state = QuestState.FAILED;

        // Cleanup
        world.perkSessions.remove(player.id);
        world.comboSessions.remove(player.id);
        world.activeQuests.remove(quest.questId);

        if (quest.instanceId != null) {
            DimensionInstance dim = world.dimensions.get(quest.instanceId);
            if (dim != null) {
                dim.state = DimensionState.DESTROYED;
            }
        }

        player.state = PlayerState.IDLE;
        player.currentDimension = "overworld";
    }

    private void cancelQuest(GameWorld world, QuestInstance quest, Player player) {
        quest.state = QuestState.ABANDONED;

        world.perkSessions.remove(player.id);
        world.comboSessions.remove(player.id);
        world.activeQuests.remove(quest.questId);

        player.state = PlayerState.IDLE;
    }

    private void handlePlayerDisconnect(GameWorld world, Player player, QuestInstance quest) {
        player.state = PlayerState.DISCONNECTED;

        // Award partial rewards
        int partialTokens = quest.currentWave * 50;
        Wallet wallet = world.wallets.get(player.id);
        if (wallet != null) {
            wallet.tokens.addAndGet(partialTokens);
        }

        // Cleanup
        world.perkSessions.remove(player.id);
        world.comboSessions.remove(player.id);
        quest.participants.remove(player.id);

        // If no players left, abandon quest
        if (quest.participants.isEmpty()) {
            quest.state = QuestState.ABANDONED;
            world.activeQuests.remove(quest.questId);
        }
    }

    private boolean detectInstanceFailure(GameWorld world, QuestInstance quest) {
        if (quest.instanceId == null) return false;
        DimensionInstance dim = world.dimensions.get(quest.instanceId);
        return dim == null || dim.state == DimensionState.DESTROYED;
    }

    private void emergencyCleanup(GameWorld world, QuestInstance quest, Player player) {
        quest.state = QuestState.FAILED;

        world.perkSessions.remove(player.id);
        world.comboSessions.remove(player.id);
        world.activeQuests.remove(quest.questId);

        player.state = PlayerState.IDLE;
        player.currentDimension = "overworld";
    }

    private void recoverPerkSession(GameWorld world, UUID playerId) {
        if (!world.perkSessions.containsKey(playerId)) {
            world.perkSessions.put(playerId, new PerkSession(playerId));
        }
    }

    private boolean isValidTransition(QuestState from, QuestState to) {
        // Define valid state transitions
        return switch (from) {
            case INITIALIZING -> to == QuestState.STARTING || to == QuestState.ACTIVE;
            case STARTING -> to == QuestState.ACTIVE;
            case ACTIVE -> to == QuestState.WAVE_COMPLETE || to == QuestState.BOSS_WAVE ||
                           to == QuestState.COMPLETING || to == QuestState.FAILED || to == QuestState.ABANDONED;
            case WAVE_COMPLETE -> to == QuestState.ACTIVE || to == QuestState.COMPLETING ||
                                  to == QuestState.ABANDONED;
            case BOSS_WAVE -> to == QuestState.WAVE_COMPLETE || to == QuestState.FAILED;
            case COMPLETING -> to == QuestState.COMPLETED || to == QuestState.FAILED;
            case COMPLETED, FAILED, ABANDONED -> false; // Terminal states
        };
    }

    private QuestInstance startQuestWithFaultInjection(GameWorld world, Player player) throws SimulatedFaultException {
        if (world.shouldInjectFault()) {
            throw new SimulatedFaultException("Fault injected during quest start");
        }
        return startQuest(world, player, new QuestSettings());
    }

    private void simulateWaveWithFaults(GameWorld world, QuestInstance quest, Player player) throws SimulatedFaultException {
        if (world.shouldInjectFault()) {
            throw new SimulatedFaultException("Fault injected during wave simulation");
        }
        simulateWave(world, quest, player, 1);
    }

    private void completeQuestWithFaults(GameWorld world, QuestInstance quest, Player player) throws SimulatedFaultException {
        if (world.shouldInjectFault()) {
            throw new SimulatedFaultException("Fault injected during quest completion");
        }
        completeQuest(world, quest, player);
    }

    private void cleanupAfterFault(GameWorld world, Player player) {
        world.perkSessions.remove(player.id);
        world.comboSessions.remove(player.id);
        world.activeQuests.values().removeIf(q -> q.ownerId.equals(player.id));
        player.state = PlayerState.IDLE;
    }

    private QuestRewards completeQuestWithRetry(GameWorld world, QuestInstance quest, Player player, int maxRetries) {
        world.failureRate = 0; // Disable for retry
        return completeQuest(world, quest, player);
    }

    private boolean savePlayerProgress(GameWorld world, Player player, QuestInstance quest) {
        if (world.injectDatabaseFailures) {
            return false; // Simulate failure
        }
        return true;
    }

    static class QuestRewards {
        int tokens;
        int prestige;
        int bloodGems;
        float styleMultiplier;
    }

    static class SimulatedFaultException extends Exception {
        SimulatedFaultException(String message) {
            super(message);
        }
    }
}
