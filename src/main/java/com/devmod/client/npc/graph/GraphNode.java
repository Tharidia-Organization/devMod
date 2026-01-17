package com.devmod.client.npc.graph;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.dialog.DialogNode;
import com.devmod.npc.dialog.DialogOption;
import com.devmod.npc.dialog.action.DialogAction;

/**
 * Represents a dialog node in the visual graph editor.
 * Handles positioning, selection state, and rendering of individual nodes.
 */
public class GraphNode {
    /** Default node dimensions */
    public static final int DEFAULT_WIDTH = 160;
    public static final int DEFAULT_HEIGHT = 80;
    public static final int MIN_WIDTH = 120;
    public static final int MIN_HEIGHT = 60;

    /** Visual constants */
    private static final int HEADER_HEIGHT = 20;
    private static final int CONNECTOR_SIZE = 8;

    /** Colors */
    private static final int COLOR_BACKGROUND = DesignTokens.Background.PANEL;
    private static final int COLOR_HEADER = DesignTokens.Background.HEADER;
    private static final int COLOR_BORDER = DesignTokens.Border.DEFAULT;
    private static final int COLOR_BORDER_SELECTED = DesignTokens.Border.ACCENT;
    private static final int COLOR_BORDER_ENTRY = DesignTokens.Accent.GREEN;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.MUTED;
    private static final int COLOR_CONNECTOR = DesignTokens.Text.MUTED;
    private static final int COLOR_CONNECTOR_HOVER = DesignTokens.Text.PRIMARY;

    private final String nodeId;
    private final DialogNode dialogNode;

    private int x;
    private int y;
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;

    private boolean selected = false;
    private boolean dragging = false;
    private boolean hovered = false;
    private boolean isEntryNode = false;

    // Drag offset for smooth dragging
    private int dragOffsetX;
    private int dragOffsetY;

    // Velocity for force-directed layout
    float velocityX = 0;
    float velocityY = 0;

    public GraphNode(@Nonnull String nodeId, @Nonnull DialogNode dialogNode, int x, int y) {
        this.nodeId = nodeId;
        this.dialogNode = dialogNode;
        this.x = x;
        this.y = y;
        recalculateHeight();
    }

    // ========================================================================
    // Getters/Setters
    // ========================================================================

    @Nonnull
    public String getNodeId() {
        return nodeId;
    }

