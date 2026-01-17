package com.devmod.clone.client.screen;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.clone.item.BioscannerItem;
import com.devmod.clone.menu.NeurocellMenu;

/**
 * GUI screen for the Neurocell container.
 * Shows a single slot for bioscanner with clear button.
 */
public class NeurocellScreen extends AbstractContainerScreen<NeurocellMenu> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("devmod", "textures/gui/neurocell.png");

    @Nullable
    private EditorButton clearButton;
    @Nullable
    private EditorButtonWidget clearButtonWidget;

    public NeurocellScreen(NeurocellMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        // Clear button (positioned to the right of the slot)
        EditorButton button = EditorButton.builder("neurocell_clear", "X")
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(this::clearBioscanner)
            .build();
        clearButton = button;
        clearButtonWidget = new EditorButtonWidget(button, leftPos + 110, topPos + 33, 20, 20);
        addRenderableWidget(clearButtonWidget);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Update clear button visibility based on slot contents
        EditorButton btn = clearButton;
        if (btn != null) {
            ItemStack stack = menu.getContainer().getItem(0);
            boolean enabled = !stack.isEmpty() && BioscannerItem.hasData(stack);
            btn.enabled(enabled);
            EditorButtonWidget widget = clearButtonWidget;
            if (widget != null) {
                widget.active = enabled;
            }
        }
    }

    private void clearBioscanner() {
        // Send clear action to server
        menu.clearBioscanner();

        // Also notify server via container action
        var mc = minecraft;
        if (mc != null && mc.gameMode != null) {
            // Use a custom button click handler
            mc.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }
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
            // Draw ghost bioscanner icon or highlight
            graphics.fill(leftPos + 79, topPos + 34, leftPos + 97, topPos + 52, DesignTokens.Neurocell.SLOT_GHOST);
        }
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        var renderFont = Objects.requireNonNull(this.font);

        // Title
        graphics.drawString(renderFont, Objects.requireNonNull(this.title), this.titleLabelX, this.titleLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);
        // Inventory label
        graphics.drawString(renderFont, Objects.requireNonNull(this.playerInventoryTitle), this.inventoryLabelX, this.inventoryLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);

        // Status text below the slot
        ItemStack stack = menu.getContainer().getItem(0);
        Component status;
        int color;

        if (stack.isEmpty()) {
            status = Objects.requireNonNull(Component.translatable("gui.devmod.neurocell.insert_bioscanner"));
            color = DesignTokens.Neurocell.STATUS_EMPTY;
        } else if (!BioscannerItem.hasData(stack)) {
            status = Objects.requireNonNull(Component.translatable("gui.devmod.neurocell.waiting_scan"));
            color = DesignTokens.Neurocell.STATUS_WAITING;
        } else {
            String entityName = BioscannerItem.getEntityName(stack);
            status = Objects.requireNonNull(Component.translatable("gui.devmod.neurocell.ready", entityName != null ? entityName : "Unknown"));
            color = DesignTokens.Neurocell.STATUS_READY;
        }

        // Center the status text
        int textWidth = renderFont.width(Objects.requireNonNull(status));
        graphics.drawString(renderFont, status, (imageWidth - textWidth) / 2, 58, color, false);
    }
}
