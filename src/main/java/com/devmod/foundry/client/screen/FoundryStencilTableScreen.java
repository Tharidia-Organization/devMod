package com.devmod.foundry.client.screen;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.foundry.menu.FoundryStencilTableMenu;
import com.devmod.foundry.network.FoundryNetworking;
import com.devmod.foundry.network.StencilTableSelectPacket;
import com.devmod.foundry.tool.FoundryPartType;

/**
 * GUI screen for the Foundry Stencil Table.
 * Displays a grid of available patterns for selection.
 */
public class FoundryStencilTableScreen extends AbstractContainerScreen<FoundryStencilTableMenu> {
    private static final int PATTERN_BUTTON_SIZE = 18;
    private static final int PATTERN_GRID_X = 55;
    private static final int PATTERN_GRID_Y = 17;
    private static final int PATTERN_GRID_COLS = 4;
    private static final int PATTERN_GRID_ROWS = 3;
    private static final int PATTERNS_PER_PAGE = PATTERN_GRID_COLS * PATTERN_GRID_ROWS;

    private int scrollOffset = 0;

    public FoundryStencilTableScreen(FoundryStencilTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // Pattern grid is handled in render, not via widgets
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderPatternGrid(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        FoundryScreenStyle.renderStandardBackground(graphics, leftPos, topPos, imageWidth, imageHeight, menu.slots);
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        var fontObj = this.font;
        if (fontObj == null) {
            return;
        }
        graphics.drawString(fontObj, this.title, this.titleLabelX, this.titleLabelY, FoundryScreenStyle.LABEL_TEXT, false);
        graphics.drawString(fontObj, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, FoundryScreenStyle.LABEL_TEXT_MUTED, false);
    }

    /**
     * Renders the pattern selection grid.
     */
    private void renderPatternGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        List<FoundryPartType> patterns = menu.getAvailablePatterns();
        int selectedIndex = menu.getSelectedPatternIndex();

        int startIndex = scrollOffset * PATTERN_GRID_COLS;
        int endIndex = Math.min(startIndex + PATTERNS_PER_PAGE, patterns.size());

        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int col = relativeIndex % PATTERN_GRID_COLS;
            int row = relativeIndex / PATTERN_GRID_COLS;

            int x = leftPos + PATTERN_GRID_X + col * PATTERN_BUTTON_SIZE;
            int y = topPos + PATTERN_GRID_Y + row * PATTERN_BUTTON_SIZE;

            FoundryPartType pattern = patterns.get(i);
            boolean isSelected = i == selectedIndex;
            boolean isHovered = mouseX >= x && mouseX < x + PATTERN_BUTTON_SIZE
                && mouseY >= y && mouseY < y + PATTERN_BUTTON_SIZE;

            // Draw button background
            int bgColor = isSelected
                ? DesignTokens.Foundry.Ui.PROGRESS
                : (isHovered ? FoundryScreenStyle.SLOT_INNER : FoundryScreenStyle.SLOT_BG);
            graphics.fill(x, y, x + PATTERN_BUTTON_SIZE - 1, y + PATTERN_BUTTON_SIZE - 1, bgColor);

            // Draw border
            int borderColor = isSelected ? FoundryScreenStyle.LABEL_TEXT : FoundryScreenStyle.SLOT_BORDER;
            graphics.renderOutline(x, y, PATTERN_BUTTON_SIZE - 1, PATTERN_BUTTON_SIZE - 1, borderColor);

            // Draw pattern icon (first letter as placeholder)
            String iconChar = pattern.id().getPath().substring(0, 1).toUpperCase(Locale.ROOT);
            graphics.drawCenteredString(this.font, iconChar, x + PATTERN_BUTTON_SIZE / 2, y + 5, 0xFFFFFFFF);
        }

