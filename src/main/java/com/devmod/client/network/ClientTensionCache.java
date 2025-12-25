package com.devmod.client.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side cache for Tension System state.
 * Updated via network packets from server.
 * Used by EnduranceQuestOverlay for HUD display.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientTensionCache {

    private static float tensionPercent = 0f;
    private static int tensionLevel = 0;
    private static boolean bossImminent = false;
    private static long lastUpdateTime = 0;

    private ClientTensionCache() {}

    /**
     * Update the cached tension state from server.
     */
    public static void update(float percent, int level, boolean imminent) {
        tensionPercent = percent;
        tensionLevel = level;
        bossImminent = imminent;
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Clear the cache (on quest end or disconnect).
     */
    public static void clear() {
        tensionPercent = 0f;
        tensionLevel = 0;
        bossImminent = false;
        lastUpdateTime = 0;
    }

    /**
     * Get current tension as percentage (0.0 - 1.0).
     */
    public static float getTensionPercent() {
        return tensionPercent;
    }

    /**
     * Get tension level (0-4) for visual intensity.
     */
    public static int getTensionLevel() {
        return tensionLevel;
    }

    /**
     * Check if boss is imminent (tension threshold exceeded).
     */
    public static boolean isBossImminent() {
        return bossImminent;
    }

    /**
     * Check if cache has been recently updated (within last 30 seconds).
     */
    public static boolean isActive() {
        return lastUpdateTime > 0 && System.currentTimeMillis() - lastUpdateTime < 30000;
    }

    /**
     * Get display color based on tension level.
     */
    public static int getDisplayColor() {
        return switch (tensionLevel) {
            case 0 -> 0x44FF44; // Green - calm
            case 1 -> 0xAAFF44; // Yellow-green - building
            case 2 -> 0xFFFF44; // Yellow - moderate
            case 3 -> 0xFFAA44; // Orange - high
            case 4 -> 0xFF4444; // Red - critical
            default -> 0xFFFFFF;
        };
    }

    /**
     * Get tension label for UI display.
     */
    public static String getTensionLabel() {
        return switch (tensionLevel) {
            case 0 -> "Calm";
            case 1 -> "Rising";
            case 2 -> "Tense";
            case 3 -> "Critical";
            case 4 -> "BOSS INCOMING";
            default -> "";
        };
    }
}
