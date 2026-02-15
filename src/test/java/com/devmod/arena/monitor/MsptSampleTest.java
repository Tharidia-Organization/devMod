package com.devmod.arena.monitor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MsptSampleTest {

    @Test
    void nowWithFullMetadata() {
        MsptSample sample = MsptSample.now(45.0, 0.9, 42.0, 20.0);
        assertEquals(45.0, sample.mspt());
        assertEquals(0.9, sample.confidenceScore());
        assertEquals(42.0, sample.windowAverage());
        assertEquals(20.0, sample.baseline());
        assertEquals(25.0, sample.buildImpact(), 0.001);
        assertNotNull(sample.timestamp());
    }

    @Test
    void nowLegacy() {
        MsptSample sample = MsptSample.now(30.0, 0.8);
        assertEquals(30.0, sample.mspt());
        assertEquals(0.8, sample.confidenceScore());
        assertEquals(30.0, sample.windowAverage());
        assertEquals(30.0, sample.baseline());
        assertEquals(0.0, sample.buildImpact());
    }

    @Test
    void nowWithDefaultConfidence() {
        MsptSample sample = MsptSample.now(20.0);
        assertEquals(20.0, sample.mspt());
        assertEquals(1.0, sample.confidenceScore());
    }

    @Test
    void shouldBackpressureAboveThreshold() {
        MsptSample high = MsptSample.now(50.0, 0.9);
        assertTrue(high.shouldBackpressure(40.0));
        assertTrue(high.shouldBackpressure()); // default threshold 40
    }

    @Test
    void shouldNotBackpressureBelowThreshold() {
        MsptSample low = MsptSample.now(30.0, 0.9);
        assertFalse(low.shouldBackpressure(40.0));
        assertFalse(low.shouldBackpressure());
    }

    @Test
    void shouldNotBackpressureLowConfidence() {
        MsptSample sample = MsptSample.now(50.0, 0.5);
        assertFalse(sample.shouldBackpressure(40.0));
    }

    @Test
    void backpressureExactThreshold() {
        // mspt must be > threshold (not >=)
        MsptSample exactly = MsptSample.now(40.0, 0.9);
        assertFalse(exactly.shouldBackpressure(40.0));

        MsptSample above = MsptSample.now(40.1, 0.9);
        assertTrue(above.shouldBackpressure(40.0));
    }

    @Test
    void confidenceThreshold() {
        // exactly 0.7 should pass
        MsptSample exact = MsptSample.now(50.0, 0.7);
        assertTrue(exact.shouldBackpressure(40.0));

        // just below 0.7 should fail
        MsptSample below = MsptSample.now(50.0, 0.699);
        assertFalse(below.shouldBackpressure(40.0));
    }

    @Test
    void freshness() {
        MsptSample sample = MsptSample.now(20.0);
        assertTrue(sample.isFresh(10_000));
        assertTrue(sample.getAgeMs() < 1000);
    }
}
