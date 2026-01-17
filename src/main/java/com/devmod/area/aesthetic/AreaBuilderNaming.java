package com.devmod.area.aesthetic;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

/**
 * BIBBIA ESTETICA - REGOLA 6: NAMING CONVENTIONS
 *
 * Standard di nomenclatura obbligatori per aree e identificatori.
 * Questi pattern garantiscono coerenza e compatibilità nel sistema.
 */
public final class AreaBuilderNaming {

    private AreaBuilderNaming() {}

    // ============================================================================
    // ID PATTERNS
    // ============================================================================

    /**
     * Pattern for valid area IDs: snake_case, solo [a-z0-9_].
     * Must start with letter, 3-32 characters.
     */
    public static final Pattern AREA_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,31}$");

    /**
     * Pattern for simple alphanumeric names.
     */
    public static final Pattern SIMPLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    // ============================================================================
    // DISPLAY NAME LIMITS
    // ============================================================================

    /** Minimum length for area IDs */
    public static final int MIN_AREA_ID_LENGTH = 3;

    /** Maximum length for area IDs */
    public static final int MAX_AREA_ID_LENGTH = 32;

    /** Maximum length for display names (UTF-8) */
    public static final int MAX_DISPLAY_NAME_LENGTH = 48;

    /** Minimum length for display names */
    public static final int MIN_DISPLAY_NAME_LENGTH = 3;

    /** Maximum length for area descriptions */
    public static final int MAX_DESCRIPTION_LENGTH = 256;

    // ============================================================================
    // PREFIXES
    // ============================================================================

    /** Prefix for preset areas */
    public static final String PRESET_PREFIX = "preset_";

    /** Prefix for custom user areas */
    public static final String CUSTOM_PREFIX = "custom_";

    /** Prefix for legacy migrated areas */
    public static final String LEGACY_PREFIX = "legacy_";

    /** Prefix for system areas */
    public static final String SYSTEM_PREFIX = "system_";

    /** Prefix for temporary areas */
    public static final String TEMP_PREFIX = "temp_";

    // ============================================================================
    // RESERVED NAMES
    // ============================================================================

    /**
     * Reserved names that cannot be used for area IDs.
     * These are reserved for system use.
     */
    public static final Set<String> RESERVED_NAMES = Set.of(
        "hub",
        "main",
        "spawn",
        "nexus",
        "central",
        "editor",
        "system",
        "admin",
        "default",
        "null",
        "undefined",
        "new",
        "create",
        "delete",
        "update"
    );

    // ============================================================================
    // DEFAULT NAMES
    // ============================================================================

    /** Default name for new areas */
    public static final String DEFAULT_AREA_NAME = "New Area";

    /** Default name for main hub */
    public static final String DEFAULT_HUB_NAME = "Main Hub";

    /** Default name for legacy hub */
    public static final String LEGACY_HUB_NAME = "Legacy Hub";

    // ============================================================================
    // VALIDATION METHODS
    // ============================================================================

    /**
     * Validates an area ID.
     *
     * @return null if valid, localized error message if invalid
     */
    @Nullable
    public static Component validateAreaId(String id) {
        if (id == null || id.isEmpty()) {
            return Component.translatable("area.builder.error.id.empty");
        }

        if (id.length() < MIN_AREA_ID_LENGTH) {
            return Component.translatable("area.builder.error.id.too_short", MIN_AREA_ID_LENGTH);
        }

        if (id.length() > MAX_AREA_ID_LENGTH) {
            return Component.translatable("area.builder.error.id.too_long", MAX_AREA_ID_LENGTH);
        }

        if (!AREA_ID_PATTERN.matcher(id).matches()) {
            return Component.translatable("area.builder.error.id.invalid_format");
        }

        if (RESERVED_NAMES.contains(id.toLowerCase(Locale.ROOT))) {
            return Component.translatable("area.builder.error.id.reserved", id);
        }

        return null; // Valid
    }

    /**
     * Validates a display name.
     *
     * @return null if valid, localized error message if invalid
     */
    @Nullable
    public static Component validateDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return Component.translatable("area.builder.error.name.empty");
        }

        String trimmed = name.trim();

        if (trimmed.length() < MIN_DISPLAY_NAME_LENGTH) {
            return Component.translatable("area.builder.error.name.too_short", MIN_DISPLAY_NAME_LENGTH);
        }

        if (trimmed.length() > MAX_DISPLAY_NAME_LENGTH) {
            return Component.translatable("area.builder.error.name.too_long", MAX_DISPLAY_NAME_LENGTH);
        }

        return null; // Valid
    }

    /**
     * Checks if a name is reserved.
     */
    public static boolean isReservedName(String name) {
        return name != null && RESERVED_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Sanitizes a string to be used as an area ID.
     * Converts to lowercase, replaces spaces with underscores, removes invalid characters.
     */
    public static String sanitizeToId(String input) {
        if (input == null || input.isEmpty()) {
            return "area";
        }

        String sanitized = input.toLowerCase(Locale.ROOT)
            .trim()
            .replaceAll("\\s+", "_")
            .replaceAll("[^a-z0-9_]", "");

        // Ensure starts with letter
        if (!sanitized.isEmpty() && !Character.isLetter(sanitized.charAt(0))) {
            sanitized = "area_" + sanitized;
        }

        // Ensure minimum length
        if (sanitized.length() < 3) {
            sanitized = sanitized + "_area";
        }

        // Truncate if too long
        if (sanitized.length() > MAX_AREA_ID_LENGTH) {
            sanitized = sanitized.substring(0, MAX_AREA_ID_LENGTH);
        }

        return sanitized;
    }

    /**
     * Generates a unique ID by appending a number if the base ID exists.
     */
    public static String generateUniqueId(String baseId, java.util.function.Predicate<String> existsCheck) {
        String id = sanitizeToId(baseId);

        if (!existsCheck.test(id)) {
            return id;
        }

        int counter = 1;
        String numberedId;
        do {
            numberedId = id + "_" + counter;
            counter++;
        } while (existsCheck.test(numberedId) && counter < 1000);

        return numberedId;
    }
}
