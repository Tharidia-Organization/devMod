package com.devmod.actions.legacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.legacy.KpiValidator.KpiInput;
import com.devmod.actions.legacy.KpiValidator.KpiReport;
import com.devmod.actions.legacy.KpiValidator.KpiResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KpiValidator.
 */
@DisplayName("KpiValidator")
class KpiValidatorTest {

    /**
     * Creates input where all KPIs pass.
     */
    private KpiInput goodInput() {
        return new KpiInput(
            2750,   // original client actions lines
            44,     // current (98% reduction)
            100,    // original outbound imports
            40,     // current (60% reduction)
            0,      // zero orphans
            0,      // zero crashes
            5.0,    // V1 P95 latency ms
            3.0     // V2 P95 latency ms (40% improvement)
        );
    }

    @Nested
    @DisplayName("All KPIs Pass")
    class AllPassTests {

        @Test
        @DisplayName("All KPIs pass with good data")
        void allKpiPassWithGoodData() {
            KpiReport report = KpiValidator.validate(goodInput());

            assertTrue(report.allPassed(), "All KPIs should pass. Report:\n" + report.toSummary());
            assertEquals(5, report.totalCount());
            assertEquals(5, report.passCount());
        }

        @Test
        @DisplayName("Each KPI result has pass=true")
        void eachKpiPassesIndividually() {
            KpiReport report = KpiValidator.validate(goodInput());

            for (KpiResult result : report.results()) {
                assertTrue(result.passed(),
                    "KPI '" + result.name() + "' should pass. " +
                    "Target: " + result.target() + ", Actual: " + result.actual());
            }
        }
    }

    @Nested
    @DisplayName("Individual KPI Failures")
    class IndividualFailureTests {

        @Test
        @DisplayName("Insufficient line reduction fails")
        void insufficientLineReduction() {
            KpiInput input = new KpiInput(
                2750, 2000, // only 27% reduction (< 70%)
                100, 40, 0, 0, 5.0, 3.0
            );

            KpiReport report = KpiValidator.validate(input);
            assertFalse(report.allPassed());

            KpiResult lineReduction = report.results().get(0);
            assertEquals("DevModClientActions Line Reduction", lineReduction.name());
            assertFalse(lineReduction.passed());
        }

        @Test
        @DisplayName("Insufficient coupling reduction fails")
        void insufficientCouplingReduction() {
            KpiInput input = new KpiInput(
                2750, 44,
                100, 80, // only 20% reduction (< 40%)
                0, 0, 5.0, 3.0
            );

            KpiReport report = KpiValidator.validate(input);
            assertFalse(report.allPassed());

            KpiResult coupling = report.results().get(1);
            assertEquals("Module Coupling Reduction", coupling.name());
            assertFalse(coupling.passed());
        }

        @Test
        @DisplayName("Non-zero orphan actions fails")
        void nonZeroOrphansFails() {
            KpiInput input = new KpiInput(
                2750, 44, 100, 40,
                3, // 3 orphan actions
                0, 5.0, 3.0
            );

            KpiReport report = KpiValidator.validate(input);
            assertFalse(report.allPassed());

            KpiResult orphans = report.results().get(2);
            assertEquals("Zero Orphan Actions", orphans.name());
            assertFalse(orphans.passed());
            assertEquals(3.0, orphans.actualValue(), 0.01);
        }

        @Test
        @DisplayName("Non-zero crash count fails")
        void nonZeroCrashesFails() {
            KpiInput input = new KpiInput(
                2750, 44, 100, 40, 0,
                2, // 2 crash regressions
                5.0, 3.0
            );

            KpiReport report = KpiValidator.validate(input);
            assertFalse(report.allPassed());

            KpiResult crashes = report.results().get(3);
            assertEquals("Zero Crash/Regressions", crashes.name());
            assertFalse(crashes.passed());
        }

        @Test
        @DisplayName("Insufficient latency improvement fails")
        void insufficientLatencyImprovement() {
            KpiInput input = new KpiInput(
                2750, 44, 100, 40, 0, 0,
                5.0, 4.5 // only 10% improvement (< 20%)
            );

            KpiReport report = KpiValidator.validate(input);
            assertFalse(report.allPassed());

            KpiResult latency = report.results().get(4);
            assertEquals("P95 Invoke Latency Improvement", latency.name());
            assertFalse(latency.passed());
        }

