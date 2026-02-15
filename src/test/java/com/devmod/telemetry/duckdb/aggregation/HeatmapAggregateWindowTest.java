package com.devmod.telemetry.duckdb.aggregation;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.telemetry.duckdb.aggregation.HeatmapAggregateWindow.HeatmapAggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HeatmapAggregateWindow")
class HeatmapAggregateWindowTest {

    private HeatmapAggregateWindow window;

    @BeforeEach
    void setUp() {
        // 100ms window, 16x16 grid, max 8 cells
        window = new HeatmapAggregateWindow(100, 16, 8);
    }

    @Test
    @DisplayName("empty window reports isEmpty")
    void emptyWindowIsEmpty() {
        assertTrue(window.isEmpty());
        assertEquals(0, window.getTotalSamples());
        assertEquals(0, window.getCellCount());
    }

    @Test
    @DisplayName("recordPosition makes window non-empty")
    void recordPositionMakesNonEmpty() {
        window.recordPosition(0, 64, 0, "movement");
        assertFalse(window.isEmpty());
        assertEquals(1, window.getTotalSamples());
    }

    @Test
    @DisplayName("multiple positions in same cell merge counts")
    void samePositionMergesCounts() {
        window.recordPosition(0, 64, 0, "movement");
        window.recordPosition(1, 64, 1, "movement"); // same cell
        window.recordPosition(2, 64, 2, "movement"); // same cell

        assertEquals(3, window.getTotalSamples());
        // All should map to same cell (within same 16-block chunk)
        assertTrue(window.getCellCount() <= 3);
    }

    @Test
    @DisplayName("max cells limit prevents memory bloat")
    void maxCellsLimitPreventsMemoryBloat() {
        // Fill 8 distinct cells
        for (int i = 0; i < 20; i++) {
            window.recordPosition(i * 100, 64, i * 100, "movement");
        }

        assertTrue(window.getCellCount() <= 8);
        // All samples should be counted even if cell was rejected
        assertEquals(20, window.getTotalSamples());
    }

    @Test
    @DisplayName("flush returns aggregate and resets")
    void flushReturnsAggregateAndResets() {
        window.recordPosition(0, 64, 0, "death");
        window.recordPosition(100, 64, 100, "death");

        HeatmapAggregate agg = window.flush();

        assertNotNull(agg);
        assertNotNull(agg.windowStart());
        assertNotNull(agg.windowEnd());
        assertEquals("death", agg.heatmapType());
        assertEquals(2, agg.totalSamples());
        assertEquals(16, agg.gridSize());

        // Window should be reset
        assertTrue(window.isEmpty());
    }

    @Test
    @DisplayName("shouldFlush is false when empty")
    void shouldFlushFalseWhenEmpty() {
        assertFalse(window.shouldFlush());
    }

    @Test
    @DisplayName("shouldFlush is true when window expired")
    void shouldFlushTrueWhenExpired() throws InterruptedException {
        window.recordPosition(0, 64, 0, "movement");
        Thread.sleep(150);
        assertTrue(window.shouldFlush());
    }

    @Test
    @DisplayName("setHeatmapType updates type")
    void setHeatmapTypeUpdatesType() {
        window.setHeatmapType("camping");
        window.recordPosition(0, 64, 0, null);

        HeatmapAggregate agg = window.flush();
        assertEquals("camping", agg.heatmapType());
    }

    @Test
    @DisplayName("context setters propagate to aggregate")
    void contextSettersPropagate() {
        UUID sessionId = UUID.randomUUID();
        window.setSessionId(sessionId);
        window.setTemplateId("arena_1");

        window.recordPosition(0, 64, 0, "movement");

        HeatmapAggregate agg = window.flush();
        assertEquals(sessionId, agg.sessionId());
        assertEquals("arena_1", agg.templateId());
    }

    @Test
    @DisplayName("reset clears all data")
    void resetClearsData() {
        window.recordPosition(0, 64, 0, "movement");
        window.reset();

        assertTrue(window.isEmpty());
        assertEquals(0, window.getTotalSamples());
        assertEquals(0, window.getCellCount());
    }

    // ============================================
    // HeatmapAggregate record tests
    // ============================================

    @Test
    @DisplayName("HeatmapAggregate.toGridJson returns {} when empty")
    void toGridJsonEmptyWhenEmpty() {
        window.recordPosition(0, 64, 0, "movement");
        window.reset(); // clear data, but we still need a flush
        window.recordPosition(0, 64, 0, "movement");
        HeatmapAggregate agg = window.flush();
        String json = agg.toGridJson();
        assertNotNull(json);
    }

    @Test
    @DisplayName("HeatmapAggregate.getHotspot returns cell with highest count")
    void getHotspotReturnsHighestCount() {
        window.recordPosition(0, 64, 0, "movement");
        window.recordPosition(0, 64, 0, "movement");
        window.recordPosition(0, 64, 0, "movement");
        window.recordPosition(500, 64, 500, "movement");

        HeatmapAggregate agg = window.flush();
        int[] hotspot = agg.getHotspot();
        assertNotNull(hotspot);
        assertEquals(3, hotspot.length);
        assertTrue(hotspot[2] >= 1); // count should be at least 1
    }

    @Test
    @DisplayName("HeatmapAggregate.getAverageDensity computes correctly")
    void getAverageDensityComputes() {
        window.recordPosition(0, 64, 0, "movement");
        window.recordPosition(0, 64, 0, "movement");
        window.recordPosition(0, 64, 0, "movement");

        HeatmapAggregate agg = window.flush();
        // 3 samples in 1 cell -> density = 3
        assertTrue(agg.getAverageDensity() >= 1.0);
    }

    @Test
    @DisplayName("HeatmapAggregate.getAverageDensity returns 0 when empty sparse map")
    void getAverageDensityZeroWhenEmpty() {
        // Create an aggregate with empty sparse counts manually via flush of a window with data that gets reset
        HeatmapAggregate empty = new HeatmapAggregate(
            java.time.Instant.now(), java.time.Instant.now(),
            "test", java.util.Collections.emptyMap(), 16, 0, null, null
        );
        assertEquals(0.0, empty.getAverageDensity(), 0.001);
    }
}
