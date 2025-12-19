package com.frenkvs.devmod.ui.editor.core;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Base interface for all editor UI components.
 * Provides common API for identification, rendering, state, and input handling.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Component System
 */
public interface EditorComponent {

    // ═══════════════════════════════════════════════════════════════
    // IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the unique identifier for this component.
     */
    String getId();

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the component at the specified position.
     *
     * @param graphics Graphics context
     * @param x        Left edge X position
     * @param y        Top edge Y position
     * @param width    Component width
     * @param height   Component height
     * @param mouseX   Current mouse X position
     * @param mouseY   Current mouse Y position
     */
    void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY);

    /**
     * Get the preferred (unscaled) width of this component.
     * Return 0 if the component can adapt to any width.
     */
    default int getPreferredWidth() {
        return 0;
    }

    /**
     * Get the preferred (unscaled) height of this component.
     * Return 0 if the component can adapt to any height.
     */
    default int getPreferredHeight() {
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if the component is enabled (can receive input).
     */
    boolean isEnabled();

    /**
     * Set the component's enabled state.
     */
    void setEnabled(boolean enabled);

    /**
     * Check if the component is currently hovered by the mouse.
     */
    boolean isHovered();

    /**
     * Check if the component is currently focused.
     */
    default boolean isFocused() {
        return false;
    }

    /**
     * Set the component's focus state.
     */
    default void setFocused(boolean focused) {
        // Default: no-op (not all components support focus)
    }

    /**
     * Check if the component is visible.
     */
    default boolean isVisible() {
        return true;
    }

    /**
     * Set the component's visibility.
     */
    default void setVisible(boolean visible) {
        // Default: no-op
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle mouse click event.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button (0 = left, 1 = right, 2 = middle)
     * @return true if the event was consumed
     */
    boolean mouseClicked(double mouseX, double mouseY, int button);

    /**
     * Handle mouse release event.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button
     * @return true if the event was consumed
     */
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handle mouse drag event.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button being held
     * @param dragX  Delta X since last event
     * @param dragY  Delta Y since last event
     * @return true if the event was consumed
     */
    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    /**
     * Handle mouse scroll event.
     *
     * @param mouseX      Mouse X position
     * @param mouseY      Mouse Y position
     * @param scrollDelta Scroll amount (positive = up)
     * @return true if the event was consumed
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        return false;
    }

    /**
     * Handle key press event.
     *
     * @param keyCode   GLFW key code
     * @param scanCode  Platform-specific scan code
     * @param modifiers Modifier flags (shift, ctrl, alt)
     * @return true if the event was consumed
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Handle key release event.
     *
     * @param keyCode   GLFW key code
     * @param scanCode  Platform-specific scan code
     * @param modifiers Modifier flags
     * @return true if the event was consumed
     */
    default boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Handle character typed event.
     *
     * @param chr       Character typed
     * @param modifiers Modifier flags
     * @return true if the event was consumed
     */
    default boolean charTyped(char chr, int modifiers) {
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // BOUNDS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the current bounds of the component.
     * May return null or empty bounds if component hasn't been rendered yet.
     */
    default ResponsiveLayout.Rect getBounds() {
        return ResponsiveLayout.Rect.EMPTY;
    }

    /**
     * Check if a point is within the component's bounds.
     */
    default boolean containsPoint(double x, double y) {
        ResponsiveLayout.Rect bounds = getBounds();
        if (bounds == null || bounds == ResponsiveLayout.Rect.EMPTY) return false;
        return x >= bounds.x() && x < bounds.x() + bounds.width() &&
               y >= bounds.y() && y < bounds.y() + bounds.height();
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called each tick for animation/state updates.
     */
    default void tick() {
        // Default: no-op
    }

    /**
     * Called when the component is removed or the screen is closed.
     */
    default void onClose() {
        // Default: no-op
    }
}
