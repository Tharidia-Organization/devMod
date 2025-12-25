package com.devmod.client.ui.editor.sections;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import net.minecraft.client.gui.GuiGraphics;

public final class ToggleSectionAdapter implements EditorSection.ToggleSection {
    private final EditorToggle toggle;
    private final int height;

    public ToggleSectionAdapter(EditorToggle toggle) {
        this(toggle, toggle.calculateHeight());
    }

    public ToggleSectionAdapter(EditorToggle toggle, int height) {
        this.toggle = toggle;
        this.height = height;
    }

    @Override
    public String getId() { return toggle.getId(); }

    @Override
    public String getLabel() { return toggle.getLabel(); }

    @Override
    public int getHeight() { return height; }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        toggle.render(graphics, bounds.x(), bounds.y(), bounds.width(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return toggle.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return toggle.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean getValue() { return toggle.getValue(); }

    @Override
    public void setValue(boolean value) { toggle.setValue(value); }
}
