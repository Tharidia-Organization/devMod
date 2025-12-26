package com.devmod.arena.monitoring;

import java.time.Duration;

public record AnomalyThresholds(
    // Build metrics - DD57: P50, P95, P99 percentiles
    Duration buildP50Warn,
    Duration buildP50Critical,
    Duration buildP95Warn,
    Duration buildP95Critical,
    Duration buildP99Warn,
    Duration buildP99Critical,
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

    // Latency metrics - DD57: P50, P95, P99 percentiles
    Duration resolveP50Warn,
    Duration resolveP50Critical,
    Duration resolveP95Warn,
    Duration resolveP95Critical,
    Duration resolveP99Warn,
    Duration resolveP99Critical
) {
    /**
     * Default thresholds based on KPIs (DD72).
     * DD57: P50, P95, P99 percentiles for build and resolve latency.
     */
    public static final AnomalyThresholds DEFAULTS = new AnomalyThresholds(
        // Build P50: warn at 1.5s, critical at 2s (KPI: <2s)
        Duration.ofMillis(1500),
        Duration.ofSeconds(2),
        // Build P95: warn at 4s, critical at 5s (KPI: <5s)
        Duration.ofSeconds(4),
        Duration.ofSeconds(5),
        // Build P99: warn at 8s, critical at 10s (KPI: <10s)
        Duration.ofSeconds(8),
        Duration.ofSeconds(10),

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

        // Resolve P50: warn at 150ms, critical at 200ms
        Duration.ofMillis(150),
        Duration.ofMillis(200),
        // Resolve P95: warn at 400ms, critical at 500ms
        Duration.ofMillis(400),
        Duration.ofMillis(500),
        // Resolve P99: warn at 800ms, critical at 1000ms
        Duration.ofMillis(800),
        Duration.ofSeconds(1)
    );

    /**
     * Stricter thresholds for production.
     * DD57: P50, P95, P99 percentiles for build and resolve latency.
     */
    public static final AnomalyThresholds PRODUCTION = new AnomalyThresholds(
        // Build P50/P95/P99
        Duration.ofSeconds(1),
        Duration.ofMillis(1500),
        Duration.ofSeconds(3),
        Duration.ofSeconds(4),
        Duration.ofSeconds(6),
        Duration.ofSeconds(8),
        // Rollback rate
        0.003,
        0.007,
        // Completion rate
        0.85,
        0.80,
        // Pool miss rate
        0.20,
        0.35,
        // Error rate
        0.005,
        0.02,
        // Resolve P50/P95/P99
        Duration.ofMillis(100),
        Duration.ofMillis(150),
        Duration.ofMillis(300),
        Duration.ofMillis(400),
        Duration.ofMillis(600),
        Duration.ofMillis(800)
    );

    /**
     * DD57: Checks if build P50 exceeds warning threshold.
     */
    public AlertLevel checkBuildP50(Duration actual) {
        if (actual.compareTo(buildP50Critical) >= 0) {
            return AlertLevel.CRITICAL;
        } else if (actual.compareTo(buildP50Warn) >= 0) {
            return AlertLevel.WARNING;
        }
        return AlertLevel.OK;
    }

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
     * DD57: Checks if build P99 exceeds warning threshold.
     */
    public AlertLevel checkBuildP99(Duration actual) {
        if (actual.compareTo(buildP99Critical) >= 0) {
            return AlertLevel.CRITICAL;
        } else if (actual.compareTo(buildP99Warn) >= 0) {
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
     * DD57: Checks if resolve P50 exceeds threshold.
     */
    public AlertLevel checkResolveP50(Duration actual) {
        if (actual.compareTo(resolveP50Critical) >= 0) {
            return AlertLevel.CRITICAL;
        } else if (actual.compareTo(resolveP50Warn) >= 0) {
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
     * DD57: Checks if resolve P99 exceeds threshold.
     */
    public AlertLevel checkResolveP99(Duration actual) {
        if (actual.compareTo(resolveP99Critical) >= 0) {
            return AlertLevel.CRITICAL;
        } else if (actual.compareTo(resolveP99Warn) >= 0) {
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
