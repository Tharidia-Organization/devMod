package com.frenkvs.devmod.ui.scroll;

import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout.Rect;
import com.frenkvs.devmod.ui.scroll.impl.InstantScrollBehavior;
import com.frenkvs.devmod.ui.scroll.impl.SmoothScrollBehavior;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Unified scroll manager for all scrollable components.
 * Centralizes mouse wheel, scrollbar drag, and keyboard navigation.
 *
 * <p>Key features:
 * <ul>
 *   <li>Bounds checking - only handles events when mouse is over the scroll area</li>
 *   <li>Delta-based drag - consistent scrollbar behavior (not centering)</li>
 *   <li>Keyboard navigation - PAGE UP/DOWN, HOME/END, arrow keys</li>
 *   <li>Safe scissoring - try/finally pattern prevents scissor leaks</li>
 *   <li>Consistent metrics - uniform scrollbar dimension calculations</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * private final ScrollManager scroll = new ScrollManager(ScrollMode.SMOOTH);
 *
 * void render(GuiGraphics g, int mouseX, int mouseY) {
 *     scroll.update(bounds, contentHeight);
 *     scroll.withScissor(g, () -> {
 *         renderContent(g, -scroll.getScrollOffset());
 *     });
 *     renderScrollbar(g, scroll.calculateMetrics());
 * }
 * }</pre>
 */
public class ScrollManager {

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    private static final int DEFAULT_SCROLL_STEP = 20;
    private static final int MIN_THUMB_HEIGHT = 20;
    private static final int DEFAULT_SCROLLBAR_WIDTH = 6;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private Rect bounds = Rect.EMPTY;
    private int contentHeight = 0;
    private int viewportHeight = 0;
    private int scrollbarWidth = DEFAULT_SCROLLBAR_WIDTH;
    private int scrollStep = DEFAULT_SCROLL_STEP;

    private final ScrollBehavior behavior;
    private boolean isDragging = false;
    private double dragStartY = 0;
    private float dragStartOffset = 0;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create a ScrollManager with the specified mode.
     *
     * @param mode INSTANT for immediate scroll, SMOOTH for animated scroll
     */
    public ScrollManager(ScrollMode mode) {
        this.behavior = mode == ScrollMode.SMOOTH
            ? new SmoothScrollBehavior()
            : new InstantScrollBehavior();
    }

