package com.devmod.client.quest;

import java.util.Objects;

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

import com.devmod.DevMod;
import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.quest.QuestData;
import com.devmod.quest.QuestManager;
import com.devmod.quest.QuestTask;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class QuestHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "quest_hud");

    // === UI Colors (consistent style with ImpactHudOverlay) ===
    private static final int PANEL_BG = OverlayTheme.Quest.PANEL_BG;
    private static final int PANEL_BORDER = OverlayTheme.Quest.BORDER;
    private static final int PANEL_BORDER_GLOW = OverlayTheme.Quest.BORDER_GLOW;

    private static final int TEXT_TITLE = OverlayTheme.Quest.TITLE;
    private static final int TEXT_NORMAL = OverlayTheme.Quest.TEXT;
    private static final int TEXT_TASK = OverlayTheme.Quest.TASK;
    private static final int TEXT_NOTE = OverlayTheme.Quest.NOTE;
    private static final int TEXT_PROGRESS = OverlayTheme.Quest.PROGRESS;
    private static final int TEXT_COMPLETED = OverlayTheme.Quest.COMPLETED;
    private static final int TEXT_HINT = OverlayTheme.Quest.HINT;

    // === Dimensions ===
    private static final int PANEL_PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int PANEL_WIDTH = 180;
    private static final int MARGIN_RIGHT = 10;
    private static final int MARGIN_BOTTOM = 10;

    // === Toggle (disabled by default - enable via radial menu) ===
    private static boolean enabled = false;
    private static boolean minimized = false;

    // === Animation ===
    private static long lastCompletionTime = 0;
    private static String lastCompletedTaskName = "";
    private static final long COMPLETION_ANIMATION_DURATION = 2000; // 2 seconds

    // Cache for change detection
    private static int lastTaskIndex = -1;
    private static String lastQuestId = "";

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            QuestHudOverlay::render
        );

        // Register change listener for animations
        QuestManager.INSTANCE.addChangeListener(QuestHudOverlay::onQuestChanged);
    }

    /**
     * Called when quest data changes (for animation triggers).
     */
    private static void onQuestChanged() {
        QuestData quest = QuestManager.INSTANCE.getActiveQuest();
        if (quest == null) return;

        // Detect task completion
        int currentIndex = quest.getCurrentTaskIndex();
        String questId = quest.getId();

        if (questId.equals(lastQuestId) && currentIndex > lastTaskIndex && lastTaskIndex >= 0) {
            // Task was completed - trigger animation
            QuestTask completedTask = quest.getTasks().get(lastTaskIndex);
            if (completedTask != null && completedTask.isCompleted()) {
                lastCompletedTaskName = completedTask.getDescription();
                lastCompletionTime = System.currentTimeMillis();
            }
        }

        lastTaskIndex = currentIndex;
        lastQuestId = questId;
    }

    /**
     * Main rendering method.
     */
    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // Hide if there's an active Endurance Quest (to avoid overlap)
        if (ClientQuestCache.hasActiveQuest()) return;

        QuestData activeQuest = QuestManager.INSTANCE.getActiveQuest();
        if (activeQuest == null) return;

        // Update UIScaleManager for responsive scaling
        UIScaleManager.update();

        Font font = mc.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // Scaled layout values
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int panelWidth = Math.min(sPanelWidth, UIScaleManager.getSafeWidth());
        int sMarginRight = UIScaleManager.scale(MARGIN_RIGHT);
        int sMarginBottom = UIScaleManager.scale(MARGIN_BOTTOM);
        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);

        // Calculate panel dimensions
        int panelHeight = calculatePanelHeight(activeQuest, sPanelPadding, sLineHeight);
        int panelX = screenWidth - panelWidth - sMarginRight;
        int panelY = screenHeight - panelHeight - sMarginBottom - UIScaleManager.scale(40); // 40px above hotbar
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        panelX = Math.max(safeLeft, Math.min(panelX, safeRight - panelWidth));
        panelY = Math.max(safeTop, Math.min(panelY, safeBottom - panelHeight));

        // Render completion animation (if active)
        renderCompletionAnimation(graphics, font, panelX, panelY - UIScaleManager.scale(20), panelWidth);

        // Render panel
        renderQuestPanel(graphics, font, activeQuest, panelX, panelY, panelWidth, panelHeight, sPanelPadding, sLineHeight);
    }

    /**
     * Renders the task completion animation.
     */
    private static void renderCompletionAnimation(GuiGraphics g, Font font, int x, int y, int sPanelWidth) {
        long elapsed = System.currentTimeMillis() - lastCompletionTime;
        if (elapsed > COMPLETION_ANIMATION_DURATION || lastCompletedTaskName.isEmpty()) return;

        // Calculate animation progress (0.0 to 1.0)
        float progress = (float) elapsed / COMPLETION_ANIMATION_DURATION;

        // Fade out in the second half
        float alpha = progress < 0.5f ? 1.0f : 1.0f - (progress - 0.5f) * 2.0f;

        // Slide up animation
        int offsetY = (int) (progress * UIScaleManager.scaleF(-10));

        // Scale animation (pulse effect at start)
        float scale = progress < 0.2f ? 1.0f + (0.2f - progress) * 0.5f : 1.0f;

        // Render completion message
        String message = "\u2713 " + truncateText(lastCompletedTaskName, font, sPanelWidth - UIScaleManager.scale(20));
        int color = applyAlpha(TEXT_COMPLETED, alpha);

        // Apply transformations
        g.pose().pushPose();
        g.pose().translate(x + sPanelWidth / 2.0f, y + offsetY, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.pose().translate(-font.width(message) / 2.0f, 0, 0);

        UIScaleManager.drawScaledString(g, font, message, 0, 0, color, true);

        g.pose().popPose();
    }

    /**
     * Renders the compact quest panel.
     */
    private static void renderQuestPanel(GuiGraphics g, Font font, QuestData quest,
                                          int x, int y, int width, int height, int sPanelPadding, int sLineHeight) {
        // Background with border
        renderPanelBackground(g, x, y, width, height);

        int textX = x + sPanelPadding;
        int textY = y + sPanelPadding;

        if (minimized) {
            // Minimized mode: icon and name only
            String title = "\u2605 " + truncateText(quest.getName(), Objects.requireNonNull(font), width - sPanelPadding * 2 - UIScaleManager.scale(20));
            UIScaleManager.drawScaledString(g, font, title, textX, textY, TEXT_TITLE, false);

            // Compact progress
            String progress = Objects.requireNonNull(quest.getProgressSummary());
            int progressWidth = UIScaleManager.getScaledStringWidth(font, progress);
            UIScaleManager.drawScaledString(g, font, progress, x + width - sPanelPadding - progressWidth, textY, TEXT_PROGRESS, false);
            return;
        }

        // === Header: Nome Quest + Progress ===
        String questName = "\u2605 " + truncateText(quest.getName(), Objects.requireNonNull(font), width - sPanelPadding * 2 - UIScaleManager.scale(40));
        UIScaleManager.drawScaledString(g, font, questName, textX, textY, TEXT_TITLE, false);

        String progress = Objects.requireNonNull(quest.getProgressSummary());
        int progressWidth = UIScaleManager.getScaledStringWidth(font, progress);
        UIScaleManager.drawScaledString(g, font, progress, x + width - sPanelPadding - progressWidth, textY, TEXT_PROGRESS, false);
        textY += sLineHeight + 2;

        // Separator line
        g.fill(x + 4, textY, x + width - 4, textY + 1, OverlayTheme.withAlpha(PANEL_BORDER, OverlayTheme.Alpha.GHOST));
        textY += 4;

        // === Current Task ===
        QuestTask currentTask = quest.getCurrentTask();
        if (currentTask != null) {
            // Label "Quick Task:"
            UIScaleManager.drawScaledString(g, font, "Quick Task:", textX, textY, TEXT_NORMAL, false);
            textY += sLineHeight;

            // Task description (with animated arrow)
            long time = System.currentTimeMillis();
            String arrow = (time / 500) % 2 == 0 ? "\u25B6 " : "\u25B7 "; // Alternating arrow
            String taskDesc = arrow + truncateText(currentTask.getDescription(), font, width - sPanelPadding * 2 - UIScaleManager.scale(10));
            UIScaleManager.drawScaledString(g, font, taskDesc, textX + 4, textY, TEXT_TASK, false);
            textY += sLineHeight + 2;

            // Task note (if present)
            if (currentTask.hasNote()) {
                String noteText = "\u270E " + truncateText(currentTask.getNote(), font, width - sPanelPadding * 2 - UIScaleManager.scale(10));
                UIScaleManager.drawScaledString(g, font, noteText, textX + 4, textY, TEXT_NOTE, false);
                textY += sLineHeight;
            }
        } else {
            // Quest completed with animation
            long time = System.currentTimeMillis();
            int pulseAlpha = (int) (200 + 55 * Math.sin(time / 300.0));
            int completedColor = (pulseAlpha << 24) | (TEXT_COMPLETED & DesignTokens.Mask.RGB);
            UIScaleManager.drawScaledString(g, font, "\u2713 Quest completed!", textX, textY, completedColor, false);
            textY += sLineHeight;
        }

        // === Quest Note (if present, at the bottom) ===
        if (quest.hasQuestNote()) {
            textY += 2;
            g.fill(x + 4, textY, x + width - 4, textY + 1,
                OverlayTheme.withAlpha(PANEL_BORDER, DesignTokens.Alpha.A20));
            textY += 4;

            String questNote = "\u270D " + truncateText(quest.getQuestNote(), font, width - sPanelPadding * 2 - UIScaleManager.scale(10));
            UIScaleManager.drawScaledString(g, font, questNote, textX, textY, TEXT_NOTE, false);
            textY += sLineHeight;
        }

        // === Keybind hint (always at the bottom) ===
        textY += 2;
        String hint = "[ Editor  ] Completa  \\ Toggle";
        UIScaleManager.drawScaledString(g, font, hint, textX, textY, TEXT_HINT, false);
    }

    /**
     * Renders panel background with border.
     */
    private static void renderPanelBackground(GuiGraphics g, int x, int y, int width, int height) {
        // Outer glow
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL_BORDER_GLOW);

        // Main background
        g.fill(x, y, x + width, y + height, PANEL_BG);

        // Border
        g.fill(x, y, x + width, y + 1, PANEL_BORDER);                    // Top
        g.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);  // Bottom
        g.fill(x, y, x + 1, y + height, PANEL_BORDER);                   // Left
        g.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);   // Right
    }

    /**
     * Calculates dynamic panel height.
     */
    private static int calculatePanelHeight(QuestData quest, int sPanelPadding, int sLineHeight) {
        if (minimized) {
            return sPanelPadding * 2 + sLineHeight;
        }

        int height = sPanelPadding * 2;
        height += sLineHeight + UIScaleManager.scale(2);  // Header
        height += UIScaleManager.scale(4);                // Separator

        QuestTask currentTask = quest.getCurrentTask();
        if (currentTask != null) {
            height += sLineHeight;      // "Task rapida:" label
            height += sLineHeight + UIScaleManager.scale(2);  // Task description
            if (currentTask.hasNote()) {
                height += sLineHeight;  // Task note
            }
        } else {
            height += sLineHeight;      // "Quest completata"
        }

        if (quest.hasQuestNote()) {
            height += UIScaleManager.scale(6);                // Separator
            height += sLineHeight;      // Quest note
        }

        height += sLineHeight + UIScaleManager.scale(2);      // Hint keybind

        return height;
    }

    /**
     * Truncates text if it exceeds the maximum width.
     */
    private static String truncateText(String text, Font font, int maxWidth) {
        String safeText = Objects.requireNonNull(text);
        if (font.width(safeText) <= maxWidth) {
            return safeText;
        }

        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (font.width(sb.toString() + c) + ellipsisWidth > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + ellipsis;
    }

    /**
     * Applies alpha to an ARGB color.
     */
    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
    }

    // === Public API ===

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static void setMinimized(boolean value) {
        minimized = value;
    }

    public static boolean isMinimized() {
        return minimized;
    }

    public static void toggleMinimized() {
        minimized = !minimized;
    }

    /**
     * Trigger a completion animation manually (for external calls).
     */
    public static void triggerCompletionAnimation(String taskName) {
        lastCompletedTaskName = taskName;
        lastCompletionTime = System.currentTimeMillis();
    }
}
