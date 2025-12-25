package com.devmod.debug;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.devmod.network.ChannelId.DEBUG_SYNC;
import static com.devmod.network.ChannelId.DEBUG_TOGGLE;

/**
 * Handles registration and processing of debug network packets.
 *
 * Note: We use Minecraft's NATIVE debug payloads (PathfindingDebugPayload, etc.)
 * which are sent by the DebugPacketsMixin. Those don't need registration here
 * because they're vanilla payloads.
 *
 * We only register:
 * - DebugTogglePayload: client -> server (player requests to toggle a feature)
 * - DebugSyncPayload: server -> client (tells client to enable/disable renderer)
 */
public class DebugNetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DebugNetworkHandler.class);

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Debug Toggle (client to server) - player requests to toggle a feature
        event.registrar(DEBUG_TOGGLE.asString()).playToServer(
            Objects.requireNonNull(DebugTogglePayload.TYPE),
            Objects.requireNonNull(DebugTogglePayload.STREAM_CODEC),
            DebugNetworkHandler::handleDebugToggle
        );

        // Debug Sync (server to client) - sync enabled state to client
        event.registrar(DEBUG_SYNC.asString()).playToClient(
            Objects.requireNonNull(DebugSyncPayload.TYPE),
            Objects.requireNonNull(DebugSyncPayload.STREAM_CODEC),
            DebugNetworkHandler::handleDebugSync
        );

        LOGGER.info("[DevMod] Debug network packets registered (channels {}, {})",
            DEBUG_TOGGLE.asString(), DEBUG_SYNC.asString());
    }

    // ========== Server-side Handlers ==========

    private static void handleDebugToggle(DebugTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DebugFeature feature = payload.getFeature();
                if (feature == null) {
                    LOGGER.warn("Unknown debug feature: {}", payload.featureId());
                    return;
                }

                boolean nowEnabled = DebugManager.INSTANCE.toggle(player, feature);

                // Send sync packet to client to update renderer state
                PacketDistributor.sendToPlayer(player, new DebugSyncPayload(feature.getId(), nowEnabled));

                // Send chat feedback
                String status = nowEnabled ? "§aENABLED" : "§cDISABLED";
                var feedback = Objects.requireNonNull(
                    net.minecraft.network.chat.Component.literal(
                        "§7[Debug] §f" + feature.getDisplayName() + " " + status
                    )
                );
                player.sendSystemMessage(feedback);

                LOGGER.info("[Debug] Player {} toggled {} to {}",
                    player.getName().getString(), feature.getDisplayName(), nowEnabled);
            }
        });
    }

    // ========== Client-side Handlers ==========

    private static void handleDebugSync(DebugSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() ->
            com.devmod.client.debug.DebugNetworkClientHandler.handleDebugSync(payload));
    }
}
