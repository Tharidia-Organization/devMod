package com.devmod.combat;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.combat.tracking.EvasionHandler;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EvasionHandler millis-based evasion tracking.
 *
 * NOTE: recordAttackAttempt/confirmHit require Player/LivingEntity parameters
 * which cannot be mocked in a test JVM without Minecraft bootstrap.
 * We test the internal maps directly via reflection, and the cleanup()
 * method which takes no parameters (uses System.currentTimeMillis internally).
 */
@DisplayName("EvasionHandler")
public class EvasionHandlerTest {

    @BeforeEach
    void setUp() {
        EvasionHandler.shutdown(); // Clears all internal state
    }

    // =============================================================
    // shutdown
    // =============================================================

    @Nested
    @DisplayName("shutdown")
    class ShutdownTests {

        @Test
        @DisplayName("Shutdown clears all state")
        void shutdownClearsAll() throws Exception {
            // Insert entries directly
            getPendingAttacks().put(42, createPendingAttack(System.currentTimeMillis()));
            getConfirmedHits().put(42, System.currentTimeMillis());

            EvasionHandler.shutdown();

            assertTrue(getPendingAttacks().isEmpty());
            assertTrue(getConfirmedHits().isEmpty());
        }

        @Test
        @DisplayName("Double shutdown is safe")
        void doubleShutdownSafe() {
            EvasionHandler.shutdown();
            assertDoesNotThrow(EvasionHandler::shutdown);
        }

        @Test
        @DisplayName("Shutdown on empty state is safe")
        void shutdownEmptySafe() {
            assertDoesNotThrow(EvasionHandler::shutdown);
        }
    }

    // =============================================================
    // cleanup() — removes stale entries based on millis
    // =============================================================

    @Nested
    @DisplayName("cleanup()")
    class CleanupTests {

        @Test
        @DisplayName("Cleanup on empty state is safe")
        void cleanupEmptySafe() {
            assertDoesNotThrow(EvasionHandler::cleanup);
        }

        @Test
        @DisplayName("Stale pending entries removed after STALE_ENTRY_AGE_MS")
        void stalePendingRemoved() throws Exception {
            long staleTime = System.currentTimeMillis() - 15_000; // 15s ago, well past 10s TTL
            getPendingAttacks().put(42, createPendingAttack(staleTime));

            // Force cleanup interval to pass
            resetLastCleanupTime(0L);
            EvasionHandler.cleanup();

            assertTrue(getPendingAttacks().isEmpty());
        }

        @Test
        @DisplayName("Fresh pending entries kept during cleanup")
        void freshPendingKept() throws Exception {
            long freshTime = System.currentTimeMillis(); // just now
            getPendingAttacks().put(42, createPendingAttack(freshTime));

            resetLastCleanupTime(0L);
            EvasionHandler.cleanup();

            // Fresh entry should still be present
            assertEquals(1, getPendingAttacks().size());
        }

        @Test
        @DisplayName("Stale confirmed hits removed after STALE_ENTRY_AGE_MS")
        void staleConfirmedHitsRemoved() throws Exception {
            long staleTime = System.currentTimeMillis() - 15_000; // 15s ago
            getConfirmedHits().put(42, staleTime);

            resetLastCleanupTime(0L);
            EvasionHandler.cleanup();

            assertTrue(getConfirmedHits().isEmpty());
        }

        @Test
        @DisplayName("Fresh confirmed hits kept during cleanup")
        void freshConfirmedHitsKept() throws Exception {
            long freshTime = System.currentTimeMillis();
            getConfirmedHits().put(42, freshTime);

            resetLastCleanupTime(0L);
            EvasionHandler.cleanup();

            assertTrue(getConfirmedHits().containsKey(42));
        }

        @Test
        @DisplayName("Cleanup below interval threshold skips cleanup")
        void belowIntervalSkipsCleanup() throws Exception {
            long staleTime = System.currentTimeMillis() - 15_000;
            getConfirmedHits().put(42, staleTime);

            // Set lastCleanupTime to recent so interval check fails
            resetLastCleanupTime(System.currentTimeMillis());
            EvasionHandler.cleanup();

            // Stale confirmed hit still present because interval hasn't passed
            assertTrue(getConfirmedHits().containsKey(42));
        }

        @Test
        @DisplayName("Multiple entities tracked simultaneously")
        void multipleEntities() throws Exception {
            long staleTime = System.currentTimeMillis() - 15_000;
            long freshTime = System.currentTimeMillis();

            getPendingAttacks().put(42, createPendingAttack(staleTime));
            getPendingAttacks().put(43, createPendingAttack(freshTime));

            resetLastCleanupTime(0L);
            EvasionHandler.cleanup();

            // Entity 42 (stale) removed, entity 43 (fresh) kept
            assertFalse(getPendingAttacks().containsKey(42));
            assertTrue(getPendingAttacks().containsKey(43));
        }
    }

    // =============================================================
    // Constants verification
    // =============================================================

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("CLEANUP_INTERVAL_MS is 30000 (30 seconds)")
        void cleanupInterval() throws Exception {
            Field field = EvasionHandler.class.getDeclaredField("CLEANUP_INTERVAL_MS");
            field.setAccessible(true);
            assertEquals(30_000L, field.getLong(null));
        }

        @Test
        @DisplayName("STALE_ENTRY_AGE_MS is 10000 (10 seconds)")
        void staleEntryAge() throws Exception {
            Field field = EvasionHandler.class.getDeclaredField("STALE_ENTRY_AGE_MS");
            field.setAccessible(true);
            assertEquals(10_000L, field.getLong(null));
        }
    }

    // =============================================================
    // Helpers: reflective access to private fields
    // =============================================================

    @SuppressWarnings("unchecked")
    private static Map<Integer, Object> getPendingAttacks() throws Exception {
        Field field = EvasionHandler.class.getDeclaredField("pendingAttacks");
        field.setAccessible(true);
        return (Map<Integer, Object>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Long> getConfirmedHits() throws Exception {
        Field field = EvasionHandler.class.getDeclaredField("confirmedHits");
        field.setAccessible(true);
        return (Map<Integer, Long>) field.get(null);
    }

    private static void resetLastCleanupTime(long value) throws Exception {
        Field field = EvasionHandler.class.getDeclaredField("lastCleanupTime");
        field.setAccessible(true);
        field.setLong(null, value);
    }

    /**
     * Creates a PendingAttack record via reflection (it's a private record).
     * PendingAttack(long timestamp, Vec3 targetPosition, Vec3 playerEye, Vec3 lookDir)
     */
    private static Object createPendingAttack(long timestamp) throws Exception {
        Class<?> pendingClass = null;
        for (Class<?> inner : EvasionHandler.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("PendingAttack")) {
                pendingClass = inner;
                break;
            }
        }
        assertNotNull(pendingClass, "PendingAttack inner class not found");

        var constructor = pendingClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        // PendingAttack(long timestamp, Vec3 targetPosition, Vec3 playerEye, Vec3 lookDir)
        // We pass null for Vec3 fields since cleanup doesn't use them
        return constructor.newInstance(timestamp, null, null, null);
    }
}
