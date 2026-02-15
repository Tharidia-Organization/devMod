package com.devmod.arena.builder;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.monitor.MsptMonitor;
import com.devmod.arena.performance.PerformanceBudgetEnforcer;
import com.devmod.arena.telemetry.ArenaTelemetry;

/**
 * Tracks performance metrics for async arena builds: MSPT monitoring, baseline capture,
 * performance budget enforcement, and telemetry reporting.
 *
 * Extracted from AsyncArenaBuilder to reduce class size.
 */
final class AsyncBuildPerformanceTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncBuildPerformanceTracker.class);

    private final MsptMonitor msptMonitor;
    private final PerformanceBudgetEnforcer performanceEnforcer;
    private final ArenaTelemetry telemetry;
    private boolean performanceTrackingActive = false;

    AsyncBuildPerformanceTracker(MsptMonitor msptMonitor, PerformanceBudgetEnforcer performanceEnforcer,
                                 ArenaTelemetry telemetry) {
        this.msptMonitor = msptMonitor;
        this.performanceEnforcer = performanceEnforcer;
        this.telemetry = telemetry;
    }

    boolean isActive() {
        return performanceTrackingActive;
    }

    MsptMonitor getMsptMonitor() {
        return msptMonitor;
    }

    PerformanceBudgetEnforcer getPerformanceEnforcer() {
        return performanceEnforcer;
    }

    void startIfNeeded(double seedMspt) {
        if (performanceTrackingActive) {
            return;
        }
        msptMonitor.reset();
        for (int i = 0; i < 5; i++) {
            msptMonitor.recordSample(seedMspt);
        }
        performanceEnforcer.captureBaseline();
        performanceEnforcer.beginBuild();
        performanceTrackingActive = true;
    }

    void recordSample(double currentMspt) {
        msptMonitor.recordSample(currentMspt);
        performanceEnforcer.captureBaselineIfNeeded();
    }

    PerformanceBudgetEnforcer.PerformanceAction checkPerformance() {
        return performanceEnforcer.checkPerformance();
    }

    double getBuildImpact() {
        return performanceEnforcer.getBuildImpact();
    }

    double getBaseline() {
        return performanceEnforcer.getBaseline();
    }

    double getConfidenceScore() {
        return msptMonitor.getCurrentSample().confidenceScore();
    }

    void recordPauseApplied() {
        performanceEnforcer.recordPauseApplied();
    }

    void finish() {
        if (!performanceTrackingActive) {
            return;
        }
        PerformanceBudgetEnforcer.BuildPerformanceReport report = performanceEnforcer.endBuild();
        telemetry.emit("arena.build.performance_summary", Map.of(
            "buildType", "async",
            "baselineMspt", report.baseline(),
            "averageMspt", report.averageMspt(),
            "peakMspt", report.peakMspt(),
            "maxBuildImpactMs", report.maxBuildImpact(),
            "pauseCount", report.pauseCount(),
            "throttleCount", report.throttleCount(),
            "aborted", report.wasAborted(),
            "durationMs", report.buildDuration().toMillis()
        ));
        performanceTrackingActive = false;
    }

    void emitDegradedTelemetry(double currentMspt, double tps, double buildImpact,
                                PerformanceBudgetEnforcer.PerformanceAction action,
                                int queueSize, int activeBuilds) {
        telemetry.emit("arena.build.perf.degraded", Map.of(
            "mspt", currentMspt,
            "tps", tps,
            "baselineMspt", performanceEnforcer.getBaseline(),
            "buildImpactMs", buildImpact,
            "confidence", msptMonitor.getCurrentSample().confidenceScore(),
            "action", action.name(),
            "queueSize", queueSize,
            "activeBuilds", activeBuilds
        ));
    }

    void emitBackpressureTelemetry(double currentMspt, double tps, double buildImpact,
                                    PerformanceBudgetEnforcer.PerformanceAction action,
                                    int previousBlocks, int blocksPerTick,
                                    int queueSize, int activeBuilds) {
        telemetry.emit("arena.build.backpressure", Map.of(
            "mspt", currentMspt,
            "tps", tps,
            "buildImpactMs", buildImpact,
            "action", action.name(),
            "previousBlocksPerTick", previousBlocks,
            "blocksPerTick", blocksPerTick,
            "queueSize", queueSize,
            "activeBuilds", activeBuilds
        ));
    }

    void emitAbortTelemetry(double currentMspt, double tps, double buildImpact,
                             int queueSize, int activeBuilds) {
        telemetry.emit("arena.build.aborted.performance", Map.of(
            "mspt", currentMspt,
            "tps", tps,
            "baselineMspt", performanceEnforcer.getBaseline(),
            "buildImpactMs", buildImpact,
            "queueSize", queueSize,
            "activeBuilds", activeBuilds
        ));
    }
}
