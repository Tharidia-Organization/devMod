package com.devmod.transport.network;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;
import com.devmod.network.ChannelId;
import com.devmod.network.PayloadValidation;
import com.devmod.network.handlers.NetworkHandlerBase;
import com.devmod.transport.TransportData;
import com.devmod.transport.TransportMode;
import com.devmod.transport.block.entity.TransportCoreBlockEntity;

/**
 * Network handler for transport system payloads.
 * Registers handlers for channels 210-218.
 */
public final class TransportNetworkHandler extends NetworkHandlerBase {
    public static final TransportNetworkHandler INSTANCE = new TransportNetworkHandler();

    /** Maximum distance squared a player can be from a transport node to interact (8 blocks). */
    private static final double MAX_INTERACT_DISTANCE_SQ = 64.0;

    private TransportNetworkHandler() {}

    /**
     * Registers all transport payloads.
     * Called from NetworkHandler.register().
     */
    public void registerPayloads(@Nonnull RegisterPayloadHandlersEvent event) {
        // 210: TRANSPORT_CONFIG_OPEN (S->C) - Open config GUI
        event.registrar(ChannelId.TRANSPORT_CONFIG_OPEN.asString()).playToClient(
            nn(TransportConfigOpenPayload.TYPE),
            nn(TransportConfigOpenPayload.STREAM_CODEC),
            PayloadValidation.validated(TransportNetworkHandler::handleConfigOpenClient, PayloadValidation.PayloadLimits.SMALL)
        );

        // 211: TRANSPORT_CONFIG_SAVE (C->S) - Save config from GUI
        event.registrar(ChannelId.TRANSPORT_CONFIG_SAVE.asString()).playToServer(
            nn(TransportConfigSavePayload.TYPE),
            nn(TransportConfigSavePayload.STREAM_CODEC),
            PayloadValidation.validated(TransportNetworkHandler::handleConfigSaveServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // 212: TRANSPORT_STATE (S->C) - Sync overlay state
        event.registrar(ChannelId.TRANSPORT_STATE.asString()).playToClient(
            nn(TransportStatePayload.TYPE),
            nn(TransportStatePayload.STREAM_CODEC),
            PayloadValidation.validated(TransportNetworkHandler::handleStateUpdateClient, PayloadValidation.PayloadLimits.SMALL)
        );

        // 213: TRANSPORT_CHARGE_UPDATE (S->C) - Charge progress update
        event.registrar(ChannelId.TRANSPORT_CHARGE_UPDATE.asString()).playToClient(
            nn(TransportChargeUpdatePayload.TYPE),
            nn(TransportChargeUpdatePayload.STREAM_CODEC),
            PayloadValidation.validated(TransportNetworkHandler::handleChargeUpdateClient, PayloadValidation.PayloadLimits.SMALL)
        );

        // 214: TRANSPORT_WAYPOINT_SELECT (C->S) - Waypoint selection from GUI
        event.registrar(ChannelId.TRANSPORT_WAYPOINT_SELECT.asString()).playToServer(
            nn(TransportWaypointSelectPayload.TYPE),
            nn(TransportWaypointSelectPayload.STREAM_CODEC),
            PayloadValidation.validated(TransportNetworkHandler::handleWaypointSelectServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // 215: TRANSPORT_NETWORK_LIST (S->C) - Network list for selection GUI
        event.registrar(ChannelId.TRANSPORT_NETWORK_LIST.asString()).playToClient(
            nn(TransportNetworkListPayload.TYPE),
            nn(TransportNetworkListPayload.STREAM_CODEC),
            PayloadValidation.validated(TransportNetworkHandler::handleNetworkListClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // 216: TRANSPORT_COUNTDOWN (S->C) - Countdown sync
        event.registrar(ChannelId.TRANSPORT_COUNTDOWN.asString()).playToClient(
            nn(TransportCountdownPayload.TYPE),
            nn(TransportCountdownPayload.STREAM_CODEC),
            PayloadValidation.validated(TransportNetworkHandler::handleCountdownClient, PayloadValidation.PayloadLimits.SMALL)
        );

        // 217: TRANSPORT_PARTY_STATUS (S->C) - Party teleport status
        event.registrar(ChannelId.TRANSPORT_PARTY_STATUS.asString()).playToClient(
            nn(TransportPartyStatusPayload.TYPE),
            nn(TransportPartyStatusPayload.STREAM_CODEC),
            PayloadValidation.validated(TransportNetworkHandler::handlePartyStatusClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // 218: TRANSPORT_ARRIVAL_CONFIRM (C->S) - Arrival confirmation
        event.registrar(ChannelId.TRANSPORT_ARRIVAL_CONFIRM.asString()).playToServer(
            nn(TransportArrivalConfirmPayload.TYPE),
            nn(TransportArrivalConfirmPayload.STREAM_CODEC),
            PayloadValidation.validated(TransportNetworkHandler::handleArrivalConfirmServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // 219: TRANSPORT_CANCEL_PARTY (C->S) - Cancel party teleport
        event.registrar(ChannelId.TRANSPORT_CANCEL_PARTY.asString()).playToServer(
            nn(TransportCancelPartyPayload.TYPE),
            nn(TransportCancelPartyPayload.STREAM_CODEC),
            PayloadValidation.validated(TransportNetworkHandler::handleCancelPartyServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // 220: TRANSPORT_DELETE_WAYPOINT (C->S) - Delete waypoint
        event.registrar(ChannelId.TRANSPORT_DELETE_WAYPOINT.asString()).playToServer(
            nn(TransportDeleteWaypointPayload.TYPE),
            nn(TransportDeleteWaypointPayload.STREAM_CODEC),
            PayloadValidation.validated(TransportNetworkHandler::handleDeleteWaypointServer, PayloadValidation.PayloadLimits.SMALL)
        );

        DevMod.LOGGER.debug("Registered Transport network handlers (210-220)");
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private static void enqueueWork(IPayloadContext ctx, Runnable work) {
        ctx.enqueueWork(java.util.Objects.requireNonNull(work));
    }

    // ========================================================================
    // Client Handlers (called on client thread)
    // ========================================================================

    /**
     * Handles TRANSPORT_CONFIG_OPEN on client.
     * Opens the transport configuration screen.
     */
    private static void handleConfigOpenClient(TransportConfigOpenPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            // Use reflection to avoid client-side class loading on server
            try {
                Class<?> hooksClass = Class.forName("com.devmod.client.transport.TransportClientPayloadHooks");
                var method = hooksClass.getMethod("handleConfigOpen", TransportConfigOpenPayload.class);
                method.invoke(null, payload);
            } catch (Exception e) {
                DevMod.LOGGER.error("Failed to open Transport config screen", e);
            }
        });
    }

    /**
     * Handles TRANSPORT_STATE on client.
     * Updates the transport overlay state.
     */
    private static void handleStateUpdateClient(TransportStatePayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            try {
                Class<?> hooksClass = Class.forName("com.devmod.client.transport.TransportClientPayloadHooks");
                var method = hooksClass.getMethod("handleStateUpdate", TransportStatePayload.class);
                method.invoke(null, payload);
            } catch (Exception e) {
                DevMod.LOGGER.error("Failed to update Transport state", e);
            }
        });
    }

    /**
     * Handles TRANSPORT_CHARGE_UPDATE on client.
     * Updates the charging progress in the overlay.
     */
    private static void handleChargeUpdateClient(TransportChargeUpdatePayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            try {
                Class<?> hooksClass = Class.forName("com.devmod.client.transport.TransportClientPayloadHooks");
                var method = hooksClass.getMethod("handleChargeUpdate", TransportChargeUpdatePayload.class);
                method.invoke(null, payload);
            } catch (Exception e) {
                DevMod.LOGGER.error("Failed to update Transport charge", e);
            }
        });
    }

    /**
     * Handles TRANSPORT_NETWORK_LIST on client.
     * Opens or updates the network selection GUI.
     */
    private static void handleNetworkListClient(TransportNetworkListPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            try {
                Class<?> hooksClass = Class.forName("com.devmod.client.transport.TransportClientPayloadHooks");
                var method = hooksClass.getMethod("handleNetworkList", TransportNetworkListPayload.class);
                method.invoke(null, payload);
            } catch (Exception e) {
                DevMod.LOGGER.error("Failed to handle Transport network list", e);
            }
        });
    }

    /**
     * Handles TRANSPORT_COUNTDOWN on client.
     * Updates countdown overlay display.
     */
    private static void handleCountdownClient(TransportCountdownPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            try {
                Class<?> hooksClass = Class.forName("com.devmod.client.transport.TransportClientPayloadHooks");
                var method = hooksClass.getMethod("handleCountdown", TransportCountdownPayload.class);
                method.invoke(null, payload);
            } catch (Exception e) {
                DevMod.LOGGER.error("Failed to handle Transport countdown", e);
            }
        });
    }

    /**
     * Handles TRANSPORT_PARTY_STATUS on client.
     * Updates party teleport status overlay.
     */
    private static void handlePartyStatusClient(TransportPartyStatusPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            try {
                Class<?> hooksClass = Class.forName("com.devmod.client.transport.TransportClientPayloadHooks");
                var method = hooksClass.getMethod("handlePartyStatus", TransportPartyStatusPayload.class);
                method.invoke(null, payload);
            } catch (Exception e) {
                DevMod.LOGGER.error("Failed to handle Transport party status", e);
            }
        });
    }

    // ========================================================================
    // Server Handlers (called on server thread)
    // ========================================================================

    /**
     * Handles TRANSPORT_CONFIG_SAVE from client.
     * Updates the transport node configuration.
     */
    private static void handleConfigSaveServer(TransportConfigSavePayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        ServerLevel level = player.serverLevel();

        // Get the block entity at the specified position
        BlockPos nodePos = payload.nodePos();
        BlockEntity be = level.getBlockEntity(nodePos);

        if (!(be instanceof TransportCoreBlockEntity coreBe)) {
            LOGGER.warn("Player {} tried to save config for invalid block at {}",
                player.getName().getString(), nodePos);
            return;
        }

        // Verify player is close enough to the block (anti-cheat)
        double distanceSq = player.distanceToSqr(
            nodePos.getX() + 0.5,
            nodePos.getY() + 0.5,
            nodePos.getZ() + 0.5
        );
        if (distanceSq > MAX_INTERACT_DISTANCE_SQ) {
            LOGGER.warn("Player {} tried to save config too far from block at {}",
                player.getName().getString(), nodePos);
            return;
        }

        // Permission check: only creator or op can reconfigure (matches waypoint deletion)
        java.util.UUID creatorId = coreBe.getCreatorId();
        boolean isCreator = creatorId != null && creatorId.equals(player.getUUID());
        if (!isCreator && !player.hasPermissions(2)) {
            LOGGER.warn("Player {} denied config save for node at {} (not creator or op)",
                player.getName().getString(), nodePos);
            return;
        }

        // Apply configuration
        TransportMode mode = payload.getMode();
        coreBe.applyConfiguration(
            mode,
            payload.getColor(),
            payload.getSanitizedNetworkName(),
            TransportData.NetworkSelectionMode.byIndex(payload.selectionModeIndex()),
            payload.getSanitizedDisplayName()
        );

        LOGGER.debug("Player {} saved Transport config at {}", player.getName().getString(), nodePos);
    }

    /**
     * Handles TRANSPORT_WAYPOINT_SELECT from client.
     * Initiates teleport to selected waypoint.
     */
    private static void handleWaypointSelectServer(TransportWaypointSelectPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        ServerLevel level = player.serverLevel();

        java.util.UUID sourceId = payload.sourceNodeId();
        java.util.UUID destId = payload.destinationNodeId();

        // Get transport registry
        com.devmod.transport.TransportRegistry registry = com.devmod.transport.TransportRegistry.get(level);

        // Validate source node exists and player is near it
        java.util.Optional<TransportData> sourceOpt = registry.get(sourceId);
        if (sourceOpt.isEmpty()) {
            LOGGER.warn("Player {} selected waypoint with invalid source node {}",
                player.getName().getString(), sourceId);
            return;
        }

        TransportData source = sourceOpt.get();
        java.util.Optional<BlockPos> sourcePosOpt = source.getPosition();
        if (sourcePosOpt.isEmpty()) {
            LOGGER.warn("Source node {} has no position", sourceId);
            return;
        }

        // Verify player is close to source
        BlockPos sourcePos = sourcePosOpt.get();
        double distanceSq = player.distanceToSqr(
            sourcePos.getX() + 0.5,
            sourcePos.getY() + 0.5,
            sourcePos.getZ() + 0.5
        );
        if (distanceSq > MAX_INTERACT_DISTANCE_SQ) {
            LOGGER.warn("Player {} tried to select waypoint too far from source at {}",
                player.getName().getString(), sourcePos);
            return;
        }

        // Validate destination node
        java.util.Optional<TransportData> destOpt = registry.get(destId);
        if (destOpt.isEmpty()) {
            LOGGER.warn("Player {} selected invalid destination node {}",
                player.getName().getString(), destId);
            return;
        }

        // The destination must belong to the source node's own network. Without
        // this, any node id in the global registry is a valid target and the
        // packet becomes a teleport-anywhere exploit.
        String sourceNetwork = source.getNetworkName().orElse(null);
        if (sourceNetwork == null
            || !sourceNetwork.equals(destOpt.get().getNetworkName().orElse(null))) {
            LOGGER.warn("Player {} selected destination {} outside the network of source {}",
                player.getName().getString(), destId, sourceId);
            return;
        }

        // Teleport to the node the player actually chose. Passing the destination
        // to executeTeleport would re-dispatch on the destination's own mode.
        com.devmod.transport.executor.TransportExecutor.INSTANCE
            .teleportToNode(player, source, destOpt.get());

        LOGGER.debug("Player {} teleported from {} to {}", player.getName().getString(), sourceId, destId);
    }

    /**
     * Handles TRANSPORT_ARRIVAL_CONFIRM from client.
     * Confirms player arrival at destination.
     */
    private static void handleArrivalConfirmServer(TransportArrivalConfirmPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        net.minecraft.server.MinecraftServer server = player.getServer();

        if (server == null) {
            return;
        }

        java.util.UUID sessionId = payload.sessionId();

        // Try to confirm arrival via ArrivalManager
        boolean confirmed = com.devmod.transport.executor.ArrivalManager.INSTANCE.confirmArrival(
            sessionId,
            player.getUUID(),
            server
        );

        if (confirmed) {
            LOGGER.debug("Player {} confirmed arrival for session {}", player.getName().getString(), sessionId);
        } else {
            LOGGER.debug("Player {} arrival confirmation failed for session {}", player.getName().getString(), sessionId);
        }
    }

    /**
     * Handles TRANSPORT_CANCEL_PARTY from client.
     * Cancels an in-progress party teleport.
     */
    private static void handleCancelPartyServer(TransportCancelPartyPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();

        java.util.UUID sessionId = payload.partyId();
        if (sessionId == null) {
            return;
        }

        // Cancel via ArrivalManager (partyId is actually the session ID)
        boolean cancelled = com.devmod.transport.executor.ArrivalManager.INSTANCE.cancelSession(
            sessionId,
            player.getUUID(),
            "Cancelled by " + player.getName().getString()
        );

        if (cancelled) {
            LOGGER.info("Player {} cancelled party teleport session {}", player.getName().getString(), sessionId);
        } else {
            LOGGER.debug("Player {} cancel request ignored for session {} (not active)",
                player.getName().getString(), sessionId);
        }
    }

    /**
     * Handles TRANSPORT_DELETE_WAYPOINT from client.
     * Deletes a waypoint from the transport network.
     */
    private static void handleDeleteWaypointServer(TransportDeleteWaypointPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        ServerLevel level = java.util.Objects.requireNonNull(player.serverLevel(), "level");

        java.util.UUID waypointId = java.util.Objects.requireNonNull(payload.waypointId(), "waypointId");

        // Get transport registry
        var registry = com.devmod.transport.TransportRegistry.get(level);

        // Verify waypoint exists
        var waypointOpt = registry.get(waypointId);
        if (waypointOpt.isEmpty()) {
            LOGGER.warn("Player {} tried to delete non-existent waypoint {}", player.getName().getString(), waypointId);
            return;
        }

        var waypoint = waypointOpt.get();

        // Permission check: only creator or op can delete
        boolean isCreator = waypoint.getCreatorId().map(id -> id.equals(player.getUUID())).orElse(false);
        boolean isOp = player.hasPermissions(2);

        if (!isCreator && !isOp) {
            LOGGER.warn("Player {} denied deletion of waypoint {} (not creator or op)",
                player.getName().getString(), waypointId);
            return;
        }

        // Delete waypoint
        registry.unregister(waypointId);
        LOGGER.info("Player {} deleted waypoint {}", player.getName().getString(), waypointId);

        // Send feedback
        player.displayClientMessage(
            java.util.Objects.requireNonNull(
                net.minecraft.network.chat.Component.translatable("message.devmod.transport.waypoint_deleted"),
                "deletedMessage"),
            true
        );
    }

    // ========================================================================
    // Public API for sending payloads
    // ========================================================================

    public static void sendStateUpdate(@Nonnull ServerPlayer player, @Nonnull TransportStatePayload payload) {
        sendPacket(player, payload);
    }

    public static void sendChargeUpdate(@Nonnull ServerPlayer player, @Nonnull TransportChargeUpdatePayload payload) {
        sendPacket(player, payload);
    }

    public static void sendCountdown(@Nonnull ServerPlayer player, @Nonnull TransportCountdownPayload payload) {
        sendPacket(player, payload);
    }

    public static void sendPartyStatus(@Nonnull ServerPlayer player, @Nonnull TransportPartyStatusPayload payload) {
        sendPacket(player, payload);
    }

    /**
     * Sends a network list to the client for manual destination selection.
     */
    public static void sendNetworkList(
            @Nonnull ServerPlayer player,
            @Nonnull com.devmod.transport.TransportData source,
            @Nonnull com.devmod.transport.TransportRegistry registry) {
        String networkName = source.networkName();
        if (networkName == null || networkName.isEmpty()) {
            return;
        }

        java.util.UUID sourceId = source.id();
        var sourcePosOpt = source.getPosition();
        var sourceDimOpt = source.getDimension();
        java.util.List<com.devmod.transport.TransportData> nodes = registry.getByNetwork(networkName);
        java.util.List<TransportNetworkListPayload.NetworkNodeInfo> payloadNodes = new java.util.ArrayList<>();

        nodes.stream()
            .filter(node -> node != null && !node.id().equals(sourceId))
            .sorted((a, b) -> {
                int distCompare = Long.compare(
                    distanceSq(sourcePosOpt, sourceDimOpt, a),
                    distanceSq(sourcePosOpt, sourceDimOpt, b)
                );
                if (distCompare != 0) {
                    return distCompare;
                }
                return resolveNodeName(a).compareToIgnoreCase(resolveNodeName(b));
            })
            .forEach(node -> {
                var posOpt = node.getPosition();
                var dimOpt = node.getDimension();
                if (posOpt.isEmpty() || dimOpt.isEmpty()) {
                    return;
                }
                var pos = posOpt.get();
                String displayName = resolveNodeName(node);
                String dimension = dimOpt.get().toString();
                int distanceBlocks = computeDistanceBlocks(sourcePosOpt, sourceDimOpt, node);
                payloadNodes.add(new TransportNetworkListPayload.NetworkNodeInfo(
                    node.id(),
                    displayName,
                    dimension,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    node.color().getIndex(),
                    true,
                    distanceBlocks
                ));
            });

        sendPacket(player, new TransportNetworkListPayload(sourceId, networkName, payloadNodes));
    }

    private static String resolveNodeName(@Nonnull com.devmod.transport.TransportData node) {
        return node.resolveDisplayName();
    }

    private static long distanceSq(
        java.util.Optional<net.minecraft.core.BlockPos> sourcePosOpt,
        java.util.Optional<net.minecraft.resources.ResourceLocation> sourceDimOpt,
        com.devmod.transport.TransportData node) {
        if (sourcePosOpt.isEmpty() || sourceDimOpt.isEmpty()) {
            return Long.MAX_VALUE;
        }
        var posOpt = node.getPosition();
        var dimOpt = node.getDimension();
        if (posOpt.isEmpty() || dimOpt.isEmpty()) {
            return Long.MAX_VALUE;
        }
        if (!sourceDimOpt.get().equals(dimOpt.get())) {
            return Long.MAX_VALUE;
        }
        return (long) sourcePosOpt.get().distSqr(posOpt.get());
    }

    private static int computeDistanceBlocks(
        java.util.Optional<net.minecraft.core.BlockPos> sourcePosOpt,
        java.util.Optional<net.minecraft.resources.ResourceLocation> sourceDimOpt,
        com.devmod.transport.TransportData node) {
        var posOpt = node.getPosition();
        var dimOpt = node.getDimension();
        if (posOpt.isEmpty() || dimOpt.isEmpty()) {
            return -1;
        }
        if (sourceDimOpt.isEmpty() || sourcePosOpt.isEmpty()) {
            return -1;
        }
        if (!sourceDimOpt.get().equals(dimOpt.get())) {
            return -2;
        }
        long distSq = distanceSq(sourcePosOpt, sourceDimOpt, node);
        return (int) Math.round(Math.sqrt((double) distSq));
    }
}
