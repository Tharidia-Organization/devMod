package com.devmod.area.aesthetic;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * BIBBIA ESTETICA - REGOLA 4: GUI LAYOUT STANDARDS
 *
 * Dimensioni e layout fissi per la GUI Area Builder.
 * Questi valori definiscono l'identità visiva delle GUI del sistema.
 */
public final class AreaBuilderGuiConstants {

    private AreaBuilderGuiConstants() {}

    // ============================================================================
    // DIMENSIONI FINESTRA
    // ============================================================================

    /** Width of the Area Builder GUI */
    public static final int GUI_WIDTH = 340;

    /** Height of the Area Builder GUI */
    public static final int GUI_HEIGHT = 240;

    // ============================================================================
    // TAB BAR
    // ============================================================================

    /** Height of each tab button */
    public static final int TAB_HEIGHT = 24;

    /** Width of each tab button */
    public static final int TAB_WIDTH = 64;

    /** Spacing between tab buttons */
    public static final int TAB_SPACING = 2;

    /** Number of tabs in the Area Builder */
    public static final int TAB_COUNT = 5;

    // ============================================================================
    // CONTENT AREA
    // ============================================================================

    /** Padding around content area */
    public static final int CONTENT_PADDING = 12;

    /** Top position of content area (after tabs) */
    public static final int CONTENT_TOP = TAB_HEIGHT + 8;

    /** Width of content area (computed at runtime) */
    public static int getContentWidth() {
        return GUI_WIDTH - (CONTENT_PADDING * 2) - PREVIEW_SIZE - PREVIEW_MARGIN;
    }

    /** Height of content area (computed at runtime) */
    public static int getContentHeight() {
        return GUI_HEIGHT - CONTENT_TOP - ACTION_BAR_HEIGHT - CONTENT_PADDING;
    }

    // ============================================================================
    // SLIDERS
    // ============================================================================

    /** Width of slider widgets */
    public static final int SLIDER_WIDTH = 200;

    /** Height of slider widgets */
    public static final int SLIDER_HEIGHT = 20;

    /** Vertical spacing between sliders */
    public static final int SLIDER_SPACING = 8;

    /** Width of slider label area */
    public static final int SLIDER_LABEL_WIDTH = 80;

    // ============================================================================
    // BUTTONS
    // ============================================================================

    /** Width of standard buttons */
    public static final int BUTTON_WIDTH = 100;

    /** Height of standard buttons */
    public static final int BUTTON_HEIGHT = 20;

    /** Horizontal spacing between buttons */
    public static final int BUTTON_SPACING = 8;

    /** Width of small buttons (toggles) */
    public static final int SMALL_BUTTON_WIDTH = 60;

    /** Height of small buttons */
    public static final int SMALL_BUTTON_HEIGHT = 16;

    // ============================================================================
    // FIELDS
    // ============================================================================

    /** Height of input fields */
    public static final int FIELD_HEIGHT = 18;

    // ============================================================================
    // PREVIEW PANEL
    // ============================================================================

    /** Size of preview panel (square) */
    public static final int PREVIEW_SIZE = 100;

    /** Margin around preview panel */
    public static final int PREVIEW_MARGIN = 8;

    /** X offset for preview panel from right edge */
    public static final int PREVIEW_X_OFFSET = CONTENT_PADDING;

    // ============================================================================
    // ACTION BAR
    // ============================================================================

    /** Height of bottom action bar */
    public static final int ACTION_BAR_HEIGHT = 36;

    /** Padding inside action bar */
    public static final int ACTION_BAR_PADDING = 8;

    // ============================================================================
    // COLORS (from DesignTokens.AreaBuilder)
    // ============================================================================

    /** Background color (dark) */
    public static final int COLOR_BACKGROUND = DesignTokens.AreaBuilder.BACKGROUND;

    /** Panel background color */
    public static final int COLOR_PANEL = DesignTokens.AreaBuilder.PANEL;

