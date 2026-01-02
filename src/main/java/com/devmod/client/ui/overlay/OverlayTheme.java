package com.devmod.client.ui.overlay;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Centralized color tokens for HUD overlays.
 *
 * <p>Extends DesignTokens with overlay-specific colors and transparency levels.
 * All overlay rendering MUST use these tokens for consistency.
 *
 * <p>Overlay backgrounds use reduced opacity to remain readable over gameplay.
 * Use {@link Alpha} constants for standard transparency levels.
 *
 * @see DesignTokens for base design tokens
 */
public final class OverlayTheme {

    private OverlayTheme() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // ALPHA LEVELS (overlay transparency)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Alpha {
        /** Near-opaque (87.5%) - modal overlays */
        public static final int HEAVY = 0xE0;
        /** Semi-transparent (80%) - standard panels */
        public static final int STANDARD = 0xCC;
        /** Light (67%) - less intrusive overlays */
        public static final int LIGHT = 0xAA;
        /** Subtle (50%) - minimal overlays */
        public static final int SUBTLE = 0x80;
        /** Ghost (33%) - background hints */
        public static final int GHOST = 0x55;
        /** Divider (26%) - separators */
        public static final int DIVIDER = 0x44;
        /** Glow (25%) - effects */
        public static final int GLOW = 0x40;

