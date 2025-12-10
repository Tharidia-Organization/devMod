package com.frenkvs.devmod.ui.unified.pages;

import com.frenkvs.devmod.rendering.HeatmapVisualizer;
import com.frenkvs.devmod.rendering.HeatmapVisualizer.HeatmapType;
import com.frenkvs.devmod.rendering.LightLevelOverlay;
import com.frenkvs.devmod.rendering.SafeSpotVisualizer;
import com.frenkvs.devmod.rendering.VerticalLevelsVisualizer;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.unified.SettingsCategory;
import com.frenkvs.devmod.ui.unified.SettingsPage;
import com.frenkvs.devmod.ui.unified.persistence.SettingsData;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;

/**
 * Visualizers settings page with controls for heatmaps, light levels,
 * safe spots, and vertical level analysis.
 */
@SuppressWarnings("null") // Minecraft APIs lack null annotations
public class VisualizersPage implements SettingsPage {

    private static final int ROW_HEIGHT = 24;
    private static final int SECTION_SPACING = 16;
    private static final int SCROLLBAR_WIDTH = 6;

    // Scrolling state
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private int visibleHeight = 0;
    private int totalContentHeight = 0;
    private boolean isDraggingScrollbar = false;
    private int lastContentY, lastContentHeight;

    // Light level settings
    private int lightLevelRadius;

    // View distance settings
    private int viewDistance;

    // Slider drag state
    private boolean draggingLightSlider = false;
    private boolean draggingViewDistSlider = false;
    private int sliderContentX, sliderWidth;
    private int viewDistSliderX, viewDistSliderWidth;

    @Override
    public SettingsCategory getCategory() {
        return SettingsCategory.VISUALIZERS;
    }

    @Override
    public String getTitle() {
        return "Visualizers";
    }

    @Override
    public void init() {
        lightLevelRadius = LightLevelOverlay.INSTANCE.getRadius();
        viewDistance = SettingsManager.INSTANCE.getSettings().visualizers.getRenderDistance();
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

        // === SECTION: Light Level Overlay ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Light Level Overlay [L]");
        currentY += ROW_HEIGHT;

        // Light Level toggle
        currentY = renderToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
                "Light Levels", "Show spawn-safe light on blocks",
                LightLevelOverlay.INSTANCE.isEnabled());

        // Radius slider
        // Calculate effective width accounting for scrollbar if present
        int effectiveWidth = totalContentHeight > visibleHeight ? width - SCROLLBAR_WIDTH - 8 : width;
        graphics.drawString(font, "Radius: " + lightLevelRadius + " blocks", x, currentY + 4, UIConstants.Text.SECONDARY, false);
        int labelWidth = 120;
        int buttonsWidth = 56; // Two 20px buttons + 8px gap + 8px margin
        int sliderX = x + labelWidth;
        // Calculate slider width to fit: label + slider + buttons within effective width
        this.sliderWidth = Math.max(60, Math.min(effectiveWidth - labelWidth - buttonsWidth, 200));
        this.sliderContentX = sliderX;
        renderSlider(graphics, sliderX, currentY + 2, this.sliderWidth, 12,
                (lightLevelRadius - 4) / 28.0f, mouseX, mouseY);

        // [-] [+] buttons - positioned after slider with proper spacing
        int minusBtnX = sliderX + sliderWidth + 8;
        int plusBtnX = minusBtnX + 24;
        boolean minusHovered = isMouseOver(mouseX, mouseY, minusBtnX, currentY, 20, 16);
        boolean plusHovered = isMouseOver(mouseX, mouseY, plusBtnX, currentY, 20, 16);
        AxiomRenderer.drawButton(graphics, font, minusBtnX, currentY, 20, 16, "-", minusHovered, false);
        AxiomRenderer.drawButton(graphics, font, plusBtnX, currentY, 20, 16, "+", plusHovered, false);
        currentY += ROW_HEIGHT;

        // Separator
        currentY += 8;
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += SECTION_SPACING;

