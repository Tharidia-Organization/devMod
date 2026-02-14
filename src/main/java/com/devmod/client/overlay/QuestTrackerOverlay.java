package com.devmod.client.overlay;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.quest.Quest;
import com.devmod.quest.QuestObjective;
import com.devmod.quest.QuestProgress;
import com.devmod.quest.QuestRegistry;

/**
 * HUD overlay showing tracked quest objectives on the right side of the screen.
 * Displays up to 3 tracked quests with compact objective progress.
 * Registration is handled by ClientModEvents.
 */
@OnlyIn(Dist.CLIENT)
public class QuestTrackerOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "quest_tracker");

    // Colors (via DesignTokens)
    private static final int PANEL_BG = DesignTokens.withAlpha(DesignTokens.Palette.BACKGROUND, 0xB0);
    private static final int PANEL_BORDER = DesignTokens.Stroke.MUTED;
    private static final int TEXT_TITLE = DesignTokens.Palette.WARNING;
    private static final int TEXT_OBJECTIVE = DesignTokens.Text.SECONDARY;
    private static final int TEXT_COMPLETE = DesignTokens.Palette.SUCCESS;
    private static final int TEXT_MUTED = DesignTokens.Text.MUTED;

    private static final int PROGRESS_BG = DesignTokens.Palette.SURFACE_ALT;
    private static final int PROGRESS_FILL = DesignTokens.Palette.ACCENT_TEAL;
    private static final int PROGRESS_COMPLETE = DesignTokens.Palette.SUCCESS;

    // Layout
    private static final int PANEL_WIDTH = 160;
    private static final int MARGIN_RIGHT = 4;
    private static final int MARGIN_TOP = 40;
    private static final int PADDING = 4;
    private static final int QUEST_GAP = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BAR_HEIGHT = 3;
    private static final int MAX_TRACKED = 3;

    // State
    private static boolean enabled = true;

    // Celebration animation
    private static long lastCompletionTime = 0;
    private static final long CELEBRATION_DURATION_MS = 1500;

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            QuestTrackerOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        // Don't show while a screen is open
        if (mc.screen != null) return;

        UUID playerId = mc.player.getUUID();
        List<QuestProgress> tracked = QuestRegistry.INSTANCE.getTrackedQuests(playerId);
        if (tracked.isEmpty()) return;

        Font font = mc.font;
        int screenWidth = graphics.guiWidth();

        int sWidth = UIScaleManager.scale(PANEL_WIDTH);
        int sMarginRight = UIScaleManager.scale(MARGIN_RIGHT);
        int sMarginTop = UIScaleManager.scale(MARGIN_TOP);
        int sPadding = UIScaleManager.scale(PADDING);
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);
        int sBarHeight = UIScaleManager.scale(BAR_HEIGHT);
        int sQuestGap = UIScaleManager.scale(QUEST_GAP);

        int panelX = screenWidth - sWidth - sMarginRight;
        int panelY = sMarginTop;

        // Clamp to safe area
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        panelX = Math.min(panelX, safeRight - sWidth);
        panelY = Math.max(safeTop, panelY);

        int count = Math.min(tracked.size(), MAX_TRACKED);
        for (int i = 0; i < count; i++) {
            QuestProgress progress = tracked.get(i);
            Quest quest = QuestRegistry.INSTANCE.getQuest(progress.getQuestId());
            if (quest == null) continue;

            int entryHeight = renderTrackedQuest(graphics, font, quest, progress,
                panelX, panelY, sWidth, sPadding, sLineHeight, sBarHeight);
            panelY += entryHeight + sQuestGap;
        }
    }

    private static int renderTrackedQuest(GuiGraphics graphics, Font font,
                                           Quest quest, QuestProgress progress,
                                           int x, int y, int width, int padding,
                                           int lineHeight, int barHeight) {
        var safeFont = Objects.requireNonNull(font);

        // Calculate height
        int objectiveCount = 0;
        for (QuestObjective obj : progress.getObjectives()) {
            if (!obj.isComplete()) {
                objectiveCount++;
                if (objectiveCount >= 3) break; // Show max 3 incomplete objectives
            }
        }
        // If all complete, show one line
        if (objectiveCount == 0) objectiveCount = 1;

        int totalHeight = padding + lineHeight + // Quest name
            objectiveCount * (lineHeight + barHeight + 2) + // Objectives
            padding;

        // Background
        graphics.fill(x, y, x + width, y + totalHeight, PANEL_BG);

        // Left accent bar
        boolean allComplete = progress.areAllObjectivesComplete();
        int accentColor = allComplete ? PROGRESS_COMPLETE : TEXT_TITLE;
        graphics.fill(x, y, x + 2, y + totalHeight, accentColor);

        int textX = x + padding + 2;
        int textY = y + padding;
        int textWidth = width - padding * 2 - 2;

        // Quest name
        String questName = quest.getName().getString();
        if (safeFont.width(questName) > textWidth) {
            questName = safeFont.plainSubstrByWidth(questName, textWidth - 8) + "..";
        }
        UIScaleManager.drawScaledString(graphics, safeFont, questName, textX, textY, TEXT_TITLE);
        textY += lineHeight;

        if (allComplete) {
            // Celebration text
            long timeSinceComplete = System.currentTimeMillis() - lastCompletionTime;
            int completeColor = TEXT_COMPLETE;
            if (timeSinceComplete < CELEBRATION_DURATION_MS) {
                // Pulse effect
                float pulse = (float) Math.sin(timeSinceComplete * 0.01) * 0.5f + 0.5f;
                int alpha = (int) (200 + 55 * pulse);
                completeColor = (alpha << 24) | (completeColor & DesignTokens.Mask.RGB);
            }
            UIScaleManager.drawScaledString(graphics, safeFont, "COMPLETE!", textX, textY, completeColor);
        } else {
            // Render incomplete objectives
            int shown = 0;
            for (QuestObjective obj : progress.getObjectives()) {
                if (obj.isComplete()) continue;
                if (shown >= 3) break;

                // Objective text (compact)
                String objText = obj.description().getString();
                if (safeFont.width(objText) > textWidth) {
                    objText = safeFont.plainSubstrByWidth(objText, textWidth - 8) + "..";
                }
                UIScaleManager.drawScaledString(graphics, safeFont, objText, textX, textY, TEXT_OBJECTIVE);
                textY += lineHeight;

                // Progress bar
                int barX = textX;
                int barWidth = textWidth;
                graphics.fill(barX, textY, barX + barWidth, textY + barHeight, PROGRESS_BG);
                int fillWidth = (int) (barWidth * obj.progress());
                if (fillWidth > 0) {
                    graphics.fill(barX, textY, barX + fillWidth, textY + barHeight, PROGRESS_FILL);
                }
                textY += barHeight + 2;
                shown++;
            }
        }

        return totalHeight;
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    /**
     * Called when a quest objective completes to trigger celebration animation.
     */
    public static void triggerCelebration() {
        lastCompletionTime = System.currentTimeMillis();
    }
}
