package com.devmod.telemetry.duckdb.lvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for RollingDPSWindow.
 */
class RollingDPSWindowTest {

    private RollingDPSWindow window;

    @BeforeEach
    void setUp() {
        // Use a small window for testing (5 seconds)
        window = new RollingDPSWindow(5);
    }

    @Test
    @DisplayName("recordDamage accumulates damage in current bucket")
    void testRecordDamage_accumulates() {
        window.recordDamage(10.0);
        window.recordDamage(15.0);
        window.recordDamage(5.0);

        // Total damage = 30, window size = 5, DPS = 6
        assertEquals(6.0, window.getCurrentDPS(), 0.1);
    }

    @Test
    @DisplayName("recordDamage ignores zero and negative values")
    void testRecordDamage_ignoresInvalid() {
        window.recordDamage(10.0);
        window.recordDamage(0.0);
        window.recordDamage(-5.0);

        // Only 10 should be recorded
        assertEquals(2.0, window.getCurrentDPS(), 0.1); // 10 / 5 = 2
    }

    @Test
    @DisplayName("getCurrentDPS returns average over window")
    void testGetCurrentDPS_averagesCorrectly() {
        // Record 50 total damage
        window.recordDamage(50.0);

        // DPS should be 50 / 5 = 10
        assertEquals(10.0, window.getCurrentDPS(), 0.1);
    }

    @Test
    @DisplayName("getTotalDamageInWindow returns total, not average")
    void testGetTotalDamageInWindow() {
        window.recordDamage(25.0);
        window.recordDamage(25.0);

        // Total should be 50
        assertEquals(50.0, window.getTotalDamageInWindow(), 0.1);
    }

    @Test
    @DisplayName("getPeakDPS tracks maximum single-second damage")
    void testGetPeakDPS_tracksMaximum() {
        window.recordDamage(10.0);
        window.recordDamage(5.0);

        // Peak should be 15 (both in same second)
        assertEquals(15.0, window.getPeakDPS(), 0.1);
    }

    @Test
    @DisplayName("reset clears all damage data")
    void testReset_clearsAllData() {
        window.recordDamage(100.0);
        window.recordDamage(50.0);

        window.reset();

        assertEquals(0.0, window.getCurrentDPS(), 0.001);
        assertEquals(0.0, window.getPeakDPS(), 0.001);
        assertFalse(window.hasActivity());
    }

    @Test
    @DisplayName("hasActivity returns true when damage recorded")
    void testHasActivity_detectsDamage() {
        assertFalse(window.hasActivity());

        window.recordDamage(1.0);

        assertTrue(window.hasActivity());
    }

    @Test
    @DisplayName("getWindowSize returns configured window size")
    void testGetWindowSize() {
        assertEquals(5, window.getWindowSize());

        RollingDPSWindow largeWindow = new RollingDPSWindow(60);
        assertEquals(60, largeWindow.getWindowSize());
    }

    @Test
    @DisplayName("window size minimum is 1")
    void testWindowSize_minimumIsOne() {
        RollingDPSWindow tinyWindow = new RollingDPSWindow(0);
        assertEquals(1, tinyWindow.getWindowSize());

        RollingDPSWindow negativeWindow = new RollingDPSWindow(-5);
        assertEquals(1, negativeWindow.getWindowSize());
    }

    @Test
    @DisplayName("default constructor uses config window size")
    void testDefaultConstructor() {
        RollingDPSWindow defaultWindow = new RollingDPSWindow();
        // Should use AggregationConfig.LVC_DPS_WINDOW_SECONDS (60)
        assertEquals(60, defaultWindow.getWindowSize());
    }

    @Test
    @DisplayName("thread safety: concurrent recordDamage calls")
    void testThreadSafety_concurrentRecords() throws InterruptedException {
        final int THREADS = 10;
        final int RECORDS_PER_THREAD = 100;
        final double DAMAGE_PER_RECORD = 1.0;

        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < RECORDS_PER_THREAD; j++) {
                    window.recordDamage(DAMAGE_PER_RECORD);
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        // Total damage should be THREADS * RECORDS_PER_THREAD * DAMAGE_PER_RECORD = 1000
        double expectedTotal = THREADS * RECORDS_PER_THREAD * DAMAGE_PER_RECORD;
        assertEquals(expectedTotal, window.getTotalDamageInWindow(), 1.0);
    }

    @Test
    @DisplayName("multiple calls to getCurrentDPS are idempotent")
    void testGetCurrentDPS_idempotent() {
        window.recordDamage(30.0);

        double dps1 = window.getCurrentDPS();
        double dps2 = window.getCurrentDPS();
        double dps3 = window.getCurrentDPS();

        assertEquals(dps1, dps2, 0.001);
        assertEquals(dps2, dps3, 0.001);
    }
}
