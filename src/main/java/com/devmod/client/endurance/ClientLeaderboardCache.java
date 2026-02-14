package com.devmod.client.endurance;

import java.util.List;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.endurance.LeaderboardSyncPayload;
import com.devmod.stats.LeaderboardEntry;

/**
 * Client-side cache for leaderboard data received from the server.
 * Updated when the server sends a {@link LeaderboardSyncPayload}.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientLeaderboardCache {

    public static final ClientLeaderboardCache INSTANCE = new ClientLeaderboardCache();

    private volatile @Nullable List<LeaderboardEntry> arenaEntries;
    private volatile @Nullable List<LeaderboardEntry> styleEntries;
    private volatile @Nullable List<LeaderboardEntry> questEntries;
    private volatile boolean loaded = false;

    private ClientLeaderboardCache() {}

    /**
     * Called when the server sends leaderboard data.
     */
    public void update(LeaderboardSyncPayload payload) {
        this.arenaEntries = List.copyOf(payload.arenaEntries());
        this.styleEntries = List.copyOf(payload.styleEntries());
        this.questEntries = List.copyOf(payload.questEntries());
        this.loaded = true;
    }

    public List<LeaderboardEntry> getArenaEntries() {
        List<LeaderboardEntry> entries = arenaEntries;
        return entries != null ? entries : List.of();
    }

    public List<LeaderboardEntry> getStyleEntries() {
        List<LeaderboardEntry> entries = styleEntries;
        return entries != null ? entries : List.of();
    }

    public List<LeaderboardEntry> getQuestEntries() {
        List<LeaderboardEntry> entries = questEntries;
        return entries != null ? entries : List.of();
    }

    /**
     * Returns true if data has been received from the server at least once.
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Clears all cached data (e.g. on disconnect).
     */
    public void clear() {
        this.arenaEntries = null;
        this.styleEntries = null;
        this.questEntries = null;
        this.loaded = false;
    }
}
