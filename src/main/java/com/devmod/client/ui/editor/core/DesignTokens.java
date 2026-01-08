package com.devmod.client.ui.editor.core;

import javax.annotation.Nonnull;

import net.minecraft.ChatFormatting;

/**
 * Centralized Design Tokens following the UI Bible specification.
 * All UI rendering MUST use these tokens - no magic numbers allowed.
 *
 * <p>This class provides:
 * <ul>
 *   <li>Color tokens (backgrounds, surfaces, strokes, text, accents, semantics)</li>
 *   <li>Spacing tokens (grid-aligned, 4px base)</li>
 *   <li>Radius tokens for border-radius</li>
 *   <li>Stroke width tokens</li>
 *   <li>Elevation tokens (shadow/glow)</li>
 *   <li>Motion duration tokens</li>
 *   <li>Icon size tokens</li>
 * </ul>
 *
 * @see DesignTokens for legacy compatibility (delegates here)
 * @see ThemeManager for theme-aware color resolution
 */
@SuppressWarnings("SameNameButDifferent") // Nested Text classes are intentional for contextual grouping
public final class DesignTokens {

    private DesignTokens() {}

    // ===========================================================================
    // GRID SYSTEM
    // ===========================================================================

    /* Base grid unit (4px) - all dimensions should be multiples of this */
    public static final int GRID = 4;

    /* Snap a value to the nearest grid point */
    public static int snap(int value) {
        return Math.round(value / (float) GRID) * GRID;
    }

    /* Snap a value up to the next grid point */
    public static int snapUp(int value) {
        return ((value + GRID - 1) / GRID) * GRID;
    }

    /* Snap a value down to the previous grid point */
    public static int snapDown(int value) {
        return (value / GRID) * GRID;
    }

    /* Check if a value is on the grid */
    public static boolean isOnGrid(int value) {
        return value % GRID == 0;
    }

    // ===========================================================================
    // BASE PALETTE (raw colors)
    // ===========================================================================

    public static final class Palette {
        public static final int BACKGROUND = 0xFF0F131A;
        public static final int SURFACE = 0xFF151B24;
        public static final int SURFACE_ALT = 0xFF1B2432;
        public static final int PANEL = 0xFF242F40;
        public static final int PANEL_ELEVATED = 0xFF2D3A4E;
        public static final int OUTLINE = 0xFF34455E;
        public static final int SHADOW = 0xFF000000;
        public static final int SCRIM = 0xFF080B0F;
        public static final int TEXT_PRIMARY = 0xFFE7ECF4;
        public static final int TEXT_SECONDARY = 0xFFB9C3D4;
        public static final int ACCENT_TEAL = 0xFF39CDBE;
        public static final int ACCENT_AMBER = 0xFFF2A74B;
        public static final int ACCENT_BLUE = 0xFF4E9CFF;
        public static final int SUCCESS = 0xFF45D483;
        public static final int WARNING = 0xFFF2C14E;
        public static final int ERROR = 0xFFE06B5B;

        private Palette() {}
    }

    // ===========================================================================
    // COLOR TOKENS - BACKGROUNDS (5 levels)
    // ===========================================================================

    public static final class Bg {
        /* Darkest - scrim/overlay backdrop */
        public static final int LEVEL_0 = Palette.SCRIM;
        /* Screen background */
        public static final int LEVEL_1 = Palette.BACKGROUND;
        /* Panel background */
        public static final int LEVEL_2 = Palette.SURFACE;
        /* Card/section background */
        public static final int LEVEL_3 = Palette.SURFACE_ALT;
        /* Elevated element */
        public static final int LEVEL_4 = Palette.PANEL;

        /* Get background by level (0-4) */
        public static int level(int level) {
            return switch (level) {
                case 0 -> LEVEL_0;
                case 1 -> LEVEL_1;
                case 2 -> LEVEL_2;
                case 3 -> LEVEL_3;
                case 4 -> LEVEL_4;
                default -> LEVEL_2;
            };
        }

        private Bg() {}
    }

    // ===========================================================================
    // COLOR TOKENS - SURFACES (5 levels)
    // ===========================================================================

    public static final class Surface {
        /* Input fields, wells */
        public static final int LEVEL_0 = Palette.SURFACE;
        /* Default surface */
        public static final int LEVEL_1 = Palette.SURFACE_ALT;
        /* Hover state */
        public static final int LEVEL_2 = Palette.PANEL;
        /* Active/pressed */
        public static final int LEVEL_3 = Palette.PANEL_ELEVATED;
        /* Highlighted */
        public static final int LEVEL_4 = Palette.OUTLINE;

        /* Get surface by level (0-4) */
        public static int level(int level) {
            return switch (level) {
                case 0 -> LEVEL_0;
                case 1 -> LEVEL_1;
                case 2 -> LEVEL_2;
                case 3 -> LEVEL_3;
                case 4 -> LEVEL_4;
                default -> LEVEL_1;
            };
        }

        private Surface() {}
    }

    // ===========================================================================
    // COLOR TOKENS - STROKES (3 levels)
    // ===========================================================================

    public static final class Stroke {
        /* Subtle borders, dividers */
        public static final int MUTED = Palette.PANEL;
        /* Default borders */
        public static final int DEFAULT = Palette.OUTLINE;
        /* Emphasized borders */
        public static final int EMPHASIS = Palette.ACCENT_BLUE;

        private Stroke() {}
    }

    // ===========================================================================
    // COLOR TOKENS - TEXT
    // ===========================================================================

    public static final class Text {
        /* Main text, titles */
        public static final int PRIMARY = Palette.TEXT_PRIMARY;
        /* Labels, captions */
        public static final int SECONDARY = Palette.TEXT_SECONDARY;
        /* Hints, disabled */
        public static final int MUTED = Palette.TEXT_SECONDARY;
        /* Text on light backgrounds */
        public static final int INVERSE = Palette.BACKGROUND;
        /* Title text */
        public static final int TITLE = PRIMARY;
        /* Value text */
        public static final int VALUE = DesignTokens.Accent.PRIMARY;
        /* Formula/code text */
        public static final int FORMULA = Semantic.SUCCESS;
        /* Disabled text */
        public static final int DISABLED = Utility.DISABLED;
        /* Info text */
        public static final int INFO = Semantic.INFO;
        /* Warning text */
        public static final int WARNING = Semantic.WARNING;
        /* Pure white text */
        public static final int WHITE = PRIMARY;
        /* Accent text */
        public static final int ACCENT = DesignTokens.Accent.PRIMARY;

        public static int PRIMARY() { return ThemeManager.INSTANCE.textPrimary(); }
        public static int SECONDARY() { return ThemeManager.INSTANCE.textSecondary(); }
        public static int MUTED() { return ThemeManager.INSTANCE.textMuted(); }
        public static int INVERSE() { return INVERSE; }
        public static int TITLE() { return ThemeManager.INSTANCE.textTitle(); }
        public static int VALUE() { return ThemeManager.INSTANCE.current().textValue(); }
        public static int FORMULA() { return ThemeManager.INSTANCE.current().textFormula(); }
        public static int DISABLED() { return ThemeManager.INSTANCE.textDisabled(); }
        public static int INFO() { return ThemeManager.INSTANCE.info(); }
        public static int WARNING() { return ThemeManager.INSTANCE.warning(); }
        public static int WHITE() { return WHITE; }
        public static int ACCENT() { return ThemeManager.INSTANCE.accent(); }

        private Text() {}
    }

    // ===========================================================================
    // CHAT FORMATTING COLORS
    // ===========================================================================

    public static final class Chat {
        public static final @Nonnull ChatFormatting BLACK = ChatFormatting.BLACK;
        public static final @Nonnull ChatFormatting DARK_BLUE = ChatFormatting.DARK_BLUE;
        public static final @Nonnull ChatFormatting DARK_GREEN = ChatFormatting.DARK_GREEN;
        public static final @Nonnull ChatFormatting DARK_AQUA = ChatFormatting.DARK_AQUA;
        public static final @Nonnull ChatFormatting DARK_RED = ChatFormatting.DARK_RED;
        public static final @Nonnull ChatFormatting DARK_PURPLE = ChatFormatting.DARK_PURPLE;
        public static final @Nonnull ChatFormatting GOLD = ChatFormatting.GOLD;
        public static final @Nonnull ChatFormatting GRAY = ChatFormatting.GRAY;
        public static final @Nonnull ChatFormatting DARK_GRAY = ChatFormatting.DARK_GRAY;
        public static final @Nonnull ChatFormatting BLUE = ChatFormatting.BLUE;
        public static final @Nonnull ChatFormatting GREEN = ChatFormatting.GREEN;
        public static final @Nonnull ChatFormatting AQUA = ChatFormatting.AQUA;
        public static final @Nonnull ChatFormatting RED = ChatFormatting.RED;
        public static final @Nonnull ChatFormatting LIGHT_PURPLE = ChatFormatting.LIGHT_PURPLE;
        public static final @Nonnull ChatFormatting YELLOW = ChatFormatting.YELLOW;
        public static final @Nonnull ChatFormatting WHITE = ChatFormatting.WHITE;

        private Chat() {}
    }

    // ===========================================================================
    // COLOR TOKENS - ACCENTS
    // ===========================================================================

    public static final class Accent {
        /* Primary accent (cyan) */
        public static final int PRIMARY = Palette.ACCENT_TEAL;
        /* Secondary accent (amber) */
        public static final int SECONDARY = Palette.ACCENT_AMBER;
        /* Glow effect (25% alpha) */
        public static final int GLOW = withAlpha(PRIMARY, 0x40);
        /* Primary accent alias */
        public static final int CYAN = PRIMARY;
        /* Success accent */
        public static final int GREEN = Semantic.SUCCESS;
        /* Warning accent */
        public static final int ORANGE = Semantic.WARNING;
        /* Error accent */
        public static final int RED = Semantic.ERROR;
        /* Info accent */
        public static final int BLUE = Semantic.INFO;
        /* Special/rare accent */
        public static final int PURPLE = BLUE;
        /* Highlight accent */
        public static final int YELLOW = Semantic.WARNING;
        /* Gold accent */
        public static final int GOLD = SECONDARY;

        public static final int POSITIVE = GREEN;
        public static final int WARNING = ORANGE;
        public static final int NEGATIVE = RED;
        public static final int INFO = BLUE;

        public static int PRIMARY() { return ThemeManager.INSTANCE.accent(); }
        public static int SECONDARY() { return SECONDARY; }
        public static int CYAN() { return ThemeManager.INSTANCE.accent(); }
        public static int GREEN() { return ThemeManager.INSTANCE.success(); }
        public static int ORANGE() { return ThemeManager.INSTANCE.warning(); }
        public static int RED() { return ThemeManager.INSTANCE.error(); }
        public static int BLUE() { return ThemeManager.INSTANCE.info(); }
        public static int PURPLE() { return PURPLE; }
        public static int YELLOW() { return YELLOW; }
        public static int GOLD() { return GOLD; }
        public static int INFO() { return ThemeManager.INSTANCE.info(); }

        private Accent() {}
    }

    // ===========================================================================
    // COLOR TOKENS - SEMANTIC
    // ===========================================================================

    public static final class Semantic {
        /* Success states */
        public static final int SUCCESS = Palette.SUCCESS;
        /* Success background (muted) */
        public static final int SUCCESS_MUTED = withAlpha(SUCCESS, 0x40);

        /* Warning states */
        public static final int WARNING = Palette.WARNING;
        /* Warning background (muted) */
        public static final int WARNING_MUTED = withAlpha(WARNING, 0x40);

        /* Error states */
        public static final int ERROR = Palette.ERROR;
        /* Error background (muted) */
        public static final int ERROR_MUTED = withAlpha(ERROR, 0x40);

        /* Info states */
        public static final int INFO = Palette.ACCENT_BLUE;
        /* Info background (muted) */
        public static final int INFO_MUTED = withAlpha(INFO, 0x40);

        private Semantic() {}
    }

    // ===========================================================================
    // EDITOR THEME COLORS (theme-specific overrides)
    // ===========================================================================

    public static final class EditorTheme {
        public static final class Shared {
            public static final int DARKER_BACKGROUND = Bg.LEVEL_0;
            public static final int TEXT_VALUE = Accent.PRIMARY;
            public static final int TEXT_FORMULA = Semantic.SUCCESS;
            public static final int ACCENT_PRIMARY = Accent.PRIMARY;
            public static final int ACCENT_SUCCESS = Semantic.SUCCESS;
            public static final int ACCENT_WARNING = Semantic.WARNING;
            public static final int ACCENT_ERROR = Semantic.ERROR;
            public static final int ACCENT_INFO = Semantic.INFO;

            private Shared() {}
        }

        public static final class Dark {
            public static final int PANEL_BG = withAlpha(Bg.LEVEL_4, 0xE0);
            public static final int PANEL_BG_SOLID = Bg.LEVEL_4;
            public static final int INPUT_BG = Surface.LEVEL_0;
            public static final int HOVER_BG = Surface.LEVEL_1;
            public static final int ACTIVE_BG = Surface.LEVEL_2;
            public static final int HEADER_BG = Bg.LEVEL_4;
            public static final int CONTENT_BG = Surface.LEVEL_1;
            public static final int TAB_INACTIVE_BG = Surface.LEVEL_1;
            public static final int TAB_ACTIVE_BG = Surface.LEVEL_2;
            public static final int OVERLAY_BG = withAlpha(Palette.SHADOW, 0x80);

            public static final int BORDER_DEFAULT = Stroke.DEFAULT;
            public static final int BORDER_MUTED = Stroke.MUTED;
            public static final int BORDER_ACCENT = Accent.PRIMARY;
            public static final int BORDER_SEPARATOR = Stroke.DEFAULT;
            public static final int BORDER_HOVER = Semantic.INFO;

            public static final int TEXT_PRIMARY = Text.PRIMARY;
            public static final int TEXT_SECONDARY = Text.SECONDARY;
            public static final int TEXT_MUTED = Text.MUTED;
            public static final int TEXT_TITLE = Text.PRIMARY;
            public static final int TEXT_DISABLED = Text.MUTED;

            public static final int BUTTON_NORMAL = Surface.LEVEL_1;
            public static final int BUTTON_HOVER = Surface.LEVEL_2;
            public static final int BUTTON_PRESSED = Surface.LEVEL_0;
            public static final int BUTTON_DISABLED = Surface.LEVEL_0;

            public static final int SLIDER_TRACK = Surface.LEVEL_1;
            public static final int SLIDER_THUMB = Stroke.DEFAULT;
            public static final int SLIDER_THUMB_HOVER = Semantic.INFO;

            private Dark() {}
        }

        public static final class Light {
            public static final int PANEL_BG = Dark.PANEL_BG;
            public static final int PANEL_BG_SOLID = Dark.PANEL_BG_SOLID;
            public static final int INPUT_BG = Dark.INPUT_BG;
            public static final int HOVER_BG = Dark.HOVER_BG;
            public static final int ACTIVE_BG = Dark.ACTIVE_BG;
            public static final int HEADER_BG = Dark.HEADER_BG;
            public static final int CONTENT_BG = Dark.CONTENT_BG;
            public static final int TAB_INACTIVE_BG = Dark.TAB_INACTIVE_BG;
            public static final int TAB_ACTIVE_BG = Dark.TAB_ACTIVE_BG;
            public static final int OVERLAY_BG = Dark.OVERLAY_BG;

            public static final int BORDER_DEFAULT = Dark.BORDER_DEFAULT;
            public static final int BORDER_MUTED = Dark.BORDER_MUTED;
            public static final int BORDER_ACCENT = Dark.BORDER_ACCENT;
            public static final int BORDER_SEPARATOR = Dark.BORDER_SEPARATOR;
            public static final int BORDER_HOVER = Dark.BORDER_HOVER;

            public static final int TEXT_PRIMARY = Dark.TEXT_PRIMARY;
            public static final int TEXT_SECONDARY = Dark.TEXT_SECONDARY;
            public static final int TEXT_MUTED = Dark.TEXT_MUTED;
            public static final int TEXT_TITLE = Dark.TEXT_TITLE;
            public static final int TEXT_DISABLED = Dark.TEXT_DISABLED;

            public static final int ACCENT_PRIMARY = Shared.ACCENT_PRIMARY;
            public static final int ACCENT_SUCCESS = Shared.ACCENT_SUCCESS;
            public static final int ACCENT_WARNING = Shared.ACCENT_WARNING;
            public static final int ACCENT_ERROR = Shared.ACCENT_ERROR;
            public static final int ACCENT_INFO = Shared.ACCENT_INFO;

            public static final int BUTTON_NORMAL = Dark.BUTTON_NORMAL;
            public static final int BUTTON_HOVER = Dark.BUTTON_HOVER;
            public static final int BUTTON_PRESSED = Dark.BUTTON_PRESSED;
            public static final int BUTTON_DISABLED = Dark.BUTTON_DISABLED;

            public static final int SLIDER_TRACK = Dark.SLIDER_TRACK;
            public static final int SLIDER_THUMB = Dark.SLIDER_THUMB;
            public static final int SLIDER_THUMB_HOVER = Dark.SLIDER_THUMB_HOVER;

            private Light() {}
        }

        public static final class HighContrast {
            public static final int PANEL_BG = Dark.PANEL_BG;
            public static final int PANEL_BG_SOLID = Dark.PANEL_BG_SOLID;
            public static final int INPUT_BG = Dark.INPUT_BG;
            public static final int HOVER_BG = Dark.HOVER_BG;
            public static final int ACTIVE_BG = Dark.ACTIVE_BG;
            public static final int HEADER_BG = Dark.HEADER_BG;
            public static final int CONTENT_BG = Dark.CONTENT_BG;
            public static final int TAB_INACTIVE_BG = Dark.TAB_INACTIVE_BG;
            public static final int TAB_ACTIVE_BG = Dark.TAB_ACTIVE_BG;
            public static final int OVERLAY_BG = Dark.OVERLAY_BG;
            public static final int DARKER_BACKGROUND = Shared.DARKER_BACKGROUND;

            public static final int BORDER_DEFAULT = Dark.BORDER_DEFAULT;
            public static final int BORDER_MUTED = Dark.BORDER_MUTED;
            public static final int BORDER_ACCENT = Dark.BORDER_ACCENT;
            public static final int BORDER_SEPARATOR = Dark.BORDER_SEPARATOR;
            public static final int BORDER_HOVER = Dark.BORDER_HOVER;

            public static final int TEXT_PRIMARY = Dark.TEXT_PRIMARY;
            public static final int TEXT_SECONDARY = Dark.TEXT_SECONDARY;
            public static final int TEXT_MUTED = Dark.TEXT_MUTED;
            public static final int TEXT_TITLE = Dark.TEXT_TITLE;
            public static final int TEXT_DISABLED = Dark.TEXT_DISABLED;
            public static final int TEXT_VALUE = Shared.TEXT_VALUE;
            public static final int TEXT_FORMULA = Shared.TEXT_FORMULA;

            public static final int ACCENT_PRIMARY = Shared.ACCENT_PRIMARY;
            public static final int ACCENT_SUCCESS = Shared.ACCENT_SUCCESS;
            public static final int ACCENT_WARNING = Shared.ACCENT_WARNING;
            public static final int ACCENT_ERROR = Shared.ACCENT_ERROR;
            public static final int ACCENT_INFO = Shared.ACCENT_INFO;

            public static final int BUTTON_NORMAL = Dark.BUTTON_NORMAL;
            public static final int BUTTON_HOVER = Dark.BUTTON_HOVER;
            public static final int BUTTON_PRESSED = Dark.BUTTON_PRESSED;
            public static final int BUTTON_DISABLED = Dark.BUTTON_DISABLED;

            public static final int SLIDER_TRACK = Dark.SLIDER_TRACK;
            public static final int SLIDER_THUMB = Dark.SLIDER_THUMB;
            public static final int SLIDER_THUMB_HOVER = Dark.SLIDER_THUMB_HOVER;

            private HighContrast() {}
        }

