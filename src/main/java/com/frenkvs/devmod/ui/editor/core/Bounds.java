package com.frenkvs.devmod.ui.editor.core;

/**
 * Basic rectangle bounds with helpers.
 */
public record Bounds(int x, int y, int width, int height) {
    public static final Bounds EMPTY = new Bounds(0, 0, 0, 0);

    public int right() { return x + width; }
    public int bottom() { return y + height; }

    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
