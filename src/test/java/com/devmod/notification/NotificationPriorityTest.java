package com.devmod.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationPriorityTest {

    @Nested
    @DisplayName("Priority Levels")
    class PriorityLevels {

        @Test
        @DisplayName("Priorities have ascending levels 0-4")
        void prioritiesHaveAscendingLevels() {
            assertEquals(0, NotificationPriority.LOW.getLevel());
            assertEquals(1, NotificationPriority.NORMAL.getLevel());
            assertEquals(2, NotificationPriority.HIGH.getLevel());
            assertEquals(3, NotificationPriority.URGENT.getLevel());
            assertEquals(4, NotificationPriority.CRITICAL.getLevel());
        }

        @Test
        @DisplayName("Levels are strictly ascending")
        void levelsAreStrictlyAscending() {
            NotificationPriority[] values = NotificationPriority.values();
            for (int i = 1; i < values.length; i++) {
                assertTrue(values[i].getLevel() > values[i - 1].getLevel(),
                    values[i].name() + " level should be > " + values[i - 1].name());
            }
        }

        @Test
        @DisplayName("Expected priority count")
        void expectedPriorityCount() {
            assertEquals(5, NotificationPriority.values().length);
        }
    }

    @Nested
    @DisplayName("Display Duration")
    class DisplayDuration {

        @Test
        @DisplayName("Durations increase with priority")
        void durationsIncreaseWithPriority() {
            NotificationPriority[] values = NotificationPriority.values();
            for (int i = 1; i < values.length; i++) {
                assertTrue(values[i].getDefaultDurationMs() > values[i - 1].getDefaultDurationMs(),
                    values[i].name() + " duration should be > " + values[i - 1].name());
            }
        }

        @Test
        @DisplayName("LOW has shortest duration")
        void lowHasShortestDuration() {
            assertEquals(1000, NotificationPriority.LOW.getDefaultDurationMs());
        }

        @Test
        @DisplayName("CRITICAL has longest duration")
        void criticalHasLongestDuration() {
            assertEquals(8000, NotificationPriority.CRITICAL.getDefaultDurationMs());
        }
    }

    @Nested
    @DisplayName("Banner Display")
    class BannerDisplay {

        @Test
        @DisplayName("LOW and NORMAL do not use banner")
        void lowAndNormalDoNotUseBanner() {
            assertFalse(NotificationPriority.LOW.shouldUseBannerDisplay());
            assertFalse(NotificationPriority.NORMAL.shouldUseBannerDisplay());
        }

        @Test
        @DisplayName("HIGH, URGENT, CRITICAL use banner")
        void highUrgentCriticalUseBanner() {
            assertTrue(NotificationPriority.HIGH.shouldUseBannerDisplay());
            assertTrue(NotificationPriority.URGENT.shouldUseBannerDisplay());
            assertTrue(NotificationPriority.CRITICAL.shouldUseBannerDisplay());
        }
    }

    @Nested
    @DisplayName("isAtLeast comparison")
    class IsAtLeastComparison {

        @Test
        @DisplayName("CRITICAL is at least everything")
        void criticalIsAtLeastEverything() {
            for (NotificationPriority p : NotificationPriority.values()) {
                assertTrue(NotificationPriority.CRITICAL.isAtLeast(p));
            }
        }

        @Test
        @DisplayName("LOW is at least only LOW")
        void lowIsAtLeastOnlyLow() {
            assertTrue(NotificationPriority.LOW.isAtLeast(NotificationPriority.LOW));
            assertFalse(NotificationPriority.LOW.isAtLeast(NotificationPriority.NORMAL));
            assertFalse(NotificationPriority.LOW.isAtLeast(NotificationPriority.HIGH));
        }

        @Test
        @DisplayName("Same priority isAtLeast returns true")
        void samePriorityIsAtLeast() {
            for (NotificationPriority p : NotificationPriority.values()) {
                assertTrue(p.isAtLeast(p));
            }
        }

        @Test
        @DisplayName("isAtLeast is consistent with level ordering")
        void isAtLeastConsistentWithLevelOrdering() {
            NotificationPriority[] values = NotificationPriority.values();
            for (int i = 0; i < values.length; i++) {
                for (int j = 0; j < values.length; j++) {
                    assertEquals(values[i].getLevel() >= values[j].getLevel(),
                        values[i].isAtLeast(values[j]),
                        values[i].name() + ".isAtLeast(" + values[j].name() + ")");
                }
            }
        }
    }

    @Nested
    @DisplayName("fromOrdinal lookup")
    class FromOrdinalLookup {

        @Test
        @DisplayName("Valid ordinals return correct priority")
        void validOrdinalsReturnCorrectPriority() {
            for (NotificationPriority p : NotificationPriority.values()) {
                assertEquals(p, NotificationPriority.fromOrdinal(p.ordinal()));
            }
        }

        @Test
        @DisplayName("Negative ordinal returns NORMAL")
        void negativeOrdinalReturnsNormal() {
            assertEquals(NotificationPriority.NORMAL, NotificationPriority.fromOrdinal(-1));
        }

        @Test
        @DisplayName("Ordinal out of bounds returns NORMAL")
        void ordinalOutOfBoundsReturnsNormal() {
            assertEquals(NotificationPriority.NORMAL, NotificationPriority.fromOrdinal(999));
        }
    }

    @Nested
    @DisplayName("fromLevel lookup")
    class FromLevelLookup {

        @Test
        @DisplayName("Valid levels return correct priority")
        void validLevelsReturnCorrectPriority() {
            assertEquals(NotificationPriority.LOW, NotificationPriority.fromLevel(0));
            assertEquals(NotificationPriority.NORMAL, NotificationPriority.fromLevel(1));
            assertEquals(NotificationPriority.HIGH, NotificationPriority.fromLevel(2));
            assertEquals(NotificationPriority.URGENT, NotificationPriority.fromLevel(3));
            assertEquals(NotificationPriority.CRITICAL, NotificationPriority.fromLevel(4));
        }

        @Test
        @DisplayName("Invalid level returns NORMAL")
        void invalidLevelReturnsNormal() {
            assertEquals(NotificationPriority.NORMAL, NotificationPriority.fromLevel(-1));
            assertEquals(NotificationPriority.NORMAL, NotificationPriority.fromLevel(5));
            assertEquals(NotificationPriority.NORMAL, NotificationPriority.fromLevel(100));
        }
    }
}
