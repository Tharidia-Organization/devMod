package com.devmod.client.ui.editor.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.ScrollState;
import com.devmod.client.ui.editor.core.UIConstants;

public class VirtualizedList<T> {

    private static final int DEFAULT_ROW_HEIGHT = 24;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int FADE_ALPHA_MAX = 128;
    private static final int FADE_LINE_HEIGHT = 1;
    private static final int MIN_PAGE_SIZE = 1;

    private final String id;
    private List<T> items = new ArrayList<>();
    private int rowHeight = ScaledCoord.scaleDim(DEFAULT_ROW_HEIGHT);
    private int selectedIndex = -1;
    private final ScrollState scrollState = new ScrollState();

    // Rendering
    private BiConsumer<RowRenderContext<T>, T> rowRenderer;
    private Consumer<T> onSelect;
    private Consumer<T> onDoubleClick;

    // Bounds (set during render)
    private int x, y, width, height;

    // State
    private long lastClickTime = 0;
    private int lastClickIndex = -1;
    private static final long DOUBLE_CLICK_MS = 400;

    /**
     * Context passed to row renderer.
     */
    public record RowRenderContext<T>(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int index,
        boolean selected,
        boolean hovered,
        T item
    ) {}

    public VirtualizedList(String id) {
        this.id = id;
    }

    // =========================================================================
    // FLUENT CONFIGURATION
    // =========================================================================

    /**
     * Set the list items.
     */
    public VirtualizedList<T> items(List<T> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        // Clamp selection to valid range
        if (selectedIndex >= this.items.size()) {
            selectedIndex = this.items.isEmpty() ? -1 : this.items.size() - 1;
        }
        return this;
    }

    /**
     * Set unscaled row height.
     */
    public VirtualizedList<T> rowHeight(int height) {
        this.rowHeight = ScaledCoord.scaleDim(height);
        return this;
    }

    /**
     * Set the row renderer callback.
     */
    public VirtualizedList<T> rowRenderer(BiConsumer<RowRenderContext<T>, T> renderer) {
        this.rowRenderer = renderer;
        return this;
    }

    /**
     * Set selection callback.
     */
    public VirtualizedList<T> onSelect(Consumer<T> callback) {
        this.onSelect = callback;
        return this;
    }

    /**
     * Set double-click callback.
     */
    public VirtualizedList<T> onDoubleClick(Consumer<T> callback) {
        this.onDoubleClick = callback;
        return this;
    }

    // =========================================================================
    // SELECTION
    // =========================================================================

    /**
     * Get currently selected index.
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Set selected index.
     */
    public void setSelectedIndex(int index) {
        if (index < -1 || index >= items.size()) return;
        this.selectedIndex = index;
        if (index >= 0) {
            scrollState.scrollToItem(index, rowHeight);
        }
    }

    /**
     * Get currently selected item, or null if none selected.
     */
    public T getSelectedItem() {
        if (selectedIndex < 0 || selectedIndex >= items.size()) return null;
        return items.get(selectedIndex);
    }

    /**
     * Move selection up.
     */
    public void selectPrevious() {
        if (items.isEmpty()) return;
        if (selectedIndex <= 0) {
            setSelectedIndex(0);
        } else {
            setSelectedIndex(selectedIndex - 1);
        }
    }

    /**
     * Move selection down.
     */
    public void selectNext() {
        if (items.isEmpty()) return;
        if (selectedIndex < 0) {
            setSelectedIndex(0);
        } else if (selectedIndex < items.size() - 1) {
            setSelectedIndex(selectedIndex + 1);
        }
    }

    /**
     * Clear selection.
     */
    public void clearSelection() {
        selectedIndex = -1;
    }

    // =========================================================================
    // SCROLL
    // =========================================================================

    /**
     * Get the scroll state for external manipulation.
     */
    public ScrollState getScrollState() {
        return scrollState;
    }

    /**
     * Reset scroll to top.
     */
    public void resetScroll() {
        scrollState.reset();
    }

    // =========================================================================
    // RENDERING
    // =========================================================================

    /**
     * Render the list.
     *
     * @param graphics Graphics context
     * @param x        Left edge X
     * @param y        Top edge Y
     * @param width    Width of list area
     * @param height   Height of list area
     * @param mouseX   Mouse X position
     * @param mouseY   Mouse Y position
     */
    public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        // Update scroll state
        int contentHeight = items.size() * rowHeight;
        scrollState.update(contentHeight, height);

        // Background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.CONTENT());
        AxiomRenderer.drawBorder(graphics, x, y, width, height, UIConstants.Border.DEFAULT());

        if (items.isEmpty()) {
            return;
        }

        // Scissor for virtualization
        graphics.enableScissor(x, y, x + width, y + height);

        // Calculate visible range
        int firstRow = scrollState.getFirstVisibleRow(rowHeight);
        int rowOffset = scrollState.getRowOffset(rowHeight);
        int visibleCount = scrollState.getVisibleRowCount(rowHeight);

