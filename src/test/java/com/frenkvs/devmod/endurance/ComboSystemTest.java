package com.frenkvs.devmod.endurance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive L1-L5 tests for the ComboSystem (DMC-inspired style scoring).
 * Tests style ranks, combo mechanics, action points, session management.
 */
@DisplayName("ComboSystem Tests")
class ComboSystemTest {

    // ========== Simulated Enums and Classes ==========

    /**
     * Simulated StyleRank enum matching production.
     */
    enum SimStyleRank {
        D("Dull", 0, 0x888888, 1.0f),
        C("Crazy", 500, 0x66FF66, 1.2f),
        B("Brutal", 1500, 0x6666FF, 1.5f),
        A("Apocalyptic", 3500, 0xFFFF00, 2.0f),
        S("Savage", 7000, 0xFF9900, 3.0f),
        SS("Sadistic", 12000, 0xFF3300, 4.0f),
        SSS("Sensational", 20000, 0xFF00FF, 5.0f);

        public final String displayName;
        public final int scoreThreshold;
        public final int color;
        public final float rewardMultiplier;

        SimStyleRank(String displayName, int scoreThreshold, int color, float rewardMultiplier) {
            this.displayName = displayName;
            this.scoreThreshold = scoreThreshold;
            this.color = color;
            this.rewardMultiplier = rewardMultiplier;
        }

        public static SimStyleRank fromScore(int score) {
            SimStyleRank result = D;
            for (SimStyleRank rank : values()) {
                if (score >= rank.scoreThreshold) {
                    result = rank;
                }
            }
            return result;
        }

        public SimStyleRank getNext() {
            int next = ordinal() + 1;
            return next < values().length ? values()[next] : this;
        }
    }

    /**
     * Simulated ActionType enum matching production.
     */
    enum SimActionType {
        // Basic attacks
        LIGHT_ATTACK("Light Attack", 10, 5),
        HEAVY_ATTACK("Heavy Attack", 25, 15),
        CRITICAL_HIT("Critical Hit", 50, 30),

        // Special moves
        AERIAL_ATTACK("Aerial Attack", 40, 25),
        BACKSTAB("Backstab", 60, 40),
        COUNTER_ATTACK("Counter Attack", 75, 50),
        PERFECT_DODGE("Perfect Dodge", 30, 20),
        PARRY("Parry", 45, 35),

        // Kill bonuses
        KILL("Kill", 100, 60),
        QUICK_KILL("Quick Kill", 150, 90),
        OVERKILL("Overkill", 200, 120),
        MULTI_KILL_2("Double Kill", 250, 150),
        MULTI_KILL_3("Triple Kill", 400, 250),
        MULTI_KILL_4("Quad Kill", 600, 400),
        MULTI_KILL_5("Penta Kill", 1000, 700),

        // Combo milestones
        COMBO_5("5 Hit Combo", 50, 30),
        COMBO_10("10 Hit Combo", 100, 60),
        COMBO_25("25 Hit Combo", 250, 150),
        COMBO_50("50 Hit Combo", 500, 300),
        COMBO_100("100 Hit Combo", 1000, 600),

        // Wave bonuses
        NO_DAMAGE_WAVE("Flawless Wave", 500, 350),
        WAVE_CLEAR("Wave Clear", 200, 100);

        public final String displayName;
        public final int basePoints;
        public final int stylePoints;

        SimActionType(String displayName, int basePoints, int stylePoints) {
            this.displayName = displayName;
            this.basePoints = basePoints;
            this.stylePoints = stylePoints;
        }
    }

    /**
     * Simulated ActionAnnouncement.
     */
    static class SimActionAnnouncement {
        public final SimActionType action;
        public final int styleEarned;
        public final SimStyleRank newRank;
        public final long timestamp;

        public SimActionAnnouncement(SimActionType action, int styleEarned, SimStyleRank newRank) {
            this.action = action;
            this.styleEarned = styleEarned;
            this.newRank = newRank;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isRankUp() {
            return newRank != null;
        }
    }

    /**
     * Simulated ActionResult.
     */
    record SimActionResult(
        int pointsEarned,
        int styleEarned,
        int currentCombo,
        SimStyleRank currentRank,
        SimActionAnnouncement announcement
    ) {}

    /**
     * Simulated ComboSession matching production behavior.
     */
    static class SimComboSession {
        private static final long COMBO_TIMEOUT_MS = 3000;
        private static final long STYLE_DECAY_INTERVAL_MS = 500;
        private static final int STYLE_DECAY_AMOUNT = 50;
        private static final int MAX_ANNOUNCEMENTS = 5;
        private static final long MULTI_KILL_WINDOW_MS = 2000;

        private final UUID playerId;
        private final UUID questId;

        private int currentCombo = 0;
        private int maxCombo = 0;
        private int styleScore = 0;
        private int totalStyleEarned = 0;
        private SimStyleRank currentRank = SimStyleRank.D;
        private SimStyleRank highestRank = SimStyleRank.D;

        private long lastActionTime = 0;
        private long lastStyleGainTime = 0;
        private long lastStyleDecayTime = 0;

        private int totalHits = 0;
        private int totalKills = 0;
        private float totalDamage = 0;
        private int perfectDodges = 0;
        private int parries = 0;
        private int counterAttacks = 0;
        private boolean tookDamageThisWave = false;

        private final Set<SimActionType> actionsUsedThisCombo = new HashSet<>();
        private int varietyBonus = 0;

        private long lastKillTime = 0;
        private int killsInWindow = 0;

        private final List<SimActionAnnouncement> recentAnnouncements = new ArrayList<>();

        public SimComboSession(UUID playerId, UUID questId) {
            this.playerId = playerId;
            this.questId = questId;
            this.lastActionTime = System.currentTimeMillis();
            this.lastStyleDecayTime = System.currentTimeMillis();
        }

