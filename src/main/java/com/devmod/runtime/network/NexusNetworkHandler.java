package com.devmod.runtime.network;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.network.ChannelId;
import com.devmod.network.PayloadValidation.PayloadLimits;
import com.devmod.network.handlers.NetworkHandlerBase;
import com.devmod.network.handlers.PayloadRegistrar;
import com.devmod.runtime.NexusDialogContext;
import com.devmod.runtime.NexusDialogManager;
import com.devmod.runtime.NexusDialogManager.DialogOption;
import com.devmod.runtime.NexusDialogManager.DialogResponse;
import com.devmod.runtime.NexusDialogManager.DialogType;
import com.devmod.runtime.NexusDimensionManager;

import static com.devmod.network.PayloadValidation.validated;

/**
 * Network handler for Nexus dialog system.
 */
public final class NexusNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusNetworkHandler.class);
    private static final int LOG_MAX_LINES = 120;
    private static final int LOG_MAX_LINE_LENGTH = 240;

    public static final NexusNetworkHandler INSTANCE = new NexusNetworkHandler();

    private NexusNetworkHandler() {}

    /** Consume a future to satisfy FutureReturnValueIgnored without unused variable warnings. */
    private static void ignoreFuture(java.util.concurrent.CompletableFuture<?> future) {
        if (future == null) {
            LOGGER.trace("Future was null");
        }
    }

    @Override
    public String getDomainName() {
        return "nexus";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // NEXUS_DIALOG: Server -> Client (opens dialog screen)
        event.registrar(ChannelId.NEXUS_DIALOG.asString()).playToClient(
            nn(NexusDialogPayload.TYPE),
            nn(NexusDialogPayload.STREAM_CODEC),
            validated(NexusNetworkHandler::handleDialogClient, PayloadLimits.MEDIUM)
        );

        // NEXUS_DIALOG_ACTION: Client -> Server (dialog option selected)
        event.registrar(ChannelId.NEXUS_DIALOG_ACTION.asString()).playToServer(
            nn(NexusDialogActionPayload.TYPE),
            nn(NexusDialogActionPayload.STREAM_CODEC),
            validated(NexusNetworkHandler::handleDialogActionServer, PayloadLimits.SMALL)
        );

        // NEXUS_UI: Server -> Client (opens Nexus UI screens)
        event.registrar(ChannelId.NEXUS_UI.asString()).playToClient(
            nn(NexusUiPayload.TYPE),
            nn(NexusUiPayload.STREAM_CODEC),
            validated(NexusNetworkHandler::handleUiClient, PayloadLimits.SMALL)
        );

        // NEXUS_LOG_REQUEST: Client -> Server (request log snapshot)
        event.registrar(ChannelId.NEXUS_LOG_REQUEST.asString()).playToServer(
            nn(NexusLogRequestPayload.TYPE),
            nn(NexusLogRequestPayload.STREAM_CODEC),
            validated(NexusNetworkHandler::handleLogRequestServer, PayloadLimits.SMALL)
        );

        // NEXUS_LOG_SNAPSHOT: Server -> Client (log snapshot)
        event.registrar(ChannelId.NEXUS_LOG_SNAPSHOT.asString()).playToClient(
            nn(NexusLogSnapshotPayload.TYPE),
            nn(NexusLogSnapshotPayload.STREAM_CODEC),
            validated(NexusNetworkHandler::handleLogSnapshotClient, PayloadLimits.LARGE)
        );
    }

    // =========================================================================
    // CLIENT HANDLERS
    // =========================================================================

    private static void handleDialogClient(NexusDialogPayload payload, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        ignoreFuture(ctx.enqueueWork(() -> {
            try {
                NexusClientBridge.get().openDialog(payload);
            } catch (Exception e) {
                LOGGER.error("[NexusNetwork] Failed to open dialog screen", e);
            }
        }));
    }

    private static void handleUiClient(NexusUiPayload payload, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        ignoreFuture(ctx.enqueueWork(() -> {
            try {
                NexusClientBridge.get().openUi(payload);
            } catch (Exception e) {
                LOGGER.error("[NexusNetwork] Failed to open Nexus UI", e);
            }
        }));
    }

    private static void handleLogSnapshotClient(NexusLogSnapshotPayload payload, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        ignoreFuture(ctx.enqueueWork(() -> {
            try {
                NexusClientBridge.get().applyLogSnapshot(payload);
            } catch (Exception e) {
                LOGGER.debug("[NexusNetwork] Client log snapshot handler unavailable: {}", e.getMessage());
            }
        }));
    }

    // =========================================================================
    // SERVER HANDLERS
    // =========================================================================

    private static void handleDialogActionServer(NexusDialogActionPayload payload, IPayloadContext ctx) {
        ignoreFuture(ctx.enqueueWork(() -> {
            try {
                if (ctx.player() instanceof ServerPlayer player) {
                    processDialogAction(player, payload);
                }
            } catch (Exception e) {
                LOGGER.error("[NexusNetwork] Failed to handle dialog action", e);
            }
        }));
    }

    private static void handleLogRequestServer(NexusLogRequestPayload payload, IPayloadContext ctx) {
        ignoreFuture(ctx.enqueueWork(() -> {
            try {
                if (!(ctx.player() instanceof ServerPlayer player)) {
                    return;
                }
                // Serves server telemetry file contents; standing in the Nexus is not authorisation.
                if (!AdminInstanceNetworkHandler.hasAdminPermission(player)) {
                    LOGGER.debug("[NexusNetwork] Denied log request from {} (insufficient permission)",
                        player.getName().getString());
                    return;
                }
                if (!player.level().dimension().equals(NexusDimensionManager.NEXUS_DIMENSION)) {
                    return;
                }
                NexusLogSnapshotPayload snapshot = buildLogSnapshot(player, payload.logType());
                PacketDistributor.sendToPlayer(player, Objects.requireNonNull(snapshot, "snapshot"));
            } catch (Exception e) {
                LOGGER.error("[NexusNetwork] Failed to handle log request", e);
            }
        }));
    }

    private static void processDialogAction(ServerPlayer player, NexusDialogActionPayload payload) {
        String optionId = payload.optionId();
        String nextDialogType = payload.nextDialogType();

        LOGGER.debug("[NexusNetwork] Player {} selected option '{}' -> {}",
            player.getName().getString(), optionId, nextDialogType);

        // Handle farewell - close dialog
        if ("farewell".equals(optionId) || "FAREWELL".equals(nextDialogType)) {
            // Don't send new dialog - client will close the screen
            return;
        }

        // Get the next dialog
        try {
            DialogType type = DialogType.valueOf(nextDialogType);
            NexusDialogContext ctx = NexusDialogContext.forPlayer(player);
            DialogResponse response = NexusDialogManager.INSTANCE.getDialog(type, ctx);

            // Send the new dialog to the client
            sendDialog(player, response);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[NexusNetwork] Unknown dialog type: {}", nextDialogType);
        }
    }

    private static NexusLogSnapshotPayload buildLogSnapshot(ServerPlayer player, NexusLogType type) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return new NexusLogSnapshotPayload(type, List.of("Server unavailable."), false, type.fileName());
        }
        Path dir = server.getServerDirectory().resolve("telemetry");
        Path file = dir.resolve(type.fileName());
        if (!Files.exists(file)) {
            return new NexusLogSnapshotPayload(type,
                List.of("No data yet for " + type.label() + "."),
                false,
                type.fileName());
        }
        TailResult result = tailFile(file, LOG_MAX_LINES, LOG_MAX_LINE_LENGTH);
        List<String> lines = result.lines().isEmpty()
            ? List.of("No data yet for " + type.label() + ".")
            : result.lines();
        return new NexusLogSnapshotPayload(type, lines, result.truncated(), type.fileName());
    }

    private static TailResult tailFile(Path file, int maxLines, int maxLineLength) {
        List<String> lines = new ArrayList<>();
        boolean truncated = false;
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            if (length <= 0) {
                return new TailResult(lines, false);
            }
            long pos = length - 1;
            StringBuilder current = new StringBuilder();
            while (pos >= 0 && lines.size() < maxLines) {
                raf.seek(pos);
                int b = raf.read();
                if (b == '\n') {
                    String line = flushLine(current, maxLineLength);
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                } else if (b != '\r') {
                    current.append((char) b);
                }
                pos--;
            }
            if (lines.size() < maxLines) {
                String line = flushLine(current, maxLineLength);
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            truncated = pos >= 0;
        } catch (IOException e) {
            return new TailResult(List.of("Failed to read log: " + e.getMessage()), false);
        }
        Collections.reverse(lines);
        return new TailResult(lines, truncated);
    }

    private static String flushLine(StringBuilder current, int maxLineLength) {
        if (current.length() == 0) {
            return "";
        }
        current.reverse();
        String line = current.toString();
        current.setLength(0);
        if (line.length() > maxLineLength) {
            return line.substring(0, Math.max(0, maxLineLength - 3)) + "...";
        }
        return line;
    }

    private record TailResult(List<String> lines, boolean truncated) {}

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Send a dialog response to a player.
     */
    public static void sendDialog(ServerPlayer player, DialogResponse response) {
        List<NexusDialogPayload.DialogOptionData> optionData = new ArrayList<>();
        for (DialogOption opt : response.options()) {
            optionData.add(new NexusDialogPayload.DialogOptionData(
                opt.id(),
                opt.label(),
                opt.icon(),
                opt.nextDialog().name()
            ));
        }

        NexusDialogPayload payload = new NexusDialogPayload(
            response.speakerName(),
            response.lines(),
            optionData
        );

        PacketDistributor.sendToPlayer(Objects.requireNonNull(player, "player"), payload);
    }

    /**
     * Open the initial greeting dialog for a player.
     */
    public static void openGreetingDialog(ServerPlayer player) {
        NexusDialogContext ctx = NexusDialogContext.forPlayer(Objects.requireNonNull(player, "player"));
        DialogResponse response = NexusDialogManager.INSTANCE.getGreeting(ctx);
        sendDialog(player, response);

        // Mark visit
        NexusDialogContext.markVisit(Objects.requireNonNull(player, "player"));
    }

}
