package com.devmod.ui;
import com.devmod.ui.editor.core.UIConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import javax.annotation.Nonnull;

/**
 * Utility class for rendering Axiom-style UI elements.
 * Provides consistent rendering methods for all mod screens.
 */
public final class AxiomRenderer {

    private AxiomRenderer() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // PANEL RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw a panel with header (legacy signature for compatibility)
     */
    public static void drawPanelWithHeader(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height,
                                           @Nonnull String title, int bgColor, int headerColor, int borderColor, int textColor) {
        int headerHeight = UIConstants.Spacing.HEADER_HEIGHT;

        // Header
        graphics.fill(x, y, x + width, y + headerHeight, headerColor);

        // Body
        graphics.fill(x, y + headerHeight, x + width, y + height, bgColor);

        // Border
        drawBorder(graphics, x, y, width, height, borderColor);
        graphics.fill(x, y + headerHeight, x + width, y + headerHeight + 1, borderColor);

        // Title
        graphics.drawString(font, title, x + UIConstants.Spacing.PANEL_PADDING, y + 6, textColor, false);
    }

    /**
     * Draw a panel with header using default colors
     */
    public static void drawPanel(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, @Nonnull String title) {
        drawPanelWithHeader(graphics, font, x, y, width, height, title,
            UIConstants.Background.PANEL(), UIConstants.Background.HEADER(),
            UIConstants.Border.DEFAULT(), UIConstants.Text.PRIMARY());
    }

