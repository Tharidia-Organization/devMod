package com.frenkvs.devmod.rendering;

import com.frenkvs.devmod.telemetry.spatial.HeatmapService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FASE 4 REQ-A10: Safe Spot / Camping Visualizer
 *
 * Visualizza le posizioni dove i player campano (safe spots):
 * - Box rosso lampeggiante per safe spots rilevati
 * - Intensità basata su quanto tempo/quanti hit da quella posizione
 * - Labels con statistiche (hit count, duration)
 *
 * I dati vengono caricati dalla telemetria camping.
 *
 * Attivazione: Tasto C (Camping)
 */
public class SafeSpotVisualizer {
    public static final SafeSpotVisualizer INSTANCE = new SafeSpotVisualizer();
    private static final Logger LOGGER = LogUtils.getLogger();

    private boolean enabled = false;
    private final Map<BlockPos, SafeSpotData> safeSpots = new ConcurrentHashMap<>();

    // MEMORY LEAK FIX: Track when it was disabled for auto-clear
    private long disabledSince = 0;
    private static final long AUTO_CLEAR_DELAY_MS = 300_000; // 5 minuti

    // Refresh interval per aggiornamento automatico dati
    private static final long REFRESH_INTERVAL_MS = 5_000; // 5 secondi
    private long lastRefreshTime = 0;

    private SafeSpotVisualizer() {}

    public void toggle() {
        setEnabled(!enabled);
        // When enabled, immediately sync data from telemetry
        if (enabled) {
            syncFromTelemetry();
        }
    }

