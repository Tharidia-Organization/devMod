package com.frenkvs.devmod.ui.radial.config;

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
    // LAYOUT RATIOS
    // ================================================================

    /** Close button size as ratio of centerButtonRadius */
    public static final float CLOSE_BUTTON_RATIO = 0.4f;

    /** Macro hub extends this many pixels beyond center button */
    public static final int MACRO_HUB_OFFSET = 8;

    /** Favorites ring offset from inner radius (pixels) */
    public static final int FAVORITES_OFFSET = 15;

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

    /** Categories per macro-category */
    public static final int CATEGORIES_PER_MACRO = 6;

    /** Number of macro-categories */
    public static final int MACRO_COUNT = 4;

    /** Total category capacity (MACRO_COUNT * CATEGORIES_PER_MACRO) */
    public static final int TOTAL_CATEGORIES = MACRO_COUNT * CATEGORIES_PER_MACRO;

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

    /** Default unselected segment color */
    public static final int COLOR_UNSELECTED_BG = 0xDD1a1a30;

    /** Default border color */
    public static final int COLOR_BORDER = 0xFF404060;

    /** Default divider color */
    public static final int COLOR_DIVIDER = 0xFF505070;

    /** Default inner ring border color */
    public static final int COLOR_INNER_RING = 0xFF303050;

    /** Close button hover background */
    public static final int COLOR_CLOSE_HOVER = 0xFF453545;

    /** Close button normal background */
    public static final int COLOR_CLOSE_NORMAL = 0xF0252530;

    /** Close button hover border */
    public static final int COLOR_CLOSE_BORDER_HOVER = 0xFFFF6666;

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

    /** Eighth of circle (45 degrees) in radians */
    public static final double QUARTER_PI = Math.PI / 4;

    /** Starting angle offset for category ring (top of screen) */
    public static final double CATEGORY_START_OFFSET = -HALF_PI;

    /** Starting angle offset for macro hub (top-left quadrant) */
    public static final double MACRO_START_OFFSET = -HALF_PI - QUARTER_PI;

    /** Segment angle for macro-categories (4 segments = PI/2 each) */
    public static final double MACRO_SEGMENT_ANGLE = HALF_PI;

    // ================================================================
    // TIMING
    // ================================================================

    /** Help text fade-in duration (milliseconds) */
    public static final long HELP_FADE_DURATION_MS = 200;

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
}
