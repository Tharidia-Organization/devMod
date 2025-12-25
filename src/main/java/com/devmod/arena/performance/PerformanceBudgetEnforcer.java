package com.devmod.arena.performance;

import com.devmod.arena.monitor.MsptMonitor;
import com.devmod.arena.monitor.MsptSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * DD40: Performance Budget Enforcer with MSPT/TPS measurement and fail-fast.
 *
 * <p>Monitors server performance during arena build and enforces:
 * <ul>
 *   <li>MSPT threshold (default: 45ms) - pause/abort if exceeded</li>
 *   <li>TPS threshold (default: 18.0) - pause/abort if below</li>
 *   <li>Build impact tracking (current MSPT - baseline)</li>
 *   <li>Fail-fast: abort build immediately if impact exceeds threshold</li>
 * </ul>
 *
 * <p>Backpressure modes:
 * <ul>
 *   <li>PAUSE - Pause build operations until performance recovers</li>
 *   <li>THROTTLE - Reduce operations per tick</li>
 *   <li>ABORT - Cancel build entirely</li>
 * </ul>
 */
public class PerformanceBudgetEnforcer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceBudgetEnforcer.class);

    // DD40: Default thresholds
    public static final double DEFAULT_MSPT_THRESHOLD = 45.0;
    public static final double DEFAULT_TPS_THRESHOLD = 18.0;
    public static final double DEFAULT_BUILD_IMPACT_THRESHOLD = 20.0;
    public static final int DEFAULT_MAX_CONSECUTIVE_VIOLATIONS = 5;
    public static final Duration DEFAULT_PAUSE_DURATION = Duration.ofMillis(500);

    private final MsptMonitor msptMonitor;
    private final PerformanceConfig config;

    // State tracking
    private volatile double baseline = -1.0;
    private volatile Instant buildStartTime;
    private volatile Instant lastViolationTime;
    private final AtomicInteger consecutiveViolations = new AtomicInteger(0);
    private final AtomicBoolean buildAborted = new AtomicBoolean(false);
    private final AtomicBoolean buildPaused = new AtomicBoolean(false);

    // Telemetry
    private final List<PerformanceSnapshot> snapshots = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger totalPauses = new AtomicInteger(0);
    private final AtomicInteger totalThrottles = new AtomicInteger(0);

    public PerformanceBudgetEnforcer(MsptMonitor msptMonitor) {
        this(msptMonitor, PerformanceConfig.defaults());
    }

    public PerformanceBudgetEnforcer(MsptMonitor msptMonitor, PerformanceConfig config) {
        this.msptMonitor = Objects.requireNonNull(msptMonitor, "msptMonitor");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Captures baseline MSPT before build starts.
     * Must be called before beginBuild().
     */
    public double captureBaseline() {
        this.baseline = msptMonitor.captureBaseline();
        LOGGER.info("Performance baseline captured: {}ms", String.format("%.2f", baseline));
        return baseline;
    }

    /**
     * Captures baseline only if it has not been set yet.
     *
     * @return true if a baseline was captured, false if it was already set or unavailable
     */
    public boolean captureBaselineIfNeeded() {
        if (baseline >= 0) {
            return false;
        }
        return captureBaseline() >= 0;
    }

    /**
     * Begins performance tracking for a build.
     */
    public void beginBuild() {
        if (baseline < 0) {
            LOGGER.warn("Build started without baseline capture, capturing now");
            captureBaseline();
        }

        buildStartTime = Instant.now();
        buildAborted.set(false);
        buildPaused.set(false);
        consecutiveViolations.set(0);
        snapshots.clear();

        LOGGER.info("Performance tracking started for build");
    }

    /**
     * Ends performance tracking for a build.
     */
    public BuildPerformanceReport endBuild() {
        Duration buildDuration = buildStartTime != null
            ? Duration.between(buildStartTime, Instant.now())
            : Duration.ZERO;

        BuildPerformanceReport report = new BuildPerformanceReport(
            baseline,
            getAverageMspt(),
            getPeakMspt(),
            getMaxBuildImpact(),
            buildDuration,
            totalPauses.get(),
            totalThrottles.get(),
            buildAborted.get(),
            new ArrayList<>(snapshots)
        );

        LOGGER.info("Build performance report: {}", report.formatSummary());
        return report;
    }

    /**
     * Checks performance and returns action to take.
     * Call this before each batch of operations.
     *
     * @return PerformanceAction indicating what to do
     */
    public PerformanceAction checkPerformance() {
        if (buildAborted.get()) {
            return PerformanceAction.ABORT;
        }

        MsptSample current = msptMonitor.getCurrentSample();
        double currentMspt = current.mspt();
        double buildImpact = baseline > 0 ? currentMspt - baseline : 0;
        double currentTps = msptToTps(currentMspt);

        // Record snapshot
        snapshots.add(new PerformanceSnapshot(
            Instant.now(),
            currentMspt,
            buildImpact,
            current.confidenceScore()
        ));

        // Trim old snapshots
        while (snapshots.size() > 1000) {
            snapshots.remove(0);
        }

        // Check fail-fast conditions
        PerformanceViolation violation = checkViolation(currentMspt, buildImpact, currentTps);

        if (violation != null) {
            consecutiveViolations.incrementAndGet();
            lastViolationTime = Instant.now();

            LOGGER.warn("Performance violation: {} (consecutive: {})",
                violation, consecutiveViolations.get());

            // Check if should abort
            if (consecutiveViolations.get() >= config.maxConsecutiveViolations()) {
                buildAborted.set(true);
                LOGGER.error("BUILD ABORTED: {} consecutive performance violations",
                    consecutiveViolations.get());
                return PerformanceAction.ABORT;
            }

            // Determine backpressure action
            return determineBackpressureAction(violation);
        }

        // Performance OK, reset violations
        consecutiveViolations.set(0);
        buildPaused.set(false);

        return PerformanceAction.CONTINUE;
    }

    /**
     * Executes an operation with performance monitoring.
     * Automatically applies backpressure if needed.
     *
     * @param operation The operation to execute
     * @return OperationResult with success/failure and metrics
     */
    public <T> OperationResult<T> executeWithBudget(Supplier<T> operation) {
        PerformanceAction action = checkPerformance();

        if (action == PerformanceAction.ABORT) {
            return OperationResult.abortedResult();
        }

        if (action == PerformanceAction.PAUSE) {
            applyPause();
            // Re-check after pause
            action = checkPerformance();
            if (action == PerformanceAction.ABORT) {
                return OperationResult.abortedResult();
            }
        }

        long startNs = System.nanoTime();
        try {
            T result = operation.get();
            long durationNs = System.nanoTime() - startNs;

            return OperationResult.success(result, Duration.ofNanos(durationNs));
        } catch (Exception e) {
            long durationNs = System.nanoTime() - startNs;
            return OperationResult.failed(e, Duration.ofNanos(durationNs));
        }
    }

    /**
     * Applies backpressure action (pause/throttle) when requested.
     */
    public void applyBackpressure(PerformanceAction action) {
        if (action == PerformanceAction.PAUSE) {
            applyPause();
            return;
        }
        if (action == PerformanceAction.THROTTLE) {
            long sleepMs = Math.max(1L, Math.min(10L, config.pauseDuration().toMillis() / 5));
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Applies pause backpressure.
     */
    private void applyPause() {
        recordPauseApplied();

        LOGGER.info("Applying pause backpressure for {}ms", config.pauseDuration().toMillis());

        try {
            Thread.sleep(config.pauseDuration().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        buildPaused.set(false);
    }

    /**
     * Records a pause backpressure without sleeping (for async tick integration).
     */
    public void recordPauseApplied() {
        buildPaused.set(true);
        totalPauses.incrementAndGet();
    }

    /**
     * Checks for performance violations.
     */
    private PerformanceViolation checkViolation(double currentMspt, double buildImpact, double currentTps) {
        // Check MSPT threshold
        if (currentMspt > config.msptThreshold()) {
            return new PerformanceViolation(
                ViolationType.MSPT_EXCEEDED,
                String.format("MSPT %.1fms exceeds threshold %.1fms",
                    currentMspt, config.msptThreshold()),
                currentMspt,
                config.msptThreshold()
            );
        }

        // Check TPS threshold
        if (currentTps < config.tpsThreshold()) {
            return new PerformanceViolation(
                ViolationType.TPS_LOW,
                String.format("TPS %.1f below threshold %.1f",
                    currentTps, config.tpsThreshold()),
                currentTps,
                config.tpsThreshold()
            );
        }

        // Check build impact threshold
        if (buildImpact > config.buildImpactThreshold()) {
            return new PerformanceViolation(
                ViolationType.BUILD_IMPACT_EXCEEDED,
                String.format("Build impact %.1fms exceeds threshold %.1fms",
                    buildImpact, config.buildImpactThreshold()),
                buildImpact,
                config.buildImpactThreshold()
            );
        }

        return null;
    }

    /**
     * Determines backpressure action based on violation severity.
     */
    private PerformanceAction determineBackpressureAction(PerformanceViolation violation) {
        // Critical violations
        if (violation.type() == ViolationType.MSPT_EXCEEDED
            && violation.actual() > config.msptThreshold() * 1.5) {
            return PerformanceAction.PAUSE;
        }

        if (violation.type() == ViolationType.TPS_LOW
            && violation.actual() < config.tpsThreshold() * 0.8) {
            return PerformanceAction.PAUSE;
        }

        if (violation.type() == ViolationType.BUILD_IMPACT_EXCEEDED
            && violation.actual() > config.buildImpactThreshold() * 2) {
            return PerformanceAction.PAUSE;
        }

        // Moderate violations
        totalThrottles.incrementAndGet();
        return PerformanceAction.THROTTLE;
    }

    // ========== Metrics ==========

    public double getCurrentMspt() {
        return msptMonitor.getCurrentSample().mspt();
    }

    public double getBaseline() {
        return baseline;
    }

    public double getBuildImpact() {
        return baseline > 0 ? getCurrentMspt() - baseline : 0;
    }

    public double getAverageMspt() {
        if (snapshots.isEmpty()) return 0;
        return snapshots.stream()
            .mapToDouble(PerformanceSnapshot::mspt)
            .average()
            .orElse(0);
    }

    public double getPeakMspt() {
        if (snapshots.isEmpty()) return 0;
        return snapshots.stream()
            .mapToDouble(PerformanceSnapshot::mspt)
            .max()
            .orElse(0);
    }

    public double getMaxBuildImpact() {
        if (snapshots.isEmpty()) return 0;
        return snapshots.stream()
            .mapToDouble(PerformanceSnapshot::buildImpact)
            .max()
            .orElse(0);
    }

    private double msptToTps(double mspt) {
        if (mspt <= 0) {
            return 20.0;
        }
        return Math.min(1000.0 / mspt, 20.0);
    }

    public boolean isBuildAborted() {
        return buildAborted.get();
    }

    public boolean isBuildPaused() {
        return buildPaused.get();
    }

    public Instant getLastViolationTime() {
        return lastViolationTime;
    }

    // ========== Supporting Types ==========

    /**
     * Actions that can be taken based on performance.
     */
    public enum PerformanceAction {
        /** Continue operations normally */
        CONTINUE,
        /** Reduce operations per tick */
        THROTTLE,
        /** Pause operations temporarily */
        PAUSE,
        /** Abort build entirely */
        ABORT
    }

    /**
     * Types of performance violations.
     */
    public enum ViolationType {
        MSPT_EXCEEDED,
        TPS_LOW,
        BUILD_IMPACT_EXCEEDED
    }

    /**
     * A performance violation.
     */
    public record PerformanceViolation(
        ViolationType type,
        String message,
        double actual,
        double threshold
    ) {}

    /**
     * Configuration for performance enforcement.
     */
    public record PerformanceConfig(
        double msptThreshold,
        double tpsThreshold,
        double buildImpactThreshold,
        int maxConsecutiveViolations,
        Duration pauseDuration
    ) {
        public static PerformanceConfig defaults() {
            return new PerformanceConfig(
                DEFAULT_MSPT_THRESHOLD,
                DEFAULT_TPS_THRESHOLD,
                DEFAULT_BUILD_IMPACT_THRESHOLD,
                DEFAULT_MAX_CONSECUTIVE_VIOLATIONS,
                DEFAULT_PAUSE_DURATION
            );
        }

        public static PerformanceConfig strict() {
            return new PerformanceConfig(
                35.0,  // Lower MSPT threshold
                19.0,  // Higher TPS threshold
                15.0,  // Lower build impact threshold
                3,     // Fewer allowed violations
                Duration.ofSeconds(1)
            );
        }

        public static PerformanceConfig relaxed() {
            return new PerformanceConfig(
                50.0,  // Higher MSPT threshold
                15.0,  // Lower TPS threshold
                30.0,  // Higher build impact threshold
                10,    // More allowed violations
                Duration.ofMillis(250)
            );
        }
    }

    /**
     * A performance snapshot at a point in time.
     */
    public record PerformanceSnapshot(
        Instant timestamp,
        double mspt,
        double buildImpact,
        double confidence
    ) {}

    /**
     * Result of an operation executed with budget.
     */
    public record OperationResult<T>(
        boolean success,
        boolean wasAborted,
        T result,
        Exception error,
        Duration duration
    ) {
        public static <T> OperationResult<T> success(T result, Duration duration) {
            return new OperationResult<>(true, false, result, null, duration);
        }

        public static <T> OperationResult<T> failed(Exception error, Duration duration) {
            return new OperationResult<>(false, false, null, error, duration);
        }

        public static <T> OperationResult<T> abortedResult() {
            return new OperationResult<>(false, true, null, null, Duration.ZERO);
        }
    }

    /**
     * Report of build performance.
     */
    public record BuildPerformanceReport(
        double baseline,
        double averageMspt,
        double peakMspt,
        double maxBuildImpact,
        Duration buildDuration,
        int pauseCount,
        int throttleCount,
        boolean wasAborted,
        List<PerformanceSnapshot> snapshots
    ) {
        public BuildPerformanceReport {
            snapshots = snapshots != null
                ? Collections.unmodifiableList(new ArrayList<>(snapshots))
                : Collections.emptyList();
        }

        public boolean hadPerformanceIssues() {
            return pauseCount > 0 || throttleCount > 0 || wasAborted;
        }

        public String formatSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Build Performance: baseline=%.1fms, avg=%.1fms, peak=%.1fms, impact=%.1fms",
                baseline, averageMspt, peakMspt, maxBuildImpact));

            if (wasAborted) {
                sb.append(" [ABORTED]");
            } else if (hadPerformanceIssues()) {
                sb.append(String.format(" [pauses=%d, throttles=%d]", pauseCount, throttleCount));
            } else {
                sb.append(" [OK]");
            }

            return sb.toString();
        }
    }
}
