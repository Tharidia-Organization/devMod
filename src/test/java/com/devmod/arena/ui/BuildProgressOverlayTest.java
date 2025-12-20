package com.devmod.arena.ui;

import com.devmod.arena.ui.BuildProgressOverlay.BuildPhase;
import com.devmod.arena.ui.BuildProgressOverlay.ProgressFlags;
import com.devmod.arena.ui.BuildProgressOverlay.ProgressUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BuildProgressOverlay.
 * DD39: Verifies rate limiting (4Hz) and delta threshold (1%).
 */
class BuildProgressOverlayTest {

    private BuildProgressOverlay overlay;
    private List<ProgressUpdate> sentUpdates;
    private UUID testPlayerId;
    private UUID testArenaId;

    @BeforeEach
    void setUp() {
        sentUpdates = new ArrayList<>();
        overlay = new BuildProgressOverlay(sentUpdates::add);
        testPlayerId = UUID.randomUUID();
        testArenaId = UUID.randomUUID();
    }

    @Test
    @DisplayName("DD39: First update is always sent")
    void testFirstUpdateAlwaysSent() {
        // When
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.1, 100, 1000, 5000);

        // Then
        assertEquals(1, sentUpdates.size(), "First update should be sent");
    }

    @Test
    @DisplayName("DD39: Rate limit skips updates < 250ms")
    void testRateLimitSkipsQuickUpdates() {
        // Given: First update
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.1, 100, 1000, 5000);

        // When: Immediate second update (< 250ms)
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.2, 200, 1000, 4000);

        // Then: Only first update sent (rate limited)
        assertEquals(1, sentUpdates.size(),
            "Update within 250ms should be rate limited");
    }

    @Test
    @DisplayName("DD39: Updates after 250ms are sent")
    void testUpdatesAfterRateLimitSent() throws InterruptedException {
        // Given: First update
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.1, 100, 1000, 5000);

        // When: Wait > 250ms then update
        Thread.sleep(260);
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.3, 300, 1000, 3500);

        // Then: Both updates sent
        assertEquals(2, sentUpdates.size(),
            "Update after 250ms should be sent");
    }

    @Test
    @DisplayName("DD39: Delta threshold skips < 1% changes")
    void testDeltaThresholdSkipsSmallChanges() throws InterruptedException {
        // Given: First update at 10%
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.1, 100, 1000, 5000);

        // When: Wait for rate limit, then send 10.5% (< 1% change)
        Thread.sleep(260);
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.105, 105, 1000, 4975);

        // Then: Second update skipped due to delta threshold
        assertEquals(1, sentUpdates.size(),
            "Update with < 1% progress change should be skipped");
    }

    @Test
    @DisplayName("DD39: Delta threshold allows >= 1% changes")
    void testDeltaThresholdAllowsLargeChanges() throws InterruptedException {
        // Given: First update at 10%
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.1, 100, 1000, 5000);

        // When: Wait for rate limit, then send 11.5% (1.5% change)
        Thread.sleep(260);
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.115, 115, 1000, 4925);

        // Then: Second update sent
        assertEquals(2, sentUpdates.size(),
            "Update with >= 1% progress change should be sent");
    }

    @Test
    @DisplayName("Phase change sets PHASE_CHANGED flag")
    void testPhaseChangeFlag() throws InterruptedException {
        // Given: First update in PLACING_BLOCKS
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.1, 100, 1000, 5000);

        // When: Phase changes to SPAWNING_ENTITIES
        Thread.sleep(260);
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.SPAWNING_ENTITIES,
            0.5, 500, 1000, 2500);

        // Then
        assertEquals(2, sentUpdates.size());
        ProgressUpdate secondUpdate = sentUpdates.get(1);
        assertTrue((secondUpdate.flags() & ProgressFlags.PHASE_CHANGED) != 0,
            "PHASE_CHANGED flag should be set");
    }

    @Test
    @DisplayName("complete() sends final update with COMPLETE flag")
    void testCompleteSendsCompleteFlag() {
        // Given: Progress started
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.9, 900, 1000, 500);

        // When
        overlay.complete(testPlayerId, testArenaId, true);

        // Then
        assertEquals(2, sentUpdates.size());
        ProgressUpdate finalUpdate = sentUpdates.get(1);
        assertEquals(BuildPhase.COMPLETE, finalUpdate.phase());
        assertEquals(1.0, finalUpdate.progress(), 0.01);
        assertTrue((finalUpdate.flags() & ProgressFlags.COMPLETE) != 0);
    }

    @Test
    @DisplayName("complete(false) sends FAILED flag")
    void testCompleteFailureSendsFailedFlag() {
        // Given: Progress started
        overlay.updateProgress(testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.5, 500, 1000, 2500);

        // When
        overlay.complete(testPlayerId, testArenaId, false);

        // Then
        assertEquals(2, sentUpdates.size());
        ProgressUpdate finalUpdate = sentUpdates.get(1);
        assertEquals(BuildPhase.FAILED, finalUpdate.phase());
        assertTrue((finalUpdate.flags() & ProgressFlags.FAILED) != 0);
    }

    @Test
    @DisplayName("complete() with unknown player is no-op")
    void testCompleteUnknownPlayerNoOp() {
        // When: Complete for player that never started
        overlay.complete(UUID.randomUUID(), testArenaId, true);

        // Then: No updates sent
        assertTrue(sentUpdates.isEmpty());
    }

    @Test
    @DisplayName("clear() removes all player states")
    void testClearRemovesAllStates() {
        // Given: Multiple players with progress
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        overlay.updateProgress(player1, testArenaId, BuildPhase.PLACING_BLOCKS, 0.1, 100, 1000, 5000);
        overlay.updateProgress(player2, testArenaId, BuildPhase.PLACING_BLOCKS, 0.2, 200, 1000, 4000);

        // When
        overlay.clear();

        // Then: Complete calls do nothing (states cleared)
        int updateCount = sentUpdates.size();
        overlay.complete(player1, testArenaId, true);
        overlay.complete(player2, testArenaId, true);
        assertEquals(updateCount, sentUpdates.size(),
            "Complete after clear should not send updates");
    }

    @Test
    @DisplayName("ProgressUpdate.toBytes() produces 28 bytes")
    void testProgressUpdateToBytes() {
        // Given
        ProgressUpdate update = new ProgressUpdate(
            testPlayerId, testArenaId, BuildPhase.PLACING_BLOCKS,
            0.5, 500, 1000, 2500, ProgressFlags.NONE
        );

        // When
        byte[] bytes = update.toBytes();

        // Then
        assertEquals(28, bytes.length, "Packet should be exactly 28 bytes");
    }

    @Test
    @DisplayName("ProgressUpdate serialization round-trip preserves data")
    void testProgressUpdateSerializationRoundTrip() {
        // Given
        ProgressUpdate original = new ProgressUpdate(
            testPlayerId, testArenaId, BuildPhase.SPAWNING_ENTITIES,
            0.75, 750, 1000, 1250, ProgressFlags.PHASE_CHANGED
        );

        // When
        byte[] bytes = original.toBytes();

        // Deserialize (manual since ProgressUpdate doesn't have fromBytes)
        // Verify key fields are encoded correctly
        // UUID is in first 16 bytes
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFF);
            lsb = (lsb << 8) | (bytes[i + 8] & 0xFF);
        }
        UUID deserializedArena = new UUID(msb, lsb);

        // Phase is byte 16
        int phase = bytes[16] & 0xFF;

        // Progress is bytes 17-18
        int progressBps = ((bytes[17] & 0xFF) << 8) | (bytes[18] & 0xFF);

        // Then
        assertEquals(testArenaId, deserializedArena);
        assertEquals(BuildPhase.SPAWNING_ENTITIES.ordinal(), phase);
        assertEquals(7500, progressBps, 1); // 0.75 * 10000
    }

    @Test
    @DisplayName("Multiple players can track independently")
    void testMultiplePlayersIndependent() throws InterruptedException {
        // Given
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        // When: Both players update
        overlay.updateProgress(player1, testArenaId, BuildPhase.PLACING_BLOCKS, 0.1, 100, 1000, 5000);
        overlay.updateProgress(player2, testArenaId, BuildPhase.LOADING_CHUNKS, 0.5, 50, 100, 250);

        // Then: Both updates sent
        assertEquals(2, sentUpdates.size());

        // Verify they're for different players
        assertEquals(player1, sentUpdates.get(0).playerId());
        assertEquals(player2, sentUpdates.get(1).playerId());
    }
}
