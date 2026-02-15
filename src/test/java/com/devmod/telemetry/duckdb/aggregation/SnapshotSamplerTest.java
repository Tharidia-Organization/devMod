package com.devmod.telemetry.duckdb.aggregation;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.telemetry.duckdb.aggregation.SnapshotSampler.SnapshotData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SnapshotSampler")
class SnapshotSamplerTest {

    private SnapshotSampler sampler;

    @BeforeEach
    void setUp() {
        // 100ms interval for fast testing
        sampler = new SnapshotSampler(100);
    }

    @Test
    @DisplayName("first snapshot is always accepted")
    void firstSnapshotAccepted() {
        SnapshotData data = SnapshotData.minimal(
            UUID.randomUUID(), "Player1", 0, 64, 0, "minecraft:overworld");
        assertTrue(sampler.tryRecord(data));
    }

    @Test
    @DisplayName("immediate second snapshot is rejected")
    void immediateSecondRejected() {
        SnapshotData data = SnapshotData.minimal(
            UUID.randomUUID(), "Player1", 0, 64, 0, "minecraft:overworld");
        sampler.tryRecord(data);
        assertFalse(sampler.tryRecord(data));
    }

    @Test
    @DisplayName("snapshot after interval is accepted")
    void snapshotAfterIntervalAccepted() throws InterruptedException {
        SnapshotData data = SnapshotData.minimal(
            UUID.randomUUID(), "Player1", 0, 64, 0, "minecraft:overworld");
        sampler.tryRecord(data);
        Thread.sleep(150); // Wait past 100ms interval
        assertTrue(sampler.tryRecord(data));
    }

    @Test
    @DisplayName("getLastSnapshot returns last accepted")
    void getLastSnapshotReturnsLastAccepted() {
        assertNull(sampler.getLastSnapshot());

        SnapshotData data = SnapshotData.minimal(
            UUID.randomUUID(), "Player1", 0, 64, 0, "minecraft:overworld");
        sampler.tryRecord(data);

        assertNotNull(sampler.getLastSnapshot());
        assertEquals("Player1", sampler.getLastSnapshot().playerName());
    }

    @Test
    @DisplayName("accepted and dropped counters are correct")
    void acceptedAndDroppedCounters() {
        SnapshotData data = SnapshotData.minimal(
            UUID.randomUUID(), "Player1", 0, 64, 0, "minecraft:overworld");

        sampler.tryRecord(data); // accepted
        sampler.tryRecord(data); // dropped
        sampler.tryRecord(data); // dropped

        assertEquals(1, sampler.getAcceptedCount());
        assertEquals(2, sampler.getDroppedCount());
    }

    @Test
    @DisplayName("getAcceptRate computes ratio correctly")
    void getAcceptRateComputesRatio() {
        SnapshotData data = SnapshotData.minimal(
            UUID.randomUUID(), "Player1", 0, 64, 0, "minecraft:overworld");

        sampler.tryRecord(data); // accepted
        sampler.tryRecord(data); // dropped
        sampler.tryRecord(data); // dropped
        sampler.tryRecord(data); // dropped

        assertEquals(0.25, sampler.getAcceptRate(), 0.001); // 1 / 4
    }

    @Test
    @DisplayName("getAcceptRate returns 0 when no samples")
    void getAcceptRateZeroWhenNoSamples() {
        assertEquals(0.0, sampler.getAcceptRate(), 0.001);
    }

    @Test
    @DisplayName("reset clears all state")
    void resetClearsState() {
        SnapshotData data = SnapshotData.minimal(
            UUID.randomUUID(), "Player1", 0, 64, 0, "minecraft:overworld");
        sampler.tryRecord(data);
        sampler.tryRecord(data);

        sampler.reset();

        assertNull(sampler.getLastSnapshot());
        assertEquals(0, sampler.getAcceptedCount());
        assertEquals(0, sampler.getDroppedCount());
        // After reset, next snapshot should be accepted
        assertTrue(sampler.tryRecord(data));
    }

    @Test
    @DisplayName("wouldAccept reflects timing")
    void wouldAcceptReflectsTiming() {
        SnapshotData data = SnapshotData.minimal(
            UUID.randomUUID(), "Player1", 0, 64, 0, "minecraft:overworld");

        assertTrue(sampler.wouldAccept()); // no previous record
        sampler.tryRecord(data);
        assertFalse(sampler.wouldAccept()); // too soon
    }

    // ============================================
    // SnapshotData record tests
    // ============================================

    @Test
    @DisplayName("SnapshotData.minimal creates with defaults")
    void snapshotDataMinimalDefaults() {
        UUID id = UUID.randomUUID();
        SnapshotData data = SnapshotData.minimal(id, "TestPlayer", 1.0, 64.0, 2.0, "minecraft:overworld");

        assertEquals(id, data.playerId());
        assertEquals("TestPlayer", data.playerName());
        assertEquals(1.0, data.x());
        assertEquals(64.0, data.y());
        assertEquals(2.0, data.z());
        assertEquals(20f, data.health());
        assertEquals(20f, data.maxHealth());
        assertEquals(100f, data.stamina());
        assertFalse(data.inCombat());
        assertFalse(data.hasQuestContext());
    }

    @Test
    @DisplayName("SnapshotData.healthPercent computes ratio")
    void snapshotDataHealthPercent() {
        SnapshotData data = SnapshotData.minimal(
            UUID.randomUUID(), "P", 0, 0, 0, "overworld");
        assertEquals(1.0f, data.healthPercent(), 0.01f);
    }

    @Test
    @DisplayName("SnapshotData.staminaPercent computes ratio")
    void snapshotDataStaminaPercent() {
        SnapshotData data = SnapshotData.minimal(
            UUID.randomUUID(), "P", 0, 0, 0, "overworld");
        assertEquals(1.0f, data.staminaPercent(), 0.01f);
    }

    @Test
    @DisplayName("SnapshotData.hasQuestContext detects quest")
    void snapshotDataHasQuestContext() {
        SnapshotData withQuest = new SnapshotData(
            java.time.Instant.now(), UUID.randomUUID(), "P",
            0, 64, 0, "overworld",
            20f, 20f, 0f, 100f, 100f,
            null, false, 0,
            UUID.randomUUID(), 1, // has quest
            60f);
        assertTrue(withQuest.hasQuestContext());
    }
}
