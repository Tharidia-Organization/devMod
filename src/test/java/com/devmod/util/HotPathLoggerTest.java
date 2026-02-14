package com.devmod.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("HotPathLogger")
class HotPathLoggerTest {

    private Logger mockLogger;

    @BeforeEach
    void setUp() {
        HotPathLogger.reset();
        mockLogger = Mockito.mock(Logger.class);
        when(mockLogger.isDebugEnabled()).thenReturn(true);
        when(mockLogger.isInfoEnabled()).thenReturn(true);
        when(mockLogger.isWarnEnabled()).thenReturn(true);
        when(mockLogger.isTraceEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        HotPathLogger.reset();
    }

    // ========================================================================
    // Rate-limited logging
    // ========================================================================

    @Nested
    @DisplayName("rate-limited logging")
    class RateLimitedTests {

        @Test
        @DisplayName("first call always logs")
        void firstCallLogs() {
            HotPathLogger.rateLimitedDebug(mockLogger, "test_key", 5000, () -> "msg");
            verify(mockLogger).debug("msg");
        }

        @Test
        @DisplayName("rapid calls within interval are suppressed")
        void rapidCallsSuppressed() {
            HotPathLogger.rateLimitedDebug(mockLogger, "rapid_key", 60_000, () -> "msg");
            HotPathLogger.rateLimitedDebug(mockLogger, "rapid_key", 60_000, () -> "msg2");
            HotPathLogger.rateLimitedDebug(mockLogger, "rapid_key", 60_000, () -> "msg3");
            // Only first should actually log
            verify(mockLogger, times(1)).debug(anyString());
        }

        @Test
        @DisplayName("different keys are independent")
        void differentKeysIndependent() {
            HotPathLogger.rateLimitedDebug(mockLogger, "key_a", 60_000, () -> "a");
            HotPathLogger.rateLimitedDebug(mockLogger, "key_b", 60_000, () -> "b");
            verify(mockLogger).debug("a");
            verify(mockLogger).debug("b");
        }

        @Test
        @DisplayName("disabled log level skips message construction")
        void disabledLevelSkipsMessage() {
            when(mockLogger.isDebugEnabled()).thenReturn(false);
            HotPathLogger.rateLimitedDebug(mockLogger, "disabled", 5000, () -> {
                fail("supplier should not be called");
                return "msg";
            });
            verify(mockLogger, never()).debug(anyString());
        }

        @Test
        @DisplayName("rateLimitedInfo logs at info level")
        void rateLimitedInfoLogs() {
            HotPathLogger.rateLimitedInfo(mockLogger, "info_key", 5000, () -> "info msg");
            verify(mockLogger).info("info msg");
        }

        @Test
        @DisplayName("rateLimitedWarn logs at warn level")
        void rateLimitedWarnLogs() {
            HotPathLogger.rateLimitedWarn(mockLogger, "warn_key", 5000, () -> "warn msg");
            verify(mockLogger).warn("warn msg");
        }
    }

    // ========================================================================
    // Sample-based logging
    // ========================================================================

    @Nested
    @DisplayName("sample-based logging")
    class SampleTests {

        @Test
        @DisplayName("logs every Nth call")
        void logsEveryNth() {
            for (int i = 1; i <= 10; i++) {
                HotPathLogger.sampleDebug(mockLogger, "sample_key", 5, () -> "sampled");
            }
            // Should log at 5 and 10 (every 5th call)
            verify(mockLogger, times(2)).debug("sampled");
        }

        @Test
        @DisplayName("sampleInfo logs at info level")
        void sampleInfoLogs() {
            for (int i = 1; i <= 3; i++) {
                HotPathLogger.sampleInfo(mockLogger, "sample_info", 3, () -> "info");
            }
            verify(mockLogger, times(1)).info("info");
        }

        @Test
        @DisplayName("disabled log level skips sample logging")
        void disabledLevelSkips() {
            when(mockLogger.isDebugEnabled()).thenReturn(false);
            for (int i = 0; i < 100; i++) {
                HotPathLogger.sampleDebug(mockLogger, "disabled_sample", 1, () -> "msg");
            }
            verify(mockLogger, never()).debug(anyString());
        }
    }

    // ========================================================================
    // Conditional logging helpers
    // ========================================================================

    @Nested
    @DisplayName("conditional logging")
    class ConditionalTests {

        @Test
        @DisplayName("debugIf logs when condition is true")
        void debugIfTrue() {
            HotPathLogger.debugIf(mockLogger, true, () -> "conditional");
            verify(mockLogger).debug("conditional");
        }

        @Test
        @DisplayName("debugIf does not log when condition is false")
        void debugIfFalse() {
            HotPathLogger.debugIf(mockLogger, false, () -> {
                fail("supplier should not be called");
                return "msg";
            });
            verify(mockLogger, never()).debug(anyString());
        }

        @Test
        @DisplayName("debugIf with format args logs when condition true")
        void debugIfWithFormat() {
            HotPathLogger.debugIf(mockLogger, true, "msg: {}", "arg");
            verify(mockLogger).debug("msg: {}", new Object[]{"arg"});
        }

        @Test
        @DisplayName("debug with supplier guards on level")
        void debugWithSupplier() {
            HotPathLogger.debug(mockLogger, () -> "debug msg");
            verify(mockLogger).debug("debug msg");
        }

        @Test
        @DisplayName("trace with supplier guards on level")
        void traceWithSupplier() {
            HotPathLogger.trace(mockLogger, () -> "trace msg");
            verify(mockLogger).trace("trace msg");
        }
    }

    // ========================================================================
    // Once-only logging
    // ========================================================================

    @Nested
    @DisplayName("once-only logging")
    class OnceOnlyTests {

        @Test
        @DisplayName("infoOnce logs only the first time")
        void infoOnceLogsOnce() {
            HotPathLogger.infoOnce(mockLogger, "once_key", "first");
            HotPathLogger.infoOnce(mockLogger, "once_key", "second");
            HotPathLogger.infoOnce(mockLogger, "once_key", "third");
            verify(mockLogger, times(1)).info("first");
        }

        @Test
        @DisplayName("warnOnce logs only the first time")
        void warnOnceLogsOnce() {
            HotPathLogger.warnOnce(mockLogger, "warn_once", "warning");
            HotPathLogger.warnOnce(mockLogger, "warn_once", "warning again");
            verify(mockLogger, times(1)).warn("warning");
        }

        @Test
        @DisplayName("different keys allow independent once logging")
        void differentKeysIndependent() {
            HotPathLogger.infoOnce(mockLogger, "key1", "msg1");
            HotPathLogger.infoOnce(mockLogger, "key2", "msg2");
            verify(mockLogger).info("msg1");
            verify(mockLogger).info("msg2");
        }
    }

    // ========================================================================
    // Reset and stats
    // ========================================================================

    @Nested
    @DisplayName("reset and stats")
    class ResetAndStatsTests {

        @Test
        @DisplayName("reset clears all tracking state")
        void resetClearsAll() {
            HotPathLogger.rateLimitedDebug(mockLogger, "k1", 1000, () -> "m");
            HotPathLogger.sampleDebug(mockLogger, "k2", 5, () -> "m");
            HotPathLogger.infoOnce(mockLogger, "k3", "m");

            HotPathLogger.LoggingStats before = HotPathLogger.getStats();
            assertTrue(before.rateLimitKeys() > 0 || before.sampleKeys() > 0 || before.onceKeys() > 0);

            HotPathLogger.reset();

            HotPathLogger.LoggingStats after = HotPathLogger.getStats();
            assertEquals(0, after.rateLimitKeys());
            assertEquals(0, after.sampleKeys());
            assertEquals(0, after.aggregateKeys());
            assertEquals(0, after.onceKeys());
        }

        @Test
        @DisplayName("getStats reflects current state")
        void getStatsReflects() {
            HotPathLogger.rateLimitedDebug(mockLogger, "r1", 1000, () -> "m");
            HotPathLogger.rateLimitedDebug(mockLogger, "r2", 1000, () -> "m");
            HotPathLogger.sampleDebug(mockLogger, "s1", 5, () -> "m");
            HotPathLogger.infoOnce(mockLogger, "o1", "m");

            HotPathLogger.LoggingStats stats = HotPathLogger.getStats();
            assertEquals(2, stats.rateLimitKeys());
            assertEquals(1, stats.sampleKeys());
            assertEquals(1, stats.onceKeys());
        }

        @Test
        @DisplayName("after reset, once logging fires again")
        void resetAllowsOnceAgain() {
            HotPathLogger.infoOnce(mockLogger, "once_reset", "first");
            HotPathLogger.reset();
            HotPathLogger.infoOnce(mockLogger, "once_reset", "after_reset");
            verify(mockLogger).info("first");
            verify(mockLogger).info("after_reset");
        }
    }
}
