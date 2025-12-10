package com.frenkvs.devmod.ui.unified;

import com.frenkvs.devmod.ui.UIConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Abstract base class for settings pages that support scrolling.
 * Handles all scroll logic including:
 * - Mouse wheel scrolling
 * - Scrollbar rendering and dragging
 * - Content clipping via scissoring
 * - Hit detection offset for scrolled content
 *
 * Subclasses only need to implement:
 * - renderScrollableContent(): draw the actual content
 * - calculateTotalContentHeight(): return total height of content
 * - handleContentClick(): handle clicks on content (with adjusted Y)
 */
public abstract class ScrollableSettingsPage implements SettingsPage {

    protected static final int SCROLLBAR_WIDTH = 6;
    protected static final int SCROLL_SPEED = 20;

    // Scroll state
    protected int scrollOffset = 0;
    protected int maxScrollOffset = 0;
    protected int visibleHeight = 0;
    protected int totalContentHeight = 0;

    // Drag state
    protected boolean isDraggingScrollbar = false;

    // Cached dimensions (set during render)
    protected int contentX, contentY, contentWidth, contentHeight;

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        // Cache dimensions
        contentX = x;
        contentY = y;
        contentWidth = width;
        contentHeight = height;
        visibleHeight = height;

        // Calculate content height and max scroll
        totalContentHeight = calculateTotalContentHeight();
        maxScrollOffset = Math.max(0, totalContentHeight - visibleHeight);

        // Clamp scroll offset
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));

        // Enable scissoring to clip content
        graphics.enableScissor(x, y, x + width, y + height);

        // Render content with scroll offset applied
        renderScrollableContent(graphics, font, x, y - scrollOffset, width, height, mouseX, mouseY);

        // Disable scissoring
        graphics.disableScissor();

        // Render scrollbar if needed
        if (totalContentHeight > visibleHeight) {
            renderScrollbar(graphics, x + width - SCROLLBAR_WIDTH - 2, y, SCROLLBAR_WIDTH, height);
        }
    }

    /**
     * Render the actual page content.
     * The y parameter is already adjusted for scroll offset.
     */
    protected abstract void renderScrollableContent(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY);

    /**
     * Calculate the total height of all content.
     * Used to determine if scrolling is needed and max scroll offset.
     */
    protected abstract int calculateTotalContentHeight();

    /**
     * Handle click on content area.
     * @param adjustedY The Y coordinate adjusted for scroll offset (contentY - scrollOffset)
     * @return true if click was handled
     */
    protected abstract boolean handleContentClick(double mouseX, double mouseY, int button, int adjustedY);

    /**
     * Render the scrollbar.
     */
    protected void renderScrollbar(GuiGraphics graphics, int x, int y, int barWidth, int height) {
        // Track background
        graphics.fill(x, y, x + barWidth, y + height, UIConstants.Background.INPUT);

        // Calculate thumb size and position
        float visibleRatio = (float) visibleHeight / totalContentHeight;
        int thumbHeight = Math.max(20, (int) (height * visibleRatio));

        int thumbY;
        if (maxScrollOffset > 0) {
            float scrollRatio = (float) scrollOffset / maxScrollOffset;
            thumbY = y + (int) ((height - thumbHeight) * scrollRatio);
        } else {
            thumbY = y;
        }

        // Thumb
        int thumbColor = isDraggingScrollbar ? UIConstants.Border.ACCENT : UIConstants.Border.DEFAULT;
        graphics.fill(x, thumbY, x + barWidth, thumbY + thumbHeight, thumbColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        // Check if click is within content area
        if (mouseX < contentX || mouseX > contentX + contentWidth ||
            mouseY < contentY || mouseY > contentY + contentHeight) {
            return false;
        }

        // Check scrollbar click
        if (totalContentHeight > visibleHeight) {
            int scrollbarX = contentX + contentWidth - SCROLLBAR_WIDTH - 2;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH + 2) {
                isDraggingScrollbar = true;
                // Calculate thumb dimensions for accurate click positioning
                float visibleRatio = (float) visibleHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int) (contentHeight * visibleRatio));
                int trackHeight = contentHeight - thumbHeight;
                if (trackHeight > 0) {
                    // Jump to clicked position (centering thumb on click)
                    float clickRatio = (float)(mouseY - contentY - thumbHeight / 2) / trackHeight;
                    clickRatio = Math.max(0, Math.min(1, clickRatio));
                    scrollOffset = (int)(maxScrollOffset * clickRatio);
                }
                return true;
            }
        }

        // Pass to subclass with adjusted Y
        int adjustedY = contentY - scrollOffset;
        return handleContentClick(mouseX, mouseY, button, adjustedY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int) (scrollY * SCROLL_SPEED);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && maxScrollOffset > 0) {
            // Calculate thumb dimensions for accurate drag
            float visibleRatio = (float) visibleHeight / totalContentHeight;
            int thumbHeight = Math.max(20, (int) (contentHeight * visibleRatio));
            int trackHeight = contentHeight - thumbHeight;
            if (trackHeight > 0) {
                // Center thumb on mouse position during drag
                float dragRatio = (float)(mouseY - contentY - thumbHeight / 2) / trackHeight;
                dragRatio = Math.max(0, Math.min(1, dragRatio));
                scrollOffset = (int)(maxScrollOffset * dragRatio);
            }
            return true;
        }
        return false;
    }

    @Override
    public int getContentHeight() {
        return calculateTotalContentHeight();
    }

    /**
     * Reset scroll position. Call when page is initialized.
     */
    protected void resetScroll() {
        scrollOffset = 0;
    }

    /**
     * Get the effective content width (excluding scrollbar).
     */
    protected int getEffectiveContentWidth() {
        if (totalContentHeight > visibleHeight) {
            return contentWidth - SCROLLBAR_WIDTH - 4;
        }
        return contentWidth;
    }
}
