package com.frenkvs.devmod.ui.editor.core;

/**
 * Helper for laying out vertical sections with consistent spacing.
 * Mirrors EDITOR_DESIGN_SYSTEM.md Section 2.19.
 */
public final class SectionLayout {
    private final int x;
    private final int startY;
    private final int width;
    private int currentY;

    public SectionLayout(int x, int y, int width) {
        this.x = ScaledCoord.alignTo4(x);
        this.startY = ScaledCoord.alignTo4(y);
        this.width = ScaledCoord.alignTo4(width);
        this.currentY = this.startY;
    }

    /** Add a header row and return its Y position. */
    public int addHeader() {
        int y = currentY;
        currentY += EditorDimensions.SECTION_HEADER_HEIGHT;
        currentY += EditorSpacing.S; // padding after header
        return y;
    }

    /** Add a row of specified height and return its Y position. */
    public int addRow(int height) {
        int y = currentY;
        currentY += ScaledCoord.alignTo4(height);
        currentY += EditorSpacing.ROW_GAP;
        return y;
    }

    /** End current section and add section gap. */
    public void endSection() {
        currentY -= EditorSpacing.ROW_GAP; // remove last row gap
        currentY += EditorSpacing.SECTION_GAP;
    }

    public int getY() { return currentY; }
    public int getHeight() { return currentY - startY; }
    public int getContentX() { return x + EditorSpacing.CONTENT_PADDING; }
    public int getContentWidth() { return width - EditorSpacing.CONTENT_PADDING * 2; }
}
