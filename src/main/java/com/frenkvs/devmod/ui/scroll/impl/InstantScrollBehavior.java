package com.frenkvs.devmod.ui.scroll.impl;

import com.frenkvs.devmod.ui.scroll.ScrollBehavior;

/**
 * Instant scroll behavior - no animation, immediate response.
 * Suitable for settings pages and simple lists where responsiveness is preferred.
 */
public class InstantScrollBehavior implements ScrollBehavior {

    private float offset = 0;
    private float maxScroll = 0;

    @Override
    public void scroll(double delta) {
        offset = clamp(offset - (float) delta);
    }

    @Override
    public void scrollTo(float target) {
        offset = clamp(target);
    }

    @Override
    public float getOffset() {
        return offset;
    }

    @Override
    public void setMaxScroll(float max) {
        this.maxScroll = Math.max(0, max);
        // Re-clamp current offset if max changed
        offset = clamp(offset);
    }

    @Override
    public void update() {
        // No animation needed for instant scroll
    }

    private float clamp(float value) {
        return Math.max(0, Math.min(maxScroll, value));
    }
}
