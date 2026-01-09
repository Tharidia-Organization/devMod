package com.devmod.portal.client;

import java.util.Objects;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.portal.PortalColor;
import com.devmod.portal.network.PortalPreviewPayload;

/**
 * Client-side overlay showing portal destination preview when looking at a portal.
 * Displays a small info box with destination name, dimension, and distance.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PortalPreviewOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "portal_preview_hud");

    // Current preview state
    private static PortalPreviewPayload currentPreview = null;
    private static long previewExpiry = 0;

    // Visual constants
    private static final int BOX_WIDTH = 140;
    private static final int BOX_HEIGHT = 50;
    private static final int BOX_PADDING = 5;
    private static final int LINE_HEIGHT = 11;
    private static final int PREVIEW_DURATION_MS = 2000; // 2 seconds

    private PortalPreviewOverlay() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.CROSSHAIR),
            Objects.requireNonNull(LAYER_ID),
            PortalPreviewOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (currentPreview == null || System.currentTimeMillis() > previewExpiry) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Position in top-right corner
        int x = screenWidth - BOX_WIDTH - 10;
        int y = 10;

        // Get portal color
        PortalColor color = currentPreview.getColor();
        int colorRgb = (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
        int colorWithAlpha = DesignTokens.Portal.PREVIEW_COLOR_ALPHA | colorRgb;

        // Draw background
        graphics.fill(x - BOX_PADDING, y - BOX_PADDING,
            x + BOX_WIDTH + BOX_PADDING, y + BOX_HEIGHT + BOX_PADDING, DesignTokens.Portal.PREVIEW_BG);

        // Draw color indicator bar on left side
        graphics.fill(x - BOX_PADDING, y - BOX_PADDING,
            x - BOX_PADDING + 3, y + BOX_HEIGHT + BOX_PADDING, colorWithAlpha);

        // Draw destination name (header)
        String destName = currentPreview.destinationName();
        graphics.drawString(mc.font, destName, x, y, DesignTokens.Portal.PREVIEW_TEXT, false);

        // Draw dimension
        String dimText = "Dim: " + currentPreview.dimensionName();
        graphics.drawString(mc.font, dimText, x, y + LINE_HEIGHT, DesignTokens.Portal.PREVIEW_TEXT_DIM, false);

        // Draw distance if available
        if (currentPreview.hasDistance()) {
            String distText = "Distance: " + currentPreview.distance() + " blocks";
            graphics.drawString(mc.font, distText, x, y + LINE_HEIGHT * 2, DesignTokens.Portal.PREVIEW_TEXT_DIM, false);
        } else if (!currentPreview.isFixedDestination()) {
            // Cross-dimension
            graphics.drawString(mc.font, "Cross-dimension", x, y + LINE_HEIGHT * 2, DesignTokens.Portal.PREVIEW_TEXT_DIM, false);
        }

        // Draw portal type indicator
        String typeText = currentPreview.isFixedDestination() ? "[Zone Portal]" : "[Linked]";
        graphics.drawString(mc.font, typeText, x, y + LINE_HEIGHT * 3, colorWithAlpha, false);
    }

    // === Public API ===

    /**
     * Show a portal preview. Called when receiving a preview payload from the server.
     */
    public static void showPreview(PortalPreviewPayload payload) {
        currentPreview = payload;
        previewExpiry = System.currentTimeMillis() + PREVIEW_DURATION_MS;
    }

    /**
     * Hide the current preview immediately.
     */
    public static void hidePreview() {
        currentPreview = null;
        previewExpiry = 0;
    }

    /**
     * Returns true if a preview is currently being shown.
     */
    public static boolean isShowing() {
        return currentPreview != null && System.currentTimeMillis() <= previewExpiry;
    }
}
