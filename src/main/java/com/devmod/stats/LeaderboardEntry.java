package com.devmod.stats;

import java.util.UUID;

/**
 * Lightweight record for leaderboard display on the client.
 * Received from the server via network packet.
 *
 * @param rank       1-based rank in the leaderboard
 * @param playerId   UUID of the player
 * @param playerName Display name
 * @param score      Primary score value (interpretation depends on category)
 * @param secondary  Secondary value (e.g. best time, unique quests)
 * @param tertiary   Tertiary value (e.g. total style, matches played)
 */
public record LeaderboardEntry(
    int rank,
    UUID playerId,
    String playerName,
    long score,
    long secondary,
    long tertiary
) {

    /**
     * Resolve a style rank index to display name.
     */
    public static String styleRankName(long index) {
        return switch ((int) index) {
            case 7 -> "SSS";
            case 6 -> "SS";
            case 5 -> "S";
            case 4 -> "A";
            case 3 -> "B";
            case 2 -> "C";
            case 1 -> "D";
            default -> "?";
        };
    }
}
