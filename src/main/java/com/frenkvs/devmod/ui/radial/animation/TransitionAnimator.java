package com.frenkvs.devmod.ui.radial.animation;

import java.util.Objects;

/**
 * Functional interface for easing functions using primitive float to avoid boxing.
 */
@FunctionalInterface
interface FloatFunction {
    float apply(float value);
}

/**
 * Manages cross-fade transitions between two states.
 *
 * <p>This class provides a clean abstraction for animating between two values
 * with configurable easing. It tracks the "from" and "to" values and provides
 * both interpolated values and separate alpha values for cross-fade effects.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * TransitionAnimator<MacroCategory> transition = new TransitionAnimator<>(
 *     MacroCategory.ANALYZE,
 *     0.12f,
 *     Easing::easeOutQuad
 * );
 *
 * // Start transition to new value
 * transition.transitionTo(MacroCategory.COMBAT);
 *
 * // Each frame
 * transition.update();
 *
 * // Render with cross-fade
 * float outgoingAlpha = transition.getOutgoingAlpha();
 * float incomingAlpha = transition.getIncomingAlpha();
 * }</pre>
 *
 * @param <T> the type of value being transitioned
 */
public final class TransitionAnimator<T> {

    // ================================================================
    // STATE
    // ================================================================

    /** Current/target value */
    private T currentValue;

    /** Previous value (null if not transitioning) */
    private T previousValue;

    /** Transition progress (0 = at previous, 1 = at current) */
    private float progress = 1f;

    /** Speed of transition (progress units per update) */
    private final float speed;

    /** Easing function to apply to progress */
    private final FloatFunction easing;

    // ================================================================
    // CONSTRUCTORS
    // ================================================================

    /**
     * Creates a new TransitionAnimator with linear easing.
     *
     * @param initialValue initial value
     * @param speed        transition speed (0-1 per update)
     */
    public TransitionAnimator(T initialValue, float speed) {
        this(initialValue, speed, Easing::linear);
    }

    /**
     * Creates a new TransitionAnimator with custom easing.
     *
     * @param initialValue initial value
     * @param speed        transition speed (0-1 per update)
     * @param easing       easing function
     */
    public TransitionAnimator(T initialValue, float speed, FloatFunction easing) {
        this.currentValue = Objects.requireNonNull(initialValue, "initialValue cannot be null");
        this.speed = speed;
        this.easing = Objects.requireNonNull(easing, "easing cannot be null");
        this.previousValue = null;
    }

    // ================================================================
    // TRANSITION CONTROL
    // ================================================================

    /**
     * Starts a transition to a new value.
     *
     * <p>If the new value equals the current value, no transition is started.
     * If a transition is already in progress, it will be interrupted.</p>
     *
     * @param newValue the target value
     * @return true if a new transition was started
     */
    public boolean transitionTo(T newValue) {
        Objects.requireNonNull(newValue, "newValue cannot be null");

        if (newValue.equals(currentValue)) {
            return false;
        }

        previousValue = currentValue;
        currentValue = newValue;
        progress = 0f;
        return true;
    }

    /**
     * Sets the value immediately without animation.
     *
     * @param value the new value
     */
    public void setImmediate(T value) {
        Objects.requireNonNull(value, "value cannot be null");
        currentValue = value;
        previousValue = null;
        progress = 1f;
    }

    /**
     * Updates the transition progress. Call once per tick/frame.
     */
    public void update() {
        if (progress < 1f) {
            progress = Math.min(1f, progress + speed);

            // Clear previous when transition completes
            if (progress >= 1f) {
                previousValue = null;
            }
        }
    }

    /**
     * Skips to the end of the current transition.
     */
    public void skipToEnd() {
        progress = 1f;
        previousValue = null;
    }

    // ================================================================
    // STATE QUERIES
    // ================================================================

    /**
     * Returns true if a transition is currently in progress.
     *
     * @return true if transitioning
     */
    public boolean isTransitioning() {
        return progress < 1f;
    }

