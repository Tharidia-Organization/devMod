package com.devmod.client.endurance;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.endurance.PartyStatsSyncPayload;
import com.devmod.endurance.PartyWaveStats;

/**
 * Client-side cache for party wave statistics.
 * Updated when receiving PartyStatsSyncPayload from server after wave completion.
 *
 * <p>Used by DebriefScreen to display a "Party Stats" tab when in a party run.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPartyStatsCache {

    private static volatile @Nullable PartyWaveStats cachedStats = null;
    private static volatile long lastUpdateTime = 0;

    private ClientPartyStatsCache() {}

    /**
     * Update the cache with new party stats from server.
     */
    public static void update(PartyStatsSyncPayload payload) {
        cachedStats = payload.toPartyWaveStats();
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Get the cached party stats, or null if none.
     */
    @Nullable
    public static PartyWaveStats getStats() {
        return cachedStats;
    }

    /**
     * Check if we have party stats available.
     */
    public static boolean hasPartyStats() {
        return cachedStats != null && cachedStats.isPartyRun();
    }

    /**
     * Get stats for the current wave number, or null if mismatch.
     */
    @Nullable
    public static PartyWaveStats getStatsForWave(int waveNumber) {
        PartyWaveStats stats = cachedStats;
        if (stats != null && stats.waveNumber() == waveNumber) {
            return stats;
        }
        return null;
    }

    /**
     * Get time since last update in milliseconds.
     */
    public static long getTimeSinceUpdate() {
        return System.currentTimeMillis() - lastUpdateTime;
    }

    /**
     * Clear the cache (e.g., when disconnecting or quest ends).
     */
    public static void clear() {
        cachedStats = null;
        lastUpdateTime = 0;
    }

    // === Convenience getters ===

    public static int getPartySize() {
        PartyWaveStats stats = cachedStats;
        return stats != null ? stats.playerStats().size() : 0;
    }

    public static int getPartyTotalKills() {
        PartyWaveStats stats = cachedStats;
        return stats != null ? stats.partyTotalKills() : 0;
    }

    public static int getPartyTotalDamageDealt() {
        PartyWaveStats stats = cachedStats;
        return stats != null ? stats.partyTotalDamageDealt() : 0;
    }

    public static float getPartyDPS() {
        PartyWaveStats stats = cachedStats;
        return stats != null ? stats.partyDPS() : 0f;
    }

    public static boolean isPartyNoDamageWave() {
        PartyWaveStats stats = cachedStats;
        return stats != null && stats.partyNoDamageWave();
    }

    @Nullable
    public static String getMvpPlayerId() {
        PartyWaveStats stats = cachedStats;
        return stats != null ? stats.mvpPlayerId() : null;
    }

    @Nullable
    public static String getMvpReason() {
        PartyWaveStats stats = cachedStats;
        return stats != null ? stats.mvpReason() : null;
    }
}
