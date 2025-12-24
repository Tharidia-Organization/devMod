package com.devmod.ui.unified.pages;

import com.devmod.debug.client.DebugRenderBools;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.rendering.DebugRenderer;
import com.devmod.rendering.LightLevelOverlay;
import com.devmod.rendering.LineOfSightVisualizer;
import com.devmod.rendering.PathfindingDebugger;
import com.devmod.rendering.RoomBoundsVisualizer;
import com.devmod.ui.AxiomRenderer;
import com.devmod.ui.editor.core.UIConstants;
import com.devmod.ui.editor.components.EditorButton;
import com.devmod.ui.unified.SettingsCategory;
import com.devmod.ui.unified.SettingsPage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Debug overlays settings page.
 * Provides toggles for all debug visualization overlays including
 * Minecraft's native debug APIs (similar to DebugUtils mod).
 */

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
    private final EditorButton disableAllBtn = new EditorButton("debug-disable-all", "Disable All").style(EditorButton.Style.DANGER);
    private final EditorButton enableAllBtn = new EditorButton("debug-enable-all", "Enable All").style(EditorButton.Style.SUCCESS);

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
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        // Store dimensions for scroll calculations
        lastContentY = y;
        lastContentHeight = height;
        visibleHeight = height;

        // Calculate total content height
        totalContentHeight = calculateContentHeight();
        maxScrollOffset = Math.max(0, totalContentHeight - visibleHeight);

        // Clamp scroll offset
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));

        // Enable scissoring to clip content outside bounds with try/finally for safety
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            // Apply scroll offset to starting Y position
            int currentY = y - scrollOffset;

            // ==========================================
            // Section: Minecraft Native Debug API
            // ==========================================
            graphics.drawString(safeFont, "§6Minecraft Native Debug API", x, currentY, 0xFFFF8800, false);
            currentY += 14;

            // Entity Pathing (like DebugUtils)
            currentY = renderNativeToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Entity Pathing", "Show mob navigation paths (native)",
                DebugRenderBools.ENTITY_PATHING);

            // Entity Goals
            currentY = renderNativeToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Entity Goals", "Show AI goal selectors",
                DebugRenderBools.ENTITY_GOALS);

            // Entity Brains
            currentY = renderNativeToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Entity Brains", "Show villager/mob brain activity",
                DebugRenderBools.ENTITY_BRAINS);

            // POI (Points of Interest)
            currentY = renderNativeToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "POI", "Show points of interest (beds, workstations)",
                DebugRenderBools.POI);

            // Raids
            currentY = renderNativeToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Raids", "Show raid center locations",
                DebugRenderBools.RAIDS);

            // Bees
            currentY = renderNativeToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Bees", "Show bee pathfinding and hive info",
                DebugRenderBools.BEES);

            // Game Events
            currentY = renderNativeToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Game Events", "Show sculk sensor game events",
                DebugRenderBools.GAME_EVENTS);

            // Structures
            currentY = renderNativeToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Structures", "Show structure bounding boxes",
                DebugRenderBools.STRUCTURES);

            currentY += SECTION_SPACING;

            // ==========================================
            // Section: Core Debug
            // ==========================================
            graphics.drawString(safeFont, "Core Debug Tools", x, currentY, UIConstants.Text.PRIMARY(), false);
            currentY += 14;

            // Debug Renderer toggle
            currentY = renderToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Debug Renderer", "Show debug shapes (boxes, lines, spheres)",
                DebugRenderer.INSTANCE.isEnabled(), "");

            // Light Level Overlay toggle
            currentY = renderToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Light Levels", "Show spawn-safe light levels on blocks",
                LightLevelOverlay.INSTANCE.isEnabled(), "");

            currentY += SECTION_SPACING;

            // Section: Custom AI Tools
            graphics.drawString(safeFont, "Custom AI Tools", x, currentY, UIConstants.Text.PRIMARY(), false);
            currentY += 14;

            // Line of Sight toggle
            currentY = renderToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Line of Sight", "Visualize mob line-of-sight rays",
                LineOfSightVisualizer.INSTANCE.isEnabled(), "");

            // Pathfinding toggle (custom)
            currentY = renderToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Custom Pathfinding", "Show AI navigation paths (custom)",
                PathfindingDebugger.INSTANCE.isEnabled(), "");

            currentY += SECTION_SPACING;

            // Section: Spatial
            graphics.drawString(safeFont, "Spatial Analysis", x, currentY, UIConstants.Text.PRIMARY(), false);
            currentY += 14;

            // Room Bounds toggle
            currentY = renderToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                "Room Bounds", "Show room boundary detection",
                RoomBoundsVisualizer.INSTANCE.isEnabled(), "");

            currentY += SECTION_SPACING;

            // Quick actions
            graphics.drawString(safeFont, "Quick Actions", x, currentY, UIConstants.Text.PRIMARY(), false);
            currentY += 14;

            // Disable All / Enable All buttons
            int buttonWidth = 100;
            int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;
            int buttonSpacing = 10;

            disableAllBtn.onClick(this::disableAll);
            disableAllBtn.render(graphics, x, currentY, buttonWidth, buttonHeight, mouseX, mouseY);

            enableAllBtn.onClick(this::enableAll);
            enableAllBtn.render(graphics, x + buttonWidth + buttonSpacing, currentY, buttonWidth, buttonHeight, mouseX, mouseY);
        } finally {
            // Disable scissoring
            graphics.disableScissor();
        }

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
        graphics.fill(x, y, x + barWidth, y + height, UIConstants.Background.INPUT());

        // Calculate thumb size and position
        float visibleRatio = (float) visibleHeight / totalContentHeight;
        int thumbHeight = Math.max(20, (int) (height * visibleRatio));
        float scrollRatio = (float) scrollOffset / maxScrollOffset;
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);

        // Thumb
        int thumbColor = isDraggingScrollbar ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT();
        graphics.fill(x, thumbY, x + barWidth, thumbY + thumbHeight, thumbColor);
    }

    /**
     * Render a toggle row for native Minecraft debug features.
     */
    private int renderNativeToggleRow(GuiGraphics graphics, Font font, int x, int y, int width,
                                       int mouseX, int mouseY, String label, String description,
                                       boolean enabled) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        @Nonnull String safeLabel = Objects.requireNonNull(label, "label");
        @Nonnull String safeDescription = Objects.requireNonNull(description, "description");
        int rowWidth = width - 20;
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;

        if (hovered) {
            graphics.fill(x - 4, y - 2, x + rowWidth + 4, y + ROW_HEIGHT - 2, UIConstants.Background.HOVER());
        }

        // Orange indicator for native API
        graphics.fill(x, y + 2, x + 3, y + 12, 0xFFFF8800);

        // Label
        graphics.drawString(safeFont, safeLabel, x + 8, y + 2, UIConstants.Text.PRIMARY(), false);

        // Description
        graphics.drawString(safeFont, safeDescription, x + 8, y + 12, UIConstants.Text.MUTED(), false);

        // Toggle switch
        int toggleX = x + rowWidth - UIConstants.Size.TOGGLE_WIDTH;
        int toggleY = y + (ROW_HEIGHT - UIConstants.Size.TOGGLE_HEIGHT) / 2;
        AxiomRenderer.drawToggle(graphics, safeFont, toggleX, toggleY,
            UIConstants.Size.TOGGLE_WIDTH, UIConstants.Size.TOGGLE_HEIGHT, enabled, hovered);

        return y + ROW_HEIGHT;
    }

    private int renderToggleRow(GuiGraphics graphics, Font font, int x, int y, int width,
                                 int mouseX, int mouseY, String label, String description,
                                 boolean enabled, String hotkey) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        @Nonnull String safeLabel = Objects.requireNonNull(label, "label");
        @Nonnull String safeDescription = Objects.requireNonNull(description, "description");
        String safeHotkey = Objects.requireNonNullElse(hotkey, "");
        int rowWidth = width - 20;
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;

        // Background on hover
        if (hovered) {
            graphics.fill(x - 4, y - 2, x + rowWidth + 4, y + ROW_HEIGHT - 2, UIConstants.Background.HOVER());
        }

        // Label
        graphics.drawString(safeFont, safeLabel, x, y + 2, UIConstants.Text.PRIMARY(), false);

        // Hotkey badge (if present)
        if (!safeHotkey.isEmpty()) {
            int hotkeyX = x + safeFont.width(safeLabel) + 8;
            graphics.fill(hotkeyX - 2, y, hotkeyX + safeFont.width(safeHotkey) + 2, y + 10, UIConstants.Background.INPUT());
            graphics.drawString(safeFont, safeHotkey, hotkeyX, y + 1, UIConstants.Text.MUTED(), false);
        }

        // Description
        graphics.drawString(safeFont, safeDescription, x, y + 12, UIConstants.Text.MUTED(), false);

        // Toggle switch
        int toggleX = x + rowWidth - UIConstants.Size.TOGGLE_WIDTH;
        int toggleY = y + (ROW_HEIGHT - UIConstants.Size.TOGGLE_HEIGHT) / 2;
        AxiomRenderer.drawToggle(graphics, safeFont, toggleX, toggleY,
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
            invokeAction(ActionIds.DEBUG_NATIVE_ENTITY_PATHING_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // Entity Goals
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            invokeAction(ActionIds.DEBUG_NATIVE_ENTITY_GOALS_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // Entity Brains
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            invokeAction(ActionIds.DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // POI
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            invokeAction(ActionIds.DEBUG_NATIVE_POI_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // Raids
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            invokeAction(ActionIds.DEBUG_NATIVE_RAIDS_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // Bees
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            invokeAction(ActionIds.DEBUG_NATIVE_BEES_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // Game Events
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            invokeAction(ActionIds.DEBUG_NATIVE_GAME_EVENTS_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // Structures
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            invokeAction(ActionIds.DEBUG_NATIVE_STRUCTURES_TOGGLE);
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
            invokeAction(ActionIds.DEBUG_OVERLAY_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // Light Level toggle
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            invokeAction(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE);
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
            invokeAction(ActionIds.DEBUG_LOS_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // Custom Pathfinding toggle
        if (isInToggleRow(mouseX, mouseY, contentX, y, rowWidth)) {
            invokeAction(ActionIds.DEBUG_PATHFINDING_TOGGLE);
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
            invokeAction(ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT + SECTION_SPACING;

        // ==========================================
        // Quick Actions section
        // ==========================================
        // Skip section header "Quick Actions"
        y += 14;

        if (disableAllBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (enableAllBtn.mouseClicked(mouseX, mouseY, button)) return true;

        return false;
    }

    private boolean isInToggleRow(double mouseX, double mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX < x + width && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2;
    }

    private void disableAll() {
        invokeAction(ActionIds.DEBUG_OVERLAYS_DISABLE_ALL);
    }

    private void enableAll() {
        invokeAction(ActionIds.DEBUG_OVERLAYS_ENABLE_ALL);
    }

    private void invokeAction(String actionId) {
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
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
