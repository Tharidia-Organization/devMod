package com.devmod.client.ui.editor.core;

/**
 * Helper for laying out vertical sections with consistent spacing.
 * Uses {@link ScaledSpacing} for scaled spacing values and {@link ScaledCoord#alignTo4(int)}
 * for grid alignment.
 *
 * @see ScaledSpacing#sectionGap()
 * @see ScaledSpacing#rowGap()
 * @see docs/editor-design-system/12-grid-spacing.md
 */
public final class SectionLayout {
    private final int x;
    private final int startY;
    private final int width;
    private int currentY;

    /**
     * Creates a section layout.
     * @param x X position (will be aligned to 4px grid)
     * @param y starting Y position (will be aligned to 4px grid)
     * @param width section width (will be aligned to 4px grid)
     */
    public SectionLayout(int x, int y, int width) {
        this.x = ScaledCoord.alignTo4(x);
        this.startY = ScaledCoord.alignTo4(y);
        this.width = ScaledCoord.alignTo4(width);
        this.currentY = this.startY;
    }

    /**
     * Add a header row and return its Y position.
     * Uses scaled header height and padding.
     * @return Y position for the header
     */
    public int addHeader() {
        int y = currentY;
        currentY += ScaledCoord.scale(EditorDimensions.SECTION_HEADER_HEIGHT);
        currentY += ScaledSpacing.s(); // padding after header
        return y;
    }

    /**
     * Add a row of specified height and return its Y position.
     * @param height row height (will be aligned to 4px grid)
     * @return Y position for this row
     */
    public int addRow(int height) {
        int y = currentY;
        currentY += ScaledCoord.alignTo4(height);
        currentY += ScaledSpacing.rowGap();
        return y;
    }

    /**
     * End current section and add section gap.
     * Removes the trailing row gap and adds section gap instead.
     */
    public void endSection() {
        currentY -= ScaledSpacing.rowGap(); // remove last row gap
        currentY += ScaledSpacing.sectionGap();
    }

    /**
     * Returns current Y position.
     */
    public int getY() { return currentY; }

    /**
     * Returns total height used so far.
     */
    public int getHeight() { return currentY - startY; }

    /**
     * Returns content X position (with scaled padding).
     */
    public int getContentX() { return x + ScaledSpacing.contentPadding(); }

    /**
     * Returns content width (minus scaled padding on both sides).
     */
    public int getContentWidth() { return width - ScaledSpacing.contentPadding() * 2; }
}
