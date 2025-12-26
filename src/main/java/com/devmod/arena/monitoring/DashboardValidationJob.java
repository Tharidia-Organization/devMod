package com.devmod.arena.monitoring;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.alert.AlertRouter;
import com.devmod.arena.alert.ErrorContext;
import com.devmod.telemetry.duckdb.ArenaRecords;
import com.devmod.telemetry.duckdb.DuckDBQueryAPI;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;
public class DashboardValidationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardValidationJob.class);

    private static final Duration MAX_DATA_AGE = Duration.ofHours(1);
    private static final double MAX_VARIANCE_PERCENT = 1.0; // 1% tolerance
    private static final Duration TEMPORAL_LOOKBACK = Duration.ofHours(24);
    private static final Duration MAX_TEMPORAL_GAP = MAX_DATA_AGE;
    private static final Duration FUTURE_TIMESTAMP_TOLERANCE = Duration.ofMinutes(5);
    private static final int MAX_GAP_REPORTS = 3;

    private final AlertRouter alertRouter;
    private final Path ndjsonLogPath;

    public DashboardValidationJob(AlertRouter alertRouter, Path ndjsonLogPath) {
        this.alertRouter = alertRouter;
        this.ndjsonLogPath = ndjsonLogPath;
    }

    /**
     * Legacy constructor for backwards compatibility.
     * @deprecated Use constructor without repository parameter
     */
    @Deprecated
    public DashboardValidationJob(Object repository,
                                   AlertRouter alertRouter, Path ndjsonLogPath) {
        this(alertRouter, ndjsonLogPath);
    }

    private DuckDBQueryAPI getQueryAPI() {
        return DuckDBTelemetryService.INSTANCE.getQueryAPI();
    }

    /**
     * Schedules daily validation at 02:00.
     */
    public void schedule(ScheduledExecutorService executor) {
        // Calculate initial delay to 02:00
        long delayMs = calculateDelayToNextRun();
        executor.scheduleAtFixedRate(
            this::runValidation,
            delayMs,
            TimeUnit.DAYS.toMillis(1),
            TimeUnit.MILLISECONDS
        );
        LOGGER.info("Dashboard validation scheduled, first run in {}ms", delayMs);
    }

    /**
     * DD69: Runs all validation checks.
     */
    public void runValidation() {
        LOGGER.info("Starting dashboard validation job");
        Instant startTime = Instant.now();
        List<ValidationResult> results = new ArrayList<>();

        try {
            results.add(validateRowCounts());
            results.add(validateDataFreshness());
            results.add(validateTemporalConsistency());

            boolean allPassed = results.stream().allMatch(ValidationResult::passed);
            Duration duration = Duration.between(startTime, Instant.now());

            if (allPassed) {
                LOGGER.info("Dashboard validation completed successfully in {}ms", duration.toMillis());
            } else {
                List<String> failures = results.stream()
                    .filter(r -> !r.passed())
                    .map(ValidationResult::message)
                    .toList();

                LOGGER.error("Dashboard validation failed: {}", failures);

                // Create error context and route through AlertRouter
                ErrorContext errorContext = ErrorContext.builder()
                    .component("DashboardValidationJob")
                    .errorType("VALIDATION_FAILED")
                    .message("Dashboard validation failed: " + String.join(", ", failures))
                    .severity(ErrorContext.Severity.ERROR)
                    .build();
                alertRouter.route(errorContext);
            }
        } catch (Exception e) {
            LOGGER.error("Dashboard validation error", e);

            ErrorContext errorContext = ErrorContext.builder()
                .component("DashboardValidationJob")
                .errorType("VALIDATION_ERROR")
                .message("Dashboard validation encountered an error: " + e.getMessage())
                .severity(ErrorContext.Severity.ERROR)
                .fromThrowable(e)
                .build();
            alertRouter.route(errorContext);
        }
    }

    /**
     * DD69: Validates row counts between NDJSON and DuckDB.
     */
    private ValidationResult validateRowCounts() {
        try {
            long ndjsonCount = countNdjsonLines();
            long duckDbCount = countDuckDbRecords();
            double variance = Math.abs(ndjsonCount - duckDbCount) * 100.0 / Math.max(ndjsonCount, 1);

            if (variance > MAX_VARIANCE_PERCENT) {
                return ValidationResult.failed(
                    String.format("Row count mismatch: NDJSON=%d, DuckDB=%d (%.2f%% variance)",
                        ndjsonCount, duckDbCount, variance)
                );
            }
            return ValidationResult.passed("Row counts match within tolerance");
        } catch (Exception e) {
            return ValidationResult.failed("Row count validation error: " + e.getMessage());
        }
    }

    /**
     * DD69: Validates data freshness using recent builds.
     */
    private ValidationResult validateDataFreshness() {
        try {
            // Query most recent builds to check freshness
            List<ArenaRecords.BuildRecord> recentBuilds = getQueryAPI().getArenaRecentBuilds("default_flat_64", 1);

            if (recentBuilds.isEmpty()) {
                return ValidationResult.passed("No builds yet - freshness check skipped");
            }

            Instant lastEvent = recentBuilds.get(0).startedAt();
            Duration age = Duration.between(lastEvent, Instant.now());

            if (age.compareTo(MAX_DATA_AGE) > 0) {
                return ValidationResult.failed(
                    String.format("Data is stale: last event was %d minutes ago",
                        age.toMinutes())
                );
            }
            return ValidationResult.passed("Data is fresh");
        } catch (Exception e) {
            return ValidationResult.failed("Freshness validation error: " + e.getMessage());
        }
    }

    /**
     * DD69: Validates temporal consistency (no future timestamps, no large gaps).
     */
    private ValidationResult validateTemporalConsistency() {
        try {
            Instant futureCutoff = Instant.now().plus(FUTURE_TIMESTAMP_TOLERANCE);
            long futureCount = getQueryAPI().countArenaBuildsAfter(futureCutoff);
            if (futureCount > 0) {
                return ValidationResult.failed(
                    String.format("Future timestamps detected: %d builds after %s",
                        futureCount, futureCutoff)
                );
            }

            Instant since = Instant.now().minus(TEMPORAL_LOOKBACK);
            List<ArenaRecords.TemporalGap> gaps = getQueryAPI().findArenaBuildGaps(
                since,
                MAX_TEMPORAL_GAP,
                MAX_GAP_REPORTS
            );
            if (!gaps.isEmpty()) {
                ArenaRecords.TemporalGap largest = gaps.get(0);
                return ValidationResult.failed(
                    String.format("Temporal gaps detected: largest gap %d minutes between %s and %s",
                        largest.gap().toMinutes(),
                        largest.previous(),
                        largest.current())
                );
            }

            return ValidationResult.passed("Temporal consistency OK");
        } catch (Exception e) {
            return ValidationResult.failed("Temporal consistency validation error: " + e.getMessage());
        }
    }

    /**
     * Counts lines in the NDJSON log file.
     */
    private long countNdjsonLines() throws Exception {
        if (!Files.exists(ndjsonLogPath)) {
            return 0;
        }
        return Files.lines(ndjsonLogPath).count();
    }

    /**
     * Counts total records in DuckDB tables.
     */
    private long countDuckDbRecords() {
        // Count builds as a proxy for total records
        return getQueryAPI().countArenaBuildsAfter(Instant.EPOCH);
    }

    private long calculateDelayToNextRun() {
        // Calculate delay to next 02:00
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime next = now.toLocalDate().atTime(2, 0);
        if (now.isAfter(next)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toMillis();
    }

    /**
     * Validation result.
     */
    public record ValidationResult(boolean passed, String message) {
        public static ValidationResult passed(String message) {
            return new ValidationResult(true, message);
        }

        public static ValidationResult failed(String message) {
            return new ValidationResult(false, message);
        }
    }

    /**
     * DD69: Manual checklist items for weekly spot-check.
     */
    public static final List<String> MANUAL_CHECKLIST = List.of(
        "1. Data freshness: Last update < 1 hour ago",
        "2. Row counts: Compare NDJSON line count vs DuckDB table count",
        "3. Aggregates: Spot-check P95, avg build time, success rate",
        "4. Cross-reference: Verify templateId FK integrity",
        "5. Visual check: Dashboard graphs render correctly, no anomalies"
    );
}
