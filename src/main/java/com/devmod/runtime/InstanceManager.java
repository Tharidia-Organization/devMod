package com.devmod.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.EnduranceLogger;
import com.devmod.endurance.EnduranceLogger.Phase;
import com.devmod.shared.SharedColorTokens;

public class InstanceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceManager.class);
    public static final InstanceManager INSTANCE = new InstanceManager();

    // Teleport countdown in ticks (10 seconds = 200 ticks)
    private static final int TELEPORT_COUNTDOWN_TICKS = 200;

    private enum TeleportMode {
        IMMEDIATE,
        COUNTDOWN,
        NONE
    }

    // Pending teleports (players in countdown)
    private final Map<UUID, TeleportRequest> pendingTeleports = new ConcurrentHashMap<>();

    // Pending recoveries (delayed by 1 tick after respawn to let respawn complete)
    private final Map<UUID, PendingRecovery> pendingRecoveries = new ConcurrentHashMap<>();

    private MinecraftServer server;
    // Volatile for thread visibility across initialization and tick threads
    private volatile boolean initialized = false;

    private InstanceManager() {}

    // === Lifecycle ===

    /**
     * Initialize the instance system.
     * Called during ServerStartedEvent.
     */
    public void initialize(MinecraftServer server) {
        this.server = server;

        // Initialize subsystems
        RecoverySystem.INSTANCE.initialize(server);
        InstanceRegistry.INSTANCE.load(server);
        DynamicDimensionManager.INSTANCE.initialize(server);

        // Perform startup recovery
        RecoverySystem.INSTANCE.performStartupCleanup(server);

        // Process any pending destructions
        InstanceRegistry.INSTANCE.processPendingDestructions();

        this.initialized = true;
        LOGGER.info("[InstanceManager] Initialized");
    }

    /**
     * Shutdown the instance system.
     * Called during ServerStoppingEvent.
     */
    public void shutdown() {
        LOGGER.info("[InstanceManager] Shutting down...");

        // Cancel all pending teleports
        pendingTeleports.clear();

        // Save registry state, then drop it so a subsequently loaded world does not
        // inherit this world's instances and player mappings.
        InstanceRegistry.INSTANCE.save();

        // Shutdown dimension manager (destroys remaining instances)
        DynamicDimensionManager.INSTANCE.shutdown();

        InstanceRegistry.INSTANCE.clear();

        this.server = null;
        this.initialized = false;

        LOGGER.info("[InstanceManager] Shutdown complete");
    }

    // === Quest Instance API ===

    /**
     * Start a new instance quest for a player (or party) with immediate teleport.
     * This variant skips the 10-second countdown and teleports immediately after
     * the dimension is created. Use this for integration with quest systems that
     * need synchronous completion.
     *
     * @param player The player (or party leader) starting the quest
     * @param arenaId The arena template to use
     * @param questId The quest identifier
     * @param partyMembers Optional list of party member UUIDs (null for solo)
     * @return CompletableFuture with the instance ID, or null on failure
     */
    public CompletableFuture<UUID> startInstanceQuestImmediate(
            ServerPlayer player,
            String arenaId,
            String questId,
            @Nullable List<UUID> partyMembers
    ) {
        return startInstanceQuestInternal(player, arenaId, questId, partyMembers, TeleportMode.IMMEDIATE);
    }

    /**
     * Start a new instance quest for a player (or party) with countdown.
     * This variant shows a 10-second countdown before teleporting.
     * Suitable for standalone instance teleports where player needs preparation time.
     *
     * @param player The player (or party leader) starting the quest
     * @param arenaId The arena template to use
     * @param questId The quest identifier
     * @param partyMembers Optional list of party member UUIDs (null for solo)
     * @return CompletableFuture with the instance ID, or null on failure
     */
    public CompletableFuture<UUID> startInstanceQuest(
            ServerPlayer player,
            String arenaId,
            String questId,
            @Nullable List<UUID> partyMembers
    ) {
        return startInstanceQuestInternal(player, arenaId, questId, partyMembers, TeleportMode.COUNTDOWN);
    }

    /**
     * Prepare a new instance quest WITHOUT teleporting players.
     * This is used when the caller needs to build/prepare the arena first,
     * then teleport at a controlled time.
     */
    public CompletableFuture<UUID> prepareInstanceQuest(
            ServerPlayer player,
            String arenaId,
            String questId,
            @Nullable List<UUID> partyMembers
    ) {
        return startInstanceQuestInternal(player, arenaId, questId, partyMembers, TeleportMode.NONE);
    }

    /**
     * Internal implementation for starting instance quests.
     *
     * @param player The player (or party leader) starting the quest
     * @param arenaId The arena template to use
     * @param questId The quest identifier
     * @param partyMembers Optional list of party member UUIDs (null for solo)
     * @param mode Teleport mode (immediate, countdown, or none)
     * @return CompletableFuture with the instance ID, or null on failure
     */
    private CompletableFuture<UUID> startInstanceQuestInternal(
            ServerPlayer player,
            String arenaId,
            String questId,
            @Nullable List<UUID> partyMembers,
            TeleportMode mode
    ) {
        if (!initialized) {
            LOGGER.error("[InstanceManager] Not initialized");
            return CompletableFuture.completedFuture(null);
        }

        UUID playerId = nn(player.getUUID(), "player uuid");

        // Check if player is already in an instance
        var existingInstance = InstanceRegistry.INSTANCE.getPlayerInstance(playerId);
        if (existingInstance.isPresent()) {
            InstanceData existing = existingInstance.get();
            LOGGER.warn("[InstanceDebug] Player {} already mapped to instance {} (state={}, dimKey={}). Blocking new quest start.",
                player.getName().getString(), existing.getInstanceId(), existing.getState(), existing.getDimensionKey());
            player.sendSystemMessage(styledMsg("[DevMod] You are already in an instance!", SharedColorTokens.Chat.RED));
            return CompletableFuture.completedFuture(null);
        }

        // Check if player has a pending teleport
        if (pendingTeleports.containsKey(playerId)) {
            player.sendSystemMessage(styledMsg("[DevMod] Teleport already in progress!", SharedColorTokens.Chat.RED));
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.info("[InstanceManager] Starting instance quest for {} (arena: {}, quest: {})",
            player.getName().getString(), arenaId, questId);

        // Collect all players (leader + party members)
        List<ServerPlayer> allPlayers = new ArrayList<>();
        allPlayers.add(player);

            if (partyMembers != null && !partyMembers.isEmpty()) {
                for (UUID rawMemberId : partyMembers) {
                    UUID memberId = nn(rawMemberId, "party member id");
                    ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                    if (member != null && !memberId.equals(playerId)) {
                        allPlayers.add(member);
                    }
                }
            }

        // Create instance data
        InstanceData instance;
        if (partyMembers != null && !partyMembers.isEmpty()) {
            java.util.Set<UUID> uniqueMembers = new java.util.HashSet<>(partyMembers);
            uniqueMembers.add(playerId);
            int maxPlayers = Math.max(1, uniqueMembers.size());
            instance = InstanceRegistry.INSTANCE.createPartyInstance(arenaId, playerId, maxPlayers);
        } else {
            instance = InstanceRegistry.INSTANCE.createInstance(arenaId, playerId);
        }
        UUID instanceId = instance.getInstanceId();

        // Add all players to instance
        for (ServerPlayer p : allPlayers) {
            instance.addPlayer(nn(p.getUUID(), "player uuid"));
        }

        // Save instance
        InstanceRegistry.INSTANCE.save();

        // Phase 1: Create snapshots for all players BEFORE any risky operations
        for (ServerPlayer p : allPlayers) {
            PlayerInstanceSnapshot snapshot = RecoverySystem.INSTANCE.createSnapshotFromPlayer(p, instance);
            snapshot.setState(PlayerInstanceState.PREPARING);

            // Set party info if multiplayer
            if (partyMembers != null && !partyMembers.isEmpty()) {
                snapshot.setPartyLeaderId(playerId);
                snapshot.setPartyMembers(new HashSet<>(partyMembers));
            }

            RecoverySystem.INSTANCE.saveSnapshot(snapshot);
            InstanceRegistry.INSTANCE.mapPlayer(nn(p.getUUID(), "player uuid"), instanceId);
        }

        // Notify players (different message based on mode)
        for (ServerPlayer p : allPlayers) {
            if (mode == TeleportMode.COUNTDOWN) {
                p.sendSystemMessage(styledMsg("[DevMod] Preparing instance... teleporting in 10 seconds",
                    SharedColorTokens.Chat.GOLD));
            } else {
                p.sendSystemMessage(styledMsg("[DevMod] Preparing instance...", SharedColorTokens.Chat.GOLD));
            }
        }

        // Capture mode for lambda
        final TeleportMode teleportMode = mode;

        // Phase 2: Create dimension asynchronously
        // Note: createDimensionAsync schedules on server thread, so thenApply also runs on server thread
        return DynamicDimensionManager.INSTANCE.createDimensionAsync(instanceId, arenaId)
            .thenApply(dimensionKey -> {
                if (dimensionKey == null) {
                    // Creation failed - recover all players that are still online
                    LOGGER.error("[InstanceManager] Dimension creation failed for instance {}", instanceId);
                    for (ServerPlayer p : allPlayers) {
                        // Verify player is still online before recovery
                        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(nn(p.getUUID(), "player uuid"));
                        if (onlinePlayer != null) {
                            RecoverySystem.INSTANCE.loadSnapshot(nn(p.getUUID(), "player uuid")).ifPresent(snapshot -> {
                                RecoverySystem.INSTANCE.performRecovery(onlinePlayer, snapshot, "Instance creation failed");
                            });
                        } else {
                            // Player disconnected - snapshot will handle recovery on next login
                            LOGGER.info("[InstanceManager] Player {} disconnected during instance creation, snapshot preserved for login recovery",
                                nn(p.getUUID(), "player uuid"));
                        }
                    }
                    InstanceRegistry.INSTANCE.removeInstance(instanceId);
                    return null;
                }

                // Update instance with dimension
                InstanceRegistry.INSTANCE.setDimensionKey(instanceId, dimensionKey);
                instance.setState(InstanceState.READY);
                InstanceRegistry.INSTANCE.save();

                // Phase 3: Teleport or start countdown based on mode
                // Re-fetch players from server to ensure they're still online
                for (ServerPlayer originalPlayer : allPlayers) {
                    ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(nn(originalPlayer.getUUID(), "player uuid"));
                    if (onlinePlayer == null) {
                        // Player disconnected during dimension creation
                        UUID originalId = nn(originalPlayer.getUUID(), "player uuid");
                        LOGGER.warn("[InstanceManager] Player {} disconnected before teleport, skipping",
                            originalId);
                        // Remove from instance, snapshot preserved for login recovery
                        instance.removePlayer(originalId);
                        InstanceRegistry.INSTANCE.unmapPlayer(originalId);
                        continue;
                    }

                    if (teleportMode == TeleportMode.IMMEDIATE) {
                        executeImmediateTeleport(onlinePlayer, instanceId);
                    } else if (teleportMode == TeleportMode.COUNTDOWN) {
                        startTeleportCountdown(onlinePlayer, instanceId);
                    } else {
                        LOGGER.debug("[InstanceManager] Instance {} ready for {}, teleport deferred",
                            instanceId, onlinePlayer.getName().getString());
                    }
                }

                // NOTE: Don't check isEmpty() here - players might have been successfully teleported
                // and the instance is active. The destruction will be handled by:
                // - InstanceData.removePlayer() when last player leaves
                // - forceEndPlayerInstances() on disconnect
                // - Normal quest completion flow

                return instanceId;
            });
    }

    /**
     * Execute immediate teleport without countdown.
     * Used when integrating with quest systems that need synchronous completion.
     */
    private void executeImmediateTeleport(ServerPlayer player, UUID instanceId) {
        LOGGER.info("[InstanceManager] Executing immediate teleport for {} to instance {}",
            player.getName().getString(), instanceId);

        // Use TeleportTransaction for atomic teleport with proper locking, validation, and state management
        TeleportTransaction.TeleportResult result = TeleportTransaction.execute(
            player,
            instanceId,
            true // update snapshot state (IN_TRANSIT -> IN_INSTANCE)
        );

        if (result == TeleportTransaction.TeleportResult.SUCCESS) {
            player.sendSystemMessage(styledMsg("[DevMod] Welcome to the arena! Good luck!", SharedColorTokens.Chat.GREEN));

            LOGGER.info("[InstanceManager] Player {} successfully teleported to instance {} (immediate)",
                player.getName().getString(), instanceId);
        } else {
            // Teleport failed - TeleportTransaction already performed recovery if needed
            LOGGER.error("[InstanceManager] Immediate teleport failed for {} to instance {} (result: {})",
                player.getName().getString(), instanceId, result);
        }
    }

    /**
     * Start the teleport countdown for a player.
     */
    private void startTeleportCountdown(ServerPlayer player, UUID instanceId) {
        UUID playerId = nn(player.getUUID(), "player uuid");

        TeleportRequest request = new TeleportRequest(
            playerId,
            instanceId,
            TELEPORT_COUNTDOWN_TICKS
        );

        pendingTeleports.put(playerId, request);

        // Update snapshot state
        RecoverySystem.INSTANCE.updateSnapshotState(playerId, PlayerInstanceState.IN_TRANSIT);

        LOGGER.debug("[InstanceManager] Started teleport countdown for {}", player.getName().getString());
    }

    /**
     * Called every server tick to process pending teleports and recoveries.
     */
    public void tick() {
        if (!initialized) return;

        // Process pending recoveries (delayed after respawn)
        processPendingRecoveries();

        // Note: Removed isEmpty() optimization to avoid TOCTOU race window.
        // The iterator is safe for ConcurrentHashMap even if entries are added/removed.
        Iterator<Map.Entry<UUID, TeleportRequest>> iterator = pendingTeleports.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, TeleportRequest> entry = iterator.next();
            TeleportRequest request = entry.getValue();

            // Safety check: remove stale requests that somehow got stuck
            if (request.isStale()) {
                LOGGER.warn("[InstanceManager] Removing stale teleport request for player {} (age: {}ms)",
                    request.playerId, System.currentTimeMillis() - request.createdAt);
                iterator.remove();

                // Try to recover the player
                ServerPlayer player = server.getPlayerList().getPlayer(nn(request.playerId, "request playerId"));
                if (player != null) {
                    RecoverySystem.INSTANCE.loadSnapshot(request.playerId).ifPresent(snapshot -> {
                        RecoverySystem.INSTANCE.performRecovery(player, snapshot, "Teleport timed out");
                    });
                }
                continue;
            }

            request.ticksRemaining--;

            // Check if player is still online
            ServerPlayer player = server.getPlayerList().getPlayer(nn(request.playerId, "request playerId"));
            if (player == null) {
                LOGGER.warn("[InstanceManager] Player {} disconnected during countdown", request.playerId);
                iterator.remove();
                // Snapshot is preserved for recovery on next login
                continue;
            }

            // Send countdown messages at intervals
            if (request.ticksRemaining == 100) { // 5 seconds
                        player.sendSystemMessage(styledMsg("[DevMod] Teleporting in 5 seconds...", SharedColorTokens.Chat.YELLOW));
                    } else if (request.ticksRemaining == 60) { // 3 seconds
                        player.sendSystemMessage(styledMsg("[DevMod] Teleporting in 3 seconds...", SharedColorTokens.Chat.YELLOW));
                    } else if (request.ticksRemaining == 20) { // 1 second
                        player.sendSystemMessage(styledMsg("[DevMod] Teleporting in 1 second...", SharedColorTokens.Chat.YELLOW));
                    }

            // Execute teleport when countdown reaches zero
            if (request.ticksRemaining <= 0) {
                // IMPORTANT: Only remove request AFTER successful teleport
                // This allows retry on transient failures
                boolean success = executeTeleport(player, request);
                if (success) {
                    iterator.remove();
                } else {
                    // Check if we should retry or give up
                    request.failureCount++;
                    if (request.failureCount >= 3) {
                        LOGGER.error("[InstanceManager] Teleport failed {} times for {}, giving up",
                            request.failureCount, player.getName().getString());
                        iterator.remove();
                        // Recovery already performed by executeTeleport
                    } else {
                        // Reset countdown for retry
                        request.ticksRemaining = 20; // 1 second before retry
                        LOGGER.warn("[InstanceManager] Teleport failed for {}, will retry (attempt {})",
                            player.getName().getString(), request.failureCount);
                    }
                }
            }
        }
    }

    /**
     * Process pending recoveries that were delayed after player respawn.
     */
    private void processPendingRecoveries() {
        if (pendingRecoveries.isEmpty()) return;

        Iterator<Map.Entry<UUID, PendingRecovery>> iterator = pendingRecoveries.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingRecovery> entry = iterator.next();
            PendingRecovery recovery = entry.getValue();

            recovery.ticksRemaining--;

            if (recovery.ticksRemaining <= 0) {
                iterator.remove();

                // Execute recovery
                ServerPlayer player = server.getPlayerList().getPlayer(nn(recovery.playerId, "recovery playerId"));
                if (player != null) {
                    LOGGER.info("[InstanceManager] Executing delayed recovery for {} (reason: {})",
                        player.getName().getString(), recovery.reason);
                    RecoverySystem.INSTANCE.performRecovery(player, recovery.snapshot, recovery.reason);
                } else {
                    LOGGER.warn("[InstanceManager] Player {} disconnected before delayed recovery could execute",
                        recovery.playerId);
                    // Snapshot is preserved for recovery on next login
                }
            }
        }
    }

    /**
     * Execute the actual teleport to the instance dimension.
     *
     * @return true if teleport succeeded, false if failed (recovery performed)
     */
    private boolean executeTeleport(ServerPlayer player, TeleportRequest request) {
        LOGGER.info("[InstanceManager] Executing teleport for {} to instance {}",
            player.getName().getString(), request.instanceId);

        // Use TeleportTransaction for atomic teleport with proper locking and validation
        TeleportTransaction.TeleportResult result = TeleportTransaction.execute(
            player,
            request.instanceId,
            true // update snapshot state
        );

        if (result == TeleportTransaction.TeleportResult.SUCCESS) {
            player.sendSystemMessage(styledMsg("[DevMod] Welcome to the arena! Good luck!", SharedColorTokens.Chat.GREEN));

            LOGGER.info("[InstanceManager] Player {} successfully teleported to instance {}",
                player.getName().getString(), request.instanceId);
            return true;
        } else {
            // Teleport failed - TeleportTransaction already performed recovery if needed
            LOGGER.error("[InstanceManager] Teleport failed for {} to instance {} (result: {})",
                player.getName().getString(), request.instanceId, result);
            return false;
        }
    }

    /**
     * End an instance quest (success or failure).
     *
     * @param instanceId The instance to end
     * @param success Whether the quest was completed successfully
     * @param reason The reason for ending (for logging/notification)
     */
    public void endInstanceQuest(UUID instanceId, boolean success, String reason) {
        if (!initialized) return;

        Optional<InstanceData> instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            LOGGER.warn("[InstanceManager] Cannot end instance {} - not found", instanceId);
            return;
        }

        InstanceData instance = instanceOpt.get();
        LOGGER.info("[InstanceManager] Ending instance {} (success: {}, reason: {})",
            instanceId, success, reason);

        // Structured logging for instance end
        EnduranceLogger.phase(Phase.INSTANCE_DESTROY, instanceId,
            "Starting instance cleanup: success=%s, reason=%s, playerCount=%d",
            success, reason, instance.getPlayerIds().size());

        // Update state
        instance.setState(InstanceState.COMPLETING);

        // Get all players in instance
        Set<UUID> playerIds = instance.getPlayerIds();

        for (UUID playerId : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(nn(playerId, "player id"));

            if (player != null) {
                // Update snapshot state
                RecoverySystem.INSTANCE.updateSnapshotState(playerId, PlayerInstanceState.RETURNING);

                // Notify player
                if (success) {
                    player.sendSystemMessage(styledMsg("[DevMod] Quest completed! Returning to overworld...", SharedColorTokens.Chat.GREEN));
                } else {
                    player.sendSystemMessage(styledMsg("[DevMod] " + reason + ". Returning to overworld...", SharedColorTokens.Chat.RED));
                }

                // Force respawn if player is dead - can't teleport dead players
                // After respawn, we need to delay recovery by a few ticks to let
                // Minecraft's respawn logic fully complete before teleporting
                boolean needsDelayedRecovery = false;
                if (player.isDeadOrDying()) {
                    LOGGER.info("[InstanceManager] Force respawning dead player {} before recovery",
                        player.getName().getString());
                    player = server.getPlayerList().respawn(player, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                    needsDelayedRecovery = true;
                }

                // Return player to original position
                // NOTE: performRecovery() also calls unmapPlayer() internally, so we don't need to do it again
                final ServerPlayer finalPlayer = player;
                final boolean delayRecovery = needsDelayedRecovery;
                final String recoveryReason = success ? "Quest completed" : reason;

                if (finalPlayer != null) {
                    RecoverySystem.INSTANCE.loadSnapshot(playerId).ifPresent(snapshot -> {
                        if (delayRecovery) {
                            // Delay recovery by 2 ticks to let respawn fully complete
                            LOGGER.info("[InstanceManager] Scheduling delayed recovery for {} (2 ticks)",
                                finalPlayer.getName().getString());
                            pendingRecoveries.put(playerId, new PendingRecovery(playerId, snapshot, recoveryReason, 2));
                        } else {
                            RecoverySystem.INSTANCE.performRecovery(finalPlayer, snapshot, recoveryReason);
                        }
                    });
                }
            } else {
                // Player is offline - just clean up mapping
                // (their snapshot will be used for recovery on next login)
                InstanceRegistry.INSTANCE.unmapPlayer(playerId);
            }
        }

        // Schedule instance destruction
        instance.setState(InstanceState.DESTROYING);
        InstanceRegistry.INSTANCE.scheduleDestruction(instanceId);
        InstanceRegistry.INSTANCE.save();

        EnduranceLogger.phase(Phase.INSTANCE_DESTROY, instanceId,
            "Instance scheduled for destruction: state=%s", instance.getState());
        LOGGER.info("[InstanceManager] Instance {} scheduled for destruction", instanceId);
    }

    /**
     * Force-end all instances for a player (e.g., on disconnect).
     */
    public void forceEndPlayerInstances(UUID playerId) {
        // Cancel any pending teleport
        pendingTeleports.remove(playerId);

        // Find player's instance
        Optional<InstanceData> instanceOpt = InstanceRegistry.INSTANCE.getPlayerInstance(playerId);
        if (instanceOpt.isEmpty()) return;

        InstanceData instance = instanceOpt.get();
        UUID instanceId = instance.getInstanceId();

        // Remove player from instance
        instance.removePlayer(playerId);
        InstanceRegistry.INSTANCE.unmapPlayer(playerId);

        // If instance is now empty, schedule destruction
        if (instance.getPlayerIds().isEmpty()) {
            LOGGER.info("[InstanceManager] Instance {} is now empty, scheduling destruction", instanceId);
            InstanceRegistry.INSTANCE.scheduleDestruction(instanceId);
        }

        InstanceRegistry.INSTANCE.save();
    }

    // === Event Handlers ===

    /**
     * Handle player login - check for recovery.
     */
    public void onPlayerLogin(ServerPlayer player) {
        if (!initialized) return;

        // Check for pending recovery
        RecoverySystem.INSTANCE.checkPendingRecovery(player);
    }

    /**
     * Handle player logout - fail quest if in instance.
     */
    public void onPlayerLogout(ServerPlayer player) {
        if (!initialized) return;

        UUID playerId = nn(player.getUUID(), "player uuid");
        if (com.devmod.endurance.EnduranceQuestManager.INSTANCE.isPartyRunActiveForPlayer(playerId)) {
            LOGGER.info("[InstanceManager] Player {} disconnected during party run; preserving instance",
                player.getName().getString());
            return;
        }

        // Cancel pending teleport if any
        if (pendingTeleports.remove(playerId) != null) {
            LOGGER.info("[InstanceManager] Cancelled pending teleport for disconnected player {}",
                player.getName().getString());
        }

        // Cancel pending recovery if any
        if (pendingRecoveries.remove(playerId) != null) {
            LOGGER.debug("[InstanceManager] Cancelled pending recovery for disconnected player {}",
                player.getName().getString());
        }

        // Check if player was in an instance
        Optional<InstanceData> instanceOpt = InstanceRegistry.INSTANCE.getPlayerInstance(playerId);
        if (instanceOpt.isPresent()) {
            LOGGER.info("[InstanceManager] Player {} disconnected while in instance, quest failed",
                player.getName().getString());

            // The snapshot is already saved, so on next login they will be recovered
            // Just clean up the instance membership
            forceEndPlayerInstances(playerId);
        }
    }

    /**
     * Handle player death in instance.
     */
    public void onPlayerDeath(ServerPlayer player) {
        if (!initialized) return;

        UUID playerId = nn(player.getUUID(), "player uuid");

        // Check if player is in an instance
        Optional<InstanceData> instanceOpt = InstanceRegistry.INSTANCE.getPlayerInstance(playerId);
        if (instanceOpt.isEmpty()) return;

        UUID instanceId = instanceOpt.get().getInstanceId();

        LOGGER.info("[InstanceManager] Player {} died in instance {}", player.getName().getString(), instanceId);

        // End the quest as failed
        endInstanceQuest(instanceId, false, "You died");
    }

    // === Queries ===

    /**
     * Check if a player is currently in an instance.
     */
    public boolean isPlayerInInstance(UUID playerId) {
        return InstanceRegistry.INSTANCE.getPlayerInstance(playerId).isPresent();
    }

    /**
     * Get the instance a player is in.
     */
    public Optional<InstanceData> getPlayerInstance(UUID playerId) {
        return InstanceRegistry.INSTANCE.getPlayerInstance(playerId);
    }

    /**
     * Check if the system is ready.
     */
    public boolean isReady() {
        return initialized && server != null;
    }

    // === Internal Types ===

    /**
     * Represents a pending teleport request.
     */
    private static class TeleportRequest {
        final UUID playerId;
        final UUID instanceId;
        final long createdAt;
        int ticksRemaining;
        int failureCount;

        // Maximum time a teleport request can exist before being considered stale (30 seconds)
        static final long MAX_AGE_MS = 30_000;

        TeleportRequest(UUID playerId, UUID instanceId, int ticksRemaining) {
            this.playerId = playerId;
            this.instanceId = instanceId;
            this.ticksRemaining = ticksRemaining;
            this.createdAt = System.currentTimeMillis();
            this.failureCount = 0;
        }

        /**
         * Check if this request is stale and should be cleaned up.
         */
        boolean isStale() {
            return System.currentTimeMillis() - createdAt > MAX_AGE_MS;
        }
    }

    /**
     * Represents a pending recovery that needs to be executed after a delay.
     * Used when a player needs respawning before recovery - we delay 1 tick
     * to let the respawn fully complete before teleporting.
     */
    private static class PendingRecovery {
        final UUID playerId;
        final PlayerInstanceSnapshot snapshot;
        final String reason;
        int ticksRemaining;

        PendingRecovery(UUID playerId, PlayerInstanceSnapshot snapshot, String reason, int ticksDelay) {
            this.playerId = playerId;
            this.snapshot = snapshot;
            this.reason = reason;
            this.ticksRemaining = ticksDelay;
        }
    }

    @Nonnull
    private static Component styledMsg(@Nonnull String text, @Nonnull ChatFormatting style) {
        return Objects.requireNonNull(
            Objects.requireNonNull(Component.literal(text), "message").withStyle(style),
            "styled message"
        );
    }

    @Nonnull
    private static <T> T nn(T value, String context) {
        return Objects.requireNonNull(value, context);
    }
}
