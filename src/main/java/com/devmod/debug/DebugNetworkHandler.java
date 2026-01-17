package com.devmod.debug;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.debug.network.EntityScanDataPayload;
import com.devmod.network.PayloadValidation;

import static com.devmod.network.ChannelId.DEBUG_SYNC;
import static com.devmod.network.ChannelId.DEBUG_TOGGLE;
import static com.devmod.network.ChannelId.ENTITY_PATHING;
import static com.devmod.network.ChannelId.ENTITY_SCANNER_OPEN;
import static com.devmod.network.ChannelId.ENTITY_SCAN_DATA;

public class DebugNetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DebugNetworkHandler.class);

    private static void observeFuture(CompletableFuture<?> future, String action) {
        future.exceptionally(throwable -> {
            LOGGER.warn("Async operation failed: {}", action, throwable);
            return null;
        });
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        try {
            // Debug Toggle (client to server) - player requests to toggle a feature
            event.registrar(DEBUG_TOGGLE.asString()).playToServer(
                Objects.requireNonNull(DebugTogglePayload.TYPE),
                Objects.requireNonNull(DebugTogglePayload.STREAM_CODEC),
                PayloadValidation.validated(DebugNetworkHandler::handleDebugToggle,
                    PayloadValidation.PayloadLimits.SMALL)
            );

            // Debug Sync (server to client) - sync enabled state to client
            event.registrar(DEBUG_SYNC.asString()).playToClient(
                Objects.requireNonNull(DebugSyncPayload.TYPE),
                Objects.requireNonNull(DebugSyncPayload.STREAM_CODEC),
                DebugNetworkHandler::handleDebugSync
            );

            // Entity Pathing (server to client) - send path data for debug visualization
            event.registrar(ENTITY_PATHING.asString()).playToClient(
                Objects.requireNonNull(EntityPathingPayload.TYPE),
                Objects.requireNonNull(EntityPathingPayload.STREAM_CODEC),
                DebugNetworkHandler::handleEntityPathing
            );

            // Entity Scan Data (server to client) - send scanned entity data
            event.registrar(ENTITY_SCAN_DATA.asString()).playToClient(
                Objects.requireNonNull(EntityScanDataPayload.TYPE),
                Objects.requireNonNull(EntityScanDataPayload.STREAM_CODEC),
                DebugNetworkHandler::handleEntityScanData
            );

            // Entity Scanner Open (server to client) - open scanner screen
            event.registrar(ENTITY_SCANNER_OPEN.asString()).playToClient(
                Objects.requireNonNull(EntityScanDataPayload.OpenScreenPayload.TYPE),
                Objects.requireNonNull(EntityScanDataPayload.OpenScreenPayload.STREAM_CODEC),
                DebugNetworkHandler::handleEntityScannerOpen
            );

            LOGGER.info("[DevMod] Debug network packets registered (channels {}, {}, {}, {}, {})",
                DEBUG_TOGGLE.asString(), DEBUG_SYNC.asString(), ENTITY_PATHING.asString(),
                ENTITY_SCAN_DATA.asString(), ENTITY_SCANNER_OPEN.asString());
        } catch (NoClassDefFoundError e) {
            LOGGER.error("[DevMod] Debug payload classes missing; debug networking disabled", e);
        }
    }

    // ========== Server-side Handlers ==========

    private static void handleDebugToggle(DebugTogglePayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
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
        }), "debug toggle");
    }

    // ========== Client-side Handlers ==========

    private static void handleDebugSync(DebugSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> invokeClientDebugSync(payload)), "debug sync");
    }

    private static void invokeClientDebugSync(DebugSyncPayload payload) {
        try {
            Class<?> handlerClass = Class.forName("com.devmod.client.debug.DebugNetworkClientHandler");
            java.lang.reflect.Method method = handlerClass.getMethod("handleDebugSync", DebugSyncPayload.class);
            method.invoke(null, payload);
        } catch (ClassNotFoundException e) {
            // Client handler not available on dedicated servers.
        } catch (Exception e) {
            LOGGER.debug("[Debug] Client debug sync unavailable: {}", e.getMessage());
        }
    }

    private static void handleEntityPathing(EntityPathingPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> invokeClientEntityPathing(payload)), "entity pathing");
    }

    private static void invokeClientEntityPathing(EntityPathingPayload payload) {
        try {
            Class<?> handlerClass = Class.forName("com.devmod.client.debug.DebugNetworkClientHandler");
            java.lang.reflect.Method method = handlerClass.getMethod("handleEntityPathing", EntityPathingPayload.class);
            method.invoke(null, payload);
        } catch (ClassNotFoundException e) {
            // Client handler not available on dedicated servers.
        } catch (Exception e) {
            LOGGER.debug("[Debug] Client entity pathing unavailable: {}", e.getMessage());
        }
    }

    private static void handleEntityScanData(EntityScanDataPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> invokeClientEntityScanData(payload)), "entity scan data");
    }

    private static void invokeClientEntityScanData(EntityScanDataPayload payload) {
        try {
            Class<?> handlerClass = Class.forName("com.devmod.debug.client.EntityScannerClientHandler");
            java.lang.reflect.Method method = handlerClass.getMethod("handleScanData", EntityScanDataPayload.class, IPayloadContext.class);
            method.invoke(null, payload, null);
        } catch (ClassNotFoundException e) {
            // Client handler not available on dedicated servers.
        } catch (Exception e) {
            LOGGER.debug("[Debug] Client entity scan data unavailable: {}", e.getMessage());
        }
    }

    private static void handleEntityScannerOpen(EntityScanDataPayload.OpenScreenPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> invokeClientEntityScannerOpen(payload)), "entity scanner open");
    }

    private static void invokeClientEntityScannerOpen(EntityScanDataPayload.OpenScreenPayload payload) {
        try {
            Class<?> handlerClass = Class.forName("com.devmod.debug.client.EntityScannerClientHandler");
            java.lang.reflect.Method method = handlerClass.getMethod("handleOpenScreen", EntityScanDataPayload.OpenScreenPayload.class, IPayloadContext.class);
            method.invoke(null, payload, null);
        } catch (ClassNotFoundException e) {
            // Client handler not available on dedicated servers.
        } catch (Exception e) {
            LOGGER.debug("[Debug] Client entity scanner open unavailable: {}", e.getMessage());
        }
    }
}
