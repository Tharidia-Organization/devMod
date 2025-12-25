package com.devmod.client.overlay;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.util.I18n;
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

import java.util.Objects;

/**
 * Loading overlay shown when creating an instance dimension.
 * Provides clear visual feedback during async operations.
 */
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
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Font font = Objects.requireNonNull(mc.font, "font");
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Update spinner
        spinnerAngle += deltaTracker.getGameTimeDeltaTicks() * 8;

        // Center panel
        int panelX = (screenWidth - PANEL_WIDTH) / 2;
        int panelY = (screenHeight - PANEL_HEIGHT) / 2;

        // Semi-transparent background overlay
        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);

        // Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, UIConstants.Border.DEFAULT());
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, UIConstants.Background.PANEL_SOLID());

        // Spinner
        int spinnerCenterX = panelX + 30;
        int spinnerCenterY = panelY + PANEL_HEIGHT / 2;
        int spinnerRadius = 12;

        for (int i = 0; i < 8; i++) {
            float angle = (float) Math.toRadians(spinnerAngle + i * 45);
            int dotX = spinnerCenterX + (int) (Math.cos(angle) * spinnerRadius);
            int dotY = spinnerCenterY + (int) (Math.sin(angle) * spinnerRadius);
            int alpha = 255 - (i * 28);
            int dotColor = (alpha << 24) | (UIConstants.Accent.BLUE() & 0x00FFFFFF);
            graphics.fill(dotX - 2, dotY - 2, dotX + 2, dotY + 2, dotColor);
        }

        // Title
        String title = I18n.translate("devmod.loading.preparing_quest").getString();
        graphics.drawString(font, "§l" + title, panelX + 55, panelY + 15, UIConstants.Text.PRIMARY());

        // Status message
        graphics.drawString(font, statusMessage, panelX + 55, panelY + 32, UIConstants.Text.SECONDARY());

        // Elapsed time
        long elapsed = System.currentTimeMillis() - startTime;
        String timeText = String.format("%.1fs", elapsed / 1000.0);
        graphics.drawString(font, timeText, panelX + 55, panelY + 50, UIConstants.Text.MUTED());

        // Hint
        String hint = I18n.translate("devmod.loading.please_wait").getString();
        graphics.drawCenteredString(font, "§8" + hint, panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT - 12, UIConstants.Text.MUTED());
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
