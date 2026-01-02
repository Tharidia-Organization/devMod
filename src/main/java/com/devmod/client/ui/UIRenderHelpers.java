package com.devmod.client.ui;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.FocusRing;

/**
 * Advanced UI rendering helpers using DesignTokens.
 * Provides consistent, token-based rendering for all UI components.
 *
 * <p>All methods follow the UI Bible specifications:
 * <ul>
 *   <li>Grid-aligned dimensions (4px)</li>
 *   <li>Consistent color tokens</li>
 *   <li>Standard panel alpha (0xE0)</li>
 *   <li>Proper elevation/shadow support</li>
 * </ul>
 *
 * @see DesignTokens
 * @see AxiomRenderer for legacy compatibility
 */
public final class UIRenderHelpers {

    private UIRenderHelpers() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // PANEL RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw a panel with elevation shadow.
     *
     * @param graphics   The graphics context
     * @param x          X position
     * @param y          Y position
     * @param width      Panel width
     * @param height     Panel height
     * @param elevation  Elevation level (0-6), affects shadow
     * @param hasBorder  Whether to draw a border
     */
    public static void drawPanel(
            @Nonnull GuiGraphics graphics,
            int x, int y, int width, int height,
            int elevation,
            boolean hasBorder
    ) {
        // Apply shadow if elevated
        if (elevation > 0) {
            var elevSpec = DesignTokens.Elevation.level(elevation);
            int shadowAlpha = (int) (elevSpec.opacity() * 255);
            int shadowColor = (shadowAlpha << 24);
            int shadowOffset = elevSpec.yOffset();

            // Simple shadow: offset rectangle
            graphics.fill(
                    x + 2, y + shadowOffset + 2,
                    x + width + 2, y + height + shadowOffset + 2,
                    shadowColor
            );
        }

        // Background with standard panel alpha
        int bgColor = DesignTokens.withPanelAlpha(DesignTokens.Bg.LEVEL_2);
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border
        if (hasBorder) {
            drawBorder(graphics, x, y, width, height, DesignTokens.Stroke.DEFAULT, 1);
        }
    }

    /**
     * Draw a panel with header.
     *
     * @param graphics    The graphics context
     * @param font        Font for title
     * @param x           X position
     * @param y           Y position
     * @param width       Panel width
     * @param height      Panel height
     * @param title       Header title
     * @param elevation   Elevation level (0-6)
     */
    public static void drawPanelWithHeader(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            int x, int y, int width, int height,
            @Nonnull String title,
            int elevation
    ) {
        int headerHeight = 28;

        // Shadow
        if (elevation > 0) {
            var elevSpec = DesignTokens.Elevation.level(elevation);
            int shadowAlpha = (int) (elevSpec.opacity() * 255);
            int shadowColor = (shadowAlpha << 24);
            int shadowOffset = elevSpec.yOffset();

            graphics.fill(
                    x + 2, y + shadowOffset + 2,
                    x + width + 2, y + height + shadowOffset + 2,
                    shadowColor
            );
        }

        // Header
        int headerBg = DesignTokens.withPanelAlpha(DesignTokens.Bg.LEVEL_3);
        graphics.fill(x, y, x + width, y + headerHeight, headerBg);

        // Body
        int bodyBg = DesignTokens.withPanelAlpha(DesignTokens.Bg.LEVEL_2);
        graphics.fill(x, y + headerHeight, x + width, y + height, bodyBg);

        // Border
        drawBorder(graphics, x, y, width, height, DesignTokens.Stroke.DEFAULT, 1);

        // Header separator
        graphics.fill(x, y + headerHeight, x + width, y + headerHeight + 1, DesignTokens.Stroke.MUTED);

        // Title
        int textY = y + (headerHeight - 8) / 2;
        graphics.drawString(font, title, x + DesignTokens.Space._4, textY, DesignTokens.Text.PRIMARY, false);
    }

