package com.devmod.mailbox.api.controllers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import com.devmod.DevMod;
import com.devmod.mailbox.MailboxConfig;
import com.devmod.mailbox.MailboxManager;
import com.devmod.mailbox.api.AuthMiddleware;
import com.devmod.mailbox.moderation.AdminAuditLog;
import com.devmod.mailbox.news.NewsManager;
import com.devmod.mailbox.task.TestTaskManager;
import com.devmod.mailbox.ticket.AutoTransitionService;

/**
 * REST API controller for configuration and statistics.
 */
public final class ConfigController {

    private ConfigController() {}

    /**
     * GET /api/config
     * Get current mailbox configuration.
     */
    public static void getConfig(Context ctx) {
        MailboxConfig config = MailboxConfig.INSTANCE;
        ctx.json(new ConfigDto(
            config.isApiEnabled(),
            config.isPlayerToPlayerEnabled(),
            config.getMaxMessagesPerPlayer(),
            config.getMaxSubjectLength(),
            config.getMaxBodyLength(),
            config.getDefaultMessageTtl().toHours(),
            config.getMaxMessagesPerMinute(),
            config.getMaxMessagesPerDay(),
            config.getMaxMessagesPerRecipientPerDay(),
            config.getSendCooldownSeconds(),
            config.getBroadcastBatchSize(),
            config.getBroadcastBatchDelayMs(),
            config.isBroadcastQueueEnabled(),
            config.getBroadcastQueueThreshold(),
            config.getMinLevelToSend(),
            config.isItemAttachmentsEnabled(),
            config.isCurrencyAttachmentsEnabled(),
            config.isItemAttachmentWhitelistEnabled(),
            config.getItemAttachmentWhitelist(),
            config.getItemAttachmentBlacklist(),
            config.getCurrencyAttachmentAllowed(),
            config.getCurrencyAttachmentMaxAmounts(),
            config.getMaxAttachmentsPerMessage(),
            config.getMessageRetentionDays(),
            config.isHardDeleteOnUserDelete(),
            config.isMaintenanceMode(),
            config.isUseOpLevelForRoles(),
            config.isContentFilterEnabled(),
            config.getContentFilterAction(),
            config.getContentFilterWords(),
            config.getContentFilterPatterns()
        ));
    }

