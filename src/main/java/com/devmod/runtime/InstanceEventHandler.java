package com.devmod.runtime;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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

import com.devmod.DevMod;

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

    private static boolean isInstanceDimension(ResourceKey<Level> dimensionKey) {
        if (dimensionKey == null) {
            return false;
        }
        ResourceLocation id = dimensionKey.location();
        if (id == null) {
            return false;
        }
        return "devmod".equals(id.getNamespace()) && id.getPath().startsWith("instance_");
    }

    private static void sanitizeRespawnDimension(ServerPlayer player, String context) {
        ResourceKey<Level> respawnDim = player.getRespawnDimension();
        if (!isInstanceDimension(respawnDim)) {
            return;
        }

        ResourceKey<Level> overworldKey = Objects.requireNonNull(Level.OVERWORLD, "overworldKey");
        ServerLevel overworld = player.server.getLevel(overworldKey);
        if (overworld == null) {
            LOGGER.warn("[InstanceEvents] Cannot sanitize respawn dimension for {} (overworld missing, context={})",
                player.getName().getString(), context);
            return;
        }

        BlockPos spawnPos = overworld.getSharedSpawnPos();
        float angle = overworld.getSharedSpawnAngle();
        player.setRespawnPosition(overworldKey, spawnPos, angle, false, false);
        LOGGER.info("[InstanceEvents] Cleared invalid respawn dimension {} for player {} (context={})",
            respawnDim.location(), player.getName().getString(), context);
    }

    // === Player Events ===

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOGGER.debug("[InstanceEvents] Player logged in: {}", player.getName().getString());
            InstanceManager.INSTANCE.onPlayerLogin(player);
            sanitizeRespawnDimension(player, "login");
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
                if (com.devmod.endurance.EnduranceQuestManager.INSTANCE.getActiveSession(player.getUUID()).isPresent()) {
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
            var sessionOpt = com.devmod.endurance.EnduranceQuestManager.INSTANCE.getActiveSession(playerId);
            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();
                LOGGER.info("[InstanceEvents] Player {} respawn event (state={}, awaitingRespawn={}, respawnRequested={}, instanceId={}, dimension={})",
                    player.getName().getString(),
                    session.getQuest().getState(),
                    session.isAwaitingRespawnChoice(),
                    session.isRespawnRequested(),
                    session.getInstanceId(),
                    player.level().dimension().location());

                // Only handle if awaiting respawn choice (player died and we showed death screen)
                if (session.isAwaitingRespawnChoice() || session.isRespawnRequested()) {
                    LOGGER.info("[InstanceEvents] Player {} respawned while in Endurance Quest - redirecting to instance arena",
                        player.getName().getString());

                    // Redirect them back to the arena and restart the wave
                    com.devmod.endurance.EnduranceQuestManager.INSTANCE.handleVanillaRespawn(player);
                }
            }
            sanitizeRespawnDimension(player, "respawn");
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean fromInstance = isInstanceDimension(event.getFrom());
            boolean toInstance = isInstanceDimension(event.getTo());
            if (fromInstance || toInstance) {
                sanitizeRespawnDimension(player, "dimension_change");
            }
            // Check if player was in an instance and left via unexpected means
            if (InstanceManager.INSTANCE.isPlayerInInstance(player.getUUID())) {
                InstanceManager.INSTANCE.getPlayerInstance(player.getUUID()).ifPresent(instance -> {
                    if (instance.getDimensionKey() != null) {
                        // Check if player is teleporting TO the instance dimension (legitimate teleport)
                        if (event.getTo().equals(instance.getDimensionKey())) {
                            LOGGER.debug("[InstanceEvents] Player {} teleporting TO instance dimension, syncing environment",
                                player.getName().getString());
                            // Re-sync environment (time override, biome) when entering/re-entering arena
                            // This handles respawn, rejoining, and any other dimension change to the arena
                            com.devmod.runtime.environment.DimensionEnvironmentManager.INSTANCE
                                .syncEnvironmentToPlayer(player, instance.getDimensionKey());
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

                            // Allow Endurance quest respawn flow to leave/re-enter without force-ending instance.
                            var enduranceSession = com.devmod.endurance.EnduranceQuestManager.INSTANCE
                                .getActiveSession(player.getUUID());
                            if (enduranceSession.isPresent()) {
                                var session = enduranceSession.get();
                                if (session.getPartyId() != null
                                    && com.devmod.endurance.EnduranceQuestManager.INSTANCE
                                        .isPartyRunActiveForPlayer(player.getUUID())) {
                                    LOGGER.debug("[InstanceEvents] Player {} left instance during party run, marking spectator",
                                        player.getName().getString());
                                    com.devmod.endurance.EnduranceQuestManager.INSTANCE
                                        .markPartyMemberInactive(player.getUUID(), "left_instance");
                                    return;
                                }
                                if (session.isAwaitingRespawnChoice() || session.isRespawnRequested()
                                    || session.getQuest().getState() == com.devmod.endurance.EnduranceQuestState.FAILED) {
                                    LOGGER.debug("[InstanceEvents] Player {} left instance during Endurance respawn window, preserving instance",
                                        player.getName().getString());
                                    return;
                                }

                                LOGGER.warn("[InstanceEvents] Player {} left instance during active Endurance quest, abandoning quest",
                                    player.getName().getString());
                                com.devmod.endurance.EnduranceQuestManager.INSTANCE.abandonQuest(player);
                                return;
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
