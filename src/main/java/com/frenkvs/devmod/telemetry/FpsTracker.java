package com.frenkvs.devmod.telemetry;

import com.frenkvs.devmod.DevMod;
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

/**
 * FPS Tracker con overlay HUD per monitoraggio performance.
 *
 * Mostra:
 * - FPS correnti e media
 * - Frame time in ms
 * - Memoria usata/allocata
 * - Mini grafico FPS history
 *
 * Toggle: Tasto F (configurabile)
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
@SuppressWarnings("null")
public class FpsTracker {

    public static final FpsTracker INSTANCE = new FpsTracker();

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "fps_tracker");

    // === Configurazione UI ===
    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_HEIGHT = 85;
    private static final int PANEL_MARGIN = 5;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;

    // Colors (Impact UI Style)
    private static final int BG_COLOR = 0xCC1A1A2E;         // Panel bg 80% opacity
    private static final int BORDER_COLOR = 0xFF3D5AFE;     // Impact electric blue
    private static final int BORDER_GLOW = 0x553D5AFE;      // Glow effect
    private static final int TEXT_CYAN = 0xFF00FFFF;        // Impact cyan (titles)
    private static final int TEXT_GREEN = 0xFF00FF00;       // Impact green
    private static final int TEXT_YELLOW = 0xFFFFD700;      // Impact gold
    private static final int TEXT_RED = 0xFFFF4444;         // Impact red
    private static final int TEXT_GRAY = 0xFFAAAAAA;        // Muted gray

    // === State ===
    private boolean enabled = false;

    // FPS tracking
    private static final int HISTORY_SIZE = 60;
    private final int[] fpsHistory = new int[HISTORY_SIZE];
    private int historyIndex = 0;
    private long lastFpsUpdate = 0;
    private int currentFps = 0;
    private int avgFps = 0;
    private int minFps = Integer.MAX_VALUE;
    private int maxFps = 0;

    // Telemetry logging (every 5 seconds when enabled)
    private static final long TELEMETRY_INTERVAL_MS = 5000;
    private long lastTelemetryLog = 0;
    private static boolean telemetryEnabled = true;

    // Frame time tracking
    private long lastFrameTime = 0;
    private float frameTimeMs = 0;
    private float avgFrameTimeMs = 0;

    // Statistiche reset
    private long sessionStartTime = 0;
    private int frameCount = 0;

    private FpsTracker() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.DEBUG_OVERLAY,
            LAYER_ID,
            FpsTracker::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!INSTANCE.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        INSTANCE.updateMetrics();
        INSTANCE.renderOverlay(graphics, mc.font);
    }

    private void updateMetrics() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        // Inizializza sessione
        if (sessionStartTime == 0) {
            sessionStartTime = now;
            lastFrameTime = System.nanoTime();
        }

        // Calcola frame time
        long currentTime = System.nanoTime();
        frameTimeMs = (currentTime - lastFrameTime) / 1_000_000f;
        lastFrameTime = currentTime;

        // Media mobile frame time
        avgFrameTimeMs = avgFrameTimeMs * 0.9f + frameTimeMs * 0.1f;

        frameCount++;

        // Update FPS every second
        if (now - lastFpsUpdate >= 1000) {
            currentFps = mc.getFps();

            // Update history
            fpsHistory[historyIndex] = currentFps;
            historyIndex = (historyIndex + 1) % HISTORY_SIZE;

            // Calculate min/max/avg
            int sum = 0;
            int count = 0;
            minFps = Integer.MAX_VALUE;
            maxFps = 0;

            for (int fps : fpsHistory) {
                if (fps > 0) {
                    sum += fps;
                    count++;
                    minFps = Math.min(minFps, fps);
                    maxFps = Math.max(maxFps, fps);
                }
            }

            avgFps = count > 0 ? sum / count : 0;
            if (minFps == Integer.MAX_VALUE) minFps = 0;

            lastFpsUpdate = now;

            // M58: Log FPS to telemetry periodically
            if (telemetryEnabled && now - lastTelemetryLog >= TELEMETRY_INTERVAL_MS) {
                lastTelemetryLog = now;
                logFpsTelemetry();
            }
        }
    }

    /**
     * Logs FPS data to client-side telemetry file.
     * M58: Client FPS tracking for performance analysis.
     */
    private void logFpsTelemetry() {
        try {
            FpsStats stats = getStats();
            String json = stats.toJson();

            // Write to client-side telemetry file
            java.nio.file.Path telemetryDir = com.frenkvs.devmod.util.ConfigPaths.getTelemetryExportDir();
            java.nio.file.Files.createDirectories(telemetryDir);
            java.nio.file.Path file = telemetryDir.resolve("client_fps.ndjson");

            java.nio.file.Files.writeString(file, json + "\n",
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            // Silently ignore - don't spam logs for telemetry failures
        }
    }

    private void renderOverlay(GuiGraphics graphics, Font font) {
        // Position: top left corner
        int x = PANEL_MARGIN;
        int y = PANEL_MARGIN;

        // Outer glow (Impact UI style)
        graphics.fill(x - 1, y - 1, x + PANEL_WIDTH + 1, y + PANEL_HEIGHT + 1, BORDER_GLOW);

        // Background
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BG_COLOR);

        // Border
        graphics.fill(x, y, x + PANEL_WIDTH, y + 1, BORDER_COLOR);
        graphics.fill(x, y + PANEL_HEIGHT - 1, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER_COLOR);
        graphics.fill(x, y, x + 1, y + PANEL_HEIGHT, BORDER_COLOR);
        graphics.fill(x + PANEL_WIDTH - 1, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER_COLOR);

        int textX = x + PADDING;
        int textY = y + PADDING;

        // Titolo (Impact UI cyan)
        graphics.drawString(font, "Performance", textX, textY, TEXT_CYAN, false);
        textY += LINE_HEIGHT + 2;

        // FPS with color based on value
        int fpsColor = getFpsColor(currentFps);
        String fpsStr = String.format("FPS: %d", currentFps);
        graphics.drawString(font, fpsStr, textX, textY, fpsColor, false);

        // Min/Max on the same row
        String minMaxStr = String.format("§7(%d-%d)", minFps, maxFps);
        graphics.drawString(font, minMaxStr, textX + 55, textY, TEXT_GRAY, false);
        textY += LINE_HEIGHT;

        // Average FPS
        String avgStr = String.format("Avg: %d fps", avgFps);
        graphics.drawString(font, avgStr, textX, textY, TEXT_GRAY, false);
        textY += LINE_HEIGHT;

        // Frame time
        int ftColor = getFrameTimeColor(frameTimeMs);
        String ftStr = String.format("Frame: %.1fms", frameTimeMs);
        graphics.drawString(font, ftStr, textX, textY, ftColor, false);
        textY += LINE_HEIGHT;

        // Memory
        Runtime runtime = Runtime.getRuntime();
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMB = runtime.maxMemory() / (1024 * 1024);
        float memPercent = (float) usedMB / maxMB * 100;

        int memColor = memPercent > 80 ? TEXT_RED : (memPercent > 60 ? TEXT_YELLOW : TEXT_GREEN);
        String memStr = String.format("Mem: %dMB/%.0f%%", usedMB, memPercent);
        graphics.drawString(font, memStr, textX, textY, memColor, false);
        textY += LINE_HEIGHT + 2;

        // Mini grafico FPS
        renderFpsGraph(graphics, textX, textY, PANEL_WIDTH - PADDING * 2, 15);
    }

    private void renderFpsGraph(GuiGraphics graphics, int x, int y, int width, int height) {
        // Sfondo grafico
        graphics.fill(x, y, x + width, y + height, 0xFF000000);

        if (maxFps == 0) return;

        // Linea target 60 FPS
        int target60Y = y + height - (int)((60f / Math.max(maxFps, 60)) * height);
        for (int i = 0; i < width; i += 4) {
            graphics.fill(x + i, target60Y, x + i + 2, target60Y + 1, 0x44FFFFFF);
        }

        // Barre FPS
        int barWidth = Math.max(1, width / HISTORY_SIZE);

        for (int i = 0; i < HISTORY_SIZE; i++) {
            int idx = (historyIndex + i) % HISTORY_SIZE;
            int fps = fpsHistory[idx];
            if (fps <= 0) continue;

            int barHeight = (int)((float) fps / Math.max(maxFps, 60) * height);
            barHeight = Math.min(barHeight, height);

            int barX = x + i * barWidth;
            int barY = y + height - barHeight;

            int color = getFpsColor(fps);
            graphics.fill(barX, barY, barX + Math.max(barWidth - 1, 1), y + height, color);
        }
    }

    private int getFpsColor(int fps) {
        if (fps >= 60) return TEXT_GREEN;
        if (fps >= 30) return TEXT_YELLOW;
        return TEXT_RED;
    }

    private int getFrameTimeColor(float ms) {
        if (ms <= 16.67f) return TEXT_GREEN;  // 60+ FPS
        if (ms <= 33.33f) return TEXT_YELLOW; // 30+ FPS
        return TEXT_RED;
    }

    // === Public API ===

    public void toggle() {
        enabled = !enabled;
        if (enabled) {
            reset();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            reset();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reset() {
        sessionStartTime = 0;
        frameCount = 0;
        historyIndex = 0;
        minFps = Integer.MAX_VALUE;
        maxFps = 0;
        avgFps = 0;
        for (int i = 0; i < HISTORY_SIZE; i++) {
            fpsHistory[i] = 0;
        }
    }

    public int getCurrentFps() {
        return currentFps;
    }

    public int getAverageFps() {
        return avgFps;
    }

    public float getFrameTimeMs() {
        return frameTimeMs;
    }

    public int getMinFps() {
        return minFps == Integer.MAX_VALUE ? 0 : minFps;
    }

    public int getMaxFps() {
        return maxFps;
    }

    public static void setTelemetryEnabled(boolean enabled) {
        telemetryEnabled = enabled;
    }

    public static boolean isTelemetryEnabled() {
        return telemetryEnabled;
    }

    /**
     * Gets comprehensive FPS stats for telemetry logging.
     * Called periodically to record client performance data.
     */
    public FpsStats getStats() {
        Runtime runtime = Runtime.getRuntime();
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMB = runtime.maxMemory() / (1024 * 1024);

        return new FpsStats(
            currentFps,
            avgFps,
            minFps == Integer.MAX_VALUE ? 0 : minFps,
            maxFps,
            frameTimeMs,
            avgFrameTimeMs,
            usedMB,
            maxMB
        );
    }

    /**
     * FPS statistics snapshot for telemetry.
     */
    public record FpsStats(
        int currentFps,
        int avgFps,
        int minFps,
        int maxFps,
        float frameTimeMs,
        float avgFrameTimeMs,
        long memoryUsedMB,
        long memoryMaxMB
    ) {
        public String toJson() {
            return String.format(
                "{\"ts\":\"%s\",\"fps\":%d,\"avg_fps\":%d,\"min_fps\":%d,\"max_fps\":%d," +
                "\"frame_ms\":%.2f,\"avg_frame_ms\":%.2f,\"mem_used_mb\":%d,\"mem_max_mb\":%d}",
                java.time.Instant.now(),
                currentFps, avgFps, minFps, maxFps,
                frameTimeMs, avgFrameTimeMs,
                memoryUsedMB, memoryMaxMB
            );
        }
    }
}