        // === SECTION: Heatmaps ===
        int activeCount = countActiveHeatmaps();
        String heatmapTitle = activeCount > 0
            ? "Heatmaps (" + activeCount + " active) [H to cycle]"
            : "Heatmaps [H to cycle]";
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, heatmapTitle);

        // "Disable All" button if any active
        if (activeCount > 0) {
            int btnX = x + width - 80;
            boolean btnHovered = isMouseOver(mouseX, mouseY, btnX, currentY - 2, 70, 16);
            AxiomRenderer.drawButton(graphics, font, btnX, currentY - 2, 70, 16, "Clear All", btnHovered, false);
        }
        currentY += ROW_HEIGHT;

        // Heatmap type toggles
        currentY = renderHeatmapToggle(graphics, font, x, currentY, width, mouseX, mouseY,
                "Death Heatmap", HeatmapType.DEATH, 0xFFFF0000);

        currentY = renderHeatmapToggle(graphics, font, x, currentY, width, mouseX, mouseY,
                "Movement Heatmap", HeatmapType.MOVEMENT, 0xFF00FF00);

        currentY = renderHeatmapToggle(graphics, font, x, currentY, width, mouseX, mouseY,
                "Camping Detection", HeatmapType.CAMPING, 0xFFFFFF00);

        currentY = renderHeatmapToggle(graphics, font, x, currentY, width, mouseX, mouseY,
                "Stuck Points", HeatmapType.STUCK, 0xFFFF8000);

        currentY = renderHeatmapToggle(graphics, font, x, currentY, width, mouseX, mouseY,
                "Aggro Drop Points", HeatmapType.AGGRO_DROP, 0xFF8000FF);

        currentY = renderHeatmapToggle(graphics, font, x, currentY, width, mouseX, mouseY,
                "Kiting Paths", HeatmapType.KITING, 0xFF00FFFF);

        currentY = renderHeatmapToggle(graphics, font, x, currentY, width, mouseX, mouseY,
                "Light Spawnable", HeatmapType.LIGHT_SPAWNABLE, 0xFFFF0000);

        currentY = renderHeatmapToggle(graphics, font, x, currentY, width, mouseX, mouseY,
                "Light Dark Areas", HeatmapType.LIGHT_DARK, 0xFFFF8800);

        // Separator
        currentY += 8;
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += SECTION_SPACING;

        // === SECTION: Spatial Analysis ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Spatial Analysis");
        currentY += ROW_HEIGHT;

        // Safe Spot Visualizer
        currentY = renderToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
                "Safe Spots [C]", "Highlight camping/safe positions",
                SafeSpotVisualizer.INSTANCE.isEnabled());

        // Vertical Levels
        currentY = renderToggleRow(graphics, font, x, currentY, width, mouseX, mouseY,
                "Vertical Levels [Y]", "Show room height zones",
                VerticalLevelsVisualizer.INSTANCE.isEnabled());

        // Separator
        currentY += 8;
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += SECTION_SPACING;

        // === SECTION: Performance ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Performance");
        currentY += ROW_HEIGHT;

        // View Distance slider
        // Calculate effective width accounting for scrollbar if present (reuse from earlier)
        graphics.drawString(font, "View Distance: " + viewDistance + " blocks", x, currentY + 4, UIConstants.Text.SECONDARY, false);
        int vdLabelWidth = 140;
        int vdButtonsWidth = 56; // Two 20px buttons + 8px gap + 8px margin
        int vdSliderX = x + vdLabelWidth;
        // Calculate slider width to fit: label + slider + buttons within effective width
        this.viewDistSliderWidth = Math.max(60, Math.min(effectiveWidth - vdLabelWidth - vdButtonsWidth, 180));
        this.viewDistSliderX = vdSliderX;
        float vdPercentage = (float) (viewDistance - SettingsData.VisualizerSettings.MIN_RENDER_DISTANCE) /
                             (SettingsData.VisualizerSettings.MAX_RENDER_DISTANCE - SettingsData.VisualizerSettings.MIN_RENDER_DISTANCE);
        renderSlider(graphics, vdSliderX, currentY + 2, this.viewDistSliderWidth, 12, vdPercentage, mouseX, mouseY);

        // [-] [+] buttons for view distance - positioned after slider with proper spacing
        int vdMinusBtnX = vdSliderX + viewDistSliderWidth + 8;
        int vdPlusBtnX = vdMinusBtnX + 24;
        boolean vdMinusHovered = isMouseOver(mouseX, mouseY, vdMinusBtnX, currentY, 20, 16);
        boolean vdPlusHovered = isMouseOver(mouseX, mouseY, vdPlusBtnX, currentY, 20, 16);
        AxiomRenderer.drawButton(graphics, font, vdMinusBtnX, currentY, 20, 16, "-", vdMinusHovered, false);
        AxiomRenderer.drawButton(graphics, font, vdPlusBtnX, currentY, 20, 16, "+", vdPlusHovered, false);
        currentY += ROW_HEIGHT;

        // Hint about view distance
        graphics.drawString(font, "Affects all visualizers render range", x, currentY, UIConstants.Text.MUTED, false);
        currentY += 16;

        // Hint
        currentY += 8;
        AxiomRenderer.drawHint(graphics, font, x, currentY, "Keybinds shown in brackets work in-game.");

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
        int h = 0;
        // Light Level section
        h += ROW_HEIGHT; // header
        h += ROW_HEIGHT; // toggle
        h += ROW_HEIGHT; // slider
        h += 8 + SECTION_SPACING; // separator

        // Heatmaps section
        h += ROW_HEIGHT; // header
        h += 20 * 8; // 8 heatmap toggles (20px each)
        h += 8 + SECTION_SPACING; // separator

        // Spatial Analysis section
        h += ROW_HEIGHT; // header
        h += ROW_HEIGHT * 2; // 2 toggles
        h += 8 + SECTION_SPACING; // separator

        // Performance section
        h += ROW_HEIGHT; // header
        h += ROW_HEIGHT; // slider
        h += 16; // hint
        h += 8 + 20; // final hint

        return h;
    }

    /**
     * Render the scrollbar.
     */
    private void renderScrollbar(GuiGraphics graphics, int x, int y, int barWidth, int height) {
        graphics.fill(x, y, x + barWidth, y + height, UIConstants.Background.INPUT);
        float visibleRatio = (float) visibleHeight / totalContentHeight;
        int thumbHeight = Math.max(20, (int) (height * visibleRatio));
        float scrollRatio = maxScrollOffset > 0 ? (float) scrollOffset / maxScrollOffset : 0;
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);
        int thumbColor = isDraggingScrollbar ? UIConstants.Border.ACCENT : UIConstants.Border.DEFAULT;
        graphics.fill(x, thumbY, x + barWidth, thumbY + thumbHeight, thumbColor);
    }

    private int renderToggleRow(GuiGraphics graphics, Font font, int x, int y, int width,
                                 int mouseX, int mouseY, String label, String description, boolean enabled) {
        int rowWidth = width - 20;
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;

        if (hovered) {
            graphics.fill(x - 4, y - 2, x + rowWidth + 4, y + ROW_HEIGHT - 2, UIConstants.Background.HOVER);
        }

        // Label and description
        graphics.drawString(font, label, x, y + 2, UIConstants.Text.PRIMARY, false);
        graphics.drawString(font, description, x, y + 12, UIConstants.Text.MUTED, false);

        // Toggle - use standardized dimensions
        int toggleX = x + rowWidth - UIConstants.Size.TOGGLE_WIDTH;
        int toggleY = y + (ROW_HEIGHT - UIConstants.Size.TOGGLE_HEIGHT) / 2;
        AxiomRenderer.drawToggle(graphics, font, toggleX, toggleY, UIConstants.Size.TOGGLE_WIDTH, UIConstants.Size.TOGGLE_HEIGHT, enabled, hovered);

        return y + ROW_HEIGHT;
    }

    private int renderHeatmapToggle(GuiGraphics graphics, Font font, int x, int y, int width,
                                     int mouseX, int mouseY, String label, HeatmapType type, int color) {
        int rowWidth = width - 20;
        boolean enabled = HeatmapVisualizer.INSTANCE.isEnabled(type);
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + 20;

        if (hovered) {
            graphics.fill(x - 4, y - 2, x + rowWidth + 4, y + 18, UIConstants.Background.HOVER);
        }

        // Color indicator
        graphics.fill(x, y + 2, x + 12, y + 14, color);
        AxiomRenderer.drawBorder(graphics, x, y + 2, 12, 12, UIConstants.Border.DEFAULT);

        // Label
        graphics.drawString(font, label, x + 18, y + 4, UIConstants.Text.PRIMARY, false);

        // Toggle - use standardized dimensions
        int toggleX = x + rowWidth - UIConstants.Size.TOGGLE_WIDTH;
        AxiomRenderer.drawToggle(graphics, font, toggleX, y, UIConstants.Size.TOGGLE_WIDTH, UIConstants.Size.TOGGLE_HEIGHT, enabled, hovered);

        return y + 20;
    }

    private void renderSlider(GuiGraphics graphics, int x, int y, int width, int height,
                               float value, int mouseX, int mouseY) {
        // Background track
        graphics.fill(x, y + height / 2 - 2, x + width, y + height / 2 + 2, UIConstants.Background.INPUT);

        // Fill track
        int fillWidth = (int) (width * value);
        graphics.fill(x, y + height / 2 - 2, x + fillWidth, y + height / 2 + 2, UIConstants.Border.ACCENT);

        // Handle
        int handleX = x + fillWidth - 4;
        graphics.fill(handleX, y, handleX + 8, y + height, UIConstants.Border.DEFAULT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0) return false;

        // Check if click is within content area
        if (mouseX < contentX || mouseX > contentX + contentWidth ||
            mouseY < contentY || mouseY > contentY + lastContentHeight) {
            return false;
        }

        // Check scrollbar click
        if (totalContentHeight > visibleHeight) {
            int scrollbarX = contentX + contentWidth - SCROLLBAR_WIDTH - 2;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH + 2) {
                isDraggingScrollbar = true;
                // Calculate thumb dimensions for accurate click position
                float visibleRatio = (float) visibleHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int) (visibleHeight * visibleRatio));
                int trackHeight = visibleHeight - thumbHeight;
                if (trackHeight > 0) {
                    float clickRatio = (float)(mouseY - contentY - thumbHeight / 2) / trackHeight;
                    clickRatio = Math.max(0, Math.min(1, clickRatio));
                    scrollOffset = (int)(maxScrollOffset * clickRatio);
                }
                return true;
            }
        }

        int rowWidth = contentWidth - 20 - SCROLLBAR_WIDTH;
        // Apply scroll offset for hit detection
        int y = contentY - scrollOffset;

        // Skip section header
        y += ROW_HEIGHT;

        // Light Level toggle
        if (isInToggleArea(mouseX, mouseY, contentX, y, rowWidth)) {
            boolean wasEnabled = LightLevelOverlay.INSTANCE.isEnabled();
            LightLevelOverlay.INSTANCE.toggle();
            if (wasEnabled) UIConstants.Sound.toggleOff(); else UIConstants.Sound.toggleOn();
            return true;
        }
        y += ROW_HEIGHT;

        // Radius controls - slider click/drag
        int sliderX = contentX + 120;
        int localSliderWidth = Math.min(contentWidth - 140, 200);
        int minusBtnX = sliderX + localSliderWidth + 8;
        int plusBtnX = minusBtnX + 24;

        // Click on slider to set value directly
        if (isMouseOver((int) mouseX, (int) mouseY, sliderX, y, localSliderWidth, 16)) {
            setRadiusFromSliderPosition((int) mouseX, sliderX, localSliderWidth);
            draggingLightSlider = true;
            return true;
        }

        if (isMouseOver((int) mouseX, (int) mouseY, minusBtnX, y, 20, 16)) {
            lightLevelRadius = Math.max(4, lightLevelRadius - 4);
            LightLevelOverlay.INSTANCE.setRadius(lightLevelRadius);
            return true;
        }
        if (isMouseOver((int) mouseX, (int) mouseY, plusBtnX, y, 20, 16)) {
            lightLevelRadius = Math.min(32, lightLevelRadius + 4);
            LightLevelOverlay.INSTANCE.setRadius(lightLevelRadius);
            return true;
        }
        y += ROW_HEIGHT;

        // Separator space
        y += 8 + SECTION_SPACING;

        // Skip section header - but check for "Clear All" button click first
        int btnX = contentX + contentWidth - 80;
        if (countActiveHeatmaps() > 0 && isMouseOver((int) mouseX, (int) mouseY, btnX, y - 2, 70, 16)) {
            // Disable all heatmaps
            for (HeatmapType type : HeatmapType.values()) {
                HeatmapVisualizer.INSTANCE.setEnabled(type, false);
            }
            UIConstants.Sound.delete();
            return true;
        }
        y += ROW_HEIGHT;

        // Heatmap toggles - use all enum values for consistency
        for (HeatmapType type : HeatmapType.values()) {
            if (isInToggleArea(mouseX, mouseY, contentX, y, rowWidth)) {
                boolean wasEnabled = HeatmapVisualizer.INSTANCE.isEnabled(type);
                HeatmapVisualizer.INSTANCE.toggle(type);
                if (wasEnabled) UIConstants.Sound.toggleOff(); else UIConstants.Sound.toggleOn();
                return true;
            }
            y += 20;
        }

        // Separator space
        y += 8 + SECTION_SPACING;

        // Skip section header
        y += ROW_HEIGHT;

        // Safe Spot toggle
        if (isInToggleArea(mouseX, mouseY, contentX, y, rowWidth)) {
            boolean wasEnabled = SafeSpotVisualizer.INSTANCE.isEnabled();
            SafeSpotVisualizer.INSTANCE.toggle();
            if (wasEnabled) UIConstants.Sound.toggleOff(); else UIConstants.Sound.toggleOn();
            return true;
        }
        y += ROW_HEIGHT;

        // Vertical Levels toggle
        if (isInToggleArea(mouseX, mouseY, contentX, y, rowWidth)) {
            boolean wasEnabled = VerticalLevelsVisualizer.INSTANCE.isEnabled();
            VerticalLevelsVisualizer.INSTANCE.toggle();
            if (wasEnabled) UIConstants.Sound.toggleOff(); else UIConstants.Sound.toggleOn();
            return true;
        }
        y += ROW_HEIGHT;

        // Separator space
        y += 8 + SECTION_SPACING;

        // Skip section header (Performance)
        y += ROW_HEIGHT;

        // View Distance controls
        int vdSliderX = contentX + 140;
        int localVdSliderWidth = Math.min(contentWidth - 160, 180);
        int vdMinusBtnX = vdSliderX + localVdSliderWidth + 8;
        int vdPlusBtnX = vdMinusBtnX + 24;

        // Click on view distance slider
        if (isMouseOver((int) mouseX, (int) mouseY, vdSliderX, y, localVdSliderWidth, 16)) {
            setViewDistFromSliderPosition((int) mouseX, vdSliderX, localVdSliderWidth);
            draggingViewDistSlider = true;
            return true;
        }

        // View distance minus button
        if (isMouseOver((int) mouseX, (int) mouseY, vdMinusBtnX, y, 20, 16)) {
            viewDistance = Math.max(SettingsData.VisualizerSettings.MIN_RENDER_DISTANCE, viewDistance - 8);
            SettingsManager.INSTANCE.getSettings().visualizers.setRenderDistance(viewDistance);
            SettingsManager.INSTANCE.markDirty();
            SettingsManager.INSTANCE.save();
            return true;
        }

        // View distance plus button
        if (isMouseOver((int) mouseX, (int) mouseY, vdPlusBtnX, y, 20, 16)) {
            viewDistance = Math.min(SettingsData.VisualizerSettings.MAX_RENDER_DISTANCE, viewDistance + 8);
            SettingsManager.INSTANCE.getSettings().visualizers.setRenderDistance(viewDistance);
            SettingsManager.INSTANCE.markDirty();
            SettingsManager.INSTANCE.save();
            return true;
        }

        return false;
    }

    private boolean isInToggleArea(double mouseX, double mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX < x + width && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2;
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Counts how many heatmap types are currently enabled.
     */
    private int countActiveHeatmaps() {
        int count = 0;
        for (HeatmapType type : HeatmapType.values()) {
            if (HeatmapVisualizer.INSTANCE.isEnabled(type)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Calculate and set radius from mouse X position on slider.
     */
    private void setRadiusFromSliderPosition(int mouseX, int sliderX, int sliderWidthParam) {
        float percentage = (float) (mouseX - sliderX) / sliderWidthParam;
        percentage = Math.max(0, Math.min(1, percentage)); // Clamp 0-1
        // Range is 4-32 (28 range), step by 4 for clean values
        int rawValue = (int) (4 + percentage * 28);
        // Round to nearest multiple of 4
        lightLevelRadius = Math.round(rawValue / 4.0f) * 4;
        lightLevelRadius = Math.max(4, Math.min(32, lightLevelRadius));
        LightLevelOverlay.INSTANCE.setRadius(lightLevelRadius);
    }

    /**
     * Calculate and set view distance from mouse X position on slider.
     */
    private void setViewDistFromSliderPosition(int mouseX, int sliderX, int sliderWidthParam) {
        float percentage = (float) (mouseX - sliderX) / sliderWidthParam;
        percentage = Math.max(0, Math.min(1, percentage)); // Clamp 0-1
        // Range is 16-128 (112 range), step by 8 for clean values
        int range = SettingsData.VisualizerSettings.MAX_RENDER_DISTANCE - SettingsData.VisualizerSettings.MIN_RENDER_DISTANCE;
        int rawValue = (int) (SettingsData.VisualizerSettings.MIN_RENDER_DISTANCE + percentage * range);
        // Round to nearest multiple of 8
        viewDistance = Math.round(rawValue / 8.0f) * 8;
        viewDistance = Math.max(SettingsData.VisualizerSettings.MIN_RENDER_DISTANCE,
                                Math.min(SettingsData.VisualizerSettings.MAX_RENDER_DISTANCE, viewDistance));
        SettingsManager.INSTANCE.getSettings().visualizers.setRenderDistance(viewDistance);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            // Scrollbar drag
            if (isDraggingScrollbar && maxScrollOffset > 0) {
                // Calculate thumb dimensions for accurate drag
                float visibleRatio = (float) visibleHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int) (visibleHeight * visibleRatio));
                int trackHeight = visibleHeight - thumbHeight;
                if (trackHeight > 0) {
                    float dragRatio = (float)(mouseY - lastContentY - thumbHeight / 2) / trackHeight;
                    dragRatio = Math.max(0, Math.min(1, dragRatio));
                    scrollOffset = (int)(maxScrollOffset * dragRatio);
                }
                return true;
            }
            if (draggingLightSlider) {
                setRadiusFromSliderPosition((int) mouseX, sliderContentX, sliderWidth);
                return true;
            }
            if (draggingViewDistSlider) {
                setViewDistFromSliderPosition((int) mouseX, viewDistSliderX, viewDistSliderWidth);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isDraggingScrollbar) {
                isDraggingScrollbar = false;
                return true;
            }
            if (draggingLightSlider) {
                draggingLightSlider = false;
                return true;
            }
            if (draggingViewDistSlider) {
                draggingViewDistSlider = false;
                // Save when slider released
                SettingsManager.INSTANCE.markDirty();
                SettingsManager.INSTANCE.save();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        return true;
    }

    @Override
    public void resetToDefaults() {
        // Disable all visualizers
        LightLevelOverlay.INSTANCE.setEnabled(false);
        lightLevelRadius = 16;
        LightLevelOverlay.INSTANCE.setRadius(16);

        for (HeatmapType type : HeatmapType.values()) {
            HeatmapVisualizer.INSTANCE.setEnabled(type, false);
        }

        SafeSpotVisualizer.INSTANCE.setEnabled(false);
        VerticalLevelsVisualizer.INSTANCE.setEnabled(false);
    }

    @Override
    public int getContentHeight() {
        return 450;
    }
}
