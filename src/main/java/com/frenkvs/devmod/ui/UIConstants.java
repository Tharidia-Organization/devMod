package com.frenkvs.devmod.ui;

import com.frenkvs.devmod.ui.editor.core.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Design System - Costanti UI unificate per DevMod.
 */
public final class UIConstants {

    private UIConstants() {}

    // === BACKGROUND COLORS (Impact UI Style) ===
    public static final class Background {
        public static int SCREEN() { return ThemeManager.INSTANCE.panelBg(); }
        public static final int SCREEN = 0xE01A1A2E;        // Dark blue (Impact style)
        public static int PANEL() { return ThemeManager.INSTANCE.panelBg(); }
        public static final int PANEL = 0xCC1A1A2E;         // Panel 80% opacity
        public static int PANEL_SOLID() { return ThemeManager.INSTANCE.panelBgSolid(); }
        public static final int PANEL_SOLID = 0xFF1A1A2E;   // Panel 100% opacity
        public static int HEADER() { return ThemeManager.INSTANCE.headerBg(); }
        public static final int HEADER = 0xFF252538;        // Header slightly lighter
        public static int INPUT() { return ThemeManager.INSTANCE.inputBg(); }
        public static final int INPUT = 0xFF151525;         // Dark input field
        public static int HOVER() { return ThemeManager.INSTANCE.hoverBg(); }
        public static final int HOVER = 0xFF2A2A42;         // Dark blue hover
        public static int ACTIVE() { return ThemeManager.INSTANCE.activeBg(); }
        public static final int ACTIVE = 0xFF3D3D5A;        // Active state
        public static int TOOLTIP() { return setAlpha(ThemeManager.INSTANCE.inputBg(), 0xF0); }
        public static final int TOOLTIP = 0xF0151525;       // Dark tooltip
        public static int HUD_PANEL() { return setAlpha(ThemeManager.INSTANCE.panelBgSolid(), 0xCC); }
        public static final int HUD_PANEL = 0xCC1A1A2E;     // HUD panel (Impact)
        public static int GLOW() { return setAlpha(ThemeManager.INSTANCE.borderAccent(), 0x55); }
        public static final int GLOW = 0x553D5AFE;          // Glow effect
        private Background() {}
    }

    // === BORDER COLORS (Impact UI Style) ===
    public static final class Border {
        public static int DEFAULT() { return ThemeManager.INSTANCE.border(); }
        public static final int DEFAULT = 0xFF3D5AFE;       // Electric blue (Impact main)
        public static int LIGHT() { return ThemeManager.INSTANCE.current().borderHover(); }
        public static final int LIGHT = 0xFF5C7AFF;         // Lighter variant
        public static int ACCENT() { return ThemeManager.INSTANCE.borderAccent(); }
        public static final int ACCENT = 0xFF00FFFF;        // Cyan (accent/hover border)
        public static int SEPARATOR() { return ThemeManager.INSTANCE.separator(); }
        public static final int SEPARATOR = 0x803D5AFE;     // Separator 50% alpha
        public static int GLOW() { return setAlpha(ThemeManager.INSTANCE.borderAccent(), 0x55); }
        public static final int GLOW = 0x553D5AFE;          // Glow border
        public static int MUTED() { return ThemeManager.INSTANCE.borderMuted(); }
        public static final int MUTED = 0xFF2A2A4A;         // Muted border
        private Border() {}
    }

    // === TEXT COLORS (Impact UI Style) ===
    public static final class Text {
        public static int PRIMARY() { return ThemeManager.INSTANCE.textPrimary(); }
        public static final int PRIMARY = 0xFFFFFFFF;       // Primary white
        public static int SECONDARY() { return ThemeManager.INSTANCE.textSecondary(); }
        public static final int SECONDARY = 0xFFAAAAAA;     // Secondary gray (muted)
        public static int MUTED() { return ThemeManager.INSTANCE.textMuted(); }
        public static final int MUTED = 0xFF888888;         // Muted gray
        public static int DISABLED() { return ThemeManager.INSTANCE.textDisabled(); }
        public static final int DISABLED = 0xFF555555;      // Disabled gray
        public static int ACCENT() { return ThemeManager.INSTANCE.accent(); }
        public static final int ACCENT = 0xFF3D5AFE;        // Electric blue
        public static int TITLE() { return ThemeManager.INSTANCE.textTitle(); }
        public static final int TITLE = 0xFF00FFFF;         // Cyan (Impact titles)
        public static int VALUE() { return ThemeManager.INSTANCE.current().textValue(); }
        public static final int VALUE = 0xFF00FF00;         // Green (Impact values)
        public static int FORMULA() { return ThemeManager.INSTANCE.current().textFormula(); }
        public static final int FORMULA = 0xFFFFD700;       // Gold (Impact formulas)
        public static int WHITE() { return WHITE; }
        public static final int WHITE = 0xFFFFFFFF;         // Pure white
        private Text() {}
    }

