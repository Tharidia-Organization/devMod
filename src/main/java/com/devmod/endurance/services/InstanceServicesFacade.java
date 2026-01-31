package com.devmod.endurance.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.InstanceArenaManager;
import com.devmod.runtime.InstanceData;
import com.devmod.runtime.InstanceManager;
import com.devmod.runtime.InstanceRegistry;
/**
 * Facade that unifies instance-related operations across multiple services.
 *
 * <p>This facade reduces coupling between EnduranceQuestManager and the
 * underlying instance services (InstanceManager, InstanceRegistry, InstanceArenaManager).
 * It provides a clean, high-level API for common instance operations.</p>
 *
 * <h2>Unified Services:</h2>
 * <ul>
 *   <li>{@link InstanceManager} - Instance lifecycle and dimension management</li>
 *   <li>{@link InstanceRegistry} - Instance data persistence and lookup</li>
 *   <li>{@link InstanceArenaManager} - Quest-specific arena instance coordination</li>
 * </ul>
 *
 * <h2>Benefits:</h2>
 * <ul>
 *   <li>Single point of access for instance operations</li>
 *   <li>Encapsulates error handling and cleanup logic</li>
 *   <li>Reduces INSTANCE.method() calls scattered throughout codebase</li>
 *   <li>Easier to test and mock</li>
 * </ul>
 */
