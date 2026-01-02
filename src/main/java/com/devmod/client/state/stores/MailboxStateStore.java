package com.devmod.client.state.stores;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.state.BaseStateStore;

/**
 * Client-side store for mailbox messages.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Inbox messages</li>
 *   <li>Sent messages</li>
 *   <li>Unread count</li>
 *   <li>News articles</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class MailboxStateStore extends BaseStateStore<MailboxStateStore.MailboxState> {

    public MailboxStateStore() {
        super("MailboxStore");
    }

    /**
     * Get unread message count.
     */
    public int getUnreadCount() {
        MailboxState state = getState();
        return state != null ? state.unreadCount() : 0;
    }

    /**
     * Check if there are unread messages.
     */
    public boolean hasUnread() {
        return getUnreadCount() > 0;
    }

    /**
     * Get message by ID.
     */
    public Optional<MailMessage> getMessage(UUID messageId) {
        MailboxState state = getState();
        if (state == null) return Optional.empty();

        return state.messages().stream()
            .filter(m -> m.id().equals(messageId))
            .findFirst();
    }

    /**
     * Get unread news count.
     */
    public int getUnreadNewsCount() {
        MailboxState state = getState();
        return state != null ? state.unreadNewsCount() : 0;
    }

    /**
     * Mail message data.
     */
    public record MailMessage(
        UUID id,
        UUID senderId,
        String senderName,
        String subject,
        String body,
        long sentTime,
        boolean isRead,
        boolean hasAttachment,
        @Nullable String attachmentData,
        MessagePriority priority,
        MessageType type
    ) {
        /**
         * Get age of message in hours.
         */
        public long getAgeHours() {
            return (System.currentTimeMillis() - sentTime) / (1000 * 60 * 60);
        }

        /**
         * Get formatted time string.
         */
        public String getFormattedTime() {
            long ageHours = getAgeHours();
            if (ageHours < 1) return "Just now";
            if (ageHours < 24) return ageHours + "h ago";
            long days = ageHours / 24;
            if (days < 7) return days + "d ago";
            return days / 7 + "w ago";
        }
    }

    /**
     * News article data.
     */
    public record NewsArticle(
        String id,
        String title,
        String content,
        long publishedAt,
        boolean isRead,
        NewsCategory category
    ) {}

    /**
     * Message priority levels.
     */
    public enum MessagePriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    /**
     * Message types.
     */
    public enum MessageType {
        PLAYER,
        SYSTEM,
        PARTY,
        REWARD,
        ANNOUNCEMENT
    }

    /**
     * News categories.
     */
    public enum NewsCategory {
        UPDATE,
        EVENT,
        MAINTENANCE,
        COMMUNITY,
        GENERAL
    }

    /**
     * Immutable mailbox state snapshot.
     */
    public record MailboxState(
        List<MailMessage> messages,
        List<NewsArticle> news,
        int unreadCount,
        int unreadNewsCount,
        int totalMessages,
        int maxMessages,
        long serverTimestamp
    ) {
        /**
         * Check if mailbox is full.
         */
        public boolean isFull() {
            return totalMessages >= maxMessages;
        }

        /**
         * Get percentage of mailbox used.
         */
        public float getUsagePercent() {
            return maxMessages > 0 ? (float) totalMessages / maxMessages : 0f;
        }

        /**
         * Create empty state.
         */
        public static MailboxState empty() {
            return new MailboxState(
                List.of(),
                List.of(),
                0,
                0,
                0,
                100,
                System.currentTimeMillis()
            );
        }
    }
}
