package com.devmod.arena.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import com.devmod.arena.alert.AlertRouter;
import com.devmod.arena.alert.AlertRouterRegistry;
import com.devmod.arena.alert.ErrorContext;
import com.devmod.arena.config.ArenaTemplateConfig;
public final class BuildOutcomeMonitor {

    private static final Duration WINDOW = Duration.ofHours(24);
    private static final Duration ALERT_COOLDOWN = Duration.ofMinutes(10);

    private static final Object LOCK = new Object();
    private static final Deque<Instant> builds = new ArrayDeque<>();
    private static final Deque<Instant> failures = new ArrayDeque<>();
    private static final Deque<Instant> rollbacks = new ArrayDeque<>();
    private static Instant lastFailureAlertAt;
    private static Instant lastRollbackAlertAt;

    private BuildOutcomeMonitor() {}

    public static void recordBuild(ArenaTemplateConfig.AlertThresholds thresholds,
                                   boolean success,
                                   boolean rolledBack,
                                   String templateId) {
        ArenaTemplateConfig.AlertThresholds t =
            thresholds != null ? thresholds : ArenaTemplateConfig.AlertThresholds.defaults();

        Instant now = Instant.now();
        int total;
        int failed;
        int rollbackCount;

        synchronized (LOCK) {
            pruneOld(now);
            builds.addLast(now);
            if (!success) {
                failures.addLast(now);
            }
            if (rolledBack) {
                rollbacks.addLast(now);
            }
            total = builds.size();
            failed = failures.size();
            rollbackCount = rollbacks.size();
        }

        if (total == 0) {
            return;
        }

        double failureRate = failed / (double) total;
        double rollbackRate = rollbackCount / (double) total;

        maybeAlertFailureRate(t, now, failureRate, total, failed, templateId);
        maybeAlertRollbackRate(t, now, rollbackRate, total, rollbackCount, templateId);
    }

    private static void pruneOld(Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!builds.isEmpty() && builds.peekFirst().isBefore(cutoff)) {
            builds.removeFirst();
        }
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.removeFirst();
        }
        while (!rollbacks.isEmpty() && rollbacks.peekFirst().isBefore(cutoff)) {
            rollbacks.removeFirst();
        }
    }

    private static void maybeAlertFailureRate(ArenaTemplateConfig.AlertThresholds t,
                                              Instant now,
                                              double rate,
                                              int total,
                                              int failed,
                                              String templateId) {
        ErrorContext.Severity severity = null;
        if (rate >= t.errorFailureRate24h()) {
            severity = ErrorContext.Severity.ERROR;
        } else if (rate >= t.warnFailureRate24h()) {
            severity = ErrorContext.Severity.WARNING;
        }
        if (severity == null || !cooldownElapsed(lastFailureAlertAt, now)) {
            return;
        }

        lastFailureAlertAt = now;
        AlertRouter router = AlertRouterRegistry.get();
        if (router == null) {
            return;
        }

        ErrorContext context = ErrorContext.builder()
            .component("BuildOutcomeMonitor")
            .errorType("FailureRate")
            .message(String.format("Failure rate %.2f%% (failed=%d, total=%d) in last 24h",
                rate * 100.0, failed, total))
            .severity(severity)
            .metadata(Map.of(
                "failure_rate", rate,
                "failed_builds", failed,
                "total_builds", total,
                "window_hours", 24,
                "templateId", templateId != null ? templateId : ""
            ))
            .build();
        router.route(context);
    }

    private static void maybeAlertRollbackRate(ArenaTemplateConfig.AlertThresholds t,
                                               Instant now,
                                               double rate,
                                               int total,
                                               int rollbackCount,
                                               String templateId) {
        ErrorContext.Severity severity = null;
        if (rate >= t.errorRollbackRate24h()) {
            severity = ErrorContext.Severity.ERROR;
        } else if (rate >= t.warnRollbackRate24h()) {
            severity = ErrorContext.Severity.WARNING;
        }
        if (severity == null || !cooldownElapsed(lastRollbackAlertAt, now)) {
            return;
        }

        lastRollbackAlertAt = now;
        AlertRouter router = AlertRouterRegistry.get();
        if (router == null) {
            return;
        }

        ErrorContext context = ErrorContext.builder()
            .component("BuildOutcomeMonitor")
            .errorType("RollbackRate")
            .message(String.format("Rollback rate %.2f%% (rollbacks=%d, total=%d) in last 24h",
                rate * 100.0, rollbackCount, total))
            .severity(severity)
            .metadata(Map.of(
                "rollback_rate", rate,
                "rollback_builds", rollbackCount,
                "total_builds", total,
                "window_hours", 24,
                "templateId", templateId != null ? templateId : ""
            ))
            .build();
        router.route(context);
    }

    private static boolean cooldownElapsed(Instant lastAlert, Instant now) {
        return lastAlert == null || Duration.between(lastAlert, now).compareTo(ALERT_COOLDOWN) >= 0;
    }
}
