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
        public static final int HEAVY = DesignTokens.Overlay.Alpha.HEAVY;
        /** Semi-transparent (80%) - standard panels */
        public static final int STANDARD = DesignTokens.Overlay.Alpha.STANDARD;
        /** Light (67%) - less intrusive overlays */
        public static final int LIGHT = DesignTokens.Overlay.Alpha.LIGHT;
        /** Subtle (50%) - minimal overlays */
        public static final int SUBTLE = DesignTokens.Overlay.Alpha.SUBTLE;
        /** Ghost (33%) - background hints */
        public static final int GHOST = DesignTokens.Overlay.Alpha.GHOST;
        /** Divider (26%) - separators */
        public static final int DIVIDER = DesignTokens.Overlay.Alpha.DIVIDER;
        /** Glow (25%) - effects */
        public static final int GLOW = DesignTokens.Overlay.Alpha.GLOW;

        private Alpha() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PANEL BACKGROUNDS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Panel {
        /** Base blue-tinted dark background (RGB only, apply alpha) */
        public static final int BG_BASE = DesignTokens.Overlay.Panel.BG_BASE;
        /** Standard panel: 80% opacity dark blue */
        public static final int BG_STANDARD = DesignTokens.Overlay.Panel.BG_STANDARD;
        /** Light panel: 67% opacity */
        public static final int BG_LIGHT = DesignTokens.Overlay.Panel.BG_LIGHT;
        /** Heavy panel: 87% opacity */
        public static final int BG_HEAVY = DesignTokens.Overlay.Panel.BG_HEAVY;

        /** Header background (slightly lighter) */
        public static final int BG_HEADER = DesignTokens.Overlay.Panel.BG_HEADER;

        /** Get panel background with custom alpha (0-255) */
        public static int withAlpha(int alpha) {
            return DesignTokens.Overlay.Panel.withAlpha(alpha);
        }

        private Panel() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PANEL BORDERS (themed by context)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Border {
        /** Default accent (electric blue) */
        public static final int ACCENT = DesignTokens.Overlay.Border.ACCENT;
        /** Info panels (cyan) */
        public static final int INFO = DesignTokens.Overlay.Border.INFO;
        /** Success/positive (green) */
        public static final int SUCCESS = DesignTokens.Overlay.Border.SUCCESS;
        /** Warning (orange) */
        public static final int WARNING = DesignTokens.Overlay.Border.WARNING;
        /** Error/danger (red) */
        public static final int ERROR = DesignTokens.Overlay.Border.ERROR;
        /** Gold (economy) */
        public static final int GOLD = DesignTokens.Overlay.Border.GOLD;
        /** Endurance theme (orange) */
        public static final int ENDURANCE = DesignTokens.Overlay.Border.ENDURANCE;
        /** Muted/dim */
        public static final int MUTED = DesignTokens.Overlay.Border.MUTED;

        /** Create glow variant of border (25% alpha) */
        public static int glow(int borderColor) {
            return DesignTokens.Overlay.Border.glow(borderColor);
        }

        /** Create divider variant of border (26% alpha) */
        public static int divider(int borderColor) {
            return DesignTokens.Overlay.Border.divider(borderColor);
        }

        private Border() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXT COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Text {
        /** Primary text (white) */
        public static final int PRIMARY = DesignTokens.Overlay.Text.PRIMARY;
        /** Light text (off-white) - for subtle primary text */
        public static final int LIGHT = DesignTokens.Overlay.Text.LIGHT;
        /** Title text (cyan accent) */
        public static final int TITLE = DesignTokens.Overlay.Text.TITLE;
        /** Muted/secondary text */
        public static final int MUTED = DesignTokens.Overlay.Text.MUTED;
        /** Hint text (very dim) */
        public static final int HINT = DesignTokens.Overlay.Text.HINT;

        /** Value (green) - positive numbers, stats */
        public static final int VALUE = DesignTokens.Overlay.Text.VALUE;
        /** Value bright (brighter green) */
        public static final int VALUE_BRIGHT = DesignTokens.Overlay.Text.VALUE_BRIGHT;

        /** Warning (yellow) */
        public static final int WARNING = DesignTokens.Overlay.Text.WARNING;
        /** Warning orange */
        public static final int WARNING_ORANGE = DesignTokens.Overlay.Text.WARNING_ORANGE;
        /** Danger (red) */
        public static final int DANGER = DesignTokens.Overlay.Text.DANGER;
        /** Danger bright */
        public static final int DANGER_BRIGHT = DesignTokens.Overlay.Text.DANGER_BRIGHT;

        /** Cyan accent */
        public static final int CYAN = DesignTokens.Overlay.Text.CYAN;
        /** Purple accent */
        public static final int PURPLE = DesignTokens.Overlay.Text.PURPLE;
        /** Gold accent */
        public static final int GOLD = DesignTokens.Overlay.Text.GOLD;

        private Text() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NEUTRAL GRAYSCALE (overlay surfaces/text)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Neutral {
        public static final int N950 = DesignTokens.Overlay.Neutral.N950;
        public static final int N920 = DesignTokens.Overlay.Neutral.N920;
        public static final int N900 = DesignTokens.Overlay.Neutral.N900;
        public static final int N880 = DesignTokens.Overlay.Neutral.N880;
        public static final int N860 = DesignTokens.Overlay.Neutral.N860;
        public static final int N840 = DesignTokens.Overlay.Neutral.N840;
        public static final int N820 = DesignTokens.Overlay.Neutral.N820;
        public static final int N800 = DesignTokens.Overlay.Neutral.N800;
        public static final int N780 = DesignTokens.Overlay.Neutral.N780;
        public static final int N760 = DesignTokens.Overlay.Neutral.N760;
        public static final int N740 = DesignTokens.Overlay.Neutral.N740;
        public static final int N700 = DesignTokens.Overlay.Neutral.N700;
        public static final int N650 = DesignTokens.Overlay.Neutral.N650;
        public static final int N600 = DesignTokens.Overlay.Neutral.N600;
        public static final int N550 = DesignTokens.Overlay.Neutral.N550;
        public static final int N500 = DesignTokens.Overlay.Neutral.N500;
        public static final int N450 = DesignTokens.Overlay.Neutral.N450;
        public static final int N400 = DesignTokens.Overlay.Neutral.N400;

        private Neutral() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROGRESS BARS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Progress {
        /** Bar background */
        public static final int BG = DesignTokens.Overlay.Progress.BG;
        /** Bar background alt */
        public static final int BG_ALT = DesignTokens.Overlay.Progress.BG_ALT;

        /** Fill default (teal/green) */
        public static final int FILL = DesignTokens.Overlay.Progress.FILL;
        /** Fill green (high/good) */
        public static final int FILL_GREEN = DesignTokens.Overlay.Progress.FILL_GREEN;
        /** Fill yellow (medium) */
        public static final int FILL_YELLOW = DesignTokens.Overlay.Progress.FILL_YELLOW;
        /** Fill red (low/danger) */
        public static final int FILL_RED = DesignTokens.Overlay.Progress.FILL_RED;
        /** Fill orange (warning) */
        public static final int FILL_ORANGE = DesignTokens.Overlay.Progress.FILL_ORANGE;
        /** Fill cyan (cooldown) */
        public static final int FILL_CYAN = DesignTokens.Overlay.Progress.FILL_CYAN;

        /** Get fill color by ratio (0.0-1.0) */
        public static int byRatio(float ratio) {
            return DesignTokens.Overlay.Progress.byRatio(ratio);
        }

        private Progress() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEMANTIC STATUS COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Status {
        /** Recording active (red dot) */
        public static final int RECORDING = DesignTokens.Overlay.Status.RECORDING;
        /** Paused (orange) */
        public static final int PAUSED = DesignTokens.Overlay.Status.PAUSED;
        /** Active (green) */
        public static final int ACTIVE = DesignTokens.Overlay.Status.ACTIVE;
        /** Inactive (gray) */
        public static final int INACTIVE = DesignTokens.Overlay.Status.INACTIVE;

        private Status() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // THEME CONTEXTS (for specialized overlays)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Endurance {
        /** Primary theme color (orange) */
        public static final int PRIMARY = DesignTokens.Overlay.Endurance.PRIMARY;
        /** Light variant */
        public static final int LIGHT = DesignTokens.Overlay.Endurance.LIGHT;
        /** Background (dark with orange tint) */
        public static final int BG = DesignTokens.Overlay.Endurance.BG;
        /** Background greenish (for survive objectives) */
        public static final int BG_SURVIVE = DesignTokens.Overlay.Endurance.BG_SURVIVE;
        /** Boss alert */
        public static final int BOSS_ALERT = DesignTokens.Overlay.Endurance.BOSS_ALERT;

        private Endurance() {}
    }

    public static final class Economy {
        /** Primary theme color (gold) */
        public static final int PRIMARY = DesignTokens.Overlay.Economy.PRIMARY;
        /** Background */
        public static final int BG = DesignTokens.Overlay.Economy.BG;

        private Economy() {}
    }

    public static final class Combat {
        /** Electric blue (impact) */
        public static final int IMPACT = DesignTokens.Overlay.Combat.IMPACT;
        /** Cyan (secondary) */
        public static final int SECONDARY = DesignTokens.Overlay.Combat.SECONDARY;
        /** Light blue (glow) */
        public static final int GLOW = DesignTokens.Overlay.Combat.GLOW;

        private Combat() {}
    }

    public static final class CombatRecap {
        public static final int BG = DesignTokens.Overlay.CombatRecap.BG;
        public static final int PANEL_BG = DesignTokens.Overlay.CombatRecap.PANEL_BG;
        public static final int ACCENT = DesignTokens.Overlay.CombatRecap.ACCENT;
        public static final int TEXT_PRIMARY = DesignTokens.Overlay.CombatRecap.TEXT_PRIMARY;
        public static final int TEXT_SECONDARY = DesignTokens.Overlay.CombatRecap.TEXT_SECONDARY;
        public static final int TEXT_HIGHLIGHT = DesignTokens.Overlay.CombatRecap.TEXT_HIGHLIGHT;

        public static final int BAR_DAMAGE = DesignTokens.Overlay.CombatRecap.BAR_DAMAGE;
        public static final int BAR_CRIT = DesignTokens.Overlay.CombatRecap.BAR_CRIT;
        public static final int BAR_HEADSHOT = DesignTokens.Overlay.CombatRecap.BAR_HEADSHOT;
        public static final int BAR_DPS = DesignTokens.Overlay.CombatRecap.BAR_DPS;

        public static final int DIVIDER = DesignTokens.Overlay.CombatRecap.DIVIDER;
        public static final int BAR_BG = DesignTokens.Overlay.CombatRecap.BAR_BG;
        public static final int GRAPH_BG = DesignTokens.Overlay.CombatRecap.GRAPH_BG;

        private CombatRecap() {}
    }

    public static final class Impact3D {
        public static final int DPS = DesignTokens.Overlay.Impact3D.DPS;

        private Impact3D() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EPIC FIGHT INTEGRATION COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class EpicFight {
        /** Header/title text (deep orange) */
        public static final int HEADER = DesignTokens.Overlay.EpicFight.HEADER;
        /** Guard indicator (blue) */
        public static final int GUARD = DesignTokens.Overlay.EpicFight.GUARD;
        /** Guard flash (white) */
        public static final int GUARD_FLASH = DesignTokens.Overlay.EpicFight.GUARD_FLASH;
        /** Parry indicator (gold) */
        public static final int PARRY = DesignTokens.Overlay.EpicFight.PARRY;
        /** Parry flash (white) */
        public static final int PARRY_FLASH = DesignTokens.Overlay.EpicFight.PARRY_FLASH;
        /** Perfect parry (magenta/rainbow start) */
        public static final int PERFECT_PARRY = DesignTokens.Overlay.EpicFight.PERFECT_PARRY;
        /** Perfect parry secondary (cyan for rainbow) */
        public static final int PERFECT_PARRY_SECONDARY = DesignTokens.Overlay.EpicFight.PERFECT_PARRY_SECONDARY;
        /** Skill name text (gray) */
        public static final int SKILL_NAME = DesignTokens.Overlay.EpicFight.SKILL_NAME;
        /** Battle mode active indicator (orange glow) */
        public static final int BATTLE_MODE = DesignTokens.Overlay.EpicFight.BATTLE_MODE;
        /** Stamina bar background */
        public static final int STAMINA_BG = DesignTokens.Overlay.EpicFight.STAMINA_BG;
        /** Stamina full (yellow-green) */
        public static final int STAMINA_FULL = DesignTokens.Overlay.EpicFight.STAMINA_FULL;
        /** Stamina medium (yellow) */
        public static final int STAMINA_MEDIUM = DesignTokens.Overlay.EpicFight.STAMINA_MEDIUM;
        /** Stamina low (orange) */
        public static final int STAMINA_LOW = DesignTokens.Overlay.EpicFight.STAMINA_LOW;
        /** Stamina exhausted (red) */
        public static final int STAMINA_EXHAUSTED = DesignTokens.Overlay.EpicFight.STAMINA_EXHAUSTED;

        /**
         * Get stamina color based on percentage (0.0-1.0).
         */
        public static int getStaminaColor(float percent) {
            return DesignTokens.Overlay.EpicFight.getStaminaColor(percent);
        }

        private EpicFight() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPACT VFX COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Impact {
        /** Core primary (electric blue) - same as Combat.IMPACT */
        public static final int CORE_PRIMARY = DesignTokens.Overlay.Impact.CORE_PRIMARY;
        /** Core secondary (cyan) - same as Combat.SECONDARY */
        public static final int CORE_SECONDARY = DesignTokens.Overlay.Impact.CORE_SECONDARY;
        /** Core glow (light blue) - same as Combat.GLOW */
        public static final int CORE_GLOW = DesignTokens.Overlay.Impact.CORE_GLOW;
        /** Slash effect color */
        public static final int SLASH = DesignTokens.Overlay.Impact.SLASH;
        /** Line effect color */
        public static final int LINE = DesignTokens.Overlay.Impact.LINE;

        /** Highlight shadow (dark red) for damage display */
        public static final int HIGHLIGHT_SHADOW = DesignTokens.Overlay.Impact.HIGHLIGHT_SHADOW;
        /** Calculated shadow (dark green) for calculated values */
        public static final int CALCULATED_SHADOW = DesignTokens.Overlay.Impact.CALCULATED_SHADOW;

        private Impact() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELP OVERLAY COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Help {
        /** Title color (light green) */
        public static final int TITLE = DesignTokens.Overlay.Help.TITLE;
        /** Category highlight (light blue) */
        public static final int CATEGORY = DesignTokens.Overlay.Help.CATEGORY;
        /** Key background */
        public static final int KEY_BG = DesignTokens.Overlay.Help.KEY_BG;
        /** Hint text */
        public static final int HINT = DesignTokens.Overlay.Help.HINT;

        private Help() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QUEST HUD COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Quest {
        public static final int PANEL_BG = DesignTokens.Overlay.Quest.PANEL_BG;
        public static final int BORDER = DesignTokens.Overlay.Quest.BORDER;
        public static final int BORDER_GLOW = DesignTokens.Overlay.Quest.BORDER_GLOW;

        public static final int TITLE = DesignTokens.Overlay.Quest.TITLE;
        public static final int TEXT = DesignTokens.Overlay.Quest.TEXT;
        public static final int TASK = DesignTokens.Overlay.Quest.TASK;
        public static final int NOTE = DesignTokens.Overlay.Quest.NOTE;
        public static final int PROGRESS = DesignTokens.Overlay.Quest.PROGRESS;
        public static final int COMPLETED = DesignTokens.Overlay.Quest.COMPLETED;
        public static final int HINT = DesignTokens.Overlay.Quest.HINT;

        private Quest() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ATTRIBUTE HUD COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Attribute {
        public static final int PANEL_BG = DesignTokens.Overlay.Attribute.PANEL_BG;
        public static final int BORDER = DesignTokens.Overlay.Attribute.BORDER;
        public static final int BORDER_GLOW = DesignTokens.Overlay.Attribute.BORDER_GLOW;
        public static final int TITLE = DesignTokens.Overlay.Attribute.TITLE;
        public static final int TEXT = DesignTokens.Overlay.Attribute.TEXT;
        public static final int VALUE_GREEN = DesignTokens.Overlay.Attribute.VALUE_GREEN;
        public static final int VALUE_YELLOW = DesignTokens.Overlay.Attribute.VALUE_YELLOW;
        public static final int VALUE_RED = DesignTokens.Overlay.Attribute.VALUE_RED;
        public static final int VALUE_GRAY = DesignTokens.Overlay.Attribute.VALUE_GRAY;
        public static final int VALUE_ORANGE = DesignTokens.Overlay.Attribute.VALUE_ORANGE;
        public static final int SCALE = DesignTokens.Overlay.Attribute.SCALE;
        public static final int EMPTY_LOG = DesignTokens.Overlay.Attribute.EMPTY_LOG;

        private Attribute() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BODY PART COLORS (Impact HUD)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class BodyPart {
        public static final int HEAD = DesignTokens.Overlay.BodyPart.HEAD;
        public static final int BODY = DesignTokens.Overlay.BodyPart.BODY;
        public static final int ARMS = DesignTokens.Overlay.BodyPart.ARMS;
        public static final int LEGS = DesignTokens.Overlay.BodyPart.LEGS;

        private BodyPart() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AFFIX COLORS (Endurance enemies)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Affix {
        public static final int SWIFT = DesignTokens.Overlay.Affix.SWIFT;
        public static final int EMPOWERED = DesignTokens.Overlay.Affix.EMPOWERED;
        public static final int FORTIFIED = DesignTokens.Overlay.Affix.FORTIFIED;
        public static final int ARMORED = DesignTokens.Overlay.Affix.ARMORED;
        public static final int BLAZING = DesignTokens.Overlay.Affix.BLAZING;
        public static final int PHANTOM = DesignTokens.Overlay.Affix.PHANTOM;
        public static final int REGENERATING = DesignTokens.Overlay.Affix.REGENERATING;
        public static final int HORDE = DesignTokens.Overlay.Affix.HORDE;

        private Affix() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MOMENTUM COLORS (Endurance)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Momentum {
        public static final int NORMAL = DesignTokens.Overlay.Momentum.NORMAL;
        public static final int HEATED = DesignTokens.Overlay.Momentum.HEATED;
        public static final int OVERDRIVE = DesignTokens.Overlay.Momentum.OVERDRIVE;
        public static final int STAGNANT = DesignTokens.Overlay.Momentum.STAGNANT;

        private Momentum() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTRACT HUD COLORS (Blood Contracts)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Contract {
        /** Header text (blood orange) */
        public static final int HEADER = DesignTokens.Overlay.Contract.HEADER;
        /** High multiplier (danger red) - >=2.0x */
        public static final int MULTIPLIER_HIGH = DesignTokens.Overlay.Contract.MULTIPLIER_HIGH;
        /** Medium multiplier (warning orange) - >=1.5x */
        public static final int MULTIPLIER_MED = DesignTokens.Overlay.Contract.MULTIPLIER_MED;
        /** Low/safe multiplier (light green) - <1.5x */
        public static final int MULTIPLIER_LOW = DesignTokens.Overlay.Contract.MULTIPLIER_LOW;
        /** Violated contract text */
        public static final int VIOLATED = DesignTokens.Overlay.Contract.VIOLATED;
        /** Violated strikethrough line */
        public static final int STRIKETHROUGH = DesignTokens.Overlay.Contract.STRIKETHROUGH;
        /** Violated multiplier text */
        public static final int VIOLATED_MUTED = DesignTokens.Overlay.Contract.VIOLATED_MUTED;
        /** Separator line */
        public static final int SEPARATOR = DesignTokens.Overlay.Contract.SEPARATOR;
        /** Normal multiplier text */
        public static final int MULTIPLIER_TEXT = DesignTokens.Overlay.Contract.MULTIPLIER_TEXT;

        private Contract() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STAMINA BAR COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Stamina {
        /** Bar background (very dark) */
        public static final int BG = DesignTokens.Overlay.Stamina.BG;
        /** Bar border */
        public static final int BORDER = DesignTokens.Overlay.Stamina.BORDER;
        /** Full stamina (green) */
        public static final int FULL = DesignTokens.Overlay.Stamina.FULL;
        /** Medium stamina (yellow) */
        public static final int MEDIUM = DesignTokens.Overlay.Stamina.MEDIUM;
        /** Low stamina (orange) */
        public static final int LOW = DesignTokens.Overlay.Stamina.LOW;
        /** Exhausted (red) */
        public static final int EXHAUSTED = DesignTokens.Overlay.Stamina.EXHAUSTED;
        /** Regenerating pulse (light green) */
        public static final int REGEN = DesignTokens.Overlay.Stamina.REGEN;

        private Stamina() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUG VISUALIZER COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Debug {
        // --- Hitbox colors ---
        /** Hitbox outline (transparent yellow) */
        public static final int HITBOX = DesignTokens.Overlay.Debug.HITBOX;
        /** Aggro range sphere (transparent cyan) */
        public static final int AGGRO_SPHERE = DesignTokens.Overlay.Debug.AGGRO_SPHERE;

        /** Wall transition default (blue) */
        public static final int WALL = DesignTokens.Overlay.Debug.WALL;

        // --- Label colors ---
        /** Label text (white) */
        public static final int LABEL = DesignTokens.Overlay.Debug.LABEL;
        /** Title text (orange/gold) */
        public static final int TITLE = DesignTokens.Overlay.Debug.TITLE;

        // --- Aggro range visualizer ---
        /** Hostile mob follow range (translucent red) */
        public static final int RANGE_HOSTILE = DesignTokens.Overlay.Debug.RANGE_HOSTILE;
        /** Neutral mob follow range (translucent yellow) */
        public static final int RANGE_NEUTRAL = DesignTokens.Overlay.Debug.RANGE_NEUTRAL;
        /** Attack range (deeper red, more opaque) */
        public static final int RANGE_ATTACK = DesignTokens.Overlay.Debug.RANGE_ATTACK;
        /** Passive mob range (translucent green) */
        public static final int RANGE_PASSIVE = DesignTokens.Overlay.Debug.RANGE_PASSIVE;

        // --- Safe spot visualizer ---
        /** Safe spot label (red) */
        public static final int SAFE_SPOT_LABEL = DesignTokens.Overlay.Debug.SAFE_SPOT_LABEL;

        // --- Light level overlay ---
        /** Safe light level (green) */
        public static final int LIGHT_SAFE = DesignTokens.Overlay.Debug.LIGHT_SAFE;
        /** Warning light level (yellow) */
        public static final int LIGHT_WARN = DesignTokens.Overlay.Debug.LIGHT_WARN;
        /** Danger light level (red) */
        public static final int LIGHT_DANGER = DesignTokens.Overlay.Debug.LIGHT_DANGER;

        // --- Spawnability overlay ---
        /** Can spawn (red) */
        public static final int SPAWN_YES = DesignTokens.Overlay.Debug.SPAWN_YES;
        /** Conditional spawn (orange) */
        public static final int SPAWN_CONDITIONAL = DesignTokens.Overlay.Debug.SPAWN_CONDITIONAL;
        /** Cannot spawn (green) */
        public static final int SPAWN_NO = DesignTokens.Overlay.Debug.SPAWN_NO;

        // --- Entity info overlay ---
        /** Entity health good (pastel green) */
        public static final int ENTITY_HEALTH_GOOD = DesignTokens.Overlay.Debug.ENTITY_HEALTH_GOOD;
        /** Entity health medium (pastel yellow) */
        public static final int ENTITY_HEALTH_MED = DesignTokens.Overlay.Debug.ENTITY_HEALTH_MED;
        /** Entity health low (pastel red) */
        public static final int ENTITY_HEALTH_LOW = DesignTokens.Overlay.Debug.ENTITY_HEALTH_LOW;
        /** Entity stat text (gray) */
        public static final int ENTITY_STAT = DesignTokens.Overlay.Debug.ENTITY_STAT;
        /** Attack reach circle (yellow) */
        public static final int ATTACK_REACH = DesignTokens.Overlay.Debug.ATTACK_REACH;
        /** Hostile entity name (pastel red) - same as HEALTH_LOW */
        public static final int ENTITY_HOSTILE = ENTITY_HEALTH_LOW;
        /** Passive entity name (pastel green) - same as HEALTH_GOOD */
        public static final int ENTITY_PASSIVE = ENTITY_HEALTH_GOOD;
        /** Neutral entity name (pastel yellow) - same as HEALTH_MED */
        public static final int ENTITY_NEUTRAL = ENTITY_HEALTH_MED;
        /** Mob stats overlay name (yellow) */
        public static final int ENTITY_NAME = DesignTokens.Overlay.Debug.ENTITY_NAME;
        /** Mob stats overlay HP (red) */
        public static final int ENTITY_HP = ENTITY_HEALTH_LOW;
        /** Mob stats overlay armor (blue) */
        public static final int ENTITY_ARMOR = DesignTokens.Overlay.Debug.ENTITY_ARMOR;
        /** Mob stats overlay damage (light red) */
        public static final int ENTITY_DAMAGE = DesignTokens.Overlay.Debug.ENTITY_DAMAGE;
        /** Mob stats overlay follow range (green) */
        public static final int ENTITY_FOLLOW_RANGE = DesignTokens.Overlay.Debug.ENTITY_FOLLOW_RANGE;
        /** Mob stats overlay reach (modified) */
        public static final int ENTITY_REACH_MODIFIED = DesignTokens.Overlay.Debug.ENTITY_REACH_MODIFIED;
        /** Mob stats overlay reach (vanilla) */
        public static final int ENTITY_REACH_VANILLA = ENTITY_STAT;
        /** Mob stats overlay target (orange) */
        public static final int ENTITY_TARGET = DesignTokens.Overlay.Debug.ENTITY_TARGET;

        // --- Vertical zones overlay ---
        /** Floor zone label (green) */
        public static final int ZONE_FLOOR = DesignTokens.Overlay.Debug.ZONE_FLOOR;
        /** Mid zone label (yellow) */
        public static final int ZONE_MID = DesignTokens.Overlay.Debug.ZONE_MID;
        /** High zone label (red) */
        public static final int ZONE_HIGH = DesignTokens.Overlay.Debug.ZONE_HIGH;

        // --- Pathfinding debugger ---
        /** Start beacon/label (cyan) */
        public static final int PATH_START = DesignTokens.Overlay.Debug.PATH_START;
        /** Destination reachable (gold) */
        public static final int PATH_DEST_OK = DesignTokens.Overlay.Debug.PATH_DEST_OK;
        /** Destination unreachable (red) */
        public static final int PATH_DEST_FAIL = DesignTokens.Overlay.Debug.PATH_DEST_FAIL;
        /** Distance info label (gray) */
        public static final int PATH_INFO = DesignTokens.Overlay.Debug.PATH_INFO;

        // --- Room bounds visualizer palette ---
        /** Room color 0: red */
        public static final int ROOM_RED = DesignTokens.Overlay.Debug.ROOM_RED;
        /** Room color 1: green */
        public static final int ROOM_GREEN = DesignTokens.Overlay.Debug.ROOM_GREEN;
        /** Room color 2: blue */
        public static final int ROOM_BLUE = DesignTokens.Overlay.Debug.ROOM_BLUE;
        /** Room color 3: yellow */
        public static final int ROOM_YELLOW = DesignTokens.Overlay.Debug.ROOM_YELLOW;
        /** Room color 4: magenta */
        public static final int ROOM_MAGENTA = DesignTokens.Overlay.Debug.ROOM_MAGENTA;
        /** Room color 5: cyan */
        public static final int ROOM_CYAN = DesignTokens.Overlay.Debug.ROOM_CYAN;
        /** Room color 6: orange */
        public static final int ROOM_ORANGE = DesignTokens.Overlay.Debug.ROOM_ORANGE;
        /** Room color 7: purple */
        public static final int ROOM_PURPLE = DesignTokens.Overlay.Debug.ROOM_PURPLE;
        /** Room gap warning label (red) */
        public static final int ROOM_GAP = DesignTokens.Overlay.Debug.ROOM_GAP;

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
        public static final int LOS_VISIBLE = DesignTokens.Overlay.Debug.LOS_VISIBLE;
        /** Target out of FOV (yellow) */
        public static final int LOS_OUT_OF_FOV = DesignTokens.Overlay.Debug.LOS_OUT_OF_FOV;
        /** Line of sight blocked (red) */
        public static final int LOS_BLOCKED = DesignTokens.Overlay.Debug.LOS_BLOCKED;

        // --- Zone environment colors (ZoneDebugRenderer) ---
        /** Default zone (white) */
        public static final int ZONE_ENV_DEFAULT = DesignTokens.Overlay.Debug.ZONE_ENV_DEFAULT;
        /** Nether biome zone (red) */
        public static final int ZONE_ENV_NETHER = DesignTokens.Overlay.Debug.ZONE_ENV_NETHER;
        /** End biome zone (purple) */
        public static final int ZONE_ENV_END = DesignTokens.Overlay.Debug.ZONE_ENV_END;
        /** Ice/snow biome zone (cyan) */
        public static final int ZONE_ENV_ICE = DesignTokens.Overlay.Debug.ZONE_ENV_ICE;
        /** Desert biome zone (yellow) */
        public static final int ZONE_ENV_DESERT = DesignTokens.Overlay.Debug.ZONE_ENV_DESERT;
        /** Desert biome zone for wall transitions (orange) */
        public static final int ZONE_ENV_DESERT_WALL = DesignTokens.Overlay.Debug.ZONE_ENV_DESERT_WALL;
        /** Ocean biome zone (blue) */
        public static final int ZONE_ENV_OCEAN = DesignTokens.Overlay.Debug.ZONE_ENV_OCEAN;
        /** Forest biome zone (green) */
        public static final int ZONE_ENV_FOREST = DesignTokens.Overlay.Debug.ZONE_ENV_FOREST;
        /** Cave biome zone (brown) */
        public static final int ZONE_ENV_CAVE = DesignTokens.Overlay.Debug.ZONE_ENV_CAVE;
        /** Night time zone (dark purple) */
        public static final int ZONE_ENV_NIGHT = DesignTokens.Overlay.Debug.ZONE_ENV_NIGHT;
        /** Day time zone (bright yellow) */
        public static final int ZONE_ENV_DAY = DesignTokens.Overlay.Debug.ZONE_ENV_DAY;
        /** Dark lighting zone (dark gray) */
        public static final int ZONE_ENV_DARK = DesignTokens.Overlay.Debug.ZONE_ENV_DARK;
        /** Bright lighting zone (light yellow) */
        public static final int ZONE_ENV_BRIGHT = DesignTokens.Overlay.Debug.ZONE_ENV_BRIGHT;
        /** Fallback zone (light gray) */
        public static final int ZONE_ENV_FALLBACK = DesignTokens.Overlay.Debug.ZONE_ENV_FALLBACK;

        // --- Gap transition effect colors ---
        /** Deep void purple */
        public static final int GAP_VOID = DesignTokens.Overlay.Debug.GAP_VOID;
        /** End void purple */
        public static final int GAP_END = DesignTokens.Overlay.Debug.GAP_END;
        /** Nether dark red */
        public static final int GAP_NETHER = DesignTokens.Overlay.Debug.GAP_NETHER;
        /** Deep dark blue-black */
        public static final int GAP_DARK = DesignTokens.Overlay.Debug.GAP_DARK;

        private Debug() {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VFX FLASH COLORS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Flash {
        /** Headshot flash (red) - RGB only, apply alpha dynamically */
        public static final int HEADSHOT = DesignTokens.Overlay.Flash.HEADSHOT;
        /** Critical hit flash (orange) */
        public static final int CRITICAL = DesignTokens.Overlay.Flash.CRITICAL;
        /** Damage taken flash (red) */
        public static final int DAMAGE = DesignTokens.Overlay.Flash.DAMAGE;
        /** Heal flash (green) */
        public static final int HEAL = DesignTokens.Overlay.Flash.HEAL;
        /** Shield flash (cyan) */
        public static final int SHIELD = DesignTokens.Overlay.Flash.SHIELD;

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
        public static final int LINE_HEIGHT_COMPACT = DesignTokens.Overlay.Dimension.LINE_HEIGHT_COMPACT;
        /** Standard line height (most overlays) */
        public static final int LINE_HEIGHT = DesignTokens.Overlay.Dimension.LINE_HEIGHT;
        /** Readable line height (tutorials, help) */
        public static final int LINE_HEIGHT_READABLE = DesignTokens.Overlay.Dimension.LINE_HEIGHT_READABLE;

        /** Tight panel padding (compact overlays) */
        public static final int PADDING_TIGHT = DesignTokens.Overlay.Dimension.PADDING_TIGHT;
        /** Standard panel padding (most overlays) */
        public static final int PADDING = DesignTokens.Overlay.Dimension.PADDING;
        /** Comfortable panel padding (tutorials, help) */
        public static final int PADDING_COMFORTABLE = DesignTokens.Overlay.Dimension.PADDING_COMFORTABLE;
        /** Spacious panel padding (modals, full screens) */
        public static final int PADDING_SPACIOUS = DesignTokens.Overlay.Dimension.PADDING_SPACIOUS;

        /** Progress bar height */
        public static final int PROGRESS_BAR_HEIGHT = DesignTokens.Overlay.Dimension.PROGRESS_BAR_HEIGHT;
        /** Small progress bar */
        public static final int PROGRESS_BAR_HEIGHT_SM = DesignTokens.Overlay.Dimension.PROGRESS_BAR_HEIGHT_SM;

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
        public static final int WHITE = DesignTokens.Overlay.Utility.WHITE;
        /** Pure black */
        public static final int BLACK = DesignTokens.Overlay.Utility.BLACK;
        /** Shadow/highlight background (25% black) */
        public static final int SHADOW = DesignTokens.Overlay.Utility.SHADOW;
        /** Light shadow (15% black) */
        public static final int SHADOW_LIGHT = DesignTokens.Overlay.Utility.SHADOW_LIGHT;
        /** Heavy shadow (50% black) */
        public static final int SHADOW_HEAVY = DesignTokens.Overlay.Utility.SHADOW_HEAVY;

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
        return DesignTokens.withAlpha(color, alpha);
    }

    /**
     * Get RGB portion of a color (strip alpha).
     * @param color ARGB color
     * @return RGB color
     */
    public static int stripAlpha(int color) {
        return color & DesignTokens.Mask.RGB;
    }

    /**
     * Interpolate between two colors.
     * @see DesignTokens#lerp(int, int, float)
     */
    public static int lerp(int color1, int color2, float t) {
        return DesignTokens.lerp(color1, color2, t);
    }
}
