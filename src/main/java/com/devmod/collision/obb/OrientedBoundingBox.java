package com.devmod.collision.obb;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
public final class OrientedBoundingBox {

    private final Vec3 center;
    private final Vec3 halfExtents;
    private final Quaternionf rotation;

    // Cached axes (lazy computed)
    @Nullable
    private volatile Vec3[] axes;

    /**
     * Creates an OBB with the given parameters.
     *
     * @param center      World-space center position
     * @param halfExtents Size along each local axis (half of full extent)
     * @param rotation    Orientation quaternion
     */
    public OrientedBoundingBox(@Nonnull Vec3 center, @Nonnull Vec3 halfExtents, @Nonnull Quaternionf rotation) {
        this.center = Objects.requireNonNull(center, "center");
        this.halfExtents = Objects.requireNonNull(halfExtents, "halfExtents");
        this.rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
    }

    /**
     * Creates an axis-aligned OBB (no rotation).
     */
    public OrientedBoundingBox(@Nonnull Vec3 center, @Nonnull Vec3 halfExtents) {
        this(center, halfExtents, new Quaternionf());
    }

    // ==================== Factory Methods ====================

    /**
     * Creates an OBB from an AABB (no rotation).
     */
    @Nonnull
    public static OrientedBoundingBox fromAABB(@Nonnull AABB aabb) {
        Vec3 center = Objects.requireNonNull(aabb.getCenter());
        Vec3 halfExtents = new Vec3(
            aabb.getXsize() / 2.0,
            aabb.getYsize() / 2.0,
            aabb.getZsize() / 2.0
        );
        return Objects.requireNonNull(new OrientedBoundingBox(center, halfExtents));
    }

    /**
     * Creates an OBB from center and full extents (not half).
     */
    @Nonnull
    public static OrientedBoundingBox fromCenterAndSize(@Nonnull Vec3 center, @Nonnull Vec3 size) {
        return new OrientedBoundingBox(center, new Vec3(size.x / 2, size.y / 2, size.z / 2));
    }

    /**
     * Creates an OBB from center, half-extents, and rotation.
     */
    @Nonnull
    public static OrientedBoundingBox create(@Nonnull Vec3 center, @Nonnull Vec3 halfExtents, @Nonnull Quaternionf rotation) {
        return new OrientedBoundingBox(center, halfExtents, rotation);
    }

    // ==================== Transform Operations ====================

    /**
     * Returns a new OBB transformed by the given matrix.
     * Extracts translation, rotation, and scale from the matrix.
     */
    @Nonnull
    public OrientedBoundingBox transform(@Nonnull Matrix4f matrix) {
        // Extract translation
        Vector3f translation = new Vector3f();
        matrix.getTranslation(translation);

        // Transform center
        Vector3f transformedCenter = new Vector3f((float) center.x, (float) center.y, (float) center.z);
        matrix.transformPosition(transformedCenter);
        Vec3 newCenter = new Vec3(transformedCenter.x, transformedCenter.y, transformedCenter.z);

        // Extract rotation from matrix
        Quaternionf matrixRotation = new Quaternionf();
        matrix.getNormalizedRotation(matrixRotation);

        // Combine rotations
        Quaternionf newRotation = new Quaternionf(matrixRotation).mul(this.rotation);

        // Extract scale and apply to half-extents
        Vector3f scale = new Vector3f();
        matrix.getScale(scale);
        Vec3 newHalfExtents = new Vec3(
            halfExtents.x * scale.x,
            halfExtents.y * scale.y,
            halfExtents.z * scale.z
        );

        return new OrientedBoundingBox(newCenter, newHalfExtents, Objects.requireNonNull(newRotation));
    }

    /**
     * Returns a new OBB translated by the given offset.
     */
    @Nonnull
    public OrientedBoundingBox translate(@Nonnull Vec3 offset) {
        return new OrientedBoundingBox(Objects.requireNonNull(center.add(offset)), Objects.requireNonNull(halfExtents), Objects.requireNonNull(rotation));
    }

    /**
     * Returns a new OBB rotated by the given quaternion.
     * Rotation is applied in world space (pre-multiplication).
     */
    @Nonnull
    public OrientedBoundingBox rotate(@Nonnull Quaternionf additionalRotation) {
        Quaternionf newRotation = new Quaternionf(additionalRotation).mul(this.rotation);
        return new OrientedBoundingBox(Objects.requireNonNull(center), Objects.requireNonNull(halfExtents), Objects.requireNonNull(newRotation));
    }

