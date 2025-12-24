package com.devmod.client.rendering;

import com.devmod.DevMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Chunk Performance Visualizer (M54).
 *
 * Renders chunk borders with color-coded performance indicators:
 * - Green: Low load (0-10 entities, 0-5 block entities)
 * - Yellow: Medium load (10-30 entities, 5-15 block entities)
 * - Red: High load (30+ entities, 15+ block entities)
 *
 * Also shows:
 * - Entity count per chunk
 * - Block entity count (tile entities)
 * - Chunk loading state
 *
 * Toggle via keybind.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class ChunkPerformanceVisualizer {
    public static final ChunkPerformanceVisualizer INSTANCE = new ChunkPerformanceVisualizer();

    private boolean enabled = false;
    private int renderRadius = 4; // Chunks to render around player

    // Cached chunk data
    private final Map<ChunkPos, ChunkPerfData> chunkDataCache = new HashMap<>();
    private long lastCacheUpdateMs = 0;
    private static final long CACHE_UPDATE_INTERVAL_MS = 500;

    // Thresholds for performance coloring
    private static final int ENTITY_LOW = 10;
    private static final int ENTITY_HIGH = 30;
    private static final int TILE_LOW = 5;
    private static final int TILE_HIGH = 15;

    // Colors (RGBA floats)
    private static final float[] COLOR_GREEN = {0.0f, 1.0f, 0.0f, 0.4f};
    private static final float[] COLOR_YELLOW = {1.0f, 1.0f, 0.0f, 0.4f};
    private static final float[] COLOR_RED = {1.0f, 0.0f, 0.0f, 0.4f};
    private static final float[] COLOR_BORDER = {1.0f, 1.0f, 1.0f, 0.8f};

    private ChunkPerformanceVisualizer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!INSTANCE.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        INSTANCE.render(event.getPoseStack(), event.getCamera().getPosition(), mc.level);
    }

    private void render(PoseStack poseStack, Vec3 cameraPos, Level level) {
        // Update cache periodically
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdateMs > CACHE_UPDATE_INTERVAL_MS) {
            updateCache(level);
            lastCacheUpdateMs = now;
        }

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Calculate renderY once using camera position for smooth movement
        Minecraft mc = Minecraft.getInstance();
        float renderY = mc.player != null ? (float) mc.player.getY() + 0.1f : 64.0f;

        for (Map.Entry<ChunkPos, ChunkPerfData> entry : chunkDataCache.entrySet()) {
            ChunkPos chunkPos = entry.getKey();
            ChunkPerfData data = entry.getValue();

            renderChunkOverlay(poseStack, bufferSource, chunkPos, data, level, renderY);
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    private void updateCache(Level level) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        ChunkPos playerChunk = player.chunkPosition();
        chunkDataCache.clear();

        for (int dx = -renderRadius; dx <= renderRadius; dx++) {
            for (int dz = -renderRadius; dz <= renderRadius; dz++) {
                ChunkPos pos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);

                if (!level.hasChunk(pos.x, pos.z)) continue;

                LevelChunk chunk = level.getChunk(pos.x, pos.z);
                ChunkPerfData data = analyzeChunk(chunk, level);
                chunkDataCache.put(pos, data);
            }
        }
    }

    private ChunkPerfData analyzeChunk(LevelChunk chunk, Level level) {
        int entityCount = 0;
        int tileEntityCount = 0;

        // Count entities in chunk
        AABB chunkBox = new AABB(
                chunk.getPos().getMinBlockX(), level.getMinBuildHeight(), chunk.getPos().getMinBlockZ(),
                chunk.getPos().getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getPos().getMaxBlockZ() + 1
        );

        List<Entity> entities = level.getEntities(null, chunkBox);
        entityCount = entities.size();

        // Count block entities
        Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
        tileEntityCount = blockEntities.size();

        // Calculate performance score (0 = good, 1 = bad)
        // Score is 0 if below LOW threshold, scales up to 1.0 at HIGH threshold
        double entityScore = entityCount <= ENTITY_LOW ? 0.0
            : Math.min(1.0, (double) (entityCount - ENTITY_LOW) / (ENTITY_HIGH - ENTITY_LOW));
        double tileScore = tileEntityCount <= TILE_LOW ? 0.0
            : Math.min(1.0, (double) (tileEntityCount - TILE_LOW) / (TILE_HIGH - TILE_LOW));
        double perfScore = (entityScore + tileScore) / 2.0;

        return new ChunkPerfData(entityCount, tileEntityCount, perfScore);
    }

    private void renderChunkOverlay(PoseStack poseStack, MultiBufferSource bufferSource,
                                     ChunkPos chunkPos, ChunkPerfData data, Level level,
                                     float renderY) {
        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX() + 1;
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ() + 1;

        // Get color based on performance
        float[] color = getColorForPerformance(data.perfScore);

        // Render filled quad at player height
        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.debugQuads()));
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        // Top face (semi-transparent)
        consumer.addVertex(matrix, minX, renderY, minZ).setColor(color[0], color[1], color[2], color[3] * 0.3f);
        consumer.addVertex(matrix, minX, renderY, maxZ).setColor(color[0], color[1], color[2], color[3] * 0.3f);
        consumer.addVertex(matrix, maxX, renderY, maxZ).setColor(color[0], color[1], color[2], color[3] * 0.3f);
        consumer.addVertex(matrix, maxX, renderY, minZ).setColor(color[0], color[1], color[2], color[3] * 0.3f);

        // Render chunk borders (lines)
        VertexConsumer lineConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.debugLineStrip(2.0)));

        float borderAlpha = 0.8f;
        // Bottom border
        lineConsumer.addVertex(matrix, minX, renderY, minZ).setColor(COLOR_BORDER[0], COLOR_BORDER[1], COLOR_BORDER[2], borderAlpha);
        lineConsumer.addVertex(matrix, maxX, renderY, minZ).setColor(COLOR_BORDER[0], COLOR_BORDER[1], COLOR_BORDER[2], borderAlpha);
        lineConsumer.addVertex(matrix, maxX, renderY, maxZ).setColor(COLOR_BORDER[0], COLOR_BORDER[1], COLOR_BORDER[2], borderAlpha);
        lineConsumer.addVertex(matrix, minX, renderY, maxZ).setColor(COLOR_BORDER[0], COLOR_BORDER[1], COLOR_BORDER[2], borderAlpha);
        lineConsumer.addVertex(matrix, minX, renderY, minZ).setColor(COLOR_BORDER[0], COLOR_BORDER[1], COLOR_BORDER[2], borderAlpha);
    }

    private float[] getColorForPerformance(double score) {
        if (score < 0.3) {
            return COLOR_GREEN;
        } else if (score < 0.6) {
            return COLOR_YELLOW;
        } else {
            return COLOR_RED;
        }
    }

    // === Public API ===

    public void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            chunkDataCache.clear();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setRenderRadius(int radius) {
        this.renderRadius = Math.max(1, Math.min(radius, 16));
    }

    public int getRenderRadius() {
        return renderRadius;
    }

    /**
     * Get performance data for the chunk at given position.
     */
    public ChunkPerfData getChunkData(ChunkPos pos) {
        return chunkDataCache.get(pos);
    }

    /**
     * Get summary of all cached chunk data.
     */
    public ChunkPerfSummary getSummary() {
        if (chunkDataCache.isEmpty()) {
            return new ChunkPerfSummary(0, 0, 0, 0, 0);
        }

        int totalChunks = chunkDataCache.size();
        int totalEntities = 0;
        int totalTileEntities = 0;
        int highLoadChunks = 0;

        for (ChunkPerfData data : chunkDataCache.values()) {
            totalEntities += data.entityCount;
            totalTileEntities += data.tileEntityCount;
            if (data.perfScore >= 0.6) {
                highLoadChunks++;
            }
        }

        double avgPerfScore = chunkDataCache.values().stream()
                .mapToDouble(d -> d.perfScore)
                .average()
                .orElse(0);

        return new ChunkPerfSummary(totalChunks, totalEntities, totalTileEntities, highLoadChunks, avgPerfScore);
    }

    // === Data Classes ===

    /**
     * Performance data for a single chunk.
     */
    public record ChunkPerfData(int entityCount, int tileEntityCount, double perfScore) {}

    /**
     * Summary of chunk performance data.
     */
    public record ChunkPerfSummary(
            int totalChunks,
            int totalEntities,
            int totalTileEntities,
            int highLoadChunks,
            double avgPerfScore
    ) {}
}
