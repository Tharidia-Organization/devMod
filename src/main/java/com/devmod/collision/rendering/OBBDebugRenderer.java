package com.devmod.collision.rendering;

import com.devmod.config.Config;
import com.devmod.collision.bodypart.BodyPartInstance;
import com.devmod.collision.integration.OBBHitHelper;
import com.devmod.collision.obb.OrientedBoundingBox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Renders OBB hitboxes for debugging.
 * Extends existing BodyPartRenderer with rotation support.
 *
 * Features:
 * - Wireframe OBB with rotation visible
 * - Color-coded by body part type
 * - Shows local axes to visualize orientation (RGB = XYZ)
 * - Animation-synced updates
 */

public final class OBBDebugRenderer {

    private OBBDebugRenderer() {} // Utility class

    // Opacity levels
    private static final float EDGE_OPACITY = 0.9f;
    private static final float FACE_OPACITY = 0.2f;
    private static final float AXIS_OPACITY = 0.8f;

    // ==================== Main Rendering API ====================

    /**
     * Renders all OBB hitboxes for an entity.
     *
     * @param poseStack    The pose stack for transforms
     * @param entity       The entity to render hitboxes for
     * @param cameraPos    Camera position for offset
     * @param bufferSource Buffer source for rendering
     * @param showLabels   Whether to show body part labels
     * @param showAxes     Whether to show OBB orientation axes
     */
    public static void renderEntityOBBs(@Nonnull PoseStack poseStack,
                                        @Nonnull LivingEntity entity,
                                        @Nonnull Vec3 cameraPos,
                                        @Nonnull MultiBufferSource bufferSource,
                                        boolean showLabels,
                                        boolean showAxes) {
        // Get OBB instances for the entity
        BodyPartInstance[] parts = OBBHitHelper.getBodyPartInstances(entity);

        try {
            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix = Objects.requireNonNull(pose.pose());

            // Render each body part OBB
            for (BodyPartInstance part : parts) {
                if (part == null || !part.isUpdated()) continue;

                OrientedBoundingBox obb = part.getWorldOBB();
                int color = part.getRenderColor();

                // Render the OBB
                renderOBB(bufferSource, matrix, pose, obb, color);

                // Render axes if enabled
                if (showAxes) {
                    renderOBBAxes(bufferSource, matrix, pose, obb);
                }
            }

            poseStack.popPose();

        } finally {
            // Always release pooled instances
            BodyPartInstance.releaseMultiple(parts);
        }
    }

    /**
     * Renders nearby entity OBBs (for use in render events).
     *
     * @param poseStack    The pose stack
     * @param bufferSource Buffer source
     * @param cameraPos    Camera position
     * @param entities     Entities to render
     */
    public static void renderNearbyEntityOBBs(@Nonnull PoseStack poseStack,
                                              @Nonnull MultiBufferSource bufferSource,
                                              @Nonnull Vec3 cameraPos,
                                              @Nonnull Iterable<? extends LivingEntity> entities) {
        boolean showAxes = false;
        try {
            showAxes = Config.OBB_DEBUG_AXES.get();
        } catch (Exception ignored) {}

        for (LivingEntity entity : entities) {
            renderEntityOBBs(poseStack, Objects.requireNonNull(entity), cameraPos, bufferSource, false, showAxes);
        }
    }

    // ==================== OBB Rendering ====================

