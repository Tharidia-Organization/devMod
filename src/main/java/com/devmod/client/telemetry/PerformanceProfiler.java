package com.devmod.client.telemetry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class PerformanceProfiler {

    public static final PerformanceProfiler INSTANCE = new PerformanceProfiler();

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "performance_profiler");

    // === UI Config ===
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_MARGIN = 5;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BAR_HEIGHT = 4;

    // Colors (Impact UI Style)
    private static final int BG_COLOR = 0xCC1A1A2E;         // Panel bg 80% opacity
    private static final int BORDER_COLOR = 0xFF3D5AFE;     // Impact electric blue
    private static final int BORDER_GLOW = 0x553D5AFE;      // Glow effect
    private static final int TEXT_GREEN = 0xFF00FF00;       // Impact green
    private static final int TEXT_YELLOW = 0xFFFFD700;      // Impact gold
    private static final int TEXT_RED = 0xFFFF4444;         // Impact red
    private static final int TEXT_GRAY = 0xFFAAAAAA;        // Muted gray
    private static final int TEXT_CYAN = 0xFF00FFFF;        // Impact cyan (titles/headers)

    // === State ===
    private boolean enabled = false;

    // Timing per system (name -> time in nanoseconds)
    private final Map<String, Long> systemTimings = new ConcurrentHashMap<>();
    private final Map<String, Long> systemTimingsAvg = new ConcurrentHashMap<>();
    private final Map<String, Integer> systemCallCounts = new ConcurrentHashMap<>();

    // Frame timing
    private static final int FRAME_HISTORY_SIZE = 120;
    private final float[] frameTimeHistory = new float[FRAME_HISTORY_SIZE];
    private int frameHistoryIndex = 0;
    private long lastFrameNano = 0;
    private float currentFrameTimeMs = 0;
    private float avgFrameTimeMs = 0;
    private float maxFrameTimeMs = 0;

    // TPS tracking
    private long lastTickTime = 0;
    private int tickCount = 0;
    private float estimatedTps = 20.0f;
    private long tpsWindowStart = 0;

    /**
     * Gets the last recorded tick time for TPS calculations.
     * Used for debugging and performance analysis.
     */
    public long getLastTickTime() {
        return lastTickTime;
    }

    // Contatori attivi
    private final Map<String, Integer> activeCounters = new LinkedHashMap<>();

    // FPS tracking
    private int currentFps = 0;
    private int minFps = Integer.MAX_VALUE;
    private int maxFps = 0;
    private int avgFps = 0;
    private final int[] fpsHistory = new int[60];
    private int fpsHistoryIndex = 0;
    private long lastFpsUpdate = 0;

    private PerformanceProfiler() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.DEBUG_OVERLAY),
            Objects.requireNonNull(LAYER_ID),
            PerformanceProfiler::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!INSTANCE.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;
        if (font == null) return;

        INSTANCE.updateMetrics();
        INSTANCE.renderOverlay(graphics, font);
    }

    // === Profiling API ===

    /**
     * Inizia il timing per un sistema.
     * @return timestamp di inizio (da passare a endTiming)
     */
    public long startTiming(String systemName) {
        if (!enabled) return 0;
        return System.nanoTime();
    }

    /**
     * Termina il timing per un sistema.
     */
    public void endTiming(String systemName, long startNano) {
        if (!enabled || startNano == 0) return;

        long elapsed = System.nanoTime() - startNano;
        systemTimings.merge(systemName, elapsed, PerformanceProfiler::sumLongs);
        systemCallCounts.merge(systemName, 1, PerformanceProfiler::sumInts);
    }

    /**
     * Aggiorna un contatore attivo.
     */
    public void setCounter(String name, int value) {
        activeCounters.put(name, value);
    }

    /**
     * Notifica un tick client (per TPS tracking).
     */
    public void onClientTick() {
        if (!enabled) return;

        tickCount++;
        long now = System.currentTimeMillis();

        // Calculate TPS every second
        if (tpsWindowStart == 0) {
            tpsWindowStart = now;
        } else if (now - tpsWindowStart >= 1000) {
            estimatedTps = tickCount * 1000.0f / (now - tpsWindowStart);
            tickCount = 0;
            tpsWindowStart = now;
        }
    }

    // === Update Metrics ===

    private void updateMetrics() {
        long now = System.nanoTime();
        long nowMs = System.currentTimeMillis();

        // Frame time
        if (lastFrameNano > 0) {
            currentFrameTimeMs = (now - lastFrameNano) / 1_000_000f;
            frameTimeHistory[frameHistoryIndex] = currentFrameTimeMs;
            frameHistoryIndex = (frameHistoryIndex + 1) % FRAME_HISTORY_SIZE;

            // Moving average
            avgFrameTimeMs = avgFrameTimeMs * 0.95f + currentFrameTimeMs * 0.05f;

            // Max (slow decay)
            if (currentFrameTimeMs > maxFrameTimeMs) {
                maxFrameTimeMs = currentFrameTimeMs;
            } else {
                maxFrameTimeMs = maxFrameTimeMs * 0.99f; // Decay
            }
        }
        lastFrameNano = now;

        // FPS (every second)
        if (nowMs - lastFpsUpdate >= 1000) {
            Minecraft mc = Minecraft.getInstance();
            currentFps = mc.getFps();

            fpsHistory[fpsHistoryIndex] = currentFps;
            fpsHistoryIndex = (fpsHistoryIndex + 1) % fpsHistory.length;

            // Calculate min/max/avg
            int sum = 0, count = 0;
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

            // Calculate average timing for each system
            for (Map.Entry<String, Long> entry : systemTimings.entrySet()) {
                String name = entry.getKey();
                long totalNano = entry.getValue();
                int calls = systemCallCounts.getOrDefault(name, 1);
                long avgNano = calls > 0 ? totalNano / calls : 0;
                systemTimingsAvg.put(name, avgNano);
            }

            // Reset counters
            systemTimings.clear();
            systemCallCounts.clear();

            lastFpsUpdate = nowMs;
        }
    }

    // === Rendering ===

    private void renderOverlay(GuiGraphics graphics, Font font) {
        font = Objects.requireNonNull(font);
        int screenWidth = graphics.guiWidth();

        // Position: top right corner
        int panelHeight = calculatePanelHeight();
        int x = screenWidth - PANEL_WIDTH - PANEL_MARGIN;
        int y = PANEL_MARGIN;

        // Outer glow (Impact UI style)
        graphics.fill(x - 1, y - 1, x + PANEL_WIDTH + 1, y + panelHeight + 1, BORDER_GLOW);

        // Background
        graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, BG_COLOR);

        // Bordo
        drawBorder(graphics, x, y, PANEL_WIDTH, panelHeight, BORDER_COLOR);

        int textX = x + PADDING;
        int textY = y + PADDING;

        // === Header ===
        graphics.drawString(font, "DevMod Profiler", textX, textY, TEXT_CYAN, false);
        textY += LINE_HEIGHT + 4;

        // === FPS Section ===
        graphics.drawString(font, "▸ Performance", textX, textY, TEXT_CYAN, false);
        textY += LINE_HEIGHT;

        int fpsColor = getFpsColor(currentFps);
        graphics.drawString(font, String.format("FPS: %d", currentFps), textX + 4, textY, fpsColor, false);
        graphics.drawString(font, String.format("§7(min:%d avg:%d max:%d)", minFps, avgFps, maxFps),
            textX + 50, textY, TEXT_GRAY, false);
        textY += LINE_HEIGHT;

        // Frame time
        int ftColor = getFrameTimeColor(currentFrameTimeMs);
        graphics.drawString(font, String.format("Frame: %.2fms", currentFrameTimeMs), textX + 4, textY, ftColor, false);
        graphics.drawString(font, String.format("§7(avg:%.1f max:%.1f)", avgFrameTimeMs, maxFrameTimeMs),
            textX + 80, textY, TEXT_GRAY, false);
        textY += LINE_HEIGHT;

        // TPS
        int tpsColor = estimatedTps >= 19 ? TEXT_GREEN : (estimatedTps >= 15 ? TEXT_YELLOW : TEXT_RED);
        graphics.drawString(font, String.format("TPS: %.1f", estimatedTps), textX + 4, textY, tpsColor, false);
        textY += LINE_HEIGHT;

        // Frame time graph
        textY += 2;
        renderFrameTimeGraph(graphics, textX, textY, PANEL_WIDTH - PADDING * 2, 20);
        textY += 24;

        // === Memory Section ===
        graphics.drawString(font, "▸ Memory", textX, textY, TEXT_CYAN, false);
        textY += LINE_HEIGHT;

        Runtime runtime = Runtime.getRuntime();
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long totalMB = runtime.totalMemory() / (1024 * 1024);
        long maxMB = runtime.maxMemory() / (1024 * 1024);
        float memPercent = (float) usedMB / maxMB * 100;

        int memColor = memPercent > 80 ? TEXT_RED : (memPercent > 60 ? TEXT_YELLOW : TEXT_GREEN);
        graphics.drawString(font, String.format("Used: %dMB / %dMB (%.0f%%)", usedMB, totalMB, memPercent),
            textX + 4, textY, memColor, false);
        textY += LINE_HEIGHT;

        // Memory bar
        renderBar(graphics, textX + 4, textY, PANEL_WIDTH - PADDING * 2 - 8, BAR_HEIGHT,
            memPercent / 100f, memColor);
        textY += BAR_HEIGHT + 6;

        // === System Timings ===
        if (!systemTimingsAvg.isEmpty()) {
            graphics.drawString(font, "▸ System Timings", textX, textY, TEXT_CYAN, false);
            textY += LINE_HEIGHT;

            // Sort by time (descending) and render
            var sortedTimings = systemTimingsAvg.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(8)
                .toList();

            for (var entry : sortedTimings) {
                String name = entry.getKey();
                float timeUs = entry.getValue() / 1000f; // microseconds
                int color = timeUs > 1000 ? TEXT_RED : (timeUs > 100 ? TEXT_YELLOW : TEXT_GREEN);

                String timeStr = timeUs >= 1000 ? String.format("%.2fms", timeUs / 1000f)
                                                : String.format("%.0fus", timeUs);

                graphics.drawString(font, String.format("%-16s %s", name, timeStr),
                    textX + 4, textY, color, false);
                textY += LINE_HEIGHT;
            }
            textY += 4;
        }

        // === Active Counters ===
        if (!activeCounters.isEmpty()) {
            graphics.drawString(font, "▸ Active Systems", textX, textY, TEXT_CYAN, false);
            textY += LINE_HEIGHT;

            for (Map.Entry<String, Integer> entry : activeCounters.entrySet()) {
                int val = entry.getValue();
                int color = val > 0 ? TEXT_CYAN : TEXT_GRAY;
                graphics.drawString(font, String.format("%-18s %d", entry.getKey(), val),
                    textX + 4, textY, color, false);
                textY += LINE_HEIGHT;
            }
        }
    }

    private void renderFrameTimeGraph(GuiGraphics graphics, int x, int y, int width, int height) {
        // Sfondo
        graphics.fill(x, y, x + width, y + height, 0xFF000000);

        // Target lines (16.67ms = 60fps, 33.33ms = 30fps)
        float maxMs = 50f; // Scala a 50ms
        int line60 = y + height - (int)(16.67f / maxMs * height);
        int line30 = y + height - (int)(33.33f / maxMs * height);

        // Linea 60fps (verde)
        for (int i = 0; i < width; i += 4) {
            graphics.fill(x + i, line60, x + i + 2, line60 + 1, 0x4400FF00);
        }
        // Linea 30fps (giallo)
        for (int i = 0; i < width; i += 4) {
            graphics.fill(x + i, line30, x + i + 2, line30 + 1, 0x44FFFF00);
        }

        // Grafico frame time - usa float per distribuzione precisa
        float barWidthFloat = (float) width / FRAME_HISTORY_SIZE;
        for (int i = 0; i < FRAME_HISTORY_SIZE; i++) {
            int idx = (frameHistoryIndex + i) % FRAME_HISTORY_SIZE;
            float ft = frameTimeHistory[idx];
            if (ft <= 0) continue;

            int barHeight = (int)(ft / maxMs * height);
            barHeight = Math.min(barHeight, height);

            // Calculate position using float to avoid gaps
            int barX = x + (int)(i * barWidthFloat);
            int barXEnd = x + (int)((i + 1) * barWidthFloat);
            int barY = y + height - barHeight;

            int color = getFrameTimeColor(ft);
            graphics.fill(barX, barY, barXEnd, y + height, color);
        }
    }

    private void renderBar(GuiGraphics graphics, int x, int y, int width, int height, float percent, int color) {
        // Sfondo
        graphics.fill(x, y, x + width, y + height, 0xFF222222);

        // Barra
        int barWidth = (int)(width * Math.min(1f, percent));
        graphics.fill(x, y, x + barWidth, y + height, color);

        // Bordo
        graphics.fill(x, y, x + width, y + 1, 0xFF444444);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF444444);
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private int calculatePanelHeight() {
        int height = PADDING * 2;
        height += LINE_HEIGHT + 4; // Header
        height += LINE_HEIGHT * 5; // Performance section
        height += 24; // Frame graph
        height += LINE_HEIGHT * 2 + BAR_HEIGHT + 6; // Memory
        if (!systemTimingsAvg.isEmpty()) {
            height += LINE_HEIGHT + LINE_HEIGHT * Math.min(8, systemTimingsAvg.size()) + 4;
        }
        if (!activeCounters.isEmpty()) {
            height += LINE_HEIGHT + LINE_HEIGHT * activeCounters.size();
        }
        return height;
    }

    private int getFpsColor(int fps) {
        if (fps >= 60) return TEXT_GREEN;
        if (fps >= 30) return TEXT_YELLOW;
        return TEXT_RED;
    }

    private int getFrameTimeColor(float ms) {
        if (ms <= 16.67f) return TEXT_GREEN;
        if (ms <= 33.33f) return TEXT_YELLOW;
        return TEXT_RED;
    }

    private static Long sumLongs(Long left, Long right) {
        if (left == null) {
            return right == null ? 0L : right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }

    private static Integer sumInts(Integer left, Integer right) {
        if (left == null) {
            return right == null ? 0 : right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }

    // === Public API ===

    public void toggle() {
        enabled = !enabled;
        if (!enabled) {
            reset();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            reset();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reset() {
        systemTimings.clear();
        systemTimingsAvg.clear();
        systemCallCounts.clear();
        activeCounters.clear();
        frameHistoryIndex = 0;
        for (int i = 0; i < FRAME_HISTORY_SIZE; i++) {
            frameTimeHistory[i] = 0;
        }
    }
}
