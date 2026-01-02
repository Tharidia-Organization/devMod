package com.devmod.client.ui.editor.core;

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

    // ═══════════════════════════════════════════════════════════════════════════
    // GRID SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════

    /** Base grid unit (4px) - all dimensions should be multiples of this */
    public static final int GRID = 4;

    /** Snap a value to the nearest grid point */
    public static int snap(int value) {
        return Math.round(value / (float) GRID) * GRID;
    }

    /** Snap a value up to the next grid point */
    public static int snapUp(int value) {
        return ((value + GRID - 1) / GRID) * GRID;
    }

    /** Snap a value down to the previous grid point */
    public static int snapDown(int value) {
        return (value / GRID) * GRID;
    }

    /** Check if a value is on the grid */
    public static boolean isOnGrid(int value) {
        return value % GRID == 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR TOKENS - BACKGROUNDS (5 levels)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Bg {
        /** Darkest - scrim/overlay backdrop */
        public static final int LEVEL_0 = 0xFF050508;
        /** Screen background */
        public static final int LEVEL_1 = 0xFF0A0A0F;
        /** Panel background */
        public static final int LEVEL_2 = 0xFF101018;
        /** Card/section background */
        public static final int LEVEL_3 = 0xFF181820;
        /** Elevated element */
        public static final int LEVEL_4 = 0xFF202028;

        /** Get background by level (0-4) */
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

    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR TOKENS - SURFACES (5 levels)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Surface {
        /** Input fields, wells */
        public static final int LEVEL_0 = 0xFF1A1A24;
        /** Default surface */
        public static final int LEVEL_1 = 0xFF242430;
        /** Hover state */
        public static final int LEVEL_2 = 0xFF2E2E3C;
        /** Active/pressed */
        public static final int LEVEL_3 = 0xFF383848;
        /** Highlighted */
        public static final int LEVEL_4 = 0xFF424254;

        /** Get surface by level (0-4) */
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

    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR TOKENS - STROKES (3 levels)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Stroke {
        /** Subtle borders, dividers */
        public static final int MUTED = 0xFF2A2A38;
        /** Default borders */
        public static final int DEFAULT = 0xFF3A3A4C;
        /** Emphasized borders */
        public static final int EMPHASIS = 0xFF4A4A60;

        private Stroke() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR TOKENS - TEXT
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Text {
        /** Main text, titles */
        public static final int PRIMARY = 0xFFE8E8EC;
        /** Labels, captions */
        public static final int SECONDARY = 0xFFA8A8B4;
        /** Hints, disabled */
        public static final int MUTED = 0xFF686878;
        /** Text on light backgrounds */
        public static final int INVERSE = 0xFF101018;
        /** Title text */
        public static final int TITLE = PRIMARY;
        /** Value text */
        public static final int VALUE = Accent.PRIMARY;
        /** Formula/code text */
        public static final int FORMULA = Semantic.SUCCESS;
        /** Disabled text */
        public static final int DISABLED = Utility.DISABLED;
        /** Info text */
        public static final int INFO = Semantic.INFO;
        /** Warning text */
        public static final int WARNING = Semantic.WARNING;
        /** Pure white text */
        public static final int WHITE = 0xFFFFFFFF;
        /** Accent text */
        public static final int ACCENT = Accent.PRIMARY;

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

    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR TOKENS - ACCENTS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Accent {
        /** Primary accent (cyan) */
        public static final int PRIMARY = 0xFF00D4FF;
        /** Secondary accent (magenta) */
        public static final int SECONDARY = 0xFFFF00AA;
        /** Glow effect (25% alpha) */
        public static final int GLOW = 0x4000D4FF;
        /** Primary accent alias */
        public static final int CYAN = PRIMARY;
        /** Success accent */
        public static final int GREEN = Semantic.SUCCESS;
        /** Warning accent */
        public static final int ORANGE = Semantic.WARNING;
        /** Error accent */
        public static final int RED = Semantic.ERROR;
        /** Info accent */
        public static final int BLUE = Semantic.INFO;
        /** Special/rare accent */
        public static final int PURPLE = SECONDARY;
        /** Highlight accent */
        public static final int YELLOW = Semantic.WARNING;
        /** Gold accent */
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

    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR TOKENS - SEMANTIC
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Semantic {
        /** Success states */
        public static final int SUCCESS = 0xFF4ADE80;
        /** Success background (muted) */
        public static final int SUCCESS_MUTED = 0x404ADE80;

        /** Warning states */
        public static final int WARNING = 0xFFFACC15;
        /** Warning background (muted) */
        public static final int WARNING_MUTED = 0x40FACC15;

        /** Error states */
        public static final int ERROR = 0xFFF87171;
        /** Error background (muted) */
        public static final int ERROR_MUTED = 0x40F87171;

        /** Info states */
        public static final int INFO = 0xFF60A5FA;
        /** Info background (muted) */
        public static final int INFO_MUTED = 0x4060A5FA;

        private Semantic() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIMITIVE COLOR PALETTES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
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

    /**
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

    // ═══════════════════════════════════════════════════════════════════════════
    // PANEL COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Panel background colors for different contexts.
     */
    public static final class Panel {
        /** Default panel background */
        public static final int BG = Bg.LEVEL_2;
        /** Elevated/raised panel */
        public static final int ELEVATED = Bg.LEVEL_3;
        /** Content area background */
        public static final int CONTENT = Bg.LEVEL_3;
        /** Header panel background */
        public static final int HEADER = Bg.LEVEL_2;
        /** Popover/modal panel */
        public static final int POPOVER = Bg.LEVEL_4;

        private Panel() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INPUT FIELD COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Input field colors (backgrounds, borders, focus states).
     */
    public static final class Input {
        /** Input background */
        public static final int BG = Surface.LEVEL_0;
        /** Input border */
        public static final int BORDER = Stroke.DEFAULT;
        /** Input border on hover */
        public static final int BORDER_HOVER = Stroke.EMPHASIS;
        /** Input border on focus */
        public static final int BORDER_FOCUS = Accent.PRIMARY;
        /** Placeholder text */
        public static final int PLACEHOLDER = Text.MUTED;
        /** Input text */
        public static final int TEXT = Text.PRIMARY;
        /** Disabled input background */
        public static final int DISABLED_BG = Surface.LEVEL_0;
        /** Disabled input border */
        public static final int DISABLED_BORDER = Stroke.MUTED;

        private Input() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOOLTIP COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tooltip styling colors.
     */
    public static final class Tooltip {
        /** Tooltip background (high opacity) */
        public static final int BG = 0xF0181820;
        /** Tooltip border */
        public static final int BORDER = Stroke.DEFAULT;
        /** Tooltip shadow */
        public static final int SHADOW = 0x80000000;

        private Tooltip() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RADIAL MENU COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Radial {
        /** Center hub background */
        public static final int HUB_BG = 0xFF181820;
        /** Center hub border */
        public static final int HUB_BORDER = 0xFF3A3A4C;
        /** Segment default background (80% opacity) */
        public static final int SEGMENT_BG = 0xCC181820;
        /** Segment hover background */
        public static final int SEGMENT_HOVER = 0xCC202028;
        /** Segment selected background */
        public static final int SEGMENT_SELECTED = 0xCC2A2A38;
        /** Segment border */
        public static final int SEGMENT_BORDER = 0xFF3A3A4C;
        /** Segment divider */
        public static final int SEGMENT_DIVIDER = 0xFF2A2A38;
        /** Icon default */
        public static final int ICON_DEFAULT = Text.SECONDARY;
        /** Icon hover */
        public static final int ICON_HOVER = Accent.PRIMARY;
        /** Label text */
        public static final int LABEL = Text.PRIMARY;
        /** Sublabel text */
        public static final int SUBLABEL = Text.MUTED;

        // ───────────────────────────────────────────────────────────────────
        // MACRO CATEGORY COLORS (6 primary)
        // ───────────────────────────────────────────────────────────────────
        /** Category: Analyze (cyan/blue) */
        public static final int CAT_ANALYZE = 0xFF4488FF;
        /** Category: Telemetry (purple) */
        public static final int CAT_TELEMETRY = 0xFFAA55FF;
        /** Category: Combat (red) */
        public static final int CAT_COMBAT = 0xFFFF4444;
        /** Category: Arena (green) */
        public static final int CAT_ARENA = 0xFF44FF88;
        /** Category: Tools (orange) */
        public static final int CAT_TOOLS = 0xFFFFAA00;
        /** Category: Play (light pink) */
        public static final int CAT_PLAY = 0xFFFFCCCC;

        // ───────────────────────────────────────────────────────────────────
        // ANALYZE SUBCATEGORY COLORS (blue gradient light→dark)
        // ───────────────────────────────────────────────────────────────────
        /** Analyze: Debug tools */
        public static final int ANALYZE_DEBUG = 0xFF4488FF;
        /** Analyze: HUD overlays */
        public static final int ANALYZE_HUD = 0xFF4488FF;
        /** Analyze: Spatial/render debug */
        public static final int ANALYZE_SPATIAL = 0xFF66AAFF;
        /** Analyze: Collision debug */
        public static final int ANALYZE_COLLISION = 0xFF66AAFF;
        /** Analyze: Performance */
        public static final int ANALYZE_PERFORMANCE = 0xFF88CCFF;
        /** Analyze: Mob visualizers */
        public static final int ANALYZE_MOBS = 0xFF88CCFF;
        /** Analyze: Density visualizers */
        public static final int ANALYZE_DENSITY = 0xFFAADDFF;
        /** Analyze: Safe spots */
        public static final int ANALYZE_SAFE_SPOTS = 0xFFCCEEFF;
        /** Analyze: Light levels */
        public static final int ANALYZE_LIGHT = 0xFFCCEEFF;
        /** Analyze: Spawnability */
        public static final int ANALYZE_SPAWN = 0xFFEEFFFF;
        /** Analyze: Room bounds */
        public static final int ANALYZE_ROOM = 0xFFBBDDFF;

        // ───────────────────────────────────────────────────────────────────
        // TELEMETRY SUBCATEGORY COLORS (purple gradient)
        // ───────────────────────────────────────────────────────────────────
        /** Telemetry: Operations */
        public static final int TELEMETRY_OPS = 0xFFAADDFF;
        /** Telemetry: Dashboard */
        public static final int TELEMETRY_DASHBOARD = 0xFFAA55FF;
        /** Telemetry: Exports */
        public static final int TELEMETRY_EXPORT = 0xFF8844DD;

        // ───────────────────────────────────────────────────────────────────
        // COMBAT SUBCATEGORY COLORS (red gradient)
        // ───────────────────────────────────────────────────────────────────
        /** Combat: Actions */
        public static final int COMBAT_ACTIONS = 0xFFFF4444;
        /** Combat: Damage/defense stats */
        public static final int COMBAT_DAMAGE = 0xFFFF8888;
        /** Combat: Defense */
        public static final int COMBAT_DEFENSE = 0xFFFF8888;
        /** Combat: Weapon editor */
        public static final int COMBAT_WEAPON = 0xFFFFAAAA;
        /** Combat: Shield editor (neutral gray) */
        public static final int COMBAT_SHIELD = 0xFFDDDDDD;

        // ───────────────────────────────────────────────────────────────────
        // ARENA SUBCATEGORY COLORS (green gradient)
        // ───────────────────────────────────────────────────────────────────
        /** Arena: Management */
        public static final int ARENA_MANAGE = 0xFF44FF88;
        /** Arena: Templates */
        public static final int ARENA_TEMPLATES = 0xFF66FFAA;
        /** Arena: Spawning */
        public static final int ARENA_SPAWNING = 0xFF88FFCC;
        /** Arena: Hazards */
        public static final int ARENA_HAZARDS = 0xFFAAFFDD;
        /** Arena: Rewards */
        public static final int ARENA_REWARDS = 0xFFCCFFEE;

        // ───────────────────────────────────────────────────────────────────
        // TOOLS SUBCATEGORY COLORS (orange/yellow gradient)
        // ───────────────────────────────────────────────────────────────────
        /** Tools: Primary */
        public static final int TOOLS_PRIMARY = 0xFFFFAA00;
        /** Tools: Editor */
        public static final int TOOLS_EDITOR = 0xFFFFCC66;
        /** Tools: Secondary */
        public static final int TOOLS_SECONDARY = 0xFFFFDD99;
        /** Tools: Utility */
        public static final int TOOLS_UTILITY = 0xFFFFEECC;

        // ───────────────────────────────────────────────────────────────────
        // PLAY SUBCATEGORY COLORS (warm/social)
        // ───────────────────────────────────────────────────────────────────
        /** Play: Party */
        public static final int PLAY_PARTY = 0xFFFFCCCC;
        /** Play: Social */
        public static final int PLAY_SOCIAL = 0xFFFFFFFF;
        /** Play: Quests */
        public static final int PLAY_QUESTS = 0xFFFFEEEE;
        /** Play: Communication */
        public static final int PLAY_COMMS = 0xFFEEFFFF;
        /** Play: Leaderboard */
        public static final int PLAY_LEADERBOARD = 0xFFFFDD88;
        /** Play: Season Pass */
        public static final int PLAY_SEASON = 0xFFFFEEAA;

        // Additional telemetry colors
        /** Telemetry: Spatial analysis */
        public static final int TELEMETRY_SPATIAL = 0xFF7755DD;

        // Additional combat colors
        /** Combat: Armor configuration */
        public static final int COMBAT_ARMOR = 0xFFAABBDD;
        /** Combat: Abilities */
        public static final int COMBAT_ABILITIES = 0xFFFFCC44;
        /** Combat: Debug tools */
        public static final int COMBAT_DEBUG = 0xFFFF6666;

        // Additional arena colors
        /** Arena: Endurance mode */
        public static final int ARENA_ENDURANCE = 0xFF88FF66;
        /** Arena: Wave control */
        public static final int ARENA_WAVES = 0xFFAAFF88;
        /** Arena: Party management */
        public static final int ARENA_PARTY = 0xFFCCFFAA;

        // Additional tools colors
        /** Tools: Testing */
        public static final int TOOLS_TESTING = 0xFFFFBB44;
        /** Tools: Notifications */
        public static final int TOOLS_NOTIFY = 0xFFFFDD66;
        /** Tools: Mailbox */
        public static final int TOOLS_MAILBOX = 0xFFFFEE88;
        /** Tools: Settings */
        public static final int TOOLS_SETTINGS = 0xFFFFFFAA;

        private Radial() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HUD OVERLAY COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Hud {
        /** Default HUD panel background (80% opacity) */
        public static final int PANEL_BG = 0xCC0A0A0F;
        /** HUD panel border (50% opacity) */
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
        public static final int XP = Accent.PRIMARY;
        public static final int XP_BG = 0x4000D4FF;

        // Boss health
        public static final int BOSS_HEALTH = Accent.SECONDARY;
        public static final int BOSS_PHASE = Semantic.WARNING;

        // Wave counter
        public static final int WAVE_TEXT = Text.WHITE;
        public static final int WAVE_NUMBER = Accent.PRIMARY;

        // Timer
        public static final int TIMER_NORMAL = Text.PRIMARY;
        public static final int TIMER_WARNING = Semantic.WARNING;
        public static final int TIMER_CRITICAL = Semantic.ERROR;

        private Hud() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTING MODE COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Colors for IntegratedTestSession types and testing overlays.
     */
    public static final class TestingMode {
        /** Combat test sessions (orange-red) */
        public static final int COMBAT = 0xFFFF6644;
        /** Boss fight test sessions (purple) */
        public static final int BOSS_FIGHT = 0xFFAA44FF;
        /** Survival waves test sessions (green) */
        public static final int SURVIVAL = 0xFF44FF88;
        /** Damage validation test sessions (orange) */
        public static final int DAMAGE_VALIDATION = 0xFFFFAA00;
        /** Performance stress test sessions (blue) */
        public static final int PERFORMANCE = 0xFF4488FF;
        /** Custom test sessions (gray) */
        public static final int CUSTOM = 0xFF888888;

        /** Endless mode pulse color */
        public static final int PULSE = 0xFF4488FF;
        /** Progress bar border */
        public static final int PROGRESS_BORDER = 0xFF555555;

        private TestingMode() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NOTIFICATION COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Notification {
        /** Default notification */
        public static final int DEFAULT_BG = 0xFF202028;
        public static final int DEFAULT_BORDER = 0xFF3A3A4C;

        /** Success notification (90% opacity) */
        public static final int SUCCESS_BG = 0xE61F6A3F;
        public static final int SUCCESS_BORDER = Semantic.SUCCESS;

        /** Warning notification (90% opacity) */
        public static final int WARNING_BG = 0xE6A06000;
        public static final int WARNING_BORDER = Semantic.WARNING;

        /** Error notification (90% opacity) */
        public static final int ERROR_BG = 0xE67A1A1E;
        public static final int ERROR_BORDER = Semantic.ERROR;

        /** Info notification (90% opacity) */
        public static final int INFO_BG = 0xE62060C0;
        public static final int INFO_BORDER = Semantic.INFO;

        private Notification() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR TOKENS - IMPACT BUTTON (special button styles)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class ImpactButton {
        /** Default button base */
        public static final int DEFAULT_BASE = 0xFF101018;
        /** Default button border */
        public static final int DEFAULT_BORDER = Stroke.DEFAULT;
        /** Ghost button base (more transparent) */
        public static final int GHOST_BASE = 0xFF0A0A10;
        /** Ghost button border */
        public static final int GHOST_BORDER = Stroke.MUTED;

        /** Primary button base (teal) */
        public static final int PRIMARY_BASE = 0xFF0E5569;
        /** Primary button border */
        public static final int PRIMARY_BORDER = 0xFF1A8BAA;

        /** Danger button base (dark red) */
        public static final int DANGER_BASE = 0xFF7A1A1E;
        /** Danger button border */
        public static final int DANGER_BORDER = 0xFFB23036;

        /** Success button base (forest green) */
        public static final int SUCCESS_BASE = 0xFF1F6A3F;
        /** Success button border */
        public static final int SUCCESS_BORDER = 0xFF2DA45A;

        private ImpactButton() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR TOKENS - UTILITY
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Utility {
        /** Focus ring color */
        public static final int FOCUS = 0xFF00D4FF;
        /** Selection background */
        public static final int SELECTION = 0x3000D4FF;
        /** Modal backdrop (scrim) */
        public static final int SCRIM = 0xCC000000;
        /** Disabled elements */
        public static final int DISABLED = 0xFF505060;
        /** Drop shadows */
        public static final int SHADOW = 0x80000000;
        /** Cyan glow */
        public static final int GLOW_CYAN = 0x6000D4FF;

        private Utility() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPACING TOKENS (4px grid)
    // ═══════════════════════════════════════════════════════════════════════════

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

        /** Get spacing by level (0-10) */
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

    // ═══════════════════════════════════════════════════════════════════════════
    // RADIUS TOKENS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Radius {
        /** No rounding (pixel-perfect) */
        public static final int NONE = 0;
        /** Subtle rounding */
        public static final int SM = 2;
        /** Default buttons/inputs */
        public static final int MD = 4;
        /** Cards, panels */
        public static final int LG = 6;
        /** Modals */
        public static final int XL = 8;
        /** Pills, badges */
        public static final int FULL = 9999;

        private Radius() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STROKE WIDTH TOKENS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class StrokeWidth {
        /** No border */
        public static final int NONE = 0;
        /** Default borders */
        public static final int THIN = 1;
        /** Emphasis, focus rings */
        public static final int MEDIUM = 2;
        /** Major emphasis */
        public static final int THICK = 3;

        private StrokeWidth() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ELEVATION TOKENS (shadow/glow specs)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Elevation {

        public record ElevationSpec(int blur, float opacity, int yOffset) {}

        public static final ElevationSpec LEVEL_0 = new ElevationSpec(0, 0f, 0);
        public static final ElevationSpec LEVEL_1 = new ElevationSpec(2, 0.10f, 1);
        public static final ElevationSpec LEVEL_2 = new ElevationSpec(4, 0.15f, 2);
        public static final ElevationSpec LEVEL_3 = new ElevationSpec(8, 0.20f, 4);
        public static final ElevationSpec LEVEL_4 = new ElevationSpec(12, 0.25f, 6);
        public static final ElevationSpec LEVEL_5 = new ElevationSpec(16, 0.30f, 8);
        public static final ElevationSpec LEVEL_6 = new ElevationSpec(24, 0.40f, 12);

        /** Get elevation spec by level (0-6) */
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

    // ═══════════════════════════════════════════════════════════════════════════
    // MOTION TOKENS (durations in milliseconds)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Motion {
        /** Immediate */
        public static final int INSTANT = 0;
        /** Hover states */
        public static final int MICRO = 80;
        /** Micro interactions */
        public static final int FAST = 120;
        /** Standard transitions */
        public static final int NORMAL = 180;
        /** Panel open/close */
        public static final int SLOW = 240;
        /** Complex animations */
        public static final int SLOWER = 320;
        /** Page transitions */
        public static final int SLOWEST = 480;

        /**
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Z-ORDER TOKENS (UI layering)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
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
        /** Base layer - screen content, backgrounds */
        public static final float BASE = 0f;
        /** Content layer - main UI elements */
        public static final float CONTENT = 50f;
        /** Panel layer - floating panels, sidebars */
        public static final float PANEL = 100f;
        /** Overlay layer - HUD elements, overlays */
        public static final float OVERLAY = 200f;
        /** Radial menu layer */
        public static final float RADIAL = 300f;
        /** Notification layer - toast notifications */
        public static final float NOTIFICATION = 400f;
        /** Tooltip layer */
        public static final float TOOLTIP = 500f;
        /** Dropdown layer - menus, popovers, dropdowns */
        public static final float DROPDOWN = 600f;
        /** Modal layer - modal dialogs, blocking UI */
        public static final float MODAL = 700f;
        /** Debug layer - debug overlays, dev tools */
        public static final float DEBUG = 800f;
        /** Cursor layer - mouse cursor, drag previews */
        public static final float CURSOR = 900f;

        // Sublayer offsets
        /** Offset to place element behind its layer's default */
        public static final float BEHIND = -10f;
        /** Offset to place element slightly above its layer */
        public static final float ABOVE = 10f;
        /** Offset to place element at foreground of its layer */
        public static final float FOREGROUND = 25f;

        /** Get layer value with sublayer offset */
        public static float at(float layer, float offset) {
            return layer + offset;
        }

        /** Check if z1 is in front of z2 */
        public static boolean isInFront(float z1, float z2) {
            return z1 > z2;
        }

        /** Check if z1 is behind z2 */
        public static boolean isBehind(float z1, float z2) {
            return z1 < z2;
        }

        /** Get layer name for debugging */
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

    // ═══════════════════════════════════════════════════════════════════════════
    // ICON SIZE TOKENS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Icon {
        /** Inline with small text */
        public static final int XS = 12;
        /** Default inline */
        public static final int SM = 16;
        /** Buttons */
        public static final int MD = 20;
        /** Headers, tabs */
        public static final int LG = 24;
        /** Featured/hero */
        public static final int XL = 32;
        /** Splash/empty states */
        public static final int XXL = 48;

        private Icon() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ALPHA SCALE (opacity values 0-255)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Alpha {
        public static final int A0   = 0x00;   // Transparent
        public static final int A10  = 0x1A;   // 10%
        public static final int A20  = 0x33;   // 20%
        public static final int A25  = 0x40;   // 25%
        public static final int A30  = 0x4D;   // 30%
        public static final int A40  = 0x66;   // 40%
        public static final int A50  = 0x80;   // 50%
        public static final int A60  = 0x99;   // 60%
        public static final int A70  = 0xB3;   // 70%
        public static final int A80  = 0xCC;   // 80%
        public static final int A90  = 0xE6;   // 90%
        public static final int A95  = 0xF2;   // 95%
        public static final int A100 = 0xFF;   // Opaque

        private Alpha() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GRID LAYOUT UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Grid {
        /** Base grid unit (4px) */
        public static final int UNIT = GRID;
        /** Major grid (16px = 4 units) */
        public static final int MAJOR = 16;
        /** Half unit (2px - use sparingly) */
        public static final int HALF = 2;

        // --- Snap functions ---

        /** Snap value to major grid (16px) */
        public static int snapMajor(int value) {
            return Math.round(value / (float) MAJOR) * MAJOR;
        }

        /** Check if value is aligned to major grid */
        public static boolean isMajorAligned(int value) {
            return value % MAJOR == 0;
        }

        // --- Unit conversions ---

        /** Convert grid units to pixels (count × 4) */
        public static int units(int count) {
            return count * UNIT;
        }

        /** Convert major grid units to pixels (count × 16) */
        public static int major(int count) {
            return count * MAJOR;
        }

        /** Convert pixels to grid units (rounded) */
        public static int toUnits(int pixels) {
            return Math.round(pixels / (float) UNIT);
        }

        /** Convert pixels to major grid units (rounded) */
        public static int toMajor(int pixels) {
            return Math.round(pixels / (float) MAJOR);
        }

        // --- Spacing shortcuts ---

        /** 4px - Micro gap */
        public static int xs() { return Space._2; }
        /** 8px - Tight spacing */
        public static int sm() { return Space._4; }
        /** 12px - Compact spacing */
        public static int md() { return Space._5; }
        /** 16px - Default spacing */
        public static int lg() { return Space._6; }
        /** 24px - Section spacing */
        public static int xl() { return Space._7; }
        /** 32px - Large spacing */
        public static int xxl() { return Space._8; }

        // --- Layout calculations ---

        /**
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

        /** Calculate column width with default gap (16px) */
        public static int columnWidth(int columns, int totalWidth) {
            return columnWidth(columns, totalWidth, MAJOR);
        }

        /** Calculate X position for column index */
        public static int columnX(int columnIndex, int columnWidth, int gap, int startX) {
            return startX + columnIndex * (columnWidth + gap);
        }

        /** Calculate row height for n-row layout */
        public static int rowHeight(int rows, int totalHeight, int gap) {
            if (rows <= 0) return totalHeight;
            int totalGaps = (rows - 1) * gap;
            int availableHeight = totalHeight - totalGaps;
            return snapDown(availableHeight / rows);
        }

        /** Calculate Y position for row index */
        public static int rowY(int rowIndex, int rowHeight, int gap, int startY) {
            return startY + rowIndex * (rowHeight + gap);
        }

        // --- Centering ---

        /** Center an element horizontally within a container */
        public static int centerX(int elementWidth, int containerWidth) {
            return snap((containerWidth - elementWidth) / 2);
        }

        /** Center an element vertically within a container */
        public static int centerY(int elementHeight, int containerHeight) {
            return snap((containerHeight - elementHeight) / 2);
        }

        /** Center element horizontally, returning absolute position */
        public static int centerInX(int elementWidth, int containerX, int containerWidth) {
            return containerX + centerX(elementWidth, containerWidth);
        }

        /** Center element vertically, returning absolute position */
        public static int centerInY(int elementHeight, int containerY, int containerHeight) {
            return containerY + centerY(elementHeight, containerHeight);
        }

        // --- Alignment ---

        /** Align element to left edge of container */
        public static int alignLeft(int containerX, int padding) {
            return containerX + snap(padding);
        }

        /** Align element to right edge of container */
        public static int alignRight(int elementWidth, int containerX, int containerWidth, int padding) {
            return containerX + containerWidth - elementWidth - snap(padding);
        }

        /** Align element to top edge of container */
        public static int alignTop(int containerY, int padding) {
            return containerY + snap(padding);
        }

        /** Align element to bottom edge of container */
        public static int alignBottom(int elementHeight, int containerY, int containerHeight, int padding) {
            return containerY + containerHeight - elementHeight - snap(padding);
        }

        // --- Responsive helpers ---

        /** Clamp dimension to valid range (grid-aligned) */
        public static int clampDimension(int value, int min, int max) {
            return snap(Math.max(min, Math.min(max, value)));
        }

        /** Calculate responsive width (percentage of container, grid-aligned) */
        public static int percentWidth(float percentage, int containerWidth) {
            return snap((int)(containerWidth * Math.max(0f, Math.min(1f, percentage))));
        }

        /** Calculate responsive height (percentage of container, grid-aligned) */
        public static int percentHeight(float percentage, int containerHeight) {
            return snap((int)(containerHeight * Math.max(0f, Math.min(1f, percentage))));
        }

        // --- Content bounds ---

        /** Calculate content area with uniform padding */
        public static int[] contentBounds(int x, int y, int width, int height, int padding) {
            int p = snap(padding);
            return new int[] { x + p, y + p, width - p * 2, height - p * 2 };
        }

        /** Calculate content area with separate horizontal/vertical padding */
        public static int[] contentBounds(int x, int y, int width, int height, int paddingH, int paddingV) {
            int ph = snap(paddingH);
            int pv = snap(paddingV);
            return new int[] { x + ph, y + pv, width - ph * 2, height - pv * 2 };
        }

        // --- Flow layout ---

        /** Calculate positions for horizontal flow layout */
        public static int[] flowHorizontal(int count, int itemWidth, int gap, int startX) {
            int[] positions = new int[count];
            int x = startX;
            for (int i = 0; i < count; i++) {
                positions[i] = x;
                x += itemWidth + gap;
            }
            return positions;
        }

        /** Calculate positions for vertical flow layout */
        public static int[] flowVertical(int count, int itemHeight, int gap, int startY) {
            int[] positions = new int[count];
            int y = startY;
            for (int i = 0; i < count; i++) {
                positions[i] = y;
                y += itemHeight + gap;
            }
            return positions;
        }

        /** Calculate total size of flow layout */
        public static int flowSize(int count, int itemSize, int gap) {
            if (count <= 0) return 0;
            return count * itemSize + (count - 1) * gap;
        }

        private Grid() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT DIMENSIONS
    // Aligned with EditorDimensions - these are the authoritative values
    // ═══════════════════════════════════════════════════════════════════════════

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
        /** Alias for LIST_ITEM_HEIGHT_COMPACT - use for compact rows */
        public static final int ROW_HEIGHT_COMPACT = LIST_ITEM_HEIGHT_COMPACT;
        /** Alias for LIST_ITEM_HEIGHT_STANDARD - use for standard rows */
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

    // ═══════════════════════════════════════════════════════════════════════════
    // LEGACY COMPATIBILITY (DesignTokens -> DesignTokens)
    // ═══════════════════════════════════════════════════════════════════════════

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
        public static final int GLOW = withAlpha(Accent.PRIMARY, 0x55);

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
        public static final int ACCENT = Accent.PRIMARY;
        public static final int SEPARATOR = Stroke.MUTED;
        public static final int SUCCESS = Semantic.SUCCESS;
        public static final int ERROR = Semantic.ERROR;
        public static final int WARNING = Semantic.WARNING;
        public static final int HOVER = Stroke.EMPHASIS;
        public static final int LIGHT = Stroke.EMPHASIS;
        public static final int GLOW = withAlpha(Accent.PRIMARY, 0x55);

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
        public static final int SPECIAL = Accent.SECONDARY;
        public static final int NEUTRAL = 0xFF78909C;
        public static final int PERCENT = Accent.PRIMARY;

        private SliderColors() {}
    }

    public static final class Slider {
        public static final int TRACK = Surface.LEVEL_1;
        public static final int TRACK_ACTIVE = Surface.LEVEL_2;
        public static final int TRACK_DISABLED = Surface.LEVEL_0;
        public static final int FILLED = Accent.PRIMARY;
        public static final int THUMB = Surface.LEVEL_3;
        public static final int THUMB_HOVER = Surface.LEVEL_4;
        public static final int THUMB_DRAG = Accent.PRIMARY;
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
        public static final int INDICATOR = Accent.PRIMARY;

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

    // ═══════════════════════════════════════════════════════════════════════════
    // STANDARD PANEL ALPHA
    // ═══════════════════════════════════════════════════════════════════════════

    /** Standard alpha for semi-transparent panels (0xE0 = 224 = 87.8%) */
    public static final int PANEL_ALPHA = 0xE0;

    /** Apply standard panel alpha to a color */
    public static int withPanelAlpha(int color) {
        return (PANEL_ALPHA << 24) | (color & 0x00FFFFFF);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    /** Set alpha on a color */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** Get alpha from a color (0-255) */
    public static int getAlpha(int color) {
        return (color >> 24) & 0xFF;
    }

    /** Darken a color by a factor (0.0 = no change, 1.0 = black) */
    public static int darken(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * (1 - factor));
        int g = (int) (((color >> 8) & 0xFF) * (1 - factor));
        int b = (int) ((color & 0xFF) * (1 - factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Lighten a color by a factor (0.0 = no change, 1.0 = white) */
    public static int lighten(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) + (255 - (color & 0xFF)) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Interpolate between two colors */
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

    /** Blend two colors with a factor */
    public static int blend(int color1, int color2, float factor) {
        return lerp(color1, color2, factor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOGGLE COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Toggle {
        /** Toggle ON state - success green */
        public static final int ON = Semantic.SUCCESS;
        /** Toggle OFF state - input background */
        public static final int OFF = Surface.LEVEL_0;
        /** Toggle ON hover state - lightened success */
        public static final int ON_HOVER = lighten(ON, 0.15f);
        /** Toggle OFF hover state - surface hover */
        public static final int OFF_HOVER = Surface.LEVEL_2;
        /** Toggle track disabled */
        public static final int TRACK_DISABLED = Surface.LEVEL_0;
        /** Toggle thumb */
        public static final int THUMB = 0xFFD0D0D8;
        /** Toggle thumb disabled */
        public static final int THUMB_DISABLED = Utility.DISABLED;

        public static int ON() { return ThemeManager.INSTANCE.success(); }
        public static int OFF() { return ThemeManager.INSTANCE.inputBg(); }
        public static int ON_HOVER() { return lighten(ThemeManager.INSTANCE.success(), 0.15f); }
        public static int OFF_HOVER() { return ThemeManager.INSTANCE.hoverBg(); }

        private Toggle() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SOUND FEEDBACK
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Helper class for playing UI sound feedback.
     * Provides consistent audio cues for user actions.
     */
    public static final class Sound {
        private Sound() {}

        /** Play a click sound for button presses */
        public static void click() {
            playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
        }

        /** Play a success sound for completed actions (save, confirm) */
        public static void success() {
            playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 1.5f);
        }

        /** Play an error sound for failed actions */
        public static void error() {
            playSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
        }

        /** Play a warning sound */
        public static void warning() {
            playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.8f);
        }

        /** Play a toggle on sound */
        public static void toggleOn() {
            playSound(net.minecraft.sounds.SoundEvents.LEVER_CLICK, 0.5f, 1.2f);
        }

        /** Play a toggle off sound */
        public static void toggleOff() {
            playSound(net.minecraft.sounds.SoundEvents.LEVER_CLICK, 0.5f, 0.8f);
        }

        /** Play a notification sound */
        public static void notification() {
            playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
        }

        /** Play a save confirmation sound */
        public static void save() {
            playSound(net.minecraft.sounds.SoundEvents.VILLAGER_YES, 0.8f, 1.2f);
        }

        /** Play a delete/reset sound */
        public static void delete() {
            playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.6f, 0.6f);
        }

        private static void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
            if (sound == null) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                net.minecraft.client.resources.sounds.SimpleSoundInstance instance =
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, pitch, volume);
                if (instance != null) {
                    mc.getSoundManager().play(instance);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BODY PART COLORS (for damage/hitbox visualization)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class BodyPart {
        public static final int HEAD = 0xFF00FFFF;
        public static final int BODY = 0xFF00FF00;
        public static final int ARMS = 0xFFFFFF00;
        public static final int LEGS = 0xFFFF0000;

        private BodyPart() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS COLORS (themed variants)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Status {
        public static final int SUCCESS = Semantic.SUCCESS;
        public static final int ERROR = Semantic.ERROR;
        public static final int WARNING = Semantic.WARNING;
        public static final int INFO = Semantic.INFO;
        public static final int PENDING = Text.MUTED;

        public static int SUCCESS() { return ThemeManager.INSTANCE.success(); }
        public static int ERROR() { return ThemeManager.INSTANCE.error(); }
        public static int WARNING() { return ThemeManager.INSTANCE.warning(); }
        public static int INFO() { return ThemeManager.INSTANCE.info(); }
        public static int PENDING() { return ThemeManager.INSTANCE.textMuted(); }

        private Status() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION CONSTANTS (layout helpers)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Position {
        public static final int TITLE_Y = 8;
        public static final int SUBTITLE_Y = 45;
        public static final int CONTENT_START_Y = 60;
        public static final int BOTTOM_MARGIN = 30;

        private Position() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RARITY COLORS (item rarity display)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Rarity {
        public static final int COMMON = 0xFF888888;
        public static final int UNCOMMON = 0xFF55FF55;
        public static final int RARE = 0xFF5555FF;
        public static final int EPIC = 0xFFAA00AA;
        public static final int LEGENDARY = 0xFFFFAA00;

        private Rarity() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PANEL DIMENSIONS (from EditorConstants)
    // ═══════════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════════
    // ANIMATION TIMING (all 0 for immediate mode)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Timing {
        /** Fade duration (disabled) */
        public static final int FADE_MS = 0;
        /** Slide duration (disabled) */
        public static final int SLIDE_MS = 0;
        /** Tooltip delay */
        public static final int TOOLTIP_DELAY_MS = 200;
        /** Button press feedback */
        public static final int BUTTON_PRESS_MS = 0;

        private Timing() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ADDITIONAL UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Set alpha on a color (alias for withAlpha for compatibility).
     */
    public static int setAlpha(int color, int alpha) {
        return withAlpha(color, alpha);
    }

    /**
     * Get health color based on percentage.
     * @param healthPercent Health percentage (0-100)
     * @return Green if > 50%, Yellow if > 25%, Red otherwise
     */
    public static int getHealthColor(float healthPercent) {
        if (healthPercent > 50) return Semantic.SUCCESS;
        if (healthPercent > 25) return Semantic.WARNING;
        return Semantic.ERROR;
    }

    /**
     * Calculate centered X position.
     */
    public static int centerX(int screenWidth, int elementWidth) {
        return (screenWidth - elementWidth) / 2;
    }

    /**
     * Calculate tab start X position for centered tabs.
     */
    public static int tabStartX(int screenWidth, int tabCount, int tabWidth) {
        return (screenWidth - (tabCount * tabWidth)) / 2;
    }
}
