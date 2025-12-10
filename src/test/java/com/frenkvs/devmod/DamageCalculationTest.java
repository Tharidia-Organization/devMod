package com.frenkvs.devmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the damage calculation system.
 * Tests body part multipliers, armor penetration, and base damage calculations.
 */
public class DamageCalculationTest {

    /**
     * Mock WeaponStats matching the mod's WeaponStats class
     */
    static class MockWeaponStats {
        public float baseDamageBonus = 0f;
        public float headMult = 2.0f;
        public float bodyMult = 1.0f;
        public float armsMult = 0.75f;
        public float legsMult = 0.5f;
        public float armorPenetration = 0f;

        public static MockWeaponStats defaultStats() {
            return new MockWeaponStats();
        }

        public static MockWeaponStats withBonus(float bonus) {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = bonus;
            return stats;
        }

        public static MockWeaponStats withArmorPen(float pen) {
            MockWeaponStats stats = new MockWeaponStats();
            stats.armorPenetration = pen;
            return stats;
        }
    }

    /**
     * Body parts enum matching HitHelper.BodyPart
     */
    enum BodyPart {
        HEAD, BODY, ARMS, LEGS
    }

    private MockWeaponStats defaultStats;

    @BeforeEach
    void setUp() {
        defaultStats = MockWeaponStats.defaultStats();
    }

    // ============================================
    // Body Part Multiplier Tests
    // ============================================

    @Nested
    @DisplayName("Body Part Multiplier Tests")
    class BodyPartMultiplierTests {

        @Test
        @DisplayName("Head shot should have 2.0x multiplier")
        void testHeadMultiplier() {
            assertEquals(2.0f, defaultStats.headMult, 0.001f);
        }

        @Test
        @DisplayName("Body shot should have 1.0x multiplier")
        void testBodyMultiplier() {
            assertEquals(1.0f, defaultStats.bodyMult, 0.001f);
        }

        @Test
        @DisplayName("Arms shot should have 0.75x multiplier")
        void testArmsMultiplier() {
            assertEquals(0.75f, defaultStats.armsMult, 0.001f);
        }

        @Test
        @DisplayName("Legs shot should have 0.5x multiplier")
        void testLegsMultiplier() {
            assertEquals(0.5f, defaultStats.legsMult, 0.001f);
        }

        @Test
        @DisplayName("Head multiplier should be highest")
        void testHeadIsHighestMultiplier() {
            assertTrue(defaultStats.headMult > defaultStats.bodyMult);
            assertTrue(defaultStats.headMult > defaultStats.armsMult);
            assertTrue(defaultStats.headMult > defaultStats.legsMult);
        }

        @Test
        @DisplayName("Multiplier hierarchy: HEAD > BODY > ARMS > LEGS")
        void testMultiplierHierarchy() {
            assertTrue(defaultStats.headMult > defaultStats.bodyMult);
            assertTrue(defaultStats.bodyMult > defaultStats.armsMult);
            assertTrue(defaultStats.armsMult > defaultStats.legsMult);
        }
    }

    // ============================================
    // Damage Calculation Tests
    // ============================================

    @Nested
    @DisplayName("Damage Calculation Tests")
    class DamageCalculationTests {

        /**
         * Simulates the damage calculation from DamageHandler.onDamage()
         * newDamage = (originalDamage + baseDamageBonus) * multiplier
         */
        float calculateDamage(float originalDamage, MockWeaponStats stats, BodyPart part) {
            float multiplier = switch (part) {
                case HEAD -> stats.headMult;
                case BODY -> stats.bodyMult;
                case ARMS -> stats.armsMult;
                case LEGS -> stats.legsMult;
            };
            return (originalDamage + stats.baseDamageBonus) * multiplier;
        }

        @Test
        @DisplayName("Base damage 10 with no bonus to head = 20")
        void testBaseDamageToHead() {
            float result = calculateDamage(10f, defaultStats, BodyPart.HEAD);
            assertEquals(20f, result, 0.001f);
        }

