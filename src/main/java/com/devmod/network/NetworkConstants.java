package com.devmod.network;

import javax.annotation.Nullable;

/**
 * Centralized constants for network payload validation.
 * Domain-specific limits (TransportPayloadLimits, MailboxPayloadLimits, etc.)
 * should reference these tiers where applicable.
 *
 * <p>String Length Tiers:
 * <ul>
 *   <li>SMALL (32): IDs, codes, short keys</li>
 *   <li>MEDIUM (256): Names, titles, short descriptions</li>
 *   <li>LARGE (1024): Full descriptions, formatted text</li>
 *   <li>XLARGE (4096): JSON blobs, serialized data</li>
 * </ul>
 *
 * <p>Array Size Tiers:
 * <ul>
 *   <li>SMALL (20): Player inventory slots, party members</li>
 *   <li>MEDIUM (50): Quest objectives, dialog options</li>
 *   <li>LARGE (100): List entries, news articles</li>
 *   <li>XLARGE (200): Full collections, node lists</li>
 * </ul>
 */
public final class NetworkConstants {

    private NetworkConstants() {
    }

    // =========================================================================
    // String Length Tiers
    // =========================================================================

    /** IDs, codes, short keys (32 chars) */
    public static final int MAX_SMALL_STRING = 32;

    /** Names, titles, short descriptions (256 chars) */
    public static final int MAX_MEDIUM_STRING = 256;

    /** Full descriptions, formatted text (1024 chars) */
    public static final int MAX_LARGE_STRING = 1024;

    /** JSON blobs, serialized data (4096 chars) */
    public static final int MAX_XLARGE_STRING = 4096;

    // =========================================================================
    // Semantic Aliases - Common Use Cases
    // =========================================================================

    /** Short identifier strings (32 chars) */
    public static final int MAX_ID_LENGTH = MAX_SMALL_STRING;

    /** Display names for players, items, etc. (64 chars) */
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;

    /** Title strings for quests, dialogs, etc. (128 chars) */
    public static final int MAX_TITLE_LENGTH = 128;

    /** Description text (1024 chars) */
    public static final int MAX_DESCRIPTION_LENGTH = MAX_LARGE_STRING;

    /** ResourceLocation dimension strings (128 chars) */
    public static final int MAX_DIMENSION_LENGTH = 128;

    /** Command strings (256 chars) */
    public static final int MAX_COMMAND_LENGTH = MAX_MEDIUM_STRING;

    // =========================================================================
    // Array Size Tiers
    // =========================================================================

    /** Small collections: party members, inventory slots (20 items) */
    public static final int MAX_SMALL_ARRAY = 20;

    /** Medium collections: quest objectives, options (50 items) */
    public static final int MAX_MEDIUM_ARRAY = 50;

    /** Large collections: list entries, articles (100 items) */
    public static final int MAX_LARGE_ARRAY = 100;

    /** Extra large collections: full node lists (200 items) */
    public static final int MAX_XLARGE_ARRAY = 200;

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Truncates a string to the specified maximum length.
     * Returns empty string for null input.
     *
     * @param value the string to truncate (may be null)
     * @param maxLength maximum allowed length
     * @return truncated string, never null
     */
    public static String truncate(@Nullable String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * Truncates a string to the specified maximum length, preserving null.
     *
     * @param value the string to truncate (may be null)
     * @param maxLength maximum allowed length
     * @return truncated string, or null if input was null
     */
    @Nullable
    public static String truncateNullable(@Nullable String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * Clamps a count to a maximum value, treating negatives as zero.
     *
     * @param count the count to clamp
     * @param max maximum allowed value
     * @return clamped count in range [0, max]
     */
    public static int clampCount(int count, int max) {
        if (count <= 0) {
            return 0;
        }
        return Math.min(count, max);
    }

    /**
     * Clamps a value to a range.
     *
     * @param value the value to clamp
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @return clamped value in range [min, max]
     */
    public static int clampRange(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
