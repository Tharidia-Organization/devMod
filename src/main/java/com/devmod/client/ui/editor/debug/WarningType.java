package com.devmod.client.ui.editor.debug;

import com.devmod.client.ui.editor.core.DesignTokens;

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
    OVERFLOW("⚠ OVERFLOW", DesignTokens.DebugOverlay.WARNING),

    /**
     * Text was truncated to fit available width.
     */
    TRUNCATED("✂ TRUNCATED", DesignTokens.DebugOverlay.WARNING_TRUNCATED),

    /**
     * Component coordinates are not aligned to the 4px grid.
     */
    MISALIGNED("⊠ MISALIGNED", DesignTokens.DebugOverlay.WARNING_MISALIGNED),

    /**
     * Component is rendered outside the visible viewport.
     */
    OUT_OF_VIEWPORT("◐ OUT OF VIEW", DesignTokens.DebugOverlay.WARNING_OUT_OF_VIEW);

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
