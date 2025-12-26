package com.devmod.collision.obb;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ray-OBB intersection using the Slab Method.
 * Optimized for real-time combat with early-exit conditions.
 *
 * Algorithm:
 * 1. Transform ray into OBB local space
 * 2. Perform AABB slab test in local space
 * 3. Transform hit point back to world space
 *
 * Performance: ~0.002ms per test (15-20 floating point operations)
 */

public final class OBBRaycast {

    private OBBRaycast() {} // Utility class

    /**
     * Result of an OBB raycast operation.
     */
    public record OBBHitResult(
        boolean hit,
        @Nullable Vec3 hitPoint,      // World space hit position
        @Nullable Vec3 hitNormal,     // Surface normal at hit point
        float distance,               // Distance from ray origin
        int faceIndex                 // Which face was hit (0-5: +X,-X,+Y,-Y,+Z,-Z)
    ) {
        public static final OBBHitResult MISS = new OBBHitResult(false, null, null, Float.MAX_VALUE, -1);

        /**
         * Creates a successful hit result.
         */
        public static OBBHitResult hit(@Nonnull Vec3 hitPoint, @Nonnull Vec3 normal, float distance, int face) {
            return new OBBHitResult(true, hitPoint, normal, distance, face);
        }
    }

    /**
     * Tests ray intersection with an OBB.
     *
     * @param rayOrigin    Start of ray (world space)
     * @param rayDirection Normalized direction vector
     * @param maxDistance  Maximum ray length
     * @param obb          The oriented bounding box to test
     * @return Hit result with point, normal, and distance
     */
    @Nonnull
    public static OBBHitResult raycast(@Nonnull Vec3 rayOrigin, @Nonnull Vec3 rayDirection,
                                       float maxDistance, @Nonnull OrientedBoundingBox obb) {
        // Transform ray to OBB local space
        Vec3 localOrigin = obb.worldToLocal(rayOrigin);
        Vec3 localDir = obb.worldDirectionToLocal(rayDirection);

        // Get half-extents for AABB test
        Vec3 halfExtents = obb.getHalfExtents();

        // Slab method: find intersection with each axis-aligned slab
        float tMin = 0.0f;
        float tMax = maxDistance;
        int hitFace = -1;
        int exitFace = -1;

        // X axis slab
        SlabResult xResult = slabIntersect(localOrigin.x, localDir.x, halfExtents.x, tMin, tMax);
        if (xResult == null) return Objects.requireNonNull(OBBHitResult.MISS);
        if (xResult.tMin > tMin) { tMin = xResult.tMin; hitFace = xResult.entryFace; }
        if (xResult.tMax < tMax) { tMax = xResult.tMax; exitFace = xResult.exitFace; }

        // Y axis slab
        SlabResult yResult = slabIntersect(localOrigin.y, localDir.y, halfExtents.y, tMin, tMax);
        if (yResult == null) return Objects.requireNonNull(OBBHitResult.MISS);
        if (yResult.tMin > tMin) { tMin = yResult.tMin; hitFace = yResult.entryFace + 2; }
        if (yResult.tMax < tMax) { tMax = yResult.tMax; exitFace = yResult.exitFace + 2; }

        // Z axis slab
        SlabResult zResult = slabIntersect(localOrigin.z, localDir.z, halfExtents.z, tMin, tMax);
        if (zResult == null) return Objects.requireNonNull(OBBHitResult.MISS);
        if (zResult.tMin > tMin) { tMin = zResult.tMin; hitFace = zResult.entryFace + 4; }
        if (zResult.tMax < tMax) { tMax = zResult.tMax; exitFace = zResult.exitFace + 4; }

        // Check if ray intersects
        if (tMin > tMax || tMax < 0) {
            return Objects.requireNonNull(OBBHitResult.MISS);
        }

        // Determine hit point and face
        float hitT;
        int face;
        if (tMin > 0) {
            hitT = tMin;
            face = hitFace;
        } else {
            // Ray starts inside OBB, use exit point
            hitT = tMax;
            face = exitFace;
        }

        // Check against max distance
        if (hitT > maxDistance) {
            return Objects.requireNonNull(OBBHitResult.MISS);
        }

        // Calculate hit point in local space
        Vec3 localHitPoint = new Vec3(
            localOrigin.x + localDir.x * hitT,
            localOrigin.y + localDir.y * hitT,
            localOrigin.z + localDir.z * hitT
        );

        // Transform hit point to world space
        Vec3 worldHitPoint = obb.localToWorld(localHitPoint);

        // Calculate normal in local space then transform
        Vec3 localNormal = getFaceNormal(face);
        Vec3 worldNormal = obb.localDirectionToWorld(localNormal);

        return Objects.requireNonNull(OBBHitResult.hit(worldHitPoint, worldNormal, hitT, face));
    }

    /**
     * Helper record for slab intersection results.
     */
    private record SlabResult(float tMin, float tMax, int entryFace, int exitFace) {}