    // === ACCENT COLORS (Impact UI Style) ===
    public static final class Accent {
        public static int BLUE() { return ThemeManager.INSTANCE.info(); }
        public static final int BLUE = 0xFF3D5AFE;          // Blu elettrico Impact
        public static int GREEN() { return ThemeManager.INSTANCE.success(); }
        public static final int GREEN = 0xFF00FF00;         // Verde Impact
        public static int RED() { return ThemeManager.INSTANCE.error(); }
        public static final int RED = 0xFFFF4444;           // Rosso Impact
        public static int YELLOW() { return YELLOW; }
        public static final int YELLOW = 0xFFFFFF00;        // Giallo
        public static int ORANGE() { return ThemeManager.INSTANCE.warning(); }
        public static final int ORANGE = 0xFFFF9800;        // Arancione
        public static int PURPLE() { return PURPLE; }
        public static final int PURPLE = 0xFF9C27B0;        // Viola
        public static int CYAN() { return ThemeManager.INSTANCE.accent(); }
        public static final int CYAN = 0xFF00FFFF;          // Cyan Impact (titoli)
        public static int GOLD() { return GOLD; }
        public static final int GOLD = 0xFFFFD700;          // Oro Impact (formule)
        private Accent() {}
    }

    // === TOGGLE COLORS (Impact UI Style) ===
    public static final class Toggle {
        public static int ON() { return ThemeManager.INSTANCE.success(); }
        public static final int ON = Accent.GREEN;          // Verde Impact
        public static int OFF() { return ThemeManager.INSTANCE.inputBg(); }
        public static final int OFF = Background.INPUT;     // Scuro
        public static int ON_HOVER() { return lighten(ThemeManager.INSTANCE.success(), 0.15f); }
        public static final int ON_HOVER = 0xFF44FF44;      // Lighter green
        public static int OFF_HOVER() { return ThemeManager.INSTANCE.hoverBg(); }
        public static final int OFF_HOVER = Background.HOVER;
        private Toggle() {}
    }

    // === STATUS COLORS (Impact UI Style) ===
    public static final class Status {
        public static int SUCCESS() { return ThemeManager.INSTANCE.success(); }
        public static final int SUCCESS = 0xFF00FF00;       // Verde Impact
        public static int ERROR() { return ThemeManager.INSTANCE.error(); }
        public static final int ERROR = 0xFFFF4444;         // Rosso Impact
        public static int WARNING() { return ThemeManager.INSTANCE.warning(); }
        public static final int WARNING = 0xFFFFD700;       // Oro Impact
        public static int INFO() { return ThemeManager.INSTANCE.info(); }
        public static final int INFO = 0xFF3D5AFE;          // Blu Impact
        public static int PENDING() { return ThemeManager.INSTANCE.textMuted(); }
        public static final int PENDING = 0xFFAAAAAA;       // Grigio muted
        private Status() {}
    }

    // === BODY PART COLORS ===
    public static final class BodyPart {
        public static final int HEAD = 0xFF00FFFF;
        public static final int BODY = 0xFF00FF00;
        public static final int ARMS = 0xFFFFFF00;
        public static final int LEGS = 0xFFFF0000;
        private BodyPart() {}
    }

    // === SIZES ===
    public static final class Size {
        public static final int BUTTON_WIDTH = 200;
        public static final int BUTTON_WIDTH_SMALL = 100;
        public static final int BUTTON_WIDTH_WIDE = 240;
        public static final int BUTTON_WIDTH_MEDIUM = 120;
        public static final int BUTTON_WIDTH_ICON = 20;        // For +/- buttons
        public static final int BUTTON_HEIGHT = 20;
        public static final int BUTTON_HEIGHT_COMPACT = 18;
        public static final int BUTTON_HEIGHT_PROMINENT = 28;  // For important actions
        public static final int BUTTON_HEIGHT_LARGE = 30;      // For primary CTA
        public static final int TAB_WIDTH = 80;
        public static final int TAB_HEIGHT = 20;
        public static final int INPUT_WIDTH = 80;
        public static final int INPUT_WIDTH_WIDE = 120;
        public static final int INPUT_HEIGHT = 20;
        public static final int LABEL_WIDTH = 90;
        public static final int PANEL_WIDTH = 220;
        public static final int PANEL_WIDTH_WIDE = 260;
        public static final int SIDEBAR_WIDTH = 200;           // Standard sidebar width
        public static final int CATEGORY_WIDTH = 150;          // Category list width
        public static final int TOGGLE_WIDTH = 40;
        public static final int TOGGLE_HEIGHT = 16;
        // Modal dialog panel sizes
        public static final int DIALOG_WIDTH_SMALL = 300;      // Confirmation dialogs
        public static final int DIALOG_WIDTH_MEDIUM = 380;     // Checkpoint/death screens
        public static final int DIALOG_WIDTH_LARGE = 420;      // Completion screens
        private Size() {}
    }

