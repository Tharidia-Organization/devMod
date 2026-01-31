package com.devmod.client.overlay.vfx;

import java.util.Objects;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

import com.devmod.DevMod;
import com.devmod.client.rendering.TrigCache;
import com.devmod.client.rendering.shader.VFXShaderRegistry;

import static com.devmod.client.overlay.vfx.ImpactVFXConstants.*;

/**
 * Renders the energy vortex effect for impacts.
 *
 * <p>The vortex consists of:
 * <ul>
 *   <li>Outer spiral (clockwise rotation)</li>
 *   <li>Inner spiral (counter-clockwise)</li>
 *   <li>Concentric wave rings</li>
 *   <li>Rays from center</li>
 *   <li>Bright center point</li>
 * </ul>
 *
 * <p>Supports both CPU (line-based) and GPU (shader-based) rendering.
 */
public final class VortexRenderer {

    private VortexRenderer() {}

    /**
     * Renders the energy vortex using CPU line rendering.
     *
     * @param poseStack Transformation stack (pre-translated to hit point)
     * @param bufferSource Buffer for rendering
     * @param alpha Current alpha (0-1)
     * @param rotation Current rotation angle (radians)
     * @param pulseScale Pulse scale factor (1.0 = normal)
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                              float alpha, float rotation, float pulseScale) {
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose(), "vortex matrix");
        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.debugLineStrip(2.5)));

        // Extract color components
        float[] primary = extractRGB(COLOR_CORE_PRIMARY);
        float[] secondary = extractRGB(COLOR_CORE_SECONDARY);
        float[] glow = extractRGB(COLOR_CORE_GLOW);

        float baseRadius = VORTEX_BASE_RADIUS * pulseScale;

        // === OUTER SPIRAL (clockwise rotation) ===
        renderOuterSpiral(consumer, matrix, primary, secondary, alpha, rotation, baseRadius);

        // === INNER SPIRAL (counter-clockwise rotation) ===
        renderInnerSpiral(consumer, matrix, secondary, alpha, rotation, baseRadius);

        // === CONCENTRIC RINGS ("wave" effect) ===
        renderRings(consumer, matrix, glow, alpha, rotation, baseRadius);

        // === RAYS FROM CENTER ===
        renderRays(consumer, matrix, secondary, alpha, rotation, baseRadius);

        // === BRIGHT CENTER POINT ===
        renderCenterPoint(consumer, matrix, alpha, pulseScale);
    }

    /**
     * Renders the energy vortex using GPU shader.
     *
     * @param poseStack Transformation stack (pre-translated to hit point)
     * @param bufferSource Buffer for rendering
     * @param alpha Current alpha (0-1)
     * @param rotation Current rotation angle (radians)
     * @param pulseScale Pulse scale factor (1.0 = normal)
     * @param intensity VFX intensity multiplier
     */
    public static void renderGPU(PoseStack poseStack, MultiBufferSource bufferSource,
                                 float alpha, float rotation, float pulseScale, float intensity) {
        ShaderInstance shader = VFXShaderRegistry.getImpactVfxShader();
        RenderType renderType = VFXShaderRegistry.getImpactVfxRenderType();
        if (shader == null || renderType == null) return;

        // Set shader uniforms
        setShaderUniform(shader, "GameTime", (float)(System.currentTimeMillis() % 100000) / 1000.0f);
        setShaderUniform(shader, "EffectType", 0); // Vortex
        setShaderUniform(shader, "Rotation", rotation);
        setShaderUniform(shader, "PulseScale", pulseScale);
        setShaderUniform(shader, "Alpha", alpha);
        setShaderUniform(shader, "Intensity", intensity);
        setShaderUniformVec3(shader, "ColorPrimary", 0.24f, 0.35f, 1.0f);
        setShaderUniformVec3(shader, "ColorSecondary", 0.0f, 0.9f, 1.0f);
        setShaderUniformVec3(shader, "ColorGlow", 0.51f, 0.69f, 1.0f);

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose(), "vortex GPU matrix");
        var pose = Objects.requireNonNull(poseStack.last());
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        float baseRadius = VORTEX_BASE_RADIUS;

