package com.frenkvs.devmod.rendering;

import com.frenkvs.devmod.telemetry.RoomDefinition;
import com.frenkvs.devmod.telemetry.TelemetryConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FASE 4 REQ-A3: Room Bounds Visualizer
 *
 * Visualizza i confini delle room configurate in telemetry_rooms.json:
 * - Box wireframe colorato per ogni room
 * - Label con nome della room
 * - Colori diversi per room diverse
 *
 * Attivazione: Tasto R (Room bounds)
 */
public class RoomBoundsVisualizer {
    public static final RoomBoundsVisualizer INSTANCE = new RoomBoundsVisualizer();

    private boolean enabled = false;
    private boolean showGaps = true; // Show gap detection by default
    private final List<RoomVisual> rooms = new ArrayList<>();
    private final Set<BlockPos> detectedGaps = new HashSet<>(); // Positions where room is not closed

    // Pending room definition markers (persist even when editor is closed)
    private BlockPos pendingPointA = null;
    private BlockPos pendingPointB = null;

    // Colori per le room (cicla)
    private static final int[] ROOM_COLORS = {
            0xFFFF0000, // Rosso
            0xFF00FF00, // Verde
            0xFF0000FF, // Blu
            0xFFFFFF00, // Giallo
            0xFFFF00FF, // Magenta
            0xFF00FFFF, // Ciano
            0xFFFF8000, // Arancione
            0xFF8000FF, // Viola
    };

    private RoomBoundsVisualizer() {}

    public void toggle() {
        enabled = !enabled;
        // Sync rooms from config when enabling
        if (enabled && rooms.isEmpty()) {
            syncFromConfig();
        }
    }

    /**
     * Loads room definitions from telemetry_rooms.json config file.
     * Called automatically when toggling on, or can be called manually to refresh.
     */
    public void syncFromConfig() {
        rooms.clear();
        detectedGaps.clear();
        TelemetryConfig config = new TelemetryConfig();
        List<RoomDefinition> defs = config.loadRooms();

        String currentDimension = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            currentDimension = mc.level.dimension().location().toString();
        }

        int colorIndex = 0;
        for (RoomDefinition def : defs) {
            // Filter by current dimension if available
            if (currentDimension != null && def.dimension() != null && !def.dimension().isEmpty()) {
                if (!def.dimension().equals(currentDimension)) {
                    continue;
                }
            }
            rooms.add(new RoomVisual(def.id(), def.min(), def.max(), ROOM_COLORS[colorIndex % ROOM_COLORS.length]));
            colorIndex++;
        }

