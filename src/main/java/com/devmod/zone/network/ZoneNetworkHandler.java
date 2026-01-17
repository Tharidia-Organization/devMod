package com.devmod.zone.network;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import com.devmod.zone.ZoneBlocks;
import com.devmod.zone.block.entity.ZoneMarkerBlockEntity;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZoneRegistry;

/**
 * Network handler for Zone Marker system payloads.
 * Handles registration and processing of all zone-related network messages.
 */
public final class ZoneNetworkHandler extends NetworkHandlerBase {
    public static final ZoneNetworkHandler INSTANCE = new ZoneNetworkHandler();

    // ========================================================================
    // Cached MethodHandles for client-side reflection
    // ========================================================================

    private static final String CLIENT_HOOKS_CLASS = "com.devmod.client.zone.ZoneClientHooks";

    @Nullable private static volatile MethodHandle openZoneEditorMethod;
    @Nullable private static volatile MethodHandle handleZoneSyncMethod;
    @Nullable private static volatile MethodHandle handleZoneEnterMethod;

    @Nullable
    private static MethodHandle getOpenZoneEditorMethod() {
        if (openZoneEditorMethod == null) {
            synchronized (ZoneNetworkHandler.class) {
                if (openZoneEditorMethod == null) {
                    openZoneEditorMethod = lookupMethod("openZoneEditor", OpenZoneEditorPayload.class);
                }
            }
        }
        return openZoneEditorMethod;
    }

    @Nullable
    private static MethodHandle getHandleZoneSyncMethod() {
        if (handleZoneSyncMethod == null) {
            synchronized (ZoneNetworkHandler.class) {
                if (handleZoneSyncMethod == null) {
                    handleZoneSyncMethod = lookupMethod("handleZoneSync", ZoneSyncPayload.class);
                }
            }
        }
        return handleZoneSyncMethod;
    }

    @Nullable
    private static MethodHandle getHandleZoneEnterMethod() {
        if (handleZoneEnterMethod == null) {
            synchronized (ZoneNetworkHandler.class) {
                if (handleZoneEnterMethod == null) {
                    handleZoneEnterMethod = lookupMethod("handleZoneEnter", ZoneEnterPayload.class);
                }
            }
        }
        return handleZoneEnterMethod;
    }

