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

import com.devmod.area.aesthetic.AreaBuilderGuiConstants;
import com.devmod.area.aesthetic.AreaBuilderIcons;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.area.data.AreaShape;

/**
 * Widget for selecting area shape.
 * Displays shapes as a grid with icons.
 */
@OnlyIn(Dist.CLIENT)
public class ShapeConfigWidget extends AbstractWidget {

    private static final int MIN_CELL_SIZE = 28;

    private final AreaShape[] shapes = AreaShape.values();
    private AreaShape selectedShape;
    private final Consumer<AreaShape> onShapeChanged;

    public ShapeConfigWidget(int x, int y, int width, int height,
                             AreaShape initialShape, Consumer<AreaShape> onShapeChanged) {
        super(x, y, width, height, Component.empty());
        this.selectedShape = initialShape;
        this.onShapeChanged = onShapeChanged;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);

        // Draw title
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.builder.tab.shape")),
            getX(), getY(),
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        int columns = AreaBuilderGuiConstants.GRID_COLUMNS;
        int spacing = AreaBuilderGuiConstants.scaledGridSpacing();
        int startY = getStartY(font);
        int cellSize = getCellSize(font, columns, spacing);

        for (int i = 0; i < shapes.length; i++) {
            AreaShape shape = shapes[i];
            int col = i % columns;
            int row = i / columns;

            int cellX = getX() + col * (cellSize + spacing);
            int cellY = startY + row * (cellSize + spacing);

            boolean isHovered = mouseX >= cellX && mouseX < cellX + cellSize &&
                               mouseY >= cellY && mouseY < cellY + cellSize;
            boolean isSelected = shape == selectedShape;

            // Draw cell background
            int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                         (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER :
                                     AreaBuilderGuiConstants.COLOR_PANEL);
            graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, bgColor);

            // Draw border
            int borderColor = isSelected ? AreaBuilderGuiConstants.COLOR_SELECTED_BORDER : AreaBuilderGuiConstants.COLOR_BORDER;
            graphics.renderOutline(cellX, cellY, cellSize, cellSize, borderColor);

            // Draw icon
            ItemStack icon = new ItemStack(Objects.requireNonNull(AreaBuilderIcons.getShapeIcon(shape.name())));
            int iconX = cellX + (cellSize - 16) / 2;
            int iconY = cellY + s(8);
            graphics.renderItem(icon, iconX, iconY);

            // Draw shape name
            String name = Objects.requireNonNull(Objects.requireNonNull(
                Component.translatable("area.shape." + shape.getSerializedName())).getString());
            int textWidth = UIScaleManager.getScaledStringWidth(font, name);
            int textX = cellX + (cellSize - textWidth) / 2;
            int textY = cellY + cellSize - s(20);
            UIScaleManager.drawScaledString(graphics, font, name, textX, textY,
                isSelected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
            );
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int columns = AreaBuilderGuiConstants.GRID_COLUMNS;
        int spacing = AreaBuilderGuiConstants.scaledGridSpacing();
        int startY = getStartY(font);
        int cellSize = getCellSize(font, columns, spacing);

        for (int i = 0; i < shapes.length; i++) {
            AreaShape shape = shapes[i];
            int col = i % columns;
            int row = i / columns;

            int cellX = getX() + col * (cellSize + spacing);
            int cellY = startY + row * (cellSize + spacing);

            if (mouseX >= cellX && mouseX < cellX + cellSize &&
                mouseY >= cellY && mouseY < cellY + cellSize) {
                if (selectedShape != shape) {
                    selectedShape = shape;
                    if (onShapeChanged != null) {
                        onShapeChanged.accept(shape);
                    }
                }
                return;
            }
        }
    }

    public AreaShape getSelectedShape() {
        return selectedShape;
    }

    public void setSelectedShape(AreaShape shape) {
        this.selectedShape = shape;
    }

    private int getStartY(net.minecraft.client.gui.Font font) {
        return getY() + font.lineHeight + s(6);
    }

    private int getCellSize(net.minecraft.client.gui.Font font, int columns, int spacing) {
        int rows = (shapes.length + columns - 1) / columns;
        int reserved = font.lineHeight + s(20);
        int availableHeight = Math.max(0, getHeight() - reserved);
        int size = (availableHeight - (rows - 1) * spacing) / rows;
        size = Math.min(size, AreaBuilderGuiConstants.scaledGridCellSize());
        return Math.max(size, s(MIN_CELL_SIZE));
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.builder.tab.shape")));
    }

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }
}
