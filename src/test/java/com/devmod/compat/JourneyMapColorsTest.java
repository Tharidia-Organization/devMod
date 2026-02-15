package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.compat.mods.journeymap.JourneyMapColors;

@DisplayName("JourneyMapColors")
class JourneyMapColorsTest {

    @Test
    @DisplayName("SPAWN color is a valid non-zero ARGB value")
    void spawnColorIsValidArgb() {
        assertNotEquals(0, JourneyMapColors.SPAWN);
    }

    @Test
    @DisplayName("OBJECTIVE color is a valid non-zero ARGB value")
    void objectiveColorIsValidArgb() {
        assertNotEquals(0, JourneyMapColors.OBJECTIVE);
    }

    @Test
    @DisplayName("EXIT color is a valid non-zero ARGB value")
    void exitColorIsValidArgb() {
        assertNotEquals(0, JourneyMapColors.EXIT);
    }

    @Test
    @DisplayName("all waypoint colors are distinct")
    void allColorsAreDistinct() {
        assertNotEquals(JourneyMapColors.SPAWN, JourneyMapColors.OBJECTIVE);
        assertNotEquals(JourneyMapColors.SPAWN, JourneyMapColors.EXIT);
        assertNotEquals(JourneyMapColors.OBJECTIVE, JourneyMapColors.EXIT);
    }
}
