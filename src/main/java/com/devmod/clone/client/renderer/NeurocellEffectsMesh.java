package com.devmod.clone.client.renderer;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * Pre-computed static mesh geometry for Neurocell energy effects.
 *
 * <p>The mesh contains:
 * <ul>
 *   <li>Two scanning rings (32 segments each)</li>
 *   <li>Two intertwined energy helixes (40 particles each)</li>
 * </ul>
 *
 * <p>Animation is achieved through PoseStack transformations,
 * not by regenerating geometry each frame.
 */
public final class NeurocellEffectsMesh {

    private static final int RING_SEGMENTS = 32;
    private static final int HELIX_PARTICLES = 40;

    private final List<EffectQuad> ringQuads = new ArrayList<>();
    private final List<EffectQuad> helixQuads = new ArrayList<>();

    private NeurocellEffectsMesh() {
        buildRingMesh();
        buildHelixMesh();
    }

    /**
     * Build a new effects mesh with pre-computed geometry.
     */
    @Nonnull
    public static NeurocellEffectsMesh build() {
        return new NeurocellEffectsMesh();
    }

    /**
     * Build the scanning ring mesh.
     * Ring is centered at origin, lying in XZ plane.
     */
    private void buildRingMesh() {
        float radius = 1.0f; // Will be scaled at render time
        float h = 0.02f;

        for (int i = 0; i < RING_SEGMENTS; i++) {
            float angle1 = (float) (i * 2 * Math.PI / RING_SEGMENTS);
            float angle2 = (float) ((i + 1) * 2 * Math.PI / RING_SEGMENTS);

            float x1 = Mth.cos(angle1) * radius;
            float z1 = Mth.sin(angle1) * radius;
            float x2 = Mth.cos(angle2) * radius;
            float z2 = Mth.sin(angle2) * radius;

            // Base brightness varies around ring for visual interest
            float baseBrightness = 0.5f + 0.5f * Mth.sin(angle1 * 4);

            // Ring quad (thin horizontal band)
            ringQuads.add(new EffectQuad(
                new float[]{x1, -h, z1},
                new float[]{x2, -h, z2},
                new float[]{x2, h, z2},
                new float[]{x1, h, z1},
                0, 220, 255,
                baseBrightness,
                new float[]{0, 1, 0}
            ));
        }
    }

    /**
     * Build the energy helix mesh.
     * Two intertwined helixes spiraling upward.
     */
    private void buildHelixMesh() {
        for (int helix = 0; helix < 2; helix++) {
            float phaseOffset = helix * 3.14159f;

            for (int i = 0; i < HELIX_PARTICLES; i++) {
                float t = i / (float) HELIX_PARTICLES;
                float y = 0.1f + t * 1.4f;
                float angle = t * 6.28318f * 2 + phaseOffset;
                float radius = 0.35f + 0.03f * Mth.sin(t * 6.28f);

                float x = Mth.cos(angle) * radius;
                float z = Mth.sin(angle) * radius;

                // Particle size varies along helix
                float size = 0.015f + 0.008f * Mth.sin(t * 12.56f);

                // Color based on helix and position
                int r = helix == 0 ? 0 : 20;
                int g = (int)(160 + 30 * t);
                int b = 200;
                float baseBrightness = 0.6f + 0.4f * (1 - t);

                // Front face
                helixQuads.add(new EffectQuad(
                    new float[]{x - size, y - size, z},
                    new float[]{x + size, y - size, z},
                    new float[]{x + size, y + size, z},
                    new float[]{x - size, y + size, z},
                    r, g, b,
                    baseBrightness,
                    new float[]{0, 0, 1}
                ));

                // Back face (reverse winding)
                helixQuads.add(new EffectQuad(
                    new float[]{x + size, y - size, z},
                    new float[]{x - size, y - size, z},
                    new float[]{x - size, y + size, z},
                    new float[]{x + size, y + size, z},
                    r, g, b,
                    baseBrightness,
                    new float[]{0, 0, -1}
                ));
            }
        }
    }

