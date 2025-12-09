package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.testing.IntegratedTestSession;
import com.frenkvs.devmod.testing.TestCase;
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

import javax.annotation.Nullable;

/**
 * Unified HUD overlay for integrated test sessions.
 *
 * Shows:
 * - Current test session type and status
 * - Wave progress (X/Y waves completed)
 * - Kill count and duration
 * - Linked TestCase progress (if any)
 * - Quick action hints (keys to press)
 *
 * Position: Left side of screen, below crosshair
 * Toggle: Automatically shows when IntegratedTestSession is active
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class IntegratedTestHud {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "integrated_test_hud");

    // === UI Colors ===
    private static final int PANEL_BG = 0xCC1A1A2E;
    private static final int PANEL_BORDER = 0xFF4A5ADE;
    private static final int TEXT_TITLE = 0xFF00FFFF;
    private static final int TEXT_NORMAL = 0xFFFFFFFF;
    private static final int TEXT_VALUE = 0xFF00FF00;
    private static final int TEXT_WARNING = 0xFFFFFF00;
    private static final int TEXT_DANGER = 0xFFFF4444;
    private static final int TEXT_MUTED = 0xFFAAAAAA;
    private static final int PROGRESS_BG = 0xFF333333;
    private static final int PROGRESS_FILL = 0xFF00DD88;

    // === Dimensions ===
    private static final int PANEL_WIDTH = 180;
    private static final int PANEL_PADDING = 6;
    private static final int LINE_HEIGHT = 11;

    // === State ===
    private static boolean enabled = true;
    private static boolean forceShow = false;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.CROSSHAIR,
            LAYER_ID,
            IntegratedTestHud::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!enabled) return;

        IntegratedTestSession session = IntegratedTestSession.INSTANCE;

        // Only show when session is active or forceShow is true
        if (!session.isSessionActive() && !forceShow) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        Font font = mc.font;
        int screenHeight = graphics.guiHeight();

        // Position: Left side, vertically centered
        int panelX = 5;
        int panelHeight = calculatePanelHeight(session);
        int panelY = (screenHeight - panelHeight) / 2;

        renderPanel(graphics, font, panelX, panelY, panelHeight, session);
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

    private static void renderPanel(GuiGraphics graphics, Font font, int x, int y, int height,
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
        String typeName = session.getCurrentType() != null ?
            session.getCurrentType().getDisplayName() : "Unknown";
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
            graphics.fill(pulseX, barY, Math.min(pulseX + pulseWidth, barX + barWidth), barY + barHeight, 0xFF4488FF);
        }

        drawBorder(graphics, barX, barY, barWidth, barHeight, 0xFF555555);
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

            // Test name (truncated)
            String testName = linkedTest.getName();
            if (font.width(testName) > PANEL_WIDTH - PANEL_PADDING * 2 - 10) {
                while (font.width(testName + "..") > PANEL_WIDTH - PANEL_PADDING * 2 - 10 && testName.length() > 5) {
                    testName = testName.substring(0, testName.length() - 1);
                }
                testName += "..";
            }
            graphics.drawString(font, "\u25B6 " + testName, textX, textY, TEXT_NORMAL, false);
            textY += LINE_HEIGHT;

            // Test status
            String statusText = linkedTest.getStatus().getDisplayName();
            int statusColor = linkedTest.getStatus().getColor();
            graphics.drawString(font, "  Status: " + statusText, textX, textY, statusColor, false);
            textY += LINE_HEIGHT;
        }

        // Hints
        textY += 4;
        graphics.drawString(font, "\u00a77[ESC] Abandon | [B] Boss HUD", textX, textY, TEXT_MUTED, false);
    }

    private static int getSessionColor(IntegratedTestSession session) {
        if (session.getCurrentType() == null) return PANEL_BORDER;

        return switch (session.getCurrentType()) {
            case COMBAT_BASIC, COMBAT_ADVANCED -> 0xFFFF6644;
            case BOSS_FIGHT -> 0xFFAA44FF;
            case SURVIVAL_WAVES -> 0xFF44FF88;
            case DAMAGE_VALIDATION -> 0xFFFFAA00;
            case PERFORMANCE_STRESS -> 0xFF4488FF;
            case CUSTOM -> 0xFF888888;
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
