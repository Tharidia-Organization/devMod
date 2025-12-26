package com.devmod.client.rendering;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.rendering.shader.VFXShaderRegistry;
import com.devmod.client.ui.unified.persistence.SettingsManager;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * FASE 4 REQ-A1: Heatmap Visualizer
 *
 * Visualizza heatmap in-world con:
 * - Colorazione gradiente (blu→verde→giallo→rosso basato su densità)
 * - Multiple layer (death, movement, camping, stuck, aggro_drop, kiting)
 * - Toggle per ogni tipo di heatmap
 * - Normalizzazione automatica basata su max count
 *
 * Uso:
 * - HeatmapVisualizer.INSTANCE.toggle(HeatmapType.DEATH)
 * - HeatmapVisualizer.INSTANCE.setData(HeatmapType.DEATH, data)
 */

public class HeatmapVisualizer {
    public static final HeatmapVisualizer INSTANCE = new HeatmapVisualizer();

    // Tipi di heatmap supportati
    public enum HeatmapType {
        DEATH(0xFFFF0000),      // Rosso
        MOVEMENT(0xFF00FF00),   // Verde
        CAMPING(0xFFFFFF00),    // Giallo
        STUCK(0xFFFF8000),      // Arancione
        AGGRO_DROP(0xFF8000FF), // Viola
        KITING(0xFF00FFFF),     // Ciano
        LIGHT_SPAWNABLE(0xFFFF0000), // Red (can spawn)
        LIGHT_DARK(0xFFFF8800); // Arancione (buio ma non spawn)

        final int baseColor;
        HeatmapType(int color) { this.baseColor = color; }
    }

    // Data for each heatmap type
    private final Map<HeatmapType, Map<BlockPos, Integer>> heatmapData = new ConcurrentHashMap<>();
    private final Map<HeatmapType, Boolean> enabled = new ConcurrentHashMap<>();
    private final Map<HeatmapType, Integer> maxCounts = new ConcurrentHashMap<>();

    // MEMORY LEAK FIX: Track when it was disabled for auto-clear
    private long disabledSince = 0;
    private static final long AUTO_CLEAR_DELAY_MS = 300_000; // 5 minutes

    // MEMORY LEAK FIX #2: Maximum points per heatmap to avoid infinite accumulation
    private static final int MAX_POINTS_PER_HEATMAP = 10_000;

    private HeatmapVisualizer() {
        // Initialize all types as disabled
        for (HeatmapType type : HeatmapType.values()) {
            enabled.put(type, false);
            heatmapData.put(type, new ConcurrentHashMap<>());
            maxCounts.put(type, 1);
        }
    }

    /**
     * Toggle visualizzazione di un tipo di heatmap
     * MEMORY LEAK FIX: Traccia timestamp quando disabilitato
     */
    public void toggle(HeatmapType type) {
        boolean newState = !Boolean.TRUE.equals(enabled.get(type));
        setEnabled(type, newState);
    }

    /**
     * Abilita/disabilita un tipo di heatmap
     * MEMORY LEAK FIX: Traccia timestamp quando disabilitato
     */
    public void setEnabled(HeatmapType type, boolean value) {
        boolean wasEnabled = enabled.getOrDefault(type, false);
        enabled.put(type, value);

        // If all heatmaps are now disabled, save timestamp
        if (!value && wasEnabled && !hasActiveHeatmaps()) {
            disabledSince = System.currentTimeMillis();
        }

        // If re-enabled, reset timestamp
        if (value) {
            disabledSince = 0;
        }
    }

    public boolean isEnabled(HeatmapType type) {
        return enabled.getOrDefault(type, false);
    }

    /**
     * Sets data for a heatmap (to use when receiving from server)
     */
    public void setData(HeatmapType type, Map<BlockPos, Integer> data) {
        heatmapData.put(type, new ConcurrentHashMap<>(data));

        // Calculate max for normalization
        int max = data.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        maxCounts.put(type, Math.max(1, max));
    }

