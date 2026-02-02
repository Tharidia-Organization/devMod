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
import com.devmod.client.ui.core.UIScaleManager;
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

        // Update UIScaleManager for responsive scaling
        UIScaleManager.update();

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Scaled layout values
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sSectionSpacing = UIScaleManager.scale(SECTION_SPACING);
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, 11);

        int panelX = (screenWidth - sPanelWidth) / 2;
        int panelY = UIScaleManager.scale(40);

        // Calculate panel height based on content
        int contentHeight = calculateContentHeight(data, sLineHeight, sSectionSpacing);
        int panelHeight = contentHeight + sPanelPadding * 2;

        // Draw background
        graphics.fill(0, 0, screenWidth, screenHeight, DesignTokens.Background.OVERLAY); // Dim screen
        graphics.fill(panelX - 2, panelY - 2, panelX + sPanelWidth + 2, panelY + panelHeight + 2, COLOR_BORDER);
        graphics.fill(panelX, panelY, panelX + sPanelWidth, panelY + panelHeight, COLOR_BG);

        int y = panelY + sPanelPadding;

        // Wave header
        String waveTitle = Objects.requireNonNull(String.format("WAVE %d / %d", data.waveNumber(), data.totalWaves()));
        int titleWidth = UIScaleManager.getScaledStringWidth(font, waveTitle);
        UIScaleManager.drawScaledString(graphics, font, waveTitle, panelX + (sPanelWidth - titleWidth) / 2, y, COLOR_ACCENT, true);
        y += sLineHeight + UIScaleManager.scale(4);

        // Separator line
        int lineX = panelX + sPanelWidth / 4;
        int lineWidth = sPanelWidth / 2;
        graphics.fill(lineX, y, lineX + lineWidth, y + 1, COLOR_BORDER);
        y += sSectionSpacing;

        // Two-column layout: Threat + Mob Composition
        int leftColX = panelX + sPanelPadding;
        int rightColX = panelX + UIScaleManager.scale(100);
        int colY = y;

        // Threat panel (left column)
        renderThreatPanel(graphics, font, leftColX, colY, data.threatLevel());

        // Mob composition (right column)
        int mobsHeight = renderMobComposition(graphics, font, rightColX, colY, data, sLineHeight);
        y = colY + Math.max(UIScaleManager.scale(60), mobsHeight) + sSectionSpacing;

        // Modifiers
        if (!data.activeModifiers().isEmpty()) {
            y = renderModifiers(graphics, font, panelX + sPanelPadding, y, data.activeModifiers(), sLineHeight);
            y += sSectionSpacing;
        }

        // Warnings
        if (data.warnings() != null && !data.warnings().isEmpty()) {
            y = renderWarnings(graphics, font, panelX + sPanelPadding, y, data.warnings(), sLineHeight);
            y += sSectionSpacing;
        }

        // Objective
        if (data.objective() != null) {
            y = renderObjective(graphics, font, panelX + sPanelPadding, y, data.objective(), sLineHeight);
            y += sSectionSpacing;
        }

        // Countdown timer
        int ticksRemaining = wis.getPhaseTicksRemaining();
        int secondsRemaining = Math.max(0, ticksRemaining / 20);
        renderCountdown(graphics, font, panelX, y, sPanelWidth, sPanelPadding, secondsRemaining, sLineHeight);
    }

    private static int calculateContentHeight(WaveBriefingData data, int sLineHeight, int sSectionSpacing) {
        int height = 0;
        height += sLineHeight + UIScaleManager.scale(4); // Title
        height += sSectionSpacing; // Separator
        height += UIScaleManager.scale(60); // Threat + Mob composition area
        height += sSectionSpacing;

        if (!data.activeModifiers().isEmpty()) {
            height += sLineHeight + UIScaleManager.scale(8) + sSectionSpacing;
        }

        if (data.warnings() != null && !data.warnings().isEmpty()) {
            height += (sLineHeight + UIScaleManager.scale(2)) * data.warnings().size() + sSectionSpacing;
        }

        if (data.objective() != null) {
            height += sLineHeight * 2 + sSectionSpacing;
        }

        height += UIScaleManager.scale(60); // Countdown area
        return height;
    }

    private static void renderThreatPanel(GuiGraphics graphics, Font font, int x, int y,
                                           WaveBriefingData.ThreatLevel threat) {
        int panelWidth = UIScaleManager.scale(70);
        int panelHeight = UIScaleManager.scale(55);

        // Panel background
        graphics.fill(x, y, x + panelWidth, y + panelHeight, COLOR_PANEL);
        graphics.fill(x, y, x + panelWidth, y + 2, threat.getColor()); // Top accent

        // "THREAT" label
        String label = "THREAT";
        int labelWidth = UIScaleManager.getScaledStringWidth(font, label);
        UIScaleManager.drawScaledString(graphics, font, label, x + (panelWidth - labelWidth) / 2, y + UIScaleManager.scale(6), COLOR_TEXT_DIM, false);

        // Threat level
        String levelText = Objects.requireNonNull(threat.getDisplayName().toUpperCase(java.util.Locale.ROOT));
        int levelWidth = UIScaleManager.getScaledStringWidth(font, levelText);
        UIScaleManager.drawScaledString(graphics, font, levelText, x + (panelWidth - levelWidth) / 2, y + UIScaleManager.scale(20), threat.getColor(), true);

        // Icon based on threat
        String icon = switch (threat) {
            case LOW -> "✓";
            case MEDIUM -> "!";
            case HIGH -> "⚠";
            case EXTREME -> "☠";
        };
        int iconWidth = UIScaleManager.getScaledStringWidth(font, icon);
        UIScaleManager.drawScaledString(graphics, font, icon, x + (panelWidth - iconWidth) / 2, y + UIScaleManager.scale(36), threat.getColor(), true);
    }

    private static int renderMobComposition(GuiGraphics graphics, Font font, int x, int y,
                                             WaveBriefingData data, int lineHeight) {
        Font safeFont = Objects.requireNonNull(font);
        int startY = y;

        // Header
        UIScaleManager.drawScaledString(graphics, safeFont, "MOB COMPOSITION", x, y, COLOR_TEXT_DIM, false);
        y += lineHeight + UIScaleManager.scale(4);

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
            UIScaleManager.drawScaledString(graphics, safeFont, line, x, y, color, false);
            y += lineHeight + UIScaleManager.scale(2);
        }

        if (mobs.size() > mobLimit) {
            UIScaleManager.drawScaledString(graphics, safeFont, "... and " + (mobs.size() - mobLimit) + " more", x, y, COLOR_TEXT_DIM, false);
            y += lineHeight + UIScaleManager.scale(2);
        }

        // Total
        String total = String.format("Total: %d mobs (%d elite)", data.totalMobCount(), data.eliteCount());
        UIScaleManager.drawScaledString(graphics, safeFont, total, x, y, COLOR_ACCENT, false);
        y += lineHeight;

        return y - startY;
    }

    private static int renderModifiers(GuiGraphics graphics, Font font, int x, int y, Set<String> modifiers, int lineHeight) {
        Font safeFont = Objects.requireNonNull(font);
        UIScaleManager.drawScaledString(graphics, safeFont, "MODIFIERS:", x, y, COLOR_TEXT_DIM, false);
        y += lineHeight + UIScaleManager.scale(4);

        int modX = x;
        for (String modifier : modifiers) {
            String tag = "[" + modifier + "]";
            int tagWidth = UIScaleManager.getScaledStringWidth(safeFont, tag) + 8;

            // Draw tag background
            graphics.fill(modX, y - 1, modX + tagWidth, y + safeFont.lineHeight + 1, DesignTokens.Surface.LEVEL_1);
            UIScaleManager.drawScaledString(graphics, safeFont, tag, modX + 4, y, DesignTokens.Semantic.WARNING, false);

            modX += tagWidth + 4;
        }

        return y + lineHeight + UIScaleManager.scale(4);
    }

    private static int renderWarnings(GuiGraphics graphics, Font font, int x, int y, List<String> warnings, int lineHeight) {
        Font safeFont = Objects.requireNonNull(font);
        for (String warning : warnings) {
            UIScaleManager.drawScaledString(graphics, safeFont, "⚠ " + warning, x, y, DesignTokens.Semantic.ERROR, false);
            y += lineHeight + UIScaleManager.scale(2);
        }
        return y;
    }

    private static int renderObjective(GuiGraphics graphics, Font font, int x, int y,
                                        WaveBriefingData.ObjectiveInfo objective, int lineHeight) {
        Font safeFont = Objects.requireNonNull(font);
        UIScaleManager.drawScaledString(graphics, safeFont, "OBJECTIVE:", x, y, COLOR_TEXT_DIM, false);
        y += lineHeight + UIScaleManager.scale(2);
        UIScaleManager.drawScaledString(graphics, safeFont, objective.description(), x, y, COLOR_TEXT, false);
        y += lineHeight;
        return y;
    }

    private static void renderCountdown(GuiGraphics graphics, Font font, int panelX, int y,
                                          int panelWidth, int sPanelPadding, int seconds, int lineHeight) {
        Font safeFont = Objects.requireNonNull(font);

        // Separator
        int lineX = panelX + sPanelPadding * 2;
        int lineWidth = panelWidth - sPanelPadding * 4;
        graphics.fill(lineX, y, lineX + lineWidth, y + 2, COLOR_BORDER);
        y += UIScaleManager.scale(12);

        // "STARTING IN" label
        String label = "STARTING IN";
        int labelWidth = UIScaleManager.getScaledStringWidth(safeFont, label);
        UIScaleManager.drawScaledString(graphics, safeFont, label, panelX + (panelWidth - labelWidth) / 2, y, COLOR_TEXT_DIM, false);
        y += lineHeight + UIScaleManager.scale(8);

        // Large countdown number
        String countdownText = Objects.requireNonNull(String.valueOf(seconds));

        // Scale the countdown number (draw it larger)
        float scale = UIScaleManager.scaleF(2.5f);
        int scaledWidth = (int)(UIScaleManager.getScaledStringWidth(safeFont, countdownText) * scale);
        int countdownX = panelX + (panelWidth - scaledWidth) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(countdownX, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        UIScaleManager.drawScaledString(graphics, safeFont, countdownText, 0, 0, COLOR_COUNTDOWN, true);
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
