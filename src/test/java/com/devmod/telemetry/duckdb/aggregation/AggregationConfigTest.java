package com.devmod.telemetry.duckdb.aggregation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AggregationConfig")
class AggregationConfigTest {

    @Test
    @DisplayName("combat window has sensible default")
    void combatWindowSensible() {
        assertTrue(AggregationConfig.COMBAT_WINDOW_MS > 0);
        assertTrue(AggregationConfig.COMBAT_WINDOW_MS <= 300_000);
    }

    @Test
    @DisplayName("ability window has sensible default")
    void abilityWindowSensible() {
        assertTrue(AggregationConfig.ABILITY_WINDOW_MS > 0);
    }

    @Test
    @DisplayName("heatmap window has sensible default")
    void heatmapWindowSensible() {
        assertTrue(AggregationConfig.HEATMAP_WINDOW_MS > 0);
    }

    @Test
    @DisplayName("snapshot sample interval is positive")
    void snapshotIntervalPositive() {
        assertTrue(AggregationConfig.SNAPSHOT_SAMPLE_INTERVAL_MS > 0);
    }

    @Test
    @DisplayName("periodic flush interval is positive")
    void periodicFlushPositive() {
        assertTrue(AggregationConfig.PERIODIC_FLUSH_INTERVAL_MS > 0);
    }

    @Test
    @DisplayName("force flush threshold is positive")
    void forceFlushThresholdPositive() {
        assertTrue(AggregationConfig.FORCE_FLUSH_EVENT_THRESHOLD > 0);
    }

    @Test
    @DisplayName("memory limits are positive")
    void memoryLimitsPositive() {
        assertTrue(AggregationConfig.MAX_WEAPONS_PER_PLAYER > 0);
        assertTrue(AggregationConfig.MAX_TARGET_TYPES_PER_PLAYER > 0);
        assertTrue(AggregationConfig.MAX_HEATMAP_CELLS > 0);
        assertTrue(AggregationConfig.HEATMAP_GRID_SIZE > 0);
    }

    @Test
    @DisplayName("LVC settings are positive")
    void lvcSettingsPositive() {
        assertTrue(AggregationConfig.LVC_DPS_WINDOW_SECONDS > 0);
        assertTrue(AggregationConfig.LVC_TOP_N_LIMIT > 0);
        assertTrue(AggregationConfig.LVC_STALE_THRESHOLD_MS > 0);
    }
}
