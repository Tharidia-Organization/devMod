package com.frenkvs.devmod.ui.radial.render;

import com.frenkvs.devmod.ui.radial.config.RadialMenuConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Geometric rendering primitives for the Radial Menu.
 *
 * <p>This class provides optimized methods for rendering:</p>
 * <ul>
 *   <li>Circles (filled)</li>
 *   <li>Rings/Annuli (filled between two radii)</li>
 *   <li>Arc segments (pie slices)</li>
 *   <li>Arc outlines</li>
 *   <li>Lines</li>
 *   <li>Radial gradients</li>
 * </ul>
 *
 * <p>Performance optimizations:</p>
 * <ul>
 *   <li>Precalculated sin/cos lookup tables</li>
 *   <li>Configurable segment counts via {@link RadialMenuConstants}</li>
 *   <li>Reusable color extraction</li>
 * </ul>
 */
@SuppressWarnings("null") // Minecraft API null annotations
public final class RadialGeometry {

    private RadialGeometry() {
        // Utility class - no instantiation
    }

    // ================================================================
    // TRIGONOMETRY CACHE
    // ================================================================

    /** Precalculated sine values for circle rendering (CIRCLE_SEGMENTS + 1 entries) */
    private static final float[] CIRCLE_SIN;
    /** Precalculated cosine values for circle rendering */
    private static final float[] CIRCLE_COS;

    /** Precalculated sine values for ring rendering (RING_SEGMENTS + 1 entries) */
    private static final float[] RING_SIN;
    /** Precalculated cosine values for ring rendering */
    private static final float[] RING_COS;

    static {
        // Initialize circle lookup tables
        int circleSegs = RadialMenuConstants.CIRCLE_SEGMENTS;
        CIRCLE_SIN = new float[circleSegs + 1];
        CIRCLE_COS = new float[circleSegs + 1];
        for (int i = 0; i <= circleSegs; i++) {
            double angle = (Math.PI * 2 * i) / circleSegs;
            CIRCLE_SIN[i] = (float) Math.sin(angle);
            CIRCLE_COS[i] = (float) Math.cos(angle);
        }

        // Initialize ring lookup tables
        int ringSegs = RadialMenuConstants.RING_SEGMENTS;
        RING_SIN = new float[ringSegs + 1];
        RING_COS = new float[ringSegs + 1];
        for (int i = 0; i <= ringSegs; i++) {
            double angle = (Math.PI * 2 * i) / ringSegs;
            RING_SIN[i] = (float) Math.sin(angle);
            RING_COS[i] = (float) Math.cos(angle);
        }
    }

    // ================================================================
    // COLOR UTILITIES
    // ================================================================

