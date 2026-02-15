package com.devmod.config.handler.export;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CommandStringUtil")
class CommandStringUtilTest {

    // ================================================================
    // HEADER_LINE constant
    // ================================================================

    @Test
    @DisplayName("HEADER_LINE is non-empty")
    void headerLineIsNonEmpty() {
        assertFalse(CommandStringUtil.HEADER_LINE.isEmpty());
        assertTrue(CommandStringUtil.HEADER_LINE.startsWith("//"));
    }

    @Test
    @DisplayName("EMPTY_ITEM is bracket notation")
    void emptyItemIsBracketNotation() {
        assertEquals("<item:empty>", CommandStringUtil.EMPTY_ITEM);
    }

    // ================================================================
    // formatFloat
    // ================================================================

    @Nested
    @DisplayName("formatFloat")
    class FormatFloat {

        @Test
        @DisplayName("whole number gets .0f suffix")
        void wholeNumberFormat() {
            String result = CommandStringUtil.formatFloat(5.0f);
            assertEquals("5.0f", result);
        }

        @Test
        @DisplayName("decimal value gets f suffix")
        void decimalFormat() {
            String result = CommandStringUtil.formatFloat(3.14f);
            assertTrue(result.endsWith("f"));
            assertTrue(result.contains("3.14"));
        }

        @Test
        @DisplayName("zero formats correctly")
        void zeroFormat() {
            assertEquals("0.0f", CommandStringUtil.formatFloat(0.0f));
        }

        @Test
        @DisplayName("negative value formats correctly")
        void negativeFormat() {
            String result = CommandStringUtil.formatFloat(-2.5f);
            assertTrue(result.startsWith("-"));
            assertTrue(result.endsWith("f"));
        }
    }

    // ================================================================
    // formatDouble
    // ================================================================

    @Nested
    @DisplayName("formatDouble")
    class FormatDouble {

        @Test
        @DisplayName("whole number gets .0 suffix")
        void wholeNumberFormat() {
            assertEquals("5.0", CommandStringUtil.formatDouble(5.0));
        }

        @Test
        @DisplayName("decimal value trims trailing zeros")
        void decimalFormat() {
            String result = CommandStringUtil.formatDouble(3.14);
            assertTrue(result.contains("3.14"));
            assertFalse(result.endsWith("0000"));
        }

        @Test
        @DisplayName("zero formats correctly")
        void zeroFormat() {
            assertEquals("0.0", CommandStringUtil.formatDouble(0.0));
        }
    }

    // ================================================================
    // formatInt / formatLong
    // ================================================================

    @Test
    @DisplayName("formatInt formats integer")
    void formatInt() {
        assertEquals("42", CommandStringUtil.formatInt(42));
        assertEquals("-1", CommandStringUtil.formatInt(-1));
        assertEquals("0", CommandStringUtil.formatInt(0));
    }

    @Test
    @DisplayName("formatLong formats long")
    void formatLong() {
        assertEquals("123456789", CommandStringUtil.formatLong(123456789L));
        assertEquals("0", CommandStringUtil.formatLong(0L));
    }

    // ================================================================
    // formatBoolean
    // ================================================================

    @Test
    @DisplayName("formatBoolean returns lowercase strings")
    void formatBoolean() {
        assertEquals("true", CommandStringUtil.formatBoolean(true));
        assertEquals("false", CommandStringUtil.formatBoolean(false));
    }

    // ================================================================
    // quoteAndEscape
    // ================================================================

    @Nested
    @DisplayName("quoteAndEscape")
    class QuoteAndEscape {

        @Test
        @DisplayName("wraps string in quotes")
        void wrapsInQuotes() {
            assertEquals("\"hello\"", CommandStringUtil.quoteAndEscape("hello"));
        }

        @Test
        @DisplayName("escapes backslash")
        void escapesBackslash() {
            assertEquals("\"a\\\\b\"", CommandStringUtil.quoteAndEscape("a\\b"));
        }

        @Test
        @DisplayName("escapes double quote")
        void escapesQuote() {
            assertEquals("\"a\\\"b\"", CommandStringUtil.quoteAndEscape("a\"b"));
        }

