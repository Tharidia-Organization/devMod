package com.frenkvs.devmod.ui.editor.core;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks dirty regions to enable partial re-rendering.
 *
 * Instead of re-rendering the entire UI every frame, components can mark
 * only the regions that changed as dirty, allowing for optimized rendering.
 *
 * @see docs/editor-design-system/19-performance-considerations.md
 */
public final class DirtyRegionTracker {

    /**
     * Represents a rectangular region.
     */
    public record Region(int x, int y, int width, int height) {
        public boolean intersects(Region other) {
            return x < other.x + other.width &&
                   x + width > other.x &&
                   y < other.y + other.height &&
                   y + height > other.y;
        }

        public boolean intersects(int rx, int ry, int rw, int rh) {
            return x < rx + rw &&
                   x + width > rx &&
                   y < ry + rh &&
                   y + height > ry;
        }

        public Region union(Region other) {
            int minX = Math.min(x, other.x);
            int minY = Math.min(y, other.y);
            int maxX = Math.max(x + width, other.x + other.width);
            int maxY = Math.max(y + height, other.y + other.height);
            return new Region(minX, minY, maxX - minX, maxY - minY);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private final Set<Region> dirtyRegions = new HashSet<>();
    private boolean fullRedraw = true;

    // Stats
    private long partialRedraws = 0;
    private long fullRedraws = 0;
    private long skippedRegions = 0;

    // Singleton (eager initialization - thread-safe)
    public static final DirtyRegionTracker INSTANCE = new DirtyRegionTracker();

    private DirtyRegionTracker() {}

    // ═══════════════════════════════════════════════════════════════
    // MARKING DIRTY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Mark a region as dirty (needs redraw).
     */
    public void markDirty(int x, int y, int width, int height) {
        if (fullRedraw) return; // Already marked for full redraw

        dirtyRegions.add(new Region(x, y, width, height));
    }

    /**
     * Mark a region as dirty using Bounds.
     */
    public void markDirty(Bounds bounds) {
        if (bounds == null || bounds == Bounds.EMPTY) return;
        markDirty(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    /**
     * Mark the entire screen as needing redraw.
     */
    public void markFullRedraw() {
        fullRedraw = true;
        dirtyRegions.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // CHECKING DIRTY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if a region needs redrawing.
     *
     * @return true if the region should be redrawn
     */
    public boolean needsRedraw(int x, int y, int width, int height) {
        if (fullRedraw) {
            return true;
        }

        for (Region dirty : dirtyRegions) {
            if (dirty.intersects(x, y, width, height)) {
                partialRedraws++;
                return true;
            }
        }

        skippedRegions++;
        return false;
    }

    /**
     * Check if a region needs redrawing using Bounds.
     */
    public boolean needsRedraw(Bounds bounds) {
        if (bounds == null || bounds == Bounds.EMPTY) {
            return fullRedraw;
        }
        return needsRedraw(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    /**
     * Check if a full redraw is needed.
     */
    public boolean isFullRedraw() {
        return fullRedraw;
    }

    /**
     * Check if any region is dirty.
     */
    public boolean hasDirtyRegions() {
        return fullRedraw || !dirtyRegions.isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════
    // CLEARING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Clear all dirty regions after rendering.
     * Call this at the end of each frame.
     */
    public void clearDirty() {
        if (fullRedraw) {
            fullRedraws++;
        }
        dirtyRegions.clear();
        fullRedraw = false;
    }

    /**
     * Reset tracker and stats.
     */
    public void reset() {
        dirtyRegions.clear();
        fullRedraw = true;
        partialRedraws = 0;
        fullRedraws = 0;
        skippedRegions = 0;
    }

    // ═══════════════════════════════════════════════════════════════
    // STATS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get tracking statistics.
     */
    public TrackerStats getStats() {
        return new TrackerStats(
            dirtyRegions.size(),
            fullRedraw,
            partialRedraws,
            fullRedraws,
            skippedRegions
        );
    }

    /**
     * Tracker statistics record.
     */
    public record TrackerStats(
        int dirtyRegionCount,
        boolean fullRedrawPending,
        long partialRedraws,
        long fullRedraws,
        long skippedRegions
    ) {
        public double skipRate() {
            long total = partialRedraws + skippedRegions;
            return total == 0 ? 0 : (double) skippedRegions / total;
        }
    }
}
