package com.devmod.arena.currency;

/**
 * Currency Source enum implementing DD54: Currency Source Enum.
 *
 * <p>Key features:
 * <ul>
 *   <li>~15 values for different currency sources</li>
 *   <li>Separate sourceId for detailed tracking</li>
 *   <li>Category grouping for analytics</li>
 * </ul>
 */
public enum CurrencySource {

    // Match-related sources
    MATCH_WIN("match", "Earned from winning a match", true),
    MATCH_LOSS("match", "Earned from completing a match (loss)", true),
    MATCH_DRAW("match", "Earned from a drawn match", true),
    MATCH_BONUS("match", "Bonus currency from match performance", true),

    // Challenge-related sources
    CHALLENGE_COMPLETE("challenge", "Earned from completing a challenge", true),
    CHALLENGE_STREAK("challenge", "Bonus from challenge streaks", true),

    // Achievement-related sources
    ACHIEVEMENT_UNLOCK("achievement", "Earned from unlocking an achievement", true),
    BADGE_AWARD("achievement", "Earned from receiving a badge", true),

    // Daily/Periodic sources
    DAILY_LOGIN("periodic", "Daily login bonus", true),
    WEEKLY_BONUS("periodic", "Weekly activity bonus", true),
    SEASONAL_REWARD("periodic", "Seasonal event reward", true),

    // Administrative sources
    ADMIN_GRANT("admin", "Granted by administrator", false),
    COMPENSATION("admin", "Compensation for issues", false),
    REFUND("admin", "Refund of previously spent currency", false),

    // Purchase sources
    PURCHASE("purchase", "Purchased with real money", false),

    // System sources
    SYSTEM_MIGRATION("system", "Migrated from legacy system", false);

    private final String category;
    private final String description;
    private final boolean earnedInGame;

    CurrencySource(String category, String description, boolean earnedInGame) {
        this.category = category;
        this.description = description;
        this.earnedInGame = earnedInGame;
    }

    /**
     * Gets the category for this source.
     *
     * @return category string
     */
    public String getCategory() {
        return category;
    }

    /**
     * Gets the human-readable description.
     *
     * @return description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this currency was earned through gameplay.
     *
     * @return true if earned in game
     */
    public boolean isEarnedInGame() {
        return earnedInGame;
    }

    /**
     * Checks if this is an administrative/system source.
     *
     * @return true if admin or system source
     */
    public boolean isAdminSource() {
        return category.equals("admin") || category.equals("system");
    }

    /**
     * Checks if this source requires additional authorization.
     *
     * @return true if requires auth
     */
    public boolean requiresAuthorization() {
        return isAdminSource() || this == PURCHASE;
    }

    /**
     * Gets all sources in a specific category.
     *
     * @param category the category to filter by
     * @return array of matching sources
     */
    public static CurrencySource[] byCategory(String category) {
        return java.util.Arrays.stream(values())
            .filter(s -> s.category.equals(category))
            .toArray(CurrencySource[]::new);
    }

    /**
     * Gets all game-earned sources.
     *
     * @return array of earned sources
     */
    public static CurrencySource[] earnedSources() {
        return java.util.Arrays.stream(values())
            .filter(CurrencySource::isEarnedInGame)
            .toArray(CurrencySource[]::new);
    }

    /**
     * Gets the total count of enum values.
     * DD54 specifies ~15 values.
     *
     * @return count of values (should be 16)
     */
    public static int count() {
        return values().length;
    }

    /**
     * Parses a currency source from string, case-insensitive.
     *
     * @param name the source name
     * @return the CurrencySource or null if not found
     */
    public static CurrencySource fromString(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
