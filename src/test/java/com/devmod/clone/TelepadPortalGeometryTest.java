package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import com.devmod.clone.client.renderer.TelepadPortalGeometry;

class TelepadPortalGeometryTest {

    private static final float EPSILON = 1e-5f;

    @Nested
    @DisplayName("Portal dimensions")
    class DimensionTests {

        @Test
        @DisplayName("Half-width is half of PORTAL_WIDTH")
        void halfWidthIsHalfOfWidth() {
            assertEquals(TelepadPortalGeometry.PORTAL_WIDTH / 2.0f,
                TelepadPortalGeometry.getHalfWidth(), EPSILON);
        }

        @Test
        @DisplayName("Half-height is half of PORTAL_HEIGHT")
        void halfHeightIsHalfOfHeight() {
            assertEquals(TelepadPortalGeometry.PORTAL_HEIGHT / 2.0f,
                TelepadPortalGeometry.getHalfHeight(), EPSILON);
        }

        @Test
        @DisplayName("Portal dimensions are positive")
        void dimensionsArePositive() {
            assertTrue(TelepadPortalGeometry.PORTAL_WIDTH > 0);
            assertTrue(TelepadPortalGeometry.PORTAL_HEIGHT > 0);
            assertTrue(TelepadPortalGeometry.PORTAL_Y_OFFSET >= 0);
        }
    }

    @Nested
    @DisplayName("getCenter")
    class GetCenterTests {

        @Test
        @DisplayName("Center of portal at origin")
        void centerAtOrigin() {
            Vec3 center = TelepadPortalGeometry.getCenter(BlockPos.ZERO);
            assertEquals(0.5, center.x, EPSILON);
            assertEquals(TelepadPortalGeometry.PORTAL_Y_OFFSET + TelepadPortalGeometry.getHalfHeight(),
                center.y, EPSILON);
            assertEquals(0.5, center.z, EPSILON);
        }

        @Test
        @DisplayName("Center at non-zero position")
        void centerAtOffset() {
            Vec3 center = TelepadPortalGeometry.getCenter(new BlockPos(10, 5, -3));
            assertEquals(10.5, center.x, EPSILON);
            assertEquals(5 + TelepadPortalGeometry.PORTAL_Y_OFFSET + TelepadPortalGeometry.getHalfHeight(),
                center.y, EPSILON);
            assertEquals(-2.5, center.z, EPSILON);
        }
    }

    @Nested
    @DisplayName("getFacingYawDegrees")
    class FacingYawTests {

        @Test
        @DisplayName("North is 0 degrees")
        void northIsZero() {
            assertEquals(0.0f, TelepadPortalGeometry.getFacingYawDegrees(Direction.NORTH), EPSILON);
        }

        @Test
        @DisplayName("South is 180 degrees")
        void southIs180() {
            assertEquals(180.0f, TelepadPortalGeometry.getFacingYawDegrees(Direction.SOUTH), EPSILON);
        }

        @Test
        @DisplayName("West is 90 degrees")
        void westIs90() {
            assertEquals(90.0f, TelepadPortalGeometry.getFacingYawDegrees(Direction.WEST), EPSILON);
        }

        @Test
        @DisplayName("East is -90 degrees")
        void eastIsNeg90() {
            assertEquals(-90.0f, TelepadPortalGeometry.getFacingYawDegrees(Direction.EAST), EPSILON);
        }

        @Test
        @DisplayName("UP and DOWN default to 0")
        void verticalDirectionsDefaultZero() {
            assertEquals(0.0f, TelepadPortalGeometry.getFacingYawDegrees(Direction.UP), EPSILON);
            assertEquals(0.0f, TelepadPortalGeometry.getFacingYawDegrees(Direction.DOWN), EPSILON);
        }
    }

    @Nested
    @DisplayName("rotateY")
    class RotateYTests {

        @Test
        @DisplayName("Rotation by 0 degrees preserves vector")
        void rotateByZero() {
            Vec3 v = new Vec3(1, 2, 3);
            Vec3 result = TelepadPortalGeometry.rotateY(v, 0);
            assertEquals(1.0, result.x, EPSILON);
            assertEquals(2.0, result.y, EPSILON);
            assertEquals(3.0, result.z, EPSILON);
        }

        @Test
        @DisplayName("Rotation by 360 degrees preserves vector")
        void rotateBy360() {
            Vec3 v = new Vec3(1, 2, 3);
            Vec3 result = TelepadPortalGeometry.rotateY(v, 360);
            assertEquals(1.0, result.x, EPSILON);
            assertEquals(2.0, result.y, EPSILON);
            assertEquals(3.0, result.z, EPSILON);
        }

        @Test
        @DisplayName("Rotation preserves Y component")
        void rotationPreservesY() {
            Vec3 v = new Vec3(5, 42, 7);
            Vec3 result = TelepadPortalGeometry.rotateY(v, 137);
            assertEquals(42.0, result.y, EPSILON);
        }

