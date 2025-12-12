package com.frenkvs.devmod.ui.radial.input;

import com.frenkvs.devmod.ui.radial.config.RadialMenuConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RadialSearchHandler} fuzzy search scoring logic.
 *
 * <p>These tests focus on the scoring algorithms without requiring Minecraft runtime.
 * Tests that require RadialMenuItem/RadialCategory are in integration tests.</p>
 */
@DisplayName("RadialSearchHandler")
class RadialSearchHandlerTest {

    // ================================================================
    // FUZZY SCORE TESTS
    // ================================================================

    @Nested
    @DisplayName("Fuzzy Scoring (calculateFuzzyScore)")
    class FuzzyScoreTests {

        @Test
        @DisplayName("returns 0 for empty query")
        void emptyQueryReturnsZero() {
            int score = RadialSearchHandler.calculateFuzzyScore("", "test item");
            assertEquals(0, score);
        }

        @Test
        @DisplayName("returns 0 for no match")
        void noMatchReturnsZero() {
            int score = RadialSearchHandler.calculateFuzzyScore("xyz", "body parts");
            assertEquals(0, score);
        }

        @Test
        @DisplayName("returns positive for sequential character match")
        void sequentialMatchPositive() {
            // "bdp" matches "body parts" (b-o-d-y p-arts)
            int score = RadialSearchHandler.calculateFuzzyScore("bdp", "body parts");
            assertTrue(score > 0, "Should match sequential characters");
        }

        @Test
        @DisplayName("returns 0 for out-of-order characters")
        void outOfOrderReturnsZero() {
            // "ptb" should not match "body parts" (wrong order)
            int score = RadialSearchHandler.calculateFuzzyScore("ptb", "body parts");
            assertEquals(0, score);
        }

        @Test
        @DisplayName("shorter names score higher")
        void shorterNamesScoreHigher() {
            int shortScore = RadialSearchHandler.calculateFuzzyScore("db", "debug");
            int longScore = RadialSearchHandler.calculateFuzzyScore("db", "debug mode extra long name");

            assertTrue(shortScore > longScore,
                "Shorter name should score higher: " + shortScore + " > " + longScore);
        }

        @Test
        @DisplayName("exact character match scores highest")
        void exactMatchScoresHighest() {
            // "test" matching "test" should score highest
            int exactScore = RadialSearchHandler.calculateFuzzyScore("test", "test");
            int longerScore = RadialSearchHandler.calculateFuzzyScore("test", "testing");

            assertTrue(exactScore > longerScore);
        }

        @Test
        @DisplayName("single character matches")
        void singleCharacterMatches() {
            int score = RadialSearchHandler.calculateFuzzyScore("d", "debug");
            assertTrue(score > 0);
        }

        @Test
        @DisplayName("all characters must be present")
        void allCharactersMustBePresent() {
            // "abc" requires a, b, and c in that order
            int matchScore = RadialSearchHandler.calculateFuzzyScore("abc", "aXbXc");
            int noMatchScore = RadialSearchHandler.calculateFuzzyScore("abc", "aXbX");

            assertTrue(matchScore > 0);
            assertEquals(0, noMatchScore);
        }

        @Test
        @DisplayName("handles repeated characters")
        void handlesRepeatedCharacters() {
            // "ee" should match "debug" (only one 'e')
            int score = RadialSearchHandler.calculateFuzzyScore("ee", "test item");
            // Should find first 'e' in "test" and second 'e' in "item"
            assertTrue(score > 0);
        }

        @Test
        @DisplayName("handles unicode characters")
        void handlesUnicode() {
            int score = RadialSearchHandler.calculateFuzzyScore("dbg", "d\u00e9bug"); // débug
            assertTrue(score > 0);
        }
    }

    // ================================================================
    // SCORE CALCULATION INTEGRATION
    // ================================================================

    @Nested
    @DisplayName("Score Bounds")
    class ScoreBoundsTests {

