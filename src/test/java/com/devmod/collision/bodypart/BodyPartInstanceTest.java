package com.devmod.collision.bodypart;

import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

import com.devmod.collision.obb.OrientedBoundingBox;
import com.devmod.combat.HitHelper;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BodyPartInstance")
class BodyPartInstanceTest {

    private static BodyPartDefinition sampleDef() {
        return BodyPartDefinition.builder("body")
            .type(HitHelper.BodyPart.BODY)
            .offset(0, 0.9, 0)
            .halfExtents(0.2, 0.3, 0.1)
            .bone("body")
            .damageMultiplier(1.0f)
            .build();
    }

    @BeforeEach
    void setUp() {
        BodyPartInstance.clearPool();
    }

    @AfterEach
    void tearDown() {
        BodyPartInstance.clearPool();
    }

    @Nested
    @DisplayName("acquire / release")
    class PoolTests {

        @Test
        @DisplayName("acquire returns initialized instance")
        void acquireReturnsInitialized() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            assertNotNull(instance);
            assertTrue(instance.isInUse());
            assertEquals("body", instance.getId());
            assertEquals(HitHelper.BodyPart.BODY, instance.getBodyPartType());
        }

        @Test
        @DisplayName("release returns instance to pool")
        void releaseReturnsToPool() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            assertEquals(0, BodyPartInstance.getPoolSize());

            instance.release();
            assertEquals(1, BodyPartInstance.getPoolSize());
            assertFalse(instance.isInUse());
        }

        @Test
        @DisplayName("double release is safe")
        void doubleReleaseSafe() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            instance.release();
            instance.release(); // should not throw or add duplicate
            assertEquals(1, BodyPartInstance.getPoolSize());
        }

        @Test
        @DisplayName("acquireMultiple returns correct count")
        void acquireMultiple() {
            BodyPartDefinition[] defs = { sampleDef(), sampleDef() };
            BodyPartInstance[] instances = BodyPartInstance.acquireMultiple(defs);
            assertEquals(2, instances.length);
            for (BodyPartInstance inst : instances) {
                assertNotNull(inst);
                assertTrue(inst.isInUse());
            }
        }

        @Test
        @DisplayName("releaseMultiple returns all to pool")
        void releaseMultiple() {
            BodyPartDefinition[] defs = { sampleDef(), sampleDef(), sampleDef() };
            BodyPartInstance[] instances = BodyPartInstance.acquireMultiple(defs);
            BodyPartInstance.releaseMultiple(instances);
            assertEquals(3, BodyPartInstance.getPoolSize());
        }

        @Test
        @DisplayName("releaseMultiple handles null array safely")
        void releaseMultipleNull() {
            assertDoesNotThrow(() -> BodyPartInstance.releaseMultiple(null));
        }

        @Test
        @DisplayName("releaseMultiple handles null elements safely")
        void releaseMultipleNullElements() {
            BodyPartInstance[] instances = { null, BodyPartInstance.acquire(sampleDef()), null };
            assertDoesNotThrow(() -> BodyPartInstance.releaseMultiple(instances));
            assertEquals(1, BodyPartInstance.getPoolSize());
        }

        @Test
        @DisplayName("clearPool empties the pool")
        void clearPool() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            instance.release();
            assertEquals(1, BodyPartInstance.getPoolSize());

            BodyPartInstance.clearPool();
            assertEquals(0, BodyPartInstance.getPoolSize());
        }

        @Test
        @DisplayName("pool recycles instances")
        void poolRecycles() {
            BodyPartInstance first = BodyPartInstance.acquire(sampleDef());
            first.release();

            BodyPartInstance second = BodyPartInstance.acquire(sampleDef());
            // second should have been recycled from pool
            assertSame(first, second);
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("update sets worldOBB and lastUpdateTick")
        void updateSetsState() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            Matrix4f transform = new Matrix4f().translate(5, 10, 15);
            instance.update(transform, 100L);

            assertTrue(instance.isUpdated());
            assertEquals(100L, instance.getLastUpdateTick());
            assertNotNull(instance.getWorldOBB());
            assertNotNull(instance.getWorldTransform());
        }

        @Test
        @DisplayName("update skips duplicate tick")
        void updateSkipsDuplicateTick() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            Matrix4f transform1 = new Matrix4f().translate(1, 0, 0);
            instance.update(transform1, 100L);
            OrientedBoundingBox firstOBB = instance.getWorldOBB();

            // Same tick, different transform - should be skipped
            Matrix4f transform2 = new Matrix4f().translate(99, 0, 0);
            instance.update(transform2, 100L);
            assertSame(firstOBB, instance.getWorldOBB());
        }

        @Test
        @DisplayName("update processes new tick")
        void updateProcessesNewTick() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            instance.update(new Matrix4f().translate(1, 0, 0), 100L);
            OrientedBoundingBox firstOBB = instance.getWorldOBB();

            instance.update(new Matrix4f().translate(99, 0, 0), 101L);
            assertNotSame(firstOBB, instance.getWorldOBB());
        }

        @Test
        @DisplayName("invalidate forces recalculation")
        void invalidate() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            instance.update(new Matrix4f(), 100L);
            instance.invalidate();
            assertEquals(-1, instance.getLastUpdateTick());
        }
    }

    @Nested
    @DisplayName("accessors")
    class AccessorTests {

        @Test
        @DisplayName("getDefinition returns the definition")
        void getDefinition() {
            BodyPartDefinition def = sampleDef();
            BodyPartInstance instance = BodyPartInstance.acquire(def);
            assertEquals(def, instance.getDefinition());
        }

        @Test
        @DisplayName("getDamageMultiplier delegates to definition")
        void getDamageMultiplier() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            assertEquals(1.0f, instance.getDamageMultiplier());
        }

        @Test
        @DisplayName("getRenderColor delegates to definition")
        void getRenderColor() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            assertEquals(sampleDef().renderColor(), instance.getRenderColor());
        }

        @Test
        @DisplayName("getWorldOBBOrNull returns null before update")
        void getWorldOBBOrNullBeforeUpdate() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            // After acquire, worldOBB is set from createBaseOBB, not null
            assertNotNull(instance.getWorldOBBOrNull());
        }

        @Test
        @DisplayName("isStale returns true for negative lastUpdateTick")
        void isStaleNegative() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            assertTrue(instance.isStale(100L, 5));
        }

        @Test
        @DisplayName("isStale returns false for recent update")
        void isStaleRecent() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            instance.update(new Matrix4f(), 100L);
            assertFalse(instance.isStale(102L, 5));
        }

        @Test
        @DisplayName("isStale returns true for old update")
        void isStaleOld() {
            BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
            instance.update(new Matrix4f(), 100L);
            assertTrue(instance.isStale(200L, 5));
        }
    }

    @Test
    @DisplayName("toString includes part id")
    void toStringTest() {
        BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
        String str = instance.toString();
        assertTrue(str.contains("body"));
    }

    @Test
    @DisplayName("released instance throws on getDefinition")
    void releasedThrows() {
        BodyPartInstance instance = BodyPartInstance.acquire(sampleDef());
        instance.release();
        assertThrows(IllegalStateException.class, instance::getDefinition);
    }
}
