package com.devmod.client.ui.editor.sections;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorTextField;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import net.minecraft.client.gui.GuiGraphics;

public final class InputSectionAdapter implements EditorSection.InputSection {
    private final EditorTextField field;
    private final boolean clearOnRightClick;

    public InputSectionAdapter(EditorTextField field) {
        this(field, false);
    }

    public InputSectionAdapter(EditorTextField field, boolean clearOnRightClick) {
        this.field = field;
        this.clearOnRightClick = clearOnRightClick;
    }

    @Override
    public String getId() { return field.getId(); }

    @Override
    public String getLabel() { return field.getLabel(); }

    @Override
    public int getHeight() { return field.calculateHeight(); }

    @Override
    public String getText() { return field.getValue(); }

    @Override
    public void setText(String text) { field.setValue(text); }

    @Override
    public String getPlaceholder() { return ""; }

    @Override
    public boolean isNumeric() { return false; }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        field.render(graphics, bounds.x(), bounds.y(), bounds.width(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clearOnRightClick && button == 1 && field.getBounds().contains((int) mouseX, (int) mouseY)) {
            field.setValue("");
            return true;
        }
        return field.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return field.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return field.charTyped(chr, modifiers);
    }
}
