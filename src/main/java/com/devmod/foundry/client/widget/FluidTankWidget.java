package com.devmod.foundry.client.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.menu.FoundryControllerMenu;

/**
 * Visual tank widget that displays stacked fluids with hover detection.
 * Fluids are rendered from bottom to top, proportionally based on their amounts.
 */
public class FluidTankWidget extends AbstractWidget {
    private static final int BORDER_COLOR = 0xFF565C66;
    private static final int INNER_BORDER_COLOR = 0xFF282C32;
    private static final int BACKGROUND_COLOR = 0xFF1E2228;
    private static final int TANK_LINES_COLOR = 0xFF2A3038;

    private final Supplier<FoundryControllerMenu> menuSupplier;

    // Cached hovered fluid info
    @Nullable
    private FluidStack hoveredFluid = null;
    private int hoveredFluidIndex = -1;

    /**
     * Creates a new fluid tank widget.
     * @param x X position
     * @param y Y position
     * @param width Widget width
     * @param height Widget height
     * @param menuSupplier Supplier for the foundry menu (to get fluid data)
     */
    public FluidTankWidget(int x, int y, int width, int height, Supplier<FoundryControllerMenu> menuSupplier) {
        super(x, y, width, height, Component.translatable("gui.devmod.foundry.tank"));
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

        // Draw tank graduation lines (every 25%)
        int innerX = x + 2;
        int innerY = y + 2;
        int innerWidth = width - 4;
        int innerHeight = height - 4;

        for (int i = 1; i < 4; i++) {
            int lineY = innerY + (innerHeight * i / 4);
            graphics.fill(innerX, lineY, innerX + innerWidth, lineY + 1, TANK_LINES_COLOR);
        }

        // Get fluid data from menu
        FoundryControllerMenu menu = menuSupplier.get();
        if (menu == null) return;

        int fluidCount = menu.getFluidTypeCount();
        if (fluidCount == 0) {
            hoveredFluid = null;
            hoveredFluidIndex = -1;
            return;
        }

        // Calculate total amount for capacity ratio
        int capacity = menu.getMoltenCapacity();
        if (capacity <= 0) return;

        // Build list of fluids with their render info
        List<FluidRenderInfo> fluidInfos = new ArrayList<>();
        int totalAmount = 0;

        for (int i = 0; i < Math.min(fluidCount, FoundryControllerMenu.MAX_DISPLAYED_FLUIDS); i++) {
            int fluidId = menu.getFluidIdAt(i);
            int amount = menu.getFluidAmountAt(i);

            if (fluidId < 0 || amount <= 0) continue;

            Fluid fluid = BuiltInRegistries.FLUID.byId(fluidId);
            if (fluid == null) continue;

            fluidInfos.add(new FluidRenderInfo(fluid, amount, i));
            totalAmount += amount;
        }

        if (fluidInfos.isEmpty() || totalAmount == 0) {
            hoveredFluid = null;
            hoveredFluidIndex = -1;
            return;
        }

        // Calculate fill height based on capacity
        float fillRatio = Math.min(1.0f, (float) totalAmount / capacity);
        int totalFillHeight = (int) (innerHeight * fillRatio);

        // Render fluids from bottom to top
        int currentY = innerY + innerHeight; // Start at bottom
        hoveredFluid = null;
        hoveredFluidIndex = -1;

        for (FluidRenderInfo info : fluidInfos) {
            // Calculate this fluid's height
            float fluidRatio = (float) info.amount / totalAmount;
            int fluidHeight = Math.max(1, (int) (totalFillHeight * fluidRatio));

            // Don't exceed available space
            if (currentY - fluidHeight < innerY) {
                fluidHeight = currentY - innerY;
            }

            if (fluidHeight <= 0) continue;

            int fluidY = currentY - fluidHeight;

            // Render the fluid
            renderFluidBar(graphics, info.fluid, innerX, fluidY, innerWidth, fluidHeight);

            // Check hover
            if (mouseX >= innerX && mouseX < innerX + innerWidth &&
                mouseY >= fluidY && mouseY < currentY) {
                hoveredFluid = new FluidStack(info.fluid, info.amount);
                hoveredFluidIndex = info.index;
            }

            currentY -= fluidHeight;
        }

        // Draw highlight border on hovered fluid
        if (hoveredFluid != null && hoveredFluidIndex >= 0) {
            // Find the hovered segment bounds
            currentY = innerY + innerHeight;
            for (FluidRenderInfo info : fluidInfos) {
                float fluidRatio = (float) info.amount / totalAmount;
                int fluidHeight = Math.max(1, (int) (totalFillHeight * fluidRatio));
                if (currentY - fluidHeight < innerY) {
                    fluidHeight = currentY - innerY;
                }
                if (fluidHeight <= 0) continue;

                int fluidY = currentY - fluidHeight;

                if (info.index == hoveredFluidIndex) {
                    // Draw highlight border
                    int highlightColor = 0xAAFFFFFF;
                    graphics.fill(innerX, fluidY, innerX + innerWidth, fluidY + 1, highlightColor);
                    graphics.fill(innerX, currentY - 1, innerX + innerWidth, currentY, highlightColor);
                    graphics.fill(innerX, fluidY, innerX + 1, currentY, highlightColor);
                    graphics.fill(innerX + innerWidth - 1, fluidY, innerX + innerWidth, currentY, highlightColor);
                    break;
                }

                currentY -= fluidHeight;
            }
        }
    }

