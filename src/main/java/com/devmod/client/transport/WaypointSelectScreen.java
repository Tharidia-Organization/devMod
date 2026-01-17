package com.devmod.client.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.BaseDevModScreen;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.transport.TransportColor;
import com.devmod.transport.network.TransportWaypointSelectPayload;

/**
 * Waypoint selection screen for personal saved waypoints.
 *
 * <p>Displays a categorized list of the player's personal waypoints,
 * allowing search and selection for teleportation.
 *
 * <p>From Bibbia Estetica Regola 4:
 * <ul>
 *   <li>List panel centered, scrollable</li>
 *   <li>Search field at top</li>
 *   <li>Categories collapsible</li>
 *   <li>Border color = TransportColor.primary</li>
 *   <li>Background transparency: 85% opaque (0xD9)</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class WaypointSelectScreen extends BaseDevModScreen {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaypointSelectScreen.class);

    private static final int BACKGROUND_ALPHA = 0xD9;
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 280;
    private static final int ITEM_HEIGHT = 32;
    private static final int VISIBLE_ITEMS = 5;
    private static final int BORDER_THICKNESS = 2;

    private final UUID sourceNodeId;
    private final List<WaypointEntry> allWaypoints;
    private List<WaypointEntry> filteredWaypoints;
    private final TransportColor color;

    @Nullable
    private WaypointEntry selectedWaypoint = null;
    private int scrollOffset = 0;
    private int hoveredIndex = -1;
    private String searchFilter = "";

    // Panel bounds
    private int panelLeft;
    private int panelTop;
    private int listTop;
    private int listHeight;

    // Widgets
    @Nullable private EditBox searchBox;
    @Nullable private EditorButtonWidget teleportButtonWidget;
    @Nullable private EditorButtonWidget cancelButtonWidget;
    @Nullable private EditorButtonWidget deleteButtonWidget;

    /**
     * Waypoint entry data class.
     */
    public record WaypointEntry(
        UUID waypointId,
        String name,
        String dimension,
        int x,
        int y,
        int z,
        TransportColor color,
        String category,
        long lastUsed,
        boolean available
    ) {
        public String getCoordinatesString() {
            return String.format("%d, %d, %d", x, y, z);
        }

        public String getFormattedDimension() {
            if (dimension.contains(":")) {
                int idx = dimension.indexOf(':');
                if (idx >= 0 && idx + 1 < dimension.length()) {
                    return dimension.substring(idx + 1);
                }
            }
            return dimension;
        }
    }

    public WaypointSelectScreen(
            @Nonnull UUID sourceNodeId,
            @Nonnull List<WaypointEntry> waypoints,
            @Nonnull TransportColor defaultColor) {
        super(Component.translatable("screen.devmod.waypoint_select"), "waypoint_select", "transport");

        this.sourceNodeId = sourceNodeId;
        this.allWaypoints = new ArrayList<>(waypoints);
        this.filteredWaypoints = new ArrayList<>(waypoints);
        this.color = defaultColor;
    }

    @Override
    protected void initContent() {
        // Calculate panel position
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        listTop = panelTop + 65;
        listHeight = ITEM_HEIGHT * VISIBLE_ITEMS;

        // Search box
        var renderFont = Objects.requireNonNull(this.font, "font");
        var search = new EditBox(renderFont, panelLeft + 15, panelTop + 35, PANEL_WIDTH - 30, 20,
            Objects.requireNonNull(Component.translatable("screen.devmod.waypoint_select.search")));
        search.setHint(Objects.requireNonNull(Component.translatable("screen.devmod.waypoint_select.search_hint")));
        search.setMaxLength(50);
        search.setResponder(this::onSearchChanged);
        search.setBordered(false);
        search.setTextColor(DesignTokens.Text.PRIMARY);
        search.setTextColorUneditable(DesignTokens.Text.MUTED);
        searchBox = search;
        addRenderableWidget(search);

        int buttonWidth = (PANEL_WIDTH - 60) / 3;
        int buttonY = panelTop + PANEL_HEIGHT - 35;

        // Teleport button
        EditorButton teleportButton = EditorButton.builder("waypoint-select-teleport",
                Component.translatable("screen.devmod.waypoint_select.teleport").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .enabled(false)
            .onClick(this::teleportToSelected)
            .build();
        teleportButtonWidget = new EditorButtonWidget(teleportButton, panelLeft + 15, buttonY, buttonWidth, 20);
        addRenderableWidget(teleportButtonWidget);

        // Delete button
        EditorButton deleteButton = EditorButton.builder("waypoint-select-delete",
                Component.translatable("screen.devmod.waypoint_select.delete").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.MEDIUM)
            .enabled(false)
            .onClick(this::deleteSelected)
            .build();
        deleteButtonWidget = new EditorButtonWidget(deleteButton, panelLeft + 20 + buttonWidth, buttonY, buttonWidth, 20);
        addRenderableWidget(deleteButtonWidget);

        // Cancel button
        EditorButton cancelButton = EditorButton.builder("waypoint-select-cancel",
                Component.translatable("gui.cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        cancelButtonWidget = new EditorButtonWidget(cancelButton, panelLeft + 25 + buttonWidth * 2, buttonY, buttonWidth, 20);
        addRenderableWidget(cancelButtonWidget);

        // Auto-select first available waypoint
        for (WaypointEntry wp : filteredWaypoints) {
            if (wp.available()) {
                selectedWaypoint = wp;
                setTeleportEnabled(true);
                setDeleteEnabled(true);
                break;
            }
        }
    }

    /**
     * Handles search filter changes.
     */
    private void onSearchChanged(String filter) {
        this.searchFilter = filter.toLowerCase(Locale.ROOT).trim();
        applyFilter();
    }

    /**
     * Applies the current search filter.
     */
    private void applyFilter() {
        if (searchFilter.isEmpty()) {
            filteredWaypoints = new ArrayList<>(allWaypoints);
        } else {
            filteredWaypoints = allWaypoints.stream()
                .filter(wp -> wp.name().toLowerCase(Locale.ROOT).contains(searchFilter)
                    || wp.category().toLowerCase(Locale.ROOT).contains(searchFilter)
                    || wp.dimension().toLowerCase(Locale.ROOT).contains(searchFilter))
                .toList();
        }

        // Reset selection if no longer in filtered list
        if (selectedWaypoint != null && !filteredWaypoints.contains(selectedWaypoint)) {
            selectedWaypoint = null;
            setTeleportEnabled(false);
            setDeleteEnabled(false);
        }

        scrollOffset = 0;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var renderFont = Objects.requireNonNull(this.font, "font");

        // Panel background
        int bgColor = (BACKGROUND_ALPHA << 24) | (DesignTokens.Bg.LEVEL_2 & 0xFFFFFF);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, bgColor);

        // Panel border
        int borderColor = (0xFF << 24) | (color.getColorValue() & 0xFFFFFF);
        renderBorder(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, borderColor);

        // Title
        Component title = Objects.requireNonNull(Component.translatable("screen.devmod.waypoint_select.title"));
        int titleWidth = renderFont.width(Objects.requireNonNull(title));
        graphics.drawString(renderFont, Objects.requireNonNull(title), panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 10, DesignTokens.Text.PRIMARY, true);

        // Waypoint count
        String countText = Objects.requireNonNull(String.format("%d waypoints", filteredWaypoints.size()));
        if (!searchFilter.isEmpty()) {
            countText += String.format(" (filtered from %d)", allWaypoints.size());
        }
        int countWidth = renderFont.width(countText);
        graphics.drawString(renderFont, countText, panelLeft + (PANEL_WIDTH - countWidth) / 2, panelTop + 58, DesignTokens.Text.SECONDARY, false);

        // List background
        int listBgColor = (BACKGROUND_ALPHA << 24) | (DesignTokens.Bg.LEVEL_1 & 0xFFFFFF);
        graphics.fill(panelLeft + 10, listTop, panelLeft + PANEL_WIDTH - 10, listTop + listHeight, listBgColor);

        // Render visible waypoints
        hoveredIndex = -1;
        int visibleCount = Math.min(VISIBLE_ITEMS, filteredWaypoints.size() - scrollOffset);

        if (filteredWaypoints.isEmpty()) {
            // Empty state
            String emptyText = searchFilter.isEmpty() ? "No waypoints saved" : "No matching waypoints";
            int emptyWidth = renderFont.width(emptyText);
            graphics.drawString(renderFont, emptyText, panelLeft + (PANEL_WIDTH - emptyWidth) / 2, listTop + listHeight / 2 - 5, DesignTokens.Text.MUTED, false);
        } else {
            for (int i = 0; i < visibleCount; i++) {
                int wpIndex = scrollOffset + i;
                WaypointEntry wp = filteredWaypoints.get(wpIndex);
                int itemY = listTop + i * ITEM_HEIGHT;

                // Check hover
                boolean hovered = mouseX >= panelLeft + 10 && mouseX < panelLeft + PANEL_WIDTH - 10
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
                if (hovered) {
                    hoveredIndex = wpIndex;
                }

                renderWaypointItem(graphics, wp, panelLeft + 12, itemY + 2, PANEL_WIDTH - 24, ITEM_HEIGHT - 4, hovered);
            }
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(renderFont, Objects.requireNonNull(Component.literal("^")), panelLeft + PANEL_WIDTH / 2, listTop - 8, DesignTokens.Text.SECONDARY);
        }
        if (scrollOffset + VISIBLE_ITEMS < filteredWaypoints.size()) {
            graphics.drawCenteredString(renderFont, Objects.requireNonNull(Component.literal("v")), panelLeft + PANEL_WIDTH / 2, listTop + listHeight + 2, DesignTokens.Text.SECONDARY);
        }

        renderInputBackgrounds(graphics);
    }

    /**
     * Renders a single waypoint item in the list.
     */
    private void renderWaypointItem(GuiGraphics graphics, WaypointEntry wp, int x, int y, int width, int height, boolean hovered) {
        var renderFont = Objects.requireNonNull(this.font, "font");
        boolean selected = wp.equals(selectedWaypoint);
        boolean available = wp.available();

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
        int wpColor = wp.color().getColorValue();
        int indicatorColor = available ? ((0xFF << 24) | (wpColor & 0xFFFFFF)) : 0xFF555555;
        graphics.fill(x, y, x + 4, y + height, indicatorColor);

        // Selection border
        if (selected) {
            int selColor = (0xFF << 24) | (color.getColorValue() & 0xFFFFFF);
            renderBorder(graphics, x, y, width, height, selColor, 1);
        }

        // Waypoint name
        int textColor = available ? DesignTokens.Text.PRIMARY : DesignTokens.Text.MUTED;
        String displayName = wp.name().isEmpty() ? "Unnamed" : wp.name();
        graphics.drawString(renderFont, displayName, x + 8, y + 3, textColor, false);

        // Category badge (if present)
        if (!wp.category().isEmpty()) {
            String catText = "[" + wp.category() + "]";
            int catWidth = renderFont.width(catText);
            int catX = x + width - catWidth - 4;
            graphics.drawString(renderFont, catText, catX, y + 3, DesignTokens.Text.MUTED, false);
        }

        // Coordinates
        String coordText = wp.getCoordinatesString();
        graphics.drawString(renderFont, coordText, x + 8, y + 13, DesignTokens.Text.SECONDARY, false);

        // Dimension
        String dimText = Objects.requireNonNull(wp.getFormattedDimension());
        int dimWidth = renderFont.width(dimText);
        graphics.drawString(renderFont, dimText, x + width - dimWidth - 4, y + 13, DesignTokens.Text.MUTED, false);

        // Availability status
        if (!available) {
            String unavailText = "Unavailable";
            graphics.drawString(renderFont, unavailText, x + 8, y + 23, DesignTokens.Semantic.ERROR, false);
        }
    }

    /**
     * Renders a border around a rectangle.
     */
    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        renderBorder(graphics, x, y, width, height, color, BORDER_THICKNESS);
    }

    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color, int thickness) {
        graphics.fill(x, y, x + width, y + thickness, color);
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        graphics.fill(x, y, x + thickness, y + height, color);
        graphics.fill(x + width - thickness, y, x + width, y + height, color);
    }

    /**
     * Teleports to the selected waypoint.
     */
    private void teleportToSelected() {
        WaypointEntry waypoint = selectedWaypoint;
        if (waypoint == null || !waypoint.available()) {
            showError("No valid waypoint selected");
            return;
        }

        // Send selection to server
        TransportWaypointSelectPayload payload = new TransportWaypointSelectPayload(sourceNodeId, waypoint.waypointId());
        PacketDistributor.sendToServer(payload);

        LOGGER.info("[WaypointSelect] Selected waypoint: {}", waypoint.name());
        onClose();
    }

    /**
     * Deletes the selected waypoint.
     */
    private void deleteSelected() {
        WaypointEntry waypoint = selectedWaypoint;
        if (waypoint == null) {
            return;
        }

        LOGGER.info("[WaypointSelect] Delete requested for waypoint: {}", waypoint.name());

        // Send delete request to server
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.devmod.transport.network.TransportDeleteWaypointPayload(waypoint.waypointId())
        );

        // Remove from lists (optimistic update - server will confirm)
        allWaypoints.remove(waypoint);
        filteredWaypoints.remove(waypoint);
        selectedWaypoint = null;
        setTeleportEnabled(false);
        setDeleteEnabled(false);
    }

    @Override
    protected boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredIndex >= 0 && hoveredIndex < filteredWaypoints.size()) {
            WaypointEntry wp = filteredWaypoints.get(hoveredIndex);
            selectedWaypoint = wp;
            setTeleportEnabled(wp.available());
            setDeleteEnabled(true);
            playClickSound();
            return true;
        }
        return false;
    }

    @Override
    protected boolean handleMouseScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelLeft + 10 && mouseX < panelLeft + PANEL_WIDTH - 10
            && mouseY >= listTop && mouseY < listTop + listHeight) {

            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (scrollY < 0 && scrollOffset + VISIBLE_ITEMS < filteredWaypoints.size()) {
                scrollOffset++;
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        // Don't capture keys when search box is focused
        if (searchBox != null && searchBox.isFocused()) {
            return false;
        }

        // Enter to teleport
        if (keyCode == 257 && selectedWaypoint != null && selectedWaypoint.available()) {
            teleportToSelected();
            return true;
        }

        // Delete key
        if (keyCode == 261 && selectedWaypoint != null) {
            deleteSelected();
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

        // Focus search on Ctrl+F
        if (keyCode == 70 && (modifiers & 2) != 0) { // GLFW_KEY_F + CTRL
            if (searchBox != null) {
                searchBox.setFocused(true);
            }
            return true;
        }

        return false;
    }

    private void selectPrevious() {
        if (filteredWaypoints.isEmpty()) return;

        WaypointEntry currentSelection = selectedWaypoint;
        int currentIndex;
        if (currentSelection == null) {
            currentIndex = filteredWaypoints.size();
        } else {
            currentIndex = filteredWaypoints.indexOf(currentSelection);
        }

        int prevIndex = currentIndex - 1;
        if (prevIndex >= 0) {
            WaypointEntry wp = filteredWaypoints.get(prevIndex);
            selectedWaypoint = wp;
            setTeleportEnabled(wp.available());
            setDeleteEnabled(true);
            ensureVisible(prevIndex);
        }
    }

    private void selectNext() {
        if (filteredWaypoints.isEmpty()) return;

        WaypointEntry currentSelection = selectedWaypoint;
        int currentIndex;
        if (currentSelection == null) {
            currentIndex = -1;
        } else {
            currentIndex = filteredWaypoints.indexOf(currentSelection);
        }

        int nextIndex = currentIndex + 1;
        if (nextIndex < filteredWaypoints.size()) {
            WaypointEntry wp = filteredWaypoints.get(nextIndex);
            selectedWaypoint = wp;
            setTeleportEnabled(wp.available());
            setDeleteEnabled(true);
            ensureVisible(nextIndex);
        }
    }

    private void setTeleportEnabled(boolean enabled) {
        EditorButtonWidget teleportWidget = teleportButtonWidget;
        if (teleportWidget != null) {
            teleportWidget.getButton().setEnabled(enabled);
        }
    }

    private void setDeleteEnabled(boolean enabled) {
        EditorButtonWidget deleteWidget = deleteButtonWidget;
        if (deleteWidget != null) {
            deleteWidget.getButton().setEnabled(enabled);
        }
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        EditBox search = searchBox;
        if (search != null) {
            AxiomRenderer.drawInputBackground(graphics, search.getX(), search.getY(), search.getWidth(),
                search.getHeight(), search.isFocused());
        }
    }

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
     * Opens this screen.
     */
    public static void open(@Nonnull UUID sourceNodeId, @Nonnull List<WaypointEntry> waypoints, @Nonnull TransportColor color) {
        Minecraft.getInstance().setScreen(new WaypointSelectScreen(sourceNodeId, waypoints, color));
    }
}
