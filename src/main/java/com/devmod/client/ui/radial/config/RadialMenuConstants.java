package com.devmod.client.ui.radial.config;

/**
 * Named constants for the Radial Menu system.
 * Centralizes all magic numbers for maintainability and documentation.
 */
public final class RadialMenuConstants {
    private RadialMenuConstants() {
        // Utility class - no instantiation
    }

    // ================================================================
    // ANIMATION SPEEDS
    // ================================================================

    /** Speed of macro-category cross-fade transition (0-1 per tick increment) */
    public static final float TRANSITION_SPEED = 0.12f;

    /** Speed of morph/selection animation */
    public static final float MORPH_SPEED = 0.08f;

    /** Default linear interpolation factor for smooth animations */
    public static final float LERP_FACTOR = 0.15f;

    /** Speed for open animation */
    public static final float OPEN_ANIM_SPEED = 0.15f;

    /** Speed for close animation */
    public static final float CLOSE_ANIM_SPEED = 0.2f;

    /** Hover animation speed (increasing) */
    public static final float HOVER_ANIM_IN = 0.1f;

    /** Hover animation speed (decreasing) */
    public static final float HOVER_ANIM_OUT = 0.05f;

    /** Category selection animation lerp factor */
    public static final float CATEGORY_ANIM_LERP = 0.2f;

    /** Item selection animation lerp factor */
    public static final float ITEM_ANIM_LERP = 0.2f;

    /** Search box animation lerp factor */
    public static final float SEARCH_BOX_LERP = 0.15f;

    // ================================================================
    // ANIMATION TIMING
    // ================================================================

    /** Time scale factor for per-tick animation delta */
    public static final float ANIMATION_TIME_SCALE = 0.05f;

    /** Pulse phase speed */
    public static final float PULSE_PHASE_SPEED = 3f;

    /** Wave phase speed */
    public static final float WAVE_PHASE_SPEED = 2f;

    /** Pulse function phase multiplier */
    public static final float PULSE_PHASE_MULTIPLIER = 2f;

    /** Threshold to consider menu fully closed */
    public static final float FULLY_CLOSED_THRESHOLD = 0.05f;

    // ================================================================
    // LAYOUT RATIOS
    // ================================================================

    /** Close button size as ratio of centerButtonRadius */
    public static final float CLOSE_BUTTON_RATIO = 0.4f;

    /** Macro hub extends this many pixels beyond center button */
    public static final int MACRO_HUB_OFFSET = 8;

    /** Favorites ring offset from inner radius (pixels) */
    public static final int FAVORITES_OFFSET = 15;

    // ================================================================
    // HUB LAYOUT
    // ================================================================

    /** Hover animation threshold for center close button */
    public static final float CENTER_HOVER_THRESHOLD = 0.5f;

    /** Default ring border thickness (pixels) */
    public static final int RING_BORDER_THICKNESS = 2;

    /** Default border width for unselected segments/items */
    public static final int BORDER_WIDTH_DEFAULT = 2;

    /** Border width for selected segments/items */
    public static final int BORDER_WIDTH_SELECTED = 3;

    /** Macro icon text vertical offset (pixels) */
    public static final int MACRO_ICON_TEXT_OFFSET_Y = -4;

    /** Center icon text vertical offset (pixels) */
    public static final int CENTER_ICON_TEXT_OFFSET_Y = -3;

    // ================================================================
    // CATEGORY RING LAYOUT
    // ================================================================

    /** Category icon vertical offset (pixels) */
    public static final int CATEGORY_ICON_OFFSET_Y = -8;

    /** Category label vertical offset (pixels) */
    public static final int CATEGORY_LABEL_OFFSET_Y = 6;

    /** Category badge X offset (pixels) */
    public static final int CATEGORY_BADGE_OFFSET_X = 20;

    /** Category badge Y offset (pixels) */
    public static final int CATEGORY_BADGE_OFFSET_Y = -14;

    /** Category icon ItemStack X offset (pixels) */
    public static final int CATEGORY_ITEMSTACK_OFFSET_X = -8;

