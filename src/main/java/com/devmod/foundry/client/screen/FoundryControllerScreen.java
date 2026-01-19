package com.devmod.foundry.client.screen;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.foundry.menu.FoundryControllerMenu;

/**
 * GUI screen for the Foundry Controller.
 * Uses the vanilla furnace texture as a placeholder.
 */
public class FoundryControllerScreen extends AbstractContainerScreen<FoundryControllerMenu> {
    private static final ResourceLocation TEXTURE = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath("devmod", "textures/gui/foundry_controller.png"));

    private static final int PROGRESS_X = 79;
    private static final int PROGRESS_Y = 35;
    private static final int PROGRESS_WIDTH = 24;
    private static final int PROGRESS_HEIGHT = 17;
    private static final int FUEL_X = 139;
    private static final int FUEL_Y = 21;
    private static final int FUEL_WIDTH = 8;
    private static final int FUEL_HEIGHT = 36;

    private static final int PROGRESS_COLOR = 0xFFCF7A2C;
    private static final int FUEL_COLOR = 0xFFF2902A;

    public FoundryControllerScreen(FoundryControllerMenu menu, Inventory playerInv, Component title) {
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
        float progress = menu.getProgressPercent();
        if (progress > 0.0f) {
            int progressWidth = (int) (PROGRESS_WIDTH * progress);
            graphics.fill(leftPos + PROGRESS_X, topPos + PROGRESS_Y,
                leftPos + PROGRESS_X + progressWidth, topPos + PROGRESS_Y + PROGRESS_HEIGHT, PROGRESS_COLOR);
        }

        float fuel = menu.getFuelPercent();
        if (fuel > 0.0f) {
            int fuelHeight = (int) (FUEL_HEIGHT * fuel);
            int yStart = topPos + FUEL_Y + (FUEL_HEIGHT - fuelHeight);
            graphics.fill(leftPos + FUEL_X, yStart,
                leftPos + FUEL_X + FUEL_WIDTH, topPos + FUEL_Y + FUEL_HEIGHT, FUEL_COLOR);
        }
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        var fontObj = Objects.requireNonNull(this.font);
        graphics.drawString(fontObj, Objects.requireNonNull(this.title), this.titleLabelX, this.titleLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);
        graphics.drawString(fontObj, Objects.requireNonNull(this.playerInventoryTitle), this.inventoryLabelX, this.inventoryLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);

        int temp = menu.getFuelTemperature();
        if (temp > 0) {
            graphics.drawString(fontObj, "Temp: " + temp + "C", 8, 6, DesignTokens.Neurocell.LABEL_TEXT, false);
        }
    }
}