    @Nullable
    private static MethodHandle lookupMethod(String methodName, Class<?> paramType) {
        try {
            Class<?> hooksClass = Class.forName(CLIENT_HOOKS_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.findStatic(hooksClass, methodName, MethodType.methodType(void.class, paramType));
        } catch (ClassNotFoundException e) {
            // Expected on dedicated server - client classes not available
            DevMod.LOGGER.debug("[Zone] Client hooks class not available (dedicated server)");
            return null;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            DevMod.LOGGER.error("[Zone] Failed to lookup client method: {}", methodName, e);
            return null;
        }
    }

    private ZoneNetworkHandler() {}

    /**
     * Registers all Zone Marker payload handlers.
     * Called from NetworkHandler during mod initialization.
     */
    public void registerPayloads(@Nonnull RegisterPayloadHandlersEvent event) {
        // Server -> Client: Open zone editor screen
        event.registrar(ChannelId.ZONE_EDITOR_OPEN.asString()).playToClient(
            nn(OpenZoneEditorPayload.TYPE),
            nn(OpenZoneEditorPayload.STREAM_CODEC),
            PayloadValidation.validated(ZoneNetworkHandler::handleOpenEditorClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Save zone configuration
        event.registrar(ChannelId.ZONE_SAVE.asString()).playToServer(
            nn(SaveZonePayload.TYPE),
            nn(SaveZonePayload.STREAM_CODEC),
            PayloadValidation.validated(ZoneNetworkHandler::handleSaveZoneServer, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Delete zone
        event.registrar(ChannelId.ZONE_DELETE.asString()).playToServer(
            nn(DeleteZonePayload.TYPE),
            nn(DeleteZonePayload.STREAM_CODEC),
            PayloadValidation.validated(ZoneNetworkHandler::handleDeleteZoneServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Zone sync
        event.registrar(ChannelId.ZONE_SYNC.asString()).playToClient(
            nn(ZoneSyncPayload.TYPE),
            nn(ZoneSyncPayload.STREAM_CODEC),
            PayloadValidation.validated(ZoneNetworkHandler::handleZoneSyncClient, PayloadValidation.PayloadLimits.LARGE)
        );

        // Server -> Client: Zone enter notification
        event.registrar(ChannelId.ZONE_ENTER.asString()).playToClient(
            nn(ZoneEnterPayload.TYPE),
            nn(ZoneEnterPayload.STREAM_CODEC),
            PayloadValidation.validated(ZoneNetworkHandler::handleZoneEnterClient, PayloadValidation.PayloadLimits.SMALL)
        );

        DevMod.LOGGER.debug("[Zone] Registered Zone Marker network handlers");
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private static void enqueueWork(IPayloadContext ctx, Runnable work) {
        ctx.enqueueWork(Objects.requireNonNull(work));
    }

    // ========================================================================
    // Client Handlers
    // ========================================================================

    private static void handleOpenEditorClient(OpenZoneEditorPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            MethodHandle method = getOpenZoneEditorMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("[Zone] Failed to open Zone Editor screen", e);
            }
        });
    }

    private static void handleZoneSyncClient(ZoneSyncPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            MethodHandle method = getHandleZoneSyncMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("[Zone] Failed to handle zone sync", e);
            }
        });
    }

    private static void handleZoneEnterClient(ZoneEnterPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            MethodHandle method = getHandleZoneEnterMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("[Zone] Failed to handle zone enter", e);
            }
        });
    }

    // ========================================================================
    // Server Handlers
    // ========================================================================

    private static void handleSaveZoneServer(SaveZonePayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions (OP level 2 required)
            if (!player.hasPermissions(2)) {
                DevMod.LOGGER.warn("[Zone] Player {} tried to save zone without permissions",
                    player.getName().getString());
                return;
            }

            ZoneRegistry registry = ZoneRegistry.get(Objects.requireNonNull(player.getServer()));
            ZoneDefinition definition = Objects.requireNonNull(payload.definition());

            if (payload.isNewZone()) {
                // Creating new zone
                UUID zoneUUID = registry.createZone(definition);
                DevMod.LOGGER.info("[Zone] Player {} created zone: {} ({})",
                    player.getName().getString(), definition.zoneId(), zoneUUID);

                // Link marker if requested
                if (payload.linkToMarker() && payload.markerPosition() != null) {
                    linkMarkerToZone(player.serverLevel(), payload.markerPosition(), zoneUUID);
                }
            } else {
                // Updating existing zone
                UUID existingId = payload.existingZoneId();
                if (existingId == null) return;

                // Validate zone exists
                Optional<ZoneDefinition> existing = registry.getZone(existingId);
                if (existing.isEmpty()) {
                    DevMod.LOGGER.warn("[Zone] Player {} tried to update non-existent zone {}",
                        player.getName().getString(), existingId);
                    return;
                }

                boolean updated = registry.updateZone(existingId, Objects.requireNonNull(definition));
                if (updated) {
                    DevMod.LOGGER.info("[Zone] Player {} updated zone: {} ({})",
                        player.getName().getString(), definition.zoneId(), existingId);
                } else {
                    DevMod.LOGGER.warn("[Zone] Failed to update zone {}", existingId);
                }
            }
        });
    }

    private static void handleDeleteZoneServer(DeleteZonePayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions
            if (!player.hasPermissions(2)) {
                DevMod.LOGGER.warn("[Zone] Player {} tried to delete zone without permissions",
                    player.getName().getString());
                return;
            }

            ZoneRegistry registry = ZoneRegistry.get(Objects.requireNonNull(player.getServer()));
            Optional<ZoneDefinition> zoneOpt = registry.getZone(Objects.requireNonNull(payload.zoneId()));

            if (zoneOpt.isEmpty()) {
                DevMod.LOGGER.warn("[Zone] Player {} tried to delete non-existent zone {}",
                    player.getName().getString(), payload.zoneId());
                return;
            }

            ZoneDefinition zone = zoneOpt.get();
            boolean deleted = registry.deleteZone(Objects.requireNonNull(payload.zoneId()));

            if (deleted) {
                DevMod.LOGGER.info("[Zone] Player {} deleted zone: {} ({})",
                    player.getName().getString(), zone.zoneId(), payload.zoneId());

                // Optionally remove markers
                if (payload.removeMarkers()) {
                    removeMarkersForZone(player.serverLevel(), payload.zoneId(), registry);
                }
            }
        });
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private static void linkMarkerToZone(ServerLevel level, BlockPos markerPos, UUID zoneId) {
        BlockEntity be = level.getBlockEntity(Objects.requireNonNull(markerPos));
        if (be instanceof ZoneMarkerBlockEntity marker) {
            marker.setZoneId(zoneId);
            ZoneRegistry registry = ZoneRegistry.get(Objects.requireNonNull(level.getServer()));
            registry.registerMarker(Objects.requireNonNull(markerPos), Objects.requireNonNull(zoneId));
            DevMod.LOGGER.debug("[Zone] Linked marker at {} to zone {}", markerPos, zoneId);
        }
    }

    private static void removeMarkersForZone(ServerLevel level, UUID zoneId, ZoneRegistry registry) {
        // Get all markers for this zone and remove them
        var markerPositions = registry.getMarkersForZone(Objects.requireNonNull(zoneId));
        for (BlockPos pos : markerPositions) {
            if (level.getBlockState(Objects.requireNonNull(pos)).is(Objects.requireNonNull(ZoneBlocks.ZONE_MARKER.get()))) {
                level.removeBlock(Objects.requireNonNull(pos), false);
            }
        }
        DevMod.LOGGER.debug("[Zone] Removed {} markers for zone {}", markerPositions.size(), zoneId);
    }

    // ========================================================================
    // Public API for sending payloads
    // ========================================================================

    /**
     * Opens the zone editor screen for a player.
     *
     * @param player        The server player
     * @param existingZone  Existing zone definition (null for new zone)
     * @param markerPosition The marker block position (optional)
     * @param canDelete     Whether the zone can be deleted
     */
    public static void openZoneEditor(
        @Nonnull ServerPlayer player,
        @Nullable ZoneDefinition existingZone,
        @Nullable BlockPos markerPosition,
        boolean canDelete
    ) {
        sendPacket(player, new OpenZoneEditorPayload(existingZone, markerPosition, canDelete));
    }

    /**
     * Opens the zone editor screen with default delete behavior.
     * Backwards-compatible wrapper for callers that don't pass canDelete.
     */
    public static void openEditorScreen(
        @Nonnull ServerPlayer player,
        @Nullable ZoneDefinition existingZone,
        @Nullable BlockPos markerPosition
    ) {
        boolean canDelete = existingZone != null;
        openZoneEditor(player, existingZone, markerPosition, canDelete);
    }

    /**
     * Syncs all zones in the current dimension to a player.
     *
     * @param player The server player
     */
    public static void syncZones(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        ZoneRegistry registry = ZoneRegistry.get(Objects.requireNonNull(player.getServer()));

        // Convert to summaries
        List<ZoneSyncPayload.ZoneSummary> summaries = registry.getAllZones().stream()
            .map(zone -> new ZoneSyncPayload.ZoneSummary(
                zone.id(),
                zone.zoneId(),
                zone.displayName(),
                zone.bounds(),
                zone.color(),
                zone.priority(),
                zone.hasTeleport()
            ))
            .toList();

        sendPacket(player, new ZoneSyncPayload(summaries, registry.getModificationVersion()));
    }

    /**
     * Sends zone enter notification to a player.
     *
     * @param player The server player
     * @param zone   The zone being entered
     */
    public static void sendZoneEnter(@Nonnull ServerPlayer player, @Nonnull ZoneDefinition zone) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(zone, "zone");

        sendPacket(player, ZoneEnterPayload.fromZone(
            zone.id(),
            zone.displayName(),
            zone.hintMessage(),
            zone.cueSound(),
            zone.color(),
            zone.isSpecial()
        ));
    }
}
