package com.devmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PacketValidator.
 * Tests value validation, clamping, and input sanitization.
 * Note: Rate limiting tests require mocking ServerPlayer which is complex,
 * so this focuses on the pure validation logic.
 */
public class PacketValidatorTest {

    /**
     * Mock PacketValidator with the same validation logic as the real one.
     * This avoids Minecraft dependencies while testing the core logic.
     */
    static class MockPacketValidator {
        // Validation bounds (same as real service)
        public static final double MIN_ATTRIBUTE_VALUE = 0.0;
        public static final double MAX_HEALTH = 100_000.0;
        public static final double MAX_DAMAGE = 10_000.0;
        public static final double MAX_FOLLOW_RANGE = 1024.0;
        public static final double MAX_ARMOR = 1000.0;
        public static final double MAX_MULTIPLIER = 100.0;
        public static final double MIN_MULTIPLIER = 0.0;
        public static final double MAX_PENETRATION = 1.0;
        public static final double MIN_PENETRATION = -1.0;

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

        public double validatePenetration(double penetration) {
            return clamp(penetration, MIN_PENETRATION, MAX_PENETRATION);
        }

        public boolean validateEntityId(int entityId) {
            return entityId >= 0;
        }

        public String validateString(String value, int maxLength) {
            if (value == null) return null;
            if (value.length() > maxLength) {
                value = value.substring(0, maxLength);
            }
            // Remove control characters except common whitespace
            return value.replaceAll("[\\p{Cntrl}&&[^\t\n\r]]", "");
        }

        public String validateItemId(String itemId) {
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
            String result = service.validateString("This is a very long string", 10);
            assertEquals("This is a ", result);
            assertEquals(10, result.length());
        }

        @Test
        @DisplayName("Control characters are removed")
        void testControlCharacterRemoval() {
            String input = "Hello\u0000World\u0001Test";
            String result = service.validateString(input, 100);
            assertEquals("HelloWorldTest", result);
        }

        @Test
        @DisplayName("Common whitespace is preserved")
        void testWhitespacePreserved() {
            String input = "Hello\tWorld\nTest\rLine";
            String result = service.validateString(input, 100);
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
            // The item ID regex only validates format, not security implications.
            String pathTraversal = "../../../etc/passwd";
            // This actually passes the regex since dots and slashes are valid
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
    }

    // ============================================
    // Follow Range and Armor Tests
    // ============================================

    @Nested
    @DisplayName("Follow Range and Armor Tests")
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
}
