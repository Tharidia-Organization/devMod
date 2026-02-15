package com.devmod.hologram.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.devmod.hologram.runtime.HologramNaming;

@DisplayName("HologramNaming")
class HologramNamingTest {

    @Nested
    @DisplayName("isValidHologramId")
    class IsValidHologramId {

        @Test
        @DisplayName("accepts simple snake_case id")
        void acceptsSnakeCase() {
            assertTrue(HologramNaming.isValidHologramId("my_hologram"));
        }

        @Test
        @DisplayName("accepts single character id")
        void acceptsSingleChar() {
            assertTrue(HologramNaming.isValidHologramId("a"));
        }

        @Test
        @DisplayName("accepts id with digits")
        void acceptsDigits() {
            assertTrue(HologramNaming.isValidHologramId("holo_42"));
        }

        @Test
        @DisplayName("accepts id at max length (32)")
        void acceptsMaxLength() {
            String id = "a".repeat(HologramNaming.MAX_ID_LENGTH);
            assertTrue(HologramNaming.isValidHologramId(id));
        }

        @Test
        @DisplayName("rejects id exceeding max length")
        void rejectsOverMaxLength() {
            String id = "a".repeat(HologramNaming.MAX_ID_LENGTH + 1);
            assertFalse(HologramNaming.isValidHologramId(id));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("rejects null and empty")
        void rejectsNullAndEmpty(String id) {
            assertFalse(HologramNaming.isValidHologramId(id));
        }

        @ParameterizedTest
        @ValueSource(strings = {"UPPER", "my-hologram", "has space", "special!", "camelCase"})
        @DisplayName("rejects invalid characters")
        void rejectsInvalidChars(String id) {
            assertFalse(HologramNaming.isValidHologramId(id));
        }

        @Test
        @DisplayName("rejects reserved prefix system_")
        void rejectsSystemPrefix() {
            assertFalse(HologramNaming.isValidHologramId("system_info"));
        }

        @Test
        @DisplayName("rejects reserved prefix preset_")
        void rejectsPresetPrefix() {
            assertFalse(HologramNaming.isValidHologramId("preset_welcome"));
        }
    }

    @Nested
    @DisplayName("isValidLabel")
    class IsValidLabel {

        @Test
        @DisplayName("accepts valid label")
        void acceptsValid() {
            assertTrue(HologramNaming.isValidLabel("My Hologram"));
        }

        @Test
        @DisplayName("accepts label at max length")
        void acceptsMaxLength() {
            assertTrue(HologramNaming.isValidLabel("x".repeat(HologramNaming.MAX_LABEL_LENGTH)));
        }

        @Test
        @DisplayName("rejects label exceeding max length")
        void rejectsOverMax() {
            assertFalse(HologramNaming.isValidLabel("x".repeat(HologramNaming.MAX_LABEL_LENGTH + 1)));
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertFalse(HologramNaming.isValidLabel(null));
        }

        @Test
        @DisplayName("rejects empty")
        void rejectsEmpty() {
            assertFalse(HologramNaming.isValidLabel(""));
        }
    }

    @Nested
    @DisplayName("isValidLineContent")
    class IsValidLineContent {

        @Test
        @DisplayName("accepts line within limit")
        void acceptsValid() {
            assertTrue(HologramNaming.isValidLineContent("Hello World"));
        }

        @Test
        @DisplayName("accepts empty line")
        void acceptsEmpty() {
            assertTrue(HologramNaming.isValidLineContent(""));
        }

        @Test
        @DisplayName("accepts line at max length")
        void acceptsMaxLength() {
            assertTrue(HologramNaming.isValidLineContent("x".repeat(HologramNaming.MAX_LINE_LENGTH)));
        }

        @Test
        @DisplayName("rejects line exceeding max")
        void rejectsOverMax() {
            assertFalse(HologramNaming.isValidLineContent("x".repeat(HologramNaming.MAX_LINE_LENGTH + 1)));
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertFalse(HologramNaming.isValidLineContent(null));
        }
    }

    @Nested
    @DisplayName("isValidLineCount")
    class IsValidLineCount {

        @Test
        @DisplayName("accepts 0")
        void acceptsZero() {
            assertTrue(HologramNaming.isValidLineCount(0));
        }

        @Test
        @DisplayName("accepts MAX_LINES")
        void acceptsMax() {
            assertTrue(HologramNaming.isValidLineCount(HologramNaming.MAX_LINES));
        }

