package com.frenkvs.devmod.ui.hub;

import com.frenkvs.devmod.ui.UIConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

public final class HubSectionHeader {
    private HubSectionHeader() {}

    public static int draw(GuiGraphics graphics, Font font, String title, int x, int y, int height) {
        return draw(graphics, font, title, x, y, height, 0);
    }

    public static int draw(GuiGraphics graphics, Font font, String title, int x, int y, int height, int textOffsetY) {
        GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        Font safeFont = Objects.requireNonNull(font, "font");
        String safeTitle = Objects.requireNonNull(title, "title");
        safeGraphics.drawString(safeFont, safeTitle, x, y + textOffsetY, UIConstants.Text.TITLE(), false);
        return y + height;
    }
}
