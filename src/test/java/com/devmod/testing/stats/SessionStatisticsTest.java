package com.devmod.testing.stats;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class SessionStatisticsTest {

    private SessionStatistics stats;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<SessionStatistics> ctor = SessionStatistics.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        stats = ctor.newInstance();
    }

    @Test
    @DisplayName("recordSessionStart increments session count")
    void recordSessionStart() {
        stats.recordSessionStart();
        stats.recordSessionStart();
        assertEquals(2, stats.getSessionsStarted());
    }

    @Test
    @DisplayName("recordTestCompleted separates manual from auto")
    void recordTestCompleted() {
        stats.recordTestCompleted(false);
        stats.recordTestCompleted(false);
        stats.recordTestCompleted(true);

        assertEquals(3, stats.getTestsCompleted());
        assertEquals(2, stats.getTestsCompletedManually());
        assertEquals(1, stats.getTestsAutoCompleted());
    }

    @Test
    @DisplayName("updatePlayTime returns false if no session started")
    void updatePlayTimeNoSession() {
        assertFalse(stats.updatePlayTime());
        assertEquals(0, stats.getPlayTimeMs());
    }

    @Test
    @DisplayName("updatePlayTime tracks time after session start")
    void updatePlayTimeAfterSession() throws InterruptedException {
        stats.recordSessionStart();
        Thread.sleep(50);
        assertTrue(stats.updatePlayTime());
        assertTrue(stats.getPlayTimeMs() >= 40); // some tolerance
    }

    @Test
    @DisplayName("toJson/fromJson round-trip")
    void jsonRoundTrip() {
        stats.recordSessionStart();
        stats.recordTestCompleted(false);
        stats.recordTestCompleted(true);

        JsonObject json = stats.toJson();

        SessionStatistics restored;
        try {
            Constructor<SessionStatistics> ctor = SessionStatistics.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            restored = ctor.newInstance();
        } catch (Exception e) {
            fail("Cannot create instance");
            return;
        }
        restored.fromJson(json);

        assertEquals(1, restored.getSessionsStarted());
        assertEquals(1, restored.getTestsCompletedManually());
        assertEquals(1, restored.getTestsAutoCompleted());
    }

    @Test
    @DisplayName("reset clears all counters")
    void reset() {
        stats.recordSessionStart();
        stats.recordTestCompleted(true);
        stats.reset();

        assertEquals(0, stats.getSessionsStarted());
        assertEquals(0, stats.getTestsCompleted());
        assertEquals(0, stats.getPlayTimeMs());
    }
}
