package com.devmod.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * P2: Thread-local object pool for high-frequency single-threaded hot paths.
 *
 * <p>More efficient than ObjectPool for render loops and other thread-bound code
 * since it requires no synchronization.
 *
 * <p>Usage:
 * <pre>
 * private static final ThreadLocalPool&lt;List&lt;String&gt;&gt; LIST_POOL =
 *     ThreadLocalPool.ofList(ArrayList::new, 5);
 *
 * void render() {
 *     List&lt;String&gt; list = LIST_POOL.acquire();
 *     try {
 *         // ... use list
 *     } finally {
 *         LIST_POOL.release(list);
 *     }
 * }
 * </pre>
 *
 * @param <T> The pooled object type
 */
public final class ThreadLocalPool<T> {

    private final ThreadLocal<PoolState<T>> state;
    private final int maxSize;

    private ThreadLocalPool(Supplier<T> factory, Consumer<T> resetFn, int maxSize) {
        this.maxSize = Math.max(1, maxSize);
        this.state = ThreadLocal.withInitial(() -> new PoolState<>(factory, resetFn, this.maxSize));
    }

    /**
     * Creates a thread-local pool.
     *
     * @param factory Function to create new instances
     * @param resetFn Function to reset an instance for reuse
     * @param maxSize Maximum pool size per thread
     */
    public static <T> ThreadLocalPool<T> create(Supplier<T> factory, Consumer<T> resetFn, int maxSize) {
        return new ThreadLocalPool<>(
            Objects.requireNonNull(factory, "factory"),
            Objects.requireNonNull(resetFn, "resetFn"),
            maxSize
        );
    }

    /**
     * Creates a pool for List implementations.
     */
    public static <E, L extends java.util.List<E>> ThreadLocalPool<L> ofList(Supplier<L> factory, int maxSize) {
        return create(factory, java.util.List::clear, maxSize);
    }

    /**
     * Creates a pool for Map implementations.
     */
    public static <K, V, M extends java.util.Map<K, V>> ThreadLocalPool<M> ofMap(Supplier<M> factory, int maxSize) {
        return create(factory, java.util.Map::clear, maxSize);
    }

    /**
     * Acquires an object from the pool, creating a new one if empty.
     */
    public T acquire() {
        return state.get().acquire();
    }

    /**
     * Releases an object back to the pool.
     */
    public void release(@Nullable T obj) {
        if (obj != null) {
            state.get().release(obj);
        }
    }

    /**
     * Pre-populates the current thread's pool.
     */
    public void prewarm(int count) {
        state.get().prewarm(count);
    }

    /**
     * Returns the maximum pool size.
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Per-thread pool state.
     */
    private static final class PoolState<T> {
        private final Supplier<T> factory;
        private final Consumer<T> resetFn;
        private final Deque<T> pool;
        private final int maxSize;

        PoolState(Supplier<T> factory, Consumer<T> resetFn, int maxSize) {
            this.factory = factory;
            this.resetFn = resetFn;
            this.maxSize = maxSize;
            this.pool = new ArrayDeque<>(maxSize);
        }

        T acquire() {
            T obj = pool.pollFirst();
            return obj != null ? obj : factory.get();
        }

        void release(T obj) {
            try {
                resetFn.accept(obj);
            } catch (Exception e) {
                // Reset failed, discard
                return;
            }

            if (pool.size() < maxSize) {
                pool.addFirst(obj);
            }
        }

        void prewarm(int count) {
            int toCreate = Math.min(count, maxSize - pool.size());
            for (int i = 0; i < toCreate; i++) {
                pool.addFirst(factory.get());
            }
        }
    }
}
