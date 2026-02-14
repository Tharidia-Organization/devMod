package com.devmod.actions.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionResult;
import com.devmod.actions.legacy.ShadowModeComparator.ComparisonResult;
import com.devmod.actions.legacy.ShadowModeComparator.MismatchType;
import com.devmod.actions.legacy.ShadowModeComparator.ShadowModeReport;
import com.devmod.actions.legacy.ShadowModeComparator.ActionStatsSummary;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ShadowModeComparator: shadow mode comparison engine.
 * These tests do not require Minecraft runtime.
 */
@DisplayName("ShadowModeComparator")
class ShadowModeComparatorTest {

    @BeforeEach
    void reset() {
        ShadowModeComparator.resetCounters();
    }

    // =========================================================================
    // Matching comparisons
    // =========================================================================

    @Nested
    @DisplayName("Matching Results")
    class MatchingResults {

        @Test
        @DisplayName("Identical OK results are a match")
        void identicalOkResultsMatch() {
            ActionResult v1 = ActionResult.ok(10);
            ActionResult v2 = ActionResult.ok(12);

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.action", v1, v2);

            assertTrue(result.matches());
            assertNull(result.mismatchType());
            assertNull(result.detail());
            assertEquals(10, result.v1DurationMs());
            assertEquals(12, result.v2DurationMs());
        }

        @Test
        @DisplayName("Identical BLOCKED results with same error code match")
        void identicalBlockedResultsMatch() {
            ActionResult v1 = ActionResult.blocked(ActionResult.ERROR_PRECONDITION_FAILED, "msg1");
            ActionResult v2 = ActionResult.blocked(ActionResult.ERROR_PRECONDITION_FAILED, "msg2");

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.blocked", v1, v2);

            assertTrue(result.matches());
            assertNull(result.mismatchType());
        }

        @Test
        @DisplayName("Identical FAILED results match")
        void identicalFailedResultsMatch() {
            ActionResult v1 = ActionResult.failed(ActionResult.ERROR_EXCEPTION, "err", 5);
            ActionResult v2 = ActionResult.failed(ActionResult.ERROR_EXCEPTION, "err", 8);

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.failed", v1, v2);

            assertTrue(result.matches());
        }

        @Test
        @DisplayName("Match increments totalMatches counter")
        void matchIncrementsCounters() {
            ActionResult v1 = ActionResult.ok(10);
            ActionResult v2 = ActionResult.ok(12);

            ShadowModeComparator.compare("devmod.test.action", v1, v2);

            assertEquals(1, ShadowModeComparator.getTotalComparisons());
            assertEquals(1, ShadowModeComparator.getTotalMatches());
            assertEquals(0, ShadowModeComparator.getTotalMismatches());
        }
    }

    // =========================================================================
    // Status mismatches
    // =========================================================================

    @Nested
    @DisplayName("Status Mismatches")
    class StatusMismatches {

        @Test
        @DisplayName("OK vs BLOCKED is a STATUS mismatch")
        void okVsBlocked() {
            ActionResult v1 = ActionResult.ok(10);
            ActionResult v2 = ActionResult.blocked(ActionResult.ERROR_PRECONDITION_FAILED, "blocked");

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.mismatch", v1, v2);

            assertFalse(result.matches());
            assertEquals(MismatchType.STATUS, result.mismatchType());
            assertNotNull(result.detail());
            assertTrue(result.detail().contains("OK"));
            assertTrue(result.detail().contains("BLOCKED"));
        }

        @Test
        @DisplayName("OK vs FAILED is a V2_EXCEPTION mismatch")
        void okVsFailed() {
            ActionResult v1 = ActionResult.ok(10);
            ActionResult v2 = ActionResult.failed(ActionResult.ERROR_EXCEPTION, "crash", 5);

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.exception", v1, v2);

            assertFalse(result.matches());
            assertEquals(MismatchType.V2_EXCEPTION, result.mismatchType());
        }

