package com.devmod.client.ui.editor.core;

import com.devmod.shared.SharedColorTokens;

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
    // COLOR TOKENS - BACKGROUNDS (5 levels)
    // ===========================================================================

    public static final class Bg {
        /* Darkest - scrim/overlay backdrop */
        public static final int LEVEL_0 = 0xFF050508;
        /* Screen background */
        public static final int LEVEL_1 = 0xFF0A0A0F;
        /* Panel background */
        public static final int LEVEL_2 = 0xFF101018;
        /* Card/section background */
        public static final int LEVEL_3 = 0xFF181820;
        /* Elevated element */
        public static final int LEVEL_4 = 0xFF202028;

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
        public static final int LEVEL_0 = 0xFF1A1A24;
        /* Default surface */
        public static final int LEVEL_1 = 0xFF242430;
        /* Hover state */
        public static final int LEVEL_2 = 0xFF2E2E3C;
        /* Active/pressed */
        public static final int LEVEL_3 = 0xFF383848;
        /* Highlighted */
        public static final int LEVEL_4 = 0xFF424254;

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
        public static final int MUTED = 0xFF2A2A38;
        /* Default borders */
        public static final int DEFAULT = 0xFF3A3A4C;
        /* Emphasized borders */
        public static final int EMPHASIS = 0xFF4A4A60;

        private Stroke() {}
    }

    // ===========================================================================
    // COLOR TOKENS - TEXT
    // ===========================================================================

    public static final class Text {
        /* Main text, titles */
        public static final int PRIMARY = 0xFFE8E8EC;
        /* Labels, captions */
        public static final int SECONDARY = 0xFFA8A8B4;
        /* Hints, disabled */
        public static final int MUTED = 0xFF686878;
        /* Text on light backgrounds */
        public static final int INVERSE = 0xFF101018;
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
        public static final int WHITE = 0xFFFFFFFF;
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
    // COLOR TOKENS - ACCENTS
    // ===========================================================================

    public static final class Accent {
        /* Primary accent (cyan) */
        public static final int PRIMARY = 0xFF00D4FF;
        /* Secondary accent (magenta) */
        public static final int SECONDARY = 0xFFFF00AA;
        /* Glow effect (25% alpha) */
        public static final int GLOW = 0x4000D4FF;
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
        public static final int PURPLE = SECONDARY;
        /* Highlight accent */
        public static final int YELLOW = Semantic.WARNING;
        /* Gold accent */
        public static final int GOLD = 0xFFFFD700;

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
        public static final int SUCCESS = 0xFF4ADE80;
        /* Success background (muted) */
        public static final int SUCCESS_MUTED = 0x404ADE80;

        /* Warning states */
        public static final int WARNING = 0xFFFACC15;
        /* Warning background (muted) */
        public static final int WARNING_MUTED = 0x40FACC15;

        /* Error states */
        public static final int ERROR = 0xFFF87171;
        /* Error background (muted) */
        public static final int ERROR_MUTED = 0x40F87171;

        /* Info states */
        public static final int INFO = 0xFF60A5FA;
        /* Info background (muted) */
        public static final int INFO_MUTED = 0x4060A5FA;

        private Semantic() {}
    }

    // ===========================================================================
    // EDITOR THEME COLORS (theme-specific overrides)
    // ===========================================================================

    public static final class EditorTheme {
        public static final class Shared {
            public static final int DARKER_BACKGROUND = 0xFF0D0D0D;
            public static final int TEXT_VALUE = 0xFFB0E0E6;
            public static final int TEXT_FORMULA = 0xFF98D4A4;
            public static final int ACCENT_PRIMARY = DesignTokens.Accent.PRIMARY;
            public static final int ACCENT_SUCCESS = 0xFF4CAF50;
            public static final int ACCENT_WARNING = 0xFFFF9800;
            public static final int ACCENT_ERROR = 0xFFE53935;
            public static final int ACCENT_INFO = 0xFF2196F3;

            private Shared() {}
        }

        public static final class Dark {
            public static final int PANEL_BG = 0xE0181818;
            public static final int PANEL_BG_SOLID = 0xFF181818;
            public static final int INPUT_BG = 0xFF252525;
            public static final int HOVER_BG = 0xFF353535;
            public static final int ACTIVE_BG = 0xFF454545;
            public static final int HEADER_BG = 0xFF1A1A1A;
            public static final int CONTENT_BG = 0xFF202020;
            public static final int TAB_INACTIVE_BG = 0xFF282828;
            public static final int TAB_ACTIVE_BG = 0xFF383838;
            public static final int OVERLAY_BG = 0x80000000;

            public static final int BORDER_DEFAULT = 0xFF3A3A3A;
            public static final int BORDER_MUTED = 0xFF2A2A2A;
            public static final int BORDER_ACCENT = 0xFF00D4FF;
            public static final int BORDER_SEPARATOR = 0xFF333333;
            public static final int BORDER_HOVER = 0xFF5A5A5A;

            public static final int TEXT_PRIMARY = 0xFFE0E0E0;
            public static final int TEXT_SECONDARY = 0xFFAAAAAA;
            public static final int TEXT_MUTED = 0xFF666666;
            public static final int TEXT_TITLE = 0xFFFFFFFF;
            public static final int TEXT_DISABLED = 0xFF555555;

            public static final int BUTTON_NORMAL = 0xFF2A2A2A;
            public static final int BUTTON_HOVER = 0xFF3A3A3A;
            public static final int BUTTON_PRESSED = 0xFF1A1A1A;
            public static final int BUTTON_DISABLED = 0xFF1A1A1A;

            public static final int SLIDER_TRACK = 0xFF2A2A2A;
            public static final int SLIDER_THUMB = 0xFF5A5A5A;
            public static final int SLIDER_THUMB_HOVER = 0xFF7A7A7A;

            private Dark() {}
        }

        public static final class Light {
            public static final int PANEL_BG = 0xE0F5F5F5;
            public static final int PANEL_BG_SOLID = 0xFFF5F5F5;
            public static final int INPUT_BG = 0xFFFFFFFF;
            public static final int HOVER_BG = 0xFFE8E8E8;
            public static final int ACTIVE_BG = 0xFFDDDDDD;
            public static final int HEADER_BG = 0xFFEEEEEE;
            public static final int CONTENT_BG = 0xFFF0F0F0;
            public static final int TAB_INACTIVE_BG = 0xFFE0E0E0;
            public static final int TAB_ACTIVE_BG = 0xFFD0D0D0;
            public static final int OVERLAY_BG = 0x60000000;

            public static final int BORDER_DEFAULT = 0xFFCCCCCC;
            public static final int BORDER_MUTED = 0xFFDDDDDD;
            public static final int BORDER_ACCENT = 0xFF0099CC;
            public static final int BORDER_SEPARATOR = 0xFFD5D5D5;
            public static final int BORDER_HOVER = 0xFFAAAAAA;

            public static final int TEXT_PRIMARY = 0xFF2A2A2A;
            public static final int TEXT_SECONDARY = 0xFF555555;
            public static final int TEXT_MUTED = 0xFF888888;
            public static final int TEXT_TITLE = 0xFF000000;
            public static final int TEXT_DISABLED = 0xFFAAAAAA;

            public static final int ACCENT_PRIMARY = 0xFF0099CC;
            public static final int ACCENT_SUCCESS = 0xFF388E3C;
            public static final int ACCENT_WARNING = 0xFFE65100;
            public static final int ACCENT_ERROR = 0xFFC62828;
            public static final int ACCENT_INFO = 0xFF1565C0;

            public static final int BUTTON_NORMAL = 0xFFE0E0E0;
            public static final int BUTTON_HOVER = 0xFFD0D0D0;
            public static final int BUTTON_PRESSED = 0xFFC0C0C0;
            public static final int BUTTON_DISABLED = 0xFFEEEEEE;

            public static final int SLIDER_TRACK = 0xFFD0D0D0;
            public static final int SLIDER_THUMB = 0xFF888888;
            public static final int SLIDER_THUMB_HOVER = 0xFF666666;

            private Light() {}
        }

        public static final class HighContrast {
            public static final int PANEL_BG = 0xFF000000;
            public static final int PANEL_BG_SOLID = 0xFF000000;
            public static final int INPUT_BG = 0xFF000000;
            public static final int HOVER_BG = 0xFF1A1A1A;
            public static final int ACTIVE_BG = 0xFF333333;
            public static final int HEADER_BG = 0xFF000000;
            public static final int CONTENT_BG = 0xFF000000;
            public static final int TAB_INACTIVE_BG = 0xFF000000;
            public static final int TAB_ACTIVE_BG = 0xFF1A1A1A;
            public static final int OVERLAY_BG = 0xE0000000;
            public static final int DARKER_BACKGROUND = 0xFF000000;

            public static final int BORDER_DEFAULT = 0xFFFFFFFF;
            public static final int BORDER_MUTED = 0xFFAAAAAA;
            public static final int BORDER_ACCENT = 0xFFFFFF00;
            public static final int BORDER_SEPARATOR = 0xFFFFFFFF;
            public static final int BORDER_HOVER = 0xFFFFFF00;

            public static final int TEXT_PRIMARY = 0xFFFFFFFF;
            public static final int TEXT_SECONDARY = 0xFFFFFFFF;
            public static final int TEXT_MUTED = 0xFFCCCCCC;
            public static final int TEXT_TITLE = 0xFFFFFFFF;
            public static final int TEXT_DISABLED = 0xFF888888;
            public static final int TEXT_VALUE = 0xFF00FFFF;
            public static final int TEXT_FORMULA = 0xFF00FF00;

            public static final int ACCENT_PRIMARY = 0xFF00FFFF;
            public static final int ACCENT_SUCCESS = 0xFF00FF00;
            public static final int ACCENT_WARNING = 0xFFFFFF00;
            public static final int ACCENT_ERROR = 0xFFFF0000;
            public static final int ACCENT_INFO = 0xFF00AAFF;

            public static final int BUTTON_NORMAL = 0xFF000000;
            public static final int BUTTON_HOVER = 0xFF222222;
            public static final int BUTTON_PRESSED = 0xFF444444;
            public static final int BUTTON_DISABLED = 0xFF111111;

            public static final int SLIDER_TRACK = 0xFF333333;
            public static final int SLIDER_THUMB = 0xFFFFFFFF;
            public static final int SLIDER_THUMB_HOVER = 0xFFFFFF00;

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
        public static final int P100 = 0xFFE9D5FF;
        public static final int P200 = 0xFFD8B4FE;
        public static final int P300 = 0xFFC084FC;
        public static final int P400 = 0xFFA855F7;
        public static final int P500 = 0xFF9333EA;
        public static final int P600 = 0xFF7E22CE;
        public static final int P700 = 0xFF6B21A8;
        public static final int P800 = 0xFF581C87;
        public static final int P900 = 0xFF3B0764;

        private Purple() {}
    }

    /*
     * Orange color palette (O100-O900).
     */
    public static final class Orange {
        public static final int O100 = 0xFFFFEDD5;
        public static final int O200 = 0xFFFED7AA;
        public static final int O300 = 0xFFFDBA74;
        public static final int O400 = 0xFFFB923C;
        public static final int O500 = 0xFFF97316;
        public static final int O600 = 0xFFEA580C;
        public static final int O700 = 0xFFC2410C;
        public static final int O800 = 0xFF9A3412;
        public static final int O900 = 0xFF7C2D12;

        private Orange() {}
    }

    /*
     * Neutral grayscale palette for UI surfaces and text.
     */
    public static final class Neutral {
        public static final int N950 = 0xFF101010;
        public static final int N920 = 0xFF111111;
        public static final int N900 = 0xFF161616;
        public static final int N880 = 0xFF1A1A1A;
        public static final int N860 = 0xFF1E1E1E;
        public static final int N840 = 0xFF222222;
        public static final int N820 = 0xFF2A2A2A;
        public static final int N800 = 0xFF2E2E2E;
        public static final int N780 = 0xFF333333;
        public static final int N760 = 0xFF3A3A3A;
        public static final int N740 = 0xFF444444;
        public static final int N700 = 0xFF555555;
        public static final int N650 = 0xFF666666;
        public static final int N600 = 0xFF777777;
        public static final int N550 = 0xFF888888;
        public static final int N500 = 0xFFAAAAAA;
        public static final int N450 = 0xFFCCCCCC;
        public static final int N400 = 0xFFEFEFEF;

        private Neutral() {}
    }

    /*
     * Basic RGB primaries for utility palettes.
     */
    public static final class Basic extends SharedColorTokens.Basic {
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
        public static final int BG = 0xF0181820;
        /* Tooltip border */
        public static final int BORDER = Stroke.DEFAULT;
        /* Tooltip shadow */
        public static final int SHADOW = 0x80000000;

        private Tooltip() {}
    }

    // ===========================================================================
    // RADIAL MENU COLORS
    // ===========================================================================

    public static final class Radial {
        /* Center hub background */
        public static final int HUB_BG = 0xFF181820;
        /* Center hub border */
        public static final int HUB_BORDER = 0xFF3A3A4C;
        /* Segment default background (80% opacity) */
        public static final int SEGMENT_BG = 0xCC181820;
        /* Segment hover background */
        public static final int SEGMENT_HOVER = 0xCC202028;
        /* Segment selected background */
        public static final int SEGMENT_SELECTED = 0xCC2A2A38;
        /* Segment border */
        public static final int SEGMENT_BORDER = 0xFF3A3A4C;
        /* Segment divider */
        public static final int SEGMENT_DIVIDER = 0xFF2A2A38;
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
        /* Category: Analyze (cyan/blue) */
        public static final int CAT_ANALYZE = 0xFF4488FF;
        /* Category: Telemetry (purple) */
        public static final int CAT_TELEMETRY = 0xFFAA55FF;
        /* Category: Combat (red) */
        public static final int CAT_COMBAT = 0xFFFF4444;
        /* Category: Arena (green) */
        public static final int CAT_ARENA = 0xFF44FF88;
        /* Category: Tools (orange) */
        public static final int CAT_TOOLS = 0xFFFFAA00;
        /* Category: Play (light pink) */
        public static final int CAT_PLAY = 0xFFFFCCCC;

        /* Macro: Analyze (blue) */
        public static final int MACRO_ANALYZE = 0xFF4488FF;
        /* Macro: Telemetry (cyan) */
        public static final int MACRO_TELEMETRY = 0xFF66CCFF;
        /* Macro: Combat (red) */
        public static final int MACRO_COMBAT = 0xFFFF4444;
        /* Macro: Arena (emerald) */
        public static final int MACRO_ARENA = 0xFF55DD88;
        /* Macro: Play (green) */
        public static final int MACRO_PLAY = 0xFF44FF88;
        /* Macro: Tools (orange) */
        public static final int MACRO_TOOLS = 0xFFFFAA00;

        // -------------------------------------------------------------------
        // ANALYZE SUBCATEGORY COLORS (blue gradient light->dark)
        // -------------------------------------------------------------------
        /* Analyze: Debug tools */
        public static final int ANALYZE_DEBUG = 0xFF4488FF;
        /* Analyze: HUD overlays */
        public static final int ANALYZE_HUD = 0xFF4488FF;
        /* Analyze: Spatial/render debug */
        public static final int ANALYZE_SPATIAL = 0xFF66AAFF;
        /* Analyze: Collision debug */
        public static final int ANALYZE_COLLISION = 0xFF66AAFF;
        /* Analyze: Performance */
        public static final int ANALYZE_PERFORMANCE = 0xFF88CCFF;
        /* Analyze: Mob visualizers */
        public static final int ANALYZE_MOBS = 0xFF88CCFF;
        /* Analyze: Density visualizers */
        public static final int ANALYZE_DENSITY = 0xFFAADDFF;
        /* Analyze: Safe spots */
        public static final int ANALYZE_SAFE_SPOTS = 0xFFCCEEFF;
        /* Analyze: Light levels */
        public static final int ANALYZE_LIGHT = 0xFFCCEEFF;
        /* Analyze: Spawnability */
        public static final int ANALYZE_SPAWN = 0xFFEEFFFF;
        /* Analyze: Room bounds */
        public static final int ANALYZE_ROOM = 0xFFBBDDFF;

        // -------------------------------------------------------------------
        // TELEMETRY SUBCATEGORY COLORS (purple gradient)
        // -------------------------------------------------------------------
        /* Telemetry: Operations */
        public static final int TELEMETRY_OPS = 0xFFAADDFF;
        /* Telemetry: Dashboard */
        public static final int TELEMETRY_DASHBOARD = 0xFFAA55FF;
        /* Telemetry: Exports */
        public static final int TELEMETRY_EXPORT = 0xFF8844DD;

        // -------------------------------------------------------------------
        // COMBAT SUBCATEGORY COLORS (red gradient)
        // -------------------------------------------------------------------
        /* Combat: Actions */
        public static final int COMBAT_ACTIONS = 0xFFFF4444;
        /* Combat: Damage/defense stats */
        public static final int COMBAT_DAMAGE = 0xFFFF8888;
        /* Combat: Defense */
        public static final int COMBAT_DEFENSE = 0xFFFF8888;
        /* Combat: Weapon editor */
        public static final int COMBAT_WEAPON = 0xFFFFAAAA;
        /* Combat: Shield editor (neutral gray) */
        public static final int COMBAT_SHIELD = 0xFFDDDDDD;

        // -------------------------------------------------------------------
        // ARENA SUBCATEGORY COLORS (green gradient)
        // -------------------------------------------------------------------
        /* Arena: Management */
        public static final int ARENA_MANAGE = 0xFF44FF88;
        /* Arena: Templates */
        public static final int ARENA_TEMPLATES = 0xFF66FFAA;
        /* Arena: Spawning */
        public static final int ARENA_SPAWNING = 0xFF88FFCC;
        /* Arena: Hazards */
        public static final int ARENA_HAZARDS = 0xFFAAFFDD;
        /* Arena: Rewards */
        public static final int ARENA_REWARDS = 0xFFCCFFEE;

        // -------------------------------------------------------------------
        // TOOLS SUBCATEGORY COLORS (orange/yellow gradient)
        // -------------------------------------------------------------------
        /* Tools: Primary */
        public static final int TOOLS_PRIMARY = 0xFFFFAA00;
        /* Tools: Editor */
        public static final int TOOLS_EDITOR = 0xFFFFCC66;
        /* Tools: Secondary */
        public static final int TOOLS_SECONDARY = 0xFFFFDD99;
        /* Tools: Utility */
        public static final int TOOLS_UTILITY = 0xFFFFEECC;

        // -------------------------------------------------------------------
        // PLAY SUBCATEGORY COLORS (warm/social)
        // -------------------------------------------------------------------
        /* Play: Party */
        public static final int PLAY_PARTY = 0xFFFFCCCC;
        /* Play: Social */
        public static final int PLAY_SOCIAL = 0xFFFFFFFF;
        /* Play: Quests */
        public static final int PLAY_QUESTS = 0xFFFFEEEE;
        /* Play: Communication */
        public static final int PLAY_COMMS = 0xFFEEFFFF;
        /* Play: Leaderboard */
        public static final int PLAY_LEADERBOARD = 0xFFFFDD88;
        /* Play: Season Pass */
        public static final int PLAY_SEASON = 0xFFFFEEAA;

        // Additional telemetry colors
        /* Telemetry: Spatial analysis */
        public static final int TELEMETRY_SPATIAL = 0xFF7755DD;
        /* Telemetry: Data tools */
        public static final int TELEMETRY_DATA = 0xFFCCEEFF;
        /* Telemetry: Scan tools */
        public static final int TELEMETRY_SCAN = 0xFFEEFFFF;
        /* Telemetry: Dashboard */
        public static final int TELEMETRY_DASH = 0xFFBBDDFF;

        // Additional combat colors
        /* Combat: Armor configuration */
        public static final int COMBAT_ARMOR = 0xFFAABBDD;
        /* Combat: Abilities */
        public static final int COMBAT_ABILITIES = 0xFFFFCC44;
        /* Combat: Debug tools */
        public static final int COMBAT_DEBUG = 0xFFFF6666;

        // Additional arena colors
        /* Arena: Endurance mode */
        public static final int ARENA_ENDURANCE = 0xFF88FF66;
        /* Arena: Wave control */
        public static final int ARENA_WAVES = 0xFFAAFF88;
        /* Arena: Party management */
        public static final int ARENA_PARTY = 0xFFCCFFAA;

        // Additional tools colors
        /* Tools: Testing */
        public static final int TOOLS_TESTING = 0xFFFFBB44;
        /* Tools: Notifications */
        public static final int TOOLS_NOTIFY = 0xFFFFDD66;
        /* Tools: Mailbox */
        public static final int TOOLS_MAILBOX = 0xFFFFEE88;
        /* Tools: Settings */
        public static final int TOOLS_SETTINGS = 0xFFFFFFAA;
        /* Tools: Game design */
        public static final int TOOLS_GAMEDESIGN = CAT_TELEMETRY;
        /* Tools: Commands */
        public static final int TOOLS_COMMANDS = 0xFFFFFFEE;

        private Radial() {}
    }

    // ===========================================================================
    // HUD OVERLAY COLORS
    // ===========================================================================

    public static final class Hud {
        /* Default HUD panel background (80% opacity) */
        public static final int PANEL_BG = 0xCC0A0A0F;
        /* HUD panel border (50% opacity) */
        public static final int PANEL_BORDER = 0x803A3A4C;

        // Health bar
        public static final int HEALTH = 0xFFE04040;
        public static final int HEALTH_BG = 0x40E04040;

        // Stamina bar
        public static final int STAMINA = 0xFF40B060;
        public static final int STAMINA_BG = 0x4040B060;

        // Mana/energy bar
        public static final int MANA = Semantic.INFO;
        public static final int MANA_BG = 0x4060A5FA;

        // Experience bar
        public static final int XP = DesignTokens.Accent.PRIMARY;
        public static final int XP_BG = 0x4000D4FF;

        // Boss health
        public static final int BOSS_HEALTH = DesignTokens.Accent.SECONDARY;
        public static final int BOSS_PHASE = Semantic.WARNING;

        // Wave counter
        public static final int WAVE_TEXT = DesignTokens.Text.WHITE;
        public static final int WAVE_NUMBER = DesignTokens.Accent.PRIMARY;

        // Timer
        public static final int TIMER_NORMAL = DesignTokens.Text.PRIMARY;
        public static final int TIMER_WARNING = Semantic.WARNING;
        public static final int TIMER_CRITICAL = Semantic.ERROR;

        private Hud() {}
    }

    // ===========================================================================
    // TESTING MODE COLORS
    // ===========================================================================

    /*
     * Colors for IntegratedTestSession types and testing overlays.
     */
    public static final class TestingMode {
        /* Combat test sessions (orange-red) */
        public static final int COMBAT = 0xFFFF6644;
        /* Boss fight test sessions (purple) */
        public static final int BOSS_FIGHT = 0xFFAA44FF;
        /* Survival waves test sessions (green) */
        public static final int SURVIVAL = 0xFF44FF88;
        /* Damage validation test sessions (orange) */
        public static final int DAMAGE_VALIDATION = 0xFFFFAA00;
        /* Performance stress test sessions (blue) */
        public static final int PERFORMANCE = 0xFF4488FF;
        /* Custom test sessions (gray) */
        public static final int CUSTOM = 0xFF888888;

        /* Endless mode pulse color */
        public static final int PULSE = 0xFF4488FF;
        /* Progress bar border */
        public static final int PROGRESS_BORDER = 0xFF555555;

        private TestingMode() {}
    }

    // ===========================================================================
    // NOTIFICATION COLORS
    // ===========================================================================

    public static final class Notification {
        /* Default notification */
        public static final int DEFAULT_BG = 0xFF202028;
        public static final int DEFAULT_BORDER = 0xFF3A3A4C;

        /* Success notification (90% opacity) */
        public static final int SUCCESS_BG = 0xE61F6A3F;
        public static final int SUCCESS_BORDER = Semantic.SUCCESS;

        /* Warning notification (90% opacity) */
        public static final int WARNING_BG = 0xE6A06000;
        public static final int WARNING_BORDER = Semantic.WARNING;

        /* Error notification (90% opacity) */
        public static final int ERROR_BG = 0xE67A1A1E;
        public static final int ERROR_BORDER = Semantic.ERROR;

        /* Info notification (90% opacity) */
        public static final int INFO_BG = 0xE62060C0;
        public static final int INFO_BORDER = Semantic.INFO;

        // Notification UI palette (warm tones)
        public static final int RGB_TEXT_PRIMARY = 0xF5F1E8;
        public static final int RGB_TEXT_SECONDARY = 0xCBBFA8;
        public static final int RGB_TEXT_MUTED = 0x938877;
        public static final int RGB_WHITE = DesignTokens.Text.WHITE & Mask.RGB;
        public static final int RGB_BLACK = Mask.NONE;

        public static final int RGB_PANEL_TOP = 0x2A2319;
        public static final int RGB_PANEL_BOTTOM = 0x162227;
        public static final int RGB_BACKDROP_TOP = mix(RGB_PANEL_TOP, RGB_BLACK, 0.45f);
        public static final int RGB_BACKDROP_BOTTOM = mix(RGB_PANEL_BOTTOM, RGB_BLACK, 0.55f);
        public static final int RGB_PANEL_INNER_TOP = 0x30281E;
        public static final int RGB_PANEL_INNER_BOTTOM = 0x1B252A;

        public static final int RGB_SURFACE_TOP = 0x2E271D;
        public static final int RGB_SURFACE_BOTTOM = 0x221C14;
        public static final int RGB_SURFACE_HOVER_TOP = 0x3A3124;
        public static final int RGB_SURFACE_HOVER_BOTTOM = 0x2A2218;
        public static final int RGB_SURFACE_READ = 0x1E1811;

        public static final int RGB_ACCENT = 0xE1A44C;
        public static final int RGB_ACCENT_SOFT = 0x9B6D2E;
        public static final int RGB_ACCENT_ALT = 0x2CB5A0;

        public static final class Category {
            public static final int ACHIEVEMENT = 0xE7B84D;
            public static final int RECORD = 0x34C9C9;
            public static final int SEASON = 0x4F7BD9;
            public static final int TOKEN = 0x59B77C;
            public static final int REWARD = 0xF19A3E;
            public static final int PARTY = 0x5DA7E3;
            public static final int QUEST = 0xD86C4D;
            public static final int COMBAT = 0xE2554F;
            public static final int RESONANCE = 0x60D1A7;
            public static final int NEWS = 0x7B9CFF;
            public static final int ADMIN = 0xE88B3D;
            public static final int SYSTEM = 0x8E97A6;
            public static final int MAILBOX = 0x3AA6D0;

            private Category() {}
        }

        public static final class Priority {
            public static final int LOW = 0x8E97A6;
            public static final int NORMAL = 0x5DA7E3;
            public static final int HIGH = RGB_ACCENT;
            public static final int URGENT = 0xE88B3D;
            public static final int CRITICAL = 0xE2554F;

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
        public static final int BG_TOP = 0xF0181818;
        public static final int BG_BOTTOM = 0xF00D0D1A;
        public static final int BORDER = 0xFF6366F1;
        public static final int TITLE = 0xFF818CF8;
        public static final int SUBTITLE = 0xFFA5B4FC;
        public static final int PARTICLE = 0xFF6366F1;

        public static final int FEATURE_MOB = 0xFF4ADE80;
        public static final int FEATURE_DEBUG = 0xFF60A5FA;
        public static final int FEATURE_ENDURANCE = 0xFFF472B6;
        public static final int FEATURE_TESTING = 0xFFFBBF24;

        public static final int HINT = 0xFF444444;
        public static final int HIGHLIGHT = 0x22FFFFFF;
        public static final int SUBTLE = 0x11FFFFFF;
        public static final int SHADOW = 0x44000000;

        private Welcome() {}
    }

    // ===========================================================================
    // SEASON PASS SCREEN COLORS
    // ===========================================================================

    public static final class SeasonPass {
        public static final int BG_TOP = 0xF0181818;
        public static final int BG_BOTTOM = 0xF00D0D1A;
        public static final int BORDER = 0xFFD4AF37;
        public static final int TITLE = 0xFFFFD700;
        public static final int SUBTITLE = 0xFFDAA520;
        public static final int FREE_TRACK = 0xFF60A5FA;
        public static final int PREMIUM_TRACK = 0xFFFFD700;
        public static final int LOCKED = 0xFF555555;
        public static final int PROGRESS_BG = 0xFF2A2A2A;
        public static final int PROGRESS_FILL = 0xFFD4AF37;
        public static final int CLAIMED = 0xFF22C55E;
        public static final int BOOST = 0xFF8B5CF6;
        public static final int BADGE = 0xFFEF4444;
        public static final int INACTIVE = 0xFF888888;
        public static final int HIGHLIGHT = 0x22FFFFFF;
        public static final int ROW_BG = 0x444444;
        public static final int ROW_BG_ALT = 0x333333;

        private SeasonPass() {}
    }

    // ===========================================================================
    // PARTY SCREEN COLORS
    // ===========================================================================

    public static final class Party {
        public static final int TAB_ACTIVE = 0xFF1A2A4A;
        public static final int ROW_HOVER = 0x40FFFFFF;
        public static final int ROW_DEFAULT = 0x20FFFFFF;
        public static final int HINT_TEXT = 0x60FFFFFF;

        public static final int STAT_HP = 0xFFFF6666;
        public static final int STAT_DMG = 0xFFFFAA00;
        public static final int STAT_POINTS = 0xFFFFFF00;
        public static final int STAT_DIFFICULTY = 0xFFAA66FF;

        public static final int READY_GLOW = 0x4000FF88;
        public static final int NOT_READY_GLOW = 0x40FF4466;

        public static final int DIFFICULTY_TRIVIAL = DesignTokens.Neutral.N650;
        public static final int DIFFICULTY_EASY = Rarity.UNCOMMON;
        public static final int DIFFICULTY_MEDIUM = 0xFFFFFF55;
        public static final int DIFFICULTY_HARD = 0xFFFF8800;
        public static final int DIFFICULTY_ELITE = 0xFFFF5555;
        public static final int DIFFICULTY_BOSS = 0xFFAA00FF;

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
        public static final int PRESET_LABEL_ACTIVE = 0xFF88FF88;
        public static final int DROPDOWN_BG = DesignTokens.Neutral.N920;
        public static final int DROPDOWN_BG_SELECTED = 0xFF1F4D3A;
        public static final int DROPDOWN_BG_HOVER = DesignTokens.Neutral.N820;
        public static final int DROPDOWN_BG_DEFAULT = DesignTokens.Neutral.N880;
        public static final int DROPDOWN_TEXT = DesignTokens.Neutral.N400;
        public static final int DROPDOWN_HINT = DesignTokens.Neutral.N550;
        public static final int DROPDOWN_HOVER = 0xFF99CCFF;
        public static final int ITEM_BG_HOVER = DesignTokens.Neutral.N760;
        public static final int ITEM_BG_DEFAULT = DesignTokens.Neutral.N840;
        public static final int ITEM_REMOVE_HOVER = Semantic.ERROR;
        public static final int PREVIEW_MODE = 0xFFFFB366;
        public static final int ACTION_ROW_BG = DesignTokens.Neutral.N880;
        public static final int PROGRESS_BAR_BG = DesignTokens.Neutral.N880;
        public static final int PROGRESS_BAR_FILL = 0xFF4CAF50;
        public static final int RESULT_BG = DesignTokens.Neutral.N950;
        public static final int RESULT_SUCCESS = 0xFF66FF66;
        public static final int RESULT_WARNING = 0xFFFFC107;
        public static final int FAILURE_TEXT = 0xFFFF8888;
        public static final int MORE_FAILURES = 0xFFFFBB66;

        private MultiEdit() {}
    }

    // ===========================================================================
    // CRAFTING RARITY COLORS
    // ===========================================================================

    public static final class Rarity {
        public static final int COMMON = 0xFF888888;
        public static final int UNCOMMON = 0xFF55FF55;
        public static final int RARE = 0xFF5555FF;
        public static final int EPIC = 0xFFAA00AA;
        public static final int LEGENDARY = 0xFFFFAA00;

        private Rarity() {}
    }

    // ===========================================================================
    // ERROR SCREEN COLORS
    // ===========================================================================

    public static final class ErrorScreen {
        public static final int BG = 0xE0200000;
        public static final int TITLE = 0xFFFF4444;
        public static final int TEXT = DesignTokens.Neutral.N450;
        public static final int HINT = DesignTokens.Neutral.N550;
        public static final int STATUS_BG = 0xC0000000;
        public static final int STATUS_ERROR = 0xFFFF5555;

        private ErrorScreen() {}
    }

    // ===========================================================================
    // ERROR BOUNDARY COLORS
    // ===========================================================================

    public static final class ErrorBoundary {
        public static final int BG = 0xE01A1A2E;
        public static final int PANEL_BG = 0xFF1A1A2E;
        public static final int BORDER = Semantic.ERROR;
        public static final int TEXT = DesignTokens.Text.WHITE;
        public static final int SCRIM = withAlpha(PANEL_BG, DesignTokens.Alpha.A75);
        public static final int HIGHLIGHT = 0xFFFF5555;

        private ErrorBoundary() {}
    }

    // ===========================================================================
    // EXTERNAL LINK CONFIRM DIALOG COLORS
    // ===========================================================================

    public static final class ExternalConfirm {
        public static final int BG = 0xDD000000;
        public static final int BORDER = DesignTokens.Neutral.N700;
        public static final int TITLE = DesignTokens.Text.WHITE;
        public static final int URL = DesignTokens.Neutral.N500;
        public static final int STATUS_OK = 0x55FF55;
        public static final int STATUS_WARN = 0xFFAA00;
        public static final int STATUS_ERROR = 0xFF5555;

        private ExternalConfirm() {}
    }

    // ===========================================================================
    // QUICK TEST WIZARD COLORS
    // ===========================================================================

    public static final class QuickTest {
        public static final int HEADER_GRADIENT_START = 0xFF2A3A5E;
        public static final int INFO_BG = 0x40FFAA00;

        private QuickTest() {}
    }

    // ===========================================================================
    // TESTING UI COLORS
    // ===========================================================================

    public static final class Testing extends SharedColorTokens.Testing {
        private Testing() {}
    }

    // ===========================================================================
    // DEBUG OVERLAY COLORS
    // ===========================================================================

    public static final class DebugOverlay {
        public static final int GRID = 0x40FFFFFF;
        public static final int GRID_MAJOR = 0x60FFFFFF;
        public static final int ZONE_BOUNDARY = 0x80FFFF00;
        public static final int BBOX = 0x8000FFFF;
        public static final int BBOX_HOVERED = 0xC000FFFF;
        public static final int WARNING = 0xFFFF4444;
        public static final int OVERFLOW = 0x80FF0000;
        public static final int INFO_BG = 0xE0000000;
        public static final int INFO_TEXT = 0xFFCCCCCC;
        public static final int WARNING_TRUNCATED = 0xFFFFAA00;
        public static final int WARNING_MISALIGNED = 0xFFFF00FF;
        public static final int WARNING_OUT_OF_VIEW = 0xFF888888;

        private DebugOverlay() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PREVIEW RENDERER COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Preview {
        public static final int SELECTED_SLOT_BG = 0x4020C0FF;
        public static final int BG = 0xFF111419;
        public static final int BASE_SHADOW = 0x10101060;

        private Preview() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECIPE GRID COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class RecipeGrid {
        public static final int TAG_INDICATOR = 0xFFFF9800;

        private RecipeGrid() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STAMINA SYSTEM EDITOR COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class StaminaEditor {
        public static final int OVERLAY_BG = 0x80000000;
        public static final int PANEL_BG = 0xFF2D2D30;
        public static final int TITLE_TEXT = DesignTokens.Text.WHITE;
        public static final int SELECTED_FIELD = 0xFFFFFF00;
        public static final int NORMAL_FIELD = DesignTokens.Neutral.N450;
        public static final int INSTRUCTIONS = DesignTokens.Neutral.N550;

        private StaminaEditor() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ITEM EDITOR SCREEN COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class ItemEditor {
        public static final int STATUS_MESSAGE_BG = 0xE0000000;
        public static final int TOOLTIP_BG = 0xF0100010;
        public static final int DEV_PANEL_BG = 0xE0101020;
        public static final int DEV_PANEL_TITLE = DesignTokens.Text.WHITE;

        private ItemEditor() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRESET SELECTOR COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class PresetSelector {
        public static final int CLOSE_HOVER = 0xFFFF4444;
        public static final int RENAME_BG = 0xFF1A1A2A;
        public static final int RENAME_BORDER = 0xFF4488FF;
        public static final int SEARCH_BG_FOCUSED = DesignTokens.Neutral.N820;
        public static final int SEARCH_BG_DEFAULT = DesignTokens.Neutral.N860;
        public static final int PREVIEW_BG = DesignTokens.Neutral.N900;
        public static final int ROW_BG_SELECTED = 0xFF1F4D3A;
        public static final int ROW_BG_HOVER = DesignTokens.Neutral.N820;
        public static final int ROW_BG_DEFAULT = DesignTokens.Neutral.N880;
        public static final int SCOPE_MODPACK = 0xFFFF9900;
        public static final int SCOPE_CATEGORY = 0xFF66AAFF;
        public static final int SCOPE_GLOBAL = 0xFF88FF88;
        public static final int SCOPE_GLOBAL_USER = 0xFFAAFF88;

        private PresetSelector() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MOB CONFIG SCREEN COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class MobConfig {
        public static final int HEADER_GRADIENT_START = 0xFF2A2A42;
        public static final int SECTION_GRADIENT_START = 0xFF151525;
        public static final int SECTION_GRADIENT_END = 0xFF1A1A2E;
        public static final int MARKER = 0x80FFFFFF;
        public static final int OVERLAY = 0xA0000000;

        private MobConfig() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TELEMETRY DASHBOARD COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class TelemetryDashboard {
        public static final int CONFIRM_HOVER_BG = 0x80FF0000;
        public static final int SCRIM = 0xC0101010;

        private TelemetryDashboard() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KEYBINDS PAGE COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Keybinds {
        public static final int CONFLICT_BG = 0x30FF4444;
        public static final int CONFLICT_GLOW = 0x18FF4444;
        public static final int CONFLICT_BORDER = 0xFFFF6666;
        public static final int CONFLICT_TEXT = 0xFFFF8888;

        private Keybinds() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HEATMAP COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Heatmap {
        public static final int DEATH = Basic.RED;
        public static final int MOVEMENT = Basic.GREEN;
        public static final int CAMPING = Basic.YELLOW;
        public static final int STUCK = 0xFFFF8000;
        public static final int AGGRO_DROP = 0xFF8000FF;
        public static final int KITING = Basic.CYAN;
        public static final int LIGHT_SPAWNABLE = Basic.RED;
        public static final int LIGHT_DARK = 0xFFFF8800;

        private Heatmap() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BODY DIAGRAM COLORS (Combat Settings)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class BodyDiagram {
        public static final int HEAD = 0xFFFF6B6B;
        public static final int BODY = 0xFF4ECDC4;
        public static final int ARMS = 0xFFFFE66D;
        public static final int LEGS = 0xFF95E1D3;
        public static final int ARMOR_LABEL = 0xFFFF8C00;

        private BodyDiagram() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUG PANEL COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class DebugPanel {
        public static final int NBT_TOGGLE = 0x88CCFF;
        public static final int NBT_SUMMARY = 0xFFCCAA;
        public static final int HEADER_TEXT = 0xFFFFFF;
        public static final int ITEM_TEXT = 0xDDDDDD;
        public static final int ITEM_DETAIL = 0xCCCCCC;
        public static final int NBT_COUNT = 0x8899AA;
        public static final int SOURCE_TEXT = 0xCCDDFF;
        public static final int LOG_TEXT = 0xAAAAAA;
        public static final int PANEL_BG = 0xE0101020;
        public static final int DIFF = 0xFFCC66;
        public static final int MATCH = 0x88FF88;

        private DebugPanel() {}
    }

    // ===========================================================================
    // COLOR TOKENS - IMPACT BUTTON (special button styles)
    // ===========================================================================

    public static final class ImpactButton {
        /* Default button base */
        public static final int DEFAULT_BASE = 0xFF101018;
        /* Default button border */
        public static final int DEFAULT_BORDER = Stroke.DEFAULT;
        /* Ghost button base (more transparent) */
        public static final int GHOST_BASE = 0xFF0A0A10;
        /* Ghost button border */
        public static final int GHOST_BORDER = Stroke.MUTED;

        /* Primary button base (teal) */
        public static final int PRIMARY_BASE = 0xFF0E5569;
        /* Primary button border */
        public static final int PRIMARY_BORDER = 0xFF1A8BAA;

        /* Danger button base (dark red) */
        public static final int DANGER_BASE = 0xFF7A1A1E;
        /* Danger button border */
        public static final int DANGER_BORDER = 0xFFB23036;

        /* Success button base (forest green) */
        public static final int SUCCESS_BASE = 0xFF1F6A3F;
        /* Success button border */
        public static final int SUCCESS_BORDER = 0xFF2DA45A;

        private ImpactButton() {}
    }

    // ===========================================================================
    // COLOR TOKENS - UTILITY
    // ===========================================================================

    public static final class Utility {
        /* Focus ring color */
        public static final int FOCUS = 0xFF00D4FF;
        /* Selection background */
        public static final int SELECTION = 0x3000D4FF;
        /* Modal backdrop (scrim) */
        public static final int SCRIM = 0xCC000000;
        /* Disabled elements */
        public static final int DISABLED = 0xFF505060;
        /* Drop shadows */
        public static final int SHADOW = 0x80000000;
        /* Cyan glow */
        public static final int GLOW_CYAN = 0x6000D4FF;

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

    public static final class Mask extends SharedColorTokens.Mask {
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
        public static final int OVERLAY = withAlpha(0x000000, 0x80);
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
        public static final int NEUTRAL = 0xFF78909C;
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
        public static final int THUMB = 0xFFD0D0D8;
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

    public static final class BodyPart extends SharedColorTokens.BodyPart {
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

    public static final class Message extends SharedColorTokens.Message {
        private Message() {}
    }

    public static final class FollowRange extends SharedColorTokens.FollowRange {
        private FollowRange() {}
    }

    public static final class AttributeLog {
        public static final int GREEN = 0xFF00FF00;
        public static final int RED = 0xFFFF0000;
        public static final int YELLOW = 0xFFFFFF00;
        public static final int LIGHT_RED = 0xFFFF5555;
        public static final int DARK_RED = 0xFFAA0000;
        public static final int CRITICAL_RED = 0xFF550000;
        public static final int ORANGE = 0xFFFFAA00;
        public static final int LIGHT_GREEN = 0xFF55FF55;
        public static final int GRAY = 0xFFAAAAAA;
        public static final int MAGENTA = 0xFFFF55FF;

        private AttributeLog() {}
    }

    public static final class Shield extends SharedColorTokens.Shield {
        private Shield() {}
    }

    public static final class Alert {
        public static final int ERROR = 0xED4245;
        public static final int WARN = 0xFEE75C;
        public static final int INFO = 0x5865F2;

        private Alert() {}
    }

    public static final class JourneyMap {
        public static final int SPAWN = 0x00FF00;
        public static final int OBJECTIVE = 0xFFD700;
        public static final int EXIT = 0x4169E1;

        private JourneyMap() {}
    }

    public static final class Trail {
        public static final class Entity {
            public static final int ARROW = 0xFFFFAA00;
            public static final int POTION = 0xFF9900FF;
            public static final int TRIDENT = 0xFF00FFFF;
            public static final int FIREWORK = 0xFFFF5555;
            public static final int WITHER_SKULL = 0xFF333333;
            public static final int FIREBALL = 0xFFFF4400;
            public static final int SMALL_FIREBALL = 0xFFFF6600;
            public static final int SHULKER_BULLET = 0xFFFF00FF;
            public static final int XP_ORB = 0xFF55FF55;
            public static final int ENDER_EYE = 0xFF00FF88;
            public static final int ELYTRA = 0xFFAADDFF;
            public static final int DEFAULT = 0xFFFFFFFF;

            private Entity() {}
        }

        private Trail() {}
    }

    public static final class Combat extends SharedColorTokens.Combat {
        private Combat() {}
    }

    public static final class CombatPanel {
        public static final class Damage {
            public static final int CRITICAL = 0xFFFF4444;
            public static final int HIGH = 0xFFFFAA00;
            public static final int MEDIUM = 0xFFFFFF00;
            public static final int LOW = 0xFFFFFFFF;

            private Damage() {}
        }

        private CombatPanel() {}
    }

    public static final class TestingUi {
        public static final class Screen {
            public static final int BG = 0xFF1A1A1A;

            private Screen() {}
        }

        public static final class Accent {
            public static final int GOLD = 0xFFFFAA00;

            private Accent() {}
        }

        public static final class Panel {
            public static final int BG = 0xE0202035;
            public static final int HEADER = 0xFF303050;
            public static final int HEADER_ALT = 0xFF403020;
            public static final int XP_BG = 0xFF333344;

            private Panel() {}
        }

        public static final class Scrollbar {
            public static final int TRACK = 0x40FFFFFF;
            public static final int THUMB = 0x80FFFFFF;

            private Scrollbar() {}
        }

        public static final class Hud {
            public static final int PANEL_BG = 0xE0202030;
            public static final int PANEL_BORDER = 0xFF5588FF;
            public static final int PANEL_HEADER_BG = 0xFF303050;

            public static final int TEXT_TITLE = 0xFF00FFFF;
            public static final int TEXT_PRIMARY = 0xFFFFFFFF;
            public static final int TEXT_SECONDARY = 0xFFBBBBBB;
            public static final int TEXT_MUTED = 0xFF888888;
            public static final int TEXT_SUCCESS = 0xFF55FF55;
            public static final int TEXT_WARNING = 0xFFFFAA00;

            public static final int PROGRESS_BG = 0xFF333344;
            public static final int PROGRESS_FILL = 0xFF5588FF;
            public static final int PROGRESS_COMPLETE = 0xFF55FF55;

            public static final int HINT_BG = 0x80000000;
            public static final int NEW_TEST_GLOW_RGB = 0x5588FF;
            public static final int HEADER_IN_PROGRESS = 0xFF504020;

            private Hud() {}
        }

        public static final class Notification {
            public static final int BG_RGB = 0x202020;
            public static final int TEXT_RGB = 0xFFFFFF;
            public static final int TEXT_MUTED_RGB = 0xAAAAAA;

            public static final int TEST_PASSED = 0xFF55FF55;
            public static final int TEST_FAILED = 0xFFFF5555;
            public static final int ACHIEVEMENT = 0xFFFFAA00;
            public static final int LEVEL_UP = 0xFFFF55FF;
            public static final int BADGE = 0xFFFFD700;
            public static final int STREAK = 0xFF55FFFF;
            public static final int XP_GAIN = 0xFFAAFFAA;

            private Notification() {}
        }

        public static final class Badge {
            public static final int UNREAD = 0xFF00FF00;

            private Badge() {}
        }

        private TestingUi() {}
    }

    public static final class ArenaTestWizard {
        public static final int UNKNOWN = 0xFF808080;
        public static final int STONE = 0xFF808080;
        public static final int WOOD = 0xFFB87333;
        public static final int GRASS = 0xFF4CAF50;
        public static final int SAND = 0xFFE8D4A0;
        public static final int DIRT = 0xFF8B4513;
        public static final int BRICK = 0xFFB22222;
        public static final int IRON = 0xFFD3D3D3;
        public static final int GOLD = 0xFFFFD700;
        public static final int DIAMOND = 0xFF00CED1;
        public static final int EMERALD = 0xFF50C878;
        public static final int OBSIDIAN = 0xFF1A1A2E;
        public static final int NETHER = 0xFF8B0000;
        public static final int END = 0xFFE8E8A0;
        public static final int PRISMARINE = 0xFF5F9EA0;
        public static final int GLASS = 0x80FFFFFF;
        public static final int WOOL_WHITE = 0xFFFFFFFF;
        public static final int WOOL_BLACK = 0xFF1A1A1A;
        public static final int WOOL_RED = 0xFFFF0000;
        public static final int WOOL_BLUE = 0xFF0000FF;
        public static final int WOOL_GREEN = 0xFF00FF00;
        public static final int WOOL_YELLOW = 0xFFFFFF00;
        public static final int FALLBACK = 0xFF606060;

        private ArenaTestWizard() {}
    }

    public static final class BuildProgressHud {
        public static final class Panel {
            public static final int BACKGROUND = 0x80000000;
            public static final int BORDER = 0xFF404040;
            public static final int BAR_EMPTY = 0xFF202020;
            public static final int TEXT = 0xFFFFFFFF;
            public static final int TEXT_SHADOW = 0xFF000000;

            private Panel() {}
        }

        public static final class Progress {
            public static final int NORMAL = 0xFF00AA00;
            public static final int WARNING = 0xFFAAAA00;
            public static final int COMPLETE = 0xFF00FF00;
            public static final int FAILED = 0xFFFF0000;

            private Progress() {}
        }

        private BuildProgressHud() {}
    }

    public static final class Endurance extends SharedColorTokens.Endurance {
        private Endurance() {}
    }

    public static final class EnduranceUi {
        public static final class Accent {
            public static final int ORANGE = 0xFFFF8C00;
            public static final int PURPLE = 0xFFA371F7;
            public static final int GOLD = 0xFFFFD700;
            public static final int GOLD_RGB = 0xFFD700;

            private Accent() {}
        }

        public static final class QuestTier {
            public static final int HARD = EnduranceUi.Accent.ORANGE;
            public static final int ELITE = EnduranceUi.Accent.PURPLE;

            private QuestTier() {}
        }

        public static final class Challenge {
            public static final int HARD = 0xFFFF8800;

            private Challenge() {}
        }

        public static final class DeathScreen {
            public static final int BG = 0xEE0A0A14;
            public static final int PANEL_BG = 0xDD1A0A0A;

            private DeathScreen() {}
        }

        public static final class CompletionScreen {
            public static final int BACKDROP_RGB = 0x0A1428;
            public static final int PANEL_RGB = 0x0F1E38;
            public static final int GOLD_RGB = EnduranceUi.Accent.GOLD_RGB;

            private CompletionScreen() {}
        }

        public static final class PerkSelection {
            public static final int TAG_REQUIRED = 0xFFE85C5C;
            public static final int TAG_OPTIONAL = 0xFF5B9BD5;

            public static final int SYNERGY_COMPLETE = 0xFFFF55;
            public static final int SYNERGY_STRONG = 0xFFA500;
            public static final int SYNERGY_MODERATE = 0xFFFF7F;
            public static final int SYNERGY_MINOR = 0x7FFF7F;

            private PerkSelection() {}
        }

        public static final class KitSelection {
            public static final int ACCENT_PURPLE = EnduranceUi.Accent.PURPLE;
            public static final int BTN_SUCCESS_HOVER = 0xFF2EA043;
            public static final int BTN_SUCCESS_BORDER_HOVER = 0xFF3FB950;
            public static final int BTN_SUCCESS_BORDER = 0xFF238636;
            public static final int SCRIM = 0xAA000000;

            private KitSelection() {}
        }

        public static final class KitCategory {
            public static final int ALL = 0xFFE6EDF3;
            public static final int ARMOR = 0xFF58A6FF;
            public static final int WEAPONS = 0xFFF85149;
            public static final int TOOLS = 0xFFD29922;
            public static final int POTIONS = EnduranceUi.Accent.PURPLE;
            public static final int FOOD = 0xFF3FB950;
            public static final int COMBAT = 0xFFFF7B72;
            public static final int BLOCKS = 0xFF79C0FF;

            private KitCategory() {}
        }

        private EnduranceUi() {}
    }

    public static final class Mailbox {
        public static final class Panel {
            public static final int BG = 0xE8101820;

            private Panel() {}
        }

        public static final class Divider {
            public static final int LINE = withAlpha(DesignTokens.Text.WHITE, DesignTokens.Alpha.A25);

            private Divider() {}
        }

        public static final class List {
            public static final int SELECTED_BG = 0x40007ACC;
            public static final int HOVER_BG = 0x20FFFFFF;

            private List() {}
        }

        public static final class Scrollbar {
            public static final int TRACK = Divider.LINE;
            public static final int THUMB = withAlpha(DesignTokens.Text.WHITE, DesignTokens.Alpha.A50);

            private Scrollbar() {}
        }

        public static final class News {
            public static final int PATCH_NOTES = 0xFF4CAF50;
            public static final int EVENTS = 0xFFFF9800;
            public static final int ANNOUNCEMENTS = 0xFF2196F3;
            public static final int MAINTENANCE = 0xFFF44336;
            public static final int DEV_BLOG = 0xFF9C27B0;
            public static final int COMMUNITY = 0xFF00BCD4;

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
            public static final int PANEL_BG = 0xDD1A1A1A;
            public static final int PANEL_OUTLINE = DesignTokens.Neutral.N760;
            public static final int LIST_BG = 0xFF0A0A0A;
            public static final int SCROLLBAR = DesignTokens.Neutral.N700;

            public static final int ENTRY_DEFAULT = DesignTokens.Neutral.N880;
            public static final int ENTRY_HOVER = DesignTokens.Neutral.N820;
            public static final int ENTRY_SELECTED = 0xFF2A4A6A;

            public static final int TEXT_PRIMARY = DesignTokens.Text.WHITE;
            public static final int TEXT_MUTED = DesignTokens.Neutral.N500;
            public static final int TEXT_DIM = DesignTokens.Neutral.N550;

            public static final int DUE_OVERDUE = 0xFFFF5555;
            public static final int DUE_SOON = 0xFFFFAA00;

            public static final int PRIORITY_HIGH = DUE_OVERDUE;
            public static final int PRIORITY_MEDIUM = DUE_SOON;
            public static final int PRIORITY_LOW = 0xFF55FF55;

            public static final int STATUS_PENDING = DesignTokens.Neutral.N500;
            public static final int STATUS_IN_PROGRESS = 0xFF55AAFF;
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
            public static final int BG_BASE = 0x1A1A2E;
            public static final int BG_STANDARD = (Overlay.Alpha.STANDARD << 24) | BG_BASE;
            public static final int BG_LIGHT = (Overlay.Alpha.LIGHT << 24) | BG_BASE;
            public static final int BG_HEAVY = (Overlay.Alpha.HEAVY << 24) | BG_BASE;
            public static final int BG_HEADER = 0xFF1A1A30;

            public static int withAlpha(int alpha) {
                return DesignTokens.withAlpha(BG_BASE, alpha);
            }

            private Panel() {}
        }

        public static final class Border {
            public static final int ACCENT = 0xFF3D5AFE;
            public static final int INFO = 0xFF00FFFF;
            public static final int SUCCESS = 0xFF4CAF50;
            public static final int WARNING = 0xFFFFAA00;
            public static final int ERROR = 0xFFFF4444;
            public static final int GOLD = 0xFFFFD700;
            public static final int ENDURANCE = 0xFFFF5722;
            public static final int MUTED = 0xFF555555;

            public static int glow(int borderColor) {
                return DesignTokens.withAlpha(borderColor, Overlay.Alpha.GLOW);
            }

            public static int divider(int borderColor) {
                return DesignTokens.withAlpha(borderColor, Overlay.Alpha.DIVIDER);
            }

            private Border() {}
        }

        public static final class Text {
            public static final int PRIMARY = DesignTokens.Text.WHITE;
            public static final int LIGHT = 0xFFE6E6E6;
            public static final int TITLE = DesignTokens.Basic.CYAN;
            public static final int MUTED = DesignTokens.Neutral.N500;
            public static final int HINT = DesignTokens.Neutral.N550;

            public static final int VALUE = DesignTokens.Basic.GREEN;
            public static final int VALUE_BRIGHT = 0xFF55FF55;

            public static final int WARNING = DesignTokens.Basic.YELLOW;
            public static final int WARNING_ORANGE = 0xFFFFAA00;
            public static final int DANGER = 0xFFFF4444;
            public static final int DANGER_BRIGHT = 0xFFFF5555;

            public static final int CYAN = 0xFF55FFFF;
            public static final int PURPLE = 0xFFAA55FF;
            public static final int GOLD = DesignTokens.Accent.GOLD;

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
            public static final int BG = DesignTokens.Neutral.N780;
            public static final int BG_ALT = 0xFF333344;

            public static final int FILL = 0xFF00DD88;
            public static final int FILL_GREEN = 0xFF44AA44;
            public static final int FILL_YELLOW = 0xFFAAAA44;
            public static final int FILL_RED = 0xFFAA4444;
            public static final int FILL_ORANGE = 0xFFFF5722;
            public static final int FILL_CYAN = 0xFF4488FF;

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
            public static final int PRIMARY = 0xFFFF5722;
            public static final int LIGHT = 0xFFFFAB91;
            public static final int BG = 0xBB1A1A2E;
            public static final int BG_SURVIVE = 0xBB1A2E1A;
            public static final int BOSS_ALERT = 0xFFFF4444;

            private Endurance() {}
        }

        public static final class Economy {
            public static final int PRIMARY = 0xFFFFD700;
            public static final int BG = 0xE0101020;

            private Economy() {}
        }

        public static final class Combat {
            public static final int IMPACT = 0xFF3D5AFE;
            public static final int SECONDARY = 0xFF00E5FF;
            public static final int GLOW = 0xFF82B1FF;

            private Combat() {}
        }

        public static final class CombatRecap {
            public static final int BG = 0xF0101018;
            public static final int PANEL_BG = 0xE0181820;
            public static final int ACCENT = 0xFF00DDFF;
            public static final int TEXT_PRIMARY = Overlay.Text.PRIMARY;
            public static final int TEXT_SECONDARY = Overlay.Text.MUTED;
            public static final int TEXT_HIGHLIGHT = 0xFFFFDD00;

            public static final int BAR_DAMAGE = 0xFFFF4444;
            public static final int BAR_CRIT = 0xFFFFAA00;
            public static final int BAR_HEADSHOT = 0xFF44DDFF;
            public static final int BAR_DPS = 0xFF44FF44;

            public static final int DIVIDER = 0x444444;
            public static final int BAR_BG = 0x333333;
            public static final int GRAPH_BG = 0x222228;

            private CombatRecap() {}
        }

        public static final class Impact3D {
            public static final int DPS = 0xFF44FF44;

            private Impact3D() {}
        }

        public static final class EpicFight {
            public static final int HEADER = 0xFFFF5722;
            public static final int GUARD = 0xFF4444FF;
            public static final int GUARD_FLASH = 0xFFFFFFFF;
            public static final int PARRY = 0xFFFFAA00;
            public static final int PARRY_FLASH = 0xFFFFFFFF;
            public static final int PERFECT_PARRY = 0xFFFF00FF;
            public static final int PERFECT_PARRY_SECONDARY = 0xFF00FFFF;
            public static final int SKILL_NAME = 0xFFAAAAAA;
            public static final int BATTLE_MODE = 0xFFFF8800;
            public static final int STAMINA_BG = 0xFF1A1A2E;
            public static final int STAMINA_FULL = 0xFFAAFF00;
            public static final int STAMINA_MEDIUM = 0xFFFFDD00;
            public static final int STAMINA_LOW = 0xFFFF6600;
            public static final int STAMINA_EXHAUSTED = 0xFFFF2222;

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

            public static final int HIGHLIGHT_SHADOW = 0xFF550000;
            public static final int CALCULATED_SHADOW = 0xFF005500;

            private Impact() {}
        }

        public static final class Help {
            public static final int TITLE = 0xFF81C784;
            public static final int CATEGORY = 0xFF64B5F6;
            public static final int KEY_BG = 0xFF333333;
            public static final int HINT = 0xFF555555;

            private Help() {}
        }

        public static final class Quest {
            public static final int PANEL_BG = Panel.BG_STANDARD;
            public static final int BORDER = Border.SUCCESS;
            public static final int BORDER_GLOW = DesignTokens.withAlpha(Border.SUCCESS, Overlay.Alpha.GHOST);

            public static final int TITLE = 0xFF81C784;
            public static final int TEXT = Overlay.Text.PRIMARY;
            public static final int TASK = 0xFFFFD54F;
            public static final int NOTE = 0xFFB0BEC5;
            public static final int PROGRESS = 0xFF64B5F6;
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
            public static final int VALUE_ORANGE = 0xFFFF9800;
            public static final int SCALE = 0xFFFF55FF;
            public static final int EMPTY_LOG = Overlay.Neutral.N700;

            private Attribute() {}
        }

        public static final class BodyPart {
            public static final int HEAD = 0xFF00FFFF;
            public static final int BODY = 0xFF00FF00;
            public static final int ARMS = 0xFFFFFF00;
            public static final int LEGS = 0xFFFF0000;

            private BodyPart() {}
        }

        public static final class Affix {
            public static final int SWIFT = 0xFF64B5F6;
            public static final int EMPOWERED = 0xFFFF5252;
            public static final int FORTIFIED = 0xFF4CAF50;
            public static final int ARMORED = 0xFF9E9E9E;
            public static final int BLAZING = 0xFFFF9800;
            public static final int PHANTOM = 0xFF7C4DFF;
            public static final int REGENERATING = 0xFFE91E63;
            public static final int HORDE = 0xFFFFEB3B;

            private Affix() {}
        }

        public static final class Momentum {
            public static final int NORMAL = 0xFF66FF66;
            public static final int HEATED = 0xFFFFAA00;
            public static final int OVERDRIVE = 0xFFFF00FF;
            public static final int STAGNANT = 0xFFFF4444;

            private Momentum() {}
        }

        public static final class Contract {
            public static final int HEADER = 0xFFFF8800;
            public static final int MULTIPLIER_HIGH = 0xFFFF4444;
            public static final int MULTIPLIER_MED = 0xFFFFAA00;
            public static final int MULTIPLIER_LOW = 0xFFAAFFAA;
            public static final int VIOLATED = 0xFF888888;
            public static final int STRIKETHROUGH = 0xFFFF4444;
            public static final int VIOLATED_MUTED = 0xFF666666;
            public static final int SEPARATOR = 0x44FFFFFF;
            public static final int MULTIPLIER_TEXT = 0xFFFFFFFF;

            private Contract() {}
        }

        public static final class Stamina {
            public static final int BG = 0xFF1A1A1A;
            public static final int BORDER = 0xFF3A3A3A;
            public static final int FULL = 0xFF4CAF50;
            public static final int MEDIUM = 0xFFFFEB3B;
            public static final int LOW = 0xFFFF5722;
            public static final int EXHAUSTED = 0xFFF44336;
            public static final int REGEN = 0xFF81C784;

            private Stamina() {}
        }

        public static final class Debug {
            public static final int HITBOX = 0x80FFFF00;
            public static final int AGGRO_SPHERE = 0x8000FFFF;

            public static final int WALL = 0x44AAFF;

            public static final int LABEL = 0xFFFFFFFF;
            public static final int TITLE = 0xFFFFAA00;

            public static final int RANGE_HOSTILE = 0x40FF5555;
            public static final int RANGE_NEUTRAL = 0x40FFFF55;
            public static final int RANGE_ATTACK = 0x60FF0000;
            public static final int RANGE_PASSIVE = 0x4055FF55;

            public static final int SAFE_SPOT_LABEL = 0xFFFF4444;

            public static final int LIGHT_SAFE = 0xFF00FF00;
            public static final int LIGHT_WARN = 0xFFFFFF00;
            public static final int LIGHT_DANGER = 0xFFFF0000;

            public static final int SPAWN_YES = 0xFFFF0000;
            public static final int SPAWN_CONDITIONAL = 0xFFFF8800;
            public static final int SPAWN_NO = 0xFF00FF00;

            public static final int ENTITY_HEALTH_GOOD = 0x55FF55;
            public static final int ENTITY_HEALTH_MED = 0xFFFF55;
            public static final int ENTITY_HEALTH_LOW = 0xFF5555;
            public static final int ENTITY_STAT = 0xAAAAAA;
            public static final int ATTACK_REACH = 0xFFFFFF00;
            public static final int ENTITY_HOSTILE = ENTITY_HEALTH_LOW;
            public static final int ENTITY_PASSIVE = ENTITY_HEALTH_GOOD;
            public static final int ENTITY_NEUTRAL = ENTITY_HEALTH_MED;
            public static final int ENTITY_NAME = 0xFFFFFF00;
            public static final int ENTITY_HP = ENTITY_HEALTH_LOW;
            public static final int ENTITY_ARMOR = 0x5555FF;
            public static final int ENTITY_DAMAGE = 0xFFAAAA;
            public static final int ENTITY_FOLLOW_RANGE = 0x00FF00;
            public static final int ENTITY_REACH_MODIFIED = 0xFFFFFF00;
            public static final int ENTITY_REACH_VANILLA = ENTITY_STAT;
            public static final int ENTITY_TARGET = 0xFFA500;

            public static final int ZONE_FLOOR = 0xFF00CC00;
            public static final int ZONE_MID = 0xFFCCCC00;
            public static final int ZONE_HIGH = 0xFFCC0000;

            public static final int PATH_START = 0xFF00FFFF;
            public static final int PATH_DEST_OK = 0xFFFFD700;
            public static final int PATH_DEST_FAIL = 0xFFFF4444;
            public static final int PATH_INFO = 0xFFAAAAAA;

            public static final int ROOM_RED = 0xFFFF0000;
            public static final int ROOM_GREEN = 0xFF00FF00;
            public static final int ROOM_BLUE = 0xFF0000FF;
            public static final int ROOM_YELLOW = 0xFFFFFF00;
            public static final int ROOM_MAGENTA = 0xFFFF00FF;
            public static final int ROOM_CYAN = 0xFF00FFFF;
            public static final int ROOM_ORANGE = 0xFFFF8000;
            public static final int ROOM_PURPLE = 0xFF8000FF;
            public static final int ROOM_GAP = 0xFFFF0000;

            private static final int[] ROOM_PALETTE = {
                ROOM_RED, ROOM_GREEN, ROOM_BLUE, ROOM_YELLOW,
                ROOM_MAGENTA, ROOM_CYAN, ROOM_ORANGE, ROOM_PURPLE
            };

            public static int[] roomPalette() {
                return ROOM_PALETTE.clone();
            }

            public static final int LOS_VISIBLE = 0xFF00FF00;
            public static final int LOS_OUT_OF_FOV = 0xFFFFFF00;
            public static final int LOS_BLOCKED = 0xFFFF4444;

            public static final int ZONE_ENV_DEFAULT = 0xFFFFFF;
            public static final int ZONE_ENV_NETHER = 0xFF4444;
            public static final int ZONE_ENV_END = 0xAA44FF;
            public static final int ZONE_ENV_ICE = 0x44FFFF;
            public static final int ZONE_ENV_DESERT = 0xFFFF44;
            public static final int ZONE_ENV_DESERT_WALL = 0xFFAA44;
            public static final int ZONE_ENV_OCEAN = 0x4444FF;
            public static final int ZONE_ENV_FOREST = 0x44FF44;
            public static final int ZONE_ENV_CAVE = 0x884422;
            public static final int ZONE_ENV_NIGHT = 0x6644AA;
            public static final int ZONE_ENV_DAY = 0xFFDD44;
            public static final int ZONE_ENV_DARK = 0x444444;
            public static final int ZONE_ENV_BRIGHT = 0xFFFFAA;
            public static final int ZONE_ENV_FALLBACK = 0xCCCCCC;

            public static final int GAP_VOID = 0x1A0A2E;
            public static final int GAP_END = 0x200030;
            public static final int GAP_NETHER = 0x2A1010;
            public static final int GAP_DARK = 0x0A0A1A;

            private Debug() {}
        }

        public static final class Flash {
            public static final int HEADSHOT = 0xFF0000;
            public static final int CRITICAL = 0xFF8800;
            public static final int DAMAGE = 0xFF4444;
            public static final int HEAL = 0x44FF44;
            public static final int SHIELD = 0x44FFFF;

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
            public static final int SHADOW_LIGHT = 0x26000000;
            public static final int SHADOW_HEAVY = (DesignTokens.Alpha.A50 << 24) | DesignTokens.Mask.NONE;

            private Utility() {}
        }

        private Overlay() {}
    }

    public static final class RadialMenu {
        public static final class Core {
            public static final int BG_DARK = 0xF0202035;
            public static final int SELECTED_BG = 0xEE252540;
            public static final int MACRO_SELECTED_BASE = 0xFF252540;
            public static final int UNSELECTED_BG = 0xDD1A1A30;
            public static final int BORDER = 0xFF404060;
            public static final int DIVIDER = 0xFF505070;
            public static final int MACRO_HOVER_BORDER = 0xFF606080;
            public static final int INNER_RING = 0xFF303050;
            public static final int CLOSE_HOVER = 0xFF453545;
            public static final int CLOSE_NORMAL = 0xF0252530;
            public static final int CLOSE_BORDER_HOVER = 0xFFFF6666;
            public static final int CENTER_ICON_BACK = 0xFF80AAFF;
            public static final int TEXT_PRIMARY = 0xFFFFFFFF;
            public static final int TEXT_SECONDARY = 0xFFBBBBCC;
            public static final int INACTIVE = 0xFFAAAAAA;

            private Core() {}
        }

        public static final class Badge {
            public static final int BG = 0xDD000000;

            private Badge() {}
        }

        public static final class Favorites {
            public static final int BG_SELECTED = 0xDDFFD700;
            public static final int BG_UNSELECTED = 0x88FFD700;
            public static final int STAR = 0xFFFFD700;

            private Favorites() {}
        }

        public static final class Item {
            public static final int STATUS_INACTIVE = 0xFF666666;

            private Item() {}
        }

        public static final class Overlay {
            public static final int BACKGROUND_RGB = 0x0D0D15;
            public static final int TOOLTIP_BG = 0xF0101020;
            public static final int SEARCH_BOX_BG = 0xEE101020;
            public static final int SEARCH_RESULT_BG = 0xCC101020;
            public static final int BREADCRUMB = 0xFFFFFFFF;
            public static final int EDIT_MODE_BG = 0xCC000000;
            public static final int EDIT_MODE_TEXT = 0xFFFF4444;
            public static final int THEME_INDICATOR_RGB = 0xFFFFFF;

            private Overlay() {}
        }

        public static final class Base {
            public static final int BG_DARK = 0xE6101020;
            public static final int BG_LIGHT = 0xCC1A1A35;
            public static final int SELECTED = 0xDD2A2A55;
            public static final int HOVER = 0xEE353566;
            public static final int ACTIVE = 0xFF00FF88;
            public static final int ACTIVE_GLOW = 0x4400FF88;
            public static final int INACTIVE = 0xFFAAAAAA;
            public static final int TEXT_PRIMARY = 0xFFFFFFFF;
            public static final int TEXT_SECONDARY = 0xFFAAAAAA;
            public static final int TEXT_HIGHLIGHT = 0xFF88CCFF;
            public static final int BORDER = 0xFF505080;
            public static final int BORDER_GLOW = 0x40FFFFFF;
            private static final int[] CATEGORY_COLORS = {
                0xFF00DDFF,
                0xFFFFDD00,
                0xFF00FF88,
                0xFFFF4466,
                0xFFFF9900,
                0xFFCC44FF
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
                0xE6101020, 0xCC1A1A35, 0xDD2A2A55, 0xEE353566,
                0xFF00FF88, 0x4400FF88, 0xFF505080, 0xFFFFFFFF, 0xFFAAAAAA
            );

            public static final PresetValues NEON = new PresetValues(
                0xE6000510, 0xCC0A0A20, 0xDD1A1A40, 0xEE2525AA,
                0xFF00FFFF, 0x4400FFFF, 0xFF0088FF, 0xFFFFFFFF, 0xFF88FFFF
            );

            public static final PresetValues CRIMSON = new PresetValues(
                0xE6200808, 0xCC351010, 0xDD552020, 0xEE663030,
                0xFFFF4444, 0x44FF4444, 0xFFFF6666, 0xFFFFFFFF, 0xFFFFAAAA
            );

            public static final PresetValues FOREST = new PresetValues(
                0xE6081808, 0xCC103510, 0xDD205520, 0xEE306630,
                0xFF44FF44, 0x4444FF44, 0xFF66FF66, 0xFFFFFFFF, 0xFFAAFFAA
            );

            public static final PresetValues GOLD = new PresetValues(
                0xE6181408, 0xCC352810, 0xDD554420, 0xEE665530,
                0xFFFFCC00, 0x44FFCC00, 0xFFFFDD44, 0xFFFFFFFF, 0xFFFFEEAA
            );

            public static final PresetValues MIDNIGHT = new PresetValues(
                0xF0050510, 0xDD080820, 0xCC151540, 0xBB202060,
                0xFF6666FF, 0x446666FF, 0xFF4444AA, 0xFFCCCCFF, 0xFF8888CC
            );

            public static final PresetValues MINIMAL = new PresetValues(
                0xE6181818, 0xCC282828, 0xDD383838, 0xEE484848,
                0xFFFFFFFF, 0x44FFFFFF, 0xFF606060, 0xFFFFFFFF, 0xFFAAAAAA
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
}
