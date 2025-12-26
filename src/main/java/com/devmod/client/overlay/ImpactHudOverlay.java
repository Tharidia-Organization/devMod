package com.devmod.client.overlay;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.config.Config;
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
// Minecraft API methods are not annotated but never return null in practice

public class ImpactHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "impact_analysis");

    // === UI Colors (reference image style) ===
    private static final int PANEL_BG = 0xCC1A1A2E;           // Dark blue 80% opacity
    private static final int PANEL_BORDER = 0xFF3D5AFE;       // Electric blue
    private static final int PANEL_BORDER_GLOW = 0x553D5AFE;  // Glow border

    // === Dimensions ===
    private static final int PANEL_PADDING = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int SECTION_SPACING = 6;
    private static final int PANEL_GAP = 8;

    private static final ImpactHudContentBuilder.NumberFormat NUMBER_FORMAT =
        new ImpactHudContentBuilder.NumberFormat("%.2f", "%.2f");

    // === Toggle (initialized from config) ===
    private static boolean enabled = getConfigEnabled();

    // === Last rendered panel position (for crosshair hit-test) ===
    private static int lastPanelX = 0;
    private static int lastPanelY = 0;
    private static int lastPanelWidth = 0;
    private static int lastPanelHeight = 0;

    private static boolean getConfigEnabled() {
        try { return Config.IMPACT_HUD_ENABLED.get(); }
        catch (Exception e) { return true; }
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.CROSSHAIR),
            Objects.requireNonNull(LAYER_ID),
            ImpactHudOverlay::render
        );
    }

    /**
     * Main rendering method called every frame.
     * NeoForge 1.21: uses DeltaTracker instead of float partialTick
     */
    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!enabled) return;

        ImpactData data = ImpactData.get();
        if (data == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        Font font = mc.font;

        ImpactHudContentBuilder.HudSection mainSection =
            ImpactHudContentBuilder.buildMainSection(data, NUMBER_FORMAT);
        ImpactHudContentBuilder.HudSection modSection =
            ImpactHudContentBuilder.buildModSection(data, NUMBER_FORMAT).orElse(null);
        ImpactHudContentBuilder.HudSection historySection =
            ImpactHudContentBuilder.buildHistorySection(data, NUMBER_FORMAT).orElse(null);

        List<ImpactHudContentBuilder.HudSection> panels = new java.util.ArrayList<>();
        panels.add(mainSection);
        if (modSection != null) {
            panels.add(modSection);
        }
        if (historySection != null) {
            panels.add(historySection);
        }

        // Calculate panel dimensions
        int panelWidth = 260;
        int totalHeight = 0;
        for (ImpactHudContentBuilder.HudSection section : panels) {
            totalHeight += calculatePanelHeight(section);
        }
        if (panels.size() > 1) {
            totalHeight += PANEL_GAP * (panels.size() - 1);
        }

        // BUG-008 FIX: Position based on config setting
        int[] position = calculatePanelPosition(screenWidth, screenHeight, panelWidth, totalHeight);
        int panelX = position[0];
        int panelY = position[1];

        // Save position for hit-test
        lastPanelX = panelX;
        lastPanelY = panelY;
        lastPanelWidth = panelWidth;
        lastPanelHeight = totalHeight;

        // Check if crosshair (screen center) is over the panel
        int crosshairX = screenWidth / 2;
        int crosshairY = screenHeight / 2;
        boolean isObserving = isCrosshairOverPanel(crosshairX, crosshairY);

        // Update observation state
        data.setObserved(isObserving);

        float alpha = data.getRemainingAlpha();
        if (alpha <= 0.01f) return;

        int currentY = panelY;
        for (ImpactHudContentBuilder.HudSection section : panels) {
            renderPanel(graphics, font, section, panelX, currentY, panelWidth, alpha);
            currentY += calculatePanelHeight(section) + PANEL_GAP;
        }
    }

    /**
     * Calculates the panel position based on config setting.
     * BUG-008 FIX: Configurable HUD position.
     * @return int array [x, y] for panel position
     */
    private static int[] calculatePanelPosition(int screenWidth, int screenHeight, int panelWidth, int panelHeight) {
        Config.HudPosition pos = Config.IMPACT_HUD_POSITION.get();
        int offsetX = 10;
        int offsetY = 10;
        try {
            offsetX = Config.IMPACT_HUD_OFFSET_X.get();
            offsetY = Config.IMPACT_HUD_OFFSET_Y.get();
        } catch (Exception ignored) {}

        return switch (pos) {
            case TOP_RIGHT -> new int[] { screenWidth - panelWidth - offsetX, offsetY };
            case TOP_LEFT -> new int[] { offsetX, offsetY };
            case BOTTOM_RIGHT -> new int[] { screenWidth - panelWidth - offsetX, screenHeight - panelHeight - offsetY };
            case BOTTOM_LEFT -> new int[] { offsetX, screenHeight - panelHeight - offsetY };
            case CENTER_RIGHT -> new int[] { screenWidth - panelWidth - offsetX, (screenHeight - panelHeight) / 2 };
            case CENTER_LEFT -> new int[] { offsetX, (screenHeight - panelHeight) / 2 };
        };
    }

    /**
     * Checks if the crosshair is over the Impact HUD panel.
     */
    private static boolean isCrosshairOverPanel(int crosshairX, int crosshairY) {
        return crosshairX >= lastPanelX && crosshairX <= lastPanelX + lastPanelWidth &&
               crosshairY >= lastPanelY && crosshairY <= lastPanelY + lastPanelHeight;
    }

    private static void renderPanel(GuiGraphics g, Font font, ImpactHudContentBuilder.HudSection section,
                                    int x, int y, int width, float alpha) {
        int height = calculatePanelHeight(section);
        renderPanelBackground(g, x, y, width, height, alpha);

        int textX = x + PANEL_PADDING;
        int textY = y + PANEL_PADDING;

        renderSection(g, font, section, x, width, textX, textY, alpha);
    }

    private static void renderSection(GuiGraphics g, Font font, ImpactHudContentBuilder.HudSection section,
                                      int panelX, int panelWidth, int textX, int textY, float alpha) {
        g.drawString(Objects.requireNonNull(font), Objects.requireNonNull(section.title()), textX, textY,
            applyAlpha(ImpactHudContentBuilder.Colors.TITLE, alpha), false);
        textY += LINE_HEIGHT + spacingPixels(section.titleSpacing());

        if (section.drawSeparator()) {
            g.fill(panelX + 4, textY, panelX + panelWidth - 4, textY + 1,
                applyAlpha(PANEL_BORDER, alpha * 0.5f));
            textY += SECTION_SPACING;
        }

        for (ImpactHudContentBuilder.HudLine line : section.lines()) {
            renderLine(g, font, line, textX, textY, alpha);
            textY += LINE_HEIGHT + spacingPixels(line.spacingAfter());
        }
    }

    private static void renderLine(GuiGraphics g, Font font, ImpactHudContentBuilder.HudLine line,
                                   int x, int y, float alpha) {
        var safeFont = Objects.requireNonNull(font);
        if (line.hasShadow()) {
            g.drawString(safeFont, Objects.requireNonNull(line.text()), x + 1, y, applyAlpha(line.shadowColor(), alpha), false);
        }
        g.drawString(safeFont, Objects.requireNonNull(line.text()), x, y, applyAlpha(line.color(), alpha), false);
    }

    /**
     * Renders panel background with glow border.
     */
    private static void renderPanelBackground(GuiGraphics g, int x, int y, int width, int height, float alpha) {
        // Outer glow (optional, subtle effect)
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, applyAlpha(PANEL_BORDER_GLOW, alpha * 0.3f));

        // Main background
        g.fill(x, y, x + width, y + height, applyAlpha(PANEL_BG, alpha));

        // Border
        int borderColor = applyAlpha(PANEL_BORDER, alpha * 0.8f);
        g.fill(x, y, x + width, y + 1, borderColor);                    // Top
        g.fill(x, y + height - 1, x + width, y + height, borderColor);  // Bottom
        g.fill(x, y, x + 1, y + height, borderColor);                   // Left
        g.fill(x + width - 1, y, x + width, y + height, borderColor);   // Right
    }

    private static int calculatePanelHeight(ImpactHudContentBuilder.HudSection section) {
        int height = PANEL_PADDING * 2;

        height += LINE_HEIGHT + spacingPixels(section.titleSpacing());
        if (section.drawSeparator()) {
            height += SECTION_SPACING;
        }

        for (ImpactHudContentBuilder.HudLine line : section.lines()) {
            height += LINE_HEIGHT + spacingPixels(line.spacingAfter());
        }

        return height;
    }

    private static int spacingPixels(ImpactHudContentBuilder.Spacing spacing) {
        return switch (spacing) {
            case NONE -> 0;
            case SMALL -> 2;
            case SECTION -> SECTION_SPACING;
            case LARGE -> 4;
        };
    }

    /**
     * Applies alpha to an ARGB color.
     */
    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    // === Public API ===

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    /**
     * Returns the Y coordinate of the bottom of the last rendered panel.
     * Used by other overlays to position themselves below this one.
     */
    public static int getLastPanelBottom() {
        return lastPanelY + lastPanelHeight;
    }
}
