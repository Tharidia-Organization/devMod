package com.devmod.arena.gamification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public final class PerkSuggestionEngine {

    /**
     * Perk category for classification.
     */
    public enum PerkCategory {
        SUGGESTED,
        POPULAR,
        NEW,
        PERSONALIZED
    }

    /**
     * Represents a perk that can be suggested to players.
     */
    public record Perk(
        String id,
        String name,
        String description,
        PerkCategory category,
        double winRate,
        int usageCount
    ) {}

    /**
     * Result of perk suggestions including A/B test variant.
     */
    public record SuggestionResult(
        List<Perk> perks,
        boolean isTestVariant,
        String variantId
    ) {}

    /**
     * Weekly winrate analysis result from DuckDB.
     */
    public record WeeklyWinrateAnalysis(
        String perkId,
        double winRate,
        int matchesPlayed,
        int wins,
        long weekStartEpoch
    ) {}

    private static final double AB_TEST_RATIO = 0.10; // 10% A/B test
    private static final String AB_TEST_SALT = "perk_suggestion_ab_v1";

    private final RandomGenerator random;

    /**
     * Creates a PerkSuggestionEngine with the default random generator.
     */
    public PerkSuggestionEngine() {
        this(RandomGeneratorFactory.getDefault().create());
    }

    /**
     * Creates a PerkSuggestionEngine with a specified random generator.
     * Useful for testing with deterministic random.
     *
     * @param random the random generator to use
     */
    public PerkSuggestionEngine(RandomGenerator random) {
        this.random = random;
    }

    /**
     * Get perk suggestions for a player with shuffle to eliminate position bias.
     * Implements DD51: shuffle SUGGESTED perks.
     *
     * @param playerId the player's unique identifier
     * @param availablePerks all available perks to suggest from
     * @param maxSuggestions maximum number of suggestions to return
     * @return suggestion result with shuffled perks and A/B test info
     */
    public SuggestionResult getSuggestions(UUID playerId, List<Perk> availablePerks, int maxSuggestions) {
        boolean isTestVariant = isInAbTestGroup(playerId);
        String variantId = isTestVariant ? "variant_b" : "variant_a";

        List<Perk> suggested = filterByCategory(availablePerks, PerkCategory.SUGGESTED);

        // Shuffle SUGGESTED perks to eliminate position bias (DD51)
        List<Perk> shuffled = shufflePerks(suggested);

        // If in test variant, apply alternative sorting strategy
        List<Perk> result;
        if (isTestVariant) {
            result = sortByWinRate(shuffled);
        } else {
            result = shuffled;
        }

        // Limit to max suggestions
        if (result.size() > maxSuggestions) {
            result = result.subList(0, maxSuggestions);
        }

        return new SuggestionResult(List.copyOf(result), isTestVariant, variantId);
    }

    /**
     * Determines if a player is in the A/B test group.
     * Uses deterministic hashing to ensure consistent assignment.
     * Implements DD51: A/B test 10%.
     *
     * @param playerId the player's unique identifier
     * @return true if player is in test variant (10%), false otherwise
     */
    public boolean isInAbTestGroup(UUID playerId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = AB_TEST_SALT + playerId.toString();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Use first 4 bytes to create a deterministic value
            int hashValue = ((hash[0] & 0xFF) << 24) |
                           ((hash[1] & 0xFF) << 16) |
                           ((hash[2] & 0xFF) << 8) |
                           (hash[3] & 0xFF);

            // Normalize to [0, 1) range
            double normalized = (hashValue & 0x7FFFFFFF) / (double) Integer.MAX_VALUE;

            return normalized < AB_TEST_RATIO;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 should always be available
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Shuffles a list of perks to eliminate position bias.
     * Creates a new list, does not modify the original.
     *
     * @param perks the perks to shuffle
     * @return a new shuffled list
     */
    public List<Perk> shufflePerks(List<Perk> perks) {
        List<Perk> shuffled = new ArrayList<>(perks);
        // Fisher-Yates shuffle using the random generator
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Perk temp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, temp);
        }
        return shuffled;
    }

    /**
     * Filters perks by category.
     *
     * @param perks all perks
     * @param category the category to filter by
     * @return filtered list of perks
     */
    public List<Perk> filterByCategory(List<Perk> perks, PerkCategory category) {
        return perks.stream()
            .filter(p -> p.category() == category)
            .toList();
    }

    /**
     * Sorts perks by win rate in descending order.
     *
     * @param perks the perks to sort
     * @return sorted list by win rate (highest first)
     */
    public List<Perk> sortByWinRate(List<Perk> perks) {
        return perks.stream()
            .sorted((a, b) -> Double.compare(b.winRate(), a.winRate()))
            .toList();
    }

    /**
     * Generates the DuckDB SQL query for weekly winrate analysis.
     * Implements DD51: weekly winrate analysis query.
     *
     * @param weekStartEpoch the start of the week (epoch seconds)
     * @return SQL query string for DuckDB
     */
    public String generateWeeklyWinrateQuery(long weekStartEpoch) {
        long weekEndEpoch = weekStartEpoch + (7 * 24 * 60 * 60); // 7 days

        return """
            SELECT
                perk_id,
                COUNT(*) as matches_played,
                SUM(CASE WHEN won = true THEN 1 ELSE 0 END) as wins,
                CAST(SUM(CASE WHEN won = true THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(*) as win_rate,
                %d as week_start_epoch
            FROM match_results
            WHERE created_at >= %d
              AND created_at < %d
              AND perk_id IS NOT NULL
            GROUP BY perk_id
            HAVING COUNT(*) >= 10
            ORDER BY win_rate DESC
            """.formatted(weekStartEpoch, weekStartEpoch, weekEndEpoch);
    }

    /**
     * Parse weekly winrate results from DuckDB query result.
     *
     * @param perkId the perk ID
     * @param winRate the calculated win rate
     * @param matchesPlayed total matches played
     * @param wins total wins
     * @param weekStartEpoch week start epoch
     * @return WeeklyWinrateAnalysis record
     */
    public WeeklyWinrateAnalysis parseWinrateResult(
            String perkId,
            double winRate,
            int matchesPlayed,
            int wins,
            long weekStartEpoch) {
        return new WeeklyWinrateAnalysis(perkId, winRate, matchesPlayed, wins, weekStartEpoch);
    }
}
