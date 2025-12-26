package com.devmod.arena.analytics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class HeatmapCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeatmapCollector.class);

    /**
     * DD49: Cell size for aggregation (5x5 blocks).
     */
    public static final int CELL_SIZE = 5;

    /**
     * Flush interval in minutes.
     */
    public static final int FLUSH_INTERVAL_MINUTES = 5;

    /**
     * Retention period in days.
     */
    public static final int RETENTION_DAYS = 30;

    /**
     * Weekly aggregation threshold in days.
     */
    public static final int WEEKLY_AGGREGATION_DAYS = 7;

    private final String arenaId;
    private final Map<CellKey, AtomicLong> currentBatch = new ConcurrentHashMap<>();
    private final List<HeatmapBatch> storedBatches = Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService scheduler;

    private Instant currentBatchStart;
    private HeatmapDataSink dataSink;

    private volatile boolean running = false;

    /**
     * Create a heatmap collector for an arena.
     */
    public HeatmapCollector(String arenaId) {
        this.arenaId = arenaId;
        this.currentBatchStart = Instant.now().truncatedTo(ChronoUnit.HOURS);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HeatmapCollector-" + arenaId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Set the data sink for persisting heatmap data.
     */
    public void setDataSink(HeatmapDataSink sink) {
        this.dataSink = sink;
    }

    /**
     * Start the collector with automatic flush scheduling.
     */
    public void start() {
        if (running) {
            return;
        }

        running = true;
        scheduler.scheduleAtFixedRate(
            this::flushBatch,
            FLUSH_INTERVAL_MINUTES,
            FLUSH_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );

        LOGGER.info("HeatmapCollector started for arena: {}", arenaId);
    }

    /**
     * Stop the collector.
     */
    public void stop() {
        running = false;
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Final flush
        flushBatch();

        LOGGER.info("HeatmapCollector stopped for arena: {}", arenaId);
    }

    /**
     * Record a position event.
     * DD49: No player ID is stored - only aggregated cell counts.
     *
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @param eventType Type of event (kill, death, damage, etc.)
     */
    public void recordEvent(double x, double y, double z, EventType eventType) {
        // Convert to cell coordinates (5x5 aggregation)
        CellKey key = toCellKey(x, y, z, eventType);

        // Increment count (atomic, thread-safe)
        currentBatch.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Record a position event (convenience method for integer coordinates).
     */
    public void recordEvent(int x, int y, int z, EventType eventType) {
        recordEvent((double) x, (double) y, (double) z, eventType);
    }

    /**
     * Convert world coordinates to cell key.
     * DD49: 5x5 cell aggregation.
     */
    private CellKey toCellKey(double x, double y, double z, EventType eventType) {
        int cellX = (int) Math.floor(x / CELL_SIZE);
        int cellY = (int) Math.floor(y / CELL_SIZE);
        int cellZ = (int) Math.floor(z / CELL_SIZE);

        return new CellKey(cellX, cellY, cellZ, eventType);
    }

    /**
     * Flush the current batch.
     * DD49: Every 5 minutes.
     */
    public void flushBatch() {
        if (currentBatch.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        Instant hourlyBucket = now.truncatedTo(ChronoUnit.HOURS);

        // Create batch from current data
        Map<CellKey, Long> batchData = new HashMap<>();
        for (Map.Entry<CellKey, AtomicLong> entry : currentBatch.entrySet()) {
            long count = entry.getValue().getAndSet(0);
            if (count > 0) {
                batchData.put(entry.getKey(), count);
            }
        }

        if (batchData.isEmpty()) {
            return;
        }

        HeatmapBatch batch = new HeatmapBatch(
            arenaId,
            hourlyBucket,
            currentBatchStart,
            now,
            Collections.unmodifiableMap(batchData)
        );

        storedBatches.add(batch);
        currentBatchStart = now;

        // Persist if sink is available
        if (dataSink != null) {
            try {
                dataSink.persistBatch(batch);
            } catch (Exception e) {
                LOGGER.error("Failed to persist heatmap batch", e);
            }
        }

        LOGGER.debug("Flushed heatmap batch: {} cells, {} total events",
            batchData.size(), batchData.values().stream().mapToLong(Long::longValue).sum());

        // Clean up old batches
        cleanupOldBatches();
    }

    /**
     * Clean up batches older than retention period.
     * DD49: 30 days retention, with weekly aggregation.
     */
    private void cleanupOldBatches() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        Instant aggregationCutoff = Instant.now().minus(WEEKLY_AGGREGATION_DAYS, ChronoUnit.DAYS);

        List<HeatmapBatch> toRemove = new ArrayList<>();
        List<HeatmapBatch> toAggregate = new ArrayList<>();

        synchronized (storedBatches) {
            for (HeatmapBatch batch : storedBatches) {
                if (batch.hourlyBucket().isBefore(cutoff)) {
                    toRemove.add(batch);
                } else if (batch.hourlyBucket().isBefore(aggregationCutoff)) {
                    toAggregate.add(batch);
                }
            }

            storedBatches.removeAll(toRemove);
        }

        // Aggregate old batches into weekly summaries
        if (!toAggregate.isEmpty()) {
            aggregateWeekly(toAggregate);
        }

        if (!toRemove.isEmpty()) {
            LOGGER.debug("Removed {} old heatmap batches", toRemove.size());
        }
    }

    /**
     * Aggregate batches into weekly summaries.
     * DD49: Weekly aggregation for older data.
     */
    private void aggregateWeekly(List<HeatmapBatch> batches) {
        // Group by week
        Map<Long, List<HeatmapBatch>> byWeek = new HashMap<>();

        for (HeatmapBatch batch : batches) {
            long weekNumber = batch.hourlyBucket().toEpochMilli() / (7 * 24 * 60 * 60 * 1000L);
            byWeek.computeIfAbsent(weekNumber, k -> new ArrayList<>()).add(batch);
        }

        // Create aggregated batches
        for (Map.Entry<Long, List<HeatmapBatch>> entry : byWeek.entrySet()) {
            List<HeatmapBatch> weekBatches = entry.getValue();
            if (weekBatches.size() > 1) {
                HeatmapBatch aggregated = aggregateBatches(weekBatches);

                synchronized (storedBatches) {
                    storedBatches.removeAll(weekBatches);
                    storedBatches.add(aggregated);
                }
            }
        }
    }

    /**
     * Aggregate multiple batches into one.
     */
    private HeatmapBatch aggregateBatches(List<HeatmapBatch> batches) {
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("Cannot aggregate empty batch list");
        }

        Map<CellKey, Long> aggregatedData = new HashMap<>();

        Instant minStart = batches.get(0).periodStart();
        Instant maxEnd = batches.get(0).periodEnd();

        for (HeatmapBatch batch : batches) {
            for (Map.Entry<CellKey, Long> entry : batch.data().entrySet()) {
                aggregatedData.merge(entry.getKey(), entry.getValue(), (a, b) -> a + b);
            }

            if (batch.periodStart().isBefore(minStart)) {
                minStart = batch.periodStart();
            }
            if (batch.periodEnd().isAfter(maxEnd)) {
                maxEnd = batch.periodEnd();
            }
        }

        return new HeatmapBatch(
            arenaId,
            minStart.truncatedTo(ChronoUnit.DAYS),
            minStart,
            maxEnd,
            Collections.unmodifiableMap(aggregatedData)
        );
    }

    /**
     * Get current batch data (for monitoring).
     */
    public Map<CellKey, Long> getCurrentBatchData() {
        Map<CellKey, Long> snapshot = new HashMap<>();
        for (Map.Entry<CellKey, AtomicLong> entry : currentBatch.entrySet()) {
            long value = entry.getValue().get();
            if (value > 0) {
                snapshot.put(entry.getKey(), value);
            }
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Get all stored batches.
     */
    public List<HeatmapBatch> getStoredBatches() {
        synchronized (storedBatches) {
            return new ArrayList<>(storedBatches);
        }
    }

    /**
     * Get arena ID.
     */
    public String getArenaId() {
        return arenaId;
    }

    /**
     * Check if collector is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Event types for heatmap tracking.
     */
    public enum EventType {
        KILL,
        DEATH,
        DAMAGE_DEALT,
        DAMAGE_RECEIVED,
        SPAWN,
        MOVEMENT,
        ABILITY_USED,
        ITEM_PICKUP
    }

    /**
     * Cell key for aggregated position data.
     * DD49: No player ID - only cell coordinates and event type.
     */
    public record CellKey(int cellX, int cellY, int cellZ, EventType eventType) {
        /**
         * Get the world coordinate range for this cell.
         */
        public int[] getWorldRange() {
            return new int[]{
                cellX * CELL_SIZE, (cellX + 1) * CELL_SIZE,
                cellY * CELL_SIZE, (cellY + 1) * CELL_SIZE,
                cellZ * CELL_SIZE, (cellZ + 1) * CELL_SIZE
            };
        }

        /**
         * Get the center world coordinates of this cell.
         */
        public double[] getWorldCenter() {
            return new double[]{
                cellX * CELL_SIZE + CELL_SIZE / 2.0,
                cellY * CELL_SIZE + CELL_SIZE / 2.0,
                cellZ * CELL_SIZE + CELL_SIZE / 2.0
            };
        }
    }

    /**
     * A batch of heatmap data.
     * DD49: Hourly bucket, no player ID.
     */
    public record HeatmapBatch(
        String arenaId,
        Instant hourlyBucket,
        Instant periodStart,
        Instant periodEnd,
        Map<CellKey, Long> data
    ) {
        /**
         * Get total event count in this batch.
         */
        public long getTotalEvents() {
            return data.values().stream().mapToLong(Long::longValue).sum();
        }

        /**
         * Get event count for a specific type.
         */
        public long getEventCount(EventType type) {
            return data.entrySet().stream()
                .filter(e -> e.getKey().eventType() == type)
                .mapToLong(Map.Entry::getValue)
                .sum();
        }

        /**
         * Get unique cell count.
         */
        public int getCellCount() {
            return data.size();
        }
    }

    /**
     * Interface for persisting heatmap data.
     */
    public interface HeatmapDataSink {
        void persistBatch(HeatmapBatch batch);
    }
}
