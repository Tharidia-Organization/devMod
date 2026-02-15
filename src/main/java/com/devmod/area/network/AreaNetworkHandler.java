package com.devmod.area.network;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaRegistry;
import com.devmod.network.ChannelId;
import com.devmod.network.PayloadValidation;
import com.devmod.network.handlers.NetworkHandlerBase;

/**
 * Network handler for Area Builder system payloads.
 * Facade that delegates to specialized handlers:
 * <ul>
 *   <li>{@link AreaClientPayloadHandler} - client-side payload handling</li>
 *   <li>{@link AreaServerPayloadHandler} - server-side payload handling</li>
 *   <li>{@link AreaDefinitionValidator} - definition validation/sanitization</li>
 *   <li>{@link TemplateManagementHandler} - template operations</li>
 *   <li>{@link SnapshotManagementHandler} - snapshot operations</li>
 * </ul>
 */
public final class AreaNetworkHandler extends NetworkHandlerBase {
    public static final AreaNetworkHandler INSTANCE = new AreaNetworkHandler();

    private AreaNetworkHandler() {}

    // ========================================================================
    // Cooldown Delegates
    // ========================================================================

    /**
     * Clears all cooldowns for a player (called on disconnect).
     * Delegates to CooldownManager.
     *
     * @param playerId The UUID of the player to clear
     */
    public static void clearPlayerBuildCooldown(@Nullable UUID playerId) {
        CooldownManager.clearAllPlayerCooldowns(playerId);
    }

    /**
     * M-04 fix: Clears area build cooldown when area is deleted.
     * Delegates to CooldownManager.
     *
     * @param areaId The UUID of the deleted area
     */
    public static void clearAreaBuildCooldown(@Nullable UUID areaId) {
        CooldownManager.clearAreaBuildCooldown(areaId);
    }

    /**
     * M-04 fix: Periodically cleans up expired cooldown entries.
     * Delegates to CooldownManager.
     */
    public static void cleanupExpiredCooldowns() {
        CooldownManager.cleanupExpiredCooldowns();
    }

    // ========================================================================
    // Payload Registration
    // ========================================================================

