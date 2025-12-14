package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.EditorDimensions;
import com.frenkvs.devmod.ui.editor.core.EditorSpacing;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Scrollable content area for module content.
 * Handles scrolling, scissoring, and scrollbar rendering.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 2.6 (Content Area)
 * @see EDITOR_DESIGN_SYSTEM.md Section 4.11 (Scrollable Content Area)
 */
public class ScrollableContentArea {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS
    // ═══════════════════════════════════════════════════════════════

    private static final int SCROLLBAR_WIDTH = EditorDimensions.SCROLLBAR_WIDTH;  // 8px
    private static final int PADDING = EditorSpacing.S;  // 8px

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect contentBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect scrollbarBounds = ResponsiveLayout.Rect.EMPTY;

    // Scroll state
    private float scrollOffset = 0;
    private int contentHeight = 0;
    private boolean scrollbarHovered = false;
    private boolean scrollbarDragging = false;
    private double dragStartY = 0;
    private float dragStartOffset = 0;

    // ═══════════════════════════════════════════════════════════════
    // CONTENT RENDERER INTERFACE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Functional interface for rendering content.
     */
    @FunctionalInterface
    public interface ContentRenderer {
        /**
         * Render content and return total height.
         * @param graphics The graphics context
         * @param x Content X position
         * @param y Content Y position (already offset by scroll)
         * @param width Available content width
         * @param mouseX Mouse X position
         * @param mouseY Mouse Y position
         * @return Total content height
         */
        int render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY);
    }

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public ScrollableContentArea() {}

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the scrollable content area.
     * @param contentRenderer Callback that renders content and returns height
     */
    public void render(GuiGraphics graphics, int x, int y, int width, int height,
                       int mouseX, int mouseY, float partialTick, ContentRenderer contentRenderer) {
        this.bounds = new ResponsiveLayout.Rect(x, y, width, height);

        // Calculate content area (minus scrollbar)
        int contentWidth = width - SCROLLBAR_WIDTH - PADDING;
        int contentX = x + PADDING;
        int contentY = y + PADDING;
        int viewportHeight = height - PADDING * 2;

        this.contentBounds = new ResponsiveLayout.Rect(contentX, contentY, contentWidth, viewportHeight);

        // Background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.CONTENT);

        // Enable scissoring to clip content
        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + viewportHeight);

        // Render content with scroll offset
        int scrolledY = contentY - (int) scrollOffset;
        this.contentHeight = contentRenderer.render(graphics, contentX, scrolledY, contentWidth, mouseX, mouseY);

        // Disable scissoring
        graphics.disableScissor();

        // Update scroll bounds
        float maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        // Render scrollbar (if needed)
        if (contentHeight > viewportHeight) {
            renderScrollbar(graphics, x + width - SCROLLBAR_WIDTH, y, SCROLLBAR_WIDTH, height,
                           viewportHeight, mouseX, mouseY);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int width, int height,
                                  int viewportHeight, int mouseX, int mouseY) {
        // Track background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.DARKER);

        // Calculate thumb size and position
        float visibleRatio = (float) viewportHeight / contentHeight;
        int thumbHeight = Math.max(20, (int) (height * visibleRatio));
        float scrollRatio = scrollOffset / Math.max(1, contentHeight - viewportHeight);
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);

        // Update bounds
        scrollbarBounds = new ResponsiveLayout.Rect(x, thumbY, width, thumbHeight);
        scrollbarHovered = scrollbarBounds.contains(mouseX, mouseY);

        // Thumb
        int thumbColor = scrollbarDragging ? UIConstants.Slider.THUMB_DRAG :
                        (scrollbarHovered ? UIConstants.Slider.THUMB_HOVER : UIConstants.Slider.THUMB);
        graphics.fill(x + 1, thumbY, x + width - 1, thumbY + thumbHeight, thumbColor);

        // Thumb border
        if (scrollbarHovered || scrollbarDragging) {
            AxiomRenderer.drawBorder(graphics, x, thumbY, width, thumbHeight, UIConstants.Border.ACCENT);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Check scrollbar thumb
        if (scrollbarBounds.contains(mouseX, mouseY)) {
            scrollbarDragging = true;
            dragStartY = mouseY;
            dragStartOffset = scrollOffset;
            return true;
        }

        // Check scrollbar track (click to jump)
        if (bounds.contains(mouseX, mouseY) && mouseX >= bounds.x() + bounds.width() - SCROLLBAR_WIDTH) {
            float clickRatio = (float) (mouseY - bounds.y()) / bounds.height();
            float maxScroll = Math.max(0, contentHeight - contentBounds.height());
            scrollOffset = clickRatio * maxScroll;
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollbarDragging) {
            float deltaY = (float) (mouseY - dragStartY);
            float trackHeight = bounds.height() - scrollbarBounds.height();
            float maxScroll = Math.max(0, contentHeight - contentBounds.height());

            if (trackHeight > 0) {
                float scrollDelta = (deltaY / trackHeight) * maxScroll;
                scrollOffset = Mth.clamp(dragStartOffset + scrollDelta, 0, maxScroll);
            }
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bounds.contains(mouseX, mouseY)) {
            float maxScroll = Math.max(0, contentHeight - contentBounds.height());
            scrollOffset -= (float) scrollY * 20;  // 20 pixels per scroll tick
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        float maxScroll = Math.max(0, contentHeight - contentBounds.height());

        // Page Up/Down
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            scrollOffset -= contentBounds.height();
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollOffset += contentBounds.height();
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
            return true;
        }

        // Home/End
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) {
            scrollOffset = 0;
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_END) {
            scrollOffset = maxScroll;
            return true;
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // SCROLL CONTROL
    // ═══════════════════════════════════════════════════════════════

    /**
     * Scroll to the top.
     */
    public void scrollToTop() {
        scrollOffset = 0;
    }

    /**
     * Scroll to the bottom.
     */
    public void scrollToBottom() {
        float maxScroll = Math.max(0, contentHeight - contentBounds.height());
        scrollOffset = maxScroll;
    }

    /**
     * Scroll to make a specific Y position visible.
     */
    public void scrollToY(int targetY) {
        float maxScroll = Math.max(0, contentHeight - contentBounds.height());

        // Calculate if target is visible
        int visibleTop = (int) scrollOffset;
        int visibleBottom = visibleTop + contentBounds.height();

        if (targetY < visibleTop) {
            // Target is above viewport
            scrollOffset = targetY;
        } else if (targetY > visibleBottom) {
            // Target is below viewport
            scrollOffset = targetY - contentBounds.height() + PADDING;
        }

        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    /**
     * Reset scroll position.
     */
    public void reset() {
        scrollOffset = 0;
        contentHeight = 0;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public float getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(float offset) {
        float maxScroll = Math.max(0, contentHeight - contentBounds.height());
        this.scrollOffset = Mth.clamp(offset, 0, maxScroll);
    }

    public int getContentHeight() {
        return contentHeight;
    }

    public int getViewportHeight() {
        return contentBounds.height();
    }

    public boolean canScroll() {
        return contentHeight > contentBounds.height();
    }

    public float getScrollProgress() {
        float maxScroll = Math.max(1, contentHeight - contentBounds.height());
        return scrollOffset / maxScroll;
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }

    public ResponsiveLayout.Rect getContentBounds() {
        return contentBounds;
    }

    public int getContentWidth() {
        return contentBounds.width();
    }

    public int getContentX() {
        return contentBounds.x();
    }

    public int getContentY() {
        return contentBounds.y();
    }
}
