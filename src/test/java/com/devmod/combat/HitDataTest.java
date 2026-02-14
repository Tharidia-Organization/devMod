package com.devmod.combat;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HitData millis-based storage and expiration.
 *
 * NOTE: HitData.store/retrieve take Entity parameters which cannot be mocked
 * in a test JVM without Minecraft bootstrap. We test the internal map and
 * HitInfo record directly using reflection, plus the cleanup() method
 * which takes no parameters (uses System.currentTimeMillis internally).
 */
@DisplayName("HitData")
public class HitDataTest {

    @BeforeEach
    void setUp() throws Exception {
        // Clear the CONTEXT map between tests
        getContextMap().clear();
    }

    // =============================================================
    // HitInfo record tests (pure POJO, no Minecraft)
    // =============================================================

    @Nested
    @DisplayName("HitInfo record")
    class HitInfoTests {

        @Test
        @DisplayName("HitInfo stores all fields correctly")
        void storesAllFields() {
            var info = new HitData.HitInfo(
                    HitHelper.BodyPart.HEAD, false, 3.5f, 0.2f, null, 100L);
            assertEquals(HitHelper.BodyPart.HEAD, info.bodyPart());
            assertFalse(info.isRanged());
            assertEquals(3.5f, info.armorPenBonus(), 0.001f);
            assertEquals(0.2f, info.armorReduction(), 0.001f);
            assertNull(info.weaponItem());
            assertEquals(100L, info.timestamp());
        }

        @Test
        @DisplayName("HitInfo with ranged flag")
        void rangedFlag() {
            var info = new HitData.HitInfo(
                    HitHelper.BodyPart.ARMS, true, 0f, 0f, null, 200L);
            assertTrue(info.isRanged());
            assertEquals(HitHelper.BodyPart.ARMS, info.bodyPart());
        }

        @Test
        @DisplayName("HitInfo records are equal by value")
        void recordEquality() {
            var a = new HitData.HitInfo(HitHelper.BodyPart.LEGS, false, 1f, 0.5f, null, 300L);
            var b = new HitData.HitInfo(HitHelper.BodyPart.LEGS, false, 1f, 0.5f, null, 300L);
            assertEquals(a, b);
        }

        @Test
        @DisplayName("HitInfo with different timestamps are not equal")
        void differentTimestamps() {
            var a = new HitData.HitInfo(HitHelper.BodyPart.BODY, false, 0f, 0f, null, 100L);
            var b = new HitData.HitInfo(HitHelper.BodyPart.BODY, false, 0f, 0f, null, 200L);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("HitInfo with different body parts are not equal")
        void differentBodyParts() {
            var a = new HitData.HitInfo(HitHelper.BodyPart.HEAD, false, 0f, 0f, null, 100L);
            var b = new HitData.HitInfo(HitHelper.BodyPart.LEGS, false, 0f, 0f, null, 100L);
            assertNotEquals(a, b);
        }
    }

    // =============================================================
    // Internal map operations via reflection
    // =============================================================

    @Nested
    @DisplayName("Internal context map operations")
    class ContextMapTests {

        @Test
        @DisplayName("Map starts empty after setUp")
        void mapStartsEmpty() throws Exception {
            assertTrue(getContextMap().isEmpty());
        }

        @Test
        @DisplayName("Inserting and retrieving from context map")
        void insertAndRetrieve() throws Exception {
            UUID targetId = UUID.randomUUID();
            var info = new HitData.HitInfo(HitHelper.BodyPart.HEAD, false, 0f, 0f, null, 100L);
            getContextMap().put(targetId, info);

            assertEquals(1, getContextMap().size());
            assertEquals(info, getContextMap().get(targetId));
        }

        @Test
        @DisplayName("Multiple entries coexist")
        void multipleEntries() throws Exception {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            getContextMap().put(id1, new HitData.HitInfo(HitHelper.BodyPart.HEAD, false, 0f, 0f, null, 100L));
            getContextMap().put(id2, new HitData.HitInfo(HitHelper.BodyPart.LEGS, true, 1f, 0f, null, 101L));

            assertEquals(2, getContextMap().size());
            assertEquals(HitHelper.BodyPart.HEAD, getContextMap().get(id1).bodyPart());
            assertEquals(HitHelper.BodyPart.LEGS, getContextMap().get(id2).bodyPart());
        }