        @Test
        @DisplayName("BLOCKED vs OK is a STATUS mismatch (not V2_EXCEPTION)")
        void blockedVsOk() {
            ActionResult v1 = ActionResult.blocked(ActionResult.ERROR_PRECONDITION_FAILED, "blocked");
            ActionResult v2 = ActionResult.ok(10);

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.reverse", v1, v2);

            assertFalse(result.matches());
            assertEquals(MismatchType.STATUS, result.mismatchType());
        }

        @Test
        @DisplayName("Status mismatch increments totalMismatches counter")
        void statusMismatchIncrementsCounters() {
            ActionResult v1 = ActionResult.ok(10);
            ActionResult v2 = ActionResult.blocked(ActionResult.ERROR_PRECONDITION_FAILED, "blocked");

            ShadowModeComparator.compare("devmod.test.mismatch", v1, v2);

            assertEquals(1, ShadowModeComparator.getTotalComparisons());
            assertEquals(0, ShadowModeComparator.getTotalMatches());
            assertEquals(1, ShadowModeComparator.getTotalMismatches());
        }
    }

    // =========================================================================
    // Error code mismatches
    // =========================================================================

    @Nested
    @DisplayName("Error Code Mismatches")
    class ErrorCodeMismatches {

        @Test
        @DisplayName("Different error codes on BLOCKED results is ERROR_CODE mismatch")
        void differentErrorCodes() {
            ActionResult v1 = ActionResult.blocked(ActionResult.ERROR_PRECONDITION_FAILED, "msg");
            ActionResult v2 = ActionResult.blocked(ActionResult.ERROR_PERMISSION_DENIED, "msg");

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.errcode", v1, v2);

            assertFalse(result.matches());
            assertEquals(MismatchType.ERROR_CODE, result.mismatchType());
            assertTrue(result.detail().contains("PRECONDITION_FAILED"));
            assertTrue(result.detail().contains("PERMISSION_DENIED"));
        }

        @Test
        @DisplayName("Same error codes on BLOCKED results match")
        void sameErrorCodes() {
            ActionResult v1 = ActionResult.blocked(ActionResult.ERROR_UNTRUSTED_ACTION, "msg1");
            ActionResult v2 = ActionResult.blocked(ActionResult.ERROR_UNTRUSTED_ACTION, "msg2");

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.sameerr", v1, v2);

            assertTrue(result.matches());
        }
    }

    // =========================================================================
    // Duration tolerance
    // =========================================================================

    @Nested
    @DisplayName("Duration Tolerance")
    class DurationTolerance {

        @Test
        @DisplayName("V2 within tolerance is a match")
        void withinTolerance() {
            // Default tolerance is 5.0x, so 10ms V1 -> up to 50ms V2 is ok
            ActionResult v1 = ActionResult.ok(10);
            ActionResult v2 = ActionResult.ok(45);

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.duration", v1, v2);

            assertTrue(result.matches());
        }

        @Test
        @DisplayName("V2 exceeding tolerance is a DURATION mismatch")
        void exceedsTolerance() {
            // 10ms V1, 60ms V2 -> 6x, exceeds default 5x tolerance
            ActionResult v1 = ActionResult.ok(10);
            ActionResult v2 = ActionResult.ok(60);

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.slow", v1, v2);

            assertFalse(result.matches());
            assertEquals(MismatchType.DURATION, result.mismatchType());
            assertNotNull(result.detail());
            assertTrue(result.detail().contains("slower"),
                "Detail should mention slower: " + result.detail());
        }

        @Test
        @DisplayName("Sub-threshold V1 duration skips tolerance check")
        void subThresholdSkipped() {
            // V1 duration < MIN_DURATION_FOR_TOLERANCE_MS (5ms) -> no duration check
            ActionResult v1 = ActionResult.ok(2);
            ActionResult v2 = ActionResult.ok(200);

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.tiny", v1, v2);

            assertTrue(result.matches());
        }

