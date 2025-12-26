package com.devmod.client.ui.animation;

import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class UiAnimation {

    /** Animation type */
    public enum Type {
        FADE,
        SLIDE_UP,
        SLIDE_DOWN,
        SCALE,
        FADE_SLIDE_UP,
        FADE_SLIDE_DOWN
    }

    /** Easing function type */
    public enum Ease {
        LINEAR,
        EASE_OUT_QUAD,
        EASE_OUT_CUBIC,
        EASE_IN_OUT_CUBIC,
        EASE_OUT_BACK,
        EASE_OUT_ELASTIC
    }

    // Configuration
    private final Type type;
    private final int durationMs;
    private final Ease ease;

    // State
    private long startTime = 0;
    private boolean animating = false;
    private boolean forward = true;
    private float progress = 0f;
    private Runnable onComplete;

    private UiAnimation(Type type, int durationMs, Ease ease) {
        this.type = type;
        this.durationMs = durationMs;
        this.ease = ease;
    }

    // =========================================================================
    // FACTORY METHODS
    // =========================================================================

    /**
     * Create a fade animation (150ms, ease-out).
     */
    public static UiAnimation fade() {
        return fade(150);
    }

    /**
     * Create a fade animation with custom duration.
     */
    public static UiAnimation fade(int durationMs) {
        return new UiAnimation(Type.FADE, durationMs, Ease.EASE_OUT_QUAD);
    }

    /**
     * Create a slide-up animation (200ms, ease-out).
     */
    public static UiAnimation slideUp() {
        return slideUp(200);
    }

    /**
     * Create a slide-up animation with custom duration.
     */
    public static UiAnimation slideUp(int durationMs) {
        return new UiAnimation(Type.SLIDE_UP, durationMs, Ease.EASE_OUT_CUBIC);
    }

    /**
     * Create a slide-down animation (200ms, ease-out).
     */
    public static UiAnimation slideDown() {
        return slideDown(200);
    }

    /**
     * Create a slide-down animation with custom duration.
     */
    public static UiAnimation slideDown(int durationMs) {
        return new UiAnimation(Type.SLIDE_DOWN, durationMs, Ease.EASE_OUT_CUBIC);
    }

    /**
     * Create a scale/pop animation (150ms, ease-out-back for overshoot).
     */
    public static UiAnimation scale() {
        return scale(150);
    }

    /**
     * Create a scale animation with custom duration.
     */
    public static UiAnimation scale(int durationMs) {
        return new UiAnimation(Type.SCALE, durationMs, Ease.EASE_OUT_BACK);
    }

    /**
     * Create a combined fade + slide-up animation (200ms).
     * Most common for overlay/dialog appearance.
     */
    public static UiAnimation fadeSlideUp() {
        return fadeSlideUp(200);
    }

    /**
     * Create a combined fade + slide-up animation with custom duration.
     */
    public static UiAnimation fadeSlideUp(int durationMs) {
        return new UiAnimation(Type.FADE_SLIDE_UP, durationMs, Ease.EASE_OUT_CUBIC);
    }

    /**
     * Create a combined fade + slide-down animation (200ms).
     * Good for dropdown menus or notifications.
     */
    public static UiAnimation fadeSlideDown() {
        return fadeSlideDown(200);
    }

    /**
     * Create a combined fade + slide-down animation with custom duration.
     */
    public static UiAnimation fadeSlideDown(int durationMs) {
        return new UiAnimation(Type.FADE_SLIDE_DOWN, durationMs, Ease.EASE_OUT_CUBIC);
    }

    /**
     * Create a custom animation with full control.
     */
    public static UiAnimation custom(Type type, int durationMs, Ease ease) {
        return new UiAnimation(type, durationMs, ease);
    }

    // =========================================================================
    // ANIMATION CONTROL
    // =========================================================================

    /**
     * Start the show animation (forward direction).
     */
    public void start() {
        startTime = System.currentTimeMillis();
        animating = true;
        forward = true;
        progress = 0f;
        onComplete = null;
    }

    /**
     * Reverse the animation (for hiding).
     *
     * @param onComplete Callback when animation completes
     */
    public void reverse(@Nonnull Runnable onComplete) {
        this.onComplete = onComplete;
        startTime = System.currentTimeMillis();
        animating = true;
        forward = false;
        // Start from current progress to allow smooth reversal mid-animation
        // Adjust start time to match current position
        if (progress < 1f && progress > 0f) {
            long adjustedElapsed = (long) ((forward ? progress : 1f - progress) * durationMs);
            startTime = System.currentTimeMillis() - adjustedElapsed;
        }
    }

    /**
     * Update animation state. Call this each frame/tick.
     */
    public void update() {
        if (!animating) return;

        long elapsed = System.currentTimeMillis() - startTime;
        float rawProgress = Math.min(1f, (float) elapsed / durationMs);

        // Apply easing
        float eased = applyEasing(rawProgress);

        // Apply direction
        progress = forward ? eased : 1f - eased;

        // Check if complete
        if (rawProgress >= 1f) {
            animating = false;
            progress = forward ? 1f : 0f;
            if (onComplete != null) {
                Runnable callback = onComplete;
                onComplete = null;
                callback.run();
            }
        }
    }

    /**
     * Skip to end state immediately.
     */
    public void skipToEnd() {
        animating = false;
        progress = forward ? 1f : 0f;
        if (onComplete != null) {
            Runnable callback = onComplete;
            onComplete = null;
            callback.run();
        }
    }

    /**
     * Reset to initial state.
     */
    public void reset() {
        animating = false;
        progress = 0f;
        forward = true;
        onComplete = null;
    }

    /**
     * Set progress directly (for external control).
     */
    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
    }

    // =========================================================================
    // STATE QUERIES
    // =========================================================================

    public boolean isAnimating() {
        return animating;
    }

    public boolean isComplete() {
        return !animating && progress >= 1f;
    }

    public boolean isHidden() {
        return !animating && progress <= 0f;
    }

    public float getProgress() {
        return progress;
    }

    public Type getType() {
        return type;
    }

    // =========================================================================
    // COMPUTED VALUES FOR RENDERING
    // =========================================================================

    /**
     * Get alpha value (0.0 to 1.0).
     */
    public float getAlpha() {
        if (type == Type.FADE || type == Type.FADE_SLIDE_UP || type == Type.FADE_SLIDE_DOWN) {
            return progress;
        }
        return 1f;
    }

    /**
     * Get alpha as integer (0-255).
     */
    public int getAlphaInt() {
        return (int) (getAlpha() * 255);
    }

    /**
     * Get Y offset for slide animations.
     *
     * @param maxOffset Maximum offset in pixels
     * @return Offset from final position (0 when complete)
     */
    public int getSlideOffset(int maxOffset) {
        switch (type) {
            case SLIDE_UP:
            case FADE_SLIDE_UP:
                return (int) ((1f - progress) * maxOffset);
            case SLIDE_DOWN:
            case FADE_SLIDE_DOWN:
                return (int) ((1f - progress) * -maxOffset);
            default:
                return 0;
        }
    }

    /**
     * Get scale factor (0.8 to 1.0 for subtle pop effect).
     */
    public float getScale() {
        if (type == Type.SCALE) {
            return 0.8f + (0.2f * progress);
        }
        return 1f;
    }

    /**
     * Apply animation to backdrop color (adjusts alpha based on progress).
     *
     * @param baseColor Base color with full alpha
     * @return Color with adjusted alpha
     */
    public int applyToBackdrop(int baseColor) {
        int baseAlpha = (baseColor >> 24) & 0xFF;
        int adjustedAlpha = (int) (baseAlpha * progress);
        return (adjustedAlpha << 24) | (baseColor & 0x00FFFFFF);
    }

    /**
     * Apply animation to any color (adjusts alpha based on progress).
     *
     * @param color Color to adjust
     * @return Color with adjusted alpha
     */
    public int applyToColor(int color) {
        int baseAlpha = (color >> 24) & 0xFF;
        int adjustedAlpha = (int) (baseAlpha * getAlpha());
        return (adjustedAlpha << 24) | (color & 0x00FFFFFF);
    }

    // =========================================================================
    // RENDER HELPERS
    // =========================================================================

    /**
     * Execute a render operation with animation transforms applied.
     *
     * @param graphics GuiGraphics context
     * @param renderAction The render operation to perform
     */
    public void withTransform(GuiGraphics graphics, Consumer<GuiGraphics> renderAction) {
        if (progress <= 0f) return; // Skip if fully hidden

        var pose = graphics.pose();
        pose.pushPose();

        // Apply scale
        if (type == Type.SCALE) {
            float scale = getScale();
            // Scale from center (assumes caller manages translation to center)
            pose.scale(scale, scale, 1f);
        }

        // Apply slide offset
        int offsetY = getSlideOffset(20);
        if (offsetY != 0) {
            pose.translate(0, offsetY, 0);
        }

        renderAction.accept(graphics);

        pose.popPose();
    }

    // =========================================================================
    // EASING FUNCTIONS
    // =========================================================================

    private float applyEasing(float t) {
        return switch (ease) {
            case LINEAR -> t;
            case EASE_OUT_QUAD -> 1 - (1 - t) * (1 - t);
            case EASE_OUT_CUBIC -> 1 - (float) Math.pow(1 - t, 3);
            case EASE_IN_OUT_CUBIC -> t < 0.5f
                ? 4 * t * t * t
                : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
            case EASE_OUT_BACK -> {
                float c1 = 1.70158f;
                float c3 = c1 + 1;
                yield 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
            }
            case EASE_OUT_ELASTIC -> {
                if (t == 0 || t == 1) yield t;
                float c4 = (float) (2 * Math.PI) / 3;
                yield (float) Math.pow(2, -10 * t) * (float) Math.sin((t * 10 - 0.75) * c4) + 1;
            }
        };
    }

    // =========================================================================
    // STATIC UTILITIES
    // =========================================================================

    /**
     * Linear interpolation between two values.
     */
    public static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    /**
     * Linear interpolation between two integers.
     */
    public static int lerp(int start, int end, float t) {
        return Math.round(start + (end - start) * t);
    }

    /**
     * Interpolate between two colors (including alpha).
     */
    public static int lerpColor(int startColor, int endColor, float t) {
        int a1 = (startColor >> 24) & 0xFF;
        int r1 = (startColor >> 16) & 0xFF;
        int g1 = (startColor >> 8) & 0xFF;
        int b1 = startColor & 0xFF;

        int a2 = (endColor >> 24) & 0xFF;
        int r2 = (endColor >> 16) & 0xFF;
        int g2 = (endColor >> 8) & 0xFF;
        int b2 = endColor & 0xFF;

        int a = lerp(a1, a2, t);
        int r = lerp(r1, r2, t);
        int g = lerp(g1, g2, t);
        int b = lerp(b1, b2, t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
