package com.devmod.foundry.client.screen;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.foundry.menu.FoundryToolStationMenu;

/**
 * GUI screen for the Foundry Tool Station.
 */
public class FoundryToolStationScreen extends AbstractContainerScreen<FoundryToolStationMenu> {
    private static final ResourceLocation TEXTURE = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath("devmod", "textures/gui/foundry_tool_station.png"));

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
        graphics.blit(Objects.requireNonNull(TEXTURE), leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        var fontObj = Objects.requireNonNull(this.font);
        graphics.drawString(fontObj, Objects.requireNonNull(this.title), this.titleLabelX, this.titleLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);
        graphics.drawString(fontObj, Objects.requireNonNull(this.playerInventoryTitle), this.inventoryLabelX, this.inventoryLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);
    }
}
