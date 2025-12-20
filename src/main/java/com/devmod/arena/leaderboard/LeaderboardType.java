package com.devmod.arena.leaderboard;

import java.time.Duration;

/**
 * Leaderboard Type enum implementing DD56: Leaderboard Batch.
 *
 * <p>Key features:
 * <ul>
 *   <li>Multiple leaderboard types with SQL queries</li>
 *   <li>Configurable cache TTL per type</li>
 *   <li>Support for different ranking algorithms</li>
 * </ul>
 */
public enum LeaderboardType {

    /**
     * All-time leaderboard based on total wins.
     */
    ALL_TIME_WINS(
        "all_time_wins",
        "All-Time Wins",
        Duration.ofHours(25),
        """
            SELECT
                p.id as player_id,
                p.username,
                COUNT(CASE WHEN mr.won = true THEN 1 END) as score,
                COUNT(*) as matches_played,
                RANK() OVER (ORDER BY COUNT(CASE WHEN mr.won = true THEN 1 END) DESC) as rank
            FROM players p
            LEFT JOIN match_results mr ON p.id = mr.player_id
            GROUP BY p.id, p.username
            ORDER BY score DESC
            LIMIT :limit OFFSET :offset
            """
    ),

    /**
     * Season leaderboard based on wins in current season.
     */
    SEASON_WINS(
        "season_wins",
        "Season Wins",
        Duration.ofHours(25),
        """
            SELECT
                p.id as player_id,
                p.username,
                COUNT(CASE WHEN mr.won = true THEN 1 END) as score,
                COUNT(*) as matches_played,
                RANK() OVER (ORDER BY COUNT(CASE WHEN mr.won = true THEN 1 END) DESC) as rank
            FROM players p
            LEFT JOIN match_results mr ON p.id = mr.player_id
            WHERE mr.created_at >= :season_start
              AND mr.created_at < :season_end
            GROUP BY p.id, p.username
            ORDER BY score DESC
            LIMIT :limit OFFSET :offset
            """
    ),

    /**
     * Weekly leaderboard based on wins in the current week.
     */
    WEEKLY_WINS(
        "weekly_wins",
        "Weekly Wins",
        Duration.ofHours(6),
        """
            SELECT
                p.id as player_id,
                p.username,
                COUNT(CASE WHEN mr.won = true THEN 1 END) as score,
                COUNT(*) as matches_played,
                RANK() OVER (ORDER BY COUNT(CASE WHEN mr.won = true THEN 1 END) DESC) as rank
            FROM players p
            LEFT JOIN match_results mr ON p.id = mr.player_id
            WHERE mr.created_at >= date_trunc('week', CURRENT_TIMESTAMP)
            GROUP BY p.id, p.username
            ORDER BY score DESC
            LIMIT :limit OFFSET :offset
            """
    );

    private final String key;
    private final String displayName;
    private final Duration cacheTtl;
    private final String sqlQuery;

    LeaderboardType(String key, String displayName, Duration cacheTtl, String sqlQuery) {
        this.key = key;
        this.displayName = displayName;
        this.cacheTtl = cacheTtl;
        this.sqlQuery = sqlQuery;
    }

    /**
     * Gets the cache key for this leaderboard type.
     *
     * @return cache key string
     */
    public String getKey() {
        return key;
    }

    /**
     * Gets the display name for UI.
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the cache TTL for this leaderboard.
     * DD56 specifies 25h for most leaderboards.
     *
     * @return cache TTL duration
     */
    public Duration getCacheTtl() {
        return cacheTtl;
    }

    /**
     * Gets the SQL query for calculating this leaderboard.
     *
     * @return SQL query with named parameters
     */
    public String getSqlQuery() {
        return sqlQuery;
    }

    /**
     * Gets the Redis key for storing this leaderboard.
     *
     * @return Redis key
     */
    public String getRedisKey() {
        return "leaderboard:" + key;
    }

    /**
     * Gets the Redis key for the player rank secondary index.
     * Used for O(1) player rank lookup (DD56).
     *
     * @return Redis key for player rank index
     */
    public String getPlayerRankIndexKey() {
        return "leaderboard:" + key + ":player_index";
    }

    /**
     * Gets the cache TTL in seconds.
     *
     * @return TTL in seconds
     */
    public long getCacheTtlSeconds() {
        return cacheTtl.toSeconds();
    }

    /**
     * Finds a leaderboard type by key.
     *
     * @param key the key to search for
     * @return the LeaderboardType or null if not found
     */
    public static LeaderboardType fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (LeaderboardType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Gets the count of leaderboard types.
     *
     * @return number of types (should be 3)
     */
    public static int count() {
        return values().length;
    }

    /**
     * Generates SQL for player rank lookup.
     * This query is used to find a specific player's rank.
     *
     * @return SQL query for single player rank
     */
    public String getPlayerRankQuery() {
        return """
            WITH ranked_players AS (
                %s
            )
            SELECT * FROM ranked_players
            WHERE player_id = :player_id
            """.formatted(sqlQuery.replace("LIMIT :limit OFFSET :offset", ""));
    }
}