        private EditorTheme() {}
    }

    // ===========================================================================
    // PRIMITIVE COLOR PALETTES
    // ===========================================================================

    /*
     * Purple color palette (P100-P900).
     */
    public static final class Purple {
        public static final int P100 = Accent.BLUE;
        public static final int P200 = Accent.BLUE;
        public static final int P300 = Accent.BLUE;
        public static final int P400 = Accent.BLUE;
        public static final int P500 = Accent.BLUE;
        public static final int P600 = Accent.BLUE;
        public static final int P700 = Accent.BLUE;
        public static final int P800 = Accent.BLUE;
        public static final int P900 = Accent.BLUE;

        private Purple() {}
    }

    /*
     * Orange color palette (O100-O900).
     */
    public static final class Orange {
        public static final int O100 = Accent.SECONDARY;
        public static final int O200 = Accent.SECONDARY;
        public static final int O300 = Accent.SECONDARY;
        public static final int O400 = Accent.SECONDARY;
        public static final int O500 = Accent.SECONDARY;
        public static final int O600 = Accent.SECONDARY;
        public static final int O700 = Accent.SECONDARY;
        public static final int O800 = Accent.SECONDARY;
        public static final int O900 = Accent.SECONDARY;

        private Orange() {}
    }

    /*
     * Neutral grayscale palette for UI surfaces and text.
     */
    public static final class Neutral {
        public static final int N950 = Bg.LEVEL_0;
        public static final int N920 = Bg.LEVEL_1;
        public static final int N900 = Surface.LEVEL_0;
        public static final int N880 = Surface.LEVEL_1;
        public static final int N860 = Surface.LEVEL_2;
        public static final int N840 = Surface.LEVEL_3;
        public static final int N820 = Surface.LEVEL_4;
        public static final int N800 = Stroke.MUTED;
        public static final int N780 = Text.SECONDARY;
        public static final int N760 = Text.SECONDARY;
        public static final int N740 = Text.SECONDARY;
        public static final int N700 = Text.SECONDARY;
        public static final int N650 = Text.SECONDARY;
        public static final int N600 = Text.SECONDARY;
        public static final int N550 = Text.SECONDARY;
        public static final int N500 = Text.SECONDARY;
        public static final int N450 = Text.PRIMARY;
        public static final int N400 = Text.PRIMARY;

        private Neutral() {}
    }

    /*
     * Basic RGB primaries for utility palettes.
     */
    public static final class Basic {
        public static final int RED = Palette.ERROR;
        public static final int YELLOW = Palette.WARNING;
        public static final int GREEN = Palette.SUCCESS;
        public static final int CYAN = Palette.ACCENT_TEAL;
        public static final int BLUE = Palette.ACCENT_BLUE;

        private Basic() {}
    }

    // ===========================================================================
    // PANEL COLORS
    // ===========================================================================

    /*
     * Panel background colors for different contexts.
     */
    public static final class Panel {
        /* Default panel background */
        public static final int BG = Bg.LEVEL_2;
        /* Elevated/raised panel */
        public static final int ELEVATED = Bg.LEVEL_3;
        /* Content area background */
        public static final int CONTENT = Bg.LEVEL_3;
        /* Header panel background */
        public static final int HEADER = Bg.LEVEL_2;
        /* Popover/modal panel */
        public static final int POPOVER = Bg.LEVEL_4;

        private Panel() {}
    }

    // ===========================================================================
    // INPUT FIELD COLORS
    // ===========================================================================

    /*
     * Input field colors (backgrounds, borders, focus states).
     */
    public static final class Input {
        /* Input background */
        public static final int BG = Surface.LEVEL_0;
        /* Input border */
        public static final int BORDER = Stroke.DEFAULT;
        /* Input border on hover */
        public static final int BORDER_HOVER = Stroke.EMPHASIS;
        /* Input border on focus */
        public static final int BORDER_FOCUS = DesignTokens.Accent.PRIMARY;
        /* Placeholder text */
        public static final int PLACEHOLDER = DesignTokens.Text.MUTED;
        /* Input text */
        public static final int TEXT = DesignTokens.Text.PRIMARY;
        /* Disabled input background */
        public static final int DISABLED_BG = Surface.LEVEL_0;
        /* Disabled input border */
        public static final int DISABLED_BORDER = Stroke.MUTED;

        private Input() {}
    }

    // ===========================================================================
    // TOOLTIP COLORS
    // ===========================================================================

    /*
     * Tooltip styling colors.
     */
    public static final class Tooltip {
        /* Tooltip background (high opacity) */
        public static final int BG = withAlpha(Surface.LEVEL_0, 0xF0);
        /* Tooltip border */
        public static final int BORDER = Stroke.DEFAULT;
        /* Tooltip shadow */
        public static final int SHADOW = withAlpha(Palette.SHADOW, 0x80);

        private Tooltip() {}
    }

    // ===========================================================================
    // RADIAL MENU COLORS
    // ===========================================================================

    public static final class Radial {
        /* Center hub background */
        public static final int HUB_BG = Bg.LEVEL_2;
        /* Center hub border */
        public static final int HUB_BORDER = Stroke.DEFAULT;
        /* Segment default background (80% opacity) */
        public static final int SEGMENT_BG = withAlpha(Bg.LEVEL_2, 0xCC);
        /* Segment hover background */
        public static final int SEGMENT_HOVER = withAlpha(Bg.LEVEL_3, 0xCC);
        /* Segment selected background */
        public static final int SEGMENT_SELECTED = withAlpha(Bg.LEVEL_4, 0xCC);
        /* Segment border */
        public static final int SEGMENT_BORDER = Stroke.DEFAULT;
        /* Segment divider */
        public static final int SEGMENT_DIVIDER = Stroke.MUTED;
        /* Icon default */
        public static final int ICON_DEFAULT = DesignTokens.Text.SECONDARY;
        /* Icon hover */
        public static final int ICON_HOVER = DesignTokens.Accent.PRIMARY;
        /* Label text */
        public static final int LABEL = DesignTokens.Text.PRIMARY;
        /* Sublabel text */
        public static final int SUBLABEL = DesignTokens.Text.MUTED;

        // -------------------------------------------------------------------
        // MACRO CATEGORY COLORS (6 primary)
        // -------------------------------------------------------------------
        /* Category: Analyze (blue) */
        public static final int CAT_ANALYZE = Semantic.INFO;
        /* Category: Telemetry (teal) */
        public static final int CAT_TELEMETRY = Accent.PRIMARY;
        /* Category: Combat (error) */
        public static final int CAT_COMBAT = Semantic.ERROR;
        /* Category: Arena (success) */
        public static final int CAT_ARENA = Semantic.SUCCESS;
        /* Category: Tools (warning) */
        public static final int CAT_TOOLS = Semantic.WARNING;
        /* Category: Play (amber) */
        public static final int CAT_PLAY = Accent.SECONDARY;

        /* Macro: Analyze (blue) */
        public static final int MACRO_ANALYZE = CAT_ANALYZE;
        /* Macro: Telemetry (teal) */
        public static final int MACRO_TELEMETRY = CAT_TELEMETRY;
        /* Macro: Combat (error) */
        public static final int MACRO_COMBAT = CAT_COMBAT;
        /* Macro: Arena (success) */
        public static final int MACRO_ARENA = CAT_ARENA;
        /* Macro: Play (amber) */
        public static final int MACRO_PLAY = CAT_PLAY;
        /* Macro: Tools (warning) */
        public static final int MACRO_TOOLS = CAT_TOOLS;

        // -------------------------------------------------------------------
        // ANALYZE SUBCATEGORY COLORS (blue gradient light->dark)
        // -------------------------------------------------------------------
        /* Analyze: Debug tools */
        public static final int ANALYZE_DEBUG = CAT_ANALYZE;
        /* Analyze: HUD overlays */
        public static final int ANALYZE_HUD = CAT_ANALYZE;
        /* Analyze: Spatial/render debug */
        public static final int ANALYZE_SPATIAL = CAT_ANALYZE;
        /* Analyze: Collision debug */
        public static final int ANALYZE_COLLISION = CAT_ANALYZE;
        /* Analyze: Performance */
        public static final int ANALYZE_PERFORMANCE = CAT_ANALYZE;
        /* Analyze: Mob visualizers */
        public static final int ANALYZE_MOBS = CAT_ANALYZE;
        /* Analyze: Density visualizers */
        public static final int ANALYZE_DENSITY = CAT_ANALYZE;
        /* Analyze: Safe spots */
        public static final int ANALYZE_SAFE_SPOTS = CAT_ANALYZE;
        /* Analyze: Light levels */
        public static final int ANALYZE_LIGHT = CAT_ANALYZE;
        /* Analyze: Spawnability */
        public static final int ANALYZE_SPAWN = CAT_ANALYZE;
        /* Analyze: Room bounds */
        public static final int ANALYZE_ROOM = CAT_ANALYZE;

        // -------------------------------------------------------------------
        // TELEMETRY SUBCATEGORY COLORS (purple gradient)
        // -------------------------------------------------------------------
        /* Telemetry: Operations */
        public static final int TELEMETRY_OPS = CAT_TELEMETRY;
        /* Telemetry: Dashboard */
        public static final int TELEMETRY_DASHBOARD = CAT_TELEMETRY;
        /* Telemetry: Exports */
        public static final int TELEMETRY_EXPORT = CAT_TELEMETRY;

        // -------------------------------------------------------------------
        // COMBAT SUBCATEGORY COLORS (red gradient)
        // -------------------------------------------------------------------
        /* Combat: Actions */
        public static final int COMBAT_ACTIONS = CAT_COMBAT;
        /* Combat: Damage/defense stats */
        public static final int COMBAT_DAMAGE = CAT_COMBAT;
        /* Combat: Defense */
        public static final int COMBAT_DEFENSE = CAT_COMBAT;
        /* Combat: Weapon editor */
        public static final int COMBAT_WEAPON = CAT_COMBAT;
        /* Combat: Shield editor (neutral gray) */
        public static final int COMBAT_SHIELD = CAT_COMBAT;

        // -------------------------------------------------------------------
        // ARENA SUBCATEGORY COLORS (green gradient)
        // -------------------------------------------------------------------
        /* Arena: Management */
        public static final int ARENA_MANAGE = CAT_ARENA;
        /* Arena: Templates */
        public static final int ARENA_TEMPLATES = CAT_ARENA;
        /* Arena: Spawning */
        public static final int ARENA_SPAWNING = CAT_ARENA;
        /* Arena: Hazards */
        public static final int ARENA_HAZARDS = CAT_ARENA;
        /* Arena: Rewards */
        public static final int ARENA_REWARDS = CAT_ARENA;

        // -------------------------------------------------------------------
        // TOOLS SUBCATEGORY COLORS (orange/yellow gradient)
        // -------------------------------------------------------------------
        /* Tools: Primary */
        public static final int TOOLS_PRIMARY = CAT_TOOLS;
        /* Tools: Editor */
        public static final int TOOLS_EDITOR = CAT_TOOLS;
        /* Tools: Secondary */
        public static final int TOOLS_SECONDARY = CAT_TOOLS;
        /* Tools: Utility */
        public static final int TOOLS_UTILITY = CAT_TOOLS;

        // -------------------------------------------------------------------
        // PLAY SUBCATEGORY COLORS (warm/social)
        // -------------------------------------------------------------------
        /* Play: Party */
        public static final int PLAY_PARTY = CAT_PLAY;
        /* Play: Social */
        public static final int PLAY_SOCIAL = CAT_PLAY;
        /* Play: Quests */
        public static final int PLAY_QUESTS = CAT_PLAY;
        /* Play: Communication */
        public static final int PLAY_COMMS = CAT_PLAY;
        /* Play: Leaderboard */
        public static final int PLAY_LEADERBOARD = CAT_PLAY;
        /* Play: Season Pass */
        public static final int PLAY_SEASON = CAT_PLAY;

        // Additional telemetry colors
        /* Telemetry: Spatial analysis */
        public static final int TELEMETRY_SPATIAL = CAT_TELEMETRY;
        /* Telemetry: Data tools */
        public static final int TELEMETRY_DATA = CAT_TELEMETRY;
        /* Telemetry: Scan tools */
        public static final int TELEMETRY_SCAN = CAT_TELEMETRY;
        /* Telemetry: Dashboard */
        public static final int TELEMETRY_DASH = CAT_TELEMETRY;

        // Additional combat colors
        /* Combat: Armor configuration */
        public static final int COMBAT_ARMOR = CAT_COMBAT;
        /* Combat: Abilities */
        public static final int COMBAT_ABILITIES = CAT_COMBAT;
        /* Combat: Debug tools */
        public static final int COMBAT_DEBUG = CAT_COMBAT;

        // Additional arena colors
        /* Arena: Endurance mode */
        public static final int ARENA_ENDURANCE = CAT_ARENA;
        /* Arena: Wave control */
        public static final int ARENA_WAVES = CAT_ARENA;
        /* Arena: Party management */
        public static final int ARENA_PARTY = CAT_ARENA;

        // Additional tools colors
        /* Tools: Testing */
        public static final int TOOLS_TESTING = CAT_TOOLS;
        /* Tools: Notifications */
        public static final int TOOLS_NOTIFY = CAT_TOOLS;
        /* Tools: Mailbox */
        public static final int TOOLS_MAILBOX = CAT_TOOLS;
        /* Tools: Settings */
        public static final int TOOLS_SETTINGS = CAT_TOOLS;
        /* Tools: Game design */
        public static final int TOOLS_GAMEDESIGN = CAT_TELEMETRY;
        /* Tools: Commands */
        public static final int TOOLS_COMMANDS = CAT_TOOLS;

        private Radial() {}
    }

    // ===========================================================================
    // HUD OVERLAY COLORS
    // ===========================================================================

    public static final class Hud {
        /* Default HUD panel background (80% opacity) */
        public static final int PANEL_BG = withAlpha(Bg.LEVEL_1, 0xCC);
        /* HUD panel border (50% opacity) */
        public static final int PANEL_BORDER = withAlpha(Stroke.DEFAULT, 0x80);

        // Health bar
        public static final int HEALTH = Semantic.ERROR;
        public static final int HEALTH_BG = withAlpha(Semantic.ERROR, 0x40);

        // Stamina bar
        public static final int STAMINA = Semantic.SUCCESS;
        public static final int STAMINA_BG = withAlpha(Semantic.SUCCESS, 0x40);

        // Mana/energy bar
        public static final int MANA = Semantic.INFO;
        public static final int MANA_BG = withAlpha(Semantic.INFO, 0x40);

        // Experience bar
        public static final int XP = DesignTokens.Accent.PRIMARY;
        public static final int XP_BG = withAlpha(DesignTokens.Accent.PRIMARY, 0x40);

        // Boss health
        public static final int BOSS_HEALTH = Semantic.ERROR;
        public static final int BOSS_PHASE = Semantic.WARNING;

        // Wave counter
        public static final int WAVE_TEXT = DesignTokens.Text.WHITE;
        public static final int WAVE_NUMBER = DesignTokens.Accent.SECONDARY;

        // Timer
        public static final int TIMER_NORMAL = DesignTokens.Text.PRIMARY;
        public static final int TIMER_WARNING = Semantic.WARNING;
        public static final int TIMER_CRITICAL = Semantic.ERROR;

        private Hud() {}
    }

    // ===========================================================================
    // NUTRITION COLORS
    // ===========================================================================

    public static final class Nutrition {
        public static final int GRAIN = 0xFFD4AF37;
        public static final int PROTEIN = 0xFFFF6B6B;
        public static final int VEGETABLE = 0xFF51CF66;
        public static final int FRUIT = 0xFFFFA500;
        public static final int SUGAR = 0xFFFFB6C1;
        public static final int WATER = 0xFF4A90E2;

        public static final int HUD_BG = withAlpha(Palette.SHADOW, Alpha.A50);
        public static final int HUD_BORDER = 0xFF1A1A1A;
        public static final int HUD_WELL_FED = 0xFF00FF00;
        public static final int HUD_CRITICAL = 0xFFFF0000;
        public static final int BAR_BG = 0xFF222222;
        public static final int BAR_LOW = 0xFF444444;
        public static final int MOD_POSITIVE = HUD_WELL_FED;
        public static final int MOD_NEGATIVE = 0xFFFF6666;
        public static final int MOD_NEUTRAL = 0xFFAAAAAA;

        private Nutrition() {}
    }

    // ===========================================================================
    // TESTING MODE COLORS
    // ===========================================================================

    /*
     * Colors for IntegratedTestSession types and testing overlays.
     */
    public static final class TestingMode {
        /* Combat test sessions (orange-red) */
        public static final int COMBAT = Semantic.ERROR;
        /* Boss fight test sessions (purple) */
        public static final int BOSS_FIGHT = DesignTokens.Accent.SECONDARY;
        /* Survival waves test sessions (green) */
        public static final int SURVIVAL = Semantic.SUCCESS;
        /* Damage validation test sessions (orange) */
        public static final int DAMAGE_VALIDATION = Semantic.WARNING;
        /* Performance stress test sessions (blue) */
        public static final int PERFORMANCE = Semantic.INFO;
        /* Custom test sessions (gray) */
        public static final int CUSTOM = DesignTokens.Text.SECONDARY;

        /* Endless mode pulse color */
        public static final int PULSE = Semantic.INFO;
        /* Progress bar border */
        public static final int PROGRESS_BORDER = Stroke.DEFAULT;

        private TestingMode() {}
    }

    // ===========================================================================
    // NOTIFICATION COLORS
    // ===========================================================================

    public static final class Notification {
        /* Default notification */
        public static final int DEFAULT_BG = Surface.LEVEL_1;
        public static final int DEFAULT_BORDER = Stroke.DEFAULT;

        /* Success notification (90% opacity) */
        public static final int SUCCESS_BG = withAlpha(Semantic.SUCCESS, 0xE6);
        public static final int SUCCESS_BORDER = Semantic.SUCCESS;

        /* Warning notification (90% opacity) */
        public static final int WARNING_BG = withAlpha(Semantic.WARNING, 0xE6);
        public static final int WARNING_BORDER = Semantic.WARNING;

        /* Error notification (90% opacity) */
        public static final int ERROR_BG = withAlpha(Semantic.ERROR, 0xE6);
        public static final int ERROR_BORDER = Semantic.ERROR;

        /* Info notification (90% opacity) */
        public static final int INFO_BG = withAlpha(Semantic.INFO, 0xE6);
        public static final int INFO_BORDER = Semantic.INFO;

        // Notification UI palette (warm tones)
        public static final int RGB_TEXT_PRIMARY = DesignTokens.Text.PRIMARY & Mask.RGB;
        public static final int RGB_TEXT_SECONDARY = DesignTokens.Text.SECONDARY & Mask.RGB;
        public static final int RGB_TEXT_MUTED = DesignTokens.Text.MUTED & Mask.RGB;
        public static final int RGB_WHITE = DesignTokens.Text.PRIMARY & Mask.RGB;
        public static final int RGB_BLACK = Mask.NONE;

        public static final int RGB_PANEL_TOP = Surface.LEVEL_1 & Mask.RGB;
        public static final int RGB_PANEL_BOTTOM = Surface.LEVEL_2 & Mask.RGB;
        public static final int RGB_BACKDROP_TOP = mix(RGB_PANEL_TOP, RGB_BLACK, 0.45f);
        public static final int RGB_BACKDROP_BOTTOM = mix(RGB_PANEL_BOTTOM, RGB_BLACK, 0.55f);
        public static final int RGB_PANEL_INNER_TOP = Surface.LEVEL_0 & Mask.RGB;
        public static final int RGB_PANEL_INNER_BOTTOM = Surface.LEVEL_1 & Mask.RGB;

        public static final int RGB_SURFACE_TOP = Surface.LEVEL_0 & Mask.RGB;
        public static final int RGB_SURFACE_BOTTOM = Surface.LEVEL_1 & Mask.RGB;
        public static final int RGB_SURFACE_HOVER_TOP = Surface.LEVEL_2 & Mask.RGB;
        public static final int RGB_SURFACE_HOVER_BOTTOM = Surface.LEVEL_3 & Mask.RGB;
        public static final int RGB_SURFACE_READ = Bg.LEVEL_2 & Mask.RGB;

