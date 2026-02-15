package com.devmod.telemetry.duckdb;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.telemetry.duckdb.ArenaRecords.BuildEventRecord;
import com.devmod.telemetry.duckdb.ArenaRecords.BuildRecord;
import com.devmod.telemetry.duckdb.ArenaRecords.SpatialEventRecord;
import com.devmod.telemetry.duckdb.ArenaRecords.TemporalGap;
import com.devmod.telemetry.duckdb.ArenaRecords.WaveAggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ArenaRecords")
class ArenaRecordsTest {

    // ============================================
    // BuildRecord tests
    // ============================================

    @Test
    @DisplayName("BuildRecord.success creates with correct fields")
    void buildRecordSuccess() {
        Instant start = Instant.now().minusSeconds(5);
        BuildRecord record = BuildRecord.success("arena_boss", "2", start, 5000L);

        assertNotNull(record.buildId());
        assertEquals("arena_boss", record.templateId());
        assertEquals("2", record.templateVersion());
        assertEquals(start, record.startedAt());
        assertNotNull(record.completedAt());
        assertEquals(5000L, record.durationMs());
        assertEquals("success", record.status());
        assertNull(record.errorMessage());
    }

    @Test
    @DisplayName("BuildRecord.failed creates with error message")
    void buildRecordFailed() {
        Instant start = Instant.now().minusSeconds(2);
        BuildRecord record = BuildRecord.failed("arena_boss", "1", start, "Out of memory");

        assertNotNull(record.buildId());
        assertEquals("arena_boss", record.templateId());
        assertEquals("failed", record.status());
        assertEquals("Out of memory", record.errorMessage());
        assertNotNull(record.durationMs());
    }

    // ============================================
    // BuildEventRecord tests
    // ============================================

    @Test
    @DisplayName("BuildEventRecord.fromBuildRecord converts success")
    void buildEventRecordFromSuccess() {
        Instant start = Instant.now().minusSeconds(5);
        BuildRecord build = BuildRecord.success("arena_boss", "3", start, 5000L);

        BuildEventRecord event = BuildEventRecord.fromBuildRecord(build);

        assertEquals(build.buildId(), event.arenaId());
        assertEquals("arena_boss", event.templateId());
        assertEquals(3, event.templateVersion());
        assertTrue(event.success());
        assertNull(event.errorMessage());
    }

    @Test
    @DisplayName("BuildEventRecord.fromBuildRecord converts failure")
    void buildEventRecordFromFailure() {
        Instant start = Instant.now().minusSeconds(2);
        BuildRecord build = BuildRecord.failed("arena_boss", "1", start, "OOM");

        BuildEventRecord event = BuildEventRecord.fromBuildRecord(build);

        assertFalse(event.success());
        assertEquals("OOM", event.errorMessage());
    }

    @Test
    @DisplayName("BuildEventRecord.fromBuildRecord handles non-numeric version")
    void buildEventRecordHandlesNonNumericVersion() {
        Instant start = Instant.now().minusSeconds(1);
        BuildRecord build = BuildRecord.success("arena_boss", "abc", start, 1000L);

        BuildEventRecord event = BuildEventRecord.fromBuildRecord(build);
        assertNull(event.templateVersion()); // Non-numeric stays null
    }

    @Test
    @DisplayName("BuildEventRecord.fromBuildRecord handles null version")
    void buildEventRecordHandlesNullVersion() {
        Instant start = Instant.now().minusSeconds(1);
        BuildRecord build = new BuildRecord(
            UUID.randomUUID(), "arena", null, start, Instant.now(), 1000L, "success", null);

        BuildEventRecord event = BuildEventRecord.fromBuildRecord(build);
        assertNull(event.templateVersion());
    }

    // ============================================
    // SpatialEventRecord tests
    // ============================================

    @Test
    @DisplayName("SpatialEventRecord.spawn creates spawn event")
    void spatialEventSpawn() {
        UUID sessionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        SpatialEventRecord event = SpatialEventRecord.spawn(
            "arena_1", sessionId, 5, 10, 100.0, 64.0, 200.0, playerId);

        assertNotNull(event.eventId());
        assertEquals("spawn", event.eventType());
        assertEquals("arena_1", event.templateId());
        assertEquals(sessionId, event.sessionId());
        assertEquals(5, event.gridX());
        assertEquals(10, event.gridZ());
        assertEquals(100.0, event.worldX());
        assertEquals(64.0, event.worldY());
        assertEquals(200.0, event.worldZ());
        assertEquals(playerId, event.playerUuid());
        assertNotNull(event.occurredAt());
    }

    @Test
    @DisplayName("SpatialEventRecord.death creates death event")
    void spatialEventDeath() {
        UUID sessionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        SpatialEventRecord event = SpatialEventRecord.death(
            "arena_1", sessionId, 3, 7, 50.0, 64.0, 150.0, playerId);

        assertEquals("death", event.eventType());
        assertNull(event.templateVersion());
    }

    // ============================================
    // WaveAggregate tests
    // ============================================

    @Test
    @DisplayName("WaveAggregate holds wave statistics")
    void waveAggregateHoldsStats() {
        WaveAggregate wave = new WaveAggregate(5, 10, 8, 25000.0);

        assertEquals(5, wave.waveNumber());
        assertEquals(10, wave.attempts());
        assertEquals(8, wave.completions());
        assertEquals(25000.0, wave.avgDurationMs());
    }

    // ============================================
    // TemporalGap tests
    // ============================================

    @Test
    @DisplayName("TemporalGap computes duration between builds")
    void temporalGapComputesDuration() {
        Instant prev = Instant.now().minusSeconds(120);
        Instant curr = Instant.now();
        Duration gap = Duration.between(prev, curr);

        TemporalGap tg = new TemporalGap(prev, curr, gap);

        assertEquals(prev, tg.previous());
        assertEquals(curr, tg.current());
        assertTrue(tg.gap().getSeconds() >= 119); // allow slight timing variance
    }
}