    /**
     * PUT /api/config
     * Update mailbox configuration (partial update).
     */
    public static void updateConfig(Context ctx) {
        UpdateConfigRequest request = ctx.bodyAsClass(UpdateConfigRequest.class);
        MailboxConfig config = MailboxConfig.INSTANCE;
        List<String> changes = new ArrayList<>();
        recordChange(changes, "enabled", config.isApiEnabled(), request.enabled());
        recordChange(changes, "playerToPlayerEnabled", config.isPlayerToPlayerEnabled(), request.playerToPlayerEnabled());
        recordChange(changes, "maxMessagesPerPlayer", config.getMaxMessagesPerPlayer(), request.maxMessagesPerPlayer());
        recordChange(changes, "maxSubjectLength", config.getMaxSubjectLength(), request.maxSubjectLength());
        recordChange(changes, "maxBodyLength", config.getMaxBodyLength(), request.maxBodyLength());
        recordChange(changes, "defaultMessageTtlHours", config.getDefaultMessageTtl().toHours(), request.defaultMessageTtlHours());
        recordChange(changes, "maxMessagesPerMinute", config.getMaxMessagesPerMinute(), request.maxMessagesPerMinute());
        recordChange(changes, "maxMessagesPerDay", config.getMaxMessagesPerDay(), request.maxMessagesPerDay());
        recordChange(changes, "maxMessagesPerRecipientPerDay", config.getMaxMessagesPerRecipientPerDay(), request.maxMessagesPerRecipientPerDay());
        recordChange(changes, "sendCooldownSeconds", config.getSendCooldownSeconds(), request.sendCooldownSeconds());
        recordChange(changes, "broadcastBatchSize", config.getBroadcastBatchSize(), request.broadcastBatchSize());
        recordChange(changes, "broadcastBatchDelayMs", config.getBroadcastBatchDelayMs(), request.broadcastBatchDelayMs());
        recordChange(changes, "broadcastQueueEnabled", config.isBroadcastQueueEnabled(), request.broadcastQueueEnabled());
        recordChange(changes, "broadcastQueueThreshold", config.getBroadcastQueueThreshold(), request.broadcastQueueThreshold());
        recordChange(changes, "minLevelToSend", config.getMinLevelToSend(), request.minLevelToSend());
        recordChange(changes, "itemAttachmentsEnabled", config.isItemAttachmentsEnabled(), request.itemAttachmentsEnabled());
        recordChange(changes, "currencyAttachmentsEnabled", config.isCurrencyAttachmentsEnabled(), request.currencyAttachmentsEnabled());
        recordChange(changes, "itemAttachmentWhitelistEnabled", config.isItemAttachmentWhitelistEnabled(), request.itemAttachmentWhitelistEnabled());
        recordChange(changes, "itemAttachmentWhitelist", config.getItemAttachmentWhitelist(), request.itemAttachmentWhitelist());
        recordChange(changes, "itemAttachmentBlacklist", config.getItemAttachmentBlacklist(), request.itemAttachmentBlacklist());
        recordChange(changes, "currencyAttachmentAllowed", config.getCurrencyAttachmentAllowed(), request.currencyAttachmentAllowed());
        recordChange(changes, "currencyAttachmentMaxAmounts", config.getCurrencyAttachmentMaxAmounts(), request.currencyAttachmentMaxAmounts());
        recordChange(changes, "maxAttachmentsPerMessage", config.getMaxAttachmentsPerMessage(), request.maxAttachmentsPerMessage());
        recordChange(changes, "messageRetentionDays", config.getMessageRetentionDays(), request.messageRetentionDays());
        recordChange(changes, "hardDeleteOnUserDelete", config.isHardDeleteOnUserDelete(), request.hardDeleteOnUserDelete());
        recordChange(changes, "maintenanceMode", config.isMaintenanceMode(), request.maintenanceMode());
        recordChange(changes, "useOpLevelForRoles", config.isUseOpLevelForRoles(), request.useOpLevelForRoles());
        recordChange(changes, "contentFilterEnabled", config.isContentFilterEnabled(), request.contentFilterEnabled());
        recordChange(changes, "contentFilterAction", config.getContentFilterAction(), request.contentFilterAction());
        recordChange(changes, "contentFilterWords", config.getContentFilterWords(), request.contentFilterWords());
        recordChange(changes, "contentFilterPatterns", config.getContentFilterPatterns(), request.contentFilterPatterns());

        // Apply updates
        if (request.enabled() != null) {
            config.setApiEnabled(request.enabled());
        }
        if (request.playerToPlayerEnabled() != null) {
            config.setPlayerToPlayerEnabled(request.playerToPlayerEnabled());
        }
        if (request.maxMessagesPerPlayer() != null) {
            config.setMaxMessagesPerPlayer(request.maxMessagesPerPlayer());
        }
        if (request.maxSubjectLength() != null) {
            config.setMaxSubjectLength(request.maxSubjectLength());
        }
        if (request.maxBodyLength() != null) {
            config.setMaxBodyLength(request.maxBodyLength());
        }
        if (request.defaultMessageTtlHours() != null) {
            config.setDefaultMessageTtl(Duration.ofHours(request.defaultMessageTtlHours()));
        }
        if (request.maxMessagesPerMinute() != null) {
            config.setMaxMessagesPerMinute(request.maxMessagesPerMinute());
        }
        if (request.maxMessagesPerDay() != null) {
            config.setMaxMessagesPerDay(request.maxMessagesPerDay());
        }
        if (request.maxMessagesPerRecipientPerDay() != null) {
            config.setMaxMessagesPerRecipientPerDay(request.maxMessagesPerRecipientPerDay());
        }
        if (request.sendCooldownSeconds() != null) {
            config.setSendCooldownSeconds(request.sendCooldownSeconds());
        }
        if (request.broadcastBatchSize() != null) {
            config.setBroadcastBatchSize(request.broadcastBatchSize());
        }
        if (request.broadcastBatchDelayMs() != null) {
            config.setBroadcastBatchDelayMs(request.broadcastBatchDelayMs());
        }
        if (request.broadcastQueueEnabled() != null) {
            config.setBroadcastQueueEnabled(request.broadcastQueueEnabled());
        }
        if (request.broadcastQueueThreshold() != null) {
            config.setBroadcastQueueThreshold(request.broadcastQueueThreshold());
        }
        if (request.minLevelToSend() != null) {
            config.setMinLevelToSend(request.minLevelToSend());
        }
        if (request.itemAttachmentsEnabled() != null) {
            config.setItemAttachmentsEnabled(request.itemAttachmentsEnabled());
        }
        if (request.currencyAttachmentsEnabled() != null) {
            config.setCurrencyAttachmentsEnabled(request.currencyAttachmentsEnabled());
        }
        if (request.itemAttachmentWhitelistEnabled() != null) {
            config.setItemAttachmentWhitelistEnabled(request.itemAttachmentWhitelistEnabled());
        }
        if (request.itemAttachmentWhitelist() != null) {
            config.setItemAttachmentWhitelist(request.itemAttachmentWhitelist());
        }
        if (request.itemAttachmentBlacklist() != null) {
            config.setItemAttachmentBlacklist(request.itemAttachmentBlacklist());
        }
        if (request.currencyAttachmentAllowed() != null) {
            config.setCurrencyAttachmentAllowed(request.currencyAttachmentAllowed());
        }
        if (request.currencyAttachmentMaxAmounts() != null) {
            config.setCurrencyAttachmentMaxAmounts(request.currencyAttachmentMaxAmounts());
        }
        if (request.maxAttachmentsPerMessage() != null) {
            config.setMaxAttachmentsPerMessage(request.maxAttachmentsPerMessage());
        }
        if (request.messageRetentionDays() != null) {
            config.setMessageRetentionDays(request.messageRetentionDays());
        }
        if (request.hardDeleteOnUserDelete() != null) {
            config.setHardDeleteOnUserDelete(request.hardDeleteOnUserDelete());
        }
        if (request.maintenanceMode() != null) {
            config.setMaintenanceMode(request.maintenanceMode());
        }
        if (request.useOpLevelForRoles() != null) {
            config.setUseOpLevelForRoles(request.useOpLevelForRoles());
        }
        if (request.contentFilterEnabled() != null) {
            config.setContentFilterEnabled(request.contentFilterEnabled());
        }
        if (request.contentFilterAction() != null) {
            config.setContentFilterAction(request.contentFilterAction());
        }
        if (request.contentFilterWords() != null) {
            config.setContentFilterWords(request.contentFilterWords());
        }
        if (request.contentFilterPatterns() != null) {
            config.setContentFilterPatterns(request.contentFilterPatterns());
        }

        config.save();
        MailboxManager.INSTANCE.applyContentFilterConfig(config);
        MailboxManager.INSTANCE.applyAttachmentRulesConfig(config);

        // Return updated config
        getConfig(ctx);

        if (!changes.isEmpty()) {
            AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                    .action(AdminAuditLog.Action.CONFIG_CHANGE)
                    .actorUuid(null)
                    .actorName(resolveActorName(ctx))
                    .targetType("config")
                    .details(String.join("; ", changes))
                    .build())
                .exceptionally(error -> {
                    DevMod.LOGGER.warn("[Mailbox API] Failed to persist config audit log", error);
                    return null;
                });
        }
    }

    /**
     * GET /api/security/metrics
     * Get security and rate limiting metrics for admin monitoring.
     */
    public static void getSecurityMetrics(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);

        // Payload validation metrics
        com.devmod.network.PayloadValidation.PayloadMetrics payloadMetrics =
            com.devmod.network.PayloadValidation.getMetrics();

        // IP rate limiter stats
        com.devmod.network.IpRateLimiter.Stats ipStats =
            com.devmod.network.IpRateLimiter.INSTANCE.getStats();

        // Packet validator stats
        com.devmod.network.PacketValidator validator = com.devmod.network.PacketValidator.INSTANCE;

        ctx.json(new SecurityMetricsDto(
            // Payload metrics
            payloadMetrics.totalProcessed(),
            payloadMetrics.sizeRejections(),
            payloadMetrics.rateLimitRejections(),
            payloadMetrics.ipRateLimitRejections(),
            payloadMetrics.totalRejections(),
            payloadMetrics.rejectionRate(),
            // IP limiter metrics
            ipStats.totalRequests(),
            ipStats.rateLimitedRequests(),
            ipStats.blockedRequests(),
            ipStats.trackedIps(),
            ipStats.blockedIps(),
            // Packet validator metrics
            validator.getTotalRejections(),
            validator.getTotalRateLimitHits(),
            validator.getTotalIpRateLimitHits(),
            System.currentTimeMillis()
        ));
    }

    /**
     * POST /api/security/metrics/reset
     * Reset security metrics (for testing/debugging).
     */
    public static void resetSecurityMetrics(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);

        com.devmod.network.PayloadValidation.resetMetrics();
        com.devmod.network.IpRateLimiter.INSTANCE.reset();
        com.devmod.network.PacketValidator.INSTANCE.resetTelemetry();

        ctx.json(new SuccessResponse("Security metrics reset"));

        AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                .action(AdminAuditLog.Action.CONFIG_CHANGE)
                .actorUuid(null)
                .actorName(resolveActorName(ctx))
                .targetType("security_metrics")
                .details("Reset all security metrics")
                .build())
            .exceptionally(error -> {
                DevMod.LOGGER.warn("[Mailbox API] Failed to persist security metrics reset audit log", error);
                return null;
            });
    }

    /**
     * GET /api/stats
     * Get system statistics.
     */
    public static void getStats(Context ctx) {
        MailboxManager.INSTANCE.getTotalMessageCount().thenCombine(
            MailboxManager.INSTANCE.getTotalUnreadCount(),
            (totalMessages, totalUnread) -> new Object[] { totalMessages, totalUnread }
        ).thenCombine(
            MailboxManager.INSTANCE.getKnownUsers(),
            (arr, users) -> new Object[] { arr[0], arr[1], users.size() }
        ).thenCombine(
            NewsManager.getInstance().getActiveNews(),
            (arr, news) -> new Object[] { arr[0], arr[1], arr[2], news.size() }
        ).thenCombine(
            TestTaskManager.INSTANCE.getAllTasks(),
            (arr, tasks) -> {
                int pendingTasks = (int) tasks.stream()
                    .filter(t -> t.status() == com.devmod.mailbox.task.TestTask.TaskStatus.PENDING)
                    .count();
                int inProgressTasks = (int) tasks.stream()
                    .filter(t -> t.status() == com.devmod.mailbox.task.TestTask.TaskStatus.IN_PROGRESS)
                    .count();
                int completedTasks = (int) tasks.stream()
                    .filter(t -> t.status() == com.devmod.mailbox.task.TestTask.TaskStatus.COMPLETED)
                    .count();

                return new StatsDto(
                    (Integer) arr[0],
                    (Integer) arr[1],
                    (Integer) arr[2],
                    (Integer) arr[3],
                    tasks.size(),
                    pendingTasks,
                    inProgressTasks,
                    completedTasks,
                    com.devmod.mailbox.api.MailboxApiServer.isRunning(),
                    Runtime.getRuntime().freeMemory(),
                    Runtime.getRuntime().totalMemory(),
                    System.currentTimeMillis()
                );
            }
        ).thenAccept(ctx::json).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    // ========================================================================
    // TICKET AUTO-TRANSITION API
    // ========================================================================

    /**
     * GET /api/tickets/auto-transition/config
     * Get ticket auto-transition configuration.
     */
    public static void getAutoTransitionConfig(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);

        AutoTransitionService service = AutoTransitionService.INSTANCE;
        ctx.json(new AutoTransitionConfigDto(
            service.isRunning(),
            service.isAutoCloseEnabled(),
            service.getAutoCloseDuration().toDays(),
            service.isSlaEscalationEnabled(),
            service.isAutoAssignEnabled()
        ));
    }

    /**
     * PUT /api/tickets/auto-transition/config
     * Update ticket auto-transition configuration.
     */
    public static void updateAutoTransitionConfig(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);

        UpdateAutoTransitionRequest request = ctx.bodyAsClass(UpdateAutoTransitionRequest.class);
        AutoTransitionService service = AutoTransitionService.INSTANCE;
        List<String> changes = new ArrayList<>();

        recordChange(changes, "autoCloseEnabled", service.isAutoCloseEnabled(), request.autoCloseEnabled());
        recordChange(changes, "autoCloseDays", service.getAutoCloseDuration().toDays(), request.autoCloseDays());
        recordChange(changes, "slaEscalationEnabled", service.isSlaEscalationEnabled(), request.slaEscalationEnabled());
        recordChange(changes, "autoAssignEnabled", service.isAutoAssignEnabled(), request.autoAssignEnabled());

        if (request.autoCloseEnabled() != null) {
            service.setAutoCloseEnabled(request.autoCloseEnabled());
        }
        if (request.autoCloseDays() != null) {
            service.setAutoCloseDuration(Duration.ofDays(request.autoCloseDays()));
        }
        if (request.slaEscalationEnabled() != null) {
            service.setSlaEscalationEnabled(request.slaEscalationEnabled());
        }
        if (request.autoAssignEnabled() != null) {
            service.setAutoAssignEnabled(request.autoAssignEnabled());
        }

        getAutoTransitionConfig(ctx);

        if (!changes.isEmpty()) {
            AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                    .action(AdminAuditLog.Action.CONFIG_CHANGE)
                    .actorUuid(null)
                    .actorName(resolveActorName(ctx))
                    .targetType("auto_transition_config")
                    .details(String.join("; ", changes))
                    .build())
                .exceptionally(error -> {
                    DevMod.LOGGER.warn("[Mailbox API] Failed to persist auto-transition config audit log", error);
                    return null;
                });
        }
    }

    /**
     * GET /api/tickets/auto-transition/metrics
     * Get ticket auto-transition metrics.
     */
    public static void getAutoTransitionMetrics(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);

        AutoTransitionService.AutoTransitionMetrics metrics = AutoTransitionService.INSTANCE.getMetrics();
        java.time.Instant lastRun = metrics.lastRunTime();
        ctx.json(new AutoTransitionMetricsDto(
            metrics.autoClosedCount(),
            metrics.escalatedCount(),
            metrics.autoAssignedCount(),
            metrics.totalRunCount(),
            lastRun != null ? lastRun.toEpochMilli() : null,
            metrics.running()
        ));
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private static ErrorResponse errorResponse(@Nullable String message) {
        return new ErrorResponse("error", message != null ? message : "Unknown error");
    }

    private static String resolveActorName(Context ctx) {
        AuthMiddleware.AuthenticatedUser user = AuthMiddleware.getUser(ctx);
        return user != null ? user.username() : "Admin";
    }

    private static void recordChange(List<String> changes, String field, Object oldValue, @Nullable Object newValue) {
        if (newValue == null) {
            return;
        }
        if (!java.util.Objects.equals(oldValue, newValue)) {
            changes.add(field + ": " + oldValue + " -> " + newValue);
        }
    }

    // ========================================================================
    // DTOs
    // ========================================================================

    public record ErrorResponse(String status, String message) {}

    public record ConfigDto(
        boolean enabled,
        boolean playerToPlayerEnabled,
        int maxMessagesPerPlayer,
        int maxSubjectLength,
        int maxBodyLength,
        long defaultMessageTtlHours,
        int maxMessagesPerMinute,
        int maxMessagesPerDay,
        int maxMessagesPerRecipientPerDay,
        int sendCooldownSeconds,
        int broadcastBatchSize,
        int broadcastBatchDelayMs,
        boolean broadcastQueueEnabled,
        int broadcastQueueThreshold,
        int minLevelToSend,
        boolean itemAttachmentsEnabled,
        boolean currencyAttachmentsEnabled,
        boolean itemAttachmentWhitelistEnabled,
        List<String> itemAttachmentWhitelist,
        List<String> itemAttachmentBlacklist,
        List<String> currencyAttachmentAllowed,
        java.util.Map<String, Integer> currencyAttachmentMaxAmounts,
        int maxAttachmentsPerMessage,
        int messageRetentionDays,
        boolean hardDeleteOnUserDelete,
        boolean maintenanceMode,
        boolean useOpLevelForRoles,
        boolean contentFilterEnabled,
        String contentFilterAction,
        List<String> contentFilterWords,
        List<String> contentFilterPatterns
    ) {}

    public record UpdateConfigRequest(
        Boolean enabled,
        Boolean playerToPlayerEnabled,
        Integer maxMessagesPerPlayer,
        Integer maxSubjectLength,
        Integer maxBodyLength,
        Long defaultMessageTtlHours,
        Integer maxMessagesPerMinute,
        Integer maxMessagesPerDay,
        Integer maxMessagesPerRecipientPerDay,
        Integer sendCooldownSeconds,
        Integer broadcastBatchSize,
        Integer broadcastBatchDelayMs,
        Boolean broadcastQueueEnabled,
        Integer broadcastQueueThreshold,
        Integer minLevelToSend,
        Boolean itemAttachmentsEnabled,
        Boolean currencyAttachmentsEnabled,
        Boolean itemAttachmentWhitelistEnabled,
        List<String> itemAttachmentWhitelist,
        List<String> itemAttachmentBlacklist,
        List<String> currencyAttachmentAllowed,
        java.util.Map<String, Integer> currencyAttachmentMaxAmounts,
        Integer maxAttachmentsPerMessage,
        Integer messageRetentionDays,
        Boolean hardDeleteOnUserDelete,
        Boolean maintenanceMode,
        Boolean useOpLevelForRoles,
        Boolean contentFilterEnabled,
        String contentFilterAction,
        List<String> contentFilterWords,
        List<String> contentFilterPatterns
    ) {}

    public record StatsDto(
        int totalMessages,
        int totalUnreadMessages,
        int totalUsers,
        int activeNewsArticles,
        int totalTasks,
        int pendingTasks,
        int inProgressTasks,
        int completedTasks,
        boolean apiServerRunning,
        long freeMemoryBytes,
        long totalMemoryBytes,
        long timestampMillis
    ) {}

    public record AutoTransitionConfigDto(
        boolean running,
        boolean autoCloseEnabled,
        long autoCloseDays,
        boolean slaEscalationEnabled,
        boolean autoAssignEnabled
    ) {}

    public record UpdateAutoTransitionRequest(
        Boolean autoCloseEnabled,
        Long autoCloseDays,
        Boolean slaEscalationEnabled,
        Boolean autoAssignEnabled
    ) {}

    public record AutoTransitionMetricsDto(
        long autoClosedCount,
        long escalatedCount,
        long autoAssignedCount,
        long totalRunCount,
        @Nullable Long lastRunTimeMillis,
        boolean running
    ) {}
}
