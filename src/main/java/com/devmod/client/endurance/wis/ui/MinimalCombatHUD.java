package com.devmod.client.endurance.wis.ui;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.endurance.wis.WaveBriefingData;
import com.devmod.client.endurance.wis.WaveIntelligenceManager;
import com.devmod.client.endurance.wis.WavePhase;
import com.devmod.client.endurance.wis.WaveTelemetryCollector;
import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Minimal transparent HUD displayed during combat phase.
 * Shows only essential real-time stats without cluttering the screen.
 *
 * Position is configurable via WaveIntelligenceManager.setHudPosition().
 * Fade state is managed per-instance in WaveIntelligenceManager (Fix #8: multiplayer safe).
 *
 * Layout (configurable corner, 20% opacity):
 * ┌─────────────────┐
 * │  WAVE 5         │
 * │  ────────────── │
 * │  12 / 27 killed │
 * │  DPS: 45.2      │
 * │  Combo: 15x     │
 * └─────────────────┘
 */
@OnlyIn(Dist.CLIENT)
public class MinimalCombatHUD {

    // Layout
    private static final int MARGIN = 10;
    private static final int PADDING = 8;
    private static final int MIN_WIDTH = 100;

    private MinimalCombatHUD() {}

    /**
     * Render the minimal combat HUD.
     * Should only be called during COMBAT phase.
     */
    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        WaveIntelligenceManager wis = WaveIntelligenceManager.INSTANCE;

        // Update fade using instance-based state (Fix #8: not static)
        wis.updateHudFade();
        float fadeProgress = wis.getHudFadeProgress();

        if (wis.getCurrentPhase() != WavePhase.COMBAT && fadeProgress <= 0f) {
            return;
        }

        WaveTelemetryCollector collector = wis.getCurrentCollector();
        if (collector == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Calculate opacity (20% base, modulated by fade)
        float baseOpacity = wis.getMinimalHudOpacity();
        float alpha = baseOpacity * fadeProgress;
        int alphaBits = (int)(alpha * 255) << 24;

        // Build HUD content
        String[] lines = buildHudLines(wis, collector);
        int maxWidth = MIN_WIDTH;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.width(Objects.requireNonNull(line)) + PADDING * 2);
        }

        int hudHeight = (font.lineHeight + 2) * lines.length + PADDING * 2;

        // Calculate position based on configured HUD position (Fix #2)
        int hudX;
        int hudY;
        WaveIntelligenceManager.HudPosition pos = wis.getHudPosition();
        switch (pos) {
            case TOP_LEFT -> {
                hudX = MARGIN;
                hudY = MARGIN;
            }
            case BOTTOM_LEFT -> {
                hudX = MARGIN;
                hudY = screenHeight - hudHeight - MARGIN - 50; // Above hotbar
            }
            case BOTTOM_RIGHT -> {
                hudX = screenWidth - maxWidth - MARGIN;
                hudY = screenHeight - hudHeight - MARGIN - 50;
            }
            default -> { // TOP_RIGHT (default)
                hudX = screenWidth - maxWidth - MARGIN;
                hudY = MARGIN;
            }
        }

        // Draw semi-transparent background
        int bgColor = (alphaBits & DesignTokens.Mask.ALPHA) | (DesignTokens.Bg.LEVEL_0 & DesignTokens.Mask.RGB);
        graphics.fill(hudX, hudY, hudX + maxWidth, hudY + hudHeight, bgColor);

        // Draw content
        int textColor = (alphaBits & DesignTokens.Mask.ALPHA) | (DesignTokens.Text.PRIMARY & DesignTokens.Mask.RGB);
        int accentColor = (alphaBits & DesignTokens.Mask.ALPHA) | (DesignTokens.Accent.PRIMARY & DesignTokens.Mask.RGB);
        int dimColor = (alphaBits & DesignTokens.Mask.ALPHA) | (DesignTokens.Text.SECONDARY & DesignTokens.Mask.RGB);

        int y = hudY + PADDING;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int color = i == 0 ? accentColor : (i == 1 ? dimColor : textColor);
            graphics.drawString(font, line, hudX + PADDING, y, color, false);
            y += font.lineHeight + 2;
        }
    }

    private static String[] buildHudLines(WaveIntelligenceManager wis, WaveTelemetryCollector collector) {
        int waveNumber = wis.getCurrentWaveNumber();
        int kills = collector.getTotalKills();
        float dps = collector.getDPS();
        int combo = collector.getCurrentCombo();
        int maxCombo = collector.getMaxCombo();

        // Get estimated total from briefing data if available
        int totalMobs = 0;
        WaveBriefingData briefingData = wis.getBriefingData();
        if (briefingData != null) {
            totalMobs = briefingData.totalMobCount();
        }

        String killsLine = totalMobs > 0
            ? String.format("%d / %d killed", kills, totalMobs)
            : String.format("%d killed", kills);

        String comboLine = combo > 0
            ? String.format("Combo: %dx", combo)
            : (maxCombo > 0 ? String.format("Best: %dx", maxCombo) : "");

        if (comboLine.isEmpty()) {
            return new String[] {
                "WAVE " + waveNumber,
                "──────────",
                killsLine,
                String.format("DPS: %.1f", dps)
            };
        } else {
            return new String[] {
                "WAVE " + waveNumber,
                "──────────",
                killsLine,
                String.format("DPS: %.1f", dps),
                comboLine
            };
        }
    }

    /**
     * Render additional stats (can be toggled).
     * Shows more detailed real-time metrics.
     */
    public static void renderExtended(GuiGraphics graphics, int screenWidth, int screenHeight) {
        WaveIntelligenceManager wis = WaveIntelligenceManager.INSTANCE;
        if (wis.getCurrentPhase() != WavePhase.COMBAT) return;

        WaveTelemetryCollector collector = wis.getCurrentCollector();
        if (collector == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        float alpha = wis.getMinimalHudOpacity() * 0.8f;
        int alphaBits = (int)(alpha * 255) << 24;

        // Extended stats (bottom-right)
        String[] extLines = {
            String.format("DMG: %.0f / %.0f", collector.getTotalDamageDealt(), collector.getTotalDamageTaken()),
            String.format("Crits: %d (%.0f%%)", collector.getCriticalHits(), collector.getCriticalHitRate() * 100),
            String.format("TTK: %.0fms", collector.getAverageTTK())
        };

        int maxWidth = 0;
        for (String line : extLines) {
            maxWidth = Math.max(maxWidth, font.width(Objects.requireNonNull(line)) + PADDING * 2);
        }

        int hudHeight = (font.lineHeight + 2) * extLines.length + PADDING * 2;
        int hudX = screenWidth - maxWidth - MARGIN;
        int hudY = screenHeight - hudHeight - MARGIN - 50; // Above hotbar

        // Draw background
        int bgColor = (alphaBits & DesignTokens.Mask.ALPHA) | (DesignTokens.Bg.LEVEL_0 & DesignTokens.Mask.RGB);
        graphics.fill(hudX, hudY, hudX + maxWidth, hudY + hudHeight, bgColor);

        // Draw lines
        int textColor = (alphaBits & DesignTokens.Mask.ALPHA) | (DesignTokens.Text.SECONDARY & DesignTokens.Mask.RGB);
        int y = hudY + PADDING;
        for (String line : extLines) {
            graphics.drawString(font, line, hudX + PADDING, y, textColor, false);
            y += font.lineHeight + 2;
        }
    }

    /**
     * Check if the HUD should be rendered.
     */
    public static boolean shouldRender() {
        WaveIntelligenceManager wis = WaveIntelligenceManager.INSTANCE;
        WavePhase phase = wis.getCurrentPhase();
        return phase == WavePhase.COMBAT ||
               (wis.getHudFadeProgress() > 0f && wis.getCurrentCollector() != null);
    }

    /**
     * Reset fade animation (call when phase changes).
     */
    public static void resetFade() {
        WaveIntelligenceManager.INSTANCE.resetHudFade();
    }
}
