package com.devmod.area.network;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;
import com.devmod.area.aesthetic.AreaBuilderMessages;
import com.devmod.area.data.AreaAuditLog;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaRegistry;
import com.devmod.area.snapshot.AreaSnapshot;
import com.devmod.area.snapshot.AreaSnapshotManager;
import com.devmod.area.snapshot.AreaSnapshotRegistry;

/**
 * Handles snapshot-related network operations.
 * Extracted from AreaNetworkHandler for better modularity.
 */
public final class SnapshotManagementHandler {

    private static final String CLIENT_HOOKS_CLASS = "com.devmod.client.area.AreaClientHooks";

    @Nullable
    private static volatile MethodHandle handleSnapshotListMethod;

    private SnapshotManagementHandler() {}

    // ========================================================================
    // Server Handlers
    // ========================================================================

    public static void handleCaptureSnapshotServer(CaptureSnapshotPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

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

            Optional<AreaDefinition> areaOpt = registry.getArea(areaId);
            if (areaOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                return;
            }

            AreaDefinition area = areaOpt.get();
            ServerLevel level = player.serverLevel();

            if (!area.dimensionId().equals(level.dimension().location())) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_wrong_dimension")), true);
                return;
            }

            String description = payload.description() != null ? Objects.requireNonNull(payload.description()) : "";
            UUID snapshotId = AreaSnapshotManager.INSTANCE.startCapture(level, area, Objects.requireNonNull(description), player);

            if (snapshotId != null) {
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

    public static void handleRestoreSnapshotServer(RestoreSnapshotPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            UUID snapshotId = payload.snapshotId();
            if (snapshotId == null) {
                player.displayClientMessage(Objects.requireNonNull(Component.translatable("area.snapshot.not_found")), true);
                return;
            }

            ServerLevel level = player.serverLevel();

            AreaSnapshotRegistry snapshotRegistry = AreaSnapshotRegistry.get(Objects.requireNonNull(player.getServer()));
            Optional<AreaSnapshot> snapshotOpt = snapshotRegistry.getSnapshot(snapshotId);
            if (snapshotOpt.isEmpty()) {
                player.displayClientMessage(Objects.requireNonNull(Component.translatable("area.snapshot.not_found")), true);
                return;
            }

            AreaSnapshot snapshot = snapshotOpt.get();

            // H-04 fix: Verify player has access to the area the snapshot belongs to
            AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));
            Optional<AreaDefinition> areaOpt = areaRegistry.getArea(Objects.requireNonNull(snapshot.areaId()));
            if (areaOpt.isPresent()) {
                AreaDefinition area = areaOpt.get();
                UUID creatorUUID = area.creatorUUID();
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

            boolean started = AreaSnapshotManager.INSTANCE.startRestore(Objects.requireNonNull(level), snapshotId, player);

            if (started) {
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

    public static void handleDeleteSnapshotServer(DeleteSnapshotPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            UUID snapshotId = payload.snapshotId();
            if (snapshotId == null) {
                player.displayClientMessage(Objects.requireNonNull(Component.translatable("area.snapshot.not_found")), true);
                return;
            }

            AreaSnapshotRegistry registry = AreaSnapshotRegistry.get(Objects.requireNonNull(player.getServer()));

            Optional<AreaSnapshot> snapshotOpt = registry.getSnapshot(snapshotId);
            if (snapshotOpt.isEmpty()) {
                player.displayClientMessage(Objects.requireNonNull(Component.translatable("area.snapshot.not_found")), true);
                return;
            }

            AreaSnapshot snapshot = snapshotOpt.get();

            // SEC-04 fix: Verify player has access to delete the snapshot (owner or OP4)
            AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));
            Optional<AreaDefinition> areaOpt = areaRegistry.getArea(Objects.requireNonNull(snapshot.areaId()));
            if (areaOpt.isPresent()) {
                AreaDefinition area = areaOpt.get();
                UUID creatorUUID = area.creatorUUID();
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

            Path worldFolder = Objects.requireNonNull(player.getServer())
                .getWorldPath(Objects.requireNonNull(LevelResource.ROOT));
            boolean deleted = registry.deleteSnapshot(snapshotId, worldFolder);

            if (deleted) {
                player.displayClientMessage(Objects.requireNonNull(Component.translatable("area.snapshot.deleted")), false);
                DevMod.LOGGER.info("Player {} deleted snapshot {} for area {}",
                    player.getName().getString(), snapshotId, snapshot.areaName());

                AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                    AreaAuditLog.ActionType.SNAPSHOT_DELETE,
                    Objects.requireNonNull(snapshot.areaId()),
                    Objects.requireNonNull(snapshot.areaName()),
                    player,
                    "snapshotId=" + snapshotId
                );
            } else {
                player.displayClientMessage(Objects.requireNonNull(Component.translatable("area.snapshot.delete_failed")), true);
            }
        });
    }

    public static void handleRequestSnapshotListServer(RequestSnapshotListPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
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
                    return;
                }
            }

            AreaSnapshotRegistry registry = AreaSnapshotRegistry.get(Objects.requireNonNull(player.getServer()));
            List<AreaSnapshot> snapshots = registry.getSnapshotsForArea(areaId);

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

            AreaNetworkHandler.sendPacket(player, new SnapshotListPayload(areaId, summaries));
            DevMod.LOGGER.debug("[Snapshot] Sent snapshot list to {} ({} snapshots for area {})",
                player.getName().getString(), summaries.size(), areaId);
        });
    }

    // ========================================================================
    // Client Handler
    // ========================================================================

    public static void handleSnapshotListClient(SnapshotListPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            MethodHandle method = getHandleSnapshotListMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to handle snapshot list", e);
            }
        });
    }

    @Nullable
    private static MethodHandle getHandleSnapshotListMethod() {
        if (handleSnapshotListMethod == null) {
            synchronized (SnapshotManagementHandler.class) {
                if (handleSnapshotListMethod == null) {
                    handleSnapshotListMethod = lookupMethod("handleSnapshotList", SnapshotListPayload.class);
                }
            }
        }
        return handleSnapshotListMethod;
    }

    @Nullable
    private static MethodHandle lookupMethod(String methodName, Class<?> paramType) {
        try {
            Class<?> hooksClass = Class.forName(CLIENT_HOOKS_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.findStatic(hooksClass, methodName, MethodType.methodType(void.class, paramType));
        } catch (ClassNotFoundException e) {
            DevMod.LOGGER.debug("[Snapshot] Client hooks class not available (dedicated server)");
            return null;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            DevMod.LOGGER.error("[Snapshot] Failed to lookup client method: {}", methodName, e);
            return null;
        }
    }
}
