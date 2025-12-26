package com.devmod.mailbox.network;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.mailbox.MailboxConfig;
import com.devmod.mailbox.MailboxManager;
import com.devmod.mailbox.MailboxMessage;
import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.news.NewsManager;
import com.devmod.mailbox.network.payload.MailboxActionPayload;
import com.devmod.mailbox.network.payload.MailboxNotifyPayload;
import com.devmod.mailbox.network.payload.MailboxSendPayload;
import com.devmod.mailbox.network.payload.MailboxSyncPayload;
import com.devmod.mailbox.network.payload.NewsReadPayload;
import com.devmod.mailbox.network.payload.NewsSyncPayload;
import com.devmod.network.handlers.NetworkHandlerBase;

/**
 * Network handler for mailbox-related packets.
 */
public final class MailboxNetworkHandler extends NetworkHandlerBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxNetworkHandler.class);

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
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "mailbox_send", false);
            if (!validation.isSuccess()) {
                player.sendSystemMessage(Component.literal("Rate limited. Please wait."));
                return;
            }

            MailboxManager.INSTANCE.sendPlayerMessage(
                player,
                payload.recipientUuid(),
                payload.subject(),
                payload.body(),
                payload.attachmentData()
            ).thenAccept(result -> {
                if (result.success()) {
                    player.sendSystemMessage(Component.literal("Message sent!"));
                    // Refresh sender's mailbox
                    sendMailboxSync(player);
                } else {
                    player.sendSystemMessage(Component.literal("Failed: " + result.error()));
                }
            });
        });
    }

    /**
     * Handle mailbox action (read, delete, claim) from client.
     */
    public static void handleAction(MailboxActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "mailbox_action", false);
            if (!validation.isSuccess()) {
                return;
            }

            UUID messageId = payload.messageId();

            switch (payload.action()) {
                case READ -> {
                    MailboxManager.INSTANCE.markAsRead(messageId)
                        .thenRun(() -> sendMailboxSync(player));
                }
                case DELETE -> {
                    MailboxManager.INSTANCE.deleteMessage(messageId)
                        .thenRun(() -> sendMailboxSync(player));
                }
                case CLAIM -> {
                    // First get the message to check if it has an attachment
                    MailboxManager.INSTANCE.getMessage(messageId).thenAccept(optMsg -> {
                        if (optMsg.isEmpty()) {
                            player.sendSystemMessage(Component.literal("Message not found"));
                            return;
                        }

                        MailboxMessage msg = optMsg.get();
                        if (!msg.canClaimAttachment()) {
                            player.sendSystemMessage(Component.literal("No attachment to claim"));
                            return;
                        }

                        // TODO: Process attachment (give items/currency to player)
                        // This requires integration with the game's inventory/economy system

                        MailboxManager.INSTANCE.claimAttachment(messageId)
                            .thenAccept(success -> {
                                if (success) {
                                    player.sendSystemMessage(Component.literal("Attachment claimed!"));
                                    sendMailboxSync(player);
                                } else {
                                    player.sendSystemMessage(Component.literal("Failed to claim attachment"));
                                }
                            });
                    });
                }
            }
        });
    }

    /**
     * Handle news read request from client.
     */
    public static void handleNewsRead(NewsReadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "news_read", false);
            if (!validation.isSuccess()) {
                return;
            }

            NewsManager.INSTANCE.markAsRead(player.getUUID(), payload.articleId())
                .thenRun(() -> sendNewsSync(player));
        });
    }

    // ============================================================================
    // CLIENT-SIDE HANDLERS (Server -> Client)
    // These are handled by ClientMailboxHandlers
    // ============================================================================

    /**
     * Handle mailbox sync on client.
     * Updates the ClientMailboxCache with received data.
     */
    public static void handleMailboxSyncClient(MailboxSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client cache
            com.devmod.mailbox.client.ClientMailboxCache.update(payload);
            LOGGER.debug("[Mailbox] Received mailbox sync: {} messages, {} unread",
                payload.messages().size(), payload.unreadCount());
        });
    }

    /**
     * Handle new message notification on client.
     * Shows a toast notification and updates cache.
     */
    public static void handleNotifyClient(MailboxNotifyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client cache and trigger notification
            com.devmod.mailbox.client.ClientMailboxCache.handleNotification(payload);
            LOGGER.debug("[Mailbox] New message notification: {} from {}",
                payload.subject(), payload.getDisplaySender());
        });
    }

    /**
     * Handle news sync on client.
     * Updates the ClientNewsCache with received data.
     */
    public static void handleNewsSyncClient(NewsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client cache
            com.devmod.mailbox.client.ClientNewsCache.update(payload);
            LOGGER.debug("[Mailbox] Received news sync: {} articles, {} unread",
                payload.articles().size(), payload.unreadCount());
        });
    }

    // ============================================================================
    // SENDING METHODS (Server -> Client)
    // ============================================================================

    /**
     * Send full mailbox sync to a player.
     */
    public static void sendMailboxSync(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        int maxMessages = MailboxConfig.INSTANCE.getMaxMessagesPerPlayer();

        MailboxManager.INSTANCE.getMessages(playerUuid).thenCombine(
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
            PacketDistributor.sendToPlayer(player, payload);
            LOGGER.debug("[Mailbox] Sent sync to {}: {} messages", player.getName().getString(), payload.messages().size());
        });
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

        PacketDistributor.sendToPlayer(player, payload);
        LOGGER.debug("[Mailbox] Sent notification to {}: {}", player.getName().getString(), message.subject());
    }

    /**
     * Send news sync to a player.
     */
    public static void sendNewsSync(ServerPlayer player) {
        UUID playerUuid = player.getUUID();

        NewsManager.INSTANCE.getActiveNews().thenCombine(
            NewsManager.INSTANCE.getUnreadCount(playerUuid),
            (articles, unreadCount) -> {
                // Map articles to data and check read status
                List<NewsSyncPayload.NewsArticleData> articleData = articles.stream()
                    .map(article -> toNewsArticleData(article, playerUuid))
                    .toList();

                return new NewsSyncPayload(articleData, unreadCount);
            }
        ).thenAccept(payload -> {
            PacketDistributor.sendToPlayer(player, payload);
            LOGGER.debug("[Mailbox] Sent news sync to {}: {} articles", player.getName().getString(), payload.articles().size());
        });
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private static MailboxSyncPayload.MailboxMessageData toMessageData(MailboxMessage msg) {
        return new MailboxSyncPayload.MailboxMessageData(
            msg.id(),
            msg.senderName(),
            msg.subject(),
            msg.body(),
            msg.messageType().ordinal(),
            msg.createdAt().toEpochMilli(),
            msg.isRead(),
            msg.expiresAt() != null ? msg.expiresAt().toEpochMilli() : 0,
            msg.hasAttachment(),
            msg.attachmentClaimed(),
            msg.attachmentData()
        );
    }

    private static NewsSyncPayload.NewsArticleData toNewsArticleData(NewsArticle article, UUID playerUuid) {
        // Note: isRead status would need to be fetched separately for accuracy
        // For now, we'll mark all as unread and let the client check
        return new NewsSyncPayload.NewsArticleData(
            article.id(),
            article.title(),
            article.content(),
            article.category().ordinal(),
            article.authorName(),
            article.publishedAt() != null ? article.publishedAt().toEpochMilli() : Instant.now().toEpochMilli(),
            article.priority(),
            false // isRead - would need async check
        );
    }
}