public final class InstanceServicesFacade {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceServicesFacade.class);

    public static final InstanceServicesFacade INSTANCE = new InstanceServicesFacade();

    private InstanceServicesFacade() {}

    // ═══════════════════════════════════════════════════════════════
    // INSTANCE LOOKUP
    // ═══════════════════════════════════════════════════════════════

    /**
     * Gets instance data by ID.
     *
     * @param instanceId The instance UUID
     * @return Optional containing instance data if found
     */
    public Optional<InstanceData> getInstance(UUID instanceId) {
        return InstanceRegistry.INSTANCE.getInstance(instanceId);
    }

    /**
     * Gets the instance data for a player (if they're mapped to one).
     *
     * @param playerId The player's UUID
     * @return Optional containing instance data if player is in an instance
     */
    public Optional<InstanceData> getPlayerInstance(UUID playerId) {
        return InstanceRegistry.INSTANCE.getPlayerInstance(playerId);
    }

    /**
     * Gets the instance ID for a player (if they're mapped to one).
     *
     * @param playerId The player's UUID
     * @return The instance UUID, or null if not in an instance
     */
    public UUID getPlayerInstanceId(UUID playerId) {
        return InstanceRegistry.INSTANCE.getPlayerInstanceId(playerId);
    }

    /**
     * Checks if an instance exists and is in a valid state.
     *
     * @param instanceId The instance UUID
     * @return true if instance exists and is usable
     */
    public boolean isInstanceValid(UUID instanceId) {
        return getInstance(instanceId)
            .map(data -> data.getState() != null && !data.getState().isTerminal())
            .orElse(false);
    }

    // ═══════════════════════════════════════════════════════════════
    // INSTANCE CREATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Starts an instance asynchronously for a quest.
     *
     * @param player The player starting the quest
     * @param arenaId The arena template ID
     * @param questId The quest identifier
     * @param partyMembers Optional list of party member UUIDs (null for solo)
     * @return CompletableFuture that completes with the instance ID
     */
    public CompletableFuture<UUID> startInstanceQuest(
            ServerPlayer player,
            String arenaId,
            String questId,
            @Nullable List<UUID> partyMembers) {
        return InstanceManager.INSTANCE.startInstanceQuest(player, arenaId, questId, partyMembers);
    }

    /**
     * Maps a player to an instance.
     *
     * @param playerId The player's UUID
     * @param instanceId The instance UUID
     */
    public void mapPlayer(UUID playerId, UUID instanceId) {
        InstanceRegistry.INSTANCE.mapPlayer(playerId, instanceId);
    }

    /**
     * Marks the instance registry as dirty (needs saving).
     */
    public void markDirty() {
        InstanceRegistry.INSTANCE.markDirty();
    }

    /**
     * Saves the instance registry.
     */
    public void save() {
        InstanceRegistry.INSTANCE.save();
    }

    // ═══════════════════════════════════════════════════════════════
    // INSTANCE CLEANUP
    // ═══════════════════════════════════════════════════════════════

    /**
     * Cleans up an instance after a quest failure or error.
     * This is the most common cleanup operation.
     *
     * @param instanceId The instance UUID to clean up
     */
    public void cleanupFailedQuest(UUID instanceId) {
        if (instanceId == null) {
            LOGGER.warn("[InstanceServices] Attempted to cleanup null instanceId");
            return;
        }

        try {
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            LOGGER.debug("[InstanceServices] Cleaned up failed quest instance: {}", instanceId);
        } catch (Exception e) {
            LOGGER.error("[InstanceServices] Error cleaning up failed quest instance {}: {}",
                instanceId, e.getMessage());
        }
    }

    /**
     * Cleans up an instance after successful quest completion.
     *
     * @param instanceId The instance UUID to clean up
     */
    public void cleanupCompletedQuest(UUID instanceId) {
        if (instanceId == null) {
            LOGGER.warn("[InstanceServices] Attempted to cleanup null instanceId");
            return;
        }

        try {
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, true);
            LOGGER.debug("[InstanceServices] Cleaned up completed quest instance: {}", instanceId);
        } catch (Exception e) {
            LOGGER.error("[InstanceServices] Error cleaning up completed quest instance {}: {}",
                instanceId, e.getMessage());
        }
    }

    /**
     * Cleans up an instance with a specific reason (for detailed logging).
     *
     * @param instanceId The instance UUID
     * @param success Whether the quest completed successfully
     * @param reason The reason for cleanup (for logging)
     */
    public void cleanupQuest(UUID instanceId, boolean success, String reason) {
        if (instanceId == null) {
            LOGGER.warn("[InstanceServices] Attempted to cleanup null instanceId, reason: {}", reason);
            return;
        }

        try {
            InstanceManager.INSTANCE.endInstanceQuest(instanceId, success, reason);
            LOGGER.debug("[InstanceServices] Cleaned up quest instance {} (success={}, reason={})",
                instanceId, success, reason);
        } catch (Exception e) {
            LOGGER.error("[InstanceServices] Error cleaning up quest instance {} (reason={}): {}",
                instanceId, reason, e.getMessage());
        }
    }

    /**
     * Safely cleans up an instance, checking if it exists first.
     * Use this when the instance might already be cleaned up.
     *
     * @param instanceId The instance UUID
     * @param success Whether the quest completed successfully
     * @return true if cleanup was performed, false if instance didn't exist
     */
    public boolean safeCleanup(UUID instanceId, boolean success) {
        if (instanceId == null) {
            return false;
        }

        Optional<InstanceData> instanceOpt = getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            LOGGER.debug("[InstanceServices] Instance {} already cleaned up or doesn't exist", instanceId);
            return false;
        }

        if (success) {
            cleanupCompletedQuest(instanceId);
        } else {
            cleanupFailedQuest(instanceId);
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPOUND OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Validates that an instance exists and returns it, or cleans up and returns empty.
     * This is a common pattern in error handling.
     *
     * @param instanceId The instance UUID
     * @param errorContext Context for error logging
     * @return Optional containing instance data, or empty if invalid/cleaned up
     */
    public Optional<InstanceData> getInstanceOrCleanup(UUID instanceId, String errorContext) {
        if (instanceId == null) {
            LOGGER.warn("[InstanceServices] Null instanceId in {}", errorContext);
            return Optional.empty();
        }

        Optional<InstanceData> instanceOpt = getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            LOGGER.warn("[InstanceServices] Instance {} not found in {}, cleaning up", instanceId, errorContext);
            cleanupFailedQuest(instanceId);
            return Optional.empty();
        }

        return instanceOpt;
    }
}
