package com.devmod.client.ui.editor.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Adapter widget to use {@link EditorButton} with the vanilla widget system.
 * This allows screens to add EditorButton via addRenderableWidget().
 */
public class EditorButtonWidget extends AbstractWidget {

    private final EditorButton button;

    public EditorButtonWidget(EditorButton button, int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal(Objects.requireNonNull(button.getLabel())));
        this.button = Objects.requireNonNull(button);
    }

    public EditorButton getButton() {
        return button;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        button.render(graphics, getX(), getY(), width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        if (button.mouseClicked(mouseX, mouseY, btn)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int btn) {
        if (button.mouseReleased(mouseX, mouseY, btn)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, btn);
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
