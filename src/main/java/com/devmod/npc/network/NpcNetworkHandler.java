package com.devmod.npc.network;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;
import com.devmod.clone.entity.PlayerCloneEntity;
import com.devmod.network.ChannelId;
import com.devmod.network.PayloadValidation;
import com.devmod.network.handlers.NetworkHandlerBase;
import com.devmod.npc.data.NpcConfiguration;
import com.devmod.npc.data.NpcRegistry;
import com.devmod.npc.dialog.DialogRegistry;
import com.devmod.npc.dialog.NpcDialogManager;

/**
 * Network handler for NPC system payloads.
 * Handles registration and processing of all NPC-related network messages.
 */
public final class NpcNetworkHandler extends NetworkHandlerBase {
    public static final NpcNetworkHandler INSTANCE = new NpcNetworkHandler();

    private NpcNetworkHandler() {}

    /**
     * Registers all NPC payload handlers.
     * Called from NetworkHandler during mod initialization.
     */
    public void registerPayloads(@Nonnull RegisterPayloadHandlersEvent event) {
        // Server -> Client: Open NPC config screen
        event.registrar(ChannelId.NPC_CONFIG_OPEN.asString()).playToClient(
            nn(OpenNpcConfigPayload.TYPE),
            nn(OpenNpcConfigPayload.STREAM_CODEC),
            PayloadValidation.validated(NpcNetworkHandler::handleOpenConfigClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Save NPC config
        event.registrar(ChannelId.NPC_CONFIG_SAVE.asString()).playToServer(
            nn(SaveNpcConfigPayload.TYPE),
            nn(SaveNpcConfigPayload.STREAM_CODEC),
            PayloadValidation.validated(NpcNetworkHandler::handleSaveConfigServer, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Server -> Client: Open dialog editor
        event.registrar(ChannelId.NPC_DIALOG_EDITOR_OPEN.asString()).playToClient(
            nn(OpenDialogEditorPayload.TYPE),
            nn(OpenDialogEditorPayload.STREAM_CODEC),
            PayloadValidation.validated(NpcNetworkHandler::handleOpenDialogEditorClient, PayloadValidation.PayloadLimits.LARGE)
        );

        // Client -> Server: Save dialog set
        event.registrar(ChannelId.NPC_DIALOG_SAVE.asString()).playToServer(
            nn(SaveDialogPayload.TYPE),
            nn(SaveDialogPayload.STREAM_CODEC),
            PayloadValidation.validated(NpcNetworkHandler::handleSaveDialogServer, PayloadValidation.PayloadLimits.LARGE)
        );

        // Server -> Client: Open NPC dialog
        event.registrar(ChannelId.NPC_DIALOG_OPEN.asString()).playToClient(
            nn(NpcDialogPayload.TYPE),
            nn(NpcDialogPayload.STREAM_CODEC),
            PayloadValidation.validated(NpcNetworkHandler::handleDialogClient, PayloadValidation.PayloadLimits.MEDIUM)
        );

        // Client -> Server: Dialog action (option selected)
        event.registrar(ChannelId.NPC_DIALOG_ACTION.asString()).playToServer(
            nn(NpcDialogActionPayload.TYPE),
            nn(NpcDialogActionPayload.STREAM_CODEC),
            PayloadValidation.validated(NpcNetworkHandler::handleDialogActionServer, PayloadValidation.PayloadLimits.SMALL)
        );

        DevMod.LOGGER.debug("Registered NPC network handlers");
    }

    // ========================================================================
    // Client Handlers (called on client thread)
    // ========================================================================

    private static void handleOpenConfigClient(OpenNpcConfigPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            // Use reflection to avoid client-side class loading on server
            try {
                Class<?> hooksClass = Class.forName("com.devmod.client.npc.NpcClientHooks");
                var method = hooksClass.getMethod("openConfigScreen", OpenNpcConfigPayload.class);
                method.invoke(null, payload);
            } catch (Exception e) {
                DevMod.LOGGER.error("Failed to open NPC config screen", e);
            }
        });
    }

    private static void handleOpenDialogEditorClient(OpenDialogEditorPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            try {
                Class<?> hooksClass = Class.forName("com.devmod.client.npc.NpcClientHooks");
                var method = hooksClass.getMethod("openDialogEditor", OpenDialogEditorPayload.class);
                method.invoke(null, payload);
            } catch (Exception e) {
                DevMod.LOGGER.error("Failed to open dialog editor", e);
            }
        });
    }

