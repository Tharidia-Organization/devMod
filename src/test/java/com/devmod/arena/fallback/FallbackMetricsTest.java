package com.devmod.arena.fallback;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FallbackMetricsTest {

    private FallbackMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new FallbackMetrics();
    }

    @Test
    void initialState() {
        assertEquals(0, metrics.getTotalAttempts());
        assertEquals(100.0, metrics.getPrimarySuccessRate());
        assertEquals(0.0, metrics.getFallbackRate());
        assertEquals(0.0, metrics.getOverallFailureRate());
        assertEquals(100.0, metrics.getOverallSuccessRate());
    }

    @Test
    void recordPrimarySuccess() {
        metrics.record(FallbackMetrics.MetricType.PRIMARY_SUCCESS);
        assertEquals(1, metrics.getTotalAttempts());
        assertEquals(1, metrics.getCount(FallbackMetrics.MetricType.PRIMARY_SUCCESS));
        assertEquals(100.0, metrics.getPrimarySuccessRate());
    }

    @Test
    void recordFallbackUsed() {
        metrics.record(FallbackMetrics.MetricType.FALLBACK_USED);
        assertEquals(1, metrics.getTotalAttempts());
        assertEquals(100.0, metrics.getFallbackRate());
    }

    @Test
    void recordAllFailed() {
        metrics.record(FallbackMetrics.MetricType.ALL_FAILED);
        assertEquals(1, metrics.getTotalAttempts());
        assertEquals(100.0, metrics.getOverallFailureRate());
    }

    @Test
    void mixedMetrics() {
        metrics.record(FallbackMetrics.MetricType.PRIMARY_SUCCESS);
        metrics.record(FallbackMetrics.MetricType.PRIMARY_SUCCESS);
        metrics.record(FallbackMetrics.MetricType.FALLBACK_USED);
        metrics.record(FallbackMetrics.MetricType.ALL_FAILED);

        assertEquals(4, metrics.getTotalAttempts());
        assertEquals(50.0, metrics.getPrimarySuccessRate());
        assertEquals(25.0, metrics.getFallbackRate());
        assertEquals(25.0, metrics.getOverallFailureRate());
        assertEquals(75.0, metrics.getOverallSuccessRate());
    }

    @Test
    void timingMetrics() {
        metrics.record(FallbackMetrics.MetricType.PRIMARY_SUCCESS);
        metrics.recordPrimaryTime(1_000_000); // 1ms
        metrics.recordPrimaryTime(3_000_000); // 3ms
        // 2 primary time records but only 1 primary success count
        assertEquals(4.0, metrics.getAveragePrimaryTimeMs(), 0.001);
    }

    @Test
    void fallbackTimingMetrics() {
        metrics.record(FallbackMetrics.MetricType.FALLBACK_USED);
        metrics.recordFallbackTime(5_000_000); // 5ms
        assertEquals(5.0, metrics.getAverageFallbackTimeMs(), 0.001);
    }

    @Test
    void zeroCountTimingReturnsZero() {
        assertEquals(0.0, metrics.getAveragePrimaryTimeMs());
        assertEquals(0.0, metrics.getAverageFallbackTimeMs());
    }

    @Test
    void reset() {
        metrics.record(FallbackMetrics.MetricType.PRIMARY_SUCCESS);
        metrics.record(FallbackMetrics.MetricType.FALLBACK_USED);
        metrics.recordPrimaryTime(1_000_000);
        metrics.reset();

        assertEquals(0, metrics.getTotalAttempts());
        assertEquals(0, metrics.getCount(FallbackMetrics.MetricType.PRIMARY_SUCCESS));
        assertEquals(0, metrics.getCount(FallbackMetrics.MetricType.FALLBACK_USED));
    }

    @Test
    void summaryString() {
        metrics.record(FallbackMetrics.MetricType.PRIMARY_SUCCESS);
        String summary = metrics.getSummary();
        assertTrue(summary.startsWith("FallbackMetrics["));
        assertTrue(summary.contains("total=1"));
        assertTrue(summary.contains("primarySuccess="));
        assertEquals(summary, metrics.toString());
    }
}
