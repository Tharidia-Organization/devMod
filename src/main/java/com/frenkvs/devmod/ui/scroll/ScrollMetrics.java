package com.frenkvs.devmod.ui.scroll;

/**
 * Immutable record containing scrollbar rendering metrics.
 * Used by ScrollManager to provide consistent scrollbar dimensions.
 */
public record ScrollMetrics(
    /** Y offset of thumb from track top */
    int thumbY,
    /** Height of the scrollbar thumb */
    int thumbHeight,
    /** Available track height (viewport height - thumb height) */
    int trackHeight,
    /** Current scroll position as percentage 0.0-1.0 */
    float scrollPercent
) {
    /**
     * Get the bottom Y position of the thumb.
     */
    public int thumbBottom() {
        return thumbY + thumbHeight;
    }

    /**
     * Check if scrollbar should be visible.
     */
    public boolean isVisible() {
        return trackHeight > 0;
    }
}
