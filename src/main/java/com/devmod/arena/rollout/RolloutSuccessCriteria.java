package com.devmod.arena.rollout;

import java.time.Duration;

/**
 * Success criteria for rollout phases (DD72).
 *
 * <p>KPIs:
 * <ul>
 *   <li>build_p95 &lt; 5s</li>
 *   <li>rollback_rate &lt; 1%</li>
 *   <li>completion_rate &gt; 75%</li>
 * </ul>
 */
public record RolloutSuccessCriteria(
    Duration maxBuildP95,
    double maxRollbackRate,
    double minCompletionRate,
    double maxErrorRate,
    Duration minObservationPeriod,
    int minSampleSize
) {
    /**
     * Default criteria based on DD72 KPIs.
     */
    public static final RolloutSuccessCriteria DEFAULT = new RolloutSuccessCriteria(
        Duration.ofSeconds(5),    // build_p95 < 5s
        0.01,                     // rollback_rate < 1%
        0.75,                     // completion_rate > 75%
        0.05,                     // error_rate < 5%
        Duration.ofHours(24),     // minimum 24h observation
        100                       // minimum 100 samples
    );

    /**
     * Stricter criteria for later phases.
     */
    public static final RolloutSuccessCriteria STRICT = new RolloutSuccessCriteria(
        Duration.ofSeconds(3),
        0.005,
        0.85,
        0.02,
        Duration.ofHours(48),
        500
    );

    /**
     * Lenient criteria for initial phases.
     */
    public static final RolloutSuccessCriteria LENIENT = new RolloutSuccessCriteria(
        Duration.ofSeconds(8),
        0.03,
        0.60,
        0.10,
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
            maxErrorRate, minObservationPeriod, minSampleSize
        );
    }

    /**
     * Creates criteria with custom rollback rate threshold.
     */
    public RolloutSuccessCriteria withMaxRollbackRate(double rate) {
        return new RolloutSuccessCriteria(
            maxBuildP95, rate, minCompletionRate,
            maxErrorRate, minObservationPeriod, minSampleSize
        );
    }

    /**
     * Creates criteria with custom completion rate threshold.
     */
    public RolloutSuccessCriteria withMinCompletionRate(double rate) {
        return new RolloutSuccessCriteria(
            maxBuildP95, maxRollbackRate, rate,
            maxErrorRate, minObservationPeriod, minSampleSize
        );
    }

    /**
     * Creates criteria with custom observation period.
     */
    public RolloutSuccessCriteria withMinObservationPeriod(Duration period) {
        return new RolloutSuccessCriteria(
            maxBuildP95, maxRollbackRate, minCompletionRate,
            maxErrorRate, period, minSampleSize
        );
    }

    /**
     * Creates criteria with custom sample size.
     */
    public RolloutSuccessCriteria withMinSampleSize(int size) {
        return new RolloutSuccessCriteria(
            maxBuildP95, maxRollbackRate, minCompletionRate,
            maxErrorRate, minObservationPeriod, size
        );
    }
}
