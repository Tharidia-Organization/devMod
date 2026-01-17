package com.devmod.client.npc.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.dialog.DialogNode;
import com.devmod.npc.dialog.DialogOption;
import com.devmod.npc.dialog.DialogSet;

/**
 * Canvas widget for rendering and interacting with the dialog graph.
 * Supports pan, zoom, node selection, and drag operations.
 */
public class GraphCanvas extends AbstractWidget {

    /** Zoom limits */
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 2.0f;
    private static final float ZOOM_STEP = 0.1f;

    /** Grid settings */
    private static final int GRID_SIZE = 50;
    private static final int GRID_COLOR = DesignTokens.withAlpha(DesignTokens.Border.MUTED, DesignTokens.Alpha.A25);
    private static final int BACKGROUND_COLOR = DesignTokens.Background.DARKER;
    private static final long DOUBLE_CLICK_THRESHOLD_MS = 250;

    // Graph data
    private final Map<String, GraphNode> nodes = new HashMap<>();
    private final List<GraphConnection> connections = new ArrayList<>();
    private String entryNodeId = "";

    // View state
    private float zoom = 1.0f;
    private int panX = 0;
    private int panY = 0;
    private boolean panning = false;
    private int panStartX, panStartY;
    private int panStartPanX, panStartPanY;

    // Selection
    private final Set<String> selectedNodes = new HashSet<>();
    private boolean boxSelecting = false;
    private int selectionStartX, selectionStartY;
    private int selectionEndX, selectionEndY;

    // Dragging
    @Nullable
    private GraphNode draggingNode = null;

    // Creating connection
    @Nullable
    private GraphNode connectionSource = null;
    private int connectionOptionIndex = -1;

    // Callbacks
    @Nullable
    private Consumer<String> onNodeSelected;
    @Nullable
    private Consumer<String> onNodeDoubleClicked;
    @Nullable
    private Runnable onSelectionChanged;

    @Nullable
    private String lastClickedNodeId;
    private long lastClickTime;

    // Layout state
    private boolean layoutRunning = false;
    private int layoutIterations = 0;

    public GraphCanvas(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    // ========================================================================
    // Data Management
    // ========================================================================

    /**
     * Loads a dialog set into the canvas.
     */
    public void loadDialogSet(@Nonnull DialogSet dialogSet) {
        nodes.clear();
        connections.clear();
        selectedNodes.clear();
        entryNodeId = dialogSet.entryNodeId();

        // Create nodes
        int index = 0;
        for (Map.Entry<String, DialogNode> entry : dialogSet.nodes().entrySet()) {
            String nodeId = entry.getKey();
            DialogNode dialogNode = entry.getValue();

            // Initial position in grid
            int col = index % 4;
            int row = index / 4;
            int x = 50 + col * 200;
            int y = 50 + row * 150;

            GraphNode graphNode = new GraphNode(nodeId, dialogNode, x, y);
            graphNode.setEntryNode(nodeId.equals(entryNodeId));
            nodes.put(nodeId, graphNode);

            index++;
        }

        // Create connections
        for (GraphNode node : nodes.values()) {
            for (GraphNode.ConnectionInfo info : node.getConnectionInfos()) {
                if (nodes.containsKey(info.targetNodeId())) {
                    connections.add(new GraphConnection(
                        node.getNodeId(),
                        info.targetNodeId(),
                        info.optionId(),
                        info.optionIndex()
                    ));
                }
            }
        }

        // Apply initial layout
        if (!nodes.isEmpty()) {
            hierarchicalLayout();
        }

        centerView();
    }

    /**
     * Gets the currently selected node IDs.
     */
    @Nonnull
    public Set<String> getSelectedNodes() {
        return Set.copyOf(selectedNodes);
    }

    /**
     * Gets all nodes in the graph.
     */
    @Nonnull
    public Map<String, GraphNode> getNodes() {
        return Map.copyOf(nodes);
    }

    /**
     * Gets all connections in the graph.
     */
    @Nonnull
    public List<GraphConnection> getConnections() {
        return List.copyOf(connections);
    }

    /**
     * Gets a single selected node, or null if none/multiple selected.
     */
    @Nullable
    public String getSingleSelectedNode() {
        return selectedNodes.size() == 1 ? selectedNodes.iterator().next() : null;
    }

    // ========================================================================
    // View Controls
    // ========================================================================

    public float getZoom() {
        return zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    public void zoomIn() {
        setZoom(zoom + ZOOM_STEP);
    }

    public void zoomOut() {
        setZoom(zoom - ZOOM_STEP);
    }

    public void resetZoom() {
        setZoom(1.0f);
    }

    /**
     * Centers the view on all nodes.
     */
    public void centerView() {
        if (nodes.isEmpty()) {
            panX = 0;
            panY = 0;
            return;
        }

        // Calculate bounding box
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (GraphNode node : nodes.values()) {
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + node.getWidth());
            maxY = Math.max(maxY, node.getY() + node.getHeight());
        }

        // Center pan
        int graphCenterX = (minX + maxX) / 2;
        int graphCenterY = (minY + maxY) / 2;

        panX = width / 2 - (int) (graphCenterX * zoom);
        panY = height / 2 - (int) (graphCenterY * zoom);
    }

    /**
     * Centers the view on a specific node.
     */
    public void centerOnNode(@Nonnull String nodeId) {
        GraphNode node = nodes.get(nodeId);
        if (node == null) return;

        panX = width / 2 - (int) (node.getCenterX() * zoom);
        panY = height / 2 - (int) (node.getCenterY() * zoom);
    }

    /**
     * Centers the view on specific graph coordinates.
     * Used by the minimap for click-to-navigate.
     *
     * @param graphX X coordinate in graph space
     * @param graphY Y coordinate in graph space
     */
    public void centerOnGraphPosition(int graphX, int graphY) {
        panX = width / 2 - (int) (graphX * zoom);
        panY = height / 2 - (int) (graphY * zoom);
    }

    /**
     * Adjusts zoom to fit all nodes in view.
     */
    public void fitToScreen() {
        if (nodes.isEmpty()) return;

        // Calculate bounding box
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (GraphNode node : nodes.values()) {
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + node.getWidth());
            maxY = Math.max(maxY, node.getY() + node.getHeight());
        }

        int graphWidth = maxX - minX + 100;
        int graphHeight = maxY - minY + 100;

        // Calculate zoom to fit
        float zoomX = (float) width / graphWidth;
        float zoomY = (float) height / graphHeight;
        setZoom(Math.min(zoomX, zoomY));

        centerView();
    }

