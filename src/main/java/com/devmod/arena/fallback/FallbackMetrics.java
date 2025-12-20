package com.devmod.arena.fallback;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics for tracking fallback strategy usage.
 * DD45: Dedicated metrics for fallback monitoring.
 */
public class FallbackMetrics {

    public enum MetricType {
        /** Primary build strategy succeeded */
        PRIMARY_SUCCESS,
        /** Fallback strategy was used after primary failure */
        FALLBACK_USED,
        /** All strategies (primary + fallback) failed */
        ALL_FAILED
    }

    private final AtomicLong primarySuccessCount = new AtomicLong(0);
    private final AtomicLong fallbackUsedCount = new AtomicLong(0);
    private final AtomicLong allFailedCount = new AtomicLong(0);
    private final AtomicLong totalAttempts = new AtomicLong(0);

    // Timing metrics
    private final AtomicLong totalPrimaryTimeNanos = new AtomicLong(0);
    private final AtomicLong totalFallbackTimeNanos = new AtomicLong(0);

    /**
     * Record a metric.
     */
    public void record(MetricType type) {
        totalAttempts.incrementAndGet();

        switch (type) {
            case PRIMARY_SUCCESS:
                primarySuccessCount.incrementAndGet();
                break;
            case FALLBACK_USED:
                fallbackUsedCount.incrementAndGet();
                break;
            case ALL_FAILED:
                allFailedCount.incrementAndGet();
                break;
        }
    }

    /**
     * Record primary strategy execution time.
     */
    public void recordPrimaryTime(long nanos) {
        totalPrimaryTimeNanos.addAndGet(nanos);
    }

    /**
     * Record fallback strategy execution time.
     */
    public void recordFallbackTime(long nanos) {
        totalFallbackTimeNanos.addAndGet(nanos);
    }

    /**
     * Get count for a specific metric type.
     */
    public long getCount(MetricType type) {
        switch (type) {
            case PRIMARY_SUCCESS:
                return primarySuccessCount.get();
            case FALLBACK_USED:
                return fallbackUsedCount.get();
            case ALL_FAILED:
                return allFailedCount.get();
            default:
                return 0;
        }
    }

    /**
     * Get total number of build attempts.
     */
    public long getTotalAttempts() {
        return totalAttempts.get();
    }

    /**
     * Get primary success rate as a percentage.
     */
    public double getPrimarySuccessRate() {
        long total = totalAttempts.get();
        if (total == 0) return 100.0;
        return (primarySuccessCount.get() * 100.0) / total;
    }

    /**
     * Get fallback rate as a percentage (how often fallback is needed).
     */
    public double getFallbackRate() {
        long total = totalAttempts.get();
        if (total == 0) return 0.0;
        return (fallbackUsedCount.get() * 100.0) / total;
    }

    /**
     * Get overall failure rate as a percentage.
     */
    public double getOverallFailureRate() {
        long total = totalAttempts.get();
        if (total == 0) return 0.0;
        return (allFailedCount.get() * 100.0) / total;
    }

    /**
     * Get overall success rate (primary + fallback).
     */
    public double getOverallSuccessRate() {
        long total = totalAttempts.get();
        if (total == 0) return 100.0;
        return ((primarySuccessCount.get() + fallbackUsedCount.get()) * 100.0) / total;
    }

    /**
     * Get average primary strategy execution time in milliseconds.
     */
    public double getAveragePrimaryTimeMs() {
        long count = primarySuccessCount.get();
        if (count == 0) return 0.0;
        return (totalPrimaryTimeNanos.get() / 1_000_000.0) / count;
    }

    /**
     * Get average fallback strategy execution time in milliseconds.
     */
    public double getAverageFallbackTimeMs() {
        long count = fallbackUsedCount.get();
        if (count == 0) return 0.0;
        return (totalFallbackTimeNanos.get() / 1_000_000.0) / count;
    }

    /**
     * Reset all metrics.
     */
    public void reset() {
        primarySuccessCount.set(0);
        fallbackUsedCount.set(0);
        allFailedCount.set(0);
        totalAttempts.set(0);
        totalPrimaryTimeNanos.set(0);
        totalFallbackTimeNanos.set(0);
    }

    /**
     * Get a summary string of current metrics.
     */
    public String getSummary() {
        return String.format(
            "FallbackMetrics[total=%d, primarySuccess=%.1f%%, fallbackUsed=%.1f%%, allFailed=%.1f%%]",
            getTotalAttempts(),
            getPrimarySuccessRate(),
            getFallbackRate(),
            getOverallFailureRate()
        );
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
