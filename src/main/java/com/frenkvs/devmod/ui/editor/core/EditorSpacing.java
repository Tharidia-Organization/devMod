package com.frenkvs.devmod.ui.editor.core;

/**
 * Spacing tokens - ONLY use these values for padding/gap/margin.
 * Never use arbitrary pixel values.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#16-grid--spacing-system
 */
public final class EditorSpacing {
    private EditorSpacing() {}

    // Base unit
    public static final int UNIT = 4;

    // Named spacing tokens
    public static final int XS  = 4;   // Intra-component (icon-text)
    public static final int S   = 8;   // Component padding, small gaps
    public static final int M   = 12;  // Section padding, medium gaps
    public static final int L   = 16;  // Zone padding, large gaps
    public static final int XL  = 24;  // Panel margins

    // Semantic aliases
    public static final int COMPONENT_GAP = S;      // 8px between components
    public static final int SECTION_GAP = M;        // 12px between sections
    public static final int ROW_GAP = S;            // 8px between rows
    public static final int CONTENT_PADDING = S;    // 8px content area padding
    public static final int BUTTON_PADDING_H = S;   // 8px horizontal button padding
    public static final int BUTTON_PADDING_V = XS;  // 4px vertical button padding

    /** Validate value is on 4px grid */
    public static boolean isOnGrid(int value) {
        return value % UNIT == 0;
    }

    /** Snap value to nearest grid point */
    public static int snapToGrid(int value) {
        return ((value + 2) / UNIT) * UNIT;
    }
}
