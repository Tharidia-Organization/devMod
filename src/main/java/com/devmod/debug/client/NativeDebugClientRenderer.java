package com.devmod.debug.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.devmod.DevMod;
import com.devmod.debug.BeesPayload;
import com.devmod.debug.BrainsPayload;
import com.devmod.debug.POIPayload;
import com.devmod.debug.RaidsPayload;
import com.devmod.debug.StructuresPayload;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class NativeDebugClientRenderer {

    private static final int SEARCH_RADIUS = 48;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        // Check if any native debug is enabled
        if (!hasAnyEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Vec3 camPos = event.getCamera().getPosition();

        // Render each enabled feature
        if (DebugRenderBools.isEntityBrains()) {
            renderEntityBrains(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.isPoi()) {
            renderPOI(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.isRaids()) {
            renderRaids(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.isBees()) {
            renderBees(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.isStructures()) {
            renderStructures(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.isGameEvents()) {
            renderGameEvents(poseStack, bufferSource, camPos, mc);
        }

        bufferSource.endBatch();
    }

    private static boolean hasAnyEnabled() {
        return DebugRenderBools.isEntityBrains() ||
               DebugRenderBools.isPoi() ||
               DebugRenderBools.isRaids() ||
               DebugRenderBools.isBees() ||
               DebugRenderBools.isStructures() ||
               DebugRenderBools.isGameEvents();
    }

    /**
     * Render entity brain activity - shows target connection lines and state indicators.
     * <p>
     * The aggressive flag is synced entity data, so it is read straight from the client level.
     * The target is not synced and arrives through {@link BrainsPayload}.
     */
    private static void renderEntityBrains(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                            Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        var level = Objects.requireNonNull(mc.level);
        var player = Objects.requireNonNull(mc.player);

        RenderType lineType = Objects.requireNonNull(RenderType.lines());
        VertexConsumer lineConsumer = bufferSource.getBuffer(lineType);

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        AABB searchBox = Objects.requireNonNull(new AABB(playerPos).inflate(SEARCH_RADIUS));

        for (Mob mob : level.getEntitiesOfClass(Mob.class, searchBox)) {
            Vec3 mobPos = mob.position();
            float mx = (float) mobPos.x;
            float mz = (float) mobPos.z;

            // Draw indicator box above mob - red if aggressive, green if passive
            float boxY = (float) mobPos.y + mob.getBbHeight() + 0.3f;
            float size = 0.15f;
            if (mob.isAggressive()) {
                // Red box for aggressive
                drawBox(lineConsumer, matrix, mx - size, boxY, mz - size,
                        mx + size, boxY + size * 2, mz + size, 1.0f, 0.0f, 0.0f, 1.0f);
            } else {
                // Green box for passive
                drawBox(lineConsumer, matrix, mx - size, boxY, mz - size,
                        mx + size, boxY + size * 2, mz + size, 0.0f, 1.0f, 0.0f, 1.0f);
            }
        }

        for (BrainsPayload.TargetLink link : NativeDebugClientStore.getBrains()) {
            Entity source = level.getEntity(link.entityId());
            Entity target = level.getEntity(link.targetId());
            if (source == null || target == null) continue;

            Vec3 sourcePos = source.position();
            Vec3 targetPos = target.position();

            // Red line to target
            drawLine(lineConsumer, matrix,
                    (float) sourcePos.x, (float) sourcePos.y + source.getBbHeight() * 0.5f, (float) sourcePos.z,
                    (float) targetPos.x, (float) targetPos.y + 1.0f, (float) targetPos.z,
                    1.0f, 0.0f, 0.0f, 1.0f);
        }

        poseStack.popPose();
    }

    /**
     * Render Points of Interest (beds, workstations, etc.) as colored boxes.
     */
    private static void renderPOI(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                   Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        List<POIPayload.POIInfo> poiList = NativeDebugClientStore.getPois();
        if (poiList.isEmpty()) return;

        RenderType lineType = Objects.requireNonNull(RenderType.lines());
        VertexConsumer lineConsumer = bufferSource.getBuffer(lineType);

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        for (POIPayload.POIInfo poi : poiList) {
            String typeName = poi.type();

            // Draw a box at the POI
            float x1 = poi.x();
            float y1 = poi.y();
            float z1 = poi.z();
            float x2 = poi.x() + 1;
            float y2 = poi.y() + 1;
            float z2 = poi.z() + 1;

            // Color based on type
            float r = 0.2f, g = 0.8f, b = 0.2f; // Default green
            if (typeName.contains("bed")) {
                r = 0.8f; g = 0.2f; b = 0.2f; // Red for beds
            } else if (typeName.contains("job")) {
                r = 0.2f; g = 0.2f; b = 0.8f; // Blue for job sites
            } else if (typeName.contains("meeting")) {
                r = 0.8f; g = 0.8f; b = 0.2f; // Yellow for meeting points
            }

            drawBox(lineConsumer, matrix, x1, y1, z1, x2, y2, z2, r, g, b, 1.0f);
        }

        poseStack.popPose();
    }

    /**
     * Render active raids as large red wireframe boxes.
     */
    private static void renderRaids(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                     Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        List<RaidsPayload.RaidInfo> raids = NativeDebugClientStore.getRaids();
        if (raids.isEmpty()) return;

        RenderType lineType = Objects.requireNonNull(RenderType.lines());
        VertexConsumer lineConsumer = bufferSource.getBuffer(lineType);

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        for (RaidsPayload.RaidInfo raid : raids) {
            if (!raid.isActive()) continue;

            // Draw large box around raid center
            float x1 = (float) raid.centerX() - 8;
            float y1 = (float) raid.centerY() - 4;
            float z1 = (float) raid.centerZ() - 8;
            float x2 = (float) raid.centerX() + 8;
            float y2 = (float) raid.centerY() + 8;
            float z2 = (float) raid.centerZ() + 8;

            // Red box for raid
            drawBox(lineConsumer, matrix, x1, y1, z1, x2, y2, z2, 1.0f, 0.0f, 0.0f, 1.0f);

            // Draw a smaller inner box at exact center
            float cx = (float) raid.centerX() + 0.5f;
            float cy = (float) raid.centerY() + 0.5f;
            float cz = (float) raid.centerZ() + 0.5f;
            drawBox(lineConsumer, matrix, cx - 1, cy - 1, cz - 1, cx + 1, cy + 1, cz + 1, 1.0f, 0.5f, 0.0f, 1.0f);
        }

        poseStack.popPose();
    }

    /**
     * Render bee information and hive/flower connections as lines.
     * <p>
     * Nectar and anger are synced entity data, so they are read straight from the client level.
     * The remembered hive and flower are not synced and arrive through {@link BeesPayload}.
     */
    private static void renderBees(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                    Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        var level = Objects.requireNonNull(mc.level);
        var player = Objects.requireNonNull(mc.player);

        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());
        AABB searchBox = Objects.requireNonNull(new AABB(playerPos).inflate(SEARCH_RADIUS));

        RenderType lineType = Objects.requireNonNull(RenderType.lines());
        VertexConsumer lineConsumer = bufferSource.getBuffer(lineType);

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        for (Bee bee : level.getEntitiesOfClass(Bee.class, searchBox)) {
            Vec3 beePos = bee.position();
            float bx = (float) beePos.x;
            float bz = (float) beePos.z;

            // Draw indicator above bee: orange if has nectar, red if angry, white otherwise
            float indicatorY = (float) beePos.y + 1.0f;
            float size = 0.1f;
            if (bee.isAngry()) {
                drawBox(lineConsumer, matrix, bx - size, indicatorY, bz - size,
                        bx + size, indicatorY + size * 2, bz + size, 1.0f, 0.0f, 0.0f, 1.0f);
            } else if (bee.hasNectar()) {
                drawBox(lineConsumer, matrix, bx - size, indicatorY, bz - size,
                        bx + size, indicatorY + size * 2, bz + size, 1.0f, 0.6f, 0.0f, 1.0f);
            }
        }

        for (BeesPayload.BeeInfo info : NativeDebugClientStore.getBees()) {
            Entity bee = level.getEntity(info.entityId());
            if (bee == null) continue;

            Vec3 beePos = bee.position();
            float bx = (float) beePos.x;
            float by = (float) beePos.y + 0.5f;
            float bz = (float) beePos.z;

            // Draw bee's home hive connection if it has one (yellow line)
            BlockPos hive = info.hivePos();
            if (hive != null) {
                drawLine(lineConsumer, matrix, bx, by, bz,
                        hive.getX() + 0.5f, hive.getY() + 0.5f, hive.getZ() + 0.5f,
                        1.0f, 0.8f, 0.0f, 1.0f);

                // Draw a small box at the hive
                drawBox(lineConsumer, matrix,
                        hive.getX(), hive.getY(), hive.getZ(),
                        hive.getX() + 1, hive.getY() + 1, hive.getZ() + 1,
                        1.0f, 0.8f, 0.0f, 1.0f);
            }

            // Draw flower target if bee has one (pink line)
            BlockPos flower = info.flowerPos();
            if (flower != null) {
                drawLine(lineConsumer, matrix, bx, by, bz,
                        flower.getX() + 0.5f, flower.getY() + 0.5f, flower.getZ() + 0.5f,
                        1.0f, 0.4f, 0.7f, 1.0f);
            }
        }

        poseStack.popPose();
    }

    /**
     * Render structure bounding boxes.
     */
    private static void renderStructures(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                          Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        List<StructuresPayload.StructureBox> boxes = NativeDebugClientStore.getStructures();
        if (boxes.isEmpty()) return;

        RenderType lineType = Objects.requireNonNull(RenderType.lines());
        VertexConsumer lineConsumer = bufferSource.getBuffer(lineType);

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        for (StructuresPayload.StructureBox box : boxes) {
            // Draw structure bounding box
            drawBox(lineConsumer, matrix,
                    box.minX(), box.minY(), box.minZ(),
                    box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1,
                    0.0f, 1.0f, 1.0f, 1.0f); // Cyan for structures
        }

        poseStack.popPose();
    }

    // Cache for sculk sensor positions to avoid scanning ~57k blocks every frame
    private static final List<BlockPos> cachedSculkPositions = new ArrayList<>();
    private static BlockPos lastScanCenter = BlockPos.ZERO;
    private static int gameEventScanCooldown = 0;
    private static final int GAME_EVENT_SCAN_INTERVAL = 20; // Re-scan every 20 frames (~1 second)
    private static final int GAME_EVENT_SCAN_RANGE = 24;

    /**
     * Render game events - shows sculk sensors and their detection range.
     * Game events are ephemeral, so we visualize sculk sensors instead.
     * Uses a scan cache to avoid scanning ~57k blocks every frame.
     */
    private static void renderGameEvents(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                          Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        var level = Objects.requireNonNull(mc.level);
        var player = Objects.requireNonNull(mc.player);

        BlockPos playerPos = Objects.requireNonNull(player.blockPosition());

        // Re-scan only when cooldown expires or player moves significantly
        gameEventScanCooldown--;
        if (gameEventScanCooldown <= 0 || playerPos.distManhattan(lastScanCenter) > GAME_EVENT_SCAN_RANGE / 2) {
            gameEventScanCooldown = GAME_EVENT_SCAN_INTERVAL;
            lastScanCenter = playerPos;
            cachedSculkPositions.clear();

            int range = GAME_EVENT_SCAN_RANGE;
            for (int dx = -range; dx <= range; dx++) {
                for (int dy = -range / 2; dy <= range / 2; dy++) {
                    for (int dz = -range; dz <= range; dz++) {
                        BlockPos pos = playerPos.offset(dx, dy, dz);
                        // Client level: sculk sensors are ordinary synced blocks, so the scan
                        // does not have to touch the server thread's chunk map at all.
                        var blockState = level.getBlockState(pos);
                        if (blockState.getBlock() instanceof net.minecraft.world.level.block.SculkSensorBlock) {
                            cachedSculkPositions.add(pos.immutable());
                        }
                    }
                }
            }
        }

        if (cachedSculkPositions.isEmpty()) return;

        RenderType lineType = Objects.requireNonNull(RenderType.lines());
        VertexConsumer lineConsumer = bufferSource.getBuffer(lineType);

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        for (BlockPos pos : cachedSculkPositions) {
            // Draw box around the sculk sensor
            drawBox(lineConsumer, matrix,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                    0.0f, 0.8f, 0.8f, 1.0f); // Cyan for sculk sensors

            // Draw detection range sphere (simplified as a larger box)
            int sensorRange = 8;
            drawBox(lineConsumer, matrix,
                    pos.getX() - sensorRange, pos.getY() - sensorRange, pos.getZ() - sensorRange,
                    pos.getX() + 1 + sensorRange, pos.getY() + 1 + sensorRange, pos.getZ() + 1 + sensorRange,
                    0.0f, 0.4f, 0.4f, 0.5f); // Darker cyan for range
        }

        poseStack.popPose();
    }

    // Helper methods for drawing
    private static void drawLine(VertexConsumer consumer, Matrix4f matrix,
                                  float x1, float y1, float z1, float x2, float y2, float z2,
                                  float r, float g, float b, float a) {
        Matrix4f safeMatrix = Objects.requireNonNull(matrix);
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001f) return;

        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;

        consumer.addVertex(safeMatrix, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz);
        consumer.addVertex(safeMatrix, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }

    private static void drawBox(VertexConsumer consumer, Matrix4f matrix,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float r, float g, float b, float a) {
        Matrix4f safeMatrix = Objects.requireNonNull(matrix);
        // Bottom
        drawLine(consumer, safeMatrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        drawLine(consumer, safeMatrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        drawLine(consumer, safeMatrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        drawLine(consumer, safeMatrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        // Top
        drawLine(consumer, safeMatrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        drawLine(consumer, safeMatrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        drawLine(consumer, safeMatrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        drawLine(consumer, safeMatrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        // Verticals
        drawLine(consumer, safeMatrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        drawLine(consumer, safeMatrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        drawLine(consumer, safeMatrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        drawLine(consumer, safeMatrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }
}
