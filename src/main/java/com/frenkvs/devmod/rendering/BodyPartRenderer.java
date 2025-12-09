package com.frenkvs.devmod.rendering;

import com.frenkvs.devmod.HitHelper;
import com.frenkvs.devmod.ModConfig;
import com.frenkvs.devmod.WeaponConfigManager;
import com.frenkvs.devmod.WeaponStats;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;

/**
 * Sistema di rendering avanzato per body part hitboxes
 *
 * Features:
 * - Wireframe outline + transparent faces (stile PhantomShapes)
 * - Color-coded per body part (HEAD cyan, ARMS yellow, BODY green, LEGS red)
 * - Billboard labels con moltiplicatori
 * - Adaptive rendering per non-humanoid entities
 * - Pulsing effect per parte colpita (opzionale)
 */
public class BodyPartRenderer {

    // Color definitions (ARGB format)
    private static final int COLOR_HEAD = 0xFF00FFFF;  // Cyan
    private static final int COLOR_ARMS = 0xFFFFFF00;  // Yellow
    private static final int COLOR_BODY = 0xFF00FF00;  // Green
    private static final int COLOR_LEGS = 0xFFFF0000;  // Red
    private static final int COLOR_HIGHLIGHT = 0xFFFFFFFF; // White (hit highlight)

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

        // Usa BodyPartCalculator come single source of truth
        BodyPartCalculator.BodyPartAABB[] bodyParts = BodyPartCalculator.calculateAllBodyParts(entity);

        // Ottieni stats per i moltiplicatori (solo per le label)
        WeaponStats stats = WeaponConfigManager.getGlobalStats();

        Matrix4f matrix = poseStack.last().pose();
        var pose = poseStack.last();

        // Renderizza ogni body part
        for (BodyPartCalculator.BodyPartAABB bodyPart : bodyParts) {
            String label = generateLabel(bodyPart.part(), stats);
            renderBodyPartBox(bufferSource, matrix, pose, bodyPart.box(), bodyPart.color(), label, showLabels);
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
    private static void renderBodyPartBox(MultiBufferSource bufferSource, Matrix4f matrix, PoseStack.Pose pose,
                                         AABB box, int color, String label, boolean showLabels) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        // 1. Render transparent faces (solid box)
        VertexConsumer facesConsumer = bufferSource.getBuffer(RenderType.debugQuads());
        renderSolidBox(facesConsumer, matrix, pose, box, r, g, b, FACE_OPACITY);

        // 2. Render opaque edges (wireframe)
        VertexConsumer edgesConsumer = bufferSource.getBuffer(RenderType.lines());
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
    private static void renderWireframeBox(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                                          AABB box, float r, float g, float b, float a) {
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
    private static void renderSolidBox(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                                      AABB box, float r, float g, float b, float a) {
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

    private static void line(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
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
     * Renderizza highlight per parte colpita (pulsing effect)
     * Usa BodyPartCalculator per ottenere l'AABB corretto
     */
    public static void renderHitHighlight(PoseStack poseStack, LivingEntity entity, HitHelper.BodyPart hitPart,
                                         Vec3 cameraPos, MultiBufferSource bufferSource, long hitTime) {
        // Pulsing effect basato su tempo
        long timeSinceHit = System.currentTimeMillis() - hitTime;
        if (timeSinceHit > 500) return; // Highlight per 500ms

        float pulse = (float) Math.sin(timeSinceHit / 50.0) * 0.5f + 0.5f; // 0.0-1.0 oscillation
        float alpha = (1.0f - (timeSinceHit / 500.0f)) * pulse; // Fade out

        // Usa BodyPartCalculator per ottenere l'AABB della parte colpita
        BodyPartCalculator.BodyPartAABB hitBodyPart = BodyPartCalculator.calculateBodyPart(entity, hitPart);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f matrix = poseStack.last().pose();
        var pose = poseStack.last();

        // White highlight wireframe
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        renderWireframeBox(consumer, matrix, pose, hitBodyPart.box(), 1.0f, 1.0f, 1.0f, alpha);

        poseStack.popPose();
    }
}
