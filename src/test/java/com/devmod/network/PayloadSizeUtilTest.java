package com.devmod.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayloadSizeUtilTest {

    @Test
    @DisplayName("varIntSize returns expected byte lengths")
    void varIntSizeReturnsExpectedLengths() {
        assertEquals(1, PayloadSizeUtil.varIntSize(0));
        assertEquals(1, PayloadSizeUtil.varIntSize(1));
        assertEquals(1, PayloadSizeUtil.varIntSize(127));
        assertEquals(2, PayloadSizeUtil.varIntSize(128));
        assertEquals(2, PayloadSizeUtil.varIntSize(16_383));
        assertEquals(3, PayloadSizeUtil.varIntSize(16_384));
    }

    @Test
    @DisplayName("varLongSize returns expected byte lengths")
    void varLongSizeReturnsExpectedLengths() {
        assertEquals(1, PayloadSizeUtil.varLongSize(0L));
        assertEquals(1, PayloadSizeUtil.varLongSize(1L));
        assertEquals(1, PayloadSizeUtil.varLongSize(127L));
        assertEquals(2, PayloadSizeUtil.varLongSize(128L));
        assertEquals(2, PayloadSizeUtil.varLongSize(16_383L));
        assertEquals(3, PayloadSizeUtil.varLongSize(16_384L));
    }

    @Test
    @DisplayName("estimatedUtfSize accounts for length prefix and bytes")
    void estimatedUtfSizeAccountsForPrefix() {
        assertEquals(PayloadSizeUtil.varIntSize(0), PayloadSizeUtil.estimatedUtfSize(null));
        assertEquals(PayloadSizeUtil.varIntSize(0), PayloadSizeUtil.estimatedUtfSize(""));
        assertEquals(PayloadSizeUtil.varIntSize(1) + 1, PayloadSizeUtil.estimatedUtfSize("a"));
        assertEquals(PayloadSizeUtil.varIntSize(5) + 5, PayloadSizeUtil.estimatedUtfSize("hello"));
    }

    @Test
    @DisplayName("clampToInt bounds values safely")
    void clampToIntBoundsValuesSafely() {
        assertEquals(0, PayloadSizeUtil.clampToInt(-5));
        assertEquals(0, PayloadSizeUtil.clampToInt(0));
        assertEquals(42, PayloadSizeUtil.clampToInt(42));
        assertEquals(Integer.MAX_VALUE, PayloadSizeUtil.clampToInt((long) Integer.MAX_VALUE + 10));
    }
}
