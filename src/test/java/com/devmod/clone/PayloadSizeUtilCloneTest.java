package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.devmod.network.PayloadSizeUtil;

/**
 * Tests for PayloadSizeUtil utility methods used by clone payloads.
 */
class PayloadSizeUtilCloneTest {

    @Nested
    @DisplayName("varIntSize")
    class VarIntSizeTests {

        @Test
        @DisplayName("Zero is 1 byte")
        void zeroIsOneByte() {
            assertEquals(1, PayloadSizeUtil.varIntSize(0));
        }

        @Test
        @DisplayName("Small values (0-127) are 1 byte")
        void smallValues() {
            assertEquals(1, PayloadSizeUtil.varIntSize(1));
            assertEquals(1, PayloadSizeUtil.varIntSize(127));
        }

        @Test
        @DisplayName("Values 128-16383 are 2 bytes")
        void mediumValues() {
            assertEquals(2, PayloadSizeUtil.varIntSize(128));
            assertEquals(2, PayloadSizeUtil.varIntSize(16383));
        }

        @Test
        @DisplayName("Larger values use more bytes")
        void largerValues() {
            assertEquals(3, PayloadSizeUtil.varIntSize(16384));
        }

        @ParameterizedTest
        @CsvSource({
            "0, 1",
            "1, 1",
            "127, 1",
            "128, 2",
            "255, 2",
            "16383, 2",
            "16384, 3"
        })
        @DisplayName("Various varInt sizes")
        void variousSizes(int value, int expectedSize) {
            assertEquals(expectedSize, PayloadSizeUtil.varIntSize(value));
        }
    }

    @Nested
    @DisplayName("varLongSize")
    class VarLongSizeTests {

        @Test
        @DisplayName("Zero is 1 byte")
        void zeroIsOneByte() {
            assertEquals(1, PayloadSizeUtil.varLongSize(0L));
        }

        @Test
        @DisplayName("Small longs (0-127) are 1 byte")
        void smallValues() {
            assertEquals(1, PayloadSizeUtil.varLongSize(1L));
            assertEquals(1, PayloadSizeUtil.varLongSize(127L));
        }

        @Test
        @DisplayName("Large long values use more bytes")
        void largeValues() {
            assertTrue(PayloadSizeUtil.varLongSize(Long.MAX_VALUE) > 1);
        }
    }

    @Nested
    @DisplayName("estimatedUtfSize")
    class EstimatedUtfSizeTests {

        @Test
        @DisplayName("Null string returns varInt(0) size")
        void nullString() {
            assertEquals(PayloadSizeUtil.varIntSize(0), PayloadSizeUtil.estimatedUtfSize(null));
        }

        @Test
        @DisplayName("Empty string returns varInt(0) size")
        void emptyString() {
            assertEquals(PayloadSizeUtil.varIntSize(0), PayloadSizeUtil.estimatedUtfSize(""));
        }

        @Test
        @DisplayName("ASCII string: varInt(length) + length bytes")
        void asciiString() {
            String s = "hello";
            int expected = PayloadSizeUtil.varIntSize(5) + 5;
            assertEquals(expected, PayloadSizeUtil.estimatedUtfSize(s));
        }

        @Test
        @DisplayName("Longer strings have larger estimated size")
        void longerIsLarger() {
            int shortSize = PayloadSizeUtil.estimatedUtfSize("ab");
            int longSize = PayloadSizeUtil.estimatedUtfSize("abcdefghijklmnop");
            assertTrue(longSize > shortSize);
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "ab", "hello", "hello world", "this is a longer test string"})
        @DisplayName("Size is always positive for non-empty strings")
        void alwaysPositive(String s) {
            assertTrue(PayloadSizeUtil.estimatedUtfSize(s) > 0);
        }
    }

    @Nested
    @DisplayName("clampToInt")
    class ClampToIntTests {

        @Test
        @DisplayName("Positive values pass through")
        void positivePassThrough() {
            assertEquals(42, PayloadSizeUtil.clampToInt(42L));
        }

        @Test
        @DisplayName("Zero returns zero")
        void zeroReturnsZero() {
            assertEquals(0, PayloadSizeUtil.clampToInt(0L));
        }

        @Test
        @DisplayName("Negative returns zero")
        void negativeReturnsZero() {
            assertEquals(0, PayloadSizeUtil.clampToInt(-1L));
            assertEquals(0, PayloadSizeUtil.clampToInt(-100L));
        }

        @Test
        @DisplayName("Value exceeding Integer.MAX_VALUE is clamped")
        void exceedingMaxClamped() {
            assertEquals(Integer.MAX_VALUE, PayloadSizeUtil.clampToInt(Long.MAX_VALUE));
        }

        @Test
        @DisplayName("Integer.MAX_VALUE passes through")
        void intMaxPassThrough() {
            assertEquals(Integer.MAX_VALUE, PayloadSizeUtil.clampToInt((long) Integer.MAX_VALUE));
        }
    }
}
