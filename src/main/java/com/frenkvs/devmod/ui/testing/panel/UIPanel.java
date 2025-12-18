package com.frenkvs.devmod.ui.testing.panel;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Sealed interface for composable UI panels in VoxelLab.
 *
 * <p>This is the base interface; concrete panel types are in separate files
 * for better organization and maintainability.</p>
 *
 * @see PanelConstants
 * @see PanelContainer
 */
public sealed interface UIPanel permits
    HeaderPanel,
    SectionPanel,
    CollapsiblePanel,
    StatusPanel,
    SliderPanel,
    GridPanel,
    SpacerPanel,
    CompositePanel,
    ShowcasePanel {

    /** Panel identifier for debugging/testing */
    String id();

    /** Display title */
    String title();

    /** Calculate required height given available width */
    int getHeight(int availableWidth);

    /** Render the panel */
    void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY);

    /** Handle mouse click */
    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }

    /** Handle mouse release */
    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }

    /** Handle mouse drag */
    default boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) { return false; }

    /** Called each frame for animations/state updates */
    default void tick() {}

    /** Called on init/resize */
    default void init() {}

    /** Whether this panel is currently visible */
    default boolean isVisible() { return true; }
}
