package com.devmod.client.overlay.vfx;

import com.devmod.client.ui.overlay.OverlayTheme;

/**
 * Centralized constants for Impact VFX rendering.
 *
 * <p>Colors are delegated to {@link OverlayTheme.Impact} for consistency.
 * Timing and geometry constants are defined here.
 */
public final class ImpactVFXConstants {

    private ImpactVFXConstants() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // TIMING (milliseconds)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Total duration of vortex/core effect */
    public static final long CORE_DURATION_MS = 2500;

    /** Duration of slash animation */
    public static final long SLASH_DURATION_MS = 600;

    /** Duration of connection lines */
    public static final long LINE_DURATION_MS = 2000;

    /** Duration of combat glyphs */
    public static final long GLYPH_DURATION_MS = 650;

    /** Fade-in time for core effect */
    public static final long CORE_FADE_IN_MS = 100;

    /** Fade-out time for core effect */
    public static final long CORE_FADE_OUT_MS = 600;

    /** Fade-in time for line effect */
    public static final long LINE_FADE_IN_MS = 150;

    /** Fade-out time for line effect */
    public static final long LINE_FADE_OUT_MS = 500;

    /** Fade-in time for glyph effect */
    public static final long GLYPH_FADE_IN_MS = 80;

    /** Fade-out time for glyph effect */
    public static final long GLYPH_FADE_OUT_MS = 220;

    // ═══════════════════════════════════════════════════════════════════════════
    // COLORS (delegated to OverlayTheme)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Core primary color (electric blue) */
    public static final int COLOR_CORE_PRIMARY = OverlayTheme.Impact.CORE_PRIMARY;

    /** Core secondary color (cyan) */
    public static final int COLOR_CORE_SECONDARY = OverlayTheme.Impact.CORE_SECONDARY;

    /** Core glow color (light blue) */
    public static final int COLOR_CORE_GLOW = OverlayTheme.Impact.CORE_GLOW;

    /** Slash effect color */
    public static final int COLOR_SLASH = OverlayTheme.Impact.SLASH;

    /** Connection line color */
    public static final int COLOR_LINE = OverlayTheme.Impact.LINE;

    // ═══════════════════════════════════════════════════════════════════════════
    // GEOMETRY
    // ═══════════════════════════════════════════════════════════════════════════

    // --- Vortex ---
    /** Base radius of the vortex effect */
    public static final float VORTEX_BASE_RADIUS = 0.25f;

    /** Number of segments in the spiral */
    public static final int VORTEX_SPIRAL_SEGMENTS = 32;

    /** Number of spiral rotations */
    public static final float VORTEX_SPIRAL_ROTATIONS = 2.0f;

    /** Number of concentric rings */
    public static final int VORTEX_RING_COUNT = 3;

    /** Number of segments per ring */
    public static final int VORTEX_RING_SEGMENTS = 24;

    /** Number of rays from center */
    public static final int VORTEX_RAY_COUNT = 6;

    // --- Slash ---
    /** Total length of the slash arc */
    public static final float SLASH_LENGTH = 0.8f;

    /** Height of the slash arc */
    public static final float SLASH_HEIGHT = 0.4f;

    /** Number of segments in slash trail */
    public static final int SLASH_TRAIL_SEGMENTS = 24;

    /** Number of spark particles */
    public static final int SLASH_SPARK_COUNT = 6;

    // --- Lines ---
    /** Length of connection lines towards camera */
    public static final float LINE_LENGTH = 1.5f;

    /** Number of connection lines */
    public static final int LINE_COUNT = 3;

    /** Number of segments per line */
    public static final int LINE_SEGMENTS = 8;

    /** Starting offset from center */
    public static final float LINE_START_OFFSET = 0.1f;

    /** Side line length (scanner effect) */
    public static final float LINE_SIDE_LENGTH = 0.8f;

    // --- Glyph ---
    /** Base size of combat glyph */
    public static final float GLYPH_BASE_SIZE = 0.14f;

    /** Size of the cross pattern */
    public static final float GLYPH_CROSS_SIZE = 0.6f;

    /** Size of critical X pattern */
    public static final float GLYPH_CRIT_SIZE = 0.45f;

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════════

    /** Two times PI for full rotations */
    public static final float TWO_PI = (float) (Math.PI * 2);

    /** PI constant */
    public static final float PI = (float) Math.PI;

    /**
     * Extracts RGB components from an ARGB color.
     *
     * @param color ARGB color
     * @return float array [r, g, b] in 0-1 range
     */
    public static float[] extractRGB(int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new float[] { r, g, b };
    }

    /**
     * Applies intensity multiplier to alpha, clamping to 0-1.
     */
    public static float applyIntensity(float alpha, float intensity) {
        float scaled = alpha * intensity;
        if (scaled < 0f) return 0f;
        return Math.min(scaled, 1.0f);
    }
}