    // ========================================================================
    // Layout
    // ========================================================================

    /**
     * Starts the force-directed layout animation.
     */
    public void startAutoLayout() {
        // First apply circular layout as starting point
        ForceDirectedLayout.circularLayout(
            nodes,
            width / 2,
            height / 2,
            Math.min(width, height) / 3
        );
        layoutRunning = true;
        layoutIterations = 0;
    }

    /**
     * Applies hierarchical layout immediately.
     */
    public void hierarchicalLayout() {
        ForceDirectedLayout.hierarchicalLayout(
            nodes,
            connections,
            entryNodeId,
            100,
            height / 2
        );
    }

    // ========================================================================
    // Selection
    // ========================================================================

    /**
     * Selects a single node.
     */
    public void selectNode(@Nonnull String nodeId) {
        selectedNodes.clear();
        selectedNodes.add(nodeId);
        updateNodeSelectionState();
        notifySelectionChanged();
    }

    /**
     * Toggles selection of a node.
     */
    public void toggleNodeSelection(@Nonnull String nodeId) {
        if (selectedNodes.contains(nodeId)) {
            selectedNodes.remove(nodeId);
        } else {
            selectedNodes.add(nodeId);
        }
        updateNodeSelectionState();
        notifySelectionChanged();
    }

    /**
     * Clears all selection.
     */
    public void clearSelection() {
        selectedNodes.clear();
        updateNodeSelectionState();
        notifySelectionChanged();
    }

    /**
     * Selects all nodes.
     */
    public void selectAll() {
        selectedNodes.addAll(nodes.keySet());
        updateNodeSelectionState();
        notifySelectionChanged();
    }

    private void updateNodeSelectionState() {
        for (Map.Entry<String, GraphNode> entry : nodes.entrySet()) {
            entry.getValue().setSelected(selectedNodes.contains(entry.getKey()));
        }
    }

    private void notifySelectionChanged() {
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
        String single = getSingleSelectedNode();
        if (single != null && onNodeSelected != null) {
            onNodeSelected.accept(single);
        }
    }

    // ========================================================================
    // Coordinate Conversion
    // ========================================================================

    /**
     * Converts screen coordinates to graph coordinates.
     */
    public int[] screenToGraph(int screenX, int screenY) {
        int graphX = (int) ((screenX - getX() - panX) / zoom);
        int graphY = (int) ((screenY - getY() - panY) / zoom);
        return new int[] { graphX, graphY };
    }

