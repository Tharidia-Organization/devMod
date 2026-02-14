package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for CentrifugeBlockEntity progress calculation logic.
 * These formulas are extracted and tested without instantiating the block entity,
 * since the actual class depends on Minecraft world/registry infrastructure.
 */
class CentrifugeProgressTest {

    /**
     * Mirrors CentrifugeBlockEntity.getProgressPercent()
     */
    static int getProgressPercent(int progress, int maxProgress) {
        return maxProgress > 0 ? (progress * 100) / maxProgress : 0;
    }

    /**
     * Mirrors CentrifugeBlockEntity.getProgressFloat()
     */
    static float getProgressFloat(int progress, int maxProgress) {
        return maxProgress > 0 ? (float) progress / maxProgress : 0f;
    }

    @Nested
    @DisplayName("getProgressPercent")
    class ProgressPercentTests {

        @Test
        @DisplayName("Zero progress returns 0%")
        void zeroProgress() {
            assertEquals(0, getProgressPercent(0, 100));
        }

        @Test
        @DisplayName("Full progress returns 100%")
        void fullProgress() {
            assertEquals(100, getProgressPercent(100, 100));
        }

        @Test
        @DisplayName("Half progress returns 50%")
        void halfProgress() {
            assertEquals(50, getProgressPercent(50, 100));
        }

        @Test
        @DisplayName("Integer division truncates")
        void truncation() {
            assertEquals(33, getProgressPercent(1, 3));
        }

        @Test
        @DisplayName("Zero maxProgress returns 0")
        void zeroMax() {
            assertEquals(0, getProgressPercent(50, 0));
        }

        @Test
        @DisplayName("Negative maxProgress returns 0")
        void negativeMax() {
            assertEquals(0, getProgressPercent(50, -1));
        }

        @ParameterizedTest
        @CsvSource({
            "0, 100, 0",
            "25, 100, 25",
            "50, 100, 50",
            "75, 100, 75",
            "100, 100, 100",
            "1, 200, 0",
            "99, 200, 49",
            "199, 200, 99"
        })
        @DisplayName("Various progress values")
        void variousValues(int progress, int max, int expected) {
            assertEquals(expected, getProgressPercent(progress, max));
        }
    }

    @Nested
    @DisplayName("getProgressFloat")
    class ProgressFloatTests {

        @Test
        @DisplayName("Zero progress returns 0.0")
        void zeroProgress() {
            assertEquals(0.0f, getProgressFloat(0, 100), 0.001f);
        }

        @Test
        @DisplayName("Full progress returns 1.0")
        void fullProgress() {
            assertEquals(1.0f, getProgressFloat(100, 100), 0.001f);
        }

        @Test
        @DisplayName("Half progress returns 0.5")
        void halfProgress() {
            assertEquals(0.5f, getProgressFloat(50, 100), 0.001f);
        }

        @Test
        @DisplayName("Zero maxProgress returns 0.0")
        void zeroMax() {
            assertEquals(0.0f, getProgressFloat(50, 0), 0.001f);
        }

        @Test
        @DisplayName("Float division preserves precision")
        void precision() {
            assertEquals(0.333f, getProgressFloat(1, 3), 0.01f);
        }

        @Test
        @DisplayName("Default processing time: 50/100 = 0.5")
        void defaultTime() {
            int defaultTime = 100; // DEFAULT_PROCESS_TIME
            assertEquals(0.5f, getProgressFloat(50, defaultTime), 0.001f);
        }
    }

    @Nested
    @DisplayName("Consistency between percent and float")
    class ConsistencyTests {

        @Test
        @DisplayName("Float * 100 approximates percent")
        void floatMatchesPercent() {
            for (int progress = 0; progress <= 100; progress += 10) {
                int percent = getProgressPercent(progress, 100);
                float flt = getProgressFloat(progress, 100);
                assertEquals(percent, (int) (flt * 100), 1,
                    "Mismatch at progress=" + progress);
            }
        }
    }
}
