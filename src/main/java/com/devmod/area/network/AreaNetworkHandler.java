package com.devmod.area.network;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;
import com.devmod.area.aesthetic.AreaBuilderMessages;
import com.devmod.area.aesthetic.AreaBuilderNaming;
import com.devmod.area.builder.AreaBuildTaskManager;
import com.devmod.area.builder.AreaShapeGenerator;
import com.devmod.area.data.AreaAuditLog;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaRegistry;
import com.devmod.area.data.AreaShape;
import com.devmod.area.data.BiomeGenerationConfig;
import com.devmod.area.snapshot.AreaSnapshot;
import com.devmod.area.snapshot.AreaSnapshotManager;
import com.devmod.area.snapshot.AreaSnapshotRegistry;
import com.devmod.area.template.AreaTemplate;
import com.devmod.area.template.AreaTemplateRegistry;
import com.devmod.network.ChannelId;
import com.devmod.network.PayloadValidation;
import com.devmod.network.handlers.NetworkHandlerBase;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZoneRegistry;

/**
 * Network handler for Area Builder system payloads.
 * Handles registration and processing of all area-related network messages.
 */
public final class AreaNetworkHandler extends NetworkHandlerBase {
    public static final AreaNetworkHandler INSTANCE = new AreaNetworkHandler();

    private static final int MAX_CUSTOM_SHAPE_POSITIONS = 65_536;
    private static final int MAX_PALETTE_MATERIALS = 128;
    /** H-06 fix: Maximum NBT size for custom shapes (64KB) */
    private static final int MAX_CUSTOM_SHAPE_NBT_SIZE = 65_536;

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
    // Cached MethodHandles for client-side reflection (avoid per-call lookup)
    // ========================================================================

    private static final String CLIENT_HOOKS_CLASS = "com.devmod.client.area.AreaClientHooks";

    @Nullable private static volatile MethodHandle openBuilderScreenMethod;
    @Nullable private static volatile MethodHandle openEditorCentralScreenMethod;
    @Nullable private static volatile MethodHandle showAreaPreviewMethod;
    @Nullable private static volatile MethodHandle handleZoneListMethod;
    @Nullable private static volatile MethodHandle handleTemplateListMethod;
    @Nullable private static volatile MethodHandle handleTemplateDataMethod;
    @Nullable private static volatile MethodHandle handleSaveAreaResultMethod;
    @Nullable private static volatile MethodHandle handleSnapshotListMethod;
    @Nullable private static volatile MethodHandle handleBuildStatusMethod;

    @Nullable
    private static MethodHandle getOpenBuilderScreenMethod() {
        if (openBuilderScreenMethod == null) {
            synchronized (AreaNetworkHandler.class) {
                if (openBuilderScreenMethod == null) {
                    openBuilderScreenMethod = lookupMethod("openBuilderScreen", OpenAreaBuilderPayload.class);
                }
            }
        }
        return openBuilderScreenMethod;
    }

    @Nullable
    private static MethodHandle getOpenEditorCentralScreenMethod() {
        if (openEditorCentralScreenMethod == null) {
            synchronized (AreaNetworkHandler.class) {
                if (openEditorCentralScreenMethod == null) {
                    openEditorCentralScreenMethod = lookupMethod("openEditorCentralScreen", OpenEditorCentralPayload.class);
                }
            }
        }
        return openEditorCentralScreenMethod;
    }

    @Nullable
    private static MethodHandle getShowAreaPreviewMethod() {
        if (showAreaPreviewMethod == null) {
            synchronized (AreaNetworkHandler.class) {
                if (showAreaPreviewMethod == null) {
                    showAreaPreviewMethod = lookupMethod("showAreaPreview", AreaPreviewPayload.class);
                }
            }
        }
        return showAreaPreviewMethod;
    }

    @Nullable
    private static MethodHandle getHandleZoneListMethod() {
        if (handleZoneListMethod == null) {
            synchronized (AreaNetworkHandler.class) {
                if (handleZoneListMethod == null) {
                    handleZoneListMethod = lookupMethod("handleZoneList", ZoneListPayload.class);
                }
            }
        }
        return handleZoneListMethod;
    }

    @Nullable
    private static MethodHandle getHandleTemplateListMethod() {
        if (handleTemplateListMethod == null) {
            synchronized (AreaNetworkHandler.class) {
                if (handleTemplateListMethod == null) {
                    handleTemplateListMethod = lookupMethod("handleTemplateList", TemplateListPayload.class);
                }
            }
        }
        return handleTemplateListMethod;
    }

    @Nullable
    private static MethodHandle getHandleTemplateDataMethod() {
        if (handleTemplateDataMethod == null) {
            synchronized (AreaNetworkHandler.class) {
                if (handleTemplateDataMethod == null) {
                    handleTemplateDataMethod = lookupMethod("handleTemplateData", TemplateDataPayload.class);
                }
            }
        }
        return handleTemplateDataMethod;
    }

    @Nullable
    private static MethodHandle getHandleSaveAreaResultMethod() {
        if (handleSaveAreaResultMethod == null) {
            synchronized (AreaNetworkHandler.class) {
                if (handleSaveAreaResultMethod == null) {
                    handleSaveAreaResultMethod = lookupMethod("handleSaveAreaResult", SaveAreaResultPayload.class);
                }
            }
        }
        return handleSaveAreaResultMethod;
    }

    @Nullable
    private static MethodHandle getHandleSnapshotListMethod() {
        if (handleSnapshotListMethod == null) {
            synchronized (AreaNetworkHandler.class) {
                if (handleSnapshotListMethod == null) {
                    handleSnapshotListMethod = lookupMethod("handleSnapshotList", SnapshotListPayload.class);
                }
            }
        }
        return handleSnapshotListMethod;
    }

    @Nullable
    private static MethodHandle getHandleBuildStatusMethod() {
        if (handleBuildStatusMethod == null) {
            synchronized (AreaNetworkHandler.class) {
                if (handleBuildStatusMethod == null) {
                    handleBuildStatusMethod = lookupMethod("handleBuildStatus", BuildStatusPayload.class);
                }
            }
        }
        return handleBuildStatusMethod;
    }

