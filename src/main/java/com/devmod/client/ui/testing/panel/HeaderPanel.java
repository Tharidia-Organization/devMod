package com.devmod.client.ui.testing.panel;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_HEADER_ACCENT;
import static com.devmod.client.ui.testing.panel.PanelConstants.SEPARATOR_HEIGHT;
import static com.devmod.client.ui.testing.panel.PanelConstants.TITLE_HEIGHT;
public record HeaderPanel(
    String id,
    String title,
    int titleColor,
    boolean showSeparator
) implements UIPanel {

    /**
     * Creates a header with default blue color and separator.
     */
    public HeaderPanel(String title) {
        this(generateId(title), title, COLOR_HEADER_ACCENT, true);
    }

    /**
     * Creates a header with custom color and separator.
     */
    public HeaderPanel(String title, int color) {
        this(generateId(title), title, color, true);
    }

    /**
     * Creates a header with custom color and optional separator.
     */
    public HeaderPanel(String title, int color, boolean showSeparator) {
        this(generateId(title), title, color, showSeparator);
    }

    private static String generateId(String title) {
        return "header-" + title.toLowerCase().replace(" ", "-");
    }

    @Override
    public int getHeight(int availableWidth) {
        return TITLE_HEIGHT + (showSeparator ? SEPARATOR_HEIGHT : 0);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
        graphics.drawString(font, title, x, y, titleColor, false);

        if (showSeparator) {
            int lineY = y + TITLE_HEIGHT;
            graphics.fill(x, lineY, x + width, lineY + 1, titleColor);
        }
    }
}
