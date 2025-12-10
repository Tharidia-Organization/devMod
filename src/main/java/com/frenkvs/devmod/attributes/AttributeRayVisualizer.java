package com.frenkvs.devmod.attributes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;

/**
 * 3D visualizer for Line of Sight rays for the attribute monitoring system.
 *
 * Shows:
 * - Green rays towards entities with clear LoS
 * - Yellow/red rays towards entities with blocked LoS
 * - Markers on blocking points
 * - Primary target indicator
 *
 * Inspired by the reference image with colored rays towards entities.
 */
@SuppressWarnings("null") // Minecraft rendering APIs (Matrix4f, PoseStack, Vec3, RenderType) are guaranteed non-null
public class AttributeRayVisualizer {
    public static final AttributeRayVisualizer INSTANCE = new AttributeRayVisualizer();

    // === Ray colors ===
    private static final float[] COLOR_LOS_CLEAR = {0.0f, 1.0f, 0.5f, 0.6f};      // Aqua green
    private static final float[] COLOR_LOS_BLOCKED = {1.0f, 0.3f, 0.0f, 0.5f};    // Orange
    private static final float[] COLOR_PRIMARY = {0.0f, 1.0f, 1.0f, 0.8f};        // Bright cyan
    private static final float[] COLOR_BLOCK_POINT = {1.0f, 0.0f, 0.0f, 0.7f};    // Red

    private AttributeRayVisualizer() {}

    /**
     * Render all rays towards tracked entities.
     * Called during RenderLevelStageEvent.Stage.AFTER_ENTITIES.
     */
    public void render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, @Nonnull Vec3 cameraPos) {
        if (!AttributeMonitoringSystem.INSTANCE.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 playerEye = mc.player.getEyePosition();
        TrackedEntity primaryTarget = AttributeMonitoringSystem.INSTANCE.getPrimaryTarget();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f matrix = poseStack.last().pose();
        var pose = poseStack.last();

        VertexConsumer lineConsumer = buffer.getBuffer(RenderType.lines());

        // Render rays for all tracked entities
        for (TrackedEntity tracked : AttributeMonitoringSystem.INSTANCE.getTrackedEntities()) {
            if (!tracked.isValid()) continue;

            LivingEntity entity = tracked.getEntity();
            if (entity == null) continue;

            boolean isPrimary = tracked == primaryTarget;
            Vec3 entityPos = entity.getEyePosition();

            if (tracked.hasLineOfSight()) {
                // Clear LoS - green ray (or cyan if primary)
                float[] color = isPrimary ? COLOR_PRIMARY : COLOR_LOS_CLEAR;
                renderRay(lineConsumer, matrix, pose, playerEye, entityPos, color);

                // If primary, add thicker/brighter ray
                if (isPrimary) {
                    renderTargetIndicator(lineConsumer, matrix, pose, entityPos);
                }
            } else {
                // Blocked LoS
                Vec3 blockPoint = tracked.getLastBlockedPoint();
                if (blockPoint != null) {
                    // Yellow ray to blocking point
                    renderRay(lineConsumer, matrix, pose, playerEye, blockPoint, COLOR_LOS_BLOCKED);

                    // Dashed red ray from block to entity
                    renderDashedRay(lineConsumer, matrix, pose, blockPoint, entityPos, COLOR_BLOCK_POINT);

                    // Marker on blocking point
                    renderBlockMarker(lineConsumer, matrix, pose, blockPoint);
                } else {
                    // Fallback: direct orange ray
                    renderRay(lineConsumer, matrix, pose, playerEye, entityPos, COLOR_LOS_BLOCKED);
                }
            }
        }

        poseStack.popPose();
    }

    private void renderRay(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                           Vec3 from, Vec3 to, float[] color) {
        consumer.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 0f, 1f, 0f);
        consumer.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 0f, 1f, 0f);
    }

    private void renderDashedRay(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                                  Vec3 from, Vec3 to, float[] color) {
        // Simulate dashing with shorter segments and reduced alpha
        Vec3 direction = to.subtract(from);
        double length = direction.length();
        Vec3 step = direction.normalize().scale(0.5); // Segmenti da 0.5 blocchi

        Vec3 current = from;
        boolean draw = true;
        int segments = (int) (length / 0.5);

        for (int i = 0; i < segments && i < 50; i++) { // Max 50 segments
            Vec3 next = current.add(step);

            if (draw) {
                float alpha = color[3] * 0.5f; // Reduced alpha for dashing
                consumer.addVertex(matrix, (float) current.x, (float) current.y, (float) current.z)
                    .setColor(color[0], color[1], color[2], alpha)
                    .setNormal(pose, 0f, 1f, 0f);
                consumer.addVertex(matrix, (float) next.x, (float) next.y, (float) next.z)
                    .setColor(color[0], color[1], color[2], alpha)
                    .setNormal(pose, 0f, 1f, 0f);
            }

            current = next;
            draw = !draw; // Alternate segments
        }
    }

    private void renderTargetIndicator(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, Vec3 pos) {
        // 3D cross around primary target
        float size = 0.3f;
        float[] color = COLOR_PRIMARY;

        // X axis
        consumer.addVertex(matrix, (float) pos.x - size, (float) pos.y, (float) pos.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 1f, 0f, 0f);
        consumer.addVertex(matrix, (float) pos.x + size, (float) pos.y, (float) pos.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 1f, 0f, 0f);

        // Y axis
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y - size, (float) pos.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 0f, 1f, 0f);
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y + size, (float) pos.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 0f, 1f, 0f);

        // Z axis
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z - size)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 0f, 0f, 1f);
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z + size)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 0f, 0f, 1f);
    }

    private void renderBlockMarker(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, Vec3 pos) {
        // Small diamond/rhombus on blocking point
        float size = 0.15f;
        float[] color = COLOR_BLOCK_POINT;

        // Horizontal rhombus
        consumer.addVertex(matrix, (float) pos.x - size, (float) pos.y, (float) pos.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 1f, 0f, 0f);
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z - size)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 0f, 0f, 1f);

        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z - size)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 0f, 0f, 1f);
        consumer.addVertex(matrix, (float) pos.x + size, (float) pos.y, (float) pos.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 1f, 0f, 0f);

        consumer.addVertex(matrix, (float) pos.x + size, (float) pos.y, (float) pos.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 1f, 0f, 0f);
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z + size)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 0f, 0f, 1f);

        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z + size)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 0f, 0f, 1f);
        consumer.addVertex(matrix, (float) pos.x - size, (float) pos.y, (float) pos.z)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(pose, 1f, 0f, 0f);
    }
}
