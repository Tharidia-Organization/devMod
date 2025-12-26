package com.devmod.mailbox.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.mailbox.network.payload.MailboxNotifyPayload;
import com.devmod.mailbox.network.payload.MailboxSyncPayload;
import com.devmod.mailbox.network.payload.MailboxSyncPayload.MailboxMessageData;

/**
 * Client-side cache for mailbox data.
 * Stores synced messages and notifications for UI display.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientMailboxCache {

    @Nullable
    private static volatile MailboxSyncPayload cachedSync = null;
    private static volatile long lastSyncTime = 0;

    // Notifications queue for toast display
    private static final List<MailboxNotifyPayload> pendingNotifications = new ArrayList<>();
    private static volatile long lastNotificationTime = 0;

    public static final long NOTIFICATION_DISPLAY_MS = 5000;
    public static final long CACHE_STALE_MS = 60000; // 1 minute

    private ClientMailboxCache() {}

    // ========== SYNC HANDLING ==========

    /**
     * Update the cache with a full sync from server.
     */
    public static void update(MailboxSyncPayload payload) {
        cachedSync = payload;
        lastSyncTime = System.currentTimeMillis();
    }

    /**
     * Handle a new message notification.
     */
    public static void handleNotification(MailboxNotifyPayload notification) {
        synchronized (pendingNotifications) {
            pendingNotifications.add(notification);
            lastNotificationTime = System.currentTimeMillis();
        }

        // Also add to cached messages if we have a sync
        MailboxSyncPayload sync = cachedSync;
        if (sync != null) {
            List<MailboxMessageData> newMessages = new ArrayList<>(sync.messages());
            newMessages.add(0, new MailboxMessageData(
                notification.messageId(),
                notification.senderName(),
                notification.subject(),
                null, // body loaded on detail view
                notification.messageTypeOrdinal(),
                System.currentTimeMillis(),
                false, // isRead
                0, // expiresAt
                notification.hasAttachment(),
                false, // attachmentClaimed
                null // attachmentData
            ));
            cachedSync = new MailboxSyncPayload(
                newMessages,
                sync.unreadCount() + 1,
                sync.maxMessages()
            );
        }
    }

    /**
     * Mark a message as read locally (optimistic update).
     */
    public static void markAsRead(UUID messageId) {
        MailboxSyncPayload sync = cachedSync;
        if (sync == null) return;

        List<MailboxMessageData> updated = new ArrayList<>();
        int unreadDelta = 0;

        for (MailboxMessageData msg : sync.messages()) {
            if (msg.id().equals(messageId) && !msg.isRead()) {
                updated.add(new MailboxMessageData(
                    msg.id(),
                    msg.senderName(),
                    msg.subject(),
                    msg.body(),
                    msg.messageTypeOrdinal(),
                    msg.createdAtMillis(),
                    true, // now read
                    msg.expiresAtMillis(),
                    msg.hasAttachment(),
                    msg.attachmentClaimed(),
                    msg.attachmentData()
                ));
                unreadDelta = -1;
            } else {
                updated.add(msg);
            }
        }

        cachedSync = new MailboxSyncPayload(
            updated,
            Math.max(0, sync.unreadCount() + unreadDelta),
            sync.maxMessages()
        );
    }

    /**
     * Mark attachment as claimed locally (optimistic update).
     */
    public static void markAttachmentClaimed(UUID messageId) {
        MailboxSyncPayload sync = cachedSync;
        if (sync == null) return;

        List<MailboxMessageData> updated = new ArrayList<>();

        for (MailboxMessageData msg : sync.messages()) {
            if (msg.id().equals(messageId)) {
                updated.add(new MailboxMessageData(
                    msg.id(),
                    msg.senderName(),
                    msg.subject(),
                    msg.body(),
                    msg.messageTypeOrdinal(),
                    msg.createdAtMillis(),
                    msg.isRead(),
                    msg.expiresAtMillis(),
                    msg.hasAttachment(),
                    true, // now claimed
                    msg.attachmentData()
                ));
            } else {
                updated.add(msg);
            }
        }

        cachedSync = new MailboxSyncPayload(updated, sync.unreadCount(), sync.maxMessages());
    }

    /**
     * Remove a message locally (optimistic update).
     */
    public static void removeMessage(UUID messageId) {
        MailboxSyncPayload sync = cachedSync;
        if (sync == null) return;

        List<MailboxMessageData> updated = new ArrayList<>();
        int unreadDelta = 0;

        for (MailboxMessageData msg : sync.messages()) {
            if (msg.id().equals(messageId)) {
                if (!msg.isRead()) {
                    unreadDelta = -1;
                }
                // Skip - effectively deletes
            } else {
                updated.add(msg);
            }
        }

        cachedSync = new MailboxSyncPayload(
            updated,
            Math.max(0, sync.unreadCount() + unreadDelta),
            sync.maxMessages()
        );
    }

    /**
     * Clear all cached data.
     */
    public static void clear() {
        cachedSync = null;
        lastSyncTime = 0;
        synchronized (pendingNotifications) {
            pendingNotifications.clear();
        }
    }

    // ========== GETTERS ==========

    /**
     * Check if we have any cached data.
     */
    public static boolean hasCachedData() {
        return cachedSync != null;
    }

    /**
     * Check if cache is stale.
     */
    public static boolean isStale() {
        return System.currentTimeMillis() - lastSyncTime > CACHE_STALE_MS;
    }

    /**
     * Get the cached sync payload.
     */
    @Nullable
    public static MailboxSyncPayload getSync() {
        return cachedSync;
    }

    /**
     * Get all messages.
     */
    public static List<MailboxMessageData> getMessages() {
        MailboxSyncPayload sync = cachedSync;
        return sync != null ? sync.messages() : List.of();
    }

    /**
     * Get a specific message by ID.
     */
    @Nullable
    public static MailboxMessageData getMessage(UUID messageId) {
        MailboxSyncPayload sync = cachedSync;
        if (sync == null) return null;

        return sync.messages().stream()
            .filter(m -> m.id().equals(messageId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get unread count.
     */
    public static int getUnreadCount() {
        MailboxSyncPayload sync = cachedSync;
        return sync != null ? sync.unreadCount() : 0;
    }

    /**
     * Get total message count.
     */
    public static int getMessageCount() {
        MailboxSyncPayload sync = cachedSync;
        return sync != null ? sync.messages().size() : 0;
    }

    /**
     * Get max allowed messages.
     */
    public static int getMaxMessages() {
        MailboxSyncPayload sync = cachedSync;
        return sync != null ? sync.maxMessages() : 100;
    }

    /**
     * Check if mailbox is full.
     */
    public static boolean isFull() {
        return getMessageCount() >= getMaxMessages();
    }

    // ========== NOTIFICATIONS ==========

    /**
     * Get the next pending notification for display.
     * Returns null if no notifications or display time expired.
     */
    @Nullable
    public static MailboxNotifyPayload getDisplayableNotification() {
        synchronized (pendingNotifications) {
            if (pendingNotifications.isEmpty()) return null;
            if (System.currentTimeMillis() - lastNotificationTime > NOTIFICATION_DISPLAY_MS) {
                pendingNotifications.clear();
                return null;
            }
            return pendingNotifications.get(0);
        }
    }

    /**
     * Dismiss the current notification.
     */
    public static void dismissNotification() {
        synchronized (pendingNotifications) {
            if (!pendingNotifications.isEmpty()) {
                pendingNotifications.remove(0);
                lastNotificationTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * Check if there are pending notifications.
     */
    public static boolean hasPendingNotifications() {
        synchronized (pendingNotifications) {
            return !pendingNotifications.isEmpty();
        }
    }

    /**
     * Get count of pending notifications.
     */
    public static int getPendingNotificationCount() {
        synchronized (pendingNotifications) {
            return pendingNotifications.size();
        }
    }

    // ========== CONVENIENCE METHODS FOR HUD ==========

    /**
     * Check if there are unread messages.
     */
    public static boolean hasUnread() {
        return getUnreadCount() > 0;
    }

    /**
     * Get messages with unclaimed attachments.
     */
    public static List<MailboxMessageData> getUnclaimedAttachmentMessages() {
        MailboxSyncPayload sync = cachedSync;
        if (sync == null) return List.of();

        return sync.messages().stream()
            .filter(MailboxMessageData::canClaimAttachment)
            .toList();
    }

    /**
     * Check if there are unclaimed attachments.
     */
    public static boolean hasUnclaimedAttachments() {
        return !getUnclaimedAttachmentMessages().isEmpty();
    }

    /**
     * Get time since last sync in ms.
     */
    public static long getTimeSinceSync() {
        return System.currentTimeMillis() - lastSyncTime;
    }
}