    /** Category icon ItemStack Y offset (pixels) */
    public static final int CATEGORY_ITEMSTACK_OFFSET_Y = -4;

    /** Category glow radius (pixels) */
    public static final int CATEGORY_GLOW_RADIUS = 60;

    /** Category glow max alpha (0-255) */
    public static final int CATEGORY_GLOW_ALPHA = 0x40;

    // ================================================================
    // CATEGORY BADGE
    // ================================================================

    /** Badge pulse base value */
    public static final float BADGE_PULSE_BASE = 0.8f;

    /** Badge pulse variation */
    public static final float BADGE_PULSE_VARIATION = 0.2f;

    /** Badge pulse speed multiplier */
    public static final float BADGE_PULSE_SPEED = 2f;

    /** Badge highlight blend factor */
    public static final float BADGE_BLEND_FACTOR = 0.3f;

    /** Badge background color */
    public static final int BADGE_BG_COLOR = 0xDD000000;

    /** Badge half width (pixels) */
    public static final int BADGE_HALF_WIDTH = 6;

    /** Badge top offset (pixels) */
    public static final int BADGE_TOP_OFFSET = -4;

    /** Badge bottom offset (pixels) */
    public static final int BADGE_BOTTOM_OFFSET = 6;

    /** Badge text Y offset (pixels) */
    public static final int BADGE_TEXT_OFFSET_Y = -2;

    // ================================================================
    // ITEM RING & ITEMS
    // ================================================================

    /** Item ring offset beyond outer radius (pixels) */
    public static final int ITEM_RING_OFFSET = 55;

    /** Base size for item bubbles (pixels) */
    public static final int ITEM_BASE_SIZE = 34;

    /** Item position expansion on hover (pixels) */
    public static final int ITEM_HOVER_OFFSET = 6;

    /** Item size expansion on hover (pixels) */
    public static final int ITEM_HOVER_SIZE_BONUS = 4;

    /** Item active blend factor */
    public static final float ITEM_ACTIVE_BLEND = 0.25f;

    /** Item disabled blend factor */
    public static final float ITEM_DISABLED_BLEND = 0.55f;

    /** Item highlight blend factor */
    public static final float ITEM_HIGHLIGHT_BLEND = 0.3f;

    /** Item icon ItemStack X offset (pixels) */
    public static final int ITEM_ICON_STACK_OFFSET_X = -8;

    /** Item icon ItemStack Y offset (pixels) */
    public static final int ITEM_ICON_STACK_OFFSET_Y = -16;

    /** Item icon text Y offset (pixels) */
    public static final int ITEM_ICON_TEXT_OFFSET_Y = -12;

    /** Item name max width (pixels) */
    public static final int ITEM_NAME_MAX_WIDTH = 56;

    /** Item name minimum characters before truncation */
    public static final int ITEM_NAME_MIN_CHARS = 6;

    /** Item name Y offset (pixels) */
    public static final int ITEM_NAME_OFFSET_Y = 4;

    /** Item status Y offset (pixels) */
    public static final int ITEM_STATUS_OFFSET_Y = 16;

    /** Item inactive status color */
    public static final int ITEM_STATUS_INACTIVE_COLOR = 0xFF666666;

    // ================================================================
    // FAVORITES RING
    // ================================================================

    /** Favorite bubble base size (pixels) */
    public static final int FAVORITE_BASE_SIZE = 14;

    /** Favorite bubble size bonus on hover (pixels) */
    public static final int FAVORITE_SIZE_BONUS = 4;

    /** Favorite background color when selected */
    public static final int FAVORITE_BG_SELECTED = 0xDDFFD700;

    /** Favorite background color when unselected */
    public static final int FAVORITE_BG_UNSELECTED = 0x88FFD700;

    /** Favorite icon scale factor */
    public static final float FAVORITE_ICON_SCALE = 0.7f;

    /** Favorite icon ItemStack X offset (pixels) */
    public static final int FAVORITE_ICON_OFFSET_X = -8;

    /** Favorite icon ItemStack Y offset (pixels) */
    public static final int FAVORITE_ICON_OFFSET_Y = -8;

