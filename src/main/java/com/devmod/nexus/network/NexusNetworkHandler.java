package com.devmod.nexus.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaRegistry;
import com.devmod.network.ChannelId;
import com.devmod.network.PayloadValidation;
import com.devmod.network.handlers.NetworkHandlerBase;
import com.devmod.nexus.client.NexusClientCache;
import com.devmod.nexus.data.ZoneSlot;
import com.devmod.nexus.data.ZoneSlotRegistry;
import com.devmod.nexus.runtime.NexusHubManager;

/**
 * Network handler for Nexus Hub system payloads.
 * Handles registration and processing of all nexus-related network messages.
 */
public final class NexusNetworkHandler extends NetworkHandlerBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusNetworkHandler.class);

    public static final NexusNetworkHandler INSTANCE = new NexusNetworkHandler();

    private NexusNetworkHandler() {}

    /**
     * Registers all Nexus Hub payload handlers.
     * Called from NetworkHandler during mod initialization.
     */
    public void registerPayloads(@Nonnull RegisterPayloadHandlersEvent event) {
        // Client -> Server: Request slot list
        event.registrar(ChannelId.NEXUS_SLOT_LIST_REQUEST.asString()).playToServer(
            nn(RequestSlotListPayload.TYPE),
            nn(RequestSlotListPayload.STREAM_CODEC),
            PayloadValidation.validated(NexusNetworkHandler::handleRequestSlotListServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Slot list response
        event.registrar(ChannelId.NEXUS_SLOT_LIST.asString()).playToClient(
            nn(SlotListPayload.TYPE),
            nn(SlotListPayload.STREAM_CODEC),
            PayloadValidation.validated(NexusNetworkHandler::handleSlotListClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Server -> Client: Hub status
        event.registrar(ChannelId.NEXUS_HUB_STATUS.asString()).playToClient(
            nn(HubStatusPayload.TYPE),
            nn(HubStatusPayload.STREAM_CODEC),
            PayloadValidation.validated(NexusNetworkHandler::handleHubStatusClient, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Build progress
        event.registrar(ChannelId.NEXUS_BUILD_PROGRESS.asString()).playToClient(
            nn(NexusBuildProgressPayload.TYPE),
            nn(NexusBuildProgressPayload.STREAM_CODEC),
            PayloadValidation.validated(NexusNetworkHandler::handleBuildProgressClient, PayloadValidation.PayloadLimits.SMALL)
        );

        LOGGER.debug("[Nexus] Network payloads registered");
    }

    // ========================================================================
    // Server Handlers
    // ========================================================================

    /**
     * Handle slot list request from client.
     */
    private static void handleRequestSlotListServer(@Nonnull RequestSlotListPayload payload,
                                                     @Nonnull IPayloadContext context) {
        var unused = context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.getServer() == null) {
                return;
            }

            ZoneSlotRegistry registry = ZoneSlotRegistry.get(Objects.requireNonNull(player.getServer()));
            AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));

            List<SlotListPayload.SlotSummary> summaries = new ArrayList<>();
            for (ZoneSlot slot : registry.getAllSlots()) {
                String linkedAreaName = null;
                if (slot.linkedAreaId() != null) {
                    linkedAreaName = areaRegistry.getArea(Objects.requireNonNull(slot.linkedAreaId()))
                        .map(AreaDefinition::name)
                        .orElse("Unknown");
                }

                summaries.add(new SlotListPayload.SlotSummary(
                    Objects.requireNonNull(slot.slotId()),
                    Objects.requireNonNull(slot.displayName()),
                    Objects.requireNonNull(slot.type()),
                    Objects.requireNonNull(slot.portalColor()),
                    slot.hasLinkedArea(),
                    slot.linkedAreaId(),
                    linkedAreaName
                ));
            }

            PacketDistributor.sendToPlayer(player, new SlotListPayload(summaries));
            LOGGER.debug("[Nexus] Sent slot list ({} slots) to {}", summaries.size(), player.getName().getString());
        });
    }

    // ========================================================================
    // Client Handlers
    // ========================================================================

    /**
     * Handle slot list response on client.
     */
    private static void handleSlotListClient(@Nonnull SlotListPayload payload,
                                              @Nonnull IPayloadContext context) {
        var unused = context.enqueueWork(() -> {
            NexusClientCache.INSTANCE.updateSlots(Objects.requireNonNull(payload.slots()));
            LOGGER.debug("[Nexus] Updated client cache with {} slots", payload.slots().size());
        });
    }

    /**
     * Handle hub status on client.
     */
    private static void handleHubStatusClient(@Nonnull HubStatusPayload payload,
                                               @Nonnull IPayloadContext context) {
        var unused = context.enqueueWork(() -> {
            NexusClientCache.INSTANCE.updateHubStatus(payload);
            LOGGER.debug("[Nexus] Updated client cache: initialized={}, slots={}",
                payload.initialized(), payload.totalSlots());
        });
    }

    /**
     * Handle build progress on client.
     */
    private static void handleBuildProgressClient(@Nonnull NexusBuildProgressPayload payload,
                                                   @Nonnull IPayloadContext context) {
        var unused = context.enqueueWork(() -> {
            NexusClientCache.INSTANCE.updateBuildProgress(payload);
            LOGGER.debug("[Nexus] Build progress: {}/{} step='{}' complete={}",
                payload.currentStep(), payload.totalSteps(), payload.stepName(), payload.complete());
        });
    }

    // ========================================================================
    // Server-side send helpers
    // ========================================================================

    /**
     * Send hub status to a player.
     */
    public static void sendHubStatus(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player);
        if (player.getServer() == null) {
            return;
        }

        NexusHubManager.HubStatus status = NexusHubManager.INSTANCE.getStatus(Objects.requireNonNull(player.getServer()));
        HubStatusPayload payload = new HubStatusPayload(
            status.sessionInitialized(),
            status.totalSlots(),
            status.linkedSlots(),
            status.emptySlots()
        );

        PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * Send slot list to a player.
     */
    public static void sendSlotList(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player);
        if (player.getServer() == null) {
            return;
        }

        ZoneSlotRegistry registry = ZoneSlotRegistry.get(Objects.requireNonNull(player.getServer()));
        AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));

        List<SlotListPayload.SlotSummary> summaries = new ArrayList<>();
        for (ZoneSlot slot : registry.getAllSlots()) {
            String linkedAreaName = null;
            if (slot.linkedAreaId() != null) {
                linkedAreaName = areaRegistry.getArea(Objects.requireNonNull(slot.linkedAreaId()))
                    .map(AreaDefinition::name)
                    .orElse("Unknown");
            }

            summaries.add(new SlotListPayload.SlotSummary(
                Objects.requireNonNull(slot.slotId()),
                Objects.requireNonNull(slot.displayName()),
                Objects.requireNonNull(slot.type()),
                Objects.requireNonNull(slot.portalColor()),
                slot.hasLinkedArea(),
                slot.linkedAreaId(),
                linkedAreaName
            ));
        }

        PacketDistributor.sendToPlayer(player, new SlotListPayload(summaries));
        LOGGER.debug("[Nexus] Sent slot list ({} slots) to {}", summaries.size(), player.getName().getString());
    }
}
