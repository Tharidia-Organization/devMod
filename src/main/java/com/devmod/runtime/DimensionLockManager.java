package com.devmod.runtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides locking mechanism for dimension/instance operations to prevent race conditions.
 *
 * Use cases:
 * 1. Atomic map updates during dimension creation (dimensionToInstance + instanceToDimension)
 * 2. Prevent teleport attempts to dimensions being destroyed
 * 3. Coordinate concurrent access to instance state
 *
 * Uses per-instance locks with automatic cleanup to prevent memory leaks.
 */
public class DimensionLockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DimensionLockManager.class);
    public static final DimensionLockManager INSTANCE = new DimensionLockManager();

    private static final long DEFAULT_TIMEOUT_MS = 5000;

    // Per-instance locks - cleaned up when instance is destroyed
    private final Map<UUID, ReentrantLock> instanceLocks = new ConcurrentHashMap<>();

    // Global lock for dimension map updates (lightweight, used only for map consistency)
    private final Object mapUpdateLock = new Object();

    private DimensionLockManager() {}

    /**
     * Execute an action with exclusive access to the dimension maps.
     * Used for atomic updates to dimensionToInstance and instanceToDimension maps.
     *
     * @param action The action to execute atomically
     */
    public void withMapLock(Runnable action) {
        synchronized (mapUpdateLock) {
            action.run();
        }
    }

    /**
     * Execute an action with exclusive access to the dimension maps, returning a result.
     *
     * @param action The action to execute atomically
     * @return The result of the action
     */
    public <T> T withMapLock(java.util.function.Supplier<T> action) {
        synchronized (mapUpdateLock) {
            return action.get();
        }
    }

    /**
     * Acquire an instance lock. Returns a LockHandle that must be closed to release.
     * Uses try-with-resources pattern for automatic release.
     *
     * @param instanceId The instance to lock
     * @return LockHandle, or null if lock could not be acquired
     */
    public LockHandle tryLock(UUID instanceId) {
        return tryLock(instanceId, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Acquire an instance lock with custom timeout.
     *
     * @param instanceId The instance to lock
     * @param timeoutMs Maximum time to wait for lock in milliseconds
     * @return LockHandle, or null if lock could not be acquired within timeout
     */
    public LockHandle tryLock(UUID instanceId, long timeoutMs) {
        if (instanceId == null) {
            return null;
        }

        ReentrantLock lock = instanceLocks.computeIfAbsent(instanceId, k -> new ReentrantLock());

        try {
            if (lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
                return new LockHandle(instanceId, lock);
            } else {
                LOGGER.warn("[DimensionLock] Failed to acquire lock for instance {} within {}ms",
                    instanceId, timeoutMs);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("[DimensionLock] Interrupted while acquiring lock for instance {}", instanceId);
            return null;
        }
    }

    /**
     * Check if an instance lock is currently held.
     *
     * @param instanceId The instance to check
     * @return true if the lock is held by any thread
     */
    public boolean isLocked(UUID instanceId) {
        if (instanceId == null) {
            return false;
        }
        ReentrantLock lock = instanceLocks.get(instanceId);
        return lock != null && lock.isLocked();
    }

    /**
     * Remove the lock entry for a destroyed instance to prevent memory leaks.
     * Should be called after instance destruction is complete.
     *
     * @param instanceId The instance whose lock should be removed
     */
    public void cleanupLock(UUID instanceId) {
        if (instanceId == null) {
            return;
        }
        ReentrantLock lock = instanceLocks.remove(instanceId);
        if (lock != null && lock.isLocked()) {
            LOGGER.warn("[DimensionLock] Cleaning up lock for instance {} while still locked - " +
                "this may indicate a bug", instanceId);
        }
    }

    /**
     * Clear all locks. Called during server shutdown.
     */
    public void shutdown() {
        instanceLocks.clear();
        LOGGER.info("[DimensionLock] Shutdown complete");
    }

    /**
     * Handle returned by tryLock. Must be closed to release the lock.
     * Implements AutoCloseable for try-with-resources support.
     */
    public static class LockHandle implements AutoCloseable {
        private final UUID instanceId;
        private final ReentrantLock lock;
        private boolean released = false;

        private LockHandle(UUID instanceId, ReentrantLock lock) {
            this.instanceId = instanceId;
            this.lock = lock;
        }

        public UUID getInstanceId() {
            return instanceId;
        }

        public boolean isAcquired() {
            return !released && lock.isHeldByCurrentThread();
        }

        @Override
        public void close() {
            release();
        }

        public void release() {
            if (!released && lock.isHeldByCurrentThread()) {
                lock.unlock();
                released = true;
            }
        }
    }
}
