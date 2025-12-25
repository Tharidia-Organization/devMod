package com.devmod.arena.tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Predefined tags for arena templates (DD28).
 *
 * <p>Provides autocomplete and typo detection for template tags.
 */
public enum PredefinedTag {

    // Layout tags
    FLAT("flat", "Flat arena with no elevation"),
    RING("ring", "Circular or ring-shaped arena"),
    MULTILEVEL("multilevel", "Arena with multiple vertical levels"),
    SYMMETRIC("symmetric", "Symmetrical layout"),

    // Combat style tags
    MELEE_FRIENDLY("melee_friendly", "Suitable for melee combat"),
    RANGED_FRIENDLY("ranged_friendly", "Suitable for ranged combat"),
    MIXED_COMBAT("mixed_combat", "Suitable for both melee and ranged"),

    // Hazard tags
    HAZARD("hazard", "Contains environmental hazards"),
    LAVA("lava", "Contains lava hazards"),
    VOID("void", "Contains void/fall hazards"),

    // Special tags
    BOSS("boss", "Designed for boss encounters"),
    PVP("pvp", "Designed for PvP combat"),
    SMOKE("smoke", "Used for smoke/autosmoke testing"),
    SEASONAL("seasonal", "Seasonal/event arena"),

    // Size tags
    SMALL("small", "Small arena (< 32 blocks)"),
    MEDIUM("medium", "Medium arena (32-64 blocks)"),
    LARGE("large", "Large arena (> 64 blocks)");

    private static final Map<String, PredefinedTag> BY_VALUE;
    private static final List<String> ALL_VALUES;

    static {
        Map<String, PredefinedTag> map = new HashMap<>();
        List<String> values = new ArrayList<>();
        for (PredefinedTag tag : values()) {
            map.put(tag.value, tag);
            values.add(tag.value);
        }
        BY_VALUE = Collections.unmodifiableMap(map);
        ALL_VALUES = Collections.unmodifiableList(values);
    }

    private final String value;
    private final String description;

    PredefinedTag(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }

    /**
     * Finds a tag by its value (case-insensitive).
     */
    public static Optional<PredefinedTag> fromValue(String value) {
        if (value == null) return Optional.empty();
        return Optional.ofNullable(BY_VALUE.get(value.toLowerCase()));
    }

    /**
     * Checks if a value is a known predefined tag.
     */
    public static boolean isKnown(String value) {
        return value != null && BY_VALUE.containsKey(value.toLowerCase());
    }

    /**
     * Returns all predefined tag values.
     */
    public static List<String> allValues() {
        return ALL_VALUES;
    }

    /**
     * Autocomplete: returns tags that start with the given prefix.
     */
    public static List<String> autocomplete(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return ALL_VALUES;
        }

        String lowerPrefix = prefix.toLowerCase();
        return ALL_VALUES.stream()
            .filter(v -> v.startsWith(lowerPrefix))
            .toList();
    }

    /**
     * Finds similar tags for typo detection (Levenshtein distance <= 2).
     */
    public static List<String> findSimilar(String input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        String lowerInput = input.toLowerCase();
        return ALL_VALUES.stream()
            .filter(v -> levenshteinDistance(lowerInput, v) <= 2 && !v.equals(lowerInput))
            .toList();
    }

    /**
     * Validates a tag and returns suggestions if invalid.
     */
    public static TagValidationResult validate(String tag) {
        if (tag == null || tag.isEmpty()) {
            return TagValidationResult.invalid("Tag cannot be empty", List.of());
        }

        String lower = tag.toLowerCase();

        // Check if known
        if (BY_VALUE.containsKey(lower)) {
            return TagValidationResult.valid(BY_VALUE.get(lower));
        }

        // Find similar tags for suggestions
        List<String> similar = findSimilar(lower);
        if (!similar.isEmpty()) {
            return TagValidationResult.unknown(
                "Unknown tag '%s'. Did you mean: %s?".formatted(tag, String.join(", ", similar)),
                similar
            );
        }

        // No suggestions
        return TagValidationResult.unknown(
            "Unknown tag '%s'. Use a predefined tag for best compatibility.".formatted(tag),
            List.of()
        );
    }

    /**
     * Validates multiple tags.
     */
    public static List<TagValidationResult> validateAll(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        return tags.stream()
            .map(PredefinedTag::validate)
            .toList();
    }

    /**
     * Calculates Levenshtein distance between two strings.
     */
    private static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                    );
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Result of tag validation.
     */
    public record TagValidationResult(
        TagStatus status,
        String message,
        PredefinedTag matchedTag,
        List<String> suggestions
    ) {
        public enum TagStatus {
            VALID,      // Known predefined tag
            UNKNOWN,    // Unknown but allowed (custom tag)
            INVALID     // Not allowed
        }

        public boolean isValid() {
            return status == TagStatus.VALID;
        }

        public boolean isUnknown() {
            return status == TagStatus.UNKNOWN;
        }

        public static TagValidationResult valid(PredefinedTag tag) {
            return new TagValidationResult(TagStatus.VALID, null, tag, List.of());
        }

        public static TagValidationResult unknown(String message, List<String> suggestions) {
            return new TagValidationResult(TagStatus.UNKNOWN, message, null, suggestions);
        }

        public static TagValidationResult invalid(String message, List<String> suggestions) {
            return new TagValidationResult(TagStatus.INVALID, message, null, suggestions);
        }
    }
}
