package com.devmod.client.ui.editor.sections;

import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;

public final class SliderSectionAdapter implements EditorSection.SliderSection {
    private final EditorSlider slider;
    private final int height;

    public SliderSectionAdapter(EditorSlider slider) {
        this(slider, slider.calculateHeight());
    }

    public SliderSectionAdapter(EditorSlider slider, int height) {
        this.slider = slider;
        this.height = height;
    }

    @Override
    public String getId() { return slider.getId(); }

    @Override
    public String getLabel() { return slider.getLabel(); }

    @Override
    public int getHeight() { return height; }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        slider.render(graphics, bounds.x(), bounds.y(), bounds.width(), mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return slider.mouseClicked(mouseX, mouseY, button); }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { return slider.mouseReleased(mouseX, mouseY, button); }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return slider.mouseDragged(mouseX, mouseY, button, dragX, dragY); }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return slider.keyPressed(keyCode, scanCode, modifiers); }
    @Override public boolean charTyped(char chr, int modifiers) { return slider.charTyped(chr, modifiers); }

    @Override public float getValue() { return slider.getValue(); }
    @Override public void setValue(float value) { slider.setValue(value); }
    @Override public float getMin() { return slider.getMin(); }
    @Override public float getMax() { return slider.getMax(); }
    @Override public float getStep() { return slider.getStep(); }
    @Override public String getFormat() { return "%.2f"; }
    @Override public int getColor() { return UIConstants.SliderColors.NEUTRAL; }
    @Override public boolean isDragging() { return slider.isDragging(); }
    @Override public void setDragging(boolean dragging) { }
}
