package com.devmod.client.notification.render;

import java.util.Objects;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.notification.NotificationUiTheme;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;

/**
 * Renders toast-style notifications in the bottom-right corner.
 *
 * <p>Toast notifications are small, unobtrusive notifications that stack vertically
 * and auto-dismiss after their display duration.
 */
@OnlyIn(Dist.CLIENT)
public class ToastRenderer {

    // ============================================================================
    // CONSTANTS
    // ============================================================================

    public static final int TOAST_WIDTH = 200;
    public static final int TOAST_HEIGHT = 40;
    public static final int TOAST_MARGIN = 8;
    public static final int TOAST_PADDING = 8;
    public static final int TOAST_SPACING = 4;

    public static final int ACCENT_BAR_WIDTH = 3;
    public static final int SLIDE_IN_MS = 200;
    public static final int FADE_OUT_MS = 300;

    // ============================================================================
    // RENDERING
    // ============================================================================

    /**
     * Render a toast notification.
     *
     * @param graphics The graphics context
     * @param font The font renderer
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param notification The notification to render
     * @param index The toast's position in the stack (0 = bottom)
     * @param alpha Current alpha (0-1)
     * @param slideProgress Slide-in progress (0-1)
     */
    public static void render(
            GuiGraphics graphics,
            Font font,
            int screenWidth,
            int screenHeight,
            Notification notification,
            int index,
            float alpha,
            float slideProgress) {

        // Calculate position with slide-in animation
        int targetX = screenWidth - TOAST_WIDTH - TOAST_MARGIN;
        int slideOffset = (int) ((1.0f - easeOutCubic(slideProgress)) * (TOAST_WIDTH + TOAST_MARGIN));
        int x = targetX + slideOffset;

        // Stack position from bottom
        int baseY = screenHeight - TOAST_MARGIN - ((index + 1) * (TOAST_HEIGHT + TOAST_SPACING));

        // Background with alpha
        int bgAlpha = (int) (200 * alpha);
        int bgTop = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_TOP, bgAlpha);
        int bgBottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, bgAlpha);
        graphics.fillGradient(x, baseY, x + TOAST_WIDTH, baseY + TOAST_HEIGHT, bgTop, bgBottom);

        // Left accent bar based on category
        int accentColor = getCategoryColor(notification.category(), alpha);
        graphics.fill(x, baseY, x + ACCENT_BAR_WIDTH, baseY + TOAST_HEIGHT, accentColor);

        // Icon area (placeholder - can be extended to render actual icons)
        int iconX = x + TOAST_PADDING + ACCENT_BAR_WIDTH;
        int iconSize = 16;
        String iconId = notification.iconId();
        if (iconId != null && !iconId.isEmpty()) {
            // Render icon background circle
            int iconBgColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, (int) (alpha * 90));
            graphics.fill(iconX, baseY + 4, iconX + iconSize, baseY + 4 + iconSize, iconBgColor);
        }

        // Text area
        int textX = iconId != null ? iconX + iconSize + 6 : x + TOAST_PADDING + ACCENT_BAR_WIDTH + 4;
        int textAlpha = (int) (255 * alpha);
        int textColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, textAlpha);
        int subTextColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, textAlpha);

        Object[] args = notification.params().values().toArray(new Object[0]);

        // Title
        String titleKey = notification.titleKey();
        if (titleKey != null) {
            Component title = Component.translatable(Objects.requireNonNull(titleKey), args);
            int maxTitleWidth = TOAST_WIDTH - (textX - x) - TOAST_PADDING;
            renderText(graphics, font, title, textX, baseY + 8, maxTitleWidth, textColor);
        }

        // Message (subtitle)
        String messageKey = notification.messageKey();
        if (messageKey != null) {
            Component message = Component.translatable(Objects.requireNonNull(messageKey), args);
            int maxMsgWidth = TOAST_WIDTH - (textX - x) - TOAST_PADDING;
            renderText(graphics, font, message, textX, baseY + 22, maxMsgWidth, subTextColor);
        }

        // Border highlight on top for emphasis
        if (alpha > 0.5f) {
            int borderColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, (int) (alpha * 60));
            graphics.fill(x, baseY, x + TOAST_WIDTH, baseY + 1, borderColor);
        }
    }

    /**
     * Render text with truncation if needed.
     */
    private static void renderText(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int maxWidth,
            int color) {

        String str = text.getString();
        if (font.width(Objects.requireNonNull(str)) > maxWidth) {
            str = font.plainSubstrByWidth(Objects.requireNonNull(str), maxWidth - 10) + "...";
        }
        graphics.drawString(font, str, x, y, color, true);
    }

    /**
     * Get color for a notification category.
     */
    public static int getCategoryColor(NotificationCategory category, float alpha) {
        int a = (int) (255 * alpha);
        return NotificationUiTheme.withAlpha(NotificationUiTheme.getCategoryColor(category), a);
    }

    /**
     * Cubic ease-out for smooth animations.
     */
    private static float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    /**
     * Calculate alpha based on timing.
     *
     * @param elapsed Time since notification started (ms)
     * @param duration Total display duration (ms)
     * @return Alpha value 0-1
     */
    public static float calculateAlpha(long elapsed, int duration) {
        if (elapsed < SLIDE_IN_MS) {
            // Fade in during slide
            return elapsed / (float) SLIDE_IN_MS;
        } else if (elapsed > duration - FADE_OUT_MS) {
            // Fade out at end
            return Math.max(0, (duration - elapsed) / (float) FADE_OUT_MS);
        }
        return 1.0f;
    }

    /**
     * Calculate slide progress.
     *
     * @param elapsed Time since notification started (ms)
     * @return Slide progress 0-1
     */
    public static float calculateSlideProgress(long elapsed) {
        return Math.min(1.0f, elapsed / (float) SLIDE_IN_MS);
    }

    /**
     * Check if a toast has expired.
     *
     * @param elapsed Time since notification started (ms)
     * @param duration Total display duration (ms)
     * @return true if expired
     */
    public static boolean isExpired(long elapsed, int duration) {
        return elapsed > duration;
    }
}