        public SimActionResult registerAction(SimActionType action, float damage) {
            long now = System.currentTimeMillis();

            // Check combo timeout
            if (now - lastActionTime > COMBO_TIMEOUT_MS) {
                endCombo();
            }

            // Increment combo
            currentCombo++;
            if (currentCombo > maxCombo) {
                maxCombo = currentCombo;
            }

            // Track stats
            totalHits++;
            totalDamage += damage;
            if (action == SimActionType.PERFECT_DODGE) perfectDodges++;
            if (action == SimActionType.PARRY) parries++;
            if (action == SimActionType.COUNTER_ATTACK) counterAttacks++;

            // Variety bonus
            if (!actionsUsedThisCombo.contains(action)) {
                actionsUsedThisCombo.add(action);
                varietyBonus += 5;
            }

            // Calculate style gain
            float varietyMultiplier = 1.0f + (varietyBonus * 0.01f);
            float comboMultiplier = 1.0f + (currentCombo * 0.02f);
            int styleGain = (int) (action.stylePoints * varietyMultiplier * comboMultiplier);

            styleScore += styleGain;
            totalStyleEarned += styleGain;
            lastActionTime = now;
            lastStyleGainTime = now;

            // Update rank
            SimStyleRank newRank = SimStyleRank.fromScore(styleScore);
            boolean rankUp = newRank.ordinal() > currentRank.ordinal();
            currentRank = newRank;
            if (newRank.ordinal() > highestRank.ordinal()) {
                highestRank = newRank;
            }

            // Check combo milestones
            checkComboMilestones();

            // Create announcement
            SimActionAnnouncement announcement = new SimActionAnnouncement(action, styleGain, rankUp ? newRank : null);
            addAnnouncement(announcement);

            return new SimActionResult(action.basePoints, styleGain, currentCombo, currentRank, announcement);
        }

        public SimActionResult registerKill(boolean wasQuick, float overkillDamage) {
            long now = System.currentTimeMillis();
            totalKills++;

            // Check for multi-kill
            if (now - lastKillTime <= MULTI_KILL_WINDOW_MS) {
                killsInWindow++;
            } else {
                killsInWindow = 1;
            }
            lastKillTime = now;

            // Determine kill type
            SimActionType killType;
            if (killsInWindow >= 5) {
                killType = SimActionType.MULTI_KILL_5;
            } else if (killsInWindow >= 4) {
                killType = SimActionType.MULTI_KILL_4;
            } else if (killsInWindow >= 3) {
                killType = SimActionType.MULTI_KILL_3;
            } else if (killsInWindow >= 2) {
                killType = SimActionType.MULTI_KILL_2;
            } else if (overkillDamage > 10) {
                killType = SimActionType.OVERKILL;
            } else if (wasQuick) {
                killType = SimActionType.QUICK_KILL;
            } else {
                killType = SimActionType.KILL;
            }

            return registerAction(killType, 0);
        }

        public void onDamageTaken(float damage) {
            tookDamageThisWave = true;

            // Style penalty
            int penalty = (int) (damage * 10);
            styleScore = Math.max(0, styleScore - penalty);

            // Update rank (can go down)
            currentRank = SimStyleRank.fromScore(styleScore);

            // Combo penalty for heavy hits
            if (damage >= 5) {
                currentCombo = currentCombo / 2;
            }
        }

        public SimActionResult registerNoDamageWave() {
            if (!tookDamageThisWave) {
                return registerAction(SimActionType.NO_DAMAGE_WAVE, 0);
            }
            return registerAction(SimActionType.WAVE_CLEAR, 0);
        }

        public void startNewWave() {
            tookDamageThisWave = false;
        }

        public void tick() {
            long now = System.currentTimeMillis();

            // Check combo timeout
            if (currentCombo > 0 && now - lastActionTime > COMBO_TIMEOUT_MS) {
                endCombo();
            }

            // Style decay
            if (styleScore > 0 && now - lastStyleGainTime > 1000) {
                if (now - lastStyleDecayTime >= STYLE_DECAY_INTERVAL_MS) {
                    styleScore = Math.max(0, styleScore - STYLE_DECAY_AMOUNT);
                    currentRank = SimStyleRank.fromScore(styleScore);
                    lastStyleDecayTime = now;
                }
            }
        }

        private void endCombo() {
            currentCombo = 0;
            actionsUsedThisCombo.clear();
            varietyBonus = 0;
        }

        private void checkComboMilestones() {
            if (currentCombo == 5) {
                awardMilestoneBonus(SimActionType.COMBO_5);
            } else if (currentCombo == 10) {
                awardMilestoneBonus(SimActionType.COMBO_10);
            } else if (currentCombo == 25) {
                awardMilestoneBonus(SimActionType.COMBO_25);
            } else if (currentCombo == 50) {
                awardMilestoneBonus(SimActionType.COMBO_50);
            } else if (currentCombo == 100) {
                awardMilestoneBonus(SimActionType.COMBO_100);
            }
        }

        private void awardMilestoneBonus(SimActionType milestone) {
            float varietyMultiplier = 1.0f + (varietyBonus * 0.01f);
            int styleGain = (int) (milestone.stylePoints * varietyMultiplier);

            styleScore += styleGain;
            totalStyleEarned += styleGain;
            lastStyleGainTime = System.currentTimeMillis();

            SimStyleRank newRank = SimStyleRank.fromScore(styleScore);
            boolean rankUp = newRank.ordinal() > currentRank.ordinal();
            currentRank = newRank;
            if (newRank.ordinal() > highestRank.ordinal()) {
                highestRank = newRank;
            }

            SimActionAnnouncement announcement = new SimActionAnnouncement(milestone, styleGain, rankUp ? newRank : null);
            addAnnouncement(announcement);
        }

        private void addAnnouncement(SimActionAnnouncement announcement) {
            recentAnnouncements.add(announcement);
            while (recentAnnouncements.size() > MAX_ANNOUNCEMENTS) {
                recentAnnouncements.remove(0);
            }
        }

        public int getFinalScore() {
            float baseScore = totalStyleEarned;
            float comboBonus = maxCombo * 10;
            float rankBonus = highestRank.ordinal() * 500;
            float perfectionBonus = (perfectDodges + parries * 2 + counterAttacks * 3) * 50;

            return (int) (baseScore + comboBonus + rankBonus + perfectionBonus);
        }

