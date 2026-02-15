package com.devmod.arena.report;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ReportHeaderTest {

    @Test
    void builderDefaults() {
        ReportHeader header = ReportHeader.builder().build();
        assertEquals("unknown", header.gitCommit());
        assertEquals("unknown", header.gitBranch());
        assertEquals("0.0.0", header.version());
        assertEquals("unknown", header.environment());
        assertNotNull(header.generatedAt());
    }

    @Test
    void builderFullyConfigured() {
        Instant buildTime = Instant.parse("2025-01-15T10:00:00Z");
        Instant genTime = Instant.parse("2025-01-15T10:01:00Z");
        ReportHeader header = ReportHeader.builder()
            .gitCommit("abc1234567890")
            .gitBranch("main")
            .configHash("conf123")
            .version("1.2.3")
            .buildTimestamp(buildTime)
            .generatedAt(genTime)
            .environment("prod")
            .instanceId("srv-01")
            .metadata(Map.of("key1", "val1"))
            .build();

        assertEquals("abc1234", header.gitCommit());
        assertEquals("abc1234567890", header.gitCommitFull());
        assertEquals("main", header.gitBranch());
        assertEquals("conf123", header.configHash());
        assertEquals("1.2.3", header.version());
        assertEquals(buildTime, header.buildTimestamp());
        assertEquals(genTime, header.generatedAt());
        assertEquals("prod", header.environment());
        assertEquals("srv-01", header.instanceId());
        assertEquals("val1", header.metadata().get("key1"));
    }

    @Test
    void nullGeneratedAtDefaultsToCurrent() {
        ReportHeader header = new ReportHeader("gc", "gcf", "gb", "ch", "v",
            Instant.now(), null, "dev", "i", Map.of());
        assertNotNull(header.generatedAt());
    }

    @Test
    void nullMetadataDefaultsToEmptyMap() {
        ReportHeader header = new ReportHeader("gc", "gcf", "gb", "ch", "v",
            Instant.now(), Instant.now(), "dev", "i", null);
        assertNotNull(header.metadata());
        assertTrue(header.metadata().isEmpty());
    }

    @Test
    void isDevelopment() {
        assertTrue(ReportHeader.builder().environment("dev").build().isDevelopment());
        assertTrue(ReportHeader.builder().environment("Development").build().isDevelopment());
        assertTrue(ReportHeader.builder().environment("local").build().isDevelopment());
        assertFalse(ReportHeader.builder().environment("prod").build().isDevelopment());
    }

    @Test
    void isProduction() {
        assertTrue(ReportHeader.builder().environment("prod").build().isProduction());
        assertTrue(ReportHeader.builder().environment("Production").build().isProduction());
        assertFalse(ReportHeader.builder().environment("dev").build().isProduction());
    }

    @Test
    void toSummary() {
        ReportHeader header = ReportHeader.builder()
            .gitCommit("abc1234567")
            .gitBranch("main")
            .configHash("conf")
            .version("1.0.0")
            .environment("prod")
            .build();
        String summary = header.toSummary();
        assertTrue(summary.contains("v1.0.0"));
        assertTrue(summary.contains("abc1234"));
        assertTrue(summary.contains("main"));
        assertTrue(summary.contains("prod"));
    }

    @Test
    void toJsonContainsFields() {
        ReportHeader header = ReportHeader.builder()
            .gitCommit("abc1234")
            .version("1.0.0")
            .build();
        String json = header.toJson();
        assertTrue(json.contains("\"gitCommit\""));
        assertTrue(json.contains("\"version\""));
        assertTrue(json.contains("1.0.0"));
    }

    @Test
    void toJsonWithMetadata() {
        ReportHeader header = ReportHeader.builder()
            .metadata(Map.of("key", "value"))
            .build();
        String json = header.toJson();
        assertTrue(json.contains("\"metadata\""));
        assertTrue(json.contains("\"key\""));
        assertTrue(json.contains("\"value\""));
    }

    @Test
    void calculateConfigHash() {
        String hash1 = ReportHeader.calculateConfigHash("content1");
        String hash2 = ReportHeader.calculateConfigHash("content2");
        assertEquals(16, hash1.length());
        assertNotEquals(hash1, hash2);

        // Same content produces same hash
        assertEquals(hash1, ReportHeader.calculateConfigHash("content1"));
    }

    @Test
    void shortCommitTruncation() {
        ReportHeader header = ReportHeader.builder().gitCommit("short").build();
        assertEquals("short", header.gitCommit());

        ReportHeader header2 = ReportHeader.builder().gitCommit("abcdefghijklmnop").build();
        assertEquals("abcdefg", header2.gitCommit());
    }
}