    /**
     * Returns a new OBB rotated around a pivot point.
     */
    @Nonnull
    public OrientedBoundingBox rotateAround(@Nonnull Vec3 pivot, @Nonnull Quaternionf additionalRotation) {
        // Translate center relative to pivot
        Vec3 relative = center.subtract(pivot);

        // Rotate the relative position
        Vector3f rotatedRelative = new Vector3f((float) relative.x, (float) relative.y, (float) relative.z);
        additionalRotation.transform(rotatedRelative);

        // New center = pivot + rotated relative
        Vec3 newCenter = pivot.add(rotatedRelative.x, rotatedRelative.y, rotatedRelative.z);

        // Combine rotations
        Quaternionf newRotation = new Quaternionf(additionalRotation).mul(this.rotation);

        return new OrientedBoundingBox(Objects.requireNonNull(newCenter), Objects.requireNonNull(halfExtents), Objects.requireNonNull(newRotation));
    }

    /**
     * Returns a new OBB scaled by the given factor.
     */
    @Nonnull
    public OrientedBoundingBox scale(double factor) {
        return new OrientedBoundingBox(Objects.requireNonNull(center), Objects.requireNonNull(halfExtents.scale(factor)), Objects.requireNonNull(rotation));
    }

    /**
     * Returns a new OBB scaled non-uniformly.
     */
    @Nonnull
    public OrientedBoundingBox scale(@Nonnull Vec3 factors) {
        return new OrientedBoundingBox(
            Objects.requireNonNull(center),
            new Vec3(halfExtents.x * factors.x, halfExtents.y * factors.y, halfExtents.z * factors.z),
            Objects.requireNonNull(rotation)
        );
    }

    // ==================== Query Methods ====================

    /**
     * Gets the 3 orthogonal axes of this OBB in world space.
     * Cached for performance (computed once per instance).
     *
     * @return Array of 3 unit vectors: [right, up, forward] (local X, Y, Z)
     */
    @Nonnull
    public Vec3[] getAxes() {
        Vec3[] cached = this.axes;
        if (cached == null) {
            cached = computeAxes();
            this.axes = cached;
        }
        return cached;
    }

    @Nonnull
    private Vec3[] computeAxes() {
        // Local axes
        Vector3f axisX = new Vector3f(1, 0, 0);
        Vector3f axisY = new Vector3f(0, 1, 0);
        Vector3f axisZ = new Vector3f(0, 0, 1);

        // Transform by rotation
        rotation.transform(axisX);
        rotation.transform(axisY);
        rotation.transform(axisZ);

        return new Vec3[]{
            new Vec3(axisX.x, axisX.y, axisX.z),
            new Vec3(axisY.x, axisY.y, axisY.z),
            new Vec3(axisZ.x, axisZ.y, axisZ.z)
        };
    }

    /**
     * Tests if a point is inside this OBB.
     */
    public boolean contains(@Nonnull Vec3 point) {
        // Transform point to OBB local space
        Vec3 local = worldToLocal(point);

        // Check against half-extents (AABB test in local space)
        return Math.abs(local.x) <= halfExtents.x &&
               Math.abs(local.y) <= halfExtents.y &&
               Math.abs(local.z) <= halfExtents.z;
    }

    /**
     * Finds the closest point on this OBB to the given point.
     */
    @Nonnull
    public Vec3 closestPoint(@Nonnull Vec3 point) {
        // Transform to local space
        Vec3 local = worldToLocal(point);

        // Clamp to half-extents
        double clampedX = Math.max(-halfExtents.x, Math.min(halfExtents.x, local.x));
        double clampedY = Math.max(-halfExtents.y, Math.min(halfExtents.y, local.y));
        double clampedZ = Math.max(-halfExtents.z, Math.min(halfExtents.z, local.z));

        // Transform back to world space
        return localToWorld(new Vec3(clampedX, clampedY, clampedZ));
    }

    /**
     * Computes squared distance from a point to this OBB.
     */
    public double distanceToSq(@Nonnull Vec3 point) {
        Vec3 closest = closestPoint(point);
        return point.distanceToSqr(closest);
    }

    /**
     * Computes distance from a point to this OBB.
     */
    public double distanceTo(@Nonnull Vec3 point) {
        return Math.sqrt(distanceToSq(point));
    }

    /**
     * Converts a world-space point to OBB local space.
     */
    @Nonnull
    public Vec3 worldToLocal(@Nonnull Vec3 worldPoint) {
        // Translate to origin
        Vec3 relative = Objects.requireNonNull(worldPoint.subtract(Objects.requireNonNull(center)));

        // Apply inverse rotation
        Quaternionf inverseRotation = new Quaternionf(rotation).conjugate();
        Vector3f local = new Vector3f((float) relative.x, (float) relative.y, (float) relative.z);
        inverseRotation.transform(local);

        return new Vec3(local.x, local.y, local.z);
    }