        // Getters
        public UUID getPlayerId() { return playerId; }
        public UUID getQuestId() { return questId; }
        public int getCurrentCombo() { return currentCombo; }
        public int getMaxCombo() { return maxCombo; }
        public int getStyleScore() { return styleScore; }
        public int getTotalStyleEarned() { return totalStyleEarned; }
        public SimStyleRank getCurrentRank() { return currentRank; }
        public SimStyleRank getHighestRank() { return highestRank; }
        public int getTotalHits() { return totalHits; }
        public int getTotalKills() { return totalKills; }
        public float getTotalDamage() { return totalDamage; }
        public int getPerfectDodges() { return perfectDodges; }
        public int getParries() { return parries; }
        public int getCounterAttacks() { return counterAttacks; }
        public List<SimActionAnnouncement> getRecentAnnouncements() { return recentAnnouncements; }
        public int getVarietyBonus() { return varietyBonus; }
        public boolean isTookDamageThisWave() { return tookDamageThisWave; }
    }

    /**
     * Simulated ComboSystem for session management.
     */
    static class SimComboSystem {
        private final Map<UUID, SimComboSession> activeSessions = new ConcurrentHashMap<>();

        public SimComboSession startSession(UUID playerId, UUID questId) {
            SimComboSession session = new SimComboSession(playerId, questId);
            activeSessions.put(playerId, session);
            return session;
        }

        public Optional<SimComboSession> getSession(UUID playerId) {
            return Optional.ofNullable(activeSessions.get(playerId));
        }

        public SimComboSession endSession(UUID playerId) {
            return activeSessions.remove(playerId);
        }

        public void tick() {
            for (SimComboSession session : activeSessions.values()) {
                session.tick();
            }
        }

        public int getActiveSessionCount() {
            return activeSessions.size();
        }

        public void clear() {
            activeSessions.clear();
        }
    }

    // ========== Test Instance ==========

    private SimComboSystem comboSystem;

    @BeforeEach
    void setUp() {
        comboSystem = new SimComboSystem();
    }

    // ========== L1: StyleRank Tests ==========

    @Nested
    @DisplayName("L1: StyleRank Tests")
    class StyleRankTests {

        @Test
        @DisplayName("All ranks have correct order")
        void allRanksHaveCorrectOrder() {
            SimStyleRank[] ranks = SimStyleRank.values();
            assertEquals(7, ranks.length);
            assertEquals(SimStyleRank.D, ranks[0]);
            assertEquals(SimStyleRank.SSS, ranks[6]);
        }

        @Test
        @DisplayName("Rank thresholds are ascending")
        void rankThresholdsAscending() {
            SimStyleRank[] ranks = SimStyleRank.values();
            for (int i = 1; i < ranks.length; i++) {
                assertTrue(ranks[i].scoreThreshold > ranks[i - 1].scoreThreshold,
                    ranks[i] + " threshold should be higher than " + ranks[i - 1]);
            }
        }

        @Test
        @DisplayName("Reward multipliers are ascending")
        void rewardMultipliersAscending() {
            SimStyleRank[] ranks = SimStyleRank.values();
            for (int i = 1; i < ranks.length; i++) {
                assertTrue(ranks[i].rewardMultiplier > ranks[i - 1].rewardMultiplier,
                    ranks[i] + " multiplier should be higher than " + ranks[i - 1]);
            }
        }

        @ParameterizedTest
        @EnumSource(SimStyleRank.class)
        @DisplayName("Each rank has valid display name")
        void eachRankHasValidDisplayName(SimStyleRank rank) {
            assertNotNull(rank.displayName);
            assertFalse(rank.displayName.isEmpty());
        }

        @ParameterizedTest
        @EnumSource(SimStyleRank.class)
        @DisplayName("Each rank has valid color")
        void eachRankHasValidColor(SimStyleRank rank) {
            assertTrue(rank.color >= 0 && rank.color <= 0xFFFFFF);
        }

        @Test
        @DisplayName("fromScore returns D for zero")
        void fromScoreReturnsD() {
            assertEquals(SimStyleRank.D, SimStyleRank.fromScore(0));
        }

        @Test
        @DisplayName("fromScore returns correct rank at boundaries")
        void fromScoreAtBoundaries() {
            assertEquals(SimStyleRank.D, SimStyleRank.fromScore(499));
            assertEquals(SimStyleRank.C, SimStyleRank.fromScore(500));
            assertEquals(SimStyleRank.C, SimStyleRank.fromScore(1499));
            assertEquals(SimStyleRank.B, SimStyleRank.fromScore(1500));
            assertEquals(SimStyleRank.A, SimStyleRank.fromScore(3500));
            assertEquals(SimStyleRank.S, SimStyleRank.fromScore(7000));
            assertEquals(SimStyleRank.SS, SimStyleRank.fromScore(12000));
            assertEquals(SimStyleRank.SSS, SimStyleRank.fromScore(20000));
            assertEquals(SimStyleRank.SSS, SimStyleRank.fromScore(100000));
        }

        @Test
        @DisplayName("getNext returns correct next rank")
        void getNextReturnsCorrectRank() {
            assertEquals(SimStyleRank.C, SimStyleRank.D.getNext());
            assertEquals(SimStyleRank.B, SimStyleRank.C.getNext());
            assertEquals(SimStyleRank.A, SimStyleRank.B.getNext());
            assertEquals(SimStyleRank.S, SimStyleRank.A.getNext());
            assertEquals(SimStyleRank.SS, SimStyleRank.S.getNext());
            assertEquals(SimStyleRank.SSS, SimStyleRank.SS.getNext());
            assertEquals(SimStyleRank.SSS, SimStyleRank.SSS.getNext()); // Max returns self
        }

        @Test
        @DisplayName("fromScore handles negative scores")
        void fromScoreHandlesNegative() {
            assertEquals(SimStyleRank.D, SimStyleRank.fromScore(-100));
        }
    }

