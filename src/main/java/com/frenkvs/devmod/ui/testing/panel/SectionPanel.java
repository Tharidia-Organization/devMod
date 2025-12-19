package com.frenkvs.devmod.ui.testing.panel;

import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.frenkvs.devmod.ui.testing.panel.PanelConstants.*;

/**
 * Section panel with title, optional description, and button rows.
 * Use the Builder for fluent construction.
 */
public record SectionPanel(
    String id,
    String title,
    int titleColor,
    String description,
    boolean showSeparator,
    List<ButtonRow> rows
) implements UIPanel {

    @Override
    public int getHeight(int availableWidth) {
        int height = TITLE_HEIGHT;
        if (description != null) height += DESCRIPTION_HEIGHT;
        if (showSeparator) height += SEPARATOR_HEIGHT;

        for (ButtonRow row : rows) {
            height += row.getHeight() + ROW_SPACING;
        }
        return height;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
        int currentY = y;

        // Title
        graphics.drawString(font, title, x, currentY, titleColor, false);
        currentY += TITLE_HEIGHT;

        // Description
        if (description != null) {
            graphics.drawString(font, description, x, currentY, UIConstants.Text.SECONDARY(), false);
            currentY += DESCRIPTION_HEIGHT;
        }

        // Separator
        if (showSeparator) {
            graphics.fill(x, currentY, x + width, currentY + 1, COLOR_SEPARATOR);
            currentY += SEPARATOR_HEIGHT;
        }

        // Button rows
        for (ButtonRow row : rows) {
            row.render(graphics, x, currentY, width, mouseX, mouseY);
            currentY += row.getHeight() + ROW_SPACING;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (ButtonRow row : rows) {
            if (row.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (ButtonRow row : rows) {
            if (row.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return false;
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
        private int titleColor = UIConstants.Text.PRIMARY();
        private String description;
        private boolean showSeparator = false;
        private final ArrayList<ButtonRow> rows = new ArrayList<>();

        private Builder(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public Builder titleColor(int color) {
            this.titleColor = color;
            return this;
        }

        public Builder description(String desc) {
            this.description = desc;
            return this;
        }

        public Builder withSeparator() {
            this.showSeparator = true;
            return this;
        }

        public Builder addButton(EditorButton button) {
            rows.add(new ButtonRow.FullWidth(button));
            return this;
        }

        public Builder addRow(EditorButton... buttons) {
            rows.add(new ButtonRow.EqualWidth(List.of(buttons)));
            return this;
        }

        public Builder addRow(List<EditorButton> buttons) {
            rows.add(new ButtonRow.EqualWidth(buttons));
            return this;
        }

        public Builder addSpacer(int height) {
            rows.add(new ButtonRow.Spacer(height));
            return this;
        }

        public SectionPanel build() {
            return new SectionPanel(id, title, titleColor, description, showSeparator, List.copyOf(rows));
        }
    }
}
