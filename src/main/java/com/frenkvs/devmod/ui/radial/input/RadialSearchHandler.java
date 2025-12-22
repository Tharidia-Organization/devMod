package com.frenkvs.devmod.ui.radial.input;

import com.frenkvs.devmod.ui.radial.RadialCategory;
import com.frenkvs.devmod.ui.radial.RadialMenuItem;
import com.frenkvs.devmod.ui.radial.config.RadialMenuConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Handles fuzzy search functionality for the Radial Menu.
 *
 * <p>This class provides:</p>
 * <ul>
 *   <li>Multi-strategy fuzzy matching (prefix, substring, character sequence)</li>
 *   <li>Configurable scoring weights via {@link RadialMenuConstants}</li>
 *   <li>Result limiting and sorting</li>
 *   <li>Testable without Minecraft runtime</li>
 * </ul>
 *
 * <p>Scoring strategy:</p>
 * <ol>
 *   <li><strong>Prefix match</strong>: Query matches start of name (highest score)</li>
 *   <li><strong>Substring match</strong>: Query found anywhere in name</li>
 *   <li><strong>Description match</strong>: Query found in description (additive)</li>
 *   <li><strong>Fuzzy match</strong>: All query characters found in order (lowest score)</li>
 * </ol>
 */
public final class RadialSearchHandler {

    private RadialSearchHandler() {
        // Utility class - no instantiation
    }

    // ================================================================
    // SEARCH RESULT
    // ================================================================

    /**
     * Represents a search result with its matched item, category, and relevance score.
     */
    public static final class SearchResult {
        private final RadialMenuItem item;
        private final RadialCategory category;
        private final int score;

        /**
         * Creates a new search result.
         *
         * @param item     the matched menu item
         * @param category the category containing the item
         * @param score    the relevance score (higher is better)
         */
        public SearchResult(RadialMenuItem item, RadialCategory category, int score) {
            this.item = Objects.requireNonNull(item, "item cannot be null");
            this.category = Objects.requireNonNull(category, "category cannot be null");
            this.score = score;
        }

        /** Returns the matched menu item. */
        public RadialMenuItem getItem() {
            return item;
        }

        /** Returns the category containing the matched item. */
        public RadialCategory getCategory() {
            return category;
        }

        /** Returns the relevance score (higher is better). */
        public int getScore() {
            return score;
        }
    }

    // ================================================================
    // SEARCH API
    // ================================================================

    /**
     * Searches all items in the given categories for matches to the query.
     *
     * @param query      the search query (case-insensitive)
     * @param categories the categories to search through
     * @return sorted list of results (highest score first), limited to MAX_SEARCH_RESULTS
     */
    public static List<SearchResult> search(String query, Collection<RadialCategory> categories) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(categories, "categories cannot be null");

        if (query.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedQuery = query.toLowerCase();
        List<SearchResult> results = new ArrayList<>();

        Set<RadialCategory> visited = new HashSet<>();
        for (RadialCategory cat : categories) {
            collectMatches(normalizedQuery, cat, results, visited);
        }

        // Sort by score descending
        results.sort((a, b) -> b.score - a.score);

        // Limit results
        if (results.size() > RadialMenuConstants.MAX_SEARCH_RESULTS) {
            return new ArrayList<>(results.subList(0, RadialMenuConstants.MAX_SEARCH_RESULTS));
        }

        return results;
    }

    // ================================================================
    // SCORING LOGIC
    // ================================================================

    /**
     * Calculates the relevance score for an item against a query.
     *
     * @param query the normalized (lowercase) search query
     * @param item  the item to score
     * @return the score (0 if no match, higher is better)
     */
    public static int calculateScore(String query, RadialMenuItem item) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(item, "item cannot be null");

        String name = item.getName().toLowerCase();
        String desc = item.getDescription().toLowerCase();

        int score = 0;

        // Strategy 1: Prefix match (highest priority)
        if (name.startsWith(query)) {
            score += RadialMenuConstants.SEARCH_PREFIX_SCORE;
        }
        // Strategy 2: Substring match
        else if (name.contains(query)) {
            score += RadialMenuConstants.SEARCH_SUBSTRING_SCORE;
        }

        // Strategy 3: Description match (additive)
        if (desc.contains(query)) {
            score += RadialMenuConstants.SEARCH_DESCRIPTION_SCORE;
        }

        // Strategy 4: Character-by-character fuzzy match (fallback)
        if (score == 0) {
            score = calculateFuzzyScore(query, name);
        }

        return score;
    }

    private static void collectMatches(String query, RadialCategory category,
                                       List<SearchResult> results, Set<RadialCategory> visited) {
        if (!visited.add(category)) {
            return;
        }

        for (RadialMenuItem item : category.getVisibleItems()) {
            int score = calculateScore(query, item);
            if (score > 0) {
                results.add(new SearchResult(item, category, score));
            }
            if (item.isSubcategoryLink()) {
                RadialCategory subcategory = item.getLinkedSubcategory();
                if (subcategory != null) {
                    collectMatches(query, subcategory, results, visited);
                }
            }
        }
    }

    /**
     * Calculates a fuzzy match score based on character sequence matching.
     *
     * <p>All characters in the query must appear in the name in order,
     * but not necessarily consecutively. The score is based on how
     * closely the name length matches the query length.</p>
     *
     * @param query the search query
     * @param name  the item name to match against
     * @return the fuzzy score (0 if no match)
     */
    public static int calculateFuzzyScore(String query, String name) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(name, "name cannot be null");

        if (query.isEmpty()) {
            return 0;
        }

        int matchedChars = 0;
        int lastIndex = -1;

        for (char c : query.toCharArray()) {
            int idx = name.indexOf(c, lastIndex + 1);
            if (idx > lastIndex) {
                matchedChars++;
                lastIndex = idx;
            }
        }

        // All characters must match in sequence
        if (matchedChars == query.length()) {
            // Score based on how close the lengths are (shorter names score higher)
            int lengthPenalty = Math.min(RadialMenuConstants.SEARCH_FUZZY_BASE_SCORE,
                name.length() - query.length());
            return RadialMenuConstants.SEARCH_FUZZY_BASE_SCORE +
                (RadialMenuConstants.SEARCH_FUZZY_BASE_SCORE - lengthPenalty);
        }

        return 0;
    }

    // ================================================================
    // UTILITY METHODS
    // ================================================================

    /**
     * Checks if a query matches an item (any match strategy).
     *
     * @param query the search query
     * @param item  the item to check
     * @return true if the item matches the query
     */
    public static boolean matches(String query, RadialMenuItem item) {
        return calculateScore(query.toLowerCase(), item) > 0;
    }

    /**
     * Returns the best matching result from a search, or null if no matches.
     *
     * @param query      the search query
     * @param categories the categories to search
     * @return the best match, or null if none found
     */
    public static SearchResult findBest(String query, Collection<RadialCategory> categories) {
        List<SearchResult> results = search(query, categories);
        return results.isEmpty() ? null : results.get(0);
    }
}
