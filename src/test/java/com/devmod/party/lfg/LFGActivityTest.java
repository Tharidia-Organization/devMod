package com.devmod.party.lfg;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LFGActivityTest {

    @Test
    void enumHasSevenValues() {
        assertEquals(7, LFGActivity.values().length);
    }

    @Test
    void fromOrdinal_returnsCorrectActivity() {
        LFGActivity[] values = LFGActivity.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], LFGActivity.fromOrdinal(i));
        }
    }

    @Test
    void fromOrdinal_returnsCustomForNegative() {
        assertEquals(LFGActivity.CUSTOM, LFGActivity.fromOrdinal(-1));
    }

    @Test
    void fromOrdinal_returnsCustomForOutOfBounds() {
        assertEquals(LFGActivity.CUSTOM, LFGActivity.fromOrdinal(100));
    }

    @Test
    void displayNames_areNonEmpty() {
        for (LFGActivity activity : LFGActivity.values()) {
            assertNotNull(activity.getDisplayName());
            assertFalse(activity.getDisplayName().isEmpty());
        }
    }

    @Test
    void colors_haveFullAlpha() {
        for (LFGActivity activity : LFGActivity.values()) {
            int alpha = (activity.getColor() >> 24) & 0xFF;
            assertEquals(0xFF, alpha, activity + " color should have full alpha");
        }
    }

    @Test
    void arenaEasy_hasGreenColor() {
        // 0xFF55FF55 -> green
        int green = (LFGActivity.ARENA_EASY.getColor() >> 8) & 0xFF;
        assertTrue(green >= 0xCC, "Arena Easy should have high green component");
    }

    @Test
    void arenaHard_hasRedColor() {
        // 0xFFFF5555 -> red
        int red = (LFGActivity.ARENA_HARD.getColor() >> 16) & 0xFF;
        assertTrue(red >= 0xCC, "Arena Hard should have high red component");
    }

    @ParameterizedTest
    @ValueSource(ints = {-100, -1, 7, 8, 50, Integer.MAX_VALUE})
    void fromOrdinal_outOfRange_returnsCustom(int ordinal) {
        assertEquals(LFGActivity.CUSTOM, LFGActivity.fromOrdinal(ordinal));
    }
}