    /**
     * MEMORY LEAK FIX: Track timestamp when disabled
     */
    public void setEnabled(boolean value) {
        boolean wasEnabled = enabled;
        enabled = value;

        // If disabled, save timestamp
        if (!value && wasEnabled) {
            disabledSince = System.currentTimeMillis();
        }

        // If re-enabled, reset timestamp
        if (value) {
            disabledSince = 0;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Adds or updates a safe spot
     */
    public void addSafeSpot(BlockPos pos, int hitCount, long durationMs) {
        safeSpots.put(pos, new SafeSpotData(pos, hitCount, durationMs, System.currentTimeMillis()));
    }

    /**
     * Loads safe spots from a telemetry data map (single room)
     */
    public void loadFromHeatmap(Map<BlockPos, Integer> campingData) {
        safeSpots.clear();
        campingData.forEach((pos, count) -> {
            // Estimate duration based on count (each count = ~2 seconds of camping)
            long estimatedDuration = count * 2000L;
            safeSpots.put(pos, new SafeSpotData(pos, count, estimatedDuration, System.currentTimeMillis()));
        });
    }

    /**
     * Sincronizza i dati di camping da HeatmapService.
     * Carica tutti i dati di camping da tutte le room.
     */
    public void syncFromTelemetry() {
        Map<String, Map<BlockPos, Integer>> allCampingData = HeatmapService.INSTANCE.getCampingHeatmap();

        safeSpots.clear();
        int totalLoaded = 0;

        for (Map.Entry<String, Map<BlockPos, Integer>> roomEntry : allCampingData.entrySet()) {
            Map<BlockPos, Integer> roomData = roomEntry.getValue();
            for (Map.Entry<BlockPos, Integer> posEntry : roomData.entrySet()) {
                BlockPos pos = posEntry.getKey();
                int count = posEntry.getValue();
                long estimatedDuration = count * 2000L;
                safeSpots.put(pos, new SafeSpotData(pos, count, estimatedDuration, System.currentTimeMillis()));
                totalLoaded++;
            }
        }

        lastRefreshTime = System.currentTimeMillis();

        if (totalLoaded > 0) {
            LOGGER.debug("[SafeSpotVisualizer] Loaded {} camping spots from {} rooms",
                totalLoaded, allCampingData.size());
        }
    }

    /**
     * Sincronizza i dati di camping per una room specifica.
     */
    public void syncFromTelemetry(String roomId) {
        Map<String, Map<BlockPos, Integer>> allCampingData = HeatmapService.INSTANCE.getCampingHeatmap();
        Map<BlockPos, Integer> roomData = allCampingData.get(roomId);

        if (roomData != null) {
            loadFromHeatmap(roomData);
            LOGGER.debug("[SafeSpotVisualizer] Loaded {} camping spots from room '{}'",
                roomData.size(), roomId);
        }
    }

    /**
     * Aggiorna automaticamente i dati se sono passati più di REFRESH_INTERVAL_MS.
     * Da chiamare nel render loop.
     */
    private void refreshIfNeeded() {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        if (now - lastRefreshTime > REFRESH_INTERVAL_MS) {
            syncFromTelemetry();
        }
    }

    /**
     * Pulisce tutti i safe spots
     */
    public void clear() {
        safeSpots.clear();
    }

    /**
     * Render safe spots
     * MEMORY LEAK FIX: Auto-clear if disabled for more than 5 minutes
     */
    public void render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, @Nonnull Vec3 cameraPos) {
        // MEMORY LEAK FIX: Clear data if disabled for too long
        clearIfDisabledFor(AUTO_CLEAR_DELAY_MS);

        if (!enabled) return;

        // Periodically update data from telemetry
        refreshIfNeeded();

        if (safeSpots.isEmpty()) return;

        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        long now = System.currentTimeMillis();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();
        var pose = poseStack.last();

        // Trova il valore massimo per normalizzazione
        int maxHits = safeSpots.values().stream()
                .mapToInt(s -> s.hitCount)
                .max()
                .orElse(1);

        for (SafeSpotData spot : safeSpots.values()) {
            // Solo spot entro 100 blocchi
            double dx = spot.pos.getX() + 0.5 - cameraPos.x;
            double dy = spot.pos.getY() + 1.0 - cameraPos.y;
            double dz = spot.pos.getZ() + 0.5 - cameraPos.z;
            if (dx*dx + dy*dy + dz*dz > 100*100) continue;

            renderSafeSpot(consumer, matrix, pose, spot, maxHits, now, cameraPos);
        }

        poseStack.popPose();
    }

    private void renderSafeSpot(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                @Nonnull PoseStack.Pose pose, @Nonnull SafeSpotData spot,
                                int maxHits, long now, @Nonnull Vec3 cameraPos) {
        // Intensity based on relative count
        float intensity = (float) spot.hitCount / maxHits;

        // Effetto lampeggiante (sinusoidale)
        float blink = (float) (Math.sin(now / 200.0) * 0.3 + 0.7);

        // Colore rosso con alpha variabile
        float r = 1.0f;
        float g = 0.2f * (1.0f - intensity);
        float b = 0.0f;
        float a = (0.4f + intensity * 0.4f) * blink;

        float x = spot.pos.getX();
        float y = spot.pos.getY();
        float z = spot.pos.getZ();

        // Larger box for more intense safe spots
        float size = 1.0f + intensity * 0.5f;
        float halfExtra = (size - 1.0f) / 2.0f;

        float minX = x - halfExtra;
        float minY = y;
        float minZ = z - halfExtra;
        float maxX = x + 1 + halfExtra;
        float maxY = y + 2; // Altezza player
        float maxZ = z + 1 + halfExtra;

        // Wireframe box
        // Bottom
        line(consumer, matrix, pose, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Top
        line(consumer, matrix, pose, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Verticals
        line(consumer, matrix, pose, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);

        // X marks the spot (croce sul pavimento)
        line(consumer, matrix, pose, minX, minY + 0.01f, minZ, maxX, minY + 0.01f, maxZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY + 0.01f, minZ, minX, minY + 0.01f, maxZ, r, g, b, a);

        // Label con statistiche
        Vec3 labelPos = new Vec3(x + 0.5, maxY + 0.5, z + 0.5);
        String label = String.format("SAFE SPOT\n%d hits\n%.1fs",
                spot.hitCount, spot.durationMs / 1000.0);
        DebugRenderer.INSTANCE.addLabel(labelPos, label, 0xFFFF4444, 50);
    }

    private void line(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix, @Nonnull PoseStack.Pose pose,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
    }

    public int getSafeSpotCount() {
        return safeSpots.size();
    }

    /**
     * MEMORY LEAK FIX: Clears data if the visualizer has been disabled for more than N milliseconds
     */
    public void clearIfDisabledFor(long ms) {
        if (disabledSince > 0 && System.currentTimeMillis() - disabledSince > ms) {
            clear();
            disabledSince = 0; // Reset timestamp after clear
        }
    }

    // Internal data class
    private record SafeSpotData(BlockPos pos, int hitCount, long durationMs, long timestamp) {}
}