        @Test
        @DisplayName("Base damage 10 with no bonus to body = 10")
        void testBaseDamageToBody() {
            float result = calculateDamage(10f, defaultStats, BodyPart.BODY);
            assertEquals(10f, result, 0.001f);
        }

        @Test
        @DisplayName("Base damage 10 with no bonus to arms = 7.5")
        void testBaseDamageToArms() {
            float result = calculateDamage(10f, defaultStats, BodyPart.ARMS);
            assertEquals(7.5f, result, 0.001f);
        }

        @Test
        @DisplayName("Base damage 10 with no bonus to legs = 5")
        void testBaseDamageToLegs() {
            float result = calculateDamage(10f, defaultStats, BodyPart.LEGS);
            assertEquals(5f, result, 0.001f);
        }

        @ParameterizedTest
        @CsvSource({
            "5.0, 0.0, HEAD, 10.0",
            "5.0, 0.0, BODY, 5.0",
            "5.0, 0.0, ARMS, 3.75",
            "5.0, 0.0, LEGS, 2.5",
            "10.0, 5.0, HEAD, 30.0",  // (10 + 5) * 2.0 = 30
            "10.0, 5.0, BODY, 15.0",  // (10 + 5) * 1.0 = 15
            "20.0, 10.0, HEAD, 60.0", // (20 + 10) * 2.0 = 60
        })
        @DisplayName("Parameterized damage calculations")
        void testParameterizedDamageCalculations(float baseDamage, float bonus, BodyPart part, float expected) {
            MockWeaponStats stats = MockWeaponStats.withBonus(bonus);
            float result = calculateDamage(baseDamage, stats, part);
            assertEquals(expected, result, 0.01f);
        }

        @Test
        @DisplayName("Zero damage should remain zero")
        void testZeroDamage() {
            float result = calculateDamage(0f, defaultStats, BodyPart.HEAD);
            assertEquals(0f, result, 0.001f);
        }

        @Test
        @DisplayName("Very large damage should calculate correctly")
        void testLargeDamage() {
            float result = calculateDamage(1000f, defaultStats, BodyPart.HEAD);
            assertEquals(2000f, result, 0.001f);
        }

        @Test
        @DisplayName("Fractional damage should calculate correctly")
        void testFractionalDamage() {
            float result = calculateDamage(7.5f, defaultStats, BodyPart.BODY);
            assertEquals(7.5f, result, 0.001f);
        }
    }

    // ============================================
    // Base Damage Bonus Tests
    // ============================================

    @Nested
    @DisplayName("Base Damage Bonus Tests")
    class BaseDamageBonusTests {

        float calculateDamage(float originalDamage, MockWeaponStats stats, BodyPart part) {
            float multiplier = switch (part) {
                case HEAD -> stats.headMult;
                case BODY -> stats.bodyMult;
                case ARMS -> stats.armsMult;
                case LEGS -> stats.legsMult;
            };
            return (originalDamage + stats.baseDamageBonus) * multiplier;
        }

        @Test
        @DisplayName("Bonus damage adds before multiplier")
        void testBonusDamageAddsBeforeMultiplier() {
            MockWeaponStats stats = MockWeaponStats.withBonus(5f);
            // (10 + 5) * 2.0 = 30
            float result = calculateDamage(10f, stats, BodyPart.HEAD);
            assertEquals(30f, result, 0.001f);
        }

        @Test
        @DisplayName("Negative bonus should reduce damage")
        void testNegativeBonusDamage() {
            MockWeaponStats stats = MockWeaponStats.withBonus(-3f);
            // (10 - 3) * 1.0 = 7
            float result = calculateDamage(10f, stats, BodyPart.BODY);
            assertEquals(7f, result, 0.001f);
        }

