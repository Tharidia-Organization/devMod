package com.devmod.collision.transform;

import java.util.Collections;

import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnimationSnapshot")
class AnimationSnapshotTest {

    @Nested
    @DisplayName("INVALID sentinel")
    class InvalidTests {

        @Test
        @DisplayName("INVALID is not valid")
        void invalidIsNotValid() {
            assertFalse(AnimationSnapshot.INVALID.isValid());
        }

        @Test
        @DisplayName("INVALID has no transforms")
        void invalidHasNoTransforms() {
            assertFalse(AnimationSnapshot.INVALID.hasTransforms());
            assertEquals(0, AnimationSnapshot.INVALID.getTransformCount());
        }

        @Test
        @DisplayName("INVALID is always stale")
        void invalidIsStale() {
            assertTrue(AnimationSnapshot.INVALID.isStale(0));
            assertTrue(AnimationSnapshot.INVALID.isStale(100));
        }
    }

    @Nested
    @DisplayName("simple factory")
    class SimpleFactoryTests {

        @Test
        @DisplayName("creates valid snapshot with position and rotations")
        void simpleFactory() {
            Vec3 pos = new Vec3(10, 20, 30);
            AnimationSnapshot snap = AnimationSnapshot.simple(42, 100L, pos, 45.0f, 30.0f, -10.0f);

            assertEquals(42, snap.entityId());
            assertEquals(100L, snap.tickCaptured());
            assertEquals(1.0f, snap.partialTick());
            assertEquals(pos, snap.entityPosition());
            assertEquals(45.0f, snap.yBodyRot());
            assertEquals(30.0f, snap.yHeadRot());
            assertEquals(-10.0f, snap.xHeadRot());
            assertTrue(snap.isValid());
            assertFalse(snap.hasTransforms());
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds snapshot with all fields")
        void fullBuild() {
            Matrix4f headTransform = new Matrix4f().rotateY(0.5f);
            AnimationSnapshot snap = AnimationSnapshot.builder(1, 50L, 0.5f)
                .position(5, 10, 15)
                .bodyRotation(90.0f)
                .headRotation(45.0f, -20.0f)
                .addPartTransform("head", headTransform)
                .valid(true)
                .build();

            assertEquals(1, snap.entityId());
            assertEquals(50L, snap.tickCaptured());
            assertEquals(0.5f, snap.partialTick());
            assertEquals(new Vec3(5, 10, 15), snap.entityPosition());
            assertEquals(90.0f, snap.yBodyRot());
            assertEquals(45.0f, snap.yHeadRot());
            assertEquals(-20.0f, snap.xHeadRot());
            assertTrue(snap.isValid());
            assertTrue(snap.hasTransforms());
            assertEquals(1, snap.getTransformCount());
        }

        @Test
        @DisplayName("addPartTransform stores defensive copy")
        void defensiveCopy() {
            Matrix4f original = new Matrix4f().translate(1, 2, 3);
            AnimationSnapshot snap = AnimationSnapshot.builder(1, 0L, 0f)
                .addPartTransform("bone", original)
                .build();

            original.translate(99, 99, 99); // mutate original
            Matrix4f stored = snap.getPartTransform("bone");
            assertNotNull(stored);
            // stored should not have been affected by mutation
            assertNotEquals(original, stored);
        }

        @Test
        @DisplayName("position(Vec3) works")
        void positionVec3() {
            Vec3 pos = new Vec3(1, 2, 3);
            AnimationSnapshot snap = AnimationSnapshot.builder(1, 0L, 0f)
                .position(pos)
                .build();
            assertEquals(pos, snap.entityPosition());
        }

        @Test
        @DisplayName("valid defaults to true")
        void validDefault() {
            AnimationSnapshot snap = AnimationSnapshot.builder(1, 0L, 0f).build();
            assertTrue(snap.isValid());
        }

        @Test
        @DisplayName("valid can be set to false")
        void validFalse() {
            AnimationSnapshot snap = AnimationSnapshot.builder(1, 0L, 0f)
                .valid(false)
                .build();
            assertFalse(snap.isValid());
        }
    }

