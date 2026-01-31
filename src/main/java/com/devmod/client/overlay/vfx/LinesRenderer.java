package com.devmod.client.overlay.vfx;

import java.util.Objects;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

import com.devmod.DevMod;
import com.devmod.client.rendering.TrigCache;
import com.devmod.client.rendering.shader.VFXShaderRegistry;

import static com.devmod.client.overlay.vfx.ImpactVFXConstants.*;

/**
 * Renders the connection lines effect for impacts.
 *
 * <p>The connection lines consist of:
 * <ul>
 *   <li>Main lines extending from impact point towards camera</li>
 *   <li>Side lines ("scanner" effect) extending horizontally and vertically</li>
 *   <li>Pulsing glow effect along lines</li>
 * </ul>
 *
 * <p>Supports both CPU (line-based) and GPU (shader-based) rendering.
 */
public final class LinesRenderer {

    private LinesRenderer() {}

    /**
     * Renders the connection lines using CPU line rendering.
     *
     * @param poseStack Transformation stack
     * @param bufferSource Buffer for rendering
     * @param cameraPos Current camera position
     * @param hitPoint Impact point in world coordinates
     * @param alpha Current alpha (0-1)
     * @param rotation Current rotation angle (radians)
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                              Vec3 cameraPos, Vec3 hitPoint, float alpha, float rotation) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 cam = Objects.requireNonNull(cameraPos, "cameraPos");
        poseStack.pushPose();
        Vec3 rel = Objects.requireNonNull(hitPoint.subtract(cam), "relative position");
        poseStack.translate(rel.x, rel.y, rel.z);

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose(), "line matrix");
        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.debugLineStrip(2.0)));

        float[] lineColor = extractRGB(COLOR_LINE);
        float r = lineColor[0];
        float g = lineColor[1];
        float b = lineColor[2];

        // Direction towards camera
        Vec3 toCamera = Objects.requireNonNull(cam.subtract(hitPoint), "toCamera").normalize();

        // === MAIN LINES TOWARDS CAMERA ===
        renderMainLines(consumer, matrix, toCamera, r, g, b, alpha, rotation);

        // === SIDE LINES ("scanner" effect) ===
        renderSideLines(consumer, matrix, r, g, b, alpha);

        poseStack.popPose();
    }

    /**
     * Renders the connection lines using GPU shader.
     *
     * @param poseStack Transformation stack
     * @param bufferSource Buffer for rendering
     * @param cameraPos Current camera position
     * @param hitPoint Impact point in world coordinates
     * @param alpha Current alpha (0-1)
     * @param rotation Current rotation angle (radians)
     * @param intensity VFX intensity multiplier
     */
    public static void renderGPU(PoseStack poseStack, MultiBufferSource bufferSource,
                                 Vec3 cameraPos, Vec3 hitPoint, float alpha, float rotation, float intensity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ShaderInstance shader = VFXShaderRegistry.getImpactVfxShader();
        RenderType renderType = VFXShaderRegistry.getImpactVfxRenderType();
        if (shader == null || renderType == null) return;

        Vec3 cam = Objects.requireNonNull(cameraPos, "cameraPos GPU");

        // Set shader uniforms
        setShaderUniform(shader, "GameTime", (float)(System.currentTimeMillis() % 100000) / 1000.0f);
        setShaderUniform(shader, "EffectType", 2); // Lines
        setShaderUniform(shader, "Rotation", rotation);
        setShaderUniform(shader, "Alpha", alpha);
        setShaderUniform(shader, "Intensity", intensity);

        poseStack.pushPose();
        Vec3 rel = Objects.requireNonNull(hitPoint.subtract(cam), "relative position GPU");
        poseStack.translate(rel.x, rel.y, rel.z);

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose(), "line GPU matrix");
        var pose = Objects.requireNonNull(poseStack.last());
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        Vec3 toCamera = Objects.requireNonNull(cam.subtract(hitPoint), "toCamera GPU").normalize();

        float lineThickness = 0.02f;

