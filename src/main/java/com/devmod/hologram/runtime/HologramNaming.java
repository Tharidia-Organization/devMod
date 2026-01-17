package com.devmod.hologram.runtime;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Naming conventions and validation for holograms (Regola 7 - Bibbia Estetica).
 *
 * <p>Hologram ID format:
 * <ul>
 *   <li>Format: snake_case</li>
 *   <li>Max length: 32 characters</li>
 *   <li>Allowed characters: a-z, 0-9, _</li>
 *   <li>Reserved prefixes: system_, preset_</li>
 * </ul>
 *
 * <p>Line content:
 * <ul>
 *   <li>Format: UTF-8</li>
 *   <li>Max length: 128 characters per line</li>
 *   <li>Supports color codes (section sign)</li>
 *   <li>Supports placeholders: {player}, {online}, {tps}, etc.</li>
 * </ul>
 */
public final class HologramNaming {
    private HologramNaming() {}

    /**
     * Maximum number of lines per hologram.
     */
    public static final int MAX_LINES = 16;

    /**
     * Maximum characters per line.
     */
    public static final int MAX_LINE_LENGTH = 128;

    /**
     * Maximum length for hologram ID.
     */
    public static final int MAX_ID_LENGTH = 32;

    /**
     * Maximum length for hologram label.
     */
    public static final int MAX_LABEL_LENGTH = 48;

    /**
     * Pattern for valid hologram IDs.
     */
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_]{1,32}$");

    /**
     * Reserved prefixes that cannot be used in user-created hologram IDs.
     */
    private static final Set<String> RESERVED_PREFIXES = Set.of("system_", "preset_");

    /**
     * Validates a hologram ID.
     *
     * @param id The ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidHologramId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }

        // Check pattern match
        if (!ID_PATTERN.matcher(id).matches()) {
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
     * Validates a hologram label.
     *
     * @param label The label to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidLabel(String label) {
        return label != null && !label.isEmpty() && label.length() <= MAX_LABEL_LENGTH;
    }

    /**
     * Validates a single line of hologram content.
     *
     * @param line The line to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidLineContent(String line) {
        return line != null && line.length() <= MAX_LINE_LENGTH;
    }

    /**
     * Validates the total number of lines.
     *
     * @param lineCount The number of lines
     * @return true if within limit, false otherwise
     */
    public static boolean isValidLineCount(int lineCount) {
        return lineCount >= 0 && lineCount <= MAX_LINES;
    }

    /**
     * Truncates a line to the maximum length if necessary.
     *
     * @param line The line to truncate
     * @return The truncated line
     */
    public static String truncateLine(String line) {
        if (line == null) {
            return "";
        }
        if (line.length() <= MAX_LINE_LENGTH) {
            return line;
        }
        return line.substring(0, MAX_LINE_LENGTH);
    }

    /**
     * Truncates a label to the maximum length if necessary.
     *
     * @param label The label to truncate
     * @return The truncated label
     */
    public static String truncateLabel(String label) {
        if (label == null) {
            return "";
        }
        if (label.length() <= MAX_LABEL_LENGTH) {
            return label;
        }
        return label.substring(0, MAX_LABEL_LENGTH);
    }

    /**
     * Generates a valid ID from a label.
     *
     * @param label The label to convert
     * @return A valid ID based on the label
     */
    public static String generateIdFromLabel(String label) {
        if (label == null || label.isEmpty()) {
            return "hologram";
        }

        // Convert to lowercase, replace spaces with underscores, remove invalid chars
        String id = label.toLowerCase(Locale.ROOT)
            .replace(' ', '_')
            .replaceAll("[^a-z0-9_]", "");

        // Ensure not empty after cleaning
        if (id.isEmpty()) {
            return "hologram";
        }

        // Truncate if too long
        if (id.length() > MAX_ID_LENGTH) {
            id = id.substring(0, MAX_ID_LENGTH);
        }

        // Ensure doesn't start with reserved prefix
        for (String prefix : RESERVED_PREFIXES) {
            if (id.startsWith(prefix)) {
                id = "h_" + id;
                if (id.length() > MAX_ID_LENGTH) {
                    id = id.substring(0, MAX_ID_LENGTH);
                }
                break;
            }
        }

        return id;
    }

    /**
     * Returns the set of reserved prefixes.
     */
    public static Set<String> getReservedPrefixes() {
        return RESERVED_PREFIXES;
    }
}
