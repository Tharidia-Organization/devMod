package com.devmod.runtime;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DimensionLockManagerTest {

    @AfterEach
    void cleanup() {
        DimensionLockManager.INSTANCE.shutdown();
    }

    @Test
    @DisplayName("tryLock returns non-null handle for valid UUID")
    void tryLockReturnsHandle() {
        UUID id = UUID.randomUUID();
        try (DimensionLockManager.LockHandle handle = DimensionLockManager.INSTANCE.tryLock(id, 100)) {
            assertNotNull(handle);
            assertTrue(handle.isAcquired());
            assertEquals(id, handle.getInstanceId());
        }
    }

    @Test
    @DisplayName("tryLock returns null for null UUID")
    void tryLockReturnsNullForNull() {
        assertNull(DimensionLockManager.INSTANCE.tryLock(null, 100));
    }

    @Test
    @DisplayName("isLocked returns true when lock is held")
    void isLockedWhenHeld() {
        UUID id = UUID.randomUUID();
        assertFalse(DimensionLockManager.INSTANCE.isLocked(id));

        try (DimensionLockManager.LockHandle handle = DimensionLockManager.INSTANCE.tryLock(id)) {
            assertNotNull(handle);
            assertTrue(DimensionLockManager.INSTANCE.isLocked(id));
        }

        assertFalse(DimensionLockManager.INSTANCE.isLocked(id));
    }

    @Test
    @DisplayName("isLocked returns false for null UUID")
    void isLockedFalseForNull() {
        assertFalse(DimensionLockManager.INSTANCE.isLocked(null));
    }

    @Test
    @DisplayName("LockHandle.close releases the lock")
    void handleCloseReleasesLock() {
        UUID id = UUID.randomUUID();
        DimensionLockManager.LockHandle handle = DimensionLockManager.INSTANCE.tryLock(id);
        assertNotNull(handle);
        assertTrue(handle.isAcquired());

        handle.close();
        assertFalse(handle.isAcquired());
        assertFalse(DimensionLockManager.INSTANCE.isLocked(id));
    }

    @Test
    @DisplayName("Double release is safe")
    void doubleReleaseIsSafe() {
        UUID id = UUID.randomUUID();
        DimensionLockManager.LockHandle handle = DimensionLockManager.INSTANCE.tryLock(id);
        assertNotNull(handle);

        handle.release();
        handle.release(); // Should not throw
        assertFalse(handle.isAcquired());
    }

    @Test
    @DisplayName("Reentrant lock allows same thread to acquire twice")
    void reentrantLockAllowsSameThread() {
        UUID id = UUID.randomUUID();
        try (DimensionLockManager.LockHandle h1 = DimensionLockManager.INSTANCE.tryLock(id)) {
            assertNotNull(h1);
            try (DimensionLockManager.LockHandle h2 = DimensionLockManager.INSTANCE.tryLock(id)) {
                assertNotNull(h2);
                assertTrue(h2.isAcquired());
            }
            assertTrue(h1.isAcquired());
        }
    }

    @Test
    @DisplayName("cleanupLock removes the lock entry")
    void cleanupLockRemovesEntry() {
        UUID id = UUID.randomUUID();
        try (DimensionLockManager.LockHandle handle = DimensionLockManager.INSTANCE.tryLock(id)) {
            assertNotNull(handle);
        }
        // After releasing, cleanup should succeed
        DimensionLockManager.INSTANCE.cleanupLock(id);
        assertFalse(DimensionLockManager.INSTANCE.isLocked(id));
    }

    @Test
    @DisplayName("cleanupLock with null UUID is safe")
    void cleanupLockNullIsSafe() {
        DimensionLockManager.INSTANCE.cleanupLock(null); // Should not throw
    }

    @Test
    @DisplayName("withMapLock executes action atomically")
    void withMapLockExecutesAction() {
        int[] counter = {0};
        DimensionLockManager.INSTANCE.withMapLock(() -> counter[0]++);
        assertEquals(1, counter[0]);
    }

    @Test
    @DisplayName("withMapLock supplier returns result")
    void withMapLockSupplierReturnsResult() {
        String result = DimensionLockManager.INSTANCE.withMapLock(() -> "test");
        assertEquals("test", result);
    }

    @Test
    @DisplayName("shutdown clears all locks")
    void shutdownClearsAllLocks() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        try (var h1 = DimensionLockManager.INSTANCE.tryLock(id1);
             var h2 = DimensionLockManager.INSTANCE.tryLock(id2)) {
            // locks acquired
        }

        DimensionLockManager.INSTANCE.shutdown();
        assertFalse(DimensionLockManager.INSTANCE.isLocked(id1));
        assertFalse(DimensionLockManager.INSTANCE.isLocked(id2));
    }
}
