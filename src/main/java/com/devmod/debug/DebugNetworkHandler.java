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

import static com.devmod.network.ChannelId.DEBUG_BEES;
import static com.devmod.network.ChannelId.DEBUG_BRAINS;
import static com.devmod.network.ChannelId.DEBUG_POI;
import static com.devmod.network.ChannelId.DEBUG_RAIDS;
import static com.devmod.network.ChannelId.DEBUG_STRUCTURES;
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
                PayloadValidation.validated(DebugNetworkHandler::handleDebugSync,
                    PayloadValidation.PayloadLimits.SMALL)
            );

            // Entity Pathing (server to client) - send path data for debug visualization
            event.registrar(ENTITY_PATHING.asString()).playToClient(
                Objects.requireNonNull(EntityPathingPayload.TYPE),
                Objects.requireNonNull(EntityPathingPayload.STREAM_CODEC),
                PayloadValidation.validated(DebugNetworkHandler::handleEntityPathing,
                    PayloadValidation.PayloadLimits.LARGE)
            );

            // Structures (server to client) - structure bounding boxes near the player
            event.registrar(DEBUG_STRUCTURES.asString()).playToClient(
                Objects.requireNonNull(StructuresPayload.TYPE),
                Objects.requireNonNull(StructuresPayload.STREAM_CODEC),
                PayloadValidation.validated(DebugNetworkHandler::handleStructures,
                    PayloadValidation.PayloadLimits.MEDIUM)
            );

            // POI (server to client) - points of interest near the player
            event.registrar(DEBUG_POI.asString()).playToClient(
                Objects.requireNonNull(POIPayload.TYPE),
                Objects.requireNonNull(POIPayload.STREAM_CODEC),
                PayloadValidation.validated(DebugNetworkHandler::handlePOI,
                    PayloadValidation.PayloadLimits.LARGE)
            );

            // Raids (server to client) - active raids near the player
            event.registrar(DEBUG_RAIDS.asString()).playToClient(
                Objects.requireNonNull(RaidsPayload.TYPE),
                Objects.requireNonNull(RaidsPayload.STREAM_CODEC),
                PayloadValidation.validated(DebugNetworkHandler::handleRaids,
                    PayloadValidation.PayloadLimits.MEDIUM)
            );

            // Brains (server to client) - mob → target links near the player
            event.registrar(DEBUG_BRAINS.asString()).playToClient(
                Objects.requireNonNull(BrainsPayload.TYPE),
                Objects.requireNonNull(BrainsPayload.STREAM_CODEC),
                PayloadValidation.validated(DebugNetworkHandler::handleBrains,
                    PayloadValidation.PayloadLimits.MEDIUM)
            );

            // Bees (server to client) - remembered hive/flower of bees near the player
            event.registrar(DEBUG_BEES.asString()).playToClient(
                Objects.requireNonNull(BeesPayload.TYPE),
                Objects.requireNonNull(BeesPayload.STREAM_CODEC),
                PayloadValidation.validated(DebugNetworkHandler::handleBees,
                    PayloadValidation.PayloadLimits.MEDIUM)
            );

            // Entity Scan Data (server to client) - send scanned entity data
            event.registrar(ENTITY_SCAN_DATA.asString()).playToClient(
                Objects.requireNonNull(EntityScanDataPayload.TYPE),
                Objects.requireNonNull(EntityScanDataPayload.STREAM_CODEC),
                PayloadValidation.validated(DebugNetworkHandler::handleEntityScanData,
                    PayloadValidation.PayloadLimits.SYNC_MEDIUM)
            );

            // Entity Scanner Open (server to client) - open scanner screen
            event.registrar(ENTITY_SCANNER_OPEN.asString()).playToClient(
                Objects.requireNonNull(EntityScanDataPayload.OpenScreenPayload.TYPE),
                Objects.requireNonNull(EntityScanDataPayload.OpenScreenPayload.STREAM_CODEC),
                PayloadValidation.validated(DebugNetworkHandler::handleEntityScannerOpen,
                    PayloadValidation.PayloadLimits.SMALL)
            );

            LOGGER.info("[DevMod] Debug network packets registered (channels {}, {}, {}, {}, {}, {}, {}, {}, {}, {})",
                DEBUG_TOGGLE.asString(), DEBUG_SYNC.asString(), ENTITY_PATHING.asString(),
                DEBUG_STRUCTURES.asString(), DEBUG_POI.asString(), DEBUG_RAIDS.asString(),
                DEBUG_BRAINS.asString(), DEBUG_BEES.asString(),
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
                String status = nowEnabled ? "\u00A7aENABLED" : "\u00A7cDISABLED";
                var feedback = Objects.requireNonNull(
                    net.minecraft.network.chat.Component.literal(
                        "\u00A77[Debug] \u00A7f" + feature.getDisplayName() + " " + status
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
        observeFuture(context.enqueueWork(() -> DebugClientBridge.get().handleDebugSync(payload)), "debug sync");
    }

    private static void handleEntityPathing(EntityPathingPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> DebugClientBridge.get().handleEntityPathing(payload)), "entity pathing");
    }

    private static void handleStructures(StructuresPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> DebugClientBridge.get().handleStructures(payload)), "debug structures");
    }

    private static void handlePOI(POIPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> DebugClientBridge.get().handlePOI(payload)), "debug poi");
    }

    private static void handleRaids(RaidsPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> DebugClientBridge.get().handleRaids(payload)), "debug raids");
    }

    private static void handleBrains(BrainsPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> DebugClientBridge.get().handleBrains(payload)), "debug brains");
    }

    private static void handleBees(BeesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> DebugClientBridge.get().handleBees(payload)), "debug bees");
    }

    private static void handleEntityScanData(EntityScanDataPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> DebugClientBridge.get().handleScanData(payload)), "entity scan data");
    }

    private static void handleEntityScannerOpen(EntityScanDataPayload.OpenScreenPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        observeFuture(context.enqueueWork(() -> DebugClientBridge.get().handleOpenScreen(payload)), "entity scanner open");
    }
}
