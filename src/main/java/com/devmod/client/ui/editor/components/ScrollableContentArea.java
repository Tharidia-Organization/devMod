package com.devmod.client.ui.editor.components;

import java.util.Objects;

import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.DirtyRegionTracker;
import com.devmod.client.ui.editor.core.EditorDimensions;
import com.devmod.client.ui.editor.core.EditorSpacing;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.scroll.ScrollManager;
import com.devmod.client.ui.scroll.ScrollMetrics;
import com.devmod.client.ui.scroll.ScrollMode;

/**
 * Scrollable content area for module content.
 * Handles scrolling, scissoring, and scrollbar rendering.
 *
 * <p>Now uses the unified {@link ScrollManager} for consistent scroll behavior.
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
    private static final int THUMB_INSET = 1;
    private static final float SCROLL_CHANGE_THRESHOLD = 0.5f;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect contentBounds = ResponsiveLayout.Rect.EMPTY;

    // Unified scroll manager with smooth animation
    private final ScrollManager scrollManager = new ScrollManager(ScrollMode.SMOOTH)
        .scrollbarWidth(SCROLLBAR_WIDTH);

    private int contentHeight = 0;
    private float lastScrollOffset = 0;
    private final DirtyRegionTracker dirtyTracker = DirtyRegionTracker.INSTANCE;

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

        // Update scroll manager with current dimensions
        scrollManager.update(contentBounds, contentHeight);

        // Track scroll changes for dirty region optimization
        float currentOffset = scrollManager.getScrollOffset();
        boolean scrollChanged = Math.abs(currentOffset - lastScrollOffset) > SCROLL_CHANGE_THRESHOLD;
        if (scrollChanged) {
            dirtyTracker.markDirty(contentX, contentY, contentWidth, viewportHeight);
        }
        lastScrollOffset = currentOffset;

        // Background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.CONTENT());

        // Render content with scissoring (try/finally for safety)
        ResponsiveLayout.Rect scissorBounds = Objects.requireNonNull(contentBounds, "contentBounds");
        scrollManager.withScissor(graphics, scissorBounds, () -> {
            int scrolledY = contentY - scrollManager.getScrollOffset();
            this.contentHeight = contentRenderer.render(graphics, contentX, scrolledY, contentWidth, mouseX, mouseY);
        });

        // Render scrollbar (if needed)
        if (scrollManager.needsScrolling()) {
            renderScrollbar(graphics, x + width - SCROLLBAR_WIDTH, y, SCROLLBAR_WIDTH, height,
                           mouseX, mouseY);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int width, int height,
                                  int mouseX, int mouseY) {
        // Track background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.DARKER());

        // Get metrics from scroll manager
        ScrollMetrics metrics = scrollManager.calculateMetrics();
        int thumbY = y + metrics.thumbY();
        int thumbHeight = metrics.thumbHeight();

        // Check hover state
        boolean thumbHovered = mouseX >= x && mouseX < x + width
            && mouseY >= thumbY && mouseY < thumbY + thumbHeight;
        boolean isDragging = scrollManager.isDragging();

        // Thumb color based on state
        int thumbColor = isDragging ? UIConstants.Slider.THUMB_DRAG :
                        (thumbHovered ? UIConstants.Slider.THUMB_HOVER : UIConstants.Slider.THUMB);
        graphics.fill(x + THUMB_INSET, thumbY, x + width - THUMB_INSET, thumbY + thumbHeight, thumbColor);

        // Thumb border when hovered or dragging
        if (thumbHovered || isDragging) {
            AxiomRenderer.drawBorder(graphics, x, thumbY, width, thumbHeight, UIConstants.Border.ACCENT());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING - Delegates to ScrollManager
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return scrollManager.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return scrollManager.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scrollManager.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollManager.mouseScrolled(mouseX, mouseY, scrollY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return scrollManager.keyPressed(keyCode, scanCode, modifiers);
    }

    // ═══════════════════════════════════════════════════════════════
    // SCROLL CONTROL - Delegates to ScrollManager
    // ═══════════════════════════════════════════════════════════════

    /**
     * Scroll to the top.
     */
    public void scrollToTop() {
        scrollManager.scrollToTop();
    }

    /**
     * Scroll to the bottom.
     */
    public void scrollToBottom() {
        scrollManager.scrollToBottom();
    }

    /**
     * Scroll to make a specific Y position visible.
     */
    public void scrollToY(int targetY) {
        scrollManager.scrollToY(targetY);
    }

    /**
     * Reset scroll position.
     */
    public void reset() {
        scrollManager.reset();
        contentHeight = 0;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public float getScrollOffset() {
        return scrollManager.getScrollOffset();
    }

    public void setScrollOffset(float offset) {
        scrollManager.getBehavior().scrollTo(offset);
    }

    public int getContentHeight() {
        return contentHeight;
    }

    public int getViewportHeight() {
        return contentBounds.height();
    }

    public boolean canScroll() {
        return scrollManager.needsScrolling();
    }

    public float getScrollProgress() {
        int maxScroll = scrollManager.getMaxScroll();
        return maxScroll > 0 ? (float) scrollManager.getScrollOffset() / maxScroll : 0;
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
