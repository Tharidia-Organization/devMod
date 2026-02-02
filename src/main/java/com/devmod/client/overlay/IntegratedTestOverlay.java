package com.devmod.client.overlay;

import java.util.Objects;

import javax.annotation.Nonnull;

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
import com.devmod.client.testing.IntegratedTestSession;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.testing.TestCase;
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class IntegratedTestOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "integrated_test_hud");

    // === UI Colors (delegating to OverlayTheme) ===
    private static final int PANEL_BG = OverlayTheme.Panel.BG_STANDARD;
    private static final int PANEL_BORDER = OverlayTheme.Border.ACCENT;
    private static final int TEXT_TITLE = OverlayTheme.Text.TITLE;
    private static final int TEXT_NORMAL = OverlayTheme.Text.PRIMARY;
    private static final int TEXT_VALUE = OverlayTheme.Text.VALUE;
    private static final int TEXT_MUTED = OverlayTheme.Text.MUTED;
    private static final int PROGRESS_BG = OverlayTheme.Progress.BG;
    private static final int PROGRESS_FILL = OverlayTheme.Progress.FILL;

    // === Dimensions ===
    private static final int PANEL_WIDTH = 180;
    private static final int PANEL_PADDING = OverlayTheme.Dimension.PADDING_TIGHT;
    private static final int LINE_HEIGHT = OverlayTheme.Dimension.LINE_HEIGHT;

    // === State (disabled by default - enable via radial menu) ===
    private static boolean enabled = false;
    private static boolean forceShow = false;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.CROSSHAIR),
            Objects.requireNonNull(LAYER_ID),
            IntegratedTestOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (!enabled) return;

        IntegratedTestSession session = IntegratedTestSession.INSTANCE;

        // Only show when session is active or forceShow is true
        if (!session.isSessionActive() && !forceShow) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        Font font = Objects.requireNonNull(mc.font, "font");
        int screenHeight = graphics.guiHeight();

        // Scale dimensions
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int panelWidth = Math.min(sPanelWidth, UIScaleManager.getSafeWidth());
        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);

        // Position: Left side, vertically centered
        int panelX = UIScaleManager.scale(5);
        int panelHeight = calculatePanelHeight(session, sPanelPadding, sLineHeight);
        int panelY = (screenHeight - panelHeight) / 2;
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        panelX = Math.max(safeLeft, Math.min(panelX, safeRight - panelWidth));
        panelY = Math.max(safeTop, Math.min(panelY, safeBottom - panelHeight));

        renderPanel(graphics, Objects.requireNonNull(font, "font"), panelX, panelY, panelHeight, session,
                    panelWidth, sPanelPadding, sLineHeight);
    }

    private static int calculatePanelHeight(IntegratedTestSession session, int sPanelPadding, int sLineHeight) {
        int height = sPanelPadding * 2;
        height += sLineHeight + UIScaleManager.scale(2);  // Title
        height += UIScaleManager.scale(4);                // Separator
        height += sLineHeight;      // Session type
        height += sLineHeight;      // Wave progress
        height += UIScaleManager.scale(8) + UIScaleManager.scale(4);  // Progress bar
        height += sLineHeight;      // Stats line
        height += sLineHeight;      // Duration

        // Add space for linked test case
        if (session.getLinkedTestCase() != null) {
            height += UIScaleManager.scale(4);  // Separator
            height += sLineHeight;  // Test case name
            height += sLineHeight;  // Test case status
        }

        height += sLineHeight + UIScaleManager.scale(4);  // Hints
        return height;
    }

    private static void renderPanel(@Nonnull GuiGraphics graphics, @Nonnull Font font, int x, int y, int height,
                                     IntegratedTestSession session, int panelWidth, int sPanelPadding, int sLineHeight) {
        // Background
        graphics.fill(x, y, x + panelWidth, y + height, PANEL_BG);

        // Border
        drawBorder(graphics, x, y, panelWidth, height, PANEL_BORDER);

        // Accent bar on left
        graphics.fill(x, y, x + UIScaleManager.scale(3), y + height, getSessionColor(session));

        int textX = x + sPanelPadding + UIScaleManager.scale(3);
        int textY = y + sPanelPadding;
        int contentWidth = Math.max(0, panelWidth - sPanelPadding * 2 - UIScaleManager.scale(3));

        // Title
        String title = truncateToWidth(font, "\u26A1 Test Session", contentWidth);
        UIScaleManager.drawScaledString(graphics, font, title, textX, textY, TEXT_TITLE, false);
        textY += sLineHeight + UIScaleManager.scale(2);

        // Separator
        graphics.fill(x + UIScaleManager.scale(4), textY, x + panelWidth - UIScaleManager.scale(4), textY + 1,
            OverlayTheme.withAlpha(PANEL_BORDER, OverlayTheme.Alpha.GHOST));
        textY += UIScaleManager.scale(4);

        // Session type
        IntegratedTestSession.TestSessionType currentType = session.getCurrentType();
        String typeName = currentType != null
            ? Objects.requireNonNullElse(currentType.getDisplayName(), "Unknown")
            : "Unknown";
        typeName = truncateToWidth(font, typeName, contentWidth);
        UIScaleManager.drawScaledString(graphics, font, typeName, textX, textY, TEXT_NORMAL, false);
        textY += sLineHeight;

        // Wave progress
        int waves = session.getCompletedWaves();
        int target = session.getTargetWaves();
        String waveText = target > 0 ?
            String.format("Wave: %d/%d", waves, target) :
            String.format("Wave: %d (Endless)", waves);
        waveText = truncateToWidth(font, waveText, contentWidth);
        UIScaleManager.drawScaledString(graphics, font, waveText, textX, textY, TEXT_VALUE, false);
        textY += sLineHeight;

        // Progress bar
        int barWidth = panelWidth - sPanelPadding * 2 - UIScaleManager.scale(3);
        int barHeight = UIScaleManager.scale(8);
        int barX = textX;
        int barY = textY;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, PROGRESS_BG);

        float progress = session.getProgress();
        if (progress > 0 && target > 0) {
            int fillWidth = (int) (barWidth * progress);
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, PROGRESS_FILL);
        } else if (target <= 0) {
            // Endless mode - pulsing bar
            long time = System.currentTimeMillis();
            float pulse = (float) (Math.sin(time / 500.0) + 1) / 2;
            int pulseWidth = (int) (barWidth * 0.3f * pulse);
            int pulseX = barX + (int) ((barWidth - pulseWidth) * ((time / 20) % barWidth) / (float) barWidth);
            graphics.fill(pulseX, barY, Math.min(pulseX + pulseWidth, barX + barWidth), barY + barHeight, DesignTokens.TestingMode.PULSE);
        }

        drawBorder(graphics, barX, barY, barWidth, barHeight, DesignTokens.TestingMode.PROGRESS_BORDER);
        textY += barHeight + UIScaleManager.scale(4);

        // Stats: Kills
        IntegratedTestSession.SessionResults results = session.getResults();
        String statsText = String.format("Kills: %d", results.totalKills);
        if (results.deaths > 0) {
            statsText += String.format(" | Deaths: %d", results.deaths);
        }
        statsText = truncateToWidth(font, statsText, contentWidth);
        UIScaleManager.drawScaledString(graphics, font, statsText, textX, textY, TEXT_MUTED, false);
        textY += sLineHeight;

        // Duration
        java.time.Instant start = session.getSessionStart();
        if (start != null) {
            long seconds = java.time.Duration.between(start, java.time.Instant.now()).toSeconds();
            long minutes = seconds / 60;
            seconds = seconds % 60;
            String duration = String.format("Time: %d:%02d", minutes, seconds);
            duration = truncateToWidth(font, duration, contentWidth);
            UIScaleManager.drawScaledString(graphics, font, duration, textX, textY, TEXT_MUTED, false);
        }
        textY += sLineHeight;

        // Linked test case (if any)
        TestCase linkedTest = session.getLinkedTestCase();
        if (linkedTest != null) {
            // Separator
            graphics.fill(x + UIScaleManager.scale(4), textY, x + panelWidth - UIScaleManager.scale(4), textY + 1,
                OverlayTheme.withAlpha(PANEL_BORDER, OverlayTheme.Alpha.GHOST));
            textY += UIScaleManager.scale(4);

            // Test name (truncated - keep at least 6 chars for readability)
            String testName = Objects.requireNonNull(linkedTest.getName(), "linked test name");
            testName = truncateToWidth(font, testName, contentWidth - UIScaleManager.scale(10));
            UIScaleManager.drawScaledString(graphics, font, "\u25B6 " + testName, textX, textY, TEXT_NORMAL, false);
            textY += sLineHeight;

            // Test status
            var status = linkedTest.getStatus();
            String statusText = status != null
                ? Objects.requireNonNullElse(status.getDisplayName(), "Unknown")
                : "Unknown";
            int statusColor = status != null ? status.getColor() : TEXT_NORMAL;
            String statusLine = truncateToWidth(font, "  Status: " + statusText, contentWidth);
            UIScaleManager.drawScaledString(graphics, font, statusLine, textX, textY, statusColor, false);
            textY += sLineHeight;
        }

        // Hints
        textY += UIScaleManager.scale(4);
        String hint = truncateToWidth(font, "\u00a77[ESC] Abandon | [B] Boss HUD", contentWidth);
        UIScaleManager.drawScaledString(graphics, font, hint, textX, textY, TEXT_MUTED, false);
    }

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        int allowed = Math.max(0, maxWidth - ellipsisWidth);
        if (allowed <= 0) {
            return "...";
        }
        return font.plainSubstrByWidth(text, allowed) + "...";
    }

    private static int getSessionColor(IntegratedTestSession session) {
        IntegratedTestSession.TestSessionType currentType = session.getCurrentType();
        if (currentType == null) return PANEL_BORDER;

        return switch (currentType) {
            case COMBAT_BASIC, COMBAT_ADVANCED -> DesignTokens.TestingMode.COMBAT;
            case BOSS_FIGHT -> DesignTokens.TestingMode.BOSS_FIGHT;
            case SURVIVAL_WAVES -> DesignTokens.TestingMode.SURVIVAL;
            case DAMAGE_VALIDATION -> DesignTokens.TestingMode.DAMAGE_VALIDATION;
            case PERFORMANCE_STRESS -> DesignTokens.TestingMode.PERFORMANCE;
            case CUSTOM -> DesignTokens.TestingMode.CUSTOM;
        };
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);                    // Top
        graphics.fill(x, y + height - 1, x + width, y + height, color);  // Bottom
        graphics.fill(x, y, x + 1, y + height, color);                   // Left
        graphics.fill(x + width - 1, y, x + width, y + height, color);   // Right
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

    /**
     * Force show the HUD even when no session is active (for debugging).
     */
    public static void setForceShow(boolean value) {
        forceShow = value;
    }
}