        private Alpha() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PANEL BACKGROUNDS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Panel {
        /** Base blue-tinted dark background (RGB only, apply alpha) */
        public static final int BG_BASE = 0x1A1A2E;
        /** Standard panel: 80% opacity dark blue */
        public static final int BG_STANDARD = (Alpha.STANDARD << 24) | BG_BASE;
        /** Light panel: 67% opacity */
        public static final int BG_LIGHT = (Alpha.LIGHT << 24) | BG_BASE;
        /** Heavy panel: 87% opacity */
        public static final int BG_HEAVY = (Alpha.HEAVY << 24) | BG_BASE;

        /** Header background (slightly lighter) */
        public static final int BG_HEADER = 0xFF1A1A30;

        /** Get panel background with custom alpha (0-255) */
        public static int withAlpha(int alpha) {
            return (alpha << 24) | BG_BASE;
        }

        private Panel() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PANEL BORDERS (themed by context)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Border {
        /** Default accent (electric blue) */
        public static final int ACCENT = 0xFF3D5AFE;
        /** Info panels (cyan) */
        public static final int INFO = 0xFF00FFFF;
        /** Success/positive (green) */
        public static final int SUCCESS = 0xFF4CAF50;
        /** Warning (orange) */
        public static final int WARNING = 0xFFFFAA00;
        /** Error/danger (red) */
        public static final int ERROR = 0xFFFF4444;
        /** Gold (economy) */
        public static final int GOLD = 0xFFFFD700;
        /** Endurance theme (orange) */
        public static final int ENDURANCE = 0xFFFF5722;
        /** Muted/dim */
        public static final int MUTED = 0xFF555555;

        /** Create glow variant of border (25% alpha) */
        public static int glow(int borderColor) {
            return (Alpha.GLOW << 24) | (borderColor & 0x00FFFFFF);
        }

        /** Create divider variant of border (26% alpha) */
        public static int divider(int borderColor) {
            return (Alpha.DIVIDER << 24) | (borderColor & 0x00FFFFFF);
        }

        private Border() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXT COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Text {
        /** Primary text (white) */
        public static final int PRIMARY = 0xFFFFFFFF;
        /** Light text (off-white) - for subtle primary text */
        public static final int LIGHT = 0xFFE6E6E6;
        /** Title text (cyan accent) */
        public static final int TITLE = 0xFF00FFFF;
        /** Muted/secondary text */
        public static final int MUTED = 0xFFAAAAAA;
        /** Hint text (very dim) */
        public static final int HINT = 0xFF888888;

        /** Value (green) - positive numbers, stats */
        public static final int VALUE = 0xFF00FF00;
        /** Value bright (brighter green) */
        public static final int VALUE_BRIGHT = 0xFF55FF55;

        /** Warning (yellow) */
        public static final int WARNING = 0xFFFFFF00;
        /** Warning orange */
        public static final int WARNING_ORANGE = 0xFFFFAA00;
        /** Danger (red) */
        public static final int DANGER = 0xFFFF4444;
        /** Danger bright */
        public static final int DANGER_BRIGHT = 0xFFFF5555;

        /** Cyan accent */
        public static final int CYAN = 0xFF55FFFF;
        /** Purple accent */
        public static final int PURPLE = 0xFFAA55FF;
        /** Gold accent */
        public static final int GOLD = 0xFFFFD700;

        private Text() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROGRESS BARS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Progress {
        /** Bar background */
        public static final int BG = 0xFF333333;
        /** Bar background alt */
        public static final int BG_ALT = 0xFF333344;

        /** Fill default (teal/green) */
        public static final int FILL = 0xFF00DD88;
        /** Fill green (high/good) */
        public static final int FILL_GREEN = 0xFF44AA44;
        /** Fill yellow (medium) */
        public static final int FILL_YELLOW = 0xFFAAAA44;
        /** Fill red (low/danger) */
        public static final int FILL_RED = 0xFFAA4444;
        /** Fill orange (warning) */
        public static final int FILL_ORANGE = 0xFFFF5722;
        /** Fill cyan (cooldown) */
        public static final int FILL_CYAN = 0xFF4488FF;

        /** Get fill color by ratio (0.0-1.0) */
        public static int byRatio(float ratio) {
            if (ratio > 0.6f) return FILL_GREEN;
            if (ratio > 0.3f) return FILL_YELLOW;
            return FILL_RED;
        }

        private Progress() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEMANTIC STATUS COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Status {
        /** Recording active (red dot) */
        public static final int RECORDING = 0xFFFF4444;
        /** Paused (orange) */
        public static final int PAUSED = 0xFFFFAA00;
        /** Active (green) */
        public static final int ACTIVE = 0xFF4CAF50;
        /** Inactive (gray) */
        public static final int INACTIVE = 0xFF888888;

        private Status() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // THEME CONTEXTS (for specialized overlays)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Endurance {
        /** Primary theme color (orange) */
        public static final int PRIMARY = 0xFFFF5722;
        /** Light variant */
        public static final int LIGHT = 0xFFFFAB91;
        /** Background (dark with orange tint) */
        public static final int BG = 0xBB1A1A2E;
        /** Background greenish (for survive objectives) */
        public static final int BG_SURVIVE = 0xBB1A2E1A;
        /** Boss alert */
        public static final int BOSS_ALERT = 0xFFFF4444;

        private Endurance() {}
    }

    public static final class Economy {
        /** Primary theme color (gold) */
        public static final int PRIMARY = 0xFFFFD700;
        /** Background */
        public static final int BG = 0xE0101020;

        private Economy() {}
    }

    public static final class Combat {
        /** Electric blue (impact) */
        public static final int IMPACT = 0xFF3D5AFE;
        /** Cyan (secondary) */
        public static final int SECONDARY = 0xFF00E5FF;
        /** Light blue (glow) */
        public static final int GLOW = 0xFF82B1FF;

        private Combat() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPACT VFX COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Impact {
        /** Core primary (electric blue) - same as Combat.IMPACT */
        public static final int CORE_PRIMARY = Combat.IMPACT;
        /** Core secondary (cyan) - same as Combat.SECONDARY */
        public static final int CORE_SECONDARY = Combat.SECONDARY;
        /** Core glow (light blue) - same as Combat.GLOW */
        public static final int CORE_GLOW = Combat.GLOW;
        /** Slash effect color */
        public static final int SLASH = Combat.IMPACT;
        /** Line effect color */
        public static final int LINE = Combat.SECONDARY;

        /** Highlight shadow (dark red) for damage display */
        public static final int HIGHLIGHT_SHADOW = 0xFF550000;
        /** Calculated shadow (dark green) for calculated values */
        public static final int CALCULATED_SHADOW = 0xFF005500;

        private Impact() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELP OVERLAY COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Help {
        /** Title color (light green) */
        public static final int TITLE = 0xFF81C784;
        /** Category highlight (light blue) */
        public static final int CATEGORY = 0xFF64B5F6;
        /** Key background */
        public static final int KEY_BG = 0xFF333333;
        /** Hint text */
        public static final int HINT = 0xFF555555;

        private Help() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BODY PART COLORS (Impact HUD)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class BodyPart {
        public static final int HEAD = 0xFF00FFFF;  // Cyan
        public static final int BODY = 0xFF00FF00;  // Green
        public static final int ARMS = 0xFFFFFF00;  // Yellow
        public static final int LEGS = 0xFFFF0000;  // Red

        private BodyPart() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AFFIX COLORS (Endurance enemies)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Affix {
        public static final int SWIFT = 0xFF64B5F6;        // Light blue
        public static final int EMPOWERED = 0xFFFF5252;    // Red
        public static final int FORTIFIED = 0xFF4CAF50;    // Green
        public static final int ARMORED = 0xFF9E9E9E;      // Gray
        public static final int BLAZING = 0xFFFF9800;      // Orange
        public static final int PHANTOM = 0xFF7C4DFF;      // Purple
        public static final int REGENERATING = 0xFFE91E63; // Pink
        public static final int HORDE = 0xFFFFEB3B;        // Yellow

        private Affix() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MOMENTUM COLORS (Endurance)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Momentum {
        public static final int NORMAL = 0xFF66FF66;     // Green
        public static final int HEATED = 0xFFFFAA00;     // Orange/Gold
        public static final int OVERDRIVE = 0xFFFF00FF;  // Magenta
        public static final int STAGNANT = 0xFFFF4444;   // Red

        private Momentum() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTRACT HUD COLORS (Blood Contracts)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Contract {
        /** Header text (blood orange) */
        public static final int HEADER = 0xFFFF8800;
        /** High multiplier (danger red) - >=2.0x */
        public static final int MULTIPLIER_HIGH = 0xFFFF4444;
        /** Medium multiplier (warning orange) - >=1.5x */
        public static final int MULTIPLIER_MED = 0xFFFFAA00;
        /** Low/safe multiplier (light green) - <1.5x */
        public static final int MULTIPLIER_LOW = 0xFFAAFFAA;
        /** Violated contract text */
        public static final int VIOLATED = 0xFF888888;
        /** Violated strikethrough line */
        public static final int STRIKETHROUGH = 0xFFFF4444;
        /** Violated multiplier text */
        public static final int VIOLATED_MUTED = 0xFF666666;
        /** Separator line */
        public static final int SEPARATOR = 0x44FFFFFF;
        /** Normal multiplier text */
        public static final int MULTIPLIER_TEXT = 0xFFFFFFFF;

        private Contract() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STAMINA BAR COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Stamina {
        /** Bar background (very dark) */
        public static final int BG = 0xFF1A1A1A;
        /** Bar border */
        public static final int BORDER = 0xFF3A3A3A;
        /** Full stamina (green) */
        public static final int FULL = 0xFF4CAF50;
        /** Medium stamina (yellow) */
        public static final int MEDIUM = 0xFFFFEB3B;
        /** Low stamina (orange) */
        public static final int LOW = 0xFFFF5722;
        /** Exhausted (red) */
        public static final int EXHAUSTED = 0xFFF44336;
        /** Regenerating pulse (light green) */
        public static final int REGEN = 0xFF81C784;

        private Stamina() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUG VISUALIZER COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Debug {
        // --- Hitbox colors ---
        /** Hitbox outline (transparent yellow) */
        public static final int HITBOX = 0x80FFFF00;
        /** Aggro range sphere (transparent cyan) */
        public static final int AGGRO_SPHERE = 0x8000FFFF;

        // --- Label colors ---
        /** Label text (white) */
        public static final int LABEL = 0xFFFFFFFF;
        /** Title text (orange/gold) */
        public static final int TITLE = 0xFFFFAA00;

        // --- Aggro range visualizer ---
        /** Hostile mob follow range (translucent red) */
        public static final int RANGE_HOSTILE = 0x40FF5555;
        /** Neutral mob follow range (translucent yellow) */
        public static final int RANGE_NEUTRAL = 0x40FFFF55;
        /** Attack range (deeper red, more opaque) */
        public static final int RANGE_ATTACK = 0x60FF0000;
        /** Passive mob range (translucent green) */
        public static final int RANGE_PASSIVE = 0x4055FF55;

        // --- Safe spot visualizer ---
        /** Safe spot label (red) */
        public static final int SAFE_SPOT_LABEL = 0xFFFF4444;

        // --- Light level overlay ---
        /** Safe light level (green) */
        public static final int LIGHT_SAFE = 0xFF00FF00;
        /** Warning light level (yellow) */
        public static final int LIGHT_WARN = 0xFFFFFF00;
        /** Danger light level (red) */
        public static final int LIGHT_DANGER = 0xFFFF0000;

        // --- Spawnability overlay ---
        /** Can spawn (red) */
        public static final int SPAWN_YES = 0xFFFF0000;
        /** Conditional spawn (orange) */
        public static final int SPAWN_CONDITIONAL = 0xFFFF8800;
        /** Cannot spawn (green) */
        public static final int SPAWN_NO = 0xFF00FF00;

        // --- Entity info overlay ---
        /** Entity health good (pastel green) */
        public static final int ENTITY_HEALTH_GOOD = 0x55FF55;
        /** Entity health medium (pastel yellow) */
        public static final int ENTITY_HEALTH_MED = 0xFFFF55;
        /** Entity health low (pastel red) */
        public static final int ENTITY_HEALTH_LOW = 0xFF5555;
        /** Entity stat text (gray) */
        public static final int ENTITY_STAT = 0xAAAAAA;
        /** Hostile entity name (pastel red) - same as HEALTH_LOW */
        public static final int ENTITY_HOSTILE = ENTITY_HEALTH_LOW;
        /** Passive entity name (pastel green) - same as HEALTH_GOOD */
        public static final int ENTITY_PASSIVE = ENTITY_HEALTH_GOOD;
        /** Neutral entity name (pastel yellow) - same as HEALTH_MED */
        public static final int ENTITY_NEUTRAL = ENTITY_HEALTH_MED;

        // --- Vertical zones overlay ---
        /** Floor zone label (green) */
        public static final int ZONE_FLOOR = 0xFF00CC00;
        /** Mid zone label (yellow) */
        public static final int ZONE_MID = 0xFFCCCC00;
        /** High zone label (red) */
        public static final int ZONE_HIGH = 0xFFCC0000;

        // --- Pathfinding debugger ---
        /** Start beacon/label (cyan) */
        public static final int PATH_START = 0xFF00FFFF;
        /** Destination reachable (gold) */
        public static final int PATH_DEST_OK = 0xFFFFD700;
        /** Destination unreachable (red) */
        public static final int PATH_DEST_FAIL = 0xFFFF4444;
        /** Distance info label (gray) */
        public static final int PATH_INFO = 0xFFAAAAAA;

        // --- Room bounds visualizer palette ---
        /** Room color 0: red */
        public static final int ROOM_RED = 0xFFFF0000;
        /** Room color 1: green */
        public static final int ROOM_GREEN = 0xFF00FF00;
        /** Room color 2: blue */
        public static final int ROOM_BLUE = 0xFF0000FF;
        /** Room color 3: yellow */
        public static final int ROOM_YELLOW = 0xFFFFFF00;
        /** Room color 4: magenta */
        public static final int ROOM_MAGENTA = 0xFFFF00FF;
        /** Room color 5: cyan */
        public static final int ROOM_CYAN = 0xFF00FFFF;
        /** Room color 6: orange */
        public static final int ROOM_ORANGE = 0xFFFF8000;
        /** Room color 7: purple */
        public static final int ROOM_PURPLE = 0xFF8000FF;
        /** Room gap warning label (red) */
        public static final int ROOM_GAP = 0xFFFF0000;

        /** Room palette array for indexed access */
        private static final int[] ROOM_PALETTE = {
            ROOM_RED, ROOM_GREEN, ROOM_BLUE, ROOM_YELLOW,
            ROOM_MAGENTA, ROOM_CYAN, ROOM_ORANGE, ROOM_PURPLE
        };

        public static int[] roomPalette() {
            return ROOM_PALETTE.clone();
        }

        // --- Line of sight visualizer ---
        /** Target visible (green) */
        public static final int LOS_VISIBLE = 0xFF00FF00;
        /** Target out of FOV (yellow) */
        public static final int LOS_OUT_OF_FOV = 0xFFFFFF00;
        /** Line of sight blocked (red) */
        public static final int LOS_BLOCKED = 0xFFFF4444;

        // --- Attack reach circle ---
        /** Attack reach circle (yellow) */
        public static final int ATTACK_REACH = 0xFFFFFF00;

        // --- Zone environment colors (ZoneDebugRenderer) ---
        /** Default zone (white) */
        public static final int ZONE_ENV_DEFAULT = 0xFFFFFF;
        /** Nether biome zone (red) */
        public static final int ZONE_ENV_NETHER = 0xFF4444;
        /** End biome zone (purple) */
        public static final int ZONE_ENV_END = 0xAA44FF;
        /** Ice/snow biome zone (cyan) */
        public static final int ZONE_ENV_ICE = 0x44FFFF;
        /** Desert biome zone (yellow) */
        public static final int ZONE_ENV_DESERT = 0xFFFF44;
        /** Ocean biome zone (blue) */
        public static final int ZONE_ENV_OCEAN = 0x4444FF;
        /** Forest biome zone (green) */
        public static final int ZONE_ENV_FOREST = 0x44FF44;
        /** Cave biome zone (brown) */
        public static final int ZONE_ENV_CAVE = 0x884422;
        /** Night time zone (dark purple) */
        public static final int ZONE_ENV_NIGHT = 0x6644AA;
        /** Day time zone (bright yellow) */
        public static final int ZONE_ENV_DAY = 0xFFDD44;
        /** Dark lighting zone (dark gray) */
        public static final int ZONE_ENV_DARK = 0x444444;
        /** Bright lighting zone (light yellow) */
        public static final int ZONE_ENV_BRIGHT = 0xFFFFAA;
        /** Fallback zone (light gray) */
        public static final int ZONE_ENV_FALLBACK = 0xCCCCCC;

        private Debug() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VFX FLASH COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Flash {
        /** Headshot flash (red) - RGB only, apply alpha dynamically */
        public static final int HEADSHOT = 0xFF0000;
        /** Critical hit flash (orange) */
        public static final int CRITICAL = 0xFF8800;
        /** Damage taken flash (red) */
        public static final int DAMAGE = 0xFF4444;
        /** Heal flash (green) */
        public static final int HEAL = 0x44FF44;
        /** Shield flash (cyan) */
        public static final int SHIELD = 0x44FFFF;

        private Flash() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OVERLAY DIMENSIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Standard dimensions for HUD overlays.
     * Use these for consistent spacing across all overlays.
     */
    public static final class Dimension {
        /** Compact line height (dense HUDs like ImpactHud) */
        public static final int LINE_HEIGHT_COMPACT = 10;
        /** Standard line height (most overlays) */
        public static final int LINE_HEIGHT = 11;
        /** Readable line height (tutorials, help) */
        public static final int LINE_HEIGHT_READABLE = 14;

        /** Tight panel padding (compact overlays) */
        public static final int PADDING_TIGHT = 6;
        /** Standard panel padding (most overlays) */
        public static final int PADDING = 8;
        /** Comfortable panel padding (tutorials, help) */
        public static final int PADDING_COMFORTABLE = 12;
        /** Spacious panel padding (modals, full screens) */
        public static final int PADDING_SPACIOUS = 16;

        /** Progress bar height */
        public static final int PROGRESS_BAR_HEIGHT = 8;
        /** Small progress bar */
        public static final int PROGRESS_BAR_HEIGHT_SM = 4;

        private Dimension() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Common utility colors for effects, highlights, and overlays.
     */
    public static final class Utility {
        /** Pure white for pulse/glow effects */
        public static final int WHITE = 0xFFFFFFFF;
        /** Pure black */
        public static final int BLACK = 0xFF000000;
        /** Shadow/highlight background (25% black) */
        public static final int SHADOW = 0x40000000;
        /** Light shadow (15% black) */
        public static final int SHADOW_LIGHT = 0x26000000;
        /** Heavy shadow (50% black) */
        public static final int SHADOW_HEAVY = 0x80000000;

        private Utility() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Apply alpha to an RGB color.
     * @param color RGB color (0xRRGGBB or 0xAARRGGBB - alpha ignored)
     * @param alpha alpha value (0-255)
     * @return ARGB color
     */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Get RGB portion of a color (strip alpha).
     * @param color ARGB color
     * @return RGB color
     */
    public static int stripAlpha(int color) {
        return color & 0x00FFFFFF;
    }

    /**
     * Interpolate between two colors.
     * @see DesignTokens#lerp(int, int, float)
     */
    public static int lerp(int color1, int color2, float t) {
        return DesignTokens.lerp(color1, color2, t);
    }
}