        @Test
        @DisplayName("escapes newline and carriage return")
        void escapesNewlineAndCR() {
            assertEquals("\"a\\nb\"", CommandStringUtil.quoteAndEscape("a\nb"));
            assertEquals("\"a\\rb\"", CommandStringUtil.quoteAndEscape("a\rb"));
        }

        @Test
        @DisplayName("escapes tab")
        void escapesTab() {
            assertEquals("\"a\\tb\"", CommandStringUtil.quoteAndEscape("a\tb"));
        }

        @Test
        @DisplayName("null returns literal null")
        void nullReturnsNull() {
            assertEquals("null", CommandStringUtil.quoteAndEscape(null));
        }

        @Test
        @DisplayName("empty string returns empty quotes")
        void emptyString() {
            assertEquals("\"\"", CommandStringUtil.quoteAndEscape(""));
        }
    }

    // ================================================================
    // formatList
    // ================================================================

    @Nested
    @DisplayName("formatList")
    class FormatList {

        @Test
        @DisplayName("null list returns []")
        void nullListReturnsEmpty() {
            assertEquals("[]", CommandStringUtil.formatList(null, Object::toString));
        }

        @Test
        @DisplayName("empty list returns []")
        void emptyListReturnsEmpty() {
            assertEquals("[]", CommandStringUtil.formatList(List.of(), Object::toString));
        }

        @Test
        @DisplayName("single element list")
        void singleElement() {
            assertEquals("[1]", CommandStringUtil.formatList(List.of(1), String::valueOf));
        }

        @Test
        @DisplayName("multiple elements separated by comma-space")
        void multipleElements() {
            assertEquals("[a, b, c]", CommandStringUtil.formatList(
                List.of("a", "b", "c"), s -> s));
        }
    }

    // ================================================================
    // formatValue (generic dispatch)
    // ================================================================

    @Nested
    @DisplayName("formatValue")
    class FormatValue {

        @Test
        @DisplayName("null returns literal null")
        void nullReturnsNull() {
            assertEquals("null", CommandStringUtil.formatValue(null));
        }

        @Test
        @DisplayName("dispatches Float to formatFloat")
        void dispatchesFloat() {
            String result = CommandStringUtil.formatValue(3.0f);
            assertTrue(result.endsWith("f"));
        }

        @Test
        @DisplayName("dispatches Double to formatDouble")
        void dispatchesDouble() {
            String result = CommandStringUtil.formatValue(3.0);
            assertFalse(result.endsWith("f"));
            assertTrue(result.contains("3.0"));
        }

        @Test
        @DisplayName("dispatches Integer to formatInt")
        void dispatchesInteger() {
            assertEquals("42", CommandStringUtil.formatValue(42));
        }

        @Test
        @DisplayName("dispatches Long to formatLong")
        void dispatchesLong() {
            assertEquals("99", CommandStringUtil.formatValue(99L));
        }

        @Test
        @DisplayName("dispatches Boolean to formatBoolean")
        void dispatchesBoolean() {
            assertEquals("true", CommandStringUtil.formatValue(true));
            assertEquals("false", CommandStringUtil.formatValue(false));
        }

        @Test
        @DisplayName("dispatches String to quoteAndEscape")
        void dispatchesString() {
            assertEquals("\"hello\"", CommandStringUtil.formatValue("hello"));
        }

        @Test
        @DisplayName("dispatches List to formatList")
        void dispatchesList() {
            String result = CommandStringUtil.formatValue(List.of(1, 2, 3));
            assertEquals("[1, 2, 3]", result);
        }

        @Test
        @DisplayName("unknown type falls back to toString")
        void unknownTypeFallsBackToString() {
            var custom = new Object() {
                @Override public String toString() { return "custom_object"; }
            };
            assertEquals("custom_object", CommandStringUtil.formatValue(custom));
        }
    }

    // ================================================================
    // Bracket notation (ResourceLocation-based)
    // ================================================================

    @Test
    @DisplayName("resourceLocationBracket with null returns empty item")
    void resourceLocationBracketNullReturnsEmpty() {
        assertEquals("<item:empty>", CommandStringUtil.resourceLocationBracket(null));
    }

    @Test
    @DisplayName("tagBracket with null returns empty item")
    void tagBracketNullReturnsEmpty() {
        assertEquals("<item:empty>", CommandStringUtil.tagBracket(null));
    }
}
