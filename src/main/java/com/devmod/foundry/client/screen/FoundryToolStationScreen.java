package com.devmod.foundry.client.screen;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import com.devmod.foundry.menu.FoundryToolStationMenu;

/**
 * GUI screen for the Foundry Tool Station.
 */
public class FoundryToolStationScreen extends AbstractContainerScreen<FoundryToolStationMenu> {
    public FoundryToolStationScreen(FoundryToolStationMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
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
}
