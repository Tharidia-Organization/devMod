package com.devmod.client.ui.testing;

import net.minecraft.client.gui.GuiGraphics;

public interface VoxelLabPage {

    /**
     * Returns the tab this page belongs to.
     *
     * @return The tab this page belongs to
     */
    VoxelLabTab getTab();

    /**
     * Initialize or reinitialize the page.
     * Called on screen init and resize.
     *
     * @param x Left position
     * @param y Top position
     * @param width Available width
     * @param height Available height
     */
    void init(int x, int y, int width, int height);

    /**
     * Called each tick for state updates.
     */
    default void tick() {}

    /**
     * Render the page content.
     *
     * @param graphics GuiGraphics for rendering
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param partialTick Partial tick for animations
     */
    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /**
     * Handle mouse click.
     *
     * @return true if the click was handled
     */
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handle mouse release.
     *
     * @return true if the release was handled
     */
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handle mouse drag.
     *
     * @return true if the drag was handled
     */
    default boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    /**
     * Handle mouse scroll.
     *
     * @return true if the scroll was handled
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    /**
     * Handle key press.
     *
     * @return true if the key was handled
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Handle character typed.
     *
     * @return true if the character was handled
     */
    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    /**
     * Called when this page becomes active.
     */
    default void onActivate() {}

    /**
     * Called when this page becomes inactive.
     */
    default void onDeactivate() {}
}
