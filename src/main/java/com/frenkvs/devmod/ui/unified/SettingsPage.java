package com.frenkvs.devmod.ui.unified;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;

/**
 * Base interface for all settings pages.
 * Each page manages a specific category of settings.
 */
public interface SettingsPage {

    /**
     * Gets the category of this page.
     */
    SettingsCategory getCategory();

    /**
     * Gets the page title.
     */
    String getTitle();

    /**
     * Initializes the page. Called when the page is selected.
     */
    default void init() {}

    /**
     * Renders the page content.
     *
     * @param graphics Rendering context
     * @param font Font for text
     * @param x Content area X coordinate
     * @param y Content area Y coordinate
     * @param width Content area width
     * @param height Content area height
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     */
    void render(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, int mouseX, int mouseY);

    /**
     * Handles a mouse click.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Button (0=left, 1=right)
     * @param contentX Content area X coordinate
     * @param contentY Content area Y coordinate
     * @param contentWidth Content area width
     * @return true if the click was handled
     */
    boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth);

    /**
     * Handles mouse release.
     */
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handles mouse drag.
     */
    default boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    /**
     * Handles mouse scroll.
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    /**
     * Handles keyboard input.
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Called every tick for updates.
     */
    default void tick() {}

    /**
     * Called when the page is closed/changed.
     */
    default void onClose() {}

    /**
     * Checks if there are unsaved changes.
     */
    default boolean hasUnsavedChanges() {
        return false;
    }

    /**
     * Saves the changes.
     */
    default void saveChanges() {}

    /**
     * Resets to default values.
     */
    default void resetToDefaults() {}

    /**
     * Gets the total content height (for scrolling).
     */
    default int getContentHeight() {
        return 200;
    }
}
