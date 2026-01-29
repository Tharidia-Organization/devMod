package com.devmod.transport;

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.devmod.DevMod;
import com.devmod.transport.executor.ArrivalManager;
import com.devmod.transport.executor.TransportExecutor;

/**
 * Event handler for transport system lifecycle events.
 * Handles player disconnect cleanup to prevent memory leaks.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class TransportEventHandler {
    private TransportEventHandler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TransportExecutor.INSTANCE.tick(event.getServer());
    }

    /**
     * Cleanup transport-related player data on disconnect.
     * Prevents memory leaks from player-specific tracking maps.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();

        // Clean up executor state (charge manager, cooldowns, sync maps)
        TransportExecutor.INSTANCE.cleanupPlayer(playerId);

        // Clean up arrival manager sessions
        ArrivalManager.INSTANCE.onPlayerDisconnect(playerId);

        // Clean up return points (player-specific transport nodes)
        TransportRegistry registry = TransportRegistry.get(player.serverLevel());
        if (registry != null) {
            registry.clearReturnPoint(playerId);
        }
    }
}
