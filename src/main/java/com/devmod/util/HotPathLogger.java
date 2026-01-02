package com.devmod.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;

/**
 * P1-010: Rate-limited logging for hot paths.
 *
 * Provides utilities to reduce log spam in frequently-called code paths:
 * <ul>
 *   <li>Rate-limited logging (at most N times per interval)</li>
 *   <li>Sample-based logging (log every Nth occurrence)</li>
 *   <li>Deferred message construction (only build message if will be logged)</li>
 *   <li>Aggregated statistics logging</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * // Instead of: LOGGER.debug("Processing entity {}", entityId);
 * HotPathLogger.sample(LOGGER, "entity_process", 100,
 *     () -> "Processing entity " + entityId);
 *
 * // Instead of: LOGGER.info("Spawned mob at {}", pos);
 * HotPathLogger.rateLimited(LOGGER, "mob_spawn", Duration.ofSeconds(5),
 *     () -> "Spawned mob at " + pos);
 * </pre>
 */
public final class HotPathLogger {

    private static final Map<String, RateLimitState> RATE_LIMITS = new ConcurrentHashMap<>();
    private static final Map<String, SampleState> SAMPLE_STATES = new ConcurrentHashMap<>();
    private static final Map<String, AggregateState> AGGREGATE_STATES = new ConcurrentHashMap<>();

    /** Default rate limit interval in milliseconds */
    private static final long DEFAULT_RATE_LIMIT_MS = 5000;

    /** Default sample rate (log every Nth) */
    private static final int DEFAULT_SAMPLE_RATE = 100;

    private HotPathLogger() {}

    // ============================================================================
    // RATE-LIMITED LOGGING
    // ============================================================================

    /**
     * Log at most once per interval.
     *
     * @param logger The logger to use
     * @param key Unique key for this log site
     * @param intervalMs Minimum milliseconds between logs
     * @param messageSupplier Supplier for the log message (only called if will log)
     */
    public static void rateLimitedDebug(Logger logger, String key, long intervalMs, Supplier<String> messageSupplier) {
        if (!logger.isDebugEnabled()) return;
        if (shouldLog(key, intervalMs)) {
            logger.debug(messageSupplier.get());
        }
    }

    /**
     * Log at most once per default interval (5 seconds).
     */
    public static void rateLimitedDebug(Logger logger, String key, Supplier<String> messageSupplier) {
        rateLimitedDebug(logger, key, DEFAULT_RATE_LIMIT_MS, messageSupplier);
    }

    /**
     * Log info at most once per interval.
     */
    public static void rateLimitedInfo(Logger logger, String key, long intervalMs, Supplier<String> messageSupplier) {
        if (!logger.isInfoEnabled()) return;
        if (shouldLog(key, intervalMs)) {
            logger.info(messageSupplier.get());
        }
    }

    /**
     * Log warn at most once per interval.
     */
    public static void rateLimitedWarn(Logger logger, String key, long intervalMs, Supplier<String> messageSupplier) {
        if (!logger.isWarnEnabled()) return;
        if (shouldLog(key, intervalMs)) {
            logger.warn(messageSupplier.get());
        }
    }

    private static boolean shouldLog(String key, long intervalMs) {
        RateLimitState state = RATE_LIMITS.computeIfAbsent(key, k -> new RateLimitState());
        long now = System.currentTimeMillis();
        long lastLog = state.lastLogTime.get();

        if (now - lastLog >= intervalMs) {
            if (state.lastLogTime.compareAndSet(lastLog, now)) {
                long skipped = state.skippedCount.getAndSet(0);
                if (skipped > 0) {
                    // Could optionally log skipped count
                }
                return true;
            }
        }

        state.skippedCount.incrementAndGet();
        return false;
    }

    // ============================================================================
    // SAMPLE-BASED LOGGING
    // ============================================================================

    /**
     * Log every Nth occurrence.
     *
     * @param logger The logger to use
     * @param key Unique key for this log site
     * @param sampleRate Log every Nth call (e.g., 100 means 1 in 100)
     * @param messageSupplier Supplier for the log message
     */
    public static void sampleDebug(Logger logger, String key, int sampleRate, Supplier<String> messageSupplier) {
        if (!logger.isDebugEnabled()) return;
        if (shouldSample(key, sampleRate)) {
            logger.debug(messageSupplier.get());
        }
    }

    /**
     * Log every Nth occurrence with default rate (1 in 100).
     */
    public static void sampleDebug(Logger logger, String key, Supplier<String> messageSupplier) {
        sampleDebug(logger, key, DEFAULT_SAMPLE_RATE, messageSupplier);
    }

    /**
     * Log info every Nth occurrence.
     */
    public static void sampleInfo(Logger logger, String key, int sampleRate, Supplier<String> messageSupplier) {
        if (!logger.isInfoEnabled()) return;
        if (shouldSample(key, sampleRate)) {
            logger.info(messageSupplier.get());
        }
    }