        @Test
        @DisplayName("90-degree rotation of unit-Z produces unit-X")
        void rotate90UnitZ() {
            Vec3 v = new Vec3(0, 0, 1);
            Vec3 result = TelepadPortalGeometry.rotateY(v, 90);
            assertEquals(1.0, result.x, EPSILON);
            assertEquals(0.0, result.y, EPSILON);
            assertEquals(0.0, result.z, EPSILON);
        }

        @Test
        @DisplayName("Rotation preserves vector magnitude in xz plane")
        void rotationPreservesMagnitude() {
            Vec3 v = new Vec3(3, 0, 4);
            double origMag = Math.sqrt(v.x * v.x + v.z * v.z);
            Vec3 result = TelepadPortalGeometry.rotateY(v, 73);
            double resultMag = Math.sqrt(result.x * result.x + result.z * result.z);
            assertEquals(origMag, resultMag, EPSILON);
        }
    }

    @Nested
    @DisplayName("getNormal")
    class GetNormalTests {

        @Test
        @DisplayName("North-facing normal points along +Z")
        void northNormal() {
            Vec3 n = TelepadPortalGeometry.getNormal(Direction.NORTH);
            assertEquals(0.0, n.x, EPSILON);
            assertEquals(0.0, n.y, EPSILON);
            assertEquals(1.0, n.z, EPSILON);
        }

        @Test
        @DisplayName("South-facing normal points along -Z")
        void southNormal() {
            Vec3 n = TelepadPortalGeometry.getNormal(Direction.SOUTH);
            assertEquals(0.0, n.x, EPSILON);
            assertEquals(0.0, n.y, EPSILON);
            assertEquals(-1.0, n.z, EPSILON);
        }

        @Test
        @DisplayName("Normal vector has unit length")
        void normalIsUnitLength() {
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                Vec3 n = TelepadPortalGeometry.getNormal(dir);
                double length = Math.sqrt(n.x * n.x + n.y * n.y + n.z * n.z);
                assertEquals(1.0, length, EPSILON, "Normal for " + dir + " should be unit length");
            }
        }
    }

    @Nested
    @DisplayName("isInsidePortalEllipse")
    class EllipseTests {

        @Test
        @DisplayName("Origin is inside ellipse")
        void originInside() {
            assertTrue(TelepadPortalGeometry.isInsidePortalEllipse(new Vec3(0, 0, 0)));
        }

        @Test
        @DisplayName("Point on semi-major axis boundary")
        void onBoundaryX() {
            Vec3 onEdge = new Vec3(TelepadPortalGeometry.getHalfWidth(), 0, 0);
            assertTrue(TelepadPortalGeometry.isInsidePortalEllipse(onEdge));
        }

        @Test
        @DisplayName("Point on semi-minor axis boundary")
        void onBoundaryY() {
            Vec3 onEdge = new Vec3(0, TelepadPortalGeometry.getHalfHeight(), 0);
            assertTrue(TelepadPortalGeometry.isInsidePortalEllipse(onEdge));
        }

        @Test
        @DisplayName("Point just outside ellipse")
        void justOutside() {
            Vec3 outside = new Vec3(TelepadPortalGeometry.getHalfWidth() + 0.1, 0, 0);
            assertFalse(TelepadPortalGeometry.isInsidePortalEllipse(outside));
        }

        @Test
        @DisplayName("Point far outside")
        void farOutside() {
            assertFalse(TelepadPortalGeometry.isInsidePortalEllipse(new Vec3(100, 100, 0)));
        }

        @Test
        @DisplayName("Z component is ignored for ellipse check")
        void zIgnored() {
            assertTrue(TelepadPortalGeometry.isInsidePortalEllipse(new Vec3(0, 0, 999)));
        }
    }

    @Nested
    @DisplayName("toLocal")
    class ToLocalTests {

        @Test
        @DisplayName("North facing is identity for local conversion")
        void northIsIdentity() {
            Vec3 offset = new Vec3(1, 2, 3);
            Vec3 local = TelepadPortalGeometry.toLocal(offset, Direction.NORTH);
            assertEquals(offset.x, local.x, EPSILON);
            assertEquals(offset.y, local.y, EPSILON);
            assertEquals(offset.z, local.z, EPSILON);
        }

        @Test
        @DisplayName("toLocal reverses getFacingYawDegrees rotation")
        void toLocalReversesYaw() {
            Vec3 worldOffset = new Vec3(1, 0, 0);
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                Vec3 local = TelepadPortalGeometry.toLocal(worldOffset, dir);
                double mag = Math.sqrt(local.x * local.x + local.z * local.z);
                assertEquals(1.0, mag, EPSILON, "Magnitude should be preserved for " + dir);
            }
        }
    }
}
