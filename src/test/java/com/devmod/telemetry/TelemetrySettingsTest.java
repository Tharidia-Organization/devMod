package com.devmod.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TelemetrySettings")
class TelemetrySettingsTest {

    @Test
    @DisplayName("defaults returns sensible values")
    void defaultsReturnsSensibleValues() {
        TelemetrySettings d = TelemetrySettings.defaults();

        assertNotNull(d);
        assertEquals(3_000, d.stuckMs());
        assertEquals(10_000, d.campingMs());
        assertEquals(3, d.campingHits());
        assertEquals(3_000, d.aggroDropMs());
        assertEquals(2_000, d.outOfBoundsMs());
        assertEquals(4.0, d.outOfBoundsDeltaY(), 0.01);
        assertEquals(100.0, d.bossHpThreshold(), 0.01);
        assertTrue(d.bossPhaseDetectionEnabled());
        assertTrue(d.skillTrackingEnabled());
    }

    @Test
    @DisplayName("defaults returns positive timing values")
    void defaultsAllTimingsPositive() {
        TelemetrySettings d = TelemetrySettings.defaults();

        assertTrue(d.stuckMs() > 0);
        assertTrue(d.campingMs() > 0);
        assertTrue(d.campingHits() > 0);
        assertTrue(d.aggroDropMs() > 0);
        assertTrue(d.outOfBoundsMs() > 0);
        assertTrue(d.outOfBoundsDeltaY() > 0);
        assertTrue(d.bossHpThreshold() > 0);
    }

    @Test
    @DisplayName("record accessor methods work")
    void recordAccessorMethodsWork() {
        TelemetrySettings s = new TelemetrySettings(
            5000, 20000, 5, 4000, 3000, 6.0, 200.0, false, false);

        assertEquals(5000, s.stuckMs());
        assertEquals(20000, s.campingMs());
        assertEquals(5, s.campingHits());
        assertEquals(4000, s.aggroDropMs());
        assertEquals(3000, s.outOfBoundsMs());
        assertEquals(6.0, s.outOfBoundsDeltaY(), 0.01);
        assertEquals(200.0, s.bossHpThreshold(), 0.01);
        assertEquals(false, s.bossPhaseDetectionEnabled());
        assertEquals(false, s.skillTrackingEnabled());
    }
}
