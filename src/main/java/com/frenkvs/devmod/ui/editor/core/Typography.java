package com.frenkvs.devmod.ui.editor.core;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/**
 * Typography constants and text rendering utilities for the editor.
 * Uses Minecraft's built-in font with scale multipliers.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#15-typography
 */
public final class Typography {
    private Typography() {}

    // =========================================================================
    // FONT SCALES
    // =========================================================================

    /** Section titles, dialog headers */
    public static final float TITLE = 1.0f;      // 8px

    /** Normal text, labels */
    public static final float BODY = 1.0f;       // 8px

    /** Small text, hints, secondary info */
    public static final float SMALL = 0.75f;     // 6px

    /** Tab labels */
    public static final float TAB = 0.875f;      // 7px

    /** Value display (numeric) */
    public static final float VALUE = 1.0f;      // 8px

    /** Line height multiplier */
    public static final float LINE_HEIGHT = 1.5f;

    /** Character spacing (0 = default) */
    public static final int LETTER_SPACING = 0;

    // =========================================================================
    // TEXT RENDERING
    // =========================================================================

    /**
     * Render scaled text with proper alignment.
     */
    public static void drawText(GuiGraphics g, Font font, String text,
                                int x, int y, int color, float scale) {
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
        if (scale == 1.0f) {
            g.drawString(font, text, x, y, color, false);
        } else {
            g.pose().pushPose();
            g.pose().scale(scale, scale, 1.0f);
            g.drawString(font, text,
                Math.round(x / scale),
                Math.round(y / scale),
                color, false);
            g.pose().popPose();
        }
    }

    /**
     * Render text with ellipsis if too long.
     */
    public static void drawTextWithEllipsis(GuiGraphics g, Font font, String text,
                                            int x, int y, int maxWidth, int color) {
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
        if (font.width(text) <= maxWidth) {
            g.drawString(font, text, x, y, color, false);
        } else {
            String ellipsis = "...";
            int ellipsisWidth = font.width(ellipsis);
            int availableWidth = maxWidth - ellipsisWidth;

            StringBuilder truncated = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (font.width(truncated.toString() + c) > availableWidth) {
                    break;
                }
                truncated.append(c);
            }

            g.drawString(font, truncated + ellipsis, x, y, color, false);
        }
    }

    /**
     * Render centered text.
     */
    public static void drawCenteredText(GuiGraphics g, Font font, String text,
                                        int centerX, int y, int color) {
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
        int textWidth = font.width(text);
        g.drawString(font, text, centerX - textWidth / 2, y, color, false);
    }

    /**
     * Render right-aligned text.
     */
    public static void drawRightAlignedText(GuiGraphics g, Font font, String text,
                                            int rightX, int y, int color) {
        Objects.requireNonNull(font, "font cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
        int textWidth = font.width(text);
        g.drawString(font, text, rightX - textWidth, y, color, false);
    }
}