    @Nonnull
    public DialogNode getDialogNode() {
        return dialogNode;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getCenterX() {
        return x + width / 2;
    }

    public int getCenterY() {
        return y + height / 2;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean isHovered() {
        return hovered;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public boolean isEntryNode() {
        return isEntryNode;
    }

    public void setEntryNode(boolean entryNode) {
        this.isEntryNode = entryNode;
    }

    // ========================================================================
    // Drag Operations
    // ========================================================================

    public void startDrag(int mouseX, int mouseY) {
        this.dragging = true;
        this.dragOffsetX = mouseX - x;
        this.dragOffsetY = mouseY - y;
    }

    public void drag(int mouseX, int mouseY) {
        if (dragging) {
            this.x = mouseX - dragOffsetX;
            this.y = mouseY - dragOffsetY;
        }
    }

    public void stopDrag() {
        this.dragging = false;
    }

    // ========================================================================
    // Hit Testing
    // ========================================================================

    /**
     * Checks if the given screen coordinates are within this node's bounds.
     */
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width
            && mouseY >= y && mouseY <= y + height;
    }

    /**
     * Checks if the given screen coordinates are over the header (for dragging).
     */
    public boolean isMouseOverHeader(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width
            && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
    }

    /**
     * Gets the index of the output connector at the given position, or -1 if none.
     */
    public int getOutputConnectorAt(int mouseX, int mouseY) {
        List<DialogOption> options = dialogNode.options();
        int connectorY = y + HEADER_HEIGHT + 10;

        for (int i = 0; i < options.size(); i++) {
            int cy = connectorY + i * 18;
            int cx = x + width - 4;

            if (mouseX >= cx - CONNECTOR_SIZE / 2 && mouseX <= cx + CONNECTOR_SIZE / 2
                && mouseY >= cy - CONNECTOR_SIZE / 2 && mouseY <= cy + CONNECTOR_SIZE / 2) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Gets the position of the input connector (left side center).
     */
    public int[] getInputConnectorPosition() {
        return new int[] { x, y + height / 2 };
    }

    /**
     * Gets the position of an output connector by option index.
     */
    public int[] getOutputConnectorPosition(int optionIndex) {
        int connectorY = y + HEADER_HEIGHT + 10 + optionIndex * 18;
        return new int[] { x + width, connectorY };
    }

    // ========================================================================
    // Connections
    // ========================================================================

    /**
     * Gets the list of node IDs that this node connects to via GoToNode actions.
     */
    @Nonnull
    public List<String> getOutgoingConnections() {
        List<String> connections = new ArrayList<>();
        for (DialogOption option : dialogNode.options()) {
            if (option.action() instanceof DialogAction.GoToNode goTo) {
                connections.add(goTo.nodeId());
            }
        }
        return connections;
    }

    /**
     * Gets detailed connection info including which option leads to which node.
     */
    @Nonnull
    public List<ConnectionInfo> getConnectionInfos() {
        List<ConnectionInfo> infos = new ArrayList<>();
        List<DialogOption> options = dialogNode.options();

        for (int i = 0; i < options.size(); i++) {
            DialogOption option = options.get(i);
            if (option.action() instanceof DialogAction.GoToNode goTo) {
                infos.add(new ConnectionInfo(option.id(), goTo.nodeId(), i));
            }
        }
        return infos;
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    /**
     * Renders this node on the graph canvas.
     *
     * @param graphics The graphics context
     * @param mouseX   Current mouse X position
     * @param mouseY   Current mouse Y position
     * @param zoom     Current zoom level
     */
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float zoom) {
        // Determine border color
        int borderColor = COLOR_BORDER;
        if (selected) {
            borderColor = COLOR_BORDER_SELECTED;
        } else if (isEntryNode) {
            borderColor = COLOR_BORDER_ENTRY;
        }

        // Background with border
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, borderColor);
        graphics.fill(x, y, x + width, y + height, COLOR_BACKGROUND);

        // Header
        graphics.fill(x, y, x + width, y + HEADER_HEIGHT, COLOR_HEADER);

        // Node ID in header
        String displayId = truncate(nodeId, 20);
        graphics.drawString(
            net.minecraft.client.Minecraft.getInstance().font,
            displayId,
            x + 6,
            y + 6,
            isEntryNode ? COLOR_BORDER_ENTRY : COLOR_TEXT,
            false
        );

        // Entry node indicator
        if (isEntryNode) {
            graphics.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
                "\u25B6",  // Play symbol
                x + width - 14,
                y + 6,
                COLOR_BORDER_ENTRY,
                false
            );
        }

        // Options list
        List<DialogOption> options = dialogNode.options();
        int optionY = y + HEADER_HEIGHT + 4;

        for (int i = 0; i < Math.min(options.size(), 4); i++) {
            DialogOption option = options.get(i);
            String label = truncate(option.label(), 18);

            // Option text
            graphics.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
                label,
                x + 6,
                optionY + i * 18,
                COLOR_TEXT_DIM,
                false
            );

            // Output connector
            if (option.action() instanceof DialogAction.GoToNode) {
                int cx = x + width - 4;
                int cy = optionY + i * 18 + 4;
                int connectorColor = getOutputConnectorAt(mouseX, mouseY) == i
                    ? COLOR_CONNECTOR_HOVER : COLOR_CONNECTOR;
                graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, connectorColor);
            }
        }

        // Show "+N more" if there are more options
        if (options.size() > 4) {
            graphics.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
                "+" + (options.size() - 4) + " more...",
                x + 6,
                optionY + 4 * 18,
                COLOR_TEXT_DIM,
                false
            );
        }

        // Input connector (left side)
        int inputY = y + height / 2;
        graphics.fill(x - 3, inputY - 3, x + 3, inputY + 3, COLOR_CONNECTOR);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private void recalculateHeight() {
        int optionCount = Math.min(dialogNode.options().size(), 5);
        this.height = HEADER_HEIGHT + 8 + optionCount * 18;
        this.height = Math.max(this.height, MIN_HEIGHT);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 2) + ".." : text;
    }

    // ========================================================================
    // Inner Classes
    // ========================================================================

    /**
     * Information about a connection from this node.
     */
    public record ConnectionInfo(String optionId, String targetNodeId, int optionIndex) {}
}
