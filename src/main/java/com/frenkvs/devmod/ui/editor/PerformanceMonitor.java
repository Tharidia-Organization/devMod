package com.frenkvs.devmod.ui.editor;

import com.frenkvs.devmod.DevMod;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performance monitoring for editor UI.
 *
 * Tracks frame times, render times, and individual operation timing.
 *
 * @see docs/editor-design-system/19-performance-considerations.md
 */
public class PerformanceMonitor {

    // ═══════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    private static final int SAMPLE_SIZE = 60; // 3 seconds at 20 TPS
    private static final double SLOW_OPERATION_THRESHOLD_MS = 1.0;
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;
    private static final double LOW_FPS_THRESHOLD_MS = 50.0;
    private static final double RENDER_BOUND_THRESHOLD_MS = 16.0;
    private static final double ENTITY_HEAVY_THRESHOLD = 1000.0;

    // ═══════════════════════════════════════════════════════════════
    // SINGLETON
    // ═══════════════════════════════════════════════════════════════

    private static PerformanceMonitor instance;

    public static PerformanceMonitor getInstance() {
        if (instance == null) {
            instance = new PerformanceMonitor();
        }
        return instance;
    }

    // ═══════════════════════════════════════════════════════════════
    // FRAME TIMING
    // ═══════════════════════════════════════════════════════════════

    private final Queue<Long> frameTimes = new ArrayDeque<>();
    private final Queue<Long> renderTimes = new ArrayDeque<>();
    private final Queue<Integer> entityCounts = new ArrayDeque<>();

    private long lastFrameTime = System.nanoTime();
    private long renderStartTime = 0;

    // ═══════════════════════════════════════════════════════════════
    // OPERATION TIMING
    // ═══════════════════════════════════════════════════════════════

    private final Map<String, OperationMetric> operationMetrics = new ConcurrentHashMap<>();
    private final Map<String, Long> activeOperations = new ConcurrentHashMap<>();

    public PerformanceMonitor() {}

    // ═══════════════════════════════════════════════════════════════
    // FRAME METHODS
    // ═══════════════════════════════════════════════════════════════

    public void startFrame() {
        long now = System.nanoTime();
        long frameTime = now - lastFrameTime;
        lastFrameTime = now;

        frameTimes.offer(frameTime);
        if (frameTimes.size() > SAMPLE_SIZE) {
            frameTimes.poll();
        }

        renderStartTime = now;
    }