    @Nullable
    private static MethodHandle lookupMethod(String methodName, Class<?> paramType) {
        try {
            Class<?> hooksClass = Class.forName(CLIENT_HOOKS_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.findStatic(hooksClass, methodName, MethodType.methodType(void.class, paramType));
        } catch (ClassNotFoundException e) {
            // Expected on dedicated server - client classes not available
            DevMod.LOGGER.debug("[Area] Client hooks class not available (dedicated server)");
            return null;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            DevMod.LOGGER.error("[Area] Failed to lookup client method: {}", methodName, e);
            return null;
        }
    }

    private AreaNetworkHandler() {}

    /**
     * Registers all Area Builder payload handlers.
     * Called from NetworkHandler during mod initialization.
     */
    public void registerPayloads(@Nonnull RegisterPayloadHandlersEvent event) {
        // Server -> Client: Open area builder screen
        event.registrar(ChannelId.AREA_BUILDER_OPEN.asString()).playToClient(
            nn(OpenAreaBuilderPayload.TYPE),
            nn(OpenAreaBuilderPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleOpenBuilderClient, PayloadValidation.PayloadLimits.LARGE)
        );

        // Server -> Client: Open editor central screen
        event.registrar(ChannelId.AREA_EDITOR_CENTRAL_OPEN.asString()).playToClient(
            nn(OpenEditorCentralPayload.TYPE),
            nn(OpenEditorCentralPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleOpenEditorCentralClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Save area configuration
        event.registrar(ChannelId.AREA_SAVE.asString()).playToServer(
            nn(SaveAreaPayload.TYPE),
            nn(SaveAreaPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleSaveAreaServer, PayloadValidation.PayloadLimits.LARGE)
        );

        // Server -> Client: Save area result (authoritative ID/revision)
        event.registrar(ChannelId.AREA_SAVE_RESULT.asString()).playToClient(
            nn(SaveAreaResultPayload.TYPE),
            nn(SaveAreaResultPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleSaveAreaResultClient, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Build area
        event.registrar(ChannelId.AREA_BUILD.asString()).playToServer(
            nn(BuildAreaPayload.TYPE),
            nn(BuildAreaPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleBuildAreaServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Request open area builder
        event.registrar(ChannelId.AREA_BUILDER_REQUEST.asString()).playToServer(
            nn(RequestOpenAreaBuilderPayload.TYPE),
            nn(RequestOpenAreaBuilderPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleOpenBuilderRequestServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Area preview
        event.registrar(ChannelId.AREA_PREVIEW.asString()).playToClient(
            nn(AreaPreviewPayload.TYPE),
            nn(AreaPreviewPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleAreaPreviewClient, PayloadValidation.PayloadLimits.LARGE)
        );

        // Client -> Server: Request zone list
        event.registrar(ChannelId.AREA_ZONE_LIST_REQUEST.asString()).playToServer(
            nn(RequestZoneListPayload.TYPE),
            nn(RequestZoneListPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleRequestZoneListServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Zone list response
        event.registrar(ChannelId.AREA_ZONE_LIST.asString()).playToClient(
            nn(ZoneListPayload.TYPE),
            nn(ZoneListPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleZoneListClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Request template list
        event.registrar(ChannelId.AREA_TEMPLATE_REQUEST.asString()).playToServer(
            nn(RequestTemplateListPayload.TYPE),
            nn(RequestTemplateListPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleRequestTemplateListServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Template list response
        event.registrar(ChannelId.AREA_TEMPLATE_LIST.asString()).playToClient(
            nn(TemplateListPayload.TYPE),
            nn(TemplateListPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleTemplateListClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Load template data
        event.registrar(ChannelId.AREA_TEMPLATE_LOAD.asString()).playToServer(
            nn(LoadTemplatePayload.TYPE),
            nn(LoadTemplatePayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleLoadTemplateServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Template data response
        event.registrar(ChannelId.AREA_TEMPLATE_DATA.asString()).playToClient(
            nn(TemplateDataPayload.TYPE),
            nn(TemplateDataPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleTemplateDataClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Save area as template
        event.registrar(ChannelId.AREA_TEMPLATE_SAVE.asString()).playToServer(
            nn(SaveAreaTemplatePayload.TYPE),
            nn(SaveAreaTemplatePayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleSaveTemplateServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Clone area
        event.registrar(ChannelId.AREA_CLONE.asString()).playToServer(
            nn(CloneAreaPayload.TYPE),
            nn(CloneAreaPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleCloneAreaServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Delete template
        event.registrar(ChannelId.AREA_TEMPLATE_DELETE.asString()).playToServer(
            nn(DeleteTemplatePayload.TYPE),
            nn(DeleteTemplatePayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleDeleteTemplateServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Delete area
        event.registrar(ChannelId.AREA_DELETE.asString()).playToServer(
            nn(DeleteAreaPayload.TYPE),
            nn(DeleteAreaPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleDeleteAreaServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Promote area to main hub
        event.registrar(ChannelId.AREA_PROMOTE_MAIN_HUB.asString()).playToServer(
            nn(PromoteMainHubPayload.TYPE),
            nn(PromoteMainHubPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handlePromoteMainHubServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Capture snapshot
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(CaptureSnapshotPayload.TYPE),
            nn(CaptureSnapshotPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleCaptureSnapshotServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Restore snapshot
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(RestoreSnapshotPayload.TYPE),
            nn(RestoreSnapshotPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleRestoreSnapshotServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Delete snapshot
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(DeleteSnapshotPayload.TYPE),
            nn(DeleteSnapshotPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleDeleteSnapshotServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Request snapshot list
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(RequestSnapshotListPayload.TYPE),
            nn(RequestSnapshotListPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleRequestSnapshotListServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Snapshot list response
        event.registrar(ChannelId.AREA_BUILDER_FEEDBACK.asString()).playToClient(
            nn(SnapshotListPayload.TYPE),
            nn(SnapshotListPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleSnapshotListClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Pause build
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(PauseBuildPayload.TYPE),
            nn(PauseBuildPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handlePauseBuildServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Client -> Server: Resume build
        event.registrar(ChannelId.AREA_BUILDER_CONTROL.asString()).playToServer(
            nn(ResumeBuildPayload.TYPE),
            nn(ResumeBuildPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleResumeBuildServer, PayloadValidation.PayloadLimits.SMALL)
        );

        // Server -> Client: Build status update
        event.registrar(ChannelId.AREA_BUILDER_FEEDBACK.asString()).playToClient(
            nn(BuildStatusPayload.TYPE),
            nn(BuildStatusPayload.STREAM_CODEC),
            PayloadValidation.validated(AreaNetworkHandler::handleBuildStatusClient, PayloadValidation.PayloadLimits.SMALL)
        );

        DevMod.LOGGER.debug("Registered Area Builder network handlers");
    }

    private static void enqueueWork(IPayloadContext ctx, Runnable work) {
        ctx.enqueueWork(Objects.requireNonNull(work))
            .exceptionally(e -> {
                DevMod.LOGGER.error("[AreaNetwork] Error in enqueued work: {}", e.getMessage(), e);
                return null;
            });
    }

    @Nullable
    private static AreaDefinition sanitizeDefinition(@Nonnull AreaDefinition input,
                                                     @Nullable AreaDefinition existing,
                                                     @Nonnull ServerPlayer player,
                                                     boolean isNewArea) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(player, "player");

        String trimmedName = input.name() != null ? input.name().trim() : "";
        Component nameError = AreaBuilderNaming.validateDisplayName(trimmedName);
        if (nameError != null) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("area.builder.error.invalid_name", nameError)), true);
            return null;
        }

        AreaDimensions dims = input.dimensions();
        if (dims == null ||
            dims.width() < AreaDimensions.MIN_SIZE || dims.width() > AreaDimensions.MAX_SIZE ||
            dims.length() < AreaDimensions.MIN_SIZE || dims.length() > AreaDimensions.MAX_SIZE ||
            dims.height() < AreaDimensions.MIN_HEIGHT || dims.height() > AreaDimensions.MAX_HEIGHT) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_dimensions")), true);
            return null;
        }

        if (input.palette() == null ||
            input.palette().materials() == null ||
            input.palette().materials().size() > MAX_PALETTE_MATERIALS ||
            // M-14 fix: Require minimum palette entries to prevent empty palette errors during build
            input.palette().materials().size() < AreaPalette.MIN_REQUIRED_MATERIALS) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_palette")), true);
            return null;
        }
        if (input.options() == null || input.centerPosition() == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
            return null;
        }

        // CRIT-02 fix: Validate shape and generationType before using them
        if (input.shape() == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
            return null;
        }
        if (input.generationType() == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
            return null;
        }
        // CRIT-02 fix: Validate wallStyle within options
        if (input.options().wallStyle() == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
            return null;
        }

        ResourceLocation dimensionId = input.dimensionId();
        if (dimensionId == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_dimension")), true);
            return null;
        }

        var server = player.getServer();
        if (server == null) {
            return null;
        }

        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey =
            net.minecraft.resources.ResourceKey.create(
                Objects.requireNonNull(net.minecraft.core.registries.Registries.DIMENSION), dimensionId);
        ServerLevel targetLevel = server.getLevel(Objects.requireNonNull(levelKey));
        if (targetLevel == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_dimension")), true);
            return null;
        }

        if (isNewArea && !player.level().dimension().location().equals(dimensionId)) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_dimension")), true);
            return null;
        }
        if (!isNewArea && existing != null && !existing.dimensionId().equals(dimensionId)) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_dimension")), true);
            return null;
        }

        int minY = targetLevel.getMinBuildHeight();
        int maxY = targetLevel.getMaxBuildHeight();
        int topY = dims.floorY() + dims.height();
        if (dims.floorY() < minY || topY >= maxY) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_BOUNDS)), true);
            return null;
        }

        if (input.generationType() == com.devmod.area.data.AreaGenerationType.BIOME && input.biomeConfig() == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
            return null;
        }

        AreaShape shape = input.shape();
        if (shape == AreaShape.CUSTOM_NBT) {
            // H-06 fix: Validate NBT size to prevent oversized payloads
            var customNbt = input.customShapeNbt();
            if (customNbt != null) {
                int nbtSize = customNbt.sizeInBytes();
                if (nbtSize > MAX_CUSTOM_SHAPE_NBT_SIZE) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_custom_shape_too_large")), true);
                    DevMod.LOGGER.warn("Player {} submitted custom shape with NBT size {} (max: {})",
                        player.getName().getString(), nbtSize, MAX_CUSTOM_SHAPE_NBT_SIZE);
                    return null;
                }
            }
            int count = AreaShapeGenerator.generateCustomFloor(
                Objects.requireNonNull(input.centerPosition()),
                Objects.requireNonNull(dims),
                customNbt
            ).size();
            if (count <= 0 || count > MAX_CUSTOM_SHAPE_POSITIONS) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_custom_shape")), true);
                return null;
            }

            // HIGH-02 fix: Validate that custom positions are within declared dimensions
            if (!AreaShapeGenerator.validateCustomNbtBounds(
                    Objects.requireNonNull(input.centerPosition()), dims, customNbt)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_custom_shape_out_of_bounds")), true);
                DevMod.LOGGER.warn("Player {} submitted custom shape with positions outside declared bounds",
                    player.getName().getString());
                return null;
            }
        }

        // HIGH-06 fix: Validate zone ID format before checking existence
        String linkedZoneId = input.linkedZoneId();
        if (linkedZoneId != null) {
            if (linkedZoneId.isBlank()) {
                linkedZoneId = null;
            } else if (linkedZoneId.length() > 64) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_zone_id_too_long")), true);
                return null;
            }
        }

        long now = System.currentTimeMillis();
        UUID finalId;
        UUID creatorUUID;
        long createdAt;
        int revision;
        if (isNewArea) {
            finalId = UUID.randomUUID();
            creatorUUID = player.getUUID();
            createdAt = now;
            revision = 0;
        } else {
            AreaDefinition ex = Objects.requireNonNull(existing);
            finalId = ex.id();
            creatorUUID = ex.creatorUUID();
            createdAt = ex.createdAt();
            revision = ex.revision() + 1;
        }
        var biomeConfig = input.generationType() == com.devmod.area.data.AreaGenerationType.BIOME
            ? ensureStableSeed(input.biomeConfig()) : null;
        var customShapeNbt = shape == AreaShape.CUSTOM_NBT ? input.customShapeNbt() : null;

        return new AreaDefinition(
            finalId,
            trimmedName,
            input.generationType(),
            shape,
            dims,
            input.centerPosition(),
            dimensionId,
            input.palette(),
            biomeConfig,
            input.options(),
            creatorUUID,
            createdAt,
            now,
            revision,
            input.isMainHub(),
            linkedZoneId,
            customShapeNbt
        );
    }

    /**
     * Ensures biome config has a stable seed for consistent terrain generation.
     * If seed is 0, generates one from current nanoTime at creation time.
     * This prevents multi-tick builds from using different seeds per call.
     */
    @Nullable
    private static BiomeGenerationConfig ensureStableSeed(@Nullable BiomeGenerationConfig config) {
        if (config == null) {
            return null;
        }
        if (config.seed() != 0L) {
            return config;
        }
        // Generate stable seed from current time at creation
        return config.withSeed(System.nanoTime());
    }

    /**
     * M-05 fix: Validates that an editor position is within valid world bounds.
     * Prevents invalid BlockPos from being registered which could cause issues.
     *
     * @param pos   The position to validate
     * @param level The level to check bounds against
     * @return true if position is valid
     */
    private static boolean isValidEditorPosition(@Nonnull BlockPos pos, @Nonnull net.minecraft.world.level.Level level) {
        Objects.requireNonNull(pos);
        Objects.requireNonNull(level);

        // Check horizontal bounds (Minecraft world coordinate limits)
        int x = pos.getX();
        int z = pos.getZ();
        if (x < -30_000_000 || x >= 30_000_000 || z < -30_000_000 || z >= 30_000_000) {
            DevMod.LOGGER.warn("[Area] Rejected invalid editor position: {} (out of world bounds)", pos);
            return false;
        }

        // Check vertical bounds against level limits
        int y = pos.getY();
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            DevMod.LOGGER.warn("[Area] Rejected invalid editor position: {} (Y out of bounds for level)", pos);
            return false;
        }

        return true;
    }

    // ========================================================================
    // Client Handlers (called on client thread)
    // Uses cached MethodHandles for ~10x faster invocation than reflection
    // ========================================================================

    private static void handleOpenBuilderClient(OpenAreaBuilderPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            MethodHandle method = getOpenBuilderScreenMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to open Area Builder screen", e);
            }
        });
    }

    private static void handleOpenEditorCentralClient(OpenEditorCentralPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            MethodHandle method = getOpenEditorCentralScreenMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to open Editor Central screen", e);
            }
        });
    }

    private static void handleAreaPreviewClient(AreaPreviewPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            MethodHandle method = getShowAreaPreviewMethod();
            // H-03 fix: Log warning instead of silently failing
            if (method == null) {
                DevMod.LOGGER.warn("[Area] Cannot show preview: client hook not found (ensure AreaClientHooks is initialized)");
                return;
            }
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to show area preview", e);
            }
        });
    }

    private static void handleZoneListClient(ZoneListPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            MethodHandle method = getHandleZoneListMethod();
            // H-03 fix: Log warning instead of silently failing
            if (method == null) {
                DevMod.LOGGER.warn("[Area] Cannot handle zone list: client hook not found");
                return;
            }
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to handle zone list", e);
            }
        });
    }

    private static void handleSaveAreaResultClient(SaveAreaResultPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            MethodHandle method = getHandleSaveAreaResultMethod();
            // H-03 fix: Log warning instead of silently failing
            if (method == null) {
                DevMod.LOGGER.warn("[Area] Cannot handle save result: client hook not found");
                return;
            }
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to handle save area result", e);
            }
        });
    }

    // ========================================================================
    // Server Handlers (called on server thread)
    // ========================================================================

    private static void handleSaveAreaServer(SaveAreaPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions (OP level 2 required for creating/editing areas)
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                DevMod.LOGGER.warn("Player {} tried to save area without permissions",
                    player.getName().getString());
                return;
            }

            AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));

            // H-11 fix: Better null validation with user-friendly error message
            AreaDefinition input = payload.definition();
            if (input == null) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
                DevMod.LOGGER.warn("Player {} sent save area request with null definition",
                    player.getName().getString());
                return;
            }
            UUID requestId = input.id();

            if (payload.isNewArea()) {
                AreaDefinition definition = sanitizeDefinition(input, null, player, true);
                if (definition == null) {
                    return;
                }

                // Validate linkedZoneId if specified
                String linkedZoneId = definition.linkedZoneId();
                if (linkedZoneId != null && !linkedZoneId.isEmpty()) {
                    ZoneRegistry zoneRegistry = ZoneRegistry.get(Objects.requireNonNull(player.getServer()));
                    if (!zoneRegistry.hasZoneId(linkedZoneId)) {
                        player.displayClientMessage(
                            Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_zone_not_found", linkedZoneId)), true);
                        DevMod.LOGGER.warn("Player {} tried to link area {} to non-existent zone {}",
                            player.getName().getString(), definition.name(), linkedZoneId);
                        return;
                    }
                }

                // MED-11 fix: Check for duplicate area names (same creator only)
                String areaName = definition.name();
                UUID creatorId = player.getUUID();
                boolean nameExists = registry.getAllAreas().stream()
                    .anyMatch(a -> areaName.equalsIgnoreCase(a.name()) &&
                                   creatorId.equals(a.creatorUUID()));
                if (nameExists) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_name_exists", areaName)), true);
                    DevMod.LOGGER.warn("Player {} tried to create area with duplicate name: {}",
                        player.getName().getString(), areaName);
                    return;
                }

                // Check for overlap with existing areas
                if (registry.wouldOverlap(definition)) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_OVERLAP)), true);
                    DevMod.LOGGER.warn("Player {} tried to create overlapping area {}",
                        player.getName().getString(), definition.name());
                    // H-05 fix: Log audit on failures, not just successes
                    AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                        AreaAuditLog.ActionType.CREATE,
                        Objects.requireNonNull(definition.id()),
                        Objects.requireNonNull(definition.name()),
                        player,
                        "FAILED: overlap"
                    );
                    return;
                }

