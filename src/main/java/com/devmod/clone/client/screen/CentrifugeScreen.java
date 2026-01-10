package com.devmod.clone.client.screen;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.clone.menu.CentrifugeMenu;

/**
 * GUI screen for the Centrifuge container.
 * Shows 3 input slots, 1 output slot, and a progress bar.
 */
public class CentrifugeScreen extends AbstractContainerScreen<CentrifugeMenu> {

    // Use vanilla furnace GUI as placeholder until custom texture is created
    private static final ResourceLocation TEXTURE = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/furnace.png"));

    // Progress bar dimensions (arrow from input to output)
    private static final int PROGRESS_X = 98;
    private static final int PROGRESS_Y = 35;
    private static final int PROGRESS_WIDTH = 24;
    private static final int PROGRESS_HEIGHT = 17;
    private static final int PROGRESS_TEX_X = 176;
    private static final int PROGRESS_TEX_Y = 0;

    public CentrifugeScreen(CentrifugeMenu menu, Inventory playerInv, Component title) {
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

        // Draw progress arrow
        float progress = menu.getProgressPercent();
        if (progress > 0) {
            int progressWidth = (int) (PROGRESS_WIDTH * progress);
            graphics.blit(TEXTURE, leftPos + PROGRESS_X, topPos + PROGRESS_Y,
                PROGRESS_TEX_X, PROGRESS_TEX_Y, progressWidth, PROGRESS_HEIGHT);
        }
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        var fontObj = Objects.requireNonNull(this.font);
        var titleComp = Objects.requireNonNull(this.title);
        var invTitle = Objects.requireNonNull(this.playerInventoryTitle);

        // Title
        graphics.drawString(fontObj, titleComp, this.titleLabelX, this.titleLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);
        // Inventory label
        graphics.drawString(fontObj, invTitle, this.inventoryLabelX, this.inventoryLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);

        // Progress percentage
        int percent = (int) (menu.getProgressPercent() * 100);
        if (percent > 0) {
            String progressText = percent + "%";
            int textWidth = fontObj.width(progressText);
            graphics.drawString(fontObj, progressText, PROGRESS_X + (PROGRESS_WIDTH - textWidth) / 2, PROGRESS_Y + PROGRESS_HEIGHT + 2, DesignTokens.Neurocell.STATUS_EMPTY, false);
        }
    }
}
