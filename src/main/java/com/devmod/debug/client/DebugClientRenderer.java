package com.devmod.debug.client;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

import com.devmod.debug.EntityGoalsPayload;
import com.devmod.debug.EntityPathingPayload;
import com.devmod.debug.POIPayload;
import com.devmod.debug.RaidsPayload;

public class DebugClientRenderer {
    public static final DebugClientRenderer INSTANCE = new DebugClientRenderer();

    // Cache for received debug data
    private final Map<Integer, EntityPathingPayload> pathingData = new ConcurrentHashMap<>();
    private final Map<Integer, EntityGoalsPayload> goalsData = new ConcurrentHashMap<>();
    private final Map<String, POIPayload.POIInfo> poiData = new ConcurrentHashMap<>();
    private final Map<Integer, RaidsPayload.RaidInfo> raidsData = new ConcurrentHashMap<>();

    // Feature toggles (client-side mirrors of server state)
    private boolean pathingEnabled = false;
    private boolean goalsEnabled = false;
    private boolean poiEnabled = false;
    private boolean raidsEnabled = false;

    // Cache expiry
    private static final long DATA_EXPIRY_MS = 1000; // Data expires after 1 second without updates
    private final Map<Integer, Long> pathingTimestamps = new ConcurrentHashMap<>();
    private final Map<Integer, Long> goalsTimestamps = new ConcurrentHashMap<>();

    private DebugClientRenderer() {}

    // ========== Data Reception ==========

    public void onPathingData(EntityPathingPayload payload) {
        if (payload.nodes().isEmpty()) {
            pathingData.remove(payload.entityId());
            pathingTimestamps.remove(payload.entityId());
        } else {
            pathingData.put(payload.entityId(), payload);
            pathingTimestamps.put(payload.entityId(), System.currentTimeMillis());
        }
    }

    public void onGoalsData(EntityGoalsPayload payload) {
        if (payload.goals().isEmpty() && payload.targetGoals().isEmpty()) {
            goalsData.remove(payload.entityId());
            goalsTimestamps.remove(payload.entityId());
        } else {
            goalsData.put(payload.entityId(), payload);
            goalsTimestamps.put(payload.entityId(), System.currentTimeMillis());
        }
    }

    public void onPOIData(POIPayload payload) {
        poiData.clear();
        for (POIPayload.POIInfo poi : payload.pois()) {
            String key = poi.x() + "," + poi.y() + "," + poi.z();
            poiData.put(key, poi);
        }
    }

    public void onRaidsData(RaidsPayload payload) {
        raidsData.clear();
        for (RaidsPayload.RaidInfo raid : payload.raids()) {
            raidsData.put(raid.raidId(), raid);
        }
    }

    // ========== Toggle Features ==========

    public void setPathingEnabled(boolean enabled) {
        this.pathingEnabled = enabled;
        if (!enabled) pathingData.clear();
    }

    public void setGoalsEnabled(boolean enabled) {
        this.goalsEnabled = enabled;
        if (!enabled) goalsData.clear();
    }

    public void setPOIEnabled(boolean enabled) {
        this.poiEnabled = enabled;
        if (!enabled) poiData.clear();
    }

    public void setRaidsEnabled(boolean enabled) {
        this.raidsEnabled = enabled;
        if (!enabled) raidsData.clear();
    }

    public boolean isPathingEnabled() { return pathingEnabled; }
    public boolean isGoalsEnabled() { return goalsEnabled; }
    public boolean isPOIEnabled() { return poiEnabled; }
    public boolean isRaidsEnabled() { return raidsEnabled; }

    // ========== Rendering ==========

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        cleanExpiredData();

        if (pathingEnabled && !pathingData.isEmpty()) {
            renderPathing(poseStack, bufferSource, cameraPos);
        }

        if (goalsEnabled && !goalsData.isEmpty()) {
            renderGoals(poseStack, bufferSource, cameraPos);
        }

        if (poiEnabled && !poiData.isEmpty()) {
            renderPOI(poseStack, bufferSource, cameraPos);
        }