    /**
     * Adds a single point to the heatmap.
     * MEMORY LEAK FIX #2: Ignores if the heatmap has reached the maximum point limit.
     */
    public void addPoint(HeatmapType type, BlockPos pos, int count) {
        Map<BlockPos, Integer> data = heatmapData.get(type);
        if (data == null) return;

        // MEMORY LEAK FIX #2: Skip if at max capacity (unless updating existing point)
        if (data.size() >= MAX_POINTS_PER_HEATMAP && !data.containsKey(pos)) {
            return; // Silently ignore to prevent unbounded growth
        }

        data.merge(pos, count, (a, b) -> Objects.requireNonNull(a) + Objects.requireNonNull(b));
        Integer newCountVal = data.get(pos);
        int newCount = newCountVal != null ? newCountVal : 0;
        Integer maxCountVal = maxCounts.get(type);
        int maxCount = maxCountVal != null ? maxCountVal : 1;
        if (newCount > maxCount) {
            maxCounts.put(type, newCount);
        }
    }

    /**
     * Pulisce i dati di una heatmap
     */
    public void clear(HeatmapType type) {
        Map<BlockPos, Integer> data = heatmapData.get(type);
        if (data != null) {
            data.clear();
        }
        maxCounts.put(type, 1);
    }

    /**
     * Pulisce tutte le heatmap
     */
    public void clearAll() {
        for (HeatmapType type : HeatmapType.values()) {
            clear(type);
        }
    }

