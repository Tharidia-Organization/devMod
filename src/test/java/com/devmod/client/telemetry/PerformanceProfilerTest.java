package com.devmod.client.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PerformanceProfiler")
class PerformanceProfilerTest {

    @Test
    @DisplayName("toggle off resets state and disables profiler")
    void toggleOffResetsState() throws Exception {
        PerformanceProfiler profiler = PerformanceProfiler.INSTANCE;

        // Enable and seed some state
        profiler.setEnabled(true);
        profiler.setCounter("test", 7);

        Field timingsField = PerformanceProfiler.class.getDeclaredField("systemTimings");
        timingsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Long> timings = (Map<String, Long>) timingsField.get(profiler);
        timings.put("unit", 100L);

        profiler.toggle(); // should disable and reset

        assertFalse(profiler.isEnabled());
        assertTrue(timings.isEmpty(), "systemTimings should be cleared");

        Field countersField = PerformanceProfiler.class.getDeclaredField("activeCounters");
        countersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Integer> counters = (Map<String, Integer>) countersField.get(profiler);
        assertTrue(counters.isEmpty(), "activeCounters should be cleared");
    }

    @Test
    @DisplayName("setEnabled(false) resets state when disabling")
    void setEnabledFalseResetsState() throws Exception {
        PerformanceProfiler profiler = PerformanceProfiler.INSTANCE;

        profiler.setEnabled(true);
        profiler.setCounter("alpha", 1);

        Field countersField = PerformanceProfiler.class.getDeclaredField("activeCounters");
        countersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Integer> counters = (Map<String, Integer>) countersField.get(profiler);
        assertEquals(1, counters.size());

        profiler.setEnabled(false);

        assertFalse(profiler.isEnabled());
        assertTrue(counters.isEmpty(), "activeCounters should be cleared after disable");
    }
}
