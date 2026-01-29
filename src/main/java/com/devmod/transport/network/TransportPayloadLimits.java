package com.devmod.transport.network;

import javax.annotation.Nullable;

/**
 * Shared limits and helpers for transport network payloads.
 */
final class TransportPayloadLimits {
    private TransportPayloadLimits() {
    }

    static final int MAX_NETWORK_NAME_LENGTH = 64;
    static final int MAX_DISPLAY_NAME_LENGTH = 48;
    static final int MAX_DESTINATION_NAME_LENGTH = 48;
    static final int MAX_DIMENSION_LENGTH = 128;
    static final int MAX_NODES = 200;
    static final int MAX_PARTY_MEMBERS = 128;

    static int clampCount(int count, int max) {
        if (count <= 0) {
            return 0;
        }
        return Math.min(count, max);
    }

    static int clampRange(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    static String truncate(@Nullable String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Nullable
    static String truncateNullable(@Nullable String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
