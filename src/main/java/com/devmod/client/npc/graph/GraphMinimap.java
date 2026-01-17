package com.devmod.client.npc.graph;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Minimap widget showing an overview of the entire graph.
 * Allows click-to-navigate to different areas of the graph.
 */
public class GraphMinimap extends AbstractWidget {

    /** Minimap colors */
    private static final int COLOR_BACKGROUND = DesignTokens.withAlpha(DesignTokens.Background.DARKER, DesignTokens.Alpha.A80);
    private static final int COLOR_BORDER = DesignTokens.Border.DEFAULT;
    private static final int COLOR_NODE = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_NODE_SELECTED = DesignTokens.Accent.BLUE;
    private static final int COLOR_VIEWPORT = DesignTokens.withAlpha(DesignTokens.Text.PRIMARY, DesignTokens.Alpha.A25);
    private static final int COLOR_VIEWPORT_BORDER = DesignTokens.Text.PRIMARY;

    private final GraphCanvas canvas;

    // Cached bounds
    private int graphMinX, graphMinY, graphMaxX, graphMaxY;
    private float scale = 1.0f;

    public GraphMinimap(int x, int y, int width, int height, @Nonnull GraphCanvas canvas) {
        super(x, y, width, height, Component.literal("Minimap"));
        this.canvas = canvas;
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Calculate graph bounds
        calculateBounds();

        // Background with border
        graphics.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, COLOR_BORDER);
        graphics.fill(getX(), getY(), getX() + width, getY() + height, COLOR_BACKGROUND);

        if (graphMinX == Integer.MAX_VALUE) {
            // No nodes
            return;
        }

        // Enable scissor
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);

        // Render connections as simple lines
        var selectedNodes = canvas.getSelectedNodes();
        // Note: We'd need access to connections from canvas - simplified version just shows nodes

        // Render nodes as dots/rectangles
        for (var entry : getCanvasNodes().entrySet()) {
            GraphNode node = entry.getValue();
            boolean selected = selectedNodes.contains(entry.getKey());

            int[] pos = graphToMinimap(node.getX(), node.getY());
            int[] size = graphToMinimapSize(node.getWidth(), node.getHeight());

            int color = selected ? COLOR_NODE_SELECTED : COLOR_NODE;
            graphics.fill(pos[0], pos[1], pos[0] + Math.max(size[0], 3), pos[1] + Math.max(size[1], 3), color);
        }

        // Render viewport rectangle
        renderViewport(graphics);

        graphics.disableScissor();
    }

    private void calculateBounds() {
        graphMinX = Integer.MAX_VALUE;
        graphMinY = Integer.MAX_VALUE;
        graphMaxX = Integer.MIN_VALUE;
        graphMaxY = Integer.MIN_VALUE;

        var nodes = getCanvasNodes();
        if (nodes.isEmpty()) return;

        for (GraphNode node : nodes.values()) {
            graphMinX = Math.min(graphMinX, node.getX());
            graphMinY = Math.min(graphMinY, node.getY());
            graphMaxX = Math.max(graphMaxX, node.getX() + node.getWidth());
            graphMaxY = Math.max(graphMaxY, node.getY() + node.getHeight());
        }

        // Add padding
        int padding = 50;
        graphMinX -= padding;
        graphMinY -= padding;
        graphMaxX += padding;
        graphMaxY += padding;

        // Calculate scale
        int graphWidth = graphMaxX - graphMinX;
        int graphHeight = graphMaxY - graphMinY;

        float scaleX = (float) width / graphWidth;
        float scaleY = (float) height / graphHeight;
        scale = Math.min(scaleX, scaleY);
    }

    private int[] graphToMinimap(int graphX, int graphY) {
        int x = getX() + (int) ((graphX - graphMinX) * scale);
        int y = getY() + (int) ((graphY - graphMinY) * scale);
        return new int[] { x, y };
    }

    private int[] graphToMinimapSize(int w, int h) {
        return new int[] { (int) (w * scale), (int) (h * scale) };
    }

    private void renderViewport(@Nonnull GuiGraphics graphics) {
        // Get canvas viewport in graph coordinates
        // Note: screenToGraph expects actual screen coordinates, not relative (0,0)
        // The canvas subtracts getX()/getY() internally, so we must pass actual positions
        int[] topLeft = canvas.screenToGraph(canvas.getX(), canvas.getY());
        int[] bottomRight = canvas.screenToGraph(
            canvas.getX() + canvas.getWidth(),
            canvas.getY() + canvas.getHeight()
        );

        // Convert to minimap coordinates
        int[] vpMin = graphToMinimap(topLeft[0], topLeft[1]);
        int[] vpMax = graphToMinimap(bottomRight[0], bottomRight[1]);

        // Clamp to minimap bounds
        int x1 = Math.max(getX(), Math.min(getX() + width, vpMin[0]));
        int y1 = Math.max(getY(), Math.min(getY() + height, vpMin[1]));
        int x2 = Math.max(getX(), Math.min(getX() + width, vpMax[0]));
        int y2 = Math.max(getY(), Math.min(getY() + height, vpMax[1]));

        // Draw viewport rectangle
        graphics.fill(x1, y1, x2, y2, COLOR_VIEWPORT);
        graphics.renderOutline(x1, y1, x2 - x1, y2 - y1, COLOR_VIEWPORT_BORDER);
    }

    // ========================================================================
    // Input
    // ========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        if (button == 0) {
            // Navigate to clicked position
            navigateTo((int) mouseX, (int) mouseY);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && isMouseOver(mouseX, mouseY)) {
            navigateTo((int) mouseX, (int) mouseY);
            return true;
        }
        return false;
    }

    private void navigateTo(int minimapX, int minimapY) {
        if (graphMinX == Integer.MAX_VALUE) return;

        // Convert minimap coordinates to graph coordinates
        int graphX = graphMinX + (int) ((minimapX - getX()) / scale);
        int graphY = graphMinY + (int) ((minimapY - getY()) / scale);

        // Center canvas on this position
        canvas.centerOnGraphPosition(graphX, graphY);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Gets nodes from the canvas.
     */
    private java.util.Map<String, GraphNode> getCanvasNodes() {
        return canvas.getNodes();
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        // No narration
    }
}
