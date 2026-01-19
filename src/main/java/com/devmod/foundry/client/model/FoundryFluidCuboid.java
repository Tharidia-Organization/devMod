package com.devmod.foundry.client.model;

import org.joml.Vector3f;

/**
 * Represents a scalable fluid cuboid for dynamic fluid level rendering.
 * The fluid geometry can be scaled based on the fill amount.
 */
public record FoundryFluidCuboid(
    Vector3f from,
    Vector3f to,
    int increments
) {
    public static final int DEFAULT_INCREMENTS = 256;

    /**
     * Create a fluid cuboid with default increments (256 levels).
     */
    public static FoundryFluidCuboid create(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return new FoundryFluidCuboid(
            new Vector3f(minX, minY, minZ),
            new Vector3f(maxX, maxY, maxZ),
            DEFAULT_INCREMENTS
        );
    }

    /**
     * Create a fluid cuboid with custom increments.
     */
    public static FoundryFluidCuboid create(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int increments) {
        return new FoundryFluidCuboid(
            new Vector3f(minX, minY, minZ),
            new Vector3f(maxX, maxY, maxZ),
            increments
        );
    }

    /**
     * Get scaled fluid bounds for a given fill amount.
     * @param amount Current amount (0 to increments)
     * @param isGas If true, fluid fills from top (for gases lighter than air)
     * @return ScaledBounds with adjusted from/to vectors
     */
    public ScaledBounds getScaledBounds(int amount, boolean isGas) {
        if (amount <= 0) {
            return null;
        }

        float minX = from.x() / 16f;
        float minY = from.y() / 16f;
        float minZ = from.z() / 16f;
        float maxX = to.x() / 16f;
        float maxY = to.y() / 16f;
        float maxZ = to.z() / 16f;

        // Clamp amount to valid range
        amount = Math.min(amount, increments);

        float scaledMinY, scaledMaxY;
        if (isGas) {
            // Gases fill from top down
            scaledMaxY = maxY;
            scaledMinY = maxY - (amount * (maxY - minY) / increments);
        } else {
            // Liquids fill from bottom up
            scaledMinY = minY;
            scaledMaxY = minY + (amount * (maxY - minY) / increments);
        }

        return new ScaledBounds(
            new Vector3f(minX, scaledMinY, minZ),
            new Vector3f(maxX, scaledMaxY, maxZ)
        );
    }

    /**
     * Get scaled bounds based on fill percentage (0.0 to 1.0).
     */
    public ScaledBounds getScaledBounds(float fillPercentage, boolean isGas) {
        int amount = (int)(fillPercentage * increments);
        return getScaledBounds(amount, isGas);
    }

    /**
     * Scaled bounds result for fluid rendering.
     */
    public record ScaledBounds(Vector3f from, Vector3f to) {
        public float minX() { return from.x(); }
        public float minY() { return from.y(); }
        public float minZ() { return from.z(); }
        public float maxX() { return to.x(); }
        public float maxY() { return to.y(); }
        public float maxZ() { return to.z(); }

        /**
         * Calculate UV coordinates for a face based on bounds.
         */
        public float[] getUVs(float u0, float v0, float u1, float v1) {
            return new float[] { u0, v0, u1, v1 };
        }
    }
}
