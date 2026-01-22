package com.devmod.network;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

/**
 * Shared helpers for estimating payload sizes for validation.
 */
public final class PayloadSizeUtil {

    private PayloadSizeUtil() {}

    public static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }

    public static int varLongSize(long value) {
        long v = value;
        int size = 1;
        while ((v & ~0x7FL) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }

    public static int estimatedUtfSize(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    public static int clampToInt(long value) {
        if (value <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
