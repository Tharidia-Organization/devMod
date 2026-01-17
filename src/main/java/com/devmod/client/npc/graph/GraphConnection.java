package com.devmod.client.npc.graph;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Represents a connection (edge) between two nodes in the graph editor.
 * Renders as a Bezier curve with an optional arrow head.
 */
public record GraphConnection(
    @Nonnull String sourceNodeId,
    @Nonnull String targetNodeId,
    @Nonnull String optionId,
    int optionIndex
) {
    /** Connection colors */
    private static final int COLOR_NORMAL = DesignTokens.Text.MUTED;
    private static final int COLOR_HIGHLIGHTED = DesignTokens.Accent.BLUE;
    private static final int COLOR_CONDITIONAL = DesignTokens.Semantic.WARNING;
    /** Arrow size */
    private static final int ARROW_SIZE = 8;

    /** Bezier curve control point offset */
    private static final int BEZIER_OFFSET = 60;

    /**
     * Renders this connection between two nodes.
     *
     * @param graphics      The graphics context
     * @param source        The source node
     * @param target        The target node
     * @param highlighted   Whether this connection is highlighted
     * @param isConditional Whether this connection has a condition
     */
    public void render(
        @Nonnull GuiGraphics graphics,
        @Nonnull GraphNode source,
        @Nonnull GraphNode target,
        boolean highlighted,
        boolean isConditional
    ) {
        // Get connection endpoints
        int[] startPos = source.getOutputConnectorPosition(optionIndex);
        int[] endPos = target.getInputConnectorPosition();

        int x1 = startPos[0];
        int y1 = startPos[1];
        int x2 = endPos[0];
        int y2 = endPos[1];

        // Choose color
        int color = COLOR_NORMAL;
        if (highlighted) {
            color = COLOR_HIGHLIGHTED;
        } else if (isConditional) {
            color = COLOR_CONDITIONAL;
        }

        // Draw bezier curve
        drawBezierCurve(graphics, x1, y1, x2, y2, color);

        // Draw arrow at end
        drawArrow(graphics, x2, y2, x1 < x2 ? -1 : 1, color);
    }

    /**
     * Renders a connection to a specific point (for creating new connections).
     */
    public static void renderToPoint(
        @Nonnull GuiGraphics graphics,
        @Nonnull GraphNode source,
        int optionIdx,
        int targetX,
        int targetY
    ) {
        int[] startPos = source.getOutputConnectorPosition(optionIdx);
        int x1 = startPos[0];
        int y1 = startPos[1];

        drawBezierCurve(graphics, x1, y1, targetX, targetY, COLOR_HIGHLIGHTED);
    }

    /**
     * Draws a quadratic Bezier curve between two points.
     */
    private static void drawBezierCurve(
        @Nonnull GuiGraphics graphics,
        int x1, int y1,
        int x2, int y2,
        int color
    ) {
        // Calculate control points for smooth S-curve
        int dx = Math.abs(x2 - x1);
        int offset = Math.min(dx / 2, BEZIER_OFFSET);

        int cp1x = x1 + offset;
        int cp1y = y1;
        int cp2x = x2 - offset;
        int cp2y = y2;

        // Draw curve as line segments
        int segments = 20;
        int prevX = x1;
        int prevY = y1;

        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;

            // Cubic Bezier formula
            float mt = 1 - t;
            float mt2 = mt * mt;
            float mt3 = mt2 * mt;
            float t2 = t * t;
            float t3 = t2 * t;

            int x = (int) (mt3 * x1 + 3 * mt2 * t * cp1x + 3 * mt * t2 * cp2x + t3 * x2);
            int y = (int) (mt3 * y1 + 3 * mt2 * t * cp1y + 3 * mt * t2 * cp2y + t3 * y2);

            drawLine(graphics, prevX, prevY, x, y, color);
            prevX = x;
            prevY = y;
        }
    }

    /**
     * Draws a simple line between two points.
     */
    private static void drawLine(
        @Nonnull GuiGraphics graphics,
        int x1, int y1,
        int x2, int y2,
        int color
    ) {
        // Use fill for simple horizontal/vertical lines
        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            graphics.fill(x1 - 1, minY, x1 + 1, maxY, color);
        } else if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            graphics.fill(minX, y1 - 1, maxX, y1 + 1, color);
        } else {
            // For diagonal lines, draw a series of small rectangles
            int dx = x2 - x1;
            int dy = y2 - y1;
            int steps = Math.max(Math.abs(dx), Math.abs(dy));

            for (int i = 0; i <= steps; i++) {
                float t = steps == 0 ? 0 : (float) i / steps;
                int x = (int) (x1 + dx * t);
                int y = (int) (y1 + dy * t);
                graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
            }
        }
    }

    /**
     * Draws an arrow head at the specified position.
     */
    private static void drawArrow(
        @Nonnull GuiGraphics graphics,
        int x, int y,
        int direction,
        int color
    ) {
        // Simple triangular arrow pointing in the direction
        int size = ARROW_SIZE;
        int halfSize = size / 2;

        if (direction < 0) {
            // Arrow pointing left
            graphics.fill(x - size, y - halfSize, x, y + halfSize, color);
        } else {
            // Arrow pointing right
            graphics.fill(x, y - halfSize, x + size, y + halfSize, color);
        }
    }

    /**
     * Checks if a point is near this connection (for selection).
     *
     * @param source The source node
     * @param target The target node
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param threshold Distance threshold
     * @return true if the point is near the connection
     */
    public boolean isNear(
        @Nonnull GraphNode source,
        @Nonnull GraphNode target,
        int mouseX,
        int mouseY,
        int threshold
    ) {
        int[] startPos = source.getOutputConnectorPosition(optionIndex);
        int[] endPos = target.getInputConnectorPosition();

        // Sample points along the curve and check distance
        int segments = 10;
        int x1 = startPos[0], y1 = startPos[1];
        int x2 = endPos[0], y2 = endPos[1];

        int dx = Math.abs(x2 - x1);
        int offset = Math.min(dx / 2, BEZIER_OFFSET);
        int cp1x = x1 + offset, cp1y = y1;
        int cp2x = x2 - offset, cp2y = y2;

        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            float mt = 1 - t;
            float mt2 = mt * mt;
            float mt3 = mt2 * mt;
            float t2 = t * t;
            float t3 = t2 * t;

            int px = (int) (mt3 * x1 + 3 * mt2 * t * cp1x + 3 * mt * t2 * cp2x + t3 * x2);
            int py = (int) (mt3 * y1 + 3 * mt2 * t * cp1y + 3 * mt * t2 * cp2y + t3 * y2);

            double dist = Math.sqrt((mouseX - px) * (mouseX - px) + (mouseY - py) * (mouseY - py));
            if (dist < threshold) {
                return true;
            }
        }

        return false;
    }
}
