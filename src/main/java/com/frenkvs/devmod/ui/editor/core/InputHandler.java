package com.frenkvs.devmod.ui.editor.core;

/**
 * Interface for components that handle user input.
 * Extracted from EditorComponent for use in non-component classes.
 *
 * @see EditorComponent
 * @see EDITOR_DESIGN_SYSTEM.md Component System
 */
public interface InputHandler {

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
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if Shift modifier is active.
     */
    static boolean isShiftDown(int modifiers) {
        return (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
    }

    /**
     * Check if Control modifier is active.
     */
    static boolean isCtrlDown(int modifiers) {
        return (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;
    }

    /**
     * Check if Alt modifier is active.
     */
    static boolean isAltDown(int modifiers) {
        return (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_ALT) != 0;
    }

    /**
     * Check if Super (Windows/Command) modifier is active.
     */
    static boolean isSuperDown(int modifiers) {
        return (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER) != 0;
    }
}
