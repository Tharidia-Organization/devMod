package com.devmod.runtime;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.devmod.runtime.environment.DimensionEnvironmentManager;
/**
 * Handles teleportation to instance dimensions as an atomic transaction.
 *
 * Provides:
 * 1. Dimension lock acquisition to prevent concurrent operations
 * 2. Pre-teleport validation (dimension exists, instance is valid)
 * 3. State transition safety (IN_TRANSIT -> IN_INSTANCE or rollback)
 * 4. Automatic recovery on failure
 *
 * Usage:
 * <pre>
 * TeleportResult result = TeleportTransaction.execute(player, instanceId);
 * if (!result.isSuccess()) {
 *     // Handle failure - player is already recovered
 * }
 * </pre>
 */
public class TeleportTransaction {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeleportTransaction.class);
    private static final long LOCK_TIMEOUT_MS = 5000;

    /**
     * Result of a teleport transaction.
     */
    public enum TeleportResult {
        /** Teleport succeeded, player is now in instance */
        SUCCESS,
        /** Failed to acquire dimension lock (concurrent operation in progress) */
        LOCK_FAILED,
        /** Instance or dimension no longer exists */
        DIMENSION_NOT_FOUND,
        /** Instance is not in a valid state for teleport */
        INSTANCE_INVALID_STATE,
        /** The actual teleport operation failed */
        TELEPORT_FAILED,
        /** Player disconnected during teleport */
        PLAYER_DISCONNECTED,
        /** Recovery was performed after failure */
        RECOVERED_AFTER_FAILURE
    }

    /**
     * Execute a teleport transaction.
     * This method handles the full lifecycle:
     * 1. Acquire lock
     * 2. Validate preconditions
     * 3. Update state to IN_TRANSIT
     * 4. Execute teleport
     * 5. Update state to IN_INSTANCE (or recover on failure)
     *
     * @param player The player to teleport
     * @param instanceId The target instance
     * @return The result of the transaction
     */
    public static TeleportResult execute(ServerPlayer player, UUID instanceId) {
        return execute(player, instanceId, true);
    }

    /**
     * Execute a teleport transaction with optional state updates.
     *
     * @param player The player to teleport
     * @param instanceId The target instance
     * @param updateSnapshotState Whether to update snapshot state (false if caller handles it)
     * @return The result of the transaction
     */
    public static TeleportResult execute(ServerPlayer player, UUID instanceId, boolean updateSnapshotState) {
        UUID playerId = player.getUUID();

        LOGGER.debug("[TeleportTx] Starting transaction for {} -> instance {}",
            player.getName().getString(), instanceId);

        // 1. Try to acquire dimension lock
        try (DimensionLockManager.LockHandle lock = DimensionLockManager.INSTANCE.tryLock(instanceId, LOCK_TIMEOUT_MS)) {
            if (lock == null) {
                LOGGER.warn("[TeleportTx] Failed to acquire lock for instance {} (timeout: {}ms)",
                    instanceId, LOCK_TIMEOUT_MS);
                return TeleportResult.LOCK_FAILED;
            }

            // 2. Validate instance exists and is in valid state
            var instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
            if (instanceOpt.isEmpty()) {
                LOGGER.error("[TeleportTx] Instance {} not found in registry", instanceId);
                return TeleportResult.DIMENSION_NOT_FOUND;
            }

            InstanceData instance = instanceOpt.get();
            if (!canAcceptPlayer(instance, playerId)) {
                LOGGER.warn("[TeleportTx] Instance {} cannot accept players (state: {})",
                    instanceId, instance.getState());
                return TeleportResult.INSTANCE_INVALID_STATE;
            }

            // 3. Validate dimension exists
            if (!DynamicDimensionManager.INSTANCE.hasDimension(instanceId)) {
                LOGGER.error("[TeleportTx] Dimension for instance {} does not exist", instanceId);
                return TeleportResult.DIMENSION_NOT_FOUND;
            }

            // 4. Update state to IN_TRANSIT (if requested)
            boolean shouldUpdateSnapshotState = updateSnapshotState;
            if (shouldUpdateSnapshotState && shouldSkipSnapshotTransition(playerId)) {
                shouldUpdateSnapshotState = false;
            }
            if (shouldUpdateSnapshotState) {
                boolean stateUpdated = RecoverySystem.INSTANCE.updateSnapshotState(playerId, PlayerInstanceState.IN_TRANSIT);
                if (!stateUpdated) {
                    LOGGER.warn("[TeleportTx] Failed to update snapshot state to IN_TRANSIT for {}", playerId);
                    // Continue anyway - snapshot might not exist yet (will be created after)
                }
            }

            // 5. Execute teleport (still under lock to prevent dimension destruction)
            boolean teleportSuccess = DynamicDimensionManager.INSTANCE.teleportToInstance(player, instanceId);

            if (!teleportSuccess) {
                LOGGER.error("[TeleportTx] Teleport failed for {} to instance {}",
                    player.getName().getString(), instanceId);

                // Attempt recovery
                if (shouldUpdateSnapshotState) {
                    performRecoveryOnFailure(player, playerId, "Teleport failed");
                }
                return TeleportResult.TELEPORT_FAILED;
            }

            // 6. Success - update state to IN_INSTANCE
            if (shouldUpdateSnapshotState) {
                RecoverySystem.INSTANCE.updateSnapshotState(playerId, PlayerInstanceState.IN_INSTANCE);
            }

            // 7. Add player to instance tracking. Must happen before the save below, and
            // addPlayer() does not set the registry dirty flag itself, so mark it here or
            // the persisted instance is missing this player after a restart.
            instance.addPlayer(playerId);
            InstanceRegistry.INSTANCE.markDirty();

            // 8. Update instance state if needed
            if (instance.getState() == InstanceState.READY) {
                instance.setState(InstanceState.ACTIVE);
                LOGGER.debug("[TeleportTx] Instance {} state -> ACTIVE", instanceId);
            }
            InstanceRegistry.INSTANCE.save();

            LOGGER.info("[TeleportTx] Successfully teleported {} to instance {}",
                player.getName().getString(), instanceId);
            return TeleportResult.SUCCESS;
        }
    }

    /**
     * Execute a teleport transaction to a specific arena spawn position.
     * Used when the instance dimension is already created and the caller has
     * a precise spawn location from an ArenaHandle.
     *
     * @param player The player to teleport
     * @param instanceId The target instance
     * @param targetLevel The instance level
     * @param spawnPos The arena spawn position
     * @param updateSnapshotState Whether to update snapshot state (IN_TRANSIT -> IN_INSTANCE)
     * @return The result of the transaction
     */
    public static TeleportResult executeToArena(ServerPlayer player,
                                                UUID instanceId,
                                                ServerLevel targetLevel,
                                                BlockPos spawnPos,
                                                boolean updateSnapshotState) {
        if (player == null || instanceId == null || targetLevel == null || spawnPos == null) {
            return TeleportResult.TELEPORT_FAILED;
        }

        UUID playerId = player.getUUID();
        ResourceKey<Level> previousDim = player.level().dimension();

        LOGGER.debug("[TeleportTx] Starting arena teleport for {} -> instance {} @ {}",
            player.getName().getString(), instanceId, spawnPos);

        try (DimensionLockManager.LockHandle lock = DimensionLockManager.INSTANCE.tryLock(instanceId, LOCK_TIMEOUT_MS)) {
            if (lock == null) {
                LOGGER.warn("[TeleportTx] Failed to acquire lock for instance {} (timeout: {}ms)",
                    instanceId, LOCK_TIMEOUT_MS);
                return TeleportResult.LOCK_FAILED;
            }

            var instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
            if (instanceOpt.isEmpty()) {
                LOGGER.error("[TeleportTx] Instance {} not found in registry", instanceId);
                return TeleportResult.DIMENSION_NOT_FOUND;
            }

            InstanceData instance = instanceOpt.get();
            if (!canAcceptPlayer(instance, playerId)) {
                LOGGER.warn("[TeleportTx] Instance {} cannot accept players (state: {})",
                    instanceId, instance.getState());
                return TeleportResult.INSTANCE_INVALID_STATE;
            }

            ResourceKey<Level> expectedKey = DynamicDimensionManager.INSTANCE.getDimensionKey(instanceId);
            if (expectedKey == null || !expectedKey.equals(targetLevel.dimension())) {
                LOGGER.error("[TeleportTx] Target level does not match instance {} (expected={}, actual={})",
                    instanceId,
                    expectedKey != null ? expectedKey.location() : "null",
                    targetLevel.dimension().location());
                return TeleportResult.DIMENSION_NOT_FOUND;
            }

            if (!DynamicDimensionManager.INSTANCE.hasDimension(instanceId)) {
                LOGGER.error("[TeleportTx] Dimension for instance {} does not exist", instanceId);
                return TeleportResult.DIMENSION_NOT_FOUND;
            }

            boolean shouldUpdateSnapshotState = updateSnapshotState;
            if (shouldUpdateSnapshotState && shouldSkipSnapshotTransition(playerId)) {
                shouldUpdateSnapshotState = false;
            }
            if (shouldUpdateSnapshotState) {
                boolean stateUpdated = RecoverySystem.INSTANCE.updateSnapshotState(playerId, PlayerInstanceState.IN_TRANSIT);
                if (!stateUpdated) {
                    LOGGER.warn("[TeleportTx] Failed to update snapshot state to IN_TRANSIT for {}", playerId);
                }
            }

            // Ensure destination chunk is loaded before teleport
            targetLevel.getChunkAt(spawnPos);

            double x = spawnPos.getX() + 0.5;
            double y = spawnPos.getY();
            double z = spawnPos.getZ() + 0.5;

            player.teleportTo(
                targetLevel,
                x,
                y,
                z,
                java.util.Objects.requireNonNull(java.util.Set.<net.minecraft.world.entity.RelativeMovement>of(), "teleport flags"),
                player.getYRot(),
                player.getXRot()
            );
            player.setDeltaMovement(0, 0, 0);
            player.fallDistance = 0;

            boolean dimensionChanged = !previousDim.equals(targetLevel.dimension());
            if (dimensionChanged) {
                player.connection.resetPosition();
            }
            if (DynamicDimensionManager.INSTANCE.isInstanceDimension(targetLevel.dimension())) {
                DimensionEnvironmentManager.INSTANCE.syncEnvironmentToPlayer(player, targetLevel.dimension());
            }

            if (shouldUpdateSnapshotState) {
                RecoverySystem.INSTANCE.updateSnapshotState(playerId, PlayerInstanceState.IN_INSTANCE);
            }

            // Add before saving; addPlayer() does not mark the registry dirty on its own.
            instance.addPlayer(playerId);
            InstanceRegistry.INSTANCE.markDirty();

            if (instance.getState() == InstanceState.READY) {
                instance.setState(InstanceState.ACTIVE);
            }
            InstanceRegistry.INSTANCE.save();

            LOGGER.info("[TeleportTx] Successfully teleported {} to instance {} @ {}",
                player.getName().getString(), instanceId, spawnPos);
            return TeleportResult.SUCCESS;
        }
    }

    /**
     * Execute teleport with retry capability.
     * If the first attempt fails due to transient issues (lock contention),
     * it will retry up to maxRetries times.
     *
     * @param player The player to teleport
     * @param instanceId The target instance
     * @param maxRetries Maximum number of retry attempts
     * @return The result of the transaction
     */
    public static TeleportResult executeWithRetry(ServerPlayer player, UUID instanceId, int maxRetries) {
        TeleportResult result = TeleportResult.LOCK_FAILED;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            result = execute(player, instanceId);

            if (result == TeleportResult.SUCCESS) {
                return result;
            }

            // Only retry on transient failures
            if (result != TeleportResult.LOCK_FAILED) {
                LOGGER.debug("[TeleportTx] Non-retryable failure: {}", result);
                return result;
            }

            if (attempt < maxRetries) {
                LOGGER.debug("[TeleportTx] Retry attempt {} for {} -> {}",
                    attempt + 1, player.getName().getString(), instanceId);
                // Small delay before retry
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return TeleportResult.TELEPORT_FAILED;
                }
            }
        }

        return result;
    }

    /**
     * Attempt to recover a player after teleport failure.
     */
    private static void performRecoveryOnFailure(ServerPlayer player, UUID playerId, String reason) {
        RecoverySystem.INSTANCE.loadSnapshot(playerId).ifPresentOrElse(
            snapshot -> {
                LOGGER.info("[TeleportTx] Performing recovery for {} after failure: {}",
                    player.getName().getString(), reason);
                RecoverySystem.INSTANCE.performRecovery(player, snapshot, reason);
            },
            () -> {
                LOGGER.warn("[TeleportTx] No snapshot found for {} - cannot recover after: {}",
                    playerId, reason);
            }
        );
    }

    private static boolean canAcceptPlayer(InstanceData instance, UUID playerId) {
        return instance.canAcceptPlayers() || instance.hasPlayer(playerId);
    }

    private static boolean shouldSkipSnapshotTransition(UUID playerId) {
        var snapshotOpt = RecoverySystem.INSTANCE.loadSnapshot(playerId);
        if (snapshotOpt.isEmpty()) {
            LOGGER.warn("[TeleportTx] No snapshot found for {} - skipping state updates", playerId);
            return true;
        }
        PlayerInstanceState state = snapshotOpt.get().getState();
        return state == PlayerInstanceState.IN_INSTANCE;
    }

    /**
     * Check if a teleport to the given instance would likely succeed.
     * This is a fast check without acquiring locks - use for UI feedback.
     *
     * @param instanceId The target instance
     * @return true if teleport prerequisites are met
     */
    public static boolean canTeleport(UUID instanceId) {
        // Check instance exists
        var instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            return false;
        }

        // Check instance state
        if (!instanceOpt.get().canAcceptPlayers()) {
            return false;
        }

        // Check dimension exists
        return DynamicDimensionManager.INSTANCE.hasDimension(instanceId);
    }
}
