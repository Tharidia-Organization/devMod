package com.frenkvs.devmod.ui.scroll;

/**
 * Interface for scroll behavior implementations.
 * Allows switching between instant and smooth scrolling algorithms.
 */
public interface ScrollBehavior {

    /**
     * Apply scroll delta.
     *
     * @param delta Positive = scroll up (content moves down), negative = scroll down
     */
    void scroll(double delta);

    /**
     * Scroll to absolute offset.
     *
     * @param offset Target offset value
     */
    void scrollTo(float offset);

    /**
     * Get current scroll offset value.
     *
     * @return Current offset (always clamped to valid range)
     */
    float getOffset();

    /**
     * Set maximum scroll value for clamping.
     *
     * @param maxScroll Maximum allowed scroll offset
     */
    void setMaxScroll(float maxScroll);

    /**
     * Update animation state. Call each frame for smooth scroll.
     */
    void update();

    /**
     * Check if scroll animation is currently in progress.
     *
     * @return true if animating (only relevant for smooth scroll)
     */
    default boolean isAnimating() {
        return false;
    }

    /**
     * Reset scroll to initial state (offset = 0).
     */
    default void reset() {
        scrollTo(0);
    }
}
