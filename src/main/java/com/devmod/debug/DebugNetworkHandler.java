package com.devmod.debug;

import com.devmod.debug.client.DebugRenderBools;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

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
        event.registrar("debug").playToServer(
            Objects.requireNonNull(DebugTogglePayload.TYPE),
            Objects.requireNonNull(DebugTogglePayload.STREAM_CODEC),
            DebugNetworkHandler::handleDebugToggle
        );

        // Debug Sync (server to client) - sync enabled state to client
        event.registrar("debug").playToClient(
            Objects.requireNonNull(DebugSyncPayload.TYPE),
            Objects.requireNonNull(DebugSyncPayload.STREAM_CODEC),
            DebugNetworkHandler::handleDebugSync
        );

        LOGGER.info("[DevMod] Debug network packets registered");
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
        context.enqueueWork(() -> {
            DebugFeature feature = payload.getFeature();
            if (feature == null) {
                LOGGER.warn("Unknown debug feature in sync: {}", payload.featureId());
                return;
            }

            // Update the client-side render booleans
            switch (feature) {
                case ENTITY_PATHING -> DebugRenderBools.ENTITY_PATHING = payload.enabled();
                case ENTITY_GOALS -> DebugRenderBools.ENTITY_GOALS = payload.enabled();
                case ENTITY_BRAINS -> DebugRenderBools.ENTITY_BRAINS = payload.enabled();
                case POI -> DebugRenderBools.POI = payload.enabled();
                case BLOCK_UPDATES -> DebugRenderBools.BLOCK_UPDATES = payload.enabled();
                case STRUCTURE_GENERATIONS -> DebugRenderBools.STRUCTURES = payload.enabled();
                case RAIDS -> DebugRenderBools.RAIDS = payload.enabled();
                case GAME_EVENTS -> DebugRenderBools.GAME_EVENTS = payload.enabled();
                case BEE_HIVES -> DebugRenderBools.BEE_HIVES = payload.enabled();
                case BEES -> DebugRenderBools.BEES = payload.enabled();
                case WATER -> DebugRenderBools.WATER = payload.enabled();
                case HEIGHTMAP -> DebugRenderBools.HEIGHTMAP = payload.enabled();
                case COLLISION -> DebugRenderBools.COLLISION = payload.enabled();
                case LIGHT -> DebugRenderBools.LIGHT = payload.enabled();
                case SOLID_FACES -> DebugRenderBools.SOLID_FACES = payload.enabled();
                case CHUNK -> DebugRenderBools.CHUNK = payload.enabled();
                case SPAWN_CHUNKS -> {} // No specific renderer for this
            }

            LOGGER.debug("[Debug] Client synced {} = {}", feature.getDisplayName(), payload.enabled());
        });
    }
}
