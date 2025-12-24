package com.devmod.abilities;

/**
 * Client-side cache for stamina data synced from server.
 * Used by HUD rendering to display stamina bar.
 */
public class ClientStaminaCache {
    private static float currentStamina = 100.0f;
    private static float maxStamina = 100.0f;
    private static long lastUpdateTime = 0;

    /**
     * Update cache from server sync packet.
     */
    public static void update(float current, float max) {
        currentStamina = current;
        maxStamina = max;
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Get current stamina value.
     */
    public static float getCurrentStamina() {
        return currentStamina;
    }

    /**
     * Get max stamina value.
     */
    public static float getMaxStamina() {
        return maxStamina;
    }

    /**
     * Get stamina as percentage (0.0 - 1.0).
     */
    public static float getStaminaPercent() {
        return maxStamina > 0 ? currentStamina / maxStamina : 0;
    }

    /**
     * Check if stamina is depleted.
     */
    public static boolean isExhausted() {
        return currentStamina <= 0;
    }

    /**
     * Check if cache is stale (no update in 2 seconds).
     */
    public static boolean isStale() {
        return System.currentTimeMillis() - lastUpdateTime > 2000;
    }

    /**
     * Reset to defaults.
     */
    public static void reset() {
        currentStamina = 100.0f;
        maxStamina = 100.0f;
        lastUpdateTime = 0;
    }
}
