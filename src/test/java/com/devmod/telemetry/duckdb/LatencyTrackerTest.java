package com.devmod.telemetry.duckdb;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.telemetry.duckdb.LatencyTracker.LatencyStats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LatencyTracker")
class LatencyTrackerTest {

    private LatencyTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new LatencyTracker(100);
    }

    @Test
    @DisplayName("empty tracker returns zero stats")
    void emptyTrackerReturnsZeroStats() {
        LatencyStats stats = tracker.getStats();
        assertEquals(0, stats.sampleCount());
        assertEquals(0.0, stats.avgMs(), 0.001);
        assertEquals(0.0, stats.p50Ms(), 0.001);
        assertEquals(0.0, stats.p95Ms(), 0.001);
        assertEquals(0.0, stats.p99Ms(), 0.001);
    }

    @Test
    @DisplayName("recordNanos tracks samples and computes avg")
    void recordNanosTracksSamples() {
        tracker.recordNanos(1_000_000); // 1ms
        tracker.recordNanos(3_000_000); // 3ms
        tracker.recordNanos(5_000_000); // 5ms

        LatencyStats stats = tracker.getStats();
        assertEquals(3, stats.sampleCount());
        assertEquals(3.0, stats.avgMs(), 0.1); // avg(1,3,5) = 3
    }

    @Test
    @DisplayName("recordMs converts to nanos")
    void recordMsConvertsToNanos() {
        tracker.recordMs(10);
        tracker.recordMs(20);

        LatencyStats stats = tracker.getStats();
        assertEquals(2, stats.sampleCount());
        assertEquals(15.0, stats.avgMs(), 0.1);
    }

    @Test
    @DisplayName("recordNanos ignores negative values")
    void recordNanosIgnoresNegative() {
        tracker.recordNanos(-1);
        assertEquals(0, tracker.getStats().sampleCount());
    }

    @Test
    @DisplayName("min and max latency tracked correctly")
    void minMaxTrackedCorrectly() {
        tracker.recordMs(5);
        tracker.recordMs(15);
        tracker.recordMs(10);

        LatencyStats stats = tracker.getStats();
        assertEquals(5.0, stats.minMs(), 0.1);
        assertEquals(15.0, stats.maxMs(), 0.1);
    }

    @Test
    @DisplayName("percentiles computed from sorted samples")
    void percentilesComputed() {
        // Add 100 samples: 1ms, 2ms, ..., 100ms
        for (int i = 1; i <= 100; i++) {
            tracker.recordMs(i);
        }

        LatencyStats stats = tracker.getStats();
        assertEquals(100, stats.sampleCount());
        // P50 ~ 50, P95 ~ 95, P99 ~ 99
        assertTrue(stats.p50Ms() >= 49 && stats.p50Ms() <= 51,
            "P50 should be ~50, was " + stats.p50Ms());
        assertTrue(stats.p95Ms() >= 94 && stats.p95Ms() <= 96,
            "P95 should be ~95, was " + stats.p95Ms());
        assertTrue(stats.p99Ms() >= 98 && stats.p99Ms() <= 100,
            "P99 should be ~99, was " + stats.p99Ms());
    }

    @Test
    @DisplayName("recordError increments error counters by type")
    void recordErrorIncrements() {
        tracker.recordError(DuckDBErrorClassifier.ErrorType.TRANSIENT);
        tracker.recordError(DuckDBErrorClassifier.ErrorType.TRANSIENT);
        tracker.recordError(DuckDBErrorClassifier.ErrorType.PERMANENT);
        tracker.recordError(DuckDBErrorClassifier.ErrorType.DEGRADATION);

        // Need at least one sample for stats to show errors
        tracker.recordMs(1);

        LatencyStats stats = tracker.getStats();
        assertEquals(2, stats.transientErrors());
        assertEquals(1, stats.permanentErrors());
        assertEquals(1, stats.degradationErrors());
        assertEquals(4, stats.totalErrors());
    }

    @Test
    @DisplayName("reset clears all state")
    void resetClearsAllState() {
        tracker.recordMs(10);
        tracker.recordError(DuckDBErrorClassifier.ErrorType.TRANSIENT);

        tracker.reset();

        LatencyStats stats = tracker.getStats();
        assertEquals(0, stats.sampleCount());
        assertEquals(0, stats.transientErrors());
    }

    @Test
    @DisplayName("LatencyStats.toLogString formats correctly")
    void toLogStringFormats() {
        tracker.recordMs(10);
        tracker.recordMs(20);

        String log = tracker.getStats().toLogString();
        assertNotNull(log);
        assertTrue(log.contains("avg="));
        assertTrue(log.contains("p50="));
        assertTrue(log.contains("samples"));
    }

    @Test
    @DisplayName("LatencyStats.toLogString handles empty")
    void toLogStringEmpty() {
        assertEquals("no samples", tracker.getStats().toLogString());
    }

    @Test
    @DisplayName("LatencyStats.isP99High detects high latency")
    void isP99HighDetectsHighLatency() {
        tracker.recordMs(100);
        assertTrue(tracker.getStats().isP99High(50.0));
        assertFalse(tracker.getStats().isP99High(200.0));
    }

    @Test
    @DisplayName("LatencyStats.isErrorRateHigh detects high error rate")
    void isErrorRateHighDetectsHighRate() {
        tracker.recordMs(1);
        tracker.recordError(DuckDBErrorClassifier.ErrorType.TRANSIENT);

        assertTrue(tracker.getStats().isErrorRateHigh(50.0)); // 100% error rate
        assertFalse(tracker.getStats().isErrorRateHigh(200.0));
    }

    @Test
    @DisplayName("concurrent recording is thread-safe")
    void concurrentRecordingIsThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int recordsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                for (int i = 0; i < recordsPerThread; i++) {
                    tracker.recordMs(1);
                }
                latch.countDown();
            }).start();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        LatencyStats stats = tracker.getStats();
        assertEquals(threadCount * recordsPerThread, stats.sampleCount());
    }

    @Test
    @DisplayName("default constructor uses 1000-sample window")
    void defaultConstructorUsesDefaultWindow() {
        LatencyTracker defaultTracker = new LatencyTracker();
        // Should accept 1000+ samples without error
        for (int i = 0; i < 1500; i++) {
            defaultTracker.recordMs(i);
        }
        assertEquals(1500, defaultTracker.getStats().sampleCount());
    }
}
