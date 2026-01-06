package com.devmod.runtime;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.party.PartyData;
import com.devmod.party.PartyManager;
import com.devmod.telemetry.duckdb.DuckDBQueryAPI;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;

/**
 * Context data about a player for the Nexus dialog system.
 * Used to personalize dialog responses based on player progress and state.
 */
public record NexusDialogContext(
    @Nonnull UUID playerId,
    @Nonnull String playerName,
    // Quest statistics
    int totalQuestsCompleted,
    int totalWavesCleared,
    int highestWaveReached,
    int totalKills,
    // Current state
    boolean hasActiveQuest,
    boolean isInParty,
    int partySize,
    boolean isPartyLeader,
    // Progression
    int seasonTier,
    int totalXp,
    // Achievements (simplified - just count)
    int achievementsUnlocked,
    // Time tracking
    long totalPlaytimeMinutes,
    boolean isFirstVisit
) {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Build a dialog context for the given player.
     * Gathers data from various subsystems.
     */
    @Nonnull
    public static NexusDialogContext forPlayer(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        UUID playerId = player.getUUID();
        String playerName = player.getName().getString();

        // Check current quest state
        boolean hasActiveQuest = EnduranceQuestManager.INSTANCE.getActiveSession(playerId).isPresent();

        // Check party state
        PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
        boolean isInParty = party != null;
        int partySize = party != null ? party.getMemberCount() : 0;
        boolean isPartyLeader = party != null && party.isLeader(playerId);

        // Fetch from telemetry/saved data
        int totalQuestsCompleted = 0;
        int totalWavesCleared = 0;
        int highestWaveReached = 0;
        int totalKills = 0;
        int seasonTier = 1;
        int totalXp = 0;
        int achievementsUnlocked = 0;
        long totalPlaytimeMinutes = 0;
        boolean isFirstVisit = true;

        // Try to get stats from DuckDB telemetry
        DuckDBQueryAPI queryAPI = DuckDBTelemetryService.INSTANCE.getQueryAPI();
        if (queryAPI != null) {
            try {
                DuckDBQueryAPI.EnduranceStats stats = queryAPI.getEnduranceStats(playerId);
                if (stats != null) {
                    totalQuestsCompleted = stats.completed();
                    // Estimate total waves from completed quests and average waves
                    totalWavesCleared = (int) (stats.totalQuests() * stats.avgWaves());
                    highestWaveReached = stats.bestWave();
                    totalKills = stats.totalKills();
                    // Calculate XP from tokens/prestige (rough estimate)
                    totalXp = stats.totalTokens() + (stats.totalPrestige() * 100);
                }
            } catch (RuntimeException e) {
                // DuckDB query or connection failed - log for diagnostics and fallback to NBT
                // Common causes: connection closed during shutdown, query timeout, circuit breaker
                LOGGER.debug("[NexusDialog] Telemetry query failed for {}: {}", playerName, e.getMessage());
            }
        }

        // Fallback/supplement with player's persistent data (session-scoped NBT cache)
        // Note: getPersistentData() is per-session, not saved to disk. DuckDB is the source of truth.
        try {
            var data = player.getPersistentData();
            if (data.contains("devmod_nexus_visits")) {
                isFirstVisit = data.getInt("devmod_nexus_visits") == 0;
            }
            // Use persistent data as fallback if telemetry returned zeros
            if (totalQuestsCompleted == 0 && data.contains("devmod_quests_completed")) {
                totalQuestsCompleted = data.getInt("devmod_quests_completed");
            }
            if (totalWavesCleared == 0 && data.contains("devmod_waves_cleared")) {
                totalWavesCleared = data.getInt("devmod_waves_cleared");
            }
            if (highestWaveReached == 0 && data.contains("devmod_highest_wave")) {
                highestWaveReached = data.getInt("devmod_highest_wave");
            }
            if (totalKills == 0 && data.contains("devmod_total_kills")) {
                totalKills = data.getInt("devmod_total_kills");
            }
        } catch (RuntimeException e) {
            // NBT access failed - use defaults, log at trace level
            LOGGER.trace("[NexusDialog] NBT fallback failed for {}: {}", playerName, e.getMessage());
        }

        return new NexusDialogContext(
            Objects.requireNonNull(playerId),
            Objects.requireNonNull(playerName),
            totalQuestsCompleted,
            totalWavesCleared,
            highestWaveReached,
            totalKills,
            hasActiveQuest,
            isInParty,
            partySize,
            isPartyLeader,
            seasonTier,
            totalXp,
            achievementsUnlocked,
            totalPlaytimeMinutes,
            isFirstVisit
        );
    }

    /**
     * Mark that the player has visited the Nexus.
     * Updates session-scoped NBT counter for isFirstVisit detection within this session.
     */
    public static void markVisit(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        try {
            var data = player.getPersistentData();
            int visits = data.getInt("devmod_nexus_visits");
            data.putInt("devmod_nexus_visits", visits + 1);
        } catch (RuntimeException e) {
            LOGGER.trace("[NexusDialog] Failed to mark visit for {}", player.getName().getString());
        }
    }

    /**
     * Get a title/rank based on player progress.
     */
    @Nonnull
    public String getPlayerTitle() {
        if (totalQuestsCompleted == 0) {
            return "Newcomer";
        } else if (totalQuestsCompleted < 5) {
            return "Initiate";
        } else if (totalQuestsCompleted < 20) {
            return "Challenger";
        } else if (totalQuestsCompleted < 50) {
            return "Veteran";
        } else if (totalQuestsCompleted < 100) {
            return "Champion";
        } else {
            return "Legend";
        }
    }

    /**
     * Check if player is a newcomer (no quest completions).
     */
    public boolean isNewcomer() {
        return totalQuestsCompleted == 0;
    }

    /**
     * Check if player is a veteran (50+ quests).
     */
    public boolean isVeteran() {
        return totalQuestsCompleted >= 50;
    }

    /**
     * Check if this is the player's absolute first visit ever.
     * True only if they have no quest history AND this is their first visit this session.
     * Useful for showing initial tutorial/introduction in Nexus dialog.
     */
    public boolean isAbsoluteFirstVisit() {
        return isNewcomer() && isFirstVisit;
    }

    /**
     * Check if player is returning after server restart.
     * They have quest history but this is their first visit this session.
     */
    public boolean isReturningPlayer() {
        return !isNewcomer() && isFirstVisit;
    }
}
