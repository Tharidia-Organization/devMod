package com.devmod.ui.editor.core;

/**
 * Central editor constants derived from EDITOR_DESIGN_SYSTEM.md (Appendice A).
 * Provides shared layout and system limits.
 */
public final class EditorConstants {
    private EditorConstants() {}

    // Panel
    public static final int PANEL_WIDTH = 550;
    public static final int PANEL_HEIGHT = 420;

    // Zones
    public static final int HEADER_ROW_HEIGHT = 28;  // Single row height
    public static final int HEADER_HEIGHT = 56;      // 2-row header: controls row + tabs row
    public static final int FOOTER_HEIGHT = 60;
    public static final int LEFT_COLUMN_WIDTH = 140;
    public static final int CONTENT_WIDTH = 390;
    public static final int CONTENT_HEIGHT = 332;

    // Preview
    public static final int PREVIEW_SIZE = 130;
    public static final int PREVIEW_X = 5;
    public static final int PREVIEW_Y = 20;

    // Slots
    public static final int SLOT_SIZE = EditorDimensions.SLOT_SIZE;
    public static final int SLOT_GAP = 5;
    public static final int SLOT_AREA_Y = 170;
    public static final int SLOT_AREA_HEIGHT = 70;

    // Item Info
    public static final int INFO_PANEL_Y = 260;
    public static final int INFO_PANEL_HEIGHT = 100;

    // Left column layout
    public static final int LEFT_COLUMN_PADDING = 5;
    public static final int ARMOR_CARD_HEIGHT = 46;

    // Tabs
    public static final int TAB_WIDTH = EditorDimensions.TAB_MIN_WIDTH;
    public static final int TAB_HEIGHT = EditorDimensions.TAB_HEIGHT;
    public static final int TAB_GAP = EditorDimensions.TAB_GAP;

    // Buttons
    public static final int BTN_SMALL_WIDTH = 50;
    public static final int BTN_MEDIUM_WIDTH = 60;
    public static final int BTN_LARGE_WIDTH = 120;
    public static final int BTN_SMALL_HEIGHT = 22;
    public static final int BTN_LARGE_HEIGHT = 50;

    // Dialog
    public static final int DIALOG_WIDTH = 350;
    public static final int DIALOG_HEIGHT = 150;

    // Undo/History
    public static final int MAX_UNDO_STATES = 50;
    public static final int MAX_HISTORY_ENTRIES = 50;
}
