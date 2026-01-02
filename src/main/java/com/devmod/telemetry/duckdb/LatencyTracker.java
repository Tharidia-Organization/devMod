package com.devmod.telemetry.duckdb;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * P2: Thread-safe latency tracker with percentile calculation (P50, P95, P99).
 * Uses a circular buffer for memory efficiency with configurable window size.
 */
public final class LatencyTracker {

    // Default window size: 1000 samples covers ~16 minutes at 1 sample/second
    private static final int DEFAULT_WINDOW_SIZE = 1000;

    private final long[] samples;
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final AtomicInteger sampleCount = new AtomicInteger(0);
    private final ReentrantLock computeLock = new ReentrantLock();

    // Running statistics (no locking needed)
    private final AtomicLong totalSamples = new AtomicLong(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);
    private final AtomicLong maxLatencyNanos = new AtomicLong(0);
    private final AtomicLong minLatencyNanos = new AtomicLong(Long.MAX_VALUE);

    // Error counters by type
    private final AtomicLong transientErrors = new AtomicLong(0);
    private final AtomicLong permanentErrors = new AtomicLong(0);
    private final AtomicLong degradationErrors = new AtomicLong(0);

    public LatencyTracker() {
        this(DEFAULT_WINDOW_SIZE);
    }

    public LatencyTracker(int windowSize) {
        this.samples = new long[windowSize];
        Arrays.fill(samples, -1L); // -1 indicates empty slot
    }

    /**
     * Record a latency sample in nanoseconds.
     */
    public void recordNanos(long nanos) {
        if (nanos < 0) return;

        // Update running stats atomically
        totalSamples.incrementAndGet();
        totalLatencyNanos.addAndGet(nanos);

        // Update max atomically
        long currentMax;
        do {
            currentMax = maxLatencyNanos.get();
            if (nanos <= currentMax) break;
        } while (!maxLatencyNanos.compareAndSet(currentMax, nanos));

        // Update min atomically
        long currentMin;
        do {
            currentMin = minLatencyNanos.get();
            if (nanos >= currentMin) break;
        } while (!minLatencyNanos.compareAndSet(currentMin, nanos));

        // Add to circular buffer (lock-free write)
        int idx = writeIndex.getAndUpdate(i -> (i + 1) % samples.length);
        samples[idx] = nanos;

        // Track actual sample count (capped at window size)
        int current;
        do {
            current = sampleCount.get();
            if (current >= samples.length) break;
        } while (!sampleCount.compareAndSet(current, current + 1));
    }

    /**
     * Record a latency sample in milliseconds.
     */
    public void recordMs(long ms) {
        recordNanos(ms * 1_000_000L);
    }

    /**
     * Record an error by type.
     */
    public void recordError(DuckDBErrorClassifier.ErrorType type) {
        switch (type) {
            case TRANSIENT -> transientErrors.incrementAndGet();
            case PERMANENT -> permanentErrors.incrementAndGet();
            case DEGRADATION -> degradationErrors.incrementAndGet();
        }
    }

    /**
     * Get latency statistics snapshot.
     */
    public LatencyStats getStats() {
        long count = totalSamples.get();
        if (count == 0) {
            return new LatencyStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        // Compute percentiles from circular buffer
        computeLock.lock();
        try {
            int actualCount = sampleCount.get();
            if (actualCount == 0) {
                return new LatencyStats(count, 0, 0, 0, 0, 0, 0,
                    transientErrors.get(), permanentErrors.get(), degradationErrors.get());
            }

            // Copy valid samples
            long[] sorted = new long[actualCount];
            int copied = 0;
            for (int i = 0; i < samples.length && copied < actualCount; i++) {
                long val = samples[i];
                if (val >= 0) {
                    sorted[copied++] = val;
                }
            }

            if (copied == 0) {
                return new LatencyStats(count, 0, 0, 0, 0, 0, 0,
                    transientErrors.get(), permanentErrors.get(), degradationErrors.get());
            }

            // Sort for percentile calculation
            Arrays.sort(sorted, 0, copied);

            double avgNanos = (double) totalLatencyNanos.get() / count;
            long minNanos = minLatencyNanos.get();
            if (minNanos == Long.MAX_VALUE) minNanos = 0;
            long maxNanos = maxLatencyNanos.get();

            // Percentiles
            long p50Nanos = sorted[Math.min((int) (copied * 0.50), copied - 1)];
            long p95Nanos = sorted[Math.min((int) (copied * 0.95), copied - 1)];
            long p99Nanos = sorted[Math.min((int) (copied * 0.99), copied - 1)];

            return new LatencyStats(
                count,
                avgNanos / 1_000_000.0,
                minNanos / 1_000_000.0,
                maxNanos / 1_000_000.0,
                p50Nanos / 1_000_000.0,
                p95Nanos / 1_000_000.0,
                p99Nanos / 1_000_000.0,
                transientErrors.get(),
                permanentErrors.get(),
                degradationErrors.get()
            );
        } finally {
            computeLock.unlock();
        }
    }

    /**
     * Reset all counters and samples.
     */
    public void reset() {
        computeLock.lock();
        try {
            Arrays.fill(samples, -1L);
            writeIndex.set(0);
            sampleCount.set(0);
            totalSamples.set(0);
            totalLatencyNanos.set(0);
            maxLatencyNanos.set(0);
            minLatencyNanos.set(Long.MAX_VALUE);
            transientErrors.set(0);
            permanentErrors.set(0);
            degradationErrors.set(0);
        } finally {
            computeLock.unlock();
        }
    }

    /**
     * Immutable latency statistics snapshot.
     */
    public record LatencyStats(
        long sampleCount,
        double avgMs,
        double minMs,
        double maxMs,
        double p50Ms,
        double p95Ms,
        double p99Ms,
        long transientErrors,
        long permanentErrors,
        long degradationErrors
    ) {
        /**
         * Total error count.
         */
        public long totalErrors() {
            return transientErrors + permanentErrors + degradationErrors;
        }

        /**
         * Format for logging: "avg=X.XX p50=X.XX p95=X.XX p99=X.XX max=X.XX ms (N samples, E errors)"
         */
        public String toLogString() {
            if (sampleCount == 0) {
                return "no samples";
            }
            return String.format(
                "avg=%.2f p50=%.2f p95=%.2f p99=%.2f max=%.2f ms (%d samples, %d errors)",
                avgMs, p50Ms, p95Ms, p99Ms, maxMs, sampleCount, totalErrors()
            );
        }

        /**
         * Check if P99 exceeds threshold (potential performance issue).
         */
        public boolean isP99High(double thresholdMs) {
            return sampleCount > 0 && p99Ms > thresholdMs;
        }

        /**
         * Check if error rate is high (> threshold percentage).
         */
        public boolean isErrorRateHigh(double thresholdPercent) {
            if (sampleCount == 0) return false;
            double errorRate = (double) totalErrors() / sampleCount * 100;
            return errorRate > thresholdPercent;
        }
    }
}