    @Nested
    @DisplayName("transform queries")
    class TransformQueryTests {

        @Test
        @DisplayName("getPartTransform returns null for missing bone")
        void missingBone() {
            AnimationSnapshot snap = AnimationSnapshot.simple(1, 0L, Vec3.ZERO, 0, 0, 0);
            assertNull(snap.getPartTransform("head"));
        }

        @Test
        @DisplayName("getPartTransformOrDefault returns default for missing bone")
        void missingBoneDefault() {
            AnimationSnapshot snap = AnimationSnapshot.simple(1, 0L, Vec3.ZERO, 0, 0, 0);
            Matrix4f def = new Matrix4f().translate(1, 0, 0);
            Matrix4f result = snap.getPartTransformOrDefault("head", def);
            assertSame(def, result);
        }

        @Test
        @DisplayName("getPartTransformOrIdentity returns identity for missing bone")
        void missingBoneIdentity() {
            AnimationSnapshot snap = AnimationSnapshot.simple(1, 0L, Vec3.ZERO, 0, 0, 0);
            Matrix4f result = snap.getPartTransformOrIdentity("head");
            assertEquals(new Matrix4f(), result);
        }

        @Test
        @DisplayName("getPartTransformOrDefault returns actual transform when present")
        void presentBone() {
            Matrix4f headTransform = new Matrix4f().rotateX(1.0f);
            AnimationSnapshot snap = AnimationSnapshot.builder(1, 0L, 0f)
                .addPartTransform("head", headTransform)
                .build();

            Matrix4f def = new Matrix4f();
            Matrix4f result = snap.getPartTransformOrDefault("head", def);
            assertNotSame(def, result);
        }

        @Test
        @DisplayName("hasPartTransform returns true for present bone")
        void hasTransformTrue() {
            AnimationSnapshot snap = AnimationSnapshot.builder(1, 0L, 0f)
                .addPartTransform("head", new Matrix4f())
                .build();
            assertTrue(snap.hasPartTransform("head"));
        }

        @Test
        @DisplayName("hasPartTransform returns false for missing bone")
        void hasTransformFalse() {
            AnimationSnapshot snap = AnimationSnapshot.simple(1, 0L, Vec3.ZERO, 0, 0, 0);
            assertFalse(snap.hasPartTransform("head"));
        }
    }

    @Nested
    @DisplayName("staleness")
    class StalenessTests {

        @Test
        @DisplayName("fresh snapshot is not stale")
        void freshNotStale() {
            AnimationSnapshot snap = AnimationSnapshot.simple(1, 100L, Vec3.ZERO, 0, 0, 0);
            assertFalse(snap.isStale(100L));
            assertFalse(snap.isStale(101L));
            assertFalse(snap.isStale(102L)); // MAX_AGE_TICKS = 2
        }

        @Test
        @DisplayName("old snapshot is stale")
        void oldIsStale() {
            AnimationSnapshot snap = AnimationSnapshot.simple(1, 100L, Vec3.ZERO, 0, 0, 0);
            assertTrue(snap.isStale(103L)); // 103 - 100 = 3 > MAX_AGE_TICKS(2)
        }

        @Test
        @DisplayName("invalid snapshot is always stale")
        void invalidAlwaysStale() {
            AnimationSnapshot snap = AnimationSnapshot.builder(1, 100L, 0f)
                .valid(false)
                .build();
            assertTrue(snap.isStale(100L));
        }
    }

    @Test
    @DisplayName("toString includes entity id and tick")
    void toStringTest() {
        AnimationSnapshot snap = AnimationSnapshot.simple(42, 100L, Vec3.ZERO, 0, 0, 0);
        String str = snap.toString();
        assertTrue(str.contains("42"));
        assertTrue(str.contains("100"));
    }

    @Test
    @DisplayName("MAX_AGE_TICKS is 2")
    void maxAgeTicks() {
        assertEquals(2, AnimationSnapshot.MAX_AGE_TICKS);
    }
}
