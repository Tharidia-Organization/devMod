package com.devmod.client.ui.hub;

import java.util.Objects;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.core.DesignTokens;

public final class HubSectionHeader {
    private HubSectionHeader() {}

    public static int draw(GuiGraphics graphics, Font font, String title, int x, int y, int height) {
        return draw(graphics, font, title, x, y, height, 0);
    }

    public static int draw(GuiGraphics graphics, Font font, String title, int x, int y, int height, int textOffsetY) {
        GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        Font safeFont = Objects.requireNonNull(font, "font");
        String safeTitle = Objects.requireNonNull(title, "title");
        safeGraphics.drawString(safeFont, safeTitle, x, y + textOffsetY, DesignTokens.Text.TITLE(), false);
        return y + height;
    }
}
