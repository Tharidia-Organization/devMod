package com.devmod.area.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * Manages cooldowns for area operations to prevent spam/abuse.
 * Thread-safe using ConcurrentHashMap.
 * Extracted from AreaNetworkHandler for better modularity.
 */
public final class CooldownManager {

    private CooldownManager() {}

    // ============================================================================
    // COOLDOWN CONSTANTS
    // ============================================================================

    /** Cooldown between builds per player (5 seconds) */
    public static final long BUILD_COOLDOWN_MS = 5_000L;

    /** Cooldown between builds per area (10 seconds) */
    public static final long BUILD_COOLDOWN_PER_AREA_MS = 10_000L;

    /** Cooldown between template saves per player (5 seconds) */
    public static final long TEMPLATE_SAVE_COOLDOWN_MS = 5_000L;

    // ============================================================================
    // COOLDOWN MAPS
    // ============================================================================

    /** Track last build time per player UUID */
    private static final Map<UUID, Long> playerBuildCooldowns = new ConcurrentHashMap<>();

    /** Track last build time per area UUID */
    private static final Map<UUID, Long> areaBuildCooldowns = new ConcurrentHashMap<>();

    /** Track last template save time per player UUID */
    private static final Map<UUID, Long> playerTemplateSaveCooldowns = new ConcurrentHashMap<>();

    // ============================================================================
    // PUBLIC API - PLAYER BUILD COOLDOWN
    // ============================================================================

    /**
     * Checks and updates player build cooldown atomically.
     * Uses ConcurrentHashMap.compute() to prevent TOCTOU race conditions.
     *
     * @param playerId Player UUID
     * @param now      Current time in milliseconds
     * @return Remaining cooldown in seconds, or 0 if cooldown passed
     */
    public static long checkAndUpdatePlayerBuildCooldown(UUID playerId, long now) {
        long[] remainingSeconds = {0};
        playerBuildCooldowns.compute(playerId, (k, lastBuildTime) -> {
            if (lastBuildTime != null) {
                long elapsed = now - lastBuildTime;
                if (elapsed < BUILD_COOLDOWN_MS) {
                    remainingSeconds[0] = (BUILD_COOLDOWN_MS - elapsed) / 1000;
                    return lastBuildTime; // Keep old value, reject request
                }
            }
            return now; // Update to current time
        });
        return remainingSeconds[0];
    }

    /**
     * Rolls back player build cooldown if area cooldown fails.
     *
     * @param playerId Player UUID
     * @param now      The timestamp that was set
     */
    public static void rollbackPlayerBuildCooldown(UUID playerId, long now) {
        playerBuildCooldowns.compute(playerId, (k, v) -> (v != null && v == now) ? null : v);
    }

    /**
     * Clears player build cooldown (called on disconnect).
     *
     * @param playerId The UUID of the player to clear
     */
    public static void clearPlayerBuildCooldown(@Nullable UUID playerId) {
        if (playerId != null) {
            playerBuildCooldowns.remove(playerId);
        }
    }

    // ============================================================================
    // PUBLIC API - AREA BUILD COOLDOWN
    // ============================================================================

    /**
     * Checks and updates area build cooldown atomically.
     * Uses ConcurrentHashMap.compute() to prevent TOCTOU race conditions.
     *
     * @param areaId Area UUID
     * @param now    Current time in milliseconds
     * @return Remaining cooldown in seconds, or 0 if cooldown passed
     */
    public static long checkAndUpdateAreaBuildCooldown(UUID areaId, long now) {
        long[] remainingSeconds = {0};
        areaBuildCooldowns.compute(areaId, (k, lastAreaBuildTime) -> {
            if (lastAreaBuildTime != null) {
                long elapsed = now - lastAreaBuildTime;
                if (elapsed < BUILD_COOLDOWN_PER_AREA_MS) {
                    remainingSeconds[0] = (BUILD_COOLDOWN_PER_AREA_MS - elapsed) / 1000;
                    return lastAreaBuildTime; // Keep old value, reject request
                }
            }
            return now; // Update to current time
        });
        return remainingSeconds[0];
    }

    /**
     * Clears area build cooldown when area is deleted.
     *
     * @param areaId The UUID of the deleted area
     */
    public static void clearAreaBuildCooldown(@Nullable UUID areaId) {
        if (areaId != null) {
            areaBuildCooldowns.remove(areaId);
        }
    }

    // ============================================================================
    // PUBLIC API - TEMPLATE SAVE COOLDOWN
    // ============================================================================

    /**
     * Checks if player is on template save cooldown.
     *
     * @param playerId Player UUID
     * @param now      Current time in milliseconds
     * @return Remaining cooldown in seconds, or 0 if cooldown passed
     */
    public static long getTemplateSaveCooldownRemaining(UUID playerId, long now) {
        Long lastSaveTime = playerTemplateSaveCooldowns.get(playerId);
        if (lastSaveTime != null) {
            long elapsed = now - lastSaveTime;
            if (elapsed < TEMPLATE_SAVE_COOLDOWN_MS) {
                return (TEMPLATE_SAVE_COOLDOWN_MS - elapsed) / 1000;
            }
        }
        return 0;
    }

    /**
     * Updates template save cooldown for player.
     *
     * @param playerId Player UUID
     * @param now      Current time in milliseconds
     */
    public static void updateTemplateSaveCooldown(UUID playerId, long now) {
        playerTemplateSaveCooldowns.put(playerId, now);
    }

    /**
     * Clears template save cooldown for player.
     *
     * @param playerId The UUID of the player to clear
     */
    public static void clearTemplateSaveCooldown(@Nullable UUID playerId) {
        if (playerId != null) {
            playerTemplateSaveCooldowns.remove(playerId);
        }
    }

    // ============================================================================
    // CLEANUP
    // ============================================================================

    /**
     * Clears all cooldowns for a player (called on disconnect).
     * Prevents memory leaks from accumulated player UUIDs.
     *
     * @param playerId The UUID of the player to clear
     */
    public static void clearAllPlayerCooldowns(@Nullable UUID playerId) {
        if (playerId != null) {
            playerBuildCooldowns.remove(playerId);
            playerTemplateSaveCooldowns.remove(playerId);
        }
    }

    /**
     * Periodically cleans up expired cooldown entries.
     * Call this from a periodic tick handler to prevent unbounded growth.
     */
    public static void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        // Clean player cooldowns (5 second TTL * 2)
        playerBuildCooldowns.entrySet().removeIf(e -> now - e.getValue() > BUILD_COOLDOWN_MS * 2);
        // Clean area cooldowns (10 second TTL * 2)
        areaBuildCooldowns.entrySet().removeIf(e -> now - e.getValue() > BUILD_COOLDOWN_PER_AREA_MS * 2);
        // Clean template save cooldowns (5 second TTL * 2)
        playerTemplateSaveCooldowns.entrySet().removeIf(e -> now - e.getValue() > TEMPLATE_SAVE_COOLDOWN_MS * 2);
    }
}