    /**
     * Blends two ARGB colors by the given factor.
     *
     * @param color1 first color (0xAARRGGBB format)
     * @param color2 second color
     * @param factor blend factor (0 = color1, 1 = color2)
     * @return blended color
     */
    public static int blendColors(int color1, int color2, float factor) {
        float invFactor = 1f - factor;

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 * invFactor + a2 * factor);
        int r = (int) (r1 * invFactor + r2 * factor);
        int g = (int) (g1 * invFactor + g2 * factor);
        int b = (int) (b1 * invFactor + b2 * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Applies an alpha multiplier to an existing ARGB color.
     *
     * @param color the original color (0xAARRGGBB format)
     * @param alpha alpha value (0-255)
     * @return color with modified alpha
     */
    public static int applyAlpha(int color, int alpha) {
        int existingAlpha = (color >> 24) & 0xFF;
        int newAlpha = (existingAlpha * alpha) / 255;
        return (newAlpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Extracts RGBA components from an ARGB color as floats (0-1 range).
     *
     * @param color ARGB color
     * @return float array [r, g, b, a]
     */
    public static float[] extractRGBA(int color) {
        return new float[] {
            ((color >> 16) & 0xFF) / 255f,
            ((color >> 8) & 0xFF) / 255f,
            (color & 0xFF) / 255f,
            ((color >> 24) & 0xFF) / 255f
        };
    }

    // ================================================================
    // CIRCLE RENDERING
    // ================================================================

    /**
     * Renders a filled circle using precalculated trigonometry.
     *
     * @param graphics the GUI graphics context
     * @param cx center X coordinate
     * @param cy center Y coordinate
     * @param radius circle radius
     * @param color fill color (ARGB)
     */
    public static void renderCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();

        float[] rgba = extractRGBA(color);
        float r = rgba[0], g = rgba[1], b = rgba[2], a = rgba[3];

        // Center vertex
        buffer.addVertex(matrix, cx, cy, 0).setColor(r, g, b, a);

        // Perimeter vertices using cached sin/cos
        for (int i = 0; i <= RadialMenuConstants.CIRCLE_SEGMENTS; i++) {
            float x = cx + CIRCLE_COS[i] * radius;
            float y = cy + CIRCLE_SIN[i] * radius;
            buffer.addVertex(matrix, x, y, 0).setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // ================================================================
    // RING RENDERING
    // ================================================================

    /**
     * Renders a filled ring (annulus) between two radii.
     *
     * @param graphics the GUI graphics context
     * @param cx center X coordinate
     * @param cy center Y coordinate
     * @param innerRadius inner radius
     * @param outerRadius outer radius
     * @param color fill color (ARGB)
     */
    public static void renderRing(GuiGraphics graphics, int cx, int cy,
                                   int innerRadius, int outerRadius, int color) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();

        float[] rgba = extractRGBA(color);
        float r = rgba[0], g = rgba[1], b = rgba[2], a = rgba[3];

        for (int i = 0; i <= RadialMenuConstants.RING_SEGMENTS; i++) {
            float cos = RING_COS[i];
            float sin = RING_SIN[i];
            buffer.addVertex(matrix, cx + cos * innerRadius, cy + sin * innerRadius, 0)
                .setColor(r, g, b, a);
            buffer.addVertex(matrix, cx + cos * outerRadius, cy + sin * outerRadius, 0)
                .setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // ================================================================
    // ARC SEGMENT RENDERING
    // ================================================================

    /**
     * Renders a filled arc segment (pie slice) between two radii and two angles.
     *
     * @param graphics the GUI graphics context
     * @param cx center X coordinate
     * @param cy center Y coordinate
     * @param innerR inner radius
     * @param outerR outer radius
     * @param startAngle start angle in radians
     * @param endAngle end angle in radians
     * @param color fill color (ARGB)
     */
    public static void renderArcSegment(GuiGraphics graphics, int cx, int cy,
                                         int innerR, int outerR,
                                         double startAngle, double endAngle, int color) {
        int segments = RadialMenuConstants.ARC_SEGMENTS;
        double angleStep = (endAngle - startAngle) / segments;

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();

        float[] rgba = extractRGBA(color);
        float r = rgba[0], g = rgba[1], b = rgba[2], a = rgba[3];

        for (int i = 0; i <= segments; i++) {
            double angle = startAngle + i * angleStep;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            buffer.addVertex(matrix, cx + cos * innerR, cy + sin * innerR, 0)
                .setColor(r, g, b, a);
            buffer.addVertex(matrix, cx + cos * outerR, cy + sin * outerR, 0)
                .setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // ================================================================
    // ARC OUTLINE RENDERING
    // ================================================================

    /**
     * Renders an arc outline (border) at the specified radius.
     *
     * @param graphics the GUI graphics context
     * @param cx center X coordinate
     * @param cy center Y coordinate
     * @param radius arc radius
     * @param startAngle start angle in radians
     * @param endAngle end angle in radians
     * @param color line color (ARGB)
     * @param lineWidth line thickness
     */
    public static void renderArcOutline(GuiGraphics graphics, int cx, int cy, int radius,
                                         double startAngle, double endAngle,
                                         int color, int lineWidth) {
        int segments = RadialMenuConstants.ARC_SEGMENTS;
        double angleStep = (endAngle - startAngle) / segments;

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(lineWidth);

        BufferBuilder buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();

        float[] rgba = extractRGBA(color);
        float r = rgba[0], g = rgba[1], b = rgba[2], a = rgba[3];

        for (int i = 0; i <= segments; i++) {
            double angle = startAngle + i * angleStep;
            float x = (float) (cx + Math.cos(angle) * radius);
            float y = (float) (cy + Math.sin(angle) * radius);
            buffer.addVertex(matrix, x, y, 0).setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // ================================================================
    // LINE RENDERING
    // ================================================================

    /**
     * Renders a single line between two points.
     *
     * @param graphics the GUI graphics context
     * @param x1 start X coordinate
     * @param y1 start Y coordinate
     * @param x2 end X coordinate
     * @param y2 end Y coordinate
     * @param color line color (ARGB)
     */
    public static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();

        float[] rgba = extractRGBA(color);
        float r = rgba[0], g = rgba[1], b = rgba[2], a = rgba[3];

        buffer.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // ================================================================
    // GRADIENT RENDERING
    // ================================================================

    /**
     * Renders a radial gradient from center color to edge color.
     *
     * @param graphics the GUI graphics context
     * @param cx center X coordinate
     * @param cy center Y coordinate
     * @param radius gradient radius
     * @param centerColor color at center (ARGB)
     * @param edgeColor color at edge (ARGB)
     */
    public static void renderRadialGradient(GuiGraphics graphics, int cx, int cy, int radius,
                                             int centerColor, int edgeColor) {
        int rings = 8;
        for (int ring = 0; ring < rings; ring++) {
            float t1 = (float) ring / rings;
            int r1 = (int) (radius * t1);
            int r2 = (int) (radius * (float) (ring + 1) / rings);
            int color = blendColors(centerColor, edgeColor, t1);
            renderRing(graphics, cx, cy, r1, r2, color);
        }
    }

    // ================================================================
    // ANGLE UTILITIES
    // ================================================================

    /**
     * Normalizes an angle to the range [0, 2π).
     *
     * @param angle angle in radians
     * @return normalized angle
     */
    public static double normalizeAngle(double angle) {
        while (angle < 0) angle += RadialMenuConstants.TWO_PI;
        while (angle >= RadialMenuConstants.TWO_PI) angle -= RadialMenuConstants.TWO_PI;
        return angle;
    }

    /**
     * Calculates the angle from center to a point.
     *
     * @param cx center X
     * @param cy center Y
     * @param px point X
     * @param py point Y
     * @return angle in radians [0, 2π)
     */
    public static double angleToPoint(int cx, int cy, int px, int py) {
        double angle = Math.atan2(py - cy, px - cx);
        return normalizeAngle(angle);
    }

    /**
     * Calculates distance from center to a point.
     *
     * @param cx center X
     * @param cy center Y
     * @param px point X
     * @param py point Y
     * @return distance
     */
    public static double distanceToPoint(int cx, int cy, int px, int py) {
        double dx = px - cx;
        double dy = py - cy;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
