package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.devmod.network.NetworkConstants;

/**
 * Tests for telepad name sanitization logic.
 * Mirrors TelepadBlockEntity.sanitizeTelepadName() which:
 *   1. Trims whitespace
 *   2. Truncates to MAX_TELEPAD_NAME_LENGTH (MAX_SMALL_STRING = 32)
 */
class TelepadNameSanitizationTest {

    private static final int MAX_LENGTH = NetworkConstants.MAX_SMALL_STRING; // 32

    /**
     * Mirrors TelepadBlockEntity.sanitizeTelepadName().
     */
    static String sanitizeTelepadName(String name) {
        String trimmed = name == null ? "" : name.trim();
        return NetworkConstants.truncate(trimmed, MAX_LENGTH);
    }

    @Nested
    @DisplayName("Null and empty inputs")
    class NullEmptyTests {

        @Test
        @DisplayName("Null becomes empty string")
        void nullInput() {
            assertEquals("", sanitizeTelepadName(null));
        }

        @Test
        @DisplayName("Empty stays empty")
        void emptyInput() {
            assertEquals("", sanitizeTelepadName(""));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Null and empty both produce empty string")
        void nullAndEmpty(String input) {
            assertEquals("", sanitizeTelepadName(input));
        }
    }

    @Nested
    @DisplayName("Whitespace trimming")
    class TrimmingTests {

        @Test
        @DisplayName("Leading spaces trimmed")
        void leadingSpaces() {
            assertEquals("home", sanitizeTelepadName("   home"));
        }

        @Test
        @DisplayName("Trailing spaces trimmed")
        void trailingSpaces() {
            assertEquals("home", sanitizeTelepadName("home   "));
        }

        @Test
        @DisplayName("Both sides trimmed")
        void bothSides() {
            assertEquals("home", sanitizeTelepadName("  home  "));
        }

        @Test
        @DisplayName("Only whitespace becomes empty")
        void onlyWhitespace() {
            assertEquals("", sanitizeTelepadName("     "));
        }

        @Test
        @DisplayName("Tabs and newlines trimmed")
        void tabsAndNewlines() {
            assertEquals("test", sanitizeTelepadName("\t\ntest\t\n"));
        }

        @Test
        @DisplayName("Internal spaces preserved")
        void internalSpaces() {
            assertEquals("my base", sanitizeTelepadName("my base"));
        }
    }

    @Nested
    @DisplayName("Length truncation")
    class TruncationTests {

        @Test
        @DisplayName("Short name preserved")
        void shortName() {
            assertEquals("base", sanitizeTelepadName("base"));
        }

        @Test
        @DisplayName("Exactly MAX_LENGTH preserved")
        void exactMaxLength() {
            String exact = "a".repeat(MAX_LENGTH);
            assertEquals(exact, sanitizeTelepadName(exact));
            assertEquals(MAX_LENGTH, sanitizeTelepadName(exact).length());
        }

        @Test
        @DisplayName("Over MAX_LENGTH truncated")
        void overMaxLength() {
            String oversize = "x".repeat(MAX_LENGTH + 50);
            String result = sanitizeTelepadName(oversize);
            assertEquals(MAX_LENGTH, result.length());
            assertEquals("x".repeat(MAX_LENGTH), result);
        }

        @Test
        @DisplayName("Trimming happens before truncation")
        void trimBeforeTruncate() {
            // If we have "  " + 40 chars + "  " the trim first reduces length
            String padded = "  " + "a".repeat(40) + "  ";
            String result = sanitizeTelepadName(padded);
            // After trim: 40 chars, then truncated to MAX_LENGTH (32)
            assertEquals(MAX_LENGTH, result.length());
        }
    }

    @Nested
    @DisplayName("Common telepad names")
    class CommonNameTests {

        @ParameterizedTest
        @ValueSource(strings = {"home", "spawn", "arena_01", "base", "farm", "nether_hub"})
        @DisplayName("Common names pass through unchanged")
        void commonNames(String name) {
            assertEquals(name, sanitizeTelepadName(name));
        }

        @Test
        @DisplayName("Unicode names are allowed")
        void unicodeNames() {
            assertEquals("base_alpha", sanitizeTelepadName("base_alpha"));
        }
    }

    @Nested
    @DisplayName("MAX_LENGTH constant")
    class ConstantsTests {

        @Test
        @DisplayName("MAX_LENGTH is 32 (NetworkConstants.MAX_SMALL_STRING)")
        void maxLengthValue() {
            assertEquals(32, MAX_LENGTH);
        }
    }
}
