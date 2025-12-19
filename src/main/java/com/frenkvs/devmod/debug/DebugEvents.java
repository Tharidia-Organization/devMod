package com.frenkvs.devmod.debug;

import com.frenkvs.devmod.DevMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Event handlers for the debug system.
 *
 * Debug rendering is handled by DebugRendererMixin (client) which renders when
 * DebugRenderBools are enabled. Debug data is sent by:
 * 1. DebugPacketsMixin (server) - intercepts vanilla DebugPackets calls
 * 2. NativeDebugSender (server tick) - actively sends debug packets for enabled features
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class DebugEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DebugCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Send debug packets to players who have features enabled
        if (event.getServer() != null) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                NativeDebugSender.INSTANCE.tick(level);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Clean up debug state when player disconnects
        DebugManager.INSTANCE.clearPlayer(event.getEntity().getUUID());
    }
}
