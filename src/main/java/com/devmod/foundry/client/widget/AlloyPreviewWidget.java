package com.devmod.foundry.client.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.menu.FoundryControllerMenu;

/**
 * Widget that shows a preview of possible alloys that can be formed
 * from the current fluids in the tank.
 */
public class AlloyPreviewWidget extends AbstractWidget {
    private static final int BORDER_COLOR = 0xFF565C66;
    private static final int INNER_BORDER_COLOR = 0xFF282C32;
    private static final int BACKGROUND_COLOR = 0xFF1E2228;
    private static final int PROGRESS_BG_COLOR = 0xFF2A3038;
    private static final int PROGRESS_COLOR = 0xFF44AA44;
    private static final int READY_COLOR = 0xFF66FF66;
    private static final int NO_ALLOY_COLOR = 0xFF666666;

    private final Supplier<FoundryControllerMenu> menuSupplier;

    // Animation
    private float animatedProgress = 0f;
    private long lastAnimTime = 0;
    private boolean pulseDirection = true;
    private float pulseAlpha = 0f;

    /**
     * Creates a new alloy preview widget.
     * @param x X position
     * @param y Y position
     * @param width Widget width
     * @param height Widget height
     * @param menuSupplier Supplier for the foundry menu
     */
    public AlloyPreviewWidget(int x, int y, int width, int height, Supplier<FoundryControllerMenu> menuSupplier) {
        super(x, y, width, height, Component.translatable("gui.devmod.foundry.alloy_preview"));
        this.menuSupplier = Objects.requireNonNull(menuSupplier);
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();

        // Draw border
        graphics.fill(x, y, x + width, y + height, BORDER_COLOR);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, INNER_BORDER_COLOR);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, BACKGROUND_COLOR);

        FoundryControllerMenu menu = menuSupplier.get();
        if (menu == null) return;

        int innerX = x + 2;
        int innerY = y + 2;
        int innerWidth = width - 4;
        int innerHeight = height - 4;

        // Check if there's an alloy preview
        int alloyFluidId = menu.getAlloyPreviewFluidId();
        int alloyRatio = menu.getAlloyPreviewRatio(); // 0-100

        if (alloyFluidId < 0 || !menu.hasAlloyPreview()) {
            // No alloy possible - show placeholder
            renderNoAlloyState(graphics, innerX, innerY, innerWidth, innerHeight);
            return;
        }

        // Get the alloy fluid
        Fluid alloyFluid = BuiltInRegistries.FLUID.byId(alloyFluidId);
        if (alloyFluid == null || alloyFluid == Fluids.EMPTY) {
            renderNoAlloyState(graphics, innerX, innerY, innerWidth, innerHeight);
            return;
        }

        // Animate progress
        updateAnimation(alloyRatio / 100f);

        // Render the alloy fluid icon
        int iconSize = Math.min(innerWidth - 4, innerHeight - 12);
        int iconX = innerX + (innerWidth - iconSize) / 2;
        int iconY = innerY + 2;

        renderFluidIcon(graphics, alloyFluid, iconX, iconY, iconSize);

        // Render progress bar below
        int barY = iconY + iconSize + 2;
        int barHeight = 4;
        int barWidth = innerWidth - 4;
        int barX = innerX + 2;

        // Background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, PROGRESS_BG_COLOR);

        // Progress fill
        int fillWidth = (int) (barWidth * animatedProgress);
        int progressColor = alloyRatio >= 100 ? READY_COLOR : PROGRESS_COLOR;
        if (fillWidth > 0) {
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, progressColor);
        }

        // Ready pulse effect
        if (alloyRatio >= 100) {
            updatePulse();
            int pulseColor = ((int)(pulseAlpha * 100) << 24) | (READY_COLOR & 0x00FFFFFF);
            graphics.fill(innerX, innerY, innerX + innerWidth, innerY + innerHeight, pulseColor);
        }
    }

    /**
     * Renders a state when no alloy is possible.
     */
    private void renderNoAlloyState(GuiGraphics graphics, int x, int y, int width, int height) {
        // Draw a "?" icon
        var font = Minecraft.getInstance().font;
        String text = "?";
        int textWidth = font.width(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - font.lineHeight) / 2;
        graphics.drawString(font, text, textX, textY, NO_ALLOY_COLOR, false);
    }

    /**
     * Renders a fluid icon.
     */
    private void renderFluidIcon(GuiGraphics graphics, Fluid fluid, int x, int y, int size) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation stillTexture = extensions.getStillTexture();

        if (stillTexture == null) {
            // Fallback: solid color
            int color = extensions.getTintColor() | 0xFF000000;
            graphics.fill(x, y, x + size, y + size, color);
            return;
        }

        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getModelManager()
            .getAtlas(InventoryMenu.BLOCK_ATLAS)
            .getSprite(stillTexture);

        int color = extensions.getTintColor();
        if (color == -1) color = 0xFFFFFFFF;

        float alpha = ((color >> 24) & 0xFF) / 255f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        if (alpha <= 0f) alpha = 1.0f;

        graphics.setColor(red, green, blue, alpha);
        graphics.blit(x, y, 0, size, size, sprite);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Updates the progress animation.
     */
    private void updateAnimation(float targetProgress) {
        long now = System.currentTimeMillis();
        if (lastAnimTime == 0) {
            lastAnimTime = now;
            animatedProgress = targetProgress;
            return;
        }

        float delta = (now - lastAnimTime) / 1000f;
        lastAnimTime = now;

        // Smooth interpolation
        float diff = targetProgress - animatedProgress;
        animatedProgress += diff * Math.min(1f, delta * 5f);
    }

    /**
     * Updates the pulse effect for ready state.
     */
    private void updatePulse() {
        float speed = 0.02f;
        if (pulseDirection) {
            pulseAlpha += speed;
            if (pulseAlpha >= 0.3f) {
                pulseAlpha = 0.3f;
                pulseDirection = false;
            }
        } else {
            pulseAlpha -= speed;
            if (pulseAlpha <= 0f) {
                pulseAlpha = 0f;
                pulseDirection = true;
            }
        }
    }

    /**
     * Builds a tooltip for this widget.
     */
    public List<Component> buildTooltipLines() {
        List<Component> tooltip = new ArrayList<>();

        FoundryControllerMenu menu = menuSupplier.get();
        if (menu == null) {
            tooltip.add(Component.translatable("gui.devmod.foundry.alloy_preview"));
            return tooltip;
        }

        int alloyFluidId = menu.getAlloyPreviewFluidId();
        int alloyRatio = menu.getAlloyPreviewRatio();

        if (alloyFluidId < 0 || !menu.hasAlloyPreview()) {
            tooltip.add(Component.translatable("gui.devmod.foundry.alloy_preview"));
            tooltip.add(Component.translatable("gui.devmod.foundry.no_alloy_possible")
                .withStyle(s -> s.withColor(0xFF888888)));
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("gui.devmod.foundry.alloy_hint")
                .withStyle(s -> s.withColor(0xFF666666)));
            return tooltip;
        }

        Fluid alloyFluid = BuiltInRegistries.FLUID.byId(alloyFluidId);
        if (alloyFluid == null || alloyFluid == Fluids.EMPTY) {
            tooltip.add(Component.translatable("gui.devmod.foundry.alloy_preview"));
            return tooltip;
        }

        FluidStack stack = new FluidStack(alloyFluid, 1000);
        tooltip.add(Component.translatable("gui.devmod.foundry.alloy_preview"));
        tooltip.add(stack.getHoverName().copy()
            .withStyle(s -> s.withColor(getFluidColor(alloyFluid))));

        if (alloyRatio >= 100) {
            tooltip.add(Component.translatable("gui.devmod.foundry.alloy_ready")
                .withStyle(s -> s.withColor(READY_COLOR)));
        } else {
            tooltip.add(Component.translatable("gui.devmod.foundry.alloy_progress", alloyRatio)
                .withStyle(s -> s.withColor(0xFFAAAA00)));
        }

        return tooltip;
    }

    /**
     * Gets the tint color for a fluid.
     */
    private int getFluidColor(Fluid fluid) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        int color = extensions.getTintColor();
        if (color == -1) return 0xFFFFFFFF;
        return color | 0xFF000000;
    }

    /**
     * Checks if the mouse is over this widget.
     */
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= getX() && mouseX < getX() + width &&
               mouseY >= getY() && mouseY < getY() + height;
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
