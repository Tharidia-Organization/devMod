package com.devmod.client.ui.scroll;

import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Unified scrollbar rendering helper.
 *
 * <p>Provides consistent scrollbar appearance across all UI components.
 * Uses DesignTokens for colors and dimensions.
 *
 * <p>Standard dimensions (from DesignTokens.Component):
 * <ul>
 *   <li>Width: 8px (2 grid units)</li>
 *   <li>Min thumb height: 20px</li>
 *   <li>Track radius: 4px</li>
 * </ul>
 *
 * @see DesignTokens.Component#SCROLLBAR_WIDTH
 * @see ScrollManager for scroll state management
 */
public final class Scrollbar {

    private Scrollbar() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // DIMENSIONS (from DesignTokens)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Standard scrollbar width */
    public static final int WIDTH = DesignTokens.Component.SCROLLBAR_WIDTH;

    /** Minimum thumb height for usability */
    public static final int MIN_THUMB_HEIGHT = DesignTokens.Component.SCROLLBAR_MIN_THUMB;

    /** Track corner radius */
    public static final int RADIUS = DesignTokens.Radius.MD;

    // ═══════════════════════════════════════════════════════════════════════════
    // COLORS (from DesignTokens)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Track background color */
    public static final int TRACK_COLOR = DesignTokens.Bg.LEVEL_0;

    /** Thumb default color */
    public static final int THUMB_COLOR = DesignTokens.Stroke.DEFAULT;

    /** Thumb hover color */
    public static final int THUMB_HOVER = DesignTokens.Stroke.EMPHASIS;

    /** Thumb dragging/active color */
    public static final int THUMB_ACTIVE = DesignTokens.Accent.PRIMARY;

    // ═══════════════════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Render a vertical scrollbar.
     *
     * @param graphics      graphics context
     * @param x             x position (left edge of scrollbar)
     * @param y             y position (top of track)
     * @param height        total height of track
     * @param scrollOffset  current scroll offset (pixels)
     * @param contentHeight total content height
     * @param viewportHeight visible viewport height
     */
    public static void render(
            GuiGraphics graphics,
            int x, int y, int height,
            int scrollOffset, int contentHeight, int viewportHeight
    ) {
        render(graphics, x, y, WIDTH, height, scrollOffset, contentHeight, viewportHeight, false, false);
    }

    /**
     * Render a vertical scrollbar with state.
     *
     * @param graphics      graphics context
     * @param x             x position (left edge of scrollbar)
     * @param y             y position (top of track)
     * @param height        total height of track
     * @param scrollOffset  current scroll offset (pixels)
     * @param contentHeight total content height
     * @param viewportHeight visible viewport height
     * @param hovered       whether scrollbar is hovered
     * @param dragging      whether scrollbar is being dragged
     */
    public static void render(
            GuiGraphics graphics,
            int x, int y, int height,
            int scrollOffset, int contentHeight, int viewportHeight,
            boolean hovered, boolean dragging
    ) {
        render(graphics, x, y, WIDTH, height, scrollOffset, contentHeight, viewportHeight, hovered, dragging);
    }

    /**
     * Render a vertical scrollbar with custom width.
     *
     * @param graphics       graphics context
     * @param x              x position (left edge of scrollbar)
     * @param y              y position (top of track)
     * @param width          scrollbar width
     * @param height         total height of track
     * @param scrollOffset   current scroll offset (pixels)
     * @param contentHeight  total content height
     * @param viewportHeight visible viewport height
     * @param hovered        whether scrollbar is hovered
     * @param dragging       whether scrollbar is being dragged
     */
    public static void render(
            GuiGraphics graphics,
            int x, int y, int width, int height,
            int scrollOffset, int contentHeight, int viewportHeight,
            boolean hovered, boolean dragging
    ) {
        // Don't render if no scrolling needed
        if (contentHeight <= viewportHeight) {
            return;
        }

        // Calculate thumb dimensions
        float visibleRatio = (float) viewportHeight / contentHeight;
        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) (height * visibleRatio));

        // Calculate thumb position
        int maxScroll = contentHeight - viewportHeight;
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0f;
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);

        // Draw track
        graphics.fill(x, y, x + width, y + height, TRACK_COLOR);

        // Draw thumb
        int thumbColor = dragging ? THUMB_ACTIVE : (hovered ? THUMB_HOVER : THUMB_COLOR);
        graphics.fill(x, thumbY, x + width, thumbY + thumbHeight, thumbColor);
    }

    /**
     * Render a scrollbar using ScrollMetrics.
     *
     * @param graphics graphics context
     * @param x        x position
     * @param y        y position
     * @param width    scrollbar width
     * @param height   track height
     * @param metrics  calculated scroll metrics
     * @param hovered  whether hovered
     * @param dragging whether dragging
     */
    public static void render(
            GuiGraphics graphics,
            int x, int y, int width, int height,
            ScrollMetrics metrics,
            boolean hovered, boolean dragging
    ) {
        if (!metrics.isVisible()) {
            return;
        }

        // Draw track
        graphics.fill(x, y, x + width, y + height, TRACK_COLOR);

        // Draw thumb
        int thumbColor = dragging ? THUMB_ACTIVE : (hovered ? THUMB_HOVER : THUMB_COLOR);
        int thumbY = y + metrics.thumbY();
        int thumbHeight = metrics.thumbHeight();
        graphics.fill(x, thumbY, x + width, thumbY + thumbHeight, thumbColor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if scrollbar is needed.
     *
     * @param contentHeight  total content height
     * @param viewportHeight visible height
     * @return true if content exceeds viewport
     */
    public static boolean isNeeded(int contentHeight, int viewportHeight) {
        return contentHeight > viewportHeight;
    }

    /**
     * Calculate thumb height.
     *
     * @param trackHeight    height of the scrollbar track
     * @param contentHeight  total content height
     * @param viewportHeight visible viewport height
     * @return thumb height in pixels
     */
    public static int calculateThumbHeight(int trackHeight, int contentHeight, int viewportHeight) {
        if (contentHeight <= viewportHeight) {
            return trackHeight;
        }
        float ratio = (float) viewportHeight / contentHeight;
        return Math.max(MIN_THUMB_HEIGHT, (int) (trackHeight * ratio));
    }

    /**
     * Calculate thumb Y position.
     *
     * @param trackY         top of track
     * @param trackHeight    height of track
     * @param thumbHeight    height of thumb
     * @param scrollOffset   current scroll offset
     * @param maxScrollOffset maximum scroll offset
     * @return thumb Y position
     */
    public static int calculateThumbY(int trackY, int trackHeight, int thumbHeight, int scrollOffset, int maxScrollOffset) {
        if (maxScrollOffset <= 0) {
            return trackY;
        }
        float ratio = (float) scrollOffset / maxScrollOffset;
        return trackY + (int) ((trackHeight - thumbHeight) * ratio);
    }
}