        if (raidsEnabled && !raidsData.isEmpty()) {
            renderRaids(poseStack, bufferSource, cameraPos);
        }
    }

    private void cleanExpiredData() {
        long now = System.currentTimeMillis();

        pathingTimestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > DATA_EXPIRY_MS) {
                pathingData.remove(entry.getKey());
                return true;
            }
            return false;
        });

        goalsTimestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > DATA_EXPIRY_MS) {
                goalsData.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Render entity pathing - shows actual navigation paths from server.
     */
    private void renderPathing(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        RenderType lineType = Objects.requireNonNull(RenderType.lines());
        VertexConsumer lines = bufferSource.getBuffer(lineType);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        for (EntityPathingPayload data : pathingData.values()) {
            List<EntityPathingPayload.PathNode> nodes = data.nodes();
            if (nodes.size() < 2) continue;

            // Color based on reachability
            float r = data.canReach() ? 0.0f : 1.0f;
            float g = data.canReach() ? 1.0f : 0.3f;
            float b = 0.0f;

            // Draw path lines
            for (int i = 0; i < nodes.size() - 1; i++) {
                EntityPathingPayload.PathNode from = nodes.get(i);
                EntityPathingPayload.PathNode to = nodes.get(i + 1);

                // Adjust color for current node
                float nodeR = r;
                float nodeG = g;
                if (from.nodeType() == 1) { // Current node
                    nodeR = 1.0f;
                    nodeG = 1.0f;
                }

                drawLine(lines, matrix,
                    (float) from.x(), (float) from.y(), (float) from.z(),
                    (float) to.x(), (float) to.y(), (float) to.z(),
                    nodeR, nodeG, b, 1.0f);
            }

            // Draw target marker
            drawCross(lines, matrix,
                (float) data.targetX(), (float) data.targetY() + 0.5f, (float) data.targetZ(),
                0.3f, 1.0f, 0.0f, 0.0f, 1.0f);

            // Draw node markers
            for (EntityPathingPayload.PathNode node : nodes) {
                float markerR = 0.5f;
                float markerG = 0.5f;
                float markerB = 0.5f;

                switch (node.nodeType()) {
                    case 1 -> { markerR = 1.0f; markerG = 1.0f; markerB = 0.0f; } // Current - yellow
                    case 3 -> { markerR = 0.0f; markerG = 1.0f; markerB = 0.0f; } // Target - green
                }

                if (node.costMalus() > 0) {
                    markerR = 1.0f; markerG = 0.5f; markerB = 0.0f; // High cost - orange
                }

                drawBox(lines, matrix,
                    (float) node.x() - 0.1f, (float) node.y(), (float) node.z() - 0.1f,
                    (float) node.x() + 0.1f, (float) node.y() + 0.2f, (float) node.z() + 0.1f,
                    markerR, markerG, markerB, 1.0f);
            }
        }

        poseStack.popPose();
    }

    /**
     * Render entity goals - shows AI goal priorities.
     */
    private void renderGoals(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (EntityGoalsPayload data : goalsData.values()) {
            // Build goal text
            StringBuilder sb = new StringBuilder();
            sb.append("§e").append(data.entityName()).append("§r\n");

            // Regular goals
            for (EntityGoalsPayload.GoalInfo goal : data.goals()) {
                String color = goal.isRunning() ? "§a" : "§7";
                sb.append(color).append("[").append(goal.priority()).append("] ")
                  .append(goal.name()).append("§r\n");
            }

            // Target goals
            if (!data.targetGoals().isEmpty()) {
                sb.append("§cTargets:§r\n");
                for (EntityGoalsPayload.GoalInfo goal : data.targetGoals()) {
                    String color = goal.isRunning() ? "§c" : "§8";
                    sb.append(color).append("[").append(goal.priority()).append("] ")
                      .append(goal.name()).append("§r\n");
                }
            }

            // Render as floating text (would need font renderer integration)
            // For now, draw a marker at the entity
            RenderType lineType = Objects.requireNonNull(RenderType.lines());
            VertexConsumer lines = bufferSource.getBuffer(lineType);
            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

            boolean hasRunning = data.goals().stream().anyMatch(EntityGoalsPayload.GoalInfo::isRunning) ||
                                data.targetGoals().stream().anyMatch(EntityGoalsPayload.GoalInfo::isRunning);

            float r = hasRunning ? 0.0f : 0.5f;
            float g = hasRunning ? 1.0f : 0.5f;
            float b = hasRunning ? 0.0f : 0.5f;

            drawCross(lines, matrix,
                (float) data.posX(), (float) data.posY() + 2.5f, (float) data.posZ(),
                0.5f, r, g, b, 1.0f);

            poseStack.popPose();
        }
    }

    /**
     * Render POI markers.
     */
    private void renderPOI(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        RenderType lineType = Objects.requireNonNull(RenderType.lines());
        VertexConsumer lines = bufferSource.getBuffer(lineType);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        for (POIPayload.POIInfo poi : poiData.values()) {
            // Color based on POI type
            float r = 0.0f, g = 1.0f, b = 1.0f; // Cyan default

            String type = poi.type().toLowerCase(Locale.ROOT);
            if (type.contains("bed")) {
                r = 1.0f; g = 0.5f; b = 0.5f; // Pink for beds
            } else if (type.contains("job") || type.contains("work")) {
                r = 1.0f; g = 1.0f; b = 0.0f; // Yellow for workstations
            } else if (type.contains("meeting") || type.contains("bell")) {
                r = 0.0f; g = 1.0f; b = 0.0f; // Green for meeting points
            } else if (type.contains("bee")) {
                r = 1.0f; g = 0.8f; b = 0.0f; // Orange for bee-related
            }

            // Draw POI box
            drawBox(lines, matrix,
                poi.x(), poi.y(), poi.z(),
                poi.x() + 1, poi.y() + 1, poi.z() + 1,
                r, g, b, 0.8f);
        }

        poseStack.popPose();
    }

    /**
     * Render raid centers.
     */
    private void renderRaids(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        RenderType lineType = Objects.requireNonNull(RenderType.lines());
        VertexConsumer lines = bufferSource.getBuffer(lineType);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        for (RaidsPayload.RaidInfo raid : raidsData.values()) {
            float r = raid.isActive() ? 1.0f : 0.5f;
            float g = raid.isVictory() ? 1.0f : 0.0f;
            float b = 0.0f;

            // Draw large cross at raid center
            drawCross(lines, matrix,
                (float) raid.centerX(), (float) raid.centerY() + 1, (float) raid.centerZ(),
                3.0f, r, g, b, 1.0f);

            // Draw vertical beam
            drawLine(lines, matrix,
                (float) raid.centerX(), (float) raid.centerY(), (float) raid.centerZ(),
                (float) raid.centerX(), (float) raid.centerY() + 10, (float) raid.centerZ(),
                r, g, b, 0.5f);
        }

        poseStack.popPose();
    }

    // ========== Drawing Helpers ==========

    private void drawLine(VertexConsumer consumer, Matrix4f matrix,
                         float x1, float y1, float z1, float x2, float y2, float z2,
                         float r, float g, float b, float a) {
        Matrix4f safeMatrix = Objects.requireNonNull(matrix);
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) len = 1;
        dx /= len; dy /= len; dz /= len;

        consumer.addVertex(safeMatrix, x1, y1, z1).setColor(r, g, b, a).setNormal(dx, dy, dz);
        consumer.addVertex(safeMatrix, x2, y2, z2).setColor(r, g, b, a).setNormal(dx, dy, dz);
    }

    private void drawBox(VertexConsumer consumer, Matrix4f matrix,
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

    private void drawCross(VertexConsumer consumer, Matrix4f matrix,
                          float x, float y, float z, float size,
                          float r, float g, float b, float a) {
        drawLine(consumer, matrix, x - size, y, z, x + size, y, z, r, g, b, a);
        drawLine(consumer, matrix, x, y - size, z, x, y + size, z, r, g, b, a);
        drawLine(consumer, matrix, x, y, z - size, x, y, z + size, r, g, b, a);
    }

    // ========== Stats ==========

    public int getPathingCount() { return pathingData.size(); }
    public int getGoalsCount() { return goalsData.size(); }
    public int getPOICount() { return poiData.size(); }
    public int getRaidsCount() { return raidsData.size(); }
}
