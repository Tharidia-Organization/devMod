package com.devmod.client.notification;

import net.minecraft.util.Mth;

import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;

public final class NotificationUiTheme {

    private NotificationUiTheme() {}

    // Base palette (RGB)
    public static final int RGB_TEXT_PRIMARY = 0xF5F1E8;
    public static final int RGB_TEXT_SECONDARY = 0xCBBFA8;
    public static final int RGB_TEXT_MUTED = 0x938877;

    public static final int RGB_PANEL_TOP = 0x2A2319;
    public static final int RGB_PANEL_BOTTOM = 0x162227;
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

    public static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    public static int mix(int rgbA, int rgbB, float t) {
        int r = (int) Mth.lerp(t, (rgbA >> 16) & 0xFF, (rgbB >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (rgbA >> 8) & 0xFF, (rgbB >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, rgbA & 0xFF, rgbB & 0xFF);
        return (r << 16) | (g << 8) | b;
    }

    public static int getCategoryColor(NotificationCategory category) {
        return switch (category) {
            case ACHIEVEMENT -> 0xE7B84D;
            case RECORD -> 0x34C9C9;
            case SEASON -> 0x4F7BD9;
            case TOKEN -> 0x59B77C;
            case REWARD -> 0xF19A3E;
            case PARTY -> 0x5DA7E3;
            case QUEST -> 0xD86C4D;
            case COMBAT -> 0xE2554F;
            case RESONANCE -> 0x60D1A7;
            case ADMIN -> 0xE88B3D;
            case SYSTEM -> 0x8E97A6;
            case MAILBOX -> 0x3AA6D0;
        };
    }

    public static String getCategoryIcon(NotificationCategory category) {
        return switch (category) {
            case ACHIEVEMENT -> "\u2605";
            case RECORD -> "\u2691";
            case SEASON -> "\u2606";
            case TOKEN -> "\u25C9";
            case REWARD -> "\u2726";
            case PARTY -> "\u263A";
            case QUEST -> "\u2694";
            case COMBAT -> "\u26A1";
            case RESONANCE -> "\u2728";
            case ADMIN -> "\u26A0";
            case SYSTEM -> "\u2699";
            case MAILBOX -> "\u2709";
        };
    }

    public static int getPriorityColor(NotificationPriority priority) {
        return switch (priority) {
            case LOW -> 0x8E97A6;
            case NORMAL -> 0x5DA7E3;
            case HIGH -> 0xE1A44C;
            case URGENT -> 0xE88B3D;
            case CRITICAL -> 0xE2554F;
        };
    }

    public static int getPriorityGlowColor(NotificationPriority priority) {
        return switch (priority) {
            case CRITICAL -> 0xE2554F;
            case URGENT -> 0xE88B3D;
            case HIGH -> 0xE1A44C;
            default -> 0x000000;
        };
    }
}
