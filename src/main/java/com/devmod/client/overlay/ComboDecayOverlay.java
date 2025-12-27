package com.devmod.client.overlay;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.endurance.ComboSystem.StyleRank;

public class ComboDecayOverlay {

    private static final long DISPLAY_DURATION_MS = 1200;

    // Colors
    private static final int COLOR_COMBO_LOST = 0xFFFF4444;
    private static final int COLOR_RANK_DOWN = 0xFFFF0000;

    // State
    private static long showStartTime = 0;
    private static int lostCombo = 0;
    private static boolean isRankDown = false;
    @Nullable
    private static StyleRank previousRank = null;
    @Nullable
    private static StyleRank newRank = null;
    private static boolean soundPlayed = false;

    public static void registerOverlay(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.CROSSHAIR),
            Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "combo_decay")),
            (guiGraphics, deltaTracker) -> render(guiGraphics)
        );
    }

    /**
     * Show combo decay feedback.
     * @param combo The combo count that was lost
     * @param prevRankOrdinal Previous style rank ordinal
     * @param newRankOrdinal New style rank ordinal
     */
    public static void show(int combo, int prevRankOrdinal, int newRankOrdinal) {
        lostCombo = combo;
        previousRank = StyleRank.values()[Math.min(prevRankOrdinal, StyleRank.values().length - 1)];
        newRank = StyleRank.values()[Math.min(newRankOrdinal, StyleRank.values().length - 1)];
        isRankDown = newRankOrdinal < prevRankOrdinal;
        showStartTime = System.currentTimeMillis();
        soundPlayed = false;
    }

    private static void render(GuiGraphics graphics) {
        if (showStartTime == 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        long elapsed = System.currentTimeMillis() - showStartTime;
        if (elapsed > DISPLAY_DURATION_MS) {
            showStartTime = 0;
            return;
        }

        // Play sound
        if (!soundPlayed && mc.getSoundManager() != null) {
            if (isRankDown) {
                mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                    Objects.requireNonNull(SoundEvents.SHIELD_BREAK), 0.8f, 0.7f)));
            } else if (lostCombo >= 5) {
                mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(
                    Objects.requireNonNull(SoundEvents.ITEM_BREAK), 0.5f, 1.2f)));
            }
            soundPlayed = true;
        }

        Font font = Objects.requireNonNull(mc.font, "font");
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Calculate fade
        float progress = elapsed / (float) DISPLAY_DURATION_MS;
        float alpha = progress < 0.2f ? progress / 0.2f : (1.0f - (progress - 0.2f) / 0.8f);
        int alphaInt = (int) (alpha * 255);
        if (alphaInt <= 0) return;

        // Position (center-right, below combo counter area)
        int x = screenWidth - 120;
        int y = screenHeight / 2 + 40;

        // Shake effect for first 300ms
        int shakeX = 0;
        int shakeY = 0;
        if (elapsed < 300) {
            shakeX = (int) (Math.sin(elapsed * 0.05) * 3 * (1 - elapsed / 300.0));
            shakeY = (int) (Math.cos(elapsed * 0.07) * 2 * (1 - elapsed / 300.0));
        }

        // Red flash overlay (subtle, edges only)
        if (elapsed < 200 && isRankDown) {
            int flashAlpha = (int) ((1.0f - elapsed / 200.0f) * 60);
            int flashColor = (flashAlpha << 24) | 0xFF0000;
            // Left edge
            graphics.fill(0, 0, 30, screenHeight, flashColor);
            // Right edge
            graphics.fill(screenWidth - 30, 0, screenWidth, screenHeight, flashColor);
        }

        // Main text
        String mainText;
        int mainColor;
        if (isRankDown) {
            mainText = "RANK DOWN!";
            mainColor = (alphaInt << 24) | (COLOR_RANK_DOWN & 0x00FFFFFF);
        } else if (lostCombo >= 10) {
            mainText = "COMBO LOST!";
            mainColor = (alphaInt << 24) | (COLOR_COMBO_LOST & 0x00FFFFFF);
        } else {
            mainText = "Combo Reset";
            mainColor = (alphaInt << 24) | 0xFFAAAA;
        }

        // Draw shadow
        int shadowColor = ((int)(alphaInt * 0.5f) << 24) | 0x000000;
        graphics.drawString(font, mainText, x + shakeX + 1, y + shakeY + 1, shadowColor, false);
        graphics.drawString(font, mainText, x + shakeX, y + shakeY, mainColor, false);

        // Show lost combo count
        if (lostCombo > 0) {
            String comboText = "-" + lostCombo + " hits";
            int comboColor = ((int)(alphaInt * 0.8f) << 24) | 0xFFFFFF;
            graphics.drawString(font, comboText, x + shakeX, y + shakeY + 12, comboColor, false);
        }

        // Show rank change if applicable
        var prevRank = previousRank;
        var currNewRank = newRank;
        if (isRankDown && prevRank != null && currNewRank != null) {
            String rankText = prevRank.displayName + " → " + currNewRank.displayName;
            int rankColor = ((int)(alphaInt * 0.9f) << 24) | (currNewRank.color & 0x00FFFFFF);
            graphics.drawString(font, rankText, x + shakeX, y + shakeY + 24, rankColor, false);
        }
    }
}
