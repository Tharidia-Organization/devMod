package com.devmod;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModConfigDirectTest {

    private final int originalColor = ModConfig.getFollowRangeColor();

    @AfterEach
    void restoreColor() {
        ModConfig.setFollowRangeColor(originalColor);
    }

    @Test
    @DisplayName("cycleColor rotates through the configured palette")
    void cycleColorRotatesThroughPalette() {
        ModConfig.setFollowRangeColor(0xFFFF0000);
        assertEquals("Red", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(0xFFFFFF00, ModConfig.getFollowRangeColor());
        assertEquals("Yellow", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(0xFF00FF00, ModConfig.getFollowRangeColor());
        assertEquals("Green", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(0xFF00FFFF, ModConfig.getFollowRangeColor());
        assertEquals("Cyan", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(0xFF0000FF, ModConfig.getFollowRangeColor());
        assertEquals("Blue", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(0xFFFF0000, ModConfig.getFollowRangeColor());
        assertEquals("Red", ModConfig.getColorName());
    }
}