        for (int i = 0; i < LINE_COUNT; i++) {
            float angleOffset = TWO_PI * i / LINE_COUNT;
            float startX = TrigCache.cos(angleOffset) * LINE_START_OFFSET;
            float startZ = TrigCache.sin(angleOffset) * LINE_START_OFFSET;

            // Build line as thin quad strip
            for (int j = 0; j < LINE_SEGMENTS; j++) {
                float t1 = (float) j / LINE_SEGMENTS;
                float t2 = (float) (j + 1) / LINE_SEGMENTS;

                float x1 = startX * (1 - t1) + (float) toCamera.x * LINE_LENGTH * t1;
                float y1 = (float) toCamera.y * LINE_LENGTH * t1;
                float z1 = startZ * (1 - t1) + (float) toCamera.z * LINE_LENGTH * t1;

                float x2 = startX * (1 - t2) + (float) toCamera.x * LINE_LENGTH * t2;
                float y2 = (float) toCamera.y * LINE_LENGTH * t2;
                float z2 = startZ * (1 - t2) + (float) toCamera.z * LINE_LENGTH * t2;

                // Normal.y carries line position (0-1), normal.z carries line index
                consumer.addVertex(matrix, x1 - lineThickness, y1, z1).setColor(1f, 1f, 1f, alpha).setNormal(pose, 0, t1, i);
                consumer.addVertex(matrix, x1 + lineThickness, y1, z1).setColor(1f, 1f, 1f, alpha).setNormal(pose, 0, t1, i);
                consumer.addVertex(matrix, x2 - lineThickness, y2, z2).setColor(1f, 1f, 1f, alpha).setNormal(pose, 0, t2, i);

                consumer.addVertex(matrix, x1 + lineThickness, y1, z1).setColor(1f, 1f, 1f, alpha).setNormal(pose, 0, t1, i);
                consumer.addVertex(matrix, x2 + lineThickness, y2, z2).setColor(1f, 1f, 1f, alpha).setNormal(pose, 0, t2, i);
                consumer.addVertex(matrix, x2 - lineThickness, y2, z2).setColor(1f, 1f, 1f, alpha).setNormal(pose, 0, t2, i);
            }
        }

        poseStack.popPose();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE RENDER HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private static void renderMainLines(VertexConsumer consumer, Matrix4f matrix, Vec3 toCamera,
                                        float r, float g, float b, float alpha, float rotation) {
        for (int i = 0; i < LINE_COUNT; i++) {
            // Angular offset for each line
            float angleOffset = TWO_PI * i / LINE_COUNT + rotation * 0.2f;

            // Starting point (slightly offset from center)
            float startX = TrigCache.cos(angleOffset) * LINE_START_OFFSET;
            float startZ = TrigCache.sin(angleOffset) * LINE_START_OFFSET;

            // Line extends towards camera with slight curve
            for (int j = 0; j <= LINE_SEGMENTS; j++) {
                float t = (float) j / LINE_SEGMENTS;

                // Interpolation towards camera
                float x = startX * (1 - t) + (float) toCamera.x * LINE_LENGTH * t;
                float y = (float) toCamera.y * LINE_LENGTH * t;
                float z = startZ * (1 - t) + (float) toCamera.z * LINE_LENGTH * t;

                // Alpha decreasing towards end
                float segmentAlpha = alpha * (1.0f - t * 0.7f);

                // "Pulse" effect along the line
                float pulse = TrigCache.sin(t * TWO_PI + rotation * 3) * 0.3f + 0.7f;
                segmentAlpha *= pulse;

                consumer.addVertex(matrix, x, y, z)
                    .setColor(r, g, b, segmentAlpha)
                    .setNormal(0, 1, 0);
            }
        }
    }

    private static void renderSideLines(VertexConsumer consumer, Matrix4f matrix,
                                        float r, float g, float b, float alpha) {
        float sideOffset = 0.15f;

        // Right line
        consumer.addVertex(matrix, sideOffset, 0, 0)
            .setColor(r, g, b, alpha * 0.6f)
            .setNormal(1, 0, 0);
        consumer.addVertex(matrix, sideOffset + LINE_SIDE_LENGTH, 0.3f, 0)
            .setColor(r, g, b, alpha * 0.1f)
            .setNormal(1, 0, 0);

        // Left line
        consumer.addVertex(matrix, -sideOffset, 0, 0)
            .setColor(r, g, b, alpha * 0.6f)
            .setNormal(1, 0, 0);
        consumer.addVertex(matrix, -sideOffset - LINE_SIDE_LENGTH, 0.3f, 0)
            .setColor(r, g, b, alpha * 0.1f)
            .setNormal(1, 0, 0);

        // Top line
        consumer.addVertex(matrix, 0, sideOffset, 0)
            .setColor(r, g, b, alpha * 0.6f)
            .setNormal(0, 1, 0);
        consumer.addVertex(matrix, 0, sideOffset + LINE_SIDE_LENGTH, 0)
            .setColor(r, g, b, alpha * 0.1f)
            .setNormal(0, 1, 0);
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
}
