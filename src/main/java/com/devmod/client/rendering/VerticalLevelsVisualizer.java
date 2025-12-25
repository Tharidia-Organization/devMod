package com.devmod.client.rendering;

import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * FASE 4 REQ-A7: Vertical Levels Visualizer
 *
 * Visualizza le zone verticali delle room:
 * - Verde = Floor zone (33% inferiore)
 * - Giallo = Mid zone (33% centrale)
 * - Rosso = High zone (33% superiore)
 *
 * Utile per analizzare la distribuzione verticale del gameplay.
 *
 * Attivazione: Tasto Y (Y-axis/Vertical)
 *
 * Performance optimizations:
 * - Max 10 active rooms rendered at a time
 * - Per-room active toggle
 * - Labels cleared when visualizer disabled
 */
public class VerticalLevelsVisualizer {
    public static final VerticalLevelsVisualizer INSTANCE = new VerticalLevelsVisualizer();

    // Performance limits
    private static final int MAX_ACTIVE_ROOMS = 10;

    // Get configurable render distance
    private double getMaxRenderDistanceSq() {
        return SettingsManager.INSTANCE.getSettings().visualizers.getRenderDistanceSq();
    }
    // Label timeout - short enough to auto-expire but long enough for smooth display
    private static final long LABEL_TIMEOUT_MS = 150;

    private boolean enabled = false;
    private final List<RoomZones> roomZonesList = new ArrayList<>();

