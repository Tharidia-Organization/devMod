package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.telemetry.TelemetryService;
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
import java.util.Objects;

/**
 * Compact HUD overlay showing telemetry recording status.
 * Displays in the top-right corner when telemetry is active.
 *
 * Shows:
 * - Recording indicator (pulsing red dot)
 * - Current room being tracked
 * - Time recording
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class TelemetryStatusOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "telemetry_status");

    // UI Colors
    private static final int PANEL_BG = 0xAA1A1A2E;
    private static final int TEXT_NORMAL = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFAAAAAA;
    private static final int RECORDING_COLOR = 0xFFFF4444;
    private static final int PAUSED_COLOR = 0xFFFFAA00;

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
        int panelWidth = 120;
        int panelHeight = 32;
        int panelX = screenWidth - panelWidth - 5;
        int panelY = 5;

        // Check if ImpactHudOverlay is active and adjust Y position to avoid overlap
        if (ImpactHudOverlay.isEnabled() && ImpactData.get() != null) {
            // ImpactHudOverlay uses approx height 150-200px, position below it
            panelY = ImpactHudOverlay.getLastPanelBottom() + 8;
            // Fallback if panel info not available
            if (panelY < 50) panelY = 160;
        }

        // Draw panel background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BG);

        // Draw border
        int borderColor = recording ? RECORDING_COLOR : PAUSED_COLOR;
        drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, borderColor);

        // Recording indicator (pulsing dot)
        int dotX = panelX + 6;
        int dotY = panelY + 6;
        int dotColor = recording ? getRecordingDotColor() : PAUSED_COLOR;
        graphics.fill(dotX, dotY, dotX + 6, dotY + 6, dotColor);

        // Status text
        String statusText = recording ? "REC" : "PAUSED";
        graphics.drawString(font, statusText, dotX + 10, dotY - 1, recording ? RECORDING_COLOR : PAUSED_COLOR, false);

        // Room info (keep at least 6 chars for readability)
        String roomText = currentRoom != null && !currentRoom.isEmpty()
            ? currentRoom
            : "No room";
        if (roomText.length() > 14) {
            int truncateAt = Math.max(6, 11); // Keep at least 6 chars
            roomText = roomText.substring(0, truncateAt) + "...";
        }
        graphics.drawString(font, roomText, panelX + 6, panelY + 16, TEXT_MUTED, false);

        // Recording time
        if (recording && recordingStartTime > 0) {
            long elapsed = System.currentTimeMillis() - recordingStartTime;
            String timeText = Objects.requireNonNull(formatTime(elapsed), "time text");
            int timeWidth = font.width(timeText);
            graphics.drawString(font, timeText, panelX + panelWidth - timeWidth - 6, panelY + 6, TEXT_NORMAL, false);
        }
    }

    private static int getRecordingDotColor() {
        // Pulsing effect
        long time = System.currentTimeMillis();
        float pulse = (float) (Math.sin(time / 300.0) + 1) / 2;
        int alpha = (int) (150 + pulse * 105);
        return (alpha << 24) | (0xFF << 16) | (0x44 << 8) | 0x44;
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
}
