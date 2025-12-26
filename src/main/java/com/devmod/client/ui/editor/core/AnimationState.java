package com.devmod.client.ui.editor.core;

import javax.annotation.Nullable;

/**
 * Utility class for managing UI animations with easing functions.
 * Supports fade, slide, and scale animations with configurable duration.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Component System
 */
public class AnimationState {

    /** Animation type constants */
    public enum Type {
        FADE,       // Alpha transition
        SLIDE_UP,   // Slide from bottom
        SLIDE_DOWN, // Slide from top
        SCALE       // Scale from center
    }

    private final Type type;
    private final int durationMs;
    private long startTime = 0;
    private boolean animating = false;
    private boolean forward = true; // true = show animation, false = hide animation
    private float progress = 0f;    // 0.0 to 1.0

    // Callback when animation completes
    @Nullable
    private Runnable onComplete;

    /**
     * Create animation state with specified type and duration.
     *
     * @param type       Animation type
     * @param durationMs Duration in milliseconds
     */
    public AnimationState(Type type, int durationMs) {
        this.type = type;
        this.durationMs = durationMs;
    }

    /**
     * Create a fade animation with default duration (150ms).
     */
    public static AnimationState fade() {
        return new AnimationState(Type.FADE, 150);
    }

    /**
     * Create a fade animation with custom duration.
     */
    public static AnimationState fade(int durationMs) {
        return new AnimationState(Type.FADE, durationMs);
    }

    /**
     * Create a slide-up animation with default duration (200ms).
     */
    public static AnimationState slideUp() {
        return new AnimationState(Type.SLIDE_UP, 200);
    }

    /**
     * Create a slide-up animation with custom duration.
     */
    public static AnimationState slideUp(int durationMs) {
        return new AnimationState(Type.SLIDE_UP, durationMs);
    }

    /**
     * Create a scale animation with default duration (150ms).
     */
    public static AnimationState scale() {
        return new AnimationState(Type.SCALE, 150);
    }

    /**
     * Create a combined fade + slide animation.
     * This is a convenience method that creates a fade animation
     * (combine with slide offset in rendering).
     */
    public static AnimationState fadeSlide() {
        return new AnimationState(Type.SLIDE_UP, 200);
    }

    // =========================================================================
    // ANIMATION CONTROL
    // =========================================================================

    /**
     * Start the show animation (forward).
     */
    public void startShow() {
        startTime = System.currentTimeMillis();
        animating = true;
        forward = true;
        progress = 0f;
    }

    /**
     * Start the hide animation (reverse).
     *
     * @param onComplete Callback when animation completes
     */
    public void startHide(@Nullable Runnable onComplete) {
        this.onComplete = onComplete;
        startTime = System.currentTimeMillis();
        animating = true;
        forward = false;
        progress = 1f;
    }

    /**
     * Update animation progress. Call this each frame.
     */
    public void update() {
        if (!animating) return;

        long elapsed = System.currentTimeMillis() - startTime;
        float rawProgress = Math.min(1f, (float) elapsed / durationMs);

        // Apply easing (ease-out cubic)
        float eased = easeOutCubic(rawProgress);

        // Direction
        if (forward) {
            progress = eased;
        } else {
            progress = 1f - eased;
        }

        // Check if complete
        if (rawProgress >= 1f) {
            animating = false;
            progress = forward ? 1f : 0f;
            if (onComplete != null) {
                onComplete.run();
                onComplete = null;
            }
        }
    }

    /**
     * Skip animation and jump to end state.
     */
    public void skipToEnd() {
        animating = false;
        progress = forward ? 1f : 0f;
        if (onComplete != null) {
            onComplete.run();
            onComplete = null;
        }
    }

    /**
     * Reset to initial hidden state.
     */
    public void reset() {
        animating = false;
        progress = 0f;
        forward = true;
        onComplete = null;
    }

    // =========================================================================
    // STATE QUERIES
    // =========================================================================

    /**
     * Check if animation is currently running.
     */
    public boolean isAnimating() {
        return animating;
    }

    /**
     * Check if currently in show animation.
     */
    public boolean isShowing() {
        return animating && forward;
    }

    /**
     * Check if currently in hide animation.
     */
    public boolean isHiding() {
        return animating && !forward;
    }

    /**
     * Get animation progress (0.0 to 1.0, with easing applied).
     */
    public float getProgress() {
        return progress;
    }

    /**
     * Get the animation type.
     */
    public Type getType() {
        return type;
    }

    // =========================================================================
    // COMPUTED VALUES FOR RENDERING
    // =========================================================================

    /**
     * Get alpha value for fade animations (0.0 to 1.0).
     */
    public float getAlpha() {
        return progress;
    }

    /**
     * Get alpha as integer (0-255).
     */
    public int getAlphaInt() {
        return (int) (progress * 255);
    }

    /**
     * Get Y offset for slide animations.
     * Returns pixels to offset from final position.
     *
     * @param slideDistance Maximum slide distance in pixels
     */
    public int getSlideOffsetY(int slideDistance) {
        if (type == Type.SLIDE_UP) {
            return (int) ((1f - progress) * slideDistance);
        } else if (type == Type.SLIDE_DOWN) {
            return (int) ((1f - progress) * -slideDistance);
        }
        return 0;
    }

    /**
     * Get scale factor for scale animations (0.0 to 1.0).
     * Starts at 0.8 and scales to 1.0 for a subtle pop effect.
     */
    public float getScale() {
        if (type == Type.SCALE) {
            return 0.8f + (0.2f * progress);
        }
        return 1f;
    }

    /**
     * Apply backdrop alpha based on animation progress.
     * Returns a color with adjusted alpha.
     *
     * @param baseColor Base backdrop color (with full alpha)
     */
    public int getBackdropColor(int baseColor) {
        int baseAlpha = (baseColor >> 24) & 0xFF;
        int adjustedAlpha = (int) (baseAlpha * progress);
        return (adjustedAlpha << 24) | (baseColor & 0x00FFFFFF);
    }

    // =========================================================================
    // EASING FUNCTIONS
    // =========================================================================

    /**
     * Ease-out cubic: decelerating to zero velocity.
     * Creates a smooth, natural-feeling animation.
     */
    private static float easeOutCubic(float t) {
        float t1 = t - 1;
        return t1 * t1 * t1 + 1;
    }

    /**
     * Ease-in-out cubic: acceleration until halfway, then deceleration.
     * Available for use in animations requiring symmetric acceleration/deceleration.
     */
    public static float easeInOutCubic(float t) {
        if (t < 0.5f) {
            return 4 * t * t * t;
        } else {
            float t1 = 2 * t - 2;
            return 0.5f * t1 * t1 * t1 + 1;
        }
    }

    /**
     * Ease-out back: slightly overshoots then settles.
     * Good for "pop" effects in UI animations.
     */
    public static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        float t1 = t - 1;
        return 1 + c3 * t1 * t1 * t1 + c1 * t1 * t1;
    }
}
