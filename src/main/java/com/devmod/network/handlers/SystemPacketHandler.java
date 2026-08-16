package com.devmod.network.handlers;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.mailbox.network.payload.MailboxActionPayload;
import com.devmod.mailbox.network.payload.MailboxAccessPayload;
import com.devmod.mailbox.network.payload.MailboxNotifyPayload;
import com.devmod.mailbox.network.payload.MailboxSendPayload;
import com.devmod.mailbox.network.payload.MailboxStatusPayload;
import com.devmod.mailbox.network.payload.MailboxSyncPayload;
import com.devmod.mailbox.network.payload.NewsReadPayload;
import com.devmod.mailbox.network.payload.NewsSyncPayload;
import com.devmod.mailbox.network.payload.TaskActionPayload;
import com.devmod.mailbox.network.payload.TaskSyncPayload;
import com.devmod.mailbox.network.payload.TicketActionPayload;
import com.devmod.mailbox.network.payload.TicketCreatePayload;
import com.devmod.mailbox.network.payload.TicketSyncPayload;
import com.devmod.mailbox.network.payload.TicketSyncRequestPayload;
import com.devmod.config.TesterModality;
import com.devmod.network.ChannelId;
import com.devmod.network.TesterModalitySyncPayload;
import com.devmod.network.GameMechanicsSyncPayload;
import com.devmod.network.NetworkHandler;
import com.devmod.network.PayloadValidation.PayloadLimits;
import com.devmod.notification.network.NotificationNetworkHandler;
import com.devmod.notification.network.NotificationPreferencesSyncPayload;
import com.devmod.notification.network.NotificationPreferencesUpdatePayload;
import com.devmod.notification.network.UnifiedNotificationPayload;
import com.devmod.telemetry.network.LVCSyncPayload;

import static com.devmod.network.PayloadValidation.validated;

/**
 * Domain-specific handler for system payloads:
 * config sync, telemetry (LVC), mailbox, and notification channels.
 */
