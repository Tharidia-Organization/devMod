package com.devmod.client.ui.editor.core;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.animation.UiAnimation;
import com.devmod.client.ui.editor.overlay.EditorOverlay;

public abstract class BaseOverlay implements EditorOverlay {

    protected boolean visible = false;

    /** Animation for show/hide transitions */
    protected final UiAnimation animation = UiAnimation.fadeSlideUp(150);

    /**
     * Show the overlay with animation.
     */
    @Override
    public void show() {
        visible = true;
        animation.start();
    }

    /**
     * Hide the overlay with animation.
     */
    @Override
    public void hide() {
        animation.reverse(() -> visible = false);
    }

    /**
     * Hide immediately without animation.
     */
    public void hideImmediate() {
        visible = false;
        animation.reset();
    }

    /**
     * Toggle the overlay visibility.
     */
    @Override
    public void toggle() {
        if (visible) {
            hide();
        } else {
            show();
        }
    }

    /**
     * Check if the overlay is currently visible (including during animations).
     */
    @Override
    public boolean isVisible() {
        return visible || animation.isAnimating();
    }

    /**
     * Check if the overlay is fully visible (animation complete).
     */
    public boolean isFullyVisible() {
        return visible && animation.isComplete();
    }

    /**
     * Render the overlay using the template method pattern.
     * Subclasses should override renderContent() to provide their specific content.
     *
     * @param graphics    The graphics context
     * @param font        The font to use for text rendering
     * @param screenWidth The screen width
     * @param screenHeight The screen height
     * @param mouseX      Current mouse X position
     * @param mouseY      Current mouse Y position
     */
    @Override
    public final void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                             int mouseX, int mouseY) {
        if (!visible && !animation.isAnimating()) return;

        // Update animation state
        animation.update();

        // Render dark backdrop with animated alpha
        renderBackdrop(graphics, screenWidth, screenHeight);

        // Calculate centered panel position (support dynamic height)
        int panelW = ScaledCoord.scaleDim(getPanelWidth());
        int panelH = usesDynamicHeight()
            ? ScaledCoord.scaleDim(getPanelHeight(screenHeight))
            : ScaledCoord.scaleDim(getPanelHeight());
        int x = (screenWidth - panelW) / 2;
        int y = (screenHeight - panelH) / 2;

        // Apply animation offset
        int offsetY = animation.getSlideOffset(20);
        y += offsetY;

        // Render panel background and border
        renderPanel(graphics, x, y, panelW, panelH);

        // Let subclass render its content
        renderContent(graphics, font, x, y, panelW, panelH, mouseX, mouseY);
    }

    /**
     * Render the dark backdrop that covers the screen behind the overlay.
     * Uses animation alpha for smooth fade effect.
     * Subclasses can override to customize.
     */
    protected void renderBackdrop(GuiGraphics graphics, int screenWidth, int screenHeight) {
        int backdropColor = animation.applyToBackdrop(DesignTokens.Background.OVERLAY());
        graphics.fill(0, 0, screenWidth, screenHeight, backdropColor);
    }

    /**
     * Render the panel background and border.
     * Subclasses can override to customize.
     */
    protected void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, DesignTokens.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, x, y, width, height, DesignTokens.Border.DEFAULT());
    }

    /**
     * Render the overlay's content. Must be implemented by subclasses.
     *
     * @param graphics The graphics context
     * @param font     The font to use
     * @param x        Panel left edge X coordinate
     * @param y        Panel top edge Y coordinate
     * @param width    Panel width (scaled)
     * @param height   Panel height (scaled)
     * @param mouseX   Current mouse X position
     * @param mouseY   Current mouse Y position
     */
    protected abstract void renderContent(GuiGraphics graphics, Font font,
                                          int x, int y, int width, int height,
                                          int mouseX, int mouseY);

    /**
     * Get the unscaled panel width. Subclasses must implement.
     */
    protected abstract int getPanelWidth();

    /**
     * Get the unscaled panel height. Subclasses must implement.
     * For static height overlays, implement this method.
     * For dynamic height overlays, override {@link #getPanelHeight(int)} instead.
     */
    protected abstract int getPanelHeight();

    /**
     * Get the unscaled panel height based on screen height.
     * Override this method for overlays that need dynamic height calculation.
     * Default implementation delegates to {@link #getPanelHeight()}.
     *
     * @param screenHeight The screen height (unscaled)
     * @return The panel height (unscaled)
     */
    protected int getPanelHeight(int screenHeight) {
        return getPanelHeight();
    }

    /**
     * Check if this overlay uses dynamic height calculation.
     * Override to return true if {@link #getPanelHeight(int)} should be used.
     */
    protected boolean usesDynamicHeight() {
        return false;
    }

    /**
     * Handle key press events. Default implementation handles ESC to close.
     * Subclasses can override to add additional key handling.
     *
     * @param keyCode The key code
     * @return true if the event was consumed
     */
    @Override
    public boolean keyPressed(int keyCode) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onEscapePressed();
            hide();
            return true;
        }

        return handleKeyPressed(keyCode);
    }

    /**
     * Called when ESC is pressed. Subclasses can override to perform
     * cleanup or trigger cancel callbacks.
     */
    protected void onEscapePressed() {
        invokeCloseCallback();
    }

    /**
     * Handle additional key presses. Subclasses can override.
     *
     * @param keyCode The key code
     * @return true if consumed
     */
    protected boolean handleKeyPressed(int keyCode) {
        return visible; // Consume all keys when visible by default
    }

    /**
     * Handle mouse click events. Default implementation checks if click
     * is outside panel to close.
     *
     * @param mouseX      Mouse X position
     * @param mouseY      Mouse Y position
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @return true if the event was consumed
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!visible) return false;

        int panelW = ScaledCoord.scaleDim(getPanelWidth());
        int panelH = usesDynamicHeight()
            ? ScaledCoord.scaleDim(getPanelHeight(screenHeight))
            : ScaledCoord.scaleDim(getPanelHeight());
        int x = (screenWidth - panelW) / 2;
        int y = (screenHeight - panelH) / 2;

        // Click outside panel closes it
        if (mouseX < x || mouseX > x + panelW || mouseY < y || mouseY > y + panelH) {
            if (shouldCloseOnClickOutside()) {
                hide();
            }
            return true;
        }

        return handleMouseClicked(mouseX, mouseY, x, y, panelW, panelH);
    }

    /**
     * Whether clicking outside the panel should close it. Default is true.
     * Subclasses can override to change behavior.
     */
    protected boolean shouldCloseOnClickOutside() {
        return true;
    }

    /**
     * Handle mouse clicks inside the panel. Subclasses should override.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param panelX Panel left edge X
     * @param panelY Panel top edge Y
     * @param panelW Panel width
     * @param panelH Panel height
     * @return true if consumed
     */
    protected boolean handleMouseClicked(double mouseX, double mouseY,
                                         int panelX, int panelY, int panelW, int panelH) {
        return true; // Consume by default to prevent clicks passing through
    }

    /**
     * Handle mouse scroll events. Default implementation passes to subclass handler.
     *
     * @param mouseX      Mouse X position
     * @param mouseY      Mouse Y position
     * @param scrollDelta Scroll amount (positive = up, negative = down)
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @return true if the event was consumed
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                  int screenWidth, int screenHeight) {
        if (!visible) return false;

        int panelW = ScaledCoord.scaleDim(getPanelWidth());
        int panelH = usesDynamicHeight()
            ? ScaledCoord.scaleDim(getPanelHeight(screenHeight))
            : ScaledCoord.scaleDim(getPanelHeight());
        int x = (screenWidth - panelW) / 2;
        int y = (screenHeight - panelH) / 2;

        // Only handle scroll inside panel
        if (mouseX >= x && mouseX <= x + panelW && mouseY >= y && mouseY <= y + panelH) {
            return handleMouseScrolled(mouseX, mouseY, scrollDelta, x, y, panelW, panelH);
        }

        return visible; // Consume scroll when visible to prevent background scrolling
    }

    /**
     * Handle mouse scroll inside the panel. Subclasses can override for scrollable content.
     *
     * @param mouseX      Mouse X position
     * @param mouseY      Mouse Y position
     * @param scrollDelta Scroll amount
     * @param panelX      Panel left edge X
     * @param panelY      Panel top edge Y
     * @param panelW      Panel width
     * @param panelH      Panel height
     * @return true if consumed
     */
    protected boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                           int panelX, int panelY, int panelW, int panelH) {
        return false; // Default: no scrollable content
    }

    /**
     * Handle character typed events. Useful for overlays with text input.
     *
     * @param chr       The character typed
     * @param modifiers Keyboard modifiers
     * @return true if the event was consumed
     */
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!visible) return false;
        return handleCharTyped(chr, modifiers);
    }

    /**
     * Handle character input. Subclasses can override for text input fields.
     *
     * @param chr       The character typed
     * @param modifiers Keyboard modifiers
     * @return true if consumed
     */
    protected boolean handleCharTyped(char chr, int modifiers) {
        return false; // Default: no text input handling
    }

    // ═══════════════════════════════════════════════════════════════
    // EditorOverlay INTERFACE METHODS
    // ═══════════════════════════════════════════════════════════════

    /** Close callback - invoked when overlay is hidden */
    protected Runnable closeCallback = () -> {};

    @Override
    public void setCloseCallback(Runnable callback) {
        this.closeCallback = callback != null ? callback : () -> {};
    }

    @Override
    public OverlayType getType() {
        return OverlayType.NONE; // Subclasses should override
    }

    /**
     * Invoke the close callback. Called automatically when ESC is pressed.
     */
    protected void invokeCloseCallback() {
        closeCallback.run();
    }
}
