package com.devmod.mailbox.config;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * P2 Architecture: Modular config records for MailboxConfig.
 * Each section represents a cohesive group of settings.
 */
public final class MailboxConfigSections {

    private MailboxConfigSections() {} // Utility class

    /**
     * Message limits configuration.
     */
    public record MessageLimits(
        int maxMessagesPerPlayer,
        int maxSubjectLength,
        int maxBodyLength,
        int defaultMessageTtlDays,
        int maxAttachmentsPerMessage
    ) {
        public static final MessageLimits DEFAULT = new MessageLimits(100, 128, 2000, 30, 5);

        public MessageLimits withValidation() {
            return new MessageLimits(
                clamp(maxMessagesPerPlayer, 10, 500),
                clamp(maxSubjectLength, 32, 256),
                clamp(maxBodyLength, 100, 10000),
                clamp(defaultMessageTtlDays, 1, 365),
                clamp(maxAttachmentsPerMessage, 0, 10)
            );
        }
    }

    /**
     * Rate limiting configuration.
     */
    public record RateLimiting(
        int maxMessagesPerMinute,
        int maxMessagesPerDay,
        int maxMessagesPerRecipientPerDay,
        int sendCooldownSeconds
    ) {
        public static final RateLimiting DEFAULT = new RateLimiting(10, 0, 0, 5);

        public RateLimiting withValidation() {
            return new RateLimiting(
                clamp(maxMessagesPerMinute, 1, 60),
                clamp(maxMessagesPerDay, 0, 10000),
                clamp(maxMessagesPerRecipientPerDay, 0, 10000),
                clamp(sendCooldownSeconds, 0, 300)
            );
        }
    }

    /**
     * Broadcast settings configuration.
     */
    public record BroadcastSettings(
        int batchSize,
        int batchDelayMs,
        boolean queueEnabled,
        int queueThreshold
    ) {
        public static final BroadcastSettings DEFAULT = new BroadcastSettings(500, 0, false, 1000);

        public BroadcastSettings withValidation() {
            return new BroadcastSettings(
                clamp(batchSize, 1, 5000),
                clamp(batchDelayMs, 0, 60000),
                queueEnabled,
                clamp(queueThreshold, 1, 1_000_000)
            );
        }
    }

    /**
     * Content filter configuration.
     */
    public record ContentFilter(
        boolean enabled,
        String action,
        List<String> words,
        List<String> patterns
    ) {
        public static final ContentFilter DEFAULT = new ContentFilter(true, "BLOCK", List.of(), List.of());

        public ContentFilter withValidation() {
            String normalizedAction = action != null ? action.toUpperCase(java.util.Locale.ROOT) : "BLOCK";
            if (!normalizedAction.equals("BLOCK") && !normalizedAction.equals("FLAG") && !normalizedAction.equals("CENSOR")) {
                normalizedAction = "BLOCK";
            }
            return new ContentFilter(
                enabled,
                normalizedAction,
                words != null ? List.copyOf(words) : List.of(),
                patterns != null ? List.copyOf(patterns) : List.of()
            );
        }
    }

    /**
     * Player messaging settings.
     */
    public record PlayerMessaging(
        boolean playerToPlayerEnabled,
        int minLevelToSend,
        boolean itemAttachmentsEnabled,
        boolean currencyAttachmentsEnabled
    ) {
        public static final PlayerMessaging DEFAULT = new PlayerMessaging(true, 0, true, true);

        public PlayerMessaging withValidation() {
            return new PlayerMessaging(
                playerToPlayerEnabled,
                Math.max(0, minLevelToSend),
                itemAttachmentsEnabled,
                currencyAttachmentsEnabled
            );
        }
    }

    /**
     * Attachment rules configuration.
     */
    public record AttachmentRules(
        boolean itemWhitelistEnabled,
        List<String> itemWhitelist,
        List<String> itemBlacklist,
        List<String> currencyAllowed,
        Map<String, Integer> currencyMaxAmounts
    ) {
        public static final AttachmentRules DEFAULT = new AttachmentRules(
            false, List.of(), List.of(), List.of(), Map.of()
        );

        public AttachmentRules withValidation() {
            return new AttachmentRules(
                itemWhitelistEnabled,
                itemWhitelist != null ? List.copyOf(itemWhitelist) : List.of(),
                itemBlacklist != null ? List.copyOf(itemBlacklist) : List.of(),
                currencyAllowed != null ? List.copyOf(currencyAllowed) : List.of(),
                currencyMaxAmounts != null ? Map.copyOf(currencyMaxAmounts) : Map.of()
            );
        }
    }