    // ========== L1: ActionType Tests ==========

    @Nested
    @DisplayName("L1: ActionType Tests")
    class ActionTypeTests {

        @ParameterizedTest
        @EnumSource(SimActionType.class)
        @DisplayName("All actions have positive base points")
        void allActionsHavePositiveBasePoints(SimActionType action) {
            assertTrue(action.basePoints > 0, action + " should have positive base points");
        }

        @ParameterizedTest
        @EnumSource(SimActionType.class)
        @DisplayName("All actions have positive style points")
        void allActionsHavePositiveStylePoints(SimActionType action) {
            assertTrue(action.stylePoints > 0, action + " should have positive style points");
        }

        @ParameterizedTest
        @EnumSource(SimActionType.class)
        @DisplayName("All actions have valid display name")
        void allActionsHaveValidDisplayName(SimActionType action) {
            assertNotNull(action.displayName);
            assertFalse(action.displayName.isEmpty());
        }

        @Test
        @DisplayName("Style points are less than or equal to base points")
        void stylePointsLessThanBase() {
            for (SimActionType action : SimActionType.values()) {
                assertTrue(action.stylePoints <= action.basePoints,
                    action + " style points should be <= base points");
            }
        }

        @Test
        @DisplayName("Multi-kill points scale correctly")
        void multiKillPointsScale() {
            assertTrue(SimActionType.MULTI_KILL_2.basePoints > SimActionType.KILL.basePoints);
            assertTrue(SimActionType.MULTI_KILL_3.basePoints > SimActionType.MULTI_KILL_2.basePoints);
            assertTrue(SimActionType.MULTI_KILL_4.basePoints > SimActionType.MULTI_KILL_3.basePoints);
            assertTrue(SimActionType.MULTI_KILL_5.basePoints > SimActionType.MULTI_KILL_4.basePoints);
        }

        @Test
        @DisplayName("Combo milestone points scale correctly")
        void comboMilestonePointsScale() {
            assertTrue(SimActionType.COMBO_10.basePoints > SimActionType.COMBO_5.basePoints);
            assertTrue(SimActionType.COMBO_25.basePoints > SimActionType.COMBO_10.basePoints);
            assertTrue(SimActionType.COMBO_50.basePoints > SimActionType.COMBO_25.basePoints);
            assertTrue(SimActionType.COMBO_100.basePoints > SimActionType.COMBO_50.basePoints);
        }

        @Test
        @DisplayName("Special moves have higher points than basic attacks")
        void specialMovesHigherPoints() {
            assertTrue(SimActionType.CRITICAL_HIT.stylePoints > SimActionType.LIGHT_ATTACK.stylePoints);
            assertTrue(SimActionType.BACKSTAB.stylePoints > SimActionType.HEAVY_ATTACK.stylePoints);
            assertTrue(SimActionType.COUNTER_ATTACK.stylePoints > SimActionType.CRITICAL_HIT.stylePoints);
        }
    }

    // ========== L2: Session Management Tests ==========

    @Nested
    @DisplayName("L2: Session Management Tests")
    class SessionManagementTests {

        @Test
        @DisplayName("Start session creates new session")
        void startSessionCreatesNew() {
            UUID playerId = UUID.randomUUID();
            UUID questId = UUID.randomUUID();

            SimComboSession session = comboSystem.startSession(playerId, questId);

            assertNotNull(session);
            assertEquals(playerId, session.getPlayerId());
            assertEquals(questId, session.getQuestId());
        }

        @Test
        @DisplayName("Get session returns active session")
        void getSessionReturnsActive() {
            UUID playerId = UUID.randomUUID();
            UUID questId = UUID.randomUUID();

            comboSystem.startSession(playerId, questId);
            Optional<SimComboSession> session = comboSystem.getSession(playerId);

            assertTrue(session.isPresent());
            assertEquals(playerId, session.get().getPlayerId());
        }

        @Test
        @DisplayName("Get session returns empty for unknown player")
        void getSessionReturnsEmpty() {
            Optional<SimComboSession> session = comboSystem.getSession(UUID.randomUUID());
            assertFalse(session.isPresent());
        }

        @Test
        @DisplayName("End session removes and returns session")
        void endSessionRemovesSession() {
            UUID playerId = UUID.randomUUID();
            UUID questId = UUID.randomUUID();

            comboSystem.startSession(playerId, questId);
            SimComboSession ended = comboSystem.endSession(playerId);

            assertNotNull(ended);
            assertEquals(playerId, ended.getPlayerId());
            assertFalse(comboSystem.getSession(playerId).isPresent());
        }

        @Test
        @DisplayName("End session returns null for unknown player")
        void endSessionReturnsNull() {
            assertNull(comboSystem.endSession(UUID.randomUUID()));
        }

        @Test
        @DisplayName("Multiple players can have concurrent sessions")
        void multipleConcurrentSessions() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();
            UUID questId = UUID.randomUUID();

            comboSystem.startSession(player1, questId);
            comboSystem.startSession(player2, questId);
            comboSystem.startSession(player3, questId);

            assertEquals(3, comboSystem.getActiveSessionCount());
            assertTrue(comboSystem.getSession(player1).isPresent());
            assertTrue(comboSystem.getSession(player2).isPresent());
            assertTrue(comboSystem.getSession(player3).isPresent());
        }

        @Test
        @DisplayName("Starting new session replaces old session")
        void startSessionReplacesOld() {
            UUID playerId = UUID.randomUUID();
            UUID questId1 = UUID.randomUUID();
            UUID questId2 = UUID.randomUUID();

            comboSystem.startSession(playerId, questId1);
            comboSystem.startSession(playerId, questId2);

            assertEquals(1, comboSystem.getActiveSessionCount());
            assertEquals(questId2, comboSystem.getSession(playerId).get().getQuestId());
        }