    /**
     * Converts graph coordinates to screen coordinates.
     */
    public int[] graphToScreen(int graphX, int graphY) {
        int screenX = (int) (graphX * zoom + panX + getX());
        int screenY = (int) (graphY * zoom + panY + getY());
        return new int[] { screenX, screenY };
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Run layout iteration if active
        if (layoutRunning) {
            layoutIterations++;
            boolean stable = ForceDirectedLayout.iterate(
                nodes,
                connections,
                (int) ((width / 2 - panX) / zoom),
                (int) ((height / 2 - panY) / zoom)
            );
            if (stable || layoutIterations > 200) {
                layoutRunning = false;
            }
        }

        // Enable scissor for canvas bounds
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, BACKGROUND_COLOR);

        // Grid
        renderGrid(graphics);

        // Apply transform for zoom/pan
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + panX, getY() + panY, 0);
        graphics.pose().scale(zoom, zoom, 1);

        // Convert mouse to graph coordinates
        int[] graphMouse = screenToGraph(mouseX, mouseY);

        // Render connections
        for (GraphConnection conn : connections) {
            GraphNode source = nodes.get(conn.sourceNodeId());
            GraphNode target = nodes.get(conn.targetNodeId());

            if (source != null && target != null) {
                boolean highlighted = selectedNodes.contains(conn.sourceNodeId())
                    || selectedNodes.contains(conn.targetNodeId());

                // Check if source option has a condition
                DialogOption option = source.getDialogNode().getOption(conn.optionId()).orElse(null);
                boolean isConditional = option != null && option.showCondition() != null;

                conn.render(graphics, source, target, highlighted, isConditional);
            }
        }

        // Render connection being created
        if (connectionSource != null && connectionOptionIndex >= 0) {
            GraphConnection.renderToPoint(
                graphics,
                connectionSource,
                connectionOptionIndex,
                graphMouse[0],
                graphMouse[1]
            );
        }

        // Render nodes
        for (GraphNode node : nodes.values()) {
            node.render(graphics, graphMouse[0], graphMouse[1], zoom);
        }

        graphics.pose().popPose();

        // Selection box
        if (boxSelecting) {
            int minX = Math.min(selectionStartX, selectionEndX);
            int minY = Math.min(selectionStartY, selectionEndY);
            int maxX = Math.max(selectionStartX, selectionEndX);
            int maxY = Math.max(selectionStartY, selectionEndY);

            int selectionFill = DesignTokens.withAlpha(DesignTokens.Accent.BLUE, DesignTokens.Alpha.A25);
            graphics.fill(minX, minY, maxX, maxY, selectionFill);
            graphics.renderOutline(minX, minY, maxX - minX, maxY - minY, DesignTokens.Accent.BLUE);
        }

        graphics.disableScissor();
    }

    private void renderGrid(@Nonnull GuiGraphics graphics) {
        int scaledGridSize = (int) (GRID_SIZE * zoom);
        if (scaledGridSize < 10) return; // Don't draw grid when zoomed out too far

        int startX = (-panX % scaledGridSize) + getX();
        int startY = (-panY % scaledGridSize) + getY();

        for (int x = startX; x < getX() + width; x += scaledGridSize) {
            graphics.fill(x, getY(), x + 1, getY() + height, GRID_COLOR);
        }

        for (int y = startY; y < getY() + height; y += scaledGridSize) {
            graphics.fill(getX(), y, getX() + width, y + 1, GRID_COLOR);
        }
    }

    // ========================================================================
    // Input Handling
    // ========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        int[] graphPos = screenToGraph((int) mouseX, (int) mouseY);
        int gx = graphPos[0], gy = graphPos[1];

        if (button == 0) { // Left click
            // Check for node click
            for (GraphNode node : nodes.values()) {
                if (node.isMouseOver(gx, gy)) {
                    // Check for connector click
                    int connectorIndex = node.getOutputConnectorAt(gx, gy);
                    if (connectorIndex >= 0) {
                        connectionSource = node;
                        connectionOptionIndex = connectorIndex;
                        return true;
                    }

                    // Node selection
                    if (Screen.hasShiftDown()) {
                        toggleNodeSelection(node.getNodeId());
                    } else {
                        if (!selectedNodes.contains(node.getNodeId())) {
                            selectNode(node.getNodeId());
                        }
                        // Start dragging
                        draggingNode = node;
                        node.startDrag(gx, gy);
                    }
                    handleNodeClick(node.getNodeId());
                    return true;
                }
            }

            // Click on empty space - start box selection or clear selection
            if (!Screen.hasShiftDown()) {
                clearSelection();
            }
            boxSelecting = true;
            selectionStartX = (int) mouseX;
            selectionStartY = (int) mouseY;
            selectionEndX = selectionStartX;
            selectionEndY = selectionStartY;

        } else if (button == 1) { // Right click - start panning
            panning = true;
            panStartX = (int) mouseX;
            panStartY = (int) mouseY;
            panStartPanX = panX;
            panStartPanY = panY;
        }

        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int[] graphPos = screenToGraph((int) mouseX, (int) mouseY);
        int gx = graphPos[0], gy = graphPos[1];

        if (button == 0) {
            // End node dragging
            if (draggingNode != null) {
                draggingNode.stopDrag();
                draggingNode = null;
            }

            // End connection creation
            if (connectionSource != null) {
                // Check if dropped on a node
                for (GraphNode node : nodes.values()) {
                    if (node.isMouseOver(gx, gy) && node != connectionSource) {
                        // TODO: Create connection (would need to modify DialogSet)
                        break;
                    }
                }
                connectionSource = null;
                connectionOptionIndex = -1;
            }

            // End box selection
            if (boxSelecting) {
                // Select all nodes in box
                int minX = Math.min(selectionStartX, selectionEndX);
                int minY = Math.min(selectionStartY, selectionEndY);
                int maxX = Math.max(selectionStartX, selectionEndX);
                int maxY = Math.max(selectionStartY, selectionEndY);

                for (Map.Entry<String, GraphNode> entry : nodes.entrySet()) {
                    GraphNode node = entry.getValue();
                    int[] nodeScreen = graphToScreen(node.getX(), node.getY());
                    int[] nodeEndScreen = graphToScreen(
                        node.getX() + node.getWidth(),
                        node.getY() + node.getHeight()
                    );

                    if (nodeScreen[0] >= minX && nodeEndScreen[0] <= maxX
                        && nodeScreen[1] >= minY && nodeEndScreen[1] <= maxY) {
                        selectedNodes.add(entry.getKey());
                    }
                }
                updateNodeSelectionState();
                notifySelectionChanged();

                boxSelecting = false;
            }

        } else if (button == 1) {
            panning = false;
        }

        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int[] graphPos = screenToGraph((int) mouseX, (int) mouseY);
        int gx = graphPos[0], gy = graphPos[1];

        if (button == 0) {
            // Node dragging
            if (draggingNode != null) {
                draggingNode.drag(gx, gy);
                return true;
            }

            // Box selection
            if (boxSelecting) {
                selectionEndX = (int) mouseX;
                selectionEndY = (int) mouseY;
                return true;
            }
        }

        if (button == 1 && panning) {
            // Panning
            panX = panStartPanX + ((int) mouseX - panStartX);
            panY = panStartPanY + ((int) mouseY - panStartY);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        // Zoom towards mouse position
        float oldZoom = zoom;
        if (scrollY > 0) {
            zoomIn();
        } else {
            zoomOut();
        }

        // Adjust pan to keep mouse position stable
        if (zoom != oldZoom) {
            float zoomDelta = zoom / oldZoom;
            int mx = (int) mouseX - getX();
            int my = (int) mouseY - getY();
            panX = (int) (mx - (mx - panX) * zoomDelta);
            panY = (int) (my - (my - panY) * zoomDelta);
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+A - select all
        if (Screen.hasControlDown() && keyCode == 65) {
            selectAll();
            return true;
        }

        // Delete - delete selected nodes
        if (keyCode == 261 && !selectedNodes.isEmpty()) {
            // TODO: Delete nodes (would need to modify DialogSet)
            return true;
        }

        // F - fit to screen
        if (keyCode == 70) {
            fitToScreen();
            return true;
        }

        // C - center view
        if (keyCode == 67) {
            centerView();
            return true;
        }

        // L - auto layout
        if (keyCode == 76) {
            startAutoLayout();
            return true;
        }

        return false;
    }

    // ========================================================================
    // Callbacks
    // ========================================================================

    public void setOnNodeSelected(Consumer<String> callback) {
        this.onNodeSelected = callback;
    }

    public void setOnNodeDoubleClicked(Consumer<String> callback) {
        this.onNodeDoubleClicked = callback;
    }

    public void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback;
    }

    private void handleNodeClick(String nodeId) {
        long now = Util.getMillis();
        if (onNodeDoubleClicked != null && nodeId.equals(lastClickedNodeId)
            && now - lastClickTime <= DOUBLE_CLICK_THRESHOLD_MS) {
            onNodeDoubleClicked.accept(nodeId);
        }
        lastClickedNodeId = nodeId;
        lastClickTime = now;
    }

    // ========================================================================
    // Narration
    // ========================================================================

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        // No narration for graph canvas
    }
}
