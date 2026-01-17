package com.devmod.client.area.widget;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.area.aesthetic.AreaBuilderGuiConstants;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.GridSettings;

/**
 * Widget for configuring area dimensions with sliders.
 */
@OnlyIn(Dist.CLIENT)
public class DimensionsWidget extends AbstractWidget {

    private static final int SLIDER_HEIGHT = 20;
    private static final int SLIDER_SPACING = 30;
    private static final int LABEL_WIDTH = 80;

    private int widthValue;
    private int lengthValue;
    private int heightValue;

    private final Consumer<AreaDimensions> onDimensionsChanged;
    private GridSettings gridSettings = GridSettings.DISABLED;

    private int activeSlider = -1; // -1 = none, 0 = width, 1 = length, 2 = height

    public DimensionsWidget(int x, int y, int width, int height,
                           AreaDimensions initialDimensions,
                           Consumer<AreaDimensions> onDimensionsChanged) {
        super(x, y, width, height, Component.empty());
        this.widthValue = initialDimensions.width();
        this.lengthValue = initialDimensions.length();
        this.heightValue = initialDimensions.height();
        this.onDimensionsChanged = onDimensionsChanged;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);

        // Title
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.builder.tab.dimensions")),
            getX(), getY(),
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        int sliderX = getX() + LABEL_WIDTH;
        int sliderWidth = getWidth() - LABEL_WIDTH - 50;

        // Width slider
        renderSlider(graphics, "Width", widthValue, AreaDimensions.MIN_SIZE, AreaDimensions.MAX_SIZE,
            getX(), getY() + 20, sliderX, sliderWidth);

        // Length slider
        renderSlider(graphics, "Length", lengthValue, AreaDimensions.MIN_SIZE, AreaDimensions.MAX_SIZE,
            getX(), getY() + 20 + SLIDER_SPACING, sliderX, sliderWidth);

        // Height slider
        renderSlider(graphics, "Height", heightValue, AreaDimensions.MIN_HEIGHT, AreaDimensions.MAX_HEIGHT,
            getX(), getY() + 20 + SLIDER_SPACING * 2, sliderX, sliderWidth);

        // Summary
        int summaryY = getY() + 20 + SLIDER_SPACING * 3 + 10;
        Component summary = Objects.requireNonNull(Component.translatable("area.dimensions.summary",
            widthValue, lengthValue, heightValue,
            (long) widthValue * lengthValue * heightValue));
        graphics.drawString(font, Objects.requireNonNull(summary), getX(), summaryY, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
    }

    private void renderSlider(GuiGraphics graphics, String label, int value, int min, int max,
                             int labelX, int y, int sliderX, int sliderWidth) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);

        // Label
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.dimensions." + label.toLowerCase(Locale.ROOT))),
            labelX, y + 6,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );

        // Slider background
        int bgColor = AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(sliderX, y, sliderX + sliderWidth, y + SLIDER_HEIGHT, bgColor);
        graphics.renderOutline(sliderX, y, sliderWidth, SLIDER_HEIGHT, AreaBuilderGuiConstants.COLOR_BORDER);

        // Slider fill
        float percentage = (float) (value - min) / (max - min);
        int fillWidth = (int) (percentage * (sliderWidth - 4));
        graphics.fill(sliderX + 2, y + 2, sliderX + 2 + fillWidth, y + SLIDER_HEIGHT - 2,
            AreaBuilderGuiConstants.COLOR_TAB_ACTIVE);

        // Value text
        String valueText = String.valueOf(value);
        int valueX = sliderX + sliderWidth + 8;
        graphics.drawString(Objects.requireNonNull(font), valueText, valueX, y + 6, AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int sliderX = getX() + LABEL_WIDTH;
            int sliderWidth = getWidth() - LABEL_WIDTH - 50;

            for (int i = 0; i < 3; i++) {
                int sliderY = getY() + 20 + SLIDER_SPACING * i;
                if (mouseX >= sliderX && mouseX <= sliderX + sliderWidth &&
                    mouseY >= sliderY && mouseY <= sliderY + SLIDER_HEIGHT) {
                    activeSlider = i;
                    updateSliderValue((int) mouseX, sliderX, sliderWidth);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (activeSlider >= 0) {
            int sliderX = getX() + LABEL_WIDTH;
            int sliderWidth = getWidth() - LABEL_WIDTH - 50;
            updateSliderValue((int) mouseX, sliderX, sliderWidth);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeSlider >= 0) {
            activeSlider = -1;
            notifyChange();
            return true;
        }
        return false;
    }

    private void updateSliderValue(int mouseX, int sliderX, int sliderWidth) {
        float percentage = Mth.clamp((mouseX - sliderX) / (float) sliderWidth, 0, 1);

        switch (activeSlider) {
            case 0 -> {
                widthValue = (int) Mth.lerp(percentage, AreaDimensions.MIN_SIZE, AreaDimensions.MAX_SIZE);
                widthValue = snapToGrid(widthValue, AreaDimensions.MIN_SIZE, AreaDimensions.MAX_SIZE, 4);
            }
            case 1 -> {
                lengthValue = (int) Mth.lerp(percentage, AreaDimensions.MIN_SIZE, AreaDimensions.MAX_SIZE);
                lengthValue = snapToGrid(lengthValue, AreaDimensions.MIN_SIZE, AreaDimensions.MAX_SIZE, 4);
            }
            case 2 -> {
                heightValue = (int) Mth.lerp(percentage, AreaDimensions.MIN_HEIGHT, AreaDimensions.MAX_HEIGHT);
                heightValue = snapToGrid(heightValue, AreaDimensions.MIN_HEIGHT, AreaDimensions.MAX_HEIGHT, 2);
            }
        }
    }

    /**
     * Snaps a value to grid if grid is enabled, otherwise uses default step.
     */
    private int snapToGrid(int value, int min, int max, int defaultStep) {
        if (gridSettings.enabled() && gridSettings.snapDimensions()) {
            return gridSettings.snapDimension(value, min, max);
        }
        return roundToStep(value, defaultStep);
    }

    private int roundToStep(int value, int step) {
        return Math.round(value / (float) step) * step;
    }

    /**
     * Sets the grid settings for dimension snapping.
     */
    public void setGridSettings(GridSettings settings) {
        this.gridSettings = settings != null ? settings : GridSettings.DISABLED;
    }

    /**
     * Gets the current grid settings.
     */
    public GridSettings getGridSettings() {
        return gridSettings;
    }

    private void notifyChange() {
        if (onDimensionsChanged != null) {
            onDimensionsChanged.accept(getDimensions());
        }
    }

    public AreaDimensions getDimensions() {
        return new AreaDimensions(widthValue, lengthValue, heightValue, AreaDimensions.DEFAULT_FLOOR_Y);
    }

    public void setDimensions(AreaDimensions dimensions) {
        this.widthValue = dimensions.width();
        this.lengthValue = dimensions.length();
        this.heightValue = dimensions.height();
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.builder.tab.dimensions")));
    }
}
