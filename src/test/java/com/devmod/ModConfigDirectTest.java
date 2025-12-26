package com.devmod;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModConfigDirectTest {

    private final int originalColor = ModConfig.followRangeColor;

    @AfterEach
    void restoreColor() {
        ModConfig.followRangeColor = originalColor;
    }

    @Test
    @DisplayName("cycleColor rotates through the configured palette")
    void cycleColorRotatesThroughPalette() {
        ModConfig.followRangeColor = 0xFFFF0000;
        assertEquals("Red", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(0xFFFFFF00, ModConfig.followRangeColor);
        assertEquals("Yellow", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(0xFF00FF00, ModConfig.followRangeColor);
        assertEquals("Green", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(0xFF00FFFF, ModConfig.followRangeColor);
        assertEquals("Cyan", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(0xFF0000FF, ModConfig.followRangeColor);
        assertEquals("Blue", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(0xFFFF0000, ModConfig.followRangeColor);
        assertEquals("Red", ModConfig.getColorName());
    }
}