        @Test
        @DisplayName("Overwriting same UUID replaces entry")
        void overwriteSameUuid() throws Exception {
            UUID id = UUID.randomUUID();
            getContextMap().put(id, new HitData.HitInfo(HitHelper.BodyPart.HEAD, false, 0f, 0f, null, 100L));
            getContextMap().put(id, new HitData.HitInfo(HitHelper.BodyPart.LEGS, true, 5f, 0f, null, 200L));

            assertEquals(1, getContextMap().size());
            var info = getContextMap().get(id);
            assertEquals(HitHelper.BodyPart.LEGS, info.bodyPart());
            assertTrue(info.isRanged());
            assertEquals(200L, info.timestamp());
        }
    }

    // =============================================================
    // cleanup() - millis-based expiration
    // =============================================================

    @Nested
    @DisplayName("cleanup()")
    class CleanupTests {

        @Test
        @DisplayName("Cleanup removes entries older than EXPIRATION_MS (100ms)")
        void removesExpiredEntries() throws Exception {
            UUID id = UUID.randomUUID();
            // Insert with a timestamp far in the past
            long staleTime = System.currentTimeMillis() - 500; // 500ms ago, well past 100ms TTL
            getContextMap().put(id, new HitData.HitInfo(HitHelper.BodyPart.HEAD, false, 0f, 0f, null, staleTime));

            HitData.cleanup();
            assertTrue(getContextMap().isEmpty());
        }

        @Test
        @DisplayName("Cleanup keeps fresh entries")
        void keepsFreshEntries() throws Exception {
            UUID id = UUID.randomUUID();
            long freshTime = System.currentTimeMillis(); // just now
            getContextMap().put(id, new HitData.HitInfo(HitHelper.BodyPart.BODY, false, 0f, 0f, null, freshTime));

            HitData.cleanup();
            assertEquals(1, getContextMap().size());
        }

        @Test
        @DisplayName("Cleanup selectively removes only expired entries")
        void selectiveCleanup() throws Exception {
            UUID old = UUID.randomUUID();
            UUID fresh = UUID.randomUUID();
            long staleTime = System.currentTimeMillis() - 500; // 500ms ago
            long freshTime = System.currentTimeMillis(); // just now
            getContextMap().put(old, new HitData.HitInfo(HitHelper.BodyPart.HEAD, false, 0f, 0f, null, staleTime));
            getContextMap().put(fresh, new HitData.HitInfo(HitHelper.BodyPart.LEGS, false, 0f, 0f, null, freshTime));

            HitData.cleanup();
            assertEquals(1, getContextMap().size());
            assertNotNull(getContextMap().get(fresh));
            assertNull(getContextMap().get(old));
        }

        @Test
        @DisplayName("Cleanup on empty map is safe")
        void emptyMapSafe() {
            assertDoesNotThrow(HitData::cleanup);
        }

        @Test
        @DisplayName("Multiple cleanups are idempotent")
        void multipleCleanups() throws Exception {
            UUID id = UUID.randomUUID();
            long staleTime = System.currentTimeMillis() - 500;
            getContextMap().put(id, new HitData.HitInfo(HitHelper.BodyPart.BODY, false, 0f, 0f, null, staleTime));

            HitData.cleanup();
            assertTrue(getContextMap().isEmpty());

            HitData.cleanup();
            assertTrue(getContextMap().isEmpty());
        }
    }

    // =============================================================
    // Expiration constant verification
    // =============================================================

    @Nested
    @DisplayName("Expiration constant")
    class ExpirationConstantTests {

        @Test
        @DisplayName("EXPIRATION_MS is 100 (100 milliseconds)")
        void expirationIs100Ms() throws Exception {
            Field field = HitData.class.getDeclaredField("EXPIRATION_MS");
            field.setAccessible(true);
            assertEquals(100L, field.getLong(null));
        }
    }

    // =============================================================
    // Helper: reflective access to private CONTEXT map
    // =============================================================

    @SuppressWarnings("unchecked")
    private static Map<UUID, HitData.HitInfo> getContextMap() throws Exception {
        Field field = HitData.class.getDeclaredField("CONTEXT");
        field.setAccessible(true);
        return (Map<UUID, HitData.HitInfo>) field.get(null);
    }
}
