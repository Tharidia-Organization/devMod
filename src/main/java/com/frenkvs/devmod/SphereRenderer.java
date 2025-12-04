package com.frenkvs.devmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility for rendering spheres in 3D space.
 * Ported from PhantomShapes Kotlin/Fabric to Java/NeoForge.
 *
 * Used for visualizing mob aggro ranges and detection areas.
 */
public class SphereRenderer {

    /**
     * Generates the surface points of a sphere using a midpoint circle algorithm.
     * Only generates the shell (surface) of the sphere, not a filled volume.
     *
     * @param center Center position of the sphere
     * @param radius Radius in blocks
     * @return Set of Vec3 positions representing the sphere surface
     */
    public static Set<Vec3> generateSpherePoints(Vec3 center, double radius) {
        double invRadius = 1.0 / (radius + 0.5);
        int ceilRadius = (int) Math.ceil(radius + 0.5);

        Set<Vec3> positions = new HashSet<>();
        double nextXn = 0.0;

        for (int x = 0; x <= ceilRadius; x++) {
            double xn = nextXn;
            nextXn = (x + 1) * invRadius;
            double nextYn = 0.0;

            for (int y = 0; y <= ceilRadius; y++) {
                double yn = nextYn;
                nextYn = (y + 1) * invRadius;
                double nextZn = 0.0;

                for (int z = 0; z <= ceilRadius; z++) {
                    double zn = nextZn;
                    nextZn = (z + 1) * invRadius;

                    // Check if current point is outside sphere
                    double distanceSq = lengthSq(xn, yn, zn);
                    if (distanceSq > 1) {
                        if (z == 0) {
                            if (y == 0) {
                                // We're done with X axis
                                return positions;
                            }
                            // Move to next Y
                            break;
                        }
                        // Move to next Z
                        break;
                    }

                    // Check if this is a surface point (not completely surrounded by other points)
                    if (lengthSq(nextXn, yn, zn) <= 1 &&
                        lengthSq(xn, nextYn, zn) <= 1 &&
                        lengthSq(xn, yn, nextZn) <= 1) {
                        // This point is inside the sphere, skip it
                        continue;
                    }

                    // Add all 8 octants of the sphere (mirror across all axes)
                    positions.add(new Vec3(center.x + x, center.y + y, center.z + z));
                    positions.add(new Vec3(center.x - x, center.y + y, center.z + z));
                    positions.add(new Vec3(center.x + x, center.y - y, center.z + z));
                    positions.add(new Vec3(center.x + x, center.y + y, center.z - z));
                    positions.add(new Vec3(center.x - x, center.y - y, center.z + z));
                    positions.add(new Vec3(center.x + x, center.y - y, center.z - z));
                    positions.add(new Vec3(center.x - x, center.y + y, center.z - z));
                    positions.add(new Vec3(center.x - x, center.y - y, center.z - z));
                }
            }
        }

        return positions;
    }

