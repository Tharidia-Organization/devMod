package com.devmod.client.state.stores;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.state.BaseStateStore;

/**
 * Client-side store for notifications.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Active notifications (toasts)</li>
 *   <li>Notification history</li>
 *   <li>Unread notification count</li>
 *   <li>User notification preferences</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class NotificationStateStore extends BaseStateStore<NotificationStateStore.NotificationState> {

    // Queue of pending notifications to display
    private final List<Notification> pendingToasts = new ArrayList<>();

    public NotificationStateStore() {
        super("NotificationStore");
    }

    /**
     * Get unread notification count.
     */
    public int getUnreadCount() {
        NotificationState state = getState();
        return state != null ? state.unreadCount() : 0;
    }

    /**
     * Check if there are unread notifications.
     */
    public boolean hasUnread() {
        return getUnreadCount() > 0;
    }

    /**
     * Add a notification to the pending toast queue.
     */
    public void queueToast(Notification notification) {
        synchronized (pendingToasts) {
            pendingToasts.add(notification);
        }
    }

    /**
     * Get and remove the next pending toast.
     */
    @Nullable
    public Notification pollNextToast() {
        synchronized (pendingToasts) {
            return pendingToasts.isEmpty() ? null : pendingToasts.remove(0);
        }
    }

    /**
     * Check if there are pending toasts.
     */
    public boolean hasPendingToasts() {
        synchronized (pendingToasts) {
            return !pendingToasts.isEmpty();
        }
    }

    /**
     * Clear pending toasts.
     */
    public void clearPendingToasts() {
        synchronized (pendingToasts) {
            pendingToasts.clear();
        }
    }

    @Override
    protected void onCleared() {
        clearPendingToasts();
    }

    /**
     * Notification data.
     */
    public record Notification(
        UUID id,
        String title,
        String message,
        NotificationType type,
        NotificationPriority priority,
        long timestamp,
        boolean isRead,
        @Nullable String actionId,
        @Nullable String actionData,
        int durationMs
    ) {
        /**
         * Default display duration for toasts.
         */
        public static final int DEFAULT_DURATION_MS = 5000;
        public static final int SHORT_DURATION_MS = 3000;
        public static final int LONG_DURATION_MS = 8000;

        /**
         * Check if notification has an action.
         */
        public boolean hasAction() {
            return actionId != null;
        }

        /**
         * Get age of notification in seconds.
         */
        public long getAgeSeconds() {
            return (System.currentTimeMillis() - timestamp) / 1000;
        }

        /**
         * Builder for notifications.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Create a simple info notification.
         */
        public static Notification info(String title, String message) {
            return builder()
                .title(title)
                .message(message)
                .type(NotificationType.INFO)
                .priority(NotificationPriority.NORMAL)
                .build();
        }

        /**
         * Create a success notification.
         */
        public static Notification success(String title, String message) {
            return builder()
                .title(title)
                .message(message)
                .type(NotificationType.SUCCESS)
                .priority(NotificationPriority.NORMAL)
                .build();
        }

        /**
         * Create a warning notification.
         */
        public static Notification warning(String title, String message) {
            return builder()
                .title(title)
                .message(message)
                .type(NotificationType.WARNING)
                .priority(NotificationPriority.HIGH)
                .build();
        }

        /**
         * Create an error notification.
         */
        public static Notification error(String title, String message) {
            return builder()
                .title(title)
                .message(message)
                .type(NotificationType.ERROR)
                .priority(NotificationPriority.URGENT)
                .build();
        }

        /**
         * Notification builder.
         */
        public static class Builder {
            private UUID id = UUID.randomUUID();
            private String title = "";
            private String message = "";
            private NotificationType type = NotificationType.INFO;
            private NotificationPriority priority = NotificationPriority.NORMAL;
            private boolean isRead = false;
            @Nullable private String actionId = null;
            @Nullable private String actionData = null;
            private int durationMs = DEFAULT_DURATION_MS;

            public Builder id(UUID id) {
                this.id = id;
                return this;
            }

            public Builder title(String title) {
                this.title = title;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder type(NotificationType type) {
                this.type = type;
                return this;
            }

            public Builder priority(NotificationPriority priority) {
                this.priority = priority;
                return this;
            }

            public Builder read(boolean isRead) {
                this.isRead = isRead;
                return this;
            }

            public Builder action(String actionId, @Nullable String actionData) {
                this.actionId = actionId;
                this.actionData = actionData;
                return this;
            }

            public Builder duration(int durationMs) {
                this.durationMs = durationMs;
                return this;
            }

            public Notification build() {
                return new Notification(
                    id,
                    title,
                    message,
                    type,
                    priority,
                    System.currentTimeMillis(),
                    isRead,
                    actionId,
                    actionData,
                    durationMs
                );
            }
        }
    }

    /**
     * Notification types.
     */
    public enum NotificationType {
        INFO,
        SUCCESS,
        WARNING,
        ERROR,
        QUEST,
        PARTY,
        ACHIEVEMENT,
        SYSTEM
    }

    /**
     * Notification priority levels.
     */
    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    /**
     * User notification preferences.
     */
    public record NotificationPreferences(
        boolean soundEnabled,
        boolean toastsEnabled,
        boolean questNotifications,
        boolean partyNotifications,
        boolean mailNotifications,
        boolean achievementNotifications,
        float volume
    ) {
        /**
         * Default preferences.
         */
        public static NotificationPreferences defaults() {
            return new NotificationPreferences(
                true,
                true,
                true,
                true,
                true,
                true,
                1.0f
            );
        }
    }

    /**
     * Immutable notification state snapshot.
     */
    public record NotificationState(
        List<Notification> notifications,
        int unreadCount,
        int totalCount,
        NotificationPreferences preferences,
        long serverTimestamp
    ) {
        /**
         * Get notifications by type.
         */
        public List<Notification> getByType(NotificationType type) {
            return notifications.stream()
                .filter(n -> n.type() == type)
                .toList();
        }

        /**
         * Get unread notifications.
         */
        public List<Notification> getUnread() {
            return notifications.stream()
                .filter(n -> !n.isRead())
                .toList();
        }

        /**
         * Create empty state.
         */
        public static NotificationState empty() {
            return new NotificationState(
                List.of(),
                0,
                0,
                NotificationPreferences.defaults(),
                System.currentTimeMillis()
            );
        }
    }
}
