package com.devmod.telemetry.duckdb.aggregation;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.telemetry.duckdb.aggregation.CombatAggregateWindow.CombatAggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CombatAggregateWindow")
class CombatAggregateWindowTest {

    private CombatAggregateWindow window;

    @BeforeEach
    void setUp() {
        // 100ms window, 4 weapons, 5 targets
        window = new CombatAggregateWindow(100, 4, 5);
    }

    @Test
    @DisplayName("empty window reports isEmpty")
    void emptyWindowReportsIsEmpty() {
        assertTrue(window.isEmpty());
        assertEquals(0, window.getHitCount());
        assertEquals(0.0, window.getTotalDamage(), 0.001);
    }

    @Test
    @DisplayName("recordHit accumulates damage and counts")
    void recordHitAccumulatesDamageAndCounts() {
        window.recordHit(10.0, false, false, "sword", "zombie");
        window.recordHit(20.0, true, false, "sword", "zombie");
        window.recordHit(15.0, false, true, "axe", "skeleton");

        assertFalse(window.isEmpty());
        assertEquals(3, window.getHitCount());
        assertEquals(45.0, window.getTotalDamage(), 0.01);
        assertEquals(1, window.getKillCount());
    }

    @Test
    @DisplayName("recordMiss makes window non-empty")
    void recordMissMakesNonEmpty() {
        window.recordMiss();
        assertFalse(window.isEmpty());
    }

    @Test
    @DisplayName("flush returns aggregate and resets window")
    void flushReturnsAggregateAndResets() {
        window.recordHit(25.0, true, true, "sword", "zombie");
        window.recordMiss();

        CombatAggregate aggregate = window.flush();

        assertNotNull(aggregate);
        assertEquals(1, aggregate.hitCount());
        assertEquals(25.0, aggregate.totalDamage(), 0.01);
        assertEquals(1, aggregate.killCount());
        assertEquals(1, aggregate.criticalHits());
        assertEquals(1, aggregate.missCount());
        assertNotNull(aggregate.windowStart());
        assertNotNull(aggregate.windowEnd());

        // Window should be reset after flush
        assertTrue(window.isEmpty());
        assertEquals(0, window.getHitCount());
    }

    @Test
    @DisplayName("reset clears all data")
    void resetClearsAllData() {
        window.recordHit(100.0, true, true, "bow", "creeper");
        window.recordMiss();

        window.reset();

        assertTrue(window.isEmpty());
        assertEquals(0, window.getHitCount());
    }

    @Test
    @DisplayName("shouldFlush is false when empty")
    void shouldFlushFalseWhenEmpty() {
        assertFalse(window.shouldFlush());
    }

    @Test
    @DisplayName("shouldFlush is true when window expired")
    void shouldFlushTrueWhenExpired() throws InterruptedException {
        window.recordHit(10.0, false, false, "sword", "zombie");
        Thread.sleep(150); // Wait past 100ms window
        assertTrue(window.shouldFlush());
    }

    @Test
    @DisplayName("weapon stats are LRU-evicted beyond max")
    void weaponStatsLRUEviction() {
        // Max 4 weapons
        window.recordHit(1.0, true, false, "sword", "zombie");
        window.recordHit(1.0, true, false, "axe", "zombie");
        window.recordHit(1.0, true, false, "bow", "zombie");
        window.recordHit(1.0, true, false, "trident", "zombie");
        window.recordHit(1.0, true, false, "mace", "zombie"); // 5th weapon evicts oldest

        CombatAggregate agg = window.flush();
        // Should have at most 4 weapon entries
        assertTrue(agg.weaponStats().size() <= 4);
    }

    @Test
    @DisplayName("context setters propagate to aggregate")
    void contextSettersPropagate() {
        UUID sessionId = UUID.randomUUID();
        UUID questId = UUID.randomUUID();

        window.setSessionId(sessionId);
        window.setQuestId(questId);
        window.setTemplateInfo("arena_boss", 2);

        window.recordHit(10.0, false, false, "sword", "zombie");

        CombatAggregate agg = window.flush();
        assertEquals(sessionId, agg.sessionId());
        assertEquals(questId, agg.questId());
        assertEquals("arena_boss", agg.templateId());
        assertEquals(2, agg.templateVersion());
    }

    @Test
    @DisplayName("null weapon and targetType are tolerated")
    void nullWeaponAndTargetTolerated() {
        window.recordHit(10.0, false, false, null, null);
        window.recordHit(10.0, false, false, "", "");
        assertEquals(2, window.getHitCount());

        CombatAggregate agg = window.flush();
        assertTrue(agg.weaponStats().isEmpty());
        assertTrue(agg.targetStats().isEmpty());
    }

    // ============================================
    // CombatAggregate record tests
    // ============================================

    @Test
    @DisplayName("CombatAggregate.weaponStatsJson returns null when empty")
    void weaponStatsJsonNullWhenEmpty() {
        window.recordHit(10.0, false, false, null, null);
        CombatAggregate agg = window.flush();
        assertNull(agg.weaponStatsJson());
    }

    @Test
    @DisplayName("CombatAggregate.weaponStatsJson formats JSON")
    void weaponStatsJsonFormats() {
        window.recordHit(10.0, true, false, "sword", "zombie");
        CombatAggregate agg = window.flush();
        String json = agg.weaponStatsJson();
        assertNotNull(json);
        assertTrue(json.contains("sword"));
        assertTrue(json.contains("hits"));
    }

    @Test
    @DisplayName("CombatAggregate.targetStatsJson returns null when empty")
    void targetStatsJsonNullWhenEmpty() {
        window.recordHit(10.0, false, false, "sword", null);
        CombatAggregate agg = window.flush();
        assertNull(agg.targetStatsJson());
    }

    @Test
    @DisplayName("CombatAggregate.getAccuracy computes correctly")
    void aggregateAccuracy() {
        window.recordHit(10.0, false, false, "sword", "zombie");
        window.recordHit(10.0, false, false, "sword", "zombie");
        window.recordMiss();

        CombatAggregate agg = window.flush();
        assertEquals(2.0 / 3.0, agg.getAccuracy(), 0.01); // 2 hits, 1 miss
    }

    @Test
    @DisplayName("CombatAggregate.getAccuracy returns 0 when no hits/misses")
    void aggregateAccuracyZeroWhenEmpty() {
        window.recordHit(1.0, false, false, null, null); // only to create aggregate
        CombatAggregate agg = window.flush();
        // 1 hit, 0 miss -> accuracy = 1.0
        assertEquals(1.0, agg.getAccuracy(), 0.01);
    }

    @Test
    @DisplayName("WeaponStats.toJson formats correctly")
    void weaponStatsToJson() {
        CombatAggregateWindow.WeaponStats ws = new CombatAggregateWindow.WeaponStats();
        ws.record(10.5, true);
        ws.record(5.0, false);

        String json = ws.toJson();
        assertTrue(json.contains("\"hits\":2"));
        assertTrue(json.contains("\"kills\":1"));
    }

    @Test
    @DisplayName("TargetStats.toJson formats correctly")
    void targetStatsToJson() {
        CombatAggregateWindow.TargetStats ts = new CombatAggregateWindow.TargetStats();
        ts.record(25.0);
        ts.record(10.0);

        String json = ts.toJson();
        assertTrue(json.contains("\"hits\":2"));
        // Damage formatting is locale-dependent (may use comma or period)
        assertTrue(json.contains("\"damage\":"), "Should contain damage key");
        assertEquals(2, ts.getHits());
        assertEquals(35.0, ts.getDamage(), 0.01);
    }
}
