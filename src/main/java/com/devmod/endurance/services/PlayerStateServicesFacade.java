package com.devmod.endurance.services;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.EndurancePlayerStateManager;
import com.devmod.endurance.EnduranceQuestManager.ActiveQuestSession;
import com.devmod.runtime.InstanceData;
import com.devmod.runtime.PlayerInstanceSnapshot;
import com.devmod.runtime.PlayerInstanceState;
import com.devmod.runtime.RecoverySystem;
/**
 * Facade that unifies player state management operations.
 *
 * <p>This facade reduces coupling between EnduranceQuestManager and the
 * underlying player state services (EndurancePlayerStateManager, RecoverySystem).
 * It provides a clean, high-level API for player state operations during quests.</p>
 *
 * <h2>Unified Services:</h2>
 * <ul>
 *   <li>{@link EndurancePlayerStateManager} - Quest-specific player state (inventory, effects, position)</li>
 *   <li>{@link RecoverySystem} - Crash recovery snapshots and state persistence</li>
 * </ul>
 *
 * <h2>Common Operations:</h2>
 * <ul>
 *   <li>Preparing player for quest entry (save state, apply effects)</li>
 *   <li>Restoring player after quest (inventory, position)</li>
 *   <li>Managing recovery snapshots for crash protection</li>
 * </ul>
 */