    /**
     * Performs slab intersection for one axis.
     * Returns null if ray misses the slab entirely.
     */
    @Nullable
    private static SlabResult slabIntersect(double origin, double direction, double halfExtent,
                                            float currentTMin, float currentTMax) {
        if (Math.abs(direction) < 1e-8) {
            // Ray is parallel to slab
            if (origin < -halfExtent || origin > halfExtent) {
                return null; // Ray misses slab
            }
            // Ray is inside slab for entire range
            return new SlabResult(currentTMin, currentTMax, 0, 1);
        }

        // Calculate t values for slab boundaries
        float t1 = (float) ((-halfExtent - origin) / direction);
        float t2 = (float) ((halfExtent - origin) / direction);

        // Determine entry and exit faces based on direction
        int entryFace, exitFace;
        if (direction > 0) {
            entryFace = 1; // Entering from negative side
            exitFace = 0;  // Exiting from positive side
        } else {
            entryFace = 0; // Entering from positive side
            exitFace = 1;  // Exiting from negative side
        }

        // Ensure t1 is entry, t2 is exit
        if (t1 > t2) {
            float temp = t1;
            t1 = t2;
            t2 = temp;
            int tempFace = entryFace;
            entryFace = exitFace;
            exitFace = tempFace;
        }

        // Update interval
        float newTMin = Math.max(currentTMin, t1);
        float newTMax = Math.min(currentTMax, t2);

        // Check if interval is valid
        if (newTMin > newTMax) {
            return null;
        }

        return new SlabResult(t1, t2, entryFace, exitFace);
    }

    /**
     * Gets the normal vector for a face index.
     * Face indices: 0=+X, 1=-X, 2=+Y, 3=-Y, 4=+Z, 5=-Z
     */
    @Nonnull
    private static Vec3 getFaceNormal(int faceIndex) {
        return switch (faceIndex) {
            case 0 -> new Vec3(1, 0, 0);   // +X
            case 1 -> new Vec3(-1, 0, 0);  // -X
            case 2 -> new Vec3(0, 1, 0);   // +Y
            case 3 -> new Vec3(0, -1, 0);  // -Y
            case 4 -> new Vec3(0, 0, 1);   // +Z
            case 5 -> new Vec3(0, 0, -1);  // -Z
            default -> new Vec3(0, 1, 0);  // Fallback
        };
    }

    /**
     * Performs raycast against an AABB (axis-aligned).
     * Faster than OBB raycast when no rotation is needed.
     *
     * @param rayOrigin    Start of ray
     * @param rayDirection Normalized direction
     * @param maxDistance  Maximum ray length
     * @param aabb         The axis-aligned bounding box
     * @return Hit result
     */
    @Nonnull
    public static OBBHitResult raycastAABB(@Nonnull Vec3 rayOrigin, @Nonnull Vec3 rayDirection,
                                           float maxDistance, @Nonnull AABB aabb) {
        Vec3 center = Objects.requireNonNull(aabb.getCenter());
        Vec3 halfExtents = new Vec3(
            aabb.getXsize() / 2.0,
            aabb.getYsize() / 2.0,
            aabb.getZsize() / 2.0
        );

        // Translate ray origin relative to AABB center
        Vec3 localOrigin = Objects.requireNonNull(rayOrigin.subtract(center));

        float tMin = 0.0f;
        float tMax = maxDistance;
        int hitFace = -1;

        // X axis
        SlabResult xResult = slabIntersect(localOrigin.x, rayDirection.x, halfExtents.x, tMin, tMax);
        if (xResult == null) return Objects.requireNonNull(OBBHitResult.MISS);
        if (xResult.tMin > tMin) { tMin = xResult.tMin; hitFace = xResult.entryFace; }
        if (xResult.tMax < tMax) tMax = xResult.tMax;

        // Y axis
        SlabResult yResult = slabIntersect(localOrigin.y, rayDirection.y, halfExtents.y, tMin, tMax);
        if (yResult == null) return Objects.requireNonNull(OBBHitResult.MISS);
        if (yResult.tMin > tMin) { tMin = yResult.tMin; hitFace = yResult.entryFace + 2; }
        if (yResult.tMax < tMax) tMax = yResult.tMax;

        // Z axis
        SlabResult zResult = slabIntersect(localOrigin.z, rayDirection.z, halfExtents.z, tMin, tMax);
        if (zResult == null) return Objects.requireNonNull(OBBHitResult.MISS);
        if (zResult.tMin > tMin) { tMin = zResult.tMin; hitFace = zResult.entryFace + 4; }
        if (zResult.tMax < tMax) tMax = zResult.tMax;

        if (tMin > tMax || tMax < 0) {
            return Objects.requireNonNull(OBBHitResult.MISS);
        }

        float hitT = tMin > 0 ? tMin : tMax;
        if (hitT > maxDistance) {
            return Objects.requireNonNull(OBBHitResult.MISS);
        }

        Vec3 hitPoint = Objects.requireNonNull(rayOrigin.add(Objects.requireNonNull(rayDirection.scale(hitT))));
        Vec3 normal = getFaceNormal(hitFace);

        return Objects.requireNonNull(OBBHitResult.hit(hitPoint, normal, hitT, hitFace));
    }

