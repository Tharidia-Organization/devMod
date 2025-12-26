package com.devmod.client.ui.testing.panel;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.UIConstants;

import static com.devmod.client.ui.testing.panel.PanelConstants.BUTTON_HEIGHT_SMALL;
import static com.devmod.client.ui.testing.panel.PanelConstants.ROW_SPACING;
import static com.devmod.client.ui.testing.panel.PanelConstants.TITLE_HEIGHT;

/**
 * Grid layout panel for displaying buttons in a grid formation.
 * Useful for position selectors, color pickers, etc.
 */
public record GridPanel(
    String id,
    String title,
    List<EditorButton> buttons,
    int columns
) implements UIPanel {

    @Override
    public int getHeight(int availableWidth) {
        int rows = (buttons.size() + columns - 1) / columns;
        return TITLE_HEIGHT + rows * (BUTTON_HEIGHT_SMALL + ROW_SPACING);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font");

        // Title
        graphics.drawString(font, title, x, y, UIConstants.Text.SECONDARY(), false);

        // Grid
        int currentY = y + TITLE_HEIGHT;
        int btnWidth = (width - ROW_SPACING * (columns - 1)) / columns;

        for (int i = 0; i < buttons.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int bx = x + col * (btnWidth + ROW_SPACING);
            int by = currentY + row * (BUTTON_HEIGHT_SMALL + ROW_SPACING);
            buttons.get(i).render(graphics, bx, by, btnWidth, BUTTON_HEIGHT_SMALL, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (EditorButton btn : buttons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (EditorButton btn : buttons) {
            if (btn.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // FACTORY
    // ═══════════════════════════════════════════════════════════════

    public static GridPanel of(String id, String title, List<EditorButton> buttons, int columns) {
        return new GridPanel(id, title, buttons, columns);
    }

    public static GridPanel of(String title, List<EditorButton> buttons, int columns) {
        return new GridPanel("grid-" + title.toLowerCase().replace(" ", "-"), title, buttons, columns);
    }
}