    /**
     * News settings configuration.
     */
    public record NewsSettings(
        int maxArticles,
        int defaultTtlDays
    ) {
        public static final NewsSettings DEFAULT = new NewsSettings(50, 90);

        public NewsSettings withValidation() {
            return new NewsSettings(
                clamp(maxArticles, 10, 200),
                clamp(defaultTtlDays, 7, 365)
            );
        }
    }

    /**
     * API settings configuration.
     */
    public record ApiSettings(
        int port,
        boolean enabled,
        String secretKey,
        List<String> allowedOrigins
    ) {
        public static final ApiSettings DEFAULT = new ApiSettings(
            8765, false, "",
            List.of("http://localhost:5173", "http://127.0.0.1:5173",
                    "http://localhost:4173", "http://127.0.0.1:4173")
        );

        public ApiSettings withValidation() {
            return new ApiSettings(
                clamp(port, 1024, 65535),
                enabled,
                secretKey != null ? secretKey : "",
                allowedOrigins != null ? List.copyOf(allowedOrigins) : List.of()
            );
        }
    }

    /**
     * Permission/role settings configuration.
     */
    public record PermissionSettings(
        boolean useOpLevelForRoles,
        List<String> adminUuids,
        List<String> testerUuids,
        List<String> blockedSenderUuids,
        List<String> blockedReceiverUuids,
        boolean maintenanceMode,
        int messageRetentionDays,
        boolean hardDeleteOnUserDelete
    ) {
        public static final PermissionSettings DEFAULT = new PermissionSettings(
            true, List.of(), List.of(), List.of(), List.of(), false, 30, false
        );

        public PermissionSettings withValidation() {
            return new PermissionSettings(
                useOpLevelForRoles,
                adminUuids != null ? List.copyOf(adminUuids) : List.of(),
                testerUuids != null ? List.copyOf(testerUuids) : List.of(),
                blockedSenderUuids != null ? List.copyOf(blockedSenderUuids) : List.of(),
                blockedReceiverUuids != null ? List.copyOf(blockedReceiverUuids) : List.of(),
                maintenanceMode,
                clamp(messageRetentionDays, 1, 365),
                hardDeleteOnUserDelete
            );
        }
    }

    /**
     * Complete mailbox configuration composed of all sections.
     */
    public record CompleteConfig(
        @Nullable MessageLimits messageLimits,
        @Nullable RateLimiting rateLimiting,
        @Nullable BroadcastSettings broadcast,
        @Nullable ContentFilter contentFilter,
        @Nullable PlayerMessaging playerMessaging,
        @Nullable AttachmentRules attachmentRules,
        @Nullable NewsSettings news,
        @Nullable ApiSettings api,
        @Nullable PermissionSettings permissions
    ) {
        public static final CompleteConfig DEFAULT = new CompleteConfig(
            MessageLimits.DEFAULT,
            RateLimiting.DEFAULT,
            BroadcastSettings.DEFAULT,
            ContentFilter.DEFAULT,
            PlayerMessaging.DEFAULT,
            AttachmentRules.DEFAULT,
            NewsSettings.DEFAULT,
            ApiSettings.DEFAULT,
            PermissionSettings.DEFAULT
        );

        public MessageLimits messageLimitsOrDefault() {
            return messageLimits != null ? messageLimits : MessageLimits.DEFAULT;
        }

        public RateLimiting rateLimitingOrDefault() {
            return rateLimiting != null ? rateLimiting : RateLimiting.DEFAULT;
        }

        public BroadcastSettings broadcastOrDefault() {
            return broadcast != null ? broadcast : BroadcastSettings.DEFAULT;
        }

        public ContentFilter contentFilterOrDefault() {
            return contentFilter != null ? contentFilter : ContentFilter.DEFAULT;
        }

        public PlayerMessaging playerMessagingOrDefault() {
            return playerMessaging != null ? playerMessaging : PlayerMessaging.DEFAULT;
        }

        public AttachmentRules attachmentRulesOrDefault() {
            return attachmentRules != null ? attachmentRules : AttachmentRules.DEFAULT;
        }

        public NewsSettings newsOrDefault() {
            return news != null ? news : NewsSettings.DEFAULT;
        }

        public ApiSettings apiOrDefault() {
            return api != null ? api : ApiSettings.DEFAULT;
        }

        public PermissionSettings permissionsOrDefault() {
            return permissions != null ? permissions : PermissionSettings.DEFAULT;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
