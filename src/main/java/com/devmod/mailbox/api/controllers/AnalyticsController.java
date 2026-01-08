package com.devmod.mailbox.api.controllers;

import java.util.List;

import javax.annotation.Nullable;

import io.javalin.http.Context;

import com.devmod.mailbox.analytics.MailboxAnalyticsEngine;
import com.devmod.mailbox.analytics.MailboxAnalyticsEngine.AnomalyAlert;
import com.devmod.mailbox.analytics.MailboxAnalyticsEngine.DashboardMetrics;
import com.devmod.mailbox.analytics.MailboxAnalyticsEngine.HourlyVolume;
import com.devmod.mailbox.analytics.MailboxAnalyticsEngine.PlayerRanking;
import com.devmod.mailbox.broadcast.BroadcastQueueWorker;
import com.devmod.mailbox.delivery.MailboxDeliveryRuntime;
import com.devmod.mailbox.digest.DigestManager;
import com.devmod.mailbox.persistence.DbPerformanceMonitor;
import com.devmod.mailbox.scheduler.MessageScheduler;

/**
 * REST API controller for advanced analytics and system monitoring.
 */
public final class AnalyticsController {

    private AnalyticsController() {}

    // ============================================================================
    // DASHBOARD METRICS
    // ============================================================================

    /**
     * GET /api/analytics/dashboard
     * Get dashboard metrics with real-time analytics.
     */
    public static void getDashboardMetrics(Context ctx) {
        DashboardMetrics metrics = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics();
        ctx.json(new DashboardDto(
            metrics.totalMessages(),
            metrics.totalRead(),
            metrics.totalDeleted(),
            metrics.readRate(),
            metrics.attachmentsSent(),
            metrics.attachmentsClaimed(),
            metrics.claimRate(),
            metrics.activePlayers(),
            metrics.avgMessagesPerHour(),
            metrics.avgMessagesPerDay()
        ));
    }

    /**
     * GET /api/analytics/hourly
     * Get hourly message volume for the last 24 hours.
     */
    public static void getHourlyVolume(Context ctx) {
        List<HourlyVolume> volumes = MailboxAnalyticsEngine.INSTANCE.getHourlyVolume();
        List<HourlyVolumeDto> dtos = volumes.stream()
            .map(v -> new HourlyVolumeDto(v.hour(), v.count()))
            .toList();
        ctx.json(new ListResponse<>(dtos, dtos.size()));
    }

    /**
     * GET /api/analytics/type-breakdown
     * Get message breakdown by type.
     */
    public static void getTypeBreakdown(Context ctx) {
        var breakdown = MailboxAnalyticsEngine.INSTANCE.getMessageTypeBreakdown();
        List<TypeBreakdownDto> dtos = breakdown.entrySet().stream()
            .map(e -> new TypeBreakdownDto(e.getKey().name(), e.getValue()))
            .toList();
        ctx.json(new ListResponse<>(dtos, dtos.size()));
    }

    // ============================================================================
    // RANKINGS
    // ============================================================================

    /**
     * GET /api/analytics/top-senders
     * Query param: limit (default 10)
     */
    public static void getTopSenders(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
        limit = Math.min(limit, 100);
        List<PlayerRanking> rankings = MailboxAnalyticsEngine.INSTANCE.getTopSenders(limit);
        List<PlayerRankingDto> dtos = rankings.stream()
            .map(r -> new PlayerRankingDto(r.playerUuid().toString(), r.count()))
            .toList();
        ctx.json(new ListResponse<>(dtos, dtos.size()));
    }

    /**
     * GET /api/analytics/top-receivers
     * Query param: limit (default 10)
     */
    public static void getTopReceivers(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
        limit = Math.min(limit, 100);
        List<PlayerRanking> rankings = MailboxAnalyticsEngine.INSTANCE.getTopReceivers(limit);
        List<PlayerRankingDto> dtos = rankings.stream()
            .map(r -> new PlayerRankingDto(r.playerUuid().toString(), r.count()))
            .toList();
        ctx.json(new ListResponse<>(dtos, dtos.size()));
    }

    // ============================================================================
    // ANOMALY DETECTION
    // ============================================================================

    /**
     * GET /api/analytics/anomalies
     * Get recent anomaly alerts.
     * Query param: limit (default 20)
     */
    public static void getAnomalies(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        limit = Math.min(limit, 100);
        List<AnomalyAlert> alerts = MailboxAnalyticsEngine.INSTANCE.getAnomalyAlerts(limit);
        List<AnomalyAlertDto> dtos = alerts.stream()
            .map(a -> new AnomalyAlertDto(
                a.type().name(),
                a.playerUuid() != null ? a.playerUuid().toString() : null,
                a.currentRate(),
                a.normalRate(),
                a.detectedAt().toEpochMilli()
            ))
            .toList();
        ctx.json(new ListResponse<>(dtos, dtos.size()));
    }

