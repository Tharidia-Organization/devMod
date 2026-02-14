package com.devmod.client.ui.radial.render;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.radial.config.RadialMenuScaler;

/**
 * Shared rendering utility methods used by multiple radial menu renderers.
 * Extracted to eliminate code duplication across RadialCategoryRenderer,
 * RadialHubRenderer, and RadialTooltipRenderer.
 */
public final class RadialRenderUtils {

    private RadialRenderUtils() {
        // Utility class - no instantiation
    }

    /**
     * Resolves the current font scale from RadialMenuScaler.
     * Returns 1.0 if the scale is invalid or unset.
     */
    public static float resolveFontScale() {
        float scale = RadialMenuScaler.getFontScale();
        return scale > 0f ? scale : 1f;
    }

    /**
     * Converts a pixel width to font-coordinate width by dividing by scale.
     *
     * @param width pixel width
     * @param scale font scale factor
     * @return width in font coordinates
     */
    public static int toFontWidth(int width, float scale) {
        if (scale <= 0f) {
            return width;
        }
        return Math.max(0, Math.round(width / scale));
    }

    /**
     * Calculates the scaled width of a text string.
     *
     * @param font  the font
     * @param text  the text
     * @param scale font scale factor
     * @return width in pixels after applying scale
     */
    public static int scaledWidth(@Nonnull Font font, @Nonnull String text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    /**
     * Calculates the scaled line height.
     *
     * @param font  the font
     * @param scale font scale factor
     * @return line height in pixels after applying scale
     */
    public static int scaledLineHeight(Font font, float scale) {
        return Math.max(1, Math.round(font.lineHeight * scale));
    }

    /**
     * Draws a centered string with optional scaling.
     * When scale is ~1.0, delegates directly to UIScaleManager for efficiency.
     *
     * @param graphics graphics context
     * @param font     font
     * @param text     text to draw
     * @param x        center X coordinate
     * @param y        Y coordinate
     * @param color    ARGB color
     * @param scale    font scale factor
     */
    public static void drawCenteredStringScaled(GuiGraphics graphics, @Nonnull Font font, @Nonnull String text,
                                                 int x, int y, int color, float scale) {
        if (scale <= 0f || Math.abs(scale - 1f) < 0.001f) {
            UIScaleManager.drawScaledCenteredString(graphics, font, text, x, y, color);
            return;
        }
        float inv = 1f / scale;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawCenteredString(font, text, Math.round(x * inv), Math.round(y * inv), color);
        graphics.pose().popPose();
    }

    /**
     * Draws a string with optional scaling.
     * When scale is ~1.0, delegates directly to UIScaleManager for efficiency.
     *
     * @param graphics graphics context
     * @param font     font
     * @param text     text to draw
     * @param x        X coordinate
     * @param y        Y coordinate
     * @param color    ARGB color
     * @param shadow   whether to draw a shadow
     * @param scale    font scale factor
     */
    public static void drawStringScaled(GuiGraphics graphics, @Nonnull Font font, @Nonnull String text,
                                         int x, int y, int color, boolean shadow, float scale) {
        if (scale <= 0f || Math.abs(scale - 1f) < 0.001f) {
            UIScaleManager.drawScaledString(graphics, font, text, x, y, color, shadow);
            return;
        }
        float inv = 1f / scale;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, Math.round(x * inv), Math.round(y * inv), color, shadow);
        graphics.pose().popPose();
    }

    /**
     * Clamps an integer value between min and max (inclusive).
     *
     * @param value the value to clamp
     * @param min   minimum bound
     * @param max   maximum bound
     * @return clamped value
     */
    public static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
