package com.devmod.notification;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable notification record for the Unified Notification Center.
 * Represents a single notification that can be routed to overlay, mailbox, or chat.
 */
public record Notification(
        UUID id,
        NotificationCategory category,
        NotificationPriority priority,
        String titleKey,
        @Nullable String messageKey,
        Map<String, String> params,
        @Nullable String iconId,
        @Nullable String soundId,
        @Nullable String actionId,
        @Nullable String actionDataJson,
        Instant createdAt,
        @Nullable UUID relatedEntityId,
        boolean persistToMailbox,
        boolean showOverlay,
        int displayDurationMs
) {
    /**
     * Compact constructor with validation and defensive copies.
     */
    public Notification {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(titleKey, "titleKey must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        // Defensive copy of params
        params = params != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(params))
                : Collections.emptyMap();

        // Use default duration from priority if not specified
        if (displayDurationMs <= 0) {
            displayDurationMs = priority.getDefaultDurationMs();
        }
    }

    /**
     * Create a builder for this category.
     */
    public static Builder builder(NotificationCategory category) {
        return new Builder(category);
    }

    /**
     * Create a simple notification with minimal configuration.
     */
    public static Notification simple(NotificationCategory category, String titleKey) {
        return builder(category).titleKey(titleKey).build();
    }

    /**
     * Create a notification with title and message.
     */
    public static Notification withMessage(NotificationCategory category, String titleKey, String messageKey) {
        return builder(category).titleKey(titleKey).messageKey(messageKey).build();
    }

    /**
     * Check if this notification should use banner display based on priority.
     */
    public boolean shouldUseBanner() {
        return priority.shouldUseBannerDisplay();
    }

    /**
     * Get a parameter value by key.
     */
    @Nullable
    public String getParam(String key) {
        return params.get(key);
    }

    /**
     * Get a parameter value with default.
     */
    public String getParam(String key, String defaultValue) {
        return params.getOrDefault(key, defaultValue);
    }

    /**
     * Builder for Notification.
     */
    public static class Builder {
        @Nullable
        private UUID id;
        private final NotificationCategory category;
        private NotificationPriority priority = NotificationPriority.NORMAL;
        @Nullable
        private String titleKey;
        @Nullable
        private String messageKey;
        @Nullable
        private Map<String, String> params;
        @Nullable
        private String iconId;
        @Nullable
        private String soundId;
        @Nullable
        private String actionId;
        @Nullable
        private String actionDataJson;
        @Nullable
        private Instant createdAt;
        @Nullable
        private UUID relatedEntityId;
        @Nullable
        private Boolean persistToMailbox;
        @Nullable
        private Boolean showOverlay;
        private int displayDurationMs;

        public Builder(NotificationCategory category) {
            this.category = Objects.requireNonNull(category, "category must not be null");
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder priority(NotificationPriority priority) {
            this.priority = Objects.requireNonNull(priority, "priority must not be null");
            return this;
        }

        public Builder titleKey(String titleKey) {
            this.titleKey = Objects.requireNonNull(titleKey, "titleKey must not be null");
            return this;
        }

        public Builder messageKey(@Nullable String messageKey) {
            this.messageKey = messageKey;
            return this;
        }

        public Builder params(@Nullable Map<String, String> params) {
            this.params = params;
            return this;
        }

        public Builder param(String key, String value) {
            if (this.params == null) {
                this.params = new LinkedHashMap<>();
            }
            this.params.put(key, value);
            return this;
        }

        public Builder param(String key, int value) {
            return param(key, String.valueOf(value));
        }

        public Builder param(String key, long value) {
            return param(key, String.valueOf(value));
        }

        public Builder param(String key, double value) {
            return param(key, String.valueOf(value));
        }

        public Builder iconId(@Nullable String iconId) {
            this.iconId = iconId;
            return this;
        }

        public Builder soundId(@Nullable String soundId) {
            this.soundId = soundId;
            return this;
        }

        public Builder actionId(@Nullable String actionId) {
            this.actionId = actionId;
            return this;
        }

        public Builder actionDataJson(@Nullable String actionDataJson) {
            this.actionDataJson = actionDataJson;
            return this;
        }

        public Builder createdAt(@Nullable Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder relatedEntityId(@Nullable UUID relatedEntityId) {
            this.relatedEntityId = relatedEntityId;
            return this;
        }

        public Builder persistToMailbox(boolean persistToMailbox) {
            this.persistToMailbox = persistToMailbox;
            return this;
        }

        public Builder showOverlay(boolean showOverlay) {
            this.showOverlay = showOverlay;
            return this;
        }

        public Builder displayDurationMs(int displayDurationMs) {
            this.displayDurationMs = displayDurationMs;
            return this;
        }

        /**
         * Build the notification.
         */
        public Notification build() {
            // Generate ID if not provided
            if (id == null) {
                id = UUID.randomUUID();
            }

            // Use current time if not provided
            if (createdAt == null) {
                createdAt = Instant.now();
            }

            // Use category defaults if not explicitly set
            boolean mailbox = persistToMailbox != null
                    ? persistToMailbox
                    : category.isDefaultMailboxEnabled();
            boolean overlay = showOverlay != null
                    ? showOverlay
                    : category.isDefaultOverlayEnabled();

            String resolvedTitleKey = Objects.requireNonNull(titleKey, "titleKey must not be null");
            Map<String, String> resolvedParams = params == null ? Collections.emptyMap() : params;

            return new Notification(
                    id,
                    category,
                    priority,
                    resolvedTitleKey,
                    messageKey,
                    resolvedParams,
                    iconId,
                    soundId,
                    actionId,
                    actionDataJson,
                    createdAt,
                    relatedEntityId,
                    mailbox,
                    overlay,
                    displayDurationMs
            );
        }
    }
}