    /** Favorite star text Y offset (pixels) */
    public static final int FAVORITE_STAR_OFFSET_Y = -4;

    /** Favorite star color */
    public static final int FAVORITE_STAR_COLOR = 0xFFFFD700;

    // ================================================================
    // BACKGROUND & OVERLAYS
    // ================================================================

    /** Background alpha max (0-255) */
    public static final int BACKGROUND_ALPHA_MAX = 0xE0;

    /** Background color (RGB) */
    public static final int BACKGROUND_COLOR = 0x0D0D15;

    /** Minimum search animation threshold */
    public static final float SEARCH_ANIMATION_EPSILON = 0.01f;

    // ================================================================
    // SOUND & FEEDBACK
    // ================================================================

    /** Default sound volume */
    public static final float SOUND_VOLUME_DEFAULT = 1.0f;

    /** Volume for hover sounds */
    public static final float SOUND_VOLUME_HOVER = 0.2f;

    /** Volume for category change sounds */
    public static final float SOUND_VOLUME_CATEGORY_CHANGE = 0.25f;

    /** Pitch for removing a favorite */
    public static final float SOUND_PITCH_FAVORITE_REMOVE = 0.7f;

    /** Pitch for adding a favorite */
    public static final float SOUND_PITCH_FAVORITE_ADD = 1.3f;

    /** Pitch for next category */
    public static final float SOUND_PITCH_CATEGORY_NEXT = 1.05f;

    /** Pitch for previous category */
    public static final float SOUND_PITCH_CATEGORY_PREV = 0.95f;

    /** Pitch for hover highlight */
    public static final float SOUND_PITCH_HOVER = 1.2f;

    /** Pitch for category change */
    public static final float SOUND_PITCH_CATEGORY_CHANGE = 1.0f;

    /** Pitch for theme cycle */
    public static final float SOUND_PITCH_THEME_CYCLE = 1.2f;

    /** Pitch for macro switch */
    public static final float SOUND_PITCH_MACRO_SWITCH = 1.1f;

    /** Pitch for navigation to subcategory */
    public static final float SOUND_PITCH_NAVIGATE_TO = 1.1f;

    /** Pitch for navigation back */
    public static final float SOUND_PITCH_NAVIGATE_BACK = 0.9f;

    /** Pitch for toggle on */
    public static final float SOUND_PITCH_TOGGLE_ON = 1.2f;

    /** Pitch for toggle off */
    public static final float SOUND_PITCH_TOGGLE_OFF = 0.8f;

    /** Pitch for non-toggle actions */
    public static final float SOUND_PITCH_ACTION_DEFAULT = 1.0f;

    // ================================================================
    // RENDERING PRECISION (Tessellator segments)
    // ================================================================

    /** Number of segments for circle rendering */
    public static final int CIRCLE_SEGMENTS = 24;

    /** Number of segments for ring/annulus rendering */
    public static final int RING_SEGMENTS = 32;

    /** Number of segments per arc (category/macro segments) */
    public static final int ARC_SEGMENTS = 16;

    // ================================================================
    // ALPHA THRESHOLDS
    // ================================================================

    /** ItemStack rendering only when alpha > this value (0-255) */
    public static final int ITEMSTACK_ALPHA_THRESHOLD = 200;

    /** Badges visible only when category alpha > this value (0-1) */
    public static final float BADGE_ALPHA_THRESHOLD = 0.5f;

    // ================================================================
    // CAPACITY LIMITS
    // ================================================================

    /** Maximum number of search results displayed */
    public static final int MAX_SEARCH_RESULTS = 8;

    /** Maximum number of favorites allowed */
    public static final int MAX_FAVORITES = 8;

    /** Maximum items per category for animations */
    public static final int MAX_ITEMS_PER_CATEGORY = 10;

    /** Categories per macro-category */
    public static final int CATEGORIES_PER_MACRO = 6;

    /** Number of macro-categories */
    public static final int MACRO_COUNT = 6;

    // ================================================================
    // SEARCH SCORING WEIGHTS
    // ================================================================

    /** Score for exact prefix match in search */
    public static final int SEARCH_PREFIX_SCORE = 100;