        // Outer spiral triangles - pass spiralT in normal.y for shader
        for (int i = 0; i < VORTEX_SPIRAL_SEGMENTS; i++) {
            float t1 = (float) i / VORTEX_SPIRAL_SEGMENTS;
            float t2 = (float) (i + 1) / VORTEX_SPIRAL_SEGMENTS;

            float angle1 = t1 * VORTEX_SPIRAL_ROTATIONS * TWO_PI;
            float angle2 = t2 * VORTEX_SPIRAL_ROTATIONS * TWO_PI;

            float radius1 = baseRadius * (1.0f - t1 * 0.6f);
            float radius2 = baseRadius * (1.0f - t2 * 0.6f);

            float x1 = TrigCache.cos(angle1) * radius1;
            float y1 = TrigCache.sin(angle1) * radius1 * 0.5f;
            float z1 = TrigCache.sin(angle1) * radius1;

            float x2 = TrigCache.cos(angle2) * radius2;
            float y2 = TrigCache.sin(angle2) * radius2 * 0.5f;
            float z2 = TrigCache.sin(angle2) * radius2;

            consumer.addVertex(matrix, 0, 0, 0).setColor(1f, 1f, 1f, alpha).setNormal(pose, 0, t1, 0);
            consumer.addVertex(matrix, x1, y1, z1).setColor(1f, 1f, 1f, alpha).setNormal(pose, 0, t1, 0);
            consumer.addVertex(matrix, x2, y2, z2).setColor(1f, 1f, 1f, alpha).setNormal(pose, 0, t2, 0);
        }