        @Test
        @DisplayName("Large bonus scales with multiplier")
        void testLargeBonusScalesWithMultiplier() {
            MockWeaponStats stats = MockWeaponStats.withBonus(100f);
            // (10 + 100) * 2.0 = 220
            float result = calculateDamage(10f, stats, BodyPart.HEAD);
            assertEquals(220f, result, 0.001f);
        }
    }

    // ============================================
    // Armor Penetration Tests
    // ============================================

    @Nested
    @DisplayName("Armor Penetration Tests")
    class ArmorPenetrationTests {

        /**
         * Simulates armor penetration calculation from DamageHandler
         * If ArmorPen > 0, add bonus based on victim's armor value
         * ignoredArmor = armorVal * armorPenetration
         * newDamage += (ignoredArmor * 0.5f)
         */
        float calculateDamageWithArmorPen(float baseDamage, MockWeaponStats stats,
                                          BodyPart part, float victimArmorValue) {
            float multiplier = switch (part) {
                case HEAD -> stats.headMult;
                case BODY -> stats.bodyMult;
                case ARMS -> stats.armsMult;
                case LEGS -> stats.legsMult;
            };

            float newDamage = (baseDamage + stats.baseDamageBonus) * multiplier;

            if (stats.armorPenetration > 0) {
                float ignoredArmor = victimArmorValue * stats.armorPenetration;
                newDamage += (ignoredArmor * 0.5f);
            }

            return newDamage;
        }

        @Test
        @DisplayName("No armor pen should not add extra damage")
        void testNoArmorPen() {
            MockWeaponStats stats = MockWeaponStats.withArmorPen(0f);
            float result = calculateDamageWithArmorPen(10f, stats, BodyPart.BODY, 20f);
            assertEquals(10f, result, 0.001f);
        }

        @Test
        @DisplayName("50% armor pen on 20 armor adds 5 damage")
        void testFiftyPercentArmorPen() {
            MockWeaponStats stats = MockWeaponStats.withArmorPen(0.5f);
            // Base: 10 * 1.0 = 10
            // Armor pen bonus: 20 * 0.5 * 0.5 = 5
            // Total: 15
            float result = calculateDamageWithArmorPen(10f, stats, BodyPart.BODY, 20f);
            assertEquals(15f, result, 0.001f);
        }

        @Test
        @DisplayName("100% armor pen on 20 armor adds 10 damage")
        void testFullArmorPen() {
            MockWeaponStats stats = MockWeaponStats.withArmorPen(1.0f);
            // Base: 10 * 1.0 = 10
            // Armor pen bonus: 20 * 1.0 * 0.5 = 10
            // Total: 20
            float result = calculateDamageWithArmorPen(10f, stats, BodyPart.BODY, 20f);
            assertEquals(20f, result, 0.001f);
        }

        @Test
        @DisplayName("Armor pen on zero armor adds no damage")
        void testArmorPenOnZeroArmor() {
            MockWeaponStats stats = MockWeaponStats.withArmorPen(0.5f);
            float result = calculateDamageWithArmorPen(10f, stats, BodyPart.BODY, 0f);
            assertEquals(10f, result, 0.001f);
        }

        @Test
        @DisplayName("Armor pen applies after body part multiplier")
        void testArmorPenAppliesAfterMultiplier() {
            MockWeaponStats stats = MockWeaponStats.withArmorPen(0.5f);
            // Head: 10 * 2.0 = 20
            // Armor pen bonus: 20 * 0.5 * 0.5 = 5
            // Total: 25
            float result = calculateDamageWithArmorPen(10f, stats, BodyPart.HEAD, 20f);
            assertEquals(25f, result, 0.001f);
        }

