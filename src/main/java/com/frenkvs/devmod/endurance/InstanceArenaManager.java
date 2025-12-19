package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.instance.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Adapter that integrates the Instance Dimension System with EnduranceQuestManager.
 *
 * When enabled, quests run in isolated temporary dimensions instead of the overworld.
 * This provides:
 * - Complete isolation between quest instances
 * - No residual data after quest completion
 * - Parallel instances for multiple players
 * - Automatic cleanup on disconnect/crash
 */
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

    // Timeout for instance creation to prevent infinite loading (30 seconds)
    private static final long CREATION_TIMEOUT_SECONDS = 30;

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
     * Start a quest in an instance dimension.
     * This is an async operation - returns a future that completes when the instance is ready.
     *
     * @param player The player starting the quest
     * @param mobId The mob type for the quest
     * @param settings Quest settings
     * @return Future with the result (success/failure and session info)
     */
    public CompletableFuture<InstanceQuestResult> startInstanceQuest(
            ServerPlayer player,
            ResourceLocation mobId,
            EnduranceQuestManager.QuestSettings settings
    ) {
        UUID playerId = player.getUUID();

        // Check if already creating an instance for this player
        if (pendingCreations.containsKey(playerId)) {
            return CompletableFuture.completedFuture(
                new InstanceQuestResult(false, "Instance creation already in progress", null, null)
            );
        }

        // Check if player already in an instance
        if (InstanceManager.INSTANCE.isPlayerInInstance(playerId)) {
            return CompletableFuture.completedFuture(
                new InstanceQuestResult(false, "Already in an instance", null, null)
            );
        }

        LOGGER.info("[InstanceArena] Starting instance quest for {} (mob: {})",
            player.getName().getString(), mobId);

        // Create the arena ID for the instance
        String arenaId = "endurance_" + mobId.toString().replace(":", "_");

        // Start instance creation through InstanceManager (immediate teleport mode)
        // Using startInstanceQuestImmediate to skip the 10-second countdown since
        // the quest system needs the player to be in the arena before starting waves
        CompletableFuture<InstanceQuestResult> future = InstanceManager.INSTANCE
            .startInstanceQuestImmediate(player, arenaId, mobId.toString(), null)
            .thenApply(instanceId -> {
                // Always cleanup pending creation
                pendingCreations.remove(playerId);

                if (instanceId == null) {
                    return new InstanceQuestResult(false, "Failed to create instance", null, null);
                }

                // Get the instance data
                Optional<InstanceData> instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
                if (instanceOpt.isEmpty()) {
                    return new InstanceQuestResult(false, "Instance not found after creation", null, null);
                }

                InstanceData instance = instanceOpt.get();

                // Create arena adapter for the instance dimension
                ResourceKey<Level> dimensionKey = instance.getDimensionKey();
                if (dimensionKey == null) {
                    return new InstanceQuestResult(false, "Instance dimension not ready", null, null);
                }

                // Get the server level for the instance
                MinecraftServer server = player.getServer();
                if (server == null) {
                    return new InstanceQuestResult(false, "Server not available", null, null);
                }

                ServerLevel instanceLevel = server.getLevel(dimensionKey);
                if (instanceLevel == null) {
                    return new InstanceQuestResult(false, "Instance level not found", null, null);
                }

                // Create an arena in the instance dimension
                // The arena center is at (0, 65, 0) - one block above the generated platform
                BlockPos arenaCenter = new BlockPos(0, 65, 0);
                ArenaManager.Arena arena = createInstanceArena(instanceLevel, arenaCenter, settings.arenaSize);

                // Store the bidirectional mapping
                arenaToInstance.put(arena.getId(), instanceId);
                instanceToArena.put(instanceId, arena.getId());

                LOGGER.info("[InstanceArena] Instance quest ready for {} (instance: {}, arena: {})",
                    player.getName().getString(), instanceId, arena.getId());

                return new InstanceQuestResult(true, "Instance ready", instanceId, arena);
            })
            // Add timeout to prevent infinite loading if something hangs
            .orTimeout(CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .exceptionally(throwable -> {
                // Ensure cleanup on any exception
                pendingCreations.remove(playerId);

                // Check if it was a timeout
                String errorMsg;
                if (throwable instanceof TimeoutException ||
                    (throwable.getCause() != null && throwable.getCause() instanceof TimeoutException)) {
                    LOGGER.error("[InstanceArena] Instance creation timed out for {} after {}s",
                        player.getName().getString(), CREATION_TIMEOUT_SECONDS);
                    errorMsg = "Instance creation timed out. Please try again.";
                } else {
                    LOGGER.error("[InstanceArena] Exception during instance creation for {}: {}",
                        player.getName().getString(), throwable.getMessage());
                    errorMsg = "Exception: " + throwable.getMessage();
                }
                return new InstanceQuestResult(false, errorMsg, null, null);
            });

        pendingCreations.put(playerId, future);
        return future;
    }

    /**
     * Start an instance quest for a party. Teleports all members and builds an arena in the instance.
     */
    public InstanceQuestResult startInstanceQuestForParty(
            ServerPlayer leader,
            ResourceLocation mobId,
            EnduranceQuestManager.QuestSettings settings
    ) {
        UUID leaderId = leader.getUUID();

        // Build party member list (including leader)
        List<UUID> members = new ArrayList<>();
        members.add(leaderId);
        if (settings.partyMemberIds != null) {
            members.addAll(settings.partyMemberIds);
        }

        // Start instance creation with immediate teleport for party
        try {
            UUID instanceId = InstanceManager.INSTANCE
                .startInstanceQuestImmediate(leader, buildArenaId(mobId), mobId.toString(), members)
                .get(CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (instanceId == null) {
                return new InstanceQuestResult(false, "Failed to create instance for party", null, null);
            }

            Optional<InstanceData> instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
            if (instanceOpt.isEmpty()) {
                return new InstanceQuestResult(false, "Instance not found after creation", null, null);
            }

            InstanceData instance = instanceOpt.get();
            ResourceKey<Level> dimensionKey = instance.getDimensionKey();
            if (dimensionKey == null) {
                return new InstanceQuestResult(false, "Instance dimension not ready", null, null);
            }

            MinecraftServer server = leader.getServer();
            if (server == null) {
                return new InstanceQuestResult(false, "Server not available", null, null);
            }

            ServerLevel instanceLevel = server.getLevel(dimensionKey);
            if (instanceLevel == null) {
                return new InstanceQuestResult(false, "Instance level not found", null, null);
            }

            ArenaManager.Arena arena = createInstanceArena(instanceLevel, new BlockPos(0, 65, 0), settings.arenaSize);
            arenaToInstance.put(arena.getId(), instanceId);
            instanceToArena.put(instanceId, arena.getId());

            LOGGER.info("[InstanceArena] Instance quest ready for party {} members (instance: {}, arena: {})",
                members.size(), instanceId, arena.getId());

            return new InstanceQuestResult(true, "Instance ready", instanceId, arena);
        } catch (Exception e) {
            LOGGER.error("[InstanceArena] Party instance creation failed: {}", e.getMessage());
            return new InstanceQuestResult(false, e.getMessage(), null, null);
        }
    }

    private String buildArenaId(ResourceLocation mobId) {
        return "endurance_" + mobId.toString().replace(":", "_");
    }

    /**
     * Create an arena within an instance dimension.
     * Similar to ArenaManager but adapted for the instance void world.
     */
    private ArenaManager.Arena createInstanceArena(ServerLevel level, BlockPos center, int size) {
        // Create arena with the given parameters
        // The instance dimension already has a platform generated, so we just need to set up barriers
        ArenaManager.Arena arena = new ArenaManager.Arena(level, center, size);

        // Build barriers around the arena (platform is already there from DynamicDimensionManager)
        buildInstanceArenaBarriers(level, center, size, arena);

        LOGGER.debug("[InstanceArena] Created arena in instance at {} (size: {})", center, size);
        return arena;
    }

    /**
     * Build barriers for the instance arena.
     */
    private void buildInstanceArenaBarriers(ServerLevel level, BlockPos center, int size, ArenaManager.Arena arena) {
        int halfSize = size / 2;
        net.minecraft.world.level.block.state.BlockState barrier =
            java.util.Objects.requireNonNull(net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState());

        // Build walls
        for (int y = 0; y < ArenaManager.ARENA_HEIGHT; y++) {
            for (int i = -halfSize; i <= halfSize; i++) {
                // North wall
                BlockPos north = java.util.Objects.requireNonNull(center.offset(i, y, -halfSize));
                level.setBlock(north, barrier, 3);
                arena.addModifiedBlock(north);

                // South wall
                BlockPos south = java.util.Objects.requireNonNull(center.offset(i, y, halfSize));
                level.setBlock(south, barrier, 3);
                arena.addModifiedBlock(south);

                // East wall
                BlockPos east = java.util.Objects.requireNonNull(center.offset(halfSize, y, i));
                level.setBlock(east, barrier, 3);
                arena.addModifiedBlock(east);

                // West wall
                BlockPos west = java.util.Objects.requireNonNull(center.offset(-halfSize, y, i));
                level.setBlock(west, barrier, 3);
                arena.addModifiedBlock(west);
            }
        }

        // Build ceiling
        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                BlockPos pos = java.util.Objects.requireNonNull(center.offset(x, ArenaManager.ARENA_HEIGHT, z));
                level.setBlock(pos, barrier, 3);
                arena.addModifiedBlock(pos);
            }
        }
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
        @Nullable ArenaManager.Arena arena
    ) {}
}