    /**
     * Renders a fluid bar segment using the fluid's texture.
     */
    private void renderFluidBar(GuiGraphics graphics, Fluid fluid, int x, int y, int width, int height) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation stillTexture = extensions.getStillTexture();

        if (stillTexture == null) {
            // Fallback: solid color
            int color = extensions.getTintColor() | 0xFF000000;
            graphics.fill(x, y, x + width, y + height, color);
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

        // Render tiled fluid texture
        renderTiledSprite(graphics, sprite, x, y, width, height, red, green, blue, alpha);
    }

    /**
     * Renders a sprite tiled to fill the specified area.
     */
    private void renderTiledSprite(GuiGraphics graphics, TextureAtlasSprite sprite,
                                   int x, int y, int width, int height,
                                   float red, float green, float blue, float alpha) {
        int spriteWidth = sprite.contents().width();
        int spriteHeight = sprite.contents().height();

        for (int tileY = 0; tileY < height; tileY += spriteHeight) {
            int tileHeight = Math.min(spriteHeight, height - tileY);
            for (int tileX = 0; tileX < width; tileX += spriteWidth) {
                int tileWidth = Math.min(spriteWidth, width - tileX);
                int renderX = x + tileX;
                int renderY = y + tileY;

                // Use blitSprite with color modulation
                graphics.setColor(red, green, blue, alpha);
                graphics.blit(renderX, renderY, 0, tileWidth, tileHeight, sprite);
                graphics.setColor(1f, 1f, 1f, 1f);
            }
        }
    }

    /**
     * Gets the currently hovered fluid, or null if none.
     */
    @Nullable
    public FluidStack getHoveredFluid() {
        return hoveredFluid;
    }

    /**
     * Gets the index of the hovered fluid in the menu's fluid list.
     */
    public int getHoveredFluidIndex() {
        return hoveredFluidIndex;
    }

    /**
     * Builds a tooltip for the currently hovered fluid.
     */
    public List<Component> getHoveredFluidTooltip() {
        List<Component> tooltip = new ArrayList<>();

        if (hoveredFluid == null || hoveredFluid.isEmpty()) {
            return tooltip;
        }

        FoundryControllerMenu menu = menuSupplier.get();
        if (menu == null) return tooltip;

        // Fluid name
        tooltip.add(hoveredFluid.getHoverName());

        // Amount
        tooltip.add(Component.literal(hoveredFluid.getAmount() + " mB")
            .withStyle(s -> s.withColor(0xFFAAAAAA)));

        // Percentage of tank
        int totalAmount = 0;
        int fluidCount = menu.getFluidTypeCount();
        for (int i = 0; i < Math.min(fluidCount, FoundryControllerMenu.MAX_DISPLAYED_FLUIDS); i++) {
            totalAmount += menu.getFluidAmountAt(i);
        }
        if (totalAmount > 0) {
            int percent = Math.round((float) hoveredFluid.getAmount() / totalAmount * 100);
            tooltip.add(Component.translatable("gui.devmod.foundry.tank_percent", percent)
                .withStyle(s -> s.withColor(0xFF888888)));
        }

        // Capacity info
        int capacity = menu.getMoltenCapacity();
        if (capacity > 0) {
            int fillPercent = Math.round((float) totalAmount / capacity * 100);
            tooltip.add(Component.translatable("gui.devmod.foundry.tank_fill", totalAmount, capacity, fillPercent)
                .withStyle(s -> s.withColor(0xFF666666)));
        }

        return tooltip;
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /**
     * Internal record for tracking fluid render info.
     */
    private record FluidRenderInfo(Fluid fluid, int amount, int index) {}
}
