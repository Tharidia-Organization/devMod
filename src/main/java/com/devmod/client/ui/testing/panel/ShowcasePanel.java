package com.devmod.ui.testing.panel;

import com.devmod.ui.editor.components.EditorButton;
import com.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.devmod.ui.testing.panel.PanelConstants.*;

/**
 * Showcase panel for demonstrating UI components with descriptions.
 * Displays items in a grid with a description label below each item.
 */
public final class ShowcasePanel implements UIPanel {

    private final String id;
    private final String title;
    private final List<ShowcaseItem> items;
    private final int columns;

    private ShowcasePanel(String id, String title, List<ShowcaseItem> items, int columns) {
        this.id = id;
        this.title = title;
        this.items = new ArrayList<>(items);
        this.columns = columns;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public int getHeight(int availableWidth) {
        int rows = (items.size() + columns - 1) / columns;
        int itemHeight = BUTTON_HEIGHT_MEDIUM + 14; // button + description
        return TITLE_HEIGHT + rows * (itemHeight + ROW_SPACING);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font");

        // Title
        if (title != null && !title.isEmpty()) {
            graphics.drawString(font, title, x, y, UIConstants.Text.TITLE(), false);
        }

        // Grid of items
        int currentY = y + TITLE_HEIGHT;
        int itemWidth = (width - ROW_SPACING * (columns - 1)) / columns;
        int itemHeight = BUTTON_HEIGHT_MEDIUM + 14;

        for (int i = 0; i < items.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int itemX = x + col * (itemWidth + ROW_SPACING);
            int itemY = currentY + row * (itemHeight + ROW_SPACING);

            ShowcaseItem item = items.get(i);

            // Update button bounds for hit testing
            item.updateBounds(itemX, itemY, itemWidth, BUTTON_HEIGHT_MEDIUM);

            // Render button
            item.button.render(graphics, itemX, itemY, itemWidth, BUTTON_HEIGHT_MEDIUM, mouseX, mouseY);

            // Render description below
            graphics.drawString(font, item.description, itemX, itemY + BUTTON_HEIGHT_MEDIUM + 4,
                UIConstants.Text.SECONDARY(), false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (ShowcaseItem item : items) {
            if (item.button.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (ShowcaseItem item : items) {
            if (item.button.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // SHOWCASE ITEM
    // ═══════════════════════════════════════════════════════════════

    /**
     * A single item in the showcase with button and description.
     */
    public static final class ShowcaseItem {
        private final EditorButton button;
        private final String description;

        public ShowcaseItem(EditorButton button, String description) {
            this.button = button;
            this.description = description;
        }

        void updateBounds(int x, int y, int width, int height) {
            // EditorButton stores its last render bounds internally
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════

    public static Builder builder(String id, String title) {
        return new Builder(id, title);
    }

    public static final class Builder {
        private final String id;
        private final String title;
        private final List<ShowcaseItem> items = new ArrayList<>();
        private int columns = 2;

        private Builder(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public Builder columns(int columns) {
            this.columns = columns;
            return this;
        }

        public Builder addItem(EditorButton button, String description) {
            items.add(new ShowcaseItem(button, description));
            return this;
        }

        public Builder addItem(ShowcaseItem item) {
            items.add(item);
            return this;
        }

        public ShowcasePanel build() {
            return new ShowcasePanel(id, title, items, columns);
        }
    }
}
