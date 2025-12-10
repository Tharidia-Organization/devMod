package com.frenkvs.devmod.ui.hub;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Base interface for TestingHub panels.
 * Each panel manages its own rendering and input.
 */
public interface HubPanel {

    /**
     * Renders the panel.
     */
    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /**
     * Handles mouse click.
     * @return true if the event was handled
     */
    boolean mouseClicked(double mouseX, double mouseY, int button);

    /**
     * Handles mouse release.
     * @return true if the event was handled
     */
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handles mouse scroll.
     * @return true if the event was handled
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    /**
     * Handles keyboard input.
     * @return true if the event was handled
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Handles character input.
     * @return true if the event was handled
     */
    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    /**
     * Checks if the mouse is over the panel.
     */
    boolean isMouseOver(int mouseX, int mouseY);

    /**
     * Updates the panel state.
     */
    default void tick() {}

    /**
     * Forces data refresh.
     */
    default void refresh() {}

    /**
     * Returns the panel bounds.
     */
    default int getX() { return 0; }
    default int getY() { return 0; }
    default int getWidth() { return 0; }
    default int getHeight() { return 0; }
}
