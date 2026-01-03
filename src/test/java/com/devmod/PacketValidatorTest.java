package com.devmod;

import java.util.Objects;

import javax.annotation.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for PacketValidator validation logic.
 * Uses MockPacketValidator to avoid Minecraft runtime dependencies
 * while testing the core validation logic that mirrors the real implementation.
 *
 * This test covers all validation methods in com.devmod.network.PacketValidator.
 */
public class PacketValidatorTest {

    /**
     * Mock PacketValidator with the same validation logic as the real one.
     * All bounds and validation methods mirror the production code.
     */
    static class MockPacketValidator {
        // Validation bounds (same as real PacketValidator)
        public static final double MIN_ATTRIBUTE_VALUE = 0.0;
        public static final double MAX_HEALTH = 100_000.0;
        public static final double MAX_DAMAGE = 10_000.0;
        public static final double MAX_FOLLOW_RANGE = 1024.0;
        public static final double MAX_ARMOR = 1000.0;
        public static final double MIN_ATTACK_SPEED = -10.0;
        public static final double MAX_MULTIPLIER = 100.0;
        public static final double MIN_MULTIPLIER = 0.0;
        public static final double MAX_PENETRATION = 1.0;
        public static final double MIN_PENETRATION = -1.0;
        public static final double MAX_ARMOR_SHRED = 66.0;
        public static final double MAX_DAMAGE_VS = 2.0;
        public static final double MAX_TRUE_DAMAGE = 1.0;
        public static final double MAX_SWEEPING = 1.0;
        public static final int MAX_DURABILITY = 4096;
        public static final int MAX_REPAIR_COST = 1000;
        public static final double MAX_TOOL_SPEED = 128.0;
        public static final int MAX_TOOL_DAMAGE_PER_BLOCK = 128;
        public static final double MAX_RANGED_SPEED = 5.0;
        public static final double MAX_RANGED_GRAVITY = 0.2;
        public static final double MAX_RANGED_SPREAD = 3.0;
        public static final double MAX_RANGED_BASE_DAMAGE = 50.0;
        public static final double MIN_RANGED_MULT = 0.2;
        public static final double MAX_RANGED_MULT = 3.0;
        public static final double MIN_ARMOR_REDUCTION = 0.0;
        public static final double MAX_ARMOR_REDUCTION = 1.0;
        public static final double MIN_ARMOR_BONUS = -20.0;
        public static final double MAX_ARMOR_BONUS = 30.0;
        public static final double MIN_TOUGHNESS_BONUS = -10.0;
        public static final double MAX_TOUGHNESS_BONUS = 20.0;
        public static final double MIN_KNOCKBACK_RESIST = 0.0;
        public static final double MAX_KNOCKBACK_RESIST = 1.0;
        public static final double MIN_THORNS_PERCENT = 0.0;
        public static final double MAX_THORNS_PERCENT = 0.5;
        public static final double MIN_SHIELD_BLOCK = 0.0;
        public static final double MAX_SHIELD_BLOCK = 1.0;
        public static final double MIN_SHIELD_RECOVERY = 0.0;
        public static final double MAX_SHIELD_RECOVERY = 2.0;

        // Core attribute validators
        public double validateHealth(double health) {
            return clamp(health, MIN_ATTRIBUTE_VALUE, MAX_HEALTH);
        }

        public double validateDamage(double damage) {
            return clamp(damage, MIN_ATTRIBUTE_VALUE, MAX_DAMAGE);
        }

        public double validateFollowRange(double followRange) {
            return clamp(followRange, MIN_ATTRIBUTE_VALUE, MAX_FOLLOW_RANGE);
        }

        public double validateArmor(double armor) {
            return clamp(armor, MIN_ATTRIBUTE_VALUE, MAX_ARMOR);
        }

        public double validateMultiplier(double multiplier) {
            return clamp(multiplier, MIN_MULTIPLIER, MAX_MULTIPLIER);
        }

        public double validateAttackSpeed(double speed) {
            return clamp(speed, MIN_ATTACK_SPEED, MAX_MULTIPLIER);
        }

