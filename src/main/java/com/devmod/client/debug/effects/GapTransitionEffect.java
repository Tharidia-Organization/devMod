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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.client.ui.overlay.OverlayTheme;

/**
 * Gap transition effect - void fog with falling particles.
 * Creates the appearance of a void/abyss gap at zone boundaries.
 * Suitable for zones that should feel isolated or ominous.
 */
public class GapTransitionEffect implements TransitionEffect {

    private static final float GAP_WIDTH = 2.0f;
    private static final float VOID_DEPTH = 8.0f;
    private static final float PARTICLE_SPAWN_CHANCE = 0.12f;
    private static final int SEGMENTS_PER_CIRCLE = 32;
    private static final Random RANDOM = new Random();

    private long lastParticleTick = 0;

    @Override
    public String getName() {
        return "gap";
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
        int color = getVoidColor(zone.environment());
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        // Subtle pulsing darkness
        float pulse = 0.7f + 0.1f * (float)Math.sin(time * 0.5f);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose(), "pose matrix");

        // Render void fog strips at boundary
        VertexConsumer fogConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.translucent()));
        renderVoidFog(fogConsumer, matrix, zone, r, g, b, pulse * 0.5f, baseY, time);

        // Render edge glow (distortion effect at edges)
        VertexConsumer glowConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.translucent()));
        renderEdgeGlow(glowConsumer, matrix, zone, baseY, time);

        // Render boundary lines (faint)
        VertexConsumer lineConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));
        renderVoidEdges(lineConsumer, matrix, zone, r * 0.5f, g * 0.5f, b * 0.5f, pulse * 0.3f, baseY);

        poseStack.popPose();

        // Spawn falling particles
        spawnFallingParticles(zone, baseY, cameraPos);
    }

    private void renderVoidFog(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                ArenaZone zone, float r, float g, float b,
                                float alpha, int baseY, float time) {
        // time reserved for future animated fog effects
        if (time < 0) throw new IllegalArgumentException("time must be non-negative");
        ArenaZone.ZoneBounds bounds = zone.bounds();

        // Void fog sits just below floor level
        float yTop = baseY - 0.1f;
        float yBottom = baseY - VOID_DEPTH;

        if (zone.shape() == ArenaZone.ZoneShape.RECTANGULAR) {
            float x1 = bounds.x1();
            float z1 = bounds.z1();
            float x2 = bounds.x2();
            float z2 = bounds.z2();

            // Render void strips on each edge (outside the zone)
            // North edge (outside = lower z)
            renderVoidStrip(consumer, matrix, x1 - GAP_WIDTH, z1 - GAP_WIDTH, x2 + GAP_WIDTH, z1, yTop, yBottom, r, g, b, alpha);
            // South edge
            renderVoidStrip(consumer, matrix, x1 - GAP_WIDTH, z2, x2 + GAP_WIDTH, z2 + GAP_WIDTH, yTop, yBottom, r, g, b, alpha);
            // West edge
            renderVoidStrip(consumer, matrix, x1 - GAP_WIDTH, z1, x1, z2, yTop, yBottom, r, g, b, alpha);
            // East edge
            renderVoidStrip(consumer, matrix, x2, z1, x2 + GAP_WIDTH, z2, yTop, yBottom, r, g, b, alpha);
        } else if (zone.shape() == ArenaZone.ZoneShape.CIRCULAR) {
            renderCircularVoid(consumer, matrix, bounds, r, g, b, alpha, yTop, yBottom);
        }
    }

    private void renderVoidStrip(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                  float x1, float z1, float x2, float z2,
                                  float yTop, float yBottom,
                                  float r, float g, float b, float alpha) {
        // Dark void color, more opaque at bottom
        int colorTop = packColor(r, g, b, alpha * 0.3f);
        int colorBottom = packColor(r * 0.2f, g * 0.2f, b * 0.2f, alpha);

        // Horizontal surface (top of void)
        consumer.addVertex(matrix, x1, yTop, z1).setColor(colorTop).setUv(0, 0).setLight(0).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x1, yTop, z2).setColor(colorTop).setUv(0, 1).setLight(0).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, yTop, z2).setColor(colorTop).setUv(1, 1).setLight(0).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, yTop, z1).setColor(colorTop).setUv(1, 0).setLight(0).setNormal(0, 1, 0);

        // Vertical surfaces (walls of void)
        // North wall
        consumer.addVertex(matrix, x1, yTop, z1).setColor(colorTop).setUv(0, 0).setLight(0).setNormal(0, 0, -1);
        consumer.addVertex(matrix, x1, yBottom, z1).setColor(colorBottom).setUv(0, 1).setLight(0).setNormal(0, 0, -1);
        consumer.addVertex(matrix, x2, yBottom, z1).setColor(colorBottom).setUv(1, 1).setLight(0).setNormal(0, 0, -1);
        consumer.addVertex(matrix, x2, yTop, z1).setColor(colorTop).setUv(1, 0).setLight(0).setNormal(0, 0, -1);

        // South wall
        consumer.addVertex(matrix, x2, yTop, z2).setColor(colorTop).setUv(0, 0).setLight(0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x2, yBottom, z2).setColor(colorBottom).setUv(0, 1).setLight(0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x1, yBottom, z2).setColor(colorBottom).setUv(1, 1).setLight(0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x1, yTop, z2).setColor(colorTop).setUv(1, 0).setLight(0).setNormal(0, 0, 1);
    }

    private void renderCircularVoid(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                     ArenaZone.ZoneBounds bounds,
                                     float r, float g, float b, float alpha,
                                     float yTop, float yBottom) {
        BlockPos center = bounds.getCenter();
        float cx = center.getX() + 0.5f;
        float cz = center.getZ() + 0.5f;
        int radius = bounds.radius().orElse(0);
        if (radius <= 0) return;

        float outerRadius = radius + GAP_WIDTH;
        int colorTop = packColor(r, g, b, alpha * 0.3f);
        int colorBottom = packColor(r * 0.2f, g * 0.2f, b * 0.2f, alpha);

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

            // Top surface
            consumer.addVertex(matrix, ix1, yTop, iz1).setColor(colorTop).setUv(0, 0).setLight(0).setNormal(0, 1, 0);
            consumer.addVertex(matrix, ox1, yTop, oz1).setColor(colorTop).setUv(0, 1).setLight(0).setNormal(0, 1, 0);
            consumer.addVertex(matrix, ox2, yTop, oz2).setColor(colorTop).setUv(1, 1).setLight(0).setNormal(0, 1, 0);
            consumer.addVertex(matrix, ix2, yTop, iz2).setColor(colorTop).setUv(1, 0).setLight(0).setNormal(0, 1, 0);

            // Inner wall (facing inward)
            consumer.addVertex(matrix, ix1, yTop, iz1).setColor(colorTop).setUv(0, 0).setLight(0).setNormal(-1, 0, 0);
            consumer.addVertex(matrix, ix1, yBottom, iz1).setColor(colorBottom).setUv(0, 1).setLight(0).setNormal(-1, 0, 0);
            consumer.addVertex(matrix, ix2, yBottom, iz2).setColor(colorBottom).setUv(1, 1).setLight(0).setNormal(-1, 0, 0);
            consumer.addVertex(matrix, ix2, yTop, iz2).setColor(colorTop).setUv(1, 0).setLight(0).setNormal(-1, 0, 0);
        }
    }

    private void renderEdgeGlow(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                 ArenaZone zone, int baseY, float time) {
        ArenaZone.ZoneBounds bounds = zone.bounds();

        // Purple edge glow for void effect
        float glowAlpha = 0.2f + 0.1f * (float)Math.sin(time * 1.5f);
        int glowColor = packColor(0.4f, 0.2f, 0.6f, glowAlpha);

        float y = baseY - 0.05f;
        float glowWidth = 0.5f;

        if (zone.shape() == ArenaZone.ZoneShape.RECTANGULAR) {
            float x1 = bounds.x1();
            float z1 = bounds.z1();
            float x2 = bounds.x2();
            float z2 = bounds.z2();

            // Glow strips on inner edge of void
            renderGlowStrip(consumer, matrix, x1 - glowWidth, z1 - glowWidth, x2 + glowWidth, z1, y, glowColor);
            renderGlowStrip(consumer, matrix, x1 - glowWidth, z2, x2 + glowWidth, z2 + glowWidth, y, glowColor);
            renderGlowStrip(consumer, matrix, x1 - glowWidth, z1, x1, z2, y, glowColor);
            renderGlowStrip(consumer, matrix, x2, z1, x2 + glowWidth, z2, y, glowColor);
        }
    }

    private void renderGlowStrip(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                  float x1, float z1, float x2, float z2, float y, int color) {
        consumer.addVertex(matrix, x1, y, z1).setColor(color).setUv(0, 0).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x1, y, z2).setColor(color).setUv(0, 1).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, y, z2).setColor(color).setUv(1, 1).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, y, z1).setColor(color).setUv(1, 0).setLight(15728880).setNormal(0, 1, 0);
    }

    private void renderVoidEdges(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                  ArenaZone zone, float r, float g, float b,
                                  float alpha, int baseY) {
        ArenaZone.ZoneBounds bounds = zone.bounds();
        float y = baseY;

        if (zone.shape() == ArenaZone.ZoneShape.RECTANGULAR) {
            float x1 = bounds.x1();
            float z1 = bounds.z1();
            float x2 = bounds.x2();
            float z2 = bounds.z2();

            // Inner boundary lines (dim)
            addLine(consumer, matrix, x1, y, z1, x2, y, z1, r, g, b, alpha);
            addLine(consumer, matrix, x2, y, z1, x2, y, z2, r, g, b, alpha);
            addLine(consumer, matrix, x2, y, z2, x1, y, z2, r, g, b, alpha);
            addLine(consumer, matrix, x1, y, z2, x1, y, z1, r, g, b, alpha);
        }
    }

    private void spawnFallingParticles(ArenaZone zone, int baseY, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        long currentTick = level.getGameTime();
        if (currentTick == lastParticleTick) return;
        lastParticleTick = currentTick;

        // Check distance
        ArenaZone.ZoneBounds bounds = zone.bounds();
        BlockPos center = bounds.getCenter();
        double distSq = cameraPos.distanceToSqr(center.getX(), cameraPos.y, center.getZ());
        int radius = bounds.radius().orElse(Math.max(bounds.getWidth(), bounds.getDepth()) / 2);
        if (distSq > (radius + 25) * (radius + 25)) return;

        // Spawn falling particles in the void area
        if (RANDOM.nextFloat() < PARTICLE_SPAWN_CHANCE) {
            Vec3 particlePos = getRandomVoidPosition(zone, baseY);
            if (particlePos != null) {
                // Ash particles for falling effect
                level.addParticle(
                    Objects.requireNonNull(ParticleTypes.ASH),
                    particlePos.x, particlePos.y, particlePos.z,
                    0,
                    -0.03, // Falling downward
                    0
                );

                // Occasionally spawn soul particles for mystical effect
                if (RANDOM.nextFloat() < 0.3f) {
                    level.addParticle(
                        Objects.requireNonNull(ParticleTypes.SOUL),
                        particlePos.x, particlePos.y - 1, particlePos.z,
                        (RANDOM.nextDouble() - 0.5) * 0.02,
                        -0.01,
                        (RANDOM.nextDouble() - 0.5) * 0.02
                    );
                }
            }
        }
    }

    @Nullable
    private Vec3 getRandomVoidPosition(ArenaZone zone, int baseY) {
        ArenaZone.ZoneBounds bounds = zone.bounds();
        float y = baseY - RANDOM.nextFloat() * VOID_DEPTH;

        if (zone.shape() == ArenaZone.ZoneShape.RECTANGULAR) {
            int edge = RANDOM.nextInt(4);
            float x1 = bounds.x1() - GAP_WIDTH;
            float z1 = bounds.z1() - GAP_WIDTH;
            // Note: outer x2/z2 not needed - using innerX2/Z2 + offset directly
            float innerX1 = bounds.x1();
            float innerZ1 = bounds.z1();
            float innerX2 = bounds.x2();
            float innerZ2 = bounds.z2();

            return switch (edge) {
                case 0 -> new Vec3(innerX1 + RANDOM.nextFloat() * (innerX2 - innerX1), y, z1 + RANDOM.nextFloat() * GAP_WIDTH);
                case 1 -> new Vec3(innerX2 + RANDOM.nextFloat() * GAP_WIDTH, y, innerZ1 + RANDOM.nextFloat() * (innerZ2 - innerZ1));
                case 2 -> new Vec3(innerX1 + RANDOM.nextFloat() * (innerX2 - innerX1), y, innerZ2 + RANDOM.nextFloat() * GAP_WIDTH);
                case 3 -> new Vec3(x1 + RANDOM.nextFloat() * GAP_WIDTH, y, innerZ1 + RANDOM.nextFloat() * (innerZ2 - innerZ1));
                default -> null;
            };
        } else if (zone.shape() == ArenaZone.ZoneShape.CIRCULAR) {
            BlockPos center = bounds.getCenter();
            int radius = bounds.radius().orElse(0);
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            float r = radius + RANDOM.nextFloat() * GAP_WIDTH;
            return new Vec3(
                center.getX() + 0.5 + Math.cos(angle) * r,
                y,
                center.getZ() + 0.5 + Math.sin(angle) * r
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

    private int packColor(float r, float g, float b, float a) {
        return ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    private int getVoidColor(ZoneEnvironment env) {
        // Void effects use dark colors
        if (env == null || ZoneEnvironment.DEFAULT.equals(env)) {
            return OverlayTheme.Debug.GAP_VOID; // Deep purple/void
        }

        var biome = env.biome();
        if (biome.isPresent()) {
            String path = biome.get().getPath().toLowerCase(java.util.Locale.ROOT);
            if (path.contains("end")) {
                return OverlayTheme.Debug.GAP_END; // End void purple
            }
            if (path.contains("nether") || path.contains("soul")) {
                return OverlayTheme.Debug.GAP_NETHER; // Nether dark red
            }
            if (path.contains("deep_dark")) {
                return OverlayTheme.Debug.GAP_DARK; // Deep dark blue-black
            }
        }

        return OverlayTheme.Debug.GAP_VOID;
    }
}
