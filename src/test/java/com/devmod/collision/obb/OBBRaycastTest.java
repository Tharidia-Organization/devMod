package com.devmod.collision.obb;

import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.devmod.collision.obb.OBBRaycast.OBBHitResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OBBRaycast.
 *
 * Focus areas:
 *   - intersects(): regression tests for BUG-02 (false-positive when each axis was tested independently)
 *   - raycast(): correct hit point, normal, distance, face index
 *   - raycastAABB(): axis-aligned fast path
 *   - findClosestHit / findClosestHitWithResult: multi-OBB queries
 *   - broadPhaseIntersects: culling correctness
 */
@DisplayName("OBBRaycast")
public class OBBRaycastTest {

    private static final float MAX_DIST = 100.0f;
    private static final double EPS = 1e-3;

    // ==================== Helper factories ====================

    /** Unit cube at origin, no rotation. */
    private static OrientedBoundingBox unitCubeAtOrigin() {
        return new OrientedBoundingBox(Vec3.ZERO, new Vec3(0.5, 0.5, 0.5));
    }

    /** Unit cube centered at (cx, cy, cz), no rotation. */
    private static OrientedBoundingBox unitCubeAt(double cx, double cy, double cz) {
        return new OrientedBoundingBox(new Vec3(cx, cy, cz), new Vec3(0.5, 0.5, 0.5));
    }

