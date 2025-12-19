package com.frenkvs.devmod.ui.testing.panel;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Simple spacer panel for layout purposes.
 */
public record SpacerPanel(
    String id,
    int height
) implements UIPanel {

    /**
     * Creates a spacer with auto-generated ID.
     */
    public SpacerPanel(int height) {
        this("spacer-" + height, height);
    }

    @Override
    public String title() {
        return "";
    }

    @Override
    public int getHeight(int availableWidth) {
        return height;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        // Spacers don't render anything
    }
}
