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
    public static final int RGB_TEXT_PRIMARY = DesignTokens.Notification.RGB_TEXT_PRIMARY;
    public static final int RGB_TEXT_SECONDARY = DesignTokens.Notification.RGB_TEXT_SECONDARY;
    public static final int RGB_TEXT_MUTED = DesignTokens.Notification.RGB_TEXT_MUTED;
    public static final int RGB_WHITE = DesignTokens.Notification.RGB_WHITE;
    public static final int RGB_BLACK = DesignTokens.Notification.RGB_BLACK;

    // Panel gradients (warm browns)
    public static final int RGB_PANEL_TOP = DesignTokens.Notification.RGB_PANEL_TOP;
    public static final int RGB_PANEL_BOTTOM = DesignTokens.Notification.RGB_PANEL_BOTTOM;
    public static final int RGB_BACKDROP_TOP = DesignTokens.Notification.RGB_BACKDROP_TOP;
    public static final int RGB_BACKDROP_BOTTOM = DesignTokens.Notification.RGB_BACKDROP_BOTTOM;
    public static final int RGB_PANEL_INNER_TOP = DesignTokens.Notification.RGB_PANEL_INNER_TOP;
    public static final int RGB_PANEL_INNER_BOTTOM = DesignTokens.Notification.RGB_PANEL_INNER_BOTTOM;

    // Surface colors
    public static final int RGB_SURFACE_TOP = DesignTokens.Notification.RGB_SURFACE_TOP;
    public static final int RGB_SURFACE_BOTTOM = DesignTokens.Notification.RGB_SURFACE_BOTTOM;
    public static final int RGB_SURFACE_HOVER_TOP = DesignTokens.Notification.RGB_SURFACE_HOVER_TOP;
    public static final int RGB_SURFACE_HOVER_BOTTOM = DesignTokens.Notification.RGB_SURFACE_HOVER_BOTTOM;
    public static final int RGB_SURFACE_READ = DesignTokens.Notification.RGB_SURFACE_READ;

    // Accent colors (gold/teal)
    public static final int RGB_ACCENT = DesignTokens.Notification.RGB_ACCENT;
    public static final int RGB_ACCENT_SOFT = DesignTokens.Notification.RGB_ACCENT_SOFT;
    public static final int RGB_ACCENT_ALT = DesignTokens.Notification.RGB_ACCENT_ALT;

    public static final class Category {
        public static final int ACHIEVEMENT = DesignTokens.Notification.Category.ACHIEVEMENT;
        public static final int RECORD = DesignTokens.Notification.Category.RECORD;
        public static final int SEASON = DesignTokens.Notification.Category.SEASON;
        public static final int TOKEN = DesignTokens.Notification.Category.TOKEN;
        public static final int REWARD = DesignTokens.Notification.Category.REWARD;
        public static final int PARTY = DesignTokens.Notification.Category.PARTY;
        public static final int QUEST = DesignTokens.Notification.Category.QUEST;
        public static final int COMBAT = DesignTokens.Notification.Category.COMBAT;
        public static final int RESONANCE = DesignTokens.Notification.Category.RESONANCE;
        public static final int NEWS = DesignTokens.Notification.Category.NEWS;
        public static final int ADMIN = DesignTokens.Notification.Category.ADMIN;
        public static final int SYSTEM = DesignTokens.Notification.Category.SYSTEM;
        public static final int MAILBOX = DesignTokens.Notification.Category.MAILBOX;

        private Category() {}
    }

    public static final class Priority {
        public static final int LOW = DesignTokens.Notification.Priority.LOW;
        public static final int NORMAL = DesignTokens.Notification.Priority.NORMAL;
        public static final int HIGH = DesignTokens.Notification.Priority.HIGH;
        public static final int URGENT = DesignTokens.Notification.Priority.URGENT;
        public static final int CRITICAL = DesignTokens.Notification.Priority.CRITICAL;

        private Priority() {}
    }

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
        int result = DesignTokens.lerp(rgbA | DesignTokens.Mask.ALPHA, rgbB | DesignTokens.Mask.ALPHA, t);
        return result & DesignTokens.Mask.RGB; // Strip alpha for RGB-only result
    }

    public static int getCategoryColor(NotificationCategory category) {
        return switch (category) {
            case ACHIEVEMENT -> Category.ACHIEVEMENT;
            case RECORD -> Category.RECORD;
            case SEASON -> Category.SEASON;
            case TOKEN -> Category.TOKEN;
            case REWARD -> Category.REWARD;
            case PARTY -> Category.PARTY;
            case QUEST -> Category.QUEST;
            case COMBAT -> Category.COMBAT;
            case RESONANCE -> Category.RESONANCE;
            case NEWS -> Category.NEWS;
            case ADMIN -> Category.ADMIN;
            case SYSTEM -> Category.SYSTEM;
            case MAILBOX -> Category.MAILBOX;
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
            case LOW -> Priority.LOW;
            case NORMAL -> Priority.NORMAL;
            case HIGH -> Priority.HIGH;
            case URGENT -> Priority.URGENT;
            case CRITICAL -> Priority.CRITICAL;
        };
    }

    public static int getPriorityGlowColor(NotificationPriority priority) {
        return switch (priority) {
            case CRITICAL -> Priority.CRITICAL;
            case URGENT -> Priority.URGENT;
            case HIGH -> Priority.HIGH;
            default -> RGB_BLACK;
        };
    }
}
