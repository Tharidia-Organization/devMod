package com.devmod.hologram.client.screen;

import java.util.EnumSet;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.UiSounds;
import com.devmod.hologram.data.EntityFilterType;
import com.devmod.hologram.data.HologramFilter;
import com.devmod.hologram.network.HologramConfigPayload;
import com.devmod.hologram.network.HologramPresetPayload;

/**
 * Configuration screen for the Hologram Projector block.
 * Allows players to adjust scan size, block scale, rotation, and transparency.
 */
@OnlyIn(Dist.CLIENT)
public class HologramConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 560;
    private static final int PADDING = 16;
    private static final int ROW_HEIGHT = 32;
    private static final int BUTTON_HEIGHT = 24;

    private static final int[] SCAN_SIZE_OPTIONS = {16, 32, 48, 64, 96, 128, 192, 256};

    private final BlockPos pos;

    // Current settings
    private int scanSize;
    private int blockSize;
    private boolean rotationEnabled;
    private boolean transparentMode;
    private EnumSet<HologramFilter> activeFilters;
    private boolean filterHighlightOnly;
    private boolean texturedMode;
    private boolean showEntities;
    private boolean fullEntityModels;
    private int maxEntityModels;
    private EnumSet<EntityFilterType> activeEntityFilters;
    private boolean ySliceEnabled;
    private int ySliceLevel;
    private int ySliceThickness;

    // UI Components
    @Nullable private EditorSlider scanSizeSlider;
    @Nullable private EditorSlider blockSizeSlider;
    @Nullable private EditorToggle rotationToggle;
    @Nullable private EditorToggle transparencyToggle;
    @Nullable private EditorToggle highlightOnlyToggle;
    @Nullable private EditorToggle texturedModeToggle;
    @Nullable private EditorToggle showEntitiesToggle;
    @Nullable private EditorToggle fullEntityModelsToggle;
    @Nullable private EditorSlider maxEntityModelsSlider;
    @Nullable private EditorToggle ySliceToggle;
    @Nullable private EditorSlider ySliceLevelSlider;
    @Nullable private EditorSlider ySliceThicknessSlider;
    @Nullable private EditorButton rescanButton;
    @Nullable private EditorButton applyButton;
    @Nullable private EditorButton closeButton;

    // Filter button bounds for click detection
    private static final int FILTER_BUTTON_SIZE = 20;
    private static final int FILTER_BUTTON_GAP = 4;

    // Preset buttons
    private static final int PRESET_BUTTON_WIDTH = 24;
    private static final int PRESET_BUTTON_HEIGHT = 20;
    private static final int PRESET_BUTTON_GAP = 4;
    private static final int NUM_PRESETS = 5;
    // Layout
    private int panelX;
    private int panelY;

    public HologramConfigScreen(BlockPos pos, int scanSize, int blockSize,
                                boolean rotationEnabled, boolean transparentMode,
                                int filterBitmask, boolean filterHighlightOnly,
                                boolean texturedMode, boolean showEntities,
                                boolean fullEntityModels, int maxEntityModels,
                                int entityFilterBitmask, boolean ySliceEnabled,
                                int ySliceLevel, int ySliceThickness) {
        super(Objects.requireNonNull(Component.translatable("screen.devmod.hologram_config")));
        this.pos = Objects.requireNonNull(pos);
        this.scanSize = scanSize;
        this.blockSize = blockSize;
        this.rotationEnabled = rotationEnabled;
        this.transparentMode = transparentMode;
        this.activeFilters = intToFilters(filterBitmask);
        this.filterHighlightOnly = filterHighlightOnly;
        this.texturedMode = texturedMode;
        this.showEntities = showEntities;
        this.fullEntityModels = fullEntityModels;
        this.maxEntityModels = maxEntityModels;
        this.activeEntityFilters = EntityFilterType.fromBitmask(entityFilterBitmask);
        this.ySliceEnabled = ySliceEnabled;
        this.ySliceLevel = ySliceLevel;
        this.ySliceThickness = ySliceThickness;
    }

    private static EnumSet<HologramFilter> intToFilters(int value) {
        EnumSet<HologramFilter> result = EnumSet.noneOf(HologramFilter.class);
        for (HologramFilter filter : HologramFilter.values()) {
            if ((value & (1 << filter.ordinal())) != 0) {
                result.add(filter);
            }
        }
        return result;
    }

    private static int filtersToInt(EnumSet<HologramFilter> filters) {
        int result = 0;
        for (HologramFilter filter : filters) {
            result |= (1 << filter.ordinal());
        }
        return result;
    }

    @Override
    protected void init() {
        super.init();

        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        initSliders();
        initToggles();
        initButtons();
    }

    private void initSliders() {
        // Scan Size Slider (16 to 256)
        scanSizeSlider = new EditorSlider("scan_size", "Scan Size", 16, 256, scanSize)
            .step(16)
            .format("%.0f")
            .suffix(" blocks")
            .onChange(val -> scanSize = snapToScanSize(Math.round(val)));

        // Block Size Slider (1-4)
        blockSizeSlider = new EditorSlider("block_size", "Display Scale", 1, 4, blockSize)
            .step(1)
            .format("%.0f")
            .suffix("x")
            .onChange(val -> blockSize = Math.round(val));

        // Max Entity Models Slider (10-200)
        maxEntityModelsSlider = new EditorSlider("max_entity_models", "Max 3D Models", 10, 200, maxEntityModels)
            .step(10)
            .format("%.0f")
            .onChange(val -> maxEntityModels = Math.round(val));

        // Y-Slice Level Slider (-64 to 319)
        ySliceLevelSlider = new EditorSlider("y_slice_level", "Y Level", -64, 319, ySliceLevel)
            .step(1)
            .format("%.0f")
            .onChange(val -> ySliceLevel = Math.round(val));

        // Y-Slice Thickness Slider (1, 3, 5, 7)
        ySliceThicknessSlider = new EditorSlider("y_slice_thickness", "Thickness", 1, 7, ySliceThickness)
            .step(2)
            .format("%.0f")
            .suffix(" blocks")
            .onChange(val -> ySliceThickness = Math.round(val));
    }

    private void initToggles() {
        rotationToggle = new EditorToggle("rotation", "Auto-Rotate", rotationEnabled)
            .onChange(val -> rotationEnabled = val);

        transparencyToggle = new EditorToggle("transparency", "Transparent Blocks", transparentMode)
            .onChange(val -> transparentMode = val);

        highlightOnlyToggle = new EditorToggle("highlight_only", "Filter Only", filterHighlightOnly)
            .onChange(val -> filterHighlightOnly = val);

        texturedModeToggle = new EditorToggle("textured_mode", "Block Textures", texturedMode)
            .onChange(val -> texturedMode = val);

        showEntitiesToggle = new EditorToggle("show_entities", "Show Entities", showEntities)
            .onChange(val -> showEntities = val);

        fullEntityModelsToggle = new EditorToggle("full_models", "3D Models", fullEntityModels)
            .onChange(val -> fullEntityModels = val);

        ySliceToggle = new EditorToggle("y_slice", "Y-Slice Mode", ySliceEnabled)
            .onChange(val -> ySliceEnabled = val);
    }

    private void initButtons() {
        rescanButton = EditorButton.builder("rescan", "Rescan Area")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onRescanClicked)
            .build();

        applyButton = EditorButton.builder("apply", "Apply")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onApplyClicked)
            .build();

        closeButton = EditorButton.builder("close", "Close")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
    }

    private int snapToScanSize(int value) {
        // Snap to nearest valid scan size
        int closest = SCAN_SIZE_OPTIONS[0];
        int minDist = Math.abs(value - closest);
        for (int option : SCAN_SIZE_OPTIONS) {
            int dist = Math.abs(value - option);
            if (dist < minDist) {
                minDist = dist;
                closest = option;
            }
        }
        return closest;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderPanel(graphics);
        renderTitle(graphics);
        renderComponents(graphics, mouseX, mouseY);
        renderButtons(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics graphics) {
        // Panel background
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT,
            DesignTokens.Bg.LEVEL_2);

        // Border
        int border = DesignTokens.Stroke.DEFAULT;
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, border);
        graphics.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, border);
        graphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, border);
        graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, border);

        // Header separator
        int headerY = panelY + 36;
        graphics.fill(panelX + PADDING, headerY, panelX + PANEL_WIDTH - PADDING, headerY + 1, border);
    }

    private void renderTitle(GuiGraphics graphics) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        String titleText = "Hologram Projector";
        graphics.drawString(font, titleText, panelX + PADDING, panelY + 14,
            DesignTokens.Text.PRIMARY, false);
    }

    private void renderComponents(GuiGraphics graphics, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        int contentX = panelX + PADDING;
        int sliderWidth = PANEL_WIDTH - PADDING * 2;
        int y = panelY + 50;

        // Scan Size
        if (scanSizeSlider != null) {
            scanSizeSlider.render(graphics, contentX, y, sliderWidth, mouseX, mouseY);
        }
        y += ROW_HEIGHT + 8;

        // Block Size
        if (blockSizeSlider != null) {
            blockSizeSlider.render(graphics, contentX, y, sliderWidth, mouseX, mouseY);
        }
        y += ROW_HEIGHT + 12;

        // Toggles side by side
        int toggleWidth = (sliderWidth - 16) / 2;
        if (rotationToggle != null) {
            rotationToggle.render(graphics, contentX, y, toggleWidth, mouseX, mouseY);
        }
        if (transparencyToggle != null) {
            transparencyToggle.render(graphics, contentX + toggleWidth + 16, y, toggleWidth, mouseX, mouseY);
        }
        y += ROW_HEIGHT + 12;

        // Filter section header
        graphics.drawString(font, "Ore Filters", contentX, y, DesignTokens.Text.PRIMARY, false);
        y += 14;

        // Render filter buttons in a grid (6 per row)
        int buttonsPerRow = 6;
        int buttonX = contentX;
        int buttonY = y;
        int col = 0;

        for (HologramFilter filter : HologramFilter.values()) {
            boolean active = activeFilters.contains(filter);
            renderFilterButton(graphics, buttonX, buttonY, filter, active, mouseX, mouseY);

            col++;
            if (col >= buttonsPerRow) {
                col = 0;
                buttonX = contentX;
                buttonY += FILTER_BUTTON_SIZE + FILTER_BUTTON_GAP;
            } else {
                buttonX += FILTER_BUTTON_SIZE + FILTER_BUTTON_GAP;
            }
        }

        // Move Y past the filter grid (2 rows of filters)
        y += (FILTER_BUTTON_SIZE + FILTER_BUTTON_GAP) * 2 + 8;

        // Highlight only and Textured mode toggles side by side
        if (highlightOnlyToggle != null) {
            highlightOnlyToggle.render(graphics, contentX, y, toggleWidth, mouseX, mouseY);
        }
        if (texturedModeToggle != null) {
            texturedModeToggle.render(graphics, contentX + toggleWidth + 16, y, toggleWidth, mouseX, mouseY);
        }
        y += ROW_HEIGHT + 12;

        // Entity section header
        graphics.drawString(font, "Entities", contentX, y, DesignTokens.Text.PRIMARY, false);
        y += 14;

        // Show Entities and 3D Models toggles side by side
        if (showEntitiesToggle != null) {
            showEntitiesToggle.render(graphics, contentX, y, toggleWidth, mouseX, mouseY);
        }
        if (fullEntityModelsToggle != null) {
            fullEntityModelsToggle.render(graphics, contentX + toggleWidth + 16, y, toggleWidth, mouseX, mouseY);
        }
        y += ROW_HEIGHT + 8;

        // Entity type filter buttons (only visible when entities are shown)
        if (showEntities) {
            // Render entity filter buttons in a row (7 types)
            int entityButtonX = contentX;
            for (EntityFilterType entityFilter : EntityFilterType.values()) {
                boolean active = activeEntityFilters.contains(entityFilter);
                renderEntityFilterButton(graphics, entityButtonX, y, entityFilter, active, mouseX, mouseY);
                entityButtonX += FILTER_BUTTON_SIZE + FILTER_BUTTON_GAP;
            }
            y += FILTER_BUTTON_SIZE + 8;

            // Max Entity Models slider
            if (maxEntityModelsSlider != null) {
                maxEntityModelsSlider.render(graphics, contentX, y, sliderWidth, mouseX, mouseY);
                y += ROW_HEIGHT + 8;
            }
        }

        // Y-Slice section header
        graphics.drawString(font, "Y-Slice (X-Ray)", contentX, y, DesignTokens.Text.PRIMARY, false);
        y += 14;

        // Y-Slice toggle
        if (ySliceToggle != null) {
            ySliceToggle.render(graphics, contentX, y, sliderWidth, mouseX, mouseY);
        }
        y += ROW_HEIGHT + 8;

        // Y-Slice sliders (only visible when Y-slice is enabled)
        if (ySliceEnabled) {
            if (ySliceLevelSlider != null) {
                ySliceLevelSlider.render(graphics, contentX, y, sliderWidth, mouseX, mouseY);
                y += ROW_HEIGHT + 8;
            }
            if (ySliceThicknessSlider != null) {
                ySliceThicknessSlider.render(graphics, contentX, y, sliderWidth, mouseX, mouseY);
                y += ROW_HEIGHT + 8;
            }
        }

        // Presets section header
        graphics.drawString(font, "Presets", contentX, y, DesignTokens.Text.PRIMARY, false);
        y += 14;

        // Render preset buttons (5 slots with Load/Save each)
        renderPresetButtons(graphics, contentX, y, mouseX, mouseY);
        y += PRESET_BUTTON_HEIGHT + 8;

        // Position info
        String posText = String.format("Position: %d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
        graphics.drawString(font, posText, contentX, y, DesignTokens.Text.MUTED, false);
    }

    private void renderFilterButton(GuiGraphics graphics, int x, int y, HologramFilter filter,
                                     boolean active, int mouseX, int mouseY) {
        int color = filter.getHighlightColor();
        boolean hovered = mouseX >= x && mouseX < x + FILTER_BUTTON_SIZE &&
                          mouseY >= y && mouseY < y + FILTER_BUTTON_SIZE;

        // Background
        int bgColor = active ? (0xFF000000 | color) : 0xFF333333;
        if (hovered) {
            bgColor = active ? brighten(bgColor) : 0xFF444444;
        }
        graphics.fill(x, y, x + FILTER_BUTTON_SIZE, y + FILTER_BUTTON_SIZE, bgColor);

        // Border
        int borderColor = active ? 0xFFFFFFFF : 0xFF555555;
        graphics.fill(x, y, x + FILTER_BUTTON_SIZE, y + 1, borderColor);
        graphics.fill(x, y + FILTER_BUTTON_SIZE - 1, x + FILTER_BUTTON_SIZE, y + FILTER_BUTTON_SIZE, borderColor);
        graphics.fill(x, y, x + 1, y + FILTER_BUTTON_SIZE, borderColor);
        graphics.fill(x + FILTER_BUTTON_SIZE - 1, y, x + FILTER_BUTTON_SIZE, y + FILTER_BUTTON_SIZE, borderColor);

        // First letter of filter name
        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        String letter = filter.getDisplayName().substring(0, 1);
        int textColor = active ? 0xFFFFFFFF : 0xFF888888;
        int textX = x + (FILTER_BUTTON_SIZE - font.width(letter)) / 2;
        int textY = y + (FILTER_BUTTON_SIZE - 8) / 2;
        graphics.drawString(font, letter, textX, textY, textColor, false);

        // Tooltip on hover
        if (hovered) {
            graphics.renderTooltip(font, Component.literal(filter.getDisplayName()), mouseX, mouseY);
        }
    }

    private void renderEntityFilterButton(GuiGraphics graphics, int x, int y, EntityFilterType filter,
                                          boolean active, int mouseX, int mouseY) {
        int color = filter.getColor();
        boolean hovered = mouseX >= x && mouseX < x + FILTER_BUTTON_SIZE &&
                          mouseY >= y && mouseY < y + FILTER_BUTTON_SIZE;

        // Background
        int bgColor = active ? (0xFF000000 | color) : 0xFF333333;
        if (hovered) {
            bgColor = active ? brighten(bgColor) : 0xFF444444;
        }
        graphics.fill(x, y, x + FILTER_BUTTON_SIZE, y + FILTER_BUTTON_SIZE, bgColor);

        // Border
        int borderColor = active ? 0xFFFFFFFF : 0xFF555555;
        graphics.fill(x, y, x + FILTER_BUTTON_SIZE, y + 1, borderColor);
        graphics.fill(x, y + FILTER_BUTTON_SIZE - 1, x + FILTER_BUTTON_SIZE, y + FILTER_BUTTON_SIZE, borderColor);
        graphics.fill(x, y, x + 1, y + FILTER_BUTTON_SIZE, borderColor);
        graphics.fill(x + FILTER_BUTTON_SIZE - 1, y, x + FILTER_BUTTON_SIZE, y + FILTER_BUTTON_SIZE, borderColor);

        // First letter of filter name
        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        String letter = filter.getDisplayName().substring(0, 1);
        int textColor = active ? 0xFFFFFFFF : 0xFF888888;
        int textX = x + (FILTER_BUTTON_SIZE - font.width(letter)) / 2;
        int textY = y + (FILTER_BUTTON_SIZE - 8) / 2;
        graphics.drawString(font, letter, textX, textY, textColor, false);

        // Tooltip on hover
        if (hovered) {
            graphics.renderTooltip(font, Component.literal(filter.getDisplayName()), mouseX, mouseY);
        }
    }

    private void renderPresetButtons(GuiGraphics graphics, int startX, int y, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font);

        for (int i = 0; i < NUM_PRESETS; i++) {
            int slotX = startX + i * (PRESET_BUTTON_WIDTH * 2 + PRESET_BUTTON_GAP * 2 + 8);

            // Slot number label
            String slotLabel = String.valueOf(i + 1);
            graphics.drawString(font, slotLabel, slotX, y + 6, DesignTokens.Text.MUTED, false);
            slotX += 10;

            // Load button
            boolean loadHovered = mouseX >= slotX && mouseX < slotX + PRESET_BUTTON_WIDTH &&
                                  mouseY >= y && mouseY < y + PRESET_BUTTON_HEIGHT;
            int loadBg = loadHovered ? 0xFF555555 : 0xFF333333;
            graphics.fill(slotX, y, slotX + PRESET_BUTTON_WIDTH, y + PRESET_BUTTON_HEIGHT, loadBg);
            graphics.fill(slotX, y, slotX + PRESET_BUTTON_WIDTH, y + 1, 0xFF666666);
            graphics.fill(slotX, y + PRESET_BUTTON_HEIGHT - 1, slotX + PRESET_BUTTON_WIDTH, y + PRESET_BUTTON_HEIGHT, 0xFF222222);
            int loadTextX = slotX + (PRESET_BUTTON_WIDTH - font.width("L")) / 2;
            graphics.drawString(font, "L", loadTextX, y + 6, loadHovered ? 0xFFFFFFFF : 0xFFAAAAAA, false);

            slotX += PRESET_BUTTON_WIDTH + PRESET_BUTTON_GAP;

            // Save button
            boolean saveHovered = mouseX >= slotX && mouseX < slotX + PRESET_BUTTON_WIDTH &&
                                  mouseY >= y && mouseY < y + PRESET_BUTTON_HEIGHT;
            int saveBg = saveHovered ? 0xFF445544 : 0xFF334433;
            graphics.fill(slotX, y, slotX + PRESET_BUTTON_WIDTH, y + PRESET_BUTTON_HEIGHT, saveBg);
            graphics.fill(slotX, y, slotX + PRESET_BUTTON_WIDTH, y + 1, 0xFF556655);
            graphics.fill(slotX, y + PRESET_BUTTON_HEIGHT - 1, slotX + PRESET_BUTTON_WIDTH, y + PRESET_BUTTON_HEIGHT, 0xFF223322);
            int saveTextX = slotX + (PRESET_BUTTON_WIDTH - font.width("S")) / 2;
            graphics.drawString(font, "S", saveTextX, y + 6, saveHovered ? 0xFFFFFFFF : 0xFFAAFFAA, false);

            // Tooltips
            if (loadHovered) {
                graphics.renderTooltip(font, Component.literal("Load Preset " + (i + 1)), mouseX, mouseY);
            } else if (saveHovered) {
                graphics.renderTooltip(font, Component.literal("Save Preset " + (i + 1)), mouseX, mouseY);
            }
        }
    }

    private static int brighten(int color) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonWidth = 90;
        int buttonY = panelY + PANEL_HEIGHT - BUTTON_HEIGHT - PADDING;

        // Close button (left)
        if (closeButton != null) {
            closeButton.render(graphics, panelX + PADDING, buttonY, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);
        }

        // Rescan button (center)
        if (rescanButton != null) {
            int rescanX = panelX + (PANEL_WIDTH - buttonWidth) / 2;
            rescanButton.render(graphics, rescanX, buttonY, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);
        }

        // Apply button (right)
        if (applyButton != null) {
            applyButton.render(graphics, panelX + PANEL_WIDTH - PADDING - buttonWidth, buttonY,
                buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Check sliders
        if (scanSizeSlider != null && scanSizeSlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (blockSizeSlider != null && blockSizeSlider.mouseClicked(mouseX, mouseY, button)) return true;

        // Check toggles
        if (rotationToggle != null && rotationToggle.mouseClicked(mouseX, mouseY, button)) return true;
        if (transparencyToggle != null && transparencyToggle.mouseClicked(mouseX, mouseY, button)) return true;
        if (highlightOnlyToggle != null && highlightOnlyToggle.mouseClicked(mouseX, mouseY, button)) return true;
        if (texturedModeToggle != null && texturedModeToggle.mouseClicked(mouseX, mouseY, button)) return true;
        if (showEntitiesToggle != null && showEntitiesToggle.mouseClicked(mouseX, mouseY, button)) return true;
        if (fullEntityModelsToggle != null && fullEntityModelsToggle.mouseClicked(mouseX, mouseY, button)) return true;

        // Check entity slider (only if visible)
        if (showEntities && maxEntityModelsSlider != null && maxEntityModelsSlider.mouseClicked(mouseX, mouseY, button)) return true;

        // Check Y-slice toggle and sliders
        if (ySliceToggle != null && ySliceToggle.mouseClicked(mouseX, mouseY, button)) return true;
        if (ySliceEnabled && ySliceLevelSlider != null && ySliceLevelSlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (ySliceEnabled && ySliceThicknessSlider != null && ySliceThicknessSlider.mouseClicked(mouseX, mouseY, button)) return true;

        // Check filter buttons
        if (handleFilterClick(mouseX, mouseY)) return true;

        // Check entity filter buttons (only if visible)
        if (showEntities && handleEntityFilterClick(mouseX, mouseY)) return true;

        // Check preset buttons
        if (handlePresetClick(mouseX, mouseY)) return true;

        // Check buttons
        if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (rescanButton != null && rescanButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (applyButton != null && applyButton.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleFilterClick(double mouseX, double mouseY) {
        int buttonsPerRow = 6;
        int col = 0;
        int buttonX = getFilterGridX();
        int buttonY = getFilterGridY();

        for (HologramFilter filter : HologramFilter.values()) {
            if (mouseX >= buttonX && mouseX < buttonX + FILTER_BUTTON_SIZE &&
                mouseY >= buttonY && mouseY < buttonY + FILTER_BUTTON_SIZE) {
                // Toggle the filter
                if (activeFilters.contains(filter)) {
                    activeFilters.remove(filter);
                } else {
                    activeFilters.add(filter);
                }
                UiSounds.click();
                return true;
            }

            col++;
            if (col >= buttonsPerRow) {
                col = 0;
                buttonX = getFilterGridX();
                buttonY += FILTER_BUTTON_SIZE + FILTER_BUTTON_GAP;
            } else {
                buttonX += FILTER_BUTTON_SIZE + FILTER_BUTTON_GAP;
            }
        }
        return false;
    }

    private int getFilterGridX() {
        return panelX + PADDING;
    }

    private int getFilterGridY() {
        int y = panelY + 50;
        y += ROW_HEIGHT + 8;
        y += ROW_HEIGHT + 12;
        y += ROW_HEIGHT + 12;
        return y + 14;
    }

    private boolean handleEntityFilterClick(double mouseX, double mouseY) {
        int buttonX = getFilterGridX();
        int buttonY = getEntityFilterGridY();

        for (EntityFilterType filter : EntityFilterType.values()) {
            if (mouseX >= buttonX && mouseX < buttonX + FILTER_BUTTON_SIZE &&
                mouseY >= buttonY && mouseY < buttonY + FILTER_BUTTON_SIZE) {
                // Toggle the entity filter
                if (activeEntityFilters.contains(filter)) {
                    activeEntityFilters.remove(filter);
                } else {
                    activeEntityFilters.add(filter);
                }
                UiSounds.click();
                return true;
            }
            buttonX += FILTER_BUTTON_SIZE + FILTER_BUTTON_GAP;
        }
        return false;
    }

    private int getEntityFilterGridY() {
        // Calculate Y position matching renderComponents layout
        int y = getFilterGridY();
        y += (FILTER_BUTTON_SIZE + FILTER_BUTTON_GAP) * 2 + 8; // After ore filter grid (2 rows)
        y += ROW_HEIGHT + 12; // After highlightOnly/texturedMode toggles
        y += 14; // "Entities" header
        y += ROW_HEIGHT + 8; // After showEntities/fullEntityModels toggles
        return y;
    }

    private boolean handlePresetClick(double mouseX, double mouseY) {
        int y = getPresetButtonsY();
        int startX = panelX + PADDING;

        for (int i = 0; i < NUM_PRESETS; i++) {
            int slotX = startX + i * (PRESET_BUTTON_WIDTH * 2 + PRESET_BUTTON_GAP * 2 + 8) + 10;

            // Check Load button
            if (mouseX >= slotX && mouseX < slotX + PRESET_BUTTON_WIDTH &&
                mouseY >= y && mouseY < y + PRESET_BUTTON_HEIGHT) {
                // Load preset
                sendPresetAction(i, false);
                UiSounds.click();
                return true;
            }

            slotX += PRESET_BUTTON_WIDTH + PRESET_BUTTON_GAP;

            // Check Save button
            if (mouseX >= slotX && mouseX < slotX + PRESET_BUTTON_WIDTH &&
                mouseY >= y && mouseY < y + PRESET_BUTTON_HEIGHT) {
                // First apply current settings, then save preset
                sendConfig(false);
                sendPresetAction(i, true);
                UiSounds.success();
                return true;
            }
        }
        return false;
    }

    private int getPresetButtonsY() {
        // Calculate Y position for preset buttons
        int y = getEntityFilterGridY();
        if (showEntities) {
            y += FILTER_BUTTON_SIZE + 8; // After entity filter buttons
            y += ROW_HEIGHT + 8; // After maxEntityModels slider
        }
        y += 14; // "Y-Slice (X-Ray)" header
        y += ROW_HEIGHT + 8; // After ySlice toggle
        if (ySliceEnabled) {
            y += ROW_HEIGHT + 8; // After ySliceLevel slider
            y += ROW_HEIGHT + 8; // After ySliceThickness slider
        }
        y += 14; // "Presets" header
        return y;
    }

    private void sendPresetAction(int slot, boolean isSave) {
        HologramPresetPayload payload = new HologramPresetPayload(pos, slot, isSave);
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scanSizeSlider != null) scanSizeSlider.mouseReleased(mouseX, mouseY, button);
        if (blockSizeSlider != null) blockSizeSlider.mouseReleased(mouseX, mouseY, button);
        if (showEntities && maxEntityModelsSlider != null) maxEntityModelsSlider.mouseReleased(mouseX, mouseY, button);
        if (ySliceEnabled && ySliceLevelSlider != null) ySliceLevelSlider.mouseReleased(mouseX, mouseY, button);
        if (ySliceEnabled && ySliceThicknessSlider != null) ySliceThicknessSlider.mouseReleased(mouseX, mouseY, button);
        if (closeButton != null) closeButton.mouseReleased(mouseX, mouseY, button);
        if (rescanButton != null) rescanButton.mouseReleased(mouseX, mouseY, button);
        if (applyButton != null) applyButton.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scanSizeSlider != null && scanSizeSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (blockSizeSlider != null && blockSizeSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (showEntities && maxEntityModelsSlider != null && maxEntityModelsSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (ySliceEnabled && ySliceLevelSlider != null && ySliceLevelSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (ySliceEnabled && ySliceThicknessSlider != null && ySliceThicknessSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESCAPE
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    private void onRescanClicked() {
        sendConfig(true);
        UiSounds.success();
    }

    private void onApplyClicked() {
        sendConfig(false);
        UiSounds.click();
        onClose();
    }

    private void sendConfig(boolean rescan) {
        HologramConfigPayload payload = new HologramConfigPayload(
            pos,
            snapToScanSize(scanSize),
            blockSize,
            rotationEnabled,
            transparentMode,
            rescan,
            filtersToInt(activeFilters),
            filterHighlightOnly,
            texturedMode,
            showEntities,
            fullEntityModels,
            maxEntityModels,
            EntityFilterType.toBitmask(activeEntityFilters),
            ySliceEnabled,
            ySliceLevel,
            ySliceThickness
        );
        PacketDistributor.sendToServer(payload);
    }
}
