package com.devmod.client.rendering;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.joml.Matrix4f;
import org.slf4j.Logger;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.config.Config;
public class DebugRenderer {
    public static final DebugRenderer INSTANCE = new DebugRenderer();
    private static final Logger LOGGER = LogUtils.getLogger();

    private boolean enabled = getConfigEnabled();

    private static boolean getConfigEnabled() {
        try { return Config.DEBUG_OVERLAY_ENABLED.get(); }
        catch (Exception e) { return false; }
    }
    // Thread-safe collections to prevent ConcurrentModificationException
    private final List<TimedDebugShape> shapes = new CopyOnWriteArrayList<>();
    private final List<TimedDebugLabel> labels = new CopyOnWriteArrayList<>();

    // Max render distance for performance - now uses configurable value
    private double getMaxRenderDistanceSq() {
        return SettingsManager.INSTANCE.getSettings().visualizers.getRenderDistanceSq();
    }
    private long lastCleanupTime = System.currentTimeMillis();

    private DebugRenderer() {}

    public void toggle() {
        enabled = !enabled;
        if (!enabled) {
            clear();
        }
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
        clear();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (value) {
            enable();
        } else {
            disable();
        }
    }

    public void clear() {
        shapes.clear();
        labels.clear();
    }

    /**
     * Removes all labels matching a specific text pattern.
     * Useful for clearing labels from a specific subsystem (e.g., room zone labels).
     */
    public void clearLabelsByText(String... texts) {
        labels.removeIf(tl -> {
            for (String text : texts) {
                if (tl.label.text().equals(text)) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Removes all labels within a certain distance of a position.
     * Useful for clearing labels when a room is removed.
     */
    public void clearLabelsNear(Vec3 center, double radius) {
        double radiusSq = radius * radius;
        labels.removeIf(tl -> tl.label.pos().distanceToSqr(Objects.requireNonNull(center)) < radiusSq);
    }

    // ===== BASIC SHAPES =====

    /**
     * Aggiunge un box (persistente fino a clear)
     */
    public void addBox(AABB box, int color, boolean wireframe) {
        addBox(box, color, wireframe, -1);
    }

    /**
     * Aggiunge un box con timeout
     * @param durationMs Durata in millisecondi (-1 = infinito)
     */
    public void addBox(AABB box, int color, boolean wireframe, long durationMs) {
        shapes.add(new TimedDebugShape(new DebugBox(box, color, wireframe), durationMs));
    }

    /**
     * Aggiunge una linea (persistente)
     */
    public void addLine(Vec3 from, Vec3 to, int color, float width) {
        addLine(from, to, color, width, -1);
    }

    /**
     * Aggiunge una linea con timeout
     */
    public void addLine(Vec3 from, Vec3 to, int color, float width, long durationMs) {
        shapes.add(new TimedDebugShape(new DebugLine(from, to, color, width), durationMs));
    }

    /**
     * Aggiunge una label (persistente)
     */
    public void addLabel(Vec3 pos, String text, int color) {
        addLabel(pos, text, color, -1);
    }

    /**
     * Aggiunge una label con timeout.
     * Labels with same position and text are deduplicated - only the expiry is refreshed.
     */
    public void addLabel(Vec3 pos, String text, int color, long durationMs) {
        // Deduplication: check if a label with same position and text already exists
        // If so, just refresh its expiry time instead of adding a duplicate
        Vec3 safePos = Objects.requireNonNull(pos);
        for (int i = 0; i < labels.size(); i++) {
            TimedDebugLabel existing = labels.get(i);
            if (existing.label.text().equals(text) &&
                existing.label.pos().distanceToSqr(safePos) < 0.01) { // Same position (within epsilon)
                // Replace with refreshed expiry
                labels.set(i, new TimedDebugLabel(new DebugLabel(pos, text, color), durationMs));
                return;
            }
        }

        // No duplicate found, add new label
        labels.add(new TimedDebugLabel(new DebugLabel(pos, text, color), durationMs));
    }

    // ===== ADVANCED SHAPES =====

    /**
     * Aggiunge una sfera wireframe
     */
    public void addSphere(Vec3 center, double radius, int color, int segments) {
        addSphere(center, radius, color, segments, -1);
    }

    public void addSphere(Vec3 center, double radius, int color, int segments, long durationMs) {
        shapes.add(new TimedDebugShape(new DebugSphere(center, radius, color, segments), durationMs));
        LOGGER.debug("Added sphere at {} radius={}, total shapes: {}", center, radius, shapes.size());
    }

    /**
     * Aggiunge un cerchio piatto (sul piano XZ)
     */
    public void addCircle(Vec3 center, double radius, int color, int segments) {
        addCircle(center, radius, color, segments, -1);
    }

    public void addCircle(Vec3 center, double radius, int color, int segments, long durationMs) {
        shapes.add(new TimedDebugShape(new DebugCircle(center, radius, color, segments), durationMs));
    }

    /**
     * Aggiunge una freccia direzionale
     */
    public void addArrow(Vec3 from, Vec3 to, int color, float arrowSize) {
        addArrow(from, to, color, arrowSize, -1);
    }

    public void addArrow(Vec3 from, Vec3 to, int color, float arrowSize, long durationMs) {
        shapes.add(new TimedDebugShape(new DebugArrow(from, to, color, arrowSize), durationMs));
    }

    /**
     * Aggiunge un punto (piccola sfera)
     */
    public void addPoint(Vec3 pos, int color, float size) {
        addPoint(pos, color, size, -1);
    }

    public void addPoint(Vec3 pos, int color, float size, long durationMs) {
        addSphere(pos, size, color, 8, durationMs);
    }

    /**
     * Aggiunge una croce 3D al punto
     */
    public void addCross(Vec3 center, float size, int color) {
        addCross(center, size, color, -1);
    }

    public void addCross(Vec3 center, float size, int color, long durationMs) {
        addLine(center.add(-size, 0, 0), center.add(size, 0, 0), color, 2.0f, durationMs);
        addLine(center.add(0, -size, 0), center.add(0, size, 0), color, 2.0f, durationMs);
        addLine(center.add(0, 0, -size), center.add(0, 0, size), color, 2.0f, durationMs);
    }

    // ===== RENDER =====

    public void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos) {
        // Cleanup expired shapes (every 100ms) - ALWAYS do this
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > 100) {
            cleanupExpired();
            lastCleanupTime = now;
        }

        // Render shapes only if enabled
        if (enabled) {
            for (TimedDebugShape timedShape : shapes) {
                // Distance culling - skip shapes too far from camera
                Vec3 shapeCenter = timedShape.shape.getCenter();
                if (shapeCenter != null && cameraPos.distanceToSqr(shapeCenter) > getMaxRenderDistanceSq()) {
                    continue;
                }
                try {
                    timedShape.shape.render(poseStack, buffer, cameraPos);
                } catch (Exception e) {
                    // Prevent single shape error from breaking all rendering
                }
            }
        }

        // ALWAYS render labels (regardless of enabled state)
        // This allows other systems (PathfindingDebugger, etc.) to show labels
        // even when the main debug overlay is disabled
        for (TimedDebugLabel timedLabel : labels) {
            Vec3 labelPos = timedLabel.label.pos();
            if (labelPos != null && cameraPos.distanceToSqr(labelPos) > getMaxRenderDistanceSq()) {
                continue;
            }
            try {
                timedLabel.label.render(poseStack, cameraPos);
            } catch (Exception e) {
                // Prevent single label error from breaking all rendering
            }
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        shapes.removeIf(ts -> ts.expiryTime != -1 && now > ts.expiryTime);
        labels.removeIf(tl -> tl.expiryTime != -1 && now > tl.expiryTime);
    }

    // ===== WRAPPER CLASSES =====

    private static class TimedDebugShape {
        final DebugShape shape;
        final long expiryTime;

        TimedDebugShape(DebugShape shape, long durationMs) {
            this.shape = shape;
            this.expiryTime = durationMs == -1 ? -1 : System.currentTimeMillis() + durationMs;
        }
    }

    private static class TimedDebugLabel {
        final DebugLabel label;
        final long expiryTime;

        TimedDebugLabel(DebugLabel label, long durationMs) {
            this.label = label;
            this.expiryTime = durationMs == -1 ? -1 : System.currentTimeMillis() + durationMs;
        }
    }

    // ===== SHAPE INTERFACES =====

    public interface DebugShape {
        void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos);
        /** Returns the center position for distance culling, or null if always render */
        default Vec3 getCenter() { return null; }
    }

    // ===== BOX =====

    public record DebugBox(AABB box, int color, boolean wireframe) implements DebugShape {
        @Override
        public Vec3 getCenter() { return box.getCenter(); }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos) {
            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            VertexConsumer consumer = buffer.getBuffer(
                Objects.requireNonNull(wireframe ? RenderType.lines() : RenderType.debugQuads())
            );

            var pose = Objects.requireNonNull(poseStack.last());
            Matrix4f matrix = Objects.requireNonNull(pose.pose());

            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float a = ((color >> 24) & 0xFF) / 255f;
            if (a == 0) a = 1.0f; // Default full alpha if not specified

            if (wireframe) {
                renderWireframeBox(consumer, matrix, pose, box, r, g, b, a);
            } else {
                renderSolidBox(consumer, matrix, pose, box, r, g, b, a);
            }

            poseStack.popPose();
        }

        private void renderWireframeBox(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, AABB box,
                                        float r, float g, float b, float a) {
            float minX = (float) box.minX;
            float minY = (float) box.minY;
            float minZ = (float) box.minZ;
            float maxX = (float) box.maxX;
            float maxY = (float) box.maxY;
            float maxZ = (float) box.maxZ;

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
            Matrix4f safeMatrix = Objects.requireNonNull(matrix);
            PoseStack.Pose safePose = Objects.requireNonNull(pose);
            consumer.addVertex(safeMatrix, x1, y1, z1).setColor(r, g, b, a).setNormal(safePose, 0f, 1f, 0f);
            consumer.addVertex(safeMatrix, x2, y2, z2).setColor(r, g, b, a).setNormal(safePose, 0f, 1f, 0f);
        }

        private void renderSolidBox(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, AABB box,
                                    float r, float g, float b, float a) {
            float minX = (float) box.minX;
            float minY = (float) box.minY;
            float minZ = (float) box.minZ;
            float maxX = (float) box.maxX;
            float maxY = (float) box.maxY;
            float maxZ = (float) box.maxZ;

            // Bottom face (Y-)
            quad(consumer, matrix, pose, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a, 0, -1, 0);

            // Top face (Y+)
            quad(consumer, matrix, pose, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a, 0, 1, 0);

            // North face (Z-)
            quad(consumer, matrix, pose, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a, 0, 0, -1);

            // South face (Z+)
            quad(consumer, matrix, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a, 0, 0, 1);

            // West face (X-)
            quad(consumer, matrix, pose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a, -1, 0, 0);

            // East face (X+)
            quad(consumer, matrix, pose, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a, 1, 0, 0);
        }

        private void quad(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                         float x1, float y1, float z1, float x2, float y2, float z2,
                         float x3, float y3, float z3, float x4, float y4, float z4,
                         float r, float g, float b, float a, float nx, float ny, float nz) {
            Matrix4f safeMatrix = Objects.requireNonNull(matrix);
            PoseStack.Pose safePose = Objects.requireNonNull(pose);
            consumer.addVertex(safeMatrix, x1, y1, z1).setColor(r, g, b, a).setNormal(safePose, nx, ny, nz);
            consumer.addVertex(safeMatrix, x2, y2, z2).setColor(r, g, b, a).setNormal(safePose, nx, ny, nz);
            consumer.addVertex(safeMatrix, x3, y3, z3).setColor(r, g, b, a).setNormal(safePose, nx, ny, nz);
            consumer.addVertex(safeMatrix, x4, y4, z4).setColor(r, g, b, a).setNormal(safePose, nx, ny, nz);
        }
    }

    // ===== LINE =====

    public record DebugLine(Vec3 from, Vec3 to, int color, float width) implements DebugShape {
        @Override
        public Vec3 getCenter() { return Objects.requireNonNull(Objects.requireNonNull(from.add(Objects.requireNonNull(to))).scale(0.5)); }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos) {
            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            VertexConsumer consumer = buffer.getBuffer(Objects.requireNonNull(RenderType.lines()));
            var pose = Objects.requireNonNull(poseStack.last());
            Matrix4f matrix = Objects.requireNonNull(pose.pose());

            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float a = ((color >> 24) & 0xFF) / 255f;
            if (a == 0) a = 1.0f;

            consumer.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                    .setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
            consumer.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                    .setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);

            poseStack.popPose();
        }
    }

    // ===== SPHERE =====

    public record DebugSphere(Vec3 center, double radius, int color, int segments) implements DebugShape {
        @Override
        public Vec3 getCenter() { return center; }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos) {
            // PhantomShapes-style rendering: semi-transparent filled surface
            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float a = ((color >> 24) & 0xFF) / 255f;
            if (a == 0) a = 0.5f; // Semi-transparent like PhantomShapes

            // Use SphereRenderer with RenderType.debugQuads() (position + color only)
            SphereRenderer.renderSphereFilled(poseStack, buffer, center, radius, r, g, b, a);

            poseStack.popPose();
        }
    }

    // ===== CIRCLE (flat on XZ plane) =====

    public record DebugCircle(Vec3 center, double radius, int color, int segments) implements DebugShape {
        @Override
        public Vec3 getCenter() { return center; }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos) {
            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            VertexConsumer consumer = buffer.getBuffer(Objects.requireNonNull(RenderType.lines()));
            var pose = Objects.requireNonNull(poseStack.last());
            Matrix4f matrix = Objects.requireNonNull(pose.pose());

            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float a = ((color >> 24) & 0xFF) / 255f;
            if (a == 0) a = 1.0f;

            float y = (float) center.y;
            for (int i = 0; i < segments; i++) {
                double angle1 = (2 * Math.PI * i) / segments;
                double angle2 = (2 * Math.PI * (i + 1)) / segments;

                float x1 = (float) (center.x + Math.cos(angle1) * radius);
                float z1 = (float) (center.z + Math.sin(angle1) * radius);
                float x2 = (float) (center.x + Math.cos(angle2) * radius);
                float z2 = (float) (center.z + Math.sin(angle2) * radius);

                consumer.addVertex(matrix, x1, y, z1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
                consumer.addVertex(matrix, x2, y, z2).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
            }

            poseStack.popPose();
        }
    }

    // ===== ARROW =====

    public record DebugArrow(Vec3 from, Vec3 to, int color, float arrowSize) implements DebugShape {
        @Override
        public Vec3 getCenter() { return Objects.requireNonNull(Objects.requireNonNull(from.add(Objects.requireNonNull(to))).scale(0.5)); }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos) {
            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            VertexConsumer consumer = buffer.getBuffer(Objects.requireNonNull(RenderType.lines()));
            var pose = Objects.requireNonNull(poseStack.last());
            Matrix4f matrix = Objects.requireNonNull(pose.pose());

            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float a = ((color >> 24) & 0xFF) / 255f;
            if (a == 0) a = 1.0f;

            // Main line
            consumer.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                    .setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
            consumer.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                    .setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);

            // Arrow head
            Vec3 direction = Objects.requireNonNull(Objects.requireNonNull(to.subtract(Objects.requireNonNull(from))).normalize());
            Vec3 perpendicular1 = Objects.requireNonNull(new Vec3(-direction.y, direction.x, 0).normalize());
            Vec3 perpendicular2 = Objects.requireNonNull(Objects.requireNonNull(direction.cross(perpendicular1)).normalize());

            Vec3 arrowBase = Objects.requireNonNull(to.subtract(Objects.requireNonNull(direction.scale(arrowSize))));
            Vec3 arrowTip1 = Objects.requireNonNull(arrowBase.add(Objects.requireNonNull(perpendicular1.scale(arrowSize * 0.5))));
            Vec3 arrowTip2 = Objects.requireNonNull(arrowBase.subtract(Objects.requireNonNull(perpendicular1.scale(arrowSize * 0.5))));
            Vec3 arrowTip3 = Objects.requireNonNull(arrowBase.add(Objects.requireNonNull(perpendicular2.scale(arrowSize * 0.5))));
            Vec3 arrowTip4 = Objects.requireNonNull(arrowBase.subtract(Objects.requireNonNull(perpendicular2.scale(arrowSize * 0.5))));

            // Arrow head lines
            line(consumer, matrix, pose, to, arrowTip1, r, g, b, a);
            line(consumer, matrix, pose, to, arrowTip2, r, g, b, a);
            line(consumer, matrix, pose, to, arrowTip3, r, g, b, a);
            line(consumer, matrix, pose, to, arrowTip4, r, g, b, a);

            poseStack.popPose();
        }

        private void line(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                         Vec3 from, Vec3 to, float r, float g, float b, float a) {
            Matrix4f safeMatrix = Objects.requireNonNull(matrix);
            PoseStack.Pose safePose = Objects.requireNonNull(pose);
            consumer.addVertex(safeMatrix, (float) from.x, (float) from.y, (float) from.z)
                    .setColor(r, g, b, a).setNormal(safePose, 0f, 1f, 0f);
            consumer.addVertex(safeMatrix, (float) to.x, (float) to.y, (float) to.z)
                    .setColor(r, g, b, a).setNormal(safePose, 0f, 1f, 0f);
        }
    }

    // ===== LABEL =====

    public record DebugLabel(Vec3 pos, String text, int color) {
        /** Returns position for distance culling */
        public Vec3 getPosition() { return pos; }

        public void render(PoseStack poseStack, Vec3 cameraPos) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.font == null) return;

            poseStack.pushPose();

            // Position relative to camera
            float x = (float) (pos.x - cameraPos.x);
            float y = (float) (pos.y - cameraPos.y);
            float z = (float) (pos.z - cameraPos.z);

            poseStack.translate(x, y, z);

            // Billboard (always face camera)
            poseStack.mulPose(Objects.requireNonNull(mc.getEntityRenderDispatcher().cameraOrientation()));

            // Scale
            float scale = 0.025f;
            poseStack.scale(-scale, -scale, scale);

            // Center text
            String safeText = Objects.requireNonNull(text);
            int width = mc.font.width(safeText);
            float xOffset = -width / 2f;

            // Draw with background
            mc.font.drawInBatch(safeText, xOffset, 0, color, false,
                    Objects.requireNonNull(poseStack.last().pose()), Objects.requireNonNull(mc.renderBuffers().bufferSource()),
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0x40000000, 15728880);

            poseStack.popPose();
        }
    }
}
