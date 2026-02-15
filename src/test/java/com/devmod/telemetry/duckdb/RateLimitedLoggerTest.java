package com.devmod.telemetry.duckdb;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@DisplayName("RateLimitedLogger")
class RateLimitedLoggerTest {

    private Logger mockLogger;
    private RateLimitedLogger rateLimited;

    @BeforeEach
    void setUp() {
        mockLogger = mock(Logger.class);
        when(mockLogger.isDebugEnabled()).thenReturn(true);
        // Use a long interval so all subsequent calls are rate-limited
        rateLimited = new RateLimitedLogger(mockLogger, 60_000);
    }

    @Test
    @DisplayName("first warn call always logs")
    void firstWarnCallLogs() {
        rateLimited.warn("test_key", "Test message: {}", "arg1");
        // Varargs is passed as Object[] so delegate sees warn(String, Object[])
        verify(mockLogger, times(1)).warn(eq("Test message: {}"), any(Object[].class));
    }

    @Test
    @DisplayName("second warn call within interval is suppressed")
    void secondWarnCallSuppressed() {
        rateLimited.warn("test_key", "msg");
        rateLimited.warn("test_key", "msg");

        // Only 1 suppressed (second call), first call went through
        assertEquals(1, rateLimited.getTotalSuppressed());
    }

    @Test
    @DisplayName("first error call always logs")
    void firstErrorCallLogs() {
        rateLimited.error("err_key", "Error: {}", "details");
        verify(mockLogger, times(1)).error(eq("Error: {}"), any(Object[].class));
    }

    @Test
    @DisplayName("second error call within interval is suppressed")
    void secondErrorCallSuppressed() {
        rateLimited.error("err_key", "msg");
        rateLimited.error("err_key", "msg");

        assertEquals(1, rateLimited.getTotalSuppressed());
    }

    @Test
    @DisplayName("debug call checks isDebugEnabled first")
    void debugChecksEnabled() {
        when(mockLogger.isDebugEnabled()).thenReturn(false);
        rateLimited.debug("dbg_key", "debug msg");
        verify(mockLogger, never()).debug(anyString());
        verify(mockLogger, never()).debug(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("different keys are tracked independently")
    void differentKeysIndependent() {
        rateLimited.warn("key_a", "msg_a");
        rateLimited.warn("key_b", "msg_b");

        // Both keys should get their first call through (no suppression)
        assertEquals(0, rateLimited.getTotalSuppressed());
    }

    @Test
    @DisplayName("suppressed calls increment counter")
    void suppressedCallsIncrementCounter() {
        rateLimited.warn("key", "msg");
        rateLimited.warn("key", "msg");
        rateLimited.warn("key", "msg");

        assertEquals(2, rateLimited.getTotalSuppressed());
    }

    @Test
    @DisplayName("getSuppressedStats returns per-key counts")
    void getSuppressedStatsReturnsPerKey() {
        rateLimited.warn("key_a", "msg");
        rateLimited.warn("key_a", "msg"); // suppressed
        rateLimited.warn("key_b", "msg");
        rateLimited.warn("key_b", "msg"); // suppressed
        rateLimited.warn("key_b", "msg"); // suppressed

        Map<String, Long> stats = rateLimited.getSuppressedStats();
        assertEquals(1L, stats.get("key_a"));
        assertEquals(2L, stats.get("key_b"));
    }

    @Test
    @DisplayName("resetStats clears all tracking")
    void resetStatsClearsAll() {
        rateLimited.warn("key", "msg");
        rateLimited.warn("key", "msg");

        rateLimited.resetStats();

        assertEquals(0, rateLimited.getTotalSuppressed());
        assertTrue(rateLimited.getSuppressedStats().isEmpty());
    }

    @Test
    @DisplayName("getDelegate returns underlying logger")
    void getDelegateReturnsLogger() {
        assertEquals(mockLogger, rateLimited.getDelegate());
    }

    @Test
    @DisplayName("getIntervalMs returns configured interval")
    void getIntervalMsReturnsInterval() {
        assertEquals(60_000, rateLimited.getIntervalMs());
    }

    @Test
    @DisplayName("zero interval allows every call through")
    void zeroIntervalAllowsAll() {
        RateLimitedLogger noLimit = new RateLimitedLogger(mockLogger, 0);
        noLimit.warn("key", "msg");
        noLimit.warn("key", "msg");
        noLimit.warn("key", "msg");

        // Zero suppressed means all 3 calls went through
        assertEquals(0, noLimit.getTotalSuppressed());
    }
}
