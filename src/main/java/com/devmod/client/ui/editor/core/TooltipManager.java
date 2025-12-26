package com.devmod.client.ui.editor.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;

/**
 * Centralized tooltip rendering manager.
 * Collects tooltips during the render pass and renders them at the end
 * to ensure they appear above all other UI elements.
 *
 * Usage:
 * 1. Call beginFrame() at the start of each render cycle
 * 2. Components register their tooltips via queueTooltip()
 * 3. Call renderAll() at the end of the render pass
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 4.2
 */
public final class TooltipManager {

    // ═══════════════════════════════════════════════════════════════
    // SINGLETON
    // ═══════════════════════════════════════════════════════════════

    public static final TooltipManager INSTANCE = new TooltipManager();

    private TooltipManager() {}

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    private static final int TOOLTIP_PADDING = 8;
    private static final int TOOLTIP_MAX_WIDTH = 220;
    private static final int LINE_HEIGHT = 10;
    private static final int TOOLTIP_MARGIN = 4;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private final List<QueuedTooltip> tooltipQueue = new ArrayList<>();
    private int screenWidth;
    private int screenHeight;

    // Pinned tooltip - persists until clicked again or dismissed
    private QueuedTooltip pinnedTooltip = null;

    /**
     * Queued tooltip data.
     */
    public record QueuedTooltip(
        String text,
        int anchorX,
        int anchorY,
        int anchorWidth,
        int anchorHeight,
        TooltipPosition position
    ) {}

    /**
     * Tooltip positioning preference.
     */
    public enum TooltipPosition {
        ABOVE,      // Show above the anchor
        BELOW,      // Show below the anchor
        LEFT,       // Show to the left of the anchor
        RIGHT,      // Show to the right of the anchor
        AUTO        // Automatically determine best position
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Begin a new render frame. Clears the tooltip queue.
     * Call this at the start of each screen render.
     *
     * @param screenWidth  Current screen width
     * @param screenHeight Current screen height
     */
    public void beginFrame(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        tooltipQueue.clear();
    }

    /**
     * Queue a tooltip for rendering at the end of the frame.
     *
     * @param text         The tooltip text (will be word-wrapped)
     * @param anchorX      X position of the anchor element
     * @param anchorY      Y position of the anchor element
     * @param anchorWidth  Width of the anchor element
     * @param anchorHeight Height of the anchor element
     * @param position     Preferred position for the tooltip
     */
    public void queueTooltip(String text, int anchorX, int anchorY,
                             int anchorWidth, int anchorHeight,
                             TooltipPosition position) {
        if (text == null || text.isEmpty()) return;
        tooltipQueue.add(new QueuedTooltip(text, anchorX, anchorY, anchorWidth, anchorHeight, position));
    }

    /**
     * Queue a tooltip with AUTO positioning.
     */
    public void queueTooltip(String text, int anchorX, int anchorY, int anchorWidth, int anchorHeight) {
        queueTooltip(text, anchorX, anchorY, anchorWidth, anchorHeight, TooltipPosition.AUTO);
    }

    /**
     * Pin a tooltip so it stays visible until dismissed.
     * If the same tooltip is already pinned, it will be unpinned (toggle behavior).
     */
    public void pinTooltip(String text, int anchorX, int anchorY,
                           int anchorWidth, int anchorHeight,
                           TooltipPosition position) {
        if (text == null || text.isEmpty()) return;

        // Toggle: if same text is already pinned, unpin it
        if (pinnedTooltip != null && pinnedTooltip.text().equals(text)) {
            pinnedTooltip = null;
            return;
        }

        pinnedTooltip = new QueuedTooltip(text, anchorX, anchorY, anchorWidth, anchorHeight, position);
    }

    /**
     * Dismiss any pinned tooltip.
     */
    public void dismissPinned() {
        pinnedTooltip = null;
    }

    /**
     * Check if there's a pinned tooltip.
     */
    public boolean hasPinnedTooltip() {
        return pinnedTooltip != null;
    }

    /**
     * Render all queued tooltips.
     * Call this at the END of the screen render, after all other UI elements.
     *
     * @param graphics The graphics context
     */
    public void renderAll(GuiGraphics graphics) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        // Render queued (hover) tooltips
        for (QueuedTooltip tooltip : tooltipQueue) {
            renderTooltip(graphics, font, tooltip);
        }