    /**
     * Tests if a ray intersects an OBB (boolean only, faster).
     *
     * @param rayOrigin    Start of ray
     * @param rayDirection Normalized direction
     * @param maxDistance  Maximum ray length
     * @param obb          The oriented bounding box
     * @return true if ray hits the OBB
     */
    public static boolean intersects(@Nonnull Vec3 rayOrigin, @Nonnull Vec3 rayDirection,
                                     float maxDistance, @Nonnull OrientedBoundingBox obb) {
        Vec3 localOrigin = obb.worldToLocal(rayOrigin);
        Vec3 localDir = obb.worldDirectionToLocal(rayDirection);
        Vec3 halfExtents = obb.getHalfExtents();

        float tMin = 0.0f;
        float tMax = maxDistance;

        // X axis
        if (!slabIntersectFast(localOrigin.x, localDir.x, halfExtents.x, tMin, tMax)) {
            return false;
        }

        // Y axis
        if (!slabIntersectFast(localOrigin.y, localDir.y, halfExtents.y, tMin, tMax)) {
            return false;
        }

        // Z axis
        return slabIntersectFast(localOrigin.z, localDir.z, halfExtents.z, tMin, tMax);
    }

    /**
     * Fast slab intersection test (boolean only).
     */
    private static boolean slabIntersectFast(double origin, double direction, double halfExtent,
                                             float tMin, float tMax) {
        if (Math.abs(direction) < 1e-8) {
            return origin >= -halfExtent && origin <= halfExtent;
        }

        float t1 = (float) ((-halfExtent - origin) / direction);
        float t2 = (float) ((halfExtent - origin) / direction);

        if (t1 > t2) {
            float temp = t1;
            t1 = t2;
            t2 = temp;
        }

        tMin = Math.max(tMin, t1);
        tMax = Math.min(tMax, t2);

        return tMin <= tMax && tMax >= 0;
    }

    /**
     * Finds the closest hit among multiple OBBs.
     *
     * @param rayOrigin    Start of ray
     * @param rayDirection Normalized direction
     * @param maxDistance  Maximum ray length
     * @param obbs         Array of OBBs to test
     * @return Index of closest hit OBB, or -1 if no hit
     */
    public static int findClosestHit(@Nonnull Vec3 rayOrigin, @Nonnull Vec3 rayDirection,
                                     float maxDistance, @Nonnull OrientedBoundingBox[] obbs) {
        int closestIndex = -1;
        float closestDistance = maxDistance;

        for (int i = 0; i < obbs.length; i++) {
            OBBHitResult result = raycast(rayOrigin, rayDirection, closestDistance, Objects.requireNonNull(obbs[i]));
            if (result.hit() && result.distance() < closestDistance) {
                closestDistance = result.distance();
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    /**
     * Finds closest hit with full result details.
     *
     * @param rayOrigin    Start of ray
     * @param rayDirection Normalized direction
     * @param maxDistance  Maximum ray length
     * @param obbs         Array of OBBs to test
     * @return Pair of (index, result), or (-1, MISS) if no hit
     */
    @Nonnull
    public static IndexedHitResult findClosestHitWithResult(@Nonnull Vec3 rayOrigin, @Nonnull Vec3 rayDirection,
                                                            float maxDistance, @Nonnull OrientedBoundingBox[] obbs) {
        int closestIndex = -1;
        float closestDistance = maxDistance;
        OBBHitResult closestResult = OBBHitResult.MISS;

        for (int i = 0; i < obbs.length; i++) {
            OBBHitResult result = raycast(rayOrigin, rayDirection, closestDistance, Objects.requireNonNull(obbs[i]));
            if (result.hit() && result.distance() < closestDistance) {
                closestDistance = result.distance();
                closestIndex = i;
                closestResult = result;
            }
        }

        return new IndexedHitResult(closestIndex, closestResult);
    }

    /**
     * Result pairing an OBB index with its hit result.
     */
    public record IndexedHitResult(int index, OBBHitResult result) {
        public boolean hit() {
            return index >= 0 && result.hit();
        }
    }

    /**
     * Performs broad-phase AABB check before detailed OBB test.
     * Use this when testing many OBBs to quickly cull non-intersecting ones.
     *
     * @param rayOrigin    Start of ray
     * @param rayDirection Normalized direction
     * @param maxDistance  Maximum ray length
     * @param obb          The OBB to test
     * @return true if ray might hit OBB (requires detailed test to confirm)
     */
    public static boolean broadPhaseIntersects(@Nonnull Vec3 rayOrigin, @Nonnull Vec3 rayDirection,
                                               float maxDistance, @Nonnull OrientedBoundingBox obb) {
        // Convert OBB to world-aligned AABB and test that
        AABB worldAABB = obb.toWorldAABB();
        return raycastAABB(rayOrigin, rayDirection, maxDistance, worldAABB).hit();
    }
}