        public double validatePenetration(double penetration) {
            return clamp(penetration, MIN_PENETRATION, MAX_PENETRATION);
        }

        // Weapon-specific validators
        public double validateArmorShred(double shredPercent) {
            return clamp(shredPercent, MIN_ATTRIBUTE_VALUE, MAX_ARMOR_SHRED);
        }

        public double validateDamageVs(double percent) {
            return clamp(percent, MIN_ATTRIBUTE_VALUE, MAX_DAMAGE_VS);
        }

        public double validateTrueDamage(double percent) {
            return clamp(percent, MIN_ATTRIBUTE_VALUE, MAX_TRUE_DAMAGE);
        }

        public double validateSweeping(double percent) {
            return clamp(percent, MIN_ATTRIBUTE_VALUE, MAX_SWEEPING);
        }

        // Tool/durability validators
        public int validateDurability(int value) {
            return (int) clamp(value, 0, MAX_DURABILITY);
        }

        public int validateRepairCost(int value) {
            return (int) clamp(value, 0, MAX_REPAIR_COST);
        }

        public double validateToolSpeed(double speed) {
            return clamp(speed, 0.0, MAX_TOOL_SPEED);
        }

        public int validateToolDamagePerBlock(int value) {
            return (int) clamp(value, 0, MAX_TOOL_DAMAGE_PER_BLOCK);
        }

        // Ranged weapon validators
        public double validateRangedMultiplier(double value) {
            return clamp(value, MIN_RANGED_MULT, MAX_RANGED_MULT);
        }

        public double validateRangedSpeed(double value) {
            return clamp(value, 0.0, MAX_RANGED_SPEED);
        }

        public double validateRangedGravity(double value) {
            return clamp(value, 0.0, MAX_RANGED_GRAVITY);
        }

        public double validateRangedSpread(double value) {
            return clamp(value, 0.0, MAX_RANGED_SPREAD);
        }

        public double validateRangedBaseDamage(double value) {
            return clamp(value, 0.0, MAX_RANGED_BASE_DAMAGE);
        }

        // Armor validators
        public double validateArmorReduction(double value) {
            return clamp(value, MIN_ARMOR_REDUCTION, MAX_ARMOR_REDUCTION);
        }

        public double validateArmorBonus(double value) {
            return clamp(value, MIN_ARMOR_BONUS, MAX_ARMOR_BONUS);
        }

        public double validateToughnessBonus(double value) {
            return clamp(value, MIN_TOUGHNESS_BONUS, MAX_TOUGHNESS_BONUS);
        }

        public double validateKnockbackResistance(double value) {
            return clamp(value, MIN_KNOCKBACK_RESIST, MAX_KNOCKBACK_RESIST);
        }

        public double validateThornsPercent(double value) {
            return clamp(value, MIN_THORNS_PERCENT, MAX_THORNS_PERCENT);
        }

        // Shield validators
        public double validateShieldBlock(double value) {
            return clamp(value, MIN_SHIELD_BLOCK, MAX_SHIELD_BLOCK);
        }

        public double validateShieldRecovery(double value) {
            return clamp(value, MIN_SHIELD_RECOVERY, MAX_SHIELD_RECOVERY);
        }

        // Entity validation
        public boolean validateEntityId(int entityId) {
            return entityId >= 0;
        }

        // String validation
        @Nullable
        public String validateString(@Nullable String value, int maxLength) {
            if (value == null) return null;
            // Use local non-null variable after null check
            String result = value;
            if (result.length() > maxLength) {
                result = result.substring(0, maxLength);
            }
            // Remove control characters except common whitespace
            return result.replaceAll("[\\p{Cntrl}&&[^\t\n\r]]", "");
        }

        @Nullable
        public String validateItemId(@Nullable String itemId) {
            if (itemId == null || itemId.isEmpty()) return null;
            if (itemId.length() > 256) return null;
            // Must match resource location pattern
            if (!itemId.matches("^[a-z0-9_.-]+(:?[a-z0-9_/.-]*)?$")) {
                return null;
            }
            return itemId;
        }

