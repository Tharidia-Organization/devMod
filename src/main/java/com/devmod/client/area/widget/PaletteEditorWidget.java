package com.devmod.client.area.widget;

import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.area.AreaBuilderGuiConstants;
import com.devmod.area.aesthetic.AreaBuilderIcons;
import com.devmod.client.ui.core.UIScaleManager;

/**
 * Widget for selecting and editing material palettes.
 */
@OnlyIn(Dist.CLIENT)
public class PaletteEditorWidget extends AbstractWidget {

    private static final int MIN_CELL_SIZE = 28;

    private static final String[] PRESETS = {
        "default", "industrial", "fantasy", "minimal",
        "nether", "end", "nature", "tech"
    };

    private String selectedPreset;
    private final Consumer<String> onPresetChanged;

    public PaletteEditorWidget(int x, int y, int width, int height,
                              String initialPreset, Consumer<String> onPresetChanged) {
        super(x, y, width, height, Component.empty());
        this.selectedPreset = initialPreset != null ? initialPreset : "default";
        this.onPresetChanged = onPresetChanged;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);

        // Title
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.builder.tab.materials")),
            getX(), getY(),
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        int columns = AreaBuilderGuiConstants.GRID_COLUMNS;
        int spacing = AreaBuilderGuiConstants.scaledGridSpacing();
        int startY = getStartY(font);
        int cellSize = getCellSize(font, columns, spacing);

        for (int i = 0; i < PRESETS.length; i++) {
            String preset = PRESETS[i];
            int col = i % columns;
            int row = i / columns;

            int cellX = getX() + col * (cellSize + spacing);
            int cellY = startY + row * (cellSize + spacing);

            boolean isHovered = mouseX >= cellX && mouseX < cellX + cellSize &&
                               mouseY >= cellY && mouseY < cellY + cellSize;
            boolean isSelected = preset.equals(selectedPreset);

            // Draw cell background
            int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                         (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER :
                                     AreaBuilderGuiConstants.COLOR_PANEL);
            graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, bgColor);

            // Draw border
            int borderColor = isSelected ? AreaBuilderGuiConstants.COLOR_SELECTED_BORDER : AreaBuilderGuiConstants.COLOR_BORDER;
            graphics.renderOutline(cellX, cellY, cellSize, cellSize, borderColor);

            // Draw icon
            ItemStack icon = new ItemStack(Objects.requireNonNull(AreaBuilderIcons.getPresetIcon(preset)));
            int iconX = cellX + (cellSize - 16) / 2;
            int iconY = cellY + s(8);
            graphics.renderItem(icon, iconX, iconY);

            // Draw preset name with truncation for overflow
            String name = Objects.requireNonNull(Objects.requireNonNull(
                Component.translatable("area.preset." + preset)).getString());
            int maxTextWidth = cellSize - s(4); // Leave 2px padding on each side
            int textWidth = UIScaleManager.getScaledStringWidth(font, name);
            if (textWidth > maxTextWidth && name.length() > 3) {
                while (textWidth > maxTextWidth && name.length() > 3) {
                    name = name.substring(0, name.length() - 1);
                    textWidth = UIScaleManager.getScaledStringWidth(font, name + "...");
                }
                name = name + "...";
                textWidth = UIScaleManager.getScaledStringWidth(font, name);
            }
            int textX = cellX + (cellSize - textWidth) / 2;
            int textY = cellY + cellSize - s(20);
            UIScaleManager.drawScaledString(graphics, font, name, textX, textY,
                isSelected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        }

        // Show selected preset info
        int rows = (PRESETS.length + columns - 1) / columns;
        int infoY = startY + rows * (cellSize + spacing) + s(6);
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.builder.selected_preset",
                Component.translatable("area.preset." + selectedPreset))),
            getX(), infoY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int columns = AreaBuilderGuiConstants.GRID_COLUMNS;
        int spacing = AreaBuilderGuiConstants.scaledGridSpacing();
        int startY = getStartY(font);
        int cellSize = getCellSize(font, columns, spacing);

        for (int i = 0; i < PRESETS.length; i++) {
            String preset = PRESETS[i];
            int col = i % columns;
            int row = i / columns;

            int cellX = getX() + col * (cellSize + spacing);
            int cellY = startY + row * (cellSize + spacing);

            if (mouseX >= cellX && mouseX < cellX + cellSize &&
                mouseY >= cellY && mouseY < cellY + cellSize) {
                if (!preset.equals(selectedPreset)) {
                    selectedPreset = preset;
                    if (onPresetChanged != null) {
                        onPresetChanged.accept(preset);
                    }
                }
                return;
            }
        }
    }

    private int getStartY(net.minecraft.client.gui.Font font) {
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
        return getY() + lineHeight + s(6);
    }

    private int getCellSize(net.minecraft.client.gui.Font font, int columns, int spacing) {
        int rows = (PRESETS.length + columns - 1) / columns;
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
        int reserved = lineHeight + s(20);
        int availableHeight = Math.max(0, getHeight() - reserved);
        int size = (availableHeight - (rows - 1) * spacing) / rows;
        size = Math.min(size, AreaBuilderGuiConstants.scaledGridCellSize());
        return Math.max(size, s(MIN_CELL_SIZE));
    }

    public String getSelectedPreset() {
        return selectedPreset;
    }

    public void setSelectedPreset(String preset) {
        if (preset != null) {
            this.selectedPreset = preset;
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.builder.tab.materials")));
    }

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }
}
