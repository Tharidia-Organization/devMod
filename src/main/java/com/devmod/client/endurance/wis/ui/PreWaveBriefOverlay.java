package com.devmod.client.endurance.wis.ui;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.endurance.wis.WaveBriefingData;
import com.devmod.client.endurance.wis.WaveIntelligenceManager;
import com.devmod.client.endurance.wis.WavePhase;
import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Pre-wave briefing overlay displayed during the 10-second countdown.
 * Shows wave composition, threat level, modifiers, and player status.
 *
 * Layout:
 * ┌─────────────────────────────────────────────┐
 * │           WAVE 5 / 10                       │
 * │         ─────────────────                   │
 * │                                             │
 * │  ┌─────────┐ ┌─────────────────────────┐    │
 * │  │  THREAT │ │  MOB COMPOSITION        │    │
 * │  │   HIGH  │ │  • Zombie x15           │    │
 * │  │   🔥    │ │  • Skeleton x10         │    │
 * │  └─────────┘ │  • Elite Creeper x2     │    │
 * │              └─────────────────────────┘    │
 * │                                             │
 * │  MODIFIERS: [Swift] [Armored]               │
 * │                                             │
 * │  OBJECTIVE: Kill all 27 mobs                │
 * │                                             │
 * │        ══════════════════════               │
 * │              STARTING IN                    │
 * │                  7                          │
 * └─────────────────────────────────────────────┘
 */
@OnlyIn(Dist.CLIENT)
public class PreWaveBriefOverlay {

    // Colors - using DesignTokens
    private static final int COLOR_BG = DesignTokens.withAlpha(DesignTokens.Bg.LEVEL_0, DesignTokens.Alpha.A88);
    private static final int COLOR_PANEL = DesignTokens.withAlpha(DesignTokens.Surface.LEVEL_0, DesignTokens.Alpha.A80);
    private static final int COLOR_BORDER = DesignTokens.Stroke.DEFAULT;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_ACCENT = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_COUNTDOWN = DesignTokens.Overlay.Text.GOLD;

    // Layout
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_PADDING = 16;
    private static final int SECTION_SPACING = 12;

    private PreWaveBriefOverlay() {}

    /**
     * Render the pre-wave brief overlay.
     * Should only be called during PRE_BRIEF phase.
     */
    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        WaveIntelligenceManager wis = WaveIntelligenceManager.INSTANCE;
        if (wis.getCurrentPhase() != WavePhase.PRE_BRIEF) return;