    /** Score for substring match in search */
    public static final int SEARCH_SUBSTRING_SCORE = 50;

    /** Score for match in description */
    public static final int SEARCH_DESCRIPTION_SCORE = 25;

    /** Base score for fuzzy character match */
    public static final int SEARCH_FUZZY_BASE_SCORE = 10;

    // ================================================================
    // COLORS (Default theme values)
    // ================================================================

    /** Default background dark color */
    public static final int COLOR_BG_DARK = 0xF0202035;

    /** Default selected segment color */
    public static final int COLOR_SELECTED_BG = 0xEE252540;

    /** Macro hub selected base color */
    public static final int COLOR_MACRO_SELECTED_BASE = 0xFF252540;

    /** Default unselected segment color */
    public static final int COLOR_UNSELECTED_BG = 0xDD1a1a30;

    /** Default border color */
    public static final int COLOR_BORDER = 0xFF404060;

    /** Default divider color */
    public static final int COLOR_DIVIDER = 0xFF505070;

    /** Macro hover border base color */
    public static final int COLOR_MACRO_HOVER_BORDER = 0xFF606080;

    /** Default inner ring border color */
    public static final int COLOR_INNER_RING = 0xFF303050;

    /** Close button hover background */
    public static final int COLOR_CLOSE_HOVER = 0xFF453545;

    /** Close button normal background */
    public static final int COLOR_CLOSE_NORMAL = 0xF0252530;

    /** Close button hover border */
    public static final int COLOR_CLOSE_BORDER_HOVER = 0xFFFF6666;

    /** Center icon color for back indicator */
    public static final int COLOR_CENTER_ICON_BACK = 0xFF80AAFF;

    /** Text primary color */
    public static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;

    /** Text secondary color */
    public static final int COLOR_TEXT_SECONDARY = 0xFFBBBBCC;

    /** Inactive element color */
    public static final int COLOR_INACTIVE = 0xFFAAAAAA;

    // ================================================================
    // ANGLES (Radians)
    // ================================================================

    /** Full circle in radians */
    public static final double TWO_PI = Math.PI * 2;

    /** Quarter circle (90 degrees) in radians */
    public static final double HALF_PI = Math.PI / 2;

    /** Starting angle offset for category ring (top of screen) */
    public static final double CATEGORY_START_OFFSET = -HALF_PI;

    /** Segment angle for macro-categories */
    public static final double MACRO_SEGMENT_ANGLE = TWO_PI / MACRO_COUNT;

    /** Starting angle offset for macro hub (centered at top) */
    public static final double MACRO_START_OFFSET = -HALF_PI - (MACRO_SEGMENT_ANGLE / 2);

    // ================================================================
    // TIMING
    // ================================================================

    /** Help text fade-in duration (milliseconds) */
    public static final long HELP_FADE_DURATION_MS = 200;

    /** Long-press threshold for action details (milliseconds) */
    public static final long LONG_PRESS_DURATION_MS = 350;

    // ================================================================
    // SELECTION INDICES
    // ================================================================

    /** Value indicating no selection */
    public static final int NO_SELECTION = -1;

    // ================================================================
    // COLOR BLENDING
    // ================================================================

    /** Blend factor for selected macro background */
    public static final float MACRO_SELECTED_BLEND = 0.4f;

    /** Blend factor for hovered macro background */
    public static final float MACRO_HOVER_BLEND = 0.25f;

    /** Blend factor for selected category background */
    public static final float CATEGORY_SELECTED_BLEND = 0.25f;

    /** Blend factor for outer ring accent */
    public static final float OUTER_RING_BLEND = 0.3f;

    /** Blend factor for hovered border */
    public static final float BORDER_HOVER_BLEND = 0.5f;

    // ================================================================
    // TOOLTIP & SEARCH OVERLAY
    // ================================================================

    /** Tooltip background color */
    public static final int TOOLTIP_BG_COLOR = 0xF0101020;

    /** Tooltip padding around text */
    public static final int TOOLTIP_PADDING = 6;

