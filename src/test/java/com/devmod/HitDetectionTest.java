package com.devmod;

import javax.annotation.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

public class HitDetectionTest {

    /**
     * Mock AABB (Axis-Aligned Bounding Box) for testing
     */
    static class MockAABB {
        public final double minX, minY, minZ;
        public final double maxX, maxY, maxZ;

        public MockAABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public double getXsize() { return maxX - minX; }
        public double getYsize() { return maxY - minY; }
        public double getZsize() { return maxZ - minZ; }

        public boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX &&
                   y >= minY && y <= maxY &&
                   z >= minZ && z <= maxZ;
        }

        public MockAABB inflate(double amount) {
            return new MockAABB(
                minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount
            );
        }

        public MockVec3 getCenter() {
            return new MockVec3(
                (minX + maxX) / 2.0,
                (minY + maxY) / 2.0,
                (minZ + maxZ) / 2.0
            );
        }
    }

    /**
     * Mock Vec3 for testing
     */
    static class MockVec3 {
        public final double x, y, z;

        public MockVec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public MockVec3 subtract(MockVec3 other) {
            return new MockVec3(x - other.x, y - other.y, z - other.z);
        }

        public MockVec3 add(MockVec3 other) {
            return new MockVec3(x + other.x, y + other.y, z + other.z);
        }

        public MockVec3 scale(double factor) {
            return new MockVec3(x * factor, y * factor, z * factor);
        }

        public MockVec3 normalize() {
            double len = length();
            if (len < 1e-10) return new MockVec3(0, 0, 0);
            return new MockVec3(x / len, y / len, z / len);
        }

        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        public double distanceTo(MockVec3 other) {
            return subtract(other).length();
        }
    }

    /**
     * Body parts enum matching HitHelper.BodyPart
     */
    enum BodyPart {
        HEAD, BODY, ARMS, LEGS
    }

    /**
     * Simulates body part detection based on hit Y position relative to entity height
     * Matches HitHelper.computeBodyPart logic
     */
    static BodyPart computeBodyPart(double hitY, double entityMinY, double entityHeight) {
        double relativeY = (hitY - entityMinY) / entityHeight;

        // Head: top 25% (0.75 - 1.0)
        if (relativeY >= 0.75) return BodyPart.HEAD;
        // Body: 37.5% - 75% (0.375 - 0.75)
        if (relativeY >= 0.375) return BodyPart.BODY;
        // Arms: 25% - 37.5% (overlaps with body in HitHelper, simplified here)
        // For simplicity, we use body threshold
        // Legs: bottom 37.5% (0 - 0.375)
        return BodyPart.LEGS;
    }

    /**
     * Simulates ray-AABB intersection test
     */
    @Nullable
    static MockVec3 raycastAABB(MockVec3 origin, MockVec3 direction, MockAABB aabb) {
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;

        // X axis
        if (Math.abs(direction.x) < 1e-10) {
            if (origin.x < aabb.minX || origin.x > aabb.maxX) return null;
        } else {
            double t1 = (aabb.minX - origin.x) / direction.x;
            double t2 = (aabb.maxX - origin.x) / direction.x;
            if (t1 > t2) { double temp = t1; t1 = t2; t2 = temp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        }

        // Y axis
        if (Math.abs(direction.y) < 1e-10) {
            if (origin.y < aabb.minY || origin.y > aabb.maxY) return null;
        } else {
            double t1 = (aabb.minY - origin.y) / direction.y;
            double t2 = (aabb.maxY - origin.y) / direction.y;
            if (t1 > t2) { double temp = t1; t1 = t2; t2 = temp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        }

        // Z axis
        if (Math.abs(direction.z) < 1e-10) {
            if (origin.z < aabb.minZ || origin.z > aabb.maxZ) return null;
        } else {
            double t1 = (aabb.minZ - origin.z) / direction.z;
            double t2 = (aabb.maxZ - origin.z) / direction.z;
            if (t1 > t2) { double temp = t1; t1 = t2; t2 = temp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        }

        if (tMin > tMax || tMax < 0) return null;

        double t = tMin > 0 ? tMin : tMax;
        return new MockVec3(
            origin.x + direction.x * t,
            origin.y + direction.y * t,
            origin.z + direction.z * t
        );
    }

    // ============================================
    // AABB Tests
    // ============================================

    @Nested
    @DisplayName("AABB Tests")
    class AABBTests {

        @Test
        @DisplayName("AABB dimensions should be correct")
        void testAABBDimensions() {
            MockAABB aabb = new MockAABB(0, 0, 0, 1, 2, 1);
            assertEquals(1.0, aabb.getXsize(), 0.001);
            assertEquals(2.0, aabb.getYsize(), 0.001);
            assertEquals(1.0, aabb.getZsize(), 0.001);
        }

        @Test
        @DisplayName("AABB center should be calculated correctly")
        void testAABBCenter() {
            MockAABB aabb = new MockAABB(0, 0, 0, 2, 4, 2);
            MockVec3 center = aabb.getCenter();
            assertEquals(1.0, center.x, 0.001);
            assertEquals(2.0, center.y, 0.001);
            assertEquals(1.0, center.z, 0.001);
        }

        @Test
        @DisplayName("Point inside AABB should be contained")
        void testPointInsideAABB() {
            MockAABB aabb = new MockAABB(0, 0, 0, 2, 2, 2);
            assertTrue(aabb.contains(1, 1, 1));
        }

        @Test
        @DisplayName("Point outside AABB should not be contained")
        void testPointOutsideAABB() {
            MockAABB aabb = new MockAABB(0, 0, 0, 2, 2, 2);
            assertFalse(aabb.contains(3, 1, 1));
            assertFalse(aabb.contains(1, 3, 1));
            assertFalse(aabb.contains(1, 1, 3));
            assertFalse(aabb.contains(-1, 1, 1));
        }

        @Test
        @DisplayName("Point on AABB edge should be contained")
        void testPointOnAABBEdge() {
            MockAABB aabb = new MockAABB(0, 0, 0, 2, 2, 2);
            assertTrue(aabb.contains(0, 0, 0));
            assertTrue(aabb.contains(2, 2, 2));
            assertTrue(aabb.contains(0, 2, 2));
        }

        @Test
        @DisplayName("Inflate should expand AABB in all directions")
        void testAABBInflate() {
            MockAABB aabb = new MockAABB(0, 0, 0, 1, 1, 1);
            MockAABB inflated = aabb.inflate(0.5);
            assertEquals(-0.5, inflated.minX, 0.001);
            assertEquals(-0.5, inflated.minY, 0.001);
            assertEquals(-0.5, inflated.minZ, 0.001);
            assertEquals(1.5, inflated.maxX, 0.001);
            assertEquals(1.5, inflated.maxY, 0.001);
            assertEquals(1.5, inflated.maxZ, 0.001);
        }
    }

    // ============================================
    // Body Part Detection Tests
    // ============================================

    @Nested
    @DisplayName("Body Part Detection Tests")
    class BodyPartDetectionTests {

        private double entityMinY;
        private double entityHeight;

        @BeforeEach
        void setUp() {
            // Standard humanoid entity: 1.8 blocks tall at Y=0
            entityMinY = 0;
            entityHeight = 1.8;
        }

        @Test
        @DisplayName("Hit at top should be HEAD")
        void testHeadHit() {
            // Top 25%: 1.35 - 1.8 blocks
            BodyPart part = computeBodyPart(1.5, entityMinY, entityHeight);
            assertEquals(BodyPart.HEAD, part);
        }

        @Test
        @DisplayName("Hit at upper body should be BODY")
        void testBodyHit() {
            // Body: 37.5% - 75% = 0.675 - 1.35 blocks
            BodyPart part = computeBodyPart(1.0, entityMinY, entityHeight);
            assertEquals(BodyPart.BODY, part);
        }

        @Test
        @DisplayName("Hit at legs should be LEGS")
        void testLegsHit() {
            // Legs: bottom 37.5% = 0 - 0.675 blocks
            BodyPart part = computeBodyPart(0.3, entityMinY, entityHeight);
            assertEquals(BodyPart.LEGS, part);
        }

        @ParameterizedTest
        @CsvSource({
            "1.8, HEAD",   // Top of entity
            "1.5, HEAD",   // Upper head
            "1.35, HEAD",  // Head boundary (75%)
            "1.2, BODY",   // Upper body
            "1.0, BODY",   // Mid body
            "0.675, BODY", // Body boundary (37.5%)
            "0.5, LEGS",   // Upper legs
            "0.2, LEGS",   // Lower legs
            "0.0, LEGS"    // Feet
        })
        @DisplayName("Body part detection at various heights")
        void testBodyPartAtVariousHeights(double hitY, BodyPart expected) {
            BodyPart part = computeBodyPart(hitY, entityMinY, entityHeight);
            assertEquals(expected, part);
        }

        @Test
        @DisplayName("Entity at elevated position should detect correctly")
        void testElevatedEntity() {
            double elevatedMinY = 10.0;
            // Hit at top of entity (10 + 1.35 to 10 + 1.8 = 11.35 to 11.8)
            BodyPart head = computeBodyPart(11.5, elevatedMinY, entityHeight);
            assertEquals(BodyPart.HEAD, head);

            // Hit at legs (10 to 10.675)
            BodyPart legs = computeBodyPart(10.3, elevatedMinY, entityHeight);
            assertEquals(BodyPart.LEGS, legs);
        }

        @Test
        @DisplayName("Short entity (spider) should detect correctly")
        void testShortEntity() {
            double spiderHeight = 0.5;
            double spiderMinY = 0;

            // Head: 0.375 - 0.5
            BodyPart head = computeBodyPart(0.45, spiderMinY, spiderHeight);
            assertEquals(BodyPart.HEAD, head);

            // Legs: 0 - 0.1875
            BodyPart legs = computeBodyPart(0.1, spiderMinY, spiderHeight);
            assertEquals(BodyPart.LEGS, legs);
        }

        @Test
        @DisplayName("Tall entity (enderman) should detect correctly")
        void testTallEntity() {
            double endermanHeight = 2.9;
            double endermanMinY = 0;

            // Head: top 25% = 2.175 - 2.9
            BodyPart head = computeBodyPart(2.5, endermanMinY, endermanHeight);
            assertEquals(BodyPart.HEAD, head);

            // Body: 37.5% - 75% = 1.0875 - 2.175
            BodyPart body = computeBodyPart(1.5, endermanMinY, endermanHeight);
            assertEquals(BodyPart.BODY, body);
        }
    }

    // ============================================
    // Raycast Tests
    // ============================================

    @Nested
    @DisplayName("Raycast Tests")
    class RaycastTests {

        @Test
        @DisplayName("Ray hitting AABB center should return hit point")
        void testRayHitsCenter() {
            MockAABB aabb = new MockAABB(-0.5, 0, -0.5, 0.5, 2, 0.5);
            MockVec3 origin = new MockVec3(5, 1, 0);
            MockVec3 direction = new MockVec3(-1, 0, 0); // Shooting towards -X

            MockVec3 hit = raycastAABB(origin, direction, aabb);
            assertNotNull(hit);
            assertEquals(0.5, hit.x, 0.01); // Should hit at x=0.5 (edge of box)
            assertEquals(1.0, hit.y, 0.01);
            assertEquals(0.0, hit.z, 0.01);
        }

        @Test
        @DisplayName("Ray missing AABB should return null")
        void testRayMisses() {
            MockAABB aabb = new MockAABB(-0.5, 0, -0.5, 0.5, 2, 0.5);
            MockVec3 origin = new MockVec3(5, 10, 0); // Too high
            MockVec3 direction = new MockVec3(-1, 0, 0);

            MockVec3 hit = raycastAABB(origin, direction, aabb);
            assertNull(hit);
        }

        @Test
        @DisplayName("Ray from inside AABB should return exit point")
        void testRayFromInside() {
            MockAABB aabb = new MockAABB(-1, 0, -1, 1, 2, 1);
            MockVec3 origin = new MockVec3(0, 1, 0);
            MockVec3 direction = new MockVec3(1, 0, 0);

            MockVec3 hit = raycastAABB(origin, direction, aabb);
            assertNotNull(hit);
            assertEquals(1.0, hit.x, 0.01); // Should hit exit at x=1
        }

        @Test
        @DisplayName("Ray shooting away from AABB should return null")
        void testRayShootingAway() {
            MockAABB aabb = new MockAABB(-0.5, 0, -0.5, 0.5, 2, 0.5);
            MockVec3 origin = new MockVec3(5, 1, 0);
            MockVec3 direction = new MockVec3(1, 0, 0); // Shooting away

            MockVec3 hit = raycastAABB(origin, direction, aabb);
            assertNull(hit);
        }

        @Test
        @DisplayName("Diagonal ray should hit correctly")
        void testDiagonalRay() {
            MockAABB aabb = new MockAABB(-0.5, 0, -0.5, 0.5, 2, 0.5);
            MockVec3 origin = new MockVec3(3, 1, 3);
            MockVec3 direction = new MockVec3(-1, 0, -1).normalize();

            MockVec3 hit = raycastAABB(origin, direction, aabb);
            assertNotNull(hit);
            // Should hit the corner/edge
            assertTrue(hit.x <= 0.51 && hit.x >= 0.49);
            assertTrue(hit.z <= 0.51 && hit.z >= 0.49);
        }

        @Test
        @DisplayName("Ray hitting top of AABB")
        void testRayHitsTop() {
            MockAABB aabb = new MockAABB(-0.5, 0, -0.5, 0.5, 2, 0.5);
            MockVec3 origin = new MockVec3(0, 5, 0);
            MockVec3 direction = new MockVec3(0, -1, 0);

            MockVec3 hit = raycastAABB(origin, direction, aabb);
            assertNotNull(hit);
            assertEquals(2.0, hit.y, 0.01); // Should hit top at y=2
        }

        @Test
        @DisplayName("Ray hitting bottom of AABB")
        void testRayHitsBottom() {
            MockAABB aabb = new MockAABB(-0.5, 1, -0.5, 0.5, 3, 0.5);
            MockVec3 origin = new MockVec3(0, 0, 0);
            MockVec3 direction = new MockVec3(0, 1, 0);

            MockVec3 hit = raycastAABB(origin, direction, aabb);
            assertNotNull(hit);
            assertEquals(1.0, hit.y, 0.01); // Should hit bottom at y=1
        }
    }

    // ============================================
    // Vec3 Tests
    // ============================================

    @Nested
    @DisplayName("Vec3 Tests")
    class Vec3Tests {

        @Test
        @DisplayName("Vec3 length calculation")
        void testLength() {
            MockVec3 v = new MockVec3(3, 4, 0);
            assertEquals(5.0, v.length(), 0.001);
        }

        @Test
        @DisplayName("Vec3 normalization")
        void testNormalize() {
            MockVec3 v = new MockVec3(10, 0, 0);
            MockVec3 normalized = v.normalize();
            assertEquals(1.0, normalized.length(), 0.001);
            assertEquals(1.0, normalized.x, 0.001);
        }

        @Test
        @DisplayName("Vec3 distance calculation")
        void testDistance() {
            MockVec3 a = new MockVec3(0, 0, 0);
            MockVec3 b = new MockVec3(3, 4, 0);
            assertEquals(5.0, a.distanceTo(b), 0.001);
        }

        @Test
        @DisplayName("Vec3 subtract")
        void testSubtract() {
            MockVec3 a = new MockVec3(5, 5, 5);
            MockVec3 b = new MockVec3(2, 3, 4);
            MockVec3 result = a.subtract(b);
            assertEquals(3.0, result.x, 0.001);
            assertEquals(2.0, result.y, 0.001);
            assertEquals(1.0, result.z, 0.001);
        }

        @Test
        @DisplayName("Vec3 scale")
        void testScale() {
            MockVec3 v = new MockVec3(1, 2, 3);
            MockVec3 scaled = v.scale(2);
            assertEquals(2.0, scaled.x, 0.001);
            assertEquals(4.0, scaled.y, 0.001);
            assertEquals(6.0, scaled.z, 0.001);
        }

        @Test
        @DisplayName("Zero vector normalization")
        void testZeroVectorNormalize() {
            MockVec3 v = new MockVec3(0, 0, 0);
            MockVec3 normalized = v.normalize();
            assertEquals(0.0, normalized.x, 0.001);
            assertEquals(0.0, normalized.y, 0.001);
            assertEquals(0.0, normalized.z, 0.001);
        }
    }

    // ============================================
    // Combined Hit Detection Tests
    // ============================================

    @Nested
    @DisplayName("Combined Hit Detection Tests")
    class CombinedHitDetectionTests {

        @Test
        @DisplayName("Raycast and detect head hit")
        void testRaycastHeadHit() {
            // Entity standing at origin, 1.8 blocks tall
            MockAABB entityBox = new MockAABB(-0.4, 0, -0.4, 0.4, 1.8, 0.4);

            // Ray from player at distance 5, aiming at head
            MockVec3 origin = new MockVec3(5, 1.6, 0);
            MockVec3 direction = new MockVec3(-1, 0.02, 0).normalize();

            MockVec3 hit = raycastAABB(origin, direction, entityBox);
            assertNotNull(hit);

            // Check body part
            BodyPart part = computeBodyPart(hit.y, 0, 1.8);
            // Due to aiming slightly up, should hit head area
            assertTrue(hit.y > 1.35, "Hit Y should be in head region: " + hit.y);
            assertEquals(BodyPart.HEAD, part);
        }

        @Test
        @DisplayName("Raycast and detect body hit")
        void testRaycastBodyHit() {
            MockAABB entityBox = new MockAABB(-0.4, 0, -0.4, 0.4, 1.8, 0.4);

            // Aim at center body
            MockVec3 origin = new MockVec3(5, 1.0, 0);
            MockVec3 direction = new MockVec3(-1, 0, 0).normalize();

            MockVec3 hit = raycastAABB(origin, direction, entityBox);
            assertNotNull(hit);

            BodyPart part = computeBodyPart(hit.y, 0, 1.8);
            assertEquals(BodyPart.BODY, part);
        }

        @Test
        @DisplayName("Raycast and detect leg hit")
        void testRaycastLegHit() {
            MockAABB entityBox = new MockAABB(-0.4, 0, -0.4, 0.4, 1.8, 0.4);

            // Aim at legs
            MockVec3 origin = new MockVec3(5, 0.3, 0);
            MockVec3 direction = new MockVec3(-1, 0, 0).normalize();

            MockVec3 hit = raycastAABB(origin, direction, entityBox);
            assertNotNull(hit);

            BodyPart part = computeBodyPart(hit.y, 0, 1.8);
            assertEquals(BodyPart.LEGS, part);
        }

        @Test
        @DisplayName("Multiple entities - closest hit detection")
        void testMultipleEntitiesClosestHit() {
            MockAABB entity1 = new MockAABB(-0.4, 0, -0.4, 0.4, 1.8, 0.4); // At origin
            MockAABB entity2 = new MockAABB(2.6, 0, -0.4, 3.4, 1.8, 0.4); // At x=3

            MockVec3 origin = new MockVec3(5, 1.0, 0);
            MockVec3 direction = new MockVec3(-1, 0, 0).normalize();

            MockVec3 hit1 = raycastAABB(origin, direction, entity1);
            MockVec3 hit2 = raycastAABB(origin, direction, entity2);

            assertNotNull(hit1);
            assertNotNull(hit2);

            // Entity2 is closer (hit2.x should be larger, meaning closer to origin at x=5)
            assertTrue(hit2.x > hit1.x, "Entity2 hit should be closer");

            // Distance check
            double dist1 = origin.distanceTo(hit1);
            double dist2 = origin.distanceTo(hit2);
            assertTrue(dist2 < dist1, "Entity2 should be hit first");
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Very close ray origin")
        void testVeryCloseRay() {
            MockAABB aabb = new MockAABB(-0.4, 0, -0.4, 0.4, 1.8, 0.4);
            MockVec3 origin = new MockVec3(0.5, 1.0, 0);
            MockVec3 direction = new MockVec3(-1, 0, 0);

            MockVec3 hit = raycastAABB(origin, direction, aabb);
            assertNotNull(hit);
            assertEquals(0.4, hit.x, 0.01);
        }

        @Test
        @DisplayName("Ray parallel to AABB face")
        void testParallelRay() {
            MockAABB aabb = new MockAABB(-0.4, 0, -0.4, 0.4, 1.8, 0.4);
            // Ray going parallel to X axis, but at z=0.5 (outside box)
            MockVec3 origin = new MockVec3(5, 1.0, 0.5);
            MockVec3 direction = new MockVec3(-1, 0, 0);

            MockVec3 hit = raycastAABB(origin, direction, aabb);
            assertNull(hit); // Should miss
        }

        @Test
        @DisplayName("Grazing ray")
        void testGrazingRay() {
            MockAABB aabb = new MockAABB(-0.4, 0, -0.4, 0.4, 1.8, 0.4);
            // Ray that just grazes the edge
            MockVec3 origin = new MockVec3(5, 1.0, 0.39);
            MockVec3 direction = new MockVec3(-1, 0, 0);

            MockVec3 hit = raycastAABB(origin, direction, aabb);
            assertNotNull(hit); // Should still hit (barely inside)
        }

        @Test
        @DisplayName("Boundary condition - exact head/body threshold")
        void testHeadBodyBoundary() {
            double entityHeight = 1.8;
            double threshold = entityHeight * 0.75; // 1.35

            BodyPart atThreshold = computeBodyPart(threshold, 0, entityHeight);
            assertEquals(BodyPart.HEAD, atThreshold);

            BodyPart belowThreshold = computeBodyPart(threshold - 0.01, 0, entityHeight);
            assertEquals(BodyPart.BODY, belowThreshold);
        }

        @Test
        @DisplayName("Boundary condition - exact body/legs threshold")
        void testBodyLegsThreshold() {
            double entityHeight = 1.8;
            double threshold = entityHeight * 0.375; // 0.675

            BodyPart atThreshold = computeBodyPart(threshold, 0, entityHeight);
            assertEquals(BodyPart.BODY, atThreshold);

            BodyPart belowThreshold = computeBodyPart(threshold - 0.01, 0, entityHeight);
            assertEquals(BodyPart.LEGS, belowThreshold);
        }

        @Test
        @DisplayName("Very small entity (bee)")
        void testVerySmallEntity() {
            double beeHeight = 0.5;

            BodyPart head = computeBodyPart(beeHeight * 0.8, 0, beeHeight);
            assertEquals(BodyPart.HEAD, head);

            BodyPart legs = computeBodyPart(beeHeight * 0.1, 0, beeHeight);
            assertEquals(BodyPart.LEGS, legs);
        }

        @Test
        @DisplayName("Very large entity (giant)")
        void testVeryLargeEntity() {
            double giantHeight = 12.0;

            BodyPart head = computeBodyPart(giantHeight * 0.9, 0, giantHeight);
            assertEquals(BodyPart.HEAD, head);

            BodyPart body = computeBodyPart(giantHeight * 0.5, 0, giantHeight);
            assertEquals(BodyPart.BODY, body);

            BodyPart legs = computeBodyPart(giantHeight * 0.1, 0, giantHeight);
            assertEquals(BodyPart.LEGS, legs);
        }
    }
}
