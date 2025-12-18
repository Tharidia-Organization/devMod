package com.frenkvs.devmod.ui.testing.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static com.frenkvs.devmod.ui.testing.panel.PanelConstants.*;

/**
 * Status indicators panel showing multiple boolean states and optional message.
 */
public record StatusPanel(
    String id,
    List<StatusItem> items,
    Supplier<String> messageSupplier
) implements UIPanel {

    /**
     * Single status item with label and active state supplier.
     */
    public record StatusItem(String label, BooleanSupplier activeSupplier) {}

    @Override
    public String title() {
        return "Status";
    }

    @Override
    public int getHeight(int availableWidth) {
        return SEPARATOR_HEIGHT + ROW_HEIGHT + (messageSupplier != null ? ROW_HEIGHT : 0);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
        int currentY = y;

        // Top separator
        graphics.fill(x, currentY, x + width, currentY + 1, COLOR_SEPARATOR);
        currentY += SEPARATOR_HEIGHT;

        // Build status line
        StringBuilder sb = new StringBuilder();
        boolean anyActive = false;

        for (StatusItem item : items) {
            boolean active = item.activeSupplier.getAsBoolean();
            if (active) anyActive = true;
            if (!sb.isEmpty()) sb.append("  ");
            sb.append(item.label).append(":").append(active ? "\u2713" : "\u2717");
        }

        int color = anyActive ? COLOR_SUCCESS : COLOR_ERROR;
        graphics.drawString(font, sb.toString(), x, currentY, color, false);
        currentY += ROW_HEIGHT;

        // Optional message
        if (messageSupplier != null) {
            String msg = messageSupplier.get();
            if (msg != null && !msg.isEmpty()) {
                int msgColor = msg.contains("Failed") || msg.contains("failed") ? COLOR_ERROR : COLOR_SUCCESS;
                graphics.drawString(font, msg, x, currentY, msgColor, false);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private final ArrayList<StatusItem> items = new ArrayList<>();
        private Supplier<String> messageSupplier;

        private Builder(String id) {
            this.id = id;
        }

        public Builder addStatus(String label, BooleanSupplier supplier) {
            items.add(new StatusItem(label, supplier));
            return this;
        }

        public Builder messageSupplier(Supplier<String> supplier) {
            this.messageSupplier = supplier;
            return this;
        }

        public StatusPanel build() {
            return new StatusPanel(id, List.copyOf(items), messageSupplier);
        }
    }
}
