package com.devmod.transport.network;

import javax.annotation.Nullable;

import com.devmod.network.NetworkConstants;

/**
 * Shared limits and helpers for transport network payloads.
 * References {@link NetworkConstants} for standard tiers.
 */
final class TransportPayloadLimits {
    private TransportPayloadLimits() {
    }

    // String length limits - referencing NetworkConstants tiers
    static final int MAX_NETWORK_NAME_LENGTH = NetworkConstants.MAX_DISPLAY_NAME_LENGTH; // 64
    static final int MAX_DISPLAY_NAME_LENGTH = 48; // Domain-specific (shorter than standard)
    static final int MAX_DESTINATION_NAME_LENGTH = 48; // Domain-specific
    static final int MAX_DIMENSION_LENGTH = NetworkConstants.MAX_DIMENSION_LENGTH; // 128

    // Array size limits - referencing NetworkConstants tiers
    static final int MAX_NODES = NetworkConstants.MAX_XLARGE_ARRAY; // 200
    static final int MAX_PARTY_MEMBERS = 128; // Domain-specific (between LARGE and XLARGE)

    // Utility methods - delegate to NetworkConstants
    static int clampCount(int count, int max) {
        return NetworkConstants.clampCount(count, max);
    }

    static int clampRange(int value, int min, int max) {
        return NetworkConstants.clampRange(value, min, max);
    }

    static String truncate(@Nullable String value, int maxLength) {
        return NetworkConstants.truncate(value, maxLength);
    }

    @Nullable
    static String truncateNullable(@Nullable String value, int maxLength) {
        return NetworkConstants.truncateNullable(value, maxLength);
    }
}
