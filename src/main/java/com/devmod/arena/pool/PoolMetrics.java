package com.devmod.arena.pool;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Metrics tracking for arena pool with auto-disable (DD65).
 *
 * <p>Tracks hit/miss ratio and auto-disables pool when miss rate exceeds 50%.
 */
public class PoolMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(PoolMetrics.class);

    private static final double AUTO_DISABLE_THRESHOLD = 0.50;
    private static final double ALERT_THRESHOLD_LOW = 0.20;
    private static final double ALERT_THRESHOLD_HIGH = 0.30;

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong poolHits = new AtomicLong(0);
    private final AtomicLong poolMisses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    private final AtomicLong buildRequests = new AtomicLong(0);

    private volatile boolean poolEnabled = true;
    private volatile boolean autoDisabled = false;
    private final PoolFeatureFlag featureFlag;

    public PoolMetrics(PoolFeatureFlag featureFlag) {
        this.featureFlag = featureFlag;
    }

    /**
     * Records a pool hit (arena was available).
     */
    public void recordHit() {
        totalRequests.incrementAndGet();
        poolHits.incrementAndGet();
        checkThresholds();
    }

    /**
     * Records a pool miss (no arena available, must build).
     */
    public void recordMiss() {
        totalRequests.incrementAndGet();
        poolMisses.incrementAndGet();
        buildRequests.incrementAndGet();
        checkThresholds();
    }

    /**
     * Records an eviction from pool.
     */
    public void recordEviction() {
        evictions.incrementAndGet();
    }

    /**
     * Returns the hit rate (0.0 to 1.0).
     */
    public double getHitRate() {
        long total = totalRequests.get();
        if (total == 0) return 0.0;
        return (double) poolHits.get() / total;
    }

    /**
     * Returns the miss rate (0.0 to 1.0).
     */
    public double getMissRate() {
        long total = totalRequests.get();
        if (total == 0) return 0.0;
        return (double) poolMisses.get() / total;
    }

    /**
     * Returns true if the pool is enabled.
     */
    public boolean isPoolEnabled() {
        return poolEnabled && !autoDisabled && featureFlag.isEnabled();
    }

    /**
     * Returns true if pool was auto-disabled due to high miss rate.
     */
    public boolean isAutoDisabled() {
        return autoDisabled;
    }

    /**
     * Manually enables/disables the pool.
     */
    public void setPoolEnabled(boolean enabled) {
        this.poolEnabled = enabled;
        if (enabled) {
            this.autoDisabled = false;
        }
    }

    /**
     * Resets all metrics.
     */
    public void reset() {
        totalRequests.set(0);
        poolHits.set(0);
        poolMisses.set(0);
        evictions.set(0);
        buildRequests.set(0);
        autoDisabled = false;
    }

    /**
     * Returns the current metrics snapshot.
     */
    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
            totalRequests.get(),
            poolHits.get(),
            poolMisses.get(),
            evictions.get(),
            buildRequests.get(),
            getHitRate(),
            getMissRate(),
            isPoolEnabled(),
            autoDisabled
        );
    }

    private void checkThresholds() {
        double missRate = getMissRate();

        // Only check after minimum sample size
        if (totalRequests.get() < 100) {
            return;
        }

        // Auto-disable on high miss rate
        if (missRate > AUTO_DISABLE_THRESHOLD && !autoDisabled) {
            autoDisabled = true;
            LOGGER.warn("Pool auto-disabled: miss rate {}% exceeds {}% threshold",
                String.format("%.1f", missRate * 100),
                String.format("%.0f", AUTO_DISABLE_THRESHOLD * 100));
        }

        // Alert on concerning miss rates
        if (missRate > ALERT_THRESHOLD_HIGH) {
            LOGGER.warn("Pool miss rate {}% exceeds {}% alert threshold",
                String.format("%.1f", missRate * 100),
                String.format("%.0f", ALERT_THRESHOLD_HIGH * 100));
        } else if (missRate > ALERT_THRESHOLD_LOW) {
            LOGGER.info("Pool miss rate {}% approaching alert threshold",
                String.format("%.1f", missRate * 100));
        }
    }

    /**
     * Metrics snapshot for reporting.
     */
    public record MetricsSnapshot(
        long totalRequests,
        long poolHits,
        long poolMisses,
        long evictions,
        long buildRequests,
        double hitRate,
        double missRate,
        boolean poolEnabled,
        boolean autoDisabled
    ) {}

    /**
     * Feature flag for pool functionality.
     */
    public interface PoolFeatureFlag {
        boolean isEnabled();
    }
}