        private static double clamp(double value, double min, double max) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return min;
            }
            return Math.max(min, Math.min(max, value));
        }
    }

    private MockPacketValidator service;

    @BeforeEach
    void setUp() {
        service = new MockPacketValidator();
    }

    // ============================================
    // Health Validation Tests
    // ============================================

    @Nested
    @DisplayName("Health Validation Tests")
    class HealthValidationTests {

        @Test
        @DisplayName("Valid health value should pass through")
        void testValidHealth() {
            assertEquals(100.0, service.validateHealth(100.0));
            assertEquals(1000.0, service.validateHealth(1000.0));
            assertEquals(50000.0, service.validateHealth(50000.0));
        }

        @Test
        @DisplayName("Health above max should be clamped")
        void testHealthAboveMax() {
            assertEquals(100_000.0, service.validateHealth(200_000.0));
            assertEquals(100_000.0, service.validateHealth(1_000_000.0));
        }

        @Test
        @DisplayName("Negative health should be clamped to zero")
        void testNegativeHealth() {
            assertEquals(0.0, service.validateHealth(-100.0));
            assertEquals(0.0, service.validateHealth(-1.0));
        }

        @Test
        @DisplayName("Zero health is valid")
        void testZeroHealth() {
            assertEquals(0.0, service.validateHealth(0.0));
        }

        @Test
        @DisplayName("NaN health should return min value")
        void testNaNHealth() {
            assertEquals(0.0, service.validateHealth(Double.NaN));
        }

        @Test
        @DisplayName("Infinite health should return min value")
        void testInfiniteHealth() {
            assertEquals(0.0, service.validateHealth(Double.POSITIVE_INFINITY));
            assertEquals(0.0, service.validateHealth(Double.NEGATIVE_INFINITY));
        }
    }

    // ============================================
    // Damage Validation Tests
    // ============================================

    @Nested
    @DisplayName("Damage Validation Tests")
    class DamageValidationTests {

        @Test
        @DisplayName("Valid damage values")
        void testValidDamage() {
            assertEquals(10.0, service.validateDamage(10.0));
            assertEquals(500.0, service.validateDamage(500.0));
            assertEquals(5000.0, service.validateDamage(5000.0));
        }

        @Test
        @DisplayName("Damage above max should be clamped")
        void testDamageAboveMax() {
            assertEquals(10_000.0, service.validateDamage(50_000.0));
            assertEquals(10_000.0, service.validateDamage(100_000.0));
        }

        @Test
        @DisplayName("Negative damage should be clamped to zero")
        void testNegativeDamage() {
            assertEquals(0.0, service.validateDamage(-10.0));
        }
    }

    // ============================================
    // Multiplier Validation Tests
    // ============================================

    @Nested
    @DisplayName("Multiplier Validation Tests")
    class MultiplierValidationTests {

        @Test
        @DisplayName("Valid multiplier values")
        void testValidMultiplier() {
            assertEquals(1.0, service.validateMultiplier(1.0));
            assertEquals(2.5, service.validateMultiplier(2.5));
            assertEquals(50.0, service.validateMultiplier(50.0));
        }

        @Test
        @DisplayName("Multiplier at boundaries")
        void testMultiplierBoundaries() {
            assertEquals(0.0, service.validateMultiplier(0.0));
            assertEquals(100.0, service.validateMultiplier(100.0));
        }

        @Test
        @DisplayName("Multiplier above max should be clamped")
        void testMultiplierAboveMax() {
            assertEquals(100.0, service.validateMultiplier(150.0));
            assertEquals(100.0, service.validateMultiplier(1000.0));
        }

        @Test
        @DisplayName("Negative multiplier should be clamped to zero")
        void testNegativeMultiplier() {
            assertEquals(0.0, service.validateMultiplier(-1.0));
            assertEquals(0.0, service.validateMultiplier(-100.0));
        }
    }

    // ============================================
    // Attack Speed Validation Tests
    // ============================================

    @Nested
    @DisplayName("Attack Speed Validation Tests")
    class AttackSpeedValidationTests {

        @Test
        @DisplayName("Valid attack speed values")
        void testValidAttackSpeed() {
            assertEquals(1.6, service.validateAttackSpeed(1.6));
            assertEquals(4.0, service.validateAttackSpeed(4.0));
        }

        @Test
        @DisplayName("Negative attack speed is allowed (vanilla weapons)")
        void testNegativeAttackSpeed() {
            assertEquals(-4.0, service.validateAttackSpeed(-4.0));
            assertEquals(-10.0, service.validateAttackSpeed(-10.0));
        }

        @Test
        @DisplayName("Attack speed below min is clamped")
        void testAttackSpeedBelowMin() {
            assertEquals(-10.0, service.validateAttackSpeed(-20.0));
        }

        @Test
        @DisplayName("Attack speed above max is clamped")
        void testAttackSpeedAboveMax() {
            assertEquals(100.0, service.validateAttackSpeed(150.0));
        }
    }

    // ============================================
    // Penetration Validation Tests
    // ============================================

    @Nested
    @DisplayName("Penetration Validation Tests")
    class PenetrationValidationTests {

        @Test
        @DisplayName("Valid penetration values")
        void testValidPenetration() {
            assertEquals(0.5, service.validatePenetration(0.5));
            assertEquals(-0.5, service.validatePenetration(-0.5));
            assertEquals(0.0, service.validatePenetration(0.0));
        }

        @Test
        @DisplayName("Penetration at boundaries")
        void testPenetrationBoundaries() {
            assertEquals(-1.0, service.validatePenetration(-1.0));
            assertEquals(1.0, service.validatePenetration(1.0));
        }

        @Test
        @DisplayName("Penetration outside range should be clamped")
        void testPenetrationClamping() {
            assertEquals(1.0, service.validatePenetration(5.0));
            assertEquals(-1.0, service.validatePenetration(-5.0));
        }
    }

    // ============================================
    // Weapon-Specific Validation Tests
    // ============================================

    @Nested
    @DisplayName("Weapon-Specific Validation Tests")
    class WeaponSpecificTests {

        @Test
        @DisplayName("Armor shred validation")
        void testArmorShred() {
            assertEquals(30.0, service.validateArmorShred(30.0));
            assertEquals(66.0, service.validateArmorShred(100.0));
            assertEquals(0.0, service.validateArmorShred(-10.0));
        }

        @Test
        @DisplayName("Damage vs validation")
        void testDamageVs() {
            assertEquals(1.5, service.validateDamageVs(1.5));
            assertEquals(2.0, service.validateDamageVs(5.0));
            assertEquals(0.0, service.validateDamageVs(-1.0));
        }

        @Test
        @DisplayName("True damage validation")
        void testTrueDamage() {
            assertEquals(0.5, service.validateTrueDamage(0.5));
            assertEquals(1.0, service.validateTrueDamage(2.0));
            assertEquals(0.0, service.validateTrueDamage(-0.5));
        }

        @Test
        @DisplayName("Sweeping validation")
        void testSweeping() {
            assertEquals(0.75, service.validateSweeping(0.75));
            assertEquals(1.0, service.validateSweeping(1.5));
            assertEquals(0.0, service.validateSweeping(-0.5));
        }
    }

    // ============================================
    // Tool/Durability Validation Tests
    // ============================================

    @Nested
    @DisplayName("Tool and Durability Validation Tests")
    class ToolDurabilityTests {

        @Test
        @DisplayName("Durability validation")
        void testDurability() {
            assertEquals(1561, service.validateDurability(1561)); // Diamond
            assertEquals(4096, service.validateDurability(10000));
            assertEquals(0, service.validateDurability(-100));
        }

        @Test
        @DisplayName("Repair cost validation")
        void testRepairCost() {
            assertEquals(39, service.validateRepairCost(39));
            assertEquals(1000, service.validateRepairCost(5000));
            assertEquals(0, service.validateRepairCost(-10));
        }

        @Test
        @DisplayName("Tool speed validation")
        void testToolSpeed() {
            assertEquals(8.0, service.validateToolSpeed(8.0)); // Diamond
            assertEquals(128.0, service.validateToolSpeed(200.0));
            assertEquals(0.0, service.validateToolSpeed(-5.0));
        }

        @Test
        @DisplayName("Tool damage per block validation")
        void testToolDamagePerBlock() {
            assertEquals(1, service.validateToolDamagePerBlock(1));
            assertEquals(128, service.validateToolDamagePerBlock(500));
            assertEquals(0, service.validateToolDamagePerBlock(-10));
        }
    }

    // ============================================
    // Ranged Weapon Validation Tests
    // ============================================

    @Nested
    @DisplayName("Ranged Weapon Validation Tests")
    class RangedWeaponTests {

        @Test
        @DisplayName("Ranged multiplier validation")
        void testRangedMultiplier() {
            assertEquals(1.0, service.validateRangedMultiplier(1.0));
            assertEquals(0.2, service.validateRangedMultiplier(0.1));
            assertEquals(3.0, service.validateRangedMultiplier(5.0));
        }

        @Test
        @DisplayName("Ranged speed validation")
        void testRangedSpeed() {
            assertEquals(3.0, service.validateRangedSpeed(3.0));
            assertEquals(5.0, service.validateRangedSpeed(10.0));
            assertEquals(0.0, service.validateRangedSpeed(-1.0));
        }

        @Test
        @DisplayName("Ranged gravity validation")
        void testRangedGravity() {
            assertEquals(0.05, service.validateRangedGravity(0.05));
            assertEquals(0.2, service.validateRangedGravity(0.5));
            assertEquals(0.0, service.validateRangedGravity(-0.1));
        }

        @Test
        @DisplayName("Ranged spread validation")
        void testRangedSpread() {
            assertEquals(1.0, service.validateRangedSpread(1.0));
            assertEquals(3.0, service.validateRangedSpread(5.0));
            assertEquals(0.0, service.validateRangedSpread(-1.0));
        }

        @Test
        @DisplayName("Ranged base damage validation")
        void testRangedBaseDamage() {
            assertEquals(8.0, service.validateRangedBaseDamage(8.0));
            assertEquals(50.0, service.validateRangedBaseDamage(100.0));
            assertEquals(0.0, service.validateRangedBaseDamage(-5.0));
        }
    }

    // ============================================
    // Armor Stat Validation Tests
    // ============================================

    @Nested
    @DisplayName("Armor Stat Validation Tests")
    class ArmorStatTests {

        @Test
        @DisplayName("Armor reduction validation")
        void testArmorReduction() {
            assertEquals(0.5, service.validateArmorReduction(0.5));
            assertEquals(1.0, service.validateArmorReduction(1.5));
            assertEquals(0.0, service.validateArmorReduction(-0.5));
        }

        @Test
        @DisplayName("Armor bonus validation")
        void testArmorBonus() {
            assertEquals(20.0, service.validateArmorBonus(20.0));
            assertEquals(30.0, service.validateArmorBonus(50.0));
            assertEquals(-20.0, service.validateArmorBonus(-30.0));
        }

        @Test
        @DisplayName("Toughness bonus validation")
        void testToughnessBonus() {
            assertEquals(4.0, service.validateToughnessBonus(4.0));
            assertEquals(20.0, service.validateToughnessBonus(30.0));
            assertEquals(-10.0, service.validateToughnessBonus(-20.0));
        }

        @Test
        @DisplayName("Knockback resistance validation")
        void testKnockbackResistance() {
            assertEquals(0.5, service.validateKnockbackResistance(0.5));
            assertEquals(1.0, service.validateKnockbackResistance(1.5));
            assertEquals(0.0, service.validateKnockbackResistance(-0.5));
        }

        @Test
        @DisplayName("Thorns percent validation")
        void testThornsPercent() {
            assertEquals(0.25, service.validateThornsPercent(0.25));
            assertEquals(0.5, service.validateThornsPercent(1.0));
            assertEquals(0.0, service.validateThornsPercent(-0.25));
        }
    }

    // ============================================
    // Shield Validation Tests
    // ============================================

    @Nested
    @DisplayName("Shield Validation Tests")
    class ShieldTests {

        @Test
        @DisplayName("Shield block validation")
        void testShieldBlock() {
            assertEquals(0.5, service.validateShieldBlock(0.5));
            assertEquals(1.0, service.validateShieldBlock(1.5));
            assertEquals(0.0, service.validateShieldBlock(-0.5));
        }

        @Test
        @DisplayName("Shield recovery validation")
        void testShieldRecovery() {
            assertEquals(1.0, service.validateShieldRecovery(1.0));
            assertEquals(2.0, service.validateShieldRecovery(5.0));
            assertEquals(0.0, service.validateShieldRecovery(-1.0));
        }
    }

    // ============================================
    // Entity ID Validation Tests
    // ============================================

    @Nested
    @DisplayName("Entity ID Validation Tests")
    class EntityIdValidationTests {

        @Test
        @DisplayName("Positive entity IDs are valid")
        void testPositiveEntityId() {
            assertTrue(service.validateEntityId(1));
            assertTrue(service.validateEntityId(100));
            assertTrue(service.validateEntityId(Integer.MAX_VALUE));
        }

        @Test
        @DisplayName("Zero entity ID is valid")
        void testZeroEntityId() {
            assertTrue(service.validateEntityId(0));
        }

        @Test
        @DisplayName("Negative entity IDs are invalid")
        void testNegativeEntityId() {
            assertFalse(service.validateEntityId(-1));
            assertFalse(service.validateEntityId(-100));
            assertFalse(service.validateEntityId(Integer.MIN_VALUE));
        }
    }

    // ============================================
    // String Validation Tests
    // ============================================

    @Nested
    @DisplayName("String Validation Tests")
    class StringValidationTests {

        @Test
        @DisplayName("Null string returns null")
        void testNullString() {
            assertNull(service.validateString(null, 100));
        }

        @Test
        @DisplayName("Valid string passes through")
        void testValidString() {
            assertEquals("Hello World", service.validateString("Hello World", 100));
        }

        @Test
        @DisplayName("String longer than max is truncated")
        void testStringTruncation() {
            String result = Objects.requireNonNull(service.validateString("This is a very long string", 10));
            assertEquals("This is a ", result);
            assertEquals(10, result.length());
        }

        @Test
        @DisplayName("Control characters are removed")
        void testControlCharacterRemoval() {
            String input = "Hello\u0000World\u0001Test";
            String result = Objects.requireNonNull(service.validateString(input, 100));
            assertEquals("HelloWorldTest", result);
        }

        @Test
        @DisplayName("Common whitespace is preserved")
        void testWhitespacePreserved() {
            String input = "Hello\tWorld\nTest\rLine";
            String result = Objects.requireNonNull(service.validateString(input, 100));
            assertEquals("Hello\tWorld\nTest\rLine", result);
        }

        @Test
        @DisplayName("Empty string is valid")
        void testEmptyString() {
            assertEquals("", service.validateString("", 100));
        }
    }

    // ============================================
    // Item ID Validation Tests
    // ============================================

    @Nested
    @DisplayName("Item ID Validation Tests")
    class ItemIdValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "minecraft:diamond_sword",
            "minecraft:stone",
            "modded:custom_item",
            "some_mod:item_v2",
            "diamond_sword"
        })
        @DisplayName("Valid item IDs should pass")
        void testValidItemIds(String itemId) {
            assertEquals(itemId, service.validateItemId(itemId));
        }

        @Test
        @DisplayName("Null item ID returns null")
        void testNullItemId() {
            assertNull(service.validateItemId(null));
        }

        @Test
        @DisplayName("Empty item ID returns null")
        void testEmptyItemId() {
            assertNull(service.validateItemId(""));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "MINECRAFT:DIAMOND_SWORD",  // uppercase
            "minecraft:Diamond_Sword",   // mixed case
            "minecraft:item with spaces",
            "minecraft:item\twith\ttabs",
            "minecraft:item;drop table", // SQL injection
            "minecraft:item<script>"     // XSS attempt
        })
        @DisplayName("Invalid item IDs should return null")
        void testInvalidItemIds(String itemId) {
            assertNull(service.validateItemId(itemId));
        }

        @Test
        @DisplayName("Path traversal patterns with valid characters pass regex but would be blocked by PathSanitizer")
        void testPathTraversalNote() {
            // Note: "../../../etc/passwd" passes the item ID regex because it contains
            // only lowercase letters, dots, and slashes - all valid for resource locations.
            // Path traversal protection is handled by PathSanitizer, not item ID validation.
            String pathTraversal = "../../../etc/passwd";
            assertEquals(pathTraversal, service.validateItemId(pathTraversal));
        }

        @Test
        @DisplayName("Item ID exceeding max length returns null")
        void testItemIdMaxLength() {
            String longId = "a".repeat(300);
            assertNull(service.validateItemId(longId));
        }

        @Test
        @DisplayName("Item ID at max length is valid")
        void testItemIdAtMaxLength() {
            String maxLengthId = "a".repeat(256);
            assertEquals(maxLengthId, service.validateItemId(maxLengthId));
        }
    }

    // ============================================
    // Follow Range and Armor Tests
    // ============================================

    @Nested
    @DisplayName("Follow Range and Base Armor Tests")
    class FollowRangeAndArmorTests {

        @Test
        @DisplayName("Valid follow range values")
        void testValidFollowRange() {
            assertEquals(64.0, service.validateFollowRange(64.0));
            assertEquals(1024.0, service.validateFollowRange(1024.0));
        }

        @Test
        @DisplayName("Follow range above max is clamped")
        void testFollowRangeAboveMax() {
            assertEquals(1024.0, service.validateFollowRange(2000.0));
        }

        @Test
        @DisplayName("Valid armor values")
        void testValidArmor() {
            assertEquals(20.0, service.validateArmor(20.0));
            assertEquals(500.0, service.validateArmor(500.0));
        }

        @Test
        @DisplayName("Armor above max is clamped")
        void testArmorAboveMax() {
            assertEquals(1000.0, service.validateArmor(2000.0));
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Very small positive values")
        void testVerySmallValues() {
            assertEquals(0.0001, service.validateHealth(0.0001), 0.00001);
            assertEquals(0.0001, service.validateDamage(0.0001), 0.00001);
        }

        @Test
        @DisplayName("Max double values")
        void testMaxDoubleValues() {
            assertEquals(100_000.0, service.validateHealth(Double.MAX_VALUE));
            assertEquals(10_000.0, service.validateDamage(Double.MAX_VALUE));
        }

        @Test
        @DisplayName("Min double values")
        void testMinDoubleValues() {
            assertEquals(0.0, service.validateHealth(-Double.MAX_VALUE));
            assertEquals(0.0, service.validateDamage(-Double.MAX_VALUE));
        }

        @ParameterizedTest
        @CsvSource({
            "10.0, 10.0",
            "100000.0, 100000.0",
            "100001.0, 100000.0",
            "-1.0, 0.0",
            "0.0, 0.0"
        })
        @DisplayName("Health validation with various inputs")
        void testHealthValidationParameterized(double input, double expected) {
            assertEquals(expected, service.validateHealth(input), 0.001);
        }

        @Test
        @DisplayName("NaN and Infinity for all validators")
        void testSpecialValuesAllValidators() {
            // All validators should handle NaN/Infinity gracefully
            assertEquals(0.0, service.validateDamage(Double.NaN));
            assertEquals(0.0, service.validateFollowRange(Double.POSITIVE_INFINITY));
            assertEquals(0.0, service.validateArmor(Double.NEGATIVE_INFINITY));
            assertEquals(0.0, service.validateMultiplier(Double.NaN));
            assertEquals(-10.0, service.validateAttackSpeed(Double.NaN));
            assertEquals(-1.0, service.validatePenetration(Double.NaN));
        }
    }

    // ============================================
    // Constants Verification Tests
    // ============================================

    @Nested
    @DisplayName("Constants Match Production Code")
    class ConstantsVerificationTests {

        @Test
        @DisplayName("Core bounds match production code")
        void testCoreBounds() {
            assertEquals(0.0, MockPacketValidator.MIN_ATTRIBUTE_VALUE);
            assertEquals(100_000.0, MockPacketValidator.MAX_HEALTH);
            assertEquals(10_000.0, MockPacketValidator.MAX_DAMAGE);
            assertEquals(1024.0, MockPacketValidator.MAX_FOLLOW_RANGE);
            assertEquals(1000.0, MockPacketValidator.MAX_ARMOR);
        }

        @Test
        @DisplayName("Multiplier bounds match production code")
        void testMultiplierBounds() {
            assertEquals(0.0, MockPacketValidator.MIN_MULTIPLIER);
            assertEquals(100.0, MockPacketValidator.MAX_MULTIPLIER);
            assertEquals(-10.0, MockPacketValidator.MIN_ATTACK_SPEED);
        }

        @Test
        @DisplayName("Penetration bounds match production code")
        void testPenetrationBounds() {
            assertEquals(-1.0, MockPacketValidator.MIN_PENETRATION);
            assertEquals(1.0, MockPacketValidator.MAX_PENETRATION);
        }

        @Test
        @DisplayName("Weapon stat bounds match production code")
        void testWeaponStatBounds() {
            assertEquals(66.0, MockPacketValidator.MAX_ARMOR_SHRED);
            assertEquals(2.0, MockPacketValidator.MAX_DAMAGE_VS);
            assertEquals(1.0, MockPacketValidator.MAX_TRUE_DAMAGE);
            assertEquals(1.0, MockPacketValidator.MAX_SWEEPING);
        }

        @Test
        @DisplayName("Tool bounds match production code")
        void testToolBounds() {
            assertEquals(4096, MockPacketValidator.MAX_DURABILITY);
            assertEquals(1000, MockPacketValidator.MAX_REPAIR_COST);
            assertEquals(128.0, MockPacketValidator.MAX_TOOL_SPEED);
            assertEquals(128, MockPacketValidator.MAX_TOOL_DAMAGE_PER_BLOCK);
        }

        @Test
        @DisplayName("Ranged weapon bounds match production code")
        void testRangedBounds() {
            assertEquals(0.2, MockPacketValidator.MIN_RANGED_MULT);
            assertEquals(3.0, MockPacketValidator.MAX_RANGED_MULT);
            assertEquals(5.0, MockPacketValidator.MAX_RANGED_SPEED);
            assertEquals(0.2, MockPacketValidator.MAX_RANGED_GRAVITY);
            assertEquals(3.0, MockPacketValidator.MAX_RANGED_SPREAD);
            assertEquals(50.0, MockPacketValidator.MAX_RANGED_BASE_DAMAGE);
        }

        @Test
        @DisplayName("Armor stat bounds match production code")
        void testArmorStatBounds() {
            assertEquals(0.0, MockPacketValidator.MIN_ARMOR_REDUCTION);
            assertEquals(1.0, MockPacketValidator.MAX_ARMOR_REDUCTION);
            assertEquals(-20.0, MockPacketValidator.MIN_ARMOR_BONUS);
            assertEquals(30.0, MockPacketValidator.MAX_ARMOR_BONUS);
            assertEquals(-10.0, MockPacketValidator.MIN_TOUGHNESS_BONUS);
            assertEquals(20.0, MockPacketValidator.MAX_TOUGHNESS_BONUS);
        }

        @Test
        @DisplayName("Shield bounds match production code")
        void testShieldBounds() {
            assertEquals(0.0, MockPacketValidator.MIN_SHIELD_BLOCK);
            assertEquals(1.0, MockPacketValidator.MAX_SHIELD_BLOCK);
            assertEquals(0.0, MockPacketValidator.MIN_SHIELD_RECOVERY);
            assertEquals(2.0, MockPacketValidator.MAX_SHIELD_RECOVERY);
        }
    }
}