    /**
     * Draw a card (smaller elevated container).
     */
    public static void drawCard(
            @Nonnull GuiGraphics graphics,
            int x, int y, int width, int height
    ) {
        int bgColor = DesignTokens.withPanelAlpha(DesignTokens.Bg.LEVEL_3);
        graphics.fill(x, y, x + width, y + height, bgColor);
        drawBorder(graphics, x, y, width, height, DesignTokens.Stroke.MUTED, 1);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BORDER RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw a border around a rectangle.
     */
    public static void drawBorder(
            @Nonnull GuiGraphics graphics,
            int x, int y, int width, int height,
            int color, int thickness
    ) {
        // Top
        graphics.fill(x, y, x + width, y + thickness, color);
        // Bottom
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        // Left
        graphics.fill(x, y, x + thickness, y + height, color);
        // Right
        graphics.fill(x + width - thickness, y, x + width, y + height, color);
    }

    /**
     * Draw a focus ring around an element.
     *
     * @see FocusRing for the unified focus ring implementation
     */
    public static void drawFocusRing(
            @Nonnull GuiGraphics graphics,
            int x, int y, int width, int height
    ) {
        // Delegate to unified FocusRing helper
        FocusRing.renderSimple(graphics, x, y, width, height);
    }

    /**
     * Draw a separator line.
     */
    public static void drawSeparator(
            @Nonnull GuiGraphics graphics,
            int x, int y, int width,
            boolean horizontal
    ) {
        int color = DesignTokens.Stroke.MUTED;
        if (horizontal) {
            graphics.fill(x, y, x + width, y + 1, color);
        } else {
            graphics.fill(x, y, x + 1, y + width, color);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXT RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw text with optional shadow.
     */
    public static void drawText(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            @Nonnull String text,
            int x, int y,
            int color,
            boolean withShadow
    ) {
        if (withShadow) {
            int shadowColor = DesignTokens.darken(color, 0.6f) | 0xFF000000;
            graphics.drawString(font, text, x + 1, y + 1, shadowColor, false);
        }
        graphics.drawString(font, text, x, y, color, false);
    }

    /**
     * Draw text with outline (for HUD numbers).
     */
    public static void drawTextWithOutline(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            @Nonnull String text,
            int x, int y,
            int color,
            int outlineColor
    ) {
        // Draw outline in 8 directions
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    graphics.drawString(font, text, x + dx, y + dy, outlineColor, false);
                }
            }
        }
        graphics.drawString(font, text, x, y, color, false);
    }

    /**
     * Draw text with automatic truncation and ellipsis.
     *
     * @param graphics The graphics context
     * @param font     Font to use
     * @param text     Text to draw
     * @param x        X position
     * @param y        Y position
     * @param maxWidth Maximum width before truncation
     * @param color    Text color
     * @return The actual width of the drawn text
     */
    public static int drawTextTruncated(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            @Nonnull String text,
            int x, int y,
            int maxWidth,
            int color
    ) {
        int textWidth = font.width(text);

        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return textWidth;
        }

