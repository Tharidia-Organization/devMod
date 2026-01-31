package com.devmod.client.overlay.vfx;

import java.util.Objects;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

import com.devmod.DevMod;
import com.devmod.client.rendering.TrigCache;
import com.devmod.client.rendering.shader.VFXShaderRegistry;

import static com.devmod.client.overlay.vfx.ImpactVFXConstants.COLOR_SLASH;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.PI;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.SLASH_HEIGHT;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.SLASH_LENGTH;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.SLASH_SPARK_COUNT;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.SLASH_TRAIL_SEGMENTS;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.applyIntensity;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.extractRGB;

/**
 * Renders the slash trail effect for weapon impacts.
 *
 * <p>The slash effect shows a "blade" moving through the impact point:
 * <ul>
 *   <li>Animated blade moving left to right</li>
 *   <li>Trailing arc behind the blade</li>
 *   <li>Spark particles</li>
 *   <li>Outer edge arc</li>
 * </ul>
 *
 * <p>Supports both CPU (line-based) and GPU (shader-based) rendering.
 */
public final class SlashRenderer {

    private SlashRenderer() {}

    /**
     * Renders the slash trail using CPU line rendering.
     *
     * @param poseStack Transformation stack (pre-translated to hit point)
     * @param bufferSource Buffer for rendering
     * @param direction Slash direction vector
     * @param progress Animation progress (0-1)
     * @param intensity VFX intensity multiplier
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                              Vec3 direction, float progress, float intensity) {
        Vec3 dir = Objects.requireNonNull(direction, "slash direction");
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose(), "slash matrix");
        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.debugLineStrip(5.0)));

        float[] slashColor = extractRGB(COLOR_SLASH);
        float r = slashColor[0];
        float g = slashColor[1];
        float b = slashColor[2];

        // Base alpha that decreases with progress
        float baseAlpha = Math.max(0, 1.0f - progress * 0.8f);
        baseAlpha = applyIntensity(baseAlpha, intensity);

        // Calculate perpendicular direction for horizontal cut
        float[] perp = calculatePerpendicular(dir);
        float perpX = perp[0];
        float perpZ = perp[1];

        // Current blade position (moves from -SLASH_LENGTH to +SLASH_LENGTH)
        float bladePos = (progress * 2.0f - 1.0f) * SLASH_LENGTH;
        float trailStart = -SLASH_LENGTH;
        float trailEnd = Math.min(bladePos, SLASH_LENGTH);

        // === CUT TRAIL ===
        renderTrail(consumer, matrix, perpX, perpZ, r, g, b, baseAlpha, bladePos, trailStart, trailEnd);

        // === BLADE (brightest moving point) ===
        if (progress < 0.95f && Math.abs(bladePos) <= SLASH_LENGTH) {
            renderBlade(consumer, matrix, perpX, perpZ, baseAlpha, bladePos);
        }

        // === SPARK PARTICLES ===
        if (progress < 0.7f) {
            renderSparks(consumer, matrix, perpX, perpZ, baseAlpha, progress, trailStart, trailEnd);
        }

        // === OUTER ARC ===
        renderOuterArc(consumer, matrix, perpX, perpZ, r, g, b, baseAlpha, trailEnd);
    }

    /**
     * Renders the slash trail using GPU shader.
     *
     * @param poseStack Transformation stack (pre-translated to hit point)
     * @param bufferSource Buffer for rendering
     * @param direction Slash direction vector
     * @param progress Animation progress (0-1)
     * @param intensity VFX intensity multiplier
     */
    public static void renderGPU(PoseStack poseStack, MultiBufferSource bufferSource,
                                 Vec3 direction, float progress, float intensity) {
        ShaderInstance shader = VFXShaderRegistry.getImpactVfxShader();
        RenderType renderType = VFXShaderRegistry.getImpactVfxRenderType();
        if (shader == null || renderType == null) return;

        Vec3 dir = Objects.requireNonNull(direction, "slash direction GPU");

        // Set shader uniforms
        setShaderUniform(shader, "GameTime", (float)(System.currentTimeMillis() % 100000) / 1000.0f);
        setShaderUniform(shader, "EffectType", 1); // Slash
        setShaderUniform(shader, "SlashProgress", progress);
        setShaderUniform(shader, "Alpha", 1.0f);
        setShaderUniform(shader, "Intensity", intensity);

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose(), "slash GPU matrix");
        var pose = Objects.requireNonNull(poseStack.last());
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        // Calculate perpendicular direction
        float[] perp = calculatePerpendicular(dir);
        float perpX = perp[0];
        float perpZ = perp[1];

