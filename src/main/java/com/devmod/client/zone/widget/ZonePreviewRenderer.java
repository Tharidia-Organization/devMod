package com.devmod.client.zone.widget;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.zone.data.ZoneBounds;

/**
 * Renders zone bounds preview in the world.
 * Shows an outline box with the zone color.
 */
@OnlyIn(Dist.CLIENT)
public final class ZonePreviewRenderer {

    private ZonePreviewRenderer() {}

    /**
     * Renders a zone bounds outline in the world.
     * Call from a world render event.
     *
     * @param poseStack The pose stack
     * @param bufferSource The buffer source
     * @param bounds The zone bounds to render
     * @param color The zone color (ARGB)
     * @param cameraPos The camera position
     */
    public static void renderBoundsOutline(
            @Nonnull PoseStack poseStack,
            @Nonnull MultiBufferSource bufferSource,
            @Nonnull ZoneBounds bounds,
            int color,
            @Nonnull Vec3 cameraPos
    ) {
        // Extract color components
        float alpha = ((color >> 24) & 0xFF) / 255.0f;
        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;

        // If no alpha specified, use default
        if (alpha == 0) alpha = 0.8f;

        // Get line consumer
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Box corners
        float x1 = bounds.minX();
        float y1 = bounds.minY();
        float z1 = bounds.minZ();
        float x2 = bounds.maxX() + 1;
        float y2 = bounds.maxY() + 1;
        float z2 = bounds.maxZ() + 1;

        // Draw 12 edges of the box
        // Bottom face edges
        drawLine(poseStack, lineConsumer, x1, y1, z1, x2, y1, z1, red, green, blue, alpha);
        drawLine(poseStack, lineConsumer, x2, y1, z1, x2, y1, z2, red, green, blue, alpha);
        drawLine(poseStack, lineConsumer, x2, y1, z2, x1, y1, z2, red, green, blue, alpha);
        drawLine(poseStack, lineConsumer, x1, y1, z2, x1, y1, z1, red, green, blue, alpha);

        // Top face edges
        drawLine(poseStack, lineConsumer, x1, y2, z1, x2, y2, z1, red, green, blue, alpha);
        drawLine(poseStack, lineConsumer, x2, y2, z1, x2, y2, z2, red, green, blue, alpha);
        drawLine(poseStack, lineConsumer, x2, y2, z2, x1, y2, z2, red, green, blue, alpha);
        drawLine(poseStack, lineConsumer, x1, y2, z2, x1, y2, z1, red, green, blue, alpha);

        // Vertical edges
        drawLine(poseStack, lineConsumer, x1, y1, z1, x1, y2, z1, red, green, blue, alpha);
        drawLine(poseStack, lineConsumer, x2, y1, z1, x2, y2, z1, red, green, blue, alpha);
        drawLine(poseStack, lineConsumer, x2, y1, z2, x2, y2, z2, red, green, blue, alpha);
        drawLine(poseStack, lineConsumer, x1, y1, z2, x1, y2, z2, red, green, blue, alpha);

        poseStack.popPose();
    }

    private static void drawLine(
            PoseStack poseStack, VertexConsumer consumer,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float r, float g, float b, float a
    ) {
        PoseStack.Pose pose = poseStack.last();

        // Calculate normal
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0) {
            dx /= len;
            dy /= len;
            dz /= len;
        }

        consumer.addVertex(pose, x1, y1, z1)
            .setColor(r, g, b, a)
            .setNormal(pose, dx, dy, dz);

        consumer.addVertex(pose, x2, y2, z2)
            .setColor(r, g, b, a)
            .setNormal(pose, dx, dy, dz);
    }

    /**
     * Renders corner markers at each corner of the bounds.
     *
     * @param poseStack The pose stack
     * @param bufferSource The buffer source
     * @param bounds The zone bounds
     * @param color The zone color
     * @param cameraPos The camera position
     */
    public static void renderCornerMarkers(
            @Nonnull PoseStack poseStack,
            @Nonnull MultiBufferSource bufferSource,
            @Nonnull ZoneBounds bounds,
            int color,
            @Nonnull Vec3 cameraPos
    ) {
        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;

        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        float markerSize = 0.5f;

        // Draw markers at all 8 corners
        drawCornerMarker(poseStack, lineConsumer, bounds.minX(), bounds.minY(), bounds.minZ(), markerSize, red, green, blue);
        drawCornerMarker(poseStack, lineConsumer, bounds.maxX() + 1, bounds.minY(), bounds.minZ(), markerSize, red, green, blue);
        drawCornerMarker(poseStack, lineConsumer, bounds.minX(), bounds.minY(), bounds.maxZ() + 1, markerSize, red, green, blue);
        drawCornerMarker(poseStack, lineConsumer, bounds.maxX() + 1, bounds.minY(), bounds.maxZ() + 1, markerSize, red, green, blue);
        drawCornerMarker(poseStack, lineConsumer, bounds.minX(), bounds.maxY() + 1, bounds.minZ(), markerSize, red, green, blue);
        drawCornerMarker(poseStack, lineConsumer, bounds.maxX() + 1, bounds.maxY() + 1, bounds.minZ(), markerSize, red, green, blue);
        drawCornerMarker(poseStack, lineConsumer, bounds.minX(), bounds.maxY() + 1, bounds.maxZ() + 1, markerSize, red, green, blue);
        drawCornerMarker(poseStack, lineConsumer, bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1, markerSize, red, green, blue);

        poseStack.popPose();
    }

    private static void drawCornerMarker(
            PoseStack poseStack, VertexConsumer consumer,
            float x, float y, float z, float size,
            float r, float g, float b
    ) {
        // Draw 3 small lines at corner
        drawLine(poseStack, consumer, x, y, z, x + size, y, z, r, g, b, 1.0f);
        drawLine(poseStack, consumer, x, y, z, x, y + size, z, r, g, b, 1.0f);
        drawLine(poseStack, consumer, x, y, z, x, y, z + size, r, g, b, 1.0f);
    }

    /**
     * Calculates if a zone is within render distance.
     *
     * @param bounds The zone bounds
     * @param cameraPos The camera position
     * @param maxDistance Maximum render distance
     * @return true if zone should be rendered
     */
    public static boolean isInRenderRange(
            @Nonnull ZoneBounds bounds,
            @Nonnull Vec3 cameraPos,
            double maxDistance
    ) {
        BlockPos center = bounds.center();
        double dist = cameraPos.distanceTo(Vec3.atCenterOf(center));
        return dist <= maxDistance;
    }

    /**
     * Default render distance for zone previews.
     */
    public static final double DEFAULT_RENDER_DISTANCE = 64.0;
}
