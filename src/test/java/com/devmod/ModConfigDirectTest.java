package com.devmod;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.util.FollowRangeColors;

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
        ModConfig.setFollowRangeColor(FollowRangeColors.RED);
        assertEquals("Red", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(FollowRangeColors.YELLOW, ModConfig.getFollowRangeColor());
        assertEquals("Yellow", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(FollowRangeColors.GREEN, ModConfig.getFollowRangeColor());
        assertEquals("Green", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(FollowRangeColors.CYAN, ModConfig.getFollowRangeColor());
        assertEquals("Cyan", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(FollowRangeColors.BLUE, ModConfig.getFollowRangeColor());
        assertEquals("Blue", ModConfig.getColorName());

        ModConfig.cycleColor();
        assertEquals(FollowRangeColors.RED, ModConfig.getFollowRangeColor());
        assertEquals("Red", ModConfig.getColorName());
    }
}
