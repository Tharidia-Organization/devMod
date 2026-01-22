package com.devmod.foundry.util;

/**
 * Accumulates fractional progress into whole ticks.
 */
public final class ProgressAccumulator {
    private float remainder;

    /**
     * Adds a speed value and returns whole-tick progress to apply.
     */
    public int accumulate(float speed) {
        if (speed <= 0f) {
            remainder = 0f;
            return 0;
        }
        remainder += speed;
        int delta = (int) Math.floor(remainder);
        remainder -= delta;
        return delta;
    }

    public void reset() {
        remainder = 0f;
    }

    public float getRemainder() {
        return remainder;
    }

    public void setRemainder(float value) {
        if (value <= 0f) {
            remainder = 0f;
            return;
        }
        remainder = value - (float) Math.floor(value);
    }
}