    /** Border color */
    public static final int COLOR_BORDER = DesignTokens.AreaBuilder.BORDER;

    /** Active tab color */
    public static final int COLOR_TAB_ACTIVE = DesignTokens.AreaBuilder.TAB_ACTIVE;

    /** Inactive tab color */
    public static final int COLOR_TAB_INACTIVE = DesignTokens.AreaBuilder.TAB_INACTIVE;

    /** Hover color */
    public static final int COLOR_HOVER = DesignTokens.AreaBuilder.HOVER;

    /** Text color primary */
    public static final int COLOR_TEXT_PRIMARY = DesignTokens.AreaBuilder.TEXT_PRIMARY;

    /** Text color secondary */
    public static final int COLOR_TEXT_SECONDARY = DesignTokens.AreaBuilder.TEXT_SECONDARY;

    /** Text color disabled */
    public static final int COLOR_TEXT_DISABLED = DesignTokens.AreaBuilder.TEXT_DISABLED;

    /** Toggle ON color */
    public static final int COLOR_TOGGLE_ON = DesignTokens.AreaBuilder.TOGGLE_ON;

    /** Toggle ON hover color */
    public static final int COLOR_TOGGLE_ON_HOVER = DesignTokens.AreaBuilder.TOGGLE_ON_HOVER;

    /** Toggle OFF color */
    public static final int COLOR_TOGGLE_OFF = DesignTokens.AreaBuilder.TOGGLE_OFF;

    /** Toggle OFF hover color */
    public static final int COLOR_TOGGLE_OFF_HOVER = DesignTokens.AreaBuilder.TOGGLE_OFF_HOVER;

    /** Selected border color */
    public static final int COLOR_SELECTED_BORDER = DesignTokens.AreaBuilder.SELECTED_BORDER;

    /** Scrollbar track color */
    public static final int COLOR_SCROLLBAR_TRACK = DesignTokens.AreaBuilder.SCROLLBAR_TRACK;

    /** Scrollbar thumb color */
    public static final int COLOR_SCROLLBAR_THUMB = DesignTokens.AreaBuilder.SCROLLBAR_THUMB;

    /** Text muted color */
    public static final int COLOR_TEXT_MUTED = DesignTokens.AreaBuilder.TEXT_MUTED;

    /** Warning/paused status color (orange) */
    public static final int COLOR_STATUS_WARNING = DesignTokens.AreaBuilder.STATUS_WARNING;

    /** Semi-transparent dark overlay for saving/loading states */
    public static final int COLOR_OVERLAY_DARK = DesignTokens.AreaBuilder.OVERLAY_DARK;

    // ============================================================================
    // LIST/GRID
    // ============================================================================

    /** Height of list items */
    public static final int LIST_ITEM_HEIGHT = 24;

    /** Spacing between list items */
    public static final int LIST_ITEM_SPACING = 2;

    /** Columns in preset/shape grid */
    public static final int GRID_COLUMNS = 2;

    /** Cell size in grid */
    public static final int GRID_CELL_SIZE = 80;

    /** Spacing between grid cells */
    public static final int GRID_SPACING = 4;

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Calculates the X position to center the GUI on screen.
     */
    public static int getCenteredX(int screenWidth) {
        return (screenWidth - GUI_WIDTH) / 2;
    }

    /**
     * Calculates the Y position to center the GUI on screen.
     */
    public static int getCenteredY(int screenHeight) {
        return (screenHeight - GUI_HEIGHT) / 2;
    }

    /**
     * Calculates the total width of tab bar.
     */
    public static int getTabBarWidth() {
        return (TAB_WIDTH + TAB_SPACING) * TAB_COUNT - TAB_SPACING;
    }

    /**
     * Calculates the X position for a specific tab.
     */
    public static int getTabX(int tabIndex, int centerX) {
        int tabBarStart = centerX - getTabBarWidth() / 2;
        return tabBarStart + tabIndex * (TAB_WIDTH + TAB_SPACING);
    }
}
