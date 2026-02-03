package com.devmod.area.aesthetic;

import com.devmod.client.ui.core.UIScaleManager;
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

    /** Width of each tab button - sized to fit longest label "Dimensions" */
    public static final int TAB_WIDTH = 80;

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
    // WIDGET LAYOUT (standardized across all widgets)
    // ============================================================================

    /** Standard row height for toggles and options */
    public static final int ROW_HEIGHT = 24;

    /** Standard toggle button width */
    public static final int TOGGLE_WIDTH = 40;

    /** Standard option button height (smaller buttons) */
    public static final int OPTION_BUTTON_HEIGHT = 18;

    /** Style button width (for wall styles, presets, etc.) */
    public static final int STYLE_BUTTON_WIDTH = 70;

    /** Size button width (for grid sizes, alignments) */
    public static final int SIZE_BUTTON_WIDTH = 80;

    /** Gap between title and content */
    public static final int TITLE_GAP = 16;

    /** Gap between sections */
    public static final int SECTION_GAP = 14;

    /** Gap between blocks of options */
    public static final int BLOCK_GAP = 8;

    // ============================================================================
    // LIST/GRID
    // ============================================================================

    /** Height of list items */
    public static final int LIST_ITEM_HEIGHT = 24;

    /** Spacing between list items */
    public static final int LIST_ITEM_SPACING = 2;

    /** Columns in preset/shape grid */
    public static final int GRID_COLUMNS = 2;

    /** Cell size in grid - sized to fit shape names like "Rectangular" */
    public static final int GRID_CELL_SIZE = 90;

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

    // ============================================================================
    // SCALED GETTERS (for UI responsiveness)
    // ============================================================================

    /** Scaled tab height */
    public static int scaledTabHeight() {
        return UIScaleManager.scale(TAB_HEIGHT);
    }

    /** Minimum tab width to prevent over-truncation */
    private static final int TAB_WIDTH_MIN = 60;

    /** Scaled tab width with minimum */
    public static int scaledTabWidth() {
        return Math.max(TAB_WIDTH_MIN, UIScaleManager.scale(TAB_WIDTH));
    }

    /** Scaled tab spacing */
    public static int scaledTabSpacing() {
        return UIScaleManager.scale(TAB_SPACING);
    }

    /** Scaled content padding */
    public static int scaledContentPadding() {
        return UIScaleManager.scale(CONTENT_PADDING);
    }

    /** Scaled button width */
    public static int scaledButtonWidth() {
        return UIScaleManager.scale(BUTTON_WIDTH);
    }

    /** Scaled button height */
    public static int scaledButtonHeight() {
        return UIScaleManager.scale(BUTTON_HEIGHT);
    }

    /** Scaled button spacing */
    public static int scaledButtonSpacing() {
        return UIScaleManager.scale(BUTTON_SPACING);
    }

    /** Scaled field height */
    public static int scaledFieldHeight() {
        return UIScaleManager.scale(FIELD_HEIGHT);
    }

    /** Scaled action bar height */
    public static int scaledActionBarHeight() {
        return UIScaleManager.scale(ACTION_BAR_HEIGHT);
    }

    /** Scaled tab bar width */
    public static int scaledTabBarWidth() {
        return (scaledTabWidth() + scaledTabSpacing()) * TAB_COUNT - scaledTabSpacing();
    }

    /** Scaled list item height */
    public static int scaledListItemHeight() {
        return UIScaleManager.scale(LIST_ITEM_HEIGHT);
    }

    /** Scaled list item spacing */
    public static int scaledListItemSpacing() {
        return UIScaleManager.scale(LIST_ITEM_SPACING);
    }

    /** Minimum grid cell size to prevent over-truncation */
    private static final int GRID_CELL_SIZE_MIN = 60;

    /** Scaled grid cell size with minimum */
    public static int scaledGridCellSize() {
        return Math.max(GRID_CELL_SIZE_MIN, UIScaleManager.scale(GRID_CELL_SIZE));
    }

    /** Scaled grid spacing */
    public static int scaledGridSpacing() {
        return UIScaleManager.scale(GRID_SPACING);
    }

    /** Scaled row height */
    public static int scaledRowHeight() {
        return UIScaleManager.scale(ROW_HEIGHT);
    }

    /** Scaled toggle width */
    public static int scaledToggleWidth() {
        return UIScaleManager.scale(TOGGLE_WIDTH);
    }

    /** Scaled option button height */
    public static int scaledOptionButtonHeight() {
        return UIScaleManager.scale(OPTION_BUTTON_HEIGHT);
    }

    /** Scaled style button width */
    public static int scaledStyleButtonWidth() {
        return UIScaleManager.scale(STYLE_BUTTON_WIDTH);
    }

    /** Scaled size button width */
    public static int scaledSizeButtonWidth() {
        return UIScaleManager.scale(SIZE_BUTTON_WIDTH);
    }

    /** Scaled title gap */
    public static int scaledTitleGap() {
        return UIScaleManager.scale(TITLE_GAP);
    }

    /** Scaled section gap */
    public static int scaledSectionGap() {
        return UIScaleManager.scale(SECTION_GAP);
    }

    /** Scaled block gap */
    public static int scaledBlockGap() {
        return UIScaleManager.scale(BLOCK_GAP);
    }

    /**
     * Calculates how many buttons fit in a row for a given button width.
     * @param availableWidth the total available width
     * @param buttonWidth the width of each button
     * @param spacing the spacing between buttons
     * @return the number of buttons that fit in one row
     */
    public static int buttonsPerRow(int availableWidth, int buttonWidth, int spacing) {
        if (buttonWidth + spacing <= 0) return 1;
        return Math.max(1, (availableWidth + spacing) / (buttonWidth + spacing));
    }
}
