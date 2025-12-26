package com.devmod.client.ui.testing.panel;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;

import static com.devmod.client.ui.testing.panel.PanelConstants.PANEL_SPACING;
public record CompositePanel(
    String id,
    String title,
    List<UIPanel> children
) implements UIPanel {

    @Override
    public int getHeight(int availableWidth) {
        int height = 0;
        for (UIPanel child : children) {
            if (child.isVisible()) {
                height += child.getHeight(availableWidth) + PANEL_SPACING;
            }
        }
        return height > 0 ? height - PANEL_SPACING : 0;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        int currentY = y;
        for (UIPanel child : children) {
            if (child.isVisible()) {
                child.render(graphics, x, currentY, width, mouseX, mouseY);
                currentY += child.getHeight(width) + PANEL_SPACING;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (UIPanel child : children) {
            if (child.isVisible() && child.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (UIPanel child : children) {
            if (child.isVisible() && child.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        for (UIPanel child : children) {
            if (child.isVisible() && child.mouseDragged(mouseX, mouseY, button, dx, dy)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        for (UIPanel child : children) {
            child.tick();
        }
    }

    @Override
    public void init() {
        for (UIPanel child : children) {
            child.init();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FACTORY
    // ═══════════════════════════════════════════════════════════════

    public static CompositePanel of(String id, String title, UIPanel... panels) {
        return new CompositePanel(id, title, Arrays.asList(panels));
    }

    public static CompositePanel of(String id, String title, List<UIPanel> panels) {
        return new CompositePanel(id, title, List.copyOf(panels));
    }

    public static CompositePanel of(String title, UIPanel... panels) {
        return of("composite-" + title.toLowerCase().replace(" ", "-"), title, panels);
    }
}
