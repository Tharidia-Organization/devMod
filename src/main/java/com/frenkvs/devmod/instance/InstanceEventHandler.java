package com.frenkvs.devmod.instance;

import com.frenkvs.devmod.DevMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        // Process pending destructions periodically
        tickCounter++;
        if (tickCounter >= DESTRUCTION_CHECK_INTERVAL) {
            tickCounter = 0;
            InstanceRegistry.INSTANCE.processPendingDestructions();
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
                LOGGER.debug("[InstanceEvents] Player died in instance: {}", player.getName().getString());
                InstanceManager.INSTANCE.onPlayerDeath(player);
            }
        }
    }

    // === Dimension Change Detection ===
    // Note: Additional handlers can be added here to detect when players
    // try to leave the instance dimension through other means (portals, commands, etc.)

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Check if player was in an instance and left via unexpected means
            if (InstanceManager.INSTANCE.isPlayerInInstance(player.getUUID())) {
                // If they're no longer in the instance dimension, force end
                InstanceManager.INSTANCE.getPlayerInstance(player.getUUID()).ifPresent(instance -> {
                    if (instance.getDimensionKey() != null &&
                        !player.level().dimension().equals(instance.getDimensionKey())) {
                        LOGGER.warn("[InstanceEvents] Player {} left instance dimension unexpectedly",
                            player.getName().getString());
                        // This shouldn't happen in normal flow, but handle it gracefully
                        InstanceManager.INSTANCE.forceEndPlayerInstances(player.getUUID());
                    }
                });
            }
        }
    }
}