        @Test
        @DisplayName("Custom tolerance is respected")
        void customTolerance() {
            ShadowModeComparator.setDurationTolerance(2.0);

            // 10ms V1, 25ms V2 -> 2.5x, exceeds 2.0x custom tolerance
            ActionResult v1 = ActionResult.ok(10);
            ActionResult v2 = ActionResult.ok(25);

            ComparisonResult result = ShadowModeComparator.compare("devmod.test.custom", v1, v2);

            assertFalse(result.matches());
            assertEquals(MismatchType.DURATION, result.mismatchType());
        }

        @Test
        @DisplayName("Tolerance below 1.0 is rejected")
        void toleranceBelowOneFails() {
            assertThrows(IllegalArgumentException.class, () ->
                ShadowModeComparator.setDurationTolerance(0.5));
        }
    }

    // =========================================================================
    // Multiple comparisons and counters
    // =========================================================================

    @Nested
    @DisplayName("Accumulated Statistics")
    class AccumulatedStatistics {

        @Test
        @DisplayName("Multiple comparisons accumulate correctly")
        void multipleComparisons() {
            ShadowModeComparator.compare("devmod.test.a",
                ActionResult.ok(10), ActionResult.ok(12));
            ShadowModeComparator.compare("devmod.test.a",
                ActionResult.ok(10), ActionResult.ok(12));
            ShadowModeComparator.compare("devmod.test.b",
                ActionResult.ok(5),
                ActionResult.blocked(ActionResult.ERROR_PRECONDITION_FAILED, "blocked"));

            assertEquals(3, ShadowModeComparator.getTotalComparisons());
            assertEquals(2, ShadowModeComparator.getTotalMatches());
            assertEquals(1, ShadowModeComparator.getTotalMismatches());
        }

        @Test
        @DisplayName("Reset clears all counters")
        void resetClearsCounters() {
            ShadowModeComparator.compare("devmod.test.a",
                ActionResult.ok(10), ActionResult.ok(12));
            ShadowModeComparator.compare("devmod.test.b",
                ActionResult.ok(5),
                ActionResult.blocked(ActionResult.ERROR_PRECONDITION_FAILED, "blocked"));

            ShadowModeComparator.resetCounters();

            assertEquals(0, ShadowModeComparator.getTotalComparisons());
            assertEquals(0, ShadowModeComparator.getTotalMatches());
            assertEquals(0, ShadowModeComparator.getTotalMismatches());
            assertEquals(ShadowModeComparator.DEFAULT_DURATION_TOLERANCE,
                ShadowModeComparator.getDurationTolerance());
        }
    }

    // =========================================================================
    // Report generation
    // =========================================================================

    @Nested
    @DisplayName("Shadow Mode Report")
    class ReportGeneration {

        @Test
        @DisplayName("Empty report has 100% match rate")
        void emptyReport() {
            ShadowModeReport report = ShadowModeComparator.generateReport();

            assertEquals(0, report.totalComparisons());
            assertEquals(0, report.totalMatches());
            assertEquals(0, report.totalMismatches());
            assertEquals(100.0, report.matchRate(), 0.001);
            assertTrue(report.perActionSummaries().isEmpty());
            assertNotNull(report.generatedAt());
        }

        @Test
        @DisplayName("Report with mixed results has correct match rate")
        void mixedReport() {
            // 2 matches + 1 mismatch = 66.67% match rate
            ShadowModeComparator.compare("devmod.test.a",
                ActionResult.ok(10), ActionResult.ok(12));
            ShadowModeComparator.compare("devmod.test.a",
                ActionResult.ok(20), ActionResult.ok(22));
            ShadowModeComparator.compare("devmod.test.b",
                ActionResult.ok(5),
                ActionResult.blocked(ActionResult.ERROR_PRECONDITION_FAILED, "blocked"));

            ShadowModeReport report = ShadowModeComparator.generateReport();

            assertEquals(3, report.totalComparisons());
            assertEquals(2, report.totalMatches());
            assertEquals(1, report.totalMismatches());
            assertEquals(66.666, report.matchRate(), 0.01);
        }

