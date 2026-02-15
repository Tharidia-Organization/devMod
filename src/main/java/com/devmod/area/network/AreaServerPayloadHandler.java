package com.devmod.area.network;

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

import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;
import com.devmod.area.aesthetic.AreaBuilderMessages;
import com.devmod.area.builder.AreaBuildTaskManager;
import com.devmod.area.data.AreaAuditLog;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaRegistry;
import com.devmod.area.snapshot.AreaSnapshotManager;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZoneRegistry;

/**
 * Handles server-side area network payloads.
 * Extracted from AreaNetworkHandler for better modularity.
 */
final class AreaServerPayloadHandler {

    private AreaServerPayloadHandler() {}

    // ========================================================================
    // Server Handlers (called on server thread)
    // ========================================================================

    static void handleSaveAreaServer(SaveAreaPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            AreaDefinition input = payload.definition();
            UUID requestId = input != null ? input.id() : null;

            // Validate permissions (OP level 2 required for creating/editing areas)
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                DevMod.LOGGER.warn("Player {} tried to save area without permissions",
                    player.getName().getString());
                sendSaveFailure(player, requestId, payload.existingAreaId(), payload.isNewArea());
                return;
            }

            AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));

            // H-11 fix: Better null validation with user-friendly error message
            if (input == null) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_definition")), true);
                DevMod.LOGGER.warn("Player {} sent save area request with null definition",
                    player.getName().getString());
                sendSaveFailure(player, requestId, payload.existingAreaId(), payload.isNewArea());
                return;
            }

            if (payload.isNewArea()) {
                handleCreateNewArea(payload, player, registry, input, requestId);
            } else {
                handleUpdateExistingArea(payload, player, registry, input, requestId);
            }
        });
    }

    private static void handleCreateNewArea(SaveAreaPayload payload, ServerPlayer player,
                                             AreaRegistry registry, AreaDefinition input, @Nullable UUID requestId) {
        AreaDefinition definition = AreaDefinitionValidator.sanitizeDefinition(input, null, player, true);
        if (definition == null) {
            sendSaveFailure(player, requestId, null, true);
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
                sendSaveFailure(player, requestId, null, true);
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
            sendSaveFailure(player, requestId, null, true);
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
            sendSaveFailure(player, requestId, null, true);
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
        if (editorPos != null && AreaDefinitionValidator.isValidEditorPosition(editorPos, Objects.requireNonNull(player.level()))) {
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

        // Use areaId as fallback if requestId is null (shouldn't happen in normal flow)
        AreaNetworkHandler.sendPacket(player, new SaveAreaResultPayload(requestId != null ? requestId : areaId, areaId, definition.revision(), true, true));
    }

    private static void handleUpdateExistingArea(SaveAreaPayload payload, ServerPlayer player,
                                                  AreaRegistry registry, AreaDefinition input, @Nullable UUID requestId) {
        // Updating existing area
        UUID existingId = payload.existingAreaId();
        if (existingId == null) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_area_id")), true);
            sendSaveFailure(player, requestId, null, false);
            return;
        }

        // Validate area exists
        Optional<AreaDefinition> existing = registry.getArea(existingId);
        if (existing.isEmpty()) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
            DevMod.LOGGER.warn("Player {} tried to update non-existent area {}",
                player.getName().getString(), existingId);
            sendSaveFailure(player, requestId, existingId, false);
            return;
        }

        AreaDefinition existingDef = existing.get();
        if (!Objects.requireNonNull(existingDef.id()).equals(requestId)) {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_area_id")), true);
            sendSaveFailure(player, requestId, existingId, false);
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
            sendSaveFailure(player, requestId, existingId, false);
            return;
        }

        AreaDefinition definition = AreaDefinitionValidator.sanitizeDefinition(input, existingDef, player, false);
        if (definition == null) {
            sendSaveFailure(player, requestId, existingId, false);
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
                sendSaveFailure(player, requestId, existingId, false);
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
            sendSaveFailure(player, requestId, existingId, false);
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
            // Use existingId as fallback if requestId is null (shouldn't happen in normal flow)
            AreaNetworkHandler.sendPacket(player, new SaveAreaResultPayload(requestId != null ? requestId : existingId, existingId, definition.revision(), false, true));
        } else {
            player.displayClientMessage(
                Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_revision_mismatch")), true);
            DevMod.LOGGER.warn("Failed to update area {} - revision mismatch", existingId);
            sendSaveFailure(player, requestId, existingId, false);
        }
    }

    static void handleBuildAreaServer(BuildAreaPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
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

            // SEC-08 fix: Check if snapshot restore is in progress for this area
            if (AreaSnapshotManager.INSTANCE.isRestoringArea(Objects.requireNonNull(payload.areaId()))) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_restore_in_progress")), true);
                DevMod.LOGGER.warn("Player {} tried to build area {} while restore is in progress",
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
                    AreaNetworkHandler.sendPacket(player, BuildStatusPayload.completed(areaId));
                }
                case STARTED_MULTI_TICK -> {
                    // Multi-tick build started, send STARTED status
                    AreaNetworkHandler.sendPacket(player, BuildStatusPayload.started(areaId));
                }
                case QUEUED -> {
                    // Build queued, send QUEUED status with position
                    int position = AreaBuildTaskManager.INSTANCE.getQueuePosition(areaId) + 1;
                    AreaNetworkHandler.sendPacket(player, BuildStatusPayload.queued(areaId, position));
                }
                case ALREADY_BUILDING, QUEUE_FULL -> {
                    // Error cases - send FAILED status
                    AreaNetworkHandler.sendPacket(player, BuildStatusPayload.failed(areaId));
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

    static void handleOpenBuilderRequestServer(RequestOpenAreaBuilderPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
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
                AreaNetworkHandler.openBuilderScreen(player, null, null, false);
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

            AreaNetworkHandler.openBuilderScreen(player, area, null, area.isMainHub());
        });
    }

    static void handleRequestZoneListServer(RequestZoneListPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
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

            AreaNetworkHandler.sendPacket(player, new ZoneListPayload(summaries));
            DevMod.LOGGER.debug("[Area] Sent zone list to {} ({} zones)",
                player.getName().getString(), summaries.size());
        });
    }

    static void handleCloneAreaServer(CloneAreaPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
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
            AreaDefinition cloned = AreaDefinitionValidator.sanitizeDefinition(clonedRaw, null, player, true);
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

    static void handleDeleteAreaServer(DeleteAreaPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
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

            // MED-DELETE fix: Cancel any active/pending/queued builds before deleting
            boolean cancelledBuild = AreaBuildTaskManager.INSTANCE.cancelBuild(areaId);
            boolean removedFromQueue = AreaBuildTaskManager.INSTANCE.removeFromQueue(areaId);
            if (cancelledBuild || removedFromQueue) {
                DevMod.LOGGER.info("[Area] Cancelled builds for deleted area: {} (active={}, queued={})",
                    areaId, cancelledBuild, removedFromQueue);
            }

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

    static void handlePromoteMainHubServer(PromoteMainHubPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
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
    // Build Pause/Resume Handlers
    // ========================================================================

    static void handlePauseBuildServer(PauseBuildPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
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
                AreaNetworkHandler.sendPacket(player, BuildStatusPayload.paused(areaId, progress));

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

    static void handleResumeBuildServer(ResumeBuildPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
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
                AreaNetworkHandler.sendPacket(player, BuildStatusPayload.resumed(areaId, 0));

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

    // ========================================================================
    // Utility
    // ========================================================================

    private static void sendSaveFailure(@Nonnull ServerPlayer player, @Nullable UUID requestId,
                                        @Nullable UUID areaId, boolean isNewArea) {
        if (requestId == null) {
            return;
        }
        UUID safeAreaId = areaId != null ? areaId : requestId;
        AreaNetworkHandler.sendPacket(player, new SaveAreaResultPayload(requestId, safeAreaId, -1, isNewArea, false));
    }
}
