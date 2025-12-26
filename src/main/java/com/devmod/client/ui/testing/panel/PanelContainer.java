package com.devmod.client.ui.testing.panel;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import com.devmod.client.ui.editor.core.UIConstants;

import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_THUMB_HOVER;
import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_THUMB_NORMAL;
import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_TRACK;
import static com.devmod.client.ui.testing.panel.PanelConstants.PANEL_SPACING;
import static com.devmod.client.ui.testing.panel.PanelConstants.SCROLLBAR_PADDING;
import static com.devmod.client.ui.testing.panel.PanelConstants.SCROLLBAR_THUMB_MIN_HEIGHT;
import static com.devmod.client.ui.testing.panel.PanelConstants.SCROLLBAR_WIDTH;
public final class PanelContainer {

    private final List<UIPanel> panels = new ArrayList<>();

    // Bounds
    private int x, y, width, height;

    // Scroll state
    private int scrollOffset = 0;
    private int totalContentHeight = 0;
    private boolean hasScrollbar = false;

    // Appearance
    private boolean showBackground = true;
    private int backgroundColor = UIConstants.Background.PANEL();
    private int padding = 8;

    // Scrollbar interaction
    private boolean draggingScrollbar = false;
    private int dragStartY = 0;
    private int dragStartOffset = 0;

    public PanelContainer() {}