    public void endRender() {
        long renderTime = System.nanoTime() - renderStartTime;
        renderTimes.offer(renderTime);
        if (renderTimes.size() > SAMPLE_SIZE) {
            renderTimes.poll();
        }

        var mc = Minecraft.getInstance();
        int entities = mc.level != null ? mc.level.getEntityCount() : 0;
        entityCounts.offer(entities);
        if (entityCounts.size() > SAMPLE_SIZE) {
            entityCounts.poll();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // OPERATION TIMING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Start timing an operation.
     *
     * @param operation Operation name (e.g., "layout", "render_sections")
     */
    public void startTiming(String operation) {
        activeOperations.put(operation, System.nanoTime());
    }

    /**
     * End timing an operation and record the sample.
     *
     * @param operation Operation name
     */
    public void endTiming(String operation) {
        Long startTime = activeOperations.remove(operation);
        if (startTime == null) {
            return;
        }

        long duration = System.nanoTime() - startTime;
        operationMetrics.computeIfAbsent(operation, k -> new OperationMetric())
            .addSample(duration);
    }

    /**
     * Time an operation using try-with-resources pattern.
     *
     * Usage:
     * <pre>
     * try (var timer = monitor.time("operation")) {
     *     // do work
     * }
     * </pre>
     */
    public TimerHandle time(String operation) {
        startTiming(operation);
        return () -> endTiming(operation);
    }

    /**
     * Auto-closeable timer handle.
     */
    @FunctionalInterface
    public interface TimerHandle extends AutoCloseable {
        @Override
        void close();
    }

    // ═══════════════════════════════════════════════════════════════
    // METRICS
    // ═══════════════════════════════════════════════════════════════

    public PerformanceData getMetrics() {
        double frameMs = calculateAverage(frameTimes) / NANOS_PER_MILLISECOND;
        double renderMs = calculateAverage(renderTimes) / NANOS_PER_MILLISECOND;
        double entityAvg = calculateAverage(entityCounts.stream()
            .mapToLong(Integer::longValue).boxed().toList());
        String bottleneck = identifyBottleneck(frameMs, renderMs, entityAvg);
        return new PerformanceData(frameMs, renderMs, entityAvg, bottleneck);
    }

    /**
     * Get metrics for a specific operation.
     */
    public OperationStats getOperationStats(String operation) {
        OperationMetric metric = operationMetrics.get(operation);
        if (metric == null) {
            return new OperationStats(operation, 0, 0, 0, 0);
        }
        return metric.toStats(operation);
    }

    /**
     * Get all operation stats.
     */
    public Map<String, OperationStats> getAllOperationStats() {
        Map<String, OperationStats> stats = new ConcurrentHashMap<>();
        operationMetrics.forEach((name, metric) -> stats.put(name, metric.toStats(name)));
        return stats;
    }

    /**
     * Log slow operations to console.
     */
    public void logSlowOperations() {
        operationMetrics.forEach((name, metric) -> {
            double avgMs = metric.getAverageMs();
            if (avgMs > SLOW_OPERATION_THRESHOLD_MS) {
                DevMod.LOGGER.warn("[Performance] Slow operation {}: avg={:.2f}ms, max={:.2f}ms, count={}",
                    name, avgMs, metric.getMaxMs(), metric.getSampleCount());
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // RESET
    // ═══════════════════════════════════════════════════════════════

    /**
     * Reset all metrics.
     */
    public void reset() {
        frameTimes.clear();
        renderTimes.clear();
        entityCounts.clear();
        operationMetrics.clear();
        activeOperations.clear();
        lastFrameTime = System.nanoTime();
    }

    /**
     * Reset operation metrics only.
     */
    public void resetOperationMetrics() {
        operationMetrics.clear();
        activeOperations.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private double calculateAverage(Queue<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private double calculateAverage(java.util.List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private String identifyBottleneck(double frameMs, double renderMs, double entities) {
        if (frameMs > LOW_FPS_THRESHOLD_MS) return "LOW_FPS";
        if (renderMs > RENDER_BOUND_THRESHOLD_MS) return "RENDER_BOUND";
        if (entities > ENTITY_HEAVY_THRESHOLD) return "ENTITY_HEAVY";
        return "OPTIMAL";
    }

    // ═══════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Internal metric tracker for operations.
     */
    private static class OperationMetric {
        private long totalTime = 0;
        private long maxTime = 0;
        private int sampleCount = 0;

        void addSample(long durationNanos) {
            totalTime += durationNanos;
            maxTime = Math.max(maxTime, durationNanos);
            sampleCount++;
        }

        double getAverageMs() {
            return sampleCount > 0 ? (totalTime / (double) sampleCount) / NANOS_PER_MILLISECOND : 0;
        }

        double getMaxMs() {
            return maxTime / NANOS_PER_MILLISECOND;
        }

        int getSampleCount() {
            return sampleCount;
        }

        OperationStats toStats(String name) {
            return new OperationStats(name, getAverageMs(), getMaxMs(), totalTime / NANOS_PER_MILLISECOND, sampleCount);
        }
    }

    /**
     * Frame performance data.
     */
    public record PerformanceData(
        double frameTime,
        double renderTime,
        double entityCount,
        String bottleneck
    ) {}

    /**
     * Operation timing statistics.
     */
    public record OperationStats(
        String operation,
        double averageMs,
        double maxMs,
        double totalMs,
        int sampleCount
    ) {}
}