        // Truncate with ellipsis
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);

        if (maxWidth <= ellipsisWidth) {
            // Can't fit anything meaningful
            graphics.drawString(font, ellipsis.substring(0, Math.min(3, maxWidth / 3)), x, y, color, false);
            return maxWidth;
        }

        String truncated = font.plainSubstrByWidth(text, maxWidth - ellipsisWidth);
        graphics.drawString(font, truncated + ellipsis, x, y, color, false);
        return font.width(truncated + ellipsis);
    }

    /**
     * Draw centered text.
     */
    public static void drawCenteredText(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            @Nonnull String text,
            int centerX, int y,
            int color
    ) {
        int textWidth = font.width(text);
        graphics.drawString(font, text, centerX - textWidth / 2, y, color, false);
    }

    /**
     * Draw right-aligned text.
     */
    public static void drawRightAlignedText(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            @Nonnull String text,
            int rightX, int y,
            int color
    ) {
        int textWidth = font.width(text);
        graphics.drawString(font, text, rightX - textWidth, y, color, false);
    }

    /**
     * Draw a label-value pair.
     */
    public static void drawLabelValue(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            int x, int y, int width,
            @Nonnull String label,
            @Nonnull String value,
            int valueColor
    ) {
        graphics.drawString(font, label, x, y, DesignTokens.Text.SECONDARY, false);
        drawRightAlignedText(graphics, font, value, x + width, y, valueColor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOOLTIP RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw a tooltip at the specified position.
     *
     * @param graphics    The graphics context
     * @param font        Font for text
     * @param lines       Lines of text
     * @param mouseX      Mouse X for positioning
     * @param mouseY      Mouse Y for positioning
     * @param screenWidth Screen width for bounds checking
     * @param screenHeight Screen height for bounds checking
     */
    public static void drawTooltip(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            @Nonnull List<String> lines,
            int mouseX, int mouseY,
            int screenWidth, int screenHeight
    ) {
        if (lines.isEmpty()) return;

        int padding = DesignTokens.Space._3;
        int lineHeight = 10;
        int gap = 2;
        int maxWidth = DesignTokens.Component.TOOLTIP_MAX_WIDTH;

        // Calculate dimensions
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        textWidth = Math.min(textWidth, maxWidth);

        int tooltipWidth = textWidth + padding * 2;
        int tooltipHeight = padding * 2 + lines.size() * lineHeight + (lines.size() - 1) * gap;

        // Calculate position
        int x = mouseX + 12;
        int y = mouseY - 12;

        // Prevent overflow
        if (x + tooltipWidth > screenWidth - 8) {
            x = mouseX - tooltipWidth - 8;
        }
        if (y + tooltipHeight > screenHeight - 8) {
            y = screenHeight - 8 - tooltipHeight;
        }
        if (y < 8) {
            y = 8;
        }
        if (x < 8) {
            x = 8;
        }

        // Draw background with elevation
        drawPanel(graphics, x, y, tooltipWidth, tooltipHeight, 5, true);

        // Draw text
        int textY = y + padding;
        for (String line : lines) {
            graphics.drawString(font, line, x + padding, textY, DesignTokens.Text.PRIMARY, false);
            textY += lineHeight + gap;
        }
    }

    /**
     * Draw a simple single-line tooltip.
     */
    public static void drawSimpleTooltip(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            @Nonnull String text,
            int x, int y
    ) {
        int padding = DesignTokens.Space._2;
        int textWidth = font.width(text);
        int width = textWidth + padding * 2;
        int height = 12 + padding * 2;

        drawPanel(graphics, x, y, width, height, 5, true);
        graphics.drawString(font, text, x + padding, y + padding + 2, DesignTokens.Text.PRIMARY, false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUTTON RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Button state enum for rendering.
     */
    public enum ButtonState {
        DEFAULT, HOVER, PRESSED, DISABLED, FOCUSED
    }

    /**
     * Button style enum for different purposes.
     */
    public enum ButtonStyle {
        PRIMARY, SECONDARY, DANGER, SUCCESS, GHOST
    }

    /**
     * Draw a button.
     */
    public static void drawButton(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            int x, int y, int width, int height,
            @Nonnull String label,
            @Nonnull ButtonState state,
            @Nonnull ButtonStyle style
    ) {
        // Determine colors
        int bgColor = getButtonBgColor(style, state);
        int borderColor = getButtonBorderColor(style, state);
        int textColor = getButtonTextColor(style, state);

        // Background
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border
        drawBorder(graphics, x, y, width, height, borderColor, 1);

        // Focus ring
        if (state == ButtonState.FOCUSED) {
            drawFocusRing(graphics, x, y, width, height);
        }

        // Label (centered)
        int textWidth = font.width(label);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;

        // Offset when pressed for tactile feedback
        if (state == ButtonState.PRESSED) {
            textY += 1;
        }

        graphics.drawString(font, label, textX, textY, textColor, false);
    }

    private static int getButtonBgColor(ButtonStyle style, ButtonState state) {
        int baseColor = switch (style) {
            case PRIMARY -> DesignTokens.Accent.PRIMARY;
            case DANGER -> DesignTokens.Semantic.ERROR;
            case SUCCESS -> DesignTokens.Semantic.SUCCESS;
            case GHOST -> 0x00000000;
            default -> DesignTokens.Surface.LEVEL_1;
        };

        return switch (state) {
            case HOVER -> DesignTokens.lighten(baseColor, 0.1f);
            case PRESSED -> DesignTokens.darken(baseColor, 0.1f);
            case DISABLED -> DesignTokens.withAlpha(baseColor, 128);
            default -> baseColor;
        };
    }

    private static int getButtonBorderColor(ButtonStyle style, ButtonState state) {
        if (style == ButtonStyle.GHOST) {
            return state == ButtonState.HOVER ? DesignTokens.Stroke.DEFAULT : 0x00000000;
        }

        int baseColor = switch (style) {
            case PRIMARY -> DesignTokens.Accent.PRIMARY;
            case DANGER -> DesignTokens.Semantic.ERROR;
            case SUCCESS -> DesignTokens.Semantic.SUCCESS;
            default -> DesignTokens.Stroke.DEFAULT;
        };

        return switch (state) {
            case HOVER -> DesignTokens.lighten(baseColor, 0.1f);
            case DISABLED -> DesignTokens.withAlpha(baseColor, 128);
            default -> baseColor;
        };
    }

    private static int getButtonTextColor(ButtonStyle style, ButtonState state) {
        if (state == ButtonState.DISABLED) {
            return DesignTokens.Text.MUTED;
        }

        return switch (style) {
            case PRIMARY, DANGER, SUCCESS -> DesignTokens.Text.INVERSE;
            case GHOST -> DesignTokens.Text.SECONDARY;
            default -> DesignTokens.Text.PRIMARY;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROGRESS BAR RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw a progress bar.
     */
    public static void drawProgressBar(
            @Nonnull GuiGraphics graphics,
            int x, int y, int width, int height,
            float progress,
            int fillColor
    ) {
        // Background
        graphics.fill(x, y, x + width, y + height, DesignTokens.Surface.LEVEL_0);

        // Fill
        float clampedProgress = Math.max(0f, Math.min(1f, progress));
        int filledWidth = (int) (width * clampedProgress);
        if (filledWidth > 0) {
            graphics.fill(x, y, x + filledWidth, y + height, fillColor);
        }

        // Border
        drawBorder(graphics, x, y, width, height, DesignTokens.Stroke.MUTED, 1);
    }

    /**
     * Draw a health bar with color based on percentage.
     */
    public static void drawHealthBar(
            @Nonnull GuiGraphics graphics,
            int x, int y, int width, int height,
            float healthPercent
    ) {
        int color;
        if (healthPercent > 0.5f) {
            color = DesignTokens.Semantic.SUCCESS;
        } else if (healthPercent > 0.25f) {
            color = DesignTokens.Semantic.WARNING;
        } else {
            color = DesignTokens.Semantic.ERROR;
        }

        drawProgressBar(graphics, x, y, width, height, healthPercent, color);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOGGLE RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw an iOS-style toggle switch.
     */
    public static void drawToggle(
            @Nonnull GuiGraphics graphics,
            int x, int y,
            boolean enabled,
            boolean hovered
    ) {
        int width = DesignTokens.Component.TOGGLE_WIDTH;
        int height = DesignTokens.Component.TOGGLE_HEIGHT;
        int thumbSize = DesignTokens.Component.TOGGLE_THUMB_SIZE;
        int thumbMargin = (height - thumbSize) / 2;

        // Track color
        int trackColor;
        if (enabled) {
            trackColor = hovered
                    ? DesignTokens.lighten(DesignTokens.Semantic.SUCCESS, 0.1f)
                    : DesignTokens.Semantic.SUCCESS;
        } else {
            trackColor = hovered
                    ? DesignTokens.Surface.LEVEL_2
                    : DesignTokens.Surface.LEVEL_0;
        }

        // Draw track
        graphics.fill(x, y, x + width, y + height, trackColor);
        drawBorder(graphics, x, y, width, height, DesignTokens.Stroke.MUTED, 1);

        // Thumb position
        int thumbX = enabled ? x + width - thumbSize - thumbMargin : x + thumbMargin;

        // Draw thumb
        graphics.fill(thumbX, y + thumbMargin, thumbX + thumbSize, y + thumbMargin + thumbSize, DesignTokens.Text.PRIMARY);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BADGE RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draw a badge/chip.
     */
    public static void drawBadge(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            int x, int y,
            @Nonnull String text,
            int bgColor,
            int textColor
    ) {
        int padding = DesignTokens.Space._2;
        int textWidth = font.width(text);
        int width = textWidth + padding * 2;
        int height = 16;

        graphics.fill(x, y, x + width, y + height, bgColor);
        graphics.drawString(font, text, x + padding, y + 4, textColor, false);
    }

    /**
     * Draw a status badge with semantic color.
     */
    public static void drawStatusBadge(
            @Nonnull GuiGraphics graphics,
            @Nonnull Font font,
            int x, int y,
            @Nonnull String text,
            @Nonnull StatusType status
    ) {
        int bgColor = switch (status) {
            case SUCCESS -> DesignTokens.Semantic.SUCCESS_MUTED;
            case WARNING -> DesignTokens.Semantic.WARNING_MUTED;
            case ERROR -> DesignTokens.Semantic.ERROR_MUTED;
            case INFO -> DesignTokens.Semantic.INFO_MUTED;
        };

        int textColor = switch (status) {
            case SUCCESS -> DesignTokens.Semantic.SUCCESS;
            case WARNING -> DesignTokens.Semantic.WARNING;
            case ERROR -> DesignTokens.Semantic.ERROR;
            case INFO -> DesignTokens.Semantic.INFO;
        };

        drawBadge(graphics, font, x, y, text, bgColor, textColor);
    }

    public enum StatusType {
        SUCCESS, WARNING, ERROR, INFO
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if mouse is within bounds.
     */
    public static boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Draw a scrim (modal backdrop).
     */
    public static void drawScrim(@Nonnull GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, DesignTokens.Utility.SCRIM);
    }

    /**
     * Draw a selection highlight.
     */
    public static void drawSelection(@Nonnull GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, DesignTokens.Utility.SELECTION);
    }
}