        @Test
        @DisplayName("Report per-action summaries are sorted by action ID")
        void summariesSorted() {
            ShadowModeComparator.compare("devmod.test.zebra",
                ActionResult.ok(10), ActionResult.ok(12));
            ShadowModeComparator.compare("devmod.test.alpha",
                ActionResult.ok(10), ActionResult.ok(12));

            ShadowModeReport report = ShadowModeComparator.generateReport();

            assertEquals(2, report.perActionSummaries().size());
            assertEquals("devmod.test.alpha", report.perActionSummaries().get(0).actionId());
            assertEquals("devmod.test.zebra", report.perActionSummaries().get(1).actionId());
        }

        @Test
        @DisplayName("Per-action summary has correct average durations")
        void perActionAverageDurations() {
            ShadowModeComparator.compare("devmod.test.a",
                ActionResult.ok(10), ActionResult.ok(20));
            ShadowModeComparator.compare("devmod.test.a",
                ActionResult.ok(30), ActionResult.ok(40));

            ShadowModeReport report = ShadowModeComparator.generateReport();
            ActionStatsSummary summary = report.perActionSummaries().get(0);

            assertEquals("devmod.test.a", summary.actionId());
            assertEquals(2, summary.comparisons());
            assertEquals(2, summary.matches());
            assertEquals(0, summary.mismatches());
            assertEquals(20.0, summary.avgV1DurationMs(), 0.001);
            assertEquals(30.0, summary.avgV2DurationMs(), 0.001);
        }
    }

    // =========================================================================
    // ComparisonResult record
    // =========================================================================

    @Nested
    @DisplayName("ComparisonResult")
    class ComparisonResultTests {

        @Test
        @DisplayName("Match result has correct fields")
        void matchResult() {
            ComparisonResult result = ComparisonResult.match("devmod.test.a", 10, 12);

            assertEquals("devmod.test.a", result.actionId());
            assertTrue(result.matches());
            assertNull(result.mismatchType());
            assertNull(result.detail());
            assertEquals(10, result.v1DurationMs());
            assertEquals(12, result.v2DurationMs());
            assertNotNull(result.timestamp());
        }

        @Test
        @DisplayName("Mismatch result has correct fields")
        void mismatchResult() {
            ComparisonResult result = ComparisonResult.mismatch(
                "devmod.test.b", MismatchType.STATUS, "V1=OK, V2=BLOCKED", 10, 12);

            assertEquals("devmod.test.b", result.actionId());
            assertFalse(result.matches());
            assertEquals(MismatchType.STATUS, result.mismatchType());
            assertEquals("V1=OK, V2=BLOCKED", result.detail());
        }
    }

    // =========================================================================
    // Null safety
    // =========================================================================

    @Nested
    @DisplayName("Null Safety")
    class NullSafety {

        @Test
        @DisplayName("Null actionId throws NPE")
        void nullActionId() {
            assertThrows(NullPointerException.class, () ->
                ShadowModeComparator.compare(null, ActionResult.ok(10), ActionResult.ok(10)));
        }

        @Test
        @DisplayName("Null v1Result throws NPE")
        void nullV1Result() {
            assertThrows(NullPointerException.class, () ->
                ShadowModeComparator.compare("devmod.test.a", null, ActionResult.ok(10)));
        }

        @Test
        @DisplayName("Null v2Result throws NPE")
        void nullV2Result() {
            assertThrows(NullPointerException.class, () ->
                ShadowModeComparator.compare("devmod.test.a", ActionResult.ok(10), null));
        }
    }
}