        @ParameterizedTest
        @CsvSource({
            "10.0, 0.25, 20.0, 12.5",  // 10 + (20 * 0.25 * 0.5) = 12.5
            "10.0, 0.5, 10.0, 12.5",   // 10 + (10 * 0.5 * 0.5) = 12.5
            "10.0, 0.75, 20.0, 17.5",  // 10 + (20 * 0.75 * 0.5) = 17.5
            "10.0, 1.0, 40.0, 30.0",   // 10 + (40 * 1.0 * 0.5) = 30
        })
        @DisplayName("Parameterized armor penetration calculations")
        void testParameterizedArmorPen(float baseDamage, float armorPen, float armor, float expected) {
            MockWeaponStats stats = MockWeaponStats.withArmorPen(armorPen);
            float result = calculateDamageWithArmorPen(baseDamage, stats, BodyPart.BODY, armor);
            assertEquals(expected, result, 0.01f);
        }
    }

    // ============================================
    // Custom Weapon Stats Tests
    // ============================================

    @Nested
    @DisplayName("Custom Weapon Stats Tests")
    class CustomWeaponStatsTests {

        @Test
        @DisplayName("Custom multipliers should override defaults")
        void testCustomMultipliers() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.headMult = 3.0f;
            stats.bodyMult = 1.5f;
            stats.armsMult = 1.0f;
            stats.legsMult = 0.75f;

            assertEquals(3.0f, stats.headMult);
            assertEquals(1.5f, stats.bodyMult);
            assertEquals(1.0f, stats.armsMult);
            assertEquals(0.75f, stats.legsMult);
        }

        @Test
        @DisplayName("Combined bonus and custom multiplier")
        void testCombinedBonusAndCustomMultiplier() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 10f;
            stats.headMult = 3.0f;

            // (10 + 10) * 3.0 = 60
            float multiplier = stats.headMult;
            float result = (10f + stats.baseDamageBonus) * multiplier;
            assertEquals(60f, result, 0.001f);
        }

        @Test
        @DisplayName("All stats combined calculation")
        void testAllStatsCombined() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 5f;
            stats.headMult = 2.5f;
            stats.armorPenetration = 0.5f;

            float baseDamage = 10f;
            float victimArmor = 20f;

            // Step 1: Apply bonus and multiplier
            float damage = (baseDamage + stats.baseDamageBonus) * stats.headMult;
            // (10 + 5) * 2.5 = 37.5

            // Step 2: Apply armor pen
            float ignoredArmor = victimArmor * stats.armorPenetration;
            damage += (ignoredArmor * 0.5f);
            // 37.5 + (20 * 0.5 * 0.5) = 37.5 + 5 = 42.5

            assertEquals(42.5f, damage, 0.001f);
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Negative damage should be handled")
        void testNegativeDamage() {
            MockWeaponStats stats = MockWeaponStats.withBonus(-20f);
            float multiplier = stats.bodyMult;
            float result = (10f + stats.baseDamageBonus) * multiplier;
            // (10 - 20) * 1.0 = -10
            assertEquals(-10f, result, 0.001f);
        }

        @Test
        @DisplayName("Very small multiplier")
        void testVerySmallMultiplier() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.legsMult = 0.01f;

            float result = 100f * stats.legsMult;
            assertEquals(1f, result, 0.001f);
        }

        @Test
        @DisplayName("Maximum reasonable values")
        void testMaximumValues() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 1000f;
            stats.headMult = 10.0f;
            stats.armorPenetration = 1.0f;

            float baseDamage = 100f;
            float victimArmor = 100f;

            float damage = (baseDamage + stats.baseDamageBonus) * stats.headMult;
            damage += (victimArmor * stats.armorPenetration * 0.5f);
            // (100 + 1000) * 10 + (100 * 1.0 * 0.5) = 11000 + 50 = 11050

            assertEquals(11050f, damage, 0.001f);
        }

        @Test
        @DisplayName("Float precision maintained")
        void testFloatPrecision() {
            MockWeaponStats stats = new MockWeaponStats();
            stats.baseDamageBonus = 0.333f;
            stats.headMult = 1.777f;

            float result = (1.0f + stats.baseDamageBonus) * stats.headMult;
            // (1.0 + 0.333) * 1.777 ≈ 2.369
            assertTrue(result > 2.36f && result < 2.38f);
        }
    }
}