        public static final int RGB_ACCENT = DesignTokens.Accent.SECONDARY & Mask.RGB;
        public static final int RGB_ACCENT_SOFT = DesignTokens.Accent.PRIMARY & Mask.RGB;
        public static final int RGB_ACCENT_ALT = Semantic.INFO & Mask.RGB;

        public static final class Category {
            public static final int ACHIEVEMENT = DesignTokens.Accent.SECONDARY;
            public static final int RECORD = DesignTokens.Accent.PRIMARY;
            public static final int SEASON = Semantic.INFO;
            public static final int TOKEN = DesignTokens.Accent.SECONDARY;
            public static final int REWARD = Semantic.SUCCESS;
            public static final int PARTY = DesignTokens.Accent.PRIMARY;
            public static final int QUEST = Semantic.WARNING;
            public static final int COMBAT = Semantic.ERROR;
            public static final int RESONANCE = DesignTokens.Accent.PRIMARY;
            public static final int NEWS = Semantic.INFO;
            public static final int ADMIN = Semantic.WARNING;
            public static final int SYSTEM = DesignTokens.Text.SECONDARY;
            public static final int MAILBOX = DesignTokens.Accent.PRIMARY;

            private Category() {}
        }

        public static final class Priority {
            public static final int LOW = DesignTokens.Text.SECONDARY;
            public static final int NORMAL = Semantic.INFO;
            public static final int HIGH = DesignTokens.Accent.SECONDARY;
            public static final int URGENT = Semantic.WARNING;
            public static final int CRITICAL = Semantic.ERROR;

            private Priority() {}
        }

        private static int mix(int rgbA, int rgbB, float t) {
            int result = lerp(rgbA | Mask.ALPHA, rgbB | Mask.ALPHA, t);
            return result & Mask.RGB;
        }

        private Notification() {}
    }

    // ===========================================================================
    // WELCOME SCREEN COLORS
    // ===========================================================================

    /*
     * Welcome screen palette (indigo theme).
     */
    public static final class Welcome {
        public static final int BG_TOP = withAlpha(Bg.LEVEL_2, 0xF0);
        public static final int BG_BOTTOM = withAlpha(Bg.LEVEL_1, 0xF0);
        public static final int BORDER = Accent.PRIMARY;
        public static final int TITLE = DesignTokens.Text.PRIMARY;
        public static final int SUBTITLE = DesignTokens.Text.SECONDARY;
        public static final int PARTICLE = Accent.PRIMARY;

        public static final int FEATURE_MOB = Semantic.SUCCESS;
        public static final int FEATURE_DEBUG = Semantic.INFO;
        public static final int FEATURE_ENDURANCE = Accent.SECONDARY;
        public static final int FEATURE_TESTING = Semantic.WARNING;

        public static final int HINT = DesignTokens.Text.MUTED;
        public static final int HIGHLIGHT = withAlpha(DesignTokens.Text.PRIMARY, 0x22);
        public static final int SUBTLE = withAlpha(DesignTokens.Text.PRIMARY, 0x11);
        public static final int SHADOW = withAlpha(Palette.SHADOW, 0x44);

        private Welcome() {}
    }

    // ===========================================================================
    // SEASON PASS SCREEN COLORS
    // ===========================================================================

    public static final class SeasonPass {
        public static final int BG_TOP = withAlpha(Bg.LEVEL_2, 0xF0);
        public static final int BG_BOTTOM = withAlpha(Bg.LEVEL_1, 0xF0);
        public static final int BORDER = Accent.SECONDARY;
        public static final int TITLE = DesignTokens.Text.PRIMARY;
        public static final int SUBTITLE = DesignTokens.Text.SECONDARY;
        public static final int FREE_TRACK = Semantic.INFO;
        public static final int PREMIUM_TRACK = Accent.SECONDARY;
        public static final int LOCKED = DesignTokens.Text.MUTED;
        public static final int PROGRESS_BG = Surface.LEVEL_0;
        public static final int PROGRESS_FILL = Accent.SECONDARY;
        public static final int CLAIMED = Semantic.SUCCESS;
        public static final int BOOST = Accent.PRIMARY;
        public static final int BADGE = Semantic.ERROR;
        public static final int INACTIVE = DesignTokens.Text.MUTED;
        public static final int HIGHLIGHT = withAlpha(DesignTokens.Text.PRIMARY, 0x22);
        public static final int ROW_BG = Surface.LEVEL_1;
        public static final int ROW_BG_ALT = Surface.LEVEL_2;

        private SeasonPass() {}
    }

    // ===========================================================================
    // PARTY SCREEN COLORS
    // ===========================================================================

    public static final class Party {
        public static final int TAB_ACTIVE = Surface.LEVEL_2;
        public static final int ROW_HOVER = withAlpha(DesignTokens.Text.PRIMARY, 0x40);
        public static final int ROW_DEFAULT = withAlpha(DesignTokens.Text.PRIMARY, 0x20);
        public static final int HINT_TEXT = withAlpha(DesignTokens.Text.SECONDARY, 0x60);

        public static final int STAT_HP = Semantic.ERROR;
        public static final int STAT_DMG = Semantic.WARNING;
        public static final int STAT_POINTS = Accent.SECONDARY;
        public static final int STAT_DIFFICULTY = Semantic.INFO;

        public static final int READY_GLOW = withAlpha(Semantic.SUCCESS, 0x40);
        public static final int NOT_READY_GLOW = withAlpha(Semantic.ERROR, 0x40);

        public static final int DIFFICULTY_TRIVIAL = DesignTokens.Neutral.N650;
        public static final int DIFFICULTY_EASY = Semantic.SUCCESS;
        public static final int DIFFICULTY_MEDIUM = Accent.SECONDARY;
        public static final int DIFFICULTY_HARD = Semantic.WARNING;
        public static final int DIFFICULTY_ELITE = Semantic.ERROR;
        public static final int DIFFICULTY_BOSS = Semantic.ERROR;

        private Party() {}
    }

    // ===========================================================================
    // MULTI-EDIT PANEL COLORS
    // ===========================================================================

    public static final class MultiEdit {
        public static final int HEADER_BG_HOVER = DesignTokens.Neutral.N760;
        public static final int HEADER_BG_DEFAULT = DesignTokens.Neutral.N820;
        public static final int TEXT_PRIMARY = DesignTokens.Text.WHITE;
        public static final int TEXT_MUTED = DesignTokens.Neutral.N500;
        public static final int TEXT_DIM = DesignTokens.Neutral.N550;
        public static final int TEXT_FAINT = DesignTokens.Neutral.N600;
        public static final int TEXT_HINT = DesignTokens.Neutral.N700;
        public static final int TEXT_SECONDARY = DesignTokens.Neutral.N450;
        public static final int EMPTY_STATE_BG = DesignTokens.Neutral.N900;
        public static final int PRESET_BG_OPEN = DesignTokens.Neutral.N800;
        public static final int PRESET_BG_CLOSED = DesignTokens.Neutral.N860;
        public static final int PRESET_LABEL_ACTIVE = Semantic.SUCCESS;
        public static final int DROPDOWN_BG = DesignTokens.Neutral.N920;
        public static final int DROPDOWN_BG_SELECTED = withAlpha(Accent.PRIMARY, 0x40);
        public static final int DROPDOWN_BG_HOVER = DesignTokens.Neutral.N820;
        public static final int DROPDOWN_BG_DEFAULT = DesignTokens.Neutral.N880;
        public static final int DROPDOWN_TEXT = DesignTokens.Neutral.N400;
        public static final int DROPDOWN_HINT = DesignTokens.Neutral.N550;
        public static final int DROPDOWN_HOVER = Semantic.INFO;
        public static final int ITEM_BG_HOVER = DesignTokens.Neutral.N760;
        public static final int ITEM_BG_DEFAULT = DesignTokens.Neutral.N840;
        public static final int ITEM_REMOVE_HOVER = Semantic.ERROR;
        public static final int PREVIEW_MODE = Accent.SECONDARY;
        public static final int ACTION_ROW_BG = DesignTokens.Neutral.N880;
        public static final int PROGRESS_BAR_BG = DesignTokens.Neutral.N880;
        public static final int PROGRESS_BAR_FILL = Semantic.SUCCESS;
        public static final int RESULT_BG = DesignTokens.Neutral.N950;
        public static final int RESULT_SUCCESS = Semantic.SUCCESS;
        public static final int RESULT_WARNING = Semantic.WARNING;
        public static final int FAILURE_TEXT = Semantic.ERROR;
        public static final int MORE_FAILURES = Accent.SECONDARY;

        private MultiEdit() {}
    }

    // ===========================================================================
    // CRAFTING RARITY COLORS
    // ===========================================================================

    public static final class Rarity {
        public static final int COMMON = DesignTokens.Text.MUTED;
        public static final int UNCOMMON = Semantic.SUCCESS;
        public static final int RARE = Semantic.INFO;
        public static final int EPIC = Accent.PRIMARY;
        public static final int LEGENDARY = Accent.SECONDARY;

        private Rarity() {}
    }

    // ===========================================================================
    // ERROR SCREEN COLORS
    // ===========================================================================

    public static final class ErrorScreen {
        public static final int BG = withAlpha(Semantic.ERROR, 0xE0);
        public static final int TITLE = Semantic.ERROR;
        public static final int TEXT = DesignTokens.Neutral.N450;
        public static final int HINT = DesignTokens.Neutral.N550;
        public static final int STATUS_BG = withAlpha(Bg.LEVEL_1, 0xC0);
        public static final int STATUS_ERROR = Semantic.ERROR;

        private ErrorScreen() {}
    }

    // ===========================================================================
    // ERROR BOUNDARY COLORS
    // ===========================================================================

    public static final class ErrorBoundary {
        public static final int BG = withAlpha(Semantic.ERROR, 0xE0);
        public static final int PANEL_BG = Bg.LEVEL_2;
        public static final int BORDER = Semantic.ERROR;
        public static final int TEXT = DesignTokens.Text.WHITE;
        public static final int SCRIM = withAlpha(PANEL_BG, DesignTokens.Alpha.A75);
        public static final int HIGHLIGHT = Semantic.ERROR;

        private ErrorBoundary() {}
    }

    // ===========================================================================
    // EXTERNAL LINK CONFIRM DIALOG COLORS
    // ===========================================================================

    public static final class ExternalConfirm {
        public static final int BG = withAlpha(Bg.LEVEL_1, 0xDD);
        public static final int BORDER = Stroke.DEFAULT;
        public static final int TITLE = DesignTokens.Text.PRIMARY;
        public static final int URL = DesignTokens.Text.SECONDARY;
        public static final int STATUS_OK = Semantic.SUCCESS;
        public static final int STATUS_WARN = Semantic.WARNING;
        public static final int STATUS_ERROR = Semantic.ERROR;

        private ExternalConfirm() {}
    }

    // ===========================================================================
    // QUICK TEST WIZARD COLORS
    // ===========================================================================

    public static final class QuickTest {
        public static final int HEADER_GRADIENT_START = Bg.LEVEL_3;
        public static final int INFO_BG = withAlpha(Semantic.WARNING, 0x40);

        private QuickTest() {}
    }

    // ===========================================================================
    // TESTING UI COLORS
    // ===========================================================================

    public static final class Testing {
        public static final int SEPARATOR = Palette.OUTLINE;
        public static final int THUMB_NORMAL = Palette.OUTLINE;
        public static final int THUMB_HOVER = Palette.ACCENT_BLUE;
        public static final int TRACK = Palette.PANEL;
        public static final int HEADER_ACCENT = Palette.ACCENT_TEAL;
        public static final int SUCCESS = Palette.SUCCESS;
        public static final int ERROR = Palette.ERROR;
        public static final int CYAN = Palette.ACCENT_TEAL;
        public static final int TELEMETRY_ACCENT = Palette.ACCENT_BLUE;
        public static final int WARNING = Palette.WARNING;
        public static final int ALERT = Palette.ERROR;

        public static final class Status {
            public static final int PENDING = Palette.TEXT_SECONDARY;
            public static final int IN_PROGRESS = Palette.ACCENT_BLUE;
            public static final int PASSED = Palette.SUCCESS;
            public static final int FAILED = Palette.ERROR;
            public static final int SKIPPED = Palette.TEXT_SECONDARY;

            private Status() {}
        }

        public static final class Priority {
            public static final int CRITICAL = Palette.ERROR;
            public static final int HIGH = Palette.WARNING;
            public static final int MEDIUM = Palette.ACCENT_AMBER;
            public static final int LOW = Palette.TEXT_SECONDARY;

            private Priority() {}
        }

        public static final class Level {
            public static final int LEVEL_1 = Palette.TEXT_SECONDARY;
            public static final int LEVEL_2 = Palette.ACCENT_TEAL;
            public static final int LEVEL_3 = Palette.ACCENT_BLUE;
            public static final int LEVEL_4 = Palette.SUCCESS;
            public static final int LEVEL_5 = Palette.WARNING;
            public static final int LEVEL_6 = Palette.ACCENT_AMBER;
            public static final int LEVEL_7 = Palette.ERROR;

            private static final int[] COLORS = {
                LEVEL_1,
                LEVEL_2,
                LEVEL_3,
                LEVEL_4,
                LEVEL_5,
                LEVEL_6,
                LEVEL_7
            };

            private Level() {}

            public static int forLevel(int level) {
                int index = Math.max(1, level) - 1;
                if (index >= COLORS.length) {
                    index = COLORS.length - 1;
                }
                return COLORS[index];
            }
        }

        public static final class AchievementCategory {
            public static final int COMBAT = Palette.ERROR;
            public static final int PRECISION = Palette.ACCENT_BLUE;
            public static final int RANGED = Palette.ACCENT_TEAL;
            public static final int SURVIVAL = Palette.SUCCESS;
            public static final int EXPLOSION = Palette.WARNING;
            public static final int ALCHEMY = Palette.ACCENT_TEAL;
            public static final int EXPLORER = Palette.ACCENT_BLUE;
            public static final int DEDICATION = Palette.ACCENT_AMBER;
            public static final int TESTING = Palette.ACCENT_TEAL;
            public static final int SPECIAL = Palette.ACCENT_AMBER;

            private AchievementCategory() {}
        }

        public static final class Badge {
            public static final int BRONZE_TESTER = Palette.ACCENT_AMBER;
            public static final int SILVER_TESTER = Palette.TEXT_SECONDARY;
            public static final int GOLD_TESTER = Palette.ACCENT_AMBER;
            public static final int DIAMOND_TESTER = Palette.ACCENT_BLUE;
            public static final int COMBAT_SPECIALIST = Palette.ERROR;
            public static final int PRECISION_EXPERT = Palette.ACCENT_BLUE;
            public static final int COMPLETIONIST = Palette.SUCCESS;

            private Badge() {}
        }

        private Testing() {}
    }

    // ===========================================================================
    // DEBUG OVERLAY COLORS
    // ===========================================================================

    public static final class DebugOverlay {
        public static final int GRID = withAlpha(Text.PRIMARY, 0x40);
        public static final int GRID_MAJOR = withAlpha(Text.PRIMARY, 0x60);
        public static final int ZONE_BOUNDARY = withAlpha(Semantic.WARNING, 0x80);
        public static final int BBOX = withAlpha(Accent.PRIMARY, 0x80);
        public static final int BBOX_HOVERED = withAlpha(Accent.PRIMARY, 0xC0);
        public static final int WARNING = Semantic.ERROR;
        public static final int OVERFLOW = withAlpha(Semantic.ERROR, 0x80);
        public static final int INFO_BG = withAlpha(Bg.LEVEL_0, 0xE0);
        public static final int INFO_TEXT = Text.SECONDARY;
        public static final int WARNING_TRUNCATED = Semantic.WARNING;
        public static final int WARNING_MISALIGNED = Semantic.WARNING;
        public static final int WARNING_OUT_OF_VIEW = Text.MUTED;

