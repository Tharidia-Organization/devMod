package com.devmod.client.ui.editor.core;

import java.util.Objects;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import static com.devmod.client.ui.editor.core.StringBuilderCache.acquire;
import static com.devmod.client.ui.editor.core.StringBuilderCache.release;

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
    // DISCRETE FONT SCALING (Section 7 - UI Scaling)
    // =========================================================================

    /**
     * Discrete UI scales supported by the design system.
     */
    private enum DiscreteScale {
        SCALE_1_0(1.0f),
        SCALE_1_25(1.25f),
        SCALE_1_5(1.5f),
        SCALE_2_0(2.0f);

        final float value;

        DiscreteScale(float value) {
            this.value = value;
        }

        static DiscreteScale from(float uiScale) {
            // Pick the closest allowed scale (values are discrete)
            float closestDiff = Float.MAX_VALUE;
            DiscreteScale closest = SCALE_1_0;
            for (DiscreteScale ds : values()) {
                float diff = Math.abs(ds.value - uiScale);
                if (diff < closestDiff) {
                    closestDiff = diff;
                    closest = ds;
                }
            }
            return closest;
        }
    }

    /**
     * Helper to map a base pixel size to the expected scaled pixels from the spec table.
     * @param basePx base pixel height at 1.0x
     * @param px125  target pixels at 1.25x
     * @param px150  target pixels at 1.5x
     * @param px200  target pixels at 2.0x
     * @return multiplier to apply when rendering
     */
    private static float discretePixelFactor(float basePx, float px125, float px150, float px200) {
        DiscreteScale scale = DiscreteScale.from(Math.max(1.0f, ScaledCoord.getScale()));
        return switch (scale) {
            case SCALE_1_0 -> 1.0f;
            case SCALE_1_25 -> px125 / basePx;
            case SCALE_1_5 -> px150 / basePx;
            case SCALE_2_0 -> px200 / basePx;
        };
    }

    /**
     * Tab label scale per spec (9px → 11/14/18).
     */
    public static float tabLabelScale() {
        // base 9px at 1.0x, table in 07-ui-scaling.md
        return TAB * discretePixelFactor(9f, 11f, 14f, 18f);
    }

    /**
     * Section header scale per spec (10px → 12/15/20).
     */
    public static float sectionHeaderScale() {
        return TITLE * discretePixelFactor(10f, 12f, 15f, 20f);
    }

    /**
     * Value text scale per spec (8px → 10/12/16).
     */
    public static float valueScale() {
        return VALUE * discretePixelFactor(8f, 10f, 12f, 16f);
    }

    /**
     * Button text scale per spec (9px → 11/14/18).
     */
    public static float buttonScale() {
        return BODY * discretePixelFactor(9f, 11f, 14f, 18f);
    }

    /**
     * Apply UI scale to a base font scale (fallback linear scaling).
     * Prefer the discrete helpers above when matching the design table.
     */
    public static float withUiScale(float baseScale) {
        return baseScale * Math.max(1.0f, ScaledCoord.getScale());
    }

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
        if (Math.abs(scale - 1.0f) < 0.001f) {
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

            // Use StringBuilderCache to avoid allocations in render loop
            StringBuilder truncated = acquire();
            for (char c : text.toCharArray()) {
                if (font.width(truncated.toString() + c) > availableWidth) {
                    break;
                }
                truncated.append(c);
            }
            String result = release(truncated);

            g.drawString(font, result + ellipsis, x, y, color, false);
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