    /**
     * Helper method to calculate squared length of a 3D vector.
     * Used for distance checks without expensive sqrt() calls.
     */
    private static double lengthSq(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    /**
     * Renders a sphere as a wireframe (outline mode).
     *
     * @param poseStack The pose stack for transformations
     * @param bufferSource Buffer source for rendering
     * @param center Center position of the sphere
     * @param radius Radius in blocks
     * @param red Red color component (0-1)
     * @param green Green color component (0-1)
     * @param blue Blue color component (0-1)
     * @param alpha Opacity (0-1)
     */
    @SuppressWarnings("null")
    public static void renderSphereWireframe(PoseStack poseStack, MultiBufferSource bufferSource,
                                              Vec3 center, double radius,
                                              float red, float green, float blue, float alpha) {
        Set<Vec3> points = generateSpherePoints(center, radius);
        if (points.isEmpty()) return;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        // Render small cubes at each point to visualize the sphere
        float blockSize = 0.1f; // Small dots

        for (Vec3 point : points) {
            float x = (float) (point.x - center.x);
            float y = (float) (point.y - center.y);
            float z = (float) (point.z - center.z);

            // Draw a small wireframe cube at each point
            drawWireframeCube(consumer, matrix, x, y, z, blockSize, red, green, blue, alpha);
        }
    }

    /**
     * Renders a sphere as a filled surface (translucent faces).
     * Uses latitude/longitude tesselation for smooth appearance.
     *
     * @param poseStack The pose stack for transformations
     * @param bufferSource Buffer source for rendering
     * @param center Center position of the sphere
     * @param radius Radius in blocks
     * @param red Red color component (0-1)
     * @param green Green color component (0-1)
     * @param blue Blue color component (0-1)
     * @param alpha Opacity (0-1)
     */
    @SuppressWarnings("null")
    public static void renderSphereFilled(PoseStack poseStack, MultiBufferSource bufferSource,
                                           Vec3 center, double radius,
                                           float red, float green, float blue, float alpha) {
        // Use custom RenderType with POSITION_COLOR + alpha blending
        VertexConsumer consumer = bufferSource.getBuffer(com.frenkvs.devmod.rendering.CustomRenderTypes.TRANSLUCENT_POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        int latitudes = 16;  // Vertical segments
        int longitudes = 32; // Horizontal segments

        float cx = (float) center.x;
        float cy = (float) center.y;
        float cz = (float) center.z;
        float r = (float) radius;

        // Generate sphere mesh using latitude/longitude
        for (int lat = 0; lat < latitudes; lat++) {
            float theta1 = lat * (float) Math.PI / latitudes;
            float theta2 = (lat + 1) * (float) Math.PI / latitudes;

            for (int lon = 0; lon < longitudes; lon++) {
                float phi1 = lon * 2 * (float) Math.PI / longitudes;
                float phi2 = (lon + 1) * 2 * (float) Math.PI / longitudes;

                // Calculate 4 vertices of the quad
                float x1 = r * (float) (Math.sin(theta1) * Math.cos(phi1));
                float y1 = r * (float) Math.cos(theta1);
                float z1 = r * (float) (Math.sin(theta1) * Math.sin(phi1));

                float x2 = r * (float) (Math.sin(theta1) * Math.cos(phi2));
                float y2 = r * (float) Math.cos(theta1);
                float z2 = r * (float) (Math.sin(theta1) * Math.sin(phi2));

                float x3 = r * (float) (Math.sin(theta2) * Math.cos(phi2));
                float y3 = r * (float) Math.cos(theta2);
                float z3 = r * (float) (Math.sin(theta2) * Math.sin(phi2));

                float x4 = r * (float) (Math.sin(theta2) * Math.cos(phi1));
                float y4 = r * (float) Math.cos(theta2);
                float z4 = r * (float) (Math.sin(theta2) * Math.sin(phi1));

                // Render quad (2 triangles)
                quadSimple(consumer, matrix,
                    cx + x1, cy + y1, cz + z1,
                    cx + x2, cy + y2, cz + z2,
                    cx + x3, cy + y3, cz + z3,
                    cx + x4, cy + y4, cz + z4,
                    red, green, blue, alpha);
            }
        }
    }

    /**
     * Draws a wireframe cube outline.
     */
    private static void drawWireframeCube(VertexConsumer consumer, Matrix4f matrix,
                                          float cx, float cy, float cz, float halfSize,
                                          float r, float g, float b, float a) {
        float x1 = cx - halfSize;
        float y1 = cy - halfSize;
        float z1 = cz - halfSize;
        float x2 = cx + halfSize;
        float y2 = cy + halfSize;
        float z2 = cz + halfSize;

        // Bottom face (4 lines)
        line(consumer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(consumer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(consumer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(consumer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        // Top face (4 lines)
        line(consumer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(consumer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(consumer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(consumer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        // Vertical edges (4 lines)
        line(consumer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(consumer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(consumer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(consumer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    /**
     * Draws a filled cube with 6 quad faces.
     * SIMPLE version: only position + color (for RenderType.debugQuads)
     */
    private static void drawFilledCubeSimple(VertexConsumer consumer, Matrix4f matrix,
                                             float cx, float cy, float cz, float halfSize,
                                             float r, float g, float b, float a) {
        float x1 = cx - halfSize;
        float y1 = cy - halfSize;
        float z1 = cz - halfSize;
        float x2 = cx + halfSize;
        float y2 = cy + halfSize;
        float z2 = cz + halfSize;

        // Top face (+Y)
        quadSimple(consumer, matrix, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);

        // Bottom face (-Y)
        quadSimple(consumer, matrix, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1, r, g, b, a);

        // North face (+Z)
        quadSimple(consumer, matrix, x1, y2, z2, x2, y2, z2, x2, y1, z2, x1, y1, z2, r, g, b, a);

        // South face (-Z)
        quadSimple(consumer, matrix, x2, y2, z1, x1, y2, z1, x1, y1, z1, x2, y1, z1, r, g, b, a);

        // East face (+X)
        quadSimple(consumer, matrix, x2, y2, z2, x2, y2, z1, x2, y1, z1, x2, y1, z2, r, g, b, a);

        // West face (-X)
        quadSimple(consumer, matrix, x1, y2, z1, x1, y2, z2, x1, y1, z2, x1, y1, z1, r, g, b, a);
    }

    /**
     * Draws a filled cube with 6 quad faces (OLD VERSION with normals - unused).
     */
    @SuppressWarnings("unused")
    private static void drawFilledCube(VertexConsumer consumer, Matrix4f matrix,
                                       float cx, float cy, float cz, float halfSize,
                                       float r, float g, float b, float a) {
        float x1 = cx - halfSize;
        float y1 = cy - halfSize;
        float z1 = cz - halfSize;
        float x2 = cx + halfSize;
        float y2 = cy + halfSize;
        float z2 = cz + halfSize;

        // Top face (+Y)
        quad(consumer, matrix, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);

        // Bottom face (-Y)
        quad(consumer, matrix, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1, r, g, b, a);

        // North face (+Z)
        quad(consumer, matrix, x1, y2, z2, x2, y2, z2, x2, y1, z2, x1, y1, z2, r, g, b, a);

        // South face (-Z)
        quad(consumer, matrix, x2, y2, z1, x1, y2, z1, x1, y1, z1, x2, y1, z1, r, g, b, a);

        // East face (+X)
        quad(consumer, matrix, x2, y2, z2, x2, y2, z1, x2, y1, z1, x2, y1, z2, r, g, b, a);

        // West face (-X)
        quad(consumer, matrix, x1, y2, z1, x1, y2, z2, x1, y1, z2, x1, y1, z1, r, g, b, a);
    }

    /**
     * Helper to draw a line between two points.
     */
    @SuppressWarnings("null")
    private static void line(VertexConsumer consumer, Matrix4f matrix,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
    }

    /**
     * Helper to draw a quad (4 vertices) - SIMPLE version (position + color only).
     */
    @SuppressWarnings("null")
    private static void quadSimple(VertexConsumer consumer, Matrix4f matrix,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3,
                                    float x4, float y4, float z4,
                                    float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a);
    }

    /**
     * Helper to draw a quad (4 vertices) - OLD VERSION (unused).
     */
    @SuppressWarnings({"null", "unused"})
    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a);
    }
}
