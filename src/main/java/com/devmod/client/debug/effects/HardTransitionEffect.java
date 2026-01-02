package com.devmod.client.debug.effects;

import java.util.Objects;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneEnvironment;

/**
 * Hard transition effect - clean lines with pulsing glow.
 * The default effect for zone boundaries.
 */
public class HardTransitionEffect implements TransitionEffect {

    private static final float PULSE_SPEED = 2.0f;
    private static final float PULSE_MIN = 0.6f;
    private static final float PULSE_MAX = 1.0f;
    private static final float GLOW_WIDTH = 0.5f;
    private static final int SEGMENTS_PER_CIRCLE = 32;

    @Override
    public String getName() {
        return "hard";
    }

    @Override
    public boolean usesGlow() {
        return true;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 cameraPos,
            ArenaZone zone,
            int baseY,
            float time
    ) {
        // Calculate pulse intensity
        float pulse = PULSE_MIN + (PULSE_MAX - PULSE_MIN) *
            (float)(0.5 + 0.5 * Math.sin(time * PULSE_SPEED));

        int color = getZoneColor(zone.environment());
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose(), "pose matrix");

        // Render solid lines
        VertexConsumer lineConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));
        renderBoundaryLines(lineConsumer, matrix, zone, r, g, b, pulse, baseY);

        // Render glow quads using translucent type
        VertexConsumer glowConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.translucent()));
        renderGlowQuads(glowConsumer, matrix, zone, r, g, b, pulse * 0.3f, baseY);

        poseStack.popPose();
    }

    private void renderBoundaryLines(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                      ArenaZone zone, float r, float g, float b,
                                      float alpha, int baseY) {
        ArenaZone.ZoneBounds bounds = zone.bounds();
        float y1 = baseY + 0.1f;
        float y2 = baseY + 3.0f;

        switch (zone.shape()) {
            case RECTANGULAR -> renderRectLines(consumer, matrix, bounds, r, g, b, alpha, y1, y2);
            case CIRCULAR -> renderCircleLines(consumer, matrix, bounds, r, g, b, alpha, y1, y2, false);
            case RING -> {
                renderCircleLines(consumer, matrix, bounds, r, g, b, alpha, y1, y2, false);
                renderCircleLines(consumer, matrix, bounds, r * 0.7f, g * 0.7f, b * 0.7f, alpha, y1, y2, true);
            }
        }
    }

    private void renderRectLines(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                  ArenaZone.ZoneBounds bounds,
                                  float r, float g, float b, float a,
                                  float y1, float y2) {
        float x1 = bounds.x1();
        float z1 = bounds.z1();
        float x2 = bounds.x2();
        float z2 = bounds.z2();

        // Bottom rectangle
        addLine(consumer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        addLine(consumer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        addLine(consumer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        addLine(consumer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        // Top rectangle
        addLine(consumer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        addLine(consumer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        addLine(consumer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        addLine(consumer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        // Vertical edges
        addLine(consumer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        addLine(consumer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        addLine(consumer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        addLine(consumer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private void renderCircleLines(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                    ArenaZone.ZoneBounds bounds,
                                    float r, float g, float b, float a,
                                    float y1, float y2, boolean useInner) {
        BlockPos center = bounds.getCenter();
        float cx = center.getX() + 0.5f;
        float cz = center.getZ() + 0.5f;

        int radius = useInner
            ? bounds.innerRadius().orElse(bounds.radius().orElse(0) / 2)
            : bounds.radius().orElse(0);
        if (radius <= 0) return;

        // Bottom circle
        drawCircle(consumer, matrix, cx, y1, cz, radius, r, g, b, a);
        // Top circle
        drawCircle(consumer, matrix, cx, y2, cz, radius, r, g, b, a);

        // Vertical lines at cardinal directions
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2;
            float px = cx + (float)(Math.cos(angle) * radius);
            float pz = cz + (float)(Math.sin(angle) * radius);
            addLine(consumer, matrix, px, y1, pz, px, y2, pz, r, g, b, a);
        }
    }

    private void drawCircle(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                            float cx, float y, float cz, float radius,
                            float r, float g, float b, float a) {
        float prevX = cx + radius;
        float prevZ = cz;

        for (int i = 1; i <= SEGMENTS_PER_CIRCLE; i++) {
            double angle = (2 * Math.PI * i) / SEGMENTS_PER_CIRCLE;
            float nextX = cx + (float)(Math.cos(angle) * radius);
            float nextZ = cz + (float)(Math.sin(angle) * radius);
            addLine(consumer, matrix, prevX, y, prevZ, nextX, y, nextZ, r, g, b, a);
            prevX = nextX;
            prevZ = nextZ;
        }
    }

    private void renderGlowQuads(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                  ArenaZone zone, float r, float g, float b,
                                  float alpha, int baseY) {
        // Glow quads are rendered as translucent strips along boundaries
        ArenaZone.ZoneBounds bounds = zone.bounds();
        float y = baseY + 0.05f;

        switch (zone.shape()) {
            case RECTANGULAR -> {
                float x1 = bounds.x1();
                float z1 = bounds.z1();
                float x2 = bounds.x2();
                float z2 = bounds.z2();

                // Render glow strips on each edge
                renderGlowStrip(consumer, matrix, x1 - GLOW_WIDTH, z1, x2 + GLOW_WIDTH, z1 + GLOW_WIDTH, y, r, g, b, alpha);
                renderGlowStrip(consumer, matrix, x1 - GLOW_WIDTH, z2 - GLOW_WIDTH, x2 + GLOW_WIDTH, z2, y, r, g, b, alpha);
                renderGlowStrip(consumer, matrix, x1 - GLOW_WIDTH, z1, x1, z2, y, r, g, b, alpha);
                renderGlowStrip(consumer, matrix, x2, z1, x2 + GLOW_WIDTH, z2, y, r, g, b, alpha);
            }
            case CIRCULAR -> {
                int radius = bounds.radius().orElse(0);
                if (radius > 0) {
                    renderCircularGlow(consumer, matrix, bounds, radius, y, r, g, b, alpha);
                }
            }
            case RING -> {
                int outerRadius = bounds.radius().orElse(0);
                int innerRadius = bounds.innerRadius().orElse(outerRadius / 2);
                if (outerRadius > 0) {
                    // Outer glow
                    renderCircularGlow(consumer, matrix, bounds, outerRadius, y, r, g, b, alpha);
                    // Inner glow (darker)
                    if (innerRadius > 0) {
                        renderCircularGlow(consumer, matrix, bounds, innerRadius, y, r * 0.7f, g * 0.7f, b * 0.7f, alpha * 0.8f);
                    }
                }
            }
        }
    }

    /**
     * Renders a circular glow as segmented quads around the perimeter.
     */
    private void renderCircularGlow(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                     ArenaZone.ZoneBounds bounds, int radius, float y,
                                     float r, float g, float b, float alpha) {
        BlockPos center = bounds.getCenter();
        float cx = center.getX() + 0.5f;
        float cz = center.getZ() + 0.5f;

        float innerR = radius - GLOW_WIDTH;
        float outerR = radius + GLOW_WIDTH;

        int colorInner = packColor(r, g, b, alpha);
        int colorOuter = packColor(r, g, b, 0); // Fade to transparent at edges

        // Render glow ring as segments
        for (int i = 0; i < SEGMENTS_PER_CIRCLE; i++) {
            double angle1 = (2 * Math.PI * i) / SEGMENTS_PER_CIRCLE;
            double angle2 = (2 * Math.PI * (i + 1)) / SEGMENTS_PER_CIRCLE;

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            // Inner edge of glow (full alpha)
            float ix1 = cx + cos1 * innerR;
            float iz1 = cz + sin1 * innerR;
            float ix2 = cx + cos2 * innerR;
            float iz2 = cz + sin2 * innerR;

            // Center of glow (at radius - brightest)
            float mx1 = cx + cos1 * radius;
            float mz1 = cz + sin1 * radius;
            float mx2 = cx + cos2 * radius;
            float mz2 = cz + sin2 * radius;

            // Outer edge of glow (fade to transparent)
            float ox1 = cx + cos1 * outerR;
            float oz1 = cz + sin1 * outerR;
            float ox2 = cx + cos2 * outerR;
            float oz2 = cz + sin2 * outerR;

            // Inner to middle quad (fade in)
            consumer.addVertex(matrix, ix1, y, iz1).setColor(colorOuter).setUv(0, 0).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, ix2, y, iz2).setColor(colorOuter).setUv(0, 1).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, mx2, y, mz2).setColor(colorInner).setUv(1, 1).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, mx1, y, mz1).setColor(colorInner).setUv(1, 0).setLight(15728880).setNormal(0, 1, 0);

            // Middle to outer quad (fade out)
            consumer.addVertex(matrix, mx1, y, mz1).setColor(colorInner).setUv(0, 0).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, mx2, y, mz2).setColor(colorInner).setUv(0, 1).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, ox2, y, oz2).setColor(colorOuter).setUv(1, 1).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, ox1, y, oz1).setColor(colorOuter).setUv(1, 0).setLight(15728880).setNormal(0, 1, 0);
        }
    }

    private int packColor(float r, float g, float b, float a) {
        return ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    private void renderGlowStrip(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                  float x1, float z1, float x2, float z2, float y,
                                  float r, float g, float b, float a) {
        // Render a flat quad
        int color = ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
        consumer.addVertex(matrix, x1, y, z1).setColor(color).setUv(0, 0).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x1, y, z2).setColor(color).setUv(0, 1).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, y, z2).setColor(color).setUv(1, 1).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, y, z1).setColor(color).setUv(1, 0).setLight(15728880).setNormal(0, 1, 0);
    }

    private void addLine(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 0.0001f) return;

        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }

    private int getZoneColor(ZoneEnvironment env) {
        if (env == null || ZoneEnvironment.DEFAULT.equals(env)) {
            return 0xFFFFFF;
        }

        var biome = env.biome();
        if (biome.isPresent()) {
            String path = biome.get().getPath().toLowerCase(java.util.Locale.ROOT);
            if (path.contains("nether") || path.contains("basalt") || path.contains("soul")) {
                return 0xFF4444;
            }
            if (path.contains("end")) {
                return 0xAA44FF;
            }
            if (path.contains("snow") || path.contains("ice") || path.contains("frozen")) {
                return 0x44FFFF;
            }
            if (path.contains("desert") || path.contains("badlands")) {
                return 0xFFFF44;
            }
            if (path.contains("ocean") || path.contains("river")) {
                return 0x4444FF;
            }
            if (path.contains("forest") || path.contains("jungle") || path.contains("taiga")) {
                return 0x44FF44;
            }
        }

        if (env.time() == ZoneEnvironment.TimeConfig.NIGHT) {
            return 0x6644AA;
        }
        if (env.time() == ZoneEnvironment.TimeConfig.DAY) {
            return 0xFFDD44;
        }

        return 0xCCCCCC;
    }
}
