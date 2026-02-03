package com.devmod.client.ui.editor.components;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * A card container component for grouping related UI elements.
 * Provides consistent styling with background, border, and shadow.
 */
public final class EditorCard {

    private EditorCard() {} // Utility class

    /** Internal card padding */
    public static final int PADDING = 12;

    /** Card background color */
    private static final int CARD_BG = DesignTokens.Bg.LEVEL_3;

    /** Card border color */
    private static final int CARD_BORDER = DesignTokens.Stroke.MUTED;

    /** Shadow color with alpha */
    private static final int SHADOW_COLOR = DesignTokens.withAlpha(DesignTokens.Palette.SHADOW, 0x40);

    /** Shadow offset in pixels */
    private static final int SHADOW_OFFSET = 2;

    /**
     * Renders a card container with shadow, background, and border.
     *
     * @param graphics The graphics context
     * @param x Left edge X position
     * @param y Top edge Y position
     * @param width Card width
     * @param height Card height
     */
    public static void render(@Nonnull GuiGraphics graphics, int x, int y, int width, int height) {
        render(graphics, x, y, width, height, CARD_BG, CARD_BORDER);
    }

    /**
     * Renders a card container with custom colors.
     *
     * @param graphics The graphics context
     * @param x Left edge X position
     * @param y Top edge Y position
     * @param width Card width
     * @param height Card height
     * @param bgColor Background color
     * @param borderColor Border color
     */
    public static void render(@Nonnull GuiGraphics graphics, int x, int y, int width, int height,
                              int bgColor, int borderColor) {
        // Draw shadow (offset down-right)
        graphics.fill(x + SHADOW_OFFSET, y + SHADOW_OFFSET,
                      x + width + SHADOW_OFFSET, y + height + SHADOW_OFFSET,
                      SHADOW_COLOR);

        // Draw card background
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Draw border (1px)
        drawBorder(graphics, x, y, width, height, borderColor);
    }

    /**
     * Renders a card with accent border on the left side.
     *
     * @param graphics The graphics context
     * @param x Left edge X position
     * @param y Top edge Y position
     * @param width Card width
     * @param height Card height
     * @param accentColor Left border accent color
     */
    public static void renderWithAccent(@Nonnull GuiGraphics graphics, int x, int y, int width, int height,
                                        int accentColor) {
        render(graphics, x, y, width, height);

        // Draw left accent border (3px wide)
        int accentWidth = 3;
        graphics.fill(x, y, x + accentWidth, y + height, accentColor);
    }

    /**
     * Renders a card optimized for hover state (no shadow, brighter border).
     */
    public static void renderHovered(@Nonnull GuiGraphics graphics, int x, int y, int width, int height) {
        // Background
        graphics.fill(x, y, x + width, y + height, DesignTokens.Surface.LEVEL_2);

        // Accent border
        drawBorder(graphics, x, y, width, height, DesignTokens.Accent.PRIMARY);
    }

    /**
     * Draw a 1px border around a rectangle.
     */
    private static void drawBorder(@Nonnull GuiGraphics graphics, int x, int y, int width, int height, int color) {
        // Top
        graphics.fill(x, y, x + width, y + 1, color);
        // Bottom
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        // Left
        graphics.fill(x, y, x + 1, y + height, color);
        // Right
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    /**
     * Calculate the inner content bounds after applying padding.
     *
     * @param x Card X position
     * @param y Card Y position
     * @param width Card width
     * @param height Card height
     * @return Array of [innerX, innerY, innerWidth, innerHeight]
     */
    public static int[] getContentBounds(int x, int y, int width, int height) {
        return new int[] {
            x + PADDING,
            y + PADDING,
            width - PADDING * 2,
            height - PADDING * 2
        };
    }
}
