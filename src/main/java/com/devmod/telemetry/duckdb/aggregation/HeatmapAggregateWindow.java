package com.devmod.telemetry.duckdb.aggregation;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Aggregation window for heatmap/spatial events.
 *
 * <p>Accumulates position data over a configurable time window (default 60s),
 * using a sparse grid representation to minimize memory usage.
 *
 * <p>Memory: ~1KB (64 cells max x 12B per cell)
 */
public class HeatmapAggregateWindow {

    /** World blocks covered by one cell edge. */
    private static final int CELL_SIZE = 16;

    /** Cell coordinate range that fits the packed map key (16 signed bits each). */
    private static final int MIN_CELL = Short.MIN_VALUE;
    private static final int MAX_CELL = Short.MAX_VALUE;

    private final long windowDurationMs;
    private Instant windowStart;

    // ============================================
    // SPARSE GRID STORAGE
    // ============================================

    /**
     * Nominal grid width, reported with the aggregate. Cells themselves are
     * absolute world cells ({@link #CELL_SIZE} blocks per edge), so a window is
     * not limited to a gridSize x gridSize area around the world origin.
     */
    private final int gridSize;

    /**
     * Sparse grid: key = packed absolute cell coordinates, value = count.
     * Only non-zero cells are stored.
     */
    private final Map<Integer, Integer> sparseCounts;

    /** Maximum cells to track (prevents memory bloat) */
    private final int maxCells;

    /** Total samples recorded */
    private int totalSamples;

    /** Heatmap type (movement, death, damage, etc.) */
    private String heatmapType;

    // ============================================
    // CONTEXT
    // ============================================

    private @Nullable UUID sessionId;
    private @Nullable String templateId;

    /**
     * Create a heatmap aggregation window with default settings.
     */
    public HeatmapAggregateWindow() {
        this(AggregationConfig.HEATMAP_WINDOW_MS,
             AggregationConfig.HEATMAP_GRID_SIZE,
             AggregationConfig.MAX_HEATMAP_CELLS);
    }

    /**
     * Create a heatmap aggregation window with custom settings.
     */
    public HeatmapAggregateWindow(long windowDurationMs, int gridSize, int maxCells) {
        this.windowDurationMs = windowDurationMs;
        this.gridSize = gridSize;
        this.maxCells = maxCells;
        this.windowStart = Instant.now();
        this.sparseCounts = new HashMap<>();
        this.heatmapType = "movement";
    }

    // ============================================
    // RECORD EVENTS
    // ============================================

    /**
     * Record a position sample.
     *
     * @param worldX world X coordinate
     * @param worldY world Y coordinate (unused for 2D grid, kept for future 3D)
     * @param worldZ world Z coordinate
     * @param type heatmap type (movement, death, damage, etc.)
     */
    public synchronized void recordPosition(int worldX, int worldY, int worldZ, String type) {
        // Absolute cell coordinates: they stay comparable across windows,
        // sessions and players, unlike a grid anchored on the window.
        int cellX = normalizeToCell(worldX);
        int cellZ = normalizeToCell(worldZ);

        // Cell coordinates outside the packable range cannot be stored;
        // the sample still counts towards the total.
        if (cellX < MIN_CELL || cellX > MAX_CELL || cellZ < MIN_CELL || cellZ > MAX_CELL) {
            totalSamples++;
            return;
        }

        int key = packCell(cellX, cellZ);

        // Check max cells limit
        if (!sparseCounts.containsKey(key) && sparseCounts.size() >= maxCells) {
            // At capacity - only update existing cells
            totalSamples++;
            return;
        }

        sparseCounts.merge(key, 1, Integer::sum);
        totalSamples++;

        // Update type if provided
        if (type != null && !type.isEmpty()) {
            this.heatmapType = type;
        }
    }

    /**
     * Normalize world coordinate to an absolute cell index
     * (chunk-like bucketing, 16 blocks per cell).
     */
    private static int normalizeToCell(int worldCoord) {
        return Math.floorDiv(worldCoord, CELL_SIZE);
    }

