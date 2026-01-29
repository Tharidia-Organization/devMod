package com.devmod.endurance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("L1: Difficulty Scaling System Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.CONCURRENT)
public class DifficultyScalingTest {

    // =========================================================================
    // Simulated Scaling Functions (mirrors DifficultyScaler formulas)
    // =========================================================================

    // Constants from DifficultyScaler
    private static final float BOSS_HP_SCALE_PER_PLAYER = 0.3f;
    private static final float BOSS_DMG_SCALE_PER_PLAYER = 0.1f;
    private static final float MOB_HP_SCALE_PER_PLAYER = 0.15f;
    private static final float MAX_MOB_COUNT_SCALE = 5.0f;
    private static final float MAX_BOSS_HP_SCALE = 10.0f;

    /** Simulates DifficultyScaler.scaleMobCount */
    private int simulateScaleMobCount(int baseMobCount, int playerCount, QuestType questType) {
        if (baseMobCount <= 0 || playerCount <= 0) {
            return baseMobCount;
        }
        int clampedPlayers = Math.max(1, Math.min(playerCount, questType.getMaxPlayers()));
        double scaleFactor = Math.sqrt(clampedPlayers) * questType.getDifficultyMultiplier();
        scaleFactor = Math.min(scaleFactor, MAX_MOB_COUNT_SCALE);
        return (int) Math.ceil(baseMobCount * scaleFactor);
    }

    /** Simulates DifficultyScaler.scaleBossHealth */
    private float simulateScaleBossHealth(float baseHP, int playerCount, QuestType questType) {
        if (baseHP <= 0 || playerCount <= 0) {
            return baseHP;
        }
        int clampedPlayers = Math.max(1, Math.min(playerCount, questType.getMaxPlayers()));
        float playerScale = 1.0f + (clampedPlayers - 1) * BOSS_HP_SCALE_PER_PLAYER;
        float totalScale = playerScale * questType.getDifficultyMultiplier();
        totalScale = Math.min(totalScale, MAX_BOSS_HP_SCALE);
        return baseHP * totalScale;
    }

    /** Simulates DifficultyScaler.scaleBossDamage */
    private float simulateScaleBossDamage(float baseDamage, int playerCount, QuestType questType) {
        if (baseDamage <= 0 || playerCount <= 0) {
            return baseDamage;
        }
        int clampedPlayers = Math.max(1, Math.min(playerCount, questType.getMaxPlayers()));
        float damageScale = 1.0f + (clampedPlayers - 1) * BOSS_DMG_SCALE_PER_PLAYER;
        damageScale = Math.min(damageScale, 2.0f);
        return baseDamage * damageScale;
    }

    /** Simulates DifficultyScaler.scaleMobHealth */
    private float simulateScaleMobHealth(float baseHP, int playerCount, QuestType questType) {
        if (baseHP <= 0 || playerCount <= 0) {
            return baseHP;
        }
        int clampedPlayers = Math.max(1, Math.min(playerCount, questType.getMaxPlayers()));
        float playerScale = 1.0f + (clampedPlayers - 1) * MOB_HP_SCALE_PER_PLAYER;
        float totalScale = playerScale * questType.getDifficultyMultiplier();
        totalScale = Math.min(totalScale, 3.0f);
        return baseHP * totalScale;
    }

    /** Simulates DifficultyScaler.getWaveMultiplier */
    private float simulateWaveMultiplier(int waveNumber, int totalWaves) {
        if (waveNumber <= 1) {
            return 1.0f;
        }
        float waveScale = 1.0f + (waveNumber - 1) * 0.05f;
        if (totalWaves == 0 && waveNumber > 10) {
            waveScale += (waveNumber - 10) * 0.03f;
        }
        return Math.min(waveScale, 3.0f);
    }

    // =========================================================================
    // L1-01: MobDifficultyPreset Enum Validation
    // =========================================================================

    @Nested
    @DisplayName("L1-01: MobDifficultyPreset Enum")
    class MobDifficultyPresetTests {

        @Test
        @Order(1)
        @DisplayName("All presets have valid multipliers")
        void allPresetsHaveValidMultipliers() {
            for (EnduranceQuestRegistry.MobDifficultyPreset preset :
                    EnduranceQuestRegistry.MobDifficultyPreset.values()) {
                assertTrue(preset.getCountMultiplier() > 0,
                    preset + " countMultiplier must be positive");
                assertTrue(preset.getHpMultiplier() > 0,
                    preset + " hpMultiplier must be positive");
                assertTrue(preset.getDamageMultiplier() > 0,
                    preset + " damageMultiplier must be positive");
            }
        }

        @Test
        @Order(2)
        @DisplayName("SWARM preset has high count, low HP")
        void swarmPresetCharacteristics() {
            var preset = EnduranceQuestRegistry.MobDifficultyPreset.SWARM;
            assertTrue(preset.getCountMultiplier() > 1.0f, "SWARM should have >1 count multiplier");
            assertTrue(preset.getHpMultiplier() < 1.0f, "SWARM should have <1 HP multiplier");
        }

        @Test
        @Order(3)
        @DisplayName("TANK preset has low count, high HP")
        void tankPresetCharacteristics() {
            var preset = EnduranceQuestRegistry.MobDifficultyPreset.TANK;
            assertTrue(preset.getCountMultiplier() < 1.0f, "TANK should have <1 count multiplier");
            assertTrue(preset.getHpMultiplier() > 1.0f, "TANK should have >1 HP multiplier");
        }

        @Test
        @Order(4)
        @DisplayName("GLASS_CANNON preset has low HP, high damage")
        void glassCannonsPresetCharacteristics() {
            var preset = EnduranceQuestRegistry.MobDifficultyPreset.GLASS_CANNON;
            assertTrue(preset.getHpMultiplier() < 1.0f, "GLASS_CANNON should have <1 HP multiplier");
            assertTrue(preset.getDamageMultiplier() > 1.0f, "GLASS_CANNON should have >1 damage multiplier");
        }

        @Test
        @Order(5)
        @DisplayName("BOSS_STYLE preset has very low count, very high HP")
        void bossStylePresetCharacteristics() {
            var preset = EnduranceQuestRegistry.MobDifficultyPreset.BOSS_STYLE;
            assertTrue(preset.getCountMultiplier() < 0.5f, "BOSS_STYLE should have very low count");
            assertTrue(preset.getHpMultiplier() >= 2.0f, "BOSS_STYLE should have very high HP");
        }

        @Test
        @Order(6)
        @DisplayName("STANDARD preset is neutral (1.0x all)")
        void standardPresetIsNeutral() {
            var preset = EnduranceQuestRegistry.MobDifficultyPreset.STANDARD;
            assertEquals(1.0f, preset.getCountMultiplier(), 0.001f);
            assertEquals(1.0f, preset.getHpMultiplier(), 0.001f);
            assertEquals(1.0f, preset.getDamageMultiplier(), 0.001f);
        }

        @Test
        @Order(7)
        @DisplayName("All presets have display names and descriptions")
        void allPresetsHaveMetadata() {
            for (var preset : EnduranceQuestRegistry.MobDifficultyPreset.values()) {
                assertNotNull(preset.getDisplayName(), preset + " must have displayName");
                assertNotNull(preset.getDescription(), preset + " must have description");
                assertFalse(preset.getDisplayName().isEmpty(), preset + " displayName must not be empty");
                assertFalse(preset.getDescription().isEmpty(), preset + " description must not be empty");
            }
        }

        @Test
        @Order(8)
        @DisplayName("Preset count is exactly 5")
        void presetCountIsFive() {
            assertEquals(5, EnduranceQuestRegistry.MobDifficultyPreset.values().length,
                "Should have exactly 5 difficulty presets");
        }
    }

    // =========================================================================
    // L1-02: Simulated Scaling Core Functions
    // =========================================================================

    @Nested
    @DisplayName("L1-02: Scaling Core (Simulated)")
    class ScalingCoreTests {

        @Test
        @Order(1)
        @DisplayName("Single player returns base values")
        void singlePlayerReturnsBase() {
            float baseHP = 20.0f;
            float scaled = simulateScaleMobHealth(baseHP, 1, QuestType.PVE_COOP);
            assertEquals(baseHP, scaled, 0.01f, "Single player should return base HP");
        }

        @Test
        @Order(2)
        @DisplayName("Zero or negative inputs return base values")
        void zeroInputsReturnBase() {
            float baseHP = 20.0f;
            assertEquals(baseHP, simulateScaleMobHealth(baseHP, 0, QuestType.PVE_COOP));
            assertEquals(baseHP, simulateScaleMobHealth(baseHP, -1, QuestType.PVE_COOP));
            assertEquals(0, simulateScaleMobHealth(0, 4, QuestType.PVE_COOP));
        }
    }

    // =========================================================================
    // L1-03: Mob Count Scaling Formula Verification
    // =========================================================================

    @Nested
    @DisplayName("L1-03: Mob Count Scaling")
    class MobCountScalingTests {

        @Test
        @Order(1)
        @DisplayName("Mob count scales with sqrt(playerCount)")
        void mobCountScalesWithSqrt() {
            int base = 10;
            int scaled1 = simulateScaleMobCount(base, 1, QuestType.PVE_COOP);
            int scaled4 = simulateScaleMobCount(base, 4, QuestType.PVE_COOP);

            assertEquals(base, scaled1, "1 player should give base count");
            assertTrue(scaled4 >= base * 1.9 && scaled4 <= base * 2.1,
                "4 players should give ~2x count (got " + scaled4 + ")");
        }

        @Test
        @Order(2)
        @DisplayName("Mob count respects MAX_MOB_COUNT_SCALE cap")
        void mobCountRespectsCap() {
            int base = 10;
            int scaled = simulateScaleMobCount(base, 20, QuestType.RAID_BOSS);
            assertTrue(scaled <= 50, "Mob count should be capped at 5x (got " + scaled + ")");
        }

        @ParameterizedTest
        @CsvSource({
            "10, 1, PVE_COOP, 10",
            "10, 4, PVE_COOP, 20",
            "10, 9, RAID_BOSS, 45",  // PVE_COOP clamps to 6, use RAID_BOSS for 9 players
            "10, 4, RAID_BOSS, 30",
            "10, 4, EVENT, 40"
        })
        @Order(3)
        @DisplayName("Mob count formula: base * sqrt(players) * questMultiplier")
        void mobCountFormulaVerification(int base, int players, String questTypeName, int expected) {
            QuestType questType = QuestType.valueOf(questTypeName);
            int actual = simulateScaleMobCount(base, players, questType);
            assertEquals(expected, actual, 2,
                "Expected ~" + expected + " for " + base + " base, " + players + " players, " + questTypeName);
        }
    }

    // =========================================================================
    // L1-04: Boss HP Scaling Formula Verification
    // =========================================================================

    @Nested
    @DisplayName("L1-04: Boss HP Scaling")
    class BossHPScalingTests {

        @Test
        @Order(1)
        @DisplayName("Boss HP scales linearly per player (30% per additional)")
        void bossHPScalesLinearly() {
            float base = 100.0f;
            float hp1 = simulateScaleBossHealth(base, 1, QuestType.PVE_COOP);
            float hp2 = simulateScaleBossHealth(base, 2, QuestType.PVE_COOP);
            float hp3 = simulateScaleBossHealth(base, 3, QuestType.PVE_COOP);

            assertEquals(100.0f, hp1, 0.1f, "1 player: base * 1.0");
            assertEquals(130.0f, hp2, 0.1f, "2 players: base * 1.3");
            assertEquals(160.0f, hp3, 0.1f, "3 players: base * 1.6");
        }

        @Test
        @Order(2)
        @DisplayName("Boss HP respects MAX_BOSS_HP_SCALE cap (10x)")
        void bossHPRespectsCap() {
            float base = 100.0f;
            float scaled = simulateScaleBossHealth(base, 20, QuestType.EVENT);
            assertTrue(scaled <= 1000.0f, "Boss HP should be capped at 10x (got " + scaled + ")");
        }

        @ParameterizedTest
        @CsvSource({
            "100, 1, PVE_COOP, 100",
            "100, 4, PVE_COOP, 190",
            "100, 4, RAID_BOSS, 285",
            "100, 4, EVENT, 380"
        })
        @Order(3)
        @DisplayName("Boss HP formula: base * (1 + (players-1)*0.3) * questMultiplier")
        void bossHPFormulaVerification(float base, int players, String questTypeName, float expected) {
            QuestType questType = QuestType.valueOf(questTypeName);
            float actual = simulateScaleBossHealth(base, players, questType);
            assertEquals(expected, actual, 5.0f,
                "Expected ~" + expected + " for " + base + " base, " + players + " players, " + questTypeName);
        }
    }

    // =========================================================================
    // L1-05: Boss Damage Scaling Formula Verification
    // =========================================================================

    @Nested
    @DisplayName("L1-05: Boss Damage Scaling")
    class BossDamageScalingTests {

        @Test
        @Order(1)
        @DisplayName("Boss damage scales modestly (10% per additional player)")
        void bossDamageScalesModestly() {
            float base = 10.0f;
            float dmg1 = simulateScaleBossDamage(base, 1, QuestType.PVE_COOP);
            float dmg4 = simulateScaleBossDamage(base, 4, QuestType.PVE_COOP);

            assertEquals(10.0f, dmg1, 0.1f, "1 player: base * 1.0");
            assertEquals(13.0f, dmg4, 0.1f, "4 players: base * 1.3");
        }

        @Test
        @Order(2)
        @DisplayName("Boss damage capped at 2x")
        void bossDamageCapped() {
            float base = 10.0f;
            float scaled = simulateScaleBossDamage(base, 20, QuestType.PVE_COOP);
            assertTrue(scaled <= 20.0f, "Boss damage should be capped at 2x (got " + scaled + ")");
        }
    }

    // =========================================================================
    // L1-06: Regular Mob HP Scaling
    // =========================================================================

    @Nested
    @DisplayName("L1-06: Regular Mob HP Scaling")
    class MobHPScalingTests {

        @Test
        @Order(1)
        @DisplayName("Mob HP scales at 15% per additional player")
        void mobHPScalesGently() {
            float base = 20.0f;
            float hp1 = simulateScaleMobHealth(base, 1, QuestType.PVE_COOP);
            float hp4 = simulateScaleMobHealth(base, 4, QuestType.PVE_COOP);

            assertEquals(20.0f, hp1, 0.1f, "1 player: base");
            assertEquals(29.0f, hp4, 0.5f, "4 players: base * 1.45");
        }

        @Test
        @Order(2)
        @DisplayName("Mob HP capped at 3x")
        void mobHPCapped() {
            float base = 20.0f;
            float scaled = simulateScaleMobHealth(base, 20, QuestType.EVENT);
            assertTrue(scaled <= 60.0f, "Mob HP should be capped at 3x (got " + scaled + ")");
        }
    }

    // =========================================================================
    // L1-07: Wave Multiplier Scaling
    // =========================================================================

    @Nested
    @DisplayName("L1-07: Wave Multiplier")
    class WaveMultiplierTests {

        @Test
        @Order(1)
        @DisplayName("Wave 1 has 1.0x multiplier")
        void wave1Neutral() {
            assertEquals(1.0f, simulateWaveMultiplier(1, 10), 0.001f);
        }

        @Test
        @Order(2)
        @DisplayName("Each wave adds 5% difficulty")
        void waveProgressiveScaling() {
            float wave5 = simulateWaveMultiplier(5, 10);
            float wave10 = simulateWaveMultiplier(10, 10);

            assertEquals(1.2f, wave5, 0.01f);
            assertEquals(1.45f, wave10, 0.01f);
        }

        @Test
        @Order(3)
        @DisplayName("Endless mode scales more aggressively after wave 10")
        void endlessModeScalesMore() {
            float wave15Endless = simulateWaveMultiplier(15, 0);
            float wave15Normal = simulateWaveMultiplier(15, 20);

            assertTrue(wave15Endless > wave15Normal,
                "Endless mode should scale more aggressively");
        }

        @Test
        @Order(4)
        @DisplayName("Wave multiplier capped at 3x")
        void waveMultiplierCapped() {
            float wave50 = simulateWaveMultiplier(50, 0);
            assertTrue(wave50 <= 3.0f, "Wave multiplier should be capped at 3x (got " + wave50 + ")");
        }
    }

    // =========================================================================
    // L1-08: QuestType Integration
    // =========================================================================

    @Nested
    @DisplayName("L1-08: QuestType Enum")
    class QuestTypeTests {

        @ParameterizedTest
        @EnumSource(QuestType.class)
        @Order(1)
        @DisplayName("All QuestTypes have valid difficulty multipliers")
        void allQuestTypesHaveValidMultipliers(QuestType questType) {
            assertTrue(questType.getDifficultyMultiplier() > 0,
                questType + " must have positive difficulty multiplier");
        }

        @Test
        @Order(2)
        @DisplayName("QuestType multipliers increase: PVE_COOP < RAID_BOSS < EVENT")
        void questTypeMultiplierOrdering() {
            assertTrue(QuestType.PVE_COOP.getDifficultyMultiplier() < QuestType.RAID_BOSS.getDifficultyMultiplier());
            assertTrue(QuestType.RAID_BOSS.getDifficultyMultiplier() < QuestType.EVENT.getDifficultyMultiplier());
        }

        @Test
        @Order(3)
        @DisplayName("QuestType player limits are valid")
        void questTypePlayerLimitsValid() {
            for (QuestType qt : QuestType.values()) {
                assertTrue(qt.getMinPlayers() >= 1, qt + " minPlayers must be >= 1");
                assertTrue(qt.getMaxPlayers() >= qt.getMinPlayers(), qt + " maxPlayers must be >= minPlayers");
                assertTrue(qt.getMaxPlayers() <= 20, qt + " maxPlayers should be reasonable");
            }
        }

        @Test
        @Order(4)
        @DisplayName("QuestType has display info")
        void questTypeHasDisplayInfo() {
            for (QuestType qt : QuestType.values()) {
                assertNotNull(qt.getDisplayName());
                assertNotNull(qt.getDescription());
                assertFalse(qt.getDisplayName().isEmpty());
            }
        }

        @Test
        @Order(5)
        @DisplayName("QuestType.isValidPlayerCount works correctly")
        void questTypeValidPlayerCount() {
            assertTrue(QuestType.PVE_COOP.isValidPlayerCount(2));
            assertTrue(QuestType.PVE_COOP.isValidPlayerCount(6));
            assertFalse(QuestType.PVE_COOP.isValidPlayerCount(1)); // min is 2
            assertFalse(QuestType.PVE_COOP.isValidPlayerCount(7)); // max is 6

            assertTrue(QuestType.RAID_BOSS.isValidPlayerCount(4));
            assertTrue(QuestType.RAID_BOSS.isValidPlayerCount(10));
        }

        @Test
        @Order(6)
        @DisplayName("QuestType.allowsSoloPlay returns correct values")
        void questTypeSoloPlay() {
            assertTrue(QuestType.PVE_COOP.allowsSoloPlay(), "PVE_COOP should allow solo");
            assertFalse(QuestType.RAID_BOSS.allowsSoloPlay(), "RAID_BOSS should not allow solo");
            assertFalse(QuestType.EVENT.allowsSoloPlay(), "EVENT should not allow solo");
        }

        @Test
        @Order(7)
        @DisplayName("QuestType.fromOrdinal handles invalid ordinals")
        void questTypeFromOrdinal() {
            assertEquals(QuestType.PVE_COOP, QuestType.fromOrdinal(0));
            assertEquals(QuestType.RAID_BOSS, QuestType.fromOrdinal(1));
            assertEquals(QuestType.EVENT, QuestType.fromOrdinal(2));
            assertEquals(QuestType.PVE_COOP, QuestType.fromOrdinal(-1), "Invalid ordinal should fallback");
            assertEquals(QuestType.PVE_COOP, QuestType.fromOrdinal(99), "Invalid ordinal should fallback");
        }

        @Test
        @Order(8)
        @DisplayName("QuestType.fromName handles invalid names")
        void questTypeFromName() {
            assertEquals(QuestType.PVE_COOP, QuestType.fromName("PVE_COOP"));
            assertEquals(QuestType.PVE_COOP, QuestType.fromName("pve_coop")); // case insensitive
            assertEquals(QuestType.PVE_COOP, QuestType.fromName(null), "Null name should fallback");
            assertEquals(QuestType.PVE_COOP, QuestType.fromName(""), "Empty name should fallback");
            assertEquals(QuestType.PVE_COOP, QuestType.fromName("INVALID"), "Invalid name should fallback");
        }
    }

    // =========================================================================
    // L1-09: Combined Scaling
    // =========================================================================

    @Nested
    @DisplayName("L1-09: Combined Scaling")
    class CombinedScalingTests {

        @Test
        @Order(1)
        @DisplayName("Total scale factor combines player, quest, and wave")
        void totalScaleFactorCombinesAll() {
            // sqrt(4) * 1.0 * 1.2 (wave 5) = 2 * 1 * 1.2 = 2.4
            float playerScale = (float) Math.sqrt(4);
            float questScale = QuestType.PVE_COOP.getDifficultyMultiplier();
            float waveScale = simulateWaveMultiplier(5, 10);
            float total = playerScale * questScale * waveScale;

            assertEquals(2.4f, total, 0.1f);
        }

        @Test
        @Order(2)
        @DisplayName("Extreme conditions give high scale")
        void extremeScaling() {
            // 10 players, EVENT (2.0x), wave 10 in endless
            float playerScale = (float) Math.sqrt(10);
            float questScale = QuestType.EVENT.getDifficultyMultiplier();
            float waveScale = simulateWaveMultiplier(10, 0);
            float total = playerScale * questScale * waveScale;

            assertTrue(total > 5.0f, "Extreme conditions should give high scale (got " + total + ")");
        }
    }

    // =========================================================================
    // L1-10: Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("L1-10: Edge Cases")
    class EdgeCaseTests {

        @Test
        @Order(1)
        @DisplayName("Player count clamped to quest type limits")
        void playerCountClampedToLimits() {
            // PVE_COOP max is 6, but pass 100 players
            int scaled = simulateScaleMobCount(10, 100, QuestType.PVE_COOP);
            int expectedMax = simulateScaleMobCount(10, 6, QuestType.PVE_COOP);
            assertEquals(expectedMax, scaled, "Should clamp to quest type max players");
        }

        @Test
        @Order(2)
        @DisplayName("Very small base values don't underflow")
        void smallBaseValuesNoUnderflow() {
            float smallHP = simulateScaleMobHealth(0.1f, 4, QuestType.PVE_COOP);
            assertTrue(smallHP > 0, "Small HP should stay positive (got " + smallHP + ")");

            int smallCount = simulateScaleMobCount(1, 4, QuestType.PVE_COOP);
            assertTrue(smallCount >= 1, "Small count should stay at least 1 (got " + smallCount + ")");
        }

        @Test
        @Order(3)
        @DisplayName("Very large base values don't overflow")
        void largeBaseValuesNoOverflow() {
            float largeHP = simulateScaleBossHealth(1000000f, 10, QuestType.EVENT);
            assertTrue(largeHP > 0, "Large HP should stay positive (got " + largeHP + ")");
            assertTrue(Float.isFinite(largeHP), "Result should be finite");
        }
    }

    // =========================================================================
    // L1-11: Consistency and Determinism
    // =========================================================================

    @Nested
    @DisplayName("L1-11: Consistency")
    class ConsistencyTests {

        @Test
        @Order(1)
        @DisplayName("Scaling is deterministic")
        void scalingIsDeterministic() {
            for (int i = 0; i < 10; i++) {
                float hp1 = simulateScaleMobHealth(20, 4, QuestType.PVE_COOP);
                float hp2 = simulateScaleMobHealth(20, 4, QuestType.PVE_COOP);
                assertEquals(hp1, hp2, 0.001f, "Scaling should be deterministic");
            }
        }

        @Test
        @Order(2)
        @DisplayName("Scaling is monotonic")
        void scalingIsMonotonic() {
            float prev = 0;
            for (int players = 1; players <= 6; players++) {
                float hp = simulateScaleMobHealth(20, players, QuestType.PVE_COOP);
                assertTrue(hp >= prev, "HP should increase monotonically with players");
                prev = hp;
            }
        }

        @Test
        @Order(3)
        @DisplayName("QuestType scaling formulas match simulated formulas")
        void questTypeMatchesSimulation() {
            // QuestType.getScaledMobCount should match our simulation
            int qResult = QuestType.PVE_COOP.getScaledMobCount(10, 4);
            int simResult = simulateScaleMobCount(10, 4, QuestType.PVE_COOP);
            assertEquals(simResult, qResult, "QuestType method should match simulation");

            // QuestType.getScaledBossHealth should match
            float qHealth = QuestType.PVE_COOP.getScaledBossHealth(100, 4);
            float simHealth = simulateScaleBossHealth(100, 4, QuestType.PVE_COOP);
            assertEquals(simHealth, qHealth, 0.1f, "QuestType boss health should match simulation");
        }
    }
}