    /**
     * Renders a single OBB with wireframe edges and transparent faces.
     */
    public static void renderOBB(@Nonnull MultiBufferSource bufferSource,
                                 @Nonnull Matrix4f matrix,
                                 @Nonnull PoseStack.Pose pose,
                                 @Nonnull OrientedBoundingBox obb,
                                 int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        // Get OBB corners
        Vec3[] corners = obb.getCorners();

        // Render transparent faces
        VertexConsumer facesConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.debugQuads()));
        renderOBBFaces(Objects.requireNonNull(facesConsumer), matrix, pose, corners, r, g, b, FACE_OPACITY);

        // Render wireframe edges
        VertexConsumer edgesConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));
        renderOBBEdges(Objects.requireNonNull(edgesConsumer), matrix, pose, corners, r, g, b, EDGE_OPACITY);
    }

    /**
     * Renders the 3 local axes of an OBB for orientation visualization.
     * Red = X, Green = Y, Blue = Z
     */
    public static void renderOBBAxes(@Nonnull MultiBufferSource bufferSource,
                                     @Nonnull Matrix4f matrix,
                                     @Nonnull PoseStack.Pose pose,
                                     @Nonnull OrientedBoundingBox obb) {
        Vec3 center = Objects.requireNonNull(obb.getCenter());
        Vec3[] axes = obb.getAxes();
        Vec3 halfExtents = Objects.requireNonNull(obb.getHalfExtents());

        VertexConsumer consumer = Objects.requireNonNull(bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines())));

        // Axis length = half extent * 1.5 for visibility
        double xLen = halfExtents.x * 1.5;
        double yLen = halfExtents.y * 1.5;
        double zLen = halfExtents.z * 1.5;

        // X axis (red)
        Vec3 xEnd = Objects.requireNonNull(center.add(Objects.requireNonNull(axes[0].scale(xLen))));
        renderLine(consumer, matrix, pose, center, xEnd, 1f, 0f, 0f, AXIS_OPACITY);

        // Y axis (green)
        Vec3 yEnd = Objects.requireNonNull(center.add(Objects.requireNonNull(axes[1].scale(yLen))));
        renderLine(consumer, matrix, pose, center, yEnd, 0f, 1f, 0f, AXIS_OPACITY);

        // Z axis (blue)
        Vec3 zEnd = Objects.requireNonNull(center.add(Objects.requireNonNull(axes[2].scale(zLen))));
        renderLine(consumer, matrix, pose, center, zEnd, 0f, 0f, 1f, AXIS_OPACITY);
    }

    // ==================== Primitive Rendering ====================

    /**
     * Renders OBB faces (6 quads).
     * Corner order from getCorners():
     * 0=(-,-,-), 1=(+,-,-), 2=(-,+,-), 3=(+,+,-), 4=(-,-,+), 5=(+,-,+), 6=(-,+,+), 7=(+,+,+)
     */
    private static void renderOBBFaces(VertexConsumer consumer,
                                       Matrix4f matrix,
                                       PoseStack.Pose pose,
                                       Vec3[] c,
                                       float r, float g, float b, float a) {
        // Bottom face (Y-): 0,1,5,4
        quad(consumer, matrix, pose, c[0], c[1], c[5], c[4], r, g, b, a, 0, -1, 0);
        // Top face (Y+): 2,6,7,3
        quad(consumer, matrix, pose, c[2], c[6], c[7], c[3], r, g, b, a, 0, 1, 0);
        // Front face (Z-): 0,2,3,1
        quad(consumer, matrix, pose, c[0], c[2], c[3], c[1], r, g, b, a, 0, 0, -1);
        // Back face (Z+): 4,5,7,6
        quad(consumer, matrix, pose, c[4], c[5], c[7], c[6], r, g, b, a, 0, 0, 1);
        // Left face (X-): 0,4,6,2
        quad(consumer, matrix, pose, c[0], c[4], c[6], c[2], r, g, b, a, -1, 0, 0);
        // Right face (X+): 1,3,7,5
        quad(consumer, matrix, pose, c[1], c[3], c[7], c[5], r, g, b, a, 1, 0, 0);
    }

    /**
     * Renders OBB edges (12 lines).
     */
    private static void renderOBBEdges(VertexConsumer consumer,
                                       Matrix4f matrix,
                                       PoseStack.Pose pose,
                                       Vec3[] c,
                                       float r, float g, float b, float a) {
        int[][] edges = OrientedBoundingBox.getEdgeIndices();

        for (int[] edge : edges) {
            renderLine(consumer, matrix, pose, c[edge[0]], c[edge[1]], r, g, b, a);
        }
    }

    /**
     * Renders a single line.
     */
    private static void renderLine(VertexConsumer consumer,
                                   Matrix4f matrix,
                                   PoseStack.Pose pose,
                                   Vec3 start,
                                   Vec3 end,
                                   float r, float g, float b, float a) {
        Matrix4f m = Objects.requireNonNull(matrix);
        PoseStack.Pose p = Objects.requireNonNull(pose);
        consumer.addVertex(m, (float) start.x, (float) start.y, (float) start.z)
                .setColor(r, g, b, a)
                .setNormal(p, 0f, 1f, 0f);
        consumer.addVertex(m, (float) end.x, (float) end.y, (float) end.z)
                .setColor(r, g, b, a)
                .setNormal(p, 0f, 1f, 0f);
    }

    /**
     * Renders a quad (4 vertices).
     */
    private static void quad(VertexConsumer consumer,
                            Matrix4f matrix,
                            PoseStack.Pose pose,
                            Vec3 v1, Vec3 v2,
                            Vec3 v3, Vec3 v4,
                            float r, float g, float b, float a,
                            float nx, float ny, float nz) {
        Matrix4f m = Objects.requireNonNull(matrix);
        PoseStack.Pose p = Objects.requireNonNull(pose);
        consumer.addVertex(m, (float) v1.x, (float) v1.y, (float) v1.z)
                .setColor(r, g, b, a).setNormal(p, nx, ny, nz);
        consumer.addVertex(m, (float) v2.x, (float) v2.y, (float) v2.z)
                .setColor(r, g, b, a).setNormal(p, nx, ny, nz);
        consumer.addVertex(m, (float) v3.x, (float) v3.y, (float) v3.z)
                .setColor(r, g, b, a).setNormal(p, nx, ny, nz);
        consumer.addVertex(m, (float) v4.x, (float) v4.y, (float) v4.z)
                .setColor(r, g, b, a).setNormal(p, nx, ny, nz);
    }

    // ==================== Hit Highlight ====================

    /**
     * Renders pulsing highlight for a hit body part.
     *
     * @param poseStack    The pose stack
     * @param entity       The hit entity
     * @param hitPartIndex Index of the hit body part
     * @param cameraPos    Camera position
     * @param bufferSource Buffer source
     * @param hitTime      Time when hit occurred (for animation)
     */
    public static void renderHitHighlight(@Nonnull PoseStack poseStack,
                                          @Nonnull LivingEntity entity,
                                          int hitPartIndex,
                                          @Nonnull Vec3 cameraPos,
                                          @Nonnull MultiBufferSource bufferSource,
                                          long hitTime) {
        // Calculate pulse animation
        long timeSinceHit = System.currentTimeMillis() - hitTime;
        if (timeSinceHit > 500) return; // 500ms highlight duration

        float pulse = (float) Math.sin(timeSinceHit / 50.0) * 0.5f + 0.5f;
        float alpha = (1.0f - (timeSinceHit / 500.0f)) * pulse;

        // Get body part OBBs
        BodyPartInstance[] parts = OBBHitHelper.getBodyPartInstances(entity);

        try {
            if (hitPartIndex < 0 || hitPartIndex >= parts.length) return;

            BodyPartInstance hitPart = parts[hitPartIndex];
            if (hitPart == null || !hitPart.isUpdated()) return;

            OrientedBoundingBox obb = hitPart.getWorldOBB();

            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix = Objects.requireNonNull(pose.pose());

            // White pulsing wireframe
            Vec3[] corners = obb.getCorners();
            VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));
            renderOBBEdges(Objects.requireNonNull(consumer), matrix, pose, corners, 1f, 1f, 1f, alpha);

            poseStack.popPose();

        } finally {
            BodyPartInstance.releaseMultiple(parts);
        }
    }
}
