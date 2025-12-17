package com.frenkvs.devmod.ui.editor.debug;

import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility for detecting rendering issues like overflow, truncation, and misalignment.
 *
 * @see DebugWarning
 * @see WarningType
 * @see docs/editor-design-system/14-debug-overlay.md
 */
public final class OverflowDetector {

    private OverflowDetector() {} // Static utility class

    /**
     * Check if text will be truncated at given width.
     *
     * @param text     The text to check
     * @param x        X position
     * @param y        Y position
     * @param maxWidth Maximum allowed width
     * @param font     Font used for measuring
     * @return Optional warning if text exceeds maxWidth
     */
    public static Optional<DebugWarning> checkTextTruncation(
            String text, int x, int y, int maxWidth, Font font) {
        if (text == null || font == null || maxWidth <= 0) {
            return Optional.empty();
        }

        int textWidth = font.width(text);
        if (textWidth > maxWidth) {
            String preview = text.length() > 10 ? text.substring(0, 10) + "..." : text;
            return Optional.of(new DebugWarning(
                WarningType.TRUNCATED,
                "Text",
                "\"" + preview + "\" exceeds by " + (textWidth - maxWidth) + "px",
                x, y, maxWidth, font.lineHeight
            ));
        }
        return Optional.empty();
    }

    /**
     * Check if component exceeds viewport bounds.
     *
     * @param component  Component name
     * @param x          Component X position
     * @param y          Component Y position
     * @param width      Component width
     * @param height     Component height
     * @param viewportX  Viewport X position
     * @param viewportY  Viewport Y position
     * @param viewportW  Viewport width
     * @param viewportH  Viewport height
     * @return Optional warning if component exceeds viewport
     */
    public static Optional<DebugWarning> checkViewportOverflow(
            String component, int x, int y, int width, int height,
            int viewportX, int viewportY, int viewportW, int viewportH) {

        int overflowRight = (x + width) - (viewportX + viewportW);
        int overflowBottom = (y + height) - (viewportY + viewportH);
        int overflowLeft = viewportX - x;
        int overflowTop = viewportY - y;

        StringBuilder msg = new StringBuilder();
        if (overflowRight > 0) msg.append("right +").append(overflowRight).append("px ");
        if (overflowBottom > 0) msg.append("bottom +").append(overflowBottom).append("px ");
        if (overflowLeft > 0) msg.append("left +").append(overflowLeft).append("px ");
        if (overflowTop > 0) msg.append("top +").append(overflowTop).append("px ");

        if (msg.length() > 0) {
            return Optional.of(new DebugWarning(
                WarningType.OVERFLOW,
                component,
                msg.toString().trim(),
                x, y, width, height
            ));
        }
        return Optional.empty();
    }

    /**
     * Check if coordinate is aligned to 4px grid.
     *
     * @param component Component name
     * @param x         X position
     * @param y         Y position
     * @param width     Component width
     * @param height    Component height
     * @return Optional warning if any value is not on 4px grid
     */
    public static Optional<DebugWarning> checkAlignment(
            String component, int x, int y, int width, int height) {

        List<String> misaligned = new ArrayList<>();
        if (x % 4 != 0) misaligned.add("x=" + x);
        if (y % 4 != 0) misaligned.add("y=" + y);
        if (width % 4 != 0) misaligned.add("w=" + width);
        if (height % 4 != 0) misaligned.add("h=" + height);

        if (!misaligned.isEmpty()) {
            return Optional.of(new DebugWarning(
                WarningType.MISALIGNED,
                component,
                String.join(", ", misaligned) + " not on 4px grid",
                x, y, width, height
            ));
        }
        return Optional.empty();
    }

    /**
     * Check if component is completely outside the viewport.
     *
     * @param component  Component name
     * @param x          Component X position
     * @param y          Component Y position
     * @param width      Component width
     * @param height     Component height
     * @param viewportX  Viewport X position
     * @param viewportY  Viewport Y position
     * @param viewportW  Viewport width
     * @param viewportH  Viewport height
     * @return Optional warning if component is entirely outside viewport
     */
    public static Optional<DebugWarning> checkOutOfViewport(
            String component, int x, int y, int width, int height,
            int viewportX, int viewportY, int viewportW, int viewportH) {

        boolean outsideRight = x >= viewportX + viewportW;
        boolean outsideLeft = x + width <= viewportX;
        boolean outsideBottom = y >= viewportY + viewportH;
        boolean outsideTop = y + height <= viewportY;

        if (outsideRight || outsideLeft || outsideBottom || outsideTop) {
            String direction = outsideRight ? "right" :
                              outsideLeft ? "left" :
                              outsideBottom ? "below" : "above";
            return Optional.of(new DebugWarning(
                WarningType.OUT_OF_VIEWPORT,
                component,
                "rendered " + direction + " viewport",
                x, y, width, height
            ));
        }
        return Optional.empty();
    }

    /**
     * Run all checks and collect warnings.
     *
     * @param component  Component name
     * @param x          Component X position
     * @param y          Component Y position
     * @param width      Component width
     * @param height     Component height
     * @param viewportX  Viewport X position
     * @param viewportY  Viewport Y position
     * @param viewportW  Viewport width
     * @param viewportH  Viewport height
     * @return List of all detected warnings
     */
    public static List<DebugWarning> checkAll(
            String component, int x, int y, int width, int height,
            int viewportX, int viewportY, int viewportW, int viewportH) {

        List<DebugWarning> warnings = new ArrayList<>();

        checkAlignment(component, x, y, width, height).ifPresent(warnings::add);
        checkViewportOverflow(component, x, y, width, height,
            viewportX, viewportY, viewportW, viewportH).ifPresent(warnings::add);
        checkOutOfViewport(component, x, y, width, height,
            viewportX, viewportY, viewportW, viewportH).ifPresent(warnings::add);

        return warnings;
    }
}
