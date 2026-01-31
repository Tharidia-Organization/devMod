package com.devmod.client.ui.unified.pages;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.rendering.HeatmapVisualizer;
import com.devmod.client.rendering.LightLevelOverlay;
import com.devmod.client.rendering.SafeSpotVisualizer;
import com.devmod.client.rendering.VerticalLevelsVisualizer;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.unified.SettingsPage;
import com.devmod.client.ui.unified.persistence.SettingsData;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.rendering.HeatmapType;

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
    private boolean showScrollbar = false;
    private int lastContentX, lastContentWidth;
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

    // Slider pulse animations (0-1, decays over time)
    private float lightSliderPulse = 0f;
    private float viewDistSliderPulse = 0f;

    // Buttons
    private final EditorButton lightMinusBtn = new EditorButton("viz-light-minus", "-");
    private final EditorButton lightPlusBtn = new EditorButton("viz-light-plus", "+");
    private final EditorButton clearAllBtn = new EditorButton("viz-clear-all", "Clear All");
    private final EditorButton vdMinusBtn = new EditorButton("viz-vd-minus", "-");
    private final EditorButton vdPlusBtn = new EditorButton("viz-vd-plus", "+");

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
    public void render(@Nonnull GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        // Decay slider pulse animations
        if (lightSliderPulse > 0) lightSliderPulse = Math.max(0, lightSliderPulse - 0.05f);
        if (viewDistSliderPulse > 0) viewDistSliderPulse = Math.max(0, viewDistSliderPulse - 0.05f);

        // Store dimensions for scroll calculations
        lastContentX = x;
        lastContentY = y;
        lastContentWidth = width;
        lastContentHeight = height;
        visibleHeight = height;

        // Calculate total content height
        totalContentHeight = calculateContentHeight();
        maxScrollOffset = Math.max(0, totalContentHeight - visibleHeight);
        showScrollbar = totalContentHeight > visibleHeight;

        // Clamp scroll offset
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));

        // Enable scissoring to clip content outside bounds with try/finally for safety
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            // Apply scroll offset to starting Y position
            int currentY = y - scrollOffset;

            // === SECTION: Light Level Overlay ===
            AxiomRenderer.drawSectionHeader(graphics, safeFont, x, currentY, "Light Level Overlay");
            currentY += ROW_HEIGHT;

            // Light Level toggle
            currentY = renderToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                    "Light Levels", "Show spawn-safe light on blocks",
                    LightLevelOverlay.INSTANCE.isEnabled());

            // Radius slider
            // Calculate effective width accounting for scrollbar if present
            int effectiveWidth = showScrollbar ? Math.max(0, width - SCROLLBAR_WIDTH - 8) : width;
            UIScaleManager.drawScaledString(graphics, safeFont, "Radius: " + lightLevelRadius + " blocks", x, currentY + 4, DesignTokens.Text.SECONDARY, false);
            int labelWidth = 120;
            int buttonsWidth = 56; // Two 20px buttons + 8px gap + 8px margin
            int sliderX = x + labelWidth;
            // Calculate slider width to fit: label + slider + buttons within effective width
            this.sliderWidth = Math.max(60, Math.min(effectiveWidth - labelWidth - buttonsWidth, 200));
            this.sliderContentX = sliderX;
            renderSlider(graphics, sliderX, currentY + 2, this.sliderWidth, 12,
                    (lightLevelRadius - 4) / 28.0f, mouseX, mouseY, lightSliderPulse);

            // [-] [+] buttons - positioned after slider with proper spacing
            int minusBtnX = sliderX + sliderWidth + 8;
            int plusBtnX = minusBtnX + 24;
            lightMinusBtn
                .style(EditorButton.Style.NORMAL)
                .onClick(() -> {
                    lightLevelRadius = Math.max(4, lightLevelRadius - 4);
                    LightLevelOverlay.INSTANCE.setRadius(lightLevelRadius);
                    lightSliderPulse = 1.0f;
                });
            lightMinusBtn.render(graphics, minusBtnX, currentY, 20, 16, mouseX, mouseY);

            lightPlusBtn
                .style(EditorButton.Style.NORMAL)
                .onClick(() -> {
                    lightLevelRadius = Math.min(32, lightLevelRadius + 4);
                    LightLevelOverlay.INSTANCE.setRadius(lightLevelRadius);
                    lightSliderPulse = 1.0f;
                });
            lightPlusBtn.render(graphics, plusBtnX, currentY, 20, 16, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            // Separator
            currentY += 8;
            AxiomRenderer.drawSeparator(graphics, x, currentY, width);
            currentY += SECTION_SPACING;

            // === SECTION: Heatmaps ===
            int activeCount = countActiveHeatmaps();
            String heatmapTitle = activeCount > 0
                ? "Heatmaps (" + activeCount + " active)"
                : "Heatmaps";
            AxiomRenderer.drawSectionHeader(graphics, safeFont, x, currentY, heatmapTitle);

            // "Disable All" button if any active
            if (activeCount > 0) {
                int btnX = x + width - 80;
                clearAllBtn
                    .style(EditorButton.Style.DANGER)
                    .onClick(this::disableAllHeatmaps);
                clearAllBtn.render(graphics, btnX, currentY - 2, 70, 16, mouseX, mouseY);
            }
            currentY += ROW_HEIGHT;

            // Heatmap type toggles
            currentY = renderHeatmapToggle(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                    "Death Heatmap", HeatmapType.DEATH, DesignTokens.Heatmap.DEATH);

            currentY = renderHeatmapToggle(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                    "Movement Heatmap", HeatmapType.MOVEMENT, DesignTokens.Heatmap.MOVEMENT);

            currentY = renderHeatmapToggle(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                    "Camping Detection", HeatmapType.CAMPING, DesignTokens.Heatmap.CAMPING);

            currentY = renderHeatmapToggle(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                    "Stuck Points", HeatmapType.STUCK, DesignTokens.Heatmap.STUCK);

            currentY = renderHeatmapToggle(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                    "Aggro Drop Points", HeatmapType.AGGRO_DROP, DesignTokens.Heatmap.AGGRO_DROP);

            currentY = renderHeatmapToggle(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                    "Kiting Paths", HeatmapType.KITING, DesignTokens.Heatmap.KITING);

            currentY = renderHeatmapToggle(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                    "Light Spawnable", HeatmapType.LIGHT_SPAWNABLE, DesignTokens.Heatmap.LIGHT_SPAWNABLE);

            currentY = renderHeatmapToggle(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                    "Light Dark Areas", HeatmapType.LIGHT_DARK, DesignTokens.Heatmap.LIGHT_DARK);

            // Separator
            currentY += 8;
            AxiomRenderer.drawSeparator(graphics, x, currentY, width);
            currentY += SECTION_SPACING;

            // === SECTION: Spatial Analysis ===
            AxiomRenderer.drawSectionHeader(graphics, safeFont, x, currentY, "Spatial Analysis");
            currentY += ROW_HEIGHT;

            // Safe Spot Visualizer
            currentY = renderToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                    "Safe Spots", "Highlight camping/safe positions",
                    SafeSpotVisualizer.INSTANCE.isEnabled());

            // Vertical Levels
            currentY = renderToggleRow(graphics, safeFont, x, currentY, width, mouseX, mouseY,
                    "Vertical Levels", "Show room height zones",
                    VerticalLevelsVisualizer.INSTANCE.isEnabled());

            // Separator
            currentY += 8;
            AxiomRenderer.drawSeparator(graphics, x, currentY, width);
            currentY += SECTION_SPACING;

            // === SECTION: Performance ===
            AxiomRenderer.drawSectionHeader(graphics, safeFont, x, currentY, "Performance");
            currentY += ROW_HEIGHT;

            // View Distance slider
            // Calculate effective width accounting for scrollbar if present (reuse from earlier)
            UIScaleManager.drawScaledString(graphics, safeFont, "View Distance: " + viewDistance + " blocks", x, currentY + 4, DesignTokens.Text.SECONDARY, false);
            int vdLabelWidth = 140;
            int vdButtonsWidth = 56; // Two 20px buttons + 8px gap + 8px margin
            int vdSliderX = x + vdLabelWidth;
            // Calculate slider width to fit: label + slider + buttons within effective width
            this.viewDistSliderWidth = Math.max(60, Math.min(effectiveWidth - vdLabelWidth - vdButtonsWidth, 180));
            this.viewDistSliderX = vdSliderX;
            float vdPercentage = (float) (viewDistance - SettingsData.VisualizerSettings.MIN_RENDER_DISTANCE) /
                                 (SettingsData.VisualizerSettings.MAX_RENDER_DISTANCE - SettingsData.VisualizerSettings.MIN_RENDER_DISTANCE);
            renderSlider(graphics, vdSliderX, currentY + 2, this.viewDistSliderWidth, 12, vdPercentage, mouseX, mouseY, viewDistSliderPulse);

            // [-] [+] buttons for view distance - positioned after slider with proper spacing
            int vdMinusBtnX = vdSliderX + viewDistSliderWidth + 8;
            int vdPlusBtnX = vdMinusBtnX + 24;
            vdMinusBtn
                .style(EditorButton.Style.NORMAL)
                .onClick(() -> {
                    viewDistance = Math.max(SettingsData.VisualizerSettings.MIN_RENDER_DISTANCE, viewDistance - 8);
                    SettingsManager.INSTANCE.getSettings().visualizers.setRenderDistance(viewDistance);
                    viewDistSliderPulse = 1.0f;
                });
            vdMinusBtn.render(graphics, vdMinusBtnX, currentY, 20, 16, mouseX, mouseY);

            vdPlusBtn
                .style(EditorButton.Style.NORMAL)
                .onClick(() -> {
                    viewDistance = Math.min(SettingsData.VisualizerSettings.MAX_RENDER_DISTANCE, viewDistance + 8);
                    SettingsManager.INSTANCE.getSettings().visualizers.setRenderDistance(viewDistance);
                    viewDistSliderPulse = 1.0f;
                });
            vdPlusBtn.render(graphics, vdPlusBtnX, currentY, 20, 16, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            // Hint about view distance
            UIScaleManager.drawScaledString(graphics, safeFont, "Affects all visualizers render range", x, currentY, DesignTokens.Text.MUTED, false);
            currentY += 16;

            // Hint
            currentY += 8;
            AxiomRenderer.drawHint(graphics, font, x, currentY, "Assign keybinds in Controls for quick access.");
        } finally {
            // Disable scissoring
            graphics.disableScissor();
        }

        // Render scrollbar if content exceeds visible area
        if (showScrollbar) {
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
        graphics.fill(x, y, x + barWidth, y + height, DesignTokens.Bg.LEVEL_0);
        float visibleRatio = (float) visibleHeight / totalContentHeight;
        int thumbHeight = Math.max(20, (int) (height * visibleRatio));
        float scrollRatio = maxScrollOffset > 0 ? (float) scrollOffset / maxScrollOffset : 0;
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);
        int thumbColor = isDraggingScrollbar ? DesignTokens.Accent.PRIMARY : DesignTokens.Stroke.DEFAULT;
        graphics.fill(x, thumbY, x + barWidth, thumbY + thumbHeight, thumbColor);
    }

    private int renderToggleRow(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                                 int mouseX, int mouseY, String label, String description, boolean enabled) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        int rowWidth = width - 20 - (showScrollbar ? SCROLLBAR_WIDTH + 4 : 0);
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;

        if (hovered) {
            graphics.fill(x - 4, y - 2, x + rowWidth + 4, y + ROW_HEIGHT - 2, DesignTokens.Surface.LEVEL_1);
        }

        // Label and description
        UIScaleManager.drawScaledString(graphics, safeFont, label, x, y + 2, DesignTokens.Text.PRIMARY, false);
        UIScaleManager.drawScaledString(graphics, safeFont, description, x, y + 12, DesignTokens.Text.MUTED, false);

        // Toggle - use standardized dimensions
        int toggleX = x + rowWidth - 36;
        int toggleY = y + (ROW_HEIGHT - 18) / 2;
        AxiomRenderer.drawToggle(graphics, safeFont, toggleX, toggleY, 36, 18, enabled, hovered);

        return y + ROW_HEIGHT;
    }

    private int renderHeatmapToggle(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                                     int mouseX, int mouseY, String label, HeatmapType type, int color) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        int rowWidth = width - 20 - (showScrollbar ? SCROLLBAR_WIDTH + 4 : 0);
        boolean enabled = HeatmapVisualizer.INSTANCE.isEnabled(type);
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + 20;

        if (hovered) {
            graphics.fill(x - 4, y - 2, x + rowWidth + 4, y + 18, DesignTokens.Surface.LEVEL_1);
        }

        // Color indicator
        graphics.fill(x, y + 2, x + 12, y + 14, color);
        AxiomRenderer.drawBorder(graphics, x, y + 2, 12, 12, DesignTokens.Stroke.DEFAULT);

        // Label
        UIScaleManager.drawScaledString(graphics, safeFont, label, x + 18, y + 4, DesignTokens.Text.PRIMARY, false);

        // Toggle - use standardized dimensions
        int toggleX = x + rowWidth - 36;
        AxiomRenderer.drawToggle(graphics, safeFont, toggleX, y, 36, 18, enabled, hovered);

        return y + 20;
    }

    private void disableAllHeatmaps() {
        invokeAction(ActionIds.DEBUG_HEATMAP_CLEAR_ALL);
        // Sound handled by EditorButton
    }

    private void invokeAction(String actionId) {
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
    }

    private String heatmapActionId(HeatmapType type) {
        return switch (type) {
            case DEATH -> ActionIds.DEBUG_HEATMAP_DEATH_TOGGLE;
            case MOVEMENT -> ActionIds.DEBUG_HEATMAP_MOVEMENT_TOGGLE;
            case CAMPING -> ActionIds.DEBUG_HEATMAP_CAMPING_TOGGLE;
            case STUCK -> ActionIds.DEBUG_HEATMAP_STUCK_TOGGLE;
            case AGGRO_DROP -> ActionIds.DEBUG_HEATMAP_AGGRO_DROP_TOGGLE;
            case KITING -> ActionIds.DEBUG_HEATMAP_KITING_TOGGLE;
            case LIGHT_SPAWNABLE -> ActionIds.DEBUG_HEATMAP_LIGHT_SPAWNABLE_TOGGLE;
            case LIGHT_DARK -> ActionIds.DEBUG_HEATMAP_LIGHT_DARK_TOGGLE;
        };
    }

    private void renderSlider(GuiGraphics graphics, int x, int y, int width, int height,
                               float value, int mouseX, int mouseY, float pulseAnimation) {
        // Background track
        graphics.fill(x, y + height / 2 - 2, x + width, y + height / 2 + 2, DesignTokens.Bg.LEVEL_0);

        // Fill track
        int fillWidth = (int) (width * value);
        graphics.fill(x, y + height / 2 - 2, x + fillWidth, y + height / 2 + 2, DesignTokens.Accent.PRIMARY);

        // Handle
        int handleX = x + fillWidth - 4;
        boolean hovered = isMouseOver(mouseX, mouseY, x, y, width, height);

        // Pulse animation effect (expands glow when value changes)
        int pulseExpand = (int) (pulseAnimation * 4);
        if (pulseAnimation > 0 || hovered) {
            int glowAlpha = 60 + (int) (pulseAnimation * 100);
            graphics.fill(handleX - 2 - pulseExpand, y - pulseExpand,
                handleX + 10 + pulseExpand, y + height + pulseExpand,
                DesignTokens.withAlpha(DesignTokens.Accent.PRIMARY, glowAlpha));
        }

        graphics.fill(handleX, y, handleX + 8, y + height, DesignTokens.Stroke.DEFAULT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0) return false;

        if (!isMouseOverContent(mouseX, mouseY)) {
            return false;
        }

        // Check scrollbar click
        if (showScrollbar) {
            int scrollbarX = contentX + contentWidth - SCROLLBAR_WIDTH - 2;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH + 2) {
                isDraggingScrollbar = true;
                // Calculate thumb dimensions for accurate click position
                float visibleRatio = (float) visibleHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int) (visibleHeight * visibleRatio));
                int trackHeight = visibleHeight - thumbHeight;
                if (trackHeight > 0) {
                    float clickRatio = (float)(mouseY - contentY - thumbHeight / 2.0) / trackHeight;
                    clickRatio = Math.max(0, Math.min(1, clickRatio));
                    scrollOffset = (int)(maxScrollOffset * clickRatio);
                }
                return true;
            }
        }

        int rowWidth = contentWidth - 20 - (showScrollbar ? SCROLLBAR_WIDTH + 4 : 0);
        // Apply scroll offset for hit detection
        int y = contentY - scrollOffset;

        // Skip section header
        y += ROW_HEIGHT;

        // Light Level toggle
        if (isInToggleArea(mouseX, mouseY, contentX, y, rowWidth)) {
            invokeAction(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // Radius controls - slider click/drag
        int sliderX = sliderContentX;
        int localSliderWidth = sliderWidth;

        // Click on slider to set value directly
        if (isMouseOver((int) mouseX, (int) mouseY, sliderX, y, localSliderWidth, 16)) {
            setRadiusFromSliderPosition((int) mouseX, sliderX, localSliderWidth);
            draggingLightSlider = true;
            return true;
        }

        if (lightMinusBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (lightPlusBtn.mouseClicked(mouseX, mouseY, button)) return true;
        y += ROW_HEIGHT;

        // Separator space
        y += 8 + SECTION_SPACING;

        // Skip section header - but check for "Clear All" button click first
        if (countActiveHeatmaps() > 0 && clearAllBtn.mouseClicked(mouseX, mouseY, button)) return true;
        y += ROW_HEIGHT;

        // Heatmap toggles - use all enum values for consistency
        for (HeatmapType type : HeatmapType.values()) {
            if (isInToggleArea(mouseX, mouseY, contentX, y, rowWidth)) {
                String actionId = heatmapActionId(type);
                if (actionId != null) {
                    invokeAction(actionId);
                } else {
                    HeatmapVisualizer.INSTANCE.toggle(type);
                }
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
            invokeAction(ActionIds.DEBUG_SAFE_SPOTS_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // Vertical Levels toggle
        if (isInToggleArea(mouseX, mouseY, contentX, y, rowWidth)) {
            invokeAction(ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE);
            return true;
        }
        y += ROW_HEIGHT;

        // Separator space
        y += 8 + SECTION_SPACING;

        // Skip section header (Performance)
        y += ROW_HEIGHT;

        // View Distance controls
        int vdSliderX = viewDistSliderX;
        int localVdSliderWidth = viewDistSliderWidth;

        // Click on view distance slider
        if (isMouseOver((int) mouseX, (int) mouseY, vdSliderX, y, localVdSliderWidth, 16)) {
            setViewDistFromSliderPosition((int) mouseX, vdSliderX, localVdSliderWidth);
            draggingViewDistSlider = true;
            return true;
        }

        if (vdMinusBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (vdPlusBtn.mouseClicked(mouseX, mouseY, button)) return true;

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
        lightSliderPulse = 1.0f; // Trigger pulse animation
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
        viewDistSliderPulse = 1.0f; // Trigger pulse animation
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
                    float dragRatio = (float)(mouseY - lastContentY - thumbHeight / 2.0) / trackHeight;
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
            boolean handled = false;
            handled |= lightMinusBtn.mouseReleased(mouseX, mouseY, button);
            handled |= lightPlusBtn.mouseReleased(mouseX, mouseY, button);
            handled |= clearAllBtn.mouseReleased(mouseX, mouseY, button);
            handled |= vdMinusBtn.mouseReleased(mouseX, mouseY, button);
            handled |= vdPlusBtn.mouseReleased(mouseX, mouseY, button);
            if (handled) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!showScrollbar || maxScrollOffset <= 0 || !isMouseOverContent(mouseX, mouseY)) {
            return false;
        }
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

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        return mouseX >= lastContentX && mouseX <= lastContentX + lastContentWidth
            && mouseY >= lastContentY && mouseY <= lastContentY + lastContentHeight;
    }
}
