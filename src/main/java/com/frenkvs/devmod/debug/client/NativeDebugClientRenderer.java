package com.frenkvs.devmod.debug.client;

import com.frenkvs.devmod.DevMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * Client-side renderer for native Minecraft debug features.
 * This replaces the broken mixin approach by directly accessing mob data
 * in singleplayer/LAN worlds and rendering our own visualization.
 */
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

        // Only works in singleplayer (where we have direct access to mob data)
        if (mc.getSingleplayerServer() == null) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Vec3 camPos = event.getCamera().getPosition();

        // Render each enabled feature
        if (DebugRenderBools.ENTITY_PATHING) {
            renderEntityPathing(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.ENTITY_GOALS) {
            renderEntityGoals(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.ENTITY_BRAINS) {
            renderEntityBrains(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.POI) {
            renderPOI(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.RAIDS) {
            renderRaids(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.BEES) {
            renderBees(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.STRUCTURES) {
            renderStructures(poseStack, bufferSource, camPos, mc);
        }

        if (DebugRenderBools.GAME_EVENTS) {
            renderGameEvents(poseStack, bufferSource, camPos, mc);
        }

        bufferSource.endBatch();
    }

    private static boolean hasAnyEnabled() {
        return DebugRenderBools.ENTITY_PATHING ||
               DebugRenderBools.ENTITY_GOALS ||
               DebugRenderBools.ENTITY_BRAINS ||
               DebugRenderBools.POI ||
               DebugRenderBools.RAIDS ||
               DebugRenderBools.BEES ||
               DebugRenderBools.STRUCTURES ||
               DebugRenderBools.GAME_EVENTS;
    }

    /**
     * Render mob navigation paths similar to Minecraft's PathfindingDebugRenderer.
     */
    private static void renderEntityPathing(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                            Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        BlockPos playerPos = mc.player.blockPosition();
        AABB searchBox = new AABB(playerPos).inflate(SEARCH_RADIUS);

        // Get mobs from server level (singleplayer only)
        var server = mc.getSingleplayerServer();
        if (server == null) return;

        var serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return;

        // Copy to list to avoid ConcurrentModificationException
        List<Mob> mobs;
        try {
            mobs = new ArrayList<>(serverLevel.getEntitiesOfClass(Mob.class, searchBox));
        } catch (ConcurrentModificationException e) {
            return; // Skip this frame if entities are being modified
        }

        for (Mob mob : mobs) {
            Path path = mob.getNavigation().getPath();
            if (path == null || path.isDone()) continue;

            renderPath(poseStack, bufferSource, camPos, path, mob);
        }
    }

    /**
     * Render a single mob's path.
     */
    private static void renderPath(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                    Vec3 camPos, Path path, Mob mob) {
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        // Draw path nodes
        int nodeCount = path.getNodeCount();
        int currentNode = path.getNextNodeIndex();

        for (int i = 0; i < nodeCount - 1; i++) {
            Node node = path.getNode(i);
            Node nextNode = path.getNode(i + 1);

            float x1 = node.x + 0.5f;
            float y1 = node.y + 0.1f;
            float z1 = node.z + 0.5f;
            float x2 = nextNode.x + 0.5f;
            float y2 = nextNode.y + 0.1f;
            float z2 = nextNode.z + 0.5f;

            // Color: green for completed, yellow for current, red for future
            float r, g, b;
            if (i < currentNode) {
                r = 0.0f; g = 1.0f; b = 0.0f; // Green - completed
            } else if (i == currentNode) {
                r = 1.0f; g = 1.0f; b = 0.0f; // Yellow - current
            } else {
                r = 1.0f; g = 0.3f; b = 0.3f; // Light red - future
            }

            // Draw line segment
            drawLine(lineConsumer, matrix, x1, y1, z1, x2, y2, z2, r, g, b, 1.0f);
        }

        // Draw node markers
        for (int i = 0; i < nodeCount; i++) {
            Node node = path.getNode(i);
            float x = node.x + 0.5f;
            float y = node.y + 0.1f;
            float z = node.z + 0.5f;
            float size = 0.1f;

            // Small box at each node
            float r = i == currentNode ? 1.0f : 0.5f;
            float g = i == currentNode ? 1.0f : 0.5f;
            float b = 0.0f;

            drawBox(lineConsumer, matrix, x - size, y, z - size, x + size, y + size * 2, z + size, r, g, b, 1.0f);
        }

        // Draw target position
        BlockPos target = path.getTarget();
        if (target != null) {
            float tx = target.getX() + 0.5f;
            float ty = target.getY() + 0.1f;
            float tz = target.getZ() + 0.5f;
            drawBox(lineConsumer, matrix, tx - 0.3f, ty, tz - 0.3f, tx + 0.3f, ty + 0.6f, tz + 0.3f,
                    0.0f, 1.0f, 1.0f, 1.0f); // Cyan for target
        }

        poseStack.popPose();
    }

    /**
     * Render mob AI goals.
     */
    private static void renderEntityGoals(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                          Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        var server = mc.getSingleplayerServer();
        if (server == null) return;

        var serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return;

        BlockPos playerPos = mc.player.blockPosition();
        AABB searchBox = new AABB(playerPos).inflate(SEARCH_RADIUS);

        // Copy to list to avoid ConcurrentModificationException
        List<Mob> mobs;
        try {
            mobs = new ArrayList<>(serverLevel.getEntitiesOfClass(Mob.class, searchBox));
        } catch (ConcurrentModificationException e) {
            return; // Skip this frame if entities are being modified
        }

        for (Mob mob : mobs) {
            renderMobGoals(poseStack, bufferSource, camPos, mob);
        }
    }

    /**
     * Render goals for a single mob as floating text.
     */
    private static void renderMobGoals(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                        Vec3 camPos, Mob mob) {
        Minecraft mc = Minecraft.getInstance();

        // Collect goals
        List<String> goalLines = new ArrayList<>();

        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals()) {
            String prefix = goal.isRunning() ? "§a▶ " : "§7○ ";
            String name = goal.getGoal().getClass().getSimpleName();
            goalLines.add(prefix + goal.getPriority() + ": " + name);
        }

        // Collect target goals
        for (WrappedGoal goal : mob.targetSelector.getAvailableGoals()) {
            String prefix = goal.isRunning() ? "§c▶ " : "§8○ ";
            String name = "[T] " + goal.getGoal().getClass().getSimpleName();
            goalLines.add(prefix + goal.getPriority() + ": " + name);
        }

        if (goalLines.isEmpty()) return;

        // Render as floating text above mob
        Vec3 mobPos = mob.position();
        double dx = mobPos.x - camPos.x;
        double dy = mobPos.y + mob.getBbHeight() + 0.5 - camPos.y;
        double dz = mobPos.z - camPos.z;

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025f, -0.025f, 0.025f);

        float yOffset = 0;
        for (String line : goalLines) {
            mc.font.drawInBatch(line, -mc.font.width(line) / 2.0f, yOffset, 0xFFFFFF,
                    false, poseStack.last().pose(), bufferSource,
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0x40000000, 15728880);
            yOffset += 10;
        }

        poseStack.popPose();
    }

    /**
     * Render entity brain activity - shows target connection lines and state indicators.
     */
    private static void renderEntityBrains(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                            Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        var server = mc.getSingleplayerServer();
        if (server == null) return;

        var serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return;

        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        BlockPos playerPos = mc.player.blockPosition();
        AABB searchBox = new AABB(playerPos).inflate(SEARCH_RADIUS);

        // Copy to list to avoid ConcurrentModificationException
        List<Mob> mobs;
        try {
            mobs = new ArrayList<>(serverLevel.getEntitiesOfClass(Mob.class, searchBox));
        } catch (ConcurrentModificationException e) {
            poseStack.popPose();
            return; // Skip this frame if entities are being modified
        }

        for (Mob mob : mobs) {
            Vec3 mobPos = mob.position();
            float mx = (float) mobPos.x;
            float my = (float) mobPos.y + mob.getBbHeight() * 0.5f;
            float mz = (float) mobPos.z;

            // Draw line to target if aggressive
            if (mob.getTarget() != null) {
                Vec3 targetPos = mob.getTarget().position();
                // Red line to target
                drawLine(lineConsumer, matrix, mx, my, mz,
                        (float) targetPos.x, (float) targetPos.y + 1.0f, (float) targetPos.z,
                        1.0f, 0.0f, 0.0f, 1.0f);
            }

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

        poseStack.popPose();
    }

    /**
     * Render Points of Interest (beds, workstations, etc.) as colored boxes.
     */
    private static void renderPOI(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                   Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        var server = mc.getSingleplayerServer();
        if (server == null) return;

        var serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return;

        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        BlockPos playerPos = mc.player.blockPosition();

        // Collect POIs into a list first to avoid lambda issues
        List<net.minecraft.world.entity.ai.village.poi.PoiRecord> poiList = serverLevel.getPoiManager().getInRange(
            holder -> true,
            playerPos,
            48,
            net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY
        ).toList();

        for (var record : poiList) {
            BlockPos pos = record.getPos();
            String typeName = record.getPoiType().getRegisteredName();

            // Draw a box at the POI
            float x1 = pos.getX();
            float y1 = pos.getY();
            float z1 = pos.getZ();
            float x2 = pos.getX() + 1;
            float y2 = pos.getY() + 1;
            float z2 = pos.getZ() + 1;

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

        var server = mc.getSingleplayerServer();
        if (server == null) return;

        var serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return;

        Raids raids = serverLevel.getRaids();
        if (raids == null) return;

        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        BlockPos playerPos = mc.player.blockPosition();
        Raid nearestRaid = raids.getNearbyRaid(playerPos, 128);

        if (nearestRaid != null && nearestRaid.isActive()) {
            BlockPos center = nearestRaid.getCenter();

            // Draw large box around raid center
            float x1 = center.getX() - 8;
            float y1 = center.getY() - 4;
            float z1 = center.getZ() - 8;
            float x2 = center.getX() + 8;
            float y2 = center.getY() + 8;
            float z2 = center.getZ() + 8;

            // Red box for raid
            drawBox(lineConsumer, matrix, x1, y1, z1, x2, y2, z2, 1.0f, 0.0f, 0.0f, 1.0f);

            // Draw a smaller inner box at exact center
            float cx = center.getX() + 0.5f;
            float cy = center.getY() + 0.5f;
            float cz = center.getZ() + 0.5f;
            drawBox(lineConsumer, matrix, cx - 1, cy - 1, cz - 1, cx + 1, cy + 1, cz + 1, 1.0f, 0.5f, 0.0f, 1.0f);
        }

        poseStack.popPose();
    }

    /**
     * Render bee information and hive/flower connections as lines.
     */
    private static void renderBees(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                    Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        var server = mc.getSingleplayerServer();
        if (server == null) return;

        var serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return;

        BlockPos playerPos = mc.player.blockPosition();
        AABB searchBox = new AABB(playerPos).inflate(SEARCH_RADIUS);

        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        // Copy to list to avoid ConcurrentModificationException
        List<Bee> bees;
        try {
            bees = new ArrayList<>(serverLevel.getEntitiesOfClass(Bee.class, searchBox));
        } catch (ConcurrentModificationException e) {
            poseStack.popPose();
            return; // Skip this frame if entities are being modified
        }

        for (Bee bee : bees) {
            Vec3 beePos = bee.position();
            float bx = (float) beePos.x;
            float by = (float) beePos.y + 0.5f;
            float bz = (float) beePos.z;

            // Draw bee's home hive connection if it has one (yellow line)
            if (bee.getHivePos() != null) {
                BlockPos hivePos = bee.getHivePos();
                drawLine(lineConsumer, matrix, bx, by, bz,
                        hivePos.getX() + 0.5f, hivePos.getY() + 0.5f, hivePos.getZ() + 0.5f,
                        1.0f, 0.8f, 0.0f, 1.0f);

                // Draw a small box at the hive
                drawBox(lineConsumer, matrix,
                        hivePos.getX(), hivePos.getY(), hivePos.getZ(),
                        hivePos.getX() + 1, hivePos.getY() + 1, hivePos.getZ() + 1,
                        1.0f, 0.8f, 0.0f, 1.0f);
            }

            // Draw flower target if bee has one (pink line)
            if (bee.getSavedFlowerPos() != null) {
                BlockPos flowerPos = bee.getSavedFlowerPos();
                drawLine(lineConsumer, matrix, bx, by, bz,
                        flowerPos.getX() + 0.5f, flowerPos.getY() + 0.5f, flowerPos.getZ() + 0.5f,
                        1.0f, 0.4f, 0.7f, 1.0f);
            }

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

        poseStack.popPose();
    }

    /**
     * Render structure bounding boxes.
     */
    private static void renderStructures(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                          Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        var server = mc.getSingleplayerServer();
        if (server == null) return;

        ServerLevel serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return;

        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        BlockPos playerPos = mc.player.blockPosition();
        SectionPos sectionPos = SectionPos.of(playerPos);

        // Get structures in nearby chunks
        StructureManager structureManager = serverLevel.structureManager();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                ChunkAccess chunk = serverLevel.getChunk(sectionPos.x() + dx, sectionPos.z() + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                var structureStarts = chunk.getAllStarts();
                for (var entry : structureStarts.entrySet()) {
                    var structureStart = entry.getValue();
                    if (structureStart == null) continue;

                    BoundingBox bb = structureStart.getBoundingBox();

                    // Draw structure bounding box
                    drawBox(lineConsumer, matrix,
                            bb.minX(), bb.minY(), bb.minZ(),
                            bb.maxX() + 1, bb.maxY() + 1, bb.maxZ() + 1,
                            0.0f, 1.0f, 1.0f, 1.0f); // Cyan for structures
                }
            }
        }

        poseStack.popPose();
    }

    /**
     * Render game events - shows sculk sensors and their detection range.
     * Game events are ephemeral, so we visualize sculk sensors instead.
     */
    private static void renderGameEvents(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                          Vec3 camPos, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        var server = mc.getSingleplayerServer();
        if (server == null) return;

        var serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return;

        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        BlockPos playerPos = mc.player.blockPosition();

        // Search for sculk sensors nearby and draw their detection spheres
        int range = 24;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range / 2; dy <= range / 2; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    var blockState = serverLevel.getBlockState(pos);

                    // Check if it's a sculk sensor or calibrated sculk sensor
                    if (blockState.getBlock() instanceof net.minecraft.world.level.block.SculkSensorBlock) {
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
                }
            }
        }

        poseStack.popPose();
    }

    // Helper methods for drawing
    private static void drawLine(VertexConsumer consumer, Matrix4f matrix,
                                  float x1, float y1, float z1, float x2, float y2, float z2,
                                  float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001f) return;

        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }

    private static void drawBox(VertexConsumer consumer, Matrix4f matrix,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float r, float g, float b, float a) {
        // Bottom
        drawLine(consumer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        drawLine(consumer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        drawLine(consumer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        drawLine(consumer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        // Top
        drawLine(consumer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        drawLine(consumer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        drawLine(consumer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        drawLine(consumer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        // Verticals
        drawLine(consumer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        drawLine(consumer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        drawLine(consumer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        drawLine(consumer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }
}
