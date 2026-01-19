package com.devmod.foundry.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Shared procedural styling for Foundry module screens.
 */
public final class FoundryScreenStyle {
    public static final int FRAME_OUTER = 0xFF181A1E;
    public static final int FRAME_INNER = 0xFF3C424A;
    public static final int PANEL_BORDER = 0xFF24282E;
    public static final int PANEL_TOP = 0xFF2E343C;
    public static final int PANEL_BOTTOM = 0xFF1E2228;
    public static final int SLOT_BORDER = 0xFF565C66;
    public static final int SLOT_INNER = 0xFF282C32;
    public static final int SLOT_BG = 0xFF1E2228;
    public static final int LABEL_TEXT = DesignTokens.Text.PRIMARY();
    public static final int LABEL_TEXT_MUTED = DesignTokens.Text.SECONDARY();

    private static final int DEFAULT_TOP_PANEL_HEIGHT = 72;

    private FoundryScreenStyle() {}

    public static void renderStandardBackground(
        GuiGraphics graphics,
        int leftPos,
        int topPos,
        int imageWidth,
        int imageHeight,
        List<Slot> slots
    ) {
        renderStandardBackground(graphics, leftPos, topPos, imageWidth, imageHeight, DEFAULT_TOP_PANEL_HEIGHT, slots);
    }

    public static void renderStandardBackground(
        GuiGraphics graphics,
        int leftPos,
        int topPos,
        int imageWidth,
        int imageHeight,
        int topPanelHeight,
        List<Slot> slots
    ) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, FRAME_OUTER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, FRAME_INNER);

        int panelX = leftPos + 4;
        int panelW = imageWidth - 8;
        int topPanelY = topPos + 4;
        int dividerY = topPanelY + topPanelHeight;
        drawPanel(graphics, panelX, topPanelY, panelW, topPanelHeight, PANEL_TOP);
        graphics.fill(panelX, dividerY, panelX + panelW, dividerY + 2, FRAME_OUTER);

        int bottomPanelY = dividerY + 2;
        int bottomPanelH = (topPos + imageHeight - 4) - bottomPanelY;
        drawPanel(graphics, panelX, bottomPanelY, panelW, bottomPanelH, PANEL_BOTTOM);

        drawSlotBackgrounds(graphics, leftPos, topPos, slots);
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height, int fillColor) {
        graphics.fill(x, y, x + width, y + height, PANEL_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fillColor);
    }

    private static void drawSlotBackgrounds(GuiGraphics graphics, int leftPos, int topPos, List<Slot> slots) {
        for (Slot slot : slots) {
            if (!slot.isActive()) {
                continue;
            }
            drawSlot(graphics, leftPos, topPos, slot.x, slot.y);
        }
    }

    private static void drawSlot(GuiGraphics graphics, int leftPos, int topPos, int slotX, int slotY) {
        int x0 = leftPos + slotX - 1;
        int y0 = topPos + slotY - 1;
        graphics.fill(x0, y0, x0 + 18, y0 + 18, SLOT_BORDER);
        graphics.fill(x0 + 1, y0 + 1, x0 + 17, y0 + 17, SLOT_INNER);
        graphics.fill(x0 + 2, y0 + 2, x0 + 16, y0 + 16, SLOT_BG);
    }
}
