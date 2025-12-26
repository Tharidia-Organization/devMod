package com.devmod.telemetry.duckdb;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;

/**
 * A rate-limited logger that prevents log spam by limiting how often
 * the same type of message can be logged.
 *
 * <p>Based on the pattern from <a href="https://github.com/Swrve/rate-limited-logger">
 * rate-limited-logger</a>: "It is easy to wipe out throughput of a performance-critical
 * backend component by several orders of magnitude with disk I/O in a performance hotspot,
 * caused by a misplaced log call."</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * private static final RateLimitedLogger RATE_LIMITED =
 *     new RateLimitedLogger(LOGGER, 60_000); // 1 min interval
 *
 * // In catch block:
 * RATE_LIMITED.warn("query_failed", "[DuckDB] Query failed: {}", e.getMessage());
 * }</pre>
 *
 * <p>When messages are suppressed, the next allowed log will include
 * "(+N suppressed)" to indicate how many were skipped.</p>
 */
public class RateLimitedLogger {

    private final Logger delegate;
    private final long intervalMs;
    private final Map<String, Long> lastLogTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> suppressedCounts = new ConcurrentHashMap<>();

    /**
     * Create a rate-limited logger.
     *
     * @param delegate    The underlying SLF4J logger
     * @param intervalMs  Minimum interval between logs of the same key (in milliseconds)
     */
    public RateLimitedLogger(Logger delegate, long intervalMs) {
        this.delegate = delegate;
        this.intervalMs = intervalMs;
    }

    /**
     * Log a warning message with rate limiting.
     *
     * @param key     Unique key to identify this log type (e.g., "query_weapons")
     * @param message The log message with {} placeholders
     * @param args    Arguments for the message placeholders
     */
    public void warn(String key, String message, Object... args) {
        if (!shouldLog(key)) {
            suppressedCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
            return;
        }

        long suppressed = getSuppressedAndReset(key);
        if (suppressed > 0) {
            delegate.warn(message + " (+{} suppressed)", appendArg(args, suppressed));
        } else {
            delegate.warn(message, args);
        }
    }

    /**
     * Log an error message with rate limiting.
     *
     * @param key     Unique key to identify this log type
     * @param message The log message with {} placeholders
     * @param args    Arguments for the message placeholders
     */
    public void error(String key, String message, Object... args) {
        if (!shouldLog(key)) {
            suppressedCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
            return;
        }

        long suppressed = getSuppressedAndReset(key);
        if (suppressed > 0) {
            delegate.error(message + " (+{} suppressed)", appendArg(args, suppressed));
        } else {
            delegate.error(message, args);
        }
    }

    /**
     * Log a debug message with rate limiting.
     *
     * @param key     Unique key to identify this log type
     * @param message The log message with {} placeholders
     * @param args    Arguments for the message placeholders
     */
    public void debug(String key, String message, Object... args) {
        if (!delegate.isDebugEnabled()) {
            return; // Skip entirely if debug not enabled
        }

        if (!shouldLog(key)) {
            suppressedCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
            return;
        }

        long suppressed = getSuppressedAndReset(key);
        if (suppressed > 0) {
            delegate.debug(message + " (+{} suppressed)", appendArg(args, suppressed));
        } else {
            delegate.debug(message, args);
        }
    }

    /**
     * Check if we should log for this key based on rate limiting.
     */
    private boolean shouldLog(String key) {
        long now = System.currentTimeMillis();
        Long lastTime = lastLogTime.get(key);

        if (lastTime == null || now - lastTime >= intervalMs) {
            lastLogTime.put(key, now);
            return true;
        }
        return false;
    }

    /**
     * Get suppressed count and reset it atomically.
     */
    private long getSuppressedAndReset(String key) {
        AtomicLong counter = suppressedCounts.get(key);
        return counter != null ? counter.getAndSet(0) : 0;
    }

    /**
     * Append a value to an args array.
     */
    private Object[] appendArg(Object[] args, long value) {
        Object[] newArgs = new Object[args.length + 1];
        System.arraycopy(args, 0, newArgs, 0, args.length);
        newArgs[args.length] = value;
        return newArgs;
    }

    /**
     * Get statistics about suppressed messages.
     *
     * @return Map of key to suppressed count
     */
    public Map<String, Long> getSuppressedStats() {
        Map<String, Long> stats = new HashMap<>();
        suppressedCounts.forEach((k, v) -> stats.put(k, v.get()));
        return stats;
    }

    /**
     * Get total number of suppressed messages across all keys.
     */
    public long getTotalSuppressed() {
        return suppressedCounts.values().stream()
            .mapToLong(AtomicLong::get)
            .sum();
    }

    /**
     * Reset all statistics (for testing or periodic cleanup).
     */
    public void resetStats() {
        suppressedCounts.clear();
        lastLogTime.clear();
    }

    /**
     * Get the underlying logger.
     */
    public Logger getDelegate() {
        return delegate;
    }

    /**
     * Get the rate limit interval in milliseconds.
     */
    public long getIntervalMs() {
        return intervalMs;
    }
}