    private static void handleDialogClient(NpcDialogPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            try {
                Class<?> hooksClass = Class.forName("com.devmod.client.npc.NpcClientHooks");
                var method = hooksClass.getMethod("openNpcDialog", NpcDialogPayload.class);
                method.invoke(null, payload);
            } catch (Exception e) {
                DevMod.LOGGER.error("Failed to open NPC dialog", e);
            }
        });
    }

    // ========================================================================
    // Server Handlers (called on server thread)
    // ========================================================================

    private static void handleSaveConfigServer(SaveNpcConfigPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions
            if (!player.hasPermissions(2)) {
                DevMod.LOGGER.warn("Player {} tried to save NPC config without permissions",
                    player.getName().getString());
                return;
            }

            NpcRegistry registry = NpcRegistry.get(Objects.requireNonNull(player.getServer()));
            NpcConfiguration config = Objects.requireNonNull(payload.config());

            if (payload.isNewNpc()) {
                // Creating new NPC - store config for later spawn
                UUID npcId = registry.createNpc(Objects.requireNonNull(config));
                DevMod.LOGGER.info("Player {} created NPC config: {} ({})",
                    player.getName().getString(), config.displayName(), npcId);

                // If hand is specified, update the item's stored config
                if (payload.hand() != null) {
                    // Item config update would be handled here
                }
            } else {
                // Updating existing NPC
                UUID existingId = payload.existingNpcId();
                if (existingId == null) return;

                // Validate ownership
                NpcConfiguration existing = registry.getNpc(existingId).orElse(null);
                if (existing == null) {
                    DevMod.LOGGER.warn("Player {} tried to update non-existent NPC {}",
                        player.getName().getString(), existingId);
                    return;
                }

                if (!existing.isOwnedBy(Objects.requireNonNull(player.getUUID())) && !player.hasPermissions(4)) {
                    DevMod.LOGGER.warn("Player {} tried to update NPC {} without ownership",
                        player.getName().getString(), existingId);
                    return;
                }

                boolean updated = registry.updateNpc(existingId, Objects.requireNonNull(config), payload.expectedRevision());
                if (updated) {
                    DevMod.LOGGER.info("Player {} updated NPC: {} ({})",
                        player.getName().getString(), config.displayName(), existingId);

                    // Update live entity if loaded
                    ResourceKey<Level> dimension = config.dimension();
                    if (dimension != null) {
                        ServerLevel level = Objects.requireNonNull(player.getServer()).getLevel(dimension);
                        if (level != null) {
                            Entity entity = level.getEntity(existingId);
                            if (entity instanceof PlayerCloneEntity clone && clone.isNpc()) {
                                clone.setNpcMode(config);
                                DevMod.LOGGER.debug("Updated live NPC entity: {}", existingId);
                            }
                        }
                    }
                } else {
                    DevMod.LOGGER.warn("Failed to update NPC {} - revision mismatch", existingId);
                }
            }
        });
    }

    private static void handleSaveDialogServer(SaveDialogPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            // Validate permissions
            if (!player.hasPermissions(2)) {
                DevMod.LOGGER.warn("Player {} tried to save dialog without permissions",
                    player.getName().getString());
                return;
            }

            DialogRegistry.SaveResult result = DialogRegistry.saveDialogSet(
                Objects.requireNonNull(payload.dialogSet()),
                payload.expectedRevision(),
                player.getUUID(),
                player.hasPermissions(4)
            );

            if (!result.success()) {
                DevMod.LOGGER.warn("Player {} failed to save dialog set {}: {}",
                    player.getName().getString(), payload.dialogSet().id(), result.message());
                player.sendSystemMessage(Objects.requireNonNull(Component.literal("Dialog save failed: " + result.message())));
                for (String error : result.errors()) {
                    player.sendSystemMessage(Objects.requireNonNull(Component.literal(" - " + error)));
                }
                return;
            }

            DevMod.LOGGER.info("Player {} saved dialog set: {}",
                player.getName().getString(), payload.dialogSet().id());
            player.sendSystemMessage(Objects.requireNonNull(Component.literal(result.message())));
        });
    }

    private static void handleDialogActionServer(NpcDialogActionPayload payload, IPayloadContext ctx) {
        enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            NpcRegistry registry = NpcRegistry.get(Objects.requireNonNull(player.getServer()));

            // Process the action
            var result = NpcDialogManager.INSTANCE.processAction(
                player,
                Objects.requireNonNull(payload.npcId()),
                Objects.requireNonNull(payload.nodeId()),
                Objects.requireNonNull(payload.optionId()),
                registry
            );

            if (result != null) {
                // Send next dialog state to client
                sendDialogToPlayer(player, result);
            }
            // If result is null, dialog was closed
        });
    }

    // ========================================================================
    // Public API for sending payloads
    // ========================================================================

    /**
     * Opens the NPC config screen for a player.
     *
     * @param player The server player
     * @param config Existing config (null for new NPC)
     * @param hand   The hand holding the item (null if editing existing NPC)
     * @param npcId  The existing NPC's ID (null if creating new)
     */
    public static void openConfigScreen(
        @Nonnull ServerPlayer player,
        @Nonnull NpcConfiguration config,
        @Nullable InteractionHand hand,
        @Nullable UUID npcId
    ) {
        sendPacket(player, new OpenNpcConfigPayload(config, hand, npcId));
    }

    /**
     * Opens the dialog editor screen for a player.
     *
     * @param player      The server player
     * @param dialogSetId The dialog set ID
     * @param dialogSet   The dialog set (null for new)
     */
    public static void openDialogEditor(
        @Nonnull ServerPlayer player,
        @Nonnull String dialogSetId,
        @Nullable com.devmod.npc.dialog.DialogSet dialogSet
    ) {
        sendPacket(player, new OpenDialogEditorPayload(dialogSetId, dialogSet));
    }

    /**
     * Sends a dialog payload to a player.
     *
     * @param player The server player
     * @param result The dialog result from NpcDialogManager
     */
    public static void sendDialogToPlayer(
        @Nonnull ServerPlayer player,
        @Nonnull NpcDialogManager.NpcDialogResult result
    ) {
        List<NpcDialogPayload.NpcDialogOptionData> options = result.options().stream()
            .map(opt -> new NpcDialogPayload.NpcDialogOptionData(opt.id(), opt.label(), opt.icon()))
            .toList();

        sendPacket(player, new NpcDialogPayload(
            result.npcId(),
            result.npcName(),
            result.dialogSetId(),
            result.nodeId(),
            result.lines(),
            options
        ));
    }

    /**
     * Opens a dialog for a player with an NPC.
     *
     * @param player   The server player
     * @param npcId    The NPC's UUID
     * @param config   The NPC configuration
     * @param registry The NPC registry
     */
    public static void openNpcDialog(
        @Nonnull ServerPlayer player,
        @Nonnull UUID npcId,
        @Nonnull NpcConfiguration config,
        @Nonnull NpcRegistry registry
    ) {
        var result = NpcDialogManager.INSTANCE.openDialog(player, npcId, config, registry);
        if (result != null) {
            sendDialogToPlayer(player, result);
        }
    }

    /**
     * Opens a dialog for a player with an NPC, looking up config by dialog set ID.
     *
     * @param player      The server player
     * @param npcId       The NPC's entity UUID
     * @param dialogSetId The dialog set ID to open
     */
    public void openDialog(
        @Nonnull ServerPlayer player,
        @Nonnull UUID npcId,
        @Nonnull String dialogSetId
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(dialogSetId, "dialogSetId");

        if (dialogSetId.isEmpty()) {
            DevMod.LOGGER.warn("Cannot open dialog for NPC {} - empty dialogSetId", npcId);
            return;
        }

        NpcRegistry registry = NpcRegistry.get(Objects.requireNonNull(player.getServer()));

        // Try to find the NPC configuration
        NpcConfiguration config = registry.getNpc(npcId).orElse(null);
        if (config == null) {
            // NPC might not be in registry (e.g., legacy or spawned differently)
            // Create a minimal config for dialog purposes
            DevMod.LOGGER.debug("NPC {} not found in registry, using minimal config for dialog", npcId);
        }

        // Open the dialog session
        var result = NpcDialogManager.INSTANCE.openDialogBySetId(player, npcId, dialogSetId, registry);
        if (result != null) {
            sendDialogToPlayer(player, result);
        }
    }

    private static void enqueueWork(IPayloadContext ctx, Runnable work) {
        ctx.enqueueWork(Objects.requireNonNull(work))
            .exceptionally(e -> {
                DevMod.LOGGER.error("[NpcNetwork] Error in enqueued work: {}", e.getMessage(), e);
                return null;
            });
    }
}
