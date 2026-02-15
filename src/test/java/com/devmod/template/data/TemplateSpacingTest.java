package com.devmod.template.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateSpacingTest {

    @Test
    void constantsHaveExpectedValues() {
        assertEquals(3, TemplateSpacing.PAD_SIZE);
        assertEquals(3, TemplateSpacing.CONSOLE_WIDTH);
        assertEquals(2, TemplateSpacing.CONSOLE_DEPTH);
        assertEquals(2, TemplateSpacing.RACK_WIDTH);
        assertEquals(3, TemplateSpacing.PLINTH_SIZE);
        assertEquals(5, TemplateSpacing.POD_SIZE);
        assertEquals(2, TemplateSpacing.BENCH_SPACING);
        assertEquals(2, TemplateSpacing.WALL_MARGIN);
        assertEquals(5, TemplateSpacing.CENTER_CLEAR);
        assertEquals(2, TemplateSpacing.COMPONENT_GAP);
        assertEquals(4, TemplateSpacing.STANDARD_HEIGHT);
        assertEquals(6, TemplateSpacing.TALL_HEIGHT);
        assertEquals(3, TemplateSpacing.SCREEN_HEIGHT);
        assertEquals(4, TemplateSpacing.PILLAR_HEIGHT);
        assertEquals(20, TemplateSpacing.SMALL_ROOM);
        assertEquals(40, TemplateSpacing.MEDIUM_ROOM);
        assertEquals(60, TemplateSpacing.LARGE_ROOM);
    }

    @Test
    void getScaleFactorForSmallRoom() {
        assertEquals(0.5f, TemplateSpacing.getScaleFactor(10));
        assertEquals(0.5f, TemplateSpacing.getScaleFactor(19));
    }

    @Test
    void getScaleFactorForMediumRoom() {
        assertEquals(1.0f, TemplateSpacing.getScaleFactor(20));
        assertEquals(1.0f, TemplateSpacing.getScaleFactor(39));
    }

    @Test
    void getScaleFactorForLargeRoom() {
        assertEquals(1.5f, TemplateSpacing.getScaleFactor(40));
        assertEquals(1.5f, TemplateSpacing.getScaleFactor(59));
    }

    @Test
    void getScaleFactorForExtraLargeRoom() {
        assertEquals(2.0f, TemplateSpacing.getScaleFactor(60));
        assertEquals(2.0f, TemplateSpacing.getScaleFactor(100));
    }

    @Test
    void scaleCountAtSmallScale() {
        // factor 0.5, 4 * 0.5 = 2
        assertEquals(2, TemplateSpacing.scaleCount(4, 10));
    }

    @Test
    void scaleCountAtMediumScale() {
        // factor 1.0, 4 * 1.0 = 4
        assertEquals(4, TemplateSpacing.scaleCount(4, 25));
    }

    @Test
    void scaleCountAtLargeScale() {
        // factor 1.5, 4 * 1.5 = 6
        assertEquals(6, TemplateSpacing.scaleCount(4, 45));
    }

    @Test
    void scaleCountAtExtraLargeScale() {
        // factor 2.0, 4 * 2.0 = 8
        assertEquals(8, TemplateSpacing.scaleCount(4, 70));
    }

    @Test
    void scaleCountNeverReturnsZero() {
        // factor 0.5, 1 * 0.5 = 0.5 -> rounds to 1, then max(1,1) = 1
        assertEquals(1, TemplateSpacing.scaleCount(1, 10));
    }

    @Test
    void fitsWithMarginReturnsTrueWhenInBounds() {
        // Room: 0 to 20, margin=2 => usable area 2 to 18
        // Component at (10, 10), size (4, 4) => (8..12, 8..12) -- fits
        assertTrue(TemplateSpacing.fitsWithMargin(10, 10, 4, 4, 0, 0, 20, 20));
    }

    @Test
    void fitsWithMarginReturnsFalseWhenTooCloseToWall() {
        // Component at (2, 10) with size 4 => left edge = 2 - 2 = 0, min wall = 0 + 2 = 2
        // 0 >= 2 is false
        assertFalse(TemplateSpacing.fitsWithMargin(2, 10, 4, 4, 0, 0, 20, 20));
    }

    @Test
    void fitsWithMarginReturnsFalseWhenTooCloseToMaxWall() {
        // Component at (18, 10) with size 4 => right edge = 18 + 2 = 20, max wall = 20 - 2 = 18
        // 20 <= 18 is false
        assertFalse(TemplateSpacing.fitsWithMargin(18, 10, 4, 4, 0, 0, 20, 20));
    }

    @Test
    void fitsWithMarginEdgeCase() {
        // Component at (5, 5) with size 2 => (4..6, 4..6)
        // Room 0..10, margin 2 => usable 2..8
        // 4 >= 2 && 6 <= 8 && 4 >= 2 && 6 <= 8 => true
        assertTrue(TemplateSpacing.fitsWithMargin(5, 5, 2, 2, 0, 0, 10, 10));
    }
}
