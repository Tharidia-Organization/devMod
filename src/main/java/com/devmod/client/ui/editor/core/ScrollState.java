package com.devmod.client.ui.editor.core;

/**
 * Reusable scroll state management utility.
 * Handles scroll offset calculations, bounds clamping, and scroll indicators.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Component System
 */
public class ScrollState {

    private int offset = 0;
    private int maxOffset = 0;
    private int viewportHeight = 0;
    private int contentHeight = 0;

    /**
     * Update scroll bounds based on content and viewport dimensions.
     *
     * @param contentHeight Total height of scrollable content
     * @param viewportHeight Height of visible viewport
     */
    public void update(int contentHeight, int viewportHeight) {
        this.contentHeight = contentHeight;
        this.viewportHeight = viewportHeight;
        this.maxOffset = Math.max(0, contentHeight - viewportHeight);
        // Clamp current offset to valid range
        this.offset = Math.max(0, Math.min(offset, maxOffset));
    }

    /**
     * Apply scroll delta and return new offset.
     *
     * @param delta Scroll delta (positive = scroll up/content moves down, negative = scroll down)
     * @param scrollAmount Amount to scroll per delta unit (typically row height)
     * @return The new scroll offset
     */
    public int scroll(double delta, int scrollAmount) {
        int scrollPixels = (int) (delta * scrollAmount);
        offset = Math.max(0, Math.min(maxOffset, offset - scrollPixels));
        return offset;
    }

    /**
     * Scroll by a fixed number of pixels.
     *
     * @param pixels Pixels to scroll (positive = down, negative = up)
     * @return The new scroll offset
     */
    public int scrollBy(int pixels) {
        offset = Math.max(0, Math.min(maxOffset, offset + pixels));
        return offset;
    }

    /**
     * Scroll to show a specific item at index.
     *
     * @param index Item index
     * @param itemHeight Height of each item
     */
    public void scrollToItem(int index, int itemHeight) {
        int itemTop = index * itemHeight;
        int itemBottom = itemTop + itemHeight;

        // If item is above viewport, scroll up to show it
        if (itemTop < offset) {
            offset = itemTop;
        }
        // If item is below viewport, scroll down to show it
        else if (itemBottom > offset + viewportHeight) {
            offset = itemBottom - viewportHeight;
        }

        offset = Math.max(0, Math.min(maxOffset, offset));
    }

    /**
     * Reset scroll to top.
     */
    public void reset() {
        offset = 0;
    }

    /**
     * Get the current scroll offset.
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Set the scroll offset directly.
     *
     * @param offset New offset (will be clamped to valid range)
     */
    public void setOffset(int offset) {
        this.offset = Math.max(0, Math.min(maxOffset, offset));
    }

    /**
     * Get maximum scroll offset.
     */
    public int getMaxOffset() {
        return maxOffset;
    }

    /**
     * Check if content can scroll up (offset > 0).
     */
    public boolean canScrollUp() {
        return offset > 0;
    }

    /**
     * Check if content can scroll down (offset < maxOffset).
     */
    public boolean canScrollDown() {
        return offset < maxOffset;
    }

    /**
     * Check if content is scrollable at all.
     */
    public boolean isScrollable() {
        return maxOffset > 0;
    }

    /**
     * Get scroll percentage (0.0 to 1.0).
     */
    public float getScrollPercentage() {
        if (maxOffset <= 0) return 0f;
        return (float) offset / maxOffset;
    }

    /**
     * Get the first visible row index for virtualized rendering.
     *
     * @param rowHeight Height of each row
     * @return Index of first visible row
     */
    public int getFirstVisibleRow(int rowHeight) {
        if (rowHeight <= 0) return 0;
        return offset / rowHeight;
    }

    /**
     * Get the pixel offset within the first visible row (for smooth scrolling).
     *
     * @param rowHeight Height of each row
     * @return Pixel offset within first row (0 to rowHeight-1)
     */
    public int getRowOffset(int rowHeight) {
        if (rowHeight <= 0) return 0;
        return offset % rowHeight;
    }

    /**
     * Get number of visible rows.
     *
     * @param rowHeight Height of each row
     * @return Number of rows that fit in viewport (may be partial)
     */
    public int getVisibleRowCount(int rowHeight) {
        if (rowHeight <= 0) return 0;
        // Add 1 for partial row at bottom
        return (viewportHeight / rowHeight) + 1;
    }

    /**
     * Get the content height.
     */
    public int getContentHeight() {
        return contentHeight;
    }

    /**
     * Get the viewport height.
     */
    public int getViewportHeight() {
        return viewportHeight;
    }

    /**
     * Calculate scrollbar thumb position and size.
     *
     * @param trackHeight Height of scrollbar track
     * @return ScrollbarMetrics with thumb position and size
     */
    public ScrollbarMetrics calculateScrollbar(int trackHeight) {
        if (!isScrollable() || trackHeight <= 0) {
            return new ScrollbarMetrics(0, trackHeight);
        }

        // Thumb size is proportional to viewport/content ratio
        float viewportRatio = (float) viewportHeight / contentHeight;
        int thumbHeight = Math.max(20, (int) (trackHeight * viewportRatio));

        // Thumb position based on scroll percentage
        int availableTrack = trackHeight - thumbHeight;
        int thumbY = (int) (availableTrack * getScrollPercentage());

        return new ScrollbarMetrics(thumbY, thumbHeight);
    }

    /**
     * Scrollbar metrics for rendering.
     */
    public record ScrollbarMetrics(int thumbY, int thumbHeight) {
        public int thumbBottom() {
            return thumbY + thumbHeight;
        }
    }
}
