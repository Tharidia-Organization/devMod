package com.devmod.clone.client.screen;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.clone.menu.NeurocellItemMenu;

/**
 * GUI screen for the Neurocell Item Display container.
 * Shows a single slot for displaying an item.
 */
public class NeurocellItemScreen extends AbstractContainerScreen<NeurocellItemMenu> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("devmod", "textures/gui/neurocell_item.png");

    public NeurocellItemScreen(NeurocellItemMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(Objects.requireNonNull(TEXTURE), leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Draw slot highlight if empty
        ItemStack stack = menu.getContainer().getItem(0);
        if (stack.isEmpty()) {
            // Draw ghost item icon or highlight
            graphics.fill(leftPos + 79, topPos + 34, leftPos + 97, topPos + 52, DesignTokens.Neurocell.SLOT_GHOST);
        }
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        var renderFont = Objects.requireNonNull(this.font);
        String titleStr = Objects.requireNonNull(this.title).getString();

        // Title (centered)
        int titleWidth = UIScaleManager.getScaledStringWidth(renderFont, titleStr);
        UIScaleManager.drawScaledString(graphics, renderFont, titleStr, (imageWidth - titleWidth) / 2, this.titleLabelY,
            DesignTokens.Neurocell.LABEL_TEXT, false);

        // Inventory label
        UIScaleManager.drawScaledString(graphics, renderFont, Objects.requireNonNull(this.playerInventoryTitle).getString(),
            this.inventoryLabelX, this.inventoryLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);

        // Status text below the slot
        ItemStack stack = menu.getContainer().getItem(0);
        String statusStr;
        int color;

        if (stack.isEmpty()) {
            statusStr = Objects.requireNonNull(Component.translatable("gui.devmod.item_display.insert_item")).getString();
            color = DesignTokens.Neurocell.STATUS_EMPTY;
        } else {
            statusStr = Objects.requireNonNull(Component.translatable("gui.devmod.item_display.displaying",
                stack.getHoverName().getString())).getString();
            color = DesignTokens.Neurocell.STATUS_READY;
        }

        // Center the status text
        int textWidth = UIScaleManager.getScaledStringWidth(renderFont, statusStr);
        int maxWidth = imageWidth - 16;
        if (textWidth > maxWidth) {
            // Truncate long names
            statusStr = Objects.requireNonNull(renderFont.plainSubstrByWidth(statusStr, maxWidth - 6)) + "...";
            textWidth = UIScaleManager.getScaledStringWidth(renderFont, statusStr);
        }
        UIScaleManager.drawScaledString(graphics, renderFont, statusStr, (imageWidth - textWidth) / 2, 58, color, false);
    }
}
