package com.devmod.ui.editor.core;

/**
 * Represents a 2D coordinate that has been scaled and aligned to the 4px grid.
 * Also provides alignment utilities.
 * Mirrors EDITOR_DESIGN_SYSTEM.md Section A0.2.
 */
public record ScaledCoord(int x, int y) {

    private static float currentScale = 1.0f;

    /**
     * Aligns a value to the nearest 4px grid unit.
     * @param value The value to align.
     * @return The aligned value.
     */
    public static int alignTo4(int value) {
        return Math.round(value / 4.0f) * 4;
    }

    /**
     * Sets the current UI scale. Values are aligned to the 4px grid.
     * @param scale scale factor (discrete values recommended: 1.0, 1.25, 1.5, 2.0)
     */
    public static void setScale(float scale) {
        currentScale = scale;
    }

    /** @return the current UI scale. */
    public static float getScale() {
        return currentScale;
    }

    /**
     * Scales a value using the current scale and aligns it to the 4px grid.
     */
    public static int scale(int value) {
        return alignTo4(Math.round(value * currentScale));
    }

    /**
     * Scales a dimension using the current scale and aligns it to the 4px grid.
     */
    public static int scaleDim(int dimension) {
        return scale(dimension);
    }

    /**
     * Scales a dimension and aligns it to the 4px grid.
     * @param dimension The dimension to scale.
     * @param scale The scale factor.
     * @return The scaled and aligned dimension.
     */
    public static int scaleDim(int dimension, float scale) {
        return alignTo4(Math.round(dimension * scale));
    }

    public ScaledCoord add(int dx, int dy) {
        return new ScaledCoord(x + dx, y + dy);
    }

    public ScaledCoord add(ScaledCoord other) {
        return new ScaledCoord(x + other.x, y + other.y);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Pre-scaled constants for common panel dimensions
    // These use the current scale and align to 4px grid automatically.
    // Reference: docs/editor-design-system/07-ui-scaling.md
    // ─────────────────────────────────────────────────────────────────────────────

    /** @return Scaled panel width (base: 550px) */
    public static int panelWidth() {
        return scaleDim(EditorConstants.PANEL_WIDTH);
    }

    /** @return Scaled panel height (base: 420px) */
    public static int panelHeight() {
        return scaleDim(EditorConstants.PANEL_HEIGHT);
    }

    /** @return Scaled header height (base: 28px) */
    public static int headerHeight() {
        return scaleDim(EditorConstants.HEADER_HEIGHT);
    }

    /** @return Scaled footer height (base: 60px) */
    public static int footerHeight() {
        return scaleDim(EditorConstants.FOOTER_HEIGHT);
    }

    /** @return Scaled left column width (base: 140px) */
    public static int leftColumnWidth() {
        return scaleDim(EditorConstants.LEFT_COLUMN_WIDTH);
    }

    /** @return Scaled content area width (base: 390px) */
    public static int contentWidth() {
        return scaleDim(EditorConstants.CONTENT_WIDTH);
    }

    /** @return Scaled preview size (base: 100px) */
    public static int previewSize() {
        return scaleDim(EditorConstants.PREVIEW_SIZE);
    }
}
