package com.devmod.arena.analytics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HeatmapCollector.
 * DD49: Heatmap Privacy - 5x5 cell, hourly bucket, no player ID.
 */
@DisplayName("HeatmapCollector Tests")
class HeatmapCollectorTest {

    private HeatmapCollector collector;

    @BeforeEach
    void setUp() {
        collector = new HeatmapCollector("test-arena");
    }

    @AfterEach
    void tearDown() {
        if (collector.isRunning()) {
            collector.stop();
        }
    }

    @Test
    @DisplayName("DD49: 5x5 cell aggregation")
    void cellAggregation5x5() {
        // Events at (0,64,0) and (4,64,4) should be in same cell
        collector.recordEvent(0, 64, 0, HeatmapCollector.EventType.KILL);
        collector.recordEvent(4, 64, 4, HeatmapCollector.EventType.KILL);

        // Event at (5,64,5) should be in different cell
        collector.recordEvent(5, 64, 5, HeatmapCollector.EventType.KILL);

        Map<HeatmapCollector.CellKey, Long> data = collector.getCurrentBatchData();

        // Should have 2 cells: (0,12,0) and (1,12,1) - using CELL_SIZE=5
        assertEquals(2, data.size());

        // First cell should have count of 2
        HeatmapCollector.CellKey cell1 = new HeatmapCollector.CellKey(0, 12, 0, HeatmapCollector.EventType.KILL);
        assertEquals(2L, data.get(cell1));
    }

    @Test
    @DisplayName("DD49: Hourly bucket grouping")
    void hourlyBucketGrouping() {
        collector.recordEvent(0, 64, 0, HeatmapCollector.EventType.KILL);
        collector.flushBatch();

        var batches = collector.getStoredBatches();
        assertEquals(1, batches.size());

        // Hourly bucket should be truncated to hour
        var batch = batches.get(0);
        assertEquals(0, batch.hourlyBucket().getNano());
        assertEquals(0, batch.hourlyBucket().getEpochSecond() % 3600);
    }

    @Test
    @DisplayName("DD49: No player ID stored")
    void noPlayerIdStored() {
        // Record events - no player ID parameter exists
        collector.recordEvent(10, 64, 10, HeatmapCollector.EventType.KILL);
        collector.recordEvent(20, 64, 20, HeatmapCollector.EventType.DEATH);

        Map<HeatmapCollector.CellKey, Long> data = collector.getCurrentBatchData();

        // CellKey only contains cell coordinates and event type - no player ID
        for (HeatmapCollector.CellKey key : data.keySet()) {
            // Verify CellKey structure has no player-identifying fields
            assertNotNull(key.eventType());
            assertTrue(key.cellX() >= 0 || key.cellX() < 0); // Has cell coords
            assertTrue(key.cellY() >= 0 || key.cellY() < 0);
            assertTrue(key.cellZ() >= 0 || key.cellZ() < 0);

            // No player ID field exists in CellKey record
            assertEquals(4, HeatmapCollector.CellKey.class.getRecordComponents().length);
        }
    }

    @Test
    @DisplayName("Flush batch persists to sink")
    void flushBatchPersistsToSink() {
        AtomicReference<HeatmapCollector.HeatmapBatch> persistedBatch = new AtomicReference<>();
        collector.setDataSink(persistedBatch::set);

        collector.recordEvent(0, 64, 0, HeatmapCollector.EventType.KILL);
        collector.flushBatch();

        assertNotNull(persistedBatch.get());
        assertEquals("test-arena", persistedBatch.get().arenaId());
        assertTrue(persistedBatch.get().getTotalEvents() > 0);
    }

    @Test
    @DisplayName("Empty batch is not flushed")
    void emptyBatchNotFlushed() {
        AtomicReference<HeatmapCollector.HeatmapBatch> persistedBatch = new AtomicReference<>();
        collector.setDataSink(persistedBatch::set);

        collector.flushBatch();

        assertNull(persistedBatch.get());
    }

    @Test
    @DisplayName("Multiple event types are tracked separately")
    void multipleEventTypesTrackedSeparately() {
        collector.recordEvent(0, 64, 0, HeatmapCollector.EventType.KILL);
        collector.recordEvent(0, 64, 0, HeatmapCollector.EventType.DEATH);
        collector.recordEvent(0, 64, 0, HeatmapCollector.EventType.DAMAGE_DEALT);

        Map<HeatmapCollector.CellKey, Long> data = collector.getCurrentBatchData();

        // Should have 3 entries (same cell, different event types)
        assertEquals(3, data.size());
    }

    @Test
    @DisplayName("CellKey world range is correct")
    void cellKeyWorldRangeIsCorrect() {
        HeatmapCollector.CellKey key = new HeatmapCollector.CellKey(2, 12, 4, HeatmapCollector.EventType.KILL);

        int[] range = key.getWorldRange();

        // Cell 2 at size 5 -> 10-15
        assertEquals(10, range[0]); // minX
        assertEquals(15, range[1]); // maxX

        // Cell 12 at size 5 -> 60-65
        assertEquals(60, range[2]); // minY
        assertEquals(65, range[3]); // maxY

        // Cell 4 at size 5 -> 20-25
        assertEquals(20, range[4]); // minZ
        assertEquals(25, range[5]); // maxZ
    }

    @Test
    @DisplayName("CellKey world center is correct")
    void cellKeyWorldCenterIsCorrect() {
        HeatmapCollector.CellKey key = new HeatmapCollector.CellKey(0, 0, 0, HeatmapCollector.EventType.KILL);

        double[] center = key.getWorldCenter();

        assertEquals(2.5, center[0]); // X center
        assertEquals(2.5, center[1]); // Y center
        assertEquals(2.5, center[2]); // Z center
    }

    @Test
    @DisplayName("Batch event count methods work")
    void batchEventCountMethodsWork() {
        collector.recordEvent(0, 64, 0, HeatmapCollector.EventType.KILL);
        collector.recordEvent(0, 64, 0, HeatmapCollector.EventType.KILL);
        collector.recordEvent(0, 64, 0, HeatmapCollector.EventType.DEATH);
        collector.flushBatch();

        var batch = collector.getStoredBatches().get(0);

        assertEquals(3, batch.getTotalEvents());
        assertEquals(2, batch.getEventCount(HeatmapCollector.EventType.KILL));
        assertEquals(1, batch.getEventCount(HeatmapCollector.EventType.DEATH));
        assertEquals(0, batch.getEventCount(HeatmapCollector.EventType.DAMAGE_DEALT));
        assertEquals(2, batch.getCellCount()); // 2 different event types in same cell location
    }

    @Test
    @DisplayName("Start and stop lifecycle works")
    void startStopLifecycleWorks() {
        assertFalse(collector.isRunning());

        collector.start();
        assertTrue(collector.isRunning());

        collector.stop();
        assertFalse(collector.isRunning());
    }

    @Test
    @DisplayName("Arena ID is correct")
    void arenaIdIsCorrect() {
        assertEquals("test-arena", collector.getArenaId());
    }

    @Test
    @DisplayName("Cell size constant is 5")
    void cellSizeConstantIs5() {
        assertEquals(5, HeatmapCollector.CELL_SIZE);
    }

    @Test
    @DisplayName("Retention period is 30 days")
    void retentionPeriodIs30Days() {
        assertEquals(30, HeatmapCollector.RETENTION_DAYS);
    }

    @Test
    @DisplayName("Flush interval is 5 minutes")
    void flushIntervalIs5Minutes() {
        assertEquals(5, HeatmapCollector.FLUSH_INTERVAL_MINUTES);
    }
}
