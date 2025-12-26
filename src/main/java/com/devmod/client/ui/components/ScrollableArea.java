package com.devmod.client.ui.components;

import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.core.UIConstants;

public class ScrollableArea {

    // Configuration
    private final int x, y, width, height;
    private int contentHeight;
    private int scrollOffset = 0;

    // Scroll behavior
    private static final int SCROLL_SPEED = 15;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MIN_HEIGHT = 20;

    // State
    private boolean showScrollbar = true;
    private boolean isDraggingScrollbar = false;
    private int dragStartY = 0;
    private int dragStartOffset = 0;

    /**
     * Creates a new scrollable area.
     *
     * @param x Left position
     * @param y Top position
     * @param width Area width
     * @param height Visible area height
     * @param contentHeight Total content height (can exceed visible height)
     */
    public ScrollableArea(int x, int y, int width, int height, int contentHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.contentHeight = contentHeight;
    }

    /**
     * Updates the content height (call when content changes).
     */
    public void setContentHeight(int contentHeight) {
        this.contentHeight = contentHeight;
        clampScrollOffset();
    }

    /**
     * Gets the current scroll offset to apply to content Y positions.
     */
    public int getScrollOffset() {
        return scrollOffset;
    }

    /**
     * Gets the maximum scroll offset.
     */
    public int getMaxScroll() {
        return Math.max(0, contentHeight - height);
    }

    /**
     * Checks if scrolling is needed (content exceeds visible area).
     */
    public boolean needsScrolling() {
        return contentHeight > height;
    }

    /**
     * Handles mouse scroll events.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param scrollY Scroll delta (positive = scroll up, negative = scroll down)
     * @return true if the event was consumed
     */
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        if (!needsScrolling()) return false;

        scrollOffset -= (int) (scrollY * SCROLL_SPEED);
        clampScrollOffset();
        return true;
    }

    /**
     * Handles mouse click for scrollbar dragging.
     *
     * @return true if click was on scrollbar
     */
    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !showScrollbar || !needsScrolling()) return false;

        int scrollbarX = x + width - SCROLLBAR_WIDTH - 2;
        if (mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_WIDTH) {
            // Check if click is on the scrollbar thumb
            int[] thumbBounds = getScrollbarThumbBounds();
            if (mouseY >= thumbBounds[0] && mouseY < thumbBounds[1]) {
                isDraggingScrollbar = true;
                dragStartY = (int) mouseY;
                dragStartOffset = scrollOffset;
                return true;
            }
            // Click on track - jump to position (centering thumb on click)
            int trackHeight = height - 4;
            int thumbHeight = getScrollbarThumbHeight();
            int effectiveTrackHeight = trackHeight - thumbHeight;
            if (effectiveTrackHeight > 0) {
                // Center thumb on click position
                float clickRatio = (float) (mouseY - y - 2 - thumbHeight / 2) / effectiveTrackHeight;
                clickRatio = Math.max(0, Math.min(1, clickRatio)); // Clamp to valid range
                scrollOffset = (int) (clickRatio * getMaxScroll());
                clampScrollOffset();
            }
            return true;
        }
        return false;
    }

    /**
     * Handles mouse drag for scrollbar.
     */
    public boolean handleMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isDraggingScrollbar || button != 0) return false;

        int trackHeight = height - 4 - getScrollbarThumbHeight();
        if (trackHeight <= 0) return true;

        int deltaY = (int) mouseY - dragStartY;
        float scrollRatio = (float) deltaY / trackHeight;
        scrollOffset = dragStartOffset + (int) (scrollRatio * getMaxScroll());
        clampScrollOffset();
        return true;
    }

    /**
     * Handles mouse release.
     */
    public boolean handleMouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar && button == 0) {
            isDraggingScrollbar = false;
            return true;
        }
        return false;
    }

    /**
     * Enables scissor clipping for the scrollable area.
     * Call before rendering content.
     * @deprecated Use {@link #withScissor(GuiGraphics, Runnable)} instead for safe try/finally handling
     */
    @Deprecated
    public void beginScissor(GuiGraphics graphics) {
        graphics.enableScissor(x, y, x + width, y + height);
    }

    /**
     * Disables scissor clipping.
     * Call after rendering content.
     * @deprecated Use {@link #withScissor(GuiGraphics, Runnable)} instead for safe try/finally handling
     */
    @Deprecated
    public void endScissor(GuiGraphics graphics) {
        graphics.disableScissor();
    }

    /**
     * Execute a render operation within scissor bounds.
     * Ensures scissor is always disabled, even on exception.
     *
     * @param graphics GuiGraphics instance
     * @param renderAction Rendering code to execute within scissor
     */
    public void withScissor(GuiGraphics graphics, Runnable renderAction) {
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            renderAction.run();
        } finally {
            graphics.disableScissor();
        }
    }

    /**
     * Renders the scrollbar if needed.
     */
    public void renderScrollbar(GuiGraphics graphics) {
        if (!showScrollbar || !needsScrolling()) return;

        int scrollbarX = x + width - SCROLLBAR_WIDTH - 2;

        // Track background
        graphics.fill(scrollbarX, y + 2, scrollbarX + SCROLLBAR_WIDTH, y + height - 2,
                UIConstants.Background.INPUT());

        // Thumb
        int[] thumbBounds = getScrollbarThumbBounds();
        int thumbColor = isDraggingScrollbar ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT();
        graphics.fill(scrollbarX, thumbBounds[0], scrollbarX + SCROLLBAR_WIDTH, thumbBounds[1], thumbColor);
    }

    // === Private Helpers ===

    private boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void clampScrollOffset() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
    }

    private int getScrollbarThumbHeight() {
        if (contentHeight <= 0) return height - 4;
        float ratio = (float) height / contentHeight;
        return Math.max(SCROLLBAR_MIN_HEIGHT, (int) ((height - 4) * ratio));
    }

    private int[] getScrollbarThumbBounds() {
        int trackHeight = height - 4;
        int thumbHeight = getScrollbarThumbHeight();
        int maxThumbY = trackHeight - thumbHeight;

        float scrollRatio = getMaxScroll() > 0 ? (float) scrollOffset / getMaxScroll() : 0;
        int thumbY = y + 2 + (int) (maxThumbY * scrollRatio);

        return new int[]{thumbY, thumbY + thumbHeight};
    }

    // === Getters/Setters ===

    public void setShowScrollbar(boolean show) {
        this.showScrollbar = show;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getContentHeight() { return contentHeight; }

    /**
     * Scrolls to make a specific Y position visible.
     */
    public void scrollToVisible(int targetY) {
        if (targetY < scrollOffset) {
            scrollOffset = targetY;
        } else if (targetY > scrollOffset + height - 20) {
            scrollOffset = targetY - height + 20;
        }
        clampScrollOffset();
    }

    /**
     * Resets scroll to top.
     */
    public void resetScroll() {
        scrollOffset = 0;
    }
}
