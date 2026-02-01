package com.devmod.client.overlay;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.config.Config;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
// Minecraft API methods are not annotated but never return null in practice

public class ImpactHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "impact_analysis");

    // === UI Colors (using DesignTokens for consistency) ===
    private static final int PANEL_BG = DesignTokens.Bg.LEVEL_1;           // Standard 0xE0 alpha
    private static final int PANEL_BORDER = DesignTokens.Accent.PRIMARY;   // Cyan accent
    private static final int PANEL_BORDER_GLOW = DesignTokens.Accent.GLOW; // Glow border

    // === Dimensions ===
    private static final int PANEL_PADDING = OverlayTheme.Dimension.PADDING;
    private static final int LINE_HEIGHT = OverlayTheme.Dimension.LINE_HEIGHT_COMPACT;
    private static final int SECTION_SPACING = 6;
    private static final int PANEL_GAP = 8;
    private static final int ACCENT_BAR_WIDTH = 4;
    private static final long FRESH_HIT_PULSE_MS = 350;

    private static final ImpactHudContentBuilder.NumberFormat NUMBER_FORMAT =
        new ImpactHudContentBuilder.NumberFormat("%.2f", "%.2f");

    // === Toggle (initialized from config) ===
    private static boolean enabled = getConfigEnabled();

    // === Cached content (reduce per-frame allocations) ===
    @Nullable
    private static ImpactData lastData = null;
    private static int lastRevision = -1;
    @Nullable
    private static ImpactHudContentBuilder.HudSection cachedMainSection = null;
    @Nullable
    private static ImpactHudContentBuilder.HudSection cachedModSection = null;
    private static int cachedMainHeight = 0;
    private static int cachedModHeight = 0;

    private static final long HISTORY_CACHE_MS = 200;
    private static long lastHistoryBuild = 0;
    @Nullable
    private static ImpactData lastHistoryData = null;
    @Nullable
    private static ImpactHudContentBuilder.HudSection cachedHistorySection = null;
    private static int cachedHistoryHeight = 0;

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

        // Check controller display mode
        ImpactDisplayMode displayMode = ImpactHudController.INSTANCE.getDisplayMode();
        if (displayMode == ImpactDisplayMode.OFF || displayMode == ImpactDisplayMode.MINIMAL) {
            return; // 2D overlay only shows in DETAILED or ANALYSIS mode
        }

        ImpactData data = ImpactData.get();
        if (data == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        Font font = mc.font;

        ensureCachedSections(data);
        ImpactHudContentBuilder.HudSection mainSection =
            Objects.requireNonNull(cachedMainSection, "cachedMainSection");
        ImpactHudContentBuilder.HudSection modSection = cachedModSection;
        ImpactHudContentBuilder.HudSection historySection = getCachedHistorySection(data);

        // Calculate panel dimensions with responsive scaling
        UIScaleManager.update();
        int sPanelWidth = UIScaleManager.scale(260);
        int panelWidth = Math.min(sPanelWidth, UIScaleManager.getSafeWidth());
        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sLineHeight = UIScaleManager.scale(LINE_HEIGHT);
        int sSectionSpacing = UIScaleManager.scale(SECTION_SPACING);
        int sPanelGap = UIScaleManager.scale(PANEL_GAP);
        int sAccentBarWidth = UIScaleManager.scale(ACCENT_BAR_WIDTH);

        int mainHeight = calculatePanelHeight(mainSection, sPanelPadding, sLineHeight, sSectionSpacing);
        int modHeight = modSection != null
            ? calculatePanelHeight(modSection, sPanelPadding, sLineHeight, sSectionSpacing)
            : 0;
        int historyHeight = historySection != null
            ? calculatePanelHeight(historySection, sPanelPadding, sLineHeight, sSectionSpacing)
            : 0;
        int panelCount = 1 + (modSection != null ? 1 : 0) + (historySection != null ? 1 : 0);
        int totalHeight = mainHeight + modHeight + historyHeight
            + (panelCount > 1 ? sPanelGap * (panelCount - 1) : 0);

        // BUG-008 FIX: Position based on config setting
        int[] position = calculatePanelPosition(screenWidth, screenHeight, panelWidth, totalHeight);
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        int panelX = Math.max(safeLeft, Math.min(position[0], safeRight - panelWidth));
        int panelY = Math.max(safeTop, Math.min(position[1], safeBottom - totalHeight));

        cachedMainHeight = mainHeight;
        cachedModHeight = modHeight;
        cachedHistoryHeight = historyHeight;

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

        float pulse = getFreshHitPulse(data);
        float emphasis = Math.min(1.0f, (isObserving ? 0.35f : 0.0f) + pulse * 0.45f);

        int currentY = panelY;
        renderPanel(graphics, font, mainSection, panelX, currentY, panelWidth, mainHeight,
            sPanelPadding, sLineHeight, sSectionSpacing, sAccentBarWidth,
            alpha, data.getBodyPartColor(), emphasis);
        currentY += mainHeight;

        if (modSection != null) {
            currentY += sPanelGap;
            renderPanel(graphics, font, modSection, panelX, currentY, panelWidth, modHeight,
                sPanelPadding, sLineHeight, sSectionSpacing, sAccentBarWidth,
                alpha, null, 0f);
            currentY += modHeight;
        }

        if (historySection != null) {
            currentY += sPanelGap;
            renderPanel(graphics, font, historySection, panelX, currentY, panelWidth, historyHeight,
                sPanelPadding, sLineHeight, sSectionSpacing, sAccentBarWidth,
                alpha, null, 0f);
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
        } catch (Exception e) {
            DevMod.LOGGER.debug("[ImpactHudOverlay] Failed to read HUD offsets, using defaults", e);
        }

        int sOffsetX = UIScaleManager.scale(offsetX);
        int sOffsetY = UIScaleManager.scale(offsetY);
        return switch (pos) {
            case TOP_RIGHT -> new int[] { screenWidth - panelWidth - sOffsetX, sOffsetY };
            case TOP_LEFT -> new int[] { sOffsetX, sOffsetY };
            case BOTTOM_RIGHT -> new int[] { screenWidth - panelWidth - sOffsetX, screenHeight - panelHeight - sOffsetY };
            case BOTTOM_LEFT -> new int[] { sOffsetX, screenHeight - panelHeight - sOffsetY };
            case CENTER_RIGHT -> new int[] { screenWidth - panelWidth - sOffsetX, (screenHeight - panelHeight) / 2 };
            case CENTER_LEFT -> new int[] { sOffsetX, (screenHeight - panelHeight) / 2 };
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
                                    int x, int y, int width, int height,
                                    int sPanelPadding, int sLineHeight, int sSectionSpacing, int sAccentBarWidth,
                                    float alpha, @Nullable Integer accentColor, float emphasis) {
        renderPanelBackground(g, x, y, width, height, sAccentBarWidth, alpha, accentColor, emphasis);

        int textX = x + sPanelPadding + (accentColor != null ? sAccentBarWidth : 0);
        int textY = y + sPanelPadding;
        int contentWidth = Math.max(0, width - sPanelPadding * 2 - (accentColor != null ? sAccentBarWidth : 0));

        renderSection(g, font, section, x, width, textX, textY, contentWidth, sLineHeight, sSectionSpacing, alpha);
    }

    private static void renderSection(GuiGraphics g, Font font, ImpactHudContentBuilder.HudSection section,
                                      int panelX, int panelWidth, int textX, int textY, int contentWidth,
                                      int sLineHeight, int sSectionSpacing, float alpha) {
        drawClampedLine(g, font, section.title(), textX, textY,
            applyAlpha(ImpactHudContentBuilder.Colors.TITLE, alpha), false, contentWidth);
        textY += sLineHeight + spacingPixels(section.titleSpacing(), sSectionSpacing);

        if (section.drawSeparator()) {
            int inset = UIScaleManager.scale(4);
            g.fill(panelX + inset, textY, panelX + panelWidth - inset, textY + 1,
                applyAlpha(PANEL_BORDER, alpha * 0.5f));
            textY += sSectionSpacing;
        }

        for (ImpactHudContentBuilder.HudLine line : section.lines()) {
            renderLine(g, font, line, textX, textY, contentWidth, alpha);
            textY += sLineHeight + spacingPixels(line.spacingAfter(), sSectionSpacing);
        }
    }

    private static void renderLine(GuiGraphics g, Font font, ImpactHudContentBuilder.HudLine line,
                                   int x, int y, int maxWidth, float alpha) {
        var safeFont = Objects.requireNonNull(font);
        Component text = Objects.requireNonNull(line.text(), "line text");
        if (line.hasShadow()) {
            drawClampedLine(g, safeFont, text, x + 1, y, applyAlpha(line.shadowColor(), alpha), false, maxWidth);
        }
        drawClampedLine(g, safeFont, text, x, y, applyAlpha(line.color(), alpha), false, maxWidth);
    }

    private static void drawClampedLine(GuiGraphics g, Font font, Component text,
                                        int x, int y, int color, boolean shadow, int maxWidth) {
        if (maxWidth <= 0) return;
        var lines = font.split(text, maxWidth);
        FormattedCharSequence sequence = lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.get(0);
        UIScaleManager.drawScaledString(g, font, sequence, x, y, color, shadow);
    }

    /**
     * Renders panel background with glow border.
     */
    private static void renderPanelBackground(GuiGraphics g, int x, int y, int width, int height,
                                              int sAccentBarWidth, float alpha,
                                              @Nullable Integer accentColor, float emphasis) {
        float glowFactor = Math.min(1.0f, 0.22f + emphasis * 0.45f);
        float borderFactor = Math.min(1.0f, 0.65f + emphasis * 0.25f);
        float bgFactor = Math.min(1.0f, 0.9f + emphasis * 0.1f);

        // Outer glow (optional, subtle effect)
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, applyAlpha(PANEL_BORDER_GLOW, alpha * glowFactor));

        // Main background
        g.fill(x, y, x + width, y + height, applyAlpha(PANEL_BG, alpha * bgFactor));

        // Border
        int borderColor = applyAlpha(PANEL_BORDER, alpha * borderFactor);
        g.fill(x, y, x + width, y + 1, borderColor);                    // Top
        g.fill(x, y + height - 1, x + width, y + height, borderColor);  // Bottom
        g.fill(x, y, x + 1, y + height, borderColor);                   // Left
        g.fill(x + width - 1, y, x + width, y + height, borderColor);   // Right

        if (accentColor != null && sAccentBarWidth > 0) {
            int accent = applyAlpha(accentColor, alpha * (0.7f + emphasis * 0.3f));
            int accentX = x + 1;
            g.fill(accentX, y + 1, accentX + sAccentBarWidth, y + height - 1, accent);
        }
    }

    private static int calculatePanelHeight(ImpactHudContentBuilder.HudSection section,
                                            int sPanelPadding, int sLineHeight, int sSectionSpacing) {
        int height = sPanelPadding * 2;

        height += sLineHeight + spacingPixels(section.titleSpacing(), sSectionSpacing);
        if (section.drawSeparator()) {
            height += sSectionSpacing;
        }

        for (ImpactHudContentBuilder.HudLine line : section.lines()) {
            height += sLineHeight + spacingPixels(line.spacingAfter(), sSectionSpacing);
        }

        return height;
    }

    private static int spacingPixels(ImpactHudContentBuilder.Spacing spacing, int sSectionSpacing) {
        return switch (spacing) {
            case NONE -> 0;
            case SMALL -> UIScaleManager.scale(2);
            case SECTION -> sSectionSpacing;
            case LARGE -> UIScaleManager.scale(4);
        };
    }

    private static float getFreshHitPulse(ImpactData data) {
        long ageMs = System.currentTimeMillis() - data.getTimestamp();
        if (ageMs <= 0 || ageMs >= FRESH_HIT_PULSE_MS) {
            return 0f;
        }
        return 1.0f - (ageMs / (float) FRESH_HIT_PULSE_MS);
    }

    private static void ensureCachedSections(ImpactData data) {
        int revision = data.getRevision();
        if (data == lastData && revision == lastRevision && cachedMainSection != null) {
            return;
        }

        cachedMainSection = ImpactHudContentBuilder.buildMainSection(data, NUMBER_FORMAT);
        cachedModSection = ImpactHudContentBuilder.buildModSection(data, NUMBER_FORMAT).orElse(null);
        lastData = data;
        lastRevision = revision;
    }

    @Nullable
    private static ImpactHudContentBuilder.HudSection getCachedHistorySection(ImpactData data) {
        long now = System.currentTimeMillis();
        if (data != lastHistoryData || now - lastHistoryBuild >= HISTORY_CACHE_MS) {
            cachedHistorySection = ImpactHudContentBuilder.buildHistorySection(data, NUMBER_FORMAT).orElse(null);
            lastHistoryBuild = now;
            lastHistoryData = data;
        }
        return cachedHistorySection;
    }

    /**
     * Applies alpha to an ARGB color.
     */
    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
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