    /**
     * Pack signed cell coordinates into a single map key.
     * Both coordinates must be within [MIN_CELL, MAX_CELL].
     */
    private static int packCell(int cellX, int cellZ) {
        return (cellX << 16) | (cellZ & 0xFFFF);
    }

    /**
     * Unpack the X coordinate of a key produced by {@link #packCell(int, int)}.
     */
    static int unpackCellX(int key) {
        return key >> 16;
    }

    /**
     * Unpack the Z coordinate of a key produced by {@link #packCell(int, int)}.
     */
    static int unpackCellZ(int key) {
        return (short) (key & 0xFFFF);
    }

    // ============================================
    // WINDOW MANAGEMENT
    // ============================================

    /**
     * Check if window should be flushed.
     */
    public synchronized boolean shouldFlush() {
        if (isEmpty()) return false;

        long elapsed = Instant.now().toEpochMilli() - windowStart.toEpochMilli();
        return elapsed >= windowDurationMs;
    }

    /**
     * Check if window has any data.
     */
    public synchronized boolean isEmpty() {
        return totalSamples == 0;
    }

    /**
     * Flush window and return aggregate data.
     */
    public synchronized HeatmapAggregate flush() {
        Instant windowEnd = Instant.now();

        HeatmapAggregate aggregate = new HeatmapAggregate(
            windowStart,
            windowEnd,
            heatmapType,
            new HashMap<>(sparseCounts),
            gridSize,
            totalSamples,
            sessionId,
            templateId
        );

        // Reset
        reset();

        return aggregate;
    }

    /**
     * Reset window.
     */
    public synchronized void reset() {
        windowStart = Instant.now();
        sparseCounts.clear();
        totalSamples = 0;
    }

    // ============================================
    // CONTEXT SETTERS
    // ============================================

    public synchronized void setSessionId(@Nullable UUID sessionId) {
        this.sessionId = sessionId;
    }

    public synchronized void setTemplateId(@Nullable String templateId) {
        this.templateId = templateId;
    }

    public synchronized void setHeatmapType(String type) {
        this.heatmapType = type;
    }

    // ============================================
    // GETTERS
    // ============================================

    public synchronized int getTotalSamples() {
        return totalSamples;
    }

    public synchronized int getCellCount() {
        return sparseCounts.size();
    }

    public synchronized Instant getWindowStart() {
        return windowStart;
    }

    // ============================================
    // INNER CLASSES
    // ============================================

    /**
     * Aggregated heatmap data for a single window.
     */
    public record HeatmapAggregate(
        Instant windowStart,
        Instant windowEnd,
        String heatmapType,
        Map<Integer, Integer> sparseCounts,
        int gridSize,
        int totalSamples,
        @Nullable UUID sessionId,
        @Nullable String templateId
    ) {
        /**
         * Convert sparse counts to JSON for storage.
         * Format: {"0,5": 10, "1,3": 5, ...}
         */
        public String toGridJson() {
            if (sparseCounts.isEmpty()) return "{}";

            StringBuilder sb = new StringBuilder("{");
            boolean first = true;

            for (Map.Entry<Integer, Integer> e : sparseCounts.entrySet()) {
                int key = e.getKey();
                int cellX = unpackCellX(key);
                int cellZ = unpackCellZ(key);

                if (!first) sb.append(",");
                sb.append("\"").append(cellX).append(",").append(cellZ)
                  .append("\":").append(e.getValue());
                first = false;
            }

            sb.append("}");
            return sb.toString();
        }

        /**
         * Get the cell with highest count.
         */
        public int[] getHotspot() {
            int maxKey = -1;
            int maxCount = 0;

            for (Map.Entry<Integer, Integer> e : sparseCounts.entrySet()) {
                if (e.getValue() > maxCount) {
                    maxCount = e.getValue();
                    maxKey = e.getKey();
                }
            }

            if (maxCount == 0) return new int[]{0, 0, 0};

            return new int[]{unpackCellX(maxKey), unpackCellZ(maxKey), maxCount};
        }

        /**
         * Get average density (samples per cell).
         */
        public double getAverageDensity() {
            return sparseCounts.isEmpty() ? 0.0 :
                (double) totalSamples / sparseCounts.size();
        }
    }
}
