package com.devmod.arena.cleanup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.alert.AlertRouter;
import com.devmod.arena.alert.ErrorContext;

/**
 * DD40: Robust Cleanup Manager with zero-residual invariant.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Zero residual entities after cleanup (excluding preserved players)</li>
 *   <li>Zero residual blocks after cleanup</li>
 *   <li>Retry mechanism for failed cleanups</li>
 *   <li>Alert routing for cleanup failures</li>
 *   <li>Detailed cleanup telemetry</li>
 * </ul>
 *
 * <p>Invariant enforcement:
 * <ul>
 *   <li>Post-cleanup verification is MANDATORY</li>
 *   <li>If residuals detected, retry up to MAX_RETRY_ATTEMPTS</li>
 *   <li>If still dirty after retries, raise CRITICAL alert</li>
 * </ul>
 */
public class RobustCleanupManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RobustCleanupManager.class);

    /** Maximum cleanup retry attempts */
    public static final int MAX_RETRY_ATTEMPTS = 3;

    /** Delay between retry attempts */
    public static final Duration RETRY_DELAY = Duration.ofMillis(500);

    /** Maximum time for cleanup operation */
    public static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(30);

    private final ArenaCleanupExecutor executor;
    private final AlertRouter alertRouter;
    private final CleanupTelemetry telemetry;

    // Statistics
    private final AtomicInteger totalCleanups = new AtomicInteger(0);
    private final AtomicInteger successfulCleanups = new AtomicInteger(0);
    private final AtomicInteger failedCleanups = new AtomicInteger(0);
    private final AtomicInteger retriedCleanups = new AtomicInteger(0);

    public RobustCleanupManager(ArenaCleanupExecutor executor) {
        this(executor, null);
    }

    public RobustCleanupManager(ArenaCleanupExecutor executor, AlertRouter alertRouter) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.alertRouter = alertRouter;
        this.telemetry = new CleanupTelemetry();
    }

    /**
     * Performs cleanup with zero-residual invariant enforcement.
     *
     * @param arenaId Unique identifier for the arena
     * @param bounds The arena bounds to clean
     * @return RobustCleanupResult with invariant verification
     */
    public RobustCleanupResult cleanupWithInvariant(UUID arenaId, ArenaCleanupExecutor.ArenaBounds bounds) {
        totalCleanups.incrementAndGet();
        long startTime = System.currentTimeMillis();

        LOGGER.info("Starting robust cleanup for arena {} with bounds {}", arenaId, bounds);

        List<CleanupAttempt> attempts = new ArrayList<>();
        CleanupResult lastResult = null;
        boolean invariantSatisfied = false;

        // Attempt cleanup with retries
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS && !invariantSatisfied; attempt++) {
            if (attempt > 1) {
                retriedCleanups.incrementAndGet();
                LOGGER.warn("Cleanup retry {} for arena {}", attempt, arenaId);

                // Wait before retry
                try {
                    Thread.sleep(RETRY_DELAY.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Execute cleanup
            long attemptStart = System.currentTimeMillis();
            lastResult = executor.execute(bounds);
            long attemptDuration = System.currentTimeMillis() - attemptStart;

            // Verify zero-residual invariant
            InvariantCheck invariantCheck = verifyInvariant(lastResult);
            invariantSatisfied = invariantCheck.satisfied();

            attempts.add(new CleanupAttempt(
                attempt,
                lastResult,
                invariantCheck,
                Duration.ofMillis(attemptDuration)
            ));

            if (invariantSatisfied) {
                LOGGER.info("Cleanup succeeded for arena {} on attempt {}", arenaId, attempt);
            } else {
                LOGGER.warn("Cleanup invariant NOT satisfied for arena {} on attempt {}: {}",
                    arenaId, attempt, invariantCheck.violations());
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;

        // Build result
        RobustCleanupResult result = new RobustCleanupResult(
            arenaId,
            invariantSatisfied,
            attempts,
            lastResult,
            Duration.ofMillis(totalDuration)
        );

        // Update statistics and telemetry
        if (invariantSatisfied) {
            successfulCleanups.incrementAndGet();
            telemetry.recordSuccess(arenaId, result);
        } else {
            failedCleanups.incrementAndGet();
            telemetry.recordFailure(arenaId, result);

            // Route critical alert for cleanup failure
            routeCleanupFailureAlert(arenaId, result);
        }

        return result;
    }

    /**
     * Performs async cleanup with invariant enforcement.
     */
    public CompletableFuture<RobustCleanupResult> cleanupWithInvariantAsync(
            UUID arenaId,
            ArenaCleanupExecutor.ArenaBounds bounds) {

        return CompletableFuture.supplyAsync(() -> cleanupWithInvariant(arenaId, bounds))
            .orTimeout(CLEANUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                LOGGER.error("Async cleanup failed for arena {}", arenaId, ex);
                failedCleanups.incrementAndGet();

                // Create failure result
                return new RobustCleanupResult(
                    arenaId,
                    false,
                    List.of(),
                    null,
                    CLEANUP_TIMEOUT
                );
            });
    }

    /**
     * Verifies the zero-residual invariant.
     */
    private InvariantCheck verifyInvariant(CleanupResult result) {
        List<String> violations = new ArrayList<>();

        // Check entities residual
        if (result.entitiesResidual() > 0) {
            violations.add(String.format("Entities residual: %d (expected 0)",
                result.entitiesResidual()));
        }

        // Check blocks residual
        if (result.blocksResidual() > 0) {
            violations.add(String.format("Blocks residual: %d (expected 0)",
                result.blocksResidual()));
        }

        // Check cleanup warnings
        if (!result.warnings().isEmpty()) {
            for (String warning : result.warnings()) {
                if (warning.contains("still present")) {
                    violations.add("Verification warning: " + warning);
                }
            }
        }

        return new InvariantCheck(
            violations.isEmpty(),
            violations,
            result.entitiesResidual(),
            result.blocksResidual()
        );
    }

    /**
     * Routes cleanup failure alert.
     */
    private void routeCleanupFailureAlert(UUID arenaId, RobustCleanupResult result) {
        if (alertRouter == null) {
            LOGGER.warn("No AlertRouter configured, cannot route cleanup failure alert");
            return;
        }

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("arenaId", arenaId.toString());
            metadata.put("attempts", result.attempts().size());
            metadata.put("entitiesResidual", result.lastInvariantCheck()
                .map(InvariantCheck::entitiesResidual).orElse(0));
            metadata.put("blocksResidual", result.lastInvariantCheck()
                .map(InvariantCheck::blocksResidual).orElse(0));
            metadata.put("durationMs", result.totalDuration().toMillis());

            ErrorContext context = ErrorContext.builder()
                .errorType("CleanupInvariantViolation")
                .message(String.format("Arena cleanup failed after %d attempts: zero-residual invariant not satisfied",
                    result.attempts().size()))
                .severity(ErrorContext.Severity.CRITICAL)
                .component("cleanup")
                .metadata(metadata)
                .build();

            alertRouter.route(context).exceptionally(ex -> {
                LOGGER.error("Failed to route cleanup failure alert", ex);
                return null;
            });
        } catch (Exception e) {
            LOGGER.error("Failed to create cleanup failure alert", e);
        }
    }

    /**
     * Forces cleanup without retry (for emergencies).
     */
    public CleanupResult forceCleanup(ArenaCleanupExecutor.ArenaBounds bounds) {
        LOGGER.warn("Force cleanup requested (no retries)");
        return executor.execute(bounds);
    }

    /**
     * Gets cleanup statistics.
     */
    public CleanupStats getStats() {
        return new CleanupStats(
            totalCleanups.get(),
            successfulCleanups.get(),
            failedCleanups.get(),
            retriedCleanups.get()
        );
    }

    /**
     * Gets telemetry data.
     */
    public CleanupTelemetry getTelemetry() {
        return telemetry;
    }

    // ========== Supporting Types ==========

    /**
     * Invariant check result.
     */
    public record InvariantCheck(
        boolean satisfied,
        List<String> violations,
        int entitiesResidual,
        int blocksResidual
    ) {
        public InvariantCheck {
            violations = violations != null
                ? Collections.unmodifiableList(new ArrayList<>(violations))
                : Collections.emptyList();
        }

        public boolean hasResiduals() {
            return entitiesResidual > 0 || blocksResidual > 0;
        }
    }

    /**
     * A single cleanup attempt.
     */
    public record CleanupAttempt(
        int attemptNumber,
        CleanupResult result,
        InvariantCheck invariantCheck,
        Duration duration
    ) {}

    /**
     * Result of robust cleanup with invariant enforcement.
     */
    public record RobustCleanupResult(
        UUID arenaId,
        boolean invariantSatisfied,
        List<CleanupAttempt> attempts,
        CleanupResult lastResult,
        Duration totalDuration
    ) {
        public RobustCleanupResult {
            attempts = attempts != null
                ? Collections.unmodifiableList(new ArrayList<>(attempts))
                : Collections.emptyList();
        }

        /**
         * Returns true if cleanup succeeded with zero residuals.
         */
        public boolean isSuccess() {
            return invariantSatisfied;
        }

        /**
         * Returns number of attempts made.
         */
        public int attemptCount() {
            return attempts.size();
        }

        /**
         * Returns true if retries were needed.
         */
        public boolean requiredRetries() {
            return attempts.size() > 1;
        }

        /**
         * Returns the last invariant check.
         */
        public Optional<InvariantCheck> lastInvariantCheck() {
            if (attempts.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(attempts.get(attempts.size() - 1).invariantCheck());
        }

        /**
         * Returns formatted summary.
         */
        public String formatSummary() {
            if (invariantSatisfied) {
                if (attempts.size() == 1) {
                    return String.format("Cleanup SUCCESS for %s (%dms)",
                        arenaId, totalDuration.toMillis());
                }
                return String.format("Cleanup SUCCESS for %s after %d attempts (%dms)",
                    arenaId, attempts.size(), totalDuration.toMillis());
            }
            return String.format("Cleanup FAILED for %s after %d attempts: invariant violated (%dms)",
                arenaId, attempts.size(), totalDuration.toMillis());
        }
    }

    /**
     * Cleanup statistics.
     */
    public record CleanupStats(
        int total,
        int successful,
        int failed,
        int retried
    ) {
        public double successRate() {
            return total > 0 ? (double) successful / total : 0.0;
        }

        public double retryRate() {
            return total > 0 ? (double) retried / total : 0.0;
        }
    }

    /**
     * Telemetry for cleanup operations.
     */
    public static class CleanupTelemetry {
        private final List<TelemetryEntry> entries = Collections.synchronizedList(new ArrayList<>());
        private static final int MAX_ENTRIES = 1000;

        public void recordSuccess(UUID arenaId, RobustCleanupResult result) {
            addEntry(new TelemetryEntry(
                arenaId,
                true,
                result.attemptCount(),
                result.totalDuration(),
                System.currentTimeMillis()
            ));
        }

        public void recordFailure(UUID arenaId, RobustCleanupResult result) {
            addEntry(new TelemetryEntry(
                arenaId,
                false,
                result.attemptCount(),
                result.totalDuration(),
                System.currentTimeMillis()
            ));
        }

        private void addEntry(TelemetryEntry entry) {
            entries.add(entry);
            // Trim old entries
            while (entries.size() > MAX_ENTRIES) {
                entries.remove(0);
            }
        }

        public List<TelemetryEntry> getRecentEntries(int count) {
            int start = Math.max(0, entries.size() - count);
            return new ArrayList<>(entries.subList(start, entries.size()));
        }

        public TelemetrySummary getSummary() {
            if (entries.isEmpty()) {
                return new TelemetrySummary(0, 0, 0, 0, Duration.ZERO);
            }

            int total = entries.size();
            int successful = (int) entries.stream().filter(TelemetryEntry::success).count();
            int failed = total - successful;
            int withRetries = (int) entries.stream().filter(e -> e.attempts() > 1).count();
            double avgDurationMs = entries.stream()
                .mapToLong(e -> e.duration().toMillis())
                .average()
                .orElse(0);

            return new TelemetrySummary(
                total, successful, failed, withRetries,
                Duration.ofMillis((long) avgDurationMs)
            );
        }

        public record TelemetryEntry(
            UUID arenaId,
            boolean success,
            int attempts,
            Duration duration,
            long timestampMs
        ) {}

        public record TelemetrySummary(
            int total,
            int successful,
            int failed,
            int withRetries,
            Duration avgDuration
        ) {}
    }
}
