package com.devmod.zone.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.devmod.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;

/**
 * Unit tests for ZoneBounds: containment checks, overlap detection,
 * factory methods, validation, and transformations.
 */
@DisplayName("ZoneBounds")
class ZoneBoundsTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Contains (point-in-zone)
    // ========================================================================

    @Nested
    @DisplayName("contains()")
    class ContainsTests {

        private final ZoneBounds bounds = new ZoneBounds(0, 10, 0, 10, 0, 10);

        @Test
        @DisplayName("point inside bounds returns true")
        void pointInsideBounds() {
            assertTrue(bounds.contains(new BlockPos(5, 5, 5)));
        }

        @Test
        @DisplayName("point at min corner (inclusive) returns true")
        void pointAtMinCorner() {
            assertTrue(bounds.contains(new BlockPos(0, 0, 0)));
        }

        @Test
        @DisplayName("point at max corner (inclusive) returns true")
        void pointAtMaxCorner() {
            assertTrue(bounds.contains(new BlockPos(10, 10, 10)));
        }

        @Test
        @DisplayName("point at each face center returns true")
        void pointsOnFaces() {
            assertTrue(bounds.contains(new BlockPos(0, 5, 5)));   // minX face
            assertTrue(bounds.contains(new BlockPos(10, 5, 5)));  // maxX face
            assertTrue(bounds.contains(new BlockPos(5, 0, 5)));   // minY face
            assertTrue(bounds.contains(new BlockPos(5, 10, 5)));  // maxY face
            assertTrue(bounds.contains(new BlockPos(5, 5, 0)));   // minZ face
            assertTrue(bounds.contains(new BlockPos(5, 5, 10)));  // maxZ face
        }

        @Test
        @DisplayName("point outside on each axis returns false")
        void pointsOutside() {
            assertFalse(bounds.contains(new BlockPos(-1, 5, 5)));  // below minX
            assertFalse(bounds.contains(new BlockPos(11, 5, 5)));  // above maxX
            assertFalse(bounds.contains(new BlockPos(5, -1, 5)));  // below minY
            assertFalse(bounds.contains(new BlockPos(5, 11, 5)));  // above maxY
            assertFalse(bounds.contains(new BlockPos(5, 5, -1)));  // below minZ
            assertFalse(bounds.contains(new BlockPos(5, 5, 11)));  // above maxZ
        }

        @Test
        @DisplayName("single-block bounds contains its own position")
        void singleBlockBounds() {
            ZoneBounds single = new ZoneBounds(5, 5, 10, 10, 15, 15);
            assertTrue(single.contains(new BlockPos(5, 10, 15)));
            assertFalse(single.contains(new BlockPos(4, 10, 15)));
            assertFalse(single.contains(new BlockPos(6, 10, 15)));
        }

        @Test
        @DisplayName("negative coordinate bounds work correctly")
        void negativeBounds() {
            ZoneBounds neg = new ZoneBounds(-10, -1, -64, -50, -20, -5);
            assertTrue(neg.contains(new BlockPos(-5, -60, -10)));
            assertFalse(neg.contains(new BlockPos(0, -60, -10)));
        }
    }

    // ========================================================================
    // Overlaps
    // ========================================================================

    @Nested
    @DisplayName("overlaps()")
    class OverlapsTests {

        private final ZoneBounds base = new ZoneBounds(0, 10, 0, 10, 0, 10);

        @Test
        @DisplayName("identical bounds overlap")
        void identicalBoundsOverlap() {
            assertTrue(base.overlaps(new ZoneBounds(0, 10, 0, 10, 0, 10)));
        }

        @Test
        @DisplayName("contained bounds overlap")
        void containedBoundsOverlap() {
            assertTrue(base.overlaps(new ZoneBounds(2, 8, 2, 8, 2, 8)));
        }

        @Test
        @DisplayName("partially overlapping on X axis")
        void partialOverlapX() {
            assertTrue(base.overlaps(new ZoneBounds(5, 15, 0, 10, 0, 10)));
        }

        @Test
        @DisplayName("touching at edge on X (maxX == other.minX) overlaps")
        void touchingEdgeX() {
            assertTrue(base.overlaps(new ZoneBounds(10, 20, 0, 10, 0, 10)));
        }

        @Test
        @DisplayName("gap of 1 on X axis does not overlap")
        void gapOnX() {
            assertFalse(base.overlaps(new ZoneBounds(11, 20, 0, 10, 0, 10)));
        }

        @Test
        @DisplayName("gap of 1 on Y axis does not overlap")
        void gapOnY() {
            assertFalse(base.overlaps(new ZoneBounds(0, 10, 11, 20, 0, 10)));
        }

        @Test
        @DisplayName("gap of 1 on Z axis does not overlap")
        void gapOnZ() {
            assertFalse(base.overlaps(new ZoneBounds(0, 10, 0, 10, 11, 20)));
        }

        @Test
        @DisplayName("completely separated bounds do not overlap")
        void completelySeparated() {
            assertFalse(base.overlaps(new ZoneBounds(100, 200, 100, 200, 100, 200)));
        }

        @Test
        @DisplayName("overlap is symmetric")
        void symmetric() {
            ZoneBounds other = new ZoneBounds(5, 15, 5, 15, 5, 15);
            assertEquals(base.overlaps(other), other.overlaps(base));
        }

        @Test
        @DisplayName("single-block bounds overlap when same position")
        void singleBlockSamePos() {
            ZoneBounds a = new ZoneBounds(5, 5, 5, 5, 5, 5);
            ZoneBounds b = new ZoneBounds(5, 5, 5, 5, 5, 5);
            assertTrue(a.overlaps(b));
        }

        @Test
        @DisplayName("single-block bounds do not overlap when adjacent")
        void singleBlockAdjacent() {
            ZoneBounds a = new ZoneBounds(5, 5, 5, 5, 5, 5);
            ZoneBounds b = new ZoneBounds(6, 6, 5, 5, 5, 5);
            assertFalse(a.overlaps(b));
        }
    }

    // ========================================================================
    // Factory Methods
    // ========================================================================

    @Nested
    @DisplayName("Factory Methods")
    class FactoryTests {

        @Test
        @DisplayName("fromCorners normalizes min/max regardless of order")
        void fromCornersNormalizes() {
            BlockPos a = new BlockPos(10, 20, 30);
            BlockPos b = new BlockPos(0, 5, 15);
            ZoneBounds bounds = ZoneBounds.fromCorners(a, b);

            assertEquals(0, bounds.minX());
            assertEquals(10, bounds.maxX());
            assertEquals(5, bounds.minY());
            assertEquals(20, bounds.maxY());
            assertEquals(15, bounds.minZ());
            assertEquals(30, bounds.maxZ());
        }

        @Test
        @DisplayName("fromCorners with same point creates single-block bounds")
        void fromCornersSamePoint() {
            BlockPos p = new BlockPos(5, 10, 15);
            ZoneBounds bounds = ZoneBounds.fromCorners(p, p);

            assertEquals(5, bounds.minX());
            assertEquals(5, bounds.maxX());
            assertEquals(1, bounds.width());
            assertEquals(1, bounds.height());
            assertEquals(1, bounds.length());
        }

        @Test
        @DisplayName("fromCenterAndSize creates symmetric bounds")
        void fromCenterAndSize() {
            BlockPos center = new BlockPos(100, 64, 200);
            ZoneBounds bounds = ZoneBounds.fromCenterAndSize(center, 10, 5, 10);

            assertEquals(90, bounds.minX());
            assertEquals(110, bounds.maxX());
            assertEquals(59, bounds.minY());
            assertEquals(69, bounds.maxY());
            assertEquals(190, bounds.minZ());
            assertEquals(210, bounds.maxZ());
            assertTrue(bounds.contains(center));
        }

        @Test
        @DisplayName("small() creates correct sized zone")
        void smallFactory() {
            ZoneBounds bounds = ZoneBounds.small(100, 200, 64, 10, 5);

            assertEquals(95, bounds.minX());
            assertEquals(105, bounds.maxX());
            assertEquals(64, bounds.minY());
            assertEquals(69, bounds.maxY());
            assertEquals(195, bounds.minZ());
            assertEquals(205, bounds.maxZ());
        }

        @Test
        @DisplayName("relative() applies offsets from origin")
        void relativeFactory() {
            BlockPos origin = new BlockPos(100, 64, 200);
            ZoneBounds bounds = ZoneBounds.relative(origin, -5, 5, -2, 8, -5, 5);

            assertEquals(95, bounds.minX());
            assertEquals(105, bounds.maxX());
            assertEquals(62, bounds.minY());
            assertEquals(72, bounds.maxY());
            assertEquals(195, bounds.minZ());
            assertEquals(205, bounds.maxZ());
        }
    }

    // ========================================================================
    // Dimension Queries
    // ========================================================================

    @Nested
    @DisplayName("Dimension Queries")
    class DimensionTests {

        @Test
        @DisplayName("width, height, length are inclusive (max - min + 1)")
        void dimensionsAreInclusive() {
            ZoneBounds bounds = new ZoneBounds(0, 9, 0, 4, 0, 19);
            assertEquals(10, bounds.width());
            assertEquals(5, bounds.height());
            assertEquals(20, bounds.length());
        }

        @Test
        @DisplayName("volume uses long arithmetic to prevent overflow")
        void volumeLongArithmetic() {
            ZoneBounds bounds = new ZoneBounds(0, 999, 0, 999, 0, 999);
            long expected = 1000L * 1000L * 1000L;
            assertEquals(expected, bounds.volume());
        }

        @Test
        @DisplayName("floorArea is width * length")
        void floorArea() {
            ZoneBounds bounds = new ZoneBounds(0, 9, 0, 4, 0, 19);
            assertEquals(200, bounds.floorArea());
        }

        @Test
        @DisplayName("center returns midpoint of bounds")
        void center() {
            ZoneBounds bounds = new ZoneBounds(0, 10, 0, 10, 0, 10);
            BlockPos center = bounds.center();
            assertEquals(5, center.getX());
            assertEquals(5, center.getY());
            assertEquals(5, center.getZ());
        }

        @Test
        @DisplayName("floorCenter returns midpoint at minY")
        void floorCenter() {
            ZoneBounds bounds = new ZoneBounds(0, 10, 64, 80, 0, 10);
            BlockPos fc = bounds.floorCenter();
            assertEquals(5, fc.getX());
            assertEquals(64, fc.getY());
            assertEquals(5, fc.getZ());
        }
    }

    // ========================================================================
    // Transformations
    // ========================================================================

    @Nested
    @DisplayName("Transformations")
    class TransformationTests {

        @Test
        @DisplayName("expand increases all dimensions by amount")
        void expandAllDirections() {
            ZoneBounds bounds = new ZoneBounds(10, 20, 10, 20, 10, 20);
            ZoneBounds expanded = bounds.expand(5);

            assertEquals(5, expanded.minX());
            assertEquals(25, expanded.maxX());
            assertEquals(5, expanded.minY());
            assertEquals(25, expanded.maxY());
            assertEquals(5, expanded.minZ());
            assertEquals(25, expanded.maxZ());
        }

        @Test
        @DisplayName("offset shifts bounds by position")
        void offsetShifts() {
            ZoneBounds bounds = new ZoneBounds(0, 10, 0, 10, 0, 10);
            ZoneBounds shifted = bounds.offset(new BlockPos(100, 64, 200));

            assertEquals(100, shifted.minX());
            assertEquals(110, shifted.maxX());
            assertEquals(64, shifted.minY());
            assertEquals(74, shifted.maxY());
            assertEquals(200, shifted.minZ());
            assertEquals(210, shifted.maxZ());
        }

        @Test
        @DisplayName("withYRange replaces Y coordinates only")
        void withYRange() {
            ZoneBounds bounds = new ZoneBounds(0, 10, 0, 10, 0, 10);
            ZoneBounds changed = bounds.withYRange(64, 128);

            assertEquals(0, changed.minX());
            assertEquals(10, changed.maxX());
            assertEquals(64, changed.minY());
            assertEquals(128, changed.maxY());
            assertEquals(0, changed.minZ());
            assertEquals(10, changed.maxZ());
        }
    }

    // ========================================================================
    // Validation
    // ========================================================================

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("valid bounds pass isValid")
        void validBounds() {
            assertTrue(new ZoneBounds(0, 10, 0, 10, 0, 10).isValid());
        }

        @Test
        @DisplayName("inverted X bounds fail isValid")
        void invertedX() {
            assertFalse(new ZoneBounds(10, 0, 0, 10, 0, 10).isValid());
        }

        @Test
        @DisplayName("inverted Y bounds fail isValid")
        void invertedY() {
            assertFalse(new ZoneBounds(0, 10, 10, 0, 0, 10).isValid());
        }

        @Test
        @DisplayName("inverted Z bounds fail isValid")
        void invertedZ() {
            assertFalse(new ZoneBounds(0, 10, 0, 10, 10, 0).isValid());
        }

        @Test
        @DisplayName("equal min/max passes isValid (single-block)")
        void equalMinMax() {
            assertTrue(new ZoneBounds(5, 5, 5, 5, 5, 5).isValid());
        }

        @Test
        @DisplayName("within world limits passes")
        void withinWorldLimits() {
            assertTrue(new ZoneBounds(-100, 100, -64, 320, -100, 100).isWithinWorldLimits());
        }

        @Test
        @DisplayName("exceeding max Y fails world limits")
        void exceedMaxY() {
            assertFalse(new ZoneBounds(0, 10, 0, 321, 0, 10).isWithinWorldLimits());
        }

        @Test
        @DisplayName("below min Y fails world limits")
        void belowMinY() {
            assertFalse(new ZoneBounds(0, 10, -65, 10, 0, 10).isWithinWorldLimits());
        }

        @Test
        @DisplayName("exceeding max coordinate fails world limits")
        void exceedMaxCoord() {
            assertFalse(new ZoneBounds(0, 30_000_001, 0, 10, 0, 10).isWithinWorldLimits());
        }

        @Test
        @DisplayName("reasonable size passes isSizeReasonable")
        void reasonableSize() {
            assertTrue(new ZoneBounds(0, 99, 0, 99, 0, 99).isSizeReasonable());
        }

        @Test
        @DisplayName("exceeding MAX_ZONE_SIZE fails isSizeReasonable")
        void exceedMaxSize() {
            assertFalse(new ZoneBounds(0, 1000, 0, 10, 0, 10).isSizeReasonable());
        }

        @Test
        @DisplayName("exactly MAX_ZONE_SIZE width passes isSizeReasonable")
        void exactMaxSize() {
            // width = 999 - 0 + 1 = 1000
            assertTrue(new ZoneBounds(0, 999, 0, 10, 0, 10).isSizeReasonable());
        }

        @Test
        @DisplayName("isFullyValid combines all checks")
        void fullyValid() {
            assertTrue(new ZoneBounds(0, 10, -64, 320, 0, 10).isFullyValid());
            // inverted
            assertFalse(new ZoneBounds(10, 0, 0, 10, 0, 10).isFullyValid());
            // out of world
            assertFalse(new ZoneBounds(0, 10, -65, 10, 0, 10).isFullyValid());
            // too large
            assertFalse(new ZoneBounds(0, 1000, 0, 10, 0, 10).isFullyValid());
        }

        @Test
        @DisplayName("normalized fixes inverted bounds")
        void normalized() {
            ZoneBounds inverted = new ZoneBounds(10, 0, 20, 5, 30, -5);
            ZoneBounds fixed = inverted.normalized();

            assertTrue(fixed.isValid());
            assertEquals(0, fixed.minX());
            assertEquals(10, fixed.maxX());
            assertEquals(5, fixed.minY());
            assertEquals(20, fixed.maxY());
            assertEquals(-5, fixed.minZ());
            assertEquals(30, fixed.maxZ());
        }

        @Test
        @DisplayName("clamped constrains to world limits")
        void clamped() {
            ZoneBounds outOfBounds = new ZoneBounds(
                -40_000_000, 40_000_000,
                -100, 400,
                -40_000_000, 40_000_000
            );
            ZoneBounds clamped = outOfBounds.clamped();

            assertEquals(ZoneBounds.MIN_COORDINATE, clamped.minX());
            assertEquals(ZoneBounds.MAX_COORDINATE, clamped.maxX());
            assertEquals(ZoneBounds.MIN_Y, clamped.minY());
            assertEquals(ZoneBounds.MAX_Y, clamped.maxY());
            assertEquals(ZoneBounds.MIN_COORDINATE, clamped.minZ());
            assertEquals(ZoneBounds.MAX_COORDINATE, clamped.maxZ());
        }
    }

    // ========================================================================
    // Record Equality
    // ========================================================================

    @Nested
    @DisplayName("Record Equality")
    class EqualityTests {

        @Test
        @DisplayName("equal bounds are equals()")
        void equalBounds() {
            ZoneBounds a = new ZoneBounds(1, 2, 3, 4, 5, 6);
            ZoneBounds b = new ZoneBounds(1, 2, 3, 4, 5, 6);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("different bounds are not equals()")
        void differentBounds() {
            ZoneBounds a = new ZoneBounds(1, 2, 3, 4, 5, 6);
            ZoneBounds b = new ZoneBounds(1, 2, 3, 4, 5, 7);
            assertNotEquals(a, b);
        }
    }
}
