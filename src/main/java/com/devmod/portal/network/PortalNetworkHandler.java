package com.devmod.portal.network;

import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import com.devmod.network.ChannelId;
import com.devmod.network.NetworkHandler;
import com.devmod.network.handlers.NetworkHandlerBase;
import com.devmod.network.handlers.PayloadRegistrar;
import com.devmod.portal.PortalData;
import com.devmod.portal.PortalRegistry;
import com.devmod.portal.block.CustomPortalBlock;
import com.devmod.runtime.NexusDimensionManager;
import com.devmod.runtime.NexusPortalManager;

import static com.devmod.network.PayloadValidation.PayloadLimits;
import static com.devmod.network.PayloadValidation.validated;

/**
 * Domain-specific network handler for portal-related payloads.
 * Handles portal state sync, preview requests, and preview responses.
 */
public final class PortalNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final PortalNetworkHandler INSTANCE = new PortalNetworkHandler();

    private PortalNetworkHandler() {}

    @Override
    public String getDomainName() {
        return "portal";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // PORTAL_STATE: Server -> Client portal teleportation overlay state
        event.registrar(ChannelId.PORTAL_STATE.asString()).playToClient(
            nn(PortalStatePayload.TYPE),
            nn(PortalStatePayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handlePortalState(payload))), "portal state");
                }
            }, PayloadLimits.SMALL)
        );

        // PORTAL_PREVIEW_REQUEST: Client -> Server request for portal destination info
        event.registrar(ChannelId.PORTAL_PREVIEW_REQUEST.asString()).playToServer(
            nn(PortalPreviewRequestPayload.TYPE),
            nn(PortalPreviewRequestPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    handlePortalPreviewRequest(player, payload);
                }
            }, PayloadLimits.SMALL)
        );

        // PORTAL_PREVIEW: Server -> Client portal destination preview
        event.registrar(ChannelId.PORTAL_PREVIEW.asString()).playToClient(
            nn(PortalPreviewPayload.TYPE),
            nn(PortalPreviewPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handlePortalPreview(payload))), "portal preview");
                }
            }, PayloadLimits.SMALL)
        );
    }

    /**
     * Send portal state sync to a player.
     * Used to sync portal teleportation overlay state.
     */
    public static void sendPortalState(ServerPlayer player, PortalStatePayload payload) {
        sendPacket(Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /**
     * Send portal preview to a player.
     * Used to show destination info when looking at a portal.
     */
    public static void sendPortalPreview(ServerPlayer player, PortalPreviewPayload payload) {
        sendPacket(Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /**
     * Handle portal preview request from a client.
     * Looks up the portal data and sends back destination information.
     */
    private static void handlePortalPreviewRequest(ServerPlayer player, PortalPreviewRequestPayload request) {
        var level = Objects.requireNonNull(player.serverLevel(), "serverLevel");
        var registry = PortalRegistry.get(level);
        var blockState = level.getBlockState(request.portalPos());

        // Verify the block is actually a portal
        if (!(blockState.getBlock() instanceof CustomPortalBlock)) {
            return;
        }

        var color = Objects.requireNonNull(
            blockState.getValue(Objects.requireNonNull(CustomPortalBlock.COLOR)),
            "portal color"
        );

        // Find the portal data
        var portalOpt = registry.findPortalContaining(level, request.portalPos(), color);
        if (portalOpt.isEmpty()) {
            return;
        }

        var portal = portalOpt.get();

        // Build the preview response
        PortalPreviewPayload response;

        if (portal.hasFixedDestination()) {
            // Fixed destination (Nexus zone portal)
            String zoneName = Objects.requireNonNull(
                getFixedDestinationName(player.getServer(), portal), "zoneName");
            String dimName = Objects.requireNonNull(
                getDimensionDisplayName(portal.fixedDestinationDimension().orElse(null)), "dimName");
            response = PortalPreviewPayload.forFixedDestination(
                request.portalPos(), zoneName, dimName, color);
        } else if (portal.isLinked()) {
            // Linked portal
            var linkedOpt = portal.linkedPortalId().flatMap(registry::get);
            if (linkedOpt.isEmpty()) {
                return;
            }
            var linked = linkedOpt.get();
            String ownerName = Objects.requireNonNull(getLinkedPortalName(linked), "ownerName");
            String dimName = Objects.requireNonNull(
                getDimensionDisplayName(linked.dimension().orElse(null)), "dimName");
            int distance = calculateDistance(portal, linked);
            response = PortalPreviewPayload.forLinkedPortal(
                request.portalPos(), ownerName, dimName, distance, color);
        } else {
            // Unlinked portal - no preview
            return;
        }

        sendPortalPreview(player, response);
    }

    /**
     * Get display name for a fixed destination portal (Nexus zone).
     */
    private static String getFixedDestinationName(@Nullable MinecraftServer server, PortalData portal) {
        // Try to determine zone from destination position
        var destPos = portal.fixedDestination().orElse(null);
        if (server != null && destPos != null) {
            var hubOrigin = Objects.requireNonNull(NexusDimensionManager.getHubOrigin(), "hubOrigin");
            var zone = NexusPortalManager.INSTANCE.getZoneAtPosition(server, hubOrigin, destPos);
            if (zone != null) {
                return zone.displayName();
            }
        }
        return "Portal Destination";
    }

    /**
     * Get display name for a linked portal.
     */
    private static String getLinkedPortalName(PortalData portal) {
        var creatorOpt = portal.creator();
        if (creatorOpt.isPresent()) {
            // Try to get player name - for now just show "Linked Portal"
            return "Linked Portal";
        }
        return "Linked Portal";
    }

    /**
     * Get display name for a dimension.
     */
    private static String getDimensionDisplayName(@Nullable net.minecraft.resources.ResourceLocation dim) {
        if (dim == null) {
            return "Unknown";
        }
        // Format dimension ID nicely
        String path = dim.getPath();
        if (path.equals("overworld")) return "Overworld";
        if (path.equals("the_nether")) return "Nether";
        if (path.equals("the_end")) return "The End";
        if (path.equals("nexus")) return "Nexus";
        // Capitalize first letter
        return path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1).replace("_", " ");
    }

    /**
     * Calculate distance between two portals.
     * Returns -1 if cross-dimension.
     */
    private static int calculateDistance(PortalData from, PortalData to) {
        Objects.requireNonNull(to, "to");
        if (from.isInterDimensional(to)) {
            return -1;
        }
        var fromPos = from.position().orElse(null);
        var toPos = to.position().orElse(null);
        if (fromPos == null || toPos == null) {
            return -1;
        }
        return (int) Math.sqrt(fromPos.distSqr(toPos));
    }
}