    /**
     * Draw a simple panel without header
     */
    public static void drawSimplePanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.PANEL());
        drawBorder(graphics, x, y, width, height, UIConstants.Border.DEFAULT());
    }

    /**
     * Draw full-screen dark background
     */
    public static void drawScreenBackground(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, UIConstants.Background.SCREEN());
    }

    /**
     * Draw a tab button with selected state
     */
    public static void drawTab(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height,
                               @Nonnull String text, boolean selected, boolean hovered) {
        int bgColor = selected ? UIConstants.Background.ACTIVE() :
                      (hovered ? UIConstants.Background.HOVER() : UIConstants.Background.HEADER());
        int textColor = selected ? UIConstants.Text.ACCENT() : UIConstants.Text.PRIMARY();
        int borderColor = selected ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT();

        // Background
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Bottom border only for selected
        if (selected) {
            graphics.fill(x, y + height - 2, x + width, y + height, borderColor);
        }

        // Text (centered)
        int textWidth = font.width(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;
        graphics.drawString(font, text, textX, textY, textColor, false);
    }

    /**
     * Draw a toggle button (ON/OFF)
     */
    public static void drawToggle(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height,
                                  boolean enabled, boolean hovered) {
        int bgColor;
        if (enabled) {
            bgColor = hovered ? UIConstants.Toggle.ON_HOVER() : UIConstants.Toggle.ON();
        } else {
            bgColor = hovered ? UIConstants.Toggle.OFF_HOVER() : UIConstants.Toggle.OFF();
        }

        // Background
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border
        drawBorder(graphics, x, y, width, height, UIConstants.Border.DEFAULT());

        // Text
        String text = enabled ? "ON" : "OFF";
        int textColor = enabled ? UIConstants.Text.WHITE() : UIConstants.Text.MUTED();
        int textWidth = font.width(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;
        graphics.drawString(font, text, textX, textY, textColor, false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LABEL AND TEXT RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw a label (replaces disabled Button anti-pattern)
     */
    public static void drawLabel(GuiGraphics graphics, @Nonnull Font font, int x, int y, @Nonnull String text) {
        graphics.drawString(font, text, x, y, UIConstants.Text.PRIMARY(), false);
    }

    /**
     * Draw a label with custom color
     */
    public static void drawLabel(GuiGraphics graphics, @Nonnull Font font, int x, int y, @Nonnull String text, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    /**
     * Draw a section header
     */
    public static void drawSectionHeader(GuiGraphics graphics, @Nonnull Font font, int x, int y, @Nonnull String text) {
        graphics.drawString(font, text, x, y, UIConstants.Text.TITLE(), false);
    }

    /**
     * Draw a muted/hint text
     */
    public static void drawHint(GuiGraphics graphics, @Nonnull Font font, int x, int y, @Nonnull String text) {
        graphics.drawString(font, text, x, y, UIConstants.Text.MUTED(), false);
    }

    /**
     * Draw centered title
     */
    public static void drawCenteredTitle(GuiGraphics graphics, @Nonnull Font font, int screenWidth, int y, @Nonnull String title) {
        int textWidth = font.width(title);
        int x = (screenWidth - textWidth) / 2;
        graphics.drawString(font, title, x, y, UIConstants.Text.WHITE(), false);
    }

    /**
     * Draw a label-value pair (e.g., "Health: 20")
     */
    public static void drawLabelValue(GuiGraphics graphics, @Nonnull Font font, int x, int y,
                                      @Nonnull String label, @Nonnull String value) {
        graphics.drawString(font, label, x, y, UIConstants.Text.SECONDARY(), false);
        int labelWidth = font.width(label);
        graphics.drawString(font, value, x + labelWidth, y, UIConstants.Text.PRIMARY(), false);
    }

    /**
     * Draw a label-value pair with custom value color
     */
    public static void drawLabelValue(GuiGraphics graphics, @Nonnull Font font, int x, int y,
                                      @Nonnull String label, @Nonnull String value, int valueColor) {
        graphics.drawString(font, label, x, y, UIConstants.Text.SECONDARY(), false);
        int labelWidth = font.width(label);
        graphics.drawString(font, value, x + labelWidth, y, valueColor, false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INPUT FIELD RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw input field background (to be used alongside EditBox)
     */
    public static void drawInputBackground(GuiGraphics graphics, int x, int y, int width, int height, boolean focused) {
        int bgColor = UIConstants.Background.INPUT();
        int borderColor = focused ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT();

        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, borderColor);
        graphics.fill(x, y, x + width, y + height, bgColor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ROW RENDERING (for lists, settings)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw a row with label and toggle
     */
    public static void drawToggleRow(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                                     @Nonnull String label, boolean enabled, boolean hovered) {
        // Label on left
        graphics.drawString(font, label, x, y + 4, UIConstants.Text.PRIMARY(), false);

        // Toggle on right
        int toggleWidth = UIConstants.Size.TOGGLE_WIDTH;
        int toggleX = x + width - toggleWidth;
        drawToggle(graphics, font, toggleX, y, toggleWidth, UIConstants.Size.TOGGLE_HEIGHT, enabled, hovered);
    }

    /**
     * Draw a stat row (label: value)
     */
    public static void drawStatRow(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                                   @Nonnull String label, @Nonnull String value, int valueColor) {
        graphics.drawString(font, label, x, y, UIConstants.Text.SECONDARY(), false);
        int valueWidth = font.width(value);
        graphics.drawString(font, value, x + width - valueWidth, y, valueColor, false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DECORATIVE ELEMENTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw a separator line
     */
    public static void drawSeparator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, UIConstants.Border.SEPARATOR());
    }

    /**
     * Draw a border around a rectangle
     */
    public static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);                    // Top
        graphics.fill(x, y + height - 1, x + width, y + height, color);  // Bottom
        graphics.fill(x, y, x + 1, y + height, color);                   // Left
        graphics.fill(x + width - 1, y, x + width, y + height, color);   // Right
    }

    /**
     * Draw a health bar
     */
    public static void drawHealthBar(GuiGraphics graphics, int x, int y, int width, int height,
                                     float healthPercent) {
        int healthColor = UIConstants.getHealthColor(healthPercent);

        // Background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.INPUT());

        // Fill
        int filledWidth = (int) (width * (healthPercent / 100f));
        if (filledWidth > 0) {
            graphics.fill(x, y, x + filledWidth, y + height, healthColor);
        }

        // Border
        drawBorder(graphics, x, y, width, height, UIConstants.Border.DEFAULT());
    }

    /**
     * Draw a progress bar with custom color
     */
    public static void drawProgressBar(GuiGraphics graphics, int x, int y, int width, int height,
                                       float progress, int fillColor) {
        // Background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.INPUT());

        // Fill
        int filledWidth = (int) (width * Math.max(0f, Math.min(1f, progress)));
        if (filledWidth > 0) {
            graphics.fill(x, y, x + filledWidth, y + height, fillColor);
        }

        // Border
        drawBorder(graphics, x, y, width, height, UIConstants.Border.DEFAULT());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOOLTIP RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw a simple tooltip
     */
    public static void drawTooltip(GuiGraphics graphics, @Nonnull Font font, int x, int y, @Nonnull String text) {
        int padding = 4;
        int textWidth = font.width(text);
        int width = textWidth + padding * 2;
        int height = 12 + padding * 2;

        // Background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.TOOLTIP());

        // Border
        drawBorder(graphics, x, y, width, height, UIConstants.Border.LIGHT());

        // Text
        graphics.drawString(font, text, x + padding, y + padding + 2, UIConstants.Text.PRIMARY(), false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if mouse is within bounds
     */
    public static boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