    /**
     * Registers all Area Builder payload handlers.
     * Called from NetworkHandler during mod initialization.
     */
    public void registerPayloads(@Nonnull RegisterPayloadHandlersEvent event) {
        // Server -> Client: Open area builder screen
        event.registrar(ChannelId.AREA_BUILDER_OPEN.asString()).playToClient(
            nn(OpenAreaBuilderPayload.TYPE),
            nn(OpenAreaBuilderPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaClientPayloadHandler::handleOpenBuilderClient, PayloadValidation.PayloadLimits.LARGE)
        );

        // Server -> Client: Open editor central screen
        event.registrar(ChannelId.AREA_EDITOR_CENTRAL_OPEN.asString()).playToClient(
            nn(OpenEditorCentralPayload.TYPE),
            nn(OpenEditorCentralPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaClientPayloadHandler::handleOpenEditorCentralClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Save area configuration
        event.registrar(ChannelId.AREA_SAVE.asString()).playToServer(
            nn(SaveAreaPayload.TYPE),
            nn(SaveAreaPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaServerPayloadHandler::handleSaveAreaServer, PayloadValidation.PayloadLimits.LARGE)
        );

        // Server -> Client: Save area result (authoritative ID/revision)
        event.registrar(ChannelId.AREA_SAVE_RESULT.asString()).playToClient(
            nn(SaveAreaResultPayload.TYPE),
            nn(SaveAreaResultPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaClientPayloadHandler::handleSaveAreaResultClient, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Build area
        event.registrar(ChannelId.AREA_BUILD.asString()).playToServer(
            nn(BuildAreaPayload.TYPE),
            nn(BuildAreaPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaServerPayloadHandler::handleBuildAreaServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Request open area builder
        event.registrar(ChannelId.AREA_BUILDER_REQUEST.asString()).playToServer(
            nn(RequestOpenAreaBuilderPayload.TYPE),
            nn(RequestOpenAreaBuilderPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaServerPayloadHandler::handleOpenBuilderRequestServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Area preview
        event.registrar(ChannelId.AREA_PREVIEW.asString()).playToClient(
            nn(AreaPreviewPayload.TYPE),
            nn(AreaPreviewPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaClientPayloadHandler::handleAreaPreviewClient, PayloadValidation.PayloadLimits.LARGE)
        );

        // Client -> Server: Request zone list
        event.registrar(ChannelId.AREA_ZONE_LIST_REQUEST.asString()).playToServer(
            nn(RequestZoneListPayload.TYPE),
            nn(RequestZoneListPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaServerPayloadHandler::handleRequestZoneListServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Zone list response
        event.registrar(ChannelId.AREA_ZONE_LIST.asString()).playToClient(
            nn(ZoneListPayload.TYPE),
            nn(ZoneListPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaClientPayloadHandler::handleZoneListClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Request template list
        event.registrar(ChannelId.AREA_TEMPLATE_REQUEST.asString()).playToServer(
            nn(RequestTemplateListPayload.TYPE),
            nn(RequestTemplateListPayload.STREAM_CODEC),
            PayloadValidation.validated(TemplateManagementHandler::handleRequestTemplateListServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Template list response
        event.registrar(ChannelId.AREA_TEMPLATE_LIST.asString()).playToClient(
            nn(TemplateListPayload.TYPE),
            nn(TemplateListPayload.STREAM_CODEC),
            PayloadValidation.validated(TemplateManagementHandler::handleTemplateListClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Load template data
        event.registrar(ChannelId.AREA_TEMPLATE_LOAD.asString()).playToServer(
            nn(LoadTemplatePayload.TYPE),
            nn(LoadTemplatePayload.STREAM_CODEC),
            PayloadValidation.validated(TemplateManagementHandler::handleLoadTemplateServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Template data response
        event.registrar(ChannelId.AREA_TEMPLATE_DATA.asString()).playToClient(
            nn(TemplateDataPayload.TYPE),
            nn(TemplateDataPayload.STREAM_CODEC),
            PayloadValidation.validated(TemplateManagementHandler::handleTemplateDataClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Save area as template
        event.registrar(ChannelId.AREA_TEMPLATE_SAVE.asString()).playToServer(
            nn(SaveAreaTemplatePayload.TYPE),
            nn(SaveAreaTemplatePayload.STREAM_CODEC),
            PayloadValidation.validated(TemplateManagementHandler::handleSaveTemplateServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Clone area
        event.registrar(ChannelId.AREA_CLONE.asString()).playToServer(
            nn(CloneAreaPayload.TYPE),
            nn(CloneAreaPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaServerPayloadHandler::handleCloneAreaServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Delete template
        event.registrar(ChannelId.AREA_TEMPLATE_DELETE.asString()).playToServer(
            nn(DeleteTemplatePayload.TYPE),
            nn(DeleteTemplatePayload.STREAM_CODEC),
            PayloadValidation.validated(TemplateManagementHandler::handleDeleteTemplateServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Delete area
        event.registrar(ChannelId.AREA_DELETE.asString()).playToServer(
            nn(DeleteAreaPayload.TYPE),
            nn(DeleteAreaPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaServerPayloadHandler::handleDeleteAreaServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Promote area to main hub
        event.registrar(ChannelId.AREA_PROMOTE_MAIN_HUB.asString()).playToServer(
            nn(PromoteMainHubPayload.TYPE),
            nn(PromoteMainHubPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaServerPayloadHandler::handlePromoteMainHubServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Capture snapshot
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(CaptureSnapshotPayload.TYPE),
            nn(CaptureSnapshotPayload.STREAM_CODEC),
            PayloadValidation.validated(SnapshotManagementHandler::handleCaptureSnapshotServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Restore snapshot
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(RestoreSnapshotPayload.TYPE),
            nn(RestoreSnapshotPayload.STREAM_CODEC),
            PayloadValidation.validated(SnapshotManagementHandler::handleRestoreSnapshotServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Delete snapshot
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(DeleteSnapshotPayload.TYPE),
            nn(DeleteSnapshotPayload.STREAM_CODEC),
            PayloadValidation.validated(SnapshotManagementHandler::handleDeleteSnapshotServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Request snapshot list
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(RequestSnapshotListPayload.TYPE),
            nn(RequestSnapshotListPayload.STREAM_CODEC),
            PayloadValidation.validated(SnapshotManagementHandler::handleRequestSnapshotListServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Snapshot list response
        event.registrar(ChannelId.AREA_BUILDER_FEEDBACK.asString()).playToClient(
            nn(SnapshotListPayload.TYPE),
            nn(SnapshotListPayload.STREAM_CODEC),
            PayloadValidation.validated(SnapshotManagementHandler::handleSnapshotListClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Pause build
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(PauseBuildPayload.TYPE),
            nn(PauseBuildPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaServerPayloadHandler::handlePauseBuildServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Resume build
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(ResumeBuildPayload.TYPE),
            nn(ResumeBuildPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaServerPayloadHandler::handleResumeBuildServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Build status update
        event.registrar(ChannelId.AREA_BUILDER_FEEDBACK.asString()).playToClient(
            nn(BuildStatusPayload.TYPE),
            nn(BuildStatusPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaClientPayloadHandler::handleBuildStatusClient, PayloadValidation.PayloadLimits.SMALL)
        );

        DevMod.LOGGER.debug("Registered Area Builder network handlers");
    }

    // ========================================================================
    // Shared Utilities (used by extracted handler classes)
    // ========================================================================

    /**
     * Enqueue work on the main thread with error handling.
     * Used by extracted handler classes.
     */
    public static void enqueueWork(IPayloadContext ctx, Runnable work) {
        ctx.enqueueWork(Objects.requireNonNull(work))
            .exceptionally(e -> {
                DevMod.LOGGER.error("[AreaNetwork] Error in enqueued work: {}", e.getMessage(), e);
                return null;
            });
    }

    /**
     * Send packet to player.
     * Used by extracted handler classes.
     */
    public static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendPacket(
            ServerPlayer player, T payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    // ========================================================================
    // Public API for sending payloads
    // ========================================================================

    /**
     * Opens the area builder screen for a player.
     *
     * @param player         The server player
     * @param existingArea   Existing area definition (null for new area)
     * @param editorPosition The editor block position (optional)
     * @param isMainHub      Whether this is the main hub area
     */
    public static void openBuilderScreen(
        @Nonnull ServerPlayer player,
        @Nullable AreaDefinition existingArea,
        @Nullable BlockPos editorPosition,
        boolean isMainHub
    ) {
        sendPacket(player, new OpenAreaBuilderPayload(existingArea, editorPosition, isMainHub));
    }

    /**
     * Opens the editor central screen for a player.
     *
     * @param player The server player
     */
    public static void openEditorCentralScreen(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));

        // H-07 fix: Track total count for truncation warning
        int totalAreaCount = registry.getAreaCount();
        boolean hasMoreAreas = totalAreaCount > OpenEditorCentralPayload.MAX_AREAS;

        // Convert to summaries
        List<OpenEditorCentralPayload.AreaSummary> summaries = registry.getAllAreas().stream()
            .limit(OpenEditorCentralPayload.MAX_AREAS)
            .map(area -> new OpenEditorCentralPayload.AreaSummary(
                area.id(),
                area.name(),
                area.shape().name(),
                area.dimensions().width(),
                area.dimensions().length(),
                area.dimensions().height(),
                area.isMainHub(),
                area.createdAt()
            ))
            .toList();

        UUID mainHubId = registry.getMainHubId();
        boolean canCreateAreas = player.hasPermissions(2);

        sendPacket(player, new OpenEditorCentralPayload(summaries, mainHubId, canCreateAreas, hasMoreAreas, totalAreaCount));
    }

    /**
     * Sends an area preview to a player.
     *
     * @param player  The server player
     * @param payload The preview payload
     */
    public static void sendAreaPreview(@Nonnull ServerPlayer player, @Nonnull AreaPreviewPayload payload) {
        sendPacket(player, payload);
    }

    /**
     * Sends a bounding box preview for an area definition.
     * Note: This uses the old rectangular bounding box method.
     * Consider using sendShapePreview() for accurate shape visualization.
     *
     * @param player     The server player
     * @param definition The area definition to preview
     */
    public static void sendBoundingBoxPreview(@Nonnull ServerPlayer player, @Nonnull AreaDefinition definition) {
        AreaPreviewPayload preview = AreaPreviewPayload.boundingBox(
            definition.centerPosition(),
            definition.dimensions().width(),
            definition.dimensions().length(),
            definition.dimensions().height(),
            definition.shape().name()
        );
        sendPacket(player, preview);
    }

    /**
     * HIGH-01 fix: Sends an accurate shape preview for an area definition.
     * This generates preview blocks that accurately represent the actual shape
     * (CIRCULAR, HEX, OCT, CUSTOM_NBT, etc.) instead of a bounding box.
     *
     * @param player     The server player
     * @param definition The area definition to preview
     */
    public static void sendShapePreview(@Nonnull ServerPlayer player, @Nonnull AreaDefinition definition) {
        AreaPreviewPayload preview = AreaPreviewPayload.fromShape(
            Objects.requireNonNull(definition.centerPosition()),
            Objects.requireNonNull(definition.shape()),
            Objects.requireNonNull(definition.dimensions()),
            definition.customShapeNbt()
        );
        sendPacket(player, preview);
    }

    /**
     * MED-09 fix: Sends build progress update to a player.
     * Called from AreaBuildTaskManager when milestone progress is crossed.
     *
     * @param player   The server player
     * @param areaId   The area ID being built
     * @param percent  Progress percentage (0-100)
     */
    public static void sendBuildProgress(@Nonnull ServerPlayer player, @Nonnull UUID areaId, int percent) {
        sendPacket(player, BuildStatusPayload.progress(areaId, percent));
    }

    /**
     * MED-09 fix: Sends build completion status to a player.
     * Called from AreaBuildTaskManager when multi-tick build completes.
     *
     * @param player The server player
     * @param areaId The area ID that completed building
     */
    public static void sendBuildCompleted(@Nonnull ServerPlayer player, @Nonnull UUID areaId) {
        sendPacket(player, BuildStatusPayload.completed(areaId));
    }

    /**
     * MED-03 fix: Sends build started status to a player.
     * Called from AreaBuildTaskManager when a queued build starts.
     *
     * @param player The server player
     * @param areaId The area ID that started building
     */
    public static void sendBuildStarted(@Nonnull ServerPlayer player, @Nonnull UUID areaId) {
        sendPacket(player, BuildStatusPayload.started(areaId));
    }
}
