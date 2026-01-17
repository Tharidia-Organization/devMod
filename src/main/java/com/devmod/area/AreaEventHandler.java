package com.devmod.area;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.devmod.DevMod;
import com.devmod.area.builder.AreaBuildTaskManager;
import com.devmod.area.network.AreaNetworkHandler;

/**
 * Event handler for Area module lifecycle events.
 * Handles player and server events for build state management.
 * Automatically registered by NeoForge via @EventBusSubscriber.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class AreaEventHandler {

    private AreaEventHandler() {}

    /** CRIT-03 fix: Counter for periodic cooldown cleanup */
    private static int cooldownCleanupCounter = 0;
    /** CRIT-03 fix: Cleanup cooldowns every 1200 ticks (60 seconds) */
    private static final int COOLDOWN_CLEANUP_INTERVAL = 1200;

    // ========================================================================
    // Server Tick Events
    // ========================================================================

    /**
     * CRIT-03 fix: Periodic cleanup of expired cooldown entries to prevent memory leaks.
     * Runs every 60 seconds (1200 ticks) to clean up stale entries from cooldown maps.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++cooldownCleanupCounter >= COOLDOWN_CLEANUP_INTERVAL) {
            cooldownCleanupCounter = 0;
            AreaNetworkHandler.cleanupExpiredCooldowns();
        }
    }

    // ========================================================================
    // Player Lifecycle Events
    // ========================================================================

    /**
     * Clean up player state on disconnect to prevent memory leaks and orphan tasks.
     * - Clears build cooldowns from AreaNetworkHandler
     * - Cancels any active build tasks for the player
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            AreaNetworkHandler.clearPlayerBuildCooldown(playerId);
            AreaBuildTaskManager.INSTANCE.cancelPlayerBuilds(playerId);
            DevMod.LOGGER.debug("[Area] Cleaned up build state for player: {}", playerId);
        }
    }

    // ========================================================================
    // Server Lifecycle Events
    // ========================================================================

    /**
     * Resume builds that were interrupted by previous server shutdown.
     * Called after the server has fully started and levels are loaded.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // L-05 fix: Graceful null handling instead of throwing NPE
        MinecraftServer server = event.getServer();
        if (server == null) {
            DevMod.LOGGER.warn("[Area] ServerStartedEvent received with null server");
            return;
        }
        int resumed = AreaBuildTaskManager.INSTANCE.resumeShutdownBuilds(server);
        if (resumed > 0) {
            DevMod.LOGGER.info("[Area] Resumed {} builds from previous shutdown", resumed);
        }
    }

    /**
     * Save active build states before server shuts down.
     * This preserves build progress for resumption on next startup.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // L-05 fix: Graceful null handling instead of throwing NPE
        MinecraftServer server = event.getServer();
        if (server == null) {
            DevMod.LOGGER.warn("[Area] ServerStoppingEvent received with null server, skipping save");
            AreaBuildTaskManager.INSTANCE.cleanup();
            return;
        }

        // First, save all active build states
        int saved = AreaBuildTaskManager.INSTANCE.saveAllActiveBuilds(server);
        if (saved > 0) {
            DevMod.LOGGER.info("[Area] Saved {} active builds for shutdown", saved);
        }

        // Then cleanup the manager
        AreaBuildTaskManager.INSTANCE.cleanup();
    }
}
