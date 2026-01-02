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

/**
 * Wall transition effect - vertical barrier with electric particles.
 * Creates a visible wall-like boundary between zones.
 */
public class WallTransitionEffect implements TransitionEffect {

    private static final float WALL_HEIGHT = 4.0f;
    private static final float WALL_THICKNESS = 0.2f;
    private static final float PULSE_SPEED = 3.0f;
    private static final float PARTICLE_SPAWN_CHANCE = 0.08f;
    private static final int SEGMENTS_PER_CIRCLE = 32;
    private static final Random RANDOM = new Random();

    private long lastParticleTick = 0;

    @Override
    public String getName() {
        return "wall";
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

        // Pulsing glow effect
        float pulse = 0.5f + 0.3f * (float)Math.sin(time * PULSE_SPEED);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose(), "pose matrix");

        // Render wall quads
        VertexConsumer wallConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.translucent()));
        renderWall(wallConsumer, matrix, zone, r, g, b, pulse * 0.6f, baseY, time);

        // Render edge lines for definition
        VertexConsumer lineConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));
        renderWallEdges(lineConsumer, matrix, zone, r, g, b, pulse, baseY);

        poseStack.popPose();

        // Spawn electric particles
        spawnElectricParticles(zone, baseY, cameraPos);
    }

    private void renderWall(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                            ArenaZone zone, float r, float g, float b,
                            float alpha, int baseY, float time) {
        ArenaZone.ZoneBounds bounds = zone.bounds();
        float y1 = baseY;
        float y2 = baseY + WALL_HEIGHT;

        // Add wave effect to wall
        float waveOffset = (float)Math.sin(time * 2.0f) * 0.1f;

        if (zone.shape() == ArenaZone.ZoneShape.RECTANGULAR) {
            float x1 = bounds.x1();
            float z1 = bounds.z1();
            float x2 = bounds.x2();
            float z2 = bounds.z2();

            // North wall
            renderWallQuad(consumer, matrix, x1, y1, z1, x2, y2, z1 + WALL_THICKNESS, r, g, b, alpha, waveOffset);
            // South wall
            renderWallQuad(consumer, matrix, x1, y1, z2 - WALL_THICKNESS, x2, y2, z2, r, g, b, alpha, waveOffset);
            // West wall
            renderWallQuad(consumer, matrix, x1, y1, z1, x1 + WALL_THICKNESS, y2, z2, r, g, b, alpha, waveOffset);
            // East wall
            renderWallQuad(consumer, matrix, x2 - WALL_THICKNESS, y1, z1, x2, y2, z2, r, g, b, alpha, waveOffset);
        } else if (zone.shape() == ArenaZone.ZoneShape.CIRCULAR) {
            renderCircularWall(consumer, matrix, bounds, r, g, b, alpha, y1, y2);
        }
    }

    private void renderWallQuad(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float r, float g, float b, float alpha, float wave) {
        // Wave distortion applied to top edge via y2 + wave

        // Color with gradient - brighter at top
        int colorBottom = packColor(r * 0.6f, g * 0.6f, b * 0.6f, alpha * 0.3f);
        int colorTop = packColor(r, g, b, alpha);

        // Determine if wall is along X or Z axis
        boolean alongX = Math.abs(x2 - x1) > Math.abs(z2 - z1);

        if (alongX) {
            // Wall runs along X axis (north/south wall)
            float z = (z1 + z2) / 2;
            consumer.addVertex(matrix, x1, y1, z).setColor(colorBottom).setUv(0, 0).setLight(15728880).setNormal(0, 0, 1);
            consumer.addVertex(matrix, x1, y2 + wave, z).setColor(colorTop).setUv(0, 1).setLight(15728880).setNormal(0, 0, 1);
            consumer.addVertex(matrix, x2, y2 + wave, z).setColor(colorTop).setUv(1, 1).setLight(15728880).setNormal(0, 0, 1);
            consumer.addVertex(matrix, x2, y1, z).setColor(colorBottom).setUv(1, 0).setLight(15728880).setNormal(0, 0, 1);
        } else {
            // Wall runs along Z axis (east/west wall)
            float x = (x1 + x2) / 2;
            consumer.addVertex(matrix, x, y1, z1).setColor(colorBottom).setUv(0, 0).setLight(15728880).setNormal(1, 0, 0);
            consumer.addVertex(matrix, x, y2 + wave, z1).setColor(colorTop).setUv(0, 1).setLight(15728880).setNormal(1, 0, 0);
            consumer.addVertex(matrix, x, y2 + wave, z2).setColor(colorTop).setUv(1, 1).setLight(15728880).setNormal(1, 0, 0);
            consumer.addVertex(matrix, x, y1, z2).setColor(colorBottom).setUv(1, 0).setLight(15728880).setNormal(1, 0, 0);
        }
    }

    private void renderCircularWall(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                     ArenaZone.ZoneBounds bounds,
                                     float r, float g, float b, float alpha,
                                     float y1, float y2) {
        BlockPos center = bounds.getCenter();
        float cx = center.getX() + 0.5f;
        float cz = center.getZ() + 0.5f;
        int radius = bounds.radius().orElse(0);
        if (radius <= 0) return;

        int colorBottom = packColor(r * 0.6f, g * 0.6f, b * 0.6f, alpha * 0.3f);
        int colorTop = packColor(r, g, b, alpha);

        for (int i = 0; i < SEGMENTS_PER_CIRCLE; i++) {
            double angle1 = (2 * Math.PI * i) / SEGMENTS_PER_CIRCLE;
            double angle2 = (2 * Math.PI * (i + 1)) / SEGMENTS_PER_CIRCLE;

            float x1 = cx + (float)(Math.cos(angle1) * radius);
            float z1 = cz + (float)(Math.sin(angle1) * radius);
            float x2 = cx + (float)(Math.cos(angle2) * radius);
            float z2 = cz + (float)(Math.sin(angle2) * radius);

            // Calculate normal (pointing outward)
            float nx = (float)Math.cos((angle1 + angle2) / 2);
            float nz = (float)Math.sin((angle1 + angle2) / 2);

            consumer.addVertex(matrix, x1, y1, z1).setColor(colorBottom).setUv(0, 0).setLight(15728880).setNormal(nx, 0, nz);
            consumer.addVertex(matrix, x1, y2, z1).setColor(colorTop).setUv(0, 1).setLight(15728880).setNormal(nx, 0, nz);
            consumer.addVertex(matrix, x2, y2, z2).setColor(colorTop).setUv(1, 1).setLight(15728880).setNormal(nx, 0, nz);
            consumer.addVertex(matrix, x2, y1, z2).setColor(colorBottom).setUv(1, 0).setLight(15728880).setNormal(nx, 0, nz);
        }
    }

    private void renderWallEdges(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                  ArenaZone zone, float r, float g, float b,
                                  float alpha, int baseY) {
        ArenaZone.ZoneBounds bounds = zone.bounds();
        float y1 = baseY;
        float y2 = baseY + WALL_HEIGHT;

        if (zone.shape() == ArenaZone.ZoneShape.RECTANGULAR) {
            float x1 = bounds.x1();
            float z1 = bounds.z1();
            float x2 = bounds.x2();
            float z2 = bounds.z2();

            // Top edges
            addLine(consumer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, alpha);
            addLine(consumer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, alpha);
            addLine(consumer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, alpha);
            addLine(consumer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, alpha);

            // Vertical corners
            addLine(consumer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, alpha);
            addLine(consumer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, alpha);
            addLine(consumer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, alpha);
            addLine(consumer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, alpha);
        }
    }

    private void spawnElectricParticles(ArenaZone zone, int baseY, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        long currentTick = level.getGameTime();
        if (currentTick == lastParticleTick) return;
        lastParticleTick = currentTick;

        // Check distance to zone
        ArenaZone.ZoneBounds bounds = zone.bounds();
        BlockPos center = bounds.getCenter();
        double distSq = cameraPos.distanceToSqr(center.getX(), cameraPos.y, center.getZ());
        int radius = bounds.radius().orElse(Math.max(bounds.getWidth(), bounds.getDepth()) / 2);
        if (distSq > (radius + 30) * (radius + 30)) return;

        // Spawn electric particles (soul particles for spooky effect)
        if (RANDOM.nextFloat() < PARTICLE_SPAWN_CHANCE) {
            Vec3 particlePos = getRandomWallPosition(zone, baseY);
            if (particlePos != null) {
                // Electric spark effect
                level.addParticle(
                    Objects.requireNonNull(ParticleTypes.ELECTRIC_SPARK),
                    particlePos.x, particlePos.y, particlePos.z,
                    (RANDOM.nextDouble() - 0.5) * 0.1,
                    RANDOM.nextDouble() * 0.05,
                    (RANDOM.nextDouble() - 0.5) * 0.1
                );
            }
        }
    }

    @Nullable
    private Vec3 getRandomWallPosition(ArenaZone zone, int baseY) {
        ArenaZone.ZoneBounds bounds = zone.bounds();
        float y = baseY + RANDOM.nextFloat() * WALL_HEIGHT;

        if (zone.shape() == ArenaZone.ZoneShape.RECTANGULAR) {
            int edge = RANDOM.nextInt(4);
            float x1 = bounds.x1();
            float z1 = bounds.z1();
            float x2 = bounds.x2();
            float z2 = bounds.z2();

            return switch (edge) {
                case 0 -> new Vec3(x1 + RANDOM.nextFloat() * (x2 - x1), y, z1);
                case 1 -> new Vec3(x2, y, z1 + RANDOM.nextFloat() * (z2 - z1));
                case 2 -> new Vec3(x1 + RANDOM.nextFloat() * (x2 - x1), y, z2);
                case 3 -> new Vec3(x1, y, z1 + RANDOM.nextFloat() * (z2 - z1));
                default -> null;
            };
        } else if (zone.shape() == ArenaZone.ZoneShape.CIRCULAR) {
            BlockPos center = bounds.getCenter();
            int radius = bounds.radius().orElse(0);
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            return new Vec3(
                center.getX() + 0.5 + Math.cos(angle) * radius,
                y,
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

    private int packColor(float r, float g, float b, float a) {
        return ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    private int getZoneColor(ZoneEnvironment env) {
        if (env == null || ZoneEnvironment.DEFAULT.equals(env)) {
            return 0x44AAFF; // Blue for walls by default
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
            if (path.contains("desert") || path.contains("mesa") || path.contains("badlands")) {
                return 0xFFAA44;
            }
        }

        return 0x44AAFF;
    }
}
