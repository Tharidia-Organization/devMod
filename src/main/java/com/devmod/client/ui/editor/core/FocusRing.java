package com.devmod.client.ui.editor.core;

import com.devmod.client.ui.AxiomRenderer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Focus ring specification for keyboard navigation.
 * Provides visual feedback for focused elements.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#13-accessibility-standards
 */
public final class FocusRing {
    private FocusRing() {}

    /** Focus ring color - cyan, high visibility */
    public static final int COLOR = 0xFF00D4FF;

    /** Focus ring outline width */
    public static final int WIDTH = 2;

    /** Offset outside the component */
    public static final int OFFSET = 2;

    /**
     * Render a focus ring around a component.
     *
     * @param graphics The graphics context
     * @param x Component X position
     * @param y Component Y position
     * @param w Component width
     * @param h Component height
     */
    public static void render(GuiGraphics graphics, int x, int y, int w, int h) {
        int fx = x - OFFSET;
        int fy = y - OFFSET;
        int fw = w + OFFSET * 2;
        int fh = h + OFFSET * 2;

        // Outer glow (semi-transparent)
        graphics.fill(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, 0x4000D4FF);

        // Inner ring border
        AxiomRenderer.drawBorder(graphics, fx, fy, fw, fh, COLOR);
    }

    /**
     * Render a focus ring with custom offset.
     */
    public static void render(GuiGraphics graphics, int x, int y, int w, int h, int customOffset) {
        int fx = x - customOffset;
        int fy = y - customOffset;
        int fw = w + customOffset * 2;
        int fh = h + customOffset * 2;

        graphics.fill(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, 0x4000D4FF);
        AxiomRenderer.drawBorder(graphics, fx, fy, fw, fh, COLOR);
    }
}
