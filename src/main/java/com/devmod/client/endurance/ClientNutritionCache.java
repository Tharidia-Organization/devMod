package com.devmod.client.endurance;

import javax.annotation.Nullable;

import com.devmod.endurance.nutrition.NutritionCategory;
import com.devmod.endurance.nutrition.NutritionSyncPayload;

/**
 * Client-side cache for nutrition state received from server.
 *
 * <p>This cache stores the latest nutrition sync payload for display
 * in the HUD overlay and other client UI elements.
 *
 * <p><b>Thread Safety:</b> This class is accessed from both the network thread
 * (updates) and render thread (reads). The {@code current} field is volatile
 * to ensure visibility across threads. All reader methods capture a local
 * snapshot to avoid TOCTOU race conditions.
 *
 * @author DevMod Team
 */
public final class ClientNutritionCache {

    /**
     * Current nutrition state, or null if not in a quest.
     *
     * <p><b>Thread Safety:</b> Volatile ensures visibility of writes from
     * network thread to render thread. Reader methods capture this in a
     * local variable before accessing to avoid TOCTOU races.
     */
    @Nullable
    private static volatile NutritionSyncPayload current = null;

    private ClientNutritionCache() {
        // Utility class
    }

    /**
     * Update the cache with new nutrition data from server.
     *
     * <p>Called from network thread when server sends sync payload.
     *
     * @param payload the nutrition sync payload
     */
    public static void update(NutritionSyncPayload payload) {
        current = payload;
    }

    /**
     * Clear the cache (when quest ends or player disconnects).
     */
    public static void clear() {
        current = null;
    }

    /**
     * Check if nutrition data is available.
     *
     * @return true if we have valid nutrition data
     */
    public static boolean isAvailable() {
        return current != null;
    }

    /**
     * Get the current nutrition payload.
     *
     * @return the payload, or null if not available
     */
    @Nullable
    public static NutritionSyncPayload getCurrent() {
        return current;
    }

    // =========================================================================
    // CONVENIENCE ACCESSORS
    // =========================================================================

    /**
     * Get value for a specific nutrition category.
     *
     * @param category the category
     * @return value 0.0-1.0, or 0.5 (neutral) if not available
     */
    public static float getValue(NutritionCategory category) {
        NutritionSyncPayload snapshot = current;
        if (snapshot == null) {
            return 0.5f; // Neutral - no buff/debuff
        }
        return snapshot.getValue(category);
    }

    /**
     * Get the overall nutrition balance.
     *
     * @return balance 0.0-1.0, or 0.5 (neutral) if not available
     */
    public static float getBalance() {
        NutritionSyncPayload snapshot = current;
        return snapshot != null ? snapshot.balance() : 0.5f;
    }

    /**
     * Get the damage multiplier.
     *
     * @return multiplier (1.0 = neutral)
     */
    public static float getDamageMultiplier() {
        NutritionSyncPayload snapshot = current;
        return snapshot != null ? snapshot.damageMultiplier() : 1.0f;
    }

    /**
     * Get the speed multiplier.
     *
     * @return multiplier (1.0 = neutral)
     */
    public static float getSpeedMultiplier() {
        NutritionSyncPayload snapshot = current;
        return snapshot != null ? snapshot.speedMultiplier() : 1.0f;
    }

    /**
     * Check if player is well-fed.
     *
     * @return true if well-fed
     */
    public static boolean isWellFed() {
        NutritionSyncPayload snapshot = current;
        return snapshot != null && snapshot.isWellFed();
    }

    /**
     * Check if player is malnourished.
     *
     * @return true if malnourished
     */
    public static boolean isMalnourished() {
        NutritionSyncPayload snapshot = current;
        return snapshot != null && snapshot.isMalnourished();
    }

    /**
     * Check if player is in critical state.
     *
     * @return true if critical
     */
    public static boolean isCritical() {
        NutritionSyncPayload snapshot = current;
        return snapshot != null && snapshot.isCritical();
    }
}
