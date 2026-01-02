package com.devmod.client.overlay;

import java.util.List;
import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.endurance.ClientCombatFlowCache;
import com.devmod.client.endurance.ClientCombatFlowCache.ActionAnnouncement;
import com.devmod.client.notification.NotificationSoundManager;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.endurance.ComboSystem.StyleRank;
import com.devmod.endurance.FlowStateTracker.FlowState;
import com.devmod.endurance.MomentumTracker.MomentumState;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;

/**
 * Animated Combat Flow HUD overlay.
 *
 * Displays:
 * - Combo counter with pulse animation
 * - Style rank with progress bar (D→SSS)
 * - Flow state indicator (STALE/FRESH/VIRTUOSO)
 * - Momentum meter with overdrive countdown
 * - Action announcements with pop-in effects
 *
 * Positioned in bottom-left corner, styled for intense combat feedback.
 */
public class CombatFlowHudOverlay implements LayeredDraw.Layer {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "combat_flow_hud");

    public static final CombatFlowHudOverlay INSTANCE = new CombatFlowHudOverlay();

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            (graphics, deltaTracker) -> INSTANCE.render(graphics, deltaTracker)
        );
    }

    // Position (bottom-left corner)
    private static final int MARGIN = 10;
    private static final int PANEL_WIDTH = 180;
    private static final int PANEL_HEIGHT = 120;

    // Visual constants - using OverlayTheme for consistency
    private static final int BG_COLOR = OverlayTheme.Panel.BG_STANDARD;
    private static final int BORDER_COLOR = OverlayTheme.Border.ACCENT;

    // Animation
    private float animationTick = 0;

    private CombatFlowHudOverlay() {}

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ClientCombatFlowCache cache = ClientCombatFlowCache.INSTANCE;

        // Play sounds for state changes BEFORE tickAnimations resets flags
        if (cache.isActive()) {
            playSoundsForStateChanges(cache);
        }

        // Tick animations (resets state change flags)
        cache.tickAnimations(deltaTracker.getGameTimeDeltaPartialTick(true));
        animationTick += deltaTracker.getGameTimeDeltaPartialTick(true);

        // Only render if active
        if (!cache.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        Font font = mc.font;
        int screenHeight = graphics.guiHeight();

        // Panel position (bottom-left)
        int x = MARGIN;
        int y = screenHeight - PANEL_HEIGHT - MARGIN - 60; // Above hotbar

        PoseStack pose = graphics.pose();
        pose.pushPose();

        // === BACKGROUND ===
        renderBackground(graphics, x, y);

        // === COMBO COUNTER ===
        renderComboCounter(graphics, font, cache, x, y);

        // === STYLE RANK ===
        renderStyleRank(graphics, font, cache, x, y + 35);

        // === FLOW STATE ===
        renderFlowState(graphics, font, cache, x, y + 58);

        // === MOMENTUM METER ===
        renderMomentumMeter(graphics, font, cache, x, y + 78);

        // === ACTION ANNOUNCEMENTS ===
        renderAnnouncements(graphics, font, cache, x + PANEL_WIDTH + 10, y);

        pose.popPose();
    }

    private void renderBackground(GuiGraphics graphics, int x, int y) {
        // Semi-transparent dark background
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BG_COLOR);

        // Border
        graphics.fill(x, y, x + PANEL_WIDTH, y + 1, BORDER_COLOR);
        graphics.fill(x, y + PANEL_HEIGHT - 1, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER_COLOR);
        graphics.fill(x, y, x + 1, y + PANEL_HEIGHT, BORDER_COLOR);
        graphics.fill(x + PANEL_WIDTH - 1, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER_COLOR);
    }

    private void renderComboCounter(GuiGraphics graphics, Font font, ClientCombatFlowCache cache, int x, int y) {
        int combo = cache.getCombo();
        float scale = cache.getComboScale();
        StyleRank rank = cache.getStyleRank();

        PoseStack pose = graphics.pose();
        pose.pushPose();

        // Center the combo number
        String comboText = String.valueOf(combo);
        int textWidth = font.width(comboText);

        // Scale animation
        int centerX = x + 40;
        int centerY = y + 18;
        pose.translate(centerX, centerY, 0);
        pose.scale(scale * 2.0f, scale * 2.0f, 1.0f);
        pose.translate(-textWidth / 2f, -font.lineHeight / 2f, 0);

        // Combo number with rank color
        int color = combo > 0 ? rank.color : 0x666666;
        graphics.drawString(font, comboText, 0, 0, color | 0xFF000000, true);

        pose.popPose();

        // "HITS" label
        if (combo > 0) {
            String hitsLabel = "HITS";
            graphics.drawString(font, hitsLabel, x + 70, y + 12, 0xFFAAAAAA, false);

            // Max combo indicator
            if (cache.getMaxCombo() > combo) {
                String maxLabel = "MAX: " + cache.getMaxCombo();
                graphics.drawString(font, maxLabel, x + 70, y + 22, 0xFF666666, false);
            }
        }
    }

    private void renderStyleRank(GuiGraphics graphics, Font font, ClientCombatFlowCache cache, int x, int y) {
        StyleRank rank = cache.getStyleRank();
        float progress = cache.getRankProgress();
        float flashAlpha = cache.getRankFlashAlpha();

        // Rank letter/name
        String rankText = rank.name();
        int rankColor = rank.color | 0xFF000000;

        // Flash effect on rank up
        if (flashAlpha > 0) {
            int flashColor = blendColor(rankColor, 0xFFFFFFFF, flashAlpha);
            graphics.drawString(font, rankText, x + 6, y, flashColor, true);
        } else {
            graphics.drawString(font, rankText, x + 6, y, rankColor, true);
        }

        // Rank name
        graphics.drawString(font, rank.displayName, x + 30, y, 0xFFCCCCCC, false);

        // Progress bar to next rank
        int barX = x + 6;
        int barY = y + 12;
        int barWidth = PANEL_WIDTH - 16;
        int barHeight = 4;

        // Background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF222222);

        // Progress fill
        int fillWidth = (int) (barWidth * progress);
        if (fillWidth > 0) {
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, rankColor);
        }

        // Glow effect when close to rank up
        if (progress > 0.8f) {
            float glow = (float) Math.sin(animationTick * 0.3) * 0.3f + 0.7f;
            int glowColor = blendColor(rankColor, 0xFFFFFFFF, glow * (progress - 0.8f) * 5f);
            graphics.fill(barX + fillWidth - 2, barY, barX + fillWidth, barY + barHeight, glowColor);
        }
    }

    private void renderFlowState(GuiGraphics graphics, Font font, ClientCombatFlowCache cache, int x, int y) {
        FlowState flow = cache.getFlowState();

        // Only show non-neutral states
        if (flow == FlowState.NEUTRAL) {
            // Show virtuoso progress instead
            float virtuosoProgress = cache.getVirtuosoProgress();
            if (virtuosoProgress > 0) {
                String label = "Variety: " + (int)(virtuosoProgress * 100) + "%";
                int color = virtuosoProgress >= 1.0f ? 0xFFFFAA00 : 0xFF888888;
                graphics.drawString(font, label, x + 6, y, color, false);
            }
            return;
        }

        // Flow state indicator
        int color = flow.color | 0xFF000000;
        String text = flow.displayName;

        // Pulsing effect for STALE (warning)
        if (flow == FlowState.STALE) {
            float pulse = (float) Math.sin(animationTick * 0.5) * 0.3f + 0.7f;
            color = blendColor(color, 0xFFFF0000, pulse);
        }

        // Rainbow-ish effect for VIRTUOSO
        if (cache.getVirtuosoProgress() >= 1.0f) {
            float hue = (animationTick * 0.02f) % 1.0f;
            color = hsvToRgb(hue, 0.8f, 1.0f) | 0xFF000000;
            text = "★ VIRTUOSO ★";
        }

        graphics.drawString(font, text, x + 6, y, color, true);

        // Stale risk bar
        float staleRisk = cache.getStaleRisk();
        if (staleRisk > 0 && flow != FlowState.STALE) {
            int barX = x + 90;
            int barY = y + 2;
            int barWidth = 80;
            int barHeight = 6;

            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF222222);
            int riskWidth = (int) (barWidth * staleRisk);
            int riskColor = staleRisk > 0.7f ? 0xFFFF4444 : 0xFFFFAA00;
            graphics.fill(barX, barY, barX + riskWidth, barY + barHeight, riskColor);
        }
    }

    private void renderMomentumMeter(GuiGraphics graphics, Font font, ClientCombatFlowCache cache, int x, int y) {
        MomentumState state = cache.getMomentumState();
        float momentum = cache.getMomentumPercent();
        boolean overdrive = cache.isInOverdrive();

        // Label
        String label = overdrive ? "OVERDRIVE" : "Momentum";
        int labelColor = state.color | 0xFF000000;

        if (overdrive) {
            // Pulsing overdrive text
            float pulse = cache.getMomentumPulse();
            labelColor = blendColor(0xFFFF00FF, 0xFFFFFFFF, pulse);
        }

        graphics.drawString(font, label, x + 6, y, labelColor, true);

        // Momentum bar
        int barX = x + 6;
        int barY = y + 12;
        int barWidth = PANEL_WIDTH - 16;
        int barHeight = 8;

        // Background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF111111);

        // Fill
        int fillWidth = (int) (barWidth * (momentum / 100f));
        int fillColor = state.color | 0xFF000000;

        if (overdrive) {
            // Animated rainbow fill during overdrive
            float pulse = cache.getMomentumPulse();
            float hue = (animationTick * 0.03f) % 1.0f;
            fillColor = hsvToRgb(hue, 0.9f, 1.0f) | 0xFF000000;

            // Full bar during overdrive
            fillWidth = barWidth;

            // Pulsing glow
            graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1,
                blendColor(fillColor, 0x00000000, 0.5f - pulse * 0.3f));
        }

        graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);

        // Overdrive countdown
        if (overdrive) {
            long remaining = cache.getOverdriveRemainingMs();
            String countdown = String.format("%.1fs", remaining / 1000f);
            int countdownX = x + PANEL_WIDTH - font.width(countdown) - 6;
            graphics.drawString(font, countdown, countdownX, y, 0xFFFFFFFF, true);
        }

        // Threshold markers
        renderThresholdMarker(graphics, barX, barY, barWidth, barHeight, 0.75f, 0xFF666666); // Heated
        renderThresholdMarker(graphics, barX, barY, barWidth, barHeight, 1.0f, 0xFFFF00FF);  // Overdrive
    }

    private void renderThresholdMarker(GuiGraphics graphics, int barX, int barY, int barWidth, int barHeight,
                                        float threshold, int color) {
        int markerX = barX + (int)(barWidth * threshold);
        graphics.fill(markerX - 1, barY - 1, markerX + 1, barY + barHeight + 1, color);
    }

    private void renderAnnouncements(GuiGraphics graphics, Font font, ClientCombatFlowCache cache, int x, int y) {
        List<ActionAnnouncement> announcements = cache.getAnnouncements();

        int offsetY = 0;
        for (int i = announcements.size() - 1; i >= 0; i--) {
            ActionAnnouncement ann = announcements.get(i);
            float alpha = ann.getAlpha();
            float progress = ann.getProgress();

            if (alpha <= 0) continue;

            // Slide in from right
            float slideIn = Math.min(1.0f, progress * 5f);
            int slideOffset = (int) ((1.0f - slideIn) * 50);

            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(slideOffset, 0, 0);

            int color = (((int)(alpha * 255)) << 24) | (ann.color() & 0x00FFFFFF);
            int subColor = (((int)(alpha * 180)) << 24) | 0x00CCCCCC;

            // Scale for important announcements
            if (ann.important()) {
                float scale = 1.0f + (1.0f - progress) * 0.3f;
                pose.translate(x, y + offsetY, 0);
                pose.scale(scale, scale, 1.0f);
                pose.translate(-x, -(y + offsetY), 0);
            }

            // Title
            graphics.drawString(font, ann.title(), x, y + offsetY, color, true);

            // Subtitle
            graphics.drawString(font, ann.subtitle(), x, y + offsetY + 10, subColor, false);

            pose.popPose();

            offsetY += 24;
        }
    }

    // === Color utilities ===

    private int blendColor(int color1, int color2, float factor) {
        factor = Math.max(0, Math.min(1, factor));
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    // === Sound Effects ===

    /**
     * Play sounds for state changes. Must be called BEFORE tickAnimations() resets flags.
     */
    private void playSoundsForStateChanges(ClientCombatFlowCache cache) {
        if (cache.wasComboJustIncreased()) {
            NotificationSoundManager.INSTANCE.play("combatflow.combo.hit",
                NotificationCategory.COMBAT, NotificationPriority.NORMAL);
        }
        if (cache.wasRankJustChanged()) {
            NotificationSoundManager.INSTANCE.play("combatflow.rank.up",
                NotificationCategory.COMBAT, NotificationPriority.HIGH);
        }
        if (cache.wasOverdriveJustStarted()) {
            NotificationSoundManager.INSTANCE.play("combatflow.overdrive",
                NotificationCategory.COMBAT, NotificationPriority.URGENT);
        }
    }
}