        // Render pinned tooltip (takes precedence visually, rendered last)
        if (pinnedTooltip != null) {
            renderTooltip(graphics, font, pinnedTooltip);
        }
    }

    /**
     * Check if there are any tooltips queued for rendering.
     */
    public boolean hasQueuedTooltips() {
        return !tooltipQueue.isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    private void renderTooltip(GuiGraphics graphics, Font font, QueuedTooltip tooltip) {
        // Wrap text
        List<String> lines = wrapText(font, tooltip.text(), TOOLTIP_MAX_WIDTH);
        if (lines.isEmpty()) return;

        // Calculate dimensions
        int padding = ScaledCoord.scaleDim(TOOLTIP_PADDING);
        int lineH = ScaledCoord.scaleDim(LINE_HEIGHT);
        int margin = ScaledCoord.scaleDim(TOOLTIP_MARGIN);

        int maxLineWidth = 0;
        for (String line : lines) {
            String safeLine = Objects.requireNonNull(line, "line");
            maxLineWidth = Math.max(maxLineWidth, font.width(safeLine));
        }

        int tooltipWidth = maxLineWidth + padding * 2;
        int tooltipHeight = lines.size() * lineH + padding * 2;

        // Calculate position
        int[] pos = calculatePosition(tooltip, tooltipWidth, tooltipHeight, margin);
        int tooltipX = pos[0];
        int tooltipY = pos[1];

        // Draw background
        graphics.fill(tooltipX, tooltipY,
                      tooltipX + tooltipWidth, tooltipY + tooltipHeight,
                      UIConstants.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, tooltipX, tooltipY,
                                  tooltipWidth, tooltipHeight,
                                  UIConstants.Border.ACCENT());

        // Draw text lines
        float textScale = Typography.withUiScale(Typography.BODY);
        int textY = tooltipY + padding;
        for (String line : lines) {
            Typography.drawText(graphics, font, Objects.requireNonNull(line, "line cannot be null"),
                               tooltipX + padding, textY,
                               UIConstants.Text.PRIMARY(), textScale);
            textY += lineH;
        }
    }

    private int[] calculatePosition(QueuedTooltip tooltip, int tooltipWidth, int tooltipHeight, int margin) {
        int anchorCenterX = tooltip.anchorX() + tooltip.anchorWidth() / 2;
        int anchorCenterY = tooltip.anchorY() + tooltip.anchorHeight() / 2;

        int x, y;
        TooltipPosition pos = tooltip.position();

        if (pos == TooltipPosition.AUTO) {
            // Prefer above, fall back to below if not enough space
            if (tooltip.anchorY() - tooltipHeight - margin >= margin) {
                pos = TooltipPosition.ABOVE;
            } else {
                pos = TooltipPosition.BELOW;
            }
        }

        switch (pos) {
            case ABOVE -> {
                x = anchorCenterX - tooltipWidth / 2;
                y = tooltip.anchorY() - tooltipHeight - margin;
            }
            case BELOW -> {
                x = anchorCenterX - tooltipWidth / 2;
                y = tooltip.anchorY() + tooltip.anchorHeight() + margin;
            }
            case LEFT -> {
                x = tooltip.anchorX() - tooltipWidth - margin;
                y = anchorCenterY - tooltipHeight / 2;
            }
            case RIGHT -> {
                x = tooltip.anchorX() + tooltip.anchorWidth() + margin;
                y = anchorCenterY - tooltipHeight / 2;
            }
            default -> {
                x = anchorCenterX - tooltipWidth / 2;
                y = tooltip.anchorY() - tooltipHeight - margin;
            }
        }

        // Clamp to screen bounds
        x = Math.max(margin, Math.min(x, screenWidth - tooltipWidth - margin));
        y = Math.max(margin, Math.min(y, screenHeight - tooltipHeight - margin));

        return new int[] { x, y };
    }

    private List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0
                ? word
                : currentLine + " " + word;

            if (font.width(Objects.requireNonNull(testLine, "testLine")) <= maxWidth) {
                currentLine = new StringBuilder(testLine);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
}
