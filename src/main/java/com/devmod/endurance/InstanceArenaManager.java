package com.devmod.endurance;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.runtime.InstanceManager;
import com.devmod.runtime.InstanceRegistry;
public class InstanceArenaManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceArenaManager.class);
    public static final InstanceArenaManager INSTANCE = new InstanceArenaManager();

    // Map from arena ID to instance ID (arena.getId() -> instanceId)
    private final Map<UUID, UUID> arenaToInstance = new ConcurrentHashMap<>();

    // Map from instance ID to arena ID (for reverse lookup when ending by instanceId)
    private final Map<UUID, UUID> instanceToArena = new ConcurrentHashMap<>();

    // Map from player to pending instance creation
    private final Map<UUID, CompletableFuture<InstanceQuestResult>> pendingCreations = new ConcurrentHashMap<>();

    // Feature flag - can be toggled via config
    private boolean useInstanceDimensions = true;

    private InstanceArenaManager() {}

    /**
     * Check if instance dimensions are enabled.
     */
    public boolean isEnabled() {
        return useInstanceDimensions && InstanceManager.INSTANCE.isReady();
    }

    /**
     * Enable or disable instance dimensions.
     */
    public void setEnabled(boolean enabled) {
        this.useInstanceDimensions = enabled;
        LOGGER.info("[InstanceArena] Instance dimensions {}", enabled ? "enabled" : "disabled");
    }

    /**
     * End an instance quest and destroy the dimension.
     * Called by EnduranceQuestManager with the instanceId stored in the session.
     *
     * @param instanceId The instance ID to end
     * @param success Whether the quest was completed successfully
     */
    public void endInstanceQuest(UUID instanceId, boolean success) {
        // Remove from both maps
        UUID arenaId = instanceToArena.remove(instanceId);
        if (arenaId != null) {
            arenaToInstance.remove(arenaId);
        }

        // Verify instance exists
        if (!InstanceRegistry.INSTANCE.getInstance(instanceId).isPresent()) {
            LOGGER.warn("[InstanceArena] Instance {} not found in registry", instanceId);
            // Still try to end it through InstanceManager in case of partial state
        }

        LOGGER.info("[InstanceArena] Ending instance quest {} (success: {})", instanceId, success);

        // End through InstanceManager - this handles player recovery and dimension destruction
        InstanceManager.INSTANCE.endInstanceQuest(instanceId, success,
            success ? "Quest completed" : "Quest ended");
    }

    /**
     * Force end a player's instance quest (e.g., on disconnect).
     */
    public void forceEndPlayerQuest(UUID playerId) {
        // Cancel any pending creation
        CompletableFuture<InstanceQuestResult> pending = pendingCreations.remove(playerId);
        if (pending != null && !pending.isDone()) {
            pending.cancel(true);
        }

        // Find and remove any session mapping for this player
        // Note: This is handled by InstanceManager.forceEndPlayerInstances
        InstanceManager.INSTANCE.forceEndPlayerInstances(playerId);
    }

    /**
     * Check if a player has a pending instance creation.
     */
    public boolean hasPendingCreation(UUID playerId) {
        CompletableFuture<InstanceQuestResult> pending = pendingCreations.get(playerId);
        return pending != null && !pending.isDone();
    }

    /**
     * Get the instance ID for an arena.
     */
    public Optional<UUID> getInstanceForArena(UUID arenaId) {
        return Optional.ofNullable(arenaToInstance.get(arenaId));
    }

    /**
     * Get the arena ID for an instance.
     */
    public Optional<UUID> getArenaForInstance(UUID instanceId) {
        return Optional.ofNullable(instanceToArena.get(instanceId));
    }

    /**
     * Result of starting an instance quest.
     */
    public record InstanceQuestResult(
        boolean success,
        String message,
        @Nullable UUID instanceId,
        @Nullable ArenaContext arena
    ) {}
}
