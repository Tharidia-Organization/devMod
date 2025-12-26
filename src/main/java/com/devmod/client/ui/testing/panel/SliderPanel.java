package com.devmod.client.ui.testing.panel;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import com.devmod.client.ui.editor.core.UIConstants;

import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_CYAN;
import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_HEADER_ACCENT;
import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_THUMB_HOVER;
import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_TRACK;
import static com.devmod.client.ui.testing.panel.PanelConstants.SLIDER_HEIGHT;
public final class SliderPanel implements UIPanel {

    private final String id;
    private final String title;
    private final Supplier<Double> valueGetter;
    private final Consumer<Double> valueSetter;
    private final double min;
    private final double max;
    private final double step;
    private final String format;

    // Drag state
    private boolean dragging = false;
    private int lastRenderX, lastRenderY, lastRenderWidth;

    public SliderPanel(
        String id,
        String title,
        Supplier<Double> valueGetter,
        Consumer<Double> valueSetter,
        double min,
        double max,
        double step,
        String format
    ) {
        this.id = id;
        this.title = title;
        this.valueGetter = valueGetter;
        this.valueSetter = valueSetter;
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = format;
    }

    public SliderPanel(String id, String title, Supplier<Double> valueGetter, Consumer<Double> valueSetter, double min, double max) {
        this(id, title, valueGetter, valueSetter, min, max, 0.1, "%.1f");
    }

    @Override
    public String id() { return id; }

    @Override
    public String title() { return title; }

    @Override
    public int getHeight(int availableWidth) {
        return SLIDER_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font");

        // Store for mouse handling
        lastRenderX = x;
        lastRenderY = y;
        lastRenderWidth = width;

        // Title
        double value = valueGetter.get();
        String valueStr = Objects.requireNonNull(String.format(format, value), "value string");
        graphics.drawString(font, title, x, y, UIConstants.Text.PRIMARY(), false);
        graphics.drawString(font, valueStr, x + width - font.width(valueStr), y, COLOR_CYAN, false);

        // Slider track
        int trackY = y + 16;
        int trackHeight = 8;
        graphics.fill(x, trackY, x + width, trackY + trackHeight, COLOR_TRACK);

        // Slider thumb
        double ratio = (value - min) / (max - min);
        int thumbWidth = 8;
        int thumbX = x + (int) ((width - thumbWidth) * ratio);
        int thumbColor = dragging ? COLOR_THUMB_HOVER : COLOR_HEADER_ACCENT;
        graphics.fill(thumbX, trackY - 2, thumbX + thumbWidth, trackY + trackHeight + 2, thumbColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int trackY = lastRenderY + 16;
        int trackHeight = 8;

        // Check if click is on slider area
        if (mouseX >= lastRenderX && mouseX <= lastRenderX + lastRenderWidth &&
            mouseY >= trackY - 4 && mouseY <= trackY + trackHeight + 4) {
            dragging = true;
            updateValueFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            updateValueFromMouse(mouseX);
            return true;
        }
        return false;
    }

    private void updateValueFromMouse(double mouseX) {
        double ratio = (mouseX - lastRenderX) / lastRenderWidth;
        ratio = Mth.clamp(ratio, 0.0, 1.0);
        double newValue = min + ratio * (max - min);

        // Snap to step
        if (step > 0) {
            newValue = Math.round(newValue / step) * step;
        }

        newValue = Mth.clamp(newValue, min, max);
        valueSetter.accept(newValue);
    }

    // ═══════════════════════════════════════════════════════════════
    // FACTORY
    // ═══════════════════════════════════════════════════════════════

    public static SliderPanel of(String id, String title, Supplier<Double> getter, Consumer<Double> setter, double min, double max) {
        return new SliderPanel(id, title, getter, setter, min, max);
    }

    public static SliderPanel of(String id, String title, Supplier<Double> getter, Consumer<Double> setter, double min, double max, double step, String format) {
        return new SliderPanel(id, title, getter, setter, min, max, step, format);
    }
}
