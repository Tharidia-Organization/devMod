package com.devmod.client.transport;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.BaseDevModScreen;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.transport.TransportColor;
import com.devmod.transport.network.TransportNetworkListPayload;
import com.devmod.transport.network.TransportNetworkListPayload.NetworkNodeInfo;
import com.devmod.transport.network.TransportWaypointSelectPayload;

/**
 * Network destination selection screen for transport nodes.
 *
 * <p>Displays a list of available nodes in a network and allows
 * the player to select a destination for teleportation.
 *
 * <p>From Bibbia Estetica Regola 4:
 * <ul>
 *   <li>List panel centered, scrollable</li>
 *   <li>Selected item highlighted with accent color</li>
 *   <li>Border color = TransportColor.primary</li>
 *   <li>Background transparency: 85% opaque (0xD9)</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null") // Minecraft API lacks @Nonnull annotations
public class TransportNetworkSelectScreen extends BaseDevModScreen {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransportNetworkSelectScreen.class);

    private static final int BACKGROUND_ALPHA = 0xD9;
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 240;
    private static final int ITEM_HEIGHT = 28;
    private static final int VISIBLE_ITEMS = 6;
    private static final int BORDER_THICKNESS = 2;

    private final UUID sourceNodeId;
    private final String networkName;
    private final List<NetworkNodeInfo> nodes;
    private final TransportColor color;

    @Nullable
    private NetworkNodeInfo selectedNode = null;
    private int scrollOffset = 0;
    private int hoveredIndex = -1;

    // Panel bounds
    private int panelLeft;
    private int panelTop;
    private int listTop;
    private int listHeight;

    // Widgets
    @Nullable private EditorButtonWidget teleportButtonWidget;
    @Nullable private EditorButtonWidget cancelButtonWidget;

    public TransportNetworkSelectScreen(
            @Nonnull UUID sourceNodeId,
            @Nonnull TransportNetworkListPayload payload) {
        super(Component.translatable("screen.devmod.transport_network_select"), "transport_network_select", "transport");

        this.sourceNodeId = sourceNodeId;
        this.networkName = payload.networkName();
        this.nodes = payload.nodes();
        this.color = nodes.isEmpty() ? TransportColor.CYAN : TransportColor.fromIndex(nodes.get(0).colorIndex());
    }

    @Override
    protected void initContent() {
        // Calculate panel position
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        listTop = panelTop + 40;
        listHeight = ITEM_HEIGHT * VISIBLE_ITEMS;

        int buttonWidth = (PANEL_WIDTH - 50) / 2;
        int buttonY = panelTop + PANEL_HEIGHT - 35;

        // Teleport button
        EditorButton teleportButton = EditorButton.builder("transport-network-teleport",
                Component.translatable("screen.devmod.transport_network_select.teleport").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .enabled(false)
            .onClick(this::teleportToSelected)
            .build();
        teleportButtonWidget = new EditorButtonWidget(teleportButton, panelLeft + 15, buttonY, buttonWidth, 20);
        addRenderableWidget(teleportButtonWidget);

        // Cancel button
        EditorButton cancelButton = EditorButton.builder("transport-network-cancel",
                Component.translatable("gui.cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        cancelButtonWidget = new EditorButtonWidget(cancelButton, panelLeft + 20 + buttonWidth + 10, buttonY, buttonWidth, 20);
        addRenderableWidget(cancelButtonWidget);

        // Auto-select first available node
        for (NetworkNodeInfo node : nodes) {
            if (node.available()) {
                selectedNode = node;
                setTeleportEnabled(true);
                break;
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Panel background
        int bgColor = (BACKGROUND_ALPHA << 24) | (DesignTokens.Bg.LEVEL_2 & 0xFFFFFF);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, bgColor);

        // Panel border
        int borderColor = (0xFF << 24) | (color.getColorValue() & 0xFFFFFF);
        renderBorder(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, borderColor);

        // Title
        Component title = Component.translatable("screen.devmod.transport_network_select.title", networkName);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 10, DesignTokens.Text.PRIMARY, true);

        // Node count info
        String countText = String.format("%d nodes (%d available)", nodes.size(),
            (int) nodes.stream().filter(NetworkNodeInfo::available).count());
        graphics.drawString(font, countText, panelLeft + 15, panelTop + 26, DesignTokens.Text.SECONDARY, false);

        // List background
        int listBgColor = (BACKGROUND_ALPHA << 24) | (DesignTokens.Bg.LEVEL_1 & 0xFFFFFF);
        graphics.fill(panelLeft + 10, listTop, panelLeft + PANEL_WIDTH - 10, listTop + listHeight, listBgColor);

        // Render visible nodes
        hoveredIndex = -1;
        int visibleCount = Math.min(VISIBLE_ITEMS, nodes.size() - scrollOffset);

        for (int i = 0; i < visibleCount; i++) {
            int nodeIndex = scrollOffset + i;
            NetworkNodeInfo node = nodes.get(nodeIndex);
            int itemY = listTop + i * ITEM_HEIGHT;

            // Check hover
            boolean hovered = mouseX >= panelLeft + 10 && mouseX < panelLeft + PANEL_WIDTH - 10
                && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            if (hovered) {
                hoveredIndex = nodeIndex;
            }

            renderNodeItem(graphics, node, panelLeft + 12, itemY + 2, PANEL_WIDTH - 24, ITEM_HEIGHT - 4, hovered);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(font, Component.literal("^"), panelLeft + PANEL_WIDTH / 2, listTop - 8, DesignTokens.Text.SECONDARY);
        }
        if (scrollOffset + VISIBLE_ITEMS < nodes.size()) {
            graphics.drawCenteredString(font, Component.literal("v"), panelLeft + PANEL_WIDTH / 2, listTop + listHeight + 2, DesignTokens.Text.SECONDARY);
        }
    }

    /**
     * Renders a single node item in the list.
     */
    private void renderNodeItem(GuiGraphics graphics, NetworkNodeInfo node, int x, int y, int width, int height, boolean hovered) {
        boolean selected = node.equals(selectedNode);
        boolean available = node.available();

        // Background
        int bgColor;
        if (selected) {
            bgColor = (0xFF << 24) | (DesignTokens.Surface.LEVEL_3 & 0xFFFFFF);
        } else if (hovered && available) {
            bgColor = (0xFF << 24) | (DesignTokens.Surface.LEVEL_2 & 0xFFFFFF);
        } else {
            bgColor = (0xFF << 24) | (DesignTokens.Surface.LEVEL_1 & 0xFFFFFF);
        }
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Color indicator (left strip)
        int nodeColor = TransportColor.fromIndex(node.colorIndex()).getColorValue();
        int indicatorColor = available ? ((0xFF << 24) | (nodeColor & 0xFFFFFF)) : 0xFF555555;
        graphics.fill(x, y, x + 4, y + height, indicatorColor);

        // Selection border
        if (selected) {
            int selColor = (0xFF << 24) | (color.getColorValue() & 0xFFFFFF);
            renderBorder(graphics, x, y, width, height, selColor, 1);
        }

        // Node name
        int textColor = available ? DesignTokens.Text.PRIMARY : DesignTokens.Text.MUTED;
        String displayName = node.displayName().isEmpty() ? "Unnamed" : node.displayName();
        graphics.drawString(font, displayName, x + 8, y + 4, textColor, false);

        // Coordinates
        String coordText = node.getCoordinatesString();
        graphics.drawString(font, coordText, x + 8, y + 14, DesignTokens.Text.SECONDARY, false);

        // Dimension (right side)
        String dimText = formatDimension(node.dimension());
        int dimWidth = font.width(dimText);
        graphics.drawString(font, dimText, x + width - dimWidth - 4, y + 4, DesignTokens.Text.MUTED, false);

        // Availability status
        if (!available) {
            String unavailText = "Unavailable";
            int unavailWidth = font.width(unavailText);
            graphics.drawString(font, unavailText, x + width - unavailWidth - 4, y + 14, DesignTokens.Semantic.ERROR, false);
        }
    }

    /**
     * Formats dimension string for display.
     */
    private String formatDimension(String dimension) {
        if (dimension.contains(":")) {
            int idx = dimension.indexOf(':');
            if (idx >= 0 && idx + 1 < dimension.length()) {
                return dimension.substring(idx + 1);
            }
        }
        return dimension;
    }

    /**
     * Renders a border around a rectangle.
     */
    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        renderBorder(graphics, x, y, width, height, color, BORDER_THICKNESS);
    }

    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color, int thickness) {
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
     * Teleports to the selected node.
     */
    private void teleportToSelected() {
        if (selectedNode == null || !selectedNode.available()) {
            showError("No valid destination selected");
            return;
        }

        // Send selection to server
        TransportWaypointSelectPayload payload = new TransportWaypointSelectPayload(sourceNodeId, selectedNode.nodeId());
        PacketDistributor.sendToServer(payload);

        LOGGER.info("[TransportSelect] Selected destination: {}", selectedNode.displayName());
        onClose();
    }

    @Override
    protected boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredIndex >= 0 && hoveredIndex < nodes.size()) {
            NetworkNodeInfo node = nodes.get(hoveredIndex);
            if (node.available()) {
                selectedNode = node;
                setTeleportEnabled(true);
                playClickSound();
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean handleMouseScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Check if mouse is over the list area
        if (mouseX >= panelLeft + 10 && mouseX < panelLeft + PANEL_WIDTH - 10
            && mouseY >= listTop && mouseY < listTop + listHeight) {

            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (scrollY < 0 && scrollOffset + VISIBLE_ITEMS < nodes.size()) {
                scrollOffset++;
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        // Enter to teleport
        if (keyCode == 257 && selectedNode != null && selectedNode.available()) {
            teleportToSelected();
            return true;
        }

        // Up arrow
        if (keyCode == 265) {
            selectPrevious();
            return true;
        }

        // Down arrow
        if (keyCode == 264) {
            selectNext();
            return true;
        }

        return false;
    }

    /**
     * Selects the previous available node.
     */
    private void selectPrevious() {
        if (nodes.isEmpty()) return;

        int startIndex = selectedNode == null ? nodes.size() : nodes.indexOf(selectedNode);
        for (int i = startIndex - 1; i >= 0; i--) {
            if (nodes.get(i).available()) {
                selectedNode = nodes.get(i);
                setTeleportEnabled(true);
                ensureVisible(i);
                return;
            }
        }
    }

    /**
     * Selects the next available node.
     */
    private void selectNext() {
        if (nodes.isEmpty()) return;

        int startIndex = selectedNode == null ? -1 : nodes.indexOf(selectedNode);
        for (int i = startIndex + 1; i < nodes.size(); i++) {
            if (nodes.get(i).available()) {
                selectedNode = nodes.get(i);
                setTeleportEnabled(true);
                ensureVisible(i);
                return;
            }
        }
    }

    private void setTeleportEnabled(boolean enabled) {
        EditorButtonWidget teleportWidget = teleportButtonWidget;
        if (teleportWidget != null) {
            teleportWidget.getButton().setEnabled(enabled);
        }
    }

    /**
     * Ensures the given index is visible by adjusting scroll.
     */
    private void ensureVisible(int index) {
        if (index < scrollOffset) {
            scrollOffset = index;
        } else if (index >= scrollOffset + VISIBLE_ITEMS) {
            scrollOffset = index - VISIBLE_ITEMS + 1;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Opens this screen from a payload.
     */
    public static void open(@Nonnull UUID sourceNodeId, @Nonnull TransportNetworkListPayload payload) {
        Minecraft.getInstance().setScreen(new TransportNetworkSelectScreen(sourceNodeId, payload));
    }
}
