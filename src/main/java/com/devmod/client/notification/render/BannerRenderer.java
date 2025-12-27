package com.devmod.client.notification.render;

import java.util.Objects;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.notification.NotificationUiTheme;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationPriority;

/**
 * Renders banner-style notifications at the top-center of the screen.
 *
 * <p>Banner notifications are used for high-priority events that need
 * immediate player attention, such as:
 * <ul>
 *   <li>Quest completions</li>
 *   <li>Personal records</li>
 *   <li>Critical alerts</li>
 *   <li>Major achievements</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class BannerRenderer {

    // ============================================================================
    // CONSTANTS
    // ============================================================================

    public static final int BANNER_MIN_WIDTH = 250;
    public static final int BANNER_MAX_WIDTH = 400;
    public static final int BANNER_HEIGHT = 50;
    public static final int BANNER_TOP_MARGIN = 30;

    public static final int BORDER_THICKNESS = 2;
    public static final int SLIDE_IN_MS = 250;
    public static final int FADE_OUT_MS = 400;

    // ============================================================================
    // RENDERING
    // ============================================================================

    /**
     * Render a banner notification.
     *
     * @param graphics The graphics context
     * @param font The font renderer
     * @param screenWidth Screen width
     * @param notification The notification to render
     * @param alpha Current alpha (0-1)
     * @param slideProgress Slide-in progress (0-1)
     */
    public static void render(
            GuiGraphics graphics,
            Font font,
            int screenWidth,
            Notification notification,
            float alpha,
            float slideProgress) {

        // Calculate banner width based on content
        String titleKey = notification.titleKey();
        String messageKey = notification.messageKey();

        Component title = titleKey != null
                ? Component.translatable(Objects.requireNonNull(titleKey))
                : Component.empty();
        Component message = messageKey != null
                ? Component.translatable(Objects.requireNonNull(messageKey))
                : null;

        int titleWidth = font.width(Objects.requireNonNull(title));
        int messageWidth = message != null ? font.width(Objects.requireNonNull(message)) : 0;
        int contentWidth = Math.max(titleWidth, messageWidth) + 40;
        int bannerWidth = Math.max(BANNER_MIN_WIDTH, Math.min(BANNER_MAX_WIDTH, contentWidth));

        // Position: centered horizontally, slide in from top
        int x = (screenWidth - bannerWidth) / 2;
        int targetY = BANNER_TOP_MARGIN;
        int slideOffset = (int) ((1.0f - easeOutBack(slideProgress)) * (BANNER_HEIGHT + BANNER_TOP_MARGIN));
        int y = targetY - slideOffset;

        // Background with gradient effect
        int bgAlpha = (int) (210 * alpha);
        int bgTop = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_TOP, bgAlpha);
        int bgBottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_BOTTOM, bgAlpha);
        graphics.fillGradient(x, y, x + bannerWidth, y + BANNER_HEIGHT, bgTop, bgBottom);

        // Inner highlight
        int highlightAlpha = (int) (40 * alpha);
        int highlightColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, highlightAlpha);
        graphics.fill(x + 1, y + 1, x + bannerWidth - 1, y + 3, highlightColor);

        // Border based on priority
        int borderColor = getPriorityBorderColor(notification.priority(), alpha);
        graphics.fill(x, y, x + bannerWidth, y + BORDER_THICKNESS, borderColor);
        graphics.fill(x, y + BANNER_HEIGHT - BORDER_THICKNESS, x + bannerWidth, y + BANNER_HEIGHT, borderColor);
        graphics.fill(x, y, x + BORDER_THICKNESS, y + BANNER_HEIGHT, borderColor);
        graphics.fill(x + bannerWidth - BORDER_THICKNESS, y, x + bannerWidth, y + BANNER_HEIGHT, borderColor);

        // Icon (left side)
        String iconId = notification.iconId();
        int textStartX = x + 15;
        if (iconId != null && !iconId.isEmpty()) {
            int iconSize = 24;
            int iconX = x + 12;
            int iconY = y + (BANNER_HEIGHT - iconSize) / 2;

            // Icon background circle
            int iconBgAlpha = (int) (alpha * 80);
            renderIconBackground(graphics, iconX, iconY, iconSize, iconBgAlpha);
            textStartX = iconX + iconSize + 10;
        }

        // Text rendering
        int textAlpha = (int) (255 * alpha);
        int titleColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, textAlpha);
        int messageColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, textAlpha);

        // Title (centered or top if message present)
        int titleY = message != null ? y + 12 : y + (BANNER_HEIGHT - 9) / 2;
        int maxTextWidth = bannerWidth - (textStartX - x) - 15;
        renderCenteredText(graphics, font, title, x, bannerWidth, titleY, maxTextWidth, titleColor);

        // Message if present
        if (message != null) {
            int messageY = y + 28;
            renderCenteredText(graphics, font, message, x, bannerWidth, messageY, maxTextWidth, messageColor);
        }

        // Glow effect for critical priority
        if (notification.priority() == NotificationPriority.CRITICAL && alpha > 0.5f) {
            renderCriticalGlow(graphics, x, y, bannerWidth, alpha);
        }
    }

    /**
     * Render icon background circle.
     */
    private static void renderIconBackground(GuiGraphics graphics, int x, int y, int size, int alpha) {
        int color = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, alpha);
        // Simple square for now - could be circle with shader
        graphics.fill(x, y, x + size, y + size, color);
    }

    /**
     * Render centered text.
     */
    private static void renderCenteredText(
            GuiGraphics graphics,
            Font font,
            Component text,
            int bannerX,
            int bannerWidth,
            int y,
            int maxWidth,
            int color) {

        String str = text.getString();
        int textWidth = font.width(Objects.requireNonNull(str));

        if (textWidth > maxWidth) {
            str = font.plainSubstrByWidth(Objects.requireNonNull(str), maxWidth - 10) + "...";
            textWidth = font.width(str);
        }

        int x = bannerX + (bannerWidth - textWidth) / 2;
        graphics.drawString(font, str, x, y, color, true);
    }

    /**
     * Render critical priority glow effect.
     */
    private static void renderCriticalGlow(GuiGraphics graphics, int x, int y, int width, float alpha) {
        // Pulsing red glow
        float pulse = (float) (0.5f + 0.5f * Math.sin(System.currentTimeMillis() / 200.0));
        int glowAlpha = (int) (30 * alpha * pulse);
        int glowColor = NotificationUiTheme.withAlpha(NotificationUiTheme.getPriorityColor(NotificationPriority.CRITICAL), glowAlpha);

        // Outer glow
        graphics.fill(x - 2, y - 2, x + width + 2, y, glowColor);
        graphics.fill(x - 2, y + BANNER_HEIGHT, x + width + 2, y + BANNER_HEIGHT + 2, glowColor);
    }

    /**
     * Get border color for a priority level.
     */
    public static int getPriorityBorderColor(NotificationPriority priority, float alpha) {
        int a = (int) (255 * alpha);
        return NotificationUiTheme.withAlpha(NotificationUiTheme.getPriorityColor(priority), a);
    }

    /**
     * Ease-out with overshoot for bouncy feel.
     */
    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
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
            return elapsed / (float) SLIDE_IN_MS;
        } else if (elapsed > duration - FADE_OUT_MS) {
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
     * Check if a banner has expired.
     *
     * @param elapsed Time since notification started (ms)
     * @param duration Total display duration (ms)
     * @return true if expired
     */
    public static boolean isExpired(long elapsed, int duration) {
        return elapsed > duration;
    }
}
