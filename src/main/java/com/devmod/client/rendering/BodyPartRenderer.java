package com.devmod.client.rendering;

import java.util.Objects;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.devmod.ModConfig;
import com.devmod.combat.HitHelper;
import com.devmod.config.WeaponConfigManager;
import com.devmod.stats.WeaponStats;
public class BodyPartRenderer {

    // Color definitions (ARGB format)
    private static final int COLOR_HEAD = 0xFF00FFFF;  // Cyan
    private static final int COLOR_ARMS = 0xFFFFFF00;  // Yellow
    private static final int COLOR_BODY = 0xFF00FF00;  // Green
    private static final int COLOR_LEGS = 0xFFFF0000;  // Red

    // Opacity levels
    private static final float EDGE_OPACITY = 0.9f;
    private static final float FACE_OPACITY = 0.25f;

    /**
     * Renderizza tutte le body part hitboxes per un'entità
     * Usa BodyPartCalculator come single source of truth
     */
    public static void renderBodyPartHitboxes(@Nonnull PoseStack poseStack,
                                             @Nonnull LivingEntity entity,
                                             @Nonnull Vec3 cameraPos,
                                             @Nonnull MultiBufferSource bufferSource,
                                             boolean showLabels) {
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Use BodyPartCalculator as single source of truth
        BodyPartCalculator.BodyPartAABB[] bodyParts = Objects.requireNonNull(
            BodyPartCalculator.calculateAllBodyParts(entity), "body parts");

        // Get stats for multipliers (only for labels)
        WeaponStats stats = WeaponConfigManager.getGlobalStats();

        PoseStack.Pose pose = Objects.requireNonNull(poseStack.last(), "pose stack");
        Matrix4f matrix = Objects.requireNonNull(pose.pose(), "pose matrix");

        // Render each body part
        for (BodyPartCalculator.BodyPartAABB bodyPart : bodyParts) {
            String label = Objects.requireNonNull(generateLabel(bodyPart.part(), stats), "label");
            renderBodyPartBox(bufferSource, matrix, pose,
                Objects.requireNonNull(bodyPart.box(), "body part box"),
                bodyPart.color(),
                label,
                showLabels);
        }

        poseStack.popPose();
    }

    /**
     * Genera label con moltiplicatore per una body part
     */
    private static String generateLabel(HitHelper.BodyPart part, WeaponStats stats) {
        return switch (part) {
            case HEAD -> stats.headMult >= 2.0f
                ? String.format("Head [Crit x%.1f]", stats.headMult)
                : String.format("Head [x%.2f]", stats.headMult);
            case ARMS -> String.format("Arms [x%.2f]", stats.armsMult);
            case BODY -> String.format("Torso [x%.2f]", stats.bodyMult);
            case LEGS -> String.format("Legs [x%.2f]", stats.legsMult);
        };
    }

    /**
     * Renderizza singola body part box con wireframe + faces trasparenti
     */
    private static void renderBodyPartBox(@Nonnull MultiBufferSource bufferSource, @Nonnull Matrix4f matrix, @Nonnull PoseStack.Pose pose,
                                         @Nonnull AABB box, int color, @Nonnull String label, boolean showLabels) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        // 1. Render transparent faces (solid box)
        VertexConsumer facesConsumer = Objects.requireNonNull(
            bufferSource.getBuffer(Objects.requireNonNull(RenderType.debugQuads(), "faces render type")),
            "faces buffer");
        renderSolidBox(facesConsumer, matrix, pose, box, r, g, b, FACE_OPACITY);

        // 2. Render opaque edges (wireframe)
        VertexConsumer edgesConsumer = Objects.requireNonNull(
            bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines(), "edges render type")),
            "edges buffer");
        renderWireframeBox(edgesConsumer, matrix, pose, box, r, g, b, EDGE_OPACITY);

        // 3. Render label (opzionale)
        if (showLabels && ModConfig.showOverlay) {
            Vec3 labelPos = box.getCenter().add(0, box.getYsize() / 2 + 0.2, 0);
            DebugRenderer.INSTANCE.addLabel(labelPos, label, color, 50L); // 50ms lifetime
        }
    }

    /**
     * Renderizza wireframe box (12 edges)
     */
    private static void renderWireframeBox(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix, @Nonnull PoseStack.Pose pose,
                                          @Nonnull AABB box, float r, float g, float b, float a) {
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

    /**
     * Renderizza solid box (6 facce)
     */
    private static void renderSolidBox(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix, @Nonnull PoseStack.Pose pose,
                                      @Nonnull AABB box, float r, float g, float b, float a) {
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

    private static void line(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix, @Nonnull PoseStack.Pose pose,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
    }

    private static void quad(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix, @Nonnull PoseStack.Pose pose,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float x3, float y3, float z3, float x4, float y4, float z4,
                            float r, float g, float b, float a, float nx, float ny, float nz) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }

    /**
     * Ottieni colore per body part
     */
    public static int getColorForBodyPart(HitHelper.BodyPart part) {
        return switch (part) {
            case HEAD -> COLOR_HEAD;
            case ARMS -> COLOR_ARMS;
            case BODY -> COLOR_BODY;
            case LEGS -> COLOR_LEGS;
        };
    }

    /**
     * Renders highlight for hit body part (pulsing effect)
     * Uses BodyPartCalculator to get the correct AABB
     */
    public static void renderHitHighlight(@Nonnull PoseStack poseStack, @Nonnull LivingEntity entity, @Nonnull HitHelper.BodyPart hitPart,
                                         @Nonnull Vec3 cameraPos, @Nonnull MultiBufferSource bufferSource, long hitTime) {
        // Pulsing effect based on time
        long timeSinceHit = System.currentTimeMillis() - hitTime;
        if (timeSinceHit > 500) return; // Highlight for 500ms

        float pulse = (float) Math.sin(timeSinceHit / 50.0) * 0.5f + 0.5f; // 0.0-1.0 oscillation
        float alpha = (1.0f - (timeSinceHit / 500.0f)) * pulse; // Fade out

        // Use BodyPartCalculator to get the hit body part's AABB
        BodyPartCalculator.BodyPartAABB hitBodyPart = Objects.requireNonNull(
            BodyPartCalculator.calculateBodyPart(entity, hitPart),
            "hit body part");

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        PoseStack.Pose pose = Objects.requireNonNull(poseStack.last(), "pose stack");
        Matrix4f matrix = Objects.requireNonNull(pose.pose(), "pose matrix");

        // White highlight wireframe
        VertexConsumer consumer = Objects.requireNonNull(
            bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines(), "highlight render type")),
            "highlight buffer");
        renderWireframeBox(consumer, matrix, pose,
            Objects.requireNonNull(hitBodyPart.box(), "hit box"),
            1.0f, 1.0f, 1.0f, alpha);

        poseStack.popPose();
    }
}