    private VerticalLevelsVisualizer() {}

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean value) {
        boolean wasEnabled = enabled;
        enabled = value;

        // When disabled, immediately clear zone labels to prevent stale display
        if (wasEnabled && !enabled) {
            DebugRenderer.INSTANCE.clearLabelsByText("FLOOR", "MID", "HIGH");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Configura le zone verticali per una room
     */
    public void addRoom(String id, BlockPos min, BlockPos max) {
        addRoom(id, min, max, true); // Active by default
    }

    /**
     * Configura le zone verticali per una room con toggle attivo/disattivo
     */
    public void addRoom(String id, BlockPos min, BlockPos max, boolean active) {
        roomZonesList.removeIf(r -> r.id.equals(id));

        double minY = Math.min(min.getY(), max.getY());
        double maxY = Math.max(min.getY(), max.getY()) + 1;
        double height = maxY - minY;

        double floorMax = minY + height * 0.33;
        double midMax = minY + height * 0.66;

        roomZonesList.add(new RoomZones(
                id,
                Math.min(min.getX(), max.getX()),
                Math.max(min.getX(), max.getX()) + 1,
                Math.min(min.getZ(), max.getZ()),
                Math.max(min.getZ(), max.getZ()) + 1,
                minY, floorMax, midMax, maxY,
                active
        ));
    }

    /**
     * Toggle active state for a specific room
     */
    public void setRoomActive(String id, boolean active) {
        for (int i = 0; i < roomZonesList.size(); i++) {
            RoomZones room = roomZonesList.get(i);
            if (room.id.equals(id)) {
                roomZonesList.set(i, new RoomZones(
                        room.id, room.minX, room.maxX, room.minZ, room.maxZ,
                        room.minY, room.floorMax, room.midMax, room.maxY, active
                ));
                break;
            }
        }
    }

    /**
     * Get list of room IDs with their active state
     */
    public List<String> getActiveRoomIds() {
        List<String> activeIds = new ArrayList<>();
        for (RoomZones room : roomZonesList) {
            if (room.active) {
                activeIds.add(room.id);
            }
        }
        return activeIds;
    }

    /**
     * Pulisce tutte le room e le relative label
     */
    public void clearRooms() {
        roomZonesList.clear();
        // Clear any stale zone labels from DebugRenderer
        DebugRenderer.INSTANCE.clearLabelsByText("FLOOR", "MID", "HIGH");
    }

    /**
     * Imposta le room dal TelemetryService.
     * Clears existing rooms and their labels before adding new ones.
     */
    public void setRoomsFromConfig(List<RoomBoundsVisualizer.RoomData> roomDataList) {
        // Clear existing labels before changing room list to prevent stale labels
        DebugRenderer.INSTANCE.clearLabelsByText("FLOOR", "MID", "HIGH");
        roomZonesList.clear();
        for (RoomBoundsVisualizer.RoomData data : roomDataList) {
            addRoom(data.id(), data.min(), data.max());
        }
    }

    /**
     * Render delle zone verticali
     * Performance optimizations:
     * - Only renders active rooms
     * - Max MAX_ACTIVE_ROOMS rendered per frame
     * - Distance culling at MAX_RENDER_DISTANCE_SQ
     */
    public void render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, @Nonnull Vec3 cameraPos) {
        if (!enabled || roomZonesList.isEmpty()) return;

        VertexConsumer consumer = Objects.requireNonNull(buffer.getBuffer(Objects.requireNonNull(RenderType.lines())));

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());
        var pose = Objects.requireNonNull(poseStack.last());

        int renderedCount = 0;
        for (RoomZones room : roomZonesList) {
            // Skip inactive rooms
            if (!room.active) continue;

            // Limit max rendered rooms for performance
            if (renderedCount >= MAX_ACTIVE_ROOMS) break;

            // Distance culling - skip rooms too far from camera
            double centerX = (room.minX + room.maxX) / 2.0;
            double centerY = (room.minY + room.maxY) / 2.0;
            double centerZ = (room.minZ + room.maxZ) / 2.0;

            double dx = centerX - cameraPos.x;
            double dy = centerY - cameraPos.y;
            double dz = centerZ - cameraPos.z;
            if (dx*dx + dy*dy + dz*dz > getMaxRenderDistanceSq()) continue;

            renderZones(Objects.requireNonNull(consumer), Objects.requireNonNull(matrix), Objects.requireNonNull(pose), room, cameraPos);
            renderedCount++;
        }

        poseStack.popPose();
    }

    private void renderZones(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                            @Nonnull PoseStack.Pose pose, @Nonnull RoomZones room, @Nonnull Vec3 cameraPos) {
        // Floor zone (green)
        renderZonePlane(consumer, matrix, pose, room, room.minY, room.floorMax, 0.0f, 0.8f, 0.0f);

        // Mid zone (yellow)
        renderZonePlane(consumer, matrix, pose, room, room.floorMax, room.midMax, 0.8f, 0.8f, 0.0f);

        // High zone (red)
        renderZonePlane(consumer, matrix, pose, room, room.midMax, room.maxY, 0.8f, 0.0f, 0.0f);

        // Labels at the center of each zone - use short timeout to auto-expire when disabled
        double centerX = (room.minX + room.maxX) / 2.0;
        double centerZ = (room.minZ + room.maxZ) / 2.0;

        DebugRenderer.INSTANCE.addLabel(
                new Vec3(centerX, (room.minY + room.floorMax) / 2.0, centerZ),
                "FLOOR", 0xFF00CC00, LABEL_TIMEOUT_MS
        );
        DebugRenderer.INSTANCE.addLabel(
                new Vec3(centerX, (room.floorMax + room.midMax) / 2.0, centerZ),
                "MID", 0xFFCCCC00, LABEL_TIMEOUT_MS
        );
        DebugRenderer.INSTANCE.addLabel(
                new Vec3(centerX, (room.midMax + room.maxY) / 2.0, centerZ),
                "HIGH", 0xFFCC0000, LABEL_TIMEOUT_MS
        );
    }

    private void renderZonePlane(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                 @Nonnull PoseStack.Pose pose, @Nonnull RoomZones room,
                                 double yBottom, double yTop, float r, float g, float b) {
        float a = 0.4f;
        float minX = (float) room.minX;
        float maxX = (float) room.maxX;
        float minZ = (float) room.minZ;
        float maxZ = (float) room.maxZ;
        float yB = (float) yBottom;
        float yT = (float) yTop;

        // Render only the border of each zone (wireframe)
        // Bottom plane
        line(consumer, matrix, pose, minX, yB, minZ, maxX, yB, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, yB, minZ, maxX, yB, maxZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, yB, maxZ, minX, yB, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, yB, maxZ, minX, yB, minZ, r, g, b, a);

        // Top plane
        line(consumer, matrix, pose, minX, yT, minZ, maxX, yT, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, yT, minZ, maxX, yT, maxZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, yT, maxZ, minX, yT, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, yT, maxZ, minX, yT, minZ, r, g, b, a);

        // Vertical edges (corners only)
        line(consumer, matrix, pose, minX, yB, minZ, minX, yT, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, yB, minZ, maxX, yT, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, yB, maxZ, maxX, yT, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, yB, maxZ, minX, yT, maxZ, r, g, b, a);
    }

    private void line(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix, @Nonnull PoseStack.Pose pose,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float r, float g, float b, float a) {
        Objects.requireNonNull(consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a)).setNormal(Objects.requireNonNull(pose), 0f, 1f, 0f);
        Objects.requireNonNull(consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a)).setNormal(Objects.requireNonNull(pose), 0f, 1f, 0f);
    }

    public int getRoomCount() {
        return roomZonesList.size();
    }

    // Internal data class with active toggle for per-room control
    private record RoomZones(
            String id,
            double minX, double maxX,
            double minZ, double maxZ,
            double minY, double floorMax, double midMax, double maxY,
            boolean active
    ) {}
}
