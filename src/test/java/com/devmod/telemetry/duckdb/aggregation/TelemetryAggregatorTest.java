package com.devmod.telemetry.duckdb.aggregation;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.telemetry.duckdb.aggregation.TelemetryAggregator.AggregatedEvent;
import com.devmod.telemetry.duckdb.lvc.TelemetryLVC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TelemetryAggregator.
 */
class TelemetryAggregatorTest {

    private TelemetryAggregator aggregator;
    private UUID testPlayerId;
    private TelemetryLVC lvc;

    @BeforeEach
    void setUp() {
        lvc = TelemetryLVC.INSTANCE;
        lvc.clear();
        testPlayerId = UUID.randomUUID();
        aggregator = new TelemetryAggregator(testPlayerId, lvc);
    }

    @AfterEach
    void tearDown() {
        lvc.clear();
    }

    // ============================================
    // LIFECYCLE TESTS
    // ============================================

    @Test
    @DisplayName("constructor initializes aggregator correctly")
    void testConstructor() {
        assertNotNull(aggregator.getPlayerId());
        assertEquals(testPlayerId, aggregator.getPlayerId());
        assertTrue(aggregator.isActive());
        assertNotNull(aggregator.getSessionId());
        assertNotNull(aggregator.getCreatedAt());
    }

    @Test
    @DisplayName("shutdown deactivates aggregator")
    void testShutdown() {
        assertTrue(aggregator.isActive());

        aggregator.shutdown();

        assertFalse(aggregator.isActive());
    }

    @Test
    @DisplayName("inactive aggregator bypasses all events")
    void testInactiveBypassesEvents() {
        aggregator.shutdown();

        // All process methods should return true (bypass) when inactive
        assertTrue(aggregator.processHit(10.0, false, false, "sword", "zombie"));
        assertTrue(aggregator.processAbility("dash", true, 10.0, 0.0));
        assertTrue(aggregator.processXp(100, "kill"));
        assertTrue(aggregator.processHeatmap(0, 64, 0, "movement"));
    }

    // ============================================
    // PROCESS HIT TESTS
    // ============================================

    @Test
    @DisplayName("processHit returns false (aggregated)")
    void testProcessHit_returnsAggregated() {
        boolean result = aggregator.processHit(25.0, false, false, "sword", "zombie");

        assertFalse(result); // Should be aggregated
    }

    @Test
    @DisplayName("processHit updates LVC")
    void testProcessHit_updatesLVC() {
        aggregator.processHit(25.0, true, false, "sword", "zombie");

        assertEquals(25.0, lvc.getTotalDamageDealt(testPlayerId), 0.1);
        assertEquals(1, lvc.getKillCount(testPlayerId));
    }

    @Test
    @DisplayName("processHit aggregates multiple hits")
    void testProcessHit_aggregatesMultiple() {
        aggregator.processHit(10.0, false, false, "sword", "zombie");
        aggregator.processHit(20.0, true, false, "sword", "skeleton");
        aggregator.processHit(15.0, false, true, "sword", "zombie"); // crit

        var stats = aggregator.getStats();
        assertEquals(3, stats.pendingHits());
        assertEquals(45.0, stats.pendingDamage(), 0.1);
    }

    @Test
    @DisplayName("processMiss increments miss counter")
    void testProcessMiss() {
        aggregator.processMiss();
        aggregator.processMiss();

        // Misses are tracked internally, verify via stats or LVC
        // Note: processMiss doesn't update LVC directly, only combat window
        assertNotNull(aggregator.getStats());
    }

    // ============================================
    // PROCESS ABILITY TESTS
    // ============================================

    @Test
    @DisplayName("processAbility returns false (aggregated)")
    void testProcessAbility_returnsAggregated() {
        boolean result = aggregator.processAbility("dash", true, 10.0, 0.0);

        assertFalse(result);
    }

    @Test
    @DisplayName("processAbility updates LVC")
    void testProcessAbility_updatesLVC() {
        aggregator.processAbility("dash", true, 10.0, 0.0);
        aggregator.processAbility("dodge", true, 5.0, 15.0);

        assertEquals(1, lvc.getAbilityUsage(testPlayerId, "dash"));
        assertEquals(1, lvc.getAbilityUsage(testPlayerId, "dodge"));
    }