    // === SPACING ===
    public static final class Spacing {
        public static final int PANEL_PADDING = 8;
        public static final int PANEL_MARGIN = 10;
        public static final int BUTTON_GAP = 25;
        public static final int GAP_SMALL = 4;
        public static final int GAP_MEDIUM = 8;
        public static final int GAP_LARGE = 12;
        public static final int LINE_HEIGHT = 11;
        public static final int SECTION_GAP = 8;
        public static final int HEADER_HEIGHT = 20;
        private Spacing() {}
    }

    // === POSITIONS ===
    public static final class Position {
        public static final int TITLE_Y = 8;
        public static final int SUBTITLE_Y = 45;
        public static final int CONTENT_START_Y = 60;
        public static final int BOTTOM_MARGIN = 30;
        private Position() {}
    }

    // === UTILITY METHODS ===

    public static int withAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int setAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    public static int lerp(int colorA, int colorB, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aA = (colorA >> 24) & 0xFF, rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF, bA = colorA & 0xFF;
        int aB = (colorB >> 24) & 0xFF, rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF, bB = colorB & 0xFF;
        int a = (int) (aA + (aB - aA) * t), r = (int) (rA + (rB - rA) * t);
        int g = (int) (gA + (gB - gA) * t), b = (int) (bA + (bB - bA) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int lighten(int color, float amount) {
        return lerp(color, 0xFFFFFFFF, amount);
    }

    public static int darken(int color, float amount) {
        return lerp(color, (color & 0xFF000000), amount);
    }

    public static int getHealthColor(float healthPercent) {
        if (healthPercent > 50) return Accent.GREEN;
        if (healthPercent > 25) return Accent.YELLOW;
        return Accent.RED;
    }

    public static int centerX(int screenWidth, int elementWidth) {
        return (screenWidth - elementWidth) / 2;
    }

    public static int tabStartX(int screenWidth, int tabCount, int tabWidth) {
        return (screenWidth - (tabCount * tabWidth)) / 2;
    }

    // === SOUND FEEDBACK ===

    /**
     * Helper class for playing UI sound feedback.
     * Provides consistent audio cues for user actions.
     */
    public static final class Sound {
        private Sound() {}

        /** Play a click sound for button presses */
        public static void click() {
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
        }

        /** Play a success sound for completed actions (save, confirm) */
        public static void success() {
            playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.5f);
        }

        /** Play an error sound for failed actions */
        public static void error() {
            playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
        }

        /** Play a warning sound */
        public static void warning() {
            playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.8f);
        }

        /** Play a toggle on sound */
        public static void toggleOn() {
            playSound(SoundEvents.LEVER_CLICK, 0.5f, 1.2f);
        }

        /** Play a toggle off sound */
        public static void toggleOff() {
            playSound(SoundEvents.LEVER_CLICK, 0.5f, 0.8f);
        }

        /** Play a notification sound */
        public static void notification() {
            playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
        }

        /** Play a save confirmation sound */
        public static void save() {
            playSound(SoundEvents.VILLAGER_YES, 0.8f, 1.2f);
        }

        /** Play a delete/reset sound */
        public static void delete() {
            playSound(SoundEvents.ITEM_PICKUP, 0.6f, 0.6f);
        }

        /** Generic sound player */
        
        private static void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                @Nonnull net.minecraft.sounds.SoundEvent safeSound = Objects.requireNonNull(sound, "sound");
                @Nonnull SoundInstance soundInstance = Objects.requireNonNull(
                    SimpleSoundInstance.forUI(safeSound, pitch, volume), "soundInstance");
                mc.getSoundManager().play(soundInstance);
            }
        }
    }
}