    /**
     * Render enabled heatmaps
     * MEMORY LEAK FIX: Auto-clear if disabled for more than 5 minutes
     */
    public void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos) {
        // MEMORY LEAK FIX: Clear data if disabled for too long
        clearIfDisabledFor(AUTO_CLEAR_DELAY_MS);

        for (HeatmapType type : HeatmapType.values()) {
            if (!Boolean.TRUE.equals(enabled.get(type))) continue;

            Map<BlockPos, Integer> data = heatmapData.get(type);
            if (data == null || data.isEmpty()) continue;

            Integer maxCountVal = maxCounts.get(type);
            int maxCount = maxCountVal != null ? maxCountVal : 1;
            renderHeatmap(poseStack, buffer, cameraPos, data, maxCount, type);
        }
    }

    /**
     * MEMORY LEAK FIX: Clears data if the visualizer has been disabled for more than N milliseconds
     */
    public void clearIfDisabledFor(long ms) {
        if (disabledSince > 0 && System.currentTimeMillis() - disabledSince > ms) {
            clearAll();
            disabledSince = 0; // Reset timestamp after clear
        }
    }

    private void renderHeatmap(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos,
                               Map<BlockPos, Integer> data, int maxCount, HeatmapType type) {
        // Check if GPU shader is available
        boolean useGPU = VFXShaderRegistry.isHeatmapShaderReady();

        if (useGPU) {
            renderHeatmapGPU(poseStack, buffer, cameraPos, data, maxCount, type);
        } else {
            renderHeatmapCPU(poseStack, buffer, cameraPos, data, maxCount, type);
        }
    }

    /**
     * GPU-accelerated heatmap rendering using custom shader.
     * Shader handles: gradient calculation, shimmer effect, edge darkening.
     */
    private void renderHeatmapGPU(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos,
                                   Map<BlockPos, Integer> data, int maxCount, HeatmapType type) {
        ShaderInstance shader = VFXShaderRegistry.getHeatmapShader();
        RenderType renderType = VFXShaderRegistry.getHeatmapRenderType();
        if (shader == null || renderType == null) {
            renderHeatmapCPU(poseStack, buffer, cameraPos, data, maxCount, type);
            return;
        }

        // Set shader uniforms
        setShaderUniform(shader, "GameTime", (float)(System.currentTimeMillis() % 100000) / 1000.0f);
        setShaderUniform(shader, "MaxIntensity", (float) maxCount);
        setShaderUniform(shader, "HeatmapType", type.ordinal());
        setShaderUniform(shader, "BaseAlpha", 0.3f);
        setShaderUniform(shader, "UseGradient",
            (type != HeatmapType.LIGHT_SPAWNABLE && type != HeatmapType.LIGHT_DARK) ? 1 : 0);

        VertexConsumer consumer = buffer.getBuffer(renderType);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());
        var pose = Objects.requireNonNull(poseStack.last());

        double maxDistSqr = SettingsManager.INSTANCE.getSettings().visualizers.getRenderDistanceSq();

        for (Map.Entry<BlockPos, Integer> entry : data.entrySet()) {
            BlockPos pos = entry.getKey();
            int count = entry.getValue();

            double dx = pos.getX() + 0.5 - cameraPos.x;
            double dy = pos.getY() + 0.5 - cameraPos.y;
            double dz = pos.getZ() + 0.5 - cameraPos.z;
            if (dx*dx + dy*dy + dz*dz > maxDistSqr) continue;

            float intensity = Math.min(1.0f, (float) count / maxCount);

            // Pass intensity in red channel - shader will compute gradient
            AABB box = new AABB(pos.getX(), pos.getY() + 1.01, pos.getZ(),
                               pos.getX() + 1, pos.getY() + 1.01 + intensity * 0.5, pos.getZ() + 1);
            renderSolidBoxGPU(consumer, matrix, pose, box, intensity);
        }

        poseStack.popPose();
    }

    /**
     * CPU fallback heatmap rendering.
     */
    private void renderHeatmapCPU(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos,
                                   Map<BlockPos, Integer> data, int maxCount, HeatmapType type) {
        VertexConsumer consumer = buffer.getBuffer(Objects.requireNonNull(RenderType.debugQuads()));

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());
        var pose = Objects.requireNonNull(poseStack.last());

        // Limit rendering to nearby blocks (performance)
        // Use configurable value from Settings
        double maxDistSqr = SettingsManager.INSTANCE.getSettings().visualizers.getRenderDistanceSq();

        for (Map.Entry<BlockPos, Integer> entry : data.entrySet()) {
            BlockPos pos = entry.getKey();
            int count = entry.getValue();

            // Distanza dalla camera
            double dx = pos.getX() + 0.5 - cameraPos.x;
            double dy = pos.getY() + 0.5 - cameraPos.y;
            double dz = pos.getZ() + 0.5 - cameraPos.z;
            if (dx*dx + dy*dy + dz*dz > maxDistSqr) continue;

            // Calculate normalized intensity (0.0 - 1.0)
            float intensity = Math.min(1.0f, (float) count / maxCount);

            // Gradient color based on intensity
            int color = getGradientColor(intensity, type);
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float a = 0.3f + intensity * 0.4f; // Alpha 0.3-0.7 based on intensity

            // Render a semi-transparent block above the floor
            AABB box = new AABB(pos.getX(), pos.getY() + 1.01, pos.getZ(),
                               pos.getX() + 1, pos.getY() + 1.01 + intensity * 0.5, pos.getZ() + 1);
            renderSolidBox(consumer, matrix, pose, box, r, g, b, a);
        }

        poseStack.popPose();
    }

    /**
     * Calcola colore gradiente basato su intensità:
     * 0.0 = blu (freddo, pochi eventi)
     * 0.5 = verde (medio)
     * 0.75 = giallo (alto)
     * 1.0 = rosso (molto alto)
     */
    private int getGradientColor(float intensity, HeatmapType type) {
        // Per LIGHT_SPAWNABLE e LIGHT_DARK usa colori fissi
        if (type == HeatmapType.LIGHT_SPAWNABLE || type == HeatmapType.LIGHT_DARK) {
            return type.baseColor;
        }

        // Gradiente blu → ciano → verde → giallo → rosso
        float r, g, b;

        if (intensity < 0.25f) {
            // Blu → Ciano
            float t = intensity / 0.25f;
            r = 0;
            g = t;
            b = 1;
        } else if (intensity < 0.5f) {
            // Ciano → Verde
            float t = (intensity - 0.25f) / 0.25f;
            r = 0;
            g = 1;
            b = 1 - t;
        } else if (intensity < 0.75f) {
            // Verde → Giallo
            float t = (intensity - 0.5f) / 0.25f;
            r = t;
            g = 1;
            b = 0;
        } else {
            // Giallo → Rosso
            float t = (intensity - 0.75f) / 0.25f;
            r = 1;
            g = 1 - t;
            b = 0;
        }

        return 0xFF000000 | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    private void renderSolidBox(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, AABB box,
                                float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // Top face only (lighter for heatmap)
        Objects.requireNonNull(consumer.addVertex(Objects.requireNonNull(matrix), minX, maxY, minZ).setColor(r, g, b, a)).setNormal(Objects.requireNonNull(pose), 0f, 1f, 0f);
        Objects.requireNonNull(consumer.addVertex(Objects.requireNonNull(matrix), minX, maxY, maxZ).setColor(r, g, b, a)).setNormal(Objects.requireNonNull(pose), 0f, 1f, 0f);
        Objects.requireNonNull(consumer.addVertex(Objects.requireNonNull(matrix), maxX, maxY, maxZ).setColor(r, g, b, a)).setNormal(Objects.requireNonNull(pose), 0f, 1f, 0f);
        Objects.requireNonNull(consumer.addVertex(Objects.requireNonNull(matrix), maxX, maxY, minZ).setColor(r, g, b, a)).setNormal(Objects.requireNonNull(pose), 0f, 1f, 0f);
    }

    /**
     * GPU version: passes intensity in color.r for shader gradient calculation.
     */
    private void renderSolidBoxGPU(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, AABB box,
                                    float intensity) {
        float minX = (float) box.minX;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // Pass intensity in red channel - shader computes gradient
        float a = 0.3f + intensity * 0.4f;
        Matrix4f safeMatrix = Objects.requireNonNull(matrix);
        PoseStack.Pose safePose = Objects.requireNonNull(pose);
        consumer.addVertex(safeMatrix, minX, maxY, minZ).setColor(intensity, intensity, intensity, a).setNormal(safePose, 0f, 1f, 0f);
        consumer.addVertex(safeMatrix, minX, maxY, maxZ).setColor(intensity, intensity, intensity, a).setNormal(safePose, 0f, 1f, 0f);
        consumer.addVertex(safeMatrix, maxX, maxY, maxZ).setColor(intensity, intensity, intensity, a).setNormal(safePose, 0f, 1f, 0f);
        consumer.addVertex(safeMatrix, maxX, maxY, minZ).setColor(intensity, intensity, intensity, a).setNormal(safePose, 0f, 1f, 0f);
    }

    // Shader uniform helper methods
    private void setShaderUniform(ShaderInstance shader, String name, float value) {
        try {
            var uniform = shader.getUniform(Objects.requireNonNull(name));
            if (uniform != null) {
                uniform.set(value);
            }
        } catch (Exception ignored) {}
    }

    private void setShaderUniform(ShaderInstance shader, String name, int value) {
        try {
            var uniform = shader.getUniform(Objects.requireNonNull(name));
            if (uniform != null) {
                uniform.set(value);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Checks if at least one heatmap is active
     */
    public boolean hasActiveHeatmaps() {
        return enabled.values().stream().anyMatch(Boolean::booleanValue);
    }

    /**
     * Lista dei tipi di heatmap attivi (per UI)
     */
    public String getActiveTypesString() {
        StringBuilder sb = new StringBuilder();
        for (HeatmapType type : HeatmapType.values()) {
            if (Boolean.TRUE.equals(enabled.get(type))) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(type.name());
            }
        }
        return sb.length() > 0 ? sb.toString() : "None";
    }

    /**
     * Carica i dati da HeatmapService per un tipo specifico.
     * Combina tutti i dati di tutte le room in un'unica mappa.
     *
     * @param type Il tipo di heatmap da caricare
     * @return Numero di punti caricati
     */
    public int loadDataFromService(HeatmapType type) {
        var service = com.devmod.telemetry.spatial.HeatmapService.INSTANCE;
        Map<String, Map<BlockPos, Integer>> sourceData = switch (type) {
            case DEATH -> service.getDeathHeatmap();
            case MOVEMENT -> service.getMovementHeatmap();
            case CAMPING -> service.getCampingHeatmap();
            case STUCK -> service.getStuckHeatmap();
            case AGGRO_DROP -> service.getAggroDropHeatmap();
            case KITING -> service.getKitingHeatmap();
            default -> null;
        };

        if (sourceData == null || sourceData.isEmpty()) {
            return 0;
        }

        // Combine all room data into a single map
        Map<BlockPos, Integer> combined = new ConcurrentHashMap<>();
        for (Map<BlockPos, Integer> roomData : sourceData.values()) {
            roomData.forEach((pos, count) -> combined.merge(pos, count, (a, b) -> Objects.requireNonNull(a) + Objects.requireNonNull(b)));
        }

        // Set data in the visualizer
        setData(type, combined);
        return combined.size();
    }

    /**
     * Carica i dati da HeatmapService per tutti i tipi principali.
     *
     * @return Numero totale di punti caricati
     */
    public int loadAllDataFromService() {
        int total = 0;
        HeatmapType[] mainTypes = {
            HeatmapType.DEATH,
            HeatmapType.MOVEMENT,
            HeatmapType.CAMPING,
            HeatmapType.STUCK,
            HeatmapType.AGGRO_DROP,
            HeatmapType.KITING
        };

        for (HeatmapType type : mainTypes) {
            total += loadDataFromService(type);
        }
        return total;
    }

    /**
     * Verifica se ci sono dati disponibili nel service per un tipo.
     */
    public boolean hasServiceData(HeatmapType type) {
        var service = com.devmod.telemetry.spatial.HeatmapService.INSTANCE;
        Map<String, Map<BlockPos, Integer>> sourceData = switch (type) {
            case DEATH -> service.getDeathHeatmap();
            case MOVEMENT -> service.getMovementHeatmap();
            case CAMPING -> service.getCampingHeatmap();
            case STUCK -> service.getStuckHeatmap();
            case AGGRO_DROP -> service.getAggroDropHeatmap();
            case KITING -> service.getKitingHeatmap();
            default -> null;
        };
        return sourceData != null && !sourceData.isEmpty();
    }

    /**
     * Conta quanti punti sono attualmente nel visualizer per un tipo.
     */
    public int getDataCount(HeatmapType type) {
        Map<BlockPos, Integer> data = heatmapData.get(type);
        return data != null ? data.size() : 0;
    }

    /**
     * Cycles to the next heatmap type, enabling it and disabling others.
     * Used by RadialMenuScreen.
     */
    public void cycleNextType() {
        HeatmapType[] types = HeatmapType.values();

        // Find currently active type (first one)
        int currentIndex = -1;
        for (int i = 0; i < types.length; i++) {
            if (isEnabled(types[i])) {
                currentIndex = i;
                break;
            }
        }

        // Disable all
        for (HeatmapType type : types) {
            enabled.put(type, false);
        }

        // Enable next type (or first if none was active)
        int nextIndex = (currentIndex + 1) % types.length;
        HeatmapType nextType = types[nextIndex];
        enabled.put(nextType, true);

        // Load data from service for the new type
        loadDataFromService(nextType);
    }
}
