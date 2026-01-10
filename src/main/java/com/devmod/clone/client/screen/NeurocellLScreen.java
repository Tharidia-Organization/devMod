package com.devmod.clone.client.screen;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.clone.item.BioscannerItem;
import com.devmod.clone.menu.NeurocellLMenu;

/**
 * GUI screen for the NeurocellL container (2x2x2 large chamber).
 * Shows a single slot for bioscanner with clear button.
 */
public class NeurocellLScreen extends AbstractContainerScreen<NeurocellLMenu> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("devmod", "textures/gui/neurocell.png");

    private Button clearButton;

    public NeurocellLScreen(NeurocellLMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        // Clear button (positioned to the right of the slot)
        clearButton = Button.builder(
            Component.literal("X"),
            btn -> clearBioscanner()
        ).bounds(leftPos + 110, topPos + 33, 20, 20).build();

        addRenderableWidget(clearButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Update clear button visibility based on slot contents
        if (clearButton != null) {
            ItemStack stack = menu.getContainer().getItem(0);
            clearButton.active = !stack.isEmpty() && BioscannerItem.hasData(stack);
        }
    }

    private void clearBioscanner() {
        // Send clear action to server
        menu.clearBioscanner();

        // Also notify server via container action
        if (minecraft != null && minecraft.gameMode != null) {
            // Use a custom button click handler
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Draw slot highlight if empty
        ItemStack stack = menu.getContainer().getItem(0);
        if (stack.isEmpty()) {
            // Draw ghost bioscanner icon or highlight
            graphics.fill(leftPos + 79, topPos + 34, leftPos + 97, topPos + 52, DesignTokens.Neurocell.SLOT_GHOST);
        }
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        // Title
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);
        // Inventory label
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);

        // Status text below the slot
        ItemStack stack = menu.getContainer().getItem(0);
        Component status;
        int color;

        if (stack.isEmpty()) {
            status = Component.translatable("gui.devmod.neurocell.insert_bioscanner");
            color = DesignTokens.Neurocell.STATUS_EMPTY;
        } else if (!BioscannerItem.hasData(stack)) {
            status = Component.translatable("gui.devmod.neurocell.waiting_scan");
            color = DesignTokens.Neurocell.STATUS_WAITING;
        } else {
            String entityName = BioscannerItem.getEntityName(stack);
            status = Component.translatable("gui.devmod.neurocell.ready", entityName != null ? entityName : "Unknown");
            color = DesignTokens.Neurocell.STATUS_READY;
        }

        // Center the status text
        int textWidth = this.font.width(status);
        graphics.drawString(this.font, status, (imageWidth - textWidth) / 2, 58, color, false);
    }
}
