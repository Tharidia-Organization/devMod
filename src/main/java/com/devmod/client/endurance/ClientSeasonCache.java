package com.devmod.client.endurance;

import com.devmod.endurance.season.SeasonPassPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Client-side cache for season pass data.
 * Updated via network packets from server.
 */
@OnlyIn(Dist.CLIENT)
public class ClientSeasonCache {

    private static SeasonPassPayload cachedData = null;
    private static long lastUpdateTime = 0;

    /**
     * Update the cached season pass data.
     */
    public static void update(SeasonPassPayload payload) {
        cachedData = payload;
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Check if we have cached data.
     */
    public static boolean hasData() {
        return cachedData != null;
    }

    /**
     * Get time since last update in milliseconds.
     */
    public static long getTimeSinceUpdate() {
        return System.currentTimeMillis() - lastUpdateTime;
    }

    /**
     * Clear the cache.
     */
    public static void clear() {
        cachedData = null;
        lastUpdateTime = 0;
    }

    // === Accessors ===

    public static int getSeasonNumber() {
        return cachedData != null ? cachedData.seasonNumber() : 0;
    }

    public static String getSeasonName() {
        return cachedData != null ? cachedData.seasonName() : "Season";
    }

    public static long getRemainingDays() {
        return cachedData != null ? cachedData.remainingDays() : 0;
    }

    public static boolean isSeasonActive() {
        return cachedData != null && cachedData.seasonActive();
    }

    public static int getCurrentTier() {
        return cachedData != null ? cachedData.currentTier() : 1;
    }

    public static int getCurrentXP() {
        return cachedData != null ? cachedData.currentXP() : 0;
    }

    public static int getXPToNextTier() {
        return cachedData != null ? cachedData.xpToNextTier() : 1000;
    }

    public static float getTierProgress() {
        return cachedData != null ? cachedData.tierProgress() : 0f;
    }

    public static boolean isPremium() {
        return cachedData != null && cachedData.premium();
    }

    public static int getUnclaimedRewards() {
        return cachedData != null ? cachedData.unclaimedRewards() : 0;
    }

    public static boolean hasXPBoost() {
        return cachedData != null && cachedData.hasBoost();
    }

    public static float getBoostMultiplier() {
        return cachedData != null ? cachedData.boostMultiplier() : 1.0f;
    }

    public static long getBoostRemainingSeconds() {
        return cachedData != null ? cachedData.boostRemainingSeconds() : 0;
    }

    public static List<SeasonPassPayload.RewardEntry> getUpcomingRewards() {
        return cachedData != null ? cachedData.upcomingRewards() : List.of();
    }

    /**
     * Get formatted remaining time for boost.
     */
    public static String getFormattedBoostTime() {
        long seconds = getBoostRemainingSeconds();
        if (seconds <= 0) return "";

        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%dm %02ds", minutes, remainingSeconds);
        } else {
            return String.format("%ds", remainingSeconds);
        }
    }

    /**
     * Get tier display text.
     */
    public static String getTierDisplayText() {
        if (!hasData()) return "Tier 1";
        return "Tier " + getCurrentTier();
    }

    /**
     * Get XP progress display text.
     */
    public static String getXPProgressText() {
        if (!hasData()) return "0 / 1000 XP";
        int current = getCurrentXP() % 1000; // XP in current tier
        return String.format("%d / 1000 XP", current);
    }

    /**
     * Get season status display text.
     */
    public static String getSeasonStatusText() {
        if (!hasData()) return "Season Loading...";
        if (!isSeasonActive()) return "Season Ended";

        long days = getRemainingDays();
        if (days > 7) {
            return String.format("%d days left", days);
        } else if (days > 1) {
            return String.format("%d days left!", days);
        } else if (days == 1) {
            return "Final day!";
        } else {
            return "Ending soon!";
        }
    }

    /**
     * Get premium badge text if premium.
     */
    public static String getPremiumBadge() {
        return isPremium() ? "PREMIUM" : "";
    }
}