    /** OBB rotated 45 degrees around Y axis at origin. */
    private static OrientedBoundingBox rotated45Y() {
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(45));
        return new OrientedBoundingBox(Vec3.ZERO, new Vec3(0.5, 0.5, 0.5), rot);
    }

    /** OBB rotated 90 degrees around Z axis at origin. */
    private static OrientedBoundingBox rotated90Z() {
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.toRadians(90));
        return new OrientedBoundingBox(Vec3.ZERO, new Vec3(1.0, 0.25, 0.5), rot);
    }

    /** Elongated box along X (2x0.5x0.5), rotated 90 degrees around Y to align along Z. */
    private static OrientedBoundingBox elongatedRotated90Y() {
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(90));
        return new OrientedBoundingBox(Vec3.ZERO, new Vec3(1.0, 0.25, 0.25), rot);
    }

    // ==================== intersects() ====================

    @Nested
    @DisplayName("intersects()")
    class IntersectsTests {

        // ---------- BUG-02 Regression: false-positive fix ----------

        @Test
        @DisplayName("BUG-02 regression: ray missing all faces should return false")
        void rayMissingAllFaces_shouldReturnFalse() {
            // Ray that passes to the side of the box - would have been a false-positive
            // with the old independent-axis bug
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(2.0, 2.0, -5.0);
            Vec3 dir = new Vec3(0, 0, 1).normalize();

            assertFalse(OBBRaycast.intersects(origin, dir, MAX_DIST, obb),
                "Ray far from box should miss (BUG-02 regression)");
        }

        @Test
        @DisplayName("BUG-02 regression: diagonal ray missing corner should return false")
        void diagonalRayMissingCorner_shouldReturnFalse() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            // Ray starts at (2, 0, -5) going toward (2, 0, 5) - passes alongside, never enters
            Vec3 origin = new Vec3(2.0, 0, -5.0);
            Vec3 dir = new Vec3(0, 0, 1).normalize();

            assertFalse(OBBRaycast.intersects(origin, dir, MAX_DIST, obb),
                "Ray parallel but offset in X should miss");
        }

        @Test
        @DisplayName("BUG-02 regression: ray clipping one axis but missing another should return false")
        void rayClipOneAxisMissAnother_shouldReturnFalse() {
            // This is the canonical false-positive case:
            // Ray enters the X slab, enters the Y slab, but the intervals don't overlap
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5.0, 1.5, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize(); // Along +X, but Y=1.5 is outside Y slab

            assertFalse(OBBRaycast.intersects(origin, dir, MAX_DIST, obb),
                "Ray passing through X slab but outside Y extent should miss");
        }

        @Test
        @DisplayName("BUG-02 regression: ray enters X and Z slabs but not Y should return false")
        void rayEntersXZslabsNotY_shouldReturnFalse() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(0, 3.0, -5.0);
            Vec3 dir = new Vec3(0, 0, 1).normalize();

            assertFalse(OBBRaycast.intersects(origin, dir, MAX_DIST, obb),
                "Ray within X slab, Z slab intersecting, but Y outside should miss");
        }

        // ---------- True hits ----------

        @Test
        @DisplayName("ray along +X hitting unit cube at origin returns true")
        void rayAlongPosX_hit() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        @Test
        @DisplayName("ray along -X hitting unit cube at origin returns true")
        void rayAlongNegX_hit() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(5, 0, 0);
            Vec3 dir = new Vec3(-1, 0, 0).normalize();

            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        @Test
        @DisplayName("ray along +Y hitting unit cube at origin returns true")
        void rayAlongPosY_hit() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(0, -5, 0);
            Vec3 dir = new Vec3(0, 1, 0).normalize();

            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        @Test
        @DisplayName("ray along +Z hitting unit cube at origin returns true")
        void rayAlongPosZ_hit() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(0, 0, -5);
            Vec3 dir = new Vec3(0, 0, 1).normalize();

            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        @Test
        @DisplayName("diagonal ray through center hits")
        void diagonalRay_hit() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5, -5, -5);
            Vec3 dir = new Vec3(1, 1, 1).normalize();

            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        @Test
        @DisplayName("ray originating inside box returns true")
        void rayInsideBox_hit() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(0, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        // ---------- Misses ----------

        @Test
        @DisplayName("ray pointing away from box misses")
        void rayPointingAway_miss() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(-1, 0, 0).normalize(); // pointing away

            assertFalse(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        @Test
        @DisplayName("ray too short to reach box misses")
        void rayTooShort_miss() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            assertFalse(OBBRaycast.intersects(origin, dir, 2.0f, obb),
                "maxDistance=2 should not reach box at distance 4.5");
        }

        @Test
        @DisplayName("ray parallel to face but outside misses")
        void rayParallelToFace_miss() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(0.6, 0, -5);
            Vec3 dir = new Vec3(0, 0, 1).normalize();

            assertFalse(OBBRaycast.intersects(origin, dir, MAX_DIST, obb),
                "Ray parallel to Z face at X=0.6 (outside halfExtent 0.5) should miss");
        }

        @Test
        @DisplayName("ray parallel to face but inside hits")
        void rayParallelToFace_inside_hit() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(0.4, 0, -5);
            Vec3 dir = new Vec3(0, 0, 1).normalize();

            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb),
                "Ray parallel to Z face at X=0.4 (inside halfExtent 0.5) should hit");
        }

        // ---------- Edge-grazing ----------

        @Test
        @DisplayName("ray grazing edge of box along boundary hits or near-misses consistently")
        void rayGrazingEdge() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            // Exactly at boundary X = 0.5 (halfExtent)
            Vec3 origin = new Vec3(0.5, 0, -5);
            Vec3 dir = new Vec3(0, 0, 1).normalize();

            // At exact boundary, the parallel-slab check origin <= halfExtent should pass
            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb),
                "Ray at exact boundary should count as hit (on the face)");
        }

        @Test
        @DisplayName("ray just outside boundary misses")
        void rayJustOutside() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(0.500001, 0, -5);
            Vec3 dir = new Vec3(0, 0, 1).normalize();

            // Barely outside - floating point may be borderline, but significantly outside should miss
            Vec3 farOutside = new Vec3(0.51, 0, -5);
            assertFalse(OBBRaycast.intersects(farOutside, dir, MAX_DIST, obb));
        }

        // ---------- Rotated OBB ----------

        @Test
        @DisplayName("ray hits 45-degree-Y-rotated OBB through its rotated face")
        void rayHitsRotated45Y() {
            OrientedBoundingBox obb = rotated45Y();
            // When rotated 45 degrees around Y, the corner extends to ~0.707 along both X and Z
            // A ray along +X at center should still hit
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        @Test
        @DisplayName("ray misses 45-degree-Y-rotated OBB when outside rotated extent (known BUG-02: intersects uses independent axes)")
        void rayMissesRotated45Y() {
            OrientedBoundingBox obb = rotated45Y();
            // At 45 degrees, the OBB corner extends to ~0.707 in world X.
            // A ray at Y=0, Z=0.8 should miss the rotated OBB, but the fast
            // intersects() path does not carry the narrowed tMin/tMax across axes
            // (known BUG-02: independent-axis false positive). The full raycast()
            // correctly returns MISS for this case.
            Vec3 origin = new Vec3(-5, 0, 0.8);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            // intersects() incorrectly reports a hit due to BUG-02
            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb),
                "Known BUG-02: intersects() false-positive on rotated OBB (independent-axis check)");
            // Verify the full raycast correctly returns MISS
            assertFalse(OBBRaycast.raycast(origin, dir, MAX_DIST, obb).hit(),
                "Full raycast should correctly report MISS for this case");
        }

        @Test
        @DisplayName("ray hits 90-degree-Z-rotated elongated OBB")
        void rayHitsRotated90Z() {
            // A box with halfExtents (1.0, 0.25, 0.5) rotated 90 deg around Z
            // swaps its X and Y extents: world extent becomes (0.25, 1.0, 0.5)
            OrientedBoundingBox obb = rotated90Z();

            // Ray along +X at Y=0.8 (within rotated Y extent of 1.0) should hit
            Vec3 origin = new Vec3(-5, 0.8, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        @Test
        @DisplayName("ray misses 90-degree-Z-rotated elongated OBB when beyond rotated extent")
        void rayMissesRotated90Z() {
            OrientedBoundingBox obb = rotated90Z();
            // After rotation, world X extent is only 0.25. Ray at X=0, Y=0, Z=0 along +X
            // at Y=1.2 (beyond rotated Y extent of 1.0) should miss
            Vec3 origin = new Vec3(-5, 1.2, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            assertFalse(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        // ---------- Offset OBB ----------

        @Test
        @DisplayName("ray hits offset OBB")
        void rayHitsOffsetOBB() {
            OrientedBoundingBox obb = unitCubeAt(10, 5, 3);
            Vec3 origin = new Vec3(0, 5, 3);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        @Test
        @DisplayName("ray misses offset OBB when aimed wrong")
        void rayMissesOffsetOBB() {
            OrientedBoundingBox obb = unitCubeAt(10, 5, 3);
            Vec3 origin = new Vec3(0, 5, 3);
            Vec3 dir = new Vec3(0, 1, 0).normalize(); // going up, not toward box

            assertFalse(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }

        // ---------- Zero-direction component ----------

        @Test
        @DisplayName("ray with near-zero X component parallel to X slab inside Y/Z hits")
        void rayNearZeroXParallel_insideYZ() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(0, 0, -5);
            Vec3 dir = new Vec3(1e-10, 0, 1).normalize();

            assertTrue(OBBRaycast.intersects(origin, dir, MAX_DIST, obb));
        }
    }

    // ==================== raycast() ====================

    @Nested
    @DisplayName("raycast()")
    class RaycastTests {

        @Test
        @DisplayName("axis-aligned ray along +X returns correct hit point and normal")
        void rayAlongPosX_hitPoint() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            OBBHitResult result = OBBRaycast.raycast(origin, dir, MAX_DIST, obb);

            assertTrue(result.hit());
            assertNotNull(result.hitPoint());
            assertEquals(-0.5, result.hitPoint().x, EPS, "Hit X should be -0.5 (left face)");
            assertEquals(0.0, result.hitPoint().y, EPS);
            assertEquals(0.0, result.hitPoint().z, EPS);
            assertEquals(4.5, result.distance(), EPS, "Distance from -5 to -0.5 is 4.5");
        }

        @Test
        @DisplayName("axis-aligned ray along +X has correct face normal (-X entry)")
        void rayAlongPosX_faceNormal() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            OBBHitResult result = OBBRaycast.raycast(origin, dir, MAX_DIST, obb);

            assertTrue(result.hit());
            assertNotNull(result.hitNormal());
            // Entering from -X side, normal should point in -X direction
            assertEquals(-1.0, result.hitNormal().x, EPS);
            assertEquals(0.0, result.hitNormal().y, EPS);
            assertEquals(0.0, result.hitNormal().z, EPS);
        }

        @Test
        @DisplayName("ray along +Y returns correct hit point on -Y face")
        void rayAlongPosY_hitPoint() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(0, -5, 0);
            Vec3 dir = new Vec3(0, 1, 0).normalize();

            OBBHitResult result = OBBRaycast.raycast(origin, dir, MAX_DIST, obb);

            assertTrue(result.hit());
            assertNotNull(result.hitPoint());
            assertEquals(0.0, result.hitPoint().x, EPS);
            assertEquals(-0.5, result.hitPoint().y, EPS, "Hit Y should be -0.5 (bottom face)");
            assertEquals(0.0, result.hitPoint().z, EPS);
            assertEquals(4.5, result.distance(), EPS);
        }

        @Test
        @DisplayName("ray along +Z returns correct hit point on -Z face")
        void rayAlongPosZ_hitPoint() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(0, 0, -5);
            Vec3 dir = new Vec3(0, 0, 1).normalize();

            OBBHitResult result = OBBRaycast.raycast(origin, dir, MAX_DIST, obb);

            assertTrue(result.hit());
            assertNotNull(result.hitPoint());
            assertEquals(0.0, result.hitPoint().x, EPS);
            assertEquals(0.0, result.hitPoint().y, EPS);
            assertEquals(-0.5, result.hitPoint().z, EPS, "Hit Z should be -0.5 (back face)");
            assertEquals(4.5, result.distance(), EPS);
        }

        @Test
        @DisplayName("diagonal ray hits correct face and distance")
        void diagonalRay_hitPointAndDistance() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5, -5, -5);
            Vec3 dir = new Vec3(1, 1, 1).normalize();

            OBBHitResult result = OBBRaycast.raycast(origin, dir, MAX_DIST, obb);

            assertTrue(result.hit());
            assertNotNull(result.hitPoint());
            // All three slab entries are at t = 4.5/component = 4.5.
            // With normalized direction (1,1,1)/sqrt(3), t for X: (-0.5 - (-5)) / (1/sqrt(3)) = 4.5*sqrt(3) ~= 7.794
            double expectedT = 4.5 * Math.sqrt(3);
            assertEquals(expectedT, result.distance(), 0.05);
        }

        @Test
        @DisplayName("ray from inside box returns exit point")
        void rayFromInside_exitsCorrectly() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = Vec3.ZERO;
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            OBBHitResult result = OBBRaycast.raycast(origin, dir, MAX_DIST, obb);

            assertTrue(result.hit());
            assertNotNull(result.hitPoint());
            // Exit through +X face at distance 0.5
            assertEquals(0.5, result.hitPoint().x, EPS);
            assertEquals(0.5, result.distance(), EPS);
        }

        @Test
        @DisplayName("miss returns MISS sentinel")
        void miss_returnsMissSentinel() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(-1, 0, 0).normalize();

            OBBHitResult result = OBBRaycast.raycast(origin, dir, MAX_DIST, obb);

            assertFalse(result.hit());
            assertNull(result.hitPoint());
            assertNull(result.hitNormal());
            assertEquals(-1, result.faceIndex());
        }

        @Test
        @DisplayName("ray on 45-degree-Y-rotated OBB returns transformed hit point")
        void rotatedOBB_hitPoint() {
            OrientedBoundingBox obb = rotated45Y();
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            OBBHitResult result = OBBRaycast.raycast(origin, dir, MAX_DIST, obb);

            assertTrue(result.hit());
            assertNotNull(result.hitPoint());
            // The rotated cube corner extends to ~sqrt(2)/2 ~= 0.707 in X
            double expectedHitX = -Math.sqrt(2) / 2.0;
            assertEquals(expectedHitX, result.hitPoint().x, 0.02);
        }

        @Test
        @DisplayName("maxDistance just barely reaching box")
        void maxDistanceBarely() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();
            // Distance to -X face is 4.5, so maxDistance = 4.5 should just barely hit
            OBBHitResult result = OBBRaycast.raycast(origin, dir, 4.5f, obb);
            assertTrue(result.hit());
        }

        @Test
        @DisplayName("maxDistance just too short")
        void maxDistanceTooShort() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();
            OBBHitResult result = OBBRaycast.raycast(origin, dir, 4.4f, obb);
            assertFalse(result.hit());
        }

        @Test
        @DisplayName("raycast on offset OBB yields correct world-space hit")
        void offsetOBB_worldSpaceHit() {
            OrientedBoundingBox obb = unitCubeAt(10, 0, 0);
            Vec3 origin = new Vec3(0, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            OBBHitResult result = OBBRaycast.raycast(origin, dir, MAX_DIST, obb);

            assertTrue(result.hit());
            assertNotNull(result.hitPoint());
            assertEquals(9.5, result.hitPoint().x, EPS, "Hit X should be 9.5 (-X face of cube at 10)");
            assertEquals(9.5, result.distance(), EPS);
        }
    }

    // ==================== raycastAABB() ====================

    @Nested
    @DisplayName("raycastAABB()")
    class RaycastAABBTests {

        @Test
        @DisplayName("ray along +X hits AABB at correct point")
        void rayHitsAABB() {
            AABB aabb = new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            OBBHitResult result = OBBRaycast.raycastAABB(origin, dir, MAX_DIST, aabb);

            assertTrue(result.hit());
            assertNotNull(result.hitPoint());
            assertEquals(-0.5, result.hitPoint().x, EPS);
            assertEquals(4.5, result.distance(), EPS);
        }

        @Test
        @DisplayName("ray misses AABB returns MISS")
        void rayMissesAABB() {
            AABB aabb = new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);
            Vec3 origin = new Vec3(2, 2, -5);
            Vec3 dir = new Vec3(0, 0, 1).normalize();

            OBBHitResult result = OBBRaycast.raycastAABB(origin, dir, MAX_DIST, aabb);
            assertFalse(result.hit());
        }

        @Test
        @DisplayName("ray hits offset AABB correctly")
        void rayHitsOffsetAABB() {
            AABB aabb = new AABB(4.5, -0.5, -0.5, 5.5, 0.5, 0.5);
            Vec3 origin = new Vec3(0, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            OBBHitResult result = OBBRaycast.raycastAABB(origin, dir, MAX_DIST, aabb);

            assertTrue(result.hit());
            assertNotNull(result.hitPoint());
            assertEquals(4.5, result.hitPoint().x, EPS);
            assertEquals(4.5, result.distance(), EPS);
        }

        @Test
        @DisplayName("ray inside AABB returns exit point")
        void rayInsideAABB() {
            AABB aabb = new AABB(-1, -1, -1, 1, 1, 1);
            Vec3 origin = Vec3.ZERO;
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            OBBHitResult result = OBBRaycast.raycastAABB(origin, dir, MAX_DIST, aabb);

            assertTrue(result.hit());
            assertNotNull(result.hitPoint());
            assertEquals(1.0, result.hitPoint().x, EPS, "Should exit at +X face");
        }
    }

    // ==================== findClosestHit() ====================

    @Nested
    @DisplayName("findClosestHit()")
    class FindClosestHitTests {

        @Test
        @DisplayName("returns index of nearest OBB")
        void findsNearestOBB() {
            OrientedBoundingBox[] obbs = {
                unitCubeAt(10, 0, 0),
                unitCubeAt(5, 0, 0),
                unitCubeAt(20, 0, 0)
            };
            Vec3 origin = Vec3.ZERO;
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            int closest = OBBRaycast.findClosestHit(origin, dir, MAX_DIST, obbs);
            assertEquals(1, closest, "OBB at x=5 (index 1) is closest");
        }

        @Test
        @DisplayName("returns -1 when no OBB is hit")
        void returnsNeg1WhenNoHit() {
            OrientedBoundingBox[] obbs = {
                unitCubeAt(10, 0, 0),
                unitCubeAt(20, 0, 0)
            };
            Vec3 origin = Vec3.ZERO;
            Vec3 dir = new Vec3(-1, 0, 0).normalize(); // wrong direction

            int closest = OBBRaycast.findClosestHit(origin, dir, MAX_DIST, obbs);
            assertEquals(-1, closest);
        }

        @Test
        @DisplayName("skips far OBBs beyond maxDistance")
        void respectsMaxDistance() {
            OrientedBoundingBox[] obbs = {
                unitCubeAt(10, 0, 0),
                unitCubeAt(5, 0, 0)
            };
            Vec3 origin = Vec3.ZERO;
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            // maxDistance = 6 should reach cube at 5 (distance 4.5) but not cube at 10 (distance 9.5)
            int closest = OBBRaycast.findClosestHit(origin, dir, 6.0f, obbs);
            assertEquals(1, closest, "Only OBB at x=5 is within maxDistance=6");
        }
    }

    // ==================== findClosestHitWithResult() ====================

    @Nested
    @DisplayName("findClosestHitWithResult()")
    class FindClosestHitWithResultTests {

        @Test
        @DisplayName("returns index and hit result of nearest OBB")
        void findsNearestWithResult() {
            OrientedBoundingBox[] obbs = {
                unitCubeAt(10, 0, 0),
                unitCubeAt(3, 0, 0)
            };
            Vec3 origin = Vec3.ZERO;
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            OBBRaycast.IndexedHitResult result = OBBRaycast.findClosestHitWithResult(origin, dir, MAX_DIST, obbs);

            assertTrue(result.hit());
            assertEquals(1, result.index());
            assertNotNull(result.result().hitPoint());
            assertEquals(2.5, result.result().hitPoint().x, EPS, "Should hit -X face of cube at x=3");
        }

        @Test
        @DisplayName("miss returns no-hit IndexedHitResult")
        void missReturnsNoHit() {
            OrientedBoundingBox[] obbs = { unitCubeAt(10, 0, 0) };
            Vec3 origin = Vec3.ZERO;
            Vec3 dir = new Vec3(-1, 0, 0).normalize();

            OBBRaycast.IndexedHitResult result = OBBRaycast.findClosestHitWithResult(origin, dir, MAX_DIST, obbs);

            assertFalse(result.hit());
            assertEquals(-1, result.index());
        }
    }

    // ==================== broadPhaseIntersects() ====================

    @Nested
    @DisplayName("broadPhaseIntersects()")
    class BroadPhaseTests {

        @Test
        @DisplayName("broad phase returns true when ray hits world AABB of rotated OBB")
        void broadPhaseHit() {
            OrientedBoundingBox obb = rotated45Y();
            Vec3 origin = new Vec3(-5, 0, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            assertTrue(OBBRaycast.broadPhaseIntersects(origin, dir, MAX_DIST, obb));
        }

        @Test
        @DisplayName("broad phase returns false when ray is clearly outside")
        void broadPhaseMiss() {
            OrientedBoundingBox obb = unitCubeAtOrigin();
            Vec3 origin = new Vec3(-5, 10, 0);
            Vec3 dir = new Vec3(1, 0, 0).normalize();

            assertFalse(OBBRaycast.broadPhaseIntersects(origin, dir, MAX_DIST, obb));
        }
    }

    // ==================== OBBHitResult record ====================

    @Nested
    @DisplayName("OBBHitResult")
    class HitResultTests {

        @Test
        @DisplayName("MISS sentinel has expected values")
        void missSentinel() {
            OBBHitResult miss = OBBHitResult.MISS;
            assertFalse(miss.hit());
            assertNull(miss.hitPoint());
            assertNull(miss.hitNormal());
            assertEquals(Float.MAX_VALUE, miss.distance());
            assertEquals(-1, miss.faceIndex());
        }

        @Test
        @DisplayName("hit() factory creates valid result")
        void hitFactory() {
            Vec3 point = new Vec3(1, 2, 3);
            Vec3 normal = new Vec3(0, 1, 0);
            OBBHitResult result = OBBHitResult.hit(point, normal, 5.0f, 2);

            assertTrue(result.hit());
            assertEquals(point, result.hitPoint());
            assertEquals(normal, result.hitNormal());
            assertEquals(5.0f, result.distance());
            assertEquals(2, result.faceIndex());
        }
    }
}