        // Render scroll buttons if needed
        int maxScroll = Math.max(0, (patterns.size() - 1) / PATTERN_GRID_COLS - PATTERN_GRID_ROWS + 1);
        if (maxScroll > 0) {
            int scrollUpX = leftPos + PATTERN_GRID_X + PATTERN_GRID_COLS * PATTERN_BUTTON_SIZE + 2;
            int scrollDownX = scrollUpX;
            int scrollUpY = topPos + PATTERN_GRID_Y;
            int scrollDownY = topPos + PATTERN_GRID_Y + PATTERN_GRID_ROWS * PATTERN_BUTTON_SIZE - 12;

            // Up arrow
            boolean canScrollUp = scrollOffset > 0;
            int upColor = canScrollUp ? (isInBounds(mouseX, mouseY, scrollUpX, scrollUpY, 10, 10) ? 0xFFFFFFFF : 0xFFAAAAAA) : 0xFF555555;
            graphics.drawString(this.font, "\u25B2", scrollUpX, scrollUpY, upColor);

            // Down arrow
            boolean canScrollDown = scrollOffset < maxScroll;
            int downColor = canScrollDown ? (isInBounds(mouseX, mouseY, scrollDownX, scrollDownY, 10, 10) ? 0xFFFFFFFF : 0xFFAAAAAA) : 0xFF555555;
            graphics.drawString(this.font, "\u25BC", scrollDownX, scrollDownY, downColor);
        }
    }

    @Override
    protected void renderTooltip(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);

        // Render pattern tooltip
        List<FoundryPartType> patterns = menu.getAvailablePatterns();
        int startIndex = scrollOffset * PATTERN_GRID_COLS;

        for (int i = startIndex; i < Math.min(startIndex + PATTERNS_PER_PAGE, patterns.size()); i++) {
            int relativeIndex = i - startIndex;
            int col = relativeIndex % PATTERN_GRID_COLS;
            int row = relativeIndex / PATTERN_GRID_COLS;

            int x = leftPos + PATTERN_GRID_X + col * PATTERN_BUTTON_SIZE;
            int y = topPos + PATTERN_GRID_Y + row * PATTERN_BUTTON_SIZE;

            if (isInBounds(mouseX, mouseY, x, y, PATTERN_BUTTON_SIZE - 1, PATTERN_BUTTON_SIZE - 1)) {
                FoundryPartType pattern = patterns.get(i);
                String translationKey = "foundry.pattern." + pattern.id().getPath();
                Component patternName = Component.translatable(translationKey);
                graphics.renderTooltip(this.font, patternName, mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check pattern grid clicks
            List<FoundryPartType> patterns = menu.getAvailablePatterns();
            int startIndex = scrollOffset * PATTERN_GRID_COLS;

            for (int i = startIndex; i < Math.min(startIndex + PATTERNS_PER_PAGE, patterns.size()); i++) {
                int relativeIndex = i - startIndex;
                int col = relativeIndex % PATTERN_GRID_COLS;
                int row = relativeIndex / PATTERN_GRID_COLS;

                int x = leftPos + PATTERN_GRID_X + col * PATTERN_BUTTON_SIZE;
                int y = topPos + PATTERN_GRID_Y + row * PATTERN_BUTTON_SIZE;

                if (isInBounds((int) mouseX, (int) mouseY, x, y, PATTERN_BUTTON_SIZE - 1, PATTERN_BUTTON_SIZE - 1)) {
                    FoundryPartType pattern = patterns.get(i);
                    menu.selectPattern(i);
                    // Send packet to server
                    FoundryNetworking.sendToServer(new StencilTableSelectPacket(pattern.id()));
                    return true;
                }
            }

            // Check scroll button clicks
            int maxScroll = Math.max(0, (patterns.size() - 1) / PATTERN_GRID_COLS - PATTERN_GRID_ROWS + 1);
            if (maxScroll > 0) {
                int scrollUpX = leftPos + PATTERN_GRID_X + PATTERN_GRID_COLS * PATTERN_BUTTON_SIZE + 2;
                int scrollUpY = topPos + PATTERN_GRID_Y;
                int scrollDownY = topPos + PATTERN_GRID_Y + PATTERN_GRID_ROWS * PATTERN_BUTTON_SIZE - 12;

                if (isInBounds((int) mouseX, (int) mouseY, scrollUpX, scrollUpY, 10, 10) && scrollOffset > 0) {
                    scrollOffset--;
                    return true;
                }
                if (isInBounds((int) mouseX, (int) mouseY, scrollUpX, scrollDownY, 10, 10) && scrollOffset < maxScroll) {
                    scrollOffset++;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<FoundryPartType> patterns = menu.getAvailablePatterns();
        int maxScroll = Math.max(0, (patterns.size() - 1) / PATTERN_GRID_COLS - PATTERN_GRID_ROWS + 1);

        // Check if mouse is over the pattern grid area
        if (isInBounds((int) mouseX, (int) mouseY,
            leftPos + PATTERN_GRID_X, topPos + PATTERN_GRID_Y,
            PATTERN_GRID_COLS * PATTERN_BUTTON_SIZE, PATTERN_GRID_ROWS * PATTERN_BUTTON_SIZE)) {

            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            }
            if (scrollY < 0 && scrollOffset < maxScroll) {
                scrollOffset++;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isInBounds(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