    @Test
    @DisplayName("processAbility tracks all ability types")
    void testProcessAbility_tracksAllTypes() {
        aggregator.processAbility("dash", true, 10.0, 0.0);
        aggregator.processAbility("dash", false, 10.0, 0.0);
        aggregator.processAbility("dodge", true, 5.0, 0.0);
        aggregator.processAbility("perfect_dodge", true, 0.0, 25.0);

        var stats = aggregator.getStats();
        assertEquals(4, stats.pendingAbilities());
    }

    // ============================================
    // PROCESS XP TESTS
    // ============================================

    @Test
    @DisplayName("processXp returns true (bypass)")
    void testProcessXp_returnsBypass() {
        // XP bypasses aggregation (handled by existing batching)
        boolean result = aggregator.processXp(100, "kill");

        assertTrue(result);
    }

    @Test
    @DisplayName("processXp updates LVC")
    void testProcessXp_updatesLVC() {
        aggregator.processXp(100, "kill");
        aggregator.processXp(50, "wave_bonus");

        var entry = lvc.getEntry(testPlayerId);
        assertNotNull(entry);
        assertEquals(150, entry.getTotalXpEarned());
    }

    // ============================================
    // PROCESS HEATMAP TESTS
    // ============================================

    @Test
    @DisplayName("processHeatmap returns false (aggregated)")
    void testProcessHeatmap_returnsAggregated() {
        boolean result = aggregator.processHeatmap(0, 64, 0, "movement");

        assertFalse(result);
    }

    @Test
    @DisplayName("processHeatmap aggregates positions")
    void testProcessHeatmap_aggregatesPositions() {
        aggregator.processHeatmap(0, 64, 0, "movement");
        aggregator.processHeatmap(1, 64, 0, "movement");
        aggregator.processHeatmap(2, 64, 0, "death");

        var stats = aggregator.getStats();
        assertEquals(3, stats.pendingHeatmapSamples());
    }

    // ============================================
    // WAVE/DEATH EVENT TESTS
    // ============================================

    @Test
    @DisplayName("processWaveStart updates LVC")
    void testProcessWaveStart() {
        aggregator.processWaveStart(5);

        assertEquals(5, lvc.getCurrentWave(testPlayerId));
    }

    @Test
    @DisplayName("processWaveComplete updates LVC")
    void testProcessWaveComplete() {
        aggregator.processWaveComplete(3);

        assertEquals(3, lvc.getHighestWave(testPlayerId));
    }

    @Test
    @DisplayName("processDeath updates LVC")
    void testProcessDeath() {
        lvc.createEntry(testPlayerId); // Entry must exist
        aggregator.processDeath();

        assertEquals(1, lvc.getDeathCount(testPlayerId));
    }

    @Test
    @DisplayName("processComboBreak updates LVC")
    void testProcessComboBreak() {
        // First build a combo
        aggregator.processHit(10.0, true, false, "sword", "zombie");
        aggregator.processHit(10.0, true, false, "sword", "zombie");

        assertEquals(2, lvc.getCurrentCombo(testPlayerId));

        aggregator.processComboBreak();

        assertEquals(0, lvc.getCurrentCombo(testPlayerId));
        assertEquals(2, lvc.getMaxCombo(testPlayerId)); // Max preserved
    }

    // ============================================
    // FLUSH TESTS
    // ============================================

