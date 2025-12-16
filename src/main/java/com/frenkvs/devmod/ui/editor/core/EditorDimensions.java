package com.frenkvs.devmod.ui.editor.core;

/**
 * Standard component dimensions - all multiples of 4.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#16-grid--spacing-system
 */
public final class EditorDimensions {
    private EditorDimensions() {}

    // =========================================================================
    // BUTTONS
    // =========================================================================

    public static final int BTN_HEIGHT_SMALL = 20;   // 5 units
    public static final int BTN_HEIGHT_NORMAL = 24;  // 6 units
    public static final int BTN_HEIGHT_LARGE = 32;   // 8 units
    public static final int BTN_MIN_WIDTH = 48;      // 12 units

    // =========================================================================
    // INPUTS
    // =========================================================================

    public static final int INPUT_HEIGHT = 20;       // 5 units
    public static final int INPUT_MIN_WIDTH = 60;    // 15 units

    // =========================================================================
    // SLIDERS
    // =========================================================================

    public static final int SLIDER_HEIGHT = 20;      // 5 units
    public static final int SLIDER_TRACK_HEIGHT = 4; // 1 unit
    public static final int SLIDER_THUMB_SIZE = 12;  // 3 units
    public static final int SLIDER_LABEL_WIDTH = 100;// 25 units

    // =========================================================================
    // TOGGLES
    // =========================================================================

    public static final int TOGGLE_WIDTH = 36;       // 9 units
    public static final int TOGGLE_HEIGHT = 20;      // 5 units

    // =========================================================================
    // TABS
    // =========================================================================

    public static final int TAB_HEIGHT = 24;         // 6 units
    public static final int TAB_MIN_WIDTH = 64;      // 16 units
    public static final int TAB_GAP = 4;             // 1 unit

    // =========================================================================
    // SECTIONS
    // =========================================================================

    public static final int SECTION_HEADER_HEIGHT = 24;  // 6 units
    public static final int SECTION_MIN_HEIGHT = 48;     // 12 units

    // =========================================================================
    // OTHER
    // =========================================================================

    public static final int SCROLLBAR_WIDTH = 8;     // 2 units
    public static final int ICON_SMALL = 12;         // 3 units
    public static final int ICON_NORMAL = 16;        // 4 units
    public static final int ICON_LARGE = 24;         // 6 units
    public static final int SLOT_SIZE = 32;          // 8 units, aligned to 4px grid
}