    private static boolean shouldSample(String key, int sampleRate) {
        SampleState state = SAMPLE_STATES.computeIfAbsent(key, k -> new SampleState());
        long count = state.counter.incrementAndGet();
        return count % sampleRate == 0;
    }

    // ============================================================================
    // AGGREGATE LOGGING
    // ============================================================================

    /**
     * Track an event and periodically log aggregate statistics.
     *
     * @param logger The logger to use
     * @param key Unique key for this aggregate
     * @param value Value to track (e.g., duration, count)
     * @param intervalMs Interval between aggregate logs
     */
    public static void aggregate(Logger logger, String key, long value, long intervalMs) {
        AggregateState state = AGGREGATE_STATES.computeIfAbsent(key, k -> new AggregateState());
        state.count.incrementAndGet();
        state.sum.addAndGet(value);
        state.updateMax(value);
        state.updateMin(value);

        long now = System.currentTimeMillis();
        long lastFlush = state.lastFlushTime.get();

        if (now - lastFlush >= intervalMs && state.lastFlushTime.compareAndSet(lastFlush, now)) {
            long count = state.count.getAndSet(0);
            long sum = state.sum.getAndSet(0);
            long max = state.max.getAndSet(Long.MIN_VALUE);
            long min = state.min.getAndSet(Long.MAX_VALUE);

            if (count > 0 && logger.isDebugEnabled()) {
                double avg = (double) sum / count;
                logger.debug("[{}] count={}, avg={}, min={}, max={}",
                    key, count, String.format("%.2f", avg), min, max);
            }
        }
    }

    /**
     * Track an event count without value.
     */
    public static void aggregateCount(Logger logger, String key, long intervalMs) {
        aggregate(logger, key, 1, intervalMs);
    }

    // ============================================================================
    // CONDITIONAL LOGGING HELPERS
    // ============================================================================

    /**
     * Only log if condition is true (avoids message construction).
     */
    public static void debugIf(Logger logger, boolean condition, Supplier<String> messageSupplier) {
        if (condition && logger.isDebugEnabled()) {
            logger.debug(messageSupplier.get());
        }
    }

    /**
     * Only log if condition is true with formatted message.
     */
    public static void debugIf(Logger logger, boolean condition, String format, Object... args) {
        if (condition && logger.isDebugEnabled()) {
            logger.debug(format, args);
        }
    }

    /**
     * Log with level guard (avoids string concatenation when level is disabled).
     */
    public static void debug(Logger logger, Supplier<String> messageSupplier) {
        if (logger.isDebugEnabled()) {
            logger.debug(messageSupplier.get());
        }
    }

    /**
     * Log trace with level guard.
     */
    public static void trace(Logger logger, Supplier<String> messageSupplier) {
        if (logger.isTraceEnabled()) {
            logger.trace(messageSupplier.get());
        }
    }

    // ============================================================================
    // ONCE-ONLY LOGGING
    // ============================================================================

    private static final Map<String, Boolean> ONCE_LOGGED = new ConcurrentHashMap<>();

    /**
     * Log a message only once ever (useful for deprecation warnings, etc.).
     */
    public static void infoOnce(Logger logger, String key, String message) {
        if (ONCE_LOGGED.putIfAbsent(key, Boolean.TRUE) == null) {
            logger.info(message);
        }
    }

    /**
     * Log a warning only once ever.
     */
    public static void warnOnce(Logger logger, String key, String message) {
        if (ONCE_LOGGED.putIfAbsent(key, Boolean.TRUE) == null) {
            logger.warn(message);
        }
    }

    // ============================================================================
    // CLEANUP
    // ============================================================================

    /**
     * Clear all tracking state. Call on server shutdown or reload.
     */
    public static void reset() {
        RATE_LIMITS.clear();
        SAMPLE_STATES.clear();
        AGGREGATE_STATES.clear();
        ONCE_LOGGED.clear();
    }

    /**
     * Get current statistics for monitoring.
     */
    public static LoggingStats getStats() {
        return new LoggingStats(
            RATE_LIMITS.size(),
            SAMPLE_STATES.size(),
            AGGREGATE_STATES.size(),
            ONCE_LOGGED.size()
        );
    }

    public record LoggingStats(
        int rateLimitKeys,
        int sampleKeys,
        int aggregateKeys,
        int onceKeys
    ) {}

    // ============================================================================
    // STATE CLASSES
    // ============================================================================

    private static class RateLimitState {
        final AtomicLong lastLogTime = new AtomicLong(0);
        final AtomicLong skippedCount = new AtomicLong(0);
    }

    private static class SampleState {
        final AtomicLong counter = new AtomicLong(0);
    }

    private static class AggregateState {
        final AtomicLong count = new AtomicLong(0);
        final AtomicLong sum = new AtomicLong(0);
        final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong lastFlushTime = new AtomicLong(0);

        void updateMax(long value) {
            long current;
            do {
                current = max.get();
                if (value <= current) return;
            } while (!max.compareAndSet(current, value));
        }

        void updateMin(long value) {
            long current;
            do {
                current = min.get();
                if (value >= current) return;
            } while (!min.compareAndSet(current, value));
        }
    }
}
