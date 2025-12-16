package com.frenkvs.devmod.ui.editor.core;

/**
 * Discrete UI scale calculator.
 * Supports: 1.0x, 1.25x, 1.5x, 2.0x and auto-detection.
 *
 * Mirrors EDITOR_DESIGN_SYSTEM.md Section 2.17.
 */
public final class EditorScaleCalculator {

    private static final float[] SCALE_OPTIONS = {1.0f, 1.25f, 1.5f, 2.0f};
    private static final int SCREEN_MARGIN = 24; // px

    private EditorScaleCalculator() {}

    /** Calculate auto scale based on available screen size. */
    public static float calculateAutoScale(int screenWidth, int screenHeight) {
        float maxScale = SCALE_OPTIONS[0];
        for (float scale : SCALE_OPTIONS) {
            int scaledWidth = Math.round(UIConstants.PanelDimensions.PANEL_WIDTH * scale) + SCREEN_MARGIN * 2;
            int scaledHeight = Math.round(UIConstants.PanelDimensions.PANEL_HEIGHT * scale) + SCREEN_MARGIN * 2;
            if (scaledWidth <= screenWidth && scaledHeight <= screenHeight) {
                maxScale = scale;
            } else {
                break; // options are ordered
            }
        }
        return maxScale;
    }

    /**
     * Resolve effective scale from a config string ("auto", "1.0", etc.).
     * Falls back to auto on invalid input.
     */
    public static float getEffectiveScale(int screenWidth, int screenHeight, String configValue) {
        if (configValue == null || "auto".equalsIgnoreCase(configValue)) {
            return calculateAutoScale(screenWidth, screenHeight);
        }
        try {
            float requested = Float.parseFloat(configValue);
            for (float allowed : SCALE_OPTIONS) {
                if (Math.abs(requested - allowed) < 0.01f) {
                    return allowed;
                }
            }
        } catch (NumberFormatException ignored) {
            // fall through to auto
        }
        return calculateAutoScale(screenWidth, screenHeight);
    }

    /**
     * Result of fitting the scaled editor panel into the available screen area.
     * If the panel would overflow, width/height are clamped to screen minus margin,
     * signalling that content should provide extra scroll (header/footer remain fixed).
     */
    public record ScreenFitResult(
        int panelX,
        int panelY,
        int panelWidth,
        int panelHeight,
        boolean contentNeedsExtraScroll
    ) {}

    /**
     * Calculate panel placement after applying scale and clamp rules.
     * Header/footer are never shrunk; clamp occurs on the overall panel bounds.
     */
    public static ScreenFitResult calculateFit(int screenWidth, int screenHeight, float scale) {
        int scaledWidth = ScaledCoord.scaleDim(UIConstants.PanelDimensions.PANEL_WIDTH, scale);
        int scaledHeight = ScaledCoord.scaleDim(UIConstants.PanelDimensions.PANEL_HEIGHT, scale);

        int panelX = ScaledCoord.alignTo4((screenWidth - scaledWidth) / 2);
        int panelY = ScaledCoord.alignTo4((screenHeight - scaledHeight) / 2);

        boolean needsClamp = false;

        // Horizontal clamp
        if (scaledWidth + SCREEN_MARGIN * 2 > screenWidth) {
            scaledWidth = screenWidth - SCREEN_MARGIN * 2;
            panelX = SCREEN_MARGIN;
            needsClamp = true;
        }

        // Vertical clamp (content must add scroll if clamped)
        if (scaledHeight + SCREEN_MARGIN * 2 > screenHeight) {
            scaledHeight = screenHeight - SCREEN_MARGIN * 2;
            panelY = SCREEN_MARGIN;
            needsClamp = true;
        }

        // Align to grid for final bounds
        scaledWidth = ScaledCoord.alignTo4(scaledWidth);
        scaledHeight = ScaledCoord.alignTo4(scaledHeight);
        panelX = ScaledCoord.alignTo4(panelX);
        panelY = ScaledCoord.alignTo4(panelY);

        return new ScreenFitResult(panelX, panelY, scaledWidth, scaledHeight, needsClamp);
    }
}
