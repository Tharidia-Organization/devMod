package com.devmod.client.overlay;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

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
import com.devmod.client.compat.mods.epicfight.ClientEpicFightCache;
import com.devmod.client.endurance.ClientCombatFlowCache;
import com.devmod.client.endurance.ClientCombatFlowCache.ActionAnnouncement;
import com.devmod.client.notification.NotificationSoundManager;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.compat.mods.epicfight.EpicFightCompat;
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
    private static final int PANEL_HEIGHT_WITH_EPICFIGHT = 170; // Extra height for Epic Fight widget

    // Visual constants - using OverlayTheme for consistency
    private static final int BG_COLOR = OverlayTheme.Panel.BG_STANDARD;
    private static final int BORDER_COLOR = OverlayTheme.Border.ACCENT;

    // Animation
    private float animationTick = 0;

    private CombatFlowHudOverlay() {}

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull DeltaTracker deltaTracker) {
        UIScaleManager.update();
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

        Font font = Objects.requireNonNull(mc.font, "Font cannot be null");
        int screenHeight = graphics.guiHeight();

        // Scale dimensions
        int sMargin = UIScaleManager.scale(MARGIN);
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int sPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        int sPanelHeightEpicFight = UIScaleManager.scale(PANEL_HEIGHT_WITH_EPICFIGHT);

        // Panel position (bottom-left)
        boolean epicFightActive = ClientEpicFightCache.INSTANCE.isActive();
        int panelHeight = epicFightActive ? sPanelHeightEpicFight : sPanelHeight;
        int x = sMargin;
        int y = screenHeight - panelHeight - sMargin - UIScaleManager.scale(60); // Above hotbar

        PoseStack pose = graphics.pose();
        pose.pushPose();

        // === BACKGROUND ===
        renderBackground(graphics, x, y, panelHeight);

        // === COMBO COUNTER ===
        renderComboCounter(graphics, font, cache, x, y);

        // === STYLE RANK ===
        renderStyleRank(graphics, font, cache, x, y + UIScaleManager.scale(35));

        // === FLOW STATE ===
        renderFlowState(graphics, font, cache, x, y + UIScaleManager.scale(58));

        // === MOMENTUM METER ===
        renderMomentumMeter(graphics, font, cache, x, y + UIScaleManager.scale(78));

        // === EPIC FIGHT INTEGRATION (if active) ===
        if (epicFightActive) {
            renderEpicFightSection(graphics, font, x, y + UIScaleManager.scale(100));
        }

        // === ACTION ANNOUNCEMENTS ===
        renderAnnouncements(graphics, font, cache, x + sPanelWidth + UIScaleManager.scale(10), y);

        pose.popPose();
    }

    private void renderBackground(@Nonnull GuiGraphics graphics, int x, int y, int panelHeight) {
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        // Semi-transparent dark background
        graphics.fill(x, y, x + sPanelWidth, y + panelHeight, BG_COLOR);

        // Border
        graphics.fill(x, y, x + sPanelWidth, y + 1, BORDER_COLOR);
        graphics.fill(x, y + panelHeight - 1, x + sPanelWidth, y + panelHeight, BORDER_COLOR);
        graphics.fill(x, y, x + 1, y + panelHeight, BORDER_COLOR);
        graphics.fill(x + sPanelWidth - 1, y, x + sPanelWidth, y + panelHeight, BORDER_COLOR);
    }

    private void renderComboCounter(@Nonnull GuiGraphics graphics, @Nonnull Font font, @Nonnull ClientCombatFlowCache cache, int x, int y) {
        int combo = cache.getCombo();
        float scale = cache.getComboScale();
        StyleRank rank = cache.getStyleRank();
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);

        PoseStack pose = graphics.pose();
        pose.pushPose();

        // Center the combo number
        String comboText = Objects.requireNonNull(String.valueOf(combo));
        int textWidth = UIScaleManager.getScaledStringWidth(font, comboText);

        // Scale animation
        int centerX = x + UIScaleManager.scale(40);
        int centerY = y + UIScaleManager.scale(18);
        pose.translate(centerX, centerY, 0);
        pose.scale(scale * 2.0f, scale * 2.0f, 1.0f);
        pose.translate(-textWidth / 2f, -lineHeight / 2f, 0);

        // Combo number with rank color
        int color = combo > 0
            ? OverlayTheme.withAlpha(rank.getColor(), DesignTokens.Alpha.A100)
            : OverlayTheme.Neutral.N650;
        UIScaleManager.drawScaledString(graphics, font, comboText, 0, 0, color, true);

        pose.popPose();

        // "HITS" label
        if (combo > 0) {
            String hitsLabel = "HITS";
            UIScaleManager.drawScaledString(graphics, font, hitsLabel, x + UIScaleManager.scale(70), y + UIScaleManager.scale(12), OverlayTheme.Text.MUTED, false);

            // Max combo indicator
            if (cache.getMaxCombo() > combo) {
                String maxLabel = "MAX: " + cache.getMaxCombo();
                UIScaleManager.drawScaledString(graphics, font, maxLabel, x + UIScaleManager.scale(70), y + UIScaleManager.scale(22), OverlayTheme.Neutral.N650, false);
            }
        }
    }

    private void renderStyleRank(@Nonnull GuiGraphics graphics, @Nonnull Font font, @Nonnull ClientCombatFlowCache cache, int x, int y) {
        StyleRank rank = cache.getStyleRank();
        float progress = cache.getRankProgress();
        float flashAlpha = cache.getRankFlashAlpha();
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);

        // Rank letter/name
        String rankText = rank.name();
        int rankColor = OverlayTheme.withAlpha(rank.getColor(), DesignTokens.Alpha.A100);

        // Flash effect on rank up
        if (flashAlpha > 0) {
            int flashColor = blendColor(rankColor, OverlayTheme.Utility.WHITE, flashAlpha);
            UIScaleManager.drawScaledString(graphics, font, rankText, x + UIScaleManager.scale(6), y, flashColor, true);
        } else {
            UIScaleManager.drawScaledString(graphics, font, rankText, x + UIScaleManager.scale(6), y, rankColor, true);
        }

        // Rank name
        UIScaleManager.drawScaledString(graphics, font, rank.getDisplayName(), x + UIScaleManager.scale(30), y, OverlayTheme.Neutral.N450, false);

        // Progress bar to next rank
        int barX = x + UIScaleManager.scale(6);
        int barY = y + UIScaleManager.scale(12);
        int barWidth = sPanelWidth - UIScaleManager.scale(16);
        int barHeight = UIScaleManager.scale(4);

        // Background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, OverlayTheme.Neutral.N840);

        // Progress fill
        int fillWidth = (int) (barWidth * progress);
        if (fillWidth > 0) {
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, rankColor);
        }

        // Glow effect when close to rank up
        if (progress > 0.8f) {
            float glow = (float) Math.sin(animationTick * 0.3) * 0.3f + 0.7f;
            int glowColor = blendColor(rankColor, OverlayTheme.Utility.WHITE, glow * (progress - 0.8f) * 5f);
            graphics.fill(barX + fillWidth - UIScaleManager.scale(2), barY, barX + fillWidth, barY + barHeight, glowColor);
        }
    }

    private void renderFlowState(@Nonnull GuiGraphics graphics, @Nonnull Font font, @Nonnull ClientCombatFlowCache cache, int x, int y) {
        FlowState flow = cache.getFlowState();

        // Only show non-neutral states
        if (flow == FlowState.NEUTRAL) {
            // Show virtuoso progress instead
            float virtuosoProgress = cache.getVirtuosoProgress();
            if (virtuosoProgress > 0) {
                String label = "Variety: " + (int)(virtuosoProgress * 100) + "%";
                int color = virtuosoProgress >= 1.0f ? OverlayTheme.Momentum.HEATED : OverlayTheme.Text.HINT;
                UIScaleManager.drawScaledString(graphics, font, label, x + UIScaleManager.scale(6), y, color, false);
            }
            return;
        }

        // Flow state indicator
        int color = OverlayTheme.withAlpha(flow.getColor(), DesignTokens.Alpha.A100);
        String text = flow.getDisplayName();

        // Pulsing effect for STALE (warning)
        if (flow == FlowState.STALE) {
            float pulse = (float) Math.sin(animationTick * 0.5) * 0.3f + 0.7f;
            color = blendColor(color, OverlayTheme.Momentum.STAGNANT, pulse);
        }

        // Rainbow-ish effect for VIRTUOSO
        if (cache.getVirtuosoProgress() >= 1.0f) {
            float hue = (animationTick * 0.02f) % 1.0f;
            color = hsvToRgb(hue, 0.8f, 1.0f);
            text = "★ VIRTUOSO ★";
        }

        UIScaleManager.drawScaledString(graphics, font, text, x + UIScaleManager.scale(6), y, color, true);

        // Stale risk bar
        float staleRisk = cache.getStaleRisk();
        if (staleRisk > 0 && flow != FlowState.STALE) {
            int barX = x + UIScaleManager.scale(90);
            int barY = y + UIScaleManager.scale(2);
            int barWidth = UIScaleManager.scale(80);
            int barHeight = UIScaleManager.scale(6);

            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, OverlayTheme.Neutral.N840);
            int riskWidth = (int) (barWidth * staleRisk);
            int riskColor = staleRisk > 0.7f ? OverlayTheme.Momentum.STAGNANT : OverlayTheme.Momentum.HEATED;
            graphics.fill(barX, barY, barX + riskWidth, barY + barHeight, riskColor);
        }
    }

    private void renderMomentumMeter(@Nonnull GuiGraphics graphics, @Nonnull Font font, @Nonnull ClientCombatFlowCache cache, int x, int y) {
        MomentumState state = cache.getMomentumState();
        float momentum = cache.getMomentumPercent();
        boolean overdrive = cache.isInOverdrive();
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);

        // Label
        String label = overdrive ? "OVERDRIVE" : "Momentum";
        int labelColor = OverlayTheme.withAlpha(state.getColor(), DesignTokens.Alpha.A100);

        if (overdrive) {
            // Pulsing overdrive text
            float pulse = cache.getMomentumPulse();
            labelColor = blendColor(OverlayTheme.Momentum.OVERDRIVE, OverlayTheme.Utility.WHITE, pulse);
        }

        UIScaleManager.drawScaledString(graphics, font, label, x + UIScaleManager.scale(6), y, labelColor, true);

        // Momentum bar
        int barX = x + UIScaleManager.scale(6);
        int barY = y + UIScaleManager.scale(12);
        int barWidth = sPanelWidth - UIScaleManager.scale(16);
        int barHeight = UIScaleManager.scale(8);

        // Background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, OverlayTheme.Neutral.N920);

        // Fill
        int fillWidth = (int) (barWidth * (momentum / 100f));
        int fillColor = OverlayTheme.withAlpha(state.getColor(), DesignTokens.Alpha.A100);

        if (overdrive) {
            // Animated rainbow fill during overdrive
            float pulse = cache.getMomentumPulse();
            float hue = (animationTick * 0.03f) % 1.0f;
            fillColor = hsvToRgb(hue, 0.9f, 1.0f);

            // Full bar during overdrive
            fillWidth = barWidth;

            // Pulsing glow
            graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1,
                blendColor(fillColor, DesignTokens.Mask.NONE, 0.5f - pulse * 0.3f));
        }

        graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);

        // Overdrive countdown
        if (overdrive) {
            long remaining = cache.getOverdriveRemainingMs();
            String countdown = Objects.requireNonNull(String.format("%.1fs", remaining / 1000f));
            int countdownX = x + sPanelWidth - UIScaleManager.getScaledStringWidth(font, countdown) - UIScaleManager.scale(6);
            UIScaleManager.drawScaledString(graphics, font, countdown, countdownX, y, OverlayTheme.Text.PRIMARY, true);
        }

        // Threshold markers
        renderThresholdMarker(graphics, barX, barY, barWidth, barHeight, 0.75f, OverlayTheme.Neutral.N650); // Heated
        renderThresholdMarker(graphics, barX, barY, barWidth, barHeight, 1.0f, OverlayTheme.Momentum.OVERDRIVE); // Overdrive
    }

    private void renderThresholdMarker(@Nonnull GuiGraphics graphics, int barX, int barY, int barWidth, int barHeight,
                                        float threshold, int color) {
        int markerX = barX + (int)(barWidth * threshold);
        graphics.fill(markerX - 1, barY - 1, markerX + 1, barY + barHeight + 1, color);
    }

    /**
     * Renders the Epic Fight integration section (stamina, guard/parry indicators).
     * Only rendered when Epic Fight is installed and player is in battle mode.
     */
    private void renderEpicFightSection(@Nonnull GuiGraphics graphics, @Nonnull Font font, int x, int y) {
        ClientEpicFightCache cache = ClientEpicFightCache.INSTANCE;
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);

        // === HEADER ===
        String header = "EPIC FIGHT";
        UIScaleManager.drawScaledString(graphics, font, header, x + UIScaleManager.scale(6), y, OverlayTheme.EpicFight.HEADER, true);

        // === GUARD/PARRY INDICATOR ===
        if (cache.isGuarding()) {
            String guardText = cache.isPerfectParry() ? "PERFECT!" : "[GUARD]";
            int guardColor;

            if (cache.isPerfectParry()) {
                // Pulsing blend between PERFECT_PARRY and PERFECT_PARRY_SECONDARY
                float blend = (float) (Math.sin(animationTick * 0.15) * 0.5 + 0.5);
                guardColor = blendColor(
                    OverlayTheme.EpicFight.PERFECT_PARRY,
                    OverlayTheme.EpicFight.PERFECT_PARRY_SECONDARY,
                    blend);

                // Scale effect
                PoseStack pose = graphics.pose();
                pose.pushPose();
                int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
                float scale = cache.getPerfectParryScale();
                int textWidth = UIScaleManager.getScaledStringWidth(font, guardText);
                int textX = x + sPanelWidth - textWidth - UIScaleManager.scale(6);
                pose.translate(textX + textWidth / 2f, y + lineHeight / 2f, 0);
                pose.scale(scale, scale, 1.0f);
                pose.translate(-(textX + textWidth / 2f), -(y + lineHeight / 2f), 0);
                UIScaleManager.drawScaledString(graphics, font, guardText, textX, y, guardColor, true);
                pose.popPose();
            } else {
                // Flash effect for guard
                float flash = cache.getGuardFlashAlpha();
                guardColor = blendColor(OverlayTheme.EpicFight.GUARD, OverlayTheme.EpicFight.GUARD_FLASH, flash);
                int textX = x + sPanelWidth - UIScaleManager.getScaledStringWidth(font, guardText) - UIScaleManager.scale(6);
                UIScaleManager.drawScaledString(graphics, font, guardText, textX, y, guardColor, true);
            }
        } else if (cache.isParrying()) {
            String parryText = "[PARRY]";
            float flash = cache.getParryFlashAlpha();
            int parryColor = blendColor(OverlayTheme.EpicFight.PARRY, OverlayTheme.EpicFight.PARRY_FLASH, flash);
            int textX = x + sPanelWidth - UIScaleManager.getScaledStringWidth(font, parryText) - UIScaleManager.scale(6);
            UIScaleManager.drawScaledString(graphics, font, parryText, textX, y, parryColor, true);
        } else if (cache.isBattleMode()) {
            // Battle mode indicator when not guarding/parrying
            String battleText = "BATTLE";
            int textX = x + sPanelWidth - UIScaleManager.getScaledStringWidth(font, battleText) - UIScaleManager.scale(6);
            UIScaleManager.drawScaledString(graphics, font, battleText, textX, y, OverlayTheme.EpicFight.BATTLE_MODE, true);
        }

        // === STAMINA BAR ===
        int barX = x + UIScaleManager.scale(6);
        int barY = y + UIScaleManager.scale(12);
        int barWidth = sPanelWidth - UIScaleManager.scale(16);
        int barHeight = UIScaleManager.scale(6);

        // Background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, OverlayTheme.EpicFight.STAMINA_BG);

        // Stamina fill (smooth animation)
        float staminaPercent = cache.getDisplayedStaminaPercent();
        int fillWidth = (int) (barWidth * staminaPercent);
        int staminaColor = OverlayTheme.EpicFight.getStaminaColor(staminaPercent);

        if (fillWidth > 0) {
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, staminaColor);
        }

        // Percentage text
        String percentText = (int)(staminaPercent * 100) + "%";
        int percentX = x + sPanelWidth - UIScaleManager.getScaledStringWidth(font, percentText) - UIScaleManager.scale(6);
        UIScaleManager.drawScaledString(graphics, font, percentText, percentX, y + UIScaleManager.scale(10), OverlayTheme.Neutral.N450, false);

        // === CURRENT SKILL NAME ===
        String skillName = cache.getCurrentSkillName();
        if (skillName != null && !skillName.isEmpty()) {
            // Truncate if too long
            if (font.width(skillName) > sPanelWidth - UIScaleManager.scale(16)) {
                skillName = font.plainSubstrByWidth(skillName, sPanelWidth - UIScaleManager.scale(20)) + "...";
            }
            UIScaleManager.drawScaledString(graphics, font, skillName, x + UIScaleManager.scale(6), y + UIScaleManager.scale(22), OverlayTheme.EpicFight.SKILL_NAME, false);
        }

        // === PLAY SOUNDS FOR STATE CHANGES ===
        playEpicFightSounds(cache);
    }

    /**
     * Play Epic Fight sounds for state changes.
     */
    private void playEpicFightSounds(@Nonnull ClientEpicFightCache cache) {
        if (cache.wasPerfectParryJustOccurred()) {
            // Try Epic Fight native sound first
            var efSound = EpicFightCompat.getParrySoundEvent();
            if (efSound != null) {
                NotificationSoundManager.INSTANCE.playSound(efSound, 1.2f, 1.0f);
            } else {
                // Fallback
                NotificationSoundManager.INSTANCE.play("epicfight.parry.perfect",
                    NotificationCategory.COMBAT, NotificationPriority.HIGH);
            }
        } else if (cache.wasParryJustSucceeded()) {
            var efSound = EpicFightCompat.getParrySoundEvent();
            if (efSound != null) {
                NotificationSoundManager.INSTANCE.playSound(efSound, 1.0f, 0.7f);
            } else {
                NotificationSoundManager.INSTANCE.play("epicfight.parry.success",
                    NotificationCategory.COMBAT, NotificationPriority.NORMAL);
            }
        } else if (cache.wasGuardJustStarted()) {
            var efSound = EpicFightCompat.getGuardSoundEvent();
            if (efSound != null) {
                NotificationSoundManager.INSTANCE.playSound(efSound, 1.0f, 0.5f);
            } else {
                NotificationSoundManager.INSTANCE.play("epicfight.guard.start",
                    NotificationCategory.COMBAT, NotificationPriority.LOW);
            }
        }

        // Guard impact sound (blocked an attack - independent check)
        if (cache.wasGuardImpactJustOccurred()) {
            var efSound = EpicFightCompat.getGuardSoundEvent();
            if (efSound != null) {
                NotificationSoundManager.INSTANCE.playSound(efSound, 0.9f, 0.8f);
            } else {
                NotificationSoundManager.INSTANCE.play("epicfight.guard.impact",
                    NotificationCategory.COMBAT, NotificationPriority.NORMAL);
            }
        }

        // Stamina low warning sound (independent check)
        if (cache.wasStaminaLowWarningTriggered()) {
            NotificationSoundManager.INSTANCE.play("epicfight.stamina.low",
                NotificationCategory.COMBAT, NotificationPriority.HIGH);
        }
    }

    private void renderAnnouncements(@Nonnull GuiGraphics graphics, @Nonnull Font font, @Nonnull ClientCombatFlowCache cache, int x, int y) {
        List<ActionAnnouncement> announcements = cache.getAnnouncements();

        int offsetY = 0;
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
        int sLineGap = Math.max(UIScaleManager.scale(24), lineHeight * 2);
        int sSubtitleOffset = Math.max(UIScaleManager.scale(10), lineHeight);
        int sSlideOffset = UIScaleManager.scale(50);
        for (int i = announcements.size() - 1; i >= 0; i--) {
            ActionAnnouncement ann = announcements.get(i);
            float alpha = ann.getAlpha();
            float progress = ann.getProgress();

            if (alpha <= 0) continue;

            // Slide in from right
            float slideIn = Math.min(1.0f, progress * 5f);
            int slideOffset = (int) ((1.0f - slideIn) * sSlideOffset);

            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(slideOffset, 0, 0);

            int color = OverlayTheme.withAlpha(ann.color(), (int) (alpha * 255));
            int subColor = OverlayTheme.withAlpha(OverlayTheme.Neutral.N450, (int) (alpha * 180));

            // Scale for important announcements
            if (ann.important()) {
                float scale = 1.0f + (1.0f - progress) * 0.3f;
                pose.translate(x, y + offsetY, 0);
                pose.scale(scale, scale, 1.0f);
                pose.translate(-x, -(y + offsetY), 0);
            }

            // Title
            UIScaleManager.drawScaledString(graphics, font, ann.title(), x, y + offsetY, color, true);

            // Subtitle
            UIScaleManager.drawScaledString(graphics, font, ann.subtitle(), x, y + offsetY + sSubtitleOffset, subColor, false);

            pose.popPose();

            offsetY += sLineGap;
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

        return DesignTokens.Mask.ALPHA | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    // === Sound Effects ===

    /**
     * Play sounds for state changes. Must be called BEFORE tickAnimations() resets flags.
     */
    private void playSoundsForStateChanges(@Nonnull ClientCombatFlowCache cache) {
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