        @Test
        @DisplayName("rejects negative")
        void rejectsNegative() {
            assertFalse(HologramNaming.isValidLineCount(-1));
        }

        @Test
        @DisplayName("rejects over max")
        void rejectsOverMax() {
            assertFalse(HologramNaming.isValidLineCount(HologramNaming.MAX_LINES + 1));
        }
    }

    @Nested
    @DisplayName("truncateLine")
    class TruncateLine {

        @Test
        @DisplayName("returns same string when within limit")
        void noTruncation() {
            assertEquals("hello", HologramNaming.truncateLine("hello"));
        }

        @Test
        @DisplayName("truncates long line")
        void truncatesLong() {
            String longLine = "x".repeat(200);
            String result = HologramNaming.truncateLine(longLine);
            assertEquals(HologramNaming.MAX_LINE_LENGTH, result.length());
        }

        @Test
        @DisplayName("returns empty for null")
        void nullToEmpty() {
            assertEquals("", HologramNaming.truncateLine(null));
        }
    }

    @Nested
    @DisplayName("truncateLabel")
    class TruncateLabel {

        @Test
        @DisplayName("returns same string when within limit")
        void noTruncation() {
            assertEquals("My Label", HologramNaming.truncateLabel("My Label"));
        }

        @Test
        @DisplayName("truncates long label")
        void truncatesLong() {
            String longLabel = "x".repeat(100);
            String result = HologramNaming.truncateLabel(longLabel);
            assertEquals(HologramNaming.MAX_LABEL_LENGTH, result.length());
        }

        @Test
        @DisplayName("returns empty for null")
        void nullToEmpty() {
            assertEquals("", HologramNaming.truncateLabel(null));
        }
    }

    @Nested
    @DisplayName("generateIdFromLabel")
    class GenerateIdFromLabel {

        @Test
        @DisplayName("converts to lowercase snake_case")
        void convertsToSnakeCase() {
            assertEquals("my_hologram", HologramNaming.generateIdFromLabel("My Hologram"));
        }

        @Test
        @DisplayName("removes invalid characters")
        void removesInvalid() {
            assertEquals("hello_world", HologramNaming.generateIdFromLabel("Hello World!@#"));
        }

        @Test
        @DisplayName("returns 'hologram' for null")
        void nullFallback() {
            assertEquals("hologram", HologramNaming.generateIdFromLabel(null));
        }

        @Test
        @DisplayName("returns 'hologram' for empty")
        void emptyFallback() {
            assertEquals("hologram", HologramNaming.generateIdFromLabel(""));
        }

        @Test
        @DisplayName("returns 'hologram' when all chars invalid")
        void allInvalidFallback() {
            assertEquals("hologram", HologramNaming.generateIdFromLabel("!!!"));
        }

        @Test
        @DisplayName("truncates to MAX_ID_LENGTH")
        void truncatesToMax() {
            String longLabel = "x".repeat(100);
            String result = HologramNaming.generateIdFromLabel(longLabel);
            assertTrue(result.length() <= HologramNaming.MAX_ID_LENGTH);
        }

        @Test
        @DisplayName("prefixes reserved system_ id")
        void prefixesSystemReserved() {
            String result = HologramNaming.generateIdFromLabel("system_test");
            assertTrue(result.startsWith("h_"));
            assertFalse(result.startsWith("system_"));
        }

        @Test
        @DisplayName("prefixes reserved preset_ id")
        void prefixesPresetReserved() {
            String result = HologramNaming.generateIdFromLabel("preset_test");
            assertTrue(result.startsWith("h_"));
        }
    }

    @Test
    @DisplayName("getReservedPrefixes returns system_ and preset_")
    void reservedPrefixes() {
        var prefixes = HologramNaming.getReservedPrefixes();
        assertTrue(prefixes.contains("system_"));
        assertTrue(prefixes.contains("preset_"));
        assertEquals(2, prefixes.size());
    }

    @Test
    @DisplayName("constants have expected values")
    void constants() {
        assertEquals(16, HologramNaming.MAX_LINES);
        assertEquals(128, HologramNaming.MAX_LINE_LENGTH);
        assertEquals(32, HologramNaming.MAX_ID_LENGTH);
        assertEquals(48, HologramNaming.MAX_LABEL_LENGTH);
    }
}
