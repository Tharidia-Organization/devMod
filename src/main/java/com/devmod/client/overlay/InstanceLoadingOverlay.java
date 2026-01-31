package com.devmod.client.overlay;

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
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.util.I18n;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class InstanceLoadingOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "instance_loading_hud");

    // State
    private static boolean active = false;
    private static String statusMessage = "";
    private static long startTime = 0;
    private static float spinnerAngle = 0;

    // Layout
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 80;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.CROSSHAIR),
            Objects.requireNonNull(LAYER_ID),
            InstanceLoadingOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Font font = Objects.requireNonNull(mc.font, "font");
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Scale dimensions
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int sPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        int panelWidth = Math.min(sPanelWidth, UIScaleManager.getSafeWidth());
        int panelHeight = Math.min(sPanelHeight, UIScaleManager.getSafeHeight());

        // Update spinner
        spinnerAngle += deltaTracker.getGameTimeDeltaTicks() * 8;

        // Center panel
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = (screenHeight - panelHeight) / 2;
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        panelX = Math.max(safeLeft, Math.min(panelX, safeRight - panelWidth));
        panelY = Math.max(safeTop, Math.min(panelY, safeBottom - panelHeight));

        // Semi-transparent background overlay
        graphics.fill(0, 0, screenWidth, screenHeight,
            OverlayTheme.withAlpha(OverlayTheme.Utility.BLACK, OverlayTheme.Alpha.SUBTLE));

        // Panel background
        int sBorder = UIScaleManager.scale(2);
        graphics.fill(panelX - sBorder, panelY - sBorder, panelX + panelWidth + sBorder, panelY + panelHeight + sBorder, DesignTokens.Stroke.DEFAULT);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, DesignTokens.Background.PANEL_SOLID);

        // Spinner
        int spinnerCenterX = panelX + UIScaleManager.scale(30);
        int spinnerCenterY = panelY + panelHeight / 2;
        int spinnerRadius = UIScaleManager.scale(12);

        for (int i = 0; i < 8; i++) {
            float angle = (float) Math.toRadians(spinnerAngle + i * 45);
            int dotX = spinnerCenterX + (int) (Math.cos(angle) * spinnerRadius);
            int dotY = spinnerCenterY + (int) (Math.sin(angle) * spinnerRadius);
            int alpha = 255 - (i * 28);
            int dotColor = OverlayTheme.withAlpha(DesignTokens.Semantic.INFO, alpha);
            int sDotSize = UIScaleManager.scale(2);
            graphics.fill(dotX - sDotSize, dotY - sDotSize, dotX + sDotSize, dotY + sDotSize, dotColor);
        }

        // Title
        int textOffsetX = UIScaleManager.scale(55);
        int textX = panelX + textOffsetX;
        int contentRight = panelX + panelWidth - UIScaleManager.scale(10);
        int maxTextWidth = Math.max(0, contentRight - textX);
        String title = I18n.translate("devmod.loading.preparing_quest").getString();
        title = truncateToWidth(font, title, maxTextWidth);
        UIScaleManager.drawScaledString(graphics, font, "\u00A7l" + title, textX, panelY + UIScaleManager.scale(15), DesignTokens.Text.PRIMARY);

        // Status message
        String statusLine = truncateToWidth(font, statusMessage, maxTextWidth);
        UIScaleManager.drawScaledString(graphics, font, statusLine, textX, panelY + UIScaleManager.scale(32), DesignTokens.Text.SECONDARY);

        // Elapsed time
        long elapsed = System.currentTimeMillis() - startTime;
        String timeText = String.format("%.1fs", elapsed / 1000.0);
        String timeLine = truncateToWidth(font, timeText, maxTextWidth);
        UIScaleManager.drawScaledString(graphics, font, timeLine, textX, panelY + UIScaleManager.scale(50), DesignTokens.Text.MUTED);

        // Hint
        String hint = I18n.translate("devmod.loading.please_wait").getString();
        hint = truncateToWidth(font, hint, panelWidth - UIScaleManager.scale(12));
        UIScaleManager.drawScaledCenteredString(graphics, font, "\u00A78" + hint, panelX + panelWidth / 2, panelY + panelHeight - UIScaleManager.scale(12), DesignTokens.Text.MUTED);
    }

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        int allowed = Math.max(0, maxWidth - ellipsisWidth);
        if (allowed <= 0) {
            return "...";
        }
        return font.plainSubstrByWidth(text, allowed) + "...";
    }

    // === Public API ===

    public static void show(String message) {
        active = true;
        statusMessage = message;
        startTime = System.currentTimeMillis();
        spinnerAngle = 0;
    }

    public static void updateStatus(String message) {
        statusMessage = message;
    }

    public static void hide() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }
}
