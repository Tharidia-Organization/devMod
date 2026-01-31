package com.devmod.client.overlay;

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
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.telemetry.TelemetryService;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class TelemetryStatusOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "telemetry_status");

    // UI Colors - delegating to OverlayTheme
    private static final int PANEL_BG = OverlayTheme.Panel.BG_LIGHT;
    private static final int TEXT_NORMAL = OverlayTheme.Text.PRIMARY;
    private static final int TEXT_MUTED = OverlayTheme.Text.MUTED;
    private static final int RECORDING_COLOR = OverlayTheme.Status.RECORDING;
    private static final int PAUSED_COLOR = OverlayTheme.Status.PAUSED;

    // State - disabled by default for regular users, only QA testers/developers need this
    private static boolean enabled = false;
    private static long recordingStartTime = 0;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            TelemetryStatusOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        // Check if telemetry is active
        boolean recording = TelemetryService.INSTANCE.isRecording();
        String currentRoom = TelemetryService.INSTANCE.getCurrentRoom();

        // Don't show if not recording and no room
        if (!recording && (currentRoom == null || currentRoom.isEmpty())) {
            return;
        }

        // Track recording time
        if (recording && recordingStartTime == 0) {
            recordingStartTime = System.currentTimeMillis();
        } else if (!recording) {
            recordingStartTime = 0;
        }

        Font font = Objects.requireNonNull(mc.font, "font");
        int screenWidth = graphics.guiWidth();

        // Position: top-right corner, but offset down if ImpactHudOverlay is visible
        int panelWidth = Math.min(UIScaleManager.scale(120), UIScaleManager.getSafeWidth());
        int panelHeight = Math.min(UIScaleManager.scale(32), UIScaleManager.getSafeHeight());
        int panelX = screenWidth - panelWidth - UIScaleManager.scale(5);
        int panelY = UIScaleManager.scale(5);

        // Check if ImpactHudOverlay is active and adjust Y position to avoid overlap
        if (ImpactHudOverlay.isEnabled() && ImpactData.get() != null) {
            // ImpactHudOverlay uses approx height 150-200px, position below it
            panelY = ImpactHudOverlay.getLastPanelBottom() + UIScaleManager.scale(8);
            // Fallback if panel info not available
            if (panelY < UIScaleManager.scale(50)) {
                panelY = UIScaleManager.scale(160);
            }
        }
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        panelX = Math.max(safeLeft, Math.min(panelX, safeRight - panelWidth));
        panelY = Math.max(safeTop, Math.min(panelY, safeBottom - panelHeight));

        // Draw panel background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BG);

        // Draw border
        int borderColor = recording ? RECORDING_COLOR : PAUSED_COLOR;
        drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, borderColor);

        // Recording indicator (pulsing dot)
        int dotX = panelX + UIScaleManager.scale(6);
        int dotY = panelY + UIScaleManager.scale(6);
        int dotColor = recording ? getRecordingDotColor() : PAUSED_COLOR;
        int dotSize = UIScaleManager.scale(6);
        graphics.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, dotColor);

        // Status text
        String statusText = recording ? "REC" : "PAUSED";
        UIScaleManager.drawScaledString(graphics, font, statusText, dotX + UIScaleManager.scale(10), dotY - 1,
            recording ? RECORDING_COLOR : PAUSED_COLOR, false);

        // Room info (keep at least 6 chars for readability)
        String roomText = currentRoom != null && !currentRoom.isEmpty()
            ? currentRoom
            : "No room";
        int roomMaxWidth = Math.max(0, panelWidth - UIScaleManager.scale(12));
        roomText = truncateToWidth(font, roomText, roomMaxWidth);
        UIScaleManager.drawScaledString(graphics, font, roomText, panelX + UIScaleManager.scale(6), panelY + UIScaleManager.scale(16), TEXT_MUTED, false);

        // Recording time
        if (recording && recordingStartTime > 0) {
            long elapsed = System.currentTimeMillis() - recordingStartTime;
            String timeText = Objects.requireNonNull(formatTime(elapsed), "time text");
            int timeWidth = UIScaleManager.getScaledStringWidth(font, timeText);
            UIScaleManager.drawScaledString(graphics, font, timeText, panelX + panelWidth - timeWidth - UIScaleManager.scale(6),
                panelY + UIScaleManager.scale(6), TEXT_NORMAL, false);
        }
    }

    private static int getRecordingDotColor() {
        // Pulsing effect
        long time = System.currentTimeMillis();
        float pulse = (float) (Math.sin(time / 300.0) + 1) / 2;
        int alpha = (int) (150 + pulse * 105);
        return OverlayTheme.withAlpha(RECORDING_COLOR, alpha);
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int minChars = Math.min(4, text.length());
        String trimmed = text;
        while (font.width(trimmed + ellipsis) > maxWidth && trimmed.length() > minChars) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
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
     * Reset recording timer (call when starting new test).
     */
    public static void resetTimer() {
        recordingStartTime = System.currentTimeMillis();
    }

    /**
     * Get a combined status summary for debug purposes.
     * Includes telemetry status and impact system info.
     *
     * @return Formatted status summary string
     */
    public static String getStatusSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Telemetry: ");
        sb.append(TelemetryService.INSTANCE.isRecording() ? "Recording" : "Paused");
        String room = TelemetryService.INSTANCE.getCurrentRoom();
        if (room != null && !room.isEmpty()) {
            sb.append(" [").append(room).append("]");
        }
        sb.append("\n");
        sb.append(ImpactHudController.INSTANCE.getDebugInfo());
        return sb.toString();
    }
}
