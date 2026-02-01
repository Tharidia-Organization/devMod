package com.devmod.client.ui.testing.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;

import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_ERROR;
import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_SEPARATOR;
import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_SUCCESS;
import static com.devmod.client.ui.testing.panel.PanelConstants.ROW_HEIGHT;
import static com.devmod.client.ui.testing.panel.PanelConstants.SEPARATOR_HEIGHT;

public final class StatusPanel implements UIPanel {

    private final String id;
    private final List<StatusItem> items;
    private final Supplier<String> messageSupplier;
    @Nullable
    private final ClickHandler clickHandler;

    private int lastX;
    private int lastY;
    private int lastWidth;

    public StatusPanel(String id, List<StatusItem> items, Supplier<String> messageSupplier,
                       @Nullable ClickHandler clickHandler) {
        this.id = id;
        this.items = items;
        this.messageSupplier = messageSupplier;
        this.clickHandler = clickHandler;
    }

    /** Single status item with label and active state supplier. */
    public record StatusItem(String label, BooleanSupplier activeSupplier,
                             int activeColor, int inactiveColor, @Nullable String tooltip) {
        public StatusItem(String label, BooleanSupplier activeSupplier) {
            this(label, activeSupplier, COLOR_SUCCESS, COLOR_ERROR, null);
        }

        public StatusItem(String label, BooleanSupplier activeSupplier,
                          int activeColor, int inactiveColor) {
            this(label, activeSupplier, activeColor, inactiveColor, null);
        }
    }

    public interface ClickHandler {
        void onClick(int index, StatusItem item, boolean active);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String title() {
        return "Status";
    }

    @Override
    public int getHeight(int availableWidth) {
        int extraRows = items != null ? items.size() : 0;
        return SEPARATOR_HEIGHT + ROW_HEIGHT + (extraRows * ROW_HEIGHT) + (messageSupplier != null ? ROW_HEIGHT : 0);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        lastX = x;
        lastY = y;
        lastWidth = width;

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
            sb.append(item.label).append(":" ).append(active ? "\u2713" : "\u2717");
        }

        int color = anyActive ? COLOR_SUCCESS : COLOR_ERROR;
        UIScaleManager.drawScaledString(graphics, font, sb.toString(), x, currentY, color, false);
        currentY += ROW_HEIGHT;

        // Per-item color indicators
        int indicatorX = x;
        int indicatorY = currentY;
        String hoveredTooltip = null;
        int tooltipX = mouseX + 8;
        int tooltipY = mouseY + 8;
        for (StatusItem item : items) {
            boolean active = item.activeSupplier.getAsBoolean();
            int itemColor = active ? item.activeColor : item.inactiveColor;
            String label = item.label + (active ? " \u2713" : " \u2715");
            if (AxiomRenderer.isMouseOver(mouseX, mouseY, indicatorX, indicatorY, width, ROW_HEIGHT)) {
                graphics.fill(indicatorX, indicatorY, indicatorX + width, indicatorY + ROW_HEIGHT,
                    com.devmod.client.ui.editor.core.DesignTokens.withAlpha(itemColor, 0x22));
            }
            UIScaleManager.drawScaledString(graphics, font, label, indicatorX, indicatorY, itemColor, false);
            if (item.tooltip != null
                && AxiomRenderer.isMouseOver(mouseX, mouseY, indicatorX, indicatorY, width, ROW_HEIGHT)) {
                hoveredTooltip = item.tooltip;
            }
            indicatorY += ROW_HEIGHT;
        }
        if (!items.isEmpty()) {
            currentY = indicatorY;
        }

        // Optional message
        if (messageSupplier != null) {
            String msg = messageSupplier.get();
            if (msg != null && !msg.isEmpty()) {
                int msgColor = msg.contains("Failed") || msg.contains("failed") ? COLOR_ERROR : COLOR_SUCCESS;
                UIScaleManager.drawScaledString(graphics, font, msg, x, currentY, msgColor, false);
            }
        }

        if (hoveredTooltip != null && !hoveredTooltip.isEmpty()) {
            AxiomRenderer.drawTooltip(graphics, font, tooltipX, tooltipY, hoveredTooltip);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || clickHandler == null) {
            return false;
        }
        int relativeY = (int) mouseY - lastY;
        int startY = SEPARATOR_HEIGHT + ROW_HEIGHT;
        for (int i = 0; i < items.size(); i++) {
            int rowTop = startY + i * ROW_HEIGHT;
            int rowBottom = rowTop + ROW_HEIGHT;
            if (relativeY >= rowTop && relativeY <= rowBottom
                && AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, lastX, lastY, lastWidth, getHeight(lastWidth))) {
                boolean active = items.get(i).activeSupplier.getAsBoolean();
                clickHandler.onClick(i, items.get(i), active);
                return true;
            }
        }
        return false;
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
        private Supplier<String> messageSupplier = () -> "";
        @Nullable
        private ClickHandler clickHandler;

        private Builder(String id) {
            this.id = id;
        }

        public Builder addStatus(String label, BooleanSupplier supplier) {
            items.add(new StatusItem(label, supplier));
            return this;
        }

        public Builder addStatus(StatusItem item) {
            items.add(item);
            return this;
        }

        public Builder onClick(ClickHandler handler) {
            this.clickHandler = handler;
            return this;
        }

        public Builder messageSupplier(Supplier<String> supplier) {
            this.messageSupplier = supplier;
            return this;
        }

        public StatusPanel build() {
            return new StatusPanel(id, List.copyOf(items), messageSupplier, clickHandler);
        }
    }
}