    // ═══════════════════════════════════════════════════════════════
    // FLUENT CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    public PanelContainer showBackground(boolean show) {
        this.showBackground = show;
        return this;
    }

    public PanelContainer backgroundColor(int color) {
        this.backgroundColor = color;
        return this;
    }

    public PanelContainer padding(int padding) {
        this.padding = padding;
        return this;
    }

    public PanelContainer bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // PANEL MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    public PanelContainer addPanel(UIPanel panel) {
        panels.add(panel);
        return this;
    }

    public PanelContainer addPanels(UIPanel... panels) {
        for (UIPanel panel : panels) {
            this.panels.add(panel);
        }
        return this;
    }

    public void clearPanels() {
        panels.clear();
        scrollOffset = 0;
        totalContentHeight = 0;
        hasScrollbar = false;
    }

    public List<UIPanel> getPanels() {
        return panels;
    }

    /**
     * @deprecated Use {@link #bounds(int, int, int, int)} instead
     */
    @Deprecated
    public void setBounds(int x, int y, int width, int height) {
        bounds(x, y, width, height);
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    public void init() {
        for (UIPanel panel : panels) {
            panel.init();
        }
        recalculateLayout();
    }

    public void tick() {
        for (UIPanel panel : panels) {
            panel.tick();
        }
        recalculateLayout();
    }

    private void recalculateLayout() {
        // First pass: calculate content height without scrollbar
        int contentWidth = width - padding * 2;
        totalContentHeight = calculateTotalHeight(contentWidth);

        // Determine if scrollbar is needed
        int viewportHeight = height - padding * 2;
        hasScrollbar = totalContentHeight > viewportHeight;

        // Second pass: recalculate with scrollbar width if needed
        if (hasScrollbar) {
            contentWidth = width - padding * 2 - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;
            totalContentHeight = calculateTotalHeight(contentWidth);
        }

        // Clamp scroll offset
        int maxScroll = Math.max(0, totalContentHeight - viewportHeight);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    private int calculateTotalHeight(int contentWidth) {
        int height = 0;
        for (UIPanel panel : panels) {
            if (panel.isVisible()) {
                height += panel.getHeight(contentWidth) + PANEL_SPACING;
            }
        }
        if (!panels.isEmpty() && height > 0) {
            height -= PANEL_SPACING; // Remove last spacing
        }
        return height;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        // Background
        if (showBackground) {
            graphics.fill(x, y, x + width, y + height, backgroundColor);
        }

        // Content area
        int contentX = x + padding;
        int contentY = y + padding;
        int contentWidth = width - padding * 2 - (hasScrollbar ? SCROLLBAR_WIDTH + SCROLLBAR_PADDING : 0);
        int contentHeight = height - padding * 2;

        // Scissor for clipping with try/finally for safety
        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        try {
            // Render visible panels (viewport culling)
            int currentY = contentY - scrollOffset;
            for (UIPanel panel : panels) {
                if (!panel.isVisible()) continue;

                int panelHeight = panel.getHeight(contentWidth);

                // Only render if in viewport
                if (currentY + panelHeight > contentY && currentY < contentY + contentHeight) {
                    panel.render(graphics, contentX, currentY, contentWidth, mouseX, mouseY);
                }

                currentY += panelHeight + PANEL_SPACING;
            }
        } finally {
            graphics.disableScissor();
        }

        // Scrollbar
        if (hasScrollbar) {
            renderScrollbar(graphics, mouseX, mouseY);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int scrollbarX = x + width - padding - SCROLLBAR_WIDTH;
        int scrollbarY = y + padding;
        int scrollbarHeight = height - padding * 2;

        // Track
        graphics.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, COLOR_TRACK);

        // Thumb
        int viewportHeight = height - padding * 2;
        int maxScroll = totalContentHeight - viewportHeight;
        if (maxScroll <= 0) return;

        float scrollRatio = (float) scrollOffset / maxScroll;
        float thumbRatio = (float) viewportHeight / totalContentHeight;
        int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, (int) (scrollbarHeight * thumbRatio));
        int thumbY = scrollbarY + (int) ((scrollbarHeight - thumbHeight) * scrollRatio);

        boolean hovered = mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH &&
                         mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
        int thumbColor = (hovered || draggingScrollbar) ? COLOR_THUMB_HOVER : COLOR_THUMB_NORMAL;

        graphics.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, thumbColor);
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        if (!hasScrollbar) return false;

        int viewportHeight = height - padding * 2;
        int maxScroll = totalContentHeight - viewportHeight;
        scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * 20), 0, maxScroll);
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        // Scrollbar click
        if (hasScrollbar && button == 0) {
            int scrollbarX = x + width - padding - SCROLLBAR_WIDTH;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH) {
                draggingScrollbar = true;
                dragStartY = (int) mouseY;
                dragStartOffset = scrollOffset;
                return true;
            }
        }

        // Forward to panels
        for (UIPanel panel : panels) {
            if (panel.isVisible() && panel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }

        for (UIPanel panel : panels) {
            if (panel.isVisible() && panel.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && hasScrollbar) {
            int scrollbarHeight = height - padding * 2;
            int viewportHeight = height - padding * 2;
            int maxScroll = totalContentHeight - viewportHeight;
            float thumbRatio = (float) viewportHeight / totalContentHeight;
            int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, (int) (scrollbarHeight * thumbRatio));
            int trackRange = scrollbarHeight - thumbHeight;

            if (trackRange > 0) {
                int deltaYPixels = (int) mouseY - dragStartY;
                float scrollDelta = (float) deltaYPixels / trackRange * maxScroll;
                scrollOffset = Mth.clamp(dragStartOffset + (int) scrollDelta, 0, maxScroll);
            }
            return true;
        }

        // Forward to panels
        for (UIPanel panel : panels) {
            if (panel.isVisible() && panel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    // ═══════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public int getScrollOffset() { return scrollOffset; }

    public void setScrollOffset(int offset) {
        int viewportHeight = height - padding * 2;
        int maxScroll = Math.max(0, totalContentHeight - viewportHeight);
        this.scrollOffset = Mth.clamp(offset, 0, maxScroll);
    }

    public void scrollToTop() { scrollOffset = 0; }

    public void scrollToBottom() {
        int viewportHeight = height - padding * 2;
        scrollOffset = Math.max(0, totalContentHeight - viewportHeight);
    }

    public boolean hasScrollbar() { return hasScrollbar; }

    public int getTotalContentHeight() { return totalContentHeight; }
}
