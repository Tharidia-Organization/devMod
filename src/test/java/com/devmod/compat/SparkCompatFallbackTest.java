package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.devmod.compat.mods.spark.SparkCompat;

/**
 * Tests for SparkCompat graceful degradation when Spark is not installed.
 * Verifies all TPS/MSPT queries return safe defaults and status strings
 * indicate unavailability without throwing exceptions.
 */
class SparkCompatFallbackTest {

    // ================================================================
    // Availability
    // ================================================================

    @Test
    void isAvailable_withoutSpark_returnsFalse() {
        assertFalse(SparkCompat.isAvailable());
    }

    @Test
    void isApiAvailable_withoutSpark_returnsFalse() {
        assertFalse(SparkCompat.isApiAvailable());
    }

    // ================================================================
    // TPS queries — should return -1 when unavailable
    // ================================================================

    @Test
    void getTps10Seconds_unavailable_returnsNegativeOne() {
        assertEquals(-1.0, SparkCompat.getTps10Seconds());
    }

    @Test
    void getTps1Minute_unavailable_returnsNegativeOne() {
        assertEquals(-1.0, SparkCompat.getTps1Minute());
    }

    @Test
    void getTps5Minutes_unavailable_returnsNegativeOne() {
        assertEquals(-1.0, SparkCompat.getTps5Minutes());
    }

    @Test
    void getTps15Minutes_unavailable_returnsNegativeOne() {
        assertEquals(-1.0, SparkCompat.getTps15Minutes());
    }

    // ================================================================
    // MSPT queries — should return -1 when unavailable
    // ================================================================

    @Test
    void getMspt10Seconds_unavailable_returnsNegativeOne() {
        assertEquals(-1.0, SparkCompat.getMspt10Seconds());
    }

    @Test
    void getMspt1Minute_unavailable_returnsNegativeOne() {
        assertEquals(-1.0, SparkCompat.getMspt1Minute());
    }

    // ================================================================
    // getAllTpsValues — all -1
    // ================================================================

    @Test
    void getAllTpsValues_unavailable_allNegativeOne() {
        double[] values = SparkCompat.getAllTpsValues();
        assertEquals(4, values.length);
        for (double v : values) {
            assertEquals(-1.0, v);
        }
    }

    // ================================================================
    // Health checks — safe defaults
    // ================================================================

    @Test
    void isTpsHealthy_unavailable_returnsFalse() {
        // -1 < 19, so not healthy
        assertFalse(SparkCompat.isTpsHealthy());
    }

    @Test
    void isTpsCritical_unavailable_returnsFalse() {
        // tps = -1 which is not > 0, so the tps > 0 && tps < 15 check is false
        assertFalse(SparkCompat.isTpsCritical());
    }

    // ================================================================
    // Status strings
    // ================================================================

    @Test
    void getTpsStatusString_unavailable_indicatesNotAvailable() {
        String status = SparkCompat.getTpsStatusString();
        assertTrue(status.contains("N/A") || status.contains("not available"),
            "Status should indicate Spark is not available, got: " + status);
    }

    @Test
    void getPerformanceSummary_unavailable_indicatesNotAvailable() {
        String summary = SparkCompat.getPerformanceSummary();
        assertTrue(summary.contains("not available"),
            "Summary should indicate data is not available, got: " + summary);
    }

    // ================================================================
    // Module metadata
    // ================================================================

    @Test
    void modId_isSpark() {
        var compat = new SparkCompat();
        assertEquals("spark", compat.modId());
    }

    @Test
    void displayName_isSparkProfiler() {
        var compat = new SparkCompat();
        assertEquals("Spark Profiler", compat.displayName());
    }

    @Test
    void featureDescription_isNonEmpty() {
        var compat = new SparkCompat();
        assertFalse(compat.getFeatureDescription().isEmpty());
    }
}