    /**
     * Create a ScrollManager with a custom scroll behavior.
     *
     * @param customBehavior Custom implementation of ScrollBehavior
     */
    public ScrollManager(ScrollBehavior customBehavior) {
        this.behavior = customBehavior;
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set scrollbar width in pixels.
     */
    public ScrollManager scrollbarWidth(int width) {
        this.scrollbarWidth = width;
        return this;
    }

    /**
     * Set scroll step (pixels per mouse wheel tick).
     */
    public ScrollManager scrollStep(int step) {
        this.scrollStep = step;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update bounds and content dimensions.
     * Call this every frame before rendering.
     *
     * @param bounds Viewport bounds (scrollable area)
     * @param contentHeight Total height of scrollable content
     */
    public void update(Rect bounds, int contentHeight) {
        this.bounds = bounds;
        this.contentHeight = contentHeight;
        this.viewportHeight = bounds.height();

        // Update behavior with current max scroll
        behavior.setMaxScroll(getMaxScroll());
        behavior.update();
    }

    /**
     * Update with x, y, width, height parameters.
     */
    public void update(int x, int y, int width, int height, int contentHeight) {
        update(new Rect(x, y, width, height), contentHeight);
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get maximum scroll offset.
     */
    public int getMaxScroll() {
        return Math.max(0, contentHeight - viewportHeight);
    }

    /**
     * Get current scroll offset (always clamped to valid range).
     */
    public int getScrollOffset() {
        return (int) behavior.getOffset();
    }

    /**
     * Check if scrolling is needed (content exceeds viewport).
     */
    public boolean needsScrolling() {
        return contentHeight > viewportHeight;
    }

    /**
     * Check if mouse is over the scrollable area.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        return bounds.contains(mouseX, mouseY);
    }

    /**
     * Check if scroll animation is in progress.
     */
    public boolean isAnimating() {
        return behavior.isAnimating();
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING - MOUSE WHEEL
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle mouse scroll event.
     * CRITICAL: Only handles scroll if mouse is over the scrollable area.
     *
     * @return true if event was consumed
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        // CRITICAL: Bounds check before handling scroll
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        if (!needsScrolling()) {
            return false;
        }

        behavior.scroll(scrollY * scrollStep);
        return true;
    }

    /**
     * Handle mouse scroll with explicit bounds check override.
     * Use this when you have nested scrollable areas.
     *
     * @param skipBoundsCheck If true, skip the isMouseOver check
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, boolean skipBoundsCheck) {
        if (!skipBoundsCheck && !isMouseOver(mouseX, mouseY)) {
            return false;
        }

        if (!needsScrolling()) {
            return false;
        }

        behavior.scroll(scrollY * scrollStep);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING - SCROLLBAR DRAG
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle mouse click for scrollbar interaction.
     *
     * @return true if event was consumed (clicked on scrollbar)
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !needsScrolling()) {
            return false;
        }

        ScrollMetrics metrics = calculateMetrics();

        // Check if click is on scrollbar thumb
        if (isOverThumb(mouseX, mouseY, metrics)) {
            isDragging = true;
            dragStartY = mouseY;
            dragStartOffset = behavior.getOffset();
            return true;
        }

        // Check if click is on scrollbar track (jump to position)
        if (isOverTrack(mouseX, mouseY)) {
            float clickRatio = (float) (mouseY - bounds.y()) / bounds.height();
            float targetOffset = clickRatio * getMaxScroll();
            behavior.scrollTo(targetOffset);
            return true;
        }

        return false;
    }

    /**
     * Handle mouse drag for scrollbar.
     * Uses DELTA-BASED algorithm (not centering) for consistent behavior.
     *
     * @return true if event was consumed
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!isDragging || button != 0) {
            return false;
        }

        ScrollMetrics metrics = calculateMetrics();
        int trackHeight = metrics.trackHeight();

        if (trackHeight <= 0) {
            return false; // Can't drag when track is too small
        }

        // DELTA-BASED: Calculate offset from drag start position
        float deltaMouseY = (float) (mouseY - dragStartY);
        float scrollDelta = (deltaMouseY / trackHeight) * getMaxScroll();
        float newOffset = dragStartOffset + scrollDelta;

        behavior.scrollTo(newOffset);
        return true;
    }

    /**
     * Handle mouse release.
     *
     * @return true if was dragging scrollbar
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging && button == 0) {
            isDragging = false;
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING - KEYBOARD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle keyboard navigation.
     * Supports PAGE UP/DOWN, HOME, END, and arrow keys.
     *
     * @return true if event was consumed
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!needsScrolling()) {
            return false;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_UP -> {
                behavior.scroll(viewportHeight);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                behavior.scroll(-viewportHeight);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                behavior.scrollTo(0);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                behavior.scrollTo(getMaxScroll());
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                behavior.scroll(scrollStep);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                behavior.scroll(-scrollStep);
                return true;
            }
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // SCISSORING UTILITIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Execute a render operation within scissor bounds.
     * Ensures scissor is always disabled, even on exception.
     *
     * @param graphics GuiGraphics instance
     * @param renderAction Rendering code to execute within scissor
     */
    public void withScissor(GuiGraphics graphics, Runnable renderAction) {
        graphics.enableScissor(
            bounds.x(),
            bounds.y(),
            bounds.right(),
            bounds.bottom()
        );
        try {
            renderAction.run();
        } finally {
            graphics.disableScissor();
        }
    }

    /**
     * Execute render with custom scissor bounds.
     */
    public void withScissor(GuiGraphics graphics, Rect scissorBounds, Runnable renderAction) {
        graphics.enableScissor(
            scissorBounds.x(),
            scissorBounds.y(),
            scissorBounds.right(),
            scissorBounds.bottom()
        );
        try {
            renderAction.run();
        } finally {
            graphics.disableScissor();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCROLLBAR METRICS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calculate scrollbar metrics for rendering.
     *
     * @return ScrollMetrics with thumb position and dimensions
     */
    public ScrollMetrics calculateMetrics() {
        if (!needsScrolling() || bounds.height() <= 0) {
            return new ScrollMetrics(0, bounds.height(), 0, 0);
        }

        float visibleRatio = (float) viewportHeight / contentHeight;
        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) (bounds.height() * visibleRatio));
        int trackHeight = bounds.height() - thumbHeight;

        float scrollPercent = getMaxScroll() > 0
            ? (float) getScrollOffset() / getMaxScroll()
            : 0;
        int thumbY = (int) (trackHeight * scrollPercent);

        return new ScrollMetrics(thumbY, thumbHeight, trackHeight, scrollPercent);
    }

    /**
     * Get the scrollbar track X position (right edge of bounds).
     */
    public int getScrollbarX() {
        return bounds.right() - scrollbarWidth;
    }

    // ═══════════════════════════════════════════════════════════════
    // SCROLL CONTROL
    // ═══════════════════════════════════════════════════════════════

    /**
     * Scroll to top.
     */
    public void scrollToTop() {
        behavior.scrollTo(0);
    }

    /**
     * Scroll to bottom.
     */
    public void scrollToBottom() {
        behavior.scrollTo(getMaxScroll());
    }

    /**
     * Scroll to make a Y position visible within the viewport.
     *
     * @param targetY Y position in content coordinates
     */
    public void scrollToY(int targetY) {
        int currentOffset = getScrollOffset();
        int visibleTop = currentOffset;
        int visibleBottom = currentOffset + viewportHeight;

        if (targetY < visibleTop) {
            behavior.scrollTo(targetY);
        } else if (targetY > visibleBottom) {
            behavior.scrollTo(targetY - viewportHeight + scrollStep);
        }
    }

    /**
     * Reset scroll to initial state.
     */
    public void reset() {
        behavior.reset();
        isDragging = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private boolean isOverThumb(double mouseX, double mouseY, ScrollMetrics metrics) {
        int thumbX = getScrollbarX();
        int thumbY = bounds.y() + metrics.thumbY();
        return mouseX >= thumbX && mouseX < thumbX + scrollbarWidth
            && mouseY >= thumbY && mouseY < thumbY + metrics.thumbHeight();
    }

    private boolean isOverTrack(double mouseX, double mouseY) {
        int trackX = getScrollbarX();
        return mouseX >= trackX && mouseX < trackX + scrollbarWidth
            && mouseY >= bounds.y() && mouseY < bounds.bottom();
    }

    // ═══════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public Rect getBounds() { return bounds; }
    public int getContentHeight() { return contentHeight; }
    public int getViewportHeight() { return viewportHeight; }
    public boolean isDragging() { return isDragging; }
    public int getScrollbarWidth() { return scrollbarWidth; }
    public ScrollBehavior getBehavior() { return behavior; }
}
