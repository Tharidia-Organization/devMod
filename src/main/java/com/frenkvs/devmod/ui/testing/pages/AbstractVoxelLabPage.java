package com.frenkvs.devmod.ui.testing.pages;

import com.frenkvs.devmod.ui.testing.VoxelLabPage;
import com.frenkvs.devmod.ui.testing.VoxelLabTab;
import com.frenkvs.devmod.ui.testing.panel.PanelContainer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Abstract base class for VoxelLab pages that use PanelContainer for layout.
 * Provides common functionality for panel-based pages.
 */
public abstract class AbstractVoxelLabPage implements VoxelLabPage {

    protected final VoxelLabTab tab;
    protected final PanelContainer panelContainer;

    protected int x, y, width, height;
    protected boolean initialized = false;

    protected AbstractVoxelLabPage(VoxelLabTab tab) {
        this.tab = tab;
        this.panelContainer = new PanelContainer();
    }

    @Override
    public VoxelLabTab getTab() {
        return tab;
    }

    @Override
    public void init(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        panelContainer.clearPanels();
        buildPanels();
        panelContainer.bounds(x, y, width, height).init();
        initialized = true;
    }

    /**
     * Build the panel hierarchy for this page.
     * Subclasses must implement this to add panels to the container.
     */
    protected abstract void buildPanels();

    @Override
    public void tick() {
        if (initialized) {
            panelContainer.tick();
            onTick();
        }
    }

    /**
     * Called each tick for subclass-specific updates.
     */
    protected void onTick() {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (initialized) {
            panelContainer.render(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return initialized && panelContainer.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return initialized && panelContainer.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return initialized && panelContainer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return initialized && panelContainer.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Get the panel container for direct manipulation if needed.
     */
    protected PanelContainer getPanelContainer() {
        return panelContainer;
    }

    /**
     * Rebuild the panels (e.g., after config changes).
     */
    protected void rebuildPanels() {
        if (initialized) {
            panelContainer.clearPanels();
            buildPanels();
            panelContainer.init();
        }
    }
}
