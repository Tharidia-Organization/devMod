package com.frenkvs.devmod.ui.editor.core;

/**
 * Helper for laying out components in a row with consistent spacing.
 * Uses {@link ScaledSpacing} for scaled gap values and {@link ScaledCoord#alignTo4(int)}
 * for grid alignment.
 *
 * @see ScaledSpacing#componentGap()
 * @see docs/editor-design-system/12-grid-spacing.md
 */
public final class RowLayout {
    private final int startX;
    private final int y;
    private final int gap;
    private int currentX;

    /**
     * Creates a row layout with scaled default component gap.
     * @param x starting X position (will be aligned to 4px grid)
     * @param y Y position (will be aligned to 4px grid)
     */
    public RowLayout(int x, int y) {
        this(x, y, ScaledSpacing.componentGap());
    }

    /**
     * Creates a row layout with custom gap.
     * @param x starting X position (will be aligned to 4px grid)
     * @param y Y position (will be aligned to 4px grid)
     * @param gap gap between components (will be aligned to 4px grid)
     */
    public RowLayout(int x, int y, int gap) {
        this.startX = ScaledCoord.alignTo4(x);
        this.y = ScaledCoord.alignTo4(y);
        this.gap = ScaledCoord.alignTo4(gap);
        this.currentX = this.startX;
    }

    /**
     * Add a component width and return its X position.
     * @param width component width (will be aligned to 4px grid)
     * @return X position for this component
     */
    public int add(int width) {
        int x = currentX;
        currentX += ScaledCoord.alignTo4(width) + gap;
        return x;
    }

    /**
     * Add flexible space (for right-aligned components).
     * @param space space to add (will be aligned to 4px grid)
     */
    public void addSpace(int space) {
        currentX += ScaledCoord.alignTo4(space);
    }

    /** @return current X position */
    public int getX() { return currentX; }

    /** @return Y position (constant for row) */
    public int getY() { return y; }

    /** @return total width used so far (excluding trailing gap) */
    public int getWidth() { return currentX - startX - gap; }
}
