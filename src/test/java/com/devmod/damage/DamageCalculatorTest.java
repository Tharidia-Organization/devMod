package com.devmod.damage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.devmod.combat.HitHelper;
import com.devmod.stats.WeaponStats;

/**
 * Unit tests for DamageCalculator.
 *
 * Tests the armor penetration formulas, ranged modifiers, body part multipliers, and edge cases.
 *
 * NOTE: applyRangedModifiers uses Math.random() internally for crit rolls, so
 * crit probability tests use critChance=0 (never) and critChance=1.0 (always)
 * to get deterministic outcomes. Methods that depend on Config, DamageSource,
 * Player, or SoulImprintManager are not tested here because they require a
 * bootstrapped Minecraft environment. The pure-math methods are tested directly.
 */
class DamageCalculatorTest {

    // ========================================================================
    // CalculationResult record
    // ========================================================================

    @Nested
    @DisplayName("CalculationResult")
    class CalculationResultTests {

        @Test
        @DisplayName("environmental() should return identity result with given damage")
        void environmentalReturnsIdentityResult() {
            DamageCalculator.CalculationResult result =
                    DamageCalculator.CalculationResult.environmental(25.0f);

            assertEquals(25.0f, result.finalDamage(), 0.001f);
            assertEquals(0f, result.armorPenBonus(), 0.001f);
            assertEquals(0f, result.trueDamagePortion(), 0.001f);
            assertEquals(0f, result.armorReduction(), 0.001f);
            assertEquals(1.0f, result.bodyPartMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("environmental() with zero damage")
        void environmentalZeroDamage() {
            DamageCalculator.CalculationResult result =
                    DamageCalculator.CalculationResult.environmental(0f);

            assertEquals(0f, result.finalDamage(), 0.001f);
        }

        @Test
        @DisplayName("environmental() with very large damage")
        void environmentalLargeDamage() {
            DamageCalculator.CalculationResult result =
                    DamageCalculator.CalculationResult.environmental(999999f);

            assertEquals(999999f, result.finalDamage(), 0.1f);
        }

        @Test
        @DisplayName("record accessor values match constructor args")
        void recordAccessorsMatchConstructor() {
            DamageCalculator.CalculationResult result =
                    new DamageCalculator.CalculationResult(50f, 5f, 10f, 0.3f, 1.5f);

            assertEquals(50f, result.finalDamage(), 0.001f);
            assertEquals(5f, result.armorPenBonus(), 0.001f);
            assertEquals(10f, result.trueDamagePortion(), 0.001f);
            assertEquals(0.3f, result.armorReduction(), 0.001f);
            assertEquals(1.5f, result.bodyPartMultiplier(), 0.001f);
        }
    }

    // ========================================================================
    // applyRangedModifiers
    // ========================================================================

    @Nested
    @DisplayName("applyRangedModifiers")
    class ApplyRangedModifiersTests {

        private DamageCalculator.CalculationResult baseResult(float damage) {
            return new DamageCalculator.CalculationResult(damage, 0f, 0f, 0f, 1.0f);
        }

        @Test
        @DisplayName("No speed multiplier or crit should return base damage")
        void noModifiersReturnBaseDamage() {
            float result = DamageCalculator.applyRangedModifiers(
                    baseResult(10f), 1.0f, 0f, 1.5f);

            assertEquals(10f, result, 0.001f);
        }

        @Test
        @DisplayName("Speed multiplier scales damage")
        void speedMultiplierScalesDamage() {
            float result = DamageCalculator.applyRangedModifiers(
                    baseResult(10f), 2.0f, 0f, 1.5f);

            assertEquals(20f, result, 0.001f);
        }

        @Test
        @DisplayName("Speed multiplier of 0.5 halves damage")
        void halfSpeedMultiplier() {
            float result = DamageCalculator.applyRangedModifiers(
                    baseResult(10f), 0.5f, 0f, 1.5f);

            assertEquals(5f, result, 0.001f);
        }

        @Test
        @DisplayName("Zero speed multiplier is skipped (no multiplication)")
        void zeroSpeedMultiplier() {
            float result = DamageCalculator.applyRangedModifiers(
                    baseResult(10f), 0f, 0f, 1.5f);

            // speedMultiplier is 0, so the condition `speedMultiplier > 0` is false
            // damage stays at 10f (no multiplication applied)
            assertEquals(10f, result, 0.001f);
        }

        @Test
        @DisplayName("Negative speed multiplier is skipped (no multiplication)")
        void negativeSpeedMultiplierSkipped() {
            float result = DamageCalculator.applyRangedModifiers(
                    baseResult(10f), -1.0f, 0f, 1.5f);

            // Negative speedMultiplier fails the > 0 check, so damage is unchanged
            assertEquals(10f, result, 0.001f);
        }

        // --- Deterministic crit tests (boundary values) ---

        @Test
        @DisplayName("100% crit chance always crits")
        void fullCritChanceAlwaysCrits() {
            // Math.random() returns [0.0, 1.0), so it's always < 1.0
            float result = DamageCalculator.applyRangedModifiers(
                    baseResult(10f), 1.0f, 1.0f, 3.0f);

            // 10 * 3.0 = 30
            assertEquals(30f, result, 0.001f);
        }

        @Test
        @DisplayName("Zero crit chance never crits")
        void zeroCritChanceNeverCrits() {
            float result = DamageCalculator.applyRangedModifiers(
                    baseResult(10f), 1.0f, 0f, 2.0f);

            assertEquals(10f, result, 0.001f);
        }

        @Test
        @DisplayName("Crit with speed multiplier applies both when critChance=1.0")
        void critWithSpeedMultiplierAppliesBoth() {
            float result = DamageCalculator.applyRangedModifiers(
                    baseResult(20f), 1.5f, 1.0f, 2.5f);

            // 20 * 1.5 (speed) = 30, then 30 * 2.5 (crit) = 75
            assertEquals(75f, result, 0.001f);
        }

        @Test
        @DisplayName("Negative crit chance never crits")
        void negativeCritChanceNeverCrits() {
            float result = DamageCalculator.applyRangedModifiers(
                    baseResult(10f), 1.0f, -0.5f, 2.0f);

            // critChance <= 0 fails the > 0 check
            assertEquals(10f, result, 0.001f);
        }
    }

    // ========================================================================
    // calculateArmorPenBonus - Config fallback path (SIMPLE formula)
    // ========================================================================

    @Nested
    @DisplayName("calculateArmorPenBonus (Config fallback path)")
    class ArmorPenBonusTests {

        // NOTE: Config is not bootstrapped in test, so the try/catch in
        // calculateArmorPenBonus falls back to SIMPLE formula with
        // multiplier=0.5, flatBonus=2.0

        @Test
        @DisplayName("Simple formula: armorPen * armorValue * multiplier(0.5)")
        void simpleFormulaBasic() {
            // ignoredArmor = 20 * 0.5 = 10, bonus = 10 * 0.5 = 5.0
            float bonus = DamageCalculator.calculateArmorPenBonus(0.5f, 20f, 10f);
            assertEquals(5.0f, bonus, 0.001f);
        }

        @Test
        @DisplayName("Full armor pen (1.0) on 20 armor = 10 bonus")
        void fullArmorPen() {
            // ignoredArmor = 20 * 1.0 = 20, bonus = 20 * 0.5 = 10
            float bonus = DamageCalculator.calculateArmorPenBonus(1.0f, 20f, 10f);
            assertEquals(10.0f, bonus, 0.001f);
        }

        @Test
        @DisplayName("Zero armor pen returns zero bonus")
        void zeroArmorPen() {
            float bonus = DamageCalculator.calculateArmorPenBonus(0f, 20f, 10f);
            assertEquals(0f, bonus, 0.001f);
        }

        @Test
        @DisplayName("Zero armor value returns zero bonus")
        void zeroArmorValue() {
            float bonus = DamageCalculator.calculateArmorPenBonus(0.5f, 0f, 10f);
            assertEquals(0f, bonus, 0.001f);
        }

        @Test
        @DisplayName("Both zero returns zero bonus")
        void bothZero() {
            float bonus = DamageCalculator.calculateArmorPenBonus(0f, 0f, 0f);
            assertEquals(0f, bonus, 0.001f);
        }

        @ParameterizedTest
        @CsvSource({
            // armorPen, armorValue, baseDamage, expected (SIMPLE: armorPen*armorValue*0.5)
            "0.25, 20.0, 10.0, 2.5",   // 20*0.25=5, 5*0.5=2.5
            "0.50, 10.0, 10.0, 2.5",   // 10*0.5=5, 5*0.5=2.5
            "0.75, 20.0, 10.0, 7.5",   // 20*0.75=15, 15*0.5=7.5
            "1.00, 40.0, 10.0, 20.0",  // 40*1.0=40, 40*0.5=20
            "0.10, 100.0, 10.0, 5.0",  // 100*0.1=10, 10*0.5=5
        })
        @DisplayName("Parameterized armor pen bonus (SIMPLE fallback)")
        void parameterizedArmorPenBonus(float armorPen, float armorValue,
                                         float baseDamage, float expected) {
            float bonus = DamageCalculator.calculateArmorPenBonus(armorPen, armorValue, baseDamage);
            assertEquals(expected, bonus, 0.01f);
        }

        @Test
        @DisplayName("Armor pen bonus scales linearly with armor value")
        void linearScalingWithArmor() {
            float bonus10 = DamageCalculator.calculateArmorPenBonus(0.5f, 10f, 10f);
            float bonus20 = DamageCalculator.calculateArmorPenBonus(0.5f, 20f, 10f);
            float bonus40 = DamageCalculator.calculateArmorPenBonus(0.5f, 40f, 10f);

            assertEquals(bonus10 * 2, bonus20, 0.001f);
            assertEquals(bonus10 * 4, bonus40, 0.001f);
        }

        @Test
        @DisplayName("Armor pen bonus scales linearly with penetration percentage")
        void linearScalingWithPenPercent() {
            float bonus25 = DamageCalculator.calculateArmorPenBonus(0.25f, 20f, 10f);
            float bonus50 = DamageCalculator.calculateArmorPenBonus(0.50f, 20f, 10f);
            float bonus100 = DamageCalculator.calculateArmorPenBonus(1.00f, 20f, 10f);

            assertEquals(bonus25 * 2, bonus50, 0.001f);
            assertEquals(bonus25 * 4, bonus100, 0.001f);
        }
    }

    // ========================================================================
    // getBodyPartMultiplier
    // ========================================================================

    @Nested
    @DisplayName("getBodyPartMultiplier")
    class BodyPartMultiplierTests {

        @Test
        @DisplayName("HEAD returns headMult from WeaponStats")
        void headReturnsHeadMult() {
            WeaponStats stats = new WeaponStats();
            stats.setHeadMult(2.5f);

            float mult = DamageCalculator.getBodyPartMultiplier(stats, HitHelper.BodyPart.HEAD);
            assertEquals(2.5f, mult, 0.001f);
        }

        @Test
        @DisplayName("BODY returns bodyMult from WeaponStats")
        void bodyReturnsBodyMult() {
            WeaponStats stats = new WeaponStats();
            stats.setBodyMult(1.0f);

            float mult = DamageCalculator.getBodyPartMultiplier(stats, HitHelper.BodyPart.BODY);
            assertEquals(1.0f, mult, 0.001f);
        }

        @Test
        @DisplayName("ARMS returns armsMult from WeaponStats")
        void armsReturnsArmsMult() {
            WeaponStats stats = new WeaponStats();
            stats.setArmsMult(0.8f);

            float mult = DamageCalculator.getBodyPartMultiplier(stats, HitHelper.BodyPart.ARMS);
            assertEquals(0.8f, mult, 0.001f);
        }

        @Test
        @DisplayName("LEGS returns legsMult from WeaponStats")
        void legsReturnsLegsMult() {
            WeaponStats stats = new WeaponStats();
            stats.setLegsMult(0.6f);

            float mult = DamageCalculator.getBodyPartMultiplier(stats, HitHelper.BodyPart.LEGS);
            assertEquals(0.6f, mult, 0.001f);
        }

        @Test
        @DisplayName("Null stats returns 1.0")
        void nullStatsReturnsOne() {
            float mult = DamageCalculator.getBodyPartMultiplier(null, HitHelper.BodyPart.HEAD);
            assertEquals(1.0f, mult, 0.001f);
        }

        @Test
        @DisplayName("Null bodyPart returns 1.0")
        void nullBodyPartReturnsOne() {
            WeaponStats stats = new WeaponStats();
            float mult = DamageCalculator.getBodyPartMultiplier(stats, null);
            assertEquals(1.0f, mult, 0.001f);
        }

        @Test
        @DisplayName("Both null returns 1.0")
        void bothNullReturnsOne() {
            float mult = DamageCalculator.getBodyPartMultiplier(null, null);
            assertEquals(1.0f, mult, 0.001f);
        }

        @Test
        @DisplayName("Custom multipliers are respected for all body parts")
        void customMultipliersRespected() {
            WeaponStats stats = new WeaponStats();
            stats.setHeadMult(3.0f);
            stats.setBodyMult(1.5f);
            stats.setArmsMult(1.0f);
            stats.setLegsMult(0.5f);

            assertEquals(3.0f, DamageCalculator.getBodyPartMultiplier(stats, HitHelper.BodyPart.HEAD), 0.001f);
            assertEquals(1.5f, DamageCalculator.getBodyPartMultiplier(stats, HitHelper.BodyPart.BODY), 0.001f);
            assertEquals(1.0f, DamageCalculator.getBodyPartMultiplier(stats, HitHelper.BodyPart.ARMS), 0.001f);
            assertEquals(0.5f, DamageCalculator.getBodyPartMultiplier(stats, HitHelper.BodyPart.LEGS), 0.001f);
        }
    }

    // ========================================================================
    // Edge cases
    // ========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("applyRangedModifiers with zero base damage")
        void rangedModifiersZeroBaseDamage() {
            DamageCalculator.CalculationResult result =
                    new DamageCalculator.CalculationResult(0f, 0f, 0f, 0f, 1.0f);

            float damage = DamageCalculator.applyRangedModifiers(
                    result, 2.0f, 1.0f, 3.0f);

            // 0 * 2.0 * 3.0 = 0
            assertEquals(0f, damage, 0.001f);
        }

        @Test
        @DisplayName("applyRangedModifiers with very large damage and guaranteed crit")
        void rangedModifiersVeryLargeDamage() {
            DamageCalculator.CalculationResult result =
                    new DamageCalculator.CalculationResult(100000f, 0f, 0f, 0f, 1.0f);

            float damage = DamageCalculator.applyRangedModifiers(
                    result, 1.0f, 1.0f, 2.0f);

            assertEquals(200000f, damage, 0.1f);
        }

        @Test
        @DisplayName("applyRangedModifiers with critDamage multiplier of 1.0 (no bonus)")
        void critDamageMultiplierOne() {
            DamageCalculator.CalculationResult result =
                    new DamageCalculator.CalculationResult(10f, 0f, 0f, 0f, 1.0f);

            // critChance=1.0 guarantees crit, critDamage=1.0 means no bonus
            float damage = DamageCalculator.applyRangedModifiers(
                    result, 1.0f, 1.0f, 1.0f);

            // 10 * 1.0 (speed) * 1.0 (crit multiplier) = 10
            assertEquals(10f, damage, 0.001f);
        }

        @Test
        @DisplayName("Armor pen bonus with very high armor value")
        void armorPenHighArmor() {
            float bonus = DamageCalculator.calculateArmorPenBonus(1.0f, 1000f, 10f);
            // SIMPLE: 1000 * 1.0 = 1000, 1000 * 0.5 = 500
            assertEquals(500f, bonus, 0.001f);
        }

        @Test
        @DisplayName("Armor pen bonus with fractional pen value")
        void armorPenFractionalPen() {
            float bonus = DamageCalculator.calculateArmorPenBonus(0.333f, 30f, 10f);
            // SIMPLE: 30 * 0.333 = 9.99, 9.99 * 0.5 = 4.995
            assertEquals(4.995f, bonus, 0.01f);
        }

        @ParameterizedTest
        @ValueSource(floats = {0f, 0.001f, 0.5f, 1.0f, 10f, 100f, Float.MAX_VALUE / 2})
        @DisplayName("environmental() handles various damage values without exception")
        void environmentalHandlesVariousDamages(float damage) {
            assertDoesNotThrow(() -> {
                DamageCalculator.CalculationResult result =
                        DamageCalculator.CalculationResult.environmental(damage);
                assertEquals(damage, result.finalDamage(), damage * 0.001f + 0.001f);
            });
        }
    }

