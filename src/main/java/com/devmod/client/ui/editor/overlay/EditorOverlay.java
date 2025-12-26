package com.devmod.client.ui.editor.overlay;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface EditorOverlay {

    // ═══════════════════════════════════════════════════════════════
    // VISIBILITY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Show the overlay.
     */
    void show();

    /**
     * Hide the overlay.
     */
    void hide();

    /**
     * Toggle the overlay visibility.
     */
    void toggle();

    /**
     * Check if the overlay is currently visible.
     */
    boolean isVisible();

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the overlay.
     *
     * @param graphics     The graphics context
     * @param font         The font to use for text rendering
     * @param screenWidth  The screen width
     * @param screenHeight The screen height
     * @param mouseX       Current mouse X position
     * @param mouseY       Current mouse Y position
     */
    void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                int mouseX, int mouseY);

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle mouse click.
     *
     * @param mouseX       Current mouse X position
     * @param mouseY       Current mouse Y position
     * @param screenWidth  The screen width (for panel bounds calculation)
     * @param screenHeight The screen height (for panel bounds calculation)
     * @return true if the click was handled
     */
    boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight);

    /**
     * Handle key press.
     *
     * @param keyCode The GLFW key code
     * @return true if the key was handled
     */
    boolean keyPressed(int keyCode);

    /**
     * Handle character typed.
     *
     * @param codePoint The character typed
     * @param modifiers The modifier keys
     * @return true if the character was handled
     */
    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    /**
     * Handle mouse scroll.
     *
     * @param mouseX       Current mouse X position
     * @param mouseY       Current mouse Y position
     * @param scrollDelta  Scroll amount (positive = up)
     * @param screenWidth  The screen width
     * @param screenHeight The screen height
     * @return true if the scroll was handled
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                   int screenWidth, int screenHeight) {
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set the callback to be invoked when the overlay is closed.
     * Default implementation does nothing - subclasses should override.
     *
     * @param callback The close callback
     */
    default void setCloseCallback(Runnable callback) {
        // Default: no-op, subclasses override
    }

    /**
     * Get the overlay type identifier.
     * Used for overlay management and state tracking.
     *
     * @return The overlay type
     */
    OverlayType getType();

    // ═══════════════════════════════════════════════════════════════
    // OVERLAY TYPES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Enumeration of overlay types for type-safe management.
     */
    enum OverlayType {
        NONE,
        HELP,
        PRESETS,
        TEMPLATES,
        CRAFTING,
        HISTORY,
        LOW_CONFIDENCE
    }
}