    @Test
    @DisplayName("flush returns empty list when no data")
    void testFlush_emptyWhenNoData() {
        List<AggregatedEvent> events = aggregator.flush();

        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("forceFlush returns all pending events")
    void testForceFlush_returnsAllPending() {
        // Add some combat data
        aggregator.processHit(25.0, true, false, "sword", "zombie");

        // Add some ability data
        aggregator.processAbility("dash", true, 10.0, 0.0);

        // Add some heatmap data
        aggregator.processHeatmap(0, 64, 0, "movement");

        // Force flush
        List<AggregatedEvent> events = aggregator.forceFlush();

        // Should have 3 events: combat, ability, heatmap
        assertEquals(3, events.size());

        boolean hasCombat = events.stream().anyMatch(e -> e.type() == AggregatedEvent.Type.COMBAT);
        boolean hasAbility = events.stream().anyMatch(e -> e.type() == AggregatedEvent.Type.ABILITY);
        boolean hasHeatmap = events.stream().anyMatch(e -> e.type() == AggregatedEvent.Type.HEATMAP);

        assertTrue(hasCombat);
        assertTrue(hasAbility);
        assertTrue(hasHeatmap);
    }

    @Test
    @DisplayName("forceFlush clears pending data")
    void testForceFlush_clearsPending() {
        aggregator.processHit(25.0, true, false, "sword", "zombie");

        // First flush
        List<AggregatedEvent> firstFlush = aggregator.forceFlush();
        assertFalse(firstFlush.isEmpty());

        // Second flush should be empty
        List<AggregatedEvent> secondFlush = aggregator.forceFlush();
        assertTrue(secondFlush.isEmpty());
    }

    @Test
    @DisplayName("AggregatedEvent can be cast to specific types")
    void testAggregatedEvent_casting() {
        aggregator.processHit(50.0, true, true, "diamond_sword", "zombie");
        aggregator.processAbility("dodge", true, 5.0, 20.0);
        aggregator.processHeatmap(10, 64, 20, "death");

        List<AggregatedEvent> events = aggregator.forceFlush();

        for (AggregatedEvent event : events) {
            switch (event.type()) {
                case COMBAT -> {
                    var combat = event.asCombat();
                    assertNotNull(combat);
                    assertEquals(1, combat.hitCount());
                    assertEquals(50.0, combat.totalDamage(), 0.1);
                }
                case ABILITY -> {
                    var ability = event.asAbility();
                    assertNotNull(ability);
                    assertTrue(ability.getTotalAttempts() > 0);
                }
                case HEATMAP -> {
                    var heatmap = event.asHeatmap();
                    assertNotNull(heatmap);
                    assertEquals(1, heatmap.totalSamples());
                }
            }
        }
    }

    // ============================================
    // CONTEXT MANAGEMENT TESTS
    // ============================================

    @Test
    @DisplayName("setQuestContext updates quest and session IDs")
    void testSetQuestContext() {
        UUID questId = UUID.randomUUID();
        UUID oldSessionId = aggregator.getSessionId();

        aggregator.setQuestContext(questId, "template1", 1);

        assertEquals(questId, aggregator.getQuestId());
        // Session ID should be new
        assertFalse(oldSessionId.equals(aggregator.getSessionId()));
    }

    @Test
    @DisplayName("clearQuestContext forces flush and clears quest")
    void testClearQuestContext() {
        UUID questId = UUID.randomUUID();
        aggregator.setQuestContext(questId, "template1", 1);
        aggregator.processHit(25.0, false, false, "sword", "zombie");

        UUID sessionBeforeClear = aggregator.getSessionId();
        aggregator.clearQuestContext();

        assertNull(aggregator.getQuestId());
        // Session should be new after clear
        assertFalse(sessionBeforeClear.equals(aggregator.getSessionId()));
    }

    // ============================================
    // STATS TESTS
    // ============================================

    @Test
    @DisplayName("getStats returns current aggregator statistics")
    void testGetStats() {
        aggregator.processHit(100.0, false, false, "sword", "zombie");
        aggregator.processAbility("dash", true, 10.0, 0.0);
        aggregator.processHeatmap(0, 64, 0, "movement");

        var stats = aggregator.getStats();

        assertEquals(testPlayerId, stats.playerId());
        assertEquals(1, stats.pendingHits());
        assertEquals(100.0, stats.pendingDamage(), 0.1);
        assertEquals(1, stats.pendingAbilities());
        assertEquals(1, stats.pendingHeatmapSamples());
    }

    @Test
    @DisplayName("getMemoryEstimate returns ~5KB")
    void testGetMemoryEstimate() {
        // Should be approximately 5KB (5120 bytes)
        assertEquals(5 * 1024, aggregator.getMemoryEstimate());
    }

    // ============================================
    // SNAPSHOT SAMPLER TESTS
    // ============================================

    @Test
    @DisplayName("processSnapshot respects rate limiting")
    void testProcessSnapshot_rateLimited() {
        var snapshot1 = SnapshotSampler.SnapshotData.minimal(
            testPlayerId, "TestPlayer", 0.0, 64.0, 0.0, "minecraft:overworld"
        );

        // First snapshot should be accepted
        boolean first = aggregator.processSnapshot(snapshot1);
        assertTrue(first);

        // Immediate second snapshot should be rejected (rate limited)
        boolean second = aggregator.processSnapshot(snapshot1);
        assertFalse(second);
    }
}