    // ========================================================================
    // DamageBreakdown (network-sync constructor)
    // ========================================================================

    @Nested
    @DisplayName("DamageBreakdown (network-sync constructor)")
    class DamageBreakdownNetworkSyncTests {

        @Test
        @DisplayName("Network-sync constructor stores values correctly")
        void networkSyncConstructorStoresValues() {
            DamageBreakdown bd = new DamageBreakdown(
                    10f, 3f, 2f, 1.5f, 5f, 30f);

            assertEquals(10f, bd.getBaseWeaponDamage(), 0.001f);
            assertEquals(3f, bd.getTotalEnchantBonus(), 0.001f);
            assertEquals(2f, bd.getPehkuiSizeBonus(), 0.001f);
            assertEquals(1.5f, bd.getBodyPartMultiplier(), 0.001f);
            assertEquals(5f, bd.getArmorPenetrationBonus(), 0.001f);
            assertEquals(30f, bd.getFinalDamage(), 0.001f);
        }

        @Test
        @DisplayName("Zero enchant total does not add enchant bonus entry")
        void zeroEnchantTotalNoEntry() {
            DamageBreakdown bd = new DamageBreakdown(
                    10f, 0f, 0f, 1.0f, 0f, 10f);

            assertTrue(bd.getEnchantBonuses().isEmpty());
        }