        int segments = SLASH_TRAIL_SEGMENTS;
        float thickness = 0.05f;

        // Build slash trail as triangle strip geometry
        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / segments;
            float t2 = (float) (i + 1) / segments;

            float pos1 = -SLASH_LENGTH + 2 * SLASH_LENGTH * t1;
            float pos2 = -SLASH_LENGTH + 2 * SLASH_LENGTH * t2;

            float x1 = perpX * pos1;
            float z1 = perpZ * pos1;
            float y1 = TrigCache.sin(t1 * PI) * SLASH_HEIGHT;

            float x2 = perpX * pos2;
            float z2 = perpZ * pos2;
            float y2 = TrigCache.sin(t2 * PI) * SLASH_HEIGHT;

            // Normal.x carries position along slash (0-1) for shader
            consumer.addVertex(matrix, x1, y1 - thickness, z1).setColor(1f, 1f, 1f, 1f).setNormal(pose, t1, 0, 0);
            consumer.addVertex(matrix, x1, y1 + thickness, z1).setColor(1f, 1f, 1f, 1f).setNormal(pose, t1, 0, 0);
            consumer.addVertex(matrix, x2, y2 - thickness, z2).setColor(1f, 1f, 1f, 1f).setNormal(pose, t2, 0, 0);

            consumer.addVertex(matrix, x1, y1 + thickness, z1).setColor(1f, 1f, 1f, 1f).setNormal(pose, t1, 0, 0);
            consumer.addVertex(matrix, x2, y2 + thickness, z2).setColor(1f, 1f, 1f, 1f).setNormal(pose, t2, 0, 0);
            consumer.addVertex(matrix, x2, y2 - thickness, z2).setColor(1f, 1f, 1f, 1f).setNormal(pose, t2, 0, 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE RENDER HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Calculates the perpendicular direction for horizontal cut.
     * Returns [perpX, perpZ] normalized.
     */
    private static float[] calculatePerpendicular(Vec3 direction) {
        float dirX = (float) direction.x;
        float dirZ = (float) direction.z;

        float perpX = -dirZ;
        float perpZ = dirX;
        float perpLen = (float) Math.sqrt(perpX * perpX + perpZ * perpZ);

        if (perpLen > 0.001f) {
            perpX /= perpLen;
            perpZ /= perpLen;
        } else {
            perpX = 1;
            perpZ = 0;
        }

        return new float[] { perpX, perpZ };
    }

    private static void renderTrail(VertexConsumer consumer, Matrix4f matrix,
                                    float perpX, float perpZ,
                                    float r, float g, float b, float baseAlpha,
                                    float bladePos, float trailStart, float trailEnd) {
        if (trailEnd <= trailStart) return;

        for (int i = 0; i <= SLASH_TRAIL_SEGMENTS; i++) {
            float t = (float) i / SLASH_TRAIL_SEGMENTS;
            float pos = trailStart + (trailEnd - trailStart) * t;

            float x = perpX * pos;
            float z = perpZ * pos;

            // Vertical arc
            float arcT = (pos + SLASH_LENGTH) / (2 * SLASH_LENGTH);
            float y = TrigCache.sin(arcT * PI) * SLASH_HEIGHT;

            // Alpha: stronger near blade, fades towards tail
            float distFromBlade = Math.abs(pos - bladePos);
            float trailAlpha = baseAlpha * Math.max(0, 1.0f - distFromBlade / SLASH_LENGTH);

            // Color fading from white (blade) to slash color (trail)
            float colorFade = Math.min(1, distFromBlade / (SLASH_LENGTH * 0.3f));
            float cr = r + (1 - r) * (1 - colorFade);
            float cg = g + (1 - g) * (1 - colorFade);
            float cb = b + (1 - b) * (1 - colorFade);

            consumer.addVertex(matrix, x, y, z)
                .setColor(cr, cg, cb, trailAlpha)
                .setNormal(0, 1, 0);
        }
    }

    private static void renderBlade(VertexConsumer consumer, Matrix4f matrix,
                                    float perpX, float perpZ, float baseAlpha, float bladePos) {
        float bladeX = perpX * bladePos;
        float bladeZ = perpZ * bladePos;
        float arcT = (bladePos + SLASH_LENGTH) / (2 * SLASH_LENGTH);
        float bladeY = TrigCache.sin(arcT * PI) * SLASH_HEIGHT;

        float bladeAlpha = baseAlpha * 1.2f;
        float bladeSize = 0.08f;

        // Luminous cross on blade
        consumer.addVertex(matrix, bladeX - bladeSize, bladeY, bladeZ)
            .setColor(1f, 1f, 1f, bladeAlpha).setNormal(1, 0, 0);
        consumer.addVertex(matrix, bladeX + bladeSize, bladeY, bladeZ)
            .setColor(1f, 1f, 1f, bladeAlpha).setNormal(1, 0, 0);
        consumer.addVertex(matrix, bladeX, bladeY - bladeSize, bladeZ)
            .setColor(1f, 1f, 1f, bladeAlpha).setNormal(0, 1, 0);
        consumer.addVertex(matrix, bladeX, bladeY + bladeSize, bladeZ)
            .setColor(1f, 1f, 1f, bladeAlpha).setNormal(0, 1, 0);

        // Vertical blade line
        float bladeLengthVert = 0.25f;
        consumer.addVertex(matrix, bladeX, bladeY - bladeLengthVert, bladeZ)
            .setColor(1f, 1f, 1f, bladeAlpha * 0.8f).setNormal(0, 1, 0);
        consumer.addVertex(matrix, bladeX, bladeY + bladeLengthVert, bladeZ)
            .setColor(1f, 1f, 1f, bladeAlpha * 0.8f).setNormal(0, 1, 0);
    }

    private static void renderSparks(VertexConsumer consumer, Matrix4f matrix,
                                     float perpX, float perpZ, float baseAlpha, float progress,
                                     float trailStart, float trailEnd) {
        for (int i = 0; i < SLASH_SPARK_COUNT; i++) {
            float sparkT = (float) i / SLASH_SPARK_COUNT;
            float sparkPos = trailStart + (trailEnd - trailStart) * sparkT;

            // Deterministic "random" offset using TrigCache
            float offsetX = TrigCache.sin(i * 7.3f + progress * 10) * 0.1f;
            float offsetY = TrigCache.cos(i * 5.1f + progress * 8) * 0.15f;
            float offsetZ = TrigCache.sin(i * 3.7f + progress * 12) * 0.1f;

            float arcT = (sparkPos + SLASH_LENGTH) / (2 * SLASH_LENGTH);
            float baseY = TrigCache.sin(arcT * PI) * SLASH_HEIGHT;

            float x = perpX * sparkPos + offsetX;
            float y = baseY + offsetY;
            float z = perpZ * sparkPos + offsetZ;

            float sparkAlpha = baseAlpha * (1.0f - progress) * 0.9f;
            float size = 0.015f;

            consumer.addVertex(matrix, x - size, y, z).setColor(1f, 1f, 0.8f, sparkAlpha).setNormal(1, 0, 0);
            consumer.addVertex(matrix, x + size, y, z).setColor(1f, 1f, 0.8f, sparkAlpha).setNormal(1, 0, 0);
        }
    }

    private static void renderOuterArc(VertexConsumer consumer, Matrix4f matrix,
                                       float perpX, float perpZ,
                                       float r, float g, float b, float baseAlpha, float trailEnd) {
        int arcSegments = 16;
        float arcOffset = 0.05f;

        for (int i = 0; i <= arcSegments; i++) {
            float t = (float) i / arcSegments;
            float pos = -SLASH_LENGTH + 2 * SLASH_LENGTH * t;

            if (pos > trailEnd) break;

            float x = perpX * pos;
            float z = perpZ * pos;
            float arcT = (pos + SLASH_LENGTH) / (2 * SLASH_LENGTH);
            float y = TrigCache.sin(arcT * PI) * (SLASH_HEIGHT + arcOffset);

            float arcAlpha = baseAlpha * 0.4f;

            consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, arcAlpha)
                .setNormal(0, 1, 0);
        }
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
