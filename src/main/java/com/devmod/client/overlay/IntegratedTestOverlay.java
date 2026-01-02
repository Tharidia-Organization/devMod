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

    // === State ===
    private static boolean enabled = true;
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
        if (!enabled) return;

        IntegratedTestSession session = IntegratedTestSession.INSTANCE;

        // Only show when session is active or forceShow is true
        if (!session.isSessionActive() && !forceShow) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        Font font = Objects.requireNonNull(mc.font, "font");
        int screenHeight = graphics.guiHeight();

        // Position: Left side, vertically centered
        int panelX = 5;
        int panelHeight = calculatePanelHeight(session);
        int panelY = (screenHeight - panelHeight) / 2;

        renderPanel(graphics, Objects.requireNonNull(font, "font"), panelX, panelY, panelHeight, session);
    }

    private static int calculatePanelHeight(IntegratedTestSession session) {
        int height = PANEL_PADDING * 2;
        height += LINE_HEIGHT + 2;  // Title
        height += 4;                // Separator
        height += LINE_HEIGHT;      // Session type
        height += LINE_HEIGHT;      // Wave progress
        height += 8 + 4;            // Progress bar
        height += LINE_HEIGHT;      // Stats line
        height += LINE_HEIGHT;      // Duration

        // Add space for linked test case
        if (session.getLinkedTestCase() != null) {
            height += 4;            // Separator
            height += LINE_HEIGHT;  // Test case name
            height += LINE_HEIGHT;  // Test case status
        }

        height += LINE_HEIGHT + 4;  // Hints
        return height;
    }

    private static void renderPanel(@Nonnull GuiGraphics graphics, @Nonnull Font font, int x, int y, int height,
                                     IntegratedTestSession session) {
        // Background
        graphics.fill(x, y, x + PANEL_WIDTH, y + height, PANEL_BG);

        // Border
        drawBorder(graphics, x, y, PANEL_WIDTH, height, PANEL_BORDER);

        // Accent bar on left
        graphics.fill(x, y, x + 3, y + height, getSessionColor(session));

        int textX = x + PANEL_PADDING + 3;
        int textY = y + PANEL_PADDING;

        // Title
        graphics.drawString(font, "\u26A1 Test Session", textX, textY, TEXT_TITLE, false);
        textY += LINE_HEIGHT + 2;

        // Separator
        graphics.fill(x + 4, textY, x + PANEL_WIDTH - 4, textY + 1, PANEL_BORDER & 0x55FFFFFF);
        textY += 4;

        // Session type
        IntegratedTestSession.TestSessionType currentType = session.getCurrentType();
        String typeName = currentType != null
            ? Objects.requireNonNullElse(currentType.getDisplayName(), "Unknown")
            : "Unknown";
        graphics.drawString(font, typeName, textX, textY, TEXT_NORMAL, false);
        textY += LINE_HEIGHT;

        // Wave progress
        int waves = session.getCompletedWaves();
        int target = session.getTargetWaves();
        String waveText = target > 0 ?
            String.format("Wave: %d/%d", waves, target) :
            String.format("Wave: %d (Endless)", waves);
        graphics.drawString(font, waveText, textX, textY, TEXT_VALUE, false);
        textY += LINE_HEIGHT;

        // Progress bar
        int barWidth = PANEL_WIDTH - PANEL_PADDING * 2 - 3;
        int barHeight = 8;
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
        textY += barHeight + 4;

        // Stats: Kills
        IntegratedTestSession.SessionResults results = session.getResults();
        String statsText = String.format("Kills: %d", results.totalKills);
        if (results.deaths > 0) {
            statsText += String.format(" | Deaths: %d", results.deaths);
        }
        graphics.drawString(font, statsText, textX, textY, TEXT_MUTED, false);
        textY += LINE_HEIGHT;

        // Duration
        java.time.Instant start = session.getSessionStart();
        if (start != null) {
            long seconds = java.time.Duration.between(start, java.time.Instant.now()).getSeconds();
            long minutes = seconds / 60;
            seconds = seconds % 60;
            String duration = String.format("Time: %d:%02d", minutes, seconds);
            graphics.drawString(font, duration, textX, textY, TEXT_MUTED, false);
        }
        textY += LINE_HEIGHT;

        // Linked test case (if any)
        TestCase linkedTest = session.getLinkedTestCase();
        if (linkedTest != null) {
            // Separator
            graphics.fill(x + 4, textY, x + PANEL_WIDTH - 4, textY + 1, PANEL_BORDER & 0x55FFFFFF);
            textY += 4;

            // Test name (truncated - keep at least 6 chars for readability)
            String testName = Objects.requireNonNull(linkedTest.getName(), "linked test name");
            int maxTestNameWidth = PANEL_WIDTH - PANEL_PADDING * 2 - 10;
            if (font.width(testName) > maxTestNameWidth) {
                String ellipsis = "...";
                int minChars = Math.min(6, testName.length());
                while (font.width(testName + ellipsis) > maxTestNameWidth && testName.length() > minChars) {
                    testName = testName.substring(0, testName.length() - 1);
                }
                testName += ellipsis;
            }
            graphics.drawString(font, "\u25B6 " + testName, textX, textY, TEXT_NORMAL, false);
            textY += LINE_HEIGHT;

            // Test status
            var status = linkedTest.getStatus();
            String statusText = status != null
                ? Objects.requireNonNullElse(status.getDisplayName(), "Unknown")
                : "Unknown";
            int statusColor = status != null ? status.getColor() : TEXT_NORMAL;
            graphics.drawString(font, "  Status: " + statusText, textX, textY, statusColor, false);
            textY += LINE_HEIGHT;
        }

        // Hints
        textY += 4;
        graphics.drawString(font, "\u00a77[ESC] Abandon | [B] Boss HUD", textX, textY, TEXT_MUTED, false);
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