        private DebugOverlay() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PREVIEW RENDERER COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Preview {
        public static final int SELECTED_SLOT_BG = withAlpha(Semantic.INFO, 0x40);
        public static final int BG = Bg.LEVEL_1;
        public static final int BASE_SHADOW = withAlpha(Palette.SHADOW, 0x10);

        private Preview() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECIPE GRID COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class RecipeGrid {
        public static final int TAG_INDICATOR = Accent.SECONDARY;

        private RecipeGrid() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STAMINA SYSTEM EDITOR COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class StaminaEditor {
        public static final int OVERLAY_BG = withAlpha(Bg.LEVEL_1, 0x80);
        public static final int PANEL_BG = Surface.LEVEL_1;
        public static final int TITLE_TEXT = DesignTokens.Text.WHITE;
        public static final int SELECTED_FIELD = Accent.SECONDARY;
        public static final int NORMAL_FIELD = DesignTokens.Neutral.N450;
        public static final int INSTRUCTIONS = DesignTokens.Neutral.N550;

        private StaminaEditor() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ITEM EDITOR SCREEN COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class ItemEditor {
        public static final int STATUS_MESSAGE_BG = withAlpha(Bg.LEVEL_1, 0xE0);
        public static final int TOOLTIP_BG = withAlpha(Bg.LEVEL_2, 0xF0);
        public static final int DEV_PANEL_BG = withAlpha(Bg.LEVEL_2, 0xE0);
        public static final int DEV_PANEL_TITLE = DesignTokens.Text.WHITE;

        private ItemEditor() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRESET SELECTOR COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class PresetSelector {
        public static final int CLOSE_HOVER = Semantic.ERROR;
        public static final int RENAME_BG = Surface.LEVEL_0;
        public static final int RENAME_BORDER = Accent.PRIMARY;
        public static final int SEARCH_BG_FOCUSED = DesignTokens.Neutral.N820;
        public static final int SEARCH_BG_DEFAULT = DesignTokens.Neutral.N860;
        public static final int PREVIEW_BG = DesignTokens.Neutral.N900;
        public static final int ROW_BG_SELECTED = withAlpha(Accent.PRIMARY, 0x33);
        public static final int ROW_BG_HOVER = DesignTokens.Neutral.N820;
        public static final int ROW_BG_DEFAULT = DesignTokens.Neutral.N880;
        public static final int SCOPE_MODPACK = Accent.SECONDARY;
        public static final int SCOPE_CATEGORY = Semantic.INFO;
        public static final int SCOPE_GLOBAL = Semantic.SUCCESS;
        public static final int SCOPE_GLOBAL_USER = Semantic.SUCCESS;

        private PresetSelector() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MOB CONFIG SCREEN COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class MobConfig {
        public static final int HEADER_GRADIENT_START = Bg.LEVEL_3;
        public static final int SECTION_GRADIENT_START = Bg.LEVEL_2;
        public static final int SECTION_GRADIENT_END = Bg.LEVEL_3;
        public static final int MARKER = withAlpha(Text.PRIMARY, 0x80);
        public static final int OVERLAY = withAlpha(Palette.SHADOW, 0xA0);

        private MobConfig() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TELEMETRY DASHBOARD COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class TelemetryDashboard {
        public static final int CONFIRM_HOVER_BG = withAlpha(Semantic.ERROR, 0x80);
        public static final int SCRIM = withAlpha(Palette.SHADOW, 0xC0);

        private TelemetryDashboard() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TELEMETRY EXPORT COLORS (PNG heatmaps)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class TelemetryExport {
        public static final int BG = Palette.BACKGROUND;
        public static final int TITLE = Palette.TEXT_PRIMARY;
        public static final int SUBTITLE = Palette.TEXT_SECONDARY;
        public static final int GRID = Palette.OUTLINE;
        public static final int BORDER = Palette.OUTLINE;
        public static final int LEGEND_TEXT = Palette.TEXT_PRIMARY;
        public static final int CELL_ALPHA = 200;

        public static final int GRADIENT_0 = Palette.ACCENT_BLUE;
        public static final int GRADIENT_1 = Palette.ACCENT_TEAL;
        public static final int GRADIENT_2 = Palette.SUCCESS;
        public static final int GRADIENT_3 = Palette.WARNING;
        public static final int GRADIENT_4 = Palette.ACCENT_AMBER;
        public static final int GRADIENT_5 = Palette.ERROR;
        public static final int GRADIENT_6 = Palette.ERROR;
        public static final int GRADIENT_7 = Palette.ERROR;
        public static final int GRADIENT_8 = Palette.ERROR;
        public static final int GRADIENT_9 = Palette.ERROR;

        private TelemetryExport() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KEYBINDS PAGE COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Keybinds {
        public static final int CONFLICT_BG = withAlpha(Semantic.ERROR, 0x30);
        public static final int CONFLICT_GLOW = withAlpha(Semantic.ERROR, 0x18);
        public static final int CONFLICT_BORDER = Semantic.ERROR;
        public static final int CONFLICT_TEXT = Semantic.ERROR;

        private Keybinds() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HEATMAP COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Heatmap {
        public static final int DEATH = Palette.ERROR;
        public static final int MOVEMENT = Palette.ACCENT_TEAL;
        public static final int CAMPING = Palette.WARNING;
        public static final int STUCK = Palette.ACCENT_AMBER;
        public static final int AGGRO_DROP = Palette.ERROR;
        public static final int KITING = Palette.ACCENT_BLUE;
        public static final int LIGHT_SPAWNABLE = Palette.SUCCESS;
        public static final int LIGHT_DARK = Palette.WARNING;

        private Heatmap() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BODY DIAGRAM COLORS (Combat Settings)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class BodyDiagram {
        public static final int HEAD = Semantic.ERROR;
        public static final int BODY = Semantic.SUCCESS;
        public static final int ARMS = Semantic.WARNING;
        public static final int LEGS = Semantic.INFO;
        public static final int ARMOR_LABEL = Accent.SECONDARY;

        private BodyDiagram() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUG PANEL COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class DebugPanel {
        public static final int NBT_TOGGLE = Accent.PRIMARY;
        public static final int NBT_SUMMARY = Accent.SECONDARY;
        public static final int HEADER_TEXT = DesignTokens.Text.PRIMARY;
        public static final int ITEM_TEXT = DesignTokens.Text.PRIMARY;
        public static final int ITEM_DETAIL = DesignTokens.Text.SECONDARY;
        public static final int NBT_COUNT = DesignTokens.Text.SECONDARY;
        public static final int SOURCE_TEXT = Semantic.INFO;
        public static final int LOG_TEXT = DesignTokens.Text.SECONDARY;
        public static final int PANEL_BG = withAlpha(Bg.LEVEL_3, 0xE0);
        public static final int DIFF = Semantic.WARNING;
        public static final int MATCH = withAlpha(Semantic.SUCCESS, 0x88);

        private DebugPanel() {}
    }

    // ===========================================================================
    // COLOR TOKENS - IMPACT BUTTON (special button styles)
    // ===========================================================================

    public static final class ImpactButton {
        /* Default button base */
        public static final int DEFAULT_BASE = Surface.LEVEL_0;
        /* Default button border */
        public static final int DEFAULT_BORDER = Stroke.DEFAULT;
        /* Ghost button base (more transparent) */
        public static final int GHOST_BASE = Surface.LEVEL_0;
        /* Ghost button border */
        public static final int GHOST_BORDER = Stroke.MUTED;

        /* Primary button base (teal) */
        public static final int PRIMARY_BASE = darken(Accent.PRIMARY, 0.35f);
        /* Primary button border */
        public static final int PRIMARY_BORDER = Accent.PRIMARY;

        /* Danger button base (dark red) */
        public static final int DANGER_BASE = darken(Semantic.ERROR, 0.35f);
        /* Danger button border */
        public static final int DANGER_BORDER = Semantic.ERROR;

        /* Success button base (forest green) */
        public static final int SUCCESS_BASE = darken(Semantic.SUCCESS, 0.35f);
        /* Success button border */
        public static final int SUCCESS_BORDER = Semantic.SUCCESS;

        private ImpactButton() {}
    }

    // ===========================================================================
    // COLOR TOKENS - UTILITY
    // ===========================================================================

    public static final class Utility {
        /* Focus ring color */
        public static final int FOCUS = DesignTokens.Accent.PRIMARY;
        /* Selection background */
        public static final int SELECTION = withAlpha(DesignTokens.Accent.PRIMARY, 0x30);
        /* Modal backdrop (scrim) */
        public static final int SCRIM = withAlpha(Palette.SHADOW, 0xCC);
        /* Disabled elements */
        public static final int DISABLED = DesignTokens.Neutral.N760;
        /* Drop shadows */
        public static final int SHADOW = withAlpha(Palette.SHADOW, 0x80);
        /* Cyan glow */
        public static final int GLOW_CYAN = withAlpha(DesignTokens.Accent.PRIMARY, 0x60);

        private Utility() {}
    }

    // ===========================================================================
    // SPACING TOKENS (4px grid)
    // ===========================================================================

    public static final class Space {
        public static final int _0 = 0;
        public static final int _1 = 2;   // Micro gaps (icon-text)
        public static final int _2 = 4;   // Tight spacing
        public static final int _3 = 6;   // Compact lists
        public static final int _4 = 8;   // Default gap
        public static final int _5 = 12;  // Section spacing
        public static final int _6 = 16;  // Large gaps
        public static final int _7 = 24;  // Panel padding
        public static final int _8 = 32;  // Major sections
        public static final int _9 = 48;  // Page margins
        public static final int _10 = 64; // XL separation

        /* Get spacing by level (0-10) */
        public static int level(int level) {
            return switch (level) {
                case 0 -> _0;
                case 1 -> _1;
                case 2 -> _2;
                case 3 -> _3;
                case 4 -> _4;
                case 5 -> _5;
                case 6 -> _6;
                case 7 -> _7;
                case 8 -> _8;
                case 9 -> _9;
                case 10 -> _10;
                default -> _4;
            };
        }

        // Semantic aliases
        public static final int COMPONENT_GAP = _4;      // 8px
        public static final int SECTION_GAP = _5;        // 12px
        public static final int PANEL_PADDING = _4;      // 8px
        public static final int BUTTON_PADDING_H = _3;   // 6px
        public static final int BUTTON_PADDING_V = _2;   // 4px

        private Space() {}
    }

    // ===========================================================================
    // RADIUS TOKENS
    // ===========================================================================

    public static final class Radius {
        /* No rounding (pixel-perfect) */
        public static final int NONE = 0;
        /* Subtle rounding */
        public static final int SM = 2;
        /* Default buttons/inputs */
        public static final int MD = 4;
        /* Cards, panels */
        public static final int LG = 6;
        /* Modals */
        public static final int XL = 8;
        /* Pills, badges */
        public static final int FULL = 9999;

        private Radius() {}
    }

    // ===========================================================================
    // STROKE WIDTH TOKENS
    // ===========================================================================

    public static final class StrokeWidth {
        /* No border */
        public static final int NONE = 0;
        /* Default borders */
        public static final int THIN = 1;
        /* Emphasis, focus rings */
        public static final int MEDIUM = 2;
        /* Major emphasis */
        public static final int THICK = 3;

        private StrokeWidth() {}
    }

    // ===========================================================================
    // ELEVATION TOKENS (shadow/glow specs)
    // ===========================================================================

    public static final class Elevation {

        public record ElevationSpec(int blur, float opacity, int yOffset) {}

        public static final ElevationSpec LEVEL_0 = new ElevationSpec(0, 0f, 0);
        public static final ElevationSpec LEVEL_1 = new ElevationSpec(2, 0.10f, 1);
        public static final ElevationSpec LEVEL_2 = new ElevationSpec(4, 0.15f, 2);
        public static final ElevationSpec LEVEL_3 = new ElevationSpec(8, 0.20f, 4);
        public static final ElevationSpec LEVEL_4 = new ElevationSpec(12, 0.25f, 6);
        public static final ElevationSpec LEVEL_5 = new ElevationSpec(16, 0.30f, 8);
        public static final ElevationSpec LEVEL_6 = new ElevationSpec(24, 0.40f, 12);

        /* Get elevation spec by level (0-6) */
        public static ElevationSpec level(int level) {
            return switch (level) {
                case 0 -> LEVEL_0;
                case 1 -> LEVEL_1;
                case 2 -> LEVEL_2;
                case 3 -> LEVEL_3;
                case 4 -> LEVEL_4;
                case 5 -> LEVEL_5;
                case 6 -> LEVEL_6;
                default -> LEVEL_0;
            };
        }

        private Elevation() {}
    }

    // ===========================================================================
    // MOTION TOKENS (durations in milliseconds)
    // ===========================================================================

    public static final class Motion {
        /* Immediate */
        public static final int INSTANT = 0;
        /* Hover states */
        public static final int MICRO = 80;
        /* Micro interactions */
        public static final int FAST = 120;
        /* Standard transitions */
        public static final int NORMAL = 180;
        /* Panel open/close */
        public static final int SLOW = 240;
        /* Complex animations */
        public static final int SLOWER = 320;
        /* Page transitions */
        public static final int SLOWEST = 480;

        /*
         * Convert duration to per-frame lerp factor (assuming 60fps).
         * Use with Mth.lerp(factor, current, target) per frame.
         */
        public static float toLerpFactor(int durationMs) {
            if (durationMs <= 0) return 1.0f;
            // Approximate: factor that reaches ~95% in durationMs at 60fps
            int frames = (int) Math.ceil(durationMs / 16.67);
            return 3.0f / frames;
        }

        private Motion() {}
    }

    // ===========================================================================
    // Z-ORDER TOKENS (UI layering)
    // ===========================================================================

    /*
     * Z-Order (layer) definitions for UI rendering.
     * Higher values render on top of lower values.
     *
     * <h2>Layer Hierarchy</h2>
     * <pre>
     * CURSOR          (900) - Mouse cursor, drag preview
     * DEBUG           (800) - Debug overlays, dev tools
     * MODAL           (700) - Modal dialogs, blocking popups
     * DROPDOWN        (600) - Dropdowns, menus, popovers
     * TOOLTIP         (500) - Tooltips
     * NOTIFICATION    (400) - Toast notifications
     * RADIAL          (300) - Radial menu
     * OVERLAY         (200) - HUD overlays
     * PANEL           (100) - Floating panels
     * CONTENT         (50)  - Main UI elements
     * BASE            (0)   - Base content, screens
     * </pre>
     *
     * <h2>Usage</h2>
     * <pre>{@code
     * poseStack.pushPose();
     * poseStack.translate(0, 0, DesignTokens.ZOrder.TOOLTIP);
     * // render tooltip
     * poseStack.popPose();
     * }</pre>
     */
    public static final class ZOrder {
        /* Base layer - screen content, backgrounds */
        public static final float BASE = 0f;
        /* Content layer - main UI elements */
        public static final float CONTENT = 50f;
        /* Panel layer - floating panels, sidebars */
        public static final float PANEL = 100f;
        /* Overlay layer - HUD elements, overlays */
        public static final float OVERLAY = 200f;
        /* Radial menu layer */
        public static final float RADIAL = 300f;
        /* Notification layer - toast notifications */
        public static final float NOTIFICATION = 400f;
        /* Tooltip layer */
        public static final float TOOLTIP = 500f;
        /* Dropdown layer - menus, popovers, dropdowns */
        public static final float DROPDOWN = 600f;
        /* Modal layer - modal dialogs, blocking UI */
        public static final float MODAL = 700f;
        /* Debug layer - debug overlays, dev tools */
        public static final float DEBUG = 800f;
        /* Cursor layer - mouse cursor, drag previews */
        public static final float CURSOR = 900f;

        // Sublayer offsets
        /* Offset to place element behind its layer's default */
        public static final float BEHIND = -10f;
        /* Offset to place element slightly above its layer */
        public static final float ABOVE = 10f;
        /* Offset to place element at foreground of its layer */
        public static final float FOREGROUND = 25f;

        /* Get layer value with sublayer offset */
        public static float at(float layer, float offset) {
            return layer + offset;
        }

        /* Check if z1 is in front of z2 */
        public static boolean isInFront(float z1, float z2) {
            return z1 > z2;
        }

        /* Check if z1 is behind z2 */
        public static boolean isBehind(float z1, float z2) {
            return z1 < z2;
        }

        /* Get layer name for debugging */
        public static String layerName(float z) {
            if (z >= CURSOR) return "CURSOR";
            if (z >= DEBUG) return "DEBUG";
            if (z >= MODAL) return "MODAL";
            if (z >= DROPDOWN) return "DROPDOWN";
            if (z >= TOOLTIP) return "TOOLTIP";
            if (z >= NOTIFICATION) return "NOTIFICATION";
            if (z >= RADIAL) return "RADIAL";
            if (z >= OVERLAY) return "OVERLAY";
            if (z >= PANEL) return "PANEL";
            if (z >= CONTENT) return "CONTENT";
            return "BASE";
        }

        private ZOrder() {}
    }

    // ===========================================================================
    // ICON SIZE TOKENS
    // ===========================================================================

    public static final class Icon {
        /* Inline with small text */
        public static final int XS = 12;
        /* Default inline */
        public static final int SM = 16;
        /* Buttons */
        public static final int MD = 20;
        /* Headers, tabs */
        public static final int LG = 24;
        /* Featured/hero */
        public static final int XL = 32;
        /* Splash/empty states */
        public static final int XXL = 48;

        private Icon() {}
    }

    // ===========================================================================
    // ALPHA SCALE (opacity values 0-255)
    // ===========================================================================

    public static final class Alpha {
        public static final int A0   = 0x00;   // Transparent
        public static final int A7   = 0x11;   // 7%
        public static final int A10  = 0x1A;   // 10%
        public static final int A12  = 0x20;   // 12%
        public static final int A13  = 0x22;   // 13%
        public static final int A20  = 0x33;   // 20%
        public static final int A25  = 0x40;   // 25%
        public static final int A27  = 0x44;   // 27%
        public static final int A30  = 0x4D;   // 30%
        public static final int A33  = 0x55;   // 33%
        public static final int A40  = 0x66;   // 40%
        public static final int A47  = 0x77;   // 47%
        public static final int A50  = 0x80;   // 50%
        public static final int A53  = 0x88;   // 53%
        public static final int A60  = 0x99;   // 60%
        public static final int A63  = 0xA0;   // 63%
        public static final int A67  = 0xAA;   // 67%
        public static final int A70  = 0xB3;   // 70%
        public static final int A75  = 0xC0;   // 75%
        public static final int A80  = 0xCC;   // 80%
        public static final int A88  = 0xE0;   // 88%
        public static final int A90  = 0xE6;   // 90%
        public static final int A93  = 0xEE;   // 93%
        public static final int A94  = 0xF0;   // 94%
        public static final int A95  = 0xF2;   // 95%
        public static final int A100 = 0xFF;   // Opaque

        private Alpha() {}
    }

    // ===========================================================================
    // COLOR MASKS (bitwise helpers)
    // ===========================================================================

    public static final class Mask {
        public static final int RGB = 0x00FFFFFF;
        public static final int ALPHA = 0xFF000000;
        public static final int NONE = 0x00000000;

        private Mask() {}
    }

    // ===========================================================================
    // GRID LAYOUT UTILITIES
    // ===========================================================================

    public static final class Grid {
        /* Base grid unit (4px) */
        public static final int UNIT = GRID;
        /* Major grid (16px = 4 units) */
        public static final int MAJOR = 16;
        /* Half unit (2px - use sparingly) */
        public static final int HALF = 2;

        // --- Snap functions ---

        /* Snap value to major grid (16px) */
        public static int snapMajor(int value) {
            return Math.round(value / (float) MAJOR) * MAJOR;
        }

        /* Check if value is aligned to major grid */
        public static boolean isMajorAligned(int value) {
            return value % MAJOR == 0;
        }

        // --- Unit conversions ---

        /* Convert grid units to pixels (count x 4) */
        public static int units(int count) {
            return count * UNIT;
        }

        /* Convert major grid units to pixels (count x 16) */
        public static int major(int count) {
            return count * MAJOR;
        }

        /* Convert pixels to grid units (rounded) */
        public static int toUnits(int pixels) {
            return Math.round(pixels / (float) UNIT);
        }

        /* Convert pixels to major grid units (rounded) */
        public static int toMajor(int pixels) {
            return Math.round(pixels / (float) MAJOR);
        }

        // --- Spacing shortcuts ---

        /* 4px - Micro gap */
        public static int xs() { return Space._2; }
        /* 8px - Tight spacing */
        public static int sm() { return Space._4; }
        /* 12px - Compact spacing */
        public static int md() { return Space._5; }
        /* 16px - Default spacing */
        public static int lg() { return Space._6; }
        /* 24px - Section spacing */
        public static int xl() { return Space._7; }
        /* 32px - Large spacing */
        public static int xxl() { return Space._8; }

        // --- Layout calculations ---

        /*
         * Calculate column width for n-column layout.
         * @param columns Number of columns
         * @param totalWidth Available width
         * @param gap Gap between columns
         * @return Width of each column (grid-aligned)
         */
        public static int columnWidth(int columns, int totalWidth, int gap) {
            if (columns <= 0) return totalWidth;
            int totalGaps = (columns - 1) * gap;
            int availableWidth = totalWidth - totalGaps;
            return snapDown(availableWidth / columns);
        }

        /* Calculate column width with default gap (16px) */
        public static int columnWidth(int columns, int totalWidth) {
            return columnWidth(columns, totalWidth, MAJOR);
        }

        /* Calculate X position for column index */
        public static int columnX(int columnIndex, int columnWidth, int gap, int startX) {
            return startX + columnIndex * (columnWidth + gap);
        }

        /* Calculate row height for n-row layout */
        public static int rowHeight(int rows, int totalHeight, int gap) {
            if (rows <= 0) return totalHeight;
            int totalGaps = (rows - 1) * gap;
            int availableHeight = totalHeight - totalGaps;
            return snapDown(availableHeight / rows);
        }

        /* Calculate Y position for row index */
        public static int rowY(int rowIndex, int rowHeight, int gap, int startY) {
            return startY + rowIndex * (rowHeight + gap);
        }

        // --- Centering ---

        /* Center an element horizontally within a container */
        public static int centerX(int elementWidth, int containerWidth) {
            return snap((containerWidth - elementWidth) / 2);
        }

        /* Center an element vertically within a container */
        public static int centerY(int elementHeight, int containerHeight) {
            return snap((containerHeight - elementHeight) / 2);
        }

        /* Center element horizontally, returning absolute position */
        public static int centerInX(int elementWidth, int containerX, int containerWidth) {
            return containerX + centerX(elementWidth, containerWidth);
        }

        /* Center element vertically, returning absolute position */
        public static int centerInY(int elementHeight, int containerY, int containerHeight) {
            return containerY + centerY(elementHeight, containerHeight);
        }

        // --- Alignment ---

        /* Align element to left edge of container */
        public static int alignLeft(int containerX, int padding) {
            return containerX + snap(padding);
        }

        /* Align element to right edge of container */
        public static int alignRight(int elementWidth, int containerX, int containerWidth, int padding) {
            return containerX + containerWidth - elementWidth - snap(padding);
        }

        /* Align element to top edge of container */
        public static int alignTop(int containerY, int padding) {
            return containerY + snap(padding);
        }

        /* Align element to bottom edge of container */
        public static int alignBottom(int elementHeight, int containerY, int containerHeight, int padding) {
            return containerY + containerHeight - elementHeight - snap(padding);
        }

        // --- Responsive helpers ---

        /* Clamp dimension to valid range (grid-aligned) */
        public static int clampDimension(int value, int min, int max) {
            return snap(Math.max(min, Math.min(max, value)));
        }

        /* Calculate responsive width (percentage of container, grid-aligned) */
        public static int percentWidth(float percentage, int containerWidth) {
            return snap((int)(containerWidth * Math.max(0f, Math.min(1f, percentage))));
        }

        /* Calculate responsive height (percentage of container, grid-aligned) */
        public static int percentHeight(float percentage, int containerHeight) {
            return snap((int)(containerHeight * Math.max(0f, Math.min(1f, percentage))));
        }

        // --- Content bounds ---

        /* Calculate content area with uniform padding */
        public static int[] contentBounds(int x, int y, int width, int height, int padding) {
            int p = snap(padding);
            return new int[] { x + p, y + p, width - p * 2, height - p * 2 };
        }

        /* Calculate content area with separate horizontal/vertical padding */
        public static int[] contentBounds(int x, int y, int width, int height, int paddingH, int paddingV) {
            int ph = snap(paddingH);
            int pv = snap(paddingV);
            return new int[] { x + ph, y + pv, width - ph * 2, height - pv * 2 };
        }

        // --- Flow layout ---

        /* Calculate positions for horizontal flow layout */
        public static int[] flowHorizontal(int count, int itemWidth, int gap, int startX) {
            int[] positions = new int[count];
            int x = startX;
            for (int i = 0; i < count; i++) {
                positions[i] = x;
                x += itemWidth + gap;
            }
            return positions;
        }

        /* Calculate positions for vertical flow layout */
        public static int[] flowVertical(int count, int itemHeight, int gap, int startY) {
            int[] positions = new int[count];
            int y = startY;
            for (int i = 0; i < count; i++) {
                positions[i] = y;
                y += itemHeight + gap;
            }
            return positions;
        }

        /* Calculate total size of flow layout */
        public static int flowSize(int count, int itemSize, int gap) {
            if (count <= 0) return 0;
            return count * itemSize + (count - 1) * gap;
        }

        private Grid() {}
    }

    // ===========================================================================
    // COMPONENT DIMENSIONS
    // Aligned with EditorDimensions - these are the authoritative values
    // ===========================================================================

    public static final class Component {
        // Buttons (aligned with EditorDimensions)
        public static final int BUTTON_HEIGHT_SM = 20;   // 5 grid units
        public static final int BUTTON_HEIGHT_MD = 24;   // 6 grid units
        public static final int BUTTON_HEIGHT_LG = 32;   // 8 grid units
        public static final int BUTTON_MIN_WIDTH = 48;   // 12 grid units

        // Inputs (aligned with EditorDimensions)
        public static final int INPUT_HEIGHT = 20;       // 5 grid units
        public static final int INPUT_MIN_WIDTH = 60;    // 15 grid units
        public static final int TEXTAREA_MIN_HEIGHT = 64;

        // Toggles (aligned with EditorDimensions)
        public static final int TOGGLE_WIDTH = 36;       // 9 grid units
        public static final int TOGGLE_HEIGHT = 20;      // 5 grid units
        public static final int TOGGLE_THUMB_SIZE = 16;  // 4 grid units

        // Sliders (aligned with EditorDimensions)
        public static final int SLIDER_HEIGHT = 20;      // 5 grid units
        public static final int SLIDER_TRACK_HEIGHT = 4; // 1 grid unit
        public static final int SLIDER_THUMB_SIZE = 12;  // 3 grid units
        public static final int SLIDER_LABEL_WIDTH = 160;// 40 grid units

        // Tabs (aligned with EditorDimensions)
        public static final int TAB_HEIGHT = 24;         // 6 grid units
        public static final int TAB_MIN_WIDTH = 72;      // 18 grid units
        public static final int TAB_GAP = 4;             // 1 grid unit

        // Sections
        public static final int SECTION_HEADER_HEIGHT = 24;  // 6 grid units
        public static final int SECTION_MIN_HEIGHT = 48;     // 12 grid units

        // Lists & Rows
        public static final int LIST_ITEM_HEIGHT_COMPACT = 24;
        public static final int LIST_ITEM_HEIGHT_STANDARD = 32;
        /* Alias for LIST_ITEM_HEIGHT_COMPACT - use for compact rows */
        public static final int ROW_HEIGHT_COMPACT = LIST_ITEM_HEIGHT_COMPACT;
        /* Alias for LIST_ITEM_HEIGHT_STANDARD - use for standard rows */
        public static final int ROW_HEIGHT_STANDARD = LIST_ITEM_HEIGHT_STANDARD;

        // Scrollbar (aligned with EditorDimensions)
        public static final int SCROLLBAR_WIDTH = 8;     // 2 grid units
        public static final int SCROLLBAR_MIN_THUMB = 20;

        // Icons (aligned with EditorDimensions)
        public static final int ICON_SM = 12;            // 3 grid units
        public static final int ICON_MD = 16;            // 4 grid units
        public static final int ICON_LG = 24;            // 6 grid units

        // Slots
        public static final int SLOT_SIZE = 32;          // 8 grid units

        // Tooltips
        public static final int TOOLTIP_MAX_WIDTH = 260;

        // Modals
        public static final int MODAL_MIN_WIDTH = 280;
        public static final int MODAL_MAX_WIDTH = 480;

        private Component() {}
    }

    // ===========================================================================
    // LEGACY COMPATIBILITY (DesignTokens -> DesignTokens)
    // ===========================================================================

    public static final class Background {
        public static final int PANEL = withPanelAlpha(Bg.LEVEL_3);
        public static final int PANEL_SOLID = Bg.LEVEL_3;
        public static final int INPUT = Surface.LEVEL_0;
        public static final int HOVER = Surface.LEVEL_2;
        public static final int ACTIVE = Surface.LEVEL_3;
        public static final int DARKER = Bg.LEVEL_0;
        public static final int HEADER = Bg.LEVEL_2;
        public static final int CONTENT = Bg.LEVEL_3;
        public static final int TAB_INACTIVE = Surface.LEVEL_1;
        public static final int TAB_ACTIVE = Surface.LEVEL_2;
        public static final int OVERLAY = withAlpha(Palette.SHADOW, 0x80);
        public static final int SCREEN = PANEL;
        public static final int TOOLTIP = withAlpha(Surface.LEVEL_0, 0xF0);
        public static final int HUD_PANEL = withAlpha(PANEL_SOLID, 0xCC);
        public static final int GLOW = withAlpha(DesignTokens.Accent.PRIMARY, 0x55);

        public static int PANEL() { return ThemeManager.INSTANCE.panelBg(); }
        public static int PANEL_SOLID() { return ThemeManager.INSTANCE.panelBgSolid(); }
        public static int INPUT() { return ThemeManager.INSTANCE.inputBg(); }
        public static int HOVER() { return ThemeManager.INSTANCE.hoverBg(); }
        public static int ACTIVE() { return ThemeManager.INSTANCE.activeBg(); }
        public static int DARKER() { return ThemeManager.INSTANCE.current().darkerBackground(); }
        public static int HEADER() { return ThemeManager.INSTANCE.headerBg(); }
        public static int CONTENT() { return ThemeManager.INSTANCE.contentBg(); }
        public static int TAB_INACTIVE() { return ThemeManager.INSTANCE.current().tabInactiveBackground(); }
        public static int TAB_ACTIVE() { return ThemeManager.INSTANCE.current().tabActiveBackground(); }
        public static int OVERLAY() { return ThemeManager.INSTANCE.overlayBg(); }
        public static int SCREEN() { return ThemeManager.INSTANCE.panelBg(); }
        public static int TOOLTIP() { return withAlpha(ThemeManager.INSTANCE.inputBg(), 0xF0); }
        public static int HUD_PANEL() { return withAlpha(ThemeManager.INSTANCE.panelBgSolid(), 0xCC); }
        public static int GLOW() { return withAlpha(ThemeManager.INSTANCE.borderAccent(), 0x55); }

        private Background() {}
    }

    public static final class Border {
        public static final int DEFAULT = Stroke.DEFAULT;
        public static final int MUTED = Stroke.MUTED;
        public static final int ACCENT = DesignTokens.Accent.PRIMARY;
        public static final int SEPARATOR = Stroke.MUTED;
        public static final int SUCCESS = Semantic.SUCCESS;
        public static final int ERROR = Semantic.ERROR;
        public static final int WARNING = Semantic.WARNING;
        public static final int HOVER = Stroke.EMPHASIS;
        public static final int LIGHT = Stroke.EMPHASIS;
        public static final int GLOW = withAlpha(DesignTokens.Accent.PRIMARY, 0x55);

        public static int DEFAULT() { return ThemeManager.INSTANCE.border(); }
        public static int MUTED() { return ThemeManager.INSTANCE.borderMuted(); }
        public static int ACCENT() { return ThemeManager.INSTANCE.borderAccent(); }
        public static int SEPARATOR() { return ThemeManager.INSTANCE.separator(); }
        public static int SUCCESS() { return ThemeManager.INSTANCE.success(); }
        public static int ERROR() { return ThemeManager.INSTANCE.error(); }
        public static int WARNING() { return ThemeManager.INSTANCE.warning(); }
        public static int HOVER() { return ThemeManager.INSTANCE.current().borderHover(); }
        public static int LIGHT() { return ThemeManager.INSTANCE.current().borderHover(); }
        public static int GLOW() { return withAlpha(ThemeManager.INSTANCE.borderAccent(), 0x55); }

        private Border() {}
    }

    public static final class SliderColors {
        public static final int DAMAGE = Semantic.ERROR;
        public static final int DEFENSE = Semantic.INFO;
        public static final int SPEED = Semantic.SUCCESS;
        public static final int DURABILITY = Semantic.WARNING;
        public static final int SPECIAL = DesignTokens.Accent.SECONDARY;
        public static final int NEUTRAL = Text.SECONDARY;
        public static final int PERCENT = DesignTokens.Accent.PRIMARY;

        private SliderColors() {}
    }

    public static final class Slider {
        public static final int TRACK = Surface.LEVEL_1;
        public static final int TRACK_ACTIVE = Surface.LEVEL_2;
        public static final int TRACK_DISABLED = Surface.LEVEL_0;
        public static final int FILLED = DesignTokens.Accent.PRIMARY;
        public static final int THUMB = Surface.LEVEL_3;
        public static final int THUMB_HOVER = Surface.LEVEL_4;
        public static final int THUMB_DRAG = DesignTokens.Accent.PRIMARY;
        public static final int THUMB_DISABLED = Utility.DISABLED;

        private Slider() {}
    }

    public static final class Button {
        public static final int NORMAL = Surface.LEVEL_1;
        public static final int HOVER = Surface.LEVEL_2;
        public static final int PRESSED = Surface.LEVEL_0;
        public static final int DISABLED = Surface.LEVEL_0;
        public static final int FOCUSED = Surface.LEVEL_3;
        public static final int PRIMARY = Semantic.SUCCESS;
        public static final int PRIMARY_HOVER = lighten(PRIMARY, 0.08f);
        public static final int PRIMARY_PRESS = darken(PRIMARY, 0.10f);
        public static final int DANGER = Semantic.ERROR;
        public static final int DANGER_HOVER = lighten(DANGER, 0.08f);
        public static final int PRESS = PRESSED;

        public static int NORMAL() { return ThemeManager.INSTANCE.btnNormal(); }
        public static int HOVER() { return ThemeManager.INSTANCE.btnHover(); }
        public static int PRESSED() { return ThemeManager.INSTANCE.btnPressed(); }
        public static int DISABLED() { return ThemeManager.INSTANCE.btnDisabled(); }

        private Button() {}
    }

    public static final class Tab {
        public static final int NORMAL = Surface.LEVEL_1;
        public static final int HOVER = Surface.LEVEL_2;
        public static final int SELECTED = Surface.LEVEL_3;
        public static final int DISABLED = Surface.LEVEL_0;
        public static final int INDICATOR = DesignTokens.Accent.PRIMARY;

        private Tab() {}
    }

    public static final class Mode {
        public static final int GLOBAL_BORDER = Semantic.WARNING;
        public static final int GLOBAL_BG = withAlpha(Semantic.WARNING, 0x40);
        public static final int SPECIFIC_BORDER = Semantic.SUCCESS;
        public static final int SPECIFIC_BG = withAlpha(Semantic.SUCCESS, 0x40);
        public static final int PREVIEW_BORDER = Semantic.WARNING;
        public static final int PREVIEW_BG = withAlpha(Semantic.WARNING, 0x40);
        public static final int APPLY_BORDER = Semantic.SUCCESS;
        public static final int APPLY_BG = withAlpha(Semantic.SUCCESS, 0x40);

        private Mode() {}
    }

    public static final class Spacing {
        public static final int XS = Space._1;
        public static final int SM = Space._2;
        public static final int MD = Space._4;
        public static final int LG = Space._5;
        public static final int XL = Space._6;
        public static final int XXL = Space._7;
        public static final int PANEL_PADDING = SM;

        public static final int PADDING_XS = XS;
        public static final int PADDING_SM = SM;
        public static final int PADDING_MD = MD;
        public static final int PADDING_LG = LG;
        public static final int PADDING_XL = XL;
        public static final int GAP_SMALL = SM;
        public static final int GAP_MEDIUM = MD;
        public static final int GAP_LARGE = LG;
        public static final int LINE_HEIGHT = 11;
        public static final int SECTION_GAP = MD;
        public static final int HEADER_HEIGHT = 20;
        public static final int PANEL_MARGIN = 10;
        public static final int BUTTON_GAP = 25;

        private Spacing() {}
    }

    public static final class Size {
        public static final int BUTTON_HEIGHT = EditorDimensions.BTN_HEIGHT_SMALL;
        public static final int BUTTON_WIDTH = 80;
        public static final int TAB_HEIGHT = EditorDimensions.TAB_HEIGHT;
        public static final int TAB_WIDTH = EditorDimensions.TAB_MIN_WIDTH;
        public static final int TAB_GAP = EditorDimensions.TAB_GAP;
        public static final int SLIDER_HEIGHT = EditorDimensions.SLIDER_HEIGHT;
        public static final int SLIDER_THUMB = EditorDimensions.SLIDER_THUMB_SIZE;
        public static final int SLIDER_WIDTH = 200;
        public static final int INPUT_HEIGHT = EditorDimensions.INPUT_HEIGHT;
        public static final int LINE_HEIGHT = 10;
        public static final int HEADER_HEIGHT = EditorConstants.HEADER_HEIGHT;
        public static final int FOOTER_HEIGHT = EditorConstants.FOOTER_HEIGHT;
        public static final int ICON = EditorDimensions.ICON_NORMAL;
        public static final int ICON_SM = EditorDimensions.ICON_SMALL;
        public static final int ICON_LG = EditorDimensions.ICON_LARGE;
        public static final int SCROLLBAR_WIDTH = EditorDimensions.SCROLLBAR_WIDTH;
        public static final int SLOT_SIZE = EditorDimensions.SLOT_SIZE;
        public static final int SIDEBAR_WIDTH_COMPACT = 140;
        public static final int SIDEBAR_WIDTH_NARROW = 180;
        public static final int SIDEBAR_WIDTH = 200;
        public static final int CATEGORY_WIDTH = 150;
        public static final int TOGGLE_WIDTH = 40;
        public static final int TOGGLE_HEIGHT = 16;
        public static final int BUTTON_WIDTH_SMALL = 100;
        public static final int BUTTON_WIDTH_MEDIUM = 120;
        public static final int BUTTON_WIDTH_WIDE = 240;
        public static final int BUTTON_HEIGHT_COMPACT = 18;
        public static final int BUTTON_HEIGHT_PROMINENT = 28;
        public static final int BUTTON_HEIGHT_LARGE = 30;
        public static final int INPUT_WIDTH = 80;
        public static final int INPUT_WIDTH_WIDE = 120;
        public static final int LABEL_WIDTH = 90;
        public static final int PANEL_WIDTH = 220;
        public static final int PANEL_WIDTH_WIDE = 260;
        public static final int DIALOG_WIDTH_SMALL = 300;
        public static final int DIALOG_WIDTH_MEDIUM = 380;
        public static final int DIALOG_WIDTH_LARGE = 420;

        private Size() {}
    }

    // ===========================================================================
    // STANDARD PANEL ALPHA
    // ===========================================================================

    /* Standard alpha for semi-transparent panels (0xE0 = 224 = 87.8%) */
    public static final int PANEL_ALPHA = 0xE0;

    /* Apply standard panel alpha to a color */
    public static int withPanelAlpha(int color) {
        return (PANEL_ALPHA << 24) | (color & Mask.RGB);
    }

    // ===========================================================================
    // COLOR UTILITIES
    // ===========================================================================

    /* Set alpha on a color */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & Mask.RGB);
    }

    /* Get alpha from a color (0-255) */
    public static int getAlpha(int color) {
        return (color >> 24) & 0xFF;
    }

    /* Darken a color by a factor (0.0 = no change, 1.0 = black) */
    public static int darken(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * (1 - factor));
        int g = (int) (((color >> 8) & 0xFF) * (1 - factor));
        int b = (int) ((color & 0xFF) * (1 - factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /* Lighten a color by a factor (0.0 = no change, 1.0 = white) */
    public static int lighten(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) + (255 - (color & 0xFF)) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /* Interpolate between two colors */
    public static int lerp(int color1, int color2, float t) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /* Blend two colors with a factor */
    public static int blend(int color1, int color2, float factor) {
        return lerp(color1, color2, factor);
    }

    // ===========================================================================
    // TOGGLE COLORS
    // ===========================================================================

    public static final class Toggle {
        /* Toggle ON state - success green */
        public static final int ON = Semantic.SUCCESS;
        /* Toggle OFF state - input background */
        public static final int OFF = Surface.LEVEL_0;
        /* Toggle ON hover state - lightened success */
        public static final int ON_HOVER = lighten(ON, 0.15f);
        /* Toggle OFF hover state - surface hover */
        public static final int OFF_HOVER = Surface.LEVEL_2;
        /* Toggle track disabled */
        public static final int TRACK_DISABLED = Surface.LEVEL_0;
        /* Toggle thumb */
        public static final int THUMB = Stroke.DEFAULT;
        /* Toggle thumb disabled */
        public static final int THUMB_DISABLED = Utility.DISABLED;

        public static int ON() { return ThemeManager.INSTANCE.success(); }
        public static int OFF() { return ThemeManager.INSTANCE.inputBg(); }
        public static int ON_HOVER() { return lighten(ThemeManager.INSTANCE.success(), 0.15f); }
        public static int OFF_HOVER() { return ThemeManager.INSTANCE.hoverBg(); }

        private Toggle() {}
    }

    // ===========================================================================
    // BODY PART COLORS (for damage/hitbox visualization)
    // ===========================================================================

    public static final class BodyPart {
        public static final int HEAD = Palette.ERROR;
        public static final int BODY = Palette.SUCCESS;
        public static final int ARMS = Palette.WARNING;
        public static final int LEGS = Palette.ACCENT_BLUE;

        private BodyPart() {}
    }

    // ===========================================================================
    // STATUS COLORS (themed variants)
    // ===========================================================================

    public static final class Status {
        public static final int SUCCESS = Semantic.SUCCESS;
        public static final int ERROR = Semantic.ERROR;
        public static final int WARNING = Semantic.WARNING;
        public static final int INFO = Semantic.INFO;
        public static final int PENDING = DesignTokens.Text.MUTED;

        public static int SUCCESS() { return ThemeManager.INSTANCE.success(); }
        public static int ERROR() { return ThemeManager.INSTANCE.error(); }
        public static int WARNING() { return ThemeManager.INSTANCE.warning(); }
        public static int INFO() { return ThemeManager.INSTANCE.info(); }
        public static int PENDING() { return ThemeManager.INSTANCE.textMuted(); }

        private Status() {}
    }

    // ===========================================================================
    // DOMAIN PALETTES (feature-specific colors)
    // ===========================================================================

    public static final class Message {
        public static final int RGB_SUCCESS = Palette.SUCCESS & Mask.RGB;
        public static final int RGB_ERROR = Palette.ERROR & Mask.RGB;
        public static final int RGB_MUTED = Palette.TEXT_SECONDARY & Mask.RGB;

        private Message() {}
    }

    public static final class FollowRange {
        public static final int RED = Basic.RED;
        public static final int YELLOW = Basic.YELLOW;
        public static final int GREEN = Basic.GREEN;
        public static final int CYAN = Basic.CYAN;
        public static final int BLUE = Basic.BLUE;

        private FollowRange() {}
    }

    public static final class AttributeLog {
        public static final int GREEN = Palette.SUCCESS;
        public static final int RED = Palette.ERROR;
        public static final int YELLOW = Palette.WARNING;
        public static final int LIGHT_RED = Palette.ERROR;
        public static final int DARK_RED = Palette.ERROR;
        public static final int CRITICAL_RED = Palette.ERROR;
        public static final int ORANGE = Palette.ACCENT_AMBER;
        public static final int LIGHT_GREEN = Palette.SUCCESS;
        public static final int GRAY = Palette.TEXT_SECONDARY;
        public static final int MAGENTA = Palette.ACCENT_BLUE;

        private AttributeLog() {}
    }

    public static final class Shield {
        public static final int DEFAULT = Palette.ACCENT_BLUE;

        private Shield() {}
    }

    public static final class Alert {
        public static final int ERROR = Palette.ERROR;
        public static final int WARN = Palette.WARNING;
        public static final int INFO = Palette.ACCENT_BLUE;

        private Alert() {}
    }

    public static final class JourneyMap {
        public static final int SPAWN = Palette.SUCCESS;
        public static final int OBJECTIVE = Palette.ACCENT_BLUE;
        public static final int EXIT = Palette.ACCENT_AMBER;

        private JourneyMap() {}
    }

    public static final class Trail {
        public static final class Entity {
            public static final int ARROW = Accent.SECONDARY;
            public static final int POTION = Accent.BLUE;
            public static final int TRIDENT = Accent.PRIMARY;
            public static final int FIREWORK = Semantic.WARNING;
            public static final int WITHER_SKULL = Stroke.MUTED;
            public static final int FIREBALL = Semantic.ERROR;
            public static final int SMALL_FIREBALL = Semantic.ERROR;
            public static final int SHULKER_BULLET = Accent.BLUE;
            public static final int XP_ORB = Semantic.SUCCESS;
            public static final int ENDER_EYE = Accent.PRIMARY;
            public static final int ELYTRA = Accent.BLUE;
            public static final int DEFAULT = Text.PRIMARY;

            private Entity() {}
        }

        private Trail() {}
    }

    public static final class Combat {
        public static final class Text {
            public static final int PRIMARY = Palette.TEXT_PRIMARY;
            public static final int MUTED = Palette.TEXT_SECONDARY;
            public static final int WARNING = Palette.WARNING;

            private Text() {}
        }

        public static final class BodyPart {
            public static final int DEFAULT = Palette.TEXT_PRIMARY;
            public static final int HEAD = Palette.ERROR;
            public static final int BODY = Palette.SUCCESS;
            public static final int ARMS = Palette.WARNING;
            public static final int LEGS = Palette.ACCENT_BLUE;

            private BodyPart() {}
        }

        public static final class ImprintStage {
            public static final int OWNER = Palette.TEXT_SECONDARY;
            public static final int ENHANCED = Palette.ACCENT_TEAL;
            public static final int LEGENDARY = Palette.ACCENT_AMBER;
            public static final int ASCENDED = Palette.ACCENT_BLUE;

            private ImprintStage() {}
        }

        public static final class WeaponTrait {
            public static final int EXECUTIONER = Palette.ERROR;
            public static final int TYRANT_SLAYER = Palette.WARNING;
            public static final int STYLISH = Palette.ACCENT_TEAL;
            public static final int BLOODTHIRSTY = Palette.ERROR;
            public static final int HARMONIC = Palette.ACCENT_TEAL;
            public static final int PRECISION = Palette.ACCENT_BLUE;
            public static final int RELENTLESS = Palette.WARNING;
            public static final int GUARDIAN = Palette.SUCCESS;
            public static final int DEVASTATING = Palette.ERROR;
            public static final int FINISHER = Palette.ERROR;
            public static final int CLEAVING = Palette.WARNING;
            public static final int RETALIATING = Palette.ACCENT_BLUE;

            private WeaponTrait() {}
        }

        private Combat() {}
    }

    public static final class CombatPanel {
        public static final class Damage {
            public static final int CRITICAL = Semantic.ERROR;
            public static final int HIGH = Semantic.WARNING;
            public static final int MEDIUM = Accent.SECONDARY;
            public static final int LOW = DesignTokens.Text.PRIMARY;

            private Damage() {}
        }

        private CombatPanel() {}
    }

    public static final class TestingUi {
        public static final class Screen {
            public static final int BG = Bg.LEVEL_1;

            private Screen() {}
        }

        public static final class Accent {
            public static final int GOLD = DesignTokens.Accent.SECONDARY;

            private Accent() {}
        }

        public static final class Panel {
            public static final int BG = withAlpha(Bg.LEVEL_4, 0xE0);
            public static final int HEADER = Bg.LEVEL_4;
            public static final int HEADER_ALT = Bg.LEVEL_3;
            public static final int XP_BG = Surface.LEVEL_0;

            private Panel() {}
        }

        public static final class Scrollbar {
            public static final int TRACK = withAlpha(DesignTokens.Text.PRIMARY, 0x40);
            public static final int THUMB = withAlpha(DesignTokens.Text.PRIMARY, 0x80);

            private Scrollbar() {}
        }

        public static final class Hud {
            public static final int PANEL_BG = withAlpha(Bg.LEVEL_4, 0xE0);
            public static final int PANEL_BORDER = Stroke.DEFAULT;
            public static final int PANEL_HEADER_BG = Bg.LEVEL_4;

            public static final int TEXT_TITLE = DesignTokens.Accent.PRIMARY;
            public static final int TEXT_PRIMARY = DesignTokens.Text.PRIMARY;
            public static final int TEXT_SECONDARY = DesignTokens.Text.SECONDARY;
            public static final int TEXT_MUTED = DesignTokens.Text.MUTED;
            public static final int TEXT_SUCCESS = Semantic.SUCCESS;
            public static final int TEXT_WARNING = Semantic.WARNING;

            public static final int PROGRESS_BG = Surface.LEVEL_0;
            public static final int PROGRESS_FILL = DesignTokens.Accent.PRIMARY;
            public static final int PROGRESS_COMPLETE = Semantic.SUCCESS;

            public static final int HINT_BG = withAlpha(Palette.SHADOW, 0x80);
            public static final int NEW_TEST_GLOW_RGB = DesignTokens.Accent.PRIMARY & Mask.RGB;
            public static final int HEADER_IN_PROGRESS = withAlpha(Semantic.WARNING, 0x55);

            private Hud() {}
        }

        public static final class Notification {
            public static final int BG_RGB = Surface.LEVEL_1 & Mask.RGB;
            public static final int TEXT_RGB = DesignTokens.Text.PRIMARY & Mask.RGB;
            public static final int TEXT_MUTED_RGB = DesignTokens.Text.SECONDARY & Mask.RGB;

            public static final int TEST_PASSED = Semantic.SUCCESS;
            public static final int TEST_FAILED = Semantic.ERROR;
            public static final int ACHIEVEMENT = DesignTokens.Accent.SECONDARY;
            public static final int LEVEL_UP = DesignTokens.Accent.PRIMARY;
            public static final int BADGE = DesignTokens.Accent.SECONDARY;
            public static final int STREAK = DesignTokens.Accent.PRIMARY;
            public static final int XP_GAIN = Semantic.SUCCESS;

            private Notification() {}
        }

        public static final class Badge {
            public static final int UNREAD = Semantic.SUCCESS;

            private Badge() {}
        }

        private TestingUi() {}
    }

    public static final class ArenaTestWizard {
        public static final int UNKNOWN = Text.SECONDARY;
        public static final int STONE = Text.SECONDARY;
        public static final int WOOD = Accent.SECONDARY;
        public static final int GRASS = Semantic.SUCCESS;
        public static final int SAND = Accent.SECONDARY;
        public static final int DIRT = Stroke.MUTED;
        public static final int BRICK = Semantic.ERROR;
        public static final int IRON = Text.SECONDARY;
        public static final int GOLD = Accent.SECONDARY;
        public static final int DIAMOND = Accent.BLUE;
        public static final int EMERALD = Semantic.SUCCESS;
        public static final int OBSIDIAN = Bg.LEVEL_3;
        public static final int NETHER = Semantic.ERROR;
        public static final int END = Text.PRIMARY;
        public static final int PRISMARINE = Accent.PRIMARY;
        public static final int GLASS = withAlpha(Text.PRIMARY, 0x80);
        public static final int WOOL_WHITE = Text.PRIMARY;
        public static final int WOOL_BLACK = Bg.LEVEL_0;
        public static final int WOOL_RED = Semantic.ERROR;
        public static final int WOOL_BLUE = Semantic.INFO;
        public static final int WOOL_GREEN = Semantic.SUCCESS;
        public static final int WOOL_YELLOW = Semantic.WARNING;
        public static final int FALLBACK = Text.SECONDARY;

        private ArenaTestWizard() {}
    }

    public static final class BuildProgressHud {
        public static final class Panel {
            public static final int BACKGROUND = withAlpha(Bg.LEVEL_1, 0x80);
            public static final int BORDER = Stroke.DEFAULT;
            public static final int BAR_EMPTY = Surface.LEVEL_0;
            public static final int TEXT = DesignTokens.Text.PRIMARY;
            public static final int TEXT_SHADOW = Palette.SHADOW;

            private Panel() {}
        }

        public static final class Progress {
            public static final int NORMAL = Semantic.SUCCESS;
            public static final int WARNING = Semantic.WARNING;
            public static final int COMPLETE = Semantic.SUCCESS;
            public static final int FAILED = Semantic.ERROR;

            private Progress() {}
        }

        private BuildProgressHud() {}
    }

    public static final class Endurance {
        private static final int ACCENT = Palette.ACCENT_TEAL;
        private static final int ACCENT_ALT = Palette.ACCENT_BLUE;
        private static final int ACCENT_WARM = Palette.ACCENT_AMBER;
        private static final int SUCCESS = Palette.SUCCESS;
        private static final int WARNING = Palette.WARNING;
        private static final int ERROR = Palette.ERROR;
        private static final int MUTED = Palette.TEXT_SECONDARY;
        private static final int PRIMARY = Palette.TEXT_PRIMARY;

        public static final class Text {
            public static final int WHITE = Endurance.PRIMARY;
            public static final int MUTED = Endurance.MUTED;

            private Text() {}
        }

        public static final class Currency {
            public static final int TOKENS = ACCENT;
            public static final int COINS = ACCENT_WARM;
            public static final int PRESTIGE = ACCENT_ALT;
            public static final int GEMS = SUCCESS;
            public static final int BLOOD_GEMS = ERROR;

            private Currency() {}
        }

        public static final class LootTier {
            public static final int COMMON = MUTED;
            public static final int UNCOMMON = ACCENT;
            public static final int RARE = ACCENT_ALT;
            public static final int EPIC = ACCENT_WARM;
            public static final int LEGENDARY = SUCCESS;
            public static final int MYTHIC = ERROR;

            private LootTier() {}
        }

        public static final class RewardCategory {
            public static final int STATS = ACCENT;
            public static final int PERKS = ACCENT_ALT;
            public static final int UTILITY = ACCENT_WARM;
            public static final int COSMETICS = SUCCESS;

            private RewardCategory() {}
        }

        public static final class StyleRank {
            public static final int D = MUTED;
            public static final int C = ACCENT;
            public static final int B = ACCENT_ALT;
            public static final int A = ACCENT_WARM;
            public static final int S = SUCCESS;
            public static final int SS = WARNING;
            public static final int SSS = ERROR;

            private StyleRank() {}
        }

        public static final class Momentum {
            public static final int STAGNANT = MUTED;
            public static final int BUILDING = ACCENT;
            public static final int HEATED = WARNING;
            public static final int OVERDRIVE = ERROR;

            private Momentum() {}
        }

        public static final class Flow {
            public static final int STALE = MUTED;
            public static final int NEUTRAL = MUTED;
            public static final int FRESH = SUCCESS;
            public static final int VIRTUOSO = ACCENT_ALT;

            private Flow() {}
        }

        public static final class Tension {
            public static final int CALM = SUCCESS;
            public static final int BUILDING = ACCENT;
            public static final int MODERATE = WARNING;
            public static final int HIGH = ACCENT_WARM;
            public static final int CRITICAL = ERROR;
            public static final int DEFAULT = PRIMARY;

            private Tension() {}
        }

        public static final class Tide {
            public static final int CALM = SUCCESS;
            public static final int RISING = ACCENT;
            public static final int HIGH = WARNING;
            public static final int STORM = ERROR;
            public static final int APOCALYPSE = ERROR;

            private Tide() {}
        }

        public static final class Boss {
            public static final int BERSERKER = ERROR;
            public static final int SUMMONER = ACCENT_ALT;
            public static final int JUGGERNAUT = SUCCESS;
            public static final int ASSASSIN = ACCENT;
            public static final int ELEMENTAL = ACCENT_WARM;

            private Boss() {}
        }

        public static final class Contract {
            public static final int MINOR = MUTED;
            public static final int STANDARD = ACCENT;
            public static final int MAJOR = ACCENT_WARM;
            public static final int BLOOD = ERROR;

            private Contract() {}
        }

        public static final class Kit {
            public static final int STARTER = MUTED;
            public static final int WARRIOR = ERROR;
            public static final int RANGER = SUCCESS;
            public static final int TANK = ACCENT_ALT;
            public static final int MAGE = ACCENT;
            public static final int BERSERKER = ERROR;
            public static final int CUSTOM = ACCENT_WARM;

            private Kit() {}
        }

        public static final class Prestige {
            public static final int PERK_SLOT = ACCENT;
            public static final int MUTATOR_UNLOCK = ACCENT_WARM;
            public static final int ARENA_UNLOCK = ACCENT_ALT;
            public static final int COSMETIC_TITLE = ACCENT_WARM;
            public static final int STARTING_BONUS = SUCCESS;
            public static final int TOKEN_MULTIPLIER = ACCENT_WARM;
            public static final int EXCLUSIVE_PERK = ACCENT_ALT;

            private Prestige() {}
        }

        public static final class PerkRarity {
            public static final int COMMON = MUTED;
            public static final int UNCOMMON = ACCENT;
            public static final int RARE = ACCENT_ALT;
            public static final int EPIC = ACCENT_WARM;
            public static final int LEGENDARY = SUCCESS;

            private PerkRarity() {}
        }

        public static final class PerkCategory {
            public static final int OFFENSE = ERROR;
            public static final int DEFENSE = SUCCESS;
            public static final int UTILITY = ACCENT;
            public static final int VAMPIRIC = ERROR;
            public static final int ELEMENTAL = ACCENT_WARM;
            public static final int COMBO = ACCENT_ALT;
            public static final int CURSE = ERROR;

            private PerkCategory() {}
        }

        public static final class Mutator {
            public static final int POSITIVE = SUCCESS;
            public static final int NEGATIVE = ERROR;
            public static final int NEUTRAL = MUTED;
            public static final int CHAOTIC = WARNING;

            private Mutator() {}
        }

        public static final class Synergy {
            public static final int MINOR = MUTED;
            public static final int MODERATE = ACCENT;
            public static final int STRONG = ACCENT_WARM;
            public static final int LEGENDARY = SUCCESS;

            private Synergy() {}
        }

        public static final class ResonanceChain {
            public static final int DUO = ACCENT;
            public static final int TRINITY = ACCENT_ALT;
            public static final int APOCALYPSE = ERROR;
            public static final int DEFAULT = PRIMARY;

            private ResonanceChain() {}
        }

        public static final class Hazard {
            public static final int FIRE = WARNING;
            public static final int BLEED = ERROR;
            public static final int VOID = ACCENT_ALT;
            public static final int ARC = ACCENT;
            public static final int PSI = ACCENT_WARM;

            private Hazard() {}
        }

        public static final class Nemesis {
            public static final int PROJECTILE_DEFLECTION = ACCENT_ALT;
            public static final int SWEEPING_BLADE = ERROR;
            public static final int EARLY_PHASE_ACTIVATION = WARNING;
            public static final int DAMAGE_RESISTANCE = MUTED;
            public static final int PROTECTIVE_HELMET = ACCENT_WARM;
            public static final int IMPROVED_REFLEXES = SUCCESS;
            public static final int ENRAGED = ERROR;
            public static final int VETERAN = ACCENT_ALT;
            public static final int EVASION = ACCENT;
            public static final int REGENERATION = SUCCESS;
            public static final int SUMMONER = ACCENT_ALT;

            private Nemesis() {}
        }

        public static final class Bargain {
            public static final int GLASS_CANNON = ERROR;
            public static final int SLUGGISH = MUTED;
            public static final int FUMBLING = WARNING;
            public static final int HUNGER = ACCENT_WARM;
            public static final int ECHO_DAMAGE = ACCENT_ALT;
            public static final int BLOOD_TITHE = ERROR;
            public static final int CROWD_PRESSURE = ACCENT_ALT;
            public static final int FRAILTY = MUTED;
            public static final int BURNING_SOUL = WARNING;
            public static final int COMBO_BREAKER = ACCENT_WARM;
            public static final int MOMENTUM_DRAIN = ACCENT;
            public static final int ONE_SHOT = ERROR;
            public static final int ELITE_HUNTER = ACCENT_ALT;
            public static final int NO_HEALING = ERROR;
            public static final int EXECUTIONER = ERROR;

            private Bargain() {}
        }

        public static final class BargainTier {
            public static final int MINOR = LootTier.UNCOMMON;
            public static final int MAJOR = LootTier.LEGENDARY;
            public static final int CURSED = LootTier.MYTHIC;

            private BargainTier() {}
        }

        private Endurance() {}
    }


    public static final class EnduranceUi {
        public static final class Accent {
            public static final int ORANGE = DesignTokens.Accent.SECONDARY;
            public static final int PURPLE = DesignTokens.Accent.BLUE;
            public static final int GOLD = DesignTokens.Accent.SECONDARY;
            public static final int GOLD_RGB = DesignTokens.Accent.SECONDARY & Mask.RGB;

            private Accent() {}
        }

        public static final class QuestTier {
            public static final int HARD = Semantic.WARNING;
            public static final int ELITE = Semantic.ERROR;

            private QuestTier() {}
        }

        public static final class Challenge {
            public static final int HARD = Semantic.WARNING;

            private Challenge() {}
        }

        public static final class DeathScreen {
            public static final int BG = withAlpha(Bg.LEVEL_1, 0xEE);
            public static final int PANEL_BG = withAlpha(Bg.LEVEL_2, 0xDD);

            private DeathScreen() {}
        }

        public static final class CompletionScreen {
            public static final int BACKDROP_RGB = Bg.LEVEL_2 & Mask.RGB;
            public static final int PANEL_RGB = Bg.LEVEL_3 & Mask.RGB;
            public static final int GOLD_RGB = EnduranceUi.Accent.GOLD_RGB;

            private CompletionScreen() {}
        }

        public static final class PerkSelection {
            public static final int TAG_REQUIRED = Semantic.ERROR;
            public static final int TAG_OPTIONAL = DesignTokens.Accent.BLUE;

            public static final int SYNERGY_COMPLETE = Semantic.SUCCESS;
            public static final int SYNERGY_STRONG = DesignTokens.Accent.SECONDARY;
            public static final int SYNERGY_MODERATE = DesignTokens.Accent.PRIMARY;
            public static final int SYNERGY_MINOR = Text.SECONDARY;

            private PerkSelection() {}
        }

        public static final class KitSelection {
            public static final int ACCENT_PURPLE = DesignTokens.Accent.BLUE;
            public static final int BTN_SUCCESS_HOVER = Semantic.SUCCESS;
            public static final int BTN_SUCCESS_BORDER_HOVER = Semantic.SUCCESS;
            public static final int BTN_SUCCESS_BORDER = Semantic.SUCCESS;
            public static final int SCRIM = withAlpha(Palette.SHADOW, 0xAA);

            private KitSelection() {}
        }

        public static final class KitCategory {
            public static final int ALL = Text.PRIMARY;
            public static final int ARMOR = Semantic.INFO;
            public static final int WEAPONS = Semantic.ERROR;
            public static final int TOOLS = DesignTokens.Accent.SECONDARY;
            public static final int POTIONS = DesignTokens.Accent.BLUE;
            public static final int FOOD = Semantic.SUCCESS;
            public static final int COMBAT = Semantic.WARNING;
            public static final int BLOCKS = DesignTokens.Accent.PRIMARY;

            private KitCategory() {}
        }

        private EnduranceUi() {}
    }

    public static final class Mailbox {
        public static final class Panel {
            public static final int BG = withAlpha(Bg.LEVEL_1, 0xE8);

            private Panel() {}
        }

        public static final class Feathered {
            public static final int PAPER_BG = 0xFFE9D7B5;
            public static final int PAPER_INSET = 0xFFE1CDAA;
            public static final int PAPER_DETAIL = 0xFFF1E2C8;
            public static final int PAPER_BORDER = 0xFFB8A17D;
            public static final int PAPER_HOVER = 0xFFF4E7D0;
            public static final int PAPER_SELECTED = 0xFFEBD3AE;

            public static final int TEXT_PRIMARY = 0xFF3B2F25;
            public static final int TEXT_SECONDARY = 0xFF5A4A3A;
            public static final int TEXT_MUTED = 0xFF7B6A58;
            public static final int TEXT_ACCENT = 0xFF8B3D2C;

            public static final int TYPE_PLAYER = 0xFF3F5C8E;
            public static final int TYPE_SYSTEM = 0xFF5A4A3A;
            public static final int TYPE_ADMIN = 0xFF8D6A2F;
            public static final int TYPE_REWARD = 0xFF3D7B4B;

            private Feathered() {}
        }

        public static final class Divider {
            public static final int LINE = withAlpha(DesignTokens.Text.WHITE, DesignTokens.Alpha.A25);

            private Divider() {}
        }

        public static final class List {
            public static final int SELECTED_BG = withAlpha(Accent.PRIMARY, 0x40);
            public static final int HOVER_BG = withAlpha(Text.PRIMARY, 0x20);

            private List() {}
        }

        public static final class Scrollbar {
            public static final int TRACK = Divider.LINE;
            public static final int THUMB = withAlpha(DesignTokens.Text.WHITE, DesignTokens.Alpha.A50);

            private Scrollbar() {}
        }

        public static final class News {
            public static final int PATCH_NOTES = Semantic.SUCCESS;
            public static final int EVENTS = Accent.SECONDARY;
            public static final int ANNOUNCEMENTS = Semantic.INFO;
            public static final int MAINTENANCE = Semantic.ERROR;
            public static final int DEV_BLOG = Accent.BLUE;
            public static final int COMMUNITY = Accent.PRIMARY;

            private static final int[] CATEGORY_COLORS = {
                PATCH_NOTES,
                EVENTS,
                ANNOUNCEMENTS,
                MAINTENANCE,
                DEV_BLOG,
                COMMUNITY
            };

            public static int[] categoryColors() {
                return CATEGORY_COLORS.clone();
            }

            private News() {}
        }

        public static final class TesterTasks {
            public static final int PANEL_BG = withAlpha(Bg.LEVEL_1, 0xDD);
            public static final int PANEL_OUTLINE = DesignTokens.Neutral.N760;
            public static final int LIST_BG = Bg.LEVEL_0;
            public static final int SCROLLBAR = DesignTokens.Neutral.N700;

            public static final int ENTRY_DEFAULT = DesignTokens.Neutral.N880;
            public static final int ENTRY_HOVER = DesignTokens.Neutral.N820;
            public static final int ENTRY_SELECTED = withAlpha(Accent.PRIMARY, 0x40);

            public static final int TEXT_PRIMARY = DesignTokens.Text.WHITE;
            public static final int TEXT_MUTED = DesignTokens.Neutral.N500;
            public static final int TEXT_DIM = DesignTokens.Neutral.N550;

            public static final int DUE_OVERDUE = Semantic.ERROR;
            public static final int DUE_SOON = Semantic.WARNING;

            public static final int PRIORITY_HIGH = DUE_OVERDUE;
            public static final int PRIORITY_MEDIUM = DUE_SOON;
            public static final int PRIORITY_LOW = Semantic.SUCCESS;

            public static final int STATUS_PENDING = DesignTokens.Neutral.N500;
            public static final int STATUS_IN_PROGRESS = Semantic.INFO;
            public static final int STATUS_COMPLETED = PRIORITY_LOW;

            private TesterTasks() {}
        }

        private Mailbox() {}
    }

    // ===========================================================================
    // OVERLAY THEME TOKENS
    // ===========================================================================

    public static final class Overlay {
        public static final class Alpha {
            public static final int HEAVY = DesignTokens.Alpha.A88;
            public static final int STANDARD = DesignTokens.Alpha.A80;
            public static final int LIGHT = DesignTokens.Alpha.A67;
            public static final int SUBTLE = DesignTokens.Alpha.A50;
            public static final int GHOST = DesignTokens.Alpha.A33;
            public static final int DIVIDER = DesignTokens.Alpha.A27;
            public static final int GLOW = DesignTokens.Alpha.A25;

            private Alpha() {}
        }

        public static final class Panel {
            public static final int BG_BASE = Bg.LEVEL_2 & Mask.RGB;
            public static final int BG_STANDARD = (Overlay.Alpha.STANDARD << 24) | BG_BASE;
            public static final int BG_LIGHT = (Overlay.Alpha.LIGHT << 24) | BG_BASE;
            public static final int BG_HEAVY = (Overlay.Alpha.HEAVY << 24) | BG_BASE;
            public static final int BG_HEADER = Bg.LEVEL_3;

            public static int withAlpha(int alpha) {
                return DesignTokens.withAlpha(BG_BASE, alpha);
            }

            private Panel() {}
        }

        public static final class Border {
            public static final int ACCENT = Accent.PRIMARY;
            public static final int INFO = Semantic.INFO;
            public static final int SUCCESS = Semantic.SUCCESS;
            public static final int WARNING = Semantic.WARNING;
            public static final int ERROR = Semantic.ERROR;
            public static final int GOLD = Accent.SECONDARY;
            public static final int ENDURANCE = Accent.SECONDARY;
            public static final int MUTED = Stroke.MUTED;

            public static int glow(int borderColor) {
                return DesignTokens.withAlpha(borderColor, Overlay.Alpha.GLOW);
            }

            public static int divider(int borderColor) {
                return DesignTokens.withAlpha(borderColor, Overlay.Alpha.DIVIDER);
            }

            private Border() {}
        }

        public static final class Text {
            public static final int PRIMARY = DesignTokens.Text.PRIMARY;
            public static final int LIGHT = DesignTokens.Text.PRIMARY;
            public static final int TITLE = Accent.PRIMARY;
            public static final int MUTED = DesignTokens.Text.SECONDARY;
            public static final int HINT = DesignTokens.Text.MUTED;

            public static final int VALUE = Semantic.SUCCESS;
            public static final int VALUE_BRIGHT = Semantic.SUCCESS;

            public static final int WARNING = Semantic.WARNING;
            public static final int WARNING_ORANGE = Semantic.WARNING;
            public static final int DANGER = Semantic.ERROR;
            public static final int DANGER_BRIGHT = Semantic.ERROR;

            public static final int CYAN = Accent.PRIMARY;
            public static final int PURPLE = Semantic.INFO;
            public static final int GOLD = Accent.SECONDARY;

            private Text() {}
        }

        public static final class Neutral {
            public static final int N950 = DesignTokens.Neutral.N950;
            public static final int N920 = DesignTokens.Neutral.N920;
            public static final int N900 = DesignTokens.Neutral.N900;
            public static final int N880 = DesignTokens.Neutral.N880;
            public static final int N860 = DesignTokens.Neutral.N860;
            public static final int N840 = DesignTokens.Neutral.N840;
            public static final int N820 = DesignTokens.Neutral.N820;
            public static final int N800 = DesignTokens.Neutral.N800;
            public static final int N780 = DesignTokens.Neutral.N780;
            public static final int N760 = DesignTokens.Neutral.N760;
            public static final int N740 = DesignTokens.Neutral.N740;
            public static final int N700 = DesignTokens.Neutral.N700;
            public static final int N650 = DesignTokens.Neutral.N650;
            public static final int N600 = DesignTokens.Neutral.N600;
            public static final int N550 = DesignTokens.Neutral.N550;
            public static final int N500 = DesignTokens.Neutral.N500;
            public static final int N450 = DesignTokens.Neutral.N450;
            public static final int N400 = DesignTokens.Neutral.N400;

            private Neutral() {}
        }

        public static final class Progress {
            public static final int BG = Surface.LEVEL_0;
            public static final int BG_ALT = Surface.LEVEL_1;

            public static final int FILL = Accent.PRIMARY;
            public static final int FILL_GREEN = Semantic.SUCCESS;
            public static final int FILL_YELLOW = Semantic.WARNING;
            public static final int FILL_RED = Semantic.ERROR;
            public static final int FILL_ORANGE = Accent.SECONDARY;
            public static final int FILL_CYAN = Accent.PRIMARY;

            public static int byRatio(float ratio) {
                if (ratio > 0.6f) return FILL_GREEN;
                if (ratio > 0.3f) return FILL_YELLOW;
                return FILL_RED;
            }

            private Progress() {}
        }

        public static final class Status {
            public static final int RECORDING = Overlay.Text.DANGER;
            public static final int PAUSED = Overlay.Text.WARNING_ORANGE;
            public static final int ACTIVE = Border.SUCCESS;
            public static final int INACTIVE = Overlay.Text.HINT;

            private Status() {}
        }

        public static final class Endurance {
            public static final int PRIMARY = Accent.SECONDARY;
            public static final int LIGHT = withAlpha(Accent.SECONDARY, 0xAA);
            public static final int BG = withAlpha(Bg.LEVEL_2, 0xBB);
            public static final int BG_SURVIVE = withAlpha(Bg.LEVEL_2, 0xBB);
            public static final int BOSS_ALERT = Semantic.ERROR;

            private Endurance() {}
        }

        public static final class Economy {
            public static final int PRIMARY = Accent.SECONDARY;
            public static final int BG = withAlpha(Bg.LEVEL_2, 0xE0);

            private Economy() {}
        }

        public static final class Combat {
            public static final int IMPACT = Accent.PRIMARY;
            public static final int SECONDARY = Semantic.INFO;
            public static final int GLOW = Semantic.INFO;

            private Combat() {}
        }

        public static final class CombatRecap {
            public static final int BG = withAlpha(Bg.LEVEL_1, 0xF0);
            public static final int PANEL_BG = withAlpha(Bg.LEVEL_2, 0xE0);
            public static final int ACCENT = Accent.PRIMARY;
            public static final int TEXT_PRIMARY = Overlay.Text.PRIMARY;
            public static final int TEXT_SECONDARY = Overlay.Text.MUTED;
            public static final int TEXT_HIGHLIGHT = Accent.SECONDARY;

            public static final int BAR_DAMAGE = Semantic.ERROR;
            public static final int BAR_CRIT = Semantic.WARNING;
            public static final int BAR_HEADSHOT = Semantic.INFO;
            public static final int BAR_DPS = Semantic.SUCCESS;

            public static final int DIVIDER = Stroke.MUTED;
            public static final int BAR_BG = Surface.LEVEL_0;
            public static final int GRAPH_BG = Surface.LEVEL_1;

            private CombatRecap() {}
        }

        public static final class Impact3D {
            public static final int DPS = Semantic.SUCCESS;

            private Impact3D() {}
        }

        public static final class EpicFight {
            public static final int HEADER = Accent.SECONDARY;
            public static final int GUARD = Semantic.INFO;
            public static final int GUARD_FLASH = Overlay.Text.PRIMARY;
            public static final int PARRY = Semantic.WARNING;
            public static final int PARRY_FLASH = Overlay.Text.PRIMARY;
            public static final int PERFECT_PARRY = Accent.PRIMARY;
            public static final int PERFECT_PARRY_SECONDARY = Accent.SECONDARY;
            public static final int SKILL_NAME = Overlay.Text.MUTED;
            public static final int BATTLE_MODE = Semantic.WARNING;
            public static final int STAMINA_BG = Surface.LEVEL_0;
            public static final int STAMINA_FULL = Semantic.SUCCESS;
            public static final int STAMINA_MEDIUM = Semantic.WARNING;
            public static final int STAMINA_LOW = Accent.SECONDARY;
            public static final int STAMINA_EXHAUSTED = Semantic.ERROR;

            public static int getStaminaColor(float percent) {
                if (percent > 0.6f) return STAMINA_FULL;
                if (percent > 0.3f) return STAMINA_MEDIUM;
                if (percent > 0.1f) return STAMINA_LOW;
                return STAMINA_EXHAUSTED;
            }

            private EpicFight() {}
        }

        public static final class Impact {
            public static final int CORE_PRIMARY = Combat.IMPACT;
            public static final int CORE_SECONDARY = Combat.SECONDARY;
            public static final int CORE_GLOW = Combat.GLOW;
            public static final int SLASH = Combat.IMPACT;
            public static final int LINE = Combat.SECONDARY;

            public static final int HIGHLIGHT_SHADOW = withAlpha(Semantic.ERROR, 0x55);
            public static final int CALCULATED_SHADOW = withAlpha(Semantic.SUCCESS, 0x55);

            private Impact() {}
        }

        public static final class Help {
            public static final int TITLE = Semantic.SUCCESS;
            public static final int CATEGORY = Semantic.INFO;
            public static final int KEY_BG = Surface.LEVEL_0;
            public static final int HINT = Overlay.Text.MUTED;

            private Help() {}
        }

        public static final class Quest {
            public static final int PANEL_BG = Panel.BG_STANDARD;
            public static final int BORDER = Border.SUCCESS;
            public static final int BORDER_GLOW = DesignTokens.withAlpha(Border.SUCCESS, Overlay.Alpha.GHOST);

            public static final int TITLE = Semantic.SUCCESS;
            public static final int TEXT = Overlay.Text.PRIMARY;
            public static final int TASK = Accent.SECONDARY;
            public static final int NOTE = Overlay.Text.MUTED;
            public static final int PROGRESS = Semantic.INFO;
            public static final int COMPLETED = Border.SUCCESS;
            public static final int HINT = Overlay.Neutral.N650;

            private Quest() {}
        }

        public static final class Attribute {
            public static final int PANEL_BG = Panel.BG_STANDARD;
            public static final int BORDER = Border.ACCENT;
            public static final int BORDER_GLOW = DesignTokens.withAlpha(Border.ACCENT, Overlay.Alpha.GHOST);
            public static final int TITLE = Overlay.Text.TITLE;
            public static final int TEXT = Overlay.Text.PRIMARY;
            public static final int VALUE_GREEN = Overlay.Text.VALUE;
            public static final int VALUE_YELLOW = Overlay.Text.GOLD;
            public static final int VALUE_RED = Overlay.Text.DANGER;
            public static final int VALUE_GRAY = Overlay.Text.MUTED;
            public static final int VALUE_ORANGE = Accent.SECONDARY;
            public static final int SCALE = Accent.PRIMARY;
            public static final int EMPTY_LOG = Overlay.Neutral.N700;

            private Attribute() {}
        }

        public static final class BodyPart {
            public static final int HEAD = Semantic.ERROR;
            public static final int BODY = Semantic.SUCCESS;
            public static final int ARMS = Semantic.WARNING;
            public static final int LEGS = Semantic.INFO;

            private BodyPart() {}
        }

        public static final class Affix {
            public static final int SWIFT = Semantic.INFO;
            public static final int EMPOWERED = Semantic.ERROR;
            public static final int FORTIFIED = Semantic.SUCCESS;
            public static final int ARMORED = Overlay.Text.MUTED;
            public static final int BLAZING = Semantic.WARNING;
            public static final int PHANTOM = Accent.PRIMARY;
            public static final int REGENERATING = Semantic.SUCCESS;
            public static final int HORDE = Accent.SECONDARY;

            private Affix() {}
        }

        public static final class Momentum {
            public static final int NORMAL = Semantic.SUCCESS;
            public static final int HEATED = Semantic.WARNING;
            public static final int OVERDRIVE = Accent.PRIMARY;
            public static final int STAGNANT = Semantic.ERROR;

            private Momentum() {}
        }

        public static final class Contract {
            public static final int HEADER = Accent.SECONDARY;
            public static final int MULTIPLIER_HIGH = Semantic.ERROR;
            public static final int MULTIPLIER_MED = Semantic.WARNING;
            public static final int MULTIPLIER_LOW = Semantic.SUCCESS;
            public static final int VIOLATED = Overlay.Text.MUTED;
            public static final int STRIKETHROUGH = Semantic.ERROR;
            public static final int VIOLATED_MUTED = Overlay.Text.MUTED;
            public static final int SEPARATOR = withAlpha(Overlay.Text.PRIMARY, 0x44);
            public static final int MULTIPLIER_TEXT = Overlay.Text.PRIMARY;

            private Contract() {}
        }

        public static final class Stamina {
            public static final int BG = Surface.LEVEL_0;
            public static final int BORDER = Stroke.DEFAULT;
            public static final int FULL = Semantic.SUCCESS;
            public static final int MEDIUM = Semantic.WARNING;
            public static final int LOW = Accent.SECONDARY;
            public static final int EXHAUSTED = Semantic.ERROR;
            public static final int REGEN = Semantic.SUCCESS;

            private Stamina() {}
        }

        public static final class Debug {
            public static final int HITBOX = withAlpha(Semantic.WARNING, 0x80);
            public static final int AGGRO_SPHERE = withAlpha(Accent.PRIMARY, 0x80);

            public static final int WALL = withAlpha(Semantic.INFO, 0x44);

            public static final int LABEL = Text.PRIMARY;
            public static final int TITLE = Accent.SECONDARY;

            public static final int RANGE_HOSTILE = withAlpha(Semantic.ERROR, 0x40);
            public static final int RANGE_NEUTRAL = withAlpha(Semantic.WARNING, 0x40);
            public static final int RANGE_ATTACK = withAlpha(Semantic.ERROR, 0x60);
            public static final int RANGE_PASSIVE = withAlpha(Semantic.SUCCESS, 0x40);

            public static final int SAFE_SPOT_LABEL = Semantic.ERROR;

            public static final int LIGHT_SAFE = Semantic.SUCCESS;
            public static final int LIGHT_WARN = Semantic.WARNING;
            public static final int LIGHT_DANGER = Semantic.ERROR;

            public static final int SPAWN_YES = Semantic.ERROR;
            public static final int SPAWN_CONDITIONAL = Semantic.WARNING;
            public static final int SPAWN_NO = Semantic.SUCCESS;

            public static final int ENTITY_HEALTH_GOOD = Semantic.SUCCESS;
            public static final int ENTITY_HEALTH_MED = Semantic.WARNING;
            public static final int ENTITY_HEALTH_LOW = Semantic.ERROR;
            public static final int ENTITY_STAT = Overlay.Text.MUTED;
            public static final int ATTACK_REACH = Semantic.WARNING;
            public static final int ENTITY_HOSTILE = ENTITY_HEALTH_LOW;
            public static final int ENTITY_PASSIVE = ENTITY_HEALTH_GOOD;
            public static final int ENTITY_NEUTRAL = ENTITY_HEALTH_MED;
            public static final int ENTITY_NAME = Text.PRIMARY;
            public static final int ENTITY_HP = ENTITY_HEALTH_LOW;
            public static final int ENTITY_ARMOR = Semantic.INFO;
            public static final int ENTITY_DAMAGE = Semantic.ERROR;
            public static final int ENTITY_FOLLOW_RANGE = Semantic.SUCCESS;
            public static final int ENTITY_REACH_MODIFIED = Semantic.WARNING;
            public static final int ENTITY_REACH_VANILLA = ENTITY_STAT;
            public static final int ENTITY_TARGET = Accent.SECONDARY;

            public static final int ZONE_FLOOR = Semantic.SUCCESS;
            public static final int ZONE_MID = Semantic.WARNING;
            public static final int ZONE_HIGH = Semantic.ERROR;

            public static final int PATH_START = Accent.PRIMARY;
            public static final int PATH_DEST_OK = Semantic.SUCCESS;
            public static final int PATH_DEST_FAIL = Semantic.ERROR;
            public static final int PATH_INFO = Overlay.Text.MUTED;

            public static final int ROOM_RED = Semantic.ERROR;
            public static final int ROOM_GREEN = Semantic.SUCCESS;
            public static final int ROOM_BLUE = Semantic.INFO;
            public static final int ROOM_YELLOW = Semantic.WARNING;
            public static final int ROOM_MAGENTA = Accent.BLUE;
            public static final int ROOM_CYAN = Accent.PRIMARY;
            public static final int ROOM_ORANGE = Accent.SECONDARY;
            public static final int ROOM_PURPLE = Accent.BLUE;
            public static final int ROOM_GAP = Semantic.ERROR;

            private static final int[] ROOM_PALETTE = {
                ROOM_RED, ROOM_GREEN, ROOM_BLUE, ROOM_YELLOW,
                ROOM_MAGENTA, ROOM_CYAN, ROOM_ORANGE, ROOM_PURPLE
            };

            public static int[] roomPalette() {
                return ROOM_PALETTE.clone();
            }

            public static final int LOS_VISIBLE = Semantic.SUCCESS;
            public static final int LOS_OUT_OF_FOV = Semantic.WARNING;
            public static final int LOS_BLOCKED = Semantic.ERROR;

            public static final int ZONE_ENV_DEFAULT = Overlay.Text.MUTED;
            public static final int ZONE_ENV_NETHER = Semantic.ERROR;
            public static final int ZONE_ENV_END = Accent.BLUE;
            public static final int ZONE_ENV_ICE = Accent.BLUE;
            public static final int ZONE_ENV_DESERT = Accent.SECONDARY;
            public static final int ZONE_ENV_DESERT_WALL = Semantic.WARNING;
            public static final int ZONE_ENV_OCEAN = Semantic.INFO;
            public static final int ZONE_ENV_FOREST = Semantic.SUCCESS;
            public static final int ZONE_ENV_CAVE = Stroke.MUTED;
            public static final int ZONE_ENV_NIGHT = Bg.LEVEL_3;
            public static final int ZONE_ENV_DAY = Accent.SECONDARY;
            public static final int ZONE_ENV_DARK = Bg.LEVEL_0;
            public static final int ZONE_ENV_BRIGHT = Text.PRIMARY;
            public static final int ZONE_ENV_FALLBACK = Overlay.Text.MUTED;

            public static final int GAP_VOID = Bg.LEVEL_2;
            public static final int GAP_END = Bg.LEVEL_3;
            public static final int GAP_NETHER = Bg.LEVEL_2;
            public static final int GAP_DARK = Bg.LEVEL_0;

            private Debug() {}
        }

        public static final class Flash {
            public static final int HEADSHOT = Semantic.ERROR;
            public static final int CRITICAL = Semantic.WARNING;
            public static final int DAMAGE = Semantic.ERROR;
            public static final int HEAL = withAlpha(Semantic.SUCCESS, 0x44);
            public static final int SHIELD = withAlpha(Accent.PRIMARY, 0x44);

            private Flash() {}
        }

        public static final class Dimension {
            public static final int LINE_HEIGHT_COMPACT = 10;
            public static final int LINE_HEIGHT = 11;
            public static final int LINE_HEIGHT_READABLE = 14;

            public static final int PADDING_TIGHT = 6;
            public static final int PADDING = 8;
            public static final int PADDING_COMFORTABLE = 12;
            public static final int PADDING_SPACIOUS = 16;

            public static final int PROGRESS_BAR_HEIGHT = 8;
            public static final int PROGRESS_BAR_HEIGHT_SM = 4;

            private Dimension() {}
        }

        public static final class Utility {
            public static final int WHITE = DesignTokens.Text.WHITE;
            public static final int BLACK = (DesignTokens.Alpha.A100 << 24) | DesignTokens.Mask.NONE;
            public static final int SHADOW = (DesignTokens.Alpha.A25 << 24) | DesignTokens.Mask.NONE;
            public static final int SHADOW_LIGHT = withAlpha(Palette.SHADOW, 0x26);
            public static final int SHADOW_HEAVY = (DesignTokens.Alpha.A50 << 24) | DesignTokens.Mask.NONE;

            private Utility() {}
        }

        private Overlay() {}
    }

    public static final class RadialMenu {
        public static final class Core {
            public static final int BG_DARK = withAlpha(Bg.LEVEL_2, 0xF0);
            public static final int SELECTED_BG = withAlpha(Bg.LEVEL_3, 0xEE);
            public static final int MACRO_SELECTED_BASE = Bg.LEVEL_3;
            public static final int UNSELECTED_BG = withAlpha(Bg.LEVEL_2, 0xDD);
            public static final int BORDER = Stroke.DEFAULT;
            public static final int DIVIDER = Stroke.MUTED;
            public static final int MACRO_HOVER_BORDER = Stroke.EMPHASIS;
            public static final int INNER_RING = Bg.LEVEL_3;
            public static final int CLOSE_HOVER = Semantic.ERROR;
            public static final int CLOSE_NORMAL = withAlpha(Bg.LEVEL_2, 0xF0);
            public static final int CLOSE_BORDER_HOVER = Semantic.ERROR;
            public static final int CENTER_ICON_BACK = Accent.PRIMARY;
            public static final int TEXT_PRIMARY = Text.PRIMARY;
            public static final int TEXT_SECONDARY = Text.SECONDARY;
            public static final int INACTIVE = Text.MUTED;

            private Core() {}
        }

        public static final class Badge {
            public static final int BG = withAlpha(Palette.SHADOW, 0xDD);

            private Badge() {}
        }

        public static final class Favorites {
            public static final int BG_SELECTED = withAlpha(Accent.SECONDARY, 0xDD);
            public static final int BG_UNSELECTED = withAlpha(Accent.SECONDARY, 0x88);
            public static final int STAR = Accent.SECONDARY;

            private Favorites() {}
        }

        public static final class Item {
            public static final int STATUS_INACTIVE = Text.MUTED;

            private Item() {}
        }

        public static final class Overlay {
            public static final int BACKGROUND_RGB = Bg.LEVEL_1 & Mask.RGB;
            public static final int TOOLTIP_BG = withAlpha(Surface.LEVEL_0, 0xF0);
            public static final int SEARCH_BOX_BG = withAlpha(Surface.LEVEL_0, 0xEE);
            public static final int SEARCH_RESULT_BG = withAlpha(Surface.LEVEL_0, 0xCC);
            public static final int BREADCRUMB = Text.PRIMARY;
            public static final int EDIT_MODE_BG = withAlpha(Palette.SHADOW, 0xCC);
            public static final int EDIT_MODE_TEXT = Semantic.ERROR;
            public static final int THEME_INDICATOR_RGB = Text.PRIMARY & Mask.RGB;

            private Overlay() {}
        }

        public static final class Base {
            public static final int BG_DARK = withAlpha(Bg.LEVEL_1, 0xE6);
            public static final int BG_LIGHT = withAlpha(Bg.LEVEL_2, 0xCC);
            public static final int SELECTED = withAlpha(Bg.LEVEL_3, 0xDD);
            public static final int HOVER = withAlpha(Bg.LEVEL_3, 0xEE);
            public static final int ACTIVE = Accent.PRIMARY;
            public static final int ACTIVE_GLOW = withAlpha(Accent.PRIMARY, 0x44);
            public static final int INACTIVE = Text.MUTED;
            public static final int TEXT_PRIMARY = Text.PRIMARY;
            public static final int TEXT_SECONDARY = Text.SECONDARY;
            public static final int TEXT_HIGHLIGHT = Accent.SECONDARY;
            public static final int BORDER = Stroke.DEFAULT;
            public static final int BORDER_GLOW = withAlpha(Text.PRIMARY, 0x40);
            private static final int[] CATEGORY_COLORS = {
                Accent.BLUE,
                Accent.SECONDARY,
                Accent.PRIMARY,
                Semantic.ERROR,
                Semantic.WARNING,
                Semantic.SUCCESS
            };

            public static int[] categoryColors() {
                return CATEGORY_COLORS.clone();
            }

            private Base() {}
        }

        public static final class PresetValues {
            public final int bgDark;
            public final int bgLight;
            public final int selected;
            public final int hover;
            public final int active;
            public final int activeGlow;
            public final int border;
            public final int textPrimary;
            public final int textSecondary;

            private PresetValues(int bgDark, int bgLight, int selected, int hover,
                                 int active, int activeGlow, int border,
                                 int textPrimary, int textSecondary) {
                this.bgDark = bgDark;
                this.bgLight = bgLight;
                this.selected = selected;
                this.hover = hover;
                this.active = active;
                this.activeGlow = activeGlow;
                this.border = border;
                this.textPrimary = textPrimary;
                this.textSecondary = textSecondary;
            }
        }

        public static final class Presets {
            public static final PresetValues DEFAULT = new PresetValues(
                withAlpha(Bg.LEVEL_1, 0xE6), withAlpha(Bg.LEVEL_2, 0xCC), withAlpha(Bg.LEVEL_3, 0xDD), withAlpha(Bg.LEVEL_3, 0xEE),
                Accent.PRIMARY, withAlpha(Accent.PRIMARY, 0x44), Stroke.DEFAULT, Text.PRIMARY, Text.SECONDARY
            );

            public static final PresetValues NEON = new PresetValues(
                withAlpha(Bg.LEVEL_1, 0xE6), withAlpha(Bg.LEVEL_2, 0xCC), withAlpha(Bg.LEVEL_3, 0xDD), withAlpha(Bg.LEVEL_3, 0xEE),
                Semantic.INFO, withAlpha(Semantic.INFO, 0x44), Stroke.EMPHASIS, Text.PRIMARY, Text.SECONDARY
            );

            public static final PresetValues CRIMSON = new PresetValues(
                withAlpha(Bg.LEVEL_1, 0xE6), withAlpha(Bg.LEVEL_2, 0xCC), withAlpha(Bg.LEVEL_3, 0xDD), withAlpha(Bg.LEVEL_3, 0xEE),
                Semantic.ERROR, withAlpha(Semantic.ERROR, 0x44), Stroke.DEFAULT, Text.PRIMARY, Text.SECONDARY
            );

            public static final PresetValues FOREST = new PresetValues(
                withAlpha(Bg.LEVEL_1, 0xE6), withAlpha(Bg.LEVEL_2, 0xCC), withAlpha(Bg.LEVEL_3, 0xDD), withAlpha(Bg.LEVEL_3, 0xEE),
                Semantic.SUCCESS, withAlpha(Semantic.SUCCESS, 0x44), Stroke.DEFAULT, Text.PRIMARY, Text.SECONDARY
            );

            public static final PresetValues GOLD = new PresetValues(
                withAlpha(Bg.LEVEL_1, 0xE6), withAlpha(Bg.LEVEL_2, 0xCC), withAlpha(Bg.LEVEL_3, 0xDD), withAlpha(Bg.LEVEL_3, 0xEE),
                Accent.SECONDARY, withAlpha(Accent.SECONDARY, 0x44), Stroke.DEFAULT, Text.PRIMARY, Text.SECONDARY
            );

            public static final PresetValues MIDNIGHT = new PresetValues(
                withAlpha(Bg.LEVEL_1, 0xE6), withAlpha(Bg.LEVEL_2, 0xCC), withAlpha(Bg.LEVEL_3, 0xDD), withAlpha(Bg.LEVEL_3, 0xEE),
                Accent.BLUE, withAlpha(Accent.BLUE, 0x44), Stroke.DEFAULT, Text.PRIMARY, Text.SECONDARY
            );

            public static final PresetValues MINIMAL = new PresetValues(
                withAlpha(Bg.LEVEL_1, 0xE6), withAlpha(Bg.LEVEL_2, 0xCC), withAlpha(Bg.LEVEL_3, 0xDD), withAlpha(Bg.LEVEL_3, 0xEE),
                Text.PRIMARY, withAlpha(Text.PRIMARY, 0x44), Stroke.MUTED, Text.PRIMARY, Text.SECONDARY
            );

            public static final PresetValues COLORBLIND = new PresetValues(
                withAlpha(Bg.LEVEL_1, 0xE6), withAlpha(Bg.LEVEL_2, 0xCC), withAlpha(Bg.LEVEL_3, 0xDD), withAlpha(Bg.LEVEL_3, 0xEE),
                Palette.ACCENT_BLUE, withAlpha(Palette.ACCENT_BLUE, 0x44), Stroke.EMPHASIS, Text.PRIMARY, Text.SECONDARY
            );

            public static final PresetValues HIGH_CONTRAST = new PresetValues(
                Palette.SHADOW, withAlpha(Palette.SURFACE, 0xFF), withAlpha(Palette.PANEL, 0xFF), withAlpha(Palette.PANEL_ELEVATED, 0xFF),
                Palette.ACCENT_AMBER, withAlpha(Palette.ACCENT_AMBER, 0x44), Palette.TEXT_PRIMARY, Palette.TEXT_PRIMARY, Palette.TEXT_SECONDARY
            );

            private Presets() {}
        }

        private RadialMenu() {}
    }

    // ===========================================================================
    // POSITION CONSTANTS (layout helpers)
    // ===========================================================================

    public static final class Position {
        public static final int TITLE_Y = 8;
        public static final int SUBTITLE_Y = 45;
        public static final int CONTENT_START_Y = 60;
        public static final int BOTTOM_MARGIN = 30;

        private Position() {}
    }

    // ===========================================================================
    // PANEL DIMENSIONS (from EditorConstants)
    // ===========================================================================

    public static final class PanelDimensions {
        public static final int PANEL_WIDTH = EditorConstants.PANEL_WIDTH;
        public static final int PANEL_HEIGHT = EditorConstants.PANEL_HEIGHT;
        public static final int LEFT_COLUMN_WIDTH = EditorConstants.LEFT_COLUMN_WIDTH;
        public static final int CONTENT_WIDTH = EditorConstants.CONTENT_WIDTH;
        public static final int CONTENT_HEIGHT = EditorConstants.CONTENT_HEIGHT;
        public static final int PREVIEW_SIZE = EditorConstants.PREVIEW_SIZE;
        public static final int SLOT_AREA_HEIGHT = EditorConstants.SLOT_AREA_HEIGHT;
        public static final int INFO_PANEL_HEIGHT = EditorConstants.INFO_PANEL_HEIGHT;

        private PanelDimensions() {}
    }

    // ===========================================================================
    // ANIMATION TIMING (all 0 for immediate mode)
    // ===========================================================================

    public static final class Timing {
        /* Fade duration (disabled) */
        public static final int FADE_MS = 0;
        /* Slide duration (disabled) */
        public static final int SLIDE_MS = 0;
        /* Tooltip delay */
        public static final int TOOLTIP_DELAY_MS = 200;
        /* Button press feedback */
        public static final int BUTTON_PRESS_MS = 0;

        private Timing() {}
    }

    // ===========================================================================
    // ADDITIONAL UTILITY METHODS
    // ===========================================================================

    /*
     * Set alpha on a color (alias for withAlpha for compatibility).
     */
    public static int setAlpha(int color, int alpha) {
        return withAlpha(color, alpha);
    }

    /*
     * Get health color based on percentage.
     * @param healthPercent Health percentage (0-100)
     * @return Green if > 50%, Yellow if > 25%, Red otherwise
     */
    public static int getHealthColor(float healthPercent) {
        if (healthPercent > 50) return Semantic.SUCCESS;
        if (healthPercent > 25) return Semantic.WARNING;
        return Semantic.ERROR;
    }

    /*
     * Calculate centered X position.
     */
    public static int centerX(int screenWidth, int elementWidth) {
        return (screenWidth - elementWidth) / 2;
    }

    /*
     * Calculate tab start X position for centered tabs.
     */
    public static int tabStartX(int screenWidth, int tabCount, int tabWidth) {
        return (screenWidth - (tabCount * tabWidth)) / 2;
    }

    // ===========================================================================
    // NEXUS HUB COLORS
    // ===========================================================================

    /**
     * Colors for the Nexus hub dimension UI elements.
     * Includes dialog screens, holograms, and portal visuals.
     */
    public static final class Nexus {

        // --- Dialog Screen ---
        /** Dialog panel background (darker, high opacity) */
        public static final int DIALOG_PANEL_BG = 0xEE0A1018;
        /** Dialog border (blue) */
        public static final int DIALOG_BORDER = 0xFF4488FF;
        /** Dialog border glow (semi-transparent blue) */
        public static final int DIALOG_BORDER_GLOW = 0x444488FF;
        /** Speaker name text (light blue) */
        public static final int DIALOG_SPEAKER = 0xFF88CCFF;

        // --- Hologram Titles ---
        /** Primary title (gold/orange) */
        public static final int TITLE_GOLD = 0xFFFFAA00;
        /** Subtitle text (light gray) */
        public static final int SUBTITLE_GRAY = 0xFFCCCCCC;
        /** Hint text (light blue) */
        public static final int HINT_BLUE = 0xFF88CCFF;
        /** Hint text (light green) */
        public static final int HINT_GREEN = 0xFF88FF88;
        /** Leaderboard gold */
        public static final int LEADERBOARD_GOLD = 0xFFFFD700;
        /** Placeholder/inactive text (medium gray) */
        public static final int PLACEHOLDER_GRAY = 0xFFAAAAAA;
        /** Footer/timestamp text (dark gray) */
        public static final int FOOTER_GRAY = 0xFF666666;
        /** Zone status title (cyan) */
        public static final int ZONE_TITLE = 0xFF44FFFF;
        /** Announcement title (pink/magenta) */
        public static final int ANNOUNCEMENT_TITLE = 0xFFFF88FF;
        /** Hologram background (semi-transparent black) */
        public static final int HOLOGRAM_BG = 0xAA000000;

        // --- Avatar Display ---
        /** Avatar name label (light cyan) - used for TextDisplay over NPCs */
        public static final int AVATAR_LABEL = 0x7AD7FF;

        // --- Zone Colors ---
        /** Combat zone (red) */
        public static final int ZONE_COMBAT = 0xFFFF4444;
        /** Arena zone (orange) */
        public static final int ZONE_ARENA = 0xFFFF8800;
        /** UI zone (blue) */
        public static final int ZONE_UI = 0xFF4488FF;
        /** Telemetry zone (green) */
        public static final int ZONE_TELEMETRY = 0xFF44FF44;
        /** Showcase zone (yellow) */
        public static final int ZONE_SHOWCASE = 0xFFFFFF44;
        /** Integration zone (purple) */
        public static final int ZONE_INTEGRATION = 0xFFAA44FF;
        /** Sandbox zone (cyan) */
        public static final int ZONE_SANDBOX = 0xFF44FFFF;
        /** Mechanics zone (gray) */
        public static final int ZONE_MECHANICS = 0xFFAAAAAA;

        // --- Leaderboard Medals ---
        /** Gold medal (1st place) */
        public static final int MEDAL_GOLD = 0xFFFFD700;
        /** Silver medal (2nd place) */
        public static final int MEDAL_SILVER = 0xFFC0C0C0;
        /** Bronze medal (3rd place) */
        public static final int MEDAL_BRONZE = 0xFFCD7F32;

        // --- Fallback ---
        /** Default white color */
        public static final int WHITE = 0xFFFFFFFF;

        private Nexus() {}
    }
}