        // Scan for gaps after loading rooms
        if (mc.level != null && showGaps) {
            scanForGaps(mc.level);
        }
    }

    /**
     * Toggle gap detection visualization
     */
    public void toggleGapDetection() {
        showGaps = !showGaps;
        if (showGaps) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                scanForGaps(mc.level);
            }
        } else {
            detectedGaps.clear();
        }
    }

    /**
     * Scans all room boundaries for non-solid blocks (gaps/openings).
     * Gaps are positions on the room's faces where there is no solid block.
     */
    public void scanForGaps(ClientLevel level) {
        detectedGaps.clear();

        for (RoomVisual room : rooms) {
            int minX = Math.min(room.min.getX(), room.max.getX());
            int minY = Math.min(room.min.getY(), room.max.getY());
            int minZ = Math.min(room.min.getZ(), room.max.getZ());
            int maxX = Math.max(room.min.getX(), room.max.getX());
            int maxY = Math.max(room.min.getY(), room.max.getY());
            int maxZ = Math.max(room.min.getZ(), room.max.getZ());

            // Scan all 6 faces of the room for gaps
            // Bottom face (Y = minY)
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, minY, z);
                    if (!isSolidBlock(level, pos)) {
                        detectedGaps.add(pos);
                    }
                }
            }

            // Top face (Y = maxY)
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, maxY, z);
                    if (!isSolidBlock(level, pos)) {
                        detectedGaps.add(pos);
                    }
                }
            }

            // Front face (Z = minZ)
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY + 1; y < maxY; y++) { // Skip corners already scanned
                    BlockPos pos = new BlockPos(x, y, minZ);
                    if (!isSolidBlock(level, pos)) {
                        detectedGaps.add(pos);
                    }
                }
            }

            // Back face (Z = maxZ)
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY + 1; y < maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, maxZ);
                    if (!isSolidBlock(level, pos)) {
                        detectedGaps.add(pos);
                    }
                }
            }

            // Left face (X = minX)
            for (int z = minZ + 1; z < maxZ; z++) { // Skip corners already scanned
                for (int y = minY + 1; y < maxY; y++) {
                    BlockPos pos = new BlockPos(minX, y, z);
                    if (!isSolidBlock(level, pos)) {
                        detectedGaps.add(pos);
                    }
                }
            }

            // Right face (X = maxX)
            for (int z = minZ + 1; z < maxZ; z++) {
                for (int y = minY + 1; y < maxY; y++) {
                    BlockPos pos = new BlockPos(maxX, y, z);
                    if (!isSolidBlock(level, pos)) {
                        detectedGaps.add(pos);
                    }
                }
            }
        }
    }

    /**
     * Check if a block is solid (not air, water, etc.)
     */
    private boolean isSolidBlock(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // Consider solid if it's not air and has some collision shape
        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Get the number of detected gaps
     */
    public int getGapCount() {
        return detectedGaps.size();
    }

    /**
     * Check if gap detection is enabled
     */
    public boolean isShowingGaps() {
        return showGaps;
    }

    /**
     * Force reload rooms from config (useful after editing config file).
     */
    public void reload() {
        syncFromConfig();
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Aggiunge una room da visualizzare
     */
    public void addRoom(String id, BlockPos min, BlockPos max) {
        // Remove if already exists
        rooms.removeIf(r -> r.id.equals(id));

        int colorIndex = rooms.size() % ROOM_COLORS.length;
        rooms.add(new RoomVisual(id, min, max, ROOM_COLORS[colorIndex]));
    }

    /**
     * Rimuove una room
     */
    public void removeRoom(String id) {
        rooms.removeIf(r -> r.id.equals(id));
    }

    /**
     * Pulisce tutte le room
     */
    public void clearRooms() {
        rooms.clear();
    }

    /**
     * Imposta le room dal TelemetryService
     */
    public void setRoomsFromConfig(List<RoomData> roomDataList) {
        rooms.clear();
        int colorIndex = 0;
        for (RoomData data : roomDataList) {
            rooms.add(new RoomVisual(data.id, data.min, data.max, ROOM_COLORS[colorIndex % ROOM_COLORS.length]));
            colorIndex++;
        }
    }

    /**
     * Render dei confini delle room
     */
    public void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos) {
        // Always render pending markers if they exist (even if visualizer is disabled)
        boolean hasPending = hasPendingPoints();

        if (!enabled && !hasPending) return;
        if (!enabled && rooms.isEmpty() && !hasPending) return;

        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();
        var pose = poseStack.last();

        for (RoomVisual room : rooms) {
            // Solo room entro 200 blocchi
            double dx = (room.min.getX() + room.max.getX()) / 2.0 - cameraPos.x;
            double dy = (room.min.getY() + room.max.getY()) / 2.0 - cameraPos.y;
            double dz = (room.min.getZ() + room.max.getZ()) / 2.0 - cameraPos.z;
            if (dx*dx + dy*dy + dz*dz > 200*200) continue;

            renderRoomBounds(consumer, matrix, pose, room);
        }

        // Render gaps as red wireframe cubes
        if (showGaps && !detectedGaps.isEmpty()) {
            for (BlockPos gap : detectedGaps) {
                // Only render gaps within 100 blocks
                double dx = gap.getX() + 0.5 - cameraPos.x;
                double dy = gap.getY() + 0.5 - cameraPos.y;
                double dz = gap.getZ() + 0.5 - cameraPos.z;
                if (dx*dx + dy*dy + dz*dz > 100*100) continue;

                renderGapMarker(consumer, matrix, pose, gap);
            }
        }

        // Render pending room definition markers (Point A/B)
        if (hasPending) {
            renderPendingMarkers(consumer, matrix, pose, cameraPos);
        }

        poseStack.popPose();

        // Render labels
        for (RoomVisual room : rooms) {
            double dx = (room.min.getX() + room.max.getX()) / 2.0 - cameraPos.x;
            double dy = (room.min.getY() + room.max.getY()) / 2.0 - cameraPos.y;
            double dz = (room.min.getZ() + room.max.getZ()) / 2.0 - cameraPos.z;
            if (dx*dx + dy*dy + dz*dz > 100*100) continue;

            Vec3 labelPos = new Vec3(
                    (room.min.getX() + room.max.getX()) / 2.0,
                    room.max.getY() + 1.5,
                    (room.min.getZ() + room.max.getZ()) / 2.0
            );
            // Add gap count to label if there are gaps
            String label = room.id;
            DebugRenderer.INSTANCE.addLabel(labelPos, label, room.color, 100);
        }

        // Add gap warning labels for each gap cluster (limit to prevent spam)
        if (showGaps && !detectedGaps.isEmpty()) {
            int labelCount = 0;
            for (BlockPos gap : detectedGaps) {
                if (labelCount >= 20) break; // Limit labels to prevent spam
                double dx = gap.getX() + 0.5 - cameraPos.x;
                double dy = gap.getY() + 0.5 - cameraPos.y;
                double dz = gap.getZ() + 0.5 - cameraPos.z;
                if (dx*dx + dy*dy + dz*dz > 50*50) continue;

                Vec3 labelPos = new Vec3(gap.getX() + 0.5, gap.getY() + 1.2, gap.getZ() + 0.5);
                DebugRenderer.INSTANCE.addLabel(labelPos, "GAP", 0xFFFF0000, 50);
                labelCount++;
            }
        }
    }

    /**
     * Render a gap marker (red wireframe cube slightly smaller than a block)
     */
    private void renderGapMarker(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, BlockPos pos) {
        float r = 1.0f, g = 0.2f, b = 0.2f, a = 1.0f; // Bright red

        float minX = pos.getX() + 0.1f;
        float minY = pos.getY() + 0.1f;
        float minZ = pos.getZ() + 0.1f;
        float maxX = pos.getX() + 0.9f;
        float maxY = pos.getY() + 0.9f;
        float maxZ = pos.getZ() + 0.9f;

        // Bottom face
        line(consumer, matrix, pose, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Top face
        line(consumer, matrix, pose, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Vertical edges
        line(consumer, matrix, pose, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);

        // Cross on top face for visibility
        line(consumer, matrix, pose, minX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, maxY, minZ, minX, maxY, maxZ, r, g, b, a);
    }

    private void renderRoomBounds(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, RoomVisual room) {
        float r = ((room.color >> 16) & 0xFF) / 255f;
        float g = ((room.color >> 8) & 0xFF) / 255f;
        float b = (room.color & 0xFF) / 255f;
        float a = 0.8f;

        float minX = room.min.getX();
        float minY = room.min.getY();
        float minZ = room.min.getZ();
        float maxX = room.max.getX() + 1; // +1 per includere il blocco
        float maxY = room.max.getY() + 1;
        float maxZ = room.max.getZ() + 1;

        // Bottom face (4 edges)
        line(consumer, matrix, pose, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Top face (4 edges)
        line(consumer, matrix, pose, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Vertical edges (4 edges)
        line(consumer, matrix, pose, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(consumer, matrix, pose, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(consumer, matrix, pose, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private void line(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
    }

    /**
     * Numero di room configurate
     */
    public int getRoomCount() {
        return rooms.size();
    }

    // Internal data classes
    private record RoomVisual(String id, BlockPos min, BlockPos max, int color) {}

    // Public data class per input esterno
    public record RoomData(String id, BlockPos min, BlockPos max) {}

    // === Pending Point A/B Markers ===

    /**
     * Set pending Point A for room definition.
     * This marker will be visible in the world even when the editor screen is closed.
     */
    public void setPendingPointA(BlockPos pos) {
        this.pendingPointA = pos;
    }

    /**
     * Set pending Point B for room definition.
     */
    public void setPendingPointB(BlockPos pos) {
        this.pendingPointB = pos;
    }

    /**
     * Get pending Point A.
     */
    public BlockPos getPendingPointA() {
        return pendingPointA;
    }

    /**
     * Get pending Point B.
     */
    public BlockPos getPendingPointB() {
        return pendingPointB;
    }

    /**
     * Clear pending points after room is saved.
     */
    public void clearPendingPoints() {
        this.pendingPointA = null;
        this.pendingPointB = null;
    }

    /**
     * Check if there are pending points to show.
     */
    public boolean hasPendingPoints() {
        return pendingPointA != null || pendingPointB != null;
    }

    /**
     * Render pending point markers.
     * Called from render() method.
     */
    private void renderPendingMarkers(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, Vec3 cameraPos) {
        // Render Point A marker (green pillar)
        if (pendingPointA != null) {
            double dx = pendingPointA.getX() + 0.5 - cameraPos.x;
            double dy = pendingPointA.getY() + 0.5 - cameraPos.y;
            double dz = pendingPointA.getZ() + 0.5 - cameraPos.z;
            if (dx*dx + dy*dy + dz*dz < 200*200) {
                renderPointMarker(consumer, matrix, pose, pendingPointA, 0.0f, 1.0f, 0.3f, "A");
            }
        }

        // Render Point B marker (blue pillar)
        if (pendingPointB != null) {
            double dx = pendingPointB.getX() + 0.5 - cameraPos.x;
            double dy = pendingPointB.getY() + 0.5 - cameraPos.y;
            double dz = pendingPointB.getZ() + 0.5 - cameraPos.z;
            if (dx*dx + dy*dy + dz*dz < 200*200) {
                renderPointMarker(consumer, matrix, pose, pendingPointB, 0.3f, 0.5f, 1.0f, "B");
            }
        }

        // If both points set, render preview wireframe
        if (pendingPointA != null && pendingPointB != null) {
            renderPreviewBox(consumer, matrix, pose, pendingPointA, pendingPointB);
        }
    }

    /**
     * Render a point marker as a vertical pillar with cross.
     */
    private void renderPointMarker(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                                    BlockPos pos, float r, float g, float b, String label) {
        float a = 1.0f;
        float cx = pos.getX() + 0.5f;
        float cy = pos.getY();
        float cz = pos.getZ() + 0.5f;

        // Vertical pillar (beacon-like)
        line(consumer, matrix, pose, cx, cy, cz, cx, cy + 5, cz, r, g, b, a);

        // Cross at bottom
        float size = 0.4f;
        line(consumer, matrix, pose, cx - size, cy + 0.1f, cz, cx + size, cy + 0.1f, cz, r, g, b, a);
        line(consumer, matrix, pose, cx, cy + 0.1f, cz - size, cx, cy + 0.1f, cz + size, r, g, b, a);

        // Square outline at position
        line(consumer, matrix, pose, cx - size, cy + 0.1f, cz - size, cx + size, cy + 0.1f, cz - size, r, g, b, a);
        line(consumer, matrix, pose, cx + size, cy + 0.1f, cz - size, cx + size, cy + 0.1f, cz + size, r, g, b, a);
        line(consumer, matrix, pose, cx + size, cy + 0.1f, cz + size, cx - size, cy + 0.1f, cz + size, r, g, b, a);
        line(consumer, matrix, pose, cx - size, cy + 0.1f, cz + size, cx - size, cy + 0.1f, cz - size, r, g, b, a);

        // Add label
        Vec3 labelPos = new Vec3(cx, cy + 2, cz);
        int color = ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255) | 0xFF000000;
        DebugRenderer.INSTANCE.addLabel(labelPos, "Point " + label, color, 200);
    }

    /**
     * Render preview wireframe box between two points.
     */
    private void renderPreviewBox(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                                   BlockPos a, BlockPos b) {
        // Yellow dashed preview
        float r = 1.0f, g = 0.9f, bl = 0.2f, alpha = 0.6f;

        float minX = Math.min(a.getX(), b.getX());
        float minY = Math.min(a.getY(), b.getY());
        float minZ = Math.min(a.getZ(), b.getZ());
        float maxX = Math.max(a.getX(), b.getX()) + 1;
        float maxY = Math.max(a.getY(), b.getY()) + 1;
        float maxZ = Math.max(a.getZ(), b.getZ()) + 1;

        // Bottom face
        line(consumer, matrix, pose, minX, minY, minZ, maxX, minY, minZ, r, g, bl, alpha);
        line(consumer, matrix, pose, maxX, minY, minZ, maxX, minY, maxZ, r, g, bl, alpha);
        line(consumer, matrix, pose, maxX, minY, maxZ, minX, minY, maxZ, r, g, bl, alpha);
        line(consumer, matrix, pose, minX, minY, maxZ, minX, minY, minZ, r, g, bl, alpha);

        // Top face
        line(consumer, matrix, pose, minX, maxY, minZ, maxX, maxY, minZ, r, g, bl, alpha);
        line(consumer, matrix, pose, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, bl, alpha);
        line(consumer, matrix, pose, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, bl, alpha);
        line(consumer, matrix, pose, minX, maxY, maxZ, minX, maxY, minZ, r, g, bl, alpha);

        // Vertical edges
        line(consumer, matrix, pose, minX, minY, minZ, minX, maxY, minZ, r, g, bl, alpha);
        line(consumer, matrix, pose, maxX, minY, minZ, maxX, maxY, minZ, r, g, bl, alpha);
        line(consumer, matrix, pose, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, bl, alpha);
        line(consumer, matrix, pose, minX, minY, maxZ, minX, maxY, maxZ, r, g, bl, alpha);
    }
}