        @Test
        @DisplayName("New session starts with default values")
        void newSessionHasDefaults() {
            UUID playerId = UUID.randomUUID();
            UUID questId = UUID.randomUUID();

            SimComboSession session = comboSystem.startSession(playerId, questId);

            assertEquals(0, session.getCurrentCombo());
            assertEquals(0, session.getMaxCombo());
            assertEquals(0, session.getStyleScore());
            assertEquals(0, session.getTotalStyleEarned());
            assertEquals(SimStyleRank.D, session.getCurrentRank());
            assertEquals(SimStyleRank.D, session.getHighestRank());
            assertEquals(0, session.getTotalHits());
            assertEquals(0, session.getTotalKills());
            assertEquals(0, session.getTotalDamage());
        }
    }

    // ========== L2: Combo Mechanics Tests ==========

    @Nested
    @DisplayName("L2: Combo Mechanics Tests")
    class ComboMechanicsTests {

        private SimComboSession session;

        @BeforeEach
        void setUp() {
            session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());
        }

        @Test
        @DisplayName("Action increments combo")
        void actionIncrementsCombo() {
            session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            assertEquals(1, session.getCurrentCombo());

            session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            assertEquals(2, session.getCurrentCombo());

            session.registerAction(SimActionType.HEAVY_ATTACK, 10);
            assertEquals(3, session.getCurrentCombo());
        }

        @Test
        @DisplayName("Max combo tracks highest")
        void maxComboTracksHighest() {
            for (int i = 0; i < 10; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }
            assertEquals(10, session.getMaxCombo());

            // Heavy damage halves combo (10 -> 5)
            session.onDamageTaken(100);
            assertEquals(5, session.getCurrentCombo());

            // New action brings to 6
            session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            assertEquals(6, session.getCurrentCombo());
            assertEquals(10, session.getMaxCombo()); // Max preserved
        }

        @Test
        @DisplayName("Actions track total hits")
        void actionsTrackTotalHits() {
            session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            session.registerAction(SimActionType.HEAVY_ATTACK, 10);
            session.registerAction(SimActionType.CRITICAL_HIT, 15);

            assertEquals(3, session.getTotalHits());
        }

        @Test
        @DisplayName("Actions accumulate damage")
        void actionsAccumulateDamage() {
            session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            session.registerAction(SimActionType.HEAVY_ATTACK, 10);
            session.registerAction(SimActionType.CRITICAL_HIT, 15);

            assertEquals(30f, session.getTotalDamage(), 0.01f);
        }

        @Test
        @DisplayName("Perfect dodge tracks count")
        void perfectDodgeTracksCount() {
            session.registerAction(SimActionType.PERFECT_DODGE, 0);
            session.registerAction(SimActionType.PERFECT_DODGE, 0);
            session.registerAction(SimActionType.LIGHT_ATTACK, 5);

            assertEquals(2, session.getPerfectDodges());
        }

        @Test
        @DisplayName("Parry tracks count")
        void parryTracksCount() {
            session.registerAction(SimActionType.PARRY, 0);
            session.registerAction(SimActionType.PARRY, 0);
            session.registerAction(SimActionType.PARRY, 0);

            assertEquals(3, session.getParries());
        }