    /** Tooltip text height assumption (pixels) */
    public static final int TOOLTIP_TEXT_HEIGHT = 10;

    /** Tooltip vertical offset from menu center */
    public static final int TOOLTIP_OFFSET_Y = 70;

    /** Tooltip border thickness (pixels) */
    public static final int TOOLTIP_BORDER_THICKNESS = 1;

    /** Search overlay max alpha */
    public static final int SEARCH_OVERLAY_ALPHA = 0x80;

    /** Search box width */
    public static final int SEARCH_BOX_WIDTH = 300;

    /** Search box height */
    public static final int SEARCH_BOX_HEIGHT = 30;

    /** Search box top Y */
    public static final int SEARCH_BOX_Y = 50;

    /** Search box border thickness */
    public static final int SEARCH_BOX_BORDER = 2;

    /** Search box background color */
    public static final int SEARCH_BOX_BG = 0xEE101020;

    /** Search box text X offset */
    public static final int SEARCH_BOX_TEXT_OFFSET_X = 10;

    /** Search box text Y offset */
    public static final int SEARCH_BOX_TEXT_OFFSET_Y = 10;

    /** Search cursor Y start */
    public static final int SEARCH_CURSOR_Y_START = 8;

    /** Search cursor Y end */
    public static final int SEARCH_CURSOR_Y_END = 22;

    /** Search cursor blink interval (milliseconds) */
    public static final int SEARCH_CURSOR_BLINK_MS = 500;

    /** Search cursor width (pixels) */
    public static final int SEARCH_CURSOR_WIDTH = 2;

    /** Search result background color */
    public static final int SEARCH_RESULT_BG = 0xCC101020;

    /** Search result row height */
    public static final int SEARCH_RESULT_HEIGHT = 25;

    /** Search results gap from search box (pixels) */
    public static final int SEARCH_RESULTS_TOP_GAP = 10;

    /** Search result vertical gap (row height + spacing) */
    public static final int SEARCH_RESULT_GAP = 28;

    /** Search result text X offset */
    public static final int SEARCH_RESULT_TEXT_OFFSET_X = 10;

    /** Search result text Y offset */
    public static final int SEARCH_RESULT_TEXT_OFFSET_Y = 8;

    /** Search result status text right offset (pixels) */
    public static final int SEARCH_RESULT_STATUS_OFFSET_X = 40;

    // ================================================================
    // HELP & INDICATORS
    // ================================================================

    /** Help text base alpha (0-255) */
    public static final int HELP_TEXT_ALPHA = 0xAA;

    /** Help text bottom margin (pixels) */
    public static final int HELP_TEXT_MARGIN_BOTTOM = 25;

    /** Breadcrumb X position */
    public static final int BREADCRUMB_X = 10;

    /** Breadcrumb Y position */
    public static final int BREADCRUMB_Y = 10;

    /** Breadcrumb text color */
    public static final int BREADCRUMB_COLOR = 0xFFFFFFFF;

    /** Edit mode background color */
    public static final int EDIT_MODE_BG_COLOR = 0xCC000000;

    /** Edit mode text color */
    public static final int EDIT_MODE_TEXT_COLOR = 0xFFFF4444;

    /** Edit mode horizontal padding (pixels) */
    public static final int EDIT_MODE_PADDING_X = 5;

    /** Edit mode background top Y (pixels) */
    public static final int EDIT_MODE_BG_TOP_Y = 5;

    /** Edit mode background bottom Y (pixels) */
    public static final int EDIT_MODE_BG_BOTTOM_Y = 20;

    /** Edit mode text Y (pixels) */
    public static final int EDIT_MODE_TEXT_Y = 8;

    /** Theme indicator total duration (milliseconds) */
    public static final long THEME_INDICATOR_DURATION_MS = 2000;

    /** Theme indicator fade start delay (milliseconds) */
    public static final long THEME_INDICATOR_FADE_START_MS = 1000;

    /** Theme indicator Y position (pixels) */
    public static final int THEME_INDICATOR_Y = 30;

    /** Theme indicator base color (RGB) */
    public static final int THEME_INDICATOR_COLOR = 0xFFFFFF;
}
