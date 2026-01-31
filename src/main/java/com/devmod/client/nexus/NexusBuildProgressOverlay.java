package com.devmod.client.nexus;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.nexus.client.NexusClientCache;

/**
 * HUD overlay that shows Nexus hub build progress.
 * Displays a progress bar during foundation construction so users
 * know the game isn't frozen.
 *
 * <p>Design:
 * <ul>
 *   <li>Semi-transparent background panel</li>
 *   <li>Gradient progress bar (teal to blue)</li>
 *   <li>Step name and percentage text</li>
 *   <li>Fade out animation on completion</li>
 * </ul>
 */
public final class NexusBuildProgressOverlay implements LayeredDraw.Layer {

    public static final NexusBuildProgressOverlay INSTANCE = new NexusBuildProgressOverlay();

    private static final ResourceLocation OVERLAY_ID =
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "nexus_build_progress"));

    // Layout constants
    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 8;
    private static final int PANEL_PADDING = 12;
    private static final int PANEL_HEIGHT = 48;
    private static final int VERTICAL_OFFSET = 80; // Above hotbar

    // Animation
    private static final long FADE_OUT_DURATION_MS = 1500;
    private static final long HOLD_COMPLETE_MS = 500; // Hold at 100% before fading

    // Colors
    private static final int BG_COLOR = DesignTokens.NexusBuild.PANEL_BG;
    private static final int BAR_BG_COLOR = DesignTokens.NexusBuild.BAR_BG;
    private static final int BAR_FILL_START = DesignTokens.Palette.ACCENT_TEAL;
    private static final int BAR_FILL_END = DesignTokens.Palette.ACCENT_BLUE;
    private static final int TEXT_PRIMARY = DesignTokens.Palette.TEXT_PRIMARY;
    private static final int TEXT_SECONDARY = DesignTokens.Palette.TEXT_SECONDARY;

    // Smooth animation state
    private float displayedProgress = 0.0f;
    private static final float PROGRESS_LERP_SPEED = 0.15f;

    private NexusBuildProgressOverlay() {}

    /**
     * Registers the overlay with the GUI layer system.
     */
    public static void register(@Nonnull RegisterGuiLayersEvent event) {
        Objects.requireNonNull(event);
        event.registerAboveAll(Objects.requireNonNull(OVERLAY_ID), Objects.requireNonNull(INSTANCE));
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull DeltaTracker deltaTracker) {
        var progressOpt = NexusClientCache.INSTANCE.getBuildProgress();
        if (progressOpt.isEmpty()) {
            displayedProgress = 0.0f;
            return;
        }

        NexusClientCache.BuildProgress progress = progressOpt.get();

        // Calculate fade alpha for completion
        float alpha = 1.0f;
        if (progress.complete()) {
            long elapsed = progress.getElapsedMs();
            if (elapsed > HOLD_COMPLETE_MS + FADE_OUT_DURATION_MS) {
                // Fade complete - clear the progress
                NexusClientCache.INSTANCE.clearBuildProgress();
                displayedProgress = 0.0f;
                return;
            } else if (elapsed > HOLD_COMPLETE_MS) {
                // Fading out
                alpha = 1.0f - ((float) (elapsed - HOLD_COMPLETE_MS) / FADE_OUT_DURATION_MS);
            }
        }

        // Smooth progress animation
        float targetProgress = progress.getProgressPercent();
        displayedProgress = lerp(displayedProgress, targetProgress, PROGRESS_LERP_SPEED);

        // Ensure we reach 100% when complete
        if (progress.complete()) {
            displayedProgress = 1.0f;
        }

        Minecraft mc = Minecraft.getInstance();
        // Note: We intentionally render during loading screens
        // so users can see the Nexus build progress

        Font font = mc.font;
        if (font == null) {
            return;
        }

        // Update UIScaleManager for responsive scaling
        UIScaleManager.update();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Scaled layout values
        int sBarWidth = UIScaleManager.scale(BAR_WIDTH);
        int sBarHeight = UIScaleManager.scale(BAR_HEIGHT);
        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sPanelWidth = sBarWidth + sPanelPadding * 2;
        int sPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        int sVerticalOffset = UIScaleManager.scale(VERTICAL_OFFSET);

        // Panel position (centered, above hotbar)
        int panelX = (screenWidth - sPanelWidth) / 2;
        int panelY = screenHeight - sVerticalOffset - sPanelHeight;

        // Apply alpha to colors
        int bgWithAlpha = applyAlpha(BG_COLOR, alpha);
        int barBgWithAlpha = applyAlpha(BAR_BG_COLOR, alpha);
        int textPrimaryWithAlpha = applyAlpha(TEXT_PRIMARY, alpha);
        int textSecondaryWithAlpha = applyAlpha(TEXT_SECONDARY, alpha);

        // Draw panel background
        graphics.fill(panelX, panelY, panelX + sPanelWidth, panelY + sPanelHeight, bgWithAlpha);

        // Draw title text
        String title = "Building Nexus Hub...";
        if (progress.complete()) {
            title = "Nexus Hub Ready!";
        }
        int titleWidth = UIScaleManager.getScaledStringWidth(font, title);
        UIScaleManager.drawScaledString(graphics, font, title, panelX + (sPanelWidth - titleWidth) / 2, panelY + UIScaleManager.scale(8), textPrimaryWithAlpha, false);

        // Draw progress bar background
        int barX = panelX + sPanelPadding;
        int barY = panelY + UIScaleManager.scale(24);
        graphics.fill(barX, barY, barX + sBarWidth, barY + sBarHeight, barBgWithAlpha);

        // Draw progress bar fill with gradient
        int fillWidth = (int) (sBarWidth * displayedProgress);
        if (fillWidth > 0) {
            drawGradientBar(graphics, barX, barY, fillWidth, sBarHeight, BAR_FILL_START, BAR_FILL_END, alpha);
        }

        // Draw percentage and step name
        int percent = Math.round(displayedProgress * 100);
        String percentText = percent + "%";
        String rawStepName = progress.stepName();
        String stepText = rawStepName != null ? rawStepName : "";

        // Percentage on the right
        int percentWidth = UIScaleManager.getScaledStringWidth(font, percentText);
        UIScaleManager.drawScaledString(graphics, font, percentText, barX + sBarWidth - percentWidth, barY + sBarHeight + UIScaleManager.scale(4), textPrimaryWithAlpha, false);

        // Step name on the left
        if (!stepText.isEmpty()) {
            int maxStepWidth = Math.max(0, sBarWidth - percentWidth - UIScaleManager.scale(8));
            String displayStep = truncateToWidth(font, stepText, maxStepWidth);
            UIScaleManager.drawScaledString(graphics, font, Objects.requireNonNull(displayStep), barX, barY + sBarHeight + UIScaleManager.scale(4), textSecondaryWithAlpha, false);
        }
    }

    /**
     * Draws a horizontal gradient bar.
     */
    private void drawGradientBar(GuiGraphics graphics, int x, int y, int width, int height,
                                  int colorStart, int colorEnd, float alpha) {
        // Draw gradient by drawing thin vertical slices
        for (int i = 0; i < width; i++) {
            float t = (float) i / Math.max(1, width - 1);
            int color = lerpColor(colorStart, colorEnd, t);
            color = applyAlpha(color, alpha);
            graphics.fill(x + i, y, x + i + 1, y + height, color);
        }
    }

    /**
     * Linear interpolation between two values.
     */
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Linear interpolation between two colors.
     */
    private int lerpColor(int colorA, int colorB, float t) {
        int aA = (colorA >> 24) & 0xFF;
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;

        int aB = (colorB >> 24) & 0xFF;
        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;

        int a = (int) lerp(aA, aB, t);
        int r = (int) lerp(rA, rB, t);
        int g = (int) lerp(gA, gB, t);
        int b = (int) lerp(bA, bB, t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Applies alpha multiplier to a color.
     */
    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & DesignTokens.NexusBuild.RGB_MASK);
    }

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || UIScaleManager.getScaledStringWidth(font, text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int minChars = Math.min(4, text.length());
        String trimmed = text;
        while (UIScaleManager.getScaledStringWidth(font, trimmed + ellipsis) > maxWidth && trimmed.length() > minChars) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }
}
