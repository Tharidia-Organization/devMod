package com.devmod.mailbox.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance monitor for database operations.
 *
 * Tracks query execution times, detects slow queries, and provides metrics
 * for admin dashboards and index optimization suggestions.
 */
public final class DbPerformanceMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbPerformanceMonitor.class);

    public static final DbPerformanceMonitor INSTANCE = new DbPerformanceMonitor();

    /** Threshold for slow query warnings (ms). */
    private static final long SLOW_QUERY_THRESHOLD_MS = 100;

    /** Threshold for very slow query errors (ms). */
    private static final long VERY_SLOW_QUERY_THRESHOLD_MS = 500;

    /** Maximum slow queries to keep in history. */
    private static final int MAX_SLOW_QUERY_HISTORY = 100;

    /** Maximum query stats entries. */
    private static final int MAX_QUERY_STATS = 200;

    // Query statistics by query type/name
    private final Map<String, QueryStats> queryStats = new ConcurrentHashMap<>();

    // Recent slow queries
    private final List<SlowQueryEntry> slowQueryHistory = new CopyOnWriteArrayList<>();

    // Global counters
    private final LongAdder totalQueries = new LongAdder();
    private final LongAdder totalSlowQueries = new LongAdder();
    private final AtomicLong totalExecutionTimeNs = new AtomicLong(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());

    // Monitoring state
    private volatile boolean enabled = true;
    private volatile long slowQueryThresholdMs = SLOW_QUERY_THRESHOLD_MS;

    private DbPerformanceMonitor() {}

    // ============================================================================
    // QUERY TRACKING
    // ============================================================================

    /**
     * Start timing a query.
     *
     * @param queryName the query identifier/name
     * @return a timer that should be stopped when the query completes
     */
    public QueryTimer startQuery(String queryName) {
        if (!enabled) {
            return QueryTimer.NOOP;
        }
        return new QueryTimer(queryName, System.nanoTime());
    }

    /**
     * Record a completed query.
     *
     * @param queryName the query identifier
     * @param durationNs the duration in nanoseconds
     */
    public void recordQuery(String queryName, long durationNs) {
        if (!enabled) {
            return;
        }

        totalQueries.increment();
        totalExecutionTimeNs.addAndGet(durationNs);

        // Update query-specific stats
        queryStats.compute(queryName, (name, stats) -> {
            if (stats == null) {
                stats = new QueryStats(name);
            }
            stats.record(durationNs);
            return stats;
        });

        // Trim stats if too many
        if (queryStats.size() > MAX_QUERY_STATS) {
            queryStats.entrySet().stream()
                .min(Comparator.comparingLong(e -> e.getValue().lastExecutionTime.get()))
                .ifPresent(oldest -> queryStats.remove(oldest.getKey()));
        }

        // Check for slow query
        long durationMs = durationNs / 1_000_000;
        if (durationMs >= slowQueryThresholdMs) {
            handleSlowQuery(queryName, durationMs);
        }
    }

    /**
     * Record a failed query.
     *
     * @param queryName the query identifier
     * @param error the error message
     */
    public void recordError(String queryName, String error) {
        if (!enabled) {
            return;
        }

        queryStats.compute(queryName, (name, stats) -> {
            if (stats == null) {
                stats = new QueryStats(name);
            }
            stats.errorCount.increment();
            return stats;
        });

        LOGGER.warn("[DbMonitor] Query error in {}: {}", queryName, error);
    }

    private void handleSlowQuery(String queryName, long durationMs) {
        totalSlowQueries.increment();

        SlowQueryEntry entry = new SlowQueryEntry(
            queryName,
            durationMs,
            Instant.now()
        );

        slowQueryHistory.add(entry);

        // Trim history
        while (slowQueryHistory.size() > MAX_SLOW_QUERY_HISTORY) {
            slowQueryHistory.remove(0);
        }

        if (durationMs >= VERY_SLOW_QUERY_THRESHOLD_MS) {
            LOGGER.error("[DbMonitor] VERY SLOW QUERY: {} took {}ms", queryName, durationMs);
        } else {
            LOGGER.warn("[DbMonitor] Slow query: {} took {}ms", queryName, durationMs);
        }
    }

    // ============================================================================
    // METRICS
    // ============================================================================

    /**
     * Get overall performance metrics.
     */
    public PerformanceMetrics getMetrics() {
        long queries = totalQueries.sum();
        long totalTimeNs = totalExecutionTimeNs.get();
        long slowQueries = totalSlowQueries.sum();

        double avgTimeMs = queries > 0 ? (totalTimeNs / 1_000_000.0) / queries : 0;

        // Find slowest queries
        List<QuerySummary> slowestQueries = queryStats.values().stream()
            .sorted(Comparator.comparingDouble(QueryStats::getAvgTimeMs).reversed())
            .limit(10)
            .map(qs -> new QuerySummary(
                qs.queryName,
                qs.count.sum(),
                qs.getAvgTimeMs(),
                qs.maxTimeNs.get() / 1_000_000.0,
                qs.errorCount.sum()
            ))
            .toList();

        // Find most frequent queries
        List<QuerySummary> mostFrequent = queryStats.values().stream()
            .sorted(Comparator.comparingLong(qs -> -qs.count.sum()))
            .limit(10)
            .map(qs -> new QuerySummary(
                qs.queryName,
                qs.count.sum(),
                qs.getAvgTimeMs(),
                qs.maxTimeNs.get() / 1_000_000.0,
                qs.errorCount.sum()
            ))
            .toList();

        return new PerformanceMetrics(
            queries,
            slowQueries,
            avgTimeMs,
            slowestQueries,
            mostFrequent,
            Duration.ofMillis(System.currentTimeMillis() - lastResetTime.get())
        );
    }

    /**
     * Get stats for a specific query.
     */
    @Nullable
    public QuerySummary getQueryStats(String queryName) {
        QueryStats stats = queryStats.get(queryName);
        if (stats == null) {
            return null;
        }

        return new QuerySummary(
            stats.queryName,
            stats.count.sum(),
            stats.getAvgTimeMs(),
            stats.maxTimeNs.get() / 1_000_000.0,
            stats.errorCount.sum()
        );
    }

    /**
     * Get recent slow queries.
     */
    public List<SlowQueryEntry> getSlowQueryHistory(int limit) {
        int size = slowQueryHistory.size();
        int fromIndex = Math.max(0, size - limit);
        return slowQueryHistory.subList(fromIndex, size).stream()
            .sorted(Comparator.comparing(SlowQueryEntry::timestamp).reversed())
            .toList();
    }

    /**
     * Get index recommendations based on slow queries.
     */
    public List<IndexRecommendation> getIndexRecommendations() {
        // Analyze slow queries to suggest indexes
        Map<String, Long> slowQueryCounts = new HashMap<>();

        for (SlowQueryEntry entry : slowQueryHistory) {
            slowQueryCounts.merge(entry.queryName(), 1L, (a, b) -> a + b);
        }

        return slowQueryCounts.entrySet().stream()
            .filter(e -> e.getValue() >= 3) // At least 3 slow occurrences
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(e -> suggestIndexForQuery(e.getKey(), e.getValue()))
            .toList();
    }

    private IndexRecommendation suggestIndexForQuery(String queryName, long slowCount) {
        // Basic heuristics based on query name patterns
        String suggestion;
        String priority;

        if (queryName.contains("getMessagesForPlayer") || queryName.contains("player")) {
            suggestion = "CREATE INDEX IF NOT EXISTS idx_messages_recipient ON mailbox_messages(recipient_uuid)";
            priority = "HIGH";
        } else if (queryName.contains("getUnread")) {
            suggestion = "CREATE INDEX IF NOT EXISTS idx_messages_unread ON mailbox_messages(recipient_uuid, read_at) WHERE read_at IS NULL";
            priority = "HIGH";
        } else if (queryName.contains("getActiveNews") || queryName.contains("news")) {
            suggestion = "CREATE INDEX IF NOT EXISTS idx_news_active ON news_articles(active, publish_at, expires_at)";
            priority = "MEDIUM";
        } else if (queryName.contains("getTasks") || queryName.contains("task")) {
            suggestion = "CREATE INDEX IF NOT EXISTS idx_tasks_assigned ON test_tasks(assigned_to)";
            priority = "MEDIUM";
        } else {
            suggestion = "Review query " + queryName + " for potential index optimization";
            priority = "LOW";
        }

        return new IndexRecommendation(
            queryName,
            slowCount,
            suggestion,
            priority
        );
    }

    // ============================================================================
    // CONFIGURATION
    // ============================================================================

    /**
     * Enable or disable monitoring.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("[DbMonitor] Monitoring {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Check if monitoring is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set slow query threshold.
     */
    public void setSlowQueryThreshold(long thresholdMs) {
        this.slowQueryThresholdMs = Math.max(1, thresholdMs);
        LOGGER.info("[DbMonitor] Slow query threshold set to {}ms", this.slowQueryThresholdMs);
    }

    /**
     * Get slow query threshold.
     */
    public long getSlowQueryThreshold() {
        return slowQueryThresholdMs;
    }

    /**
     * Reset all statistics.
     */
    public void reset() {
        queryStats.clear();
        slowQueryHistory.clear();
        totalQueries.reset();
        totalSlowQueries.reset();
        totalExecutionTimeNs.set(0);
        lastResetTime.set(System.currentTimeMillis());
        LOGGER.info("[DbMonitor] Statistics reset");
    }

    // ============================================================================
    // TYPES
    // ============================================================================

    /**
     * Timer for measuring query duration.
     */
    public static final class QueryTimer implements AutoCloseable {
        static final QueryTimer NOOP = new QueryTimer(null, 0);

        @Nullable
        private final String queryName;
        private final long startTimeNs;
        private boolean stopped = false;

        QueryTimer(@Nullable String queryName, long startTimeNs) {
            this.queryName = queryName;
            this.startTimeNs = startTimeNs;
        }

        /**
         * Stop the timer and record the query.
         */
        public void stop() {
            if (stopped || queryName == null) {
                return;
            }
            stopped = true;
            long durationNs = System.nanoTime() - startTimeNs;
            DbPerformanceMonitor.INSTANCE.recordQuery(queryName, durationNs);
        }

        /**
         * Stop with error.
         */
        public void stopWithError(String error) {
            if (stopped || queryName == null) {
                return;
            }
            stopped = true;
            DbPerformanceMonitor.INSTANCE.recordError(queryName, error);
        }

        @Override
        public void close() {
            stop();
        }
    }

    /**
     * Internal query statistics tracker.
     */
    private static final class QueryStats {
        final String queryName;
        final LongAdder count = new LongAdder();
        final AtomicLong totalTimeNs = new AtomicLong(0);
        final AtomicLong maxTimeNs = new AtomicLong(0);
        final AtomicLong minTimeNs = new AtomicLong(Long.MAX_VALUE);
        final LongAdder errorCount = new LongAdder();
        final AtomicLong lastExecutionTime = new AtomicLong(System.currentTimeMillis());

        QueryStats(String queryName) {
            this.queryName = queryName;
        }

        void record(long durationNs) {
            count.increment();
            totalTimeNs.addAndGet(durationNs);
            lastExecutionTime.set(System.currentTimeMillis());

            // Update max
            long currentMax;
            do {
                currentMax = maxTimeNs.get();
                if (durationNs <= currentMax) break;
            } while (!maxTimeNs.compareAndSet(currentMax, durationNs));

            // Update min
            long currentMin;
            do {
                currentMin = minTimeNs.get();
                if (durationNs >= currentMin) break;
            } while (!minTimeNs.compareAndSet(currentMin, durationNs));
        }

        double getAvgTimeMs() {
            long c = count.sum();
            return c > 0 ? (totalTimeNs.get() / 1_000_000.0) / c : 0;
        }
    }

    /**
     * Slow query history entry.
     */
    public record SlowQueryEntry(
        String queryName,
        long durationMs,
        Instant timestamp
    ) {}

    /**
     * Query summary for metrics.
     */
    public record QuerySummary(
        String queryName,
        long executionCount,
        double avgTimeMs,
        double maxTimeMs,
        long errorCount
    ) {}

    /**
     * Index recommendation.
     */
    public record IndexRecommendation(
        String queryName,
        long slowCount,
        String suggestion,
        String priority
    ) {}

    /**
     * Overall performance metrics.
     */
    public record PerformanceMetrics(
        long totalQueries,
        long slowQueries,
        double avgQueryTimeMs,
        List<QuerySummary> slowestQueries,
        List<QuerySummary> mostFrequentQueries,
        Duration monitoringDuration
    ) {
        public double slowQueryPercentage() {
            return totalQueries > 0 ? (slowQueries * 100.0) / totalQueries : 0;
        }
    }
}