public final class PlayerStateServicesFacade {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerStateServicesFacade.class);

    public static final PlayerStateServicesFacade INSTANCE = new PlayerStateServicesFacade();

    private PlayerStateServicesFacade() {}

    // ═══════════════════════════════════════════════════════════════
    // QUEST PREPARATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Prepares a player for entering a quest.
     * Saves current state and applies quest-ready configuration.
     *
     * @param player The player to prepare
     * @param session The quest session
     */
    public void preparePlayerForQuest(ServerPlayer player, ActiveQuestSession session) {
        EndurancePlayerStateManager.INSTANCE.preparePlayerForQuest(player, session);
        LOGGER.debug("[PlayerState] Prepared player {} for quest {}",
            player.getName().getString(), session.getQuest().getQuestId());
    }

    /**
     * Applies safe window effects (invulnerability period) to a player.
     *
     * @param player The player
     * @param ticks Duration in ticks
     */
    public void applySafeWindowEffects(ServerPlayer player, int ticks) {
        EndurancePlayerStateManager.INSTANCE.applySafeWindowEffects(player, ticks);
    }

    /**
     * Resets the player's loadout to the quest kit.
     *
     * @param player The player
     * @param session The quest session
     */
    public void resetQuestLoadout(ServerPlayer player, ActiveQuestSession session) {
        EndurancePlayerStateManager.INSTANCE.resetQuestLoadout(player, session);
    }

    // ═══════════════════════════════════════════════════════════════
    // QUEST CLEANUP
    // ═══════════════════════════════════════════════════════════════

    /**
     * Restores a player's state after quest completion.
     *
     * @param player The player to restore
     * @param session The completed quest session
     */
    public void restorePlayerAfterQuest(ServerPlayer player, ActiveQuestSession session) {
        EndurancePlayerStateManager.INSTANCE.restorePlayerAfterQuest(player, session);
        LOGGER.debug("[PlayerState] Restored player {} after quest", player.getName().getString());
    }

    /**
     * Cleans up quest-related systems for a session.
     *
     * @param session The session to clean up
     */
    public void cleanupQuestSystems(ActiveQuestSession session) {
        EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);
    }

    /**
     * Cleans up arena or instance resources for a session.
     *
     * @param session The session
     * @param completed Whether the quest was completed successfully
     */
    public void cleanupArenaOrInstance(ActiveQuestSession session, boolean completed) {
        EndurancePlayerStateManager.INSTANCE.cleanupArenaOrInstance(session, completed);
    }

    // ═══════════════════════════════════════════════════════════════
    // RECOVERY SNAPSHOTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Loads a recovery snapshot for a player.
     *
     * @param playerId The player's UUID
     * @return Optional containing the snapshot if found
     */
    public Optional<PlayerInstanceSnapshot> loadSnapshot(UUID playerId) {
        return RecoverySystem.INSTANCE.loadSnapshot(playerId);
    }

    /**
     * Saves a recovery snapshot.
     *
     * @param snapshot The snapshot to save
     */
    public void saveSnapshot(PlayerInstanceSnapshot snapshot) {
        RecoverySystem.INSTANCE.saveSnapshot(snapshot);
    }

    /**
     * Creates a recovery snapshot from a player's current state.
     *
     * @param player The player
     * @param instance The instance data
     * @return The created snapshot
     */
    public PlayerInstanceSnapshot createSnapshotFromPlayer(ServerPlayer player, InstanceData instance) {
        return RecoverySystem.INSTANCE.createSnapshotFromPlayer(player, instance);
    }

    /**
     * Updates the state of a player's snapshot.
     *
     * @param playerId The player's UUID
     * @param state The new state
     * @return true if the update succeeded, false if snapshot not found or I/O error occurred
     */
    public boolean updateSnapshotState(UUID playerId, PlayerInstanceState state) {
        return RecoverySystem.INSTANCE.updateSnapshotState(playerId, state);
    }

    /**
     * Performs recovery for a player using their snapshot.
     *
     * @param player The player to recover
     * @param snapshot The snapshot to restore from
     * @param reason The reason for recovery (for logging)
     */
    public void performRecovery(ServerPlayer player, PlayerInstanceSnapshot snapshot, String reason) {
        RecoverySystem.INSTANCE.performRecovery(player, snapshot, reason);
        LOGGER.info("[PlayerState] Performed recovery for player {}: {}",
            player.getName().getString(), reason);
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPOUND OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates a recovery snapshot and prepares a player for quest.
     * IMPORTANT: Snapshot is created FIRST to capture original state (inventory, position, etc.)
     * before preparePlayerForQuest modifies the player (clears inventory, sets survival mode).
     *
     * @param player The player
     * @param session The quest session
     * @param instance The instance data
     * @return The created snapshot containing the player's ORIGINAL state
     */
    public PlayerInstanceSnapshot preparePlayerWithSnapshot(
            ServerPlayer player,
            ActiveQuestSession session,
            InstanceData instance) {
        // CRITICAL: Create snapshot FIRST to capture original state
        // preparePlayerForQuest() clears inventory and modifies player state,
        // so we must snapshot before those modifications
        PlayerInstanceSnapshot snapshot = createSnapshotFromPlayer(player, instance);
        saveSnapshot(snapshot);

        // NOW prepare the player (clears inventory, sets survival mode, gives kit)
        preparePlayerForQuest(player, session);

        return snapshot;
    }

    /**
     * Attempts recovery if a snapshot exists, otherwise just logs.
     * This is a common pattern for error handling.
     *
     * @param player The player
     * @param reason The reason for recovery attempt
     * @return true if recovery was performed
     */
    public boolean tryRecovery(ServerPlayer player, String reason) {
        UUID playerId = player.getUUID();
        Optional<PlayerInstanceSnapshot> snapshotOpt = loadSnapshot(playerId);

        if (snapshotOpt.isPresent()) {
            performRecovery(player, snapshotOpt.get(), reason);
            return true;
        } else {
            LOGGER.warn("[PlayerState] No snapshot found for player {} during recovery attempt: {}",
                player.getName().getString(), reason);
            return false;
        }
    }

    /**
     * Full cleanup sequence for a quest session.
     *
     * @param player The player
     * @param session The session to clean up
     * @param completed Whether the quest completed successfully
     */
    public void fullCleanup(ServerPlayer player, ActiveQuestSession session, boolean completed) {
        restorePlayerAfterQuest(player, session);
        cleanupQuestSystems(session);
        cleanupArenaOrInstance(session, completed);
        LOGGER.debug("[PlayerState] Full cleanup completed for player {}", player.getName().getString());
    }
}
