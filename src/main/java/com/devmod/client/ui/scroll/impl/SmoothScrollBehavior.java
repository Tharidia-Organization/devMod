package com.devmod.client.ui.scroll.impl;

import com.devmod.client.ui.scroll.ScrollBehavior;
public class SmoothScrollBehavior implements ScrollBehavior {

    private static final float SMOOTHING = 0.8f;
    private static final float FRICTION = 0.9f;
    private static final float STOP_THRESHOLD = 0.1f;

    private float targetOffset = 0;
    private float currentOffset = 0;
    private float velocity = 0;
    private float maxScroll = 0;

    @Override
    public void scroll(double delta) {
        // FIX: Clamp target immediately to prevent out-of-bounds
        targetOffset = clamp(targetOffset - (float) delta);
    }

    @Override
    public void scrollTo(float target) {
        // FIX: Clamp target immediately
        targetOffset = clamp(target);
    }

    @Override
    public float getOffset() {
        return currentOffset;
    }

    @Override
    public void setMaxScroll(float max) {
        this.maxScroll = Math.max(0, max);
        // Clamp both target and current when max changes
        targetOffset = clamp(targetOffset);
        currentOffset = clamp(currentOffset);
    }

    @Override
    public void update() {
        float diff = targetOffset - currentOffset;
        velocity += diff * SMOOTHING;
        velocity *= FRICTION;
        currentOffset += velocity;

        // Stop micro-movements when close enough
        if (Math.abs(diff) < STOP_THRESHOLD && Math.abs(velocity) < STOP_THRESHOLD) {
            currentOffset = targetOffset;
            velocity = 0;
        }

        // Final safety clamp
        currentOffset = clamp(currentOffset);
    }

    @Override
    public boolean isAnimating() {
        return Math.abs(targetOffset - currentOffset) > STOP_THRESHOLD
            || Math.abs(velocity) > STOP_THRESHOLD;
    }

    @Override
    public void reset() {
        targetOffset = 0;
        currentOffset = 0;
        velocity = 0;
    }

    private float clamp(float value) {
        return Math.max(0, Math.min(maxScroll, value));
    }
}
