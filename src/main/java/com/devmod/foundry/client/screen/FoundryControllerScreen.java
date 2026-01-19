package com.devmod.foundry.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.foundry.menu.FoundryControllerMenu;
import com.devmod.foundry.quality.MaterialQuality;
import com.devmod.foundry.risk.RiskLevel;

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

    // New widget positions
    private static final int HEAT_BAR_X = 152;
    private static final int HEAT_BAR_Y = 21;
    private static final int HEAT_BAR_WIDTH = 6;
    private static final int HEAT_BAR_HEIGHT = 36;

    private static final int STRESS_BAR_X = 160;
    private static final int STRESS_BAR_Y = 21;
    private static final int STRESS_BAR_WIDTH = 4;
    private static final int STRESS_BAR_HEIGHT = 36;

    private static final int PURITY_BAR_X = 8;
    private static final int PURITY_BAR_Y = 60;
    private static final int PURITY_BAR_WIDTH = 50;
    private static final int PURITY_BAR_HEIGHT = 4;

    private static final int RISK_INDICATOR_X = 8;
    private static final int RISK_INDICATOR_Y = 50;
    private static final int RISK_INDICATOR_SIZE = 8;

    private static final int QUALITY_INDICATOR_X = 8;
    private static final int QUALITY_INDICATOR_Y = 38;
    private static final int QUALITY_INDICATOR_SIZE = 8;
    private static final int ALLOY_PREVIEW_X = 8;
    private static final int ALLOY_PREVIEW_Y = 26;

    private static final int PROGRESS_COLOR = DesignTokens.Foundry.Ui.PROGRESS;
    private static final int FUEL_COLOR = DesignTokens.Foundry.Ui.FUEL;
    private static final int HEAT_COLOR_COLD = DesignTokens.Foundry.Ui.HEAT_COLD;
    private static final int HEAT_COLOR_HOT = DesignTokens.Foundry.Ui.HEAT_HOT;
    private static final int STRESS_COLOR_LOW = DesignTokens.Foundry.Ui.STRESS_LOW;
    private static final int STRESS_COLOR_HIGH = DesignTokens.Foundry.Ui.STRESS_HIGH;
    private static final int PURITY_COLOR = DesignTokens.Foundry.Ui.PURITY;
    private static final int BAR_BACKGROUND = DesignTokens.Foundry.Ui.BAR_BACKGROUND;

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

        // Progress bar
        float progress = menu.getProgressPercent();
        if (progress > 0.0f) {
            int progressWidth = (int) (PROGRESS_WIDTH * progress);
            graphics.fill(leftPos + PROGRESS_X, topPos + PROGRESS_Y,
                leftPos + PROGRESS_X + progressWidth, topPos + PROGRESS_Y + PROGRESS_HEIGHT, PROGRESS_COLOR);
        }

        // Fuel bar
        float fuel = menu.getFuelPercent();
        if (fuel > 0.0f) {
            int fuelHeight = (int) (FUEL_HEIGHT * fuel);
            int yStart = topPos + FUEL_Y + (FUEL_HEIGHT - fuelHeight);
            graphics.fill(leftPos + FUEL_X, yStart,
                leftPos + FUEL_X + FUEL_WIDTH, topPos + FUEL_Y + FUEL_HEIGHT, FUEL_COLOR);
        }

        // Structure heat bar (vertical, fills from bottom)
        renderVerticalBar(graphics, HEAT_BAR_X, HEAT_BAR_Y, HEAT_BAR_WIDTH, HEAT_BAR_HEIGHT,
            getHeatPercent(), getHeatColor());

        // Thermal stress bar (vertical, fills from bottom)
        float stressPercent = menu.getThermalStressPercent();
        renderVerticalBar(graphics, STRESS_BAR_X, STRESS_BAR_Y, STRESS_BAR_WIDTH, STRESS_BAR_HEIGHT,
            stressPercent, getStressColor(stressPercent));

        // Purity bar (horizontal)
        renderHorizontalBar(graphics, PURITY_BAR_X, PURITY_BAR_Y, PURITY_BAR_WIDTH, PURITY_BAR_HEIGHT,
            menu.getPurityPercent(), PURITY_COLOR);

        // Risk indicator (colored square)
        RiskLevel risk = RiskLevel.values()[Math.min(menu.getRiskLevelOrdinal(), RiskLevel.values().length - 1)];
        int riskColor = DesignTokens.Mask.ALPHA | risk.getColor();
        graphics.fill(leftPos + RISK_INDICATOR_X, topPos + RISK_INDICATOR_Y,
            leftPos + RISK_INDICATOR_X + RISK_INDICATOR_SIZE, topPos + RISK_INDICATOR_Y + RISK_INDICATOR_SIZE, riskColor);

        // Quality indicator (colored square)
        MaterialQuality quality = MaterialQuality.fromTier(menu.getMoltenQualityTier());
        int qualityColor = DesignTokens.Mask.ALPHA | quality.getColor();
        graphics.fill(leftPos + QUALITY_INDICATOR_X, topPos + QUALITY_INDICATOR_Y,
            leftPos + QUALITY_INDICATOR_X + QUALITY_INDICATOR_SIZE, topPos + QUALITY_INDICATOR_Y + QUALITY_INDICATOR_SIZE, qualityColor);
    }

    private void renderVerticalBar(GuiGraphics graphics, int x, int y, int width, int height, float percent, int color) {
        // Background
        graphics.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + height, BAR_BACKGROUND);
        // Filled portion (from bottom)
        if (percent > 0.0f) {
            int filledHeight = (int) (height * Math.min(1.0f, percent));
            int yStart = topPos + y + (height - filledHeight);
            graphics.fill(leftPos + x, yStart, leftPos + x + width, topPos + y + height, color);
        }
    }

    private void renderHorizontalBar(GuiGraphics graphics, int x, int y, int width, int height, float percent, int color) {
        // Background
        graphics.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + height, BAR_BACKGROUND);
        // Filled portion (from left)
        if (percent > 0.0f) {
            int filledWidth = (int) (width * Math.min(1.0f, percent));
            graphics.fill(leftPos + x, topPos + y, leftPos + x + filledWidth, topPos + y + height, color);
        }
    }

    private float getHeatPercent() {
        int heat = menu.getStructureHeat();
        int maxHeat = Math.max(menu.getFuelTemperature(), 1);
        return Math.min(1.0f, (float) heat / maxHeat);
    }

    private int getHeatColor() {
        float heatPercent = getHeatPercent();
        // Interpolate from cold (blue) to hot (red/orange)
        return interpolateColor(HEAT_COLOR_COLD, HEAT_COLOR_HOT, heatPercent);
    }

    private int getStressColor(float stressPercent) {
        // Interpolate from green (safe) to red (danger)
        return interpolateColor(STRESS_COLOR_LOW, STRESS_COLOR_HIGH, stressPercent);
    }

    private int interpolateColor(int colorA, int colorB, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aA = (colorA >> 24) & 0xFF;
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;
        int aB = (colorB >> 24) & 0xFF;
        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;
        int a = (int) (aA + (aB - aA) * t);
        int r = (int) (rA + (rB - rA) * t);
        int g = (int) (gA + (gB - gA) * t);
        int b = (int) (bA + (bB - bA) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        var fontObj = Objects.requireNonNull(this.font);
        graphics.drawString(fontObj, Objects.requireNonNull(this.title), this.titleLabelX, this.titleLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);
        graphics.drawString(fontObj, Objects.requireNonNull(this.playerInventoryTitle), this.inventoryLabelX, this.inventoryLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);

        // Temperature display
        int temp = menu.getFuelTemperature();
        int structHeat = menu.getStructureHeat();
        if (temp > 0) {
            graphics.drawString(fontObj, structHeat + "/" + temp + "C", 8, 6, DesignTokens.Neurocell.LABEL_TEXT, false);
        }

        // Molten display
        int molten = menu.getMoltenAmount();
        int capacity = menu.getMoltenCapacity();
        if (capacity > 0) {
            graphics.drawString(fontObj, molten + "/" + capacity + " mB", 8, 16, DesignTokens.Neurocell.LABEL_TEXT, false);
        }

        // Alloy preview (dynamic ratio)
        if (menu.hasAlloyPreview()) {
            int fluidId = menu.getAlloyPreviewFluidId();
            if (fluidId >= 0) {
                var fluid = BuiltInRegistries.FLUID.byId(fluidId);
                if (fluid != null) {
                    Component fluidName = new FluidStack(fluid, 1).getHoverName();
                    int ratio = menu.getAlloyPreviewRatio();
                    Component preview = Component.translatable("gui.devmod.foundry.alloy_preview", fluidName, ratio);
                    graphics.drawString(fontObj, preview, ALLOY_PREVIEW_X, ALLOY_PREVIEW_Y, DesignTokens.Neurocell.LABEL_TEXT, false);
                }
            }
        }

        // Risk level text next to indicator
        RiskLevel risk = RiskLevel.values()[Math.min(menu.getRiskLevelOrdinal(), RiskLevel.values().length - 1)];
        int riskTextColor = DesignTokens.Mask.ALPHA | risk.getColor();
        graphics.drawString(fontObj, risk.getName().toUpperCase(Locale.ROOT), RISK_INDICATOR_X + RISK_INDICATOR_SIZE + 2, RISK_INDICATOR_Y, riskTextColor, false);

        // Quality text next to indicator
        MaterialQuality quality = MaterialQuality.fromTier(menu.getMoltenQualityTier());
        int qualityTextColor = DesignTokens.Mask.ALPHA | quality.getColor();
        graphics.drawString(fontObj, quality.getDisplayName(), QUALITY_INDICATOR_X + QUALITY_INDICATOR_SIZE + 2, QUALITY_INDICATOR_Y, qualityTextColor, false);

        // Purity label
        int purityPercent = (int) (menu.getPurityPercent() * 100);
        graphics.drawString(fontObj, "Purity: " + purityPercent + "%", PURITY_BAR_X + PURITY_BAR_WIDTH + 4, PURITY_BAR_Y - 1, DesignTokens.Neurocell.LABEL_TEXT, false);

        // Structure damage indicator (if any)
        int damage = menu.getStructureDamage();
        if (damage > 0) {
            graphics.drawString(fontObj, "DMG: " + damage, 8, 26, DesignTokens.Foundry.Ui.DAMAGE_TEXT, false);
        }
    }

    @Override
    protected void renderTooltip(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);

        // Heat bar tooltip
        if (isMouseOver(mouseX, mouseY, HEAT_BAR_X, HEAT_BAR_Y, HEAT_BAR_WIDTH, HEAT_BAR_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.devmod.foundry.structure_heat"));
            tooltip.add(Component.literal(menu.getStructureHeat() + "C / " + menu.getFuelTemperature() + "C"));
            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // Stress bar tooltip
        if (isMouseOver(mouseX, mouseY, STRESS_BAR_X, STRESS_BAR_Y, STRESS_BAR_WIDTH, STRESS_BAR_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.devmod.foundry.thermal_stress"));
            int stressPercent = (int) (menu.getThermalStressPercent() * 100);
            tooltip.add(Component.literal(stressPercent + "%"));
            if (stressPercent >= 80) {
                tooltip.add(Component.translatable("gui.devmod.foundry.stress_warning")
                    .withStyle(s -> s.withColor(DesignTokens.Foundry.Ui.STRESS_WARNING)));
            }
            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // Risk indicator tooltip
        if (isMouseOver(mouseX, mouseY, RISK_INDICATOR_X, RISK_INDICATOR_Y, RISK_INDICATOR_SIZE, RISK_INDICATOR_SIZE)) {
            RiskLevel risk = RiskLevel.values()[Math.min(menu.getRiskLevelOrdinal(), RiskLevel.values().length - 1)];
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.devmod.foundry.risk_level"));
            tooltip.add(risk.getColoredDisplayName());
            int efficiency = (int) (risk.getEfficiencyMultiplier() * 100);
            tooltip.add(Component.translatable("gui.devmod.foundry.efficiency", efficiency + "%"));
            if (risk.getIncidentChance() > 0) {
                int chance = (int) (risk.getIncidentChance() * 100);
                tooltip.add(Component.translatable("gui.devmod.foundry.incident_chance", chance + "%")
                    .withStyle(s -> s.withColor(DesignTokens.Foundry.Ui.INCIDENT_WARNING)));
            }
            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // Quality indicator tooltip
        if (isMouseOver(mouseX, mouseY, QUALITY_INDICATOR_X, QUALITY_INDICATOR_Y, QUALITY_INDICATOR_SIZE, QUALITY_INDICATOR_SIZE)) {
            MaterialQuality quality = MaterialQuality.fromTier(menu.getMoltenQualityTier());
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.devmod.foundry.quality"));
            tooltip.add(quality.getColoredDisplayName());
            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // Purity bar tooltip
        if (isMouseOver(mouseX, mouseY, PURITY_BAR_X, PURITY_BAR_Y, PURITY_BAR_WIDTH, PURITY_BAR_HEIGHT)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.devmod.foundry.purity"));
            int purity = (int) (menu.getPurityPercent() * 100);
            tooltip.add(Component.literal(purity + "%"));
            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        int actualX = leftPos + x;
        int actualY = topPos + y;
        return mouseX >= actualX && mouseX < actualX + width && mouseY >= actualY && mouseY < actualY + height;
    }
}
