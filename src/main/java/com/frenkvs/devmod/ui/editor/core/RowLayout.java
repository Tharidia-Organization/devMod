package com.frenkvs.devmod.ui.editor.core;

/**
 * Helper for laying out components in a row with consistent spacing.
 * Mirrors EDITOR_DESIGN_SYSTEM.md Section 2.19.
 */
public final class RowLayout {
    private final int startX;
    private final int y;
    private final int gap;
    private int currentX;

    public RowLayout(int x, int y) {
        this(x, y, EditorSpacing.COMPONENT_GAP);
    }

    public RowLayout(int x, int y, int gap) {
        this.startX = ScaledCoord.alignTo4(x);
        this.y = ScaledCoord.alignTo4(y);
        this.gap = ScaledCoord.alignTo4(gap);
        this.currentX = this.startX;
    }

    /** Add a component width and return its X position. */
    public int add(int width) {
        int x = currentX;
        currentX += ScaledCoord.alignTo4(width) + gap;
        return x;
    }

    /** Add flexible space. */
    public void addSpace(int space) {
        currentX += ScaledCoord.alignTo4(space);
    }

    public int getX() { return currentX; }
    public int getY() { return y; }
    public int getWidth() { return currentX - startX - gap; }
}