        @Test
        @DisplayName("Positive enchant total adds combined entry")
        void positiveEnchantTotalAddsEntry() {
            DamageBreakdown bd = new DamageBreakdown(
                    10f, 5f, 0f, 1.0f, 0f, 15f);

            assertEquals(1, bd.getEnchantBonuses().size());
            assertEquals("Enchants", bd.getEnchantBonuses().get(0).name());
            assertEquals(5f, bd.getEnchantBonuses().get(0).bonus(), 0.001f);
        }

        @Test
        @DisplayName("Pehkui scale is derived from bonus")
        void pehkuiScaleDerivedFromBonus() {
            // pehkuiBonus = baseDmg * 0.25 * (scale - 1.0)
            // 5.0 = 10.0 * 0.25 * (scale - 1.0)
            // scale - 1.0 = 5.0 / 2.5 = 2.0
            // scale = 3.0
            DamageBreakdown bd = new DamageBreakdown(
                    10f, 0f, 5f, 1.0f, 0f, 15f);

            assertEquals(3.0f, bd.getPehkuiScale(), 0.001f);
        }

        @Test
        @DisplayName("No pehkui bonus gives scale of 1.0")
        void noPehkuiBonusScaleIsOne() {
            DamageBreakdown bd = new DamageBreakdown(
                    10f, 0f, 0f, 1.0f, 0f, 10f);

            assertEquals(1.0f, bd.getPehkuiScale(), 0.001f);
        }

