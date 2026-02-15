package com.devmod.combat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the reflection method caching in RangedProjectileHooks
 * uses proper double-checked locking and is idempotent.
 */
class RangedProjectileHooksReflectionTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Test
    void getCachedSetPierceMethod_returnsConsistentResult() throws Exception {
        Method accessor = RangedProjectileHooks.class.getDeclaredMethod("getCachedSetPierceMethod");
        accessor.setAccessible(true);

        Object first = accessor.invoke(null);
        Object second = accessor.invoke(null);

        // Both calls should return the exact same Method instance (or both null)
        assertSame(first, second, "Cached method must be the same instance on repeated calls");
    }

    @Test
    void getCachedSetPierceMethod_isIdempotentAcrossMultipleCalls() throws Exception {
        Method accessor = RangedProjectileHooks.class.getDeclaredMethod("getCachedSetPierceMethod");
        accessor.setAccessible(true);

        // Call many times to verify no state corruption
        Object baseline = accessor.invoke(null);
        for (int i = 0; i < 100; i++) {
            assertSame(baseline, accessor.invoke(null),
                "Call #" + i + " returned a different instance");
        }
    }

    @Test
    void pierceLookupAttempted_isVolatile() throws Exception {
        Field field = RangedProjectileHooks.class.getDeclaredField("pierceLookupAttempted");
        assertTrue(java.lang.reflect.Modifier.isVolatile(field.getModifiers()),
            "pierceLookupAttempted must be volatile for thread-safe DCL");
    }

    @Test
    void setPierceField_isVolatile() throws Exception {
        Field field = RangedProjectileHooks.class.getDeclaredField("SET_PIERCE");
        assertTrue(java.lang.reflect.Modifier.isVolatile(field.getModifiers()),
            "SET_PIERCE must be volatile for thread-safe DCL");
    }
}
