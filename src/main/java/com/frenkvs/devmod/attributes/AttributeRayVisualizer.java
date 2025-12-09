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
 * Visualizzatore 3D dei raggi di Line of Sight per il sistema di monitoraggio attributi.
 *
 * Mostra:
 * - Raggi verdi verso entità con LoS libera
 * - Raggi gialli/rossi verso entità con LoS bloccata
 * - Marker sui punti di blocco
 * - Indicatore del target primario
 *
 * Ispirato all'immagine di riferimento con raggi colorati verso le entità.
 */
@SuppressWarnings("null") // Minecraft rendering APIs (Matrix4f, PoseStack, Vec3, RenderType) are guaranteed non-null
public class AttributeRayVisualizer {
    public static final AttributeRayVisualizer INSTANCE = new AttributeRayVisualizer();

    // === Colori raggi ===
    private static final float[] COLOR_LOS_CLEAR = {0.0f, 1.0f, 0.5f, 0.6f};      // Verde acqua
    private static final float[] COLOR_LOS_BLOCKED = {1.0f, 0.3f, 0.0f, 0.5f};    // Arancione
    private static final float[] COLOR_PRIMARY = {0.0f, 1.0f, 1.0f, 0.8f};        // Cyan brillante
    private static final float[] COLOR_BLOCK_POINT = {1.0f, 0.0f, 0.0f, 0.7f};    // Rosso

    private AttributeRayVisualizer() {}

    /**
     * Renderizza tutti i raggi verso le entità tracciate.
     * Chiamato durante RenderLevelStageEvent.Stage.AFTER_ENTITIES.
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

        // Renderizza raggi per tutte le entità tracciate
        for (TrackedEntity tracked : AttributeMonitoringSystem.INSTANCE.getTrackedEntities()) {
            if (!tracked.isValid()) continue;

            LivingEntity entity = tracked.getEntity();
            if (entity == null) continue;

            boolean isPrimary = tracked == primaryTarget;
            Vec3 entityPos = entity.getEyePosition();

            if (tracked.hasLineOfSight()) {
                // LoS libera - raggio verde (o cyan se primario)
                float[] color = isPrimary ? COLOR_PRIMARY : COLOR_LOS_CLEAR;
                renderRay(lineConsumer, matrix, pose, playerEye, entityPos, color);

                // Se primario, aggiungi raggio più spesso/luminoso
                if (isPrimary) {
                    renderTargetIndicator(lineConsumer, matrix, pose, entityPos);
                }
            } else {
                // LoS bloccata
                Vec3 blockPoint = tracked.getLastBlockedPoint();
                if (blockPoint != null) {
                    // Raggio giallo fino al punto di blocco
                    renderRay(lineConsumer, matrix, pose, playerEye, blockPoint, COLOR_LOS_BLOCKED);

                    // Raggio rosso tratteggiato dal blocco all'entità
                    renderDashedRay(lineConsumer, matrix, pose, blockPoint, entityPos, COLOR_BLOCK_POINT);

                    // Marker sul punto di blocco
                    renderBlockMarker(lineConsumer, matrix, pose, blockPoint);
                } else {
                    // Fallback: raggio arancione diretto
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
        // Simula tratteggio con segmenti più corti e alpha ridotto
        Vec3 direction = to.subtract(from);
        double length = direction.length();
        Vec3 step = direction.normalize().scale(0.5); // Segmenti da 0.5 blocchi

        Vec3 current = from;
        boolean draw = true;
        int segments = (int) (length / 0.5);

        for (int i = 0; i < segments && i < 50; i++) { // Max 50 segmenti
            Vec3 next = current.add(step);

            if (draw) {
                float alpha = color[3] * 0.5f; // Alpha ridotto per tratteggio
                consumer.addVertex(matrix, (float) current.x, (float) current.y, (float) current.z)
                    .setColor(color[0], color[1], color[2], alpha)
                    .setNormal(pose, 0f, 1f, 0f);
                consumer.addVertex(matrix, (float) next.x, (float) next.y, (float) next.z)
                    .setColor(color[0], color[1], color[2], alpha)
                    .setNormal(pose, 0f, 1f, 0f);
            }

            current = next;
            draw = !draw; // Alterna segmenti
        }
    }

    private void renderTargetIndicator(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, Vec3 pos) {
        // Croce 3D attorno al target primario
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
        // Piccolo diamante/rombo sul punto di blocco
        float size = 0.15f;
        float[] color = COLOR_BLOCK_POINT;

        // Rombo orizzontale
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
