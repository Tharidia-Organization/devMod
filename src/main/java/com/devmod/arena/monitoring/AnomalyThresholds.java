package com.devmod.arena.monitoring;

import java.time.Duration;

/**
 * Anomaly detection thresholds for 48h monitoring (DD68).
 *
 * <p>Defines thresholds for various metrics with escalation levels.
 */
public record AnomalyThresholds(
    // Build metrics
    Duration buildP95Warn,
    Duration buildP95Critical,
    double rollbackRateWarn,
    double rollbackRateCritical,

    // Completion metrics
    double completionRateWarn,
    double completionRateCritical,

    // Pool metrics
    double poolMissRateWarn,
    double poolMissRateCritical,

    // Error metrics
    double errorRateWarn,
    double errorRateCritical,

    // Latency metrics
    Duration resolveP95Warn,
    Duration resolveP95Critical
) {
    /**
     * Default thresholds based on KPIs (DD72).
     */
    public static final AnomalyThresholds DEFAULTS = new AnomalyThresholds(
        // Build P95: warn at 4s, critical at 5s (KPI: <5s)
        Duration.ofSeconds(4),
        Duration.ofSeconds(5),

        // Rollback rate: warn at 0.5%, critical at 1% (KPI: <1%)
        0.005,
        0.01,

        // Completion rate: warn at 80%, critical at 75% (KPI: >75%)
        0.80,
        0.75,

        // Pool miss rate: warn at 30%, critical at 50%
        0.30,
        0.50,

        // Error rate: warn at 1%, critical at 5%
        0.01,
        0.05,

        // Resolve P95: warn at 400ms, critical at 500ms
        Duration.ofMillis(400),
        Duration.ofMillis(500)
    );

    /**
     * Stricter thresholds for production.
     */
    public static final AnomalyThresholds PRODUCTION = new AnomalyThresholds(
        Duration.ofSeconds(3),
        Duration.ofSeconds(4),
        0.003,
        0.007,
        0.85,
        0.80,
        0.20,
        0.35,
        0.005,
        0.02,
        Duration.ofMillis(300),
        Duration.ofMillis(400)
    );

    /**
     * Checks if build P95 exceeds warning threshold.
     */
    public AlertLevel checkBuildP95(Duration actual) {
        if (actual.compareTo(buildP95Critical) >= 0) {
            return AlertLevel.CRITICAL;
        } else if (actual.compareTo(buildP95Warn) >= 0) {
            return AlertLevel.WARNING;
        }
        return AlertLevel.OK;
    }

    /**
     * Checks if rollback rate exceeds threshold.
     */
    public AlertLevel checkRollbackRate(double actual) {
        if (actual >= rollbackRateCritical) {
            return AlertLevel.CRITICAL;
        } else if (actual >= rollbackRateWarn) {
            return AlertLevel.WARNING;
        }
        return AlertLevel.OK;
    }

    /**
     * Checks if completion rate is below threshold.
     */
    public AlertLevel checkCompletionRate(double actual) {
        if (actual <= completionRateCritical) {
            return AlertLevel.CRITICAL;
        } else if (actual <= completionRateWarn) {
            return AlertLevel.WARNING;
        }
        return AlertLevel.OK;
    }

    /**
     * Checks if pool miss rate exceeds threshold.
     */
    public AlertLevel checkPoolMissRate(double actual) {
        if (actual >= poolMissRateCritical) {
            return AlertLevel.CRITICAL;
        } else if (actual >= poolMissRateWarn) {
            return AlertLevel.WARNING;
        }
        return AlertLevel.OK;
    }

    /**
     * Checks if error rate exceeds threshold.
     */
    public AlertLevel checkErrorRate(double actual) {
        if (actual >= errorRateCritical) {
            return AlertLevel.CRITICAL;
        } else if (actual >= errorRateWarn) {
            return AlertLevel.WARNING;
        }
        return AlertLevel.OK;
    }

    /**
     * Checks if resolve P95 exceeds threshold.
     */
    public AlertLevel checkResolveP95(Duration actual) {
        if (actual.compareTo(resolveP95Critical) >= 0) {
            return AlertLevel.CRITICAL;
        } else if (actual.compareTo(resolveP95Warn) >= 0) {
            return AlertLevel.WARNING;
        }
        return AlertLevel.OK;
    }

    /**
     * Alert severity levels.
     */
    public enum AlertLevel {
        OK,
        WARNING,
        CRITICAL
    }
}