        @Test
        @DisplayName("fuzzy score bounded by SEARCH_FUZZY_BASE_SCORE")
        void fuzzyScoreBounded() {
            int maxPossible = RadialMenuConstants.SEARCH_FUZZY_BASE_SCORE * 2;
            int score = RadialSearchHandler.calculateFuzzyScore("test", "test");

            assertTrue(score <= maxPossible,
                "Fuzzy score should be bounded: " + score + " <= " + maxPossible);
            assertTrue(score > 0);
        }

        @Test
        @DisplayName("constants are properly ordered")
        void constantsProperlyOrdered() {
            // PREFIX > SUBSTRING > DESCRIPTION > FUZZY
            assertTrue(RadialMenuConstants.SEARCH_PREFIX_SCORE >
                RadialMenuConstants.SEARCH_SUBSTRING_SCORE);
            assertTrue(RadialMenuConstants.SEARCH_SUBSTRING_SCORE >
                RadialMenuConstants.SEARCH_DESCRIPTION_SCORE);
            assertTrue(RadialMenuConstants.SEARCH_DESCRIPTION_SCORE >
                RadialMenuConstants.SEARCH_FUZZY_BASE_SCORE);
        }
    }

    // ================================================================
    // NULL HANDLING TESTS
    // ================================================================

    @Nested
    @DisplayName("Null Handling")
    class NullHandlingTests {

        @Test
        @DisplayName("calculateFuzzyScore throws on null query")
        void fuzzyScoreThrowsOnNullQuery() {
            assertThrows(NullPointerException.class,
                () -> RadialSearchHandler.calculateFuzzyScore(null, "test"));
        }

        @Test
        @DisplayName("calculateFuzzyScore throws on null name")
        void fuzzyScoreThrowsOnNullName() {
            assertThrows(NullPointerException.class,
                () -> RadialSearchHandler.calculateFuzzyScore("test", null));
        }
    }

    // ================================================================
    // EDGE CASE TESTS
    // ================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("very long query against short name")
        void longQueryShortName() {
            int score = RadialSearchHandler.calculateFuzzyScore(
                "abcdefghijklmnop", "ab");
            assertEquals(0, score, "Cannot match longer query against shorter name");
        }

        @Test
        @DisplayName("same length query and name")
        void sameLengthQueryAndName() {
            int score = RadialSearchHandler.calculateFuzzyScore("test", "test");
            assertTrue(score > 0);
            // Max score when lengths match
            assertEquals(RadialMenuConstants.SEARCH_FUZZY_BASE_SCORE * 2, score);
        }

        @Test
        @DisplayName("query with spaces")
        void queryWithSpaces() {
            int score = RadialSearchHandler.calculateFuzzyScore("b p", "body parts");
            assertTrue(score > 0, "Should match 'b', space, 'p' in sequence");
        }

        @Test
        @DisplayName("name with special characters")
        void nameWithSpecialChars() {
            int score = RadialSearchHandler.calculateFuzzyScore("test", "test-item_v2.0");
            assertTrue(score > 0);
        }

        @Test
        @DisplayName("empty name returns 0")
        void emptyNameReturnsZero() {
            int score = RadialSearchHandler.calculateFuzzyScore("test", "");
            assertEquals(0, score);
        }
    }

    // ================================================================
    // PERFORMANCE HINT TESTS
    // ================================================================

    @Nested
    @DisplayName("Performance")
    class PerformanceTests {

        @Test
        @DisplayName("fuzzy search completes quickly for typical inputs")
        void fuzzySearchPerformance() {
            long start = System.nanoTime();

            // Simulate searching 1000 items
            for (int i = 0; i < 1000; i++) {
                RadialSearchHandler.calculateFuzzyScore("test", "test item number " + i);
            }

            long elapsed = System.nanoTime() - start;
            long elapsedMs = elapsed / 1_000_000;

            assertTrue(elapsedMs < 100,
                "1000 fuzzy searches should complete in under 100ms, took: " + elapsedMs + "ms");
        }
    }
}
