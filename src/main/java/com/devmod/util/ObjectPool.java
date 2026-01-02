package com.devmod.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * P2: Generic object pool for reducing allocation pressure in hot paths.
 *
 * <p>Thread-safe implementation using synchronized blocks. For very high-frequency
 * single-threaded use, consider ThreadLocalPool instead.
 *
 * <p>Usage:
 * <pre>
 * ObjectPool&lt;ArrayList&lt;String&gt;&gt; listPool = new ObjectPool&lt;&gt;(
 *     ArrayList::new,  // factory
 *     ArrayList::clear, // reset
 *     10               // max size
 * );
 *
 * ArrayList&lt;String&gt; list = listPool.acquire();
 * try {
 *     list.add("foo");
 *     // ... use list
 * } finally {
 *     listPool.release(list);
 * }
 * </pre>
 *
 * @param <T> The pooled object type
 */
public final class ObjectPool<T> {

    private final Supplier<T> factory;
    private final Consumer<T> resetFn;
    private final Deque<T> pool;
    private final int maxSize;

    // Statistics
    private long acquireCount = 0;
    private long releaseCount = 0;
    private long createCount = 0;
    private long discardCount = 0;

    /**
     * Creates an object pool.
     *
     * @param factory Function to create new instances
     * @param resetFn Function to reset an instance for reuse (e.g., clear() for collections)
     * @param maxSize Maximum pool size (excess objects are discarded on release)
     */
    public ObjectPool(Supplier<T> factory, Consumer<T> resetFn, int maxSize) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.resetFn = Objects.requireNonNull(resetFn, "resetFn");
        this.maxSize = Math.max(1, maxSize);
        this.pool = new ArrayDeque<>(this.maxSize);
    }

    /**
     * Creates an object pool with no reset function.
     */
    public ObjectPool(Supplier<T> factory, int maxSize) {
        this(factory, obj -> {}, maxSize);
    }

    /**
     * Acquires an object from the pool, creating a new one if empty.
     *
     * @return A pooled or new object
     */
    public T acquire() {
        synchronized (pool) {
            acquireCount++;
            T obj = pool.pollFirst();
            if (obj == null) {
                createCount++;
                return factory.get();
            }
            return obj;
        }
    }

    /**
     * Releases an object back to the pool.
     * The reset function is called before pooling.
     * Objects are discarded if pool is at capacity.
     *
     * @param obj The object to release (null is ignored)
     */
    public void release(@Nullable T obj) {
        if (obj == null) {
            return;
        }

        synchronized (pool) {
            releaseCount++;

            // Reset object state
            try {
                resetFn.accept(obj);
            } catch (Exception e) {
                // Reset failed, discard object
                discardCount++;
                return;
            }

            // Pool or discard
            if (pool.size() < maxSize) {
                pool.addFirst(obj);
            } else {
                discardCount++;
            }
        }
    }

    /**
     * Pre-populates the pool with objects.
     *
     * @param count Number of objects to create
     */
    public void prewarm(int count) {
        synchronized (pool) {
            int toCreate = Math.min(count, maxSize - pool.size());
            for (int i = 0; i < toCreate; i++) {
                pool.addFirst(factory.get());
                createCount++;
            }
        }
    }

    /**
     * Clears all pooled objects.
     */
    public void clear() {
        synchronized (pool) {
            pool.clear();
        }
    }

    /**
     * Returns pool statistics.
     */
    public PoolStats getStats() {
        synchronized (pool) {
            return new PoolStats(
                pool.size(),
                maxSize,
                acquireCount,
                releaseCount,
                createCount,
                discardCount
            );
        }
    }

    /**
     * Pool statistics snapshot.
     */
    public record PoolStats(
        int currentSize,
        int maxSize,
        long acquireCount,
        long releaseCount,
        long createCount,
        long discardCount
    ) {
        public double hitRate() {
            if (acquireCount == 0) return 0.0;
            return 1.0 - ((double) createCount / acquireCount);
        }

        public double utilizationRate() {
            if (releaseCount == 0) return 0.0;
            return 1.0 - ((double) discardCount / releaseCount);
        }
    }
}