        // Render visible rows
        for (int i = 0; i < visibleCount && (firstRow + i) < items.size(); i++) {
            int index = firstRow + i;
            int rowY = y + (i * rowHeight) - rowOffset;

            // Skip if completely outside viewport
            if (rowY + rowHeight < y || rowY > y + height) continue;

            T item = items.get(index);
            boolean selected = index == selectedIndex;
            boolean hovered = isRowHovered(rowY, mouseX, mouseY);

            // Row background
            int rowBg = selected ? UIConstants.Background.ACTIVE()
                                 : hovered ? UIConstants.Background.HOVER()
                                           : UIConstants.Background.PANEL();
            graphics.fill(x, rowY, x + width, rowY + rowHeight, rowBg);

            // Custom row rendering
            if (rowRenderer != null) {
                RowRenderContext<T> ctx = new RowRenderContext<>(
                    graphics, x, rowY, width, rowHeight, index, selected, hovered, item
                );
                rowRenderer.accept(ctx, item);
            }
        }

        graphics.disableScissor();

        // Scroll indicators (fade gradients)
        renderScrollIndicators(graphics, x, y, width, height);

        // Optional scrollbar
        if (scrollState.isScrollable()) {
            renderScrollbar(graphics, x + width - ScaledCoord.scaleDim(UIConstants.Spacing.SM), y, height);
        }
    }

    private boolean isRowHovered(int rowY, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= rowY && mouseY <= rowY + rowHeight &&
               mouseY >= y && mouseY <= y + height;
    }

    private void renderScrollIndicators(GuiGraphics graphics, int x, int y, int width, int height) {
        int fadeH = ScaledCoord.scaleDim(UIConstants.Spacing.MD);

        if (scrollState.canScrollUp()) {
            // Top fade
            for (int i = 0; i < fadeH; i++) {
                int alpha = (int) (FADE_ALPHA_MAX * (1.0f - (float) i / fadeH));
                graphics.fill(x, y + i, x + width, y + i + FADE_LINE_HEIGHT, (alpha << 24));
            }
        }

        if (scrollState.canScrollDown()) {
            // Bottom fade
            for (int i = 0; i < fadeH; i++) {
                int alpha = (int) (FADE_ALPHA_MAX * ((float) i / fadeH));
                graphics.fill(x, y + height - fadeH + i, x + width, y + height - fadeH + i + FADE_LINE_HEIGHT,
                    (alpha << 24));
            }
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int height) {
        int trackWidth = ScaledCoord.scaleDim(SCROLLBAR_WIDTH);
        ScrollState.ScrollbarMetrics metrics = scrollState.calculateScrollbar(height);

        // Track
        graphics.fill(x, y, x + trackWidth, y + height, UIConstants.Background.PANEL());

        // Thumb
        graphics.fill(x, y + metrics.thumbY(), x + trackWidth, y + metrics.thumbBottom(),
                      UIConstants.Border.MUTED());
    }

    // =========================================================================
    // INPUT HANDLING
    // =========================================================================

    /**
     * Handle mouse click.
     *
     * @return true if consumed
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isInBounds(mouseX, mouseY)) return false;
        if (button != 0) return false;

        int clickedIndex = getIndexAtY((int) mouseY);
        if (clickedIndex >= 0 && clickedIndex < items.size()) {
            // Check for double-click
            long now = System.currentTimeMillis();
            if (clickedIndex == lastClickIndex && (now - lastClickTime) < DOUBLE_CLICK_MS) {
                // Double-click
                if (onDoubleClick != null) {
                    onDoubleClick.accept(items.get(clickedIndex));
                }
                lastClickTime = 0;
            } else {
                // Single click - select
                selectedIndex = clickedIndex;
                if (onSelect != null) {
                    onSelect.accept(items.get(clickedIndex));
                }
                lastClickTime = now;
                lastClickIndex = clickedIndex;
            }
            return true;
        }

        return true; // Consume click even if outside items
    }

    /**
     * Handle mouse scroll.
     *
     * @return true if consumed
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (!isInBounds(mouseX, mouseY)) return false;
        scrollState.scroll(scrollDelta, rowHeight);
        return true;
    }

    /**
     * Handle key press.
     *
     * @return true if consumed
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Arrow keys for selection
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
            selectPrevious();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            selectNext();
            return true;
        }
        // Enter for double-click action
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            T selected = getSelectedItem();
            if (selected != null && onDoubleClick != null) {
                onDoubleClick.accept(selected);
            }
            return true;
        }
        // Home/End
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME && !items.isEmpty()) {
            setSelectedIndex(0);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_END && !items.isEmpty()) {
            setSelectedIndex(items.size() - 1);
            return true;
        }
        // Page up/down
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            int pageSize = Math.max(MIN_PAGE_SIZE, height / rowHeight);
            setSelectedIndex(Math.max(0, selectedIndex - pageSize));
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            int pageSize = Math.max(MIN_PAGE_SIZE, height / rowHeight);
            setSelectedIndex(Math.min(items.size() - 1, selectedIndex + pageSize));
            return true;
        }

        return false;
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private boolean isInBounds(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= y && mouseY <= y + height;
    }

    private int getIndexAtY(int mouseY) {
        if (mouseY < y || mouseY > y + height) return -1;
        int localY = mouseY - y + scrollState.getOffset();
        return localY / rowHeight;
    }

    /**
     * Get the list ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Get current item count.
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * Check if list is empty.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Get bounds for external use.
     */
    public ResponsiveLayout.Rect getBounds() {
        return new ResponsiveLayout.Rect(x, y, width, height);
    }
}
