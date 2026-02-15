package com.devmod.telemetry.duckdb.aggregation;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.telemetry.duckdb.aggregation.AbilityAggregateWindow.AbilityAggregate;
import com.devmod.telemetry.duckdb.aggregation.AbilityAggregateWindow.AbilityStats;
import com.devmod.telemetry.duckdb.aggregation.AbilityAggregateWindow.AbilityType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AbilityAggregateWindow")
class AbilityAggregateWindowTest {

    private AbilityAggregateWindow window;

    @BeforeEach
    void setUp() {
        window = new AbilityAggregateWindow(100); // 100ms window
    }

    @Test
    @DisplayName("empty window reports isEmpty")
    void emptyWindowReportsIsEmpty() {
        assertTrue(window.isEmpty());
        assertEquals(0, window.getTotalAttempts());
    }

    @Test
    @DisplayName("recordAbility makes window non-empty")
    void recordAbilityMakesNonEmpty() {
        window.recordAbility("dash", true, 10.0, 0.0);
        assertFalse(window.isEmpty());
        assertEquals(1, window.getTotalAttempts());
    }

    @Test
    @DisplayName("recordAbility ignores null/empty ability type")
    void recordAbilityIgnoresNullEmpty() {
        window.recordAbility(null, true, 10.0, 0.0);
        window.recordAbility("", true, 10.0, 0.0);
        assertTrue(window.isEmpty());
    }

    @Test
    @DisplayName("recordAbility parses known ability types")
    void recordAbilityParsesKnownTypes() {
        window.recordAbility("dash", true, 10.0, 0.0);
        window.recordAbility("DODGE", true, 5.0, 0.0);
        window.recordAbility("perfect_dodge", true, 0.0, 15.0);
        window.recordAbility("BLOCK", true, 0.0, 20.0);
        window.recordAbility("parry", true, 0.0, 25.0);
        window.recordAbility("exhaustion", false, 0.0, 0.0);

        assertEquals(6, window.getTotalAttempts());
    }

    @Test
    @DisplayName("recordAbility handles custom ability types")
    void recordAbilityHandlesCustom() {
        window.recordAbility("custom_ability", true, 5.0, 0.0);
        assertEquals(1, window.getTotalAttempts());
    }

    @Test
    @DisplayName("ability type variations are normalized")
    void abilityTypeVariationsNormalized() {
        // "forward_dash" contains DASH -> should map to DASH
        window.recordAbility("forward_dash", true, 10.0, 0.0);
        // "perfect-dodge" with hyphen should map to PERFECT_DODGE
        window.recordAbility("perfect-dodge", true, 0.0, 15.0);

        assertEquals(2, window.getTotalAttempts());
    }

    @Test
    @DisplayName("flush returns aggregate with stats")
    void flushReturnsAggregate() {
        window.recordAbility("dash", true, 10.0, 0.0);
        window.recordAbility("dash", false, 10.0, 0.0);
        window.recordAbility("dodge", true, 5.0, 15.0);

        AbilityAggregate aggregate = window.flush();

        assertNotNull(aggregate);
        assertNotNull(aggregate.windowStart());
        assertNotNull(aggregate.windowEnd());
        assertEquals(3, aggregate.getTotalAttempts());
        assertEquals(25.0, aggregate.getTotalStaminaSpent(), 0.01);
    }

    @Test
    @DisplayName("flush resets window")
    void flushResetsWindow() {
        window.recordAbility("dash", true, 10.0, 0.0);
        window.flush();

        assertTrue(window.isEmpty());
        assertEquals(0, window.getTotalAttempts());
    }

    @Test
    @DisplayName("shouldFlush is false when empty")
    void shouldFlushFalseWhenEmpty() {
        assertFalse(window.shouldFlush());
    }

    @Test
    @DisplayName("shouldFlush is true when window expired")
    void shouldFlushTrueWhenExpired() throws InterruptedException {
        window.recordAbility("dash", true, 10.0, 0.0);
        Thread.sleep(150); // Wait past 100ms window
        assertTrue(window.shouldFlush());
    }

    @Test
    @DisplayName("reset clears all data")
    void resetClearsAllData() {
        window.recordAbility("dash", true, 10.0, 0.0);
        window.reset();
        assertTrue(window.isEmpty());
    }

    @Test
    @DisplayName("setSessionId propagates to aggregate")
    void setSessionIdPropagates() {
        UUID sessionId = UUID.randomUUID();
        window.setSessionId(sessionId);
        window.recordAbility("dash", true, 10.0, 0.0);

        AbilityAggregate agg = window.flush();
        assertEquals(sessionId, agg.sessionId());
    }

    // ============================================
    // AbilityStats tests
    // ============================================

    @Test
    @DisplayName("AbilityStats tracks attempts and successes")
    void abilityStatsTracksAttemptsSuccesses() {
        AbilityStats stats = new AbilityStats();
        stats.record(true, 10.0, 0.0);
        stats.record(false, 5.0, 0.0);
        stats.record(true, 8.0, 3.0);

        assertEquals(3, stats.getAttempts());
        assertEquals(2, stats.getSuccesses());
        assertEquals(23.0, stats.getTotalStaminaCost(), 0.01);
        assertEquals(3.0, stats.getTotalDamageNegated(), 0.01);
    }

    @Test
    @DisplayName("AbilityStats.getSuccessRate computes ratio")
    void abilityStatsSuccessRate() {
        AbilityStats stats = new AbilityStats();
        stats.record(true, 0, 0);
        stats.record(true, 0, 0);
        stats.record(false, 0, 0);

        assertEquals(2.0 / 3.0, stats.getSuccessRate(), 0.01);
    }

    @Test
    @DisplayName("AbilityStats.getSuccessRate returns 0 when empty")
    void abilityStatsSuccessRateZeroWhenEmpty() {
        assertEquals(0.0, new AbilityStats().getSuccessRate(), 0.001);
    }

    @Test
    @DisplayName("AbilityStats.copy creates independent copy")
    void abilityStatsCopyIsIndependent() {
        AbilityStats original = new AbilityStats();
        original.record(true, 10.0, 5.0);

        AbilityStats copy = original.copy();
        assertEquals(1, copy.getAttempts());

        original.record(true, 10.0, 5.0);
        assertEquals(1, copy.getAttempts()); // copy unchanged
    }

    @Test
    @DisplayName("AbilityStats.reset clears all fields")
    void abilityStatsResetClears() {
        AbilityStats stats = new AbilityStats();
        stats.record(true, 10.0, 5.0);
        stats.reset();

        assertEquals(0, stats.getAttempts());
        assertEquals(0, stats.getSuccesses());
        assertEquals(0.0, stats.getTotalStaminaCost(), 0.001);
        assertEquals(0.0, stats.getTotalDamageNegated(), 0.001);
    }
}
