package com.devmod.client.debug.effects;

import java.util.Objects;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.client.ui.overlay.OverlayTheme;

/**
 * Gradient transition effect - fog with particles and soft glow.
 * Creates a smooth blending effect at zone boundaries.
 */
public class GradientTransitionEffect implements TransitionEffect {

    private static final float GRADIENT_WIDTH = 2.0f;
    private static final float PARTICLE_SPAWN_CHANCE = 0.05f;
    private static final int SEGMENTS_PER_CIRCLE = 32;
    private static final Random RANDOM = new Random();

    // Track last particle spawn tick to limit rate
    private long lastParticleTick = 0;

    @Override
    public String getName() {
        return "gradient";
    }

    @Override
    public boolean usesParticles() {
        return true;
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
        int color = getZoneColor(zone.environment());
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose(), "pose matrix");

        // Render gradient fog strips
        VertexConsumer fogConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.translucent()));
        renderGradientFog(fogConsumer, matrix, zone, r, g, b, baseY, time);

        // Render soft boundary lines
        VertexConsumer lineConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));
        float lineAlpha = 0.4f + 0.2f * (float)Math.sin(time * 1.5f);
        renderBoundaryLines(lineConsumer, matrix, zone, r, g, b, lineAlpha, baseY);

        poseStack.popPose();

        // Spawn ambient particles at boundaries (client-side only)
        spawnBoundaryParticles(zone, baseY, cameraPos, time);
    }

    private void renderGradientFog(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                    ArenaZone zone, float r, float g, float b,
                                    int baseY, float time) {
        ArenaZone.ZoneBounds bounds = zone.bounds();
        float y = baseY + 0.02f;

        // Animate fog intensity
        float baseAlpha = 0.15f + 0.05f * (float)Math.sin(time * 0.8f);

        if (zone.shape() == ArenaZone.ZoneShape.RECTANGULAR) {
            float x1 = bounds.x1();
            float z1 = bounds.z1();
            float x2 = bounds.x2();
            float z2 = bounds.z2();

            // Gradient strips on each edge (inner to outer fade)
            // North edge
            renderGradientStrip(consumer, matrix, x1, z1 - GRADIENT_WIDTH, x2, z1, y, r, g, b, 0, baseAlpha);
            // South edge
            renderGradientStrip(consumer, matrix, x1, z2, x2, z2 + GRADIENT_WIDTH, y, r, g, b, baseAlpha, 0);
            // West edge
            renderGradientStripVertical(consumer, matrix, x1 - GRADIENT_WIDTH, z1, x1, z2, y, r, g, b, 0, baseAlpha);
            // East edge
            renderGradientStripVertical(consumer, matrix, x2, z1, x2 + GRADIENT_WIDTH, z2, y, r, g, b, baseAlpha, 0);
        } else if (zone.shape() == ArenaZone.ZoneShape.CIRCULAR) {
            // Render radial gradient around circle (outer edge only)
            renderCircularGradient(consumer, matrix, bounds, r, g, b, baseAlpha, y);
        } else if (zone.shape() == ArenaZone.ZoneShape.RING) {
            // Render gradient on both outer and inner edges
            int outerRadius = bounds.radius().orElse(0);
            int innerRadius = bounds.innerRadius().orElse(outerRadius / 2);
            if (outerRadius > 0) {
                // Outer edge gradient (fade outward)
                renderCircularGradient(consumer, matrix, bounds, r, g, b, baseAlpha, y);
                // Inner edge gradient (fade inward) - slightly different color
                if (innerRadius > 0) {
                    renderInnerRingGradient(consumer, matrix, bounds, innerRadius, r * 0.8f, g * 0.8f, b * 0.8f, baseAlpha, y);
                }
            }
        }
    }

    /**
     * Renders an inward gradient for the inner edge of a RING zone.
     */
    private void renderInnerRingGradient(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                          ArenaZone.ZoneBounds bounds, int innerRadius,
                                          float r, float g, float b, float alpha, float y) {
        BlockPos center = bounds.getCenter();
        float cx = center.getX() + 0.5f;
        float cz = center.getZ() + 0.5f;

        float innerEdge = innerRadius - GRADIENT_WIDTH;
        if (innerEdge < 0) innerEdge = 0;

        int colorInner = ((int)(alpha * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
        int colorOuter = 0; // Fully transparent

        // Draw ring segments (gradient fades toward center)
        for (int i = 0; i < SEGMENTS_PER_CIRCLE; i++) {
            double angle1 = (2 * Math.PI * i) / SEGMENTS_PER_CIRCLE;
            double angle2 = (2 * Math.PI * (i + 1)) / SEGMENTS_PER_CIRCLE;

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            // Inner ring boundary (full alpha)
            float bx1 = cx + cos1 * innerRadius;
            float bz1 = cz + sin1 * innerRadius;
            float bx2 = cx + cos2 * innerRadius;
            float bz2 = cz + sin2 * innerRadius;

            // Fade edge (transparent toward center)
            float fx1 = cx + cos1 * innerEdge;
            float fz1 = cz + sin1 * innerEdge;
            float fx2 = cx + cos2 * innerEdge;
            float fz2 = cz + sin2 * innerEdge;

            consumer.addVertex(matrix, bx1, y, bz1).setColor(colorInner).setUv(0, 0).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, fx1, y, fz1).setColor(colorOuter).setUv(0, 1).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, fx2, y, fz2).setColor(colorOuter).setUv(1, 1).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, bx2, y, bz2).setColor(colorInner).setUv(1, 0).setLight(15728880).setNormal(0, 1, 0);
        }
    }

    private void renderGradientStrip(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                      float x1, float z1, float x2, float z2, float y,
                                      float r, float g, float b,
                                      float alphaStart, float alphaEnd) {
        // Quad with alpha gradient from z1 to z2
        int colorStart = ((int)(alphaStart * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
        int colorEnd = ((int)(alphaEnd * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);

        consumer.addVertex(matrix, x1, y, z1).setColor(colorStart).setUv(0, 0).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x1, y, z2).setColor(colorEnd).setUv(0, 1).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, y, z2).setColor(colorEnd).setUv(1, 1).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, y, z1).setColor(colorStart).setUv(1, 0).setLight(15728880).setNormal(0, 1, 0);
    }

    private void renderGradientStripVertical(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                              float x1, float z1, float x2, float z2, float y,
                                              float r, float g, float b,
                                              float alphaStart, float alphaEnd) {
        // Quad with alpha gradient from x1 to x2
        int colorStart = ((int)(alphaStart * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
        int colorEnd = ((int)(alphaEnd * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);

        consumer.addVertex(matrix, x1, y, z1).setColor(colorStart).setUv(0, 0).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x1, y, z2).setColor(colorStart).setUv(0, 1).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, y, z2).setColor(colorEnd).setUv(1, 1).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, y, z1).setColor(colorEnd).setUv(1, 0).setLight(15728880).setNormal(0, 1, 0);
    }

    private void renderCircularGradient(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                         ArenaZone.ZoneBounds bounds,
                                         float r, float g, float b, float alpha, float y) {
        BlockPos center = bounds.getCenter();
        float cx = center.getX() + 0.5f;
        float cz = center.getZ() + 0.5f;
        int radius = bounds.radius().orElse(0);
        if (radius <= 0) return;

        float outerRadius = radius + GRADIENT_WIDTH;
        int colorInner = ((int)(alpha * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
        int colorOuter = 0; // Fully transparent

        // Draw ring segments
        for (int i = 0; i < SEGMENTS_PER_CIRCLE; i++) {
            double angle1 = (2 * Math.PI * i) / SEGMENTS_PER_CIRCLE;
            double angle2 = (2 * Math.PI * (i + 1)) / SEGMENTS_PER_CIRCLE;

            float ix1 = cx + (float)(Math.cos(angle1) * radius);
            float iz1 = cz + (float)(Math.sin(angle1) * radius);
            float ox1 = cx + (float)(Math.cos(angle1) * outerRadius);
            float oz1 = cz + (float)(Math.sin(angle1) * outerRadius);

            float ix2 = cx + (float)(Math.cos(angle2) * radius);
            float iz2 = cz + (float)(Math.sin(angle2) * radius);
            float ox2 = cx + (float)(Math.cos(angle2) * outerRadius);
            float oz2 = cz + (float)(Math.sin(angle2) * outerRadius);

            consumer.addVertex(matrix, ix1, y, iz1).setColor(colorInner).setUv(0, 0).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, ox1, y, oz1).setColor(colorOuter).setUv(0, 1).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, ox2, y, oz2).setColor(colorOuter).setUv(1, 1).setLight(15728880).setNormal(0, 1, 0);
            consumer.addVertex(matrix, ix2, y, iz2).setColor(colorInner).setUv(1, 0).setLight(15728880).setNormal(0, 1, 0);
        }
    }

    private void renderBoundaryLines(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                      ArenaZone zone, float r, float g, float b,
                                      float alpha, int baseY) {
        // Same as HardTransitionEffect but with lower alpha
        ArenaZone.ZoneBounds bounds = zone.bounds();
        float y = baseY + 0.1f;

        if (zone.shape() == ArenaZone.ZoneShape.RECTANGULAR) {
            float x1 = bounds.x1();
            float z1 = bounds.z1();
            float x2 = bounds.x2();
            float z2 = bounds.z2();

            addLine(consumer, matrix, x1, y, z1, x2, y, z1, r, g, b, alpha);
            addLine(consumer, matrix, x2, y, z1, x2, y, z2, r, g, b, alpha);
            addLine(consumer, matrix, x2, y, z2, x1, y, z2, r, g, b, alpha);
            addLine(consumer, matrix, x1, y, z2, x1, y, z1, r, g, b, alpha);
        }
    }

    private void spawnBoundaryParticles(ArenaZone zone, int baseY, Vec3 cameraPos, float time) {
        // time and cameraPos reserved for future distance-based and animated particle effects
        if (time < 0) throw new IllegalArgumentException("time must be non-negative");
        java.util.Objects.requireNonNull(cameraPos, "cameraPos");
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;

        // Limit particle spawn rate
        long currentTick = level.getGameTime();
        if (currentTick == lastParticleTick) return;
        lastParticleTick = currentTick;

        // Only spawn if player is near the zone
        BlockPos playerPos = player.blockPosition();
        ArenaZone.ZoneBounds bounds = zone.bounds();
        BlockPos center = bounds.getCenter();

        double distSq = playerPos.distSqr(Objects.requireNonNull(center));
        int radius = bounds.radius().orElse(Math.max(bounds.getWidth(), bounds.getDepth()) / 2);
        if (distSq > (radius + 20) * (radius + 20)) return;

        // Spawn particles along boundary
        if (RANDOM.nextFloat() < PARTICLE_SPAWN_CHANCE) {
            Vec3 particlePos = getRandomBoundaryPosition(zone, baseY);
            if (particlePos != null) {
                level.addParticle(
                    Objects.requireNonNull(ParticleTypes.END_ROD),
                    particlePos.x, particlePos.y, particlePos.z,
                    (RANDOM.nextDouble() - 0.5) * 0.02,
                    RANDOM.nextDouble() * 0.01,
                    (RANDOM.nextDouble() - 0.5) * 0.02
                );
            }
        }
    }

    @Nullable
    private Vec3 getRandomBoundaryPosition(ArenaZone zone, int baseY) {
        ArenaZone.ZoneBounds bounds = zone.bounds();

        if (zone.shape() == ArenaZone.ZoneShape.RECTANGULAR) {
            int edge = RANDOM.nextInt(4);
            float x1 = bounds.x1();
            float z1 = bounds.z1();
            float x2 = bounds.x2();
            float z2 = bounds.z2();

            return switch (edge) {
                case 0 -> new Vec3(x1 + RANDOM.nextFloat() * (x2 - x1), baseY + 0.5, z1);
                case 1 -> new Vec3(x2, baseY + 0.5, z1 + RANDOM.nextFloat() * (z2 - z1));
                case 2 -> new Vec3(x1 + RANDOM.nextFloat() * (x2 - x1), baseY + 0.5, z2);
                case 3 -> new Vec3(x1, baseY + 0.5, z1 + RANDOM.nextFloat() * (z2 - z1));
                default -> null;
            };
        } else if (zone.shape() == ArenaZone.ZoneShape.CIRCULAR) {
            BlockPos center = bounds.getCenter();
            int radius = bounds.radius().orElse(0);
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            return new Vec3(
                center.getX() + 0.5 + Math.cos(angle) * radius,
                baseY + 0.5,
                center.getZ() + 0.5 + Math.sin(angle) * radius
            );
        }

        return null;
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

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(dx/len, dy/len, dz/len);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(dx/len, dy/len, dz/len);
    }

    private int getZoneColor(ZoneEnvironment env) {
        // Same color logic as HardTransitionEffect
        if (env == null || ZoneEnvironment.DEFAULT.equals(env)) {
            return OverlayTheme.Debug.ZONE_ENV_DEFAULT;
        }

        var biome = env.biome();
        if (biome.isPresent()) {
            String path = biome.get().getPath().toLowerCase(java.util.Locale.ROOT);
            if (path.contains("nether")) return OverlayTheme.Debug.ZONE_ENV_NETHER;
            if (path.contains("end")) return OverlayTheme.Debug.ZONE_ENV_END;
            if (path.contains("snow") || path.contains("ice")) return OverlayTheme.Debug.ZONE_ENV_ICE;
            if (path.contains("desert")) return OverlayTheme.Debug.ZONE_ENV_DESERT;
            if (path.contains("ocean")) return OverlayTheme.Debug.ZONE_ENV_OCEAN;
            if (path.contains("forest")) return OverlayTheme.Debug.ZONE_ENV_FOREST;
        }

        return OverlayTheme.Debug.ZONE_ENV_FALLBACK;
    }
}
