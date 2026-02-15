package com.devmod.testing.stats;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class OverlayUsageTrackerTest {

    private OverlayUsageTracker tracker;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<OverlayUsageTracker> ctor = OverlayUsageTracker.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        tracker = ctor.newInstance();
    }

    @Test
    @DisplayName("recordOverlayToggle increments counts")
    void recordOverlayToggle() {
        tracker.recordOverlayToggle("impact_hud");
        tracker.recordOverlayToggle("impact_hud");
        assertEquals(2, tracker.getOverlayToggles("impact_hud"));
    }

    @Test
    @DisplayName("hasUsedAllOverlays returns true when all toggled")
    void hasUsedAllOverlays() {
        assertFalse(tracker.hasUsedAllOverlays());

        tracker.recordOverlayToggle("impact_hud");
        tracker.recordOverlayToggle("debug_renderer");
        tracker.recordOverlayToggle("pathfinding");
        tracker.recordOverlayToggle("light_level");
        tracker.recordOverlayToggle("room_bounds");
        tracker.recordOverlayToggle("line_of_sight");
        assertFalse(tracker.hasUsedAllOverlays());

        assertTrue(tracker.recordOverlayToggle("safe_spots"));
        assertTrue(tracker.hasUsedAllOverlays());
    }

    @Test
    @DisplayName("recordScreenOpen tracks screen opens")
    void recordScreenOpen() {
        tracker.recordScreenOpen("telemetry");
        tracker.recordScreenOpen("telemetry");
        tracker.recordScreenOpen("mob_config");

        assertEquals(2, tracker.getScreenOpens("telemetry"));
        assertEquals(1, tracker.getScreenOpens("mob_config"));
        assertEquals(0, tracker.getScreenOpens("unknown"));
    }

    @Test
    @DisplayName("getTotalOverlayToggles sums all")
    void getTotalOverlayToggles() {
        tracker.recordOverlayToggle("impact_hud");
        tracker.recordOverlayToggle("debug_renderer");
        tracker.recordOverlayToggle("pathfinding");
        assertEquals(3, tracker.getTotalOverlayToggles());
    }

    @Test
    @DisplayName("unknown overlay name returns 0")
    void unknownOverlay() {
        assertEquals(0, tracker.getOverlayToggles("nonexistent"));
    }

    @Test
    @DisplayName("toJson/fromJson round-trip")
    void jsonRoundTrip() {
        tracker.recordOverlayToggle("impact_hud");
        tracker.recordOverlayToggle("debug_renderer");
        tracker.recordScreenOpen("telemetry");

        JsonObject json = tracker.toJson();

        OverlayUsageTracker restored;
        try {
            Constructor<OverlayUsageTracker> ctor = OverlayUsageTracker.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            restored = ctor.newInstance();
        } catch (Exception e) {
            fail("Cannot create instance");
            return;
        }
        restored.fromJson(json);

        assertEquals(1, restored.getOverlayToggles("impact_hud"));
        assertEquals(1, restored.getOverlayToggles("debug_renderer"));
        assertEquals(1, restored.getScreenOpens("telemetry"));
    }

    @Test
    @DisplayName("reset clears everything")
    void reset() {
        tracker.recordOverlayToggle("impact_hud");
        tracker.recordScreenOpen("telemetry");
        tracker.reset();

        assertEquals(0, tracker.getOverlayToggles("impact_hud"));
        assertEquals(0, tracker.getScreenOpens("telemetry"));
        assertEquals(0, tracker.getTotalOverlayToggles());
    }
}