public final class SystemPacketHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final SystemPacketHandler INSTANCE = new SystemPacketHandler();

    private SystemPacketHandler() {}

    @Override
    public String getDomainName() {
        return "system";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Game mechanics sync (always enabled)
        event.registrar(ChannelId.GAME_MECHANICS_SYNC.asString()).playToClient(
            nn(GameMechanicsSyncPayload.TYPE),
            nn(GameMechanicsSyncPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(payload::applyToClient), "game mechanics sync");
                }
            }, PayloadLimits.LARGE)
        );

        // LVC telemetry sync
        event.registrar(ChannelId.LVC_SYNC.asString()).playToClient(
            nn(LVCSyncPayload.TYPE),
            nn(LVCSyncPayload.STREAM_CODEC),
            validated(SystemPacketHandler::handleLvcSync, PayloadLimits.SMALL)
        );

        // Tester modality handshake.
        // This registration never existed: GameplayEvents.onPlayerLoggedIn sent the payload on
        // every login and NeoForge refused it with "Payload devmod:250 may not be sent to the
        // client!", swallowed by a catch that degraded it to a WARN. The whole client/server
        // compatibility check -- TesterModality.onServerSync, isCompatible, mismatchReason --
        // has therefore never run in any shipped build.
        event.registrar(ChannelId.TESTER_MODALITY_SYNC.asString()).playToClient(
            nn(TesterModalitySyncPayload.TYPE),
            nn(TesterModalitySyncPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(
                        context.enqueueWork(() -> TesterModality.onServerSync(payload.enabled())),
                        "tester modality sync");
                }
            }, PayloadLimits.SMALL)
        );

        registerMailboxPayloads(event);
        registerNotificationPayloads(event);
    }

    // =================================================================================
    // MAILBOX SYSTEM CHANNELS (100-115)
    // =================================================================================
    private void registerMailboxPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(ChannelId.MAILBOX_SYNC.asString()).playToClient(
            nn(MailboxSyncPayload.TYPE),
            nn(MailboxSyncPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleMailboxSync(payload))), "mailbox sync");
                }
            }, PayloadLimits.SYNC_LARGE)
        );
        event.registrar(ChannelId.MAILBOX_SEND.asString()).playToServer(
            nn(MailboxSendPayload.TYPE),
            nn(MailboxSendPayload.STREAM_CODEC),
            validated(com.devmod.mailbox.network.MailboxNetworkHandler::handleSend, PayloadLimits.MAILBOX)
        );
        event.registrar(ChannelId.MAILBOX_READ.asString()).playToServer(
            nn(MailboxActionPayload.TYPE),
            nn(MailboxActionPayload.STREAM_CODEC),
            validated(com.devmod.mailbox.network.MailboxNetworkHandler::handleAction, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.MAILBOX_NOTIFY.asString()).playToClient(
            nn(MailboxNotifyPayload.TYPE),
            nn(MailboxNotifyPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleMailboxNotify(payload))), "mailbox notify");
                }
            }, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.MAILBOX_STATUS.asString()).playToClient(
            nn(MailboxStatusPayload.TYPE),
            nn(MailboxStatusPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleMailboxStatus(payload))), "mailbox status");
                }
            }, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.MAILBOX_ACCESS.asString()).playToClient(
            nn(MailboxAccessPayload.TYPE),
            nn(MailboxAccessPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleMailboxAccess(payload))), "mailbox access");
                }
            }, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.NEWS_SYNC.asString()).playToClient(
            nn(NewsSyncPayload.TYPE),
            nn(NewsSyncPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleNewsSync(payload))), "news sync");
                }
            }, PayloadLimits.SYNC_LARGE)
        );
        event.registrar(ChannelId.NEWS_READ.asString()).playToServer(
            nn(NewsReadPayload.TYPE),
            nn(NewsReadPayload.STREAM_CODEC),
            validated(com.devmod.mailbox.network.MailboxNetworkHandler::handleNewsRead, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.TASK_SYNC.asString()).playToClient(
            nn(TaskSyncPayload.TYPE),
            nn(TaskSyncPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleTaskSync(payload))), "task sync");
                }
            }, PayloadLimits.SYNC_MEDIUM)
        );
        event.registrar(ChannelId.TASK_ACTION.asString()).playToServer(
            nn(TaskActionPayload.TYPE),
            nn(TaskActionPayload.STREAM_CODEC),
            validated(com.devmod.mailbox.network.MailboxNetworkHandler::handleTaskAction, PayloadLimits.SMALL)
        );
        event.registrar(ChannelId.TICKET_SYNC.asString()).playToClient(
            nn(TicketSyncPayload.TYPE),
            nn(TicketSyncPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleTicketSync(payload))), "ticket sync");
                }
            }, PayloadLimits.SYNC_MEDIUM)
        );
        event.registrar(ChannelId.TICKET_CREATE.asString()).playToServer(
            nn(TicketCreatePayload.TYPE),
            nn(TicketCreatePayload.STREAM_CODEC),
            validated(com.devmod.mailbox.network.TicketNetworkHandler::handleTicketCreate, PayloadLimits.TICKET)
        );
        event.registrar(ChannelId.TICKET_ACTION.asString()).playToServer(
            nn(TicketActionPayload.TYPE),
            nn(TicketActionPayload.STREAM_CODEC),
            validated(com.devmod.mailbox.network.TicketNetworkHandler::handleTicketAction, PayloadLimits.TICKET)
        );
        event.registrar(ChannelId.TICKET_SYNC_REQUEST.asString()).playToServer(
            nn(TicketSyncRequestPayload.TYPE),
            nn(TicketSyncRequestPayload.STREAM_CODEC),
            validated(com.devmod.mailbox.network.TicketNetworkHandler::handleTicketSyncRequest, PayloadLimits.SMALL)
        );
    }

    // =================================================================================
    // UNIFIED NOTIFICATION CENTER CHANNELS (120-129)
    // =================================================================================
    private void registerNotificationPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(ChannelId.UNIFIED_NOTIFICATION.asString()).playToClient(
            nn(UnifiedNotificationPayload.TYPE),
            nn(UnifiedNotificationPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleUnifiedNotification(payload))), "unified notification");
                }
            }, PayloadLimits.MEDIUM)
        );
        event.registrar(ChannelId.NOTIFICATION_PREFS_SYNC.asString()).playToClient(
            nn(NotificationPreferencesSyncPayload.TYPE),
            nn(NotificationPreferencesSyncPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleNotificationPreferencesSync(payload))), "notification prefs sync");
                }
            }, PayloadLimits.LARGE)
        );
        event.registrar(ChannelId.NOTIFICATION_PREFS_UPDATE.asString()).playToServer(
            nn(NotificationPreferencesUpdatePayload.TYPE),
            nn(NotificationPreferencesUpdatePayload.STREAM_CODEC),
            validated(NotificationNetworkHandler::handlePreferencesUpdate, PayloadLimits.SMALL)
        );
    }

    // =================================================================================
    // LVC SYNC (client-side)
    // =================================================================================
    private static void handleLvcSync(LVCSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleLvcSync(payload))), "lvc sync");
        }
    }
}
