package com.devmod.endurance;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache for shop/wallet data synced from server.
 * Updated when ShopSyncPayload is received.
 */
public class ClientShopCache {

    private static int tokens = 0;
    private static int prestige = 0;
    private static int bloodGems = 0;
    private static Map<String, Integer> purchases = new HashMap<>();
    private static long lastSyncTime = 0;

    private ClientShopCache() {}

    /**
     * Update cache from server sync payload.
     */
    public static void update(ShopSyncPayload payload) {
        tokens = payload.tokens();
        prestige = payload.prestige();
        bloodGems = payload.bloodGems();
        purchases = new HashMap<>(payload.purchases());
        lastSyncTime = System.currentTimeMillis();
    }

    /**
     * Get current token balance.
     */
    public static int getTokens() {
        return tokens;
    }

    /**
     * Get current prestige points.
     */
    public static int getPrestige() {
        return prestige;
    }

    /**
     * Get current blood gems.
     */
    public static int getBloodGems() {
        return bloodGems;
    }

    /**
     * Get purchase count for an item.
     */
    public static int getPurchaseCount(String itemId) {
        return purchases.getOrDefault(itemId, 0);
    }

    /**
     * Get all purchases map.
     */
    public static Map<String, Integer> getPurchases() {
        return new HashMap<>(purchases);
    }

    /**
     * Check if we have recent sync data (within 30 seconds).
     */
    public static boolean hasRecentData() {
        return System.currentTimeMillis() - lastSyncTime < 30000;
    }

    /**
     * Get time since last sync in milliseconds.
     */
    public static long getTimeSinceSync() {
        return System.currentTimeMillis() - lastSyncTime;
    }

    /**
     * Clear cache (on disconnect).
     */
    public static void clear() {
        tokens = 0;
        prestige = 0;
        bloodGems = 0;
        purchases.clear();
        lastSyncTime = 0;
    }

    /**
     * Optimistically update after a purchase (before server confirms).
     */
    public static void optimisticPurchase(String itemId, RewardSystem.Currency currency, int price) {
        switch (currency) {
            case TOKENS -> tokens -= price;
            case PRESTIGE -> prestige -= price;
            case BLOOD_GEMS -> bloodGems -= price;
        }
        int current = purchases.getOrDefault(itemId, 0);
        purchases.put(itemId, current + 1);
    }
}