    // ============================================================================
    // SYSTEM STATUS
    // ============================================================================

    /**
     * GET /api/analytics/system-status
     * Get status of all advanced subsystems.
     */
    public static void getSystemStatus(Context ctx) {
        // Broadcast queue stats
        var queueStats = BroadcastQueueWorker.INSTANCE.getStats();

        // Scheduler stats
        var schedulerStats = MessageScheduler.INSTANCE.getStats();

        // Digest stats
        var digestStats = DigestManager.INSTANCE.getStats();

        // Delivery stats
        var deliveryStats = MailboxDeliveryRuntime.INSTANCE.getStats();

        // DB performance
        var dbMetrics = DbPerformanceMonitor.INSTANCE.getMetrics();
        var slowQueries = DbPerformanceMonitor.INSTANCE.getSlowQueryHistory(5);

        ctx.json(new SystemStatusDto(
            new BroadcastQueueStatusDto(
                queueStats.queueSize(),
                queueStats.activeJobs(),
                queueStats.totalJobsProcessed(),
                queueStats.totalFailures()
            ),
            new SchedulerStatusDto(
                schedulerStats.pendingMessages(),
                schedulerStats.recurringTemplates(),
                schedulerStats.totalDelivered(),
                schedulerStats.totalFailed()
            ),
            new DigestStatusDto(
                digestStats.totalPending(),
                digestStats.playersWithPreferences(),
                digestStats.digestsSent(),
                digestStats.immediateDeliveries()
            ),
            new DeliveryStatusDto(
                deliveryStats.totalAttempts(),
                deliveryStats.totalDelivered(),
                deliveryStats.totalFailed(),
                deliveryStats.totalRetried(),
                deliveryStats.totalRecalls(),
                deliveryStats.totalExpired(),
                deliveryStats.inFlight(),
                deliveryStats.running()
            ),
            new DbPerformanceStatusDto(
                dbMetrics.totalQueries(),
                dbMetrics.avgQueryTimeMs(),
                (int) dbMetrics.slowQueries(),
                slowQueries.stream()
                    .map(sq -> new SlowQueryDto(sq.queryName(), sq.durationMs(), sq.timestamp().toEpochMilli()))
                    .toList()
            )
        ));
    }

    /**
     * GET /api/analytics/db-recommendations
     * Get index recommendations for database optimization.
     */
    public static void getDbRecommendations(Context ctx) {
        var recommendations = DbPerformanceMonitor.INSTANCE.getIndexRecommendations();
        List<IndexRecommendationDto> dtos = recommendations.stream()
            .map(r -> new IndexRecommendationDto(r.queryName(), r.slowCount(), r.suggestion(), r.priority()))
            .toList();
        ctx.json(new ListResponse<>(dtos, dtos.size()));
    }

    // ============================================================================
    // DTOs
    // ============================================================================

    public record ErrorResponse(String status, String message) {}

    public record ListResponse<T>(List<T> items, int count) {}

    public record DashboardDto(
        long totalMessages,
        long totalRead,
        long totalDeleted,
        double readRate,
        long attachmentsSent,
        long attachmentsClaimed,
        double claimRate,
        int activePlayers,
        double avgMessagesPerHour,
        double avgMessagesPerDay
    ) {}

    public record HourlyVolumeDto(int hour, long count) {}

    public record TypeBreakdownDto(String type, long count) {}

    public record PlayerRankingDto(String playerUuid, long count) {}

    public record AnomalyAlertDto(
        String type,
        @Nullable String playerUuid,
        double currentRate,
        double normalRate,
        long detectedAtMillis
    ) {}

    public record SystemStatusDto(
        BroadcastQueueStatusDto broadcastQueue,
        SchedulerStatusDto scheduler,
        DigestStatusDto digest,
        DeliveryStatusDto delivery,
        DbPerformanceStatusDto dbPerformance
    ) {}

    public record BroadcastQueueStatusDto(
        int queueSize,
        int activeJobs,
        int totalCompleted,
        int totalFailed
    ) {}

    public record SchedulerStatusDto(
        int pendingMessages,
        int activeRecurringTemplates,
        int totalDelivered,
        int totalFailed
    ) {}

    public record DigestStatusDto(
        int pendingNotifications,
        int playersWithPending,
        int digestsSent,
        int immediateDeliveries
    ) {}

    public record DeliveryStatusDto(
        long totalAttempts,
        long totalDelivered,
        long totalFailed,
        long totalRetried,
        long totalRecalls,
        long totalExpired,
        int inFlight,
        boolean running
    ) {}

    public record DbPerformanceStatusDto(
        long totalQueries,
        double avgQueryTimeMs,
        int slowQueryCount,
        List<SlowQueryDto> recentSlowQueries
    ) {}

    public record SlowQueryDto(String queryName, long durationMs, long timestampMillis) {}

    public record IndexRecommendationDto(
        String queryName,
        long slowCount,
        String suggestion,
        String priority
    ) {}
}
