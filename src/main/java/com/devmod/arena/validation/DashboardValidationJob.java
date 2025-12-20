package com.devmod.arena.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Daily dashboard validation job (DD69).
 *
 * <p>Runs at 02:00 daily to verify:
 * <ul>
 *   <li>Row counts match between sources</li>
 *   <li>Aggregate values are consistent</li>
 *   <li>Temporal data is fresh</li>
 * </ul>
 */
public class DashboardValidationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardValidationJob.class);
    private static final LocalTime RUN_TIME = LocalTime.of(2, 0);
    private static final Duration FRESHNESS_THRESHOLD = Duration.ofHours(1);

    private final DataSourceValidator validator;
    private final AlertSender alertSender;
    private final ScheduledExecutorService scheduler;

    private volatile boolean running = false;

    public DashboardValidationJob(DataSourceValidator validator, AlertSender alertSender) {
        this.validator = validator;
        this.alertSender = alertSender;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DashboardValidationJob");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the scheduled job.
     */
    public void start() {
        if (running) return;
        running = true;

        long initialDelay = calculateInitialDelay();
        long period = TimeUnit.DAYS.toMillis(1);

        scheduler.scheduleAtFixedRate(
            this::runValidation,
            initialDelay,
            period,
            TimeUnit.MILLISECONDS
        );

        LOGGER.info("DashboardValidationJob scheduled to run daily at {}", RUN_TIME);
    }

    /**
     * Stops the scheduled job.
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Runs validation immediately.
     */
    public ValidationResult runValidation() {
        LOGGER.info("Starting dashboard validation");
        long startTime = System.currentTimeMillis();

        List<ValidationCheck> checks = new ArrayList<>();

        try {
            // Row count validation
            checks.add(validateRowCounts());

            // Aggregate validation
            checks.add(validateAggregates());

            // Temporal consistency
            checks.add(validateTemporalConsistency());

            // Freshness check
            checks.add(validateFreshness());

            ValidationResult result = new ValidationResult(
                LocalDateTime.now(),
                checks,
                System.currentTimeMillis() - startTime
            );

            if (!result.isValid()) {
                alertSender.sendValidationAlert(result);
            }

            LOGGER.info("Dashboard validation completed in {}ms: {}",
                result.durationMs(),
                result.isValid() ? "PASSED" : "FAILED");

            return result;

        } catch (Exception e) {
            LOGGER.error("Dashboard validation failed", e);
            return ValidationResult.failed(e.getMessage());
        }
    }

    private ValidationCheck validateRowCounts() {
        try {
            long sourceCount = validator.getSourceRowCount();
            long dashboardCount = validator.getDashboardRowCount();

            boolean passed = sourceCount == dashboardCount;
            String message = passed
                ? String.format("Row counts match: %d", sourceCount)
                : String.format("Row count mismatch: source=%d, dashboard=%d", sourceCount, dashboardCount);

            return new ValidationCheck("row_count", passed, message);
        } catch (Exception e) {
            return new ValidationCheck("row_count", false, "Error: " + e.getMessage());
        }
    }

    private ValidationCheck validateAggregates() {
        try {
            double sourceSum = validator.getSourceAggregateSum();
            double dashboardSum = validator.getDashboardAggregateSum();

            // Allow 0.01% tolerance for floating point
            double tolerance = Math.abs(sourceSum) * 0.0001;
            boolean passed = Math.abs(sourceSum - dashboardSum) <= tolerance;

            String message = passed
                ? String.format("Aggregate sums match: %.2f", sourceSum)
                : String.format("Aggregate mismatch: source=%.2f, dashboard=%.2f", sourceSum, dashboardSum);

            return new ValidationCheck("aggregate_sum", passed, message);
        } catch (Exception e) {
            return new ValidationCheck("aggregate_sum", false, "Error: " + e.getMessage());
        }
    }

    private ValidationCheck validateTemporalConsistency() {
        try {
            LocalDateTime sourceLatest = validator.getSourceLatestTimestamp();
            LocalDateTime dashboardLatest = validator.getDashboardLatestTimestamp();

            // Allow 5 minute lag
            Duration lag = Duration.between(dashboardLatest, sourceLatest);
            boolean passed = lag.toMinutes() <= 5;

            String message = passed
                ? String.format("Temporal data consistent (lag: %ds)", lag.toSeconds())
                : String.format("Temporal lag too high: %d minutes", lag.toMinutes());

            return new ValidationCheck("temporal_consistency", passed, message);
        } catch (Exception e) {
            return new ValidationCheck("temporal_consistency", false, "Error: " + e.getMessage());
        }
    }

    private ValidationCheck validateFreshness() {
        try {
            LocalDateTime lastUpdate = validator.getDashboardLastUpdate();
            Duration age = Duration.between(lastUpdate, LocalDateTime.now());

            boolean passed = age.compareTo(FRESHNESS_THRESHOLD) <= 0;

            String message = passed
                ? String.format("Data is fresh (updated %d minutes ago)", age.toMinutes())
                : String.format("Data is stale (updated %d hours ago)", age.toHours());

            return new ValidationCheck("freshness", passed, message);
        } catch (Exception e) {
            return new ValidationCheck("freshness", false, "Error: " + e.getMessage());
        }
    }

    private long calculateInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.toLocalDate().atTime(RUN_TIME);

        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }

        return Duration.between(now, nextRun).toMillis();
    }

    // ========== Result Types ==========

    public record ValidationResult(
        LocalDateTime timestamp,
        List<ValidationCheck> checks,
        long durationMs
    ) {
        public boolean isValid() {
            return checks.stream().allMatch(ValidationCheck::passed);
        }

        public List<ValidationCheck> getFailedChecks() {
            return checks.stream()
                .filter(c -> !c.passed())
                .toList();
        }

        public static ValidationResult failed(String reason) {
            return new ValidationResult(
                LocalDateTime.now(),
                List.of(new ValidationCheck("error", false, reason)),
                -1
            );
        }
    }

    public record ValidationCheck(
        String name,
        boolean passed,
        String message
    ) {}

    // ========== Dependencies ==========

    public interface DataSourceValidator {
        long getSourceRowCount();
        long getDashboardRowCount();
        double getSourceAggregateSum();
        double getDashboardAggregateSum();
        LocalDateTime getSourceLatestTimestamp();
        LocalDateTime getDashboardLatestTimestamp();
        LocalDateTime getDashboardLastUpdate();
    }

    public interface AlertSender {
        void sendValidationAlert(ValidationResult result);
    }
}
