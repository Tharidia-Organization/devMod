package com.devmod.arena.rollout;

import java.time.Duration;

public record RolloutSuccessCriteria(
    Duration maxBuildP95,
    double maxRollbackRate,
    double minCompletionRate,
    double maxErrorRate,
    double minBuildSuccessRate,
    Duration minObservationPeriod,
    int minSampleSize
) {
    /**
     * DD72: Default criteria based on KPIs.
     * - p95 latency < 500ms
     * - build success > 99%
     * - zero critical bugs (error_rate < 1%)
     */
    public static final RolloutSuccessCriteria DEFAULT = new RolloutSuccessCriteria(
        Duration.ofMillis(500),   // DD72: p95 latency < 500ms
        0.01,                     // rollback_rate < 1%
        0.75,                     // completion_rate > 75%
        0.01,                     // error_rate < 1% (zero critical bugs)
        0.99,                     // DD72: build success > 99%
        Duration.ofHours(24),     // minimum 24h observation
        100                       // minimum 100 samples
    );

    /**
     * Stricter criteria for later phases.
     */
    public static final RolloutSuccessCriteria STRICT = new RolloutSuccessCriteria(
        Duration.ofMillis(300),
        0.005,
        0.85,
        0.005,
        0.995,
        Duration.ofDays(2),
        500
    );

    /**
     * Lenient criteria for initial phases.
     */
    public static final RolloutSuccessCriteria LENIENT = new RolloutSuccessCriteria(
        Duration.ofSeconds(1),
        0.03,
        0.60,
        0.05,
        0.95,
        Duration.ofHours(12),
        50
    );

    /**
     * Validates that criteria values are sensible.
     */
    public RolloutSuccessCriteria {
        if (maxBuildP95.isNegative() || maxBuildP95.isZero()) {
            throw new IllegalArgumentException("maxBuildP95 must be positive");
        }
        if (maxRollbackRate < 0 || maxRollbackRate > 1) {
            throw new IllegalArgumentException("maxRollbackRate must be between 0 and 1");
        }
        if (minCompletionRate < 0 || minCompletionRate > 1) {
            throw new IllegalArgumentException("minCompletionRate must be between 0 and 1");
        }
        if (maxErrorRate < 0 || maxErrorRate > 1) {
            throw new IllegalArgumentException("maxErrorRate must be between 0 and 1");
        }
        if (minBuildSuccessRate < 0 || minBuildSuccessRate > 1) {
            throw new IllegalArgumentException("minBuildSuccessRate must be between 0 and 1");
        }
        if (minObservationPeriod.isNegative()) {
            throw new IllegalArgumentException("minObservationPeriod must not be negative");
        }
        if (minSampleSize < 0) {
            throw new IllegalArgumentException("minSampleSize must not be negative");
        }
    }

    /**
     * Creates criteria with custom build P95 threshold.
     */
    public RolloutSuccessCriteria withMaxBuildP95(Duration duration) {
        return new RolloutSuccessCriteria(
            duration, maxRollbackRate, minCompletionRate,
            maxErrorRate, minBuildSuccessRate, minObservationPeriod, minSampleSize
        );
    }

    /**
     * Creates criteria with custom rollback rate threshold.
     */
    public RolloutSuccessCriteria withMaxRollbackRate(double rate) {
        return new RolloutSuccessCriteria(
            maxBuildP95, rate, minCompletionRate,
            maxErrorRate, minBuildSuccessRate, minObservationPeriod, minSampleSize
        );
    }

    /**
     * Creates criteria with custom completion rate threshold.
     */
    public RolloutSuccessCriteria withMinCompletionRate(double rate) {
        return new RolloutSuccessCriteria(
            maxBuildP95, maxRollbackRate, rate,
            maxErrorRate, minBuildSuccessRate, minObservationPeriod, minSampleSize
        );
    }

    /**
     * Creates criteria with custom build success rate threshold.
     */
    public RolloutSuccessCriteria withMinBuildSuccessRate(double rate) {
        return new RolloutSuccessCriteria(
            maxBuildP95, maxRollbackRate, minCompletionRate,
            maxErrorRate, rate, minObservationPeriod, minSampleSize
        );
    }

    /**
     * Creates criteria with custom observation period.
     */
    public RolloutSuccessCriteria withMinObservationPeriod(Duration period) {
        return new RolloutSuccessCriteria(
            maxBuildP95, maxRollbackRate, minCompletionRate,
            maxErrorRate, minBuildSuccessRate, period, minSampleSize
        );
    }

    /**
     * Creates criteria with custom sample size.
     */
    public RolloutSuccessCriteria withMinSampleSize(int size) {
        return new RolloutSuccessCriteria(
            maxBuildP95, maxRollbackRate, minCompletionRate,
            maxErrorRate, minBuildSuccessRate, minObservationPeriod, size
        );
    }
}