    /**
     * Converts an OBB local-space point to world space.
     */
    @Nonnull
    public Vec3 localToWorld(@Nonnull Vec3 localPoint) {
        // Apply rotation
        Vector3f rotated = new Vector3f((float) localPoint.x, (float) localPoint.y, (float) localPoint.z);
        rotation.transform(rotated);

        // Translate from origin
        return Objects.requireNonNull(center.add(rotated.x, rotated.y, rotated.z));
    }

    /**
     * Transforms a direction vector from local to world space.
     */
    @Nonnull
    public Vec3 localDirectionToWorld(@Nonnull Vec3 localDir) {
        Vector3f rotated = new Vector3f((float) localDir.x, (float) localDir.y, (float) localDir.z);
        rotation.transform(rotated);
        return new Vec3(rotated.x, rotated.y, rotated.z);
    }

    /**
     * Transforms a direction vector from world to local space.
     */
    @Nonnull
    public Vec3 worldDirectionToLocal(@Nonnull Vec3 worldDir) {
        Quaternionf inverseRotation = new Quaternionf(rotation).conjugate();
        Vector3f local = new Vector3f((float) worldDir.x, (float) worldDir.y, (float) worldDir.z);
        inverseRotation.transform(local);
        return new Vec3(local.x, local.y, local.z);
    }

    // ==================== Conversion Methods ====================

    /**
     * Computes an AABB that contains this entire OBB.
     * Useful for broad-phase collision checks.
     */
    @Nonnull
    public AABB toWorldAABB() {
        Vec3[] corners = getCorners();

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Vec3 corner : corners) {
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Gets all 8 corners of the OBB in world space.
     * Order: (-,-,-), (+,-,-), (-,+,-), (+,+,-), (-,-,+), (+,-,+), (-,+,+), (+,+,+)
     */
    @Nonnull
    public Vec3[] getCorners() {
        Vec3[] corners = new Vec3[8];
        double ex = halfExtents.x;
        double ey = halfExtents.y;
        double ez = halfExtents.z;

        // All 8 combinations of +/- half-extents
        int index = 0;
        for (int z = -1; z <= 1; z += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int x = -1; x <= 1; x += 2) {
                    Vec3 local = new Vec3(x * ex, y * ey, z * ez);
                    corners[index++] = localToWorld(local);
                }
            }
        }

        return corners;
    }

    /**
     * Gets the 12 edges of the OBB as pairs of corner indices.
     * Useful for wireframe rendering.
     */
    @Nonnull
    public static int[][] getEdgeIndices() {
        return new int[][]{
            // Bottom face
            {0, 1}, {2, 3}, {0, 2}, {1, 3},
            // Top face
            {4, 5}, {6, 7}, {4, 6}, {5, 7},
            // Vertical edges
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
    }

    // ==================== Accessors ====================

    @Nonnull
    public Vec3 getCenter() {
        return Objects.requireNonNull(center);
    }

    @Nonnull
    public Vec3 getHalfExtents() {
        return Objects.requireNonNull(halfExtents);
    }

    @Nonnull
    public Quaternionf getRotation() {
        return new Quaternionf(rotation); // Return copy for immutability
    }

    /**
     * Gets the full size (2 * halfExtents) along each axis.
     */
    @Nonnull
    public Vec3 getSize() {
        return Objects.requireNonNull(halfExtents.scale(2));
    }

    /**
     * Gets the volume of this OBB.
     */
    public double getVolume() {
        return 8.0 * halfExtents.x * halfExtents.y * halfExtents.z;
    }

    /**
     * Checks if this OBB has any rotation (is not axis-aligned).
     */
    public boolean isRotated() {
        // Check if rotation is approximately identity
        return Math.abs(rotation.x) > 0.0001 ||
               Math.abs(rotation.y) > 0.0001 ||
               Math.abs(rotation.z) > 0.0001 ||
               Math.abs(rotation.w - 1.0) > 0.0001;
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrientedBoundingBox that = (OrientedBoundingBox) o;
        return center.equals(that.center) &&
               halfExtents.equals(that.halfExtents) &&
               rotation.equals(that.rotation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, halfExtents, rotation.x, rotation.y, rotation.z, rotation.w);
    }

    @Override
    public String toString() {
        return String.format("OBB[center=%s, halfExtents=%s, rotation=(%.2f, %.2f, %.2f, %.2f)]",
            center, halfExtents, rotation.x, rotation.y, rotation.z, rotation.w);
    }
}
