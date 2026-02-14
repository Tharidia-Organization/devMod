package com.devmod.actions.legacy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionResult;
import com.devmod.actions.engine.EngineFeatureFlags;

/**
 * Compares V1 and V2 pipeline results during shadow mode to validate
 * migration correctness. This is read-only - it logs discrepancies
 * but does not affect action outcomes.
 *
 * <p>Shadow mode is enabled per-action during the migration period.
 * Once all actions show zero discrepancies, shadow mode can be disabled
 * and V1 code removed.
 *
 * <p>Comparisons check:
 * <ol>
 *   <li>Status (OK/BLOCKED/FAILED)</li>
 *   <li>Error code (when both results are BLOCKED)</li>
 *   <li>Duration tolerance (large deviations logged as warnings)</li>
 * </ol>
 *
 * <p>Per-action statistics are accumulated and can be retrieved via
 * {@link #generateReport()}.
 */
public final class ShadowModeComparator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowModeComparator.class);

    /** Default duration tolerance: V2 can be up to 5x slower before flagging. */
    static final double DEFAULT_DURATION_TOLERANCE = 5.0;

    /** Minimum V1 duration to apply tolerance check (avoids noise on sub-ms actions). */
    static final long MIN_DURATION_FOR_TOLERANCE_MS = 5;

    // Global counters
    private static final AtomicLong totalComparisons = new AtomicLong(0);
    private static final AtomicLong totalMismatches = new AtomicLong(0);
    private static final AtomicLong totalMatches = new AtomicLong(0);

    // Per-action statistics
    private static final ConcurrentHashMap<String, ActionStats> perActionStats = new ConcurrentHashMap<>();

    private static volatile double durationTolerance = DEFAULT_DURATION_TOLERANCE;

    private ShadowModeComparator() {}

    // =========================================================================
    // Mismatch types
    // =========================================================================

    /**
     * Types of mismatches that can be detected during shadow comparison.
     */
    public enum MismatchType {
        /** V1 and V2 returned different Status values (OK vs BLOCKED, etc.) */
        STATUS,
        /** Both BLOCKED but with different error codes. */
        ERROR_CODE,
        /** V2 duration exceeded the tolerance threshold relative to V1. */
        DURATION,
        /** V1 succeeded but V2 threw an exception (captured as FAILED). */
        V2_EXCEPTION
    }

    // =========================================================================
    // Comparison result
    // =========================================================================

    /**
     * Result of a single shadow comparison between V1 and V2 results.
     *
     * @param actionId      the action that was compared
     * @param matches       true if V1 and V2 are functionally equivalent
     * @param mismatchType  the type of mismatch, or null if matching
     * @param detail        human-readable description of the mismatch
     * @param v1DurationMs  V1 execution duration in ms
     * @param v2DurationMs  V2 execution duration in ms
     * @param timestamp     when the comparison occurred
     */
    public record ComparisonResult(
        String actionId,
        boolean matches,
        @Nullable MismatchType mismatchType,
        @Nullable String detail,
        long v1DurationMs,
        long v2DurationMs,
        Instant timestamp
    ) {
        /** Creates a matching result. */
        static ComparisonResult match(String actionId, long v1DurationMs, long v2DurationMs) {
            return new ComparisonResult(actionId, true, null, null,
                v1DurationMs, v2DurationMs, Instant.now());
        }

        /** Creates a mismatching result. */
        static ComparisonResult mismatch(String actionId, MismatchType type, String detail,
                                          long v1DurationMs, long v2DurationMs) {
            return new ComparisonResult(actionId, false, type, detail,
                v1DurationMs, v2DurationMs, Instant.now());
        }
    }

    // =========================================================================
    // Shadow report
    // =========================================================================

    /**
     * Aggregated report of all shadow mode comparisons.
     *
     * @param totalComparisons  total number of comparisons performed
     * @param totalMatches      comparisons where V1 and V2 agreed
     * @param totalMismatches   comparisons where V1 and V2 disagreed
     * @param matchRate         percentage of matching comparisons (0.0 - 100.0)
     * @param perActionSummaries per-action statistics, sorted by action ID
     * @param generatedAt       when this report was generated
     */
    public record ShadowModeReport(
        long totalComparisons,
        long totalMatches,
        long totalMismatches,
        double matchRate,
        List<ActionStatsSummary> perActionSummaries,
        Instant generatedAt
    ) {}

    /**
     * Summary statistics for a single action's shadow comparisons.
     *
     * @param actionId      the action ID
     * @param comparisons   number of comparisons for this action
     * @param matches       number of matches
     * @param mismatches    number of mismatches
     * @param avgV1DurationMs average V1 duration in ms
     * @param avgV2DurationMs average V2 duration in ms
     */
    public record ActionStatsSummary(
        String actionId,
        long comparisons,
        long matches,
        long mismatches,
        double avgV1DurationMs,
        double avgV2DurationMs
    ) {}

    // =========================================================================
    // Core comparison
    // =========================================================================

    /**
     * Compares V1 and V2 results for the same action invocation.
     * Returns a structured ComparisonResult and accumulates statistics.
     *
     * @param actionId the action ID that was invoked
     * @param v1Result the result from the V1 pipeline
     * @param v2Result the result from the V2 pipeline
     * @return the comparison result
     */
    public static ComparisonResult compare(String actionId, ActionResult v1Result, ActionResult v2Result) {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(v1Result, "v1Result");
        Objects.requireNonNull(v2Result, "v2Result");

        totalComparisons.incrementAndGet();
        ActionStats stats = perActionStats.computeIfAbsent(actionId, k -> new ActionStats());
        stats.incrementComparisons();
        stats.addV1Duration(v1Result.durationMs());
        stats.addV2Duration(v2Result.durationMs());

        boolean verbose = EngineFeatureFlags.isShadowVerbose();

        // Check 1: Status mismatch
        if (v1Result.status() != v2Result.status()) {
            totalMismatches.incrementAndGet();
            stats.incrementMismatches();

            MismatchType type = (v2Result.isFailed() && v1Result.isSuccess())
                ? MismatchType.V2_EXCEPTION
                : MismatchType.STATUS;

            String detail = String.format("V1=%s, V2=%s", v1Result.status(), v2Result.status());
            LOGGER.warn("[ShadowMode] Status mismatch for '{}': {}", actionId, detail);

            return ComparisonResult.mismatch(actionId, type, detail,
                v1Result.durationMs(), v2Result.durationMs());
        }

        // Check 2: Error code mismatch (both BLOCKED)
        if (v1Result.isBlocked() && v2Result.isBlocked()) {
            if (!Objects.equals(v1Result.errorCode(), v2Result.errorCode())) {
                totalMismatches.incrementAndGet();
                stats.incrementMismatches();

                String detail = String.format("V1 errorCode=%s, V2 errorCode=%s",
                    v1Result.errorCode(), v2Result.errorCode());
                LOGGER.warn("[ShadowMode] Error code mismatch for '{}': {}", actionId, detail);

                return ComparisonResult.mismatch(actionId, MismatchType.ERROR_CODE, detail,
                    v1Result.durationMs(), v2Result.durationMs());
            }
        }

        // Check 3: Duration tolerance (only when V1 had meaningful duration)
        if (v1Result.durationMs() >= MIN_DURATION_FOR_TOLERANCE_MS) {
            double ratio = (double) v2Result.durationMs() / v1Result.durationMs();
            if (ratio > durationTolerance) {
                totalMismatches.incrementAndGet();
                stats.incrementMismatches();

                String detail = String.format("V1=%dms, V2=%dms (%.1fx slower, tolerance=%.1fx)",
                    v1Result.durationMs(), v2Result.durationMs(), ratio, durationTolerance);
                LOGGER.warn("[ShadowMode] Duration mismatch for '{}': {}", actionId, detail);

                return ComparisonResult.mismatch(actionId, MismatchType.DURATION, detail,
                    v1Result.durationMs(), v2Result.durationMs());
            }
        }

        // All checks pass
        totalMatches.incrementAndGet();
        stats.incrementMatches();

        if (verbose) {
            LOGGER.debug("[ShadowMode] Match for '{}': status={}, V1={}ms, V2={}ms",
                actionId, v1Result.status(), v1Result.durationMs(), v2Result.durationMs());
        }

        return ComparisonResult.match(actionId, v1Result.durationMs(), v2Result.durationMs());
    }

    // =========================================================================
    // Report generation
    // =========================================================================

    /**
     * Generates a report of all shadow mode comparisons performed so far.
     *
     * @return the aggregated report
     */
    public static ShadowModeReport generateReport() {
        long comps = totalComparisons.get();
        long matches = totalMatches.get();
        long mismatches = totalMismatches.get();
        double matchRate = comps > 0 ? (matches * 100.0) / comps : 100.0;

        List<ActionStatsSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, ActionStats> entry : perActionStats.entrySet()) {
            ActionStats s = entry.getValue();
            summaries.add(new ActionStatsSummary(
                entry.getKey(),
                s.getComparisons(),
                s.getMatches(),
                s.getMismatches(),
                s.getAvgV1Duration(),
                s.getAvgV2Duration()
            ));
        }
        summaries.sort((a, b) -> a.actionId().compareTo(b.actionId()));

        return new ShadowModeReport(
            comps, matches, mismatches, matchRate,
            Collections.unmodifiableList(summaries),
            Instant.now()
        );
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /**
     * Returns the total number of shadow comparisons performed.
     */
    public static long getTotalComparisons() {
        return totalComparisons.get();
    }

    /**
     * Returns the total number of matches detected.
     */
    public static long getTotalMatches() {
        return totalMatches.get();
    }

    /**
     * Returns the total number of mismatches detected.
     */
    public static long getTotalMismatches() {
        return totalMismatches.get();
    }

    /**
     * Returns the current duration tolerance multiplier.
     */
    public static double getDurationTolerance() {
        return durationTolerance;
    }

    /**
     * Sets the duration tolerance multiplier.
     *
     * @param tolerance the maximum ratio of V2/V1 duration before flagging
     * @throws IllegalArgumentException if tolerance is less than 1.0
     */
    public static void setDurationTolerance(double tolerance) {
        if (tolerance < 1.0) {
            throw new IllegalArgumentException("Duration tolerance must be >= 1.0, got: " + tolerance);
        }
        durationTolerance = tolerance;
    }

    /**
     * Resets all counters and per-action statistics. For testing.
     */
    public static void resetCounters() {
        totalComparisons.set(0);
        totalMismatches.set(0);
        totalMatches.set(0);
        perActionStats.clear();
        durationTolerance = DEFAULT_DURATION_TOLERANCE;
    }

    // =========================================================================
    // Per-action stats accumulator (thread-safe)
    // =========================================================================

    static final class ActionStats {
        private final AtomicLong comparisons = new AtomicLong(0);
        private final AtomicLong matches = new AtomicLong(0);
        private final AtomicLong mismatches = new AtomicLong(0);
        private final AtomicLong totalV1DurationMs = new AtomicLong(0);
        private final AtomicLong totalV2DurationMs = new AtomicLong(0);

        void incrementComparisons() { comparisons.incrementAndGet(); }
        void incrementMatches() { matches.incrementAndGet(); }
        void incrementMismatches() { mismatches.incrementAndGet(); }
        void addV1Duration(long ms) { totalV1DurationMs.addAndGet(ms); }
        void addV2Duration(long ms) { totalV2DurationMs.addAndGet(ms); }

        long getComparisons() { return comparisons.get(); }
        long getMatches() { return matches.get(); }
        long getMismatches() { return mismatches.get(); }

        double getAvgV1Duration() {
            long c = comparisons.get();
            return c > 0 ? (double) totalV1DurationMs.get() / c : 0.0;
        }

        double getAvgV2Duration() {
            long c = comparisons.get();
            return c > 0 ? (double) totalV2DurationMs.get() / c : 0.0;
        }
    }
}
