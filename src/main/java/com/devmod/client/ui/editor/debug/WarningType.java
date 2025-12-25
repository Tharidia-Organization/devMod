package com.devmod.client.ui.editor.debug;

/**
 * Types of debug warnings that can be detected.
 *
 * @see DebugWarning
 * @see OverflowDetector
 * @see docs/editor-design-system/14-debug-overlay.md
 */
public enum WarningType {
    /**
     * Content exceeds its container bounds.
     */
    OVERFLOW("⚠ OVERFLOW", 0xFFFF4444),

    /**
     * Text was truncated to fit available width.
     */
    TRUNCATED("✂ TRUNCATED", 0xFFFFAA00),

    /**
     * Component coordinates are not aligned to the 4px grid.
     */
    MISALIGNED("⊠ MISALIGNED", 0xFFFF00FF),

    /**
     * Component is rendered outside the visible viewport.
     */
    OUT_OF_VIEWPORT("◐ OUT OF VIEW", 0xFF888888);

    private final String icon;
    private final int color;

    WarningType(String icon, int color) {
        this.icon = icon;
        this.color = color;
    }

    /**
     * Get the display icon for this warning type.
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Get the display color for this warning type.
     */
    public int getColor() {
        return color;
    }
}
