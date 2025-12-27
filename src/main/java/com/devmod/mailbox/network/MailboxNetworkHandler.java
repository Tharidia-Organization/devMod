package com.devmod.mailbox.network;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.mailbox.MailboxConfig;
import com.devmod.mailbox.MailboxManager;
import com.devmod.mailbox.MailboxMessage;
import com.devmod.mailbox.MailboxPermissions;
import com.devmod.mailbox.network.payload.MailboxAccessPayload;
import com.devmod.mailbox.network.payload.MailboxActionPayload;
import com.devmod.mailbox.network.payload.MailboxNotifyPayload;
import com.devmod.mailbox.network.payload.MailboxSendPayload;
import com.devmod.mailbox.network.payload.MailboxStatusPayload;
import com.devmod.mailbox.network.payload.MailboxSyncPayload;
import com.devmod.mailbox.network.payload.NewsReadPayload;
import com.devmod.mailbox.network.payload.NewsSyncPayload;
import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.news.NewsManager;
import com.devmod.network.handlers.NetworkHandlerBase;

/**
 * Network handler for mailbox-related packets.
 */
public final class MailboxNetworkHandler extends NetworkHandlerBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxNetworkHandler.class);
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private MailboxNetworkHandler() {
        // Prevent instantiation
    }

    // ============================================================================
    // SERVER-SIDE HANDLERS (Client -> Server)
    // ============================================================================

    /**
     * Handle a send message request from client.
     */
    public static void handleSend(MailboxSendPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "mailbox_send", false);
            if (!validation.isSuccess()) {
                sendStatus(player, MailboxStatusPayload.Action.SEND, MailboxStatusPayload.Status.ERROR,
                    "Rate limited. Please wait.");
                return;
            }

            UUID recipientUuid = resolveRecipientUuid(player, payload);
            if (recipientUuid == null) {
                sendStatus(player, MailboxStatusPayload.Action.SEND, MailboxStatusPayload.Status.ERROR,
                    "Recipient not found");
                return;
            }

            observeFuture(MailboxManager.INSTANCE.sendPlayerMessage(
                player,
                recipientUuid,
                payload.subject(),
                payload.body(),
                payload.attachmentData()
            ).thenAccept(result -> {
                if (result.success()) {
                    sendStatus(player, MailboxStatusPayload.Action.SEND, MailboxStatusPayload.Status.SUCCESS,
                        "Message sent");
                    // Refresh sender's mailbox
                    sendMailboxSync(player);
                } else {
                    String message = result.error() != null ? result.error() : "Failed to send message";
                    sendStatus(player, MailboxStatusPayload.Action.SEND, MailboxStatusPayload.Status.ERROR,
                        message);
                }
            }), "mailbox send message");
        }), "mailbox send");
    }

    /**
     * Handle mailbox action (read, delete, claim) from client.
     */
    public static void handleAction(MailboxActionPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "mailbox_action", false);
            if (!validation.isSuccess()) {
                return;
            }

            MailboxActionPayload.Action action = payload.action();
            if (action == MailboxActionPayload.Action.REFRESH) {
                sendMailboxSync(player);
                return;
            }

            UUID messageId = payload.messageId();

            observeFuture(MailboxManager.INSTANCE.getMessage(messageId).thenAccept(optMsg -> {
                if (optMsg.isEmpty()) {
                    sendStatus(player, toStatusAction(action), MailboxStatusPayload.Status.ERROR,
                        "Message not found");
                    return;
                }

                MailboxMessage msg = optMsg.get();
                if (!msg.recipientUuid().equals(player.getUUID()) || msg.deleted()) {
                    LOGGER.warn("[Mailbox] Unauthorized access attempt by {} for message {}",
                        player.getName().getString(), messageId);
                    sendStatus(player, toStatusAction(action), MailboxStatusPayload.Status.ERROR,
                        "Access denied");
                    return;
                }
                if ((action == MailboxActionPayload.Action.DELETE || action == MailboxActionPayload.Action.CLAIM)
                        && MailboxConfig.INSTANCE.isMaintenanceMode()
                        && !MailboxPermissions.INSTANCE.isAdmin(player.getUUID(), player)) {
                    sendStatus(player, toStatusAction(action), MailboxStatusPayload.Status.ERROR,
                        "Mailbox is in maintenance mode");
                    return;
                }

                switch (action) {
                    case READ -> observeFuture(MailboxManager.INSTANCE.markAsRead(messageId)
                        .thenRun(() -> sendMailboxSync(player)), "mailbox mark read");
                    case DELETE -> {
                        if (msg.canClaimAttachment()) {
                            sendStatus(player, MailboxStatusPayload.Action.DELETE, MailboxStatusPayload.Status.WARNING,
                                "Claim attachment before deleting");
                            return;
                        }
                        observeFuture(MailboxManager.INSTANCE.deleteMessage(messageId)
                            .thenRun(() -> {
                                sendMailboxSync(player);
                                sendStatus(player, MailboxStatusPayload.Action.DELETE, MailboxStatusPayload.Status.SUCCESS,
                                    "Message deleted");
                            }), "mailbox delete");
                    }
                    case CLAIM -> {
                        observeFuture(MailboxManager.INSTANCE.claimAttachments(player, msg)
                            .thenAccept(result -> {
                                String resultMsg = result.message() != null ? result.message() : "Attachment claimed";
                                sendStatus(player, MailboxStatusPayload.Action.CLAIM,
                                    result.success() ? MailboxStatusPayload.Status.SUCCESS : MailboxStatusPayload.Status.ERROR,
                                    resultMsg);
                                if (result.success()) {
                                    sendMailboxSync(player);
                                }
                            }), "mailbox claim");
                    }
                    case REFRESH -> sendMailboxSync(player);
                }
            }), "mailbox action " + action);
        }), "mailbox action enqueue");
    }

    /**
     * Handle news read request from client.
     */
    public static void handleNewsRead(NewsReadPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "news_read", false);
            if (!validation.isSuccess()) {
                return;
            }

            observeFuture(NewsManager.INSTANCE.markAsRead(player.getUUID(), payload.articleId())
                .thenRun(() -> sendNewsSync(player)), "news read");
        }), "news read enqueue");
    }

    // ============================================================================
    // CLIENT-SIDE HANDLERS (Server -> Client)
    // Delegated to ClientNetworkPayloadHooks via NetworkHandler.withClientHooks()
    // ============================================================================

    // ============================================================================
    // SENDING METHODS (Server -> Client)
    // ============================================================================

    /**
     * Send full mailbox sync to a player.
     */
    public static void sendMailboxSync(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        int maxMessages = MailboxConfig.INSTANCE.getMaxMessagesPerPlayer();

        observeFuture(MailboxManager.INSTANCE.getMessages(playerUuid).thenCombine(
            MailboxManager.INSTANCE.getUnreadCount(playerUuid),
            (messages, unreadCount) -> {
                List<MailboxSyncPayload.MailboxMessageData> messageData = messages.stream()
                    .map(MailboxNetworkHandler::toMessageData)
                    .toList();

                return new MailboxSyncPayload(
                    messageData,
                    unreadCount,
                    maxMessages
                );
            }
        ).thenAccept(payload -> {
            PacketDistributor.sendToPlayer(player, Objects.requireNonNull(payload, "sync payload"));
            LOGGER.debug("[Mailbox] Sent sync to {}: {} messages", player.getName().getString(), payload.messages().size());
        }), "mailbox sync");
    }

    /**
     * Send mailbox access flags to a player.
     */
    public static void sendAccessSync(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        boolean isAdmin = MailboxPermissions.INSTANCE.isAdmin(playerUuid, player);
        boolean isTester = MailboxPermissions.INSTANCE.isTester(playerUuid, player);
        MailboxAccessPayload payload = new MailboxAccessPayload(isAdmin, isTester);
        PacketDistributor.sendToPlayer(Objects.requireNonNull(player, "player"), payload);
    }

    /**
     * Send new message notification to a player.
     */
    public static void sendNotification(ServerPlayer player, MailboxMessage message, int totalUnread) {
        MailboxNotifyPayload payload = new MailboxNotifyPayload(
            message.id(),
            message.senderName(),
            message.subject(),
            message.messageType().ordinal(),
            message.hasAttachment(),
            totalUnread
        );

        PacketDistributor.sendToPlayer(Objects.requireNonNull(player, "player"), payload);
        LOGGER.debug("[Mailbox] Sent notification to {}: {}", player.getName().getString(), message.subject());
    }

    /**
     * Send structured status feedback to a player.
     */
    private static void sendStatus(ServerPlayer player, MailboxStatusPayload.Action action,
            MailboxStatusPayload.Status status, String message) {
        MailboxStatusPayload payload = new MailboxStatusPayload(action, status, message);
        PacketDistributor.sendToPlayer(Objects.requireNonNull(player, "player"), payload);
    }

    private static MailboxStatusPayload.Action toStatusAction(MailboxActionPayload.Action action) {
        return switch (action) {
            case READ -> MailboxStatusPayload.Action.READ;
            case DELETE -> MailboxStatusPayload.Action.DELETE;
            case CLAIM -> MailboxStatusPayload.Action.CLAIM;
            case REFRESH -> MailboxStatusPayload.Action.REFRESH;
        };
    }

    /**
     * Send news sync to a player.
     */
    public static void sendNewsSync(ServerPlayer player) {
        UUID playerUuid = player.getUUID();

        observeFuture(NewsManager.INSTANCE.getActiveNews().thenCompose(articles -> {
            List<CompletableFuture<NewsSyncPayload.NewsArticleData>> futures = articles.stream()
                .map(article -> NewsManager.INSTANCE.hasPlayerReadNews(playerUuid, article.id())
                    .exceptionally(e -> false)
                    .thenApply(isRead -> toNewsArticleData(article, isRead)))
                .toList();

            CompletableFuture<?>[] futureArray = futures.toArray(new CompletableFuture<?>[0]);
            return CompletableFuture.allOf(futureArray)
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
        }).thenCombine(
            NewsManager.INSTANCE.getUnreadCount(playerUuid),
            (articleData, unreadCount) -> new NewsSyncPayload(articleData, unreadCount)
        ).thenAccept(payload -> {
            PacketDistributor.sendToPlayer(player, Objects.requireNonNull(payload, "news sync payload"));
            LOGGER.debug("[Mailbox] Sent news sync to {}: {} articles", player.getName().getString(), payload.articles().size());
        }), "news sync");
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private static MailboxSyncPayload.MailboxMessageData toMessageData(MailboxMessage msg) {
        Instant expiresAt = msg.expiresAt();
        return new MailboxSyncPayload.MailboxMessageData(
            msg.id(),
            msg.senderName(),
            msg.subject(),
            msg.body(),
            msg.messageType().ordinal(),
            msg.createdAt().toEpochMilli(),
            msg.isRead(),
            expiresAt != null ? expiresAt.toEpochMilli() : 0,
            msg.hasAttachment(),
            msg.attachmentClaimed(),
            msg.attachmentData()
        );
    }

    private static NewsSyncPayload.NewsArticleData toNewsArticleData(NewsArticle article, boolean isRead) {
        Instant publishedAt = article.publishedAt();
        return new NewsSyncPayload.NewsArticleData(
            article.id(),
            article.title(),
            article.content(),
            article.category().ordinal(),
            article.authorName(),
            publishedAt != null ? publishedAt.toEpochMilli() : Instant.now().toEpochMilli(),
            article.priority(),
            isRead
        );
    }

    @Nullable
    private static UUID resolveRecipientUuid(ServerPlayer sender, MailboxSendPayload payload) {
        UUID recipientUuid = payload.recipientUuid();
        if (!ZERO_UUID.equals(recipientUuid)) {
            return recipientUuid;
        }

        String recipientName = payload.recipientName();
        if (recipientName == null || recipientName.isBlank() || recipientName.length() > 64) {
            return null;
        }

        String trimmed = recipientName.trim();
        ServerPlayer online = sender.server.getPlayerList().getPlayerByName(trimmed);
        if (online != null) {
            return online.getUUID();
        }

        var profileCache = sender.server.getProfileCache();
        if (profileCache != null) {
            var profileOpt = profileCache.get(trimmed);
            if (profileOpt.isPresent()) {
                return profileOpt.get().getId();
            }
        }

        return null;
    }

    // ========================================================================
    // TASK HANDLERS (Server-side only - client sync delegated to ClientNetworkPayloadHooks)
    // ========================================================================

    /**
     * Handle task action from client.
     */
    public static void handleTaskAction(
            com.devmod.mailbox.network.payload.TaskActionPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        observeFuture(context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;
            if (!MailboxPermissions.INSTANCE.hasPermission(player, MailboxPermissions.Permission.TESTER)) {
                return;
            }

            switch (payload.action()) {
                case UPDATE_STATUS -> {
                    if (payload.statusId() != null) {
                        com.devmod.mailbox.task.TestTask.TaskStatus status =
                            com.devmod.mailbox.task.TestTask.TaskStatus.fromId(payload.statusId());
                        observeFuture(com.devmod.mailbox.task.TestTaskManager.INSTANCE.getTask(payload.taskId())
                            .thenAccept(task -> {
                                if (task != null && task.assignedTo().equals(player.getUUID())) {
                                    observeFuture(com.devmod.mailbox.task.TestTaskManager.INSTANCE
                                        .updateTask(task.withStatus(status)), "task update status");
                                    sendTaskSync(player);
                                }
                            }), "task fetch status");
                    }
                }
                case ADD_NOTES -> {
                    String notes = payload.notes();
                    if (notes != null) {
                        observeFuture(com.devmod.mailbox.task.TestTaskManager.INSTANCE.getTask(payload.taskId())
                            .thenAccept(task -> {
                                if (task != null && task.assignedTo().equals(player.getUUID())) {
                                    observeFuture(com.devmod.mailbox.task.TestTaskManager.INSTANCE
                                        .updateTask(task.withNotes(notes)), "task update notes");
                                    sendTaskSync(player);
                                }
                            }), "task fetch notes");
                    }
                }
            }
        }), "task action enqueue");
    }

    /**
     * Send task sync to a player.
     */
    public static void sendTaskSync(ServerPlayer player) {
        if (!MailboxPermissions.INSTANCE.hasPermission(player, MailboxPermissions.Permission.TESTER)) {
            return;
        }
        observeFuture(com.devmod.mailbox.task.TestTaskManager.INSTANCE.getTasksForUser(player.getUUID())
            .thenAccept(tasks -> {
                com.devmod.mailbox.network.payload.TaskSyncPayload payload =
                    com.devmod.mailbox.network.payload.TaskSyncPayload.fromTasks(tasks);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    Objects.requireNonNull(payload, "task sync payload"));
            }), "task sync");
    }
}
