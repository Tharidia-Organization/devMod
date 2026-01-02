package com.devmod.client.notification;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;

/**
 * Theme constants for the Notification UI system.
 *
 * <p>This class provides notification-specific colors that complement the
 * core {@link DesignTokens} palette. The notification system uses a warmer
 * color scheme (browns/golds) to distinguish it from the main editor UI.
 *
 * <p>Utility methods delegate to {@link DesignTokens} for consistency.
 *
 * @see DesignTokens for the core design token definitions
 */
public final class NotificationUiTheme {

    private NotificationUiTheme() {}

    // ═══════════════════════════════════════════════════════════════
    // NOTIFICATION-SPECIFIC PALETTE (warm tones)
    // These complement DesignTokens with notification-specific colors
    // ═══════════════════════════════════════════════════════════════

    // Text colors (warm cream tones for notification panels)
    public static final int RGB_TEXT_PRIMARY = 0xF5F1E8;
    public static final int RGB_TEXT_SECONDARY = 0xCBBFA8;
    public static final int RGB_TEXT_MUTED = 0x938877;

    // Panel gradients (warm browns)
    public static final int RGB_PANEL_TOP = 0x2A2319;
    public static final int RGB_PANEL_BOTTOM = 0x162227;
    public static final int RGB_BACKDROP_TOP = mix(RGB_PANEL_TOP, 0x000000, 0.45f);
    public static final int RGB_BACKDROP_BOTTOM = mix(RGB_PANEL_BOTTOM, 0x000000, 0.55f);
    public static final int RGB_PANEL_INNER_TOP = 0x30281E;
    public static final int RGB_PANEL_INNER_BOTTOM = 0x1B252A;

    // Surface colors
    public static final int RGB_SURFACE_TOP = 0x2E271D;
    public static final int RGB_SURFACE_BOTTOM = 0x221C14;
    public static final int RGB_SURFACE_HOVER_TOP = 0x3A3124;
    public static final int RGB_SURFACE_HOVER_BOTTOM = 0x2A2218;
    public static final int RGB_SURFACE_READ = 0x1E1811;

    // Accent colors (gold/teal)
    public static final int RGB_ACCENT = 0xE1A44C;
    public static final int RGB_ACCENT_SOFT = 0x9B6D2E;
    public static final int RGB_ACCENT_ALT = 0x2CB5A0;

    // ═══════════════════════════════════════════════════════════════
    // UTILITY METHODS (delegate to DesignTokens)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Apply alpha to an RGB color.
     * @see DesignTokens#withAlpha(int, int)
     */
    public static int withAlpha(int rgb, int alpha) {
        return DesignTokens.withAlpha(rgb, alpha);
    }

    /**
     * Mix two RGB colors (ignores alpha).
     * @see DesignTokens#lerp(int, int, float)
     */
    public static int mix(int rgbA, int rgbB, float t) {
        // Use DesignTokens.lerp but handle RGB (no alpha channel)
        int result = DesignTokens.lerp(rgbA | 0xFF000000, rgbB | 0xFF000000, t);
        return result & 0x00FFFFFF; // Strip alpha for RGB-only result
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
            case NEWS -> 0x7B9CFF;
            case ADMIN -> 0xE88B3D;
            case SYSTEM -> 0x8E97A6;
            case MAILBOX -> 0x3AA6D0;
        };
    }

    public static String getCategoryIcon(NotificationCategory category) {
        return switch (category) {
            case ACHIEVEMENT -> "*";
            case RECORD -> "#";
            case SEASON -> "S";
            case TOKEN -> "$";
            case REWARD -> "+";
            case PARTY -> "P";
            case QUEST -> "Q";
            case COMBAT -> "!";
            case RESONANCE -> "R";
            case NEWS -> "N";
            case ADMIN -> "!";
            case SYSTEM -> "i";
            case MAILBOX -> "M";
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
