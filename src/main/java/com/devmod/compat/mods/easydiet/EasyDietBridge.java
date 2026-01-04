package com.devmod.compat.mods.easydiet;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridge class that provides a stable API for Easy-Diet integration.
 *
 * <p>This class wraps the reflection-based {@link EasyDietApi} and provides
 * a convenient interface for DevMod systems to access Easy-Diet functionality.
 *
 * <p>All methods are safe to call even when Easy-Diet is not installed.
 * When Easy-Diet is not available, methods return sensible default values
 * (0.5 for nutrition values - neutral, no buff/debuff).
 *
 * <p>For optimal performance, callers should check {@link EasyDietCompat#isAvailable()}
 * before calling methods in tight loops, but this is not strictly required.
 *
 * @author DevMod Team
 * @see EasyDietApi
 * @see EasyDietCompat
 */
public final class EasyDietBridge {

    private EasyDietBridge() {
        // Utility class
    }

    /**
     * Get diet values for a player.
     *
     * @param player the player
     * @return array of 6 float values (0.0-1.0)
     */
    public static float[] getDietValues(ServerPlayer player) {
        return EasyDietApi.getDietValues(player);
    }

    /**
     * Get overall nutrition balance for a player.
     *
     * @param player the player
     * @return balance 0.0-1.0
     */
    public static float getNutritionBalance(ServerPlayer player) {
        return EasyDietApi.getNutritionBalance(player);
    }

    /**
     * Check if player is well-fed (all categories above threshold).
     *
     * @param player the player
     * @return true if well-fed
     */
    public static boolean isWellFed(ServerPlayer player) {
        return EasyDietApi.isWellFed(player);
    }

    /**
     * Check if player is malnourished (any category below threshold).
     *
     * @param player the player
     * @return true if malnourished
     */
    public static boolean isMalnourished(ServerPlayer player) {
        return EasyDietApi.isMalnourished(player);
    }

    /**
     * Check if player is in critical state (any category dangerously low).
     *
     * @param player the player
     * @return true if critical
     */
    public static boolean isCritical(ServerPlayer player) {
        return EasyDietApi.isCritical(player);
    }

    /**
     * Get the lowest diet category value.
     *
     * @param player the player
     * @return the minimum value across all categories
     */
    public static float getLowestValue(ServerPlayer player) {
        return EasyDietApi.getLowestValue(player);
    }
}