    /**
     * Returns the raw progress value (0-1).
     *
     * @return raw progress
     */
    public float getRawProgress() {
        return progress;
    }

    /**
     * Returns the eased progress value (0-1).
     *
     * @return eased progress
     */
    public float getEasedProgress() {
        return easing.apply(progress);
    }

    /**
     * Returns the current/target value.
     *
     * @return current value
     */
    public T getCurrentValue() {
        return currentValue;
    }

    /**
     * Returns the previous value being transitioned from.
     *
     * @return previous value, or null if not transitioning
     */
    public T getPreviousValue() {
        return previousValue;
    }

    // ================================================================
    // CROSS-FADE SUPPORT
    // ================================================================

    /**
     * Returns the alpha for the outgoing (previous) element in a cross-fade.
     *
     * <p>Returns 0 if not transitioning.</p>
     *
     * @return outgoing alpha (0-1)
     */
    public float getOutgoingAlpha() {
        if (!isTransitioning()) return 0f;
        return 1f - getEasedProgress();
    }

    /**
     * Returns the alpha for the incoming (current) element in a cross-fade.
     *
     * <p>Returns 1 if not transitioning.</p>
     *
     * @return incoming alpha (0-1)
     */
    public float getIncomingAlpha() {
        if (!isTransitioning()) return 1f;
        return getEasedProgress();
    }

    // ================================================================
    // INTERPOLATION SUPPORT
    // ================================================================

    /**
     * Interpolates between two float values based on transition progress.
     *
     * @param fromValue value when progress is 0
     * @param toValue   value when progress is 1
     * @return interpolated value
     */
    public float interpolate(float fromValue, float toValue) {
        float easedProgress = getEasedProgress();
        return fromValue + (toValue - fromValue) * easedProgress;
    }

    /**
     * Interpolates between two int values based on transition progress.
     *
     * @param fromValue value when progress is 0
     * @param toValue   value when progress is 1
     * @return interpolated value
     */
    public int interpolate(int fromValue, int toValue) {
        return Math.round(interpolate((float) fromValue, (float) toValue));
    }

    /**
     * Interpolates between two colors (ARGB format) based on transition progress.
     *
     * @param fromColor color when progress is 0
     * @param toColor   color when progress is 1
     * @return interpolated color
     */
    public int interpolateColor(int fromColor, int toColor) {
        float t = getEasedProgress();
        float invT = 1f - t;

        int a1 = (fromColor >> 24) & 0xFF;
        int r1 = (fromColor >> 16) & 0xFF;
        int g1 = (fromColor >> 8) & 0xFF;
        int b1 = fromColor & 0xFF;

        int a2 = (toColor >> 24) & 0xFF;
        int r2 = (toColor >> 16) & 0xFF;
        int g2 = (toColor >> 8) & 0xFF;
        int b2 = toColor & 0xFF;

        int a = (int) (a1 * invT + a2 * t);
        int r = (int) (r1 * invT + r2 * t);
        int g = (int) (g1 * invT + g2 * t);
        int b = (int) (b1 * invT + b2 * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ================================================================
    // FACTORY METHODS
    // ================================================================

    /**
     * Creates a TransitionAnimator with ease-out-quad easing.
     *
     * @param initialValue initial value
     * @param speed        transition speed
     * @param <T>          value type
     * @return new animator
     */
    public static <T> TransitionAnimator<T> easeOut(T initialValue, float speed) {
        return new TransitionAnimator<>(initialValue, speed, Easing::easeOutQuad);
    }

    /**
     * Creates a TransitionAnimator with ease-in-out-quad easing.
     *
     * @param initialValue initial value
     * @param speed        transition speed
     * @param <T>          value type
     * @return new animator
     */
    public static <T> TransitionAnimator<T> easeInOut(T initialValue, float speed) {
        return new TransitionAnimator<>(initialValue, speed, Easing::easeInOutQuad);
    }
}
