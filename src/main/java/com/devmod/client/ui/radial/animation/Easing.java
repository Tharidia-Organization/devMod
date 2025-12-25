package com.devmod.ui.radial.animation;

/**
 * Standard easing functions for smooth animations.
 *
 * <p>Easing functions transform a linear progress value (0-1) into a curved value
 * that creates more natural-feeling animations. Each function takes a progress
 * value {@code t} in range [0, 1] and returns a transformed value.</p>
 *
 * <p>Naming convention:</p>
 * <ul>
 *   <li><strong>In</strong>: Starts slow, accelerates</li>
 *   <li><strong>Out</strong>: Starts fast, decelerates</li>
 *   <li><strong>InOut</strong>: Slow at both ends, fast in middle</li>
 * </ul>
 *
 * @see <a href="https://easings.net/">easings.net</a> for visualizations
 */
public final class Easing {

    private Easing() {
        // Utility class - no instantiation
    }

    // ================================================================
    // LINEAR
    // ================================================================

    /**
     * Linear interpolation - no easing.
     *
     * @param t progress (0-1)
     * @return unchanged progress
     */
    public static float linear(float t) {
        return t;
    }

    // ================================================================
    // QUADRATIC (power of 2)
    // ================================================================

    /**
     * Quadratic ease-in: starts slow, accelerates.
     *
     * @param t progress (0-1)
     * @return eased value
     */
    public static float easeInQuad(float t) {
        return t * t;
    }

    /**
     * Quadratic ease-out: starts fast, decelerates.
     * Most commonly used for UI animations.
     *
     * @param t progress (0-1)
     * @return eased value
     */
    public static float easeOutQuad(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    /**
     * Quadratic ease-in-out: slow at both ends.
     *
     * @param t progress (0-1)
     * @return eased value
     */
    public static float easeInOutQuad(float t) {
        return t < 0.5f
            ? 2 * t * t
            : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
    }

    // ================================================================
    // CUBIC (power of 3)
    // ================================================================

    /**
     * Cubic ease-in: starts slow, accelerates more aggressively.
     *
     * @param t progress (0-1)
     * @return eased value
     */
    public static float easeInCubic(float t) {
        return t * t * t;
    }

    /**
     * Cubic ease-out: starts fast, decelerates more aggressively.
     *
     * @param t progress (0-1)
     * @return eased value
     */
    public static float easeOutCubic(float t) {
        return 1 - (float) Math.pow(1 - t, 3);
    }

    /**
     * Cubic ease-in-out: slow at both ends, fast in middle.
     *
     * @param t progress (0-1)
     * @return eased value
     */
    public static float easeInOutCubic(float t) {
        return t < 0.5f
            ? 4 * t * t * t
            : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    // ================================================================
    // QUARTIC (power of 4)
    // ================================================================

    /**
     * Quartic ease-out: very smooth deceleration.
     *
     * @param t progress (0-1)
     * @return eased value
     */
    public static float easeOutQuart(float t) {
        return 1 - (float) Math.pow(1 - t, 4);
    }

    // ================================================================
    // EXPONENTIAL
    // ================================================================

    /**
     * Exponential ease-out: very quick start, very slow end.
     *
     * @param t progress (0-1)
     * @return eased value
     */
    public static float easeOutExpo(float t) {
        return t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
    }

    // ================================================================
    // ELASTIC
    // ================================================================

    /**
     * Elastic ease-out: overshoots then settles (like a spring).
     * Good for "bounce" or "pop" effects.
     *
     * @param t progress (0-1)
     * @return eased value (may exceed 1 during overshoot)
     */
    public static float easeOutElastic(float t) {
        if (t == 0 || t == 1) return t;

        float c4 = (float) (2 * Math.PI) / 3;
        return (float) Math.pow(2, -10 * t) * (float) Math.sin((t * 10 - 0.75) * c4) + 1;
    }

    // ================================================================
    // BACK (overshoots)
    // ================================================================

    /**
     * Back ease-out: overshoots slightly then returns.
     * Subtle "pop" effect.
     *
     * @param t progress (0-1)
     * @return eased value
     */
    public static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    // ================================================================
    // BOUNCE
    // ================================================================

    /**
     * Bounce ease-out: bounces at the end like a ball.
     *
     * @param t progress (0-1)
     * @return eased value
     */
    public static float easeOutBounce(float t) {
        float n1 = 7.5625f;
        float d1 = 2.75f;

        if (t < 1 / d1) {
            return n1 * t * t;
        } else if (t < 2 / d1) {
            t -= 1.5f / d1;
            return n1 * t * t + 0.75f;
        } else if (t < 2.5 / d1) {
            t -= 2.25f / d1;
            return n1 * t * t + 0.9375f;
        } else {
            t -= 2.625f / d1;
            return n1 * t * t + 0.984375f;
        }
    }

    // ================================================================
    // UTILITY METHODS
    // ================================================================

    /**
     * Clamps a value to [0, 1] range.
     *
     * @param t value to clamp
     * @return clamped value
     */
    public static float clamp01(float t) {
        return Math.max(0, Math.min(1, t));
    }

    /**
     * Linear interpolation between two values.
     *
     * @param start start value
     * @param end   end value
     * @param t     progress (0-1)
     * @return interpolated value
     */
    public static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    /**
     * Inverse lerp: finds progress given a value between start and end.
     *
     * @param start start value
     * @param end   end value
     * @param value current value
     * @return progress (0-1)
     */
    public static float inverseLerp(float start, float end, float value) {
        if (Math.abs(end - start) < 0.0001f) return 0;
        return (value - start) / (end - start);
    }

    /**
     * Smooth step: Hermite interpolation.
     *
     * @param t progress (0-1)
     * @return smoothed value
     */
    public static float smoothStep(float t) {
        return t * t * (3 - 2 * t);
    }

    /**
     * Smoother step: Ken Perlin's improved interpolation.
     *
     * @param t progress (0-1)
     * @return smoothed value
     */
    public static float smootherStep(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
}
