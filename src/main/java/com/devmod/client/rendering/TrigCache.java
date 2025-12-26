package com.devmod.client.rendering;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TrigCache {

    /** Number of entries in lookup tables (power of 2 for fast modulo) */
    private static final int TABLE_SIZE = 4096;
    private static final int TABLE_MASK = TABLE_SIZE - 1;

    /** Pre-computed lookup tables */
    private static final float[] SIN_TABLE = new float[TABLE_SIZE];
    private static final float[] COS_TABLE = new float[TABLE_SIZE];

    /** Scale factor: angle -> table index */
    private static final float ANGLE_TO_INDEX = TABLE_SIZE / (float) (Math.PI * 2);
    private static final float INDEX_TO_ANGLE = (float) (Math.PI * 2) / TABLE_SIZE;

    /** Initialize lookup tables */
    static {
        for (int i = 0; i < TABLE_SIZE; i++) {
            double angle = i * INDEX_TO_ANGLE;
            SIN_TABLE[i] = (float) Math.sin(angle);
            COS_TABLE[i] = (float) Math.cos(angle);
        }
    }

    private TrigCache() {} // Utility class

    /**
     * Fast sine lookup.
     *
     * @param angle Angle in radians
     * @return Approximate sine value
     */
    public static float sin(float angle) {
        int index = (int) (angle * ANGLE_TO_INDEX) & TABLE_MASK;
        return SIN_TABLE[index];
    }

    /**
     * Fast cosine lookup.
     *
     * @param angle Angle in radians
     * @return Approximate cosine value
     */
    public static float cos(float angle) {
        int index = (int) (angle * ANGLE_TO_INDEX) & TABLE_MASK;
        return COS_TABLE[index];
    }

    /**
     * Fast sine and cosine lookup (returns both values).
     * More efficient when both are needed.
     *
     * @param angle Angle in radians
     * @return Array of [sin, cos]
     */
    public static float[] sinCos(float angle) {
        int index = (int) (angle * ANGLE_TO_INDEX) & TABLE_MASK;
        return new float[] { SIN_TABLE[index], COS_TABLE[index] };
    }

    /**
     * Fast sine and cosine lookup without allocation.
     *
     * @param angle Angle in radians
     * @param out Output array (must be length >= 2), [0]=sin, [1]=cos
     */
    public static void sinCos(float angle, float[] out) {
        int index = (int) (angle * ANGLE_TO_INDEX) & TABLE_MASK;
        out[0] = SIN_TABLE[index];
        out[1] = COS_TABLE[index];
    }

    /**
     * Interpolated sine for higher accuracy when needed.
     *
     * @param angle Angle in radians
     * @return Interpolated sine value
     */
    public static float sinLerp(float angle) {
        float indexF = angle * ANGLE_TO_INDEX;
        int index0 = (int) indexF & TABLE_MASK;
        int index1 = (index0 + 1) & TABLE_MASK;
        float frac = indexF - (int) indexF;
        return SIN_TABLE[index0] * (1 - frac) + SIN_TABLE[index1] * frac;
    }

    /**
     * Interpolated cosine for higher accuracy when needed.
     *
     * @param angle Angle in radians
     * @return Interpolated cosine value
     */
    public static float cosLerp(float angle) {
        float indexF = angle * ANGLE_TO_INDEX;
        int index0 = (int) indexF & TABLE_MASK;
        int index1 = (index0 + 1) & TABLE_MASK;
        float frac = indexF - (int) indexF;
        return COS_TABLE[index0] * (1 - frac) + COS_TABLE[index1] * frac;
    }

    // === Pre-computed Spiral Geometry ===

    /**
     * Pre-computed spiral for vortex rendering.
     * Eliminates per-frame spiral calculation.
     */
    public static class CachedSpiral {
        private final float[] x;
        private final float[] y;
        private final float[] z;
        private final float[] colorT; // Color interpolation factor
        private final int segments;

        /**
         * Creates a cached spiral.
         *
         * @param segments Number of spiral segments
         * @param baseRadius Base radius
         * @param rotations Number of spiral rotations
         * @param verticalFlatten Vertical flattening factor (0.5 = half height)
         * @param radiusFalloff How much radius decreases (0.6 = 60% at end)
         */
        public CachedSpiral(int segments, float baseRadius, float rotations,
                           float verticalFlatten, float radiusFalloff) {
            this.segments = segments;
            this.x = new float[segments + 1];
            this.y = new float[segments + 1];
            this.z = new float[segments + 1];
            this.colorT = new float[segments + 1];

            float twoPi = (float) (Math.PI * 2);

            for (int i = 0; i <= segments; i++) {
                float t = (float) i / segments;
                float angle = t * rotations * twoPi; // Base angle (rotation added at render time)
                float radius = baseRadius * (1.0f - t * radiusFalloff);

                // Store un-rotated positions (rotation applied at render time)
                x[i] = cos(angle) * radius;
                y[i] = sin(angle) * radius * verticalFlatten;
                z[i] = sin(angle) * radius;
                colorT[i] = t;
            }
        }

        /**
         * Gets X coordinate at segment index with rotation applied.
         */
        public float getX(int index, float rotation) {
            float baseAngle = (float) index / segments * (float) (Math.PI * 2);
            return cos(baseAngle + rotation) * (x[index] / cos(baseAngle));
        }

        /**
         * Renders the spiral to vertex consumer with rotation.
         * This is optimized to avoid per-vertex Math calls.
         *
         * @param consumer Vertex consumer callback
         * @param rotation Current rotation offset
         * @param alpha Base alpha
         */
        public void render(SpiralVertexConsumer consumer, float rotation, float alpha) {
            for (int i = 0; i <= segments; i++) {
                float t = colorT[i];
                // Rotate around Y axis
                float cosR = cos(rotation);
                float sinR = sin(rotation);

                float rx = x[i] * cosR - z[i] * sinR;
                float ry = y[i];
                float rz = x[i] * sinR + z[i] * cosR;

                float segmentAlpha = alpha * (1.0f - t * 0.5f);
                consumer.vertex(rx, ry, rz, t, segmentAlpha);
            }
        }

        public int getSegments() {
            return segments;
        }
    }

    @FunctionalInterface
    public interface SpiralVertexConsumer {
        void vertex(float x, float y, float z, float colorT, float alpha);
    }

    // === Pre-computed Ring Geometry ===

    /**
     * Pre-computed circular ring for vortex rendering.
     */
    public static class CachedRing {
        private final float[] x;
        private final float[] z;
        private final int segments;

        public CachedRing(int segments) {
            this.segments = segments;
            this.x = new float[segments + 1];
            this.z = new float[segments + 1];

            float twoPi = (float) (Math.PI * 2);
            for (int i = 0; i <= segments; i++) {
                float angle = twoPi * i / segments;
                x[i] = cos(angle);
                z[i] = sin(angle);
            }
        }

        /**
         * Renders the ring at specified radius with rotation.
         */
        public void render(RingVertexConsumer consumer, float radius, float rotation, float alpha) {
            float cosR = cos(rotation);
            float sinR = sin(rotation);

            for (int i = 0; i <= segments; i++) {
                float rx = (x[i] * cosR - z[i] * sinR) * radius;
                float rz = (x[i] * sinR + z[i] * cosR) * radius;
                consumer.vertex(rx, rz, alpha);
            }
        }

        public int getSegments() {
            return segments;
        }
    }

    @FunctionalInterface
    public interface RingVertexConsumer {
        void vertex(float x, float z, float alpha);
    }

    // === Singleton Cached Geometries ===

    @Nullable
    private static CachedSpiral outerSpiral;
    @Nullable
    private static CachedSpiral innerSpiral;
    @Nullable
    private static CachedRing ring24;

    /**
     * Gets cached outer spiral for vortex (32 segments).
     */
    public static CachedSpiral getOuterSpiral() {
        if (outerSpiral == null) {
            outerSpiral = new CachedSpiral(32, 0.25f, 2.0f, 0.5f, 0.6f);
        }
        return outerSpiral;
    }

    /**
     * Gets cached inner spiral for vortex (32 segments).
     */
    public static CachedSpiral getInnerSpiral() {
        if (innerSpiral == null) {
            innerSpiral = new CachedSpiral(32, 0.15f, 2.0f, 0.3f, 0.5f);
        }
        return innerSpiral;
    }

    /**
     * Gets cached ring with 24 segments.
     */
    public static CachedRing getRing24() {
        if (ring24 == null) {
            ring24 = new CachedRing(24);
        }
        return ring24;
    }

    /**
     * Clears cached geometries (call on resource reload).
     */
    public static void clearCache() {
        outerSpiral = null;
        innerSpiral = null;
        ring24 = null;
    }
}