    /**
     * Render the ring mesh into the buffer.
     *
     * @param builder The vertex buffer builder
     * @param poseStack Current pose stack (should include ring transformations)
     * @param animTime Animation time for brightness pulsing
     * @param alphaMultiplier Overall alpha multiplier (0.0-1.0)
     */
    public void renderRing(@Nonnull BufferBuilder builder, @Nonnull PoseStack poseStack,
                           float animTime, float alphaMultiplier) {
        Matrix4f pose = poseStack.last().pose();

        for (EffectQuad quad : ringQuads) {
            // Animate brightness based on time
            float brightness = quad.baseBrightness + 0.3f * Mth.sin(animTime * 2);
            int alpha = (int)(brightness * 220 * alphaMultiplier);
            int g = (int)(220 + brightness * 35);

            builder.addVertex(pose, quad.v1[0], quad.v1[1], quad.v1[2])
                   .setColor(quad.r, g, quad.b, alpha)
                   .setNormal(quad.normal[0], quad.normal[1], quad.normal[2]);
            builder.addVertex(pose, quad.v2[0], quad.v2[1], quad.v2[2])
                   .setColor(quad.r, g, quad.b, alpha)
                   .setNormal(quad.normal[0], quad.normal[1], quad.normal[2]);
            builder.addVertex(pose, quad.v3[0], quad.v3[1], quad.v3[2])
                   .setColor(quad.r, g, quad.b, alpha)
                   .setNormal(quad.normal[0], quad.normal[1], quad.normal[2]);
            builder.addVertex(pose, quad.v4[0], quad.v4[1], quad.v4[2])
                   .setColor(quad.r, g, quad.b, alpha)
                   .setNormal(quad.normal[0], quad.normal[1], quad.normal[2]);
        }
    }

    /**
     * Render the helix mesh into the buffer.
     *
     * @param builder The vertex buffer builder
     * @param poseStack Current pose stack (should include helix transformations)
     * @param fadeMultiplier Fade multiplier for pulsing effect (0.0-1.0)
     */
    public void renderHelix(@Nonnull BufferBuilder builder, @Nonnull PoseStack poseStack,
                            float fadeMultiplier) {
        Matrix4f pose = poseStack.last().pose();

        for (EffectQuad quad : helixQuads) {
            int alpha = (int)(quad.baseBrightness * 40 * fadeMultiplier);

            builder.addVertex(pose, quad.v1[0], quad.v1[1], quad.v1[2])
                   .setColor(quad.r, quad.g, quad.b, alpha)
                   .setNormal(quad.normal[0], quad.normal[1], quad.normal[2]);
            builder.addVertex(pose, quad.v2[0], quad.v2[1], quad.v2[2])
                   .setColor(quad.r, quad.g, quad.b, alpha)
                   .setNormal(quad.normal[0], quad.normal[1], quad.normal[2]);
            builder.addVertex(pose, quad.v3[0], quad.v3[1], quad.v3[2])
                   .setColor(quad.r, quad.g, quad.b, alpha)
                   .setNormal(quad.normal[0], quad.normal[1], quad.normal[2]);
            builder.addVertex(pose, quad.v4[0], quad.v4[1], quad.v4[2])
                   .setColor(quad.r, quad.g, quad.b, alpha)
                   .setNormal(quad.normal[0], quad.normal[1], quad.normal[2]);
        }
    }

    /**
     * Get the total number of quads in this mesh.
     */
    public int getQuadCount() {
        return ringQuads.size() + helixQuads.size();
    }

    /**
     * Get the total vertex count.
     */
    public int getVertexCount() {
        return getQuadCount() * 4;
    }

    /**
     * A quad with four vertices, base color, brightness, and normal.
     */
    private static class EffectQuad {
        final float[] v1, v2, v3, v4;
        final int r, g, b;
        final float baseBrightness;
        final float[] normal;

        EffectQuad(float[] v1, float[] v2, float[] v3, float[] v4,
                   int r, int g, int b, float baseBrightness, float[] normal) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.v4 = v4;
            this.r = r;
            this.g = g;
            this.b = b;
            this.baseBrightness = baseBrightness;
            this.normal = normal;
        }
    }
}
