package com.frenkvs.devmod.ui.editor.debug;

/**
 * Represents a debug warning for rendering issues.
 *
 * @param type      The type of warning
 * @param component The component name that generated the warning
 * @param message   Human-readable description of the issue
 * @param x         X position of the affected area
 * @param y         Y position of the affected area
 * @param width     Width of the affected area
 * @param height    Height of the affected area
 *
 * @see docs/editor-design-system/14-debug-overlay.md
 */
public record DebugWarning(
    WarningType type,
    String component,
    String message,
    int x, int y, int width, int height
) {
    /**
     * Create a warning with bounds from a Bounds record.
     */
    public static DebugWarning of(WarningType type, String component, String message,
                                   com.frenkvs.devmod.ui.editor.core.Bounds bounds) {
        return new DebugWarning(type, component, message,
            bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    /**
     * Check if a point is within the warning area.
     */
    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /**
     * Get the right edge of the affected area.
     */
    public int right() {
        return x + width;
    }

    /**
     * Get the bottom edge of the affected area.
     */
    public int bottom() {
        return y + height;
    }
}
