package com.devmod.hologram.network;

import java.util.EnumSet;
import java.util.Objects;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import com.devmod.hologram.block.entity.HologramProjectorBlockEntity;
import com.devmod.hologram.data.EntityFilterType;
import com.devmod.hologram.data.HologramFilter;
import com.devmod.hologram.data.HologramRegistry;
import com.devmod.hologram.runtime.HologramManager;
import com.devmod.network.ChannelId;
import com.devmod.network.NetworkHandler;
import com.devmod.network.handlers.NetworkHandlerBase;
import com.devmod.network.handlers.PayloadRegistrar;

import static com.devmod.network.PayloadValidation.PayloadLimits;
import static com.devmod.network.PayloadValidation.validated;

/**
 * Domain-specific network handler for hologram-related payloads.
 * Handles hologram configuration, editor, sync, save, and delete operations.
 */
public final class HologramNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final HologramNetworkHandler INSTANCE = new HologramNetworkHandler();

    private HologramNetworkHandler() {}

    @Override
    public String getDomainName() {
        return "hologram";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // HOLOGRAM_CONFIG: Client -> Server 3D Projector configuration
        event.registrar(ChannelId.HOLOGRAM_CONFIG.asString()).playToServer(
            nn(HologramConfigPayload.TYPE),
            nn(HologramConfigPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    handleHologramConfig(player, payload);
                }
            }, PayloadLimits.SMALL)
        );

        // HOLOGRAM_OPEN_SCREEN: Server -> Client open 3D Projector screen
        event.registrar(ChannelId.HOLOGRAM_OPEN_SCREEN.asString()).playToClient(
            nn(HologramOpenScreenPayload.TYPE),
            nn(HologramOpenScreenPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleHologramOpenScreen(payload))), "hologram open screen");
                }
            }, PayloadLimits.SMALL)
        );

        // HOLOGRAM_EDITOR_OPEN: Server -> Client open TextDisplay editor
        event.registrar(ChannelId.HOLOGRAM_EDITOR_OPEN.asString()).playToClient(
            nn(OpenHologramEditorPayload.TYPE),
            nn(OpenHologramEditorPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        com.devmod.client.hologram.HologramClientHandler.handleOpenEditor(payload)), "hologram editor open");
                }
            }, PayloadLimits.MEDIUM)
        );

        // HOLOGRAM_SAVE: Client -> Server save hologram changes
        event.registrar(ChannelId.HOLOGRAM_SAVE.asString()).playToServer(
            nn(SaveHologramPayload.TYPE),
            nn(SaveHologramPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    handleHologramSave(player, payload);
                }
            }, PayloadLimits.MEDIUM)
        );

        // HOLOGRAM_DELETE: Client -> Server delete hologram
        event.registrar(ChannelId.HOLOGRAM_DELETE.asString()).playToServer(
            nn(DeleteHologramPayload.TYPE),
            nn(DeleteHologramPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    handleHologramDelete(player, payload);
                }
            }, PayloadLimits.SMALL)
        );

        // HOLOGRAM_SYNC: Server -> Client hologram update broadcast
        event.registrar(ChannelId.HOLOGRAM_SYNC.asString()).playToClient(
            nn(HologramSyncPayload.TYPE),
            nn(HologramSyncPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        com.devmod.client.hologram.HologramClientHandler.handleSync(payload)), "hologram sync");
                }
            }, PayloadLimits.MEDIUM)
        );

        // HOLOGRAM_PRESET: Client -> Server save/load configuration preset
        event.registrar(ChannelId.HOLOGRAM_PRESET.asString()).playToServer(
            nn(HologramPresetPayload.TYPE),
            nn(HologramPresetPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    handleHologramPreset(player, payload);
                }
            }, PayloadLimits.SMALL)
        );
    }

    /**
     * Send hologram open screen payload to a player.
     */
    public static void sendHologramOpenScreen(ServerPlayer player, HologramOpenScreenPayload payload) {
        sendPacket(Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /**
     * Handle hologram configuration from client.
     */
    private static void handleHologramConfig(ServerPlayer player, HologramConfigPayload payload) {
        var level = player.serverLevel();
        var pos = Objects.requireNonNull(payload.pos(), "pos");

        // Validate distance (max 8 blocks)
        if (player.blockPosition().distManhattan(pos) > 8) {
            return;
        }

        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof HologramProjectorBlockEntity projector) {
            // Apply settings
            if (projector.getScanSize() != payload.scanSize()) {
                projector.setScanSize(payload.scanSize());
            }
            if (projector.getBlockSize() != payload.blockSize()) {
                projector.setBlockSize(payload.blockSize());
            }
            if (projector.isRotationEnabled() != payload.rotationEnabled()) {
                projector.toggleRotation();
            }
            if (projector.isTransparentMode() != payload.transparentMode()) {
                projector.toggleTransparency();
            }

            // Apply filter settings
            EnumSet<HologramFilter> newFilters = intToFilters(payload.filterBitmask());
            projector.setActiveFilters(newFilters);
            projector.setFilterHighlightOnly(payload.filterHighlightOnly());

            // Apply texture mode setting
            if (projector.isTexturedMode() != payload.texturedMode()) {
                projector.setTexturedMode(payload.texturedMode());
            }

            // Apply entity rendering settings
            if (projector.isShowEntities() != payload.showEntities()) {
                projector.setShowEntities(payload.showEntities());
            }
            if (projector.isFullEntityModels() != payload.fullEntityModels()) {
                projector.setFullEntityModels(payload.fullEntityModels());
            }
            if (projector.getMaxEntityModels() != payload.maxEntityModels()) {
                projector.setMaxEntityModels(payload.maxEntityModels());
            }

            // Apply entity filter settings
            EnumSet<EntityFilterType> newEntityFilters = EntityFilterType.fromBitmask(payload.entityFilterBitmask());
            projector.setActiveEntityFilters(newEntityFilters);

            // Apply Y-slice settings
            if (projector.isYSliceEnabled() != payload.ySliceEnabled()) {
                projector.setYSliceEnabled(payload.ySliceEnabled());
            }
            if (projector.getYSliceLevel() != payload.ySliceLevel()) {
                projector.setYSliceLevel(payload.ySliceLevel());
            }
            if (projector.getYSliceThickness() != payload.ySliceThickness()) {
                projector.setYSliceThickness(payload.ySliceThickness());
            }

            // Force rescan if requested
            if (payload.rescan()) {
                projector.setupScanRegion();
            }
        }
    }

    /**
     * Handle preset save/load from client.
     */
    private static void handleHologramPreset(ServerPlayer player, HologramPresetPayload payload) {
        var level = player.serverLevel();
        var pos = Objects.requireNonNull(payload.pos(), "pos");

        // Validate distance (max 8 blocks)
        if (player.blockPosition().distManhattan(pos) > 8) {
            return;
        }

        // Validate slot
        if (!payload.isValidSlot()) {
            return;
        }

        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof HologramProjectorBlockEntity projector) {
            if (payload.isSave()) {
                // Save current config to preset slot
                projector.savePreset(payload.slot());
            } else {
                // Load preset from slot
                projector.loadPreset(payload.slot());
            }
        }
    }

    private static EnumSet<HologramFilter> intToFilters(int value) {
        EnumSet<HologramFilter> result = EnumSet.noneOf(HologramFilter.class);
        for (HologramFilter filter : HologramFilter.values()) {
            if ((value & (1 << filter.ordinal())) != 0) {
                result.add(filter);
            }
        }
        return result;
    }

    /**
     * Handle hologram save from client.
     */
    private static void handleHologramSave(ServerPlayer player, SaveHologramPayload payload) {
        var server = player.getServer();
        if (server == null) return;

        // Permission check
        if (!player.hasPermissions(2)) {
            return;
        }

        var registry = HologramRegistry.get(server);
        var existingOpt = registry.getHologram(payload.hologramId());
        if (existingOpt.isEmpty()) {
            return;
        }

        var existing = existingOpt.get();

        // Optimistic locking check
        if (existing.revision() != payload.expectedRevision()) {
            player.displayClientMessage(
                Objects.requireNonNull(
                    net.minecraft.network.chat.Component.translatable("message.devmod.hologram.error.revision_conflict")),
                true
            );
            return;
        }

        // Update hologram using the withUpdate method
        var updated = Objects.requireNonNull(
            existing.withUpdate(payload.lines(), payload.style(), payload.options()), "updated");

        var hologramId = Objects.requireNonNull(existing.id(), "hologramId");
        registry.updateHologram(hologramId, updated, payload.expectedRevision());

        // Update entity: despawn and respawn
        var serverLevel = Objects.requireNonNull(player.serverLevel(), "serverLevel");
        HologramManager.INSTANCE.despawnHologram(serverLevel, hologramId);
        HologramManager.INSTANCE.spawnHologram(serverLevel, updated);

        // Feedback
        player.displayClientMessage(
            Objects.requireNonNull(
                net.minecraft.network.chat.Component.translatable("message.devmod.hologram.saved")),
            true
        );
    }

    /**
     * Handle hologram delete from client.
     */
    private static void handleHologramDelete(ServerPlayer player, DeleteHologramPayload payload) {
        var server = player.getServer();
        if (server == null) return;

        // Permission check
        if (!player.hasPermissions(2)) {
            return;
        }

        var registry = HologramRegistry.get(server);
        var existingOpt = registry.getHologram(payload.hologramId());
        if (existingOpt.isEmpty()) {
            return;
        }

        var existing = existingOpt.get();

        // Check if locked
        if (existing.options().locked()) {
            player.displayClientMessage(
                Objects.requireNonNull(
                    net.minecraft.network.chat.Component.translatable("message.devmod.hologram.error.locked")),
                true
            );
            return;
        }

        // Despawn entity
        var serverLevel = Objects.requireNonNull(player.serverLevel(), "serverLevel");
        HologramManager.INSTANCE.despawnHologram(serverLevel, payload.hologramId());

        // Remove from registry
        registry.deleteHologram(payload.hologramId());

        // Feedback
        player.displayClientMessage(
            Objects.requireNonNull(
                net.minecraft.network.chat.Component.translatable("message.devmod.hologram.removed")),
            true
        );
    }
}
