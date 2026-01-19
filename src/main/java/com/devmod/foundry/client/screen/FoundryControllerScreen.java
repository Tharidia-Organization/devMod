package com.devmod.foundry.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.foundry.client.widget.AlloyPreviewWidget;
import com.devmod.foundry.client.widget.FluidTankWidget;
import com.devmod.foundry.menu.FoundryControllerMenu;
import com.devmod.foundry.quality.MaterialQuality;
import com.devmod.foundry.risk.RiskLevel;

/**
 * GUI screen for the Foundry Controller.
 * Renders a procedural background aligned to slot positions.
 */
public class FoundryControllerScreen extends AbstractContainerScreen<FoundryControllerMenu> {
    private static final int FRAME_OUTER = 0xFF181A1E;
    private static final int FRAME_INNER = 0xFF3C424A;
    private static final int PANEL_BORDER = 0xFF24282E;
    private static final int PANEL_TOP = 0xFF2E343C;
    private static final int PANEL_BOTTOM = 0xFF1E2228;
    private static final int SLOT_BORDER = 0xFF565C66;
    private static final int SLOT_INNER = 0xFF282C32;
    private static final int SLOT_BG = 0xFF1E2228;
    private static final int LABEL_TEXT = DesignTokens.Text.PRIMARY();
    private static final int LABEL_TEXT_MUTED = DesignTokens.Text.SECONDARY();

    private static final int UI_VERTICAL_OFFSET = 16;
    private static final int PROGRESS_X = 79;
    private static final int PROGRESS_Y = 35 + UI_VERTICAL_OFFSET;
    private static final int PROGRESS_WIDTH = 24;
    private static final int PROGRESS_HEIGHT = 17;
    private static final int FUEL_X = 139;
    private static final int FUEL_Y = 21 + UI_VERTICAL_OFFSET;
    private static final int FUEL_WIDTH = 8;
    private static final int FUEL_HEIGHT = 36;

    // New widget positions
    private static final int HEAT_BAR_X = 152;
    private static final int HEAT_BAR_Y = 21 + UI_VERTICAL_OFFSET;
    private static final int HEAT_BAR_WIDTH = 6;
    private static final int HEAT_BAR_HEIGHT = 36;

    private static final int STRESS_BAR_X = 160;
    private static final int STRESS_BAR_Y = 21 + UI_VERTICAL_OFFSET;
    private static final int STRESS_BAR_WIDTH = 4;
    private static final int STRESS_BAR_HEIGHT = 36;

    private static final int PURITY_BAR_X = 8;
    private static final int PURITY_BAR_Y = 60 + UI_VERTICAL_OFFSET;
    private static final int PURITY_BAR_WIDTH = 50;
    private static final int PURITY_BAR_HEIGHT = 4;

    private static final int RISK_INDICATOR_X = 8;
    private static final int RISK_INDICATOR_Y = 50 + UI_VERTICAL_OFFSET;
    private static final int RISK_INDICATOR_SIZE = 8;

    private static final int QUALITY_INDICATOR_X = 8;
    private static final int QUALITY_INDICATOR_Y = 38 + UI_VERTICAL_OFFSET;
    private static final int QUALITY_INDICATOR_SIZE = 8;
    private static final int STATS_TEXT_Y = 8 + UI_VERTICAL_OFFSET;
    private static final int STATS_TEXT_RIGHT = FUEL_X - 6;
    private static final int STATS_PANEL_Y = 4 + UI_VERTICAL_OFFSET;
    private static final int STATS_PANEL_HEIGHT = 24;
    private static final int STATS_PANEL_WIDTH = 72;
    private static final int TITLE_LABEL_X = 8;
    private static final int TITLE_LABEL_Y = 6;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = 82 + UI_VERTICAL_OFFSET;

    private static final int PROGRESS_COLOR = DesignTokens.Foundry.Ui.PROGRESS;
    private static final int FUEL_COLOR = DesignTokens.Foundry.Ui.FUEL;
    private static final int HEAT_COLOR_COLD = DesignTokens.Foundry.Ui.HEAT_COLD;
    private static final int HEAT_COLOR_HOT = DesignTokens.Foundry.Ui.HEAT_HOT;
    private static final int STRESS_COLOR_LOW = DesignTokens.Foundry.Ui.STRESS_LOW;
    private static final int STRESS_COLOR_HIGH = DesignTokens.Foundry.Ui.STRESS_HIGH;
    private static final int PURITY_COLOR_HIGH = DesignTokens.Foundry.Ui.PURITY;
    private static final int PURITY_COLOR_MED = 0xFFCCAA00; // Yellow-orange
    private static final int PURITY_COLOR_LOW = 0xFFCC4400; // Orange-red
    private static final int BAR_BACKGROUND = DesignTokens.Foundry.Ui.BAR_BACKGROUND;
    private static final float PURITY_WARNING_THRESHOLD = 0.85f;
    private static final float PURITY_CRITICAL_THRESHOLD = 0.7f;
    private static final float OXIDATION_WARNING_THRESHOLD = 0.3f;
    private static final float OXIDATION_CRITICAL_THRESHOLD = 0.6f;

