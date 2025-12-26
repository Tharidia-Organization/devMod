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

    private final long windowDurationMs;
    private Instant windowStart;

    // ============================================
    // SPARSE GRID STORAGE
    // ============================================

    /**
     * Grid size (positions are bucketed into gridSize x gridSize cells).
     * Cell (x,z) covers positions from (x*cellSize) to ((x+1)*cellSize-1).
     */
    private final int gridSize;

    /**
     * Sparse grid: key = cellX * gridSize + cellZ, value = count.
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
    public void recordPosition(int worldX, int worldY, int worldZ, String type) {
        // Normalize to grid cell
        int cellX = normalizeToCell(worldX);
        int cellZ = normalizeToCell(worldZ);

        // Clamp to grid bounds
        cellX = Math.max(0, Math.min(gridSize - 1, cellX));
        cellZ = Math.max(0, Math.min(gridSize - 1, cellZ));

        int key = cellX * gridSize + cellZ;

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
     * Normalize world coordinate to grid cell index.
     * Uses chunk-like bucketing (16 blocks per cell by default).
     */
    private int normalizeToCell(int worldCoord) {
        // Center the grid around origin
        int offset = worldCoord + (gridSize * 8);  // Shift to positive space
        return offset / 16;  // 16 blocks per cell
    }

    // ============================================
    // WINDOW MANAGEMENT
    // ============================================

    /**
     * Check if window should be flushed.
     */
    public boolean shouldFlush() {
        if (isEmpty()) return false;

        long elapsed = Instant.now().toEpochMilli() - windowStart.toEpochMilli();
        return elapsed >= windowDurationMs;
    }

    /**
     * Check if window has any data.
     */
    public boolean isEmpty() {
        return totalSamples == 0;
    }

    /**
     * Flush window and return aggregate data.
     */
    public HeatmapAggregate flush() {
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
    public void reset() {
        windowStart = Instant.now();
        sparseCounts.clear();
        totalSamples = 0;
    }

    // ============================================
    // CONTEXT SETTERS
    // ============================================

    public void setSessionId(@Nullable UUID sessionId) {
        this.sessionId = sessionId;
    }

    public void setTemplateId(@Nullable String templateId) {
        this.templateId = templateId;
    }

    public void setHeatmapType(String type) {
        this.heatmapType = type;
    }

    // ============================================
    // GETTERS
    // ============================================

    public int getTotalSamples() {
        return totalSamples;
    }

    public int getCellCount() {
        return sparseCounts.size();
    }

    public Instant getWindowStart() {
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
                int cellX = key / gridSize;
                int cellZ = key % gridSize;

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

            if (maxKey < 0) return new int[]{0, 0, 0};

            int cellX = maxKey / gridSize;
            int cellZ = maxKey % gridSize;
            return new int[]{cellX, cellZ, maxCount};
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
