package com.devmod.arena.registry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates arena template tags with typo detection (DD28).
 *
 * <p>Provides:
 * <ul>
 *   <li>Predefined tag dictionary for common categories</li>
 *   <li>Levenshtein distance-based typo detection (threshold ≤ 2)</li>
 *   <li>Autocomplete suggestions (max 5) for unknown tags</li>
 *   <li>Telemetry integration for tracking tag usage</li>
 * </ul>
 *
 * <p>Predefined tag categories:
 * <ul>
 *   <li><b>Difficulty:</b> EASY, MEDIUM, HARD, NIGHTMARE</li>
 *   <li><b>Environment:</b> INDOOR, OUTDOOR, UNDERGROUND, NETHER, END</li>
 *   <li><b>Size:</b> SMALL, MEDIUM_SIZE, LARGE, HUGE</li>
 *   <li><b>Type:</b> BOSS, WAVE, PVP, PUZZLE, SURVIVAL</li>
 *   <li><b>Special:</b> TUTORIAL, EVENT, SEASONAL</li>
 * </ul>
 */
public class TagValidator {

    /** Maximum Levenshtein distance for typo suggestions (DD28). */
    private static final int TYPO_THRESHOLD = 2;

    /** Maximum number of suggestions to return. */
    private static final int MAX_SUGGESTIONS = 5;

    /**
     * Predefined tags organized by category.
     */
    public static final Set<String> PREDEFINED_TAGS = Set.of(
        // Difficulty
        "easy", "medium", "hard", "nightmare",
        // Environment
        "indoor", "outdoor", "underground", "nether", "end", "overworld",
        // Size
        "small", "medium_size", "large", "huge", "tiny",
        // Type
        "boss", "wave", "pvp", "puzzle", "survival", "defense",
        // Special
        "tutorial", "event", "seasonal", "limited_time",
        // Mob categories
        "undead", "arthropod", "aquatic", "illager", "nether_mob", "end_mob",
        // Misc
        "lava", "water", "hazardous", "peaceful", "no_mobs"
    );

    private final Set<String> customTags = new HashSet<>();
    private final Telemetry telemetry;

    public TagValidator() {
        this(null);
    }

    public TagValidator(@Nullable Telemetry telemetry) {
        this.telemetry = telemetry;
    }

    /**
     * Registers a custom tag as valid (for mod-specific tags).
     */
    public void registerCustomTag(String tag) {
        if (tag != null && !tag.isBlank()) {
            customTags.add(tag.toLowerCase());
        }
    }

    /**
     * Registers multiple custom tags.
     */
    public void registerCustomTags(Collection<String> tags) {
        if (tags != null) {
            tags.forEach(this::registerCustomTag);
        }
    }

    /**
     * Validates a list of tags, returning validation result with suggestions.
     */
    public ValidationResult validate(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return ValidationResult.ok();
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, List<String>> suggestions = new LinkedHashMap<>();

        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                errors.add("Empty or null tag found");
                continue;
            }

            String normalized = tag.toLowerCase().trim();

            if (isKnownTag(normalized)) {
                // Valid tag
                continue;
            }

            // Unknown tag - check for typos
            List<String> typoSuggestions = findSuggestions(normalized);

            if (!typoSuggestions.isEmpty()) {
                warnings.add("Unknown tag '%s' - did you mean: %s?".formatted(
                    tag, String.join(", ", typoSuggestions)));
                suggestions.put(tag, typoSuggestions);

                if (telemetry != null) {
                    telemetry.onTypoDetected(tag, typoSuggestions);
                }
            } else {
                warnings.add("Unknown tag '%s' (no similar tags found)".formatted(tag));

                if (telemetry != null) {
                    telemetry.onUnknownTag(tag);
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings, suggestions);
    }

    /**
     * Checks if a tag is known (predefined or custom).
     */
    public boolean isKnownTag(String tag) {
        if (tag == null) return false;
        String normalized = tag.toLowerCase().trim();
        return PREDEFINED_TAGS.contains(normalized) || customTags.contains(normalized);
    }

    /**
     * Returns all known tags (predefined + custom).
     */
    public Set<String> getAllKnownTags() {
        Set<String> all = new HashSet<>(PREDEFINED_TAGS);
        all.addAll(customTags);
        return Collections.unmodifiableSet(all);
    }

    /**
     * Finds suggestion for a possibly misspelled tag using Levenshtein distance.
     */
    public List<String> findSuggestions(String unknownTag) {
        if (unknownTag == null || unknownTag.isBlank()) {
            return List.of();
        }

        String normalized = unknownTag.toLowerCase().trim();
        List<ScoredTag> candidates = new ArrayList<>();

        for (String knownTag : getAllKnownTags()) {
            int distance = levenshteinDistance(normalized, knownTag);
            if (distance <= TYPO_THRESHOLD) {
                candidates.add(new ScoredTag(knownTag, distance));
            }
        }

        // Sort by distance (ascending), then alphabetically
        candidates.sort(Comparator
            .comparingInt(ScoredTag::distance)
            .thenComparing(ScoredTag::tag));

        return candidates.stream()
            .limit(MAX_SUGGESTIONS)
            .map(ScoredTag::tag)
            .toList();
    }

    /**
     * Computes Levenshtein distance between two strings.
     * Uses dynamic programming O(m*n) algorithm.
     */
    static int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return s1 == s2 ? 0 : Integer.MAX_VALUE;
        }

        int m = s1.length();
        int n = s2.length();

        // Early exit for identical strings
        if (s1.equals(s2)) return 0;

        // Early exit for empty strings
        if (m == 0) return n;
        if (n == 0) return m;

        // Use two rows for space optimization
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        // Initialize first row
        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        // Fill matrix
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                    Math.min(prev[j] + 1, curr[j - 1] + 1),
                    prev[j - 1] + cost
                );
            }
            // Swap rows
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[n];
    }

    // === Records ===

    private record ScoredTag(String tag, int distance) {}

    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        Map<String, List<String>> suggestions
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of(), Map.of());
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean hasSuggestions() {
            return !suggestions.isEmpty();
        }
    }

    /**
     * Telemetry interface for tag validation events.
     */
    public interface Telemetry {
        void onTypoDetected(String tag, List<String> suggestions);
        void onUnknownTag(String tag);
    }
}
