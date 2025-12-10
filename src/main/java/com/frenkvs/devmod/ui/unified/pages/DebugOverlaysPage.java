package com.frenkvs.devmod.ui.unified.pages;

import com.frenkvs.devmod.debug.DebugFeature;
import com.frenkvs.devmod.debug.DebugTogglePayload;
import com.frenkvs.devmod.debug.client.DebugRenderBools;
import com.frenkvs.devmod.rendering.DebugRenderer;
import com.frenkvs.devmod.rendering.LightLevelOverlay;
import com.frenkvs.devmod.rendering.LineOfSightVisualizer;
import com.frenkvs.devmod.rendering.PathfindingDebugger;
import com.frenkvs.devmod.rendering.RoomBoundsVisualizer;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.unified.SettingsCategory;
import com.frenkvs.devmod.ui.unified.SettingsPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;

/**
 * Debug overlays settings page.
 * Provides toggles for all debug visualization overlays including
 * Minecraft's native debug APIs (similar to DebugUtils mod).
 */
@SuppressWarnings("null") // Minecraft APIs lack null annotations
public class DebugOverlaysPage implements SettingsPage {

    private static final int ROW_HEIGHT = 24;
    private static final int SECTION_SPACING = 16;
    private static final int SCROLLBAR_WIDTH = 6;

    // Scrolling state
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private int visibleHeight = 0;
    private int totalContentHeight = 0;

    // Scroll drag state
    private boolean isDraggingScrollbar = false;
    private int lastContentY, lastContentHeight;

    @Override
    public SettingsCategory getCategory() {
        return SettingsCategory.DEBUG;
    }

    @Override
    public String getTitle() {
        return "Debug Overlays";
    }

