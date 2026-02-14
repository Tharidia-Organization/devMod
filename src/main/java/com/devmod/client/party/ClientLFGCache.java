package com.devmod.client.party;

import java.util.List;

import javax.annotation.Nullable;

import com.devmod.party.lfg.LFGSyncPayload;

/**
 * Client-side cache for LFG listings received from server.
 */
public final class ClientLFGCache {

    @Nullable
    private static volatile List<LFGSyncPayload.LFGListingEntry> listings = null;
    private static volatile long lastUpdateTime = 0;

    private ClientLFGCache() {}

    /**
     * Update from server sync.
     */
    public static void update(LFGSyncPayload payload) {
        listings = List.copyOf(payload.listings());
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Get the current listings.
     */
    public static List<LFGSyncPayload.LFGListingEntry> getListings() {
        List<LFGSyncPayload.LFGListingEntry> current = listings;
        return current != null ? current : List.of();
    }

    /**
     * Check if data is stale (older than 30 seconds).
     */
    public static boolean isStale() {
        return System.currentTimeMillis() - lastUpdateTime > 30_000;
    }

    /**
     * Clear cache.
     */
    public static void clear() {
        listings = null;
        lastUpdateTime = 0;
    }
}
