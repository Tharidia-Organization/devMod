package com.frenkvs.devmod.instance;

import com.frenkvs.devmod.DevMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;


/**
 * Event handler for the instance dimension system.
 *
 * Hooks into:
 * - Server lifecycle (start/stop)
 * - Server tick (for processing teleports and destructions)
 * - Player login/logout (for recovery and cleanup)
 * - Player death (for quest failure)
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class InstanceEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceEventHandler.class);

    // Tick counter for periodic tasks
    private static int tickCounter = 0;
    private static final int DESTRUCTION_CHECK_INTERVAL = 100; // Every 5 seconds (100 ticks)

    // === Server Lifecycle ===

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("[InstanceEvents] Server started, initializing instance system");
        InstanceManager.INSTANCE.initialize(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[InstanceEvents] Server stopping, shutting down instance system");
        InstanceManager.INSTANCE.shutdown();
    }

    // === Server Tick ===

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Process pending teleports every tick
        InstanceManager.INSTANCE.tick();

        // Force tick instance dimensions - they may not be ticked by vanilla server loop
        tickInstanceDimensions(event.getServer());

        // Process pending destructions periodically
        tickCounter++;
        if (tickCounter >= DESTRUCTION_CHECK_INTERVAL) {
            tickCounter = 0;
            InstanceRegistry.INSTANCE.processPendingDestructions();
        }
    }

    /**
     * Force tick all active instance dimensions.
     * This ensures entities in dynamically created dimensions receive full ticks.
     *
     * NOTE: We call level.tick() because dynamically created dimensions may not
     * be included in the server's normal tick loop.
     */
    private static void tickInstanceDimensions(MinecraftServer server) {
        for (InstanceData instance : InstanceRegistry.INSTANCE.getInstancesByState(InstanceState.ACTIVE)) {
            ResourceKey<Level> dimKey = instance.getDimensionKey();
            if (dimKey != null) {
                ServerLevel level = server.getLevel(dimKey);
                if (level != null && !level.players().isEmpty()) {
                    // Only tick if there are players in the dimension
                    // This prevents double-ticking and issues during death/respawn
                    try {
                        level.tick(() -> true);
                    } catch (Exception e) {
                        LOGGER.warn("[InstanceEvents] Failed to tick instance dimension {}: {}",
                            dimKey.location(), e.getMessage());
                    }
                }
            }
        }
    }

    // === Player Events ===

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOGGER.debug("[InstanceEvents] Player logged in: {}", player.getName().getString());
            InstanceManager.INSTANCE.onPlayerLogin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOGGER.debug("[InstanceEvents] Player logged out: {}", player.getName().getString());
            InstanceManager.INSTANCE.onPlayerLogout(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Only handle if player is in an instance
            if (InstanceManager.INSTANCE.isPlayerInInstance(player.getUUID())) {
                // Check if player has an active Endurance Quest - if so, let EnduranceQuestManager handle death
                // EnduranceQuestManager has its own death handling with respawn options
                if (com.frenkvs.devmod.endurance.EnduranceQuestManager.INSTANCE.getActiveSession(player.getUUID()).isPresent()) {
                    LOGGER.debug("[InstanceEvents] Player {} died in instance but has active Endurance Quest - delegating to EnduranceQuestManager",
                        player.getName().getString());
                    // Don't call InstanceManager.onPlayerDeath() - EnduranceQuestManager will handle it
                    return;
                }

                LOGGER.debug("[InstanceEvents] Player died in instance: {}", player.getName().getString());
                InstanceManager.INSTANCE.onPlayerDeath(player);
            }
        }
    }

    // === Dimension Change Detection ===
    // Note: Additional handlers can be added here to detect when players
    // try to leave the instance dimension through other means (portals, commands, etc.)

    /**
     * Handle vanilla respawn for players with active Endurance Quests.
     * When a player clicks the vanilla "Respawn" button (instead of our custom F11/F12),
     * they get teleported to their spawn point. We intercept this and redirect them back
     * to the instance arena.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();

            // Check if player has an active Endurance Quest session
            var sessionOpt = com.frenkvs.devmod.endurance.EnduranceQuestManager.INSTANCE.getActiveSession(playerId);
            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();

                // Only handle if awaiting respawn choice (player died and we showed death screen)
                if (session.isAwaitingRespawnChoice()) {
                    LOGGER.info("[InstanceEvents] Player {} used vanilla respawn while in Endurance Quest - redirecting to instance arena",
                        player.getName().getString());

                    // Use EnduranceQuestManager's respawn handling with continueQuest=true
                    // This will teleport them back to the arena and restart the wave
                    com.frenkvs.devmod.endurance.EnduranceQuestManager.INSTANCE.handleRespawnChoice(player, true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Check if player was in an instance and left via unexpected means
            if (InstanceManager.INSTANCE.isPlayerInInstance(player.getUUID())) {
                InstanceManager.INSTANCE.getPlayerInstance(player.getUUID()).ifPresent(instance -> {
                    if (instance.getDimensionKey() != null) {
                        // Check if player is teleporting TO the instance dimension (legitimate teleport)
                        if (event.getTo().equals(instance.getDimensionKey())) {
                            LOGGER.debug("[InstanceEvents] Player {} teleporting TO instance dimension, ignoring",
                                player.getName().getString());
                            return;
                        }

                        // Check if player LEFT the instance dimension unexpectedly
                        if (event.getFrom().equals(instance.getDimensionKey())) {
                            // Check if this is a legitimate return teleport (player dying or quest ending)
                            // by checking the snapshot state - RETURNING means endInstanceQuest() is handling it
                            java.util.Optional<PlayerInstanceSnapshot> snapshotOpt =
                                RecoverySystem.INSTANCE.loadSnapshot(player.getUUID());
                            if (snapshotOpt.isPresent()) {
                                PlayerInstanceState state = snapshotOpt.get().getState();
                                if (state == PlayerInstanceState.RETURNING) {
                                    LOGGER.debug("[InstanceEvents] Player {} leaving instance dimension via legitimate return (state: RETURNING), ignoring",
                                        player.getName().getString());
                                    return;
                                }
                            }

                            LOGGER.warn("[InstanceEvents] Player {} left instance dimension unexpectedly (from {} to {})",
                                player.getName().getString(), event.getFrom().location(), event.getTo().location());
                            // This shouldn't happen in normal flow, but handle it gracefully
                            InstanceManager.INSTANCE.forceEndPlayerInstances(player.getUUID());
                        }
                    }
                });
            }
        }
    }
}
