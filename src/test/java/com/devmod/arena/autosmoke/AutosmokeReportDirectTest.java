package com.devmod.arena.autosmoke;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutosmokeReportDirectTest {

    @Test
    @DisplayName("report totals and flags reflect per-template results")
    void reportTotalsAndFlags() {
        AutosmokeReportHeader.ReportHeader header = new AutosmokeReportHeader.ReportHeader(
            "2025-01-01T00:00:00.000+0000",
            "abc123",
            "main",
            "2025-01-01",
            "1.2.3",
            "deadbeefcafe",
            "21",
            "Linux",
            2048,
            8
        );

        AutosmokeRunner.TemplateTestResult pass = AutosmokeRunner.TemplateTestResult.passed(
            "devmod:pass",
            Duration.ofMillis(120),
            "STRICT",
            1,
            2,
            3
        );
        AutosmokeRunner.TemplateTestResult fail = AutosmokeRunner.TemplateTestResult.failed(
            "devmod:fail",
            Duration.ofMillis(220),
            "LARGE",
            "bad \"input\"",
            0,
            1,
            0
        );

        AutosmokeRunner.AutosmokeReport report = new AutosmokeRunner.AutosmokeReport(
            header,
            List.of(pass, fail),
            Duration.ofMillis(340),
            1,
            1,
            new AutosmokeGuard.GuardResult(true, true, true, true)
        );

        assertEquals(1, report.totalRollbacks());
        assertEquals(6, report.totalResiduals());
        assertTrue(report.hadAnyRollbacks());
        assertTrue(report.hasAnyResiduals());
        assertFalse(report.allPassed());
    }

    @Test
    @DisplayName("CSV and JSON exports include headers, summaries, and escaped errors")
    void reportExportsContainExpectedFields() {
        AutosmokeReportHeader.ReportHeader header = new AutosmokeReportHeader.ReportHeader(
            "2025-01-01T00:00:00.000+0000",
            "abc123",
            "main",
            "2025-01-01",
            "1.2.3",
            "deadbeefcafe",
            "21",
            "Linux",
            2048,
            8
        );

        AutosmokeRunner.TemplateTestResult pass = AutosmokeRunner.TemplateTestResult.passed(
            "devmod:pass",
            Duration.ofMillis(120),
            "STRICT",
            1,
            2,
            3
        );
        AutosmokeRunner.TemplateTestResult fail = AutosmokeRunner.TemplateTestResult.failed(
            "devmod:fail",
            Duration.ofMillis(220),
            "LARGE",
            "bad \"input\"",
            0,
            1,
            0
        );

        AutosmokeRunner.AutosmokeReport report = new AutosmokeRunner.AutosmokeReport(
            header,
            List.of(pass, fail),
            Duration.ofMillis(340),
            1,
            1,
            new AutosmokeGuard.GuardResult(true, true, true, true)
        );

        String csv = report.toCsv();
        assertTrue(csv.startsWith("template_id,passed,duration_ms,threshold_mode,rollback_count,entities_residual,blocks_residual,error_message"));
        assertTrue(csv.contains("devmod:pass,true,120,STRICT,1,2,3,"));
        assertTrue(csv.contains("devmod:fail,false,220,LARGE,0,1,0,\"bad \"\"input\"\"\""));

        String json = report.toJson();
        assertTrue(json.contains("\"total_rollbacks\": 1"));
        assertTrue(json.contains("\"total_residuals\": 6"));
        assertTrue(json.contains("\"template_id\":\"devmod:fail\""));
        assertTrue(json.contains("\"error\":\"bad \\\"input\\\"\""));
    }
}
