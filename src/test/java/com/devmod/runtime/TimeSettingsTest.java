package com.devmod.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.runtime.environment.TimeController;

import static org.junit.jupiter.api.Assertions.*;

class TimeSettingsTest {

    @Test
    @DisplayName("TimeSettings stores fixedTime and frozen state")
    void storesFields() {
        TimeController.TimeSettings settings = new TimeController.TimeSettings(6000, true, null);
        assertEquals(6000, settings.fixedTime());
        assertTrue(settings.frozen());
        assertNull(settings.config());
    }

    @Test
    @DisplayName("getTimeString returns formatted time when no config")
    void getTimeStringFormattedWhenNoConfig() {
        // Time 6000 = 12:00 (noon)
        TimeController.TimeSettings noon = new TimeController.TimeSettings(6000, true, null);
        assertEquals("12:00", noon.getTimeString());
    }

    @Test
    @DisplayName("getTimeString formats midnight correctly")
    void getTimeStringMidnight() {
        // Time 18000 = 00:00 (midnight): (18000/1000 + 6) % 24 = 0
        TimeController.TimeSettings midnight = new TimeController.TimeSettings(18000, true, null);
        assertEquals("00:00", midnight.getTimeString());
    }

    @Test
    @DisplayName("getTimeString formats 6 AM correctly")
    void getTimeStringSixAM() {
        // Time 0 = 06:00 AM: (0/1000 + 6) % 24 = 6
        TimeController.TimeSettings dawn = new TimeController.TimeSettings(0, true, null);
        assertEquals("06:00", dawn.getTimeString());
    }

    @Test
    @DisplayName("getTimeString formats 6 PM correctly")
    void getTimeStringSixPM() {
        // Time 12000 = 18:00: (12000/1000 + 6) % 24 = 18
        TimeController.TimeSettings dusk = new TimeController.TimeSettings(12000, true, null);
        assertEquals("18:00", dusk.getTimeString());
    }
}
