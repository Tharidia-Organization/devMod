package com.frenkvs.devmod.instance;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main orchestrator for the instance dimension system.
 *
 * Coordinates between:
 * - InstanceRegistry (data persistence)
 * - DynamicDimensionManager (dimension creation/destruction)
 * - RecoverySystem (player state snapshots)
 * - EnduranceQuestManager (quest logic)
 *
 * Provides the high-level API for starting and ending instance quests.
 */
public class InstanceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceManager.class);
    public static final InstanceManager INSTANCE = new InstanceManager();

    // Teleport countdown in ticks (10 seconds = 200 ticks)
    private static final int TELEPORT_COUNTDOWN_TICKS = 200;

    // Pending teleports (players in countdown)
    private final Map<UUID, TeleportRequest> pendingTeleports = new ConcurrentHashMap<>();

    private MinecraftServer server;
    private boolean initialized = false;

    private InstanceManager() {}

    // === Lifecycle ===

    /**
     * Initialize the instance system.
     * Called during ServerStartedEvent.
     */
    public void initialize(MinecraftServer server) {
        this.server = server;

        // Initialize subsystems
        RecoverySystem.INSTANCE.initialize();
        InstanceRegistry.INSTANCE.load();
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

        // Save registry state
        InstanceRegistry.INSTANCE.save();

        // Shutdown dimension manager (destroys remaining instances)
        DynamicDimensionManager.INSTANCE.shutdown();

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
        return startInstanceQuestInternal(player, arenaId, questId, partyMembers, true);
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
        return startInstanceQuestInternal(player, arenaId, questId, partyMembers, false);
    }

    /**
     * Internal implementation for starting instance quests.
     *
     * @param player The player (or party leader) starting the quest
     * @param arenaId The arena template to use
     * @param questId The quest identifier
     * @param partyMembers Optional list of party member UUIDs (null for solo)
     * @param immediate If true, teleport immediately; if false, use 10-second countdown
     * @return CompletableFuture with the instance ID, or null on failure
     */
    private CompletableFuture<UUID> startInstanceQuestInternal(
            ServerPlayer player,
            String arenaId,
            String questId,
            @Nullable List<UUID> partyMembers,
            boolean immediate
    ) {
        if (!initialized) {
            LOGGER.error("[InstanceManager] Not initialized");
            return CompletableFuture.completedFuture(null);
        }

        UUID playerId = player.getUUID();

        // Check if player is already in an instance
        if (InstanceRegistry.INSTANCE.getPlayerInstance(playerId).isPresent()) {
            player.sendSystemMessage(Component.literal("[DevMod] You are already in an instance!")
                .withStyle(ChatFormatting.RED));
            return CompletableFuture.completedFuture(null);
        }

        // Check if player has a pending teleport
        if (pendingTeleports.containsKey(playerId)) {
            player.sendSystemMessage(Component.literal("[DevMod] Teleport already in progress!")
                .withStyle(ChatFormatting.RED));
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.info("[InstanceManager] Starting instance quest for {} (arena: {}, quest: {})",
            player.getName().getString(), arenaId, questId);

        // Collect all players (leader + party members)
        List<ServerPlayer> allPlayers = new ArrayList<>();
        allPlayers.add(player);

        if (partyMembers != null && !partyMembers.isEmpty()) {
            for (UUID memberId : partyMembers) {
                ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                if (member != null && !memberId.equals(playerId)) {
                    allPlayers.add(member);
                }
            }
        }

        // Create instance data
        InstanceData instance = InstanceRegistry.INSTANCE.createInstance(arenaId, playerId);
        UUID instanceId = instance.getInstanceId();

        // Add all players to instance
        for (ServerPlayer p : allPlayers) {
            instance.addPlayer(p.getUUID());
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
            InstanceRegistry.INSTANCE.mapPlayer(p.getUUID(), instanceId);
        }

        // Notify players (different message based on mode)
        for (ServerPlayer p : allPlayers) {
            if (immediate) {
                p.sendSystemMessage(Component.literal("[DevMod] Preparing instance...")
                    .withStyle(ChatFormatting.GOLD));
            } else {
                p.sendSystemMessage(Component.literal("[DevMod] Preparing instance... teleporting in 10 seconds")
                    .withStyle(ChatFormatting.GOLD));
            }
        }

        // Capture immediate flag for lambda
        final boolean teleportImmediately = immediate;

        // Phase 2: Create dimension asynchronously
        // Note: createDimensionAsync schedules on server thread, so thenApply also runs on server thread
        return DynamicDimensionManager.INSTANCE.createDimensionAsync(instanceId, arenaId)
            .thenApply(dimensionKey -> {
                if (dimensionKey == null) {
                    // Creation failed - recover all players that are still online
                    LOGGER.error("[InstanceManager] Dimension creation failed for instance {}", instanceId);
                    for (ServerPlayer p : allPlayers) {
                        // Verify player is still online before recovery
                        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(p.getUUID());
                        if (onlinePlayer != null) {
                            RecoverySystem.INSTANCE.loadSnapshot(p.getUUID()).ifPresent(snapshot -> {
                                RecoverySystem.INSTANCE.performRecovery(onlinePlayer, snapshot, "Instance creation failed");
                            });
                        } else {
                            // Player disconnected - snapshot will handle recovery on next login
                            LOGGER.info("[InstanceManager] Player {} disconnected during instance creation, snapshot preserved for login recovery",
                                p.getUUID());
                        }
                    }
                    InstanceRegistry.INSTANCE.removeInstance(instanceId);
                    return null;
                }

                // Update instance with dimension
                instance.setDimensionKey(dimensionKey);
                instance.setState(InstanceState.READY);
                InstanceRegistry.INSTANCE.save();

                // Phase 3: Teleport or start countdown based on mode
                // Re-fetch players from server to ensure they're still online
                for (ServerPlayer originalPlayer : allPlayers) {
                    ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(originalPlayer.getUUID());
                    if (onlinePlayer == null) {
                        // Player disconnected during dimension creation
                        LOGGER.warn("[InstanceManager] Player {} disconnected before teleport, skipping",
                            originalPlayer.getUUID());
                        // Remove from instance, snapshot preserved for login recovery
                        instance.removePlayer(originalPlayer.getUUID());
                        InstanceRegistry.INSTANCE.unmapPlayer(originalPlayer.getUUID());
                        continue;
                    }

                    if (teleportImmediately) {
                        executeImmediateTeleport(onlinePlayer, instanceId, dimensionKey);
                    } else {
                        startTeleportCountdown(onlinePlayer, instanceId, dimensionKey);
                    }
                }

                // Check if all players disconnected
                if (instance.isEmpty()) {
                    LOGGER.warn("[InstanceManager] All players disconnected before teleport, destroying instance {}",
                        instanceId);
                    InstanceRegistry.INSTANCE.scheduleDestruction(instanceId);
                }

                return instanceId;
            });
    }

    /**
     * Execute immediate teleport without countdown.
     * Used when integrating with quest systems that need synchronous completion.
     */
    private void executeImmediateTeleport(ServerPlayer player, UUID instanceId, ResourceKey<Level> dimensionKey) {
        UUID playerId = player.getUUID();

        LOGGER.info("[InstanceManager] Executing immediate teleport for {} to instance {}",
            player.getName().getString(), instanceId);

        // Update snapshot state
        RecoverySystem.INSTANCE.updateSnapshotState(playerId, PlayerInstanceState.IN_TRANSIT);

        // Perform teleport immediately
        boolean success = DynamicDimensionManager.INSTANCE.teleportToInstance(player, instanceId);

        if (success) {
            // Update states
            RecoverySystem.INSTANCE.updateSnapshotState(playerId, PlayerInstanceState.IN_INSTANCE);

            InstanceRegistry.INSTANCE.getInstance(instanceId).ifPresent(instance -> {
                if (instance.getState() == InstanceState.READY) {
                    instance.setState(InstanceState.ACTIVE);
                    InstanceRegistry.INSTANCE.save();
                }
            });

            player.sendSystemMessage(Component.literal("[DevMod] Welcome to the arena! Good luck!")
                .withStyle(ChatFormatting.GREEN));

            LOGGER.info("[InstanceManager] Player {} successfully teleported to instance {} (immediate)",
                player.getName().getString(), instanceId);
        } else {
            // Teleport failed - recover player
            LOGGER.error("[InstanceManager] Immediate teleport failed for {} to instance {}",
                player.getName().getString(), instanceId);

            RecoverySystem.INSTANCE.loadSnapshot(playerId).ifPresent(snapshot -> {
                RecoverySystem.INSTANCE.performRecovery(player, snapshot, "Teleport failed");
            });
        }
    }

    /**
     * Start the teleport countdown for a player.
     */
    private void startTeleportCountdown(ServerPlayer player, UUID instanceId, ResourceKey<Level> dimensionKey) {
        UUID playerId = player.getUUID();

        TeleportRequest request = new TeleportRequest(
            playerId,
            instanceId,
            dimensionKey,
            TELEPORT_COUNTDOWN_TICKS
        );

        pendingTeleports.put(playerId, request);

        // Update snapshot state
        RecoverySystem.INSTANCE.updateSnapshotState(playerId, PlayerInstanceState.IN_TRANSIT);

        LOGGER.debug("[InstanceManager] Started teleport countdown for {}", player.getName().getString());
    }

    /**
     * Called every server tick to process pending teleports.
     */
    public void tick() {
        if (!initialized || pendingTeleports.isEmpty()) return;

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
                ServerPlayer player = server.getPlayerList().getPlayer(request.playerId);
                if (player != null) {
                    RecoverySystem.INSTANCE.loadSnapshot(request.playerId).ifPresent(snapshot -> {
                        RecoverySystem.INSTANCE.performRecovery(player, snapshot, "Teleport timed out");
                    });
                }
                continue;
            }

            request.ticksRemaining--;

            // Check if player is still online
            ServerPlayer player = server.getPlayerList().getPlayer(request.playerId);
            if (player == null) {
                LOGGER.warn("[InstanceManager] Player {} disconnected during countdown", request.playerId);
                iterator.remove();
                // Snapshot is preserved for recovery on next login
                continue;
            }

            // Send countdown messages at intervals
            if (request.ticksRemaining == 100) { // 5 seconds
                player.sendSystemMessage(Component.literal("[DevMod] Teleporting in 5 seconds...")
                    .withStyle(ChatFormatting.YELLOW));
            } else if (request.ticksRemaining == 60) { // 3 seconds
                player.sendSystemMessage(Component.literal("[DevMod] Teleporting in 3 seconds...")
                    .withStyle(ChatFormatting.YELLOW));
            } else if (request.ticksRemaining == 20) { // 1 second
                player.sendSystemMessage(Component.literal("[DevMod] Teleporting in 1 second...")
                    .withStyle(ChatFormatting.YELLOW));
            }

            // Execute teleport when countdown reaches zero
            if (request.ticksRemaining <= 0) {
                iterator.remove();
                executeTeleport(player, request);
            }
        }
    }

    /**
     * Execute the actual teleport to the instance dimension.
     */
    private void executeTeleport(ServerPlayer player, TeleportRequest request) {
        LOGGER.info("[InstanceManager] Executing teleport for {} to instance {}",
            player.getName().getString(), request.instanceId);

        boolean success = DynamicDimensionManager.INSTANCE.teleportToInstance(player, request.instanceId);

        if (success) {
            // Update snapshot and instance state
            RecoverySystem.INSTANCE.updateSnapshotState(player.getUUID(), PlayerInstanceState.IN_INSTANCE);

            InstanceRegistry.INSTANCE.getInstance(request.instanceId).ifPresent(instance -> {
                if (instance.getState() == InstanceState.READY) {
                    instance.setState(InstanceState.ACTIVE);
                    InstanceRegistry.INSTANCE.save();
                }
            });

            player.sendSystemMessage(Component.literal("[DevMod] Welcome to the arena! Good luck!")
                .withStyle(ChatFormatting.GREEN));

            LOGGER.info("[InstanceManager] Player {} successfully teleported to instance {}",
                player.getName().getString(), request.instanceId);
        } else {
            // Teleport failed - recover player
            LOGGER.error("[InstanceManager] Teleport failed for {} to instance {}",
                player.getName().getString(), request.instanceId);

            RecoverySystem.INSTANCE.loadSnapshot(player.getUUID()).ifPresent(snapshot -> {
                RecoverySystem.INSTANCE.performRecovery(player, snapshot, "Teleport failed");
            });
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

        // Update state
        instance.setState(InstanceState.COMPLETING);

        // Get all players in instance
        Set<UUID> playerIds = instance.getPlayerIds();

        for (UUID playerId : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            if (player != null) {
                // Update snapshot state
                RecoverySystem.INSTANCE.updateSnapshotState(playerId, PlayerInstanceState.RETURNING);

                // Notify player
                if (success) {
                    player.sendSystemMessage(Component.literal("[DevMod] Quest completed! Returning to overworld...")
                        .withStyle(ChatFormatting.GREEN));
                } else {
                    player.sendSystemMessage(Component.literal("[DevMod] " + reason + ". Returning to overworld...")
                        .withStyle(ChatFormatting.RED));
                }

                // Return player to original position
                // NOTE: performRecovery() also calls unmapPlayer() internally, so we don't need to do it again
                RecoverySystem.INSTANCE.loadSnapshot(playerId).ifPresent(snapshot -> {
                    RecoverySystem.INSTANCE.performRecovery(player, snapshot,
                        success ? "Quest completed" : reason);
                });
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

        UUID playerId = player.getUUID();

        // Cancel pending teleport if any
        if (pendingTeleports.remove(playerId) != null) {
            LOGGER.info("[InstanceManager] Cancelled pending teleport for disconnected player {}",
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

        UUID playerId = player.getUUID();

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
        final ResourceKey<Level> dimensionKey;
        final long createdAt;
        int ticksRemaining;

        // Maximum time a teleport request can exist before being considered stale (30 seconds)
        static final long MAX_AGE_MS = 30_000;

        TeleportRequest(UUID playerId, UUID instanceId, ResourceKey<Level> dimensionKey, int ticksRemaining) {
            this.playerId = playerId;
            this.instanceId = instanceId;
            this.dimensionKey = dimensionKey;
            this.ticksRemaining = ticksRemaining;
            this.createdAt = System.currentTimeMillis();
        }

        /**
         * Check if this request is stale and should be cleaned up.
         */
        boolean isStale() {
            return System.currentTimeMillis() - createdAt > MAX_AGE_MS;
        }
    }
}
