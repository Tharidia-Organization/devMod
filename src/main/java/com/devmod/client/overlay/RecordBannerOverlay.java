package com.devmod.client.overlay;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;

/**
 * Record Banner Overlay - shows "NEW RECORD!" banner when player beats personal record.
 * TASK-008: P1 High Priority
 *
 * Features:
 * - Gold banner slides down from top
 * - Shows record type (wave, score, time, etc.)
 * - Particle sparkle effect
 * - Stays for 4 seconds then fades
 */
public class RecordBannerOverlay {

    private static final long DISPLAY_DURATION_MS = 4000;
    private static final long SLIDE_IN_MS = 300;
    private static final long FADE_OUT_MS = 500;

    // Banner colors
    private static final int BANNER_COLOR = 0xDD1A1A1A;
    private static final int GOLD_COLOR = 0xFFFFD700;
    private static final int GOLD_DARK = 0xFFDAA520;
    private static final int WHITE = 0xFFFFFFFF;

    // State
    private static long showStartTime = 0;
    private static String recordType = "";
    private static String recordValue = "";
    private static boolean isNewRecord = false;

    public static void registerOverlay(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.BOSS_OVERLAY),
            Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "record_banner")),
            (guiGraphics, deltaTracker) -> render(guiGraphics)
        );
    }

    /**
     * Show the record banner.
     * @param type Record type (e.g., "BEST WAVE", "HIGH SCORE", "FASTEST TIME")
     * @param value Record value (e.g., "Wave 15", "12,500 pts", "2:34")
     */
    public static void showRecord(String type, String value) {
        recordType = type;
        recordValue = value;
        showStartTime = System.currentTimeMillis();
        isNewRecord = true;
    }

    /**
     * Check if banner is currently visible.
     */
    public static boolean isVisible() {
        return isNewRecord && (System.currentTimeMillis() - showStartTime) < DISPLAY_DURATION_MS;
    }

    private static void render(GuiGraphics graphics) {
        if (!isNewRecord) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        long elapsed = System.currentTimeMillis() - showStartTime;
        if (elapsed > DISPLAY_DURATION_MS) {
            isNewRecord = false;
            return;
        }

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Calculate animation progress
        float slideProgress = Math.min(1.0f, elapsed / (float) SLIDE_IN_MS);
        float fadeProgress = elapsed > (DISPLAY_DURATION_MS - FADE_OUT_MS)
            ? 1.0f - ((elapsed - (DISPLAY_DURATION_MS - FADE_OUT_MS)) / (float) FADE_OUT_MS)
            : 1.0f;

        // Ease out for slide
        slideProgress = 1.0f - (1.0f - slideProgress) * (1.0f - slideProgress);

        int bannerHeight = 50;
        int bannerY = (int) (-bannerHeight + (bannerHeight + 10) * slideProgress);

        int alpha = (int) (fadeProgress * 255);
        if (alpha <= 0) return;

        // Draw banner background
        int bgColor = (alpha << 24) | (BANNER_COLOR & 0x00FFFFFF);
        graphics.fill(0, bannerY, screenWidth, bannerY + bannerHeight, bgColor);

        // Gold accent line at bottom
        int goldAlpha = (alpha << 24) | (GOLD_COLOR & 0x00FFFFFF);
        graphics.fill(0, bannerY + bannerHeight - 3, screenWidth, bannerY + bannerHeight, goldAlpha);

        // Draw "NEW RECORD!" title with gold color and pulsing
        String title = "★ NEW RECORD! ★";
        float pulse = (float) (1.0 + 0.1 * Math.sin(elapsed * 0.008));
        int titleWidth = font.width(title);
        int titleX = (screenWidth - titleWidth) / 2;
        int titleY = bannerY + 8 - (int) (pulse * 1.5f); // subtle bounce with pulse

        // Gold shadow
        int goldDarkAlpha = ((int)(alpha * 0.8f) << 24) | (GOLD_DARK & 0x00FFFFFF);
        graphics.drawString(font, title, titleX + 1, titleY + 1, goldDarkAlpha, false);

        // Main gold text
        int mainGold = (alpha << 24) | (GOLD_COLOR & 0x00FFFFFF);
        graphics.drawString(font, title, titleX, titleY, mainGold, false);

        // Draw record type
        int typeWidth = font.width(Objects.requireNonNull(recordType, "record type"));
        int typeX = (screenWidth - typeWidth) / 2;
        int typeY = bannerY + 22;
        int typeColor = (alpha << 24) | (WHITE & 0x00FFFFFF);
        graphics.drawString(font, recordType, typeX, typeY, typeColor, false);

        // Draw record value (larger, emphasized)
        String valueText = Objects.requireNonNull(recordValue, "record value");
        int valueWidth = font.width(valueText);
        int valueX = (screenWidth - valueWidth) / 2;
        int valueY = bannerY + 35;

        // Value with gold highlight
        graphics.drawString(font, valueText, valueX + 1, valueY + 1, goldDarkAlpha, false);
        graphics.drawString(font, valueText, valueX, valueY, mainGold, false);

        // Sparkle particles (simple gold dots that move)
        renderSparkles(graphics, screenWidth, bannerY, bannerHeight, elapsed, alpha);
    }

    private static void renderSparkles(GuiGraphics graphics, int screenWidth, int bannerY,
            int bannerHeight, long elapsed, int alpha) {
        // Simple sparkle effect - gold particles that float up
        int sparkleCount = 8;

        for (int i = 0; i < sparkleCount; i++) {
            // Each sparkle has different timing offset
            long offset = (i * 1000 / sparkleCount);
            long sparkleTime = (elapsed + offset) % 2000;

            if (sparkleTime < 1500) {
                float progress = sparkleTime / 1500.0f;

                // Position along banner
                int baseX = (int) ((screenWidth * (i + 0.5f)) / sparkleCount);
                int x = baseX + (int) (Math.sin(sparkleTime * 0.01 + i) * 10);
                int y = bannerY + bannerHeight - (int) (progress * (bannerHeight + 10));

                // Fade in and out
                float sparkleAlpha = progress < 0.2f ? progress / 0.2f
                    : progress > 0.8f ? (1.0f - progress) / 0.2f
                    : 1.0f;

                int sAlpha = (int) (sparkleAlpha * alpha * 0.8f);
                if (sAlpha > 0) {
                    int sparkleColor = (sAlpha << 24) | (GOLD_COLOR & 0x00FFFFFF);
                    // Draw small sparkle (2x2 pixel)
                    graphics.fill(x, y, x + 2, y + 2, sparkleColor);
                }
            }
        }
    }
}