    @Override
    public void render(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        // Store dimensions for scroll calculations
        lastContentY = y;
        lastContentHeight = height;
        visibleHeight = height;

        // Calculate total content height
        totalContentHeight = calculateContentHeight();
        maxScrollOffset = Math.max(0, totalContentHeight - visibleHeight);

        // Clamp scroll offset
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));

        // Enable scissoring to clip content outside bounds
        graphics.enableScissor(x, y, x + width, y + height);

        // Apply scroll offset to starting Y position
        int currentY = y - scrollOffset;

        // ==========================================
        // Section: Minecraft Native Debug API
        // ==========================================
        graphics.drawString(font, "§6Minecraft Native Debug API", x, currentY, 0xFFFF8800, false);
        currentY += 14;

        // Entity Pathing (like DebugUtils)
        currentY = renderNativeToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Entity Pathing", "Show mob navigation paths (native)",
            DebugRenderBools.ENTITY_PATHING);

        // Entity Goals
        currentY = renderNativeToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Entity Goals", "Show AI goal selectors",
            DebugRenderBools.ENTITY_GOALS);

        // Entity Brains
        currentY = renderNativeToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Entity Brains", "Show villager/mob brain activity",
            DebugRenderBools.ENTITY_BRAINS);

        // POI (Points of Interest)
        currentY = renderNativeToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "POI", "Show points of interest (beds, workstations)",
            DebugRenderBools.POI);

        // Raids
        currentY = renderNativeToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Raids", "Show raid center locations",
            DebugRenderBools.RAIDS);

        // Bees
        currentY = renderNativeToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Bees", "Show bee pathfinding and hive info",
            DebugRenderBools.BEES);

        // Game Events
        currentY = renderNativeToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Game Events", "Show sculk sensor game events",
            DebugRenderBools.GAME_EVENTS);

        // Structures
        currentY = renderNativeToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Structures", "Show structure bounding boxes",
            DebugRenderBools.STRUCTURES);

        currentY += SECTION_SPACING;

        // ==========================================
        // Section: Core Debug
        // ==========================================
        graphics.drawString(font, "Core Debug Tools", x, currentY, UIConstants.Text.PRIMARY, false);
        currentY += 14;

        // Debug Renderer toggle
        currentY = renderToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Debug Renderer", "Show debug shapes (boxes, lines, spheres)",
            DebugRenderer.INSTANCE.isEnabled(), "G");

        // Light Level Overlay toggle
        currentY = renderToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Light Levels", "Show spawn-safe light levels on blocks",
            LightLevelOverlay.INSTANCE.isEnabled(), "L");

        currentY += SECTION_SPACING;

        // Section: Custom AI Tools
        graphics.drawString(font, "Custom AI Tools", x, currentY, UIConstants.Text.PRIMARY, false);
        currentY += 14;

        // Line of Sight toggle
        currentY = renderToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Line of Sight", "Visualize mob line-of-sight rays",
            LineOfSightVisualizer.INSTANCE.isEnabled(), "");

        // Pathfinding toggle (custom)
        currentY = renderToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Custom Pathfinding", "Show AI navigation paths (custom)",
            PathfindingDebugger.INSTANCE.isEnabled(), "");

        currentY += SECTION_SPACING;

        // Section: Spatial
        graphics.drawString(font, "Spatial Analysis", x, currentY, UIConstants.Text.PRIMARY, false);
        currentY += 14;

        // Room Bounds toggle
        currentY = renderToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
            "Room Bounds", "Show room boundary detection",
            RoomBoundsVisualizer.INSTANCE.isEnabled(), "");

        currentY += SECTION_SPACING;

        // Quick actions
        graphics.drawString(font, "Quick Actions", x, currentY, UIConstants.Text.PRIMARY, false);
        currentY += 14;

        // Disable All / Enable All buttons
        int buttonWidth = 100;
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;
        int buttonSpacing = 10;

        boolean disableAllHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, buttonWidth, buttonHeight);
        AxiomRenderer.drawButton(graphics, font, x, currentY, buttonWidth, buttonHeight,
            "Disable All", disableAllHovered, false);

        boolean enableAllHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x + buttonWidth + buttonSpacing, currentY, buttonWidth, buttonHeight);
        AxiomRenderer.drawButton(graphics, font, x + buttonWidth + buttonSpacing, currentY, buttonWidth, buttonHeight,
            "Enable All", enableAllHovered, false);

        // Disable scissoring
        graphics.disableScissor();

        // Render scrollbar if content exceeds visible area
        if (totalContentHeight > visibleHeight) {
            renderScrollbar(graphics, x + width - SCROLLBAR_WIDTH - 2, y, SCROLLBAR_WIDTH, height);
        }
    }

    /**
     * Calculate total content height for scrolling.
     */
    private int calculateContentHeight() {
        int height = 0;

        // Section: Minecraft Native Debug API
        height += 14; // Header
        height += ROW_HEIGHT * 8; // 8 native toggles
        height += SECTION_SPACING;

        // Section: Core Debug Tools
        height += 14; // Header
        height += ROW_HEIGHT * 2; // 2 toggles
        height += SECTION_SPACING;

        // Section: Custom AI Tools
        height += 14; // Header
        height += ROW_HEIGHT * 2; // 2 toggles
        height += SECTION_SPACING;

        // Section: Spatial Analysis
        height += 14; // Header
        height += ROW_HEIGHT; // 1 toggle
        height += SECTION_SPACING;

        // Section: Quick Actions
        height += 14; // Header
        height += UIConstants.Size.BUTTON_HEIGHT + 10; // Buttons

        return height;
    }

    /**
     * Render the scrollbar.
     */
    private void renderScrollbar(GuiGraphics graphics, int x, int y, int barWidth, int height) {
        // Track background
        graphics.fill(x, y, x + barWidth, y + height, UIConstants.Background.INPUT);

        // Calculate thumb size and position
        float visibleRatio = (float) visibleHeight / totalContentHeight;
        int thumbHeight = Math.max(20, (int) (height * visibleRatio));
        float scrollRatio = (float) scrollOffset / maxScrollOffset;
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);

        // Thumb
        int thumbColor = isDraggingScrollbar ? UIConstants.Border.ACCENT : UIConstants.Border.DEFAULT;
        graphics.fill(x, thumbY, x + barWidth, thumbY + thumbHeight, thumbColor);
    }

    /**
     * Render a toggle row for native Minecraft debug features.
     */
    private int renderNativeToggleRow(GuiGraphics graphics, Font font, int x, int y, int width,
                                       int mouseX, int mouseY, String label, String description,
                                       boolean enabled) {
        int rowWidth = width - 20;
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;

        if (hovered) {
            graphics.fill(x - 4, y - 2, x + rowWidth + 4, y + ROW_HEIGHT - 2, UIConstants.Background.HOVER);
        }

        // Orange indicator for native API
        graphics.fill(x, y + 2, x + 3, y + 12, 0xFFFF8800);

        // Label
        graphics.drawString(font, label, x + 8, y + 2, UIConstants.Text.PRIMARY, false);

        // Description
        graphics.drawString(font, description, x + 8, y + 12, UIConstants.Text.MUTED, false);

        // Toggle switch
        int toggleX = x + rowWidth - UIConstants.Size.TOGGLE_WIDTH;
        int toggleY = y + (ROW_HEIGHT - UIConstants.Size.TOGGLE_HEIGHT) / 2;
        AxiomRenderer.drawToggle(graphics, font, toggleX, toggleY,
            UIConstants.Size.TOGGLE_WIDTH, UIConstants.Size.TOGGLE_HEIGHT, enabled, hovered);

        return y + ROW_HEIGHT;
    }

    private int renderToggleRow(GuiGraphics graphics, Font font, int x, int y, int width,
                                 int mouseX, int mouseY, String label, String description,
                                 boolean enabled, String hotkey) {
        int rowWidth = width - 20;
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;

        // Background on hover
        if (hovered) {
            graphics.fill(x - 4, y - 2, x + rowWidth + 4, y + ROW_HEIGHT - 2, UIConstants.Background.HOVER);
        }

        // Label
        graphics.drawString(font, label, x, y + 2, UIConstants.Text.PRIMARY, false);

        // Hotkey badge (if present)
        if (!hotkey.isEmpty()) {
            int hotkeyX = x + font.width(label) + 8;
            graphics.fill(hotkeyX - 2, y, hotkeyX + font.width(hotkey) + 2, y + 10, UIConstants.Background.INPUT);
            graphics.drawString(font, hotkey, hotkeyX, y + 1, UIConstants.Text.MUTED, false);
        }

        // Description
        graphics.drawString(font, description, x, y + 12, UIConstants.Text.MUTED, false);

        // Toggle switch
        int toggleX = x + rowWidth - UIConstants.Size.TOGGLE_WIDTH;
        int toggleY = y + (ROW_HEIGHT - UIConstants.Size.TOGGLE_HEIGHT) / 2;
        AxiomRenderer.drawToggle(graphics, font, toggleX, toggleY,
            UIConstants.Size.TOGGLE_WIDTH, UIConstants.Size.TOGGLE_HEIGHT, enabled, hovered);

        return y + ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        // Check if click is within content area
        if (mouseX < contentX || mouseX > contentX + contentWidth ||
            mouseY < contentY || mouseY > contentY + lastContentHeight) {
            return false;
        }

        // Check scrollbar click
        if (totalContentHeight > visibleHeight) {
            int scrollbarX = contentX + contentWidth - SCROLLBAR_WIDTH - 2;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH) {
                isDraggingScrollbar = true;
                // Calculate thumb dimensions for accurate click positioning
                float visibleRatio = (float) visibleHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int) (lastContentHeight * visibleRatio));
                int trackHeight = lastContentHeight - thumbHeight;
                if (trackHeight > 0) {
                    // Center thumb on click position
                    float clickRatio = (float)(mouseY - contentY - thumbHeight / 2) / trackHeight;
                    clickRatio = Math.max(0, Math.min(1, clickRatio));
                    scrollOffset = (int)(maxScrollOffset * clickRatio);
                }
                return true;
            }
        }

        int rowWidth = contentWidth - 20 - SCROLLBAR_WIDTH;
        // Apply scroll offset to Y coordinate for hit detection
        int y = contentY - scrollOffset;

        // ==========================================
        // Native Debug API section
        // ==========================================
        // Skip section header "Minecraft Native Debug API"
        y += 14;

        // Entity Pathing
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            toggleNativeFeature(DebugFeature.ENTITY_PATHING);
            return true;
        }
        y += ROW_HEIGHT;

        // Entity Goals
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            toggleNativeFeature(DebugFeature.ENTITY_GOALS);
            return true;
        }
        y += ROW_HEIGHT;

        // Entity Brains
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            toggleNativeFeature(DebugFeature.ENTITY_BRAINS);
            return true;
        }
        y += ROW_HEIGHT;

        // POI
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            toggleNativeFeature(DebugFeature.POI);
            return true;
        }
        y += ROW_HEIGHT;

        // Raids
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            toggleNativeFeature(DebugFeature.RAIDS);
            return true;
        }
        y += ROW_HEIGHT;

        // Bees
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            toggleNativeFeature(DebugFeature.BEES);
            return true;
        }
        y += ROW_HEIGHT;

        // Game Events
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            toggleNativeFeature(DebugFeature.GAME_EVENTS);
            return true;
        }
        y += ROW_HEIGHT;

        // Structures
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            toggleNativeFeature(DebugFeature.STRUCTURE_GENERATIONS);
            return true;
        }
        y += ROW_HEIGHT + SECTION_SPACING;

        // ==========================================
        // Core Debug section
        // ==========================================
        // Skip section header "Core Debug Tools"
        y += 14;

        // Debug Renderer toggle
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            DebugRenderer.INSTANCE.toggle();
            return true;
        }
        y += ROW_HEIGHT;

        // Light Level toggle
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            LightLevelOverlay.INSTANCE.toggle();
            return true;
        }
        y += ROW_HEIGHT + SECTION_SPACING;

        // ==========================================
        // Custom AI Tools section
        // ==========================================
        // Skip section header "Custom AI Tools"
        y += 14;

        // Line of Sight toggle
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            LineOfSightVisualizer.INSTANCE.toggle();
            return true;
        }
        y += ROW_HEIGHT;

        // Custom Pathfinding toggle
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            PathfindingDebugger.INSTANCE.toggle();
            return true;
        }
        y += ROW_HEIGHT + SECTION_SPACING;

        // ==========================================
        // Spatial Analysis section
        // ==========================================
        // Skip section header "Spatial Analysis"
        y += 14;

        // Room Bounds toggle
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            RoomBoundsVisualizer.INSTANCE.toggle();
            return true;
        }
        y += ROW_HEIGHT + SECTION_SPACING;

        // ==========================================
        // Quick Actions section
        // ==========================================
        // Skip section header "Quick Actions"
        y += 14;

        // Quick action buttons
        int buttonWidth = 100;
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;
        int buttonSpacing = 10;

        // Disable All
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, buttonWidth, buttonHeight)) {
            disableAll();
            return true;
        }

        // Enable All
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX + buttonWidth + buttonSpacing, y, buttonWidth, buttonHeight)) {
            enableAll();
            return true;
        }

        return false;
    }

    /**
     * Toggle a native Minecraft debug feature by sending packet to server.
     */
    private void toggleNativeFeature(DebugFeature feature) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new DebugTogglePayload(feature.getId()));
        }
    }

    private boolean isInToggleRow(double mouseX, double mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX < x + width && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2;
    }

    private void disableAll() {
        // Disable custom debug tools
        DebugRenderer.INSTANCE.disable();
        LightLevelOverlay.INSTANCE.setEnabled(false);
        LineOfSightVisualizer.INSTANCE.setEnabled(false);
        PathfindingDebugger.INSTANCE.setEnabled(false);
        RoomBoundsVisualizer.INSTANCE.setEnabled(false);

        // Disable native debug features
        if (Minecraft.getInstance().getConnection() != null) {
            // Send disable for all enabled features
            if (DebugRenderBools.ENTITY_PATHING) toggleNativeFeature(DebugFeature.ENTITY_PATHING);
            if (DebugRenderBools.ENTITY_GOALS) toggleNativeFeature(DebugFeature.ENTITY_GOALS);
            if (DebugRenderBools.ENTITY_BRAINS) toggleNativeFeature(DebugFeature.ENTITY_BRAINS);
            if (DebugRenderBools.POI) toggleNativeFeature(DebugFeature.POI);
            if (DebugRenderBools.RAIDS) toggleNativeFeature(DebugFeature.RAIDS);
            if (DebugRenderBools.BEES) toggleNativeFeature(DebugFeature.BEES);
            if (DebugRenderBools.GAME_EVENTS) toggleNativeFeature(DebugFeature.GAME_EVENTS);
            if (DebugRenderBools.STRUCTURES) toggleNativeFeature(DebugFeature.STRUCTURE_GENERATIONS);
        }
    }

    private void enableAll() {
        // Enable custom debug tools
        DebugRenderer.INSTANCE.enable();
        LightLevelOverlay.INSTANCE.setEnabled(true);
        LineOfSightVisualizer.INSTANCE.setEnabled(true);
        PathfindingDebugger.INSTANCE.setEnabled(true);
        RoomBoundsVisualizer.INSTANCE.setEnabled(true);

        // Enable main native debug features
        if (Minecraft.getInstance().getConnection() != null) {
            if (!DebugRenderBools.ENTITY_PATHING) toggleNativeFeature(DebugFeature.ENTITY_PATHING);
            if (!DebugRenderBools.ENTITY_GOALS) toggleNativeFeature(DebugFeature.ENTITY_GOALS);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll speed: 20 pixels per scroll unit
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && maxScrollOffset > 0) {
            // Calculate thumb dimensions for accurate drag positioning
            float visibleRatio = (float) visibleHeight / totalContentHeight;
            int thumbHeight = Math.max(20, (int) (lastContentHeight * visibleRatio));
            int trackHeight = lastContentHeight - thumbHeight;
            if (trackHeight > 0) {
                // Center thumb on mouse position during drag
                float dragRatio = (float)(mouseY - lastContentY - thumbHeight / 2) / trackHeight;
                dragRatio = Math.max(0, Math.min(1, dragRatio));
                scrollOffset = (int)(maxScrollOffset * dragRatio);
            }
            return true;
        }
        return false;
    }

    @Override
    public int getContentHeight() {
        return calculateContentHeight();
    }
}