                // Creating new area
                UUID areaId = registry.createArea(definition);
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.successTranslatable("area.message.area_created", definition.name())), true);
                DevMod.LOGGER.info("Player {} created area: {} ({})",
                    player.getName().getString(), definition.name(), areaId);

                // Audit log
                AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                    AreaAuditLog.ActionType.CREATE,
                    areaId,
                    Objects.requireNonNull(definition.name()),
                    player,
                    "size=" + definition.dimensions().width() + "x" + definition.dimensions().length() + "x" + definition.dimensions().height()
                );

                // HIGH-07 fix: Wrap editor registration in try-catch to prevent failures from affecting area creation
                BlockPos editorPos = payload.editorPosition();
                if (editorPos != null && isValidEditorPosition(editorPos, Objects.requireNonNull(player.level()))) {
                    try {
                        ResourceLocation editorDimension = Objects.requireNonNull(player.level().dimension().location());
                        registry.registerEditor(editorDimension, editorPos, areaId);
                        if (player.level().getBlockEntity(editorPos) instanceof com.devmod.area.block.entity.AreaEditorBlockEntity editorBE) {
                            editorBE.setAreaId(areaId);
                        }
                    } catch (Exception e) {
                        // Log but don't fail - area was created successfully
                        DevMod.LOGGER.warn("[Area] Failed to link editor position for new area {}: {}",
                            areaId, e.getMessage());
                    }
                }

                sendPacket(player, new SaveAreaResultPayload(requestId, areaId, definition.revision(), true));
            } else {
                // Updating existing area
                UUID existingId = payload.existingAreaId();
                if (existingId == null) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_area_id")), true);
                    return;
                }

                // Validate area exists
                Optional<AreaDefinition> existing = registry.getArea(existingId);
                if (existing.isEmpty()) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                    DevMod.LOGGER.warn("Player {} tried to update non-existent area {}",
                        player.getName().getString(), existingId);
                    return;
                }

                AreaDefinition existingDef = existing.get();
                if (!Objects.requireNonNull(existingDef.id()).equals(requestId)) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_area_id")), true);
                    return;
                }

                // Validate ownership (creator or admin)
                UUID creatorUUID = existingDef.creatorUUID();
                if (creatorUUID != null &&
                    !creatorUUID.equals(player.getUUID()) &&
                    !player.hasPermissions(4)) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_ownership")), true);
                    DevMod.LOGGER.warn("Player {} tried to update area {} without ownership",
                        player.getName().getString(), existingId);
                    return;
                }

                AreaDefinition definition = sanitizeDefinition(input, existingDef, player, false);
                if (definition == null) {
                    return;
                }

                // Validate linkedZoneId if specified
                String linkedZoneId = definition.linkedZoneId();
                if (linkedZoneId != null && !linkedZoneId.isEmpty()) {
                    ZoneRegistry zoneRegistry = ZoneRegistry.get(Objects.requireNonNull(player.getServer()));
                    if (!zoneRegistry.hasZoneId(linkedZoneId)) {
                        player.displayClientMessage(
                            Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_zone_not_found", linkedZoneId)), true);
                        DevMod.LOGGER.warn("Player {} tried to link area {} to non-existent zone {}",
                            player.getName().getString(), definition.name(), linkedZoneId);
                        return;
                    }
                }

                // Check for overlap with existing areas
                if (registry.wouldOverlap(definition)) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_OVERLAP)), true);
                    DevMod.LOGGER.warn("Player {} tried to update overlapping area {}",
                        player.getName().getString(), definition.name());
                    // H-05 fix: Log audit on failures
                    AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                        AreaAuditLog.ActionType.UPDATE,
                        existingId,
                        Objects.requireNonNull(definition.name()),
                        player,
                        "FAILED: overlap"
                    );
                    return;
                }

                boolean updated = registry.updateArea(existingId, Objects.requireNonNull(definition), payload.expectedRevision());
                if (updated) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.successTranslatable("area.message.area_updated", definition.name())), true);
                    DevMod.LOGGER.info("Player {} updated area: {} ({})",
                        player.getName().getString(), definition.name(), existingId);

                    // Audit log
                    AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                        AreaAuditLog.ActionType.UPDATE,
                        existingId,
                        Objects.requireNonNull(definition.name()),
                        player,
                        "revision=" + definition.revision()
                    );

                    // HIGH-07 fix: Wrap editor registration in try-catch
                    BlockPos editorPos = payload.editorPosition();
                    if (editorPos != null) {
                        try {
                            ResourceLocation editorDimension = Objects.requireNonNull(player.level().dimension().location());
                            registry.registerEditor(editorDimension, editorPos, existingId);
                            if (player.level().getBlockEntity(editorPos) instanceof com.devmod.area.block.entity.AreaEditorBlockEntity editorBE) {
                                editorBE.setAreaId(existingId);
                            }
                        } catch (Exception e) {
                            DevMod.LOGGER.warn("[Area] Failed to link editor position for updated area {}: {}",
                                existingId, e.getMessage());
                        }
                    }
                    sendPacket(player, new SaveAreaResultPayload(requestId, existingId, definition.revision(), false));
                } else {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_revision_mismatch")), true);
                    DevMod.LOGGER.warn("Failed to update area {} - revision mismatch", existingId);
                }
            }
        });
    }

    private static void handleBuildAreaServer(BuildAreaPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                DevMod.LOGGER.warn("Player {} tried to build area without permissions",
                    player.getName().getString());
                return;
            }

            // HIGH-05 fix: Use atomic compute for cooldowns to prevent TOCTOU race
            UUID playerId = player.getUUID();
            long now = System.currentTimeMillis();

            // Check player cooldown
            long playerCooldownRemaining = CooldownManager.checkAndUpdatePlayerBuildCooldown(playerId, now);
            if (playerCooldownRemaining > 0) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.message.error_build_cooldown", playerCooldownRemaining)), true);
                return;
            }

            // Check area cooldown
            UUID areaId = Objects.requireNonNull(payload.areaId());
            long areaCooldownRemaining = CooldownManager.checkAndUpdateAreaBuildCooldown(areaId, now);
            if (areaCooldownRemaining > 0) {
                // Rollback player cooldown since area cooldown failed
                CooldownManager.rollbackPlayerBuildCooldown(playerId, now);
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.message.error_area_cooldown", areaCooldownRemaining)), true);
                return;
            }

            AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));
            Optional<AreaDefinition> areaOpt = registry.getArea(areaId);

            if (areaOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                DevMod.LOGGER.warn("Player {} tried to build non-existent area {}",
                    player.getName().getString(), payload.areaId());
                return;
            }

            AreaDefinition definition = areaOpt.get();
            ServerLevel level = player.serverLevel();

            // Validate we're in the correct dimension
            if (!definition.dimensionId().equals(level.dimension().location())) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_wrong_dimension")), true);
                DevMod.LOGGER.warn("Player {} tried to build area {} in wrong dimension",
                    player.getName().getString(), payload.areaId());
                return;
            }

            // Check if already building this area
            if (AreaBuildTaskManager.INSTANCE.isBuildingArea(payload.areaId())) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_build_in_progress")), true);
                DevMod.LOGGER.warn("Player {} tried to build area {} which is already building",
                    player.getName().getString(), payload.areaId());
                return;
            }

            // Use AreaBuildTaskManager - handles both immediate and multi-tick builds
            AreaBuildTaskManager.BuildStartResult result = AreaBuildTaskManager.INSTANCE.startBuild(
                level, definition, player, payload.clearFirst(), payload.useMultiTick());

            // LOW-02 fix: Send build status update to client UI based on result
            switch (result) {
                case STARTED_IMMEDIATE -> {
                    // Immediate builds complete synchronously, send COMPLETED
                    sendPacket(player, BuildStatusPayload.completed(areaId));
                }
                case STARTED_MULTI_TICK -> {
                    // Multi-tick build started, send STARTED status
                    sendPacket(player, BuildStatusPayload.started(areaId));
                }
                case QUEUED -> {
                    // Build queued, send QUEUED status with position
                    int position = AreaBuildTaskManager.INSTANCE.getQueuePosition(areaId) + 1;
                    sendPacket(player, BuildStatusPayload.queued(areaId, position));
                }
                case ALREADY_BUILDING, QUEUE_FULL -> {
                    // Error cases - send FAILED status
                    sendPacket(player, BuildStatusPayload.failed(areaId));
                }
            }

            // Cooldowns already updated atomically in the compute() calls above (HIGH-05 fix)

            // Only show success message and log audit for actual starts
            if (result == AreaBuildTaskManager.BuildStartResult.STARTED_IMMEDIATE ||
                result == AreaBuildTaskManager.BuildStartResult.STARTED_MULTI_TICK) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.successTranslatable("area.message.build_started", definition.name())), true);
                DevMod.LOGGER.info("Player {} initiated build for area {}",
                    player.getName().getString(), definition.name());

                // Audit log
                AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                    AreaAuditLog.ActionType.BUILD_START,
                    areaId,
                    Objects.requireNonNull(definition.name()),
                    player,
                    payload.clearFirst() ? "clearFirst=true" : null
                );
            }
        });
    }

    private static void handleOpenBuilderRequestServer(RequestOpenAreaBuilderPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.editor_central.error.permission")), true);
                return;
            }

            AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));
            UUID requestedId = payload.areaId();

            if (requestedId == null) {
                openBuilderScreen(player, null, null, false);
                return;
            }

            Optional<AreaDefinition> areaOpt = registry.getArea(requestedId);
            if (areaOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.editor_central.error.not_found")), true);
                return;
            }

            AreaDefinition area = areaOpt.get();
            UUID areaCreatorUUID = area.creatorUUID();
            if (areaCreatorUUID != null &&
                !areaCreatorUUID.equals(player.getUUID()) &&
                !player.hasPermissions(4)) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.editor_central.error.permission")), true);
                return;
            }

            openBuilderScreen(player, area, null, area.isMainHub());
        });
    }

    private static void handleRequestZoneListServer(RequestZoneListPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            ZoneRegistry zoneRegistry = ZoneRegistry.get(Objects.requireNonNull(player.getServer()));
            List<ZoneListPayload.ZoneSummary> summaries = new ArrayList<>();

            for (ZoneDefinition zone : zoneRegistry.getAllZones()) {
                int areaCount = zoneRegistry.getAreasForZone(zone.zoneId()).size();
                summaries.add(new ZoneListPayload.ZoneSummary(
                    Objects.requireNonNull(zone.zoneId()),
                    Objects.requireNonNull(zone.displayName()),
                    areaCount
                ));
            }

            sendPacket(player, new ZoneListPayload(summaries));
            DevMod.LOGGER.debug("[Area] Sent zone list to {} ({} zones)",
                player.getName().getString(), summaries.size());
        });
    }

    private static void handleRequestTemplateListServer(RequestTemplateListPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            AreaTemplateRegistry registry = AreaTemplateRegistry.get(Objects.requireNonNull(player.getServer()));
            List<TemplateListPayload.TemplateSummary> summaries = new ArrayList<>();

            for (AreaTemplate template : registry.getAllTemplates()) {
                summaries.add(new TemplateListPayload.TemplateSummary(
                    Objects.requireNonNull(template.id()),
                    Objects.requireNonNull(template.name()),
                    Objects.requireNonNull(template.description()),
                    Objects.requireNonNull(template.author()),
                    Objects.requireNonNull(template.shape()),
                    template.createdAt()
                ));
            }

            sendPacket(player, new TemplateListPayload(summaries));
            DevMod.LOGGER.debug("[Area] Sent template list to {} ({} templates)",
                player.getName().getString(), summaries.size());
        });
    }

    private static void handleTemplateListClient(TemplateListPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            MethodHandle method = getHandleTemplateListMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to handle template list", e);
            }
        });
    }

    private static void handleTemplateDataClient(TemplateDataPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            MethodHandle method = getHandleTemplateDataMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to handle template data", e);
            }
        });
    }

    private static void handleLoadTemplateServer(LoadTemplatePayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            AreaTemplateRegistry registry = AreaTemplateRegistry.get(
                Objects.requireNonNull(player.getServer()));

            registry.getTemplate(Objects.requireNonNull(payload.templateId())).ifPresentOrElse(
                template -> {
                    String presetId = template.palette().presetId() != null
                        ? template.palette().presetId() : "default";

                    TemplateDataPayload response = new TemplateDataPayload(
                        template.id(),
                        template.generationType(),
                        template.shape(),
                        template.dimensions(),
                        template.palette(),
                        template.biomeConfig(),
                        template.options(),
                        presetId
                    );
                    sendPacket(player, response);
                    DevMod.LOGGER.debug("[Area] Sent template data for {} to {}",
                        template.name(), player.getName().getString());
                },
                () -> player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(
                        "area.message.error_template_not_found")), true)
            );
        });
    }

    private static void handleSaveTemplateServer(SaveAreaTemplatePayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            // SEC-03 fix: Rate limiting for template saves
            UUID playerId = player.getUUID();
            long now = System.currentTimeMillis();
            long remainingSeconds = CooldownManager.getTemplateSaveCooldownRemaining(playerId, now);
            if (remainingSeconds > 0) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.message.error_template_cooldown", remainingSeconds)), true);
                return;
            }
            CooldownManager.updateTemplateSaveCooldown(playerId, now);

            // Validate payload
            if (!payload.isValid()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_template")), true);
                return;
            }

            AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));
            AreaTemplateRegistry templateRegistry = AreaTemplateRegistry.get(Objects.requireNonNull(player.getServer()));

            // Extract validated values (isValid() ensures these are non-null)
            UUID sourceAreaId = Objects.requireNonNull(payload.sourceAreaId(), "sourceAreaId validated by isValid()");
            String templateName = Objects.requireNonNull(payload.templateName(), "templateName validated by isValid()");
            String description = Objects.requireNonNull(payload.description(), "description validated by isValid()");
            String authorName = Objects.requireNonNull(player.getName().getString(), "player name");

            // Get source area
            Optional<AreaDefinition> sourceOpt = areaRegistry.getArea(sourceAreaId);
            if (sourceOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                return;
            }

            // Check registry capacity
            if (!templateRegistry.hasRoom()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_template_registry_full")), true);
                return;
            }

            AreaDefinition sourceArea = Objects.requireNonNull(sourceOpt.get());

            // Create and save template
            AreaTemplate template = AreaTemplate.fromDefinition(
                Objects.requireNonNull(sourceArea),
                templateName,
                description,
                authorName
            );

            Optional<UUID> savedId = templateRegistry.saveTemplate(template);
            if (savedId.isPresent()) {
                UUID templateId = Objects.requireNonNull(savedId.get());
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.successTranslatable("area.message.template_saved", templateName)), true);
                DevMod.LOGGER.info("Player {} saved template: {} ({})",
                    authorName, templateName, templateId);

                // Audit log
                AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                    AreaAuditLog.ActionType.TEMPLATE_SAVE,
                    templateId,
                    templateName,
                    player,
                    "sourceArea=" + sourceAreaId
                );
            } else {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_template_save_failed")), true);
            }
        });
    }

    private static void handleCloneAreaServer(CloneAreaPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            // Validate payload
            if (!payload.isValid()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_clone")), true);
                return;
            }

            AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));

            // Extract validated values (isValid() ensures these are non-null)
            UUID sourceAreaId = Objects.requireNonNull(payload.sourceAreaId(), "sourceAreaId validated by isValid()");
            String newName = Objects.requireNonNull(payload.newName(), "newName validated by isValid()");
            BlockPos newCenter = Objects.requireNonNull(payload.newCenter(), "newCenter validated by isValid()");

            // Get source area
            Optional<AreaDefinition> sourceOpt = registry.getArea(sourceAreaId);
            if (sourceOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                return;
            }

            AreaDefinition source = sourceOpt.get();

            // Create cloned definition with new ID, name, and center
            long now = System.currentTimeMillis();
            AreaDefinition clonedRaw = new AreaDefinition(
                UUID.randomUUID(),
                newName,
                source.generationType(),
                source.shape(),
                source.dimensions(),
                newCenter,
                source.dimensionId(),
                source.palette(),
                source.biomeConfig(),
                source.options(),
                player.getUUID(),
                now,
                now,
                0, // Initial revision
                false, // Clones are not main hub by default
                payload.keepLinkedZone() ? source.linkedZoneId() : null,
                source.customShapeNbt()
            );

            // LOW-01 fix: Sanitize cloned definition like any new area
            // This validates CUSTOM_NBT bounds and other constraints
            AreaDefinition cloned = sanitizeDefinition(clonedRaw, null, player, true);
            if (cloned == null) {
                // sanitizeDefinition already sent error message to player
                return;
            }

            // Check for overlap
            if (registry.wouldOverlap(cloned)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_OVERLAP)), true);
                return;
            }

            // Save cloned area
            UUID newId = registry.createArea(cloned);
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.successTranslatable("area.message.area_cloned", source.name(), newName)), true);
            DevMod.LOGGER.info("Player {} cloned area {} -> {} ({})",
                player.getName().getString(), source.name(), newName, newId);

            // Audit log
            AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                AreaAuditLog.ActionType.CLONE,
                newId,
                newName,
                player,
                "clonedFrom=" + sourceAreaId
            );
        });
    }

    private static void handleDeleteTemplateServer(DeleteTemplatePayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions (OP level 2+ required)
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            UUID templateId = payload.templateId();
            if (templateId == null) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_template_not_found")), true);
                return;
            }

            AreaTemplateRegistry registry = AreaTemplateRegistry.get(Objects.requireNonNull(player.getServer()));

            // Check if template exists and get its info for logging
            Optional<AreaTemplate> templateOpt = registry.getTemplate(templateId);
            if (templateOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_template_not_found")), true);
                return;
            }

            AreaTemplate template = templateOpt.get();

            // SEC-02 fix: Check ownership - only template author or OP4+ can delete
            String templateAuthor = template.author();
            String playerName = player.getName().getString();
            if (templateAuthor != null && !templateAuthor.equalsIgnoreCase(playerName) && !player.hasPermissions(4)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_not_author")), true);
                DevMod.LOGGER.debug("[Template] Delete denied for {} - not author of template {} (author: {})",
                    playerName, template.name(), templateAuthor);
                return;
            }

            if (registry.deleteTemplate(templateId)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.successTranslatable("area.message.template_deleted", template.name())), true);
                DevMod.LOGGER.info("Player {} deleted template: {} ({})",
                    player.getName().getString(), template.name(), templateId);

                // Audit log
                AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                    AreaAuditLog.ActionType.TEMPLATE_DELETE,
                    templateId,
                    Objects.requireNonNull(template.name()),
                    player,
                    null
                );
            } else {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_template_delete_failed")), true);
            }
        });
    }

    private static void handleDeleteAreaServer(DeleteAreaPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions (OP level 2+ required)
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            UUID areaId = payload.areaId();
            if (areaId == null) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                return;
            }

            AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));

            // Check if area exists
            Optional<AreaDefinition> areaOpt = registry.getArea(areaId);
            if (areaOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                return;
            }

            AreaDefinition area = areaOpt.get();

            // CRIT-03 fix: Check ownership - only owner or OP4+ can delete others' areas
            // This makes delete permissions consistent with update permissions
            UUID playerId = player.getUUID();
            if (!Objects.equals(area.creatorUUID(), playerId) && !player.hasPermissions(4)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_not_owner")), true);
                DevMod.LOGGER.warn("Player {} tried to delete area {} owned by {} without OP4+",
                    player.getName().getString(), areaId, area.creatorUUID());
                return;
            }

            // Prevent deletion of main hub without explicit confirmation
            UUID mainHubId = registry.getMainHubId();
            if (areaId.equals(mainHubId)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_cannot_delete_main_hub")), true);
                return;
            }

            String areaName = area.name();
            registry.deleteArea(areaId);

            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.successTranslatable("area.message.area_deleted", areaName)), true);
            DevMod.LOGGER.info("Player {} deleted area: {} ({})",
                player.getName().getString(), areaName, areaId);

            // Audit log
            AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                AreaAuditLog.ActionType.DELETE,
                areaId,
                Objects.requireNonNull(areaName),
                player,
                null
            );
        });
    }

    private static void handlePromoteMainHubServer(PromoteMainHubPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions (OP level 2+ required)
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            UUID areaId = payload.areaId();
            if (areaId == null) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                return;
            }

            AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));

            // Check if area exists
            Optional<AreaDefinition> areaOpt = registry.getArea(areaId);
            if (areaOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                return;
            }

            AreaDefinition newMainHub = areaOpt.get();

            // Check if already main hub
            UUID currentMainHubId = registry.getMainHubId();
            if (areaId.equals(currentMainHubId)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_already_main_hub")), true);
                return;
            }

            // C-07 fix: Promote new area FIRST, then demote old one to ensure atomicity
            // This prevents leaving no main hub if the demotion succeeds but promotion fails
            AreaDefinition promoted = newMainHub.withMainHub(true);
            boolean updated = registry.updateArea(areaId, promoted, newMainHub.revision());
            if (!updated) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_revision_mismatch")), true);
                DevMod.LOGGER.warn("[Area] Failed to promote area {} to main hub: revision mismatch", areaId);
                return;
            }

            // Now demote the old main hub (safe because new one is already promoted)
            if (currentMainHubId != null) {
                Optional<AreaDefinition> oldMainHubOpt = registry.getArea(currentMainHubId);
                if (oldMainHubOpt.isPresent()) {
                    AreaDefinition oldMainHub = oldMainHubOpt.get();
                    AreaDefinition demoted = oldMainHub.withMainHub(false);
                    // Demotion failure is not critical since we already have a new main hub
                    if (!registry.updateArea(currentMainHubId, demoted, oldMainHub.revision())) {
                        DevMod.LOGGER.warn("[Area] Failed to demote old main hub {} (non-critical)", currentMainHubId);
                    } else {
                        DevMod.LOGGER.debug("[Area] Demoted previous main hub: {} ({})",
                            oldMainHub.name(), currentMainHubId);
                    }
                }
            }

            registry.setMainHub(areaId);

            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.successTranslatable("area.message.area_promoted", newMainHub.name())), true);
            DevMod.LOGGER.info("Player {} promoted area to main hub: {} ({})",
                player.getName().getString(), newMainHub.name(), areaId);

            // Audit log
            AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                AreaAuditLog.ActionType.SET_MAIN_HUB,
                areaId,
                Objects.requireNonNull(newMainHub.name()),
                player,
                currentMainHubId != null ? "previousMainHub=" + currentMainHubId : null
            );
        });
    }

    // ========================================================================
    // Snapshot Handlers
    // ========================================================================

    private static void handleCaptureSnapshotServer(CaptureSnapshotPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions (OP level 2+ required)
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            UUID areaId = payload.areaId();
            if (areaId == null) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                return;
            }

            AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));

            // Check if area exists
            Optional<AreaDefinition> areaOpt = registry.getArea(areaId);
            if (areaOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                return;
            }

            AreaDefinition area = areaOpt.get();
            ServerLevel level = player.serverLevel();

            // Validate we're in the correct dimension
            if (!area.dimensionId().equals(level.dimension().location())) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_wrong_dimension")), true);
                return;
            }

            // Start capture
            String description = payload.description() != null ? payload.description() : "";
            UUID snapshotId = AreaSnapshotManager.INSTANCE.startCapture(level, area, Objects.requireNonNull(description), player);

            if (snapshotId != null) {
                // Audit log
                AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                    AreaAuditLog.ActionType.SNAPSHOT_CAPTURE,
                    areaId,
                    Objects.requireNonNull(area.name()),
                    player,
                    "snapshotId=" + snapshotId
                );
            }
        });
    }

    private static void handleRestoreSnapshotServer(RestoreSnapshotPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions (OP level 2+ required)
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            UUID snapshotId = payload.snapshotId();
            if (snapshotId == null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.not_found")), true);
                return;
            }

            ServerLevel level = player.serverLevel();

            // Get snapshot to verify dimension
            AreaSnapshotRegistry snapshotRegistry = AreaSnapshotRegistry.get(Objects.requireNonNull(player.getServer()));
            Optional<AreaSnapshot> snapshotOpt = snapshotRegistry.getSnapshot(snapshotId);
            if (snapshotOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.not_found")), true);
                return;
            }

            AreaSnapshot snapshot = snapshotOpt.get();

            // H-04 fix: Verify player has access to the area the snapshot belongs to
            AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));
            Optional<AreaDefinition> areaOpt = areaRegistry.getArea(Objects.requireNonNull(snapshot.areaId()));
            if (areaOpt.isPresent()) {
                AreaDefinition area = areaOpt.get();
                UUID creatorUUID = area.creatorUUID();
                // Must be creator or admin (OP level 4) to restore snapshots
                if (creatorUUID != null &&
                    !creatorUUID.equals(player.getUUID()) &&
                    !player.hasPermissions(4)) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_ownership")), true);
                    DevMod.LOGGER.warn("Player {} tried to restore snapshot for area {} without ownership",
                        player.getName().getString(), snapshot.areaId());
                    return;
                }
            }

            // Start restore
            boolean started = AreaSnapshotManager.INSTANCE.startRestore(Objects.requireNonNull(level), snapshotId, player);

            if (started) {
                // Audit log
                AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                    AreaAuditLog.ActionType.SNAPSHOT_RESTORE,
                    Objects.requireNonNull(snapshot.areaId()),
                    Objects.requireNonNull(snapshot.areaName()),
                    player,
                    "snapshotId=" + snapshotId
                );
            }
        });
    }

    private static void handleDeleteSnapshotServer(DeleteSnapshotPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions (OP level 2+ required)
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            UUID snapshotId = payload.snapshotId();
            if (snapshotId == null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.not_found")), true);
                return;
            }

            AreaSnapshotRegistry registry = AreaSnapshotRegistry.get(Objects.requireNonNull(player.getServer()));

            // Get snapshot info for logging before deletion
            Optional<AreaSnapshot> snapshotOpt = registry.getSnapshot(snapshotId);
            if (snapshotOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.not_found")), true);
                return;
            }

            AreaSnapshot snapshot = snapshotOpt.get();

            // SEC-04 fix: Verify player has access to delete the snapshot (owner or OP4)
            AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));
            Optional<AreaDefinition> areaOpt = areaRegistry.getArea(Objects.requireNonNull(snapshot.areaId()));
            if (areaOpt.isPresent()) {
                AreaDefinition area = areaOpt.get();
                UUID creatorUUID = area.creatorUUID();
                // Must be creator or admin (OP level 4) to delete snapshots
                if (creatorUUID != null &&
                    !creatorUUID.equals(player.getUUID()) &&
                    !player.hasPermissions(4)) {
                    player.displayClientMessage(
                        Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_ownership")), true);
                    DevMod.LOGGER.warn("Player {} tried to delete snapshot for area {} without ownership",
                        player.getName().getString(), snapshot.areaId());
                    return;
                }
            }

            // Delete snapshot
            java.nio.file.Path worldFolder = Objects.requireNonNull(player.getServer())
                .getWorldPath(Objects.requireNonNull(net.minecraft.world.level.storage.LevelResource.ROOT));
            boolean deleted = registry.deleteSnapshot(snapshotId, worldFolder);

            if (deleted) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.deleted")), false);
                DevMod.LOGGER.info("Player {} deleted snapshot {} for area {}",
                    player.getName().getString(), snapshotId, snapshot.areaName());

                // Audit log
                AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                    AreaAuditLog.ActionType.SNAPSHOT_DELETE,
                    Objects.requireNonNull(snapshot.areaId()),
                    Objects.requireNonNull(snapshot.areaName()),
                    player,
                    "snapshotId=" + snapshotId
                );
            } else {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.snapshot.delete_failed")), true);
            }
        });
    }

    private static void handleRequestSnapshotListServer(RequestSnapshotListPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            UUID areaId = payload.areaId();
            if (areaId == null) {
                return;
            }

            // SEC-01 fix: Permission check - only owner or OP4 can view snapshots for an area
            AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));
            Optional<AreaDefinition> areaOpt = areaRegistry.getArea(areaId);
            if (areaOpt.isPresent()) {
                AreaDefinition area = areaOpt.get();
                UUID creatorUUID = area.creatorUUID();
                if (creatorUUID != null && !creatorUUID.equals(player.getUUID()) && !player.hasPermissions(4)) {
                    DevMod.LOGGER.debug("[Snapshot] Denied snapshot list request from {} for area {} (not owner/admin)",
                        player.getName().getString(), areaId);
                    return; // Silent denial - no info disclosure
                }
            }

            AreaSnapshotRegistry registry = AreaSnapshotRegistry.get(Objects.requireNonNull(player.getServer()));
            List<AreaSnapshot> snapshots = registry.getSnapshotsForArea(areaId);

            // Convert to summaries
            List<SnapshotListPayload.SnapshotSummary> summaries = new ArrayList<>();
            for (AreaSnapshot snapshot : snapshots) {
                summaries.add(new SnapshotListPayload.SnapshotSummary(
                    snapshot.id(),
                    snapshot.description(),
                    snapshot.creatorName(),
                    snapshot.createdAt(),
                    snapshot.blockCount(),
                    snapshot.fileSizeBytes()
                ));
            }

            sendPacket(player, new SnapshotListPayload(areaId, summaries));
            DevMod.LOGGER.debug("[Snapshot] Sent snapshot list to {} ({} snapshots for area {})",
                player.getName().getString(), summaries.size(), areaId);
        });
    }

    private static void handleSnapshotListClient(SnapshotListPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            MethodHandle method = getHandleSnapshotListMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to handle snapshot list", e);
            }
        });
    }

    // ========================================================================
    // Build Pause/Resume Handlers
    // ========================================================================

    private static void handlePauseBuildServer(PauseBuildPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions (OP level 2+ required)
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            UUID areaId = payload.areaId();
            var server = player.getServer();
            if (server == null) return;

            // Check if build is active for this area
            if (!AreaBuildTaskManager.INSTANCE.isBuildingArea(areaId)) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.message.no_active_build")), true);
                return;
            }

            // Pause the build
            boolean paused = AreaBuildTaskManager.INSTANCE.pauseBuild(Objects.requireNonNull(areaId), server);

            if (paused) {
                int progress = AreaBuildTaskManager.INSTANCE.getBuildProgress(areaId);
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.message.build_paused")), false);

                // Notify client about status change
                sendPacket(player, BuildStatusPayload.paused(areaId, progress));

                // Audit log
                AreaRegistry registry = AreaRegistry.get(server);
                registry.getArea(Objects.requireNonNull(areaId)).ifPresent(area ->
                    AreaAuditLog.get(server).log(
                        AreaAuditLog.ActionType.BUILD_CANCEL,
                        Objects.requireNonNull(areaId), Objects.requireNonNull(area.name()), player, "paused at " + progress + "%"
                    )
                );

                DevMod.LOGGER.info("Player {} paused build for area {}", player.getName().getString(), areaId);
            } else {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.message.pause_failed")), true);
            }
        });
    }

    private static void handleResumeBuildServer(ResumeBuildPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions (OP level 2+ required)
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            UUID areaId = payload.areaId();
            var server = player.getServer();
            if (server == null) return;

            // Resume the build
            boolean resumed = AreaBuildTaskManager.INSTANCE.resumeBuild(server, Objects.requireNonNull(areaId), player);

            if (resumed) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.message.build_resumed")), false);

                // Notify client about status change
                sendPacket(player, BuildStatusPayload.resumed(areaId, 0));

                // Audit log
                AreaRegistry registry = AreaRegistry.get(server);
                registry.getArea(Objects.requireNonNull(areaId)).ifPresent(area ->
                    AreaAuditLog.get(server).log(
                        AreaAuditLog.ActionType.BUILD_START,
                        Objects.requireNonNull(areaId), Objects.requireNonNull(area.name()), player, "resumed"
                    )
                );

                DevMod.LOGGER.info("Player {} resumed build for area {}", player.getName().getString(), areaId);
            } else {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.message.resume_failed")), true);
            }
        });
    }

    private static void handleBuildStatusClient(BuildStatusPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            // CRIT-07 fix: Invoke client hook via MethodHandle like other handlers
            MethodHandle method = getHandleBuildStatusMethod();
            if (method != null) {
                try {
                    method.invoke(payload);
                } catch (Throwable e) {
                    DevMod.LOGGER.error("[Area] Failed to invoke handleBuildStatus: {}", e.getMessage());
                }
            } else {
                // Fallback logging for dedicated server
                DevMod.LOGGER.debug("[Area] Build status update: area={}, status={}, progress={}%",
                    payload.areaId(), payload.status().getSerializedName(), payload.progressPercent());
            }
        });
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
