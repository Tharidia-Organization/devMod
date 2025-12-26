package com.devmod.arena.autosmoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutosmokeReportHeaderDirectTest {

    @Test
    @DisplayName("capture hashes config content and populates context")
    void captureHashesConfigContent() {
        AutosmokeReportHeader.clearCache();

        AutosmokeReportHeader.ReportHeader header = AutosmokeReportHeader.capture("config=smoke");

        assertNotNull(header);
        assertFalse(header.timestamp().isBlank());
        assertNotEquals("N/A", header.configHash());
        assertEquals(12, header.configHash().length());
    }

    @Test
    @DisplayName("formatters include expected fields")
    void formattersIncludeExpectedFields() {
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

        String formatted = header.format();
        assertTrue(formatted.contains("AUTOSMOKE REPORT"));
        assertTrue(formatted.contains("Timestamp:     2025-01-01T00:00:00.000+0000"));
        assertTrue(formatted.contains("Git Commit:    abc123"));
        assertTrue(formatted.contains("Config Hash:   deadbeefcafe"));

        assertEquals(
            "commit=abc123 branch=main version=1.2.3 config=deadbeefcafe java=21",
            header.formatCompact()
        );

        String json = header.formatJson();
        assertTrue(json.contains("\"git_commit\":\"abc123\""));
        assertTrue(json.contains("\"config_hash\":\"deadbeefcafe\""));
        assertTrue(json.contains("\"max_memory_mb\":2048"));
        assertTrue(json.contains("\"cpus\":8"));
    }
}
