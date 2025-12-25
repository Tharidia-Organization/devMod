package com.devmod.client.ui.testing.panel;

import com.devmod.client.ui.editor.components.EditorButton;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

import static com.devmod.client.ui.testing.panel.PanelConstants.*;

/**
 * Sealed interface for button row layouts within SectionPanel.
 */
public sealed interface ButtonRow permits
    ButtonRow.FullWidth,
    ButtonRow.EqualWidth,
    ButtonRow.Spacer {

    int getHeight();

    void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY);

    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }

    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }

    // ═══════════════════════════════════════════════════════════════
    // ROW TYPES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Single button spanning full width.
     */
    record FullWidth(EditorButton button) implements ButtonRow {
        @Override
        public int getHeight() {
            return button.getSize() == EditorButton.Size.SMALL ? BUTTON_HEIGHT_SMALL : BUTTON_HEIGHT_NORMAL;
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            button.render(graphics, x, y, width, getHeight(), mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int btn) {
            return button.mouseClicked(mouseX, mouseY, btn);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int btn) {
            return button.mouseReleased(mouseX, mouseY, btn);
        }
    }

    /**
     * Multiple buttons with equal widths.
     */
    record EqualWidth(List<EditorButton> buttons) implements ButtonRow {
        @Override
        public int getHeight() {
            boolean hasSmall = buttons.stream()
                .anyMatch(b -> b.getSize() == EditorButton.Size.SMALL);
            return hasSmall ? BUTTON_HEIGHT_SMALL : BUTTON_HEIGHT_NORMAL;
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            if (buttons.isEmpty()) return;

            int count = buttons.size();
            int totalSpacing = BUTTON_SPACING * (count - 1);
            int btnWidth = (width - totalSpacing) / count;
            int height = getHeight();

            for (int i = 0; i < count; i++) {
                int bx = x + i * (btnWidth + BUTTON_SPACING);
                buttons.get(i).render(graphics, bx, y, btnWidth, height, mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int btn) {
            for (EditorButton button : buttons) {
                if (button.mouseClicked(mouseX, mouseY, btn)) return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int btn) {
            for (EditorButton button : buttons) {
                if (button.mouseReleased(mouseX, mouseY, btn)) return true;
            }
            return false;
        }
    }

    /**
     * Empty space between rows.
     */
    record Spacer(int height) implements ButtonRow {
        @Override
        public int getHeight() { return height; }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            // Spacers don't render anything
        }
    }
}