        WaveBriefingData data = wis.getBriefingData();
        if (data == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int panelX = (screenWidth - PANEL_WIDTH) / 2;
        int panelY = 40;

        // Calculate panel height based on content
        int contentHeight = calculateContentHeight(data, font);
        int panelHeight = contentHeight + PANEL_PADDING * 2;

        // Draw background
        graphics.fill(0, 0, screenWidth, screenHeight, DesignTokens.Background.OVERLAY); // Dim screen
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + panelHeight + 2, COLOR_BORDER);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, COLOR_BG);

        int y = panelY + PANEL_PADDING;

        // Wave header
        String waveTitle = Objects.requireNonNull(String.format("WAVE %d / %d", data.waveNumber(), data.totalWaves()));
        int titleWidth = font.width(waveTitle);
        graphics.drawString(font, waveTitle, panelX + (PANEL_WIDTH - titleWidth) / 2, y, COLOR_ACCENT, true);
        y += font.lineHeight + 4;

        // Separator line
        int lineX = panelX + PANEL_WIDTH / 4;
        int lineWidth = PANEL_WIDTH / 2;
        graphics.fill(lineX, y, lineX + lineWidth, y + 1, COLOR_BORDER);
        y += SECTION_SPACING;

        // Two-column layout: Threat + Mob Composition
        int leftColX = panelX + PANEL_PADDING;
        int rightColX = panelX + 100;
        int colY = y;

        // Threat panel (left column)
        renderThreatPanel(graphics, font, leftColX, colY, data.threatLevel());

        // Mob composition (right column)
        int mobsHeight = renderMobComposition(graphics, font, rightColX, colY, data);
        y = colY + Math.max(60, mobsHeight) + SECTION_SPACING;

        // Modifiers
        if (!data.activeModifiers().isEmpty()) {
            y = renderModifiers(graphics, font, panelX + PANEL_PADDING, y, data.activeModifiers());
            y += SECTION_SPACING;
        }

        // Warnings
        if (data.warnings() != null && !data.warnings().isEmpty()) {
            y = renderWarnings(graphics, font, panelX + PANEL_PADDING, y, data.warnings());
            y += SECTION_SPACING;
        }

        // Objective
        if (data.objective() != null) {
            y = renderObjective(graphics, font, panelX + PANEL_PADDING, y, data.objective());
            y += SECTION_SPACING;
        }

        // Countdown timer
        int ticksRemaining = wis.getPhaseTicksRemaining();
        int secondsRemaining = Math.max(0, ticksRemaining / 20);
        renderCountdown(graphics, font, panelX, y, PANEL_WIDTH, secondsRemaining);
    }

    private static int calculateContentHeight(WaveBriefingData data, Font font) {
        int height = 0;
        height += font.lineHeight + 4; // Title
        height += SECTION_SPACING; // Separator
        height += 60; // Threat + Mob composition area
        height += SECTION_SPACING;

        if (!data.activeModifiers().isEmpty()) {
            height += font.lineHeight + 8 + SECTION_SPACING;
        }

        if (data.warnings() != null && !data.warnings().isEmpty()) {
            height += (font.lineHeight + 2) * data.warnings().size() + SECTION_SPACING;
        }

        if (data.objective() != null) {
            height += font.lineHeight * 2 + SECTION_SPACING;
        }

        height += 60; // Countdown area
        return height;
    }

    private static void renderThreatPanel(GuiGraphics graphics, Font font, int x, int y,
                                           WaveBriefingData.ThreatLevel threat) {
        int panelWidth = 70;
        int panelHeight = 55;

        // Panel background
        graphics.fill(x, y, x + panelWidth, y + panelHeight, COLOR_PANEL);
        graphics.fill(x, y, x + panelWidth, y + 2, threat.getColor()); // Top accent

        // "THREAT" label
        String label = "THREAT";
        int labelWidth = font.width(label);
        graphics.drawString(font, label, x + (panelWidth - labelWidth) / 2, y + 6, COLOR_TEXT_DIM, false);

        // Threat level
        String levelText = Objects.requireNonNull(threat.getDisplayName().toUpperCase(java.util.Locale.ROOT));
        int levelWidth = font.width(levelText);
        graphics.drawString(font, levelText, x + (panelWidth - levelWidth) / 2, y + 20, threat.getColor(), true);

        // Icon based on threat
        String icon = switch (threat) {
            case LOW -> "✓";
            case MEDIUM -> "!";
            case HIGH -> "⚠";
            case EXTREME -> "☠";
        };
        int iconWidth = font.width(icon);
        graphics.drawString(font, icon, x + (panelWidth - iconWidth) / 2, y + 36, threat.getColor(), true);
    }

    private static int renderMobComposition(GuiGraphics graphics, Font font, int x, int y,
                                             WaveBriefingData data) {
        Font safeFont = Objects.requireNonNull(font);
        int startY = y;

        // Header
        graphics.drawString(safeFont, "MOB COMPOSITION", x, y, COLOR_TEXT_DIM, false);
        y += safeFont.lineHeight + 4;

        // Mob list (Fix #6: configurable limit via WaveIntelligenceManager)
        List<WaveBriefingData.MobComposition> mobs = data.mobComposition();
        int mobLimit = WaveIntelligenceManager.INSTANCE.getBriefingMobListLimit();
        int shown = Math.min(mobs.size(), mobLimit);

        for (int i = 0; i < shown; i++) {
            WaveBriefingData.MobComposition mob = mobs.get(i);
            String prefix = mob.isBoss() ? "★ " : (mob.eliteCount() > 0 ? "◆ " : "• ");
            String line = prefix + mob.displayName() + " x" + mob.count();
            if (mob.eliteCount() > 0 && !mob.isBoss()) {
                line += " (" + mob.eliteCount() + " elite)";
            }

            int color = mob.isBoss() ? DesignTokens.Semantic.ERROR :
                        (mob.eliteCount() > 0 ? DesignTokens.Semantic.WARNING : COLOR_TEXT);
            graphics.drawString(safeFont, line, x, y, color, false);
            y += safeFont.lineHeight + 2;
        }

        if (mobs.size() > mobLimit) {
            graphics.drawString(safeFont, "... and " + (mobs.size() - mobLimit) + " more", x, y, COLOR_TEXT_DIM, false);
            y += safeFont.lineHeight + 2;
        }

        // Total
        String total = String.format("Total: %d mobs (%d elite)", data.totalMobCount(), data.eliteCount());
        graphics.drawString(safeFont, total, x, y, COLOR_ACCENT, false);
        y += safeFont.lineHeight;

        return y - startY;
    }

    private static int renderModifiers(GuiGraphics graphics, Font font, int x, int y, Set<String> modifiers) {
        Font safeFont = Objects.requireNonNull(font);
        graphics.drawString(safeFont, "MODIFIERS:", x, y, COLOR_TEXT_DIM, false);
        y += safeFont.lineHeight + 4;

        int modX = x;
        for (String modifier : modifiers) {
            String tag = "[" + modifier + "]";
            int tagWidth = safeFont.width(tag) + 8;

            // Draw tag background
            graphics.fill(modX, y - 1, modX + tagWidth, y + safeFont.lineHeight + 1, DesignTokens.Surface.LEVEL_1);
            graphics.drawString(safeFont, tag, modX + 4, y, DesignTokens.Semantic.WARNING, false);

            modX += tagWidth + 4;
        }

        return y + safeFont.lineHeight + 4;
    }

    private static int renderWarnings(GuiGraphics graphics, Font font, int x, int y, List<String> warnings) {
        Font safeFont = Objects.requireNonNull(font);
        for (String warning : warnings) {
            graphics.drawString(safeFont, "⚠ " + warning, x, y, DesignTokens.Semantic.ERROR, false);
            y += safeFont.lineHeight + 2;
        }
        return y;
    }

    private static int renderObjective(GuiGraphics graphics, Font font, int x, int y,
                                        WaveBriefingData.ObjectiveInfo objective) {
        Font safeFont = Objects.requireNonNull(font);
        graphics.drawString(safeFont, "OBJECTIVE:", x, y, COLOR_TEXT_DIM, false);
        y += safeFont.lineHeight + 2;
        graphics.drawString(safeFont, objective.description(), x, y, COLOR_TEXT, false);
        y += safeFont.lineHeight;
        return y;
    }

    private static void renderCountdown(GuiGraphics graphics, Font font, int panelX, int y,
                                          int panelWidth, int seconds) {
        Font safeFont = Objects.requireNonNull(font);

        // Separator
        int lineX = panelX + PANEL_PADDING * 2;
        int lineWidth = panelWidth - PANEL_PADDING * 4;
        graphics.fill(lineX, y, lineX + lineWidth, y + 2, COLOR_BORDER);
        y += 12;

        // "STARTING IN" label
        String label = "STARTING IN";
        int labelWidth = safeFont.width(label);
        graphics.drawString(safeFont, label, panelX + (panelWidth - labelWidth) / 2, y, COLOR_TEXT_DIM, false);
        y += safeFont.lineHeight + 8;

        // Large countdown number
        String countdownText = Objects.requireNonNull(String.valueOf(seconds));

        // Scale the countdown number (draw it larger)
        float scale = 2.5f;
        int scaledWidth = (int)(safeFont.width(countdownText) * scale);
        int countdownX = panelX + (panelWidth - scaledWidth) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(countdownX, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(safeFont, countdownText, 0, 0, COLOR_COUNTDOWN, true);
        graphics.pose().popPose();
    }

    /**
     * Check if overlay should be rendered.
     */
    public static boolean shouldRender() {
        return WaveIntelligenceManager.INSTANCE.getCurrentPhase() == WavePhase.PRE_BRIEF
            && WaveIntelligenceManager.INSTANCE.getBriefingData() != null;
    }
}