        // Concentric rings as thin triangles
        for (int ring = 0; ring < VORTEX_RING_COUNT; ring++) {
            float ringRadius = baseRadius * (0.4f + ring * 0.3f);
            float ringY = 0.0f;

            for (int i = 0; i < VORTEX_RING_SEGMENTS; i++) {
                float angle1 = TWO_PI * i / VORTEX_RING_SEGMENTS;
                float angle2 = TWO_PI * (i + 1) / VORTEX_RING_SEGMENTS;

                float x1 = TrigCache.cos(angle1) * ringRadius;
                float z1 = TrigCache.sin(angle1) * ringRadius;
                float x2 = TrigCache.cos(angle2) * ringRadius;
                float z2 = TrigCache.sin(angle2) * ringRadius;

                float innerRadius = ringRadius * 0.95f;
                float ix1 = TrigCache.cos(angle1) * innerRadius;
                float iz1 = TrigCache.sin(angle1) * innerRadius;
                float ix2 = TrigCache.cos(angle2) * innerRadius;
                float iz2 = TrigCache.sin(angle2) * innerRadius;

                consumer.addVertex(matrix, x1, ringY, z1).setColor(1f, 1f, 1f, alpha * 0.5f).setNormal(pose, 0, 0.5f, 0);
                consumer.addVertex(matrix, ix1, ringY, iz1).setColor(1f, 1f, 1f, alpha * 0.5f).setNormal(pose, 0, 0.5f, 0);
                consumer.addVertex(matrix, x2, ringY, z2).setColor(1f, 1f, 1f, alpha * 0.5f).setNormal(pose, 0, 0.5f, 0);

                consumer.addVertex(matrix, ix1, ringY, iz1).setColor(1f, 1f, 1f, alpha * 0.5f).setNormal(pose, 0, 0.5f, 0);
                consumer.addVertex(matrix, ix2, ringY, iz2).setColor(1f, 1f, 1f, alpha * 0.5f).setNormal(pose, 0, 0.5f, 0);
                consumer.addVertex(matrix, x2, ringY, z2).setColor(1f, 1f, 1f, alpha * 0.5f).setNormal(pose, 0, 0.5f, 0);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE RENDER HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private static void renderOuterSpiral(VertexConsumer consumer, Matrix4f matrix,
                                          float[] primary, float[] secondary,
                                          float alpha, float rotation, float baseRadius) {
        for (int i = 0; i <= VORTEX_SPIRAL_SEGMENTS; i++) {
            float t = (float) i / VORTEX_SPIRAL_SEGMENTS;
            float angle = rotation + t * VORTEX_SPIRAL_ROTATIONS * TWO_PI;
            float radius = baseRadius * (1.0f - t * 0.6f);

            float cosAngle = TrigCache.cos(angle);
            float sinAngle = TrigCache.sin(angle);

            float x = cosAngle * radius;
            float y = sinAngle * radius * 0.5f;
            float z = sinAngle * radius;

            // Color fading from primary to secondary
            float r = primary[0] * (1 - t) + secondary[0] * t;
            float g = primary[1] * (1 - t) + secondary[1] * t;
            float b = primary[2] * (1 - t) + secondary[2] * t;

            consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, alpha * (1.0f - t * 0.5f))
                .setNormal(0, 1, 0);
        }
    }

    private static void renderInnerSpiral(VertexConsumer consumer, Matrix4f matrix,
                                          float[] secondary, float alpha, float rotation, float baseRadius) {
        for (int i = 0; i <= VORTEX_SPIRAL_SEGMENTS; i++) {
            float t = (float) i / VORTEX_SPIRAL_SEGMENTS;
            float angle = -rotation * 1.5f + t * VORTEX_SPIRAL_ROTATIONS * TWO_PI;
            float radius = baseRadius * 0.6f * (1.0f - t * 0.5f);

            float cosAngle = TrigCache.cos(angle);
            float sinAngle = TrigCache.sin(angle);

            float x = cosAngle * radius;
            float y = sinAngle * radius * 0.3f;
            float z = sinAngle * radius;

            consumer.addVertex(matrix, x, y, z)
                .setColor(secondary[0], secondary[1], secondary[2], alpha * 0.7f * (1.0f - t * 0.3f))
                .setNormal(0, 1, 0);
        }
    }

    private static void renderRings(VertexConsumer consumer, Matrix4f matrix,
                                    float[] glow, float alpha, float rotation, float baseRadius) {
        for (int ring = 0; ring < VORTEX_RING_COUNT; ring++) {
            float ringRadius = baseRadius * (0.4f + ring * 0.3f);
            float ringAlpha = alpha * (1.0f - ring * 0.25f);
            float ringRotation = rotation * (1.0f + ring * 0.3f);

            for (int i = 0; i <= VORTEX_RING_SEGMENTS; i++) {
                float angle = ringRotation + TWO_PI * i / VORTEX_RING_SEGMENTS;
                float x = TrigCache.cos(angle) * ringRadius;
                float z = TrigCache.sin(angle) * ringRadius;

                // Vertical wave
                float waveY = TrigCache.sin(angle * 3 + rotation * 2) * 0.03f;

                consumer.addVertex(matrix, x, waveY, z)
                    .setColor(glow[0], glow[1], glow[2], ringAlpha * 0.5f)
                    .setNormal(0, 1, 0);
            }
        }
    }

    private static void renderRays(VertexConsumer consumer, Matrix4f matrix,
                                   float[] secondary, float alpha, float rotation, float baseRadius) {
        float rayLength = baseRadius * 1.2f;

        for (int i = 0; i < VORTEX_RAY_COUNT; i++) {
            float angle = rotation * 0.5f + TWO_PI * i / VORTEX_RAY_COUNT;

            // Central point
            consumer.addVertex(matrix, 0, 0, 0)
                .setColor(secondary[0], secondary[1], secondary[2], alpha)
                .setNormal(0, 1, 0);

            // Outer point
            float x = TrigCache.cos(angle) * rayLength;
            float z = TrigCache.sin(angle) * rayLength;
            consumer.addVertex(matrix, x, 0, z)
                .setColor(secondary[0], secondary[1], secondary[2], alpha * 0.2f)
                .setNormal(0, 1, 0);
        }
    }

    private static void renderCenterPoint(VertexConsumer consumer, Matrix4f matrix,
                                          float alpha, float pulseScale) {
        float dotSize = 0.02f * pulseScale;
        consumer.addVertex(matrix, -dotSize, 0, 0).setColor(1f, 1f, 1f, alpha).setNormal(1, 0, 0);
        consumer.addVertex(matrix, dotSize, 0, 0).setColor(1f, 1f, 1f, alpha).setNormal(1, 0, 0);
        consumer.addVertex(matrix, 0, -dotSize, 0).setColor(1f, 1f, 1f, alpha).setNormal(0, 1, 0);
        consumer.addVertex(matrix, 0, dotSize, 0).setColor(1f, 1f, 1f, alpha).setNormal(0, 1, 0);
        consumer.addVertex(matrix, 0, 0, -dotSize).setColor(1f, 1f, 1f, alpha).setNormal(0, 0, 1);
        consumer.addVertex(matrix, 0, 0, dotSize).setColor(1f, 1f, 1f, alpha).setNormal(0, 0, 1);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SHADER UNIFORM HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private static void setShaderUniform(ShaderInstance shader, String name, float value) {
        try {
            var uniform = shader.getUniform(Objects.requireNonNull(name));
            if (uniform != null) {
                uniform.set(value);
            }
        } catch (Exception ex) {
            DevMod.LOGGER.debug("Unable to set shader uniform '{}'", name, ex);
        }
    }

    private static void setShaderUniform(ShaderInstance shader, String name, int value) {
        try {
            var uniform = shader.getUniform(Objects.requireNonNull(name));
            if (uniform != null) {
                uniform.set(value);
            }
        } catch (Exception ex) {
            DevMod.LOGGER.debug("Unable to set shader uniform '{}'", name, ex);
        }
    }

    private static void setShaderUniformVec3(ShaderInstance shader, String name, float x, float y, float z) {
        try {
            var uniform = shader.getUniform(Objects.requireNonNull(name));
            if (uniform != null) {
                uniform.set(x, y, z);
            }
        } catch (Exception ex) {
            DevMod.LOGGER.debug("Unable to set shader uniform '{}'", name, ex);
        }
    }
}
