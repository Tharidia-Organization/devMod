package com.devmod.zone.data;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

/**
 * Validation and naming conventions for zones.
 * Enforces consistent naming across the zone system.
 */
public final class ZoneNaming {
    private ZoneNaming() {}

    // ========================================================================
    // Constants
    // ========================================================================

    /** Maximum length for zone IDs */
    public static final int MAX_ID_LENGTH = 32;

    /** Maximum length for display names */
    public static final int MAX_NAME_LENGTH = 48;

    /** Maximum length for hint messages */
    public static final int MAX_HINT_LENGTH = 128;

    /** Pattern for valid zone IDs: lowercase alphanumeric + underscore */
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_]{1,32}$");

    /** Reserved prefixes that cannot be used by user-created zones */
    private static final Set<String> RESERVED_PREFIXES = Set.of("system_", "preset_");

    /** Reserved zone IDs that cannot be used by user-created zones */
    private static final Set<String> RESERVED_IDS = Set.of("outside", "unknown", "none");

    // ========================================================================
    // Validation
    // ========================================================================

    /**
     * Validates a zone ID.
     *
     * @param id The zone ID to validate
     * @return true if the ID is valid
     */
    public static boolean isValidZoneId(@Nonnull String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }

        // Check pattern
        if (!ID_PATTERN.matcher(id).matches()) {
            return false;
        }

        // Check reserved IDs
        if (RESERVED_IDS.contains(id)) {
            return false;
        }

        // Check reserved prefixes
        for (String prefix : RESERVED_PREFIXES) {
            if (id.startsWith(prefix)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validates a display name.
     *
     * @param name The display name to validate
     * @return true if the name is valid
     */
    public static boolean isValidDisplayName(@Nonnull String name) {
        return name != null && !name.isEmpty() && name.length() <= MAX_NAME_LENGTH;
    }

    /**
     * Validates a hint message.
     *
     * @param hint The hint message to validate
     * @return true if the hint is valid
     */
    public static boolean isValidHintMessage(@Nonnull String hint) {
        return hint != null && hint.length() <= MAX_HINT_LENGTH;
    }

    // ========================================================================
    // Normalization
    // ========================================================================

    /**
     * Normalizes a zone ID (lowercase, trim, replace invalid chars).
     *
     * @param id The input ID
     * @return Normalized ID (may still be invalid if too long or reserved)
     */
    @Nonnull
    public static String normalizeZoneId(@Nonnull String id) {
        if (id == null) {
            return "";
        }

        return id.toLowerCase(Locale.ROOT)
            .trim()
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    }

    /**
     * Converts a display name to a suggested zone ID.
     *
     * @param displayName The display name
     * @return Suggested zone ID
     */
    @Nonnull
    public static String displayNameToZoneId(@Nonnull String displayName) {
        String normalized = normalizeZoneId(displayName);
        if (normalized.length() > MAX_ID_LENGTH) {
            normalized = normalized.substring(0, MAX_ID_LENGTH);
        }
        return normalized.isEmpty() ? "zone" : normalized;
    }

    // ========================================================================
    // Error Messages
    // ========================================================================

    /**
     * Gets an error message for an invalid zone ID.
     *
     * @param id The invalid ID
     * @return Human-readable error message
     */
    @Nonnull
    public static String getZoneIdError(@Nonnull String id) {
        if (id == null || id.isEmpty()) {
            return "Zone ID cannot be empty";
        }
        if (id.length() > MAX_ID_LENGTH) {
            return "Zone ID too long (max " + MAX_ID_LENGTH + " characters)";
        }
        if (!ID_PATTERN.matcher(id).matches()) {
            return "Zone ID must be lowercase letters, numbers, and underscores only";
        }
        if (RESERVED_IDS.contains(id)) {
            return "Zone ID '" + id + "' is reserved";
        }
        for (String prefix : RESERVED_PREFIXES) {
            if (id.startsWith(prefix)) {
                return "Zone ID cannot start with '" + prefix + "'";
            }
        }
        return "Unknown error";
    }
}
