package com.devmod.zone.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for ZoneNaming: ID validation, display name validation,
 * normalization, and reserved ID handling.
 */
@DisplayName("ZoneNaming")
class ZoneNamingTest {

    // ========================================================================
    // Zone ID Validation
    // ========================================================================

    @Nested
    @DisplayName("isValidZoneId()")
    class ZoneIdValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {"hub", "combat", "arena_test", "zone1", "a", "my_zone_123"})
        @DisplayName("valid zone IDs are accepted")
        void validIds(String id) {
            assertTrue(ZoneNaming.isValidZoneId(id), "Expected valid: " + id);
        }

        @Test
        @DisplayName("empty string is rejected")
        void emptyString() {
            assertFalse(ZoneNaming.isValidZoneId(""));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Combat", "ARENA", "Hub"})
        @DisplayName("uppercase IDs are rejected")
        void uppercaseRejected(String id) {
            assertFalse(ZoneNaming.isValidZoneId(id), "Expected invalid: " + id);
        }

        @ParameterizedTest
        @ValueSource(strings = {"my-zone", "zone.test", "zone test", "zone@1"})
        @DisplayName("special characters are rejected")
        void specialCharsRejected(String id) {
            assertFalse(ZoneNaming.isValidZoneId(id), "Expected invalid: " + id);
        }

        @Test
        @DisplayName("ID exceeding max length is rejected")
        void tooLong() {
            String longId = "a".repeat(ZoneNaming.MAX_ID_LENGTH + 1);
            assertFalse(ZoneNaming.isValidZoneId(longId));
        }

        @Test
        @DisplayName("ID at exactly max length is accepted")
        void exactMaxLength() {
            String maxId = "a".repeat(ZoneNaming.MAX_ID_LENGTH);
            assertTrue(ZoneNaming.isValidZoneId(maxId));
        }

        @ParameterizedTest
        @ValueSource(strings = {"outside", "unknown", "none"})
        @DisplayName("reserved IDs are rejected")
        void reservedIds(String id) {
            assertFalse(ZoneNaming.isValidZoneId(id), "Expected reserved: " + id);
        }

        @ParameterizedTest
        @ValueSource(strings = {"system_zone", "system_test", "preset_hub", "preset_combat"})
        @DisplayName("reserved prefixes are rejected")
        void reservedPrefixes(String id) {
            assertFalse(ZoneNaming.isValidZoneId(id), "Expected reserved prefix: " + id);
        }

        @Test
        @DisplayName("IDs with reserved words as substring are valid")
        void reservedSubstring() {
            assertTrue(ZoneNaming.isValidZoneId("my_outside"));
            assertTrue(ZoneNaming.isValidZoneId("unknown_zone"));
        }
    }

    // ========================================================================
    // Display Name Validation
    // ========================================================================

    @Nested
    @DisplayName("isValidDisplayName()")
    class DisplayNameValidationTests {

        @Test
        @DisplayName("normal display names are valid")
        void normalNames() {
            assertTrue(ZoneNaming.isValidDisplayName("Combat Test"));
            assertTrue(ZoneNaming.isValidDisplayName("A"));
        }

        @Test
        @DisplayName("empty display name is invalid")
        void emptyName() {
            assertFalse(ZoneNaming.isValidDisplayName(""));
        }

        @Test
        @DisplayName("display name at max length is valid")
        void maxLength() {
            String name = "x".repeat(ZoneNaming.MAX_NAME_LENGTH);
            assertTrue(ZoneNaming.isValidDisplayName(name));
        }

        @Test
        @DisplayName("display name exceeding max length is invalid")
        void exceedMaxLength() {
            String name = "x".repeat(ZoneNaming.MAX_NAME_LENGTH + 1);
            assertFalse(ZoneNaming.isValidDisplayName(name));
        }
    }

    // ========================================================================
    // Hint Message Validation
    // ========================================================================

    @Nested
    @DisplayName("isValidHintMessage()")
    class HintMessageValidationTests {

        @Test
        @DisplayName("empty hint is valid")
        void emptyHint() {
            assertTrue(ZoneNaming.isValidHintMessage(""));
        }

        @Test
        @DisplayName("normal hint is valid")
        void normalHint() {
            assertTrue(ZoneNaming.isValidHintMessage("Welcome to the combat zone!"));
        }

        @Test
        @DisplayName("hint exceeding max length is invalid")
        void tooLongHint() {
            String hint = "x".repeat(ZoneNaming.MAX_HINT_LENGTH + 1);
            assertFalse(ZoneNaming.isValidHintMessage(hint));
        }
    }

    // ========================================================================
    // Normalization
    // ========================================================================

    @Nested
    @DisplayName("normalizeZoneId()")
    class NormalizationTests {

        @Test
        @DisplayName("converts to lowercase")
        void lowercases() {
            assertEquals("combat", ZoneNaming.normalizeZoneId("COMBAT"));
        }

        @Test
        @DisplayName("replaces spaces and special chars with underscores")
        void replacesSpecialChars() {
            assertEquals("my_zone", ZoneNaming.normalizeZoneId("My Zone"));
            assertEquals("zone_test", ZoneNaming.normalizeZoneId("zone-test"));
        }

        @Test
        @DisplayName("collapses multiple underscores")
        void collapsesUnderscores() {
            assertEquals("a_b", ZoneNaming.normalizeZoneId("a___b"));
        }

        @Test
        @DisplayName("trims leading/trailing underscores")
        void trimsUnderscores() {
            assertEquals("zone", ZoneNaming.normalizeZoneId("_zone_"));
        }

        @Test
        @DisplayName("trims whitespace")
        void trimsWhitespace() {
            assertEquals("hub", ZoneNaming.normalizeZoneId("  hub  "));
        }
    }

    // ========================================================================
    // Display Name to Zone ID
    // ========================================================================

    @Nested
    @DisplayName("displayNameToZoneId()")
    class DisplayNameToIdTests {

        @Test
        @DisplayName("converts display name to valid zone ID")
        void convertsName() {
            assertEquals("combat_test", ZoneNaming.displayNameToZoneId("Combat Test"));
        }

        @Test
        @DisplayName("truncates to max length")
        void truncates() {
            String longName = "a".repeat(100);
            String result = ZoneNaming.displayNameToZoneId(longName);
            assertTrue(result.length() <= ZoneNaming.MAX_ID_LENGTH);
        }

        @Test
        @DisplayName("empty name produces 'zone' fallback")
        void emptyFallback() {
            assertEquals("zone", ZoneNaming.displayNameToZoneId(""));
        }

        @Test
        @DisplayName("all-special-chars name produces 'zone' fallback")
        void specialCharsFallback() {
            assertEquals("zone", ZoneNaming.displayNameToZoneId("@#$%"));
        }
    }

    // ========================================================================
    // Error Messages
    // ========================================================================

    @Nested
    @DisplayName("getZoneIdError()")
    class ErrorMessageTests {

        @Test
        @DisplayName("empty ID gives correct error")
        void emptyIdError() {
            assertEquals("Zone ID cannot be empty", ZoneNaming.getZoneIdError(""));
        }

        @Test
        @DisplayName("too long ID gives correct error")
        void tooLongError() {
            String longId = "a".repeat(ZoneNaming.MAX_ID_LENGTH + 1);
            assertTrue(ZoneNaming.getZoneIdError(longId).contains("too long"));
        }

        @Test
        @DisplayName("invalid chars gives correct error")
        void invalidCharsError() {
            assertTrue(ZoneNaming.getZoneIdError("MY-Zone").contains("lowercase"));
        }

        @Test
        @DisplayName("reserved ID gives correct error")
        void reservedError() {
            assertTrue(ZoneNaming.getZoneIdError("outside").contains("reserved"));
        }

        @Test
        @DisplayName("reserved prefix gives correct error")
        void reservedPrefixError() {
            assertTrue(ZoneNaming.getZoneIdError("system_test").contains("system_"));
        }
    }
}
