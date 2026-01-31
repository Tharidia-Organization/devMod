package com.devmod.client.overlay.vfx;

import java.util.Objects;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.overlay.ImpactHudContentBuilder;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.combat.HitHelper.BodyPart;

import static com.devmod.client.overlay.vfx.ImpactVFXConstants.GLYPH_BASE_SIZE;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.GLYPH_CRIT_SIZE;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.GLYPH_CROSS_SIZE;

/**
 * Renders the combat glyph effect for impacts.
 *
 * <p>The glyph displays:
 * <ul>
 *   <li>Cross pattern centered at impact point</li>
 *   <li>Body part indicator (head crown, body rectangle, etc.)</li>
 *   <li>Critical hit X marker (when applicable)</li>
 *   <li>Damage reduction/amplification indicator</li>
 * </ul>
 *
 * <p>Glyphs are billboarded to always face the camera.
 */
public final class GlyphRenderer {

    private GlyphRenderer() {}

    /**
     * Renders the combat glyph.
     *
     * @param poseStack Transformation stack
     * @param bufferSource Buffer for rendering
     * @param cameraPos Current camera position
     * @param hitPoint Impact point in world coordinates
     * @param alpha Current alpha (0-1)
     * @param damageScale Scale factor based on damage (0-1)
     * @param bodyPart Hit body part
     * @param bodyPartColor Color for the body part (ARGB)
     * @param isCritical Whether this was a critical hit
     * @param reduction Armor reduction factor (-1 to 1, negative = amplified)
     * @param reductionScale Visual scale for reduction indicator
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                              Vec3 cameraPos, Vec3 hitPoint, float alpha,
                              float damageScale, BodyPart bodyPart, int bodyPartColor,
                              boolean isCritical, float reduction, float reductionScale) {
        if (alpha <= 0.01f) {
            return;
        }

        poseStack.pushPose();

        // Position at hit point relative to camera
        Vec3 rel = Objects.requireNonNull(hitPoint.subtract(cameraPos), "glyph relative position");
        poseStack.translate(rel.x, rel.y, rel.z);

        // Billboard towards camera
        Vec3 toCamera = cameraPos.subtract(hitPoint).normalize();
        float yaw = (float) Math.atan2(toCamera.x, toCamera.z);
        float pitch = (float) Math.atan2(toCamera.y, Math.sqrt(toCamera.x * toCamera.x + toCamera.z * toCamera.z));
        poseStack.mulPose(new Quaternionf().rotationY(yaw).rotateX(-pitch));

        // Scale based on damage
        float baseSize = GLYPH_BASE_SIZE + damageScale;
        poseStack.scale(baseSize, baseSize, baseSize);

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose(), "glyph matrix");
        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));

        // Base cross pattern
        float[] baseColor = colorToFloat(bodyPartColor, alpha);
        drawLine(consumer, matrix, -GLYPH_CROSS_SIZE, 0, 0, GLYPH_CROSS_SIZE, 0, 0, baseColor);
        drawLine(consumer, matrix, 0, -GLYPH_CROSS_SIZE, 0, 0, GLYPH_CROSS_SIZE, 0, baseColor);

        // Body part specific glyph
        renderBodyPartGlyph(consumer, matrix, bodyPart, baseColor);

        // Critical hit X marker
        if (isCritical) {
            float[] critColor = colorToFloat(OverlayTheme.Impact.CORE_GLOW, alpha);
            drawLine(consumer, matrix, -GLYPH_CRIT_SIZE, -GLYPH_CRIT_SIZE, 0, GLYPH_CRIT_SIZE, GLYPH_CRIT_SIZE, 0, critColor);
            drawLine(consumer, matrix, -GLYPH_CRIT_SIZE, GLYPH_CRIT_SIZE, 0, GLYPH_CRIT_SIZE, -GLYPH_CRIT_SIZE, 0, critColor);
        }

        // Damage reduction/amplification indicator
        if (Math.abs(reduction) > 0.05f) {
            int color = reduction > 0
                ? ImpactHudContentBuilder.Colors.REDUCTION
                : ImpactHudContentBuilder.Colors.AMPLIFIED;
            float[] redColor = colorToFloat(color, alpha);
            float length = 0.2f + reductionScale * 0.25f;
            float y = -0.8f;
            drawLine(consumer, matrix, -length, y, 0, length, y, 0, redColor);
            if (reduction < 0) {
                // Plus sign for amplified damage
                drawLine(consumer, matrix, 0, y - length, 0, 0, y + length, 0, redColor);
            }
        }

        poseStack.popPose();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE RENDER HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private static void renderBodyPartGlyph(VertexConsumer consumer, Matrix4f matrix,
                                            BodyPart part, float[] color) {
        switch (part) {
            case HEAD -> {
                // Crown/triangle shape
                drawLine(consumer, matrix, 0, 0.45f, 0, -0.25f, 0.05f, 0, color);
                drawLine(consumer, matrix, 0, 0.45f, 0, 0.25f, 0.05f, 0, color);
                drawLine(consumer, matrix, -0.25f, 0.05f, 0, 0.25f, 0.05f, 0, color);
            }
            case BODY -> {
                // Rectangle
                drawLine(consumer, matrix, -0.25f, 0.25f, 0, 0.25f, 0.25f, 0, color);
                drawLine(consumer, matrix, 0.25f, 0.25f, 0, 0.25f, -0.25f, 0, color);
                drawLine(consumer, matrix, 0.25f, -0.25f, 0, -0.25f, -0.25f, 0, color);
                drawLine(consumer, matrix, -0.25f, -0.25f, 0, -0.25f, 0.25f, 0, color);
            }
            case ARMS -> {
                // Horizontal line
                drawLine(consumer, matrix, -0.45f, 0.05f, 0, 0.45f, 0.05f, 0, color);
            }
            case LEGS -> {
                // Vertical line
                drawLine(consumer, matrix, 0, -0.45f, 0, 0, 0.05f, 0, color);
            }
        }
    }

    private static void drawLine(VertexConsumer consumer, Matrix4f matrix,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float[] color) {
        consumer.addVertex(matrix, x1, y1, z1)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, y2, z2)
            .setColor(color[0], color[1], color[2], color[3])
            .setNormal(0, 1, 0);
    }

    private static float[] colorToFloat(int argb, float alphaOverride) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float a = ((argb >> 24) & 0xFF) / 255f;
        float aFinal = Math.min(a * alphaOverride, 1.0f);
        return new float[] { r, g, b, aFinal };
    }
}
