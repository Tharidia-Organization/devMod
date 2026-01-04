package com.devmod.endurance;

import java.util.List;
import java.util.UUID;

/**
 * Aggregated statistics for a party wave in Endurance mode.
 * Sent from server to all party members after each wave completes.
 *
 * <p>This record contains:
 * - Per-player breakdown (kills, damage, deaths)
 * - Party-wide totals
 * - Computed metrics (party DPS, damage efficiency)
 *
 * <p>Used by DebriefScreen to show "Party Stats" tab when in a party run.
 */
public record PartyWaveStats(
    // Wave context
    int waveNumber,
    int totalWaves,
    long waveDurationMs,

    // Per-player data
    List<PlayerWaveData> playerStats,

    // Party totals
    int partyTotalKills,
    int partyEliteKills,
    int partyTotalDamageDealt,
    int partyTotalDamageTaken,
    int partyDeaths,
    int partyMaxCombo,

    // Computed metrics
    float partyDPS,
    boolean partyNoDamageWave,
    String mvpPlayerId,
    String mvpReason
) {

    /**
     * Individual player stats within a wave.
     */
    public record PlayerWaveData(
        String playerId,
        String playerName,
        int kills,
        int eliteKills,
        int damageDealt,
        int damageTaken,
        int deaths,
        int maxCombo,
        float dps,
        float killPercent,
        boolean wasSpectator
    ) {
        /**
         * Create empty data for a spectator.
         */
        public static PlayerWaveData spectator(UUID playerId, String playerName) {
            return new PlayerWaveData(
                playerId.toString(),
                playerName,
                0, 0, 0, 0, 0, 0, 0f, 0f, true
            );
        }
    }

    /**
     * Create empty stats (no party).
     */
    public static PartyWaveStats empty() {
        return new PartyWaveStats(
            0, 0, 0,
            List.of(),
            0, 0, 0, 0, 0, 0,
            0f, false, "", ""
        );
    }

    /**
     * Check if this represents a party run (2+ players).
     */
    public boolean isPartyRun() {
        return playerStats.size() >= 2;
    }

    /**
     * Get stats for a specific player by UUID string.
     */
    public PlayerWaveData getPlayerData(String playerId) {
        for (PlayerWaveData data : playerStats) {
            if (data.playerId().equals(playerId)) {
                return data;
            }
        }
        return null;
    }

    /**
     * Calculate MVP based on contribution metrics.
     * FIX UX #2: Include tank contribution (damage absorbed) in MVP calculation.
     */
    public static String determineMvp(List<PlayerWaveData> stats) {
        if (stats.isEmpty()) return "";

        PlayerWaveData mvp = stats.get(0);
        float bestScore = Float.NEGATIVE_INFINITY;

        for (PlayerWaveData player : stats) {
            if (player.wasSpectator()) continue;

            // MVP score components:
            // - Kills: main contribution (x2)
            // - Damage dealt: scaled contribution (/100)
            // - Combo: style bonus (/10)
            // - Tank contribution: damage absorbed rewards tanks (/150)
            // - Deaths: penalty (-5 per death)
            // FIX UX #2: Added damageTaken / 150 to reward tanks who absorb damage for the team
            float score = player.kills() * 2f
                + player.damageDealt() / 100f
                + player.maxCombo() / 10f
                + player.damageTaken() / 150f  // Tank contribution
                - player.deaths() * 5f;

            if (score > bestScore) {
                bestScore = score;
                mvp = player;
            }
        }

        return mvp.playerId();
    }

    /**
     * Determine MVP reason based on their stats.
     * FIX UX #2: Added "Guardian" reason for tanks who absorbed the most damage.
     *
     * @param mvp the MVP player data
     * @param allStats all player stats in the wave
     * @param waveNumber current wave number (1-indexed) for threshold scaling
     * @return reason string for the MVP title
     */
    public static String determineMvpReason(PlayerWaveData mvp, List<PlayerWaveData> allStats, int waveNumber) {
        if (mvp == null) return "";

        // Check if highest kills
        boolean highestKills = allStats.stream()
            .filter(p -> !p.wasSpectator())
            .allMatch(p -> p.kills() <= mvp.kills());

        // Check if highest combo
        boolean highestCombo = allStats.stream()
            .filter(p -> !p.wasSpectator())
            .allMatch(p -> p.maxCombo() <= mvp.maxCombo());

        // FIX UX #2: Check if highest damage absorbed (tank role)
        boolean highestDamageTaken = allStats.stream()
            .filter(p -> !p.wasSpectator())
            .allMatch(p -> p.damageTaken() <= mvp.damageTaken());

        // FIX AUDIT #3: Calculate total party damage for relative threshold
        int totalPartyDamageTaken = allStats.stream()
            .filter(p -> !p.wasSpectator())
            .mapToInt(PlayerWaveData::damageTaken)
            .sum();

        // Check if no damage taken
        boolean noDamage = mvp.damageTaken() == 0 && mvp.deaths() == 0;

        if (noDamage) return "Flawless";
        if (highestKills && mvp.killPercent() > 50) return "Slayer";
        if (highestCombo && mvp.maxCombo() >= 20) return "Stylist";

        // FIX AUDIT2 #2/#8: Guardian threshold scales with wave difficulty
        // Early waves have weaker mobs, so lower threshold prevents trivial Guardian
        int minDamageThreshold = getGuardianDamageThreshold(waveNumber);

        // FIX AUDIT #3/#5: Guardian threshold requires BOTH conditions:
        // - 30%+ of total party damage absorbed (relative contribution)
        // - At least minDamageThreshold raw damage taken (scales with wave)
        // Must also survive without dying to qualify as Guardian.
        boolean significantTank = totalPartyDamageTaken > 0
            && mvp.damageTaken() >= totalPartyDamageTaken * 0.30f
            && mvp.damageTaken() >= minDamageThreshold;
        if (highestDamageTaken && significantTank && mvp.deaths() == 0) return "Guardian";
        if (highestKills) return "Most Kills";

        return "Top Contributor";
    }

    /**
     * FIX AUDIT2 #2/#8: Get minimum damage threshold for Guardian title based on wave.
     * Early waves have weaker mobs, so threshold scales up as difficulty increases.
     *
     * @param waveNumber current wave (1-indexed)
     * @return minimum damage required for Guardian consideration
     */
    private static int getGuardianDamageThreshold(int waveNumber) {
        if (waveNumber <= 3) {
            return 25;  // Early game: weak mobs
        } else if (waveNumber <= 7) {
            return 50;  // Mid game: moderate difficulty
        } else {
            return 75;  // Late game: strong mobs
        }
    }

    /**
     * Determine MVP reason (legacy overload for backward compatibility).
     * Defaults to wave 5 (mid-game threshold of 50 damage).
     *
     * @deprecated Use {@link #determineMvpReason(PlayerWaveData, List, int)} with wave number
     */
    @Deprecated
    @SuppressWarnings("InlineMeSuggester") // Keeping as deprecated stub for API compatibility
    public static String determineMvpReason(PlayerWaveData mvp, List<PlayerWaveData> allStats) {
        return determineMvpReason(mvp, allStats, 5);  // Default to mid-game threshold
    }
}