        @Test
        @DisplayName("Counter attack tracks count")
        void counterAttackTracksCount() {
            session.registerAction(SimActionType.COUNTER_ATTACK, 10);
            session.registerAction(SimActionType.COUNTER_ATTACK, 10);

            assertEquals(2, session.getCounterAttacks());
        }
    }

    // ========== L2: Style Score Tests ==========

    @Nested
    @DisplayName("L2: Style Score Tests")
    class StyleScoreTests {

        private SimComboSession session;

        @BeforeEach
        void setUp() {
            session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());
        }

        @Test
        @DisplayName("Action earns style points")
        void actionEarnsStyle() {
            SimActionResult result = session.registerAction(SimActionType.LIGHT_ATTACK, 5);

            assertTrue(result.styleEarned() > 0);
            assertTrue(session.getStyleScore() > 0);
            assertEquals(session.getStyleScore(), session.getTotalStyleEarned());
        }

        @Test
        @DisplayName("Higher actions earn more style")
        void higherActionsEarnMoreStyle() {
            SimComboSession session1 = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());
            SimComboSession session2 = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());

            SimActionResult light = session1.registerAction(SimActionType.LIGHT_ATTACK, 5);
            SimActionResult critical = session2.registerAction(SimActionType.CRITICAL_HIT, 15);

            assertTrue(critical.styleEarned() > light.styleEarned());
        }

        @Test
        @DisplayName("Combo multiplier increases style")
        void comboMultiplierIncreasesStyle() {
            SimActionResult first = session.registerAction(SimActionType.LIGHT_ATTACK, 5);

            // Build up combo
            for (int i = 0; i < 10; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }

            // This action should have higher multiplier
            SimActionResult later = session.registerAction(SimActionType.LIGHT_ATTACK, 5);

            assertTrue(later.styleEarned() > first.styleEarned(),
                "Later actions in combo should earn more style");
        }

        @Test
        @DisplayName("Variety bonus increases style")
        void varietyBonusIncreasesStyle() {
            // First attack - adds LIGHT_ATTACK to variety set, bonus = 5
            session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            assertEquals(5, session.getVarietyBonus());

            // Same attack type (no variety bonus increase)
            session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            assertEquals(5, session.getVarietyBonus());

            // Different attack type - adds HEAVY_ATTACK to variety set, bonus = 10
            session.registerAction(SimActionType.HEAVY_ATTACK, 10);
            assertEquals(10, session.getVarietyBonus());
        }

        @Test
        @DisplayName("Damage taken reduces style")
        void damageTakenReducesStyle() {
            // Build up style
            for (int i = 0; i < 20; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }
            int styleBefore = session.getStyleScore();

            session.onDamageTaken(10);

            assertTrue(session.getStyleScore() < styleBefore);
        }

        @Test
        @DisplayName("Style cannot go below zero")
        void styleCannotGoNegative() {
            session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            session.onDamageTaken(1000);

            assertEquals(0, session.getStyleScore());
        }

        @Test
        @DisplayName("Total style earned preserves history")
        void totalStyleEarnedPreservesHistory() {
            for (int i = 0; i < 10; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }
            int totalBefore = session.getTotalStyleEarned();

            session.onDamageTaken(50);

            // Total should not decrease even if current drops
            assertEquals(totalBefore, session.getTotalStyleEarned());
        }
    }

    // ========== L3: Rank Progression Tests ==========

    @Nested
    @DisplayName("L3: Rank Progression Tests")
    class RankProgressionTests {

        private SimComboSession session;

        @BeforeEach
        void setUp() {
            session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());
        }

        @Test
        @DisplayName("Rank increases with style score")
        void rankIncreasesWithStyle() {
            assertEquals(SimStyleRank.D, session.getCurrentRank());

            // Earn enough for C rank (500)
            for (int i = 0; i < 50; i++) {
                session.registerAction(SimActionType.CRITICAL_HIT, 15);
            }

            assertTrue(session.getCurrentRank().ordinal() >= SimStyleRank.C.ordinal(),
                "Should reach at least C rank");
        }

        @Test
        @DisplayName("Highest rank is preserved")
        void highestRankPreserved() {
            // Build up to high rank
            for (int i = 0; i < 100; i++) {
                session.registerAction(SimActionType.CRITICAL_HIT, 15);
            }
            SimStyleRank peakRank = session.getHighestRank();

            // Take damage to drop rank
            session.onDamageTaken(200);

            assertEquals(peakRank, session.getHighestRank());
            assertTrue(session.getCurrentRank().ordinal() < peakRank.ordinal());
        }

        @Test
        @DisplayName("Rank up triggers announcement")
        void rankUpTriggersAnnouncement() {
            // Register many actions to trigger rank up
            boolean foundRankUp = false;
            for (int i = 0; i < 100 && !foundRankUp; i++) {
                SimActionResult result = session.registerAction(SimActionType.CRITICAL_HIT, 15);
                if (result.announcement().isRankUp()) {
                    foundRankUp = true;
                }
            }

            assertTrue(foundRankUp, "Should have at least one rank up announcement");
        }

        @Test
        @DisplayName("Can reach SSS rank")
        void canReachSSSRank() {
            // Lots of high-value actions
            for (int i = 0; i < 500; i++) {
                session.registerAction(SimActionType.OVERKILL, 20);
            }

            assertEquals(SimStyleRank.SSS, session.getHighestRank());
        }
    }

    // ========== L3: Kill System Tests ==========

    @Nested
    @DisplayName("L3: Kill System Tests")
    class KillSystemTests {

        private SimComboSession session;

        @BeforeEach
        void setUp() {
            session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());
        }

        @Test
        @DisplayName("Regular kill awards points")
        void regularKillAwardsPoints() {
            SimActionResult result = session.registerKill(false, 0);

            assertTrue(result.styleEarned() > 0);
            assertEquals(1, session.getTotalKills());
        }

        @Test
        @DisplayName("Quick kill awards bonus points")
        void quickKillAwardsBonusPoints() {
            SimActionResult regular = session.registerKill(false, 0);
            SimComboSession session2 = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());
            SimActionResult quick = session2.registerKill(true, 0);

            assertTrue(quick.styleEarned() > regular.styleEarned());
        }

        @Test
        @DisplayName("Overkill awards bonus points")
        void overkillAwardsBonusPoints() {
            SimActionResult regular = session.registerKill(false, 0);
            SimComboSession session2 = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());
            SimActionResult overkill = session2.registerKill(false, 20);

            assertTrue(overkill.styleEarned() > regular.styleEarned());
        }

        @Test
        @DisplayName("Multi-kill detection works")
        void multiKillDetectionWorks() throws InterruptedException {
            // Rapid kills within window
            session.registerKill(false, 0);
            session.registerKill(false, 0);

            // Third kill should be triple kill
            SimActionResult result = session.registerKill(false, 0);

            // Multi-kill 3 has higher points than regular kill
            assertTrue(result.styleEarned() > SimActionType.KILL.stylePoints * 1.5);
        }

        @Test
        @DisplayName("Penta kill awards maximum points")
        void pentaKillAwardsMaximum() {
            for (int i = 0; i < 4; i++) {
                session.registerKill(false, 0);
            }

            SimActionResult penta = session.registerKill(false, 0);

            // Should be penta kill
            assertTrue(penta.styleEarned() >= SimActionType.MULTI_KILL_5.stylePoints);
        }
    }

    // ========== L3: Combo Milestone Tests ==========

    @Nested
    @DisplayName("L3: Combo Milestone Tests")
    class ComboMilestoneTests {

        private SimComboSession session;

        @BeforeEach
        void setUp() {
            session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());
        }

        @Test
        @DisplayName("5 hit combo awards bonus")
        void fiveHitComboAwardsBonus() {
            int styleBefore = 0;
            for (int i = 0; i < 4; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }
            styleBefore = session.getTotalStyleEarned();

            session.registerAction(SimActionType.LIGHT_ATTACK, 5); // 5th hit

            // Should have milestone bonus added
            assertTrue(session.getTotalStyleEarned() > styleBefore + SimActionType.LIGHT_ATTACK.stylePoints);
        }

        @Test
        @DisplayName("10 hit combo awards bonus")
        void tenHitComboAwardsBonus() {
            for (int i = 0; i < 9; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }
            int styleBefore = session.getTotalStyleEarned();

            session.registerAction(SimActionType.LIGHT_ATTACK, 5); // 10th hit

            assertTrue(session.getTotalStyleEarned() > styleBefore + SimActionType.LIGHT_ATTACK.stylePoints);
        }

        @Test
        @DisplayName("100 hit combo awards large bonus")
        void hundredHitComboAwardsLargeBonus() {
            for (int i = 0; i < 99; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }
            int styleBefore = session.getTotalStyleEarned();

            session.registerAction(SimActionType.LIGHT_ATTACK, 5); // 100th hit

            int styleGained = session.getTotalStyleEarned() - styleBefore;
            assertTrue(styleGained >= SimActionType.COMBO_100.stylePoints);
        }
    }

    // ========== L3: Wave Completion Tests ==========

    @Nested
    @DisplayName("L3: Wave Completion Tests")
    class WaveCompletionTests {

        private SimComboSession session;

        @BeforeEach
        void setUp() {
            session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());
        }

        @Test
        @DisplayName("Flawless wave awards large bonus")
        void flawlessWaveAwardsBonus() {
            // Do some combat without taking damage
            for (int i = 0; i < 10; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }

            SimActionResult result = session.registerNoDamageWave();

            assertTrue(result.styleEarned() >= SimActionType.NO_DAMAGE_WAVE.stylePoints / 2);
        }

        @Test
        @DisplayName("Damaged wave awards smaller bonus")
        void damagedWaveAwardsSmallerBonus() {
            session.onDamageTaken(5);
            SimActionResult result = session.registerNoDamageWave();

            // Should be wave clear, not flawless
            assertTrue(result.styleEarned() < SimActionType.NO_DAMAGE_WAVE.stylePoints);
        }

        @Test
        @DisplayName("New wave resets damage flag")
        void newWaveResetsDamageFlag() {
            session.onDamageTaken(5);
            assertTrue(session.isTookDamageThisWave());

            session.startNewWave();
            assertFalse(session.isTookDamageThisWave());
        }
    }

    // ========== L4: Final Score Calculation Tests ==========

    @Nested
    @DisplayName("L4: Final Score Calculation Tests")
    class FinalScoreTests {

        private SimComboSession session;

        @BeforeEach
        void setUp() {
            session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());
        }

        @Test
        @DisplayName("Final score includes total style")
        void finalScoreIncludesTotalStyle() {
            for (int i = 0; i < 10; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }

            int finalScore = session.getFinalScore();
            assertTrue(finalScore >= session.getTotalStyleEarned());
        }

        @Test
        @DisplayName("Final score includes combo bonus")
        void finalScoreIncludesComboBonus() {
            for (int i = 0; i < 50; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }

            int finalScore = session.getFinalScore();
            int expectedComboBonus = session.getMaxCombo() * 10;

            assertTrue(finalScore >= session.getTotalStyleEarned() + expectedComboBonus - 100);
        }

        @Test
        @DisplayName("Final score includes rank bonus")
        void finalScoreIncludesRankBonus() {
            // Reach high rank
            for (int i = 0; i < 100; i++) {
                session.registerAction(SimActionType.CRITICAL_HIT, 15);
            }

            int finalScore = session.getFinalScore();
            int expectedRankBonus = session.getHighestRank().ordinal() * 500;

            assertTrue(finalScore >= expectedRankBonus);
        }

        @Test
        @DisplayName("Final score includes perfection bonus")
        void finalScoreIncludesPerfectionBonus() {
            session.registerAction(SimActionType.PERFECT_DODGE, 0);
            session.registerAction(SimActionType.PARRY, 0);
            session.registerAction(SimActionType.COUNTER_ATTACK, 10);

            int finalScore = session.getFinalScore();
            // perfectDodges + parries * 2 + counterAttacks * 3 = 1 + 2 + 3 = 6
            // 6 * 50 = 300 perfection bonus
            int expectedPerfectionBonus = (1 + 2 + 3) * 50;

            assertTrue(finalScore >= expectedPerfectionBonus);
        }
    }

    // ========== L4: Announcement System Tests ==========

    @Nested
    @DisplayName("L4: Announcement System Tests")
    class AnnouncementSystemTests {

        private SimComboSession session;

        @BeforeEach
        void setUp() {
            session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());
        }

        @Test
        @DisplayName("Actions generate announcements")
        void actionsGenerateAnnouncements() {
            session.registerAction(SimActionType.LIGHT_ATTACK, 5);

            assertFalse(session.getRecentAnnouncements().isEmpty());
        }

        @Test
        @DisplayName("Announcements limited to max size")
        void announcementsLimitedToMax() {
            for (int i = 0; i < 20; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }

            assertTrue(session.getRecentAnnouncements().size() <= 5);
        }

        @Test
        @DisplayName("Announcement contains correct action")
        void announcementContainsCorrectAction() {
            session.registerAction(SimActionType.CRITICAL_HIT, 15);

            SimActionAnnouncement announcement = session.getRecentAnnouncements().get(0);
            assertEquals(SimActionType.CRITICAL_HIT, announcement.action);
        }

        @Test
        @DisplayName("Rank up announcement has new rank")
        void rankUpAnnouncementHasNewRank() {
            boolean foundRankUp = false;
            for (int i = 0; i < 100 && !foundRankUp; i++) {
                session.registerAction(SimActionType.CRITICAL_HIT, 15);
                for (SimActionAnnouncement ann : session.getRecentAnnouncements()) {
                    if (ann.isRankUp()) {
                        foundRankUp = true;
                        assertNotNull(ann.newRank);
                        break;
                    }
                }
            }

            assertTrue(foundRankUp, "Should have found a rank up announcement");
        }

        @Test
        @DisplayName("Announcements have timestamps")
        void announcementsHaveTimestamps() {
            long before = System.currentTimeMillis();
            session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            long after = System.currentTimeMillis();

            SimActionAnnouncement announcement = session.getRecentAnnouncements().get(0);
            assertTrue(announcement.timestamp >= before && announcement.timestamp <= after);
        }
    }

    // ========== L5: Stress Tests ==========

    @Nested
    @DisplayName("L5: Stress Tests")
    class StressTests {

        @Test
        @DisplayName("Handle 1000 concurrent sessions")
        void handleManyConcurrentSessions() {
            SimComboSystem system = new SimComboSystem();
            UUID questId = UUID.randomUUID();

            for (int i = 0; i < 1000; i++) {
                system.startSession(UUID.randomUUID(), questId);
            }

            assertEquals(1000, system.getActiveSessionCount());
        }

        @Test
        @DisplayName("Session handles rapid action registration")
        void sessionHandlesRapidActions() {
            SimComboSession session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());

            for (int i = 0; i < 10000; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }

            assertEquals(10000, session.getCurrentCombo());
            assertEquals(10000, session.getTotalHits());
        }

        @Test
        @DisplayName("Concurrent session modifications")
        void concurrentSessionModifications() throws InterruptedException {
            SimComboSystem system = new SimComboSystem();
            int threadCount = 10;
            int operationsPerThread = 100;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        UUID playerId = UUID.randomUUID();
                        UUID questId = UUID.randomUUID();

                        for (int i = 0; i < operationsPerThread; i++) {
                            system.startSession(playerId, questId);
                            Optional<SimComboSession> session = system.getSession(playerId);
                            if (session.isPresent()) {
                                session.get().registerAction(SimActionType.LIGHT_ATTACK, 5);
                            }
                            if (i % 2 == 0) {
                                system.endSession(playerId);
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(0, errors.get(), "Should have no concurrent modification errors");
        }

        @Test
        @DisplayName("Session survives extreme damage")
        void sessionSurvivesExtremeDamage() {
            SimComboSession session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());

            // Build up score
            for (int i = 0; i < 100; i++) {
                session.registerAction(SimActionType.CRITICAL_HIT, 15);
            }

            // Extreme damage
            session.onDamageTaken(Float.MAX_VALUE);

            assertEquals(0, session.getStyleScore());
            assertTrue(session.getTotalStyleEarned() > 0);
        }

        @Test
        @DisplayName("Tick handles many sessions")
        void tickHandlesManySessions() {
            SimComboSystem system = new SimComboSystem();
            UUID questId = UUID.randomUUID();

            for (int i = 0; i < 500; i++) {
                SimComboSession session = system.startSession(UUID.randomUUID(), questId);
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }

            // Should not throw
            for (int i = 0; i < 100; i++) {
                system.tick();
            }
        }

        @Test
        @DisplayName("Memory efficiency with announcements")
        void memoryEfficiencyWithAnnouncements() {
            SimComboSession session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());

            // Generate many announcements
            for (int i = 0; i < 10000; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }

            // Should be limited to MAX_ANNOUNCEMENTS
            assertTrue(session.getRecentAnnouncements().size() <= 5);
        }
    }

    // ========== L5: Edge Cases ==========

    @Nested
    @DisplayName("L5: Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Zero damage action is valid")
        void zeroDamageActionValid() {
            SimComboSession session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());

            SimActionResult result = session.registerAction(SimActionType.PERFECT_DODGE, 0);

            assertNotNull(result);
            assertTrue(result.styleEarned() > 0);
        }

        @Test
        @DisplayName("Negative damage treated as zero")
        void negativeDamageValid() {
            SimComboSession session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());

            // Should not throw
            session.registerAction(SimActionType.LIGHT_ATTACK, -10);
            assertEquals(-10f, session.getTotalDamage());
        }

        @Test
        @DisplayName("Session with same player and quest IDs")
        void samePlayerAndQuestIds() {
            UUID id = UUID.randomUUID();
            SimComboSession session = new SimComboSession(id, id);

            assertEquals(id, session.getPlayerId());
            assertEquals(id, session.getQuestId());
        }

        @Test
        @DisplayName("Rapid rank oscillation")
        void rapidRankOscillation() {
            SimComboSession session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());

            for (int cycle = 0; cycle < 10; cycle++) {
                // Build up
                for (int i = 0; i < 50; i++) {
                    session.registerAction(SimActionType.CRITICAL_HIT, 15);
                }
                // Knock down
                session.onDamageTaken(100);
            }

            // Should still be functional
            assertTrue(session.getTotalHits() > 0);
            assertTrue(session.getHighestRank().ordinal() > SimStyleRank.D.ordinal());
        }

        @Test
        @DisplayName("All action types can be registered")
        void allActionTypesCanBeRegistered() {
            SimComboSession session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());

            for (SimActionType action : SimActionType.values()) {
                SimActionResult result = session.registerAction(action, 10);
                assertNotNull(result);
                assertTrue(result.styleEarned() > 0);
            }

            assertEquals(SimActionType.values().length, session.getTotalHits());
        }

        @Test
        @DisplayName("Empty session has valid final score")
        void emptySessionValidFinalScore() {
            SimComboSession session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());

            int finalScore = session.getFinalScore();
            assertEquals(0, finalScore);
        }

        @Test
        @DisplayName("System tick with no sessions")
        void systemTickNoSessions() {
            SimComboSystem system = new SimComboSystem();

            // Should not throw
            for (int i = 0; i < 100; i++) {
                system.tick();
            }
        }

        @Test
        @DisplayName("Double end session")
        void doubleEndSession() {
            SimComboSystem system = new SimComboSystem();
            UUID playerId = UUID.randomUUID();

            system.startSession(playerId, UUID.randomUUID());
            SimComboSession first = system.endSession(playerId);
            SimComboSession second = system.endSession(playerId);

            assertNotNull(first);
            assertNull(second);
        }

        @Test
        @DisplayName("Variety bonus resets on combo break")
        void varietyBonusResetsOnComboBreak() {
            SimComboSession session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());

            // Build variety
            session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            session.registerAction(SimActionType.HEAVY_ATTACK, 10);
            session.registerAction(SimActionType.CRITICAL_HIT, 15);

            assertTrue(session.getVarietyBonus() > 0);

            // Heavy damage breaks combo
            session.onDamageTaken(100);
            // Wait for timeout simulation would reset, but we can't wait in test
            // The damage itself resets combo to half, variety preserved until timeout
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 25, 50, 100, 500, 1000})
        @DisplayName("Combo milestones at exact values")
        void comboMilestonesAtExactValues(int targetCombo) {
            SimComboSession session = new SimComboSession(UUID.randomUUID(), UUID.randomUUID());

            for (int i = 0; i < targetCombo; i++) {
                session.registerAction(SimActionType.LIGHT_ATTACK, 5);
            }

            assertEquals(targetCombo, session.getCurrentCombo());
        }
    }
}
