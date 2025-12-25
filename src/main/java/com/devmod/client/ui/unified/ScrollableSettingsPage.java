package com.devmod.client.ui.unified;

import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.editor.core.ResponsiveLayout.Rect;
import com.devmod.client.ui.scroll.ScrollManager;
import com.devmod.client.ui.scroll.ScrollMetrics;
import com.devmod.client.ui.scroll.ScrollMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Abstract base class for settings pages that support scrolling.
 * Now uses the unified {@link ScrollManager} for consistent scroll behavior.
 *
 * <p>Key improvements over the old implementation:
 * <ul>
 *   <li>Bounds checking - only handles scroll when mouse is over content</li>
 *   <li>Delta-based drag - consistent scrollbar behavior (not centering)</li>
 *   <li>Safe scissoring - try/finally prevents scissor leaks</li>
 * </ul>
 *
 * Subclasses only need to implement:
 * - renderScrollableContent(): draw the actual content
 * - calculateTotalContentHeight(): return total height of content
 * - handleContentClick(): handle clicks on content (with adjusted Y)
 */
public abstract class ScrollableSettingsPage implements SettingsPage {

    protected static final int SCROLLBAR_WIDTH = 6;

    // Unified scroll manager (replaces manual scroll handling)
    protected final ScrollManager scrollManager = new ScrollManager(ScrollMode.INSTANT)
        .scrollbarWidth(SCROLLBAR_WIDTH)
        .scrollStep(20);

    // Cached dimensions (set during render)
    protected int contentX, contentY, contentWidth, contentHeight;
    protected int visibleHeight = 0;
    protected int totalContentHeight = 0;

    @Override
    public void render(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        // Cache dimensions
        contentX = x;
        contentY = y;
        contentWidth = width;
        contentHeight = height;
        visibleHeight = height;

        // Calculate content height
        totalContentHeight = calculateTotalContentHeight();

        // Update scroll manager
        Rect bounds = new Rect(x, y, width, height);
        scrollManager.update(bounds, totalContentHeight);

        // Render content with scissoring (try/finally for safety)
        scrollManager.withScissor(safeGraphics, bounds, () -> {
            // Render content with scroll offset applied
            int scrolledY = y - scrollManager.getScrollOffset();
            renderScrollableContent(safeGraphics, font, x, scrolledY, width, height, mouseX, mouseY);
        });

        // Render scrollbar if needed
        if (scrollManager.needsScrolling()) {
            renderScrollbar(safeGraphics, x + width - SCROLLBAR_WIDTH - 2, y, SCROLLBAR_WIDTH, height, mouseX, mouseY);
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
    protected void renderScrollbar(GuiGraphics graphics, int x, int y, int barWidth, int height, int mouseX, int mouseY) {
        // Track background
        graphics.fill(x, y, x + barWidth, y + height, UIConstants.Background.INPUT());

        // Get metrics from scroll manager
        ScrollMetrics metrics = scrollManager.calculateMetrics();
        int thumbY = y + metrics.thumbY();
        int thumbHeight = metrics.thumbHeight();

        // Check hover state
        boolean thumbHovered = mouseX >= x && mouseX < x + barWidth
            && mouseY >= thumbY && mouseY < thumbY + thumbHeight;
        boolean isDragging = scrollManager.isDragging();

        // Thumb color based on state
        int thumbColor = isDragging ? UIConstants.Border.ACCENT() :
                        (thumbHovered ? UIConstants.Border.HOVER() : UIConstants.Border.DEFAULT());
        graphics.fill(x, thumbY, x + barWidth, thumbY + thumbHeight, thumbColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        // Check if click is within content area
        if (mouseX < contentX || mouseX > contentX + this.contentWidth ||
            mouseY < contentY || mouseY > contentY + contentHeight) {
            return false;
        }

        // Check scrollbar click first
        if (scrollManager.needsScrolling()) {
            if (scrollManager.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        // Pass to subclass with adjusted Y
        int adjustedY = contentY - scrollManager.getScrollOffset();
        return handleContentClick(mouseX, mouseY, button, adjustedY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // FIX: Delegate to ScrollManager which handles bounds checking
        return scrollManager.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return scrollManager.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // FIX: Now uses delta-based drag instead of centering
        return scrollManager.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public int getContentHeight() {
        return calculateTotalContentHeight();
    }

    /**
     * Reset scroll position. Call when page is initialized.
     */
    protected void resetScroll() {
        scrollManager.reset();
    }

    /**
     * Get the effective content width (excluding scrollbar).
     */
    protected int getEffectiveContentWidth() {
        if (scrollManager.needsScrolling()) {
            return contentWidth - SCROLLBAR_WIDTH - 4;
        }
        return contentWidth;
    }

    /**
     * Get current scroll offset.
     */
    protected int getScrollOffset() {
        return scrollManager.getScrollOffset();
    }

    /**
     * Get max scroll offset.
     */
    protected int getMaxScrollOffset() {
        return scrollManager.getMaxScroll();
    }
}
