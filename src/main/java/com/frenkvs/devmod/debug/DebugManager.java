package com.frenkvs.devmod.debug;

import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages debug feature states for players.
 * Server-side manager that tracks which debug features are enabled per player.
 */
public class DebugManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DebugManager.class);

    public static final DebugManager INSTANCE = new DebugManager();

    // Player UUID -> Set of enabled features
    private final Map<UUID, Set<DebugFeature>> playerFeatures = new ConcurrentHashMap<>();

    // Global subscribers (for server-wide debug like structures)
    private final Set<UUID> globalSubscribers = ConcurrentHashMap.newKeySet();

    private DebugManager() {}

    /**
     * Toggle a debug feature for a player.
     * @return true if now enabled, false if now disabled
     */
    public boolean toggle(ServerPlayer player, DebugFeature feature) {
        UUID playerId = player.getUUID();
        Set<DebugFeature> features = playerFeatures.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        boolean wasEnabled = features.contains(feature);
        if (wasEnabled) {
            features.remove(feature);
            LOGGER.debug("[Debug] Player {} disabled {}", player.getName().getString(), feature.getDisplayName());
        } else {
            features.add(feature);
            LOGGER.debug("[Debug] Player {} enabled {}", player.getName().getString(), feature.getDisplayName());
        }

        return !wasEnabled;
    }

    /**
     * Enable a debug feature for a player.
     */
    public void enable(ServerPlayer player, DebugFeature feature) {
        UUID playerId = player.getUUID();
        playerFeatures.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(feature);
        LOGGER.debug("[Debug] Player {} enabled {}", player.getName().getString(), feature.getDisplayName());
    }

    /**
     * Disable a debug feature for a player.
     */
    public void disable(ServerPlayer player, DebugFeature feature) {
        UUID playerId = player.getUUID();
        Set<DebugFeature> features = playerFeatures.get(playerId);
        if (features != null) {
            features.remove(feature);
            LOGGER.debug("[Debug] Player {} disabled {}", player.getName().getString(), feature.getDisplayName());
        }
    }

    /**
     * Check if a feature is enabled for a player.
     */
    public boolean isEnabled(ServerPlayer player, DebugFeature feature) {
        Set<DebugFeature> features = playerFeatures.get(player.getUUID());
        return features != null && features.contains(feature);
    }

    /**
     * Check if a feature is enabled for a player by UUID.
     */
    public boolean isEnabled(UUID playerId, DebugFeature feature) {
        Set<DebugFeature> features = playerFeatures.get(playerId);
        return features != null && features.contains(feature);
    }

    /**
     * Get all enabled features for a player.
     */
    public Set<DebugFeature> getEnabledFeatures(ServerPlayer player) {
        Set<DebugFeature> features = playerFeatures.get(player.getUUID());
        return features != null ? Collections.unmodifiableSet(features) : Collections.emptySet();
    }

    /**
     * Get all players who have a specific feature enabled.
     */
    public List<UUID> getPlayersWithFeature(DebugFeature feature) {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, Set<DebugFeature>> entry : playerFeatures.entrySet()) {
            if (entry.getValue().contains(feature)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Disable all features for a player (e.g., on disconnect).
     */
    public void clearPlayer(UUID playerId) {
        playerFeatures.remove(playerId);
        globalSubscribers.remove(playerId);
    }

    /**
     * Check if any player has a feature enabled (for optimization).
     */
    public boolean anyPlayerHasFeature(DebugFeature feature) {
        for (Set<DebugFeature> features : playerFeatures.values()) {
            if (features.contains(feature)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get count of players with debug features enabled.
     */
    public int getActivePlayerCount() {
        int count = 0;
        for (Set<DebugFeature> features : playerFeatures.values()) {
            if (!features.isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