        @Test
        @DisplayName("Formula string is generated and non-empty")
        void formulaStringGenerated() {
            DamageBreakdown bd = new DamageBreakdown(
                    12f, 3f, 0f, 0.9f, 2f, 15.5f);

            String formula = bd.getFormulaString();
            assertNotNull(formula);
            assertFalse(formula.isEmpty(), "Formula string should not be empty");
            // Formula contains parentheses, multiplication, and equals sign
            assertTrue(formula.contains("("), "Formula should start with open paren");
            assertTrue(formula.contains("*"), "Formula should contain multiplier operator");
            assertTrue(formula.contains("="), "Formula should contain equals sign");
        }

        @Test
        @DisplayName("Compact string is generated correctly")
        void compactStringGenerated() {
            DamageBreakdown bd = new DamageBreakdown(
                    12f, 3f, 0f, 0.9f, 2f, 15.5f);

            String compact = bd.toCompactString();
            assertNotNull(compact);
            assertFalse(compact.isEmpty());
            assertTrue(compact.contains("Base:"));
            assertTrue(compact.contains("Ench:"));
        }

        @Test
        @DisplayName("All-zero values produce valid breakdown")
        void allZeroValuesValid() {
            DamageBreakdown bd = new DamageBreakdown(
                    0f, 0f, 0f, 1.0f, 0f, 0f);

            assertEquals(0f, bd.getFinalDamage(), 0.001f);
            assertNotNull(bd.getFormulaString());
            assertNotNull(bd.toCompactString());
        }
    }
}