        @Test
        @DisplayName("V2 slower than V1 fails latency KPI")
        void v2SlowerThanV1Fails() {
            KpiInput input = new KpiInput(
                2750, 44, 100, 40, 0, 0,
                5.0, 8.0 // V2 is 60% slower
            );

            KpiReport report = KpiValidator.validate(input);

            KpiResult latency = report.results().get(4);
            assertFalse(latency.passed());
            assertTrue(latency.actualValue() < 0,
                "Negative improvement means V2 is slower");
        }
    }

    @Nested
    @DisplayName("Report Format")
    class ReportFormatTests {

        @Test
        @DisplayName("Report summary contains all KPI names")
        void reportContainsAllKpiNames() {
            KpiReport report = KpiValidator.validate(goodInput());
            String summary = report.toSummary();

            assertTrue(summary.contains("DevModClientActions Line Reduction"));
            assertTrue(summary.contains("Module Coupling Reduction"));
            assertTrue(summary.contains("Zero Orphan Actions"));
            assertTrue(summary.contains("Zero Crash/Regressions"));
            assertTrue(summary.contains("P95 Invoke Latency Improvement"));
        }

        @Test
        @DisplayName("Report shows PASS/FAIL for each KPI")
        void reportShowsPassFail() {
            KpiReport report = KpiValidator.validate(goodInput());
            String summary = report.toSummary();

            assertTrue(summary.contains("[PASS]"));
            assertTrue(summary.contains("ALL PASS"));
        }

        @Test
        @DisplayName("Failed report shows SOME FAILED")
        void failedReportShowsSomeFailed() {
            KpiInput badInput = new KpiInput(
                2750, 2000, 100, 80, 3, 1, 5.0, 8.0
            );
            KpiReport report = KpiValidator.validate(badInput);
            String summary = report.toSummary();

            assertTrue(summary.contains("[FAIL]"));
            assertTrue(summary.contains("SOME FAILED"));
        }

        @Test
        @DisplayName("Report has correct pass count")
        void reportHasCorrectPassCount() {
            // 4 KPIs fail, 1 passes (line reduction is fine)
            KpiInput mostlyBadInput = new KpiInput(
                2750, 44, // line reduction passes
                100, 80,  // coupling fails
                3,        // orphans fails
                1,        // crashes fails
                5.0, 8.0  // latency fails
            );
            KpiReport report = KpiValidator.validate(mostlyBadInput);

            assertEquals(1, report.passCount());
            assertEquals(5, report.totalCount());
            assertFalse(report.allPassed());
        }
    }

    @Nested
    @DisplayName("Code Reduction Calculation")
    class CodeReductionTests {

        @Test
        @DisplayName("98% reduction from 2750 to 44 lines")
        void calculatesCorrectReduction() {
            KpiReport report = KpiValidator.validate(goodInput());

            KpiResult lineReduction = report.results().get(0);
            double expected = ((2750.0 - 44.0) / 2750.0) * 100.0;
            assertEquals(expected, lineReduction.actualValue(), 0.1);
            assertTrue(lineReduction.passed());
        }

        @Test
        @DisplayName("Exact 70% reduction passes boundary")
        void exactBoundaryPasses() {
            // 70% reduction: 2750 -> 825
            KpiInput input = new KpiInput(
                2750, 825,
                100, 40, 0, 0, 5.0, 3.0
            );

            KpiReport report = KpiValidator.validate(input);
            KpiResult lineReduction = report.results().get(0);
            assertTrue(lineReduction.passed(),
                "Exactly 70% reduction should pass. Actual: " + lineReduction.actualValue());
        }

        @Test
        @DisplayName("Zero original lines handled gracefully")
        void zeroOriginalLinesHandled() {
            KpiInput input = new KpiInput(
                0, 0,
                100, 40, 0, 0, 5.0, 3.0
            );

            KpiReport report = KpiValidator.validate(input);
            KpiResult lineReduction = report.results().get(0);
            // 0% reduction from 0 lines
            assertEquals(0.0, lineReduction.actualValue(), 0.01);
        }
    }
}