    // Animation timing
    private long lastTickTime = 0;
    private boolean warningBlink = false;

    // UI Widgets
    private FluidTankWidget tankWidget;
    private AlloyPreviewWidget alloyWidget;

    // Fluid tank widget position
    private static final int TANK_WIDGET_X = 8;
    private static final int TANK_WIDGET_Y = 21 + UI_VERTICAL_OFFSET;
    private static final int TANK_WIDGET_WIDTH = 32;
    private static final int TANK_WIDGET_HEIGHT = 52;

    // Alloy preview widget position
    private static final int ALLOY_WIDGET_X = 116;
    private static final int ALLOY_WIDGET_Y = 21 + UI_VERTICAL_OFFSET;
    private static final int ALLOY_WIDGET_WIDTH = 18;
    private static final int ALLOY_WIDGET_HEIGHT = 30;

    public FoundryControllerScreen(FoundryControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 166 + UI_VERTICAL_OFFSET;
        this.titleLabelX = TITLE_LABEL_X;
        this.titleLabelY = TITLE_LABEL_Y;
        this.inventoryLabelX = INVENTORY_LABEL_X;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();

        // Create fluid tank widget
        this.tankWidget = new FluidTankWidget(
            leftPos + TANK_WIDGET_X,
            topPos + TANK_WIDGET_Y,
            TANK_WIDGET_WIDTH,
            TANK_WIDGET_HEIGHT,
            () -> this.menu
        );
        addRenderableWidget(tankWidget);

        // Create alloy preview widget
        this.alloyWidget = new AlloyPreviewWidget(
            leftPos + ALLOY_WIDGET_X,
            topPos + ALLOY_WIDGET_Y,
            ALLOY_WIDGET_WIDTH,
            ALLOY_WIDGET_HEIGHT,
            () -> this.menu
        );
        addRenderableWidget(alloyWidget);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderPanelBackground(graphics);

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

        // Purity bar (horizontal) - color changes based on purity level
        float purity = menu.getPurityPercent();
        renderHorizontalBar(graphics, PURITY_BAR_X, PURITY_BAR_Y, PURITY_BAR_WIDTH, PURITY_BAR_HEIGHT,
            purity, getPurityColor(purity));

        // Purity critical warning flash
        if (purity < PURITY_CRITICAL_THRESHOLD) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTickTime > 500) {
                lastTickTime = currentTime;
                warningBlink = !warningBlink;
            }
            if (warningBlink) {
                // Draw flashing border around purity bar
                int flashColor = 0x80FF0000; // Semi-transparent red
                graphics.fill(leftPos + PURITY_BAR_X - 1, topPos + PURITY_BAR_Y - 1,
                    leftPos + PURITY_BAR_X + PURITY_BAR_WIDTH + 1, topPos + PURITY_BAR_Y, flashColor);
                graphics.fill(leftPos + PURITY_BAR_X - 1, topPos + PURITY_BAR_Y + PURITY_BAR_HEIGHT,
                    leftPos + PURITY_BAR_X + PURITY_BAR_WIDTH + 1, topPos + PURITY_BAR_Y + PURITY_BAR_HEIGHT + 1, flashColor);
                graphics.fill(leftPos + PURITY_BAR_X - 1, topPos + PURITY_BAR_Y,
                    leftPos + PURITY_BAR_X, topPos + PURITY_BAR_Y + PURITY_BAR_HEIGHT, flashColor);
                graphics.fill(leftPos + PURITY_BAR_X + PURITY_BAR_WIDTH, topPos + PURITY_BAR_Y,
                    leftPos + PURITY_BAR_X + PURITY_BAR_WIDTH + 1, topPos + PURITY_BAR_Y + PURITY_BAR_HEIGHT, flashColor);
            }
        }

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

        // Note: Fluid mixing bar replaced by FluidTankWidget
        // Widgets are rendered automatically by addRenderableWidget
    }

    private void renderPanelBackground(GuiGraphics graphics) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, FRAME_OUTER);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, FRAME_INNER);

        int panelX = x + 4;
        int panelW = imageWidth - 8;
        int topPanelY = y + 4;
        int topPanelH = 72 + UI_VERTICAL_OFFSET;
        drawPanel(graphics, panelX, topPanelY, panelW, topPanelH, PANEL_TOP);

        int dividerY = y + 76 + UI_VERTICAL_OFFSET;
        graphics.fill(panelX, dividerY, panelX + panelW, dividerY + 2, FRAME_OUTER);

        int bottomPanelY = y + 78 + UI_VERTICAL_OFFSET;
        int bottomPanelH = (y + imageHeight - 4) - bottomPanelY;
        drawPanel(graphics, panelX, bottomPanelY, panelW, bottomPanelH, PANEL_BOTTOM);

        drawSlotBackgrounds(graphics);

        drawFrame(graphics, PROGRESS_X, PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT);
        drawFrame(graphics, FUEL_X, FUEL_Y, FUEL_WIDTH, FUEL_HEIGHT);
        drawFrame(graphics, HEAT_BAR_X, HEAT_BAR_Y, HEAT_BAR_WIDTH, HEAT_BAR_HEIGHT);
        drawFrame(graphics, STRESS_BAR_X, STRESS_BAR_Y, STRESS_BAR_WIDTH, STRESS_BAR_HEIGHT);
        drawFrame(graphics, PURITY_BAR_X, PURITY_BAR_Y, PURITY_BAR_WIDTH, PURITY_BAR_HEIGHT);
        // Note: Mixing bar frame removed - replaced by FluidTankWidget
        drawFrame(graphics, QUALITY_INDICATOR_X, QUALITY_INDICATOR_Y, QUALITY_INDICATOR_SIZE, QUALITY_INDICATOR_SIZE);
        drawFrame(graphics, RISK_INDICATOR_X, RISK_INDICATOR_Y, RISK_INDICATOR_SIZE, RISK_INDICATOR_SIZE);

        int statsPanelRight = x + STATS_TEXT_RIGHT;
        int statsPanelX = statsPanelRight - STATS_PANEL_WIDTH;
        drawPanel(graphics, statsPanelX, y + STATS_PANEL_Y, STATS_PANEL_WIDTH, STATS_PANEL_HEIGHT, PANEL_BOTTOM);
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height, int fillColor) {
        graphics.fill(x, y, x + width, y + height, PANEL_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fillColor);
    }

    private void drawFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        int x0 = leftPos + x;
        int y0 = topPos + y;
        graphics.fill(x0 - 1, y0 - 1, x0 + width + 1, y0, SLOT_BORDER);
        graphics.fill(x0 - 1, y0 + height, x0 + width + 1, y0 + height + 1, SLOT_BORDER);
        graphics.fill(x0 - 1, y0, x0, y0 + height, SLOT_BORDER);
        graphics.fill(x0 + width, y0, x0 + width + 1, y0 + height, SLOT_BORDER);
    }

    private void drawSlotBackgrounds(GuiGraphics graphics) {
        for (var slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            drawSlot(graphics, slot.x, slot.y);
        }
    }

    private void drawSlot(GuiGraphics graphics, int slotX, int slotY) {
        int x0 = leftPos + slotX - 1;
        int y0 = topPos + slotY - 1;
        graphics.fill(x0, y0, x0 + 18, y0 + 18, SLOT_BORDER);
        graphics.fill(x0 + 1, y0 + 1, x0 + 17, y0 + 17, SLOT_INNER);
        graphics.fill(x0 + 2, y0 + 2, x0 + 16, y0 + 16, SLOT_BG);
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

    private int getPurityColor(float purityPercent) {
        // High purity = green, medium = yellow, low = red
        if (purityPercent >= PURITY_WARNING_THRESHOLD) {
            return PURITY_COLOR_HIGH;
        } else if (purityPercent >= PURITY_CRITICAL_THRESHOLD) {
            // Interpolate from high to medium
            float t = (PURITY_WARNING_THRESHOLD - purityPercent) / (PURITY_WARNING_THRESHOLD - PURITY_CRITICAL_THRESHOLD);
            return interpolateColor(PURITY_COLOR_HIGH, PURITY_COLOR_MED, t);
        } else {
            // Interpolate from medium to low
            float t = (PURITY_CRITICAL_THRESHOLD - purityPercent) / PURITY_CRITICAL_THRESHOLD;
            return interpolateColor(PURITY_COLOR_MED, PURITY_COLOR_LOW, Math.min(1.0f, t));
        }
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
        graphics.drawString(fontObj, Objects.requireNonNull(this.title), this.titleLabelX, this.titleLabelY, LABEL_TEXT, false);
        graphics.drawString(fontObj, Objects.requireNonNull(this.playerInventoryTitle), this.inventoryLabelX, this.inventoryLabelY, LABEL_TEXT_MUTED, false);

        int statsY = STATS_TEXT_Y;

        // Temperature display
        int temp = menu.getFuelTemperature();
        int structHeat = menu.getStructureHeat();
        if (temp > 0) {
            String tempText = structHeat + "/" + temp + "C";
            graphics.drawString(fontObj, tempText, rightAlignedX(fontObj, tempText), statsY, LABEL_TEXT, false);
            statsY += fontObj.lineHeight + 2;
        }

        // Molten display
        int molten = menu.getMoltenAmount();
        int capacity = menu.getMoltenCapacity();
        if (capacity > 0) {
            String moltenText = molten + "/" + capacity + " mB";
            graphics.drawString(fontObj, moltenText, rightAlignedX(fontObj, moltenText), statsY, LABEL_TEXT, false);
            statsY += fontObj.lineHeight + 2;
        }

        // Structure damage indicator (if any)
        int damage = menu.getStructureDamage();
        if (damage > 0) {
            Component damageText = Component.translatable("gui.devmod.foundry.structure_damage", damage);
            graphics.drawString(fontObj, damageText, rightAlignedX(fontObj, damageText.getString()), statsY,
                DesignTokens.Foundry.Ui.DAMAGE_TEXT, false);
        }
    }

    private int rightAlignedX(@Nonnull net.minecraft.client.gui.Font font, @Nonnull String text) {
        return rightAlignedX(font, text, STATS_TEXT_RIGHT);
    }

    private int rightAlignedX(@Nonnull net.minecraft.client.gui.Font font, @Nonnull String text, int rightEdge) {
        return rightEdge - font.width(text);
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
            float purityFloat = menu.getPurityPercent();
            int purity = (int) (purityFloat * 100);
            tooltip.add(Component.literal(purity + "%")
                .withStyle(s -> s.withColor(getPurityColor(purityFloat))));
            int oxidation = (int) (menu.getOxidationPercent() * 100);
            if (oxidation > 0) {
                tooltip.add(Component.translatable("gui.devmod.foundry.oxidation", oxidation));
            }
            // Show impurity sources info
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("gui.devmod.foundry.impurity_sources")
                .withStyle(s -> s.withColor(0xFFAAAAAA)));
            tooltip.add(Component.translatable("gui.devmod.foundry.impurity_ore")
                .withStyle(s -> s.withColor(0xFF888888)));
            tooltip.add(Component.translatable("gui.devmod.foundry.impurity_raw")
                .withStyle(s -> s.withColor(0xFF888888)));
            // Show tips if purity is low
            if (purityFloat < PURITY_WARNING_THRESHOLD) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("gui.devmod.foundry.purity_tip_header")
                    .withStyle(s -> s.withColor(0xFF55FF55)));
                tooltip.add(Component.translatable("gui.devmod.foundry.purity_tip_flux")
                    .withStyle(s -> s.withColor(0xFF88FF88)));
                tooltip.add(Component.translatable("gui.devmod.foundry.purity_tip_rich")
                    .withStyle(s -> s.withColor(0xFF88FF88)));
            }
            if (purityFloat < PURITY_CRITICAL_THRESHOLD) {
                tooltip.add(Component.translatable("gui.devmod.foundry.purity_warning")
                    .withStyle(s -> s.withColor(DesignTokens.Foundry.Ui.INCIDENT_WARNING)));
            } else if (purityFloat < PURITY_WARNING_THRESHOLD) {
                tooltip.add(Component.translatable("gui.devmod.foundry.purity_warning")
                    .withStyle(s -> s.withColor(DesignTokens.Foundry.Ui.STRESS_WARNING)));
            }
            if (menu.getOxidationPercent() > OXIDATION_WARNING_THRESHOLD) {
                tooltip.add(Component.translatable("gui.devmod.foundry.oxidation_warning")
                    .withStyle(s -> s.withColor(DesignTokens.Foundry.Ui.INCIDENT_WARNING)));
            }
            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // Fluid tank widget tooltip
        if (tankWidget != null && tankWidget.getHoveredFluid() != null) {
            List<Component> tooltip = tankWidget.getHoveredFluidTooltip();
            if (!tooltip.isEmpty()) {
                graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }
        }

        // Alloy preview widget tooltip
        if (alloyWidget != null && alloyWidget.isMouseOver(mouseX, mouseY)) {
            List<Component> tooltip = alloyWidget.buildTooltipLines();
            if (!tooltip.isEmpty()) {
                graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }
        }
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        int actualX = leftPos + x;
        int actualY = topPos + y;
        return mouseX >= actualX && mouseX < actualX + width && mouseY >= actualY && mouseY < actualY + height;
    }
}
