package com.devmod.collision.obb;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OrientedBoundingBox.
 *
 * Pure math/geometry tests -- no Minecraft bootstrap required.
 */
@DisplayName("OrientedBoundingBox")
public class OrientedBoundingBoxTest {

    private static final double EPS = 1e-3;

    // ==================== Construction ====================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("constructor stores center, halfExtents, and identity rotation")
        void basicConstruction() {
            Vec3 center = new Vec3(1, 2, 3);
            Vec3 half = new Vec3(0.5, 1.0, 0.5);
            OrientedBoundingBox obb = new OrientedBoundingBox(center, half);

            assertEquals(center, obb.getCenter());
            assertEquals(half, obb.getHalfExtents());
            assertFalse(obb.isRotated(), "No rotation was applied");
        }

        @Test
        @DisplayName("constructor with rotation stores rotation")
        void rotatedConstruction() {
            Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(45));
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1), rot);

            assertTrue(obb.isRotated());
        }

        @Test
        @DisplayName("getRotation returns defensive copy")
        void rotationDefensiveCopy() {
            Quaternionf original = new Quaternionf().rotateY(1.0f);
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1), original);

            Quaternionf returned = obb.getRotation();
            returned.rotateX(2.0f); // mutate returned copy

            // Original should be unaffected
            Quaternionf fresh = obb.getRotation();
            assertEquals(original.x, fresh.x, 1e-5f);
            assertEquals(original.y, fresh.y, 1e-5f);
            assertEquals(original.z, fresh.z, 1e-5f);
            assertEquals(original.w, fresh.w, 1e-5f);
        }
    }

    // ==================== Factory methods ====================

    @Nested
    @DisplayName("Factory methods")
    class FactoryTests {

        @Test
        @DisplayName("fromAABB creates axis-aligned OBB from AABB")
        void fromAABB() {
            AABB aabb = new AABB(-1, -2, -3, 1, 2, 3);
            OrientedBoundingBox obb = OrientedBoundingBox.fromAABB(aabb);

            assertEquals(0.0, obb.getCenter().x, EPS);
            assertEquals(0.0, obb.getCenter().y, EPS);
            assertEquals(0.0, obb.getCenter().z, EPS);
            assertEquals(1.0, obb.getHalfExtents().x, EPS);
            assertEquals(2.0, obb.getHalfExtents().y, EPS);
            assertEquals(3.0, obb.getHalfExtents().z, EPS);
            assertFalse(obb.isRotated());
        }

        @Test
        @DisplayName("fromCenterAndSize creates OBB with half-size")
        void fromCenterAndSize() {
            Vec3 center = new Vec3(5, 5, 5);
            Vec3 size = new Vec3(2, 4, 6);
            OrientedBoundingBox obb = OrientedBoundingBox.fromCenterAndSize(center, size);

            assertEquals(5.0, obb.getCenter().x, EPS);
            assertEquals(1.0, obb.getHalfExtents().x, EPS);
            assertEquals(2.0, obb.getHalfExtents().y, EPS);
            assertEquals(3.0, obb.getHalfExtents().z, EPS);
        }
    }

    // ==================== Queries ====================

    @Nested
    @DisplayName("Query methods")
    class QueryTests {

        @Test
        @DisplayName("getSize returns 2 * halfExtents")
        void getSize() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 2, 3));
            Vec3 size = obb.getSize();

            assertEquals(2.0, size.x, EPS);
            assertEquals(4.0, size.y, EPS);
            assertEquals(6.0, size.z, EPS);
        }

        @Test
        @DisplayName("getVolume returns 8 * hx * hy * hz")
        void getVolume() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 2, 3));
            assertEquals(8.0 * 1 * 2 * 3, obb.getVolume(), EPS);
        }

        @Test
        @DisplayName("unit cube volume is 1.0")
        void unitCubeVolume() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(0.5, 0.5, 0.5));
            assertEquals(1.0, obb.getVolume(), EPS);
        }

        @Test
        @DisplayName("isRotated returns false for identity quaternion")
        void isRotatedFalse() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            assertFalse(obb.isRotated());
        }

        @Test
        @DisplayName("isRotated returns true for non-identity quaternion")
        void isRotatedTrue() {
            Quaternionf rot = new Quaternionf().rotateZ(0.1f);
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1), rot);
            assertTrue(obb.isRotated());
        }
    }

    // ==================== Contains ====================

    @Nested
    @DisplayName("contains()")
    class ContainsTests {

        @Test
        @DisplayName("center is inside axis-aligned OBB")
        void centerInside() {
            OrientedBoundingBox obb = new OrientedBoundingBox(new Vec3(5, 5, 5), new Vec3(1, 1, 1));
            assertTrue(obb.contains(new Vec3(5, 5, 5)));
        }

        @Test
        @DisplayName("point inside axis-aligned OBB")
        void pointInside() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            assertTrue(obb.contains(new Vec3(0.5, 0.5, 0.5)));
        }

        @Test
        @DisplayName("point on boundary is inside (<=)")
        void pointOnBoundary() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            assertTrue(obb.contains(new Vec3(1.0, 0, 0)));
        }

        @Test
        @DisplayName("point outside axis-aligned OBB")
        void pointOutside() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            assertFalse(obb.contains(new Vec3(1.5, 0, 0)));
        }

        @Test
        @DisplayName("contains works with rotated OBB")
        void rotatedContains() {
            Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(45));
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 0.5, 0.5), rot);

            // After rotateY(45), local X axis maps to world direction (cos45, 0, -sin45).
            // A point along that direction within the local X halfExtent (1.0) should be inside.
            double cos45 = Math.cos(Math.toRadians(45));
            double sin45 = Math.sin(Math.toRadians(45));
            double dist = 0.6; // within halfExtent.x = 1.0
            assertTrue(obb.contains(new Vec3(dist * cos45, 0, -dist * sin45)),
                "Point along rotated local X axis should be inside");

            // A point perpendicular to the rotated long axis, outside halfExtent.z (0.5)
            double perpDist = 0.6; // exceeds halfExtent.z = 0.5 in local Z
            assertFalse(obb.contains(new Vec3(perpDist * sin45, 0, perpDist * cos45)),
                "Point perpendicular to long axis beyond halfExtent.z should be outside");
        }
    }

    // ==================== closestPoint / distanceTo ====================

    @Nested
    @DisplayName("closestPoint() and distanceTo()")
    class ClosestPointTests {

        @Test
        @DisplayName("closest point to center is center itself")
        void closestToCenter() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            Vec3 closest = obb.closestPoint(Vec3.ZERO);

            assertEquals(0.0, closest.x, EPS);
            assertEquals(0.0, closest.y, EPS);
            assertEquals(0.0, closest.z, EPS);
        }

        @Test
        @DisplayName("closest point to point inside is the point itself")
        void closestToInside() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            Vec3 inside = new Vec3(0.3, -0.5, 0.8);
            Vec3 closest = obb.closestPoint(inside);

            assertEquals(0.3, closest.x, EPS);
            assertEquals(-0.5, closest.y, EPS);
            assertEquals(0.8, closest.z, EPS);
        }

        @Test
        @DisplayName("closest point to point outside is clamped to face")
        void closestToOutside() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            Vec3 outside = new Vec3(5, 0, 0);
            Vec3 closest = obb.closestPoint(outside);

            assertEquals(1.0, closest.x, EPS, "Clamped to +X face");
            assertEquals(0.0, closest.y, EPS);
            assertEquals(0.0, closest.z, EPS);
        }

        @Test
        @DisplayName("closest point to corner outside is clamped to corner")
        void closestToCorner() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            Vec3 outside = new Vec3(5, 5, 5);
            Vec3 closest = obb.closestPoint(outside);

            assertEquals(1.0, closest.x, EPS);
            assertEquals(1.0, closest.y, EPS);
            assertEquals(1.0, closest.z, EPS);
        }

        @Test
        @DisplayName("distanceTo for point inside is 0")
        void distanceToInside() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            assertEquals(0.0, obb.distanceTo(new Vec3(0.5, 0, 0)), EPS);
        }

        @Test
        @DisplayName("distanceTo for point outside is correct")
        void distanceToOutside() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            // Point at (3, 0, 0) -- closest surface point at (1, 0, 0) -- distance = 2
            assertEquals(2.0, obb.distanceTo(new Vec3(3, 0, 0)), EPS);
        }

        @Test
        @DisplayName("distanceToSq returns squared distance")
        void distanceToSq() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            assertEquals(4.0, obb.distanceToSq(new Vec3(3, 0, 0)), EPS);
        }
    }

    // ==================== World/Local transforms ====================

    @Nested
    @DisplayName("worldToLocal() and localToWorld()")
    class TransformTests {

        @Test
        @DisplayName("worldToLocal and localToWorld are inverses for axis-aligned OBB")
        void axisAlignedRoundtrip() {
            OrientedBoundingBox obb = new OrientedBoundingBox(new Vec3(10, 20, 30), new Vec3(1, 1, 1));
            Vec3 worldPoint = new Vec3(12, 22, 32);

            Vec3 local = obb.worldToLocal(worldPoint);
            Vec3 backToWorld = obb.localToWorld(local);

            assertEquals(worldPoint.x, backToWorld.x, EPS);
            assertEquals(worldPoint.y, backToWorld.y, EPS);
            assertEquals(worldPoint.z, backToWorld.z, EPS);
        }

        @Test
        @DisplayName("worldToLocal and localToWorld are inverses for rotated OBB")
        void rotatedRoundtrip() {
            Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(37));
            OrientedBoundingBox obb = new OrientedBoundingBox(new Vec3(5, 10, 15), new Vec3(1, 1, 1), rot);
            Vec3 worldPoint = new Vec3(7, 11, 16);

            Vec3 local = obb.worldToLocal(worldPoint);
            Vec3 backToWorld = obb.localToWorld(local);

            assertEquals(worldPoint.x, backToWorld.x, EPS);
            assertEquals(worldPoint.y, backToWorld.y, EPS);
            assertEquals(worldPoint.z, backToWorld.z, EPS);
        }

        @Test
        @DisplayName("worldToLocal of center returns origin")
        void centerToLocal() {
            OrientedBoundingBox obb = new OrientedBoundingBox(new Vec3(5, 5, 5), new Vec3(1, 1, 1));
            Vec3 local = obb.worldToLocal(new Vec3(5, 5, 5));

            assertEquals(0.0, local.x, EPS);
            assertEquals(0.0, local.y, EPS);
            assertEquals(0.0, local.z, EPS);
        }

        @Test
        @DisplayName("localToWorld of origin returns center")
        void originToWorld() {
            Vec3 center = new Vec3(5, 5, 5);
            OrientedBoundingBox obb = new OrientedBoundingBox(center, new Vec3(1, 1, 1));
            Vec3 world = obb.localToWorld(Vec3.ZERO);

            assertEquals(center.x, world.x, EPS);
            assertEquals(center.y, world.y, EPS);
            assertEquals(center.z, world.z, EPS);
        }
    }

    // ==================== Direction transforms ====================

    @Nested
    @DisplayName("Direction transforms")
    class DirectionTransformTests {

        @Test
        @DisplayName("localDirectionToWorld and worldDirectionToLocal are inverses")
        void directionRoundtrip() {
            Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(60));
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1), rot);

            Vec3 worldDir = new Vec3(1, 0, 0);
            Vec3 localDir = obb.worldDirectionToLocal(worldDir);
            Vec3 backToWorld = obb.localDirectionToWorld(localDir);

            assertEquals(worldDir.x, backToWorld.x, EPS);
            assertEquals(worldDir.y, backToWorld.y, EPS);
            assertEquals(worldDir.z, backToWorld.z, EPS);
        }

        @Test
        @DisplayName("axis-aligned OBB does not rotate directions")
        void axisAlignedNoRotation() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            Vec3 dir = new Vec3(1, 0, 0);

            Vec3 local = obb.worldDirectionToLocal(dir);
            assertEquals(1.0, local.x, EPS);
            assertEquals(0.0, local.y, EPS);
            assertEquals(0.0, local.z, EPS);
        }
    }

    // ==================== Axes ====================

    @Nested
    @DisplayName("getAxes()")
    class AxesTests {

        @Test
        @DisplayName("identity rotation returns standard basis vectors")
        void identityAxes() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            Vec3[] axes = obb.getAxes();

            assertEquals(3, axes.length);
            assertEquals(1.0, axes[0].x, EPS); // X axis
            assertEquals(1.0, axes[1].y, EPS); // Y axis
            assertEquals(1.0, axes[2].z, EPS); // Z axis
        }

        @Test
        @DisplayName("90-degree Y rotation swaps X and Z axes")
        void rotated90YAxes() {
            Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(90));
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1), rot);
            Vec3[] axes = obb.getAxes();

            // After 90 deg Y rotation: local X -> world -Z, local Y -> world Y, local Z -> world X
            assertEquals(0.0, axes[0].x, EPS);
            assertEquals(0.0, axes[0].y, EPS);
            assertEquals(-1.0, axes[0].z, EPS); // local X is now world -Z

            assertEquals(0.0, axes[1].x, EPS);
            assertEquals(1.0, axes[1].y, EPS);
            assertEquals(0.0, axes[1].z, EPS); // Y unchanged

            assertEquals(1.0, axes[2].x, EPS);
            assertEquals(0.0, axes[2].y, EPS);
            assertEquals(0.0, axes[2].z, EPS); // local Z is now world +X
        }

        @Test
        @DisplayName("axes are cached (same array returned)")
        void axesCached() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            Vec3[] first = obb.getAxes();
            Vec3[] second = obb.getAxes();
            assertSame(first, second, "Should return cached array");
        }
    }

    // ==================== Corners ====================

    @Nested
    @DisplayName("getCorners()")
    class CornersTests {

        @Test
        @DisplayName("axis-aligned unit cube has 8 corners at +/-0.5")
        void unitCubeCorners() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(0.5, 0.5, 0.5));
            Vec3[] corners = obb.getCorners();

            assertEquals(8, corners.length);

            // Verify all corners are at distance 0.5*sqrt(3) from center
            double expectedDist = 0.5 * Math.sqrt(3);
            for (Vec3 corner : corners) {
                double dist = corner.length();
                assertEquals(expectedDist, dist, EPS, "Corner " + corner + " should be at expected distance");
            }
        }

        @Test
        @DisplayName("offset OBB corners are translated")
        void offsetCorners() {
            Vec3 center = new Vec3(10, 20, 30);
            OrientedBoundingBox obb = new OrientedBoundingBox(center, new Vec3(1, 1, 1));
            Vec3[] corners = obb.getCorners();

            for (Vec3 corner : corners) {
                assertTrue(corner.x >= 9.0 - EPS && corner.x <= 11.0 + EPS);
                assertTrue(corner.y >= 19.0 - EPS && corner.y <= 21.0 + EPS);
                assertTrue(corner.z >= 29.0 - EPS && corner.z <= 31.0 + EPS);
            }
        }
    }

    // ==================== toWorldAABB ====================

    @Nested
    @DisplayName("toWorldAABB()")
    class WorldAABBTests {

        @Test
        @DisplayName("axis-aligned OBB produces matching AABB")
        void axisAlignedAABB() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 2, 3));
            AABB aabb = obb.toWorldAABB();

            assertEquals(-1.0, aabb.minX, EPS);
            assertEquals(-2.0, aabb.minY, EPS);
            assertEquals(-3.0, aabb.minZ, EPS);
            assertEquals(1.0, aabb.maxX, EPS);
            assertEquals(2.0, aabb.maxY, EPS);
            assertEquals(3.0, aabb.maxZ, EPS);
        }

        @Test
        @DisplayName("rotated OBB produces enlarged AABB")
        void rotatedAABBIsLarger() {
            OrientedBoundingBox axisAligned = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 0.5, 0.5));
            OrientedBoundingBox rotated = axisAligned.rotate(new Quaternionf().rotateY((float) Math.toRadians(45)));

            AABB alignedAABB = axisAligned.toWorldAABB();
            AABB rotatedAABB = rotated.toWorldAABB();

            // Rotated AABB should be at least as large in X and Z
            assertTrue(rotatedAABB.getXsize() >= alignedAABB.getXsize() - EPS);
        }
    }

    // ==================== Transform operations ====================

    @Nested
    @DisplayName("Transform operations")
    class TransformOperationTests {

        @Test
        @DisplayName("translate shifts center")
        void translate() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            OrientedBoundingBox moved = obb.translate(new Vec3(5, 10, 15));

            assertEquals(5.0, moved.getCenter().x, EPS);
            assertEquals(10.0, moved.getCenter().y, EPS);
            assertEquals(15.0, moved.getCenter().z, EPS);
            assertEquals(obb.getHalfExtents(), moved.getHalfExtents());
        }

        @Test
        @DisplayName("uniform scale multiplies halfExtents")
        void uniformScale() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 2, 3));
            OrientedBoundingBox scaled = obb.scale(2.0);

            assertEquals(2.0, scaled.getHalfExtents().x, EPS);
            assertEquals(4.0, scaled.getHalfExtents().y, EPS);
            assertEquals(6.0, scaled.getHalfExtents().z, EPS);
        }

        @Test
        @DisplayName("non-uniform scale multiplies each axis independently")
        void nonUniformScale() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            OrientedBoundingBox scaled = obb.scale(new Vec3(2, 3, 4));

            assertEquals(2.0, scaled.getHalfExtents().x, EPS);
            assertEquals(3.0, scaled.getHalfExtents().y, EPS);
            assertEquals(4.0, scaled.getHalfExtents().z, EPS);
        }

        @Test
        @DisplayName("rotate changes rotation but not center or halfExtents")
        void rotate() {
            OrientedBoundingBox obb = new OrientedBoundingBox(new Vec3(1, 2, 3), new Vec3(0.5, 0.5, 0.5));
            Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(45));
            OrientedBoundingBox rotated = obb.rotate(rot);

            assertEquals(obb.getCenter(), rotated.getCenter());
            assertEquals(obb.getHalfExtents(), rotated.getHalfExtents());
            assertTrue(rotated.isRotated());
        }

        @Test
        @DisplayName("rotateAround changes center and rotation")
        void rotateAround() {
            OrientedBoundingBox obb = new OrientedBoundingBox(new Vec3(1, 0, 0), new Vec3(0.5, 0.5, 0.5));
            Vec3 pivot = Vec3.ZERO;
            Quaternionf rot90Y = new Quaternionf().rotateY((float) Math.toRadians(90));

            OrientedBoundingBox rotated = obb.rotateAround(pivot, rot90Y);

            // After 90 deg Y rotation of (1,0,0) around origin: -> (0,0,-1)
            assertEquals(0.0, rotated.getCenter().x, EPS);
            assertEquals(0.0, rotated.getCenter().y, EPS);
            assertEquals(-1.0, rotated.getCenter().z, EPS);
            assertTrue(rotated.isRotated());
        }

        @Test
        @DisplayName("transform with translation matrix moves center")
        void transformTranslation() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(0.5, 0.5, 0.5));
            Matrix4f matrix = new Matrix4f().translate(3, 4, 5);

            OrientedBoundingBox transformed = obb.transform(matrix);

            assertEquals(3.0, transformed.getCenter().x, EPS);
            assertEquals(4.0, transformed.getCenter().y, EPS);
            assertEquals(5.0, transformed.getCenter().z, EPS);
        }

        @Test
        @DisplayName("transform with scale matrix scales halfExtents")
        void transformScale() {
            OrientedBoundingBox obb = new OrientedBoundingBox(Vec3.ZERO, new Vec3(1, 1, 1));
            Matrix4f matrix = new Matrix4f().scale(2, 3, 4);

            OrientedBoundingBox transformed = obb.transform(matrix);

            assertEquals(2.0, transformed.getHalfExtents().x, EPS);
            assertEquals(3.0, transformed.getHalfExtents().y, EPS);
            assertEquals(4.0, transformed.getHalfExtents().z, EPS);
        }
    }

    // ==================== Equality / hashCode / toString ====================

    @Nested
    @DisplayName("equals / hashCode / toString")
    class EqualityTests {

        @Test
        @DisplayName("equal OBBs are equal")
        void equalOBBs() {
            OrientedBoundingBox a = new OrientedBoundingBox(new Vec3(1, 2, 3), new Vec3(0.5, 0.5, 0.5));
            OrientedBoundingBox b = new OrientedBoundingBox(new Vec3(1, 2, 3), new Vec3(0.5, 0.5, 0.5));

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("different center makes OBBs unequal")
        void differentCenter() {
            OrientedBoundingBox a = new OrientedBoundingBox(new Vec3(1, 2, 3), new Vec3(0.5, 0.5, 0.5));
            OrientedBoundingBox b = new OrientedBoundingBox(new Vec3(4, 5, 6), new Vec3(0.5, 0.5, 0.5));

            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("toString includes center and halfExtents")
        void toStringFormat() {
            OrientedBoundingBox obb = new OrientedBoundingBox(new Vec3(1, 2, 3), new Vec3(0.5, 0.5, 0.5));
            String str = obb.toString();

            assertTrue(str.contains("OBB"));
        }
    }

    // ==================== Edge indices ====================

    @Test
    @DisplayName("getEdgeIndices returns 12 edges")
    void edgeIndices() {
        int[][] edges = OrientedBoundingBox.getEdgeIndices();
        assertEquals(12, edges.length, "A box has 12 edges");
        for (int[] edge : edges) {
            assertEquals(2, edge.length, "Each edge is a pair");
            assertTrue(edge[0] >= 0 && edge[0] < 8);
            assertTrue(edge[1] >= 0 && edge[1] < 8);
            assertNotEquals(edge[0], edge[1], "Edge endpoints should be different");
        }
    }
}
