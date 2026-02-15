package com.devmod.telemetry.duckdb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DuckDBConfig")
class DuckDBConfigTest {

    @AfterEach
    void tearDown() {
        DuckDBConfig.clearSamplingOverride();
    }

    @Test
    @DisplayName("BATCH_SIZE has sensible default")
    void batchSizeSensible() {
        assertTrue(DuckDBConfig.BATCH_SIZE > 0, "BATCH_SIZE should be positive");
        assertTrue(DuckDBConfig.BATCH_SIZE <= 10000, "BATCH_SIZE should not be excessive");
    }

    @Test
    @DisplayName("FLUSH_INTERVAL_MS has sensible default")
    void flushIntervalSensible() {
        assertTrue(DuckDBConfig.FLUSH_INTERVAL_MS > 0);
        assertTrue(DuckDBConfig.FLUSH_INTERVAL_MS <= 60_000);
    }

    @Test
    @DisplayName("QUEUE_CAPACITY has sensible default")
    void queueCapacitySensible() {
        assertTrue(DuckDBConfig.QUEUE_CAPACITY > 0);
    }

    @Test
    @DisplayName("WRITER_THREADS is 1 (DuckDB single-writer)")
    void writerThreadsIsOne() {
        assertEquals(1, DuckDBConfig.WRITER_THREADS);
    }

    @Test
    @DisplayName("SCHEMA_VERSION is positive")
    void schemaVersionPositive() {
        assertTrue(DuckDBConfig.SCHEMA_VERSION > 0);
    }

    @Test
    @DisplayName("DB_FILENAME has default value")
    void dbFilenameHasDefault() {
        assertNotNull(DuckDBConfig.DB_FILENAME);
        assertFalse(DuckDBConfig.DB_FILENAME.isBlank());
    }

    @Test
    @DisplayName("TELEMETRY_DIR has default value")
    void telemetryDirHasDefault() {
        assertNotNull(DuckDBConfig.TELEMETRY_DIR);
        assertFalse(DuckDBConfig.TELEMETRY_DIR.isBlank());
    }

    @Test
    @DisplayName("network constants are sensible")
    void networkConstantsSensible() {
        assertTrue(DuckDBConfig.MAX_EVENTS_PER_PACKET > 0);
        assertTrue(DuckDBConfig.CLIENT_SYNC_INTERVAL_TICKS > 0);
        assertTrue(DuckDBConfig.MAX_EVENTS_PER_SECOND_PER_CLIENT > 0);
        assertTrue(DuckDBConfig.CLIENT_BUFFER_CAPACITY > 0);
    }

    @Test
    @DisplayName("timeout constants are sensible")
    void timeoutConstantsSensible() {
        assertTrue(DuckDBConfig.CONNECTION_TIMEOUT_SECONDS > 0);
        assertTrue(DuckDBConfig.QUERY_TIMEOUT_SECONDS > 0);
        assertTrue(DuckDBConfig.ANALYTICS_QUERY_TIMEOUT_SECONDS >= DuckDBConfig.QUERY_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("sampling rates are in [0,1] range")
    void samplingRatesInRange() {
        assertTrue(DuckDBConfig.SAMPLE_RATE_LOW >= 0.0 && DuckDBConfig.SAMPLE_RATE_LOW <= 1.0);
        assertTrue(DuckDBConfig.SAMPLE_RATE_NORMAL >= 0.0 && DuckDBConfig.SAMPLE_RATE_NORMAL <= 1.0);
        assertTrue(DuckDBConfig.SAMPLE_RATE_HIGH >= 0.0 && DuckDBConfig.SAMPLE_RATE_HIGH <= 1.0);
        assertTrue(DuckDBConfig.SAMPLE_RATE_CRITICAL >= 0.0 && DuckDBConfig.SAMPLE_RATE_CRITICAL <= 1.0);
    }

    @Test
    @DisplayName("critical rate defaults to 1.0")
    void criticalRateDefaultsToOne() {
        assertEquals(1.0, DuckDBConfig.SAMPLE_RATE_CRITICAL, 0.001);
    }

    @Test
    @DisplayName("sampling override works for tests")
    void samplingOverrideWorksForTests() {
        DuckDBConfig.overrideSamplingForTest(false, 0.5, 0.6, 0.7, 0.8);

        assertFalse(DuckDBConfig.isSamplingEnabled());
        assertEquals(0.5, DuckDBConfig.sampleRateLow(), 0.001);
        assertEquals(0.6, DuckDBConfig.sampleRateNormal(), 0.001);
        assertEquals(0.7, DuckDBConfig.sampleRateHigh(), 0.001);
        assertEquals(0.8, DuckDBConfig.sampleRateCritical(), 0.001);
    }

    @Test
    @DisplayName("clearSamplingOverride restores defaults")
    void clearSamplingOverrideRestoresDefaults() {
        DuckDBConfig.overrideSamplingForTest(false, 0.0, 0.0, 0.0, 0.0);
        DuckDBConfig.clearSamplingOverride();

        // After clear, should return static field values
        assertEquals(DuckDBConfig.SAMPLING_ENABLED, DuckDBConfig.isSamplingEnabled());
        assertEquals(DuckDBConfig.SAMPLE_RATE_LOW, DuckDBConfig.sampleRateLow(), 0.001);
    }

    @Test
    @DisplayName("ndjson fallback toggle works")
    void ndjsonFallbackToggleWorks() {
        boolean original = DuckDBConfig.isNdjsonFallbackEnabled();

        DuckDBConfig.setNdjsonFallbackEnabled(true);
        assertTrue(DuckDBConfig.isNdjsonFallbackEnabled());

        DuckDBConfig.setNdjsonFallbackEnabled(false);
        assertFalse(DuckDBConfig.isNdjsonFallbackEnabled());

        // Restore original
        DuckDBConfig.setNdjsonFallbackEnabled(original);
    }

    @Test
    @DisplayName("enableNdjsonFallbackForSession sets to true")
    void enableNdjsonFallbackForSession() {
        boolean original = DuckDBConfig.isNdjsonFallbackEnabled();

        DuckDBConfig.enableNdjsonFallbackForSession();
        assertTrue(DuckDBConfig.isNdjsonFallbackEnabled());

        // Restore
        DuckDBConfig.setNdjsonFallbackEnabled(original);
    }
}
