package com.devmod.ui.editor.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Object pool for frequently created render objects to reduce GC pressure.
 *
 * Pools reusable objects like StringBuilders, ArrayLists, and int arrays
 * that would otherwise be created every frame.
 *
 * @see docs/editor-design-system/19-performance-considerations.md
 */
public final class RenderObjectPool {

    // Pool size limits
    private static final int MAX_STRING_BUILDERS = 16;
    private static final int MAX_STRING_LISTS = 16;
    private static final int MAX_INT_ARRAYS = 8;

    // Capacity limits to avoid pooling huge objects
    private static final int MAX_BUILDER_CAPACITY = 1024;
    private static final int MAX_LIST_SIZE = 100;
    private static final int MAX_ARRAY_LENGTH = 256;

    // Pools
    private final Deque<StringBuilder> stringBuilders = new ArrayDeque<>();
    private final Deque<ArrayList<String>> stringLists = new ArrayDeque<>();
    private final Deque<int[]> intArrays = new ArrayDeque<>();

    // Stats
    private long borrows = 0;
    private long returns = 0;
    private long allocations = 0;

    // Singleton (eager initialization - thread-safe)
    public static final RenderObjectPool INSTANCE = new RenderObjectPool();

    private RenderObjectPool() {}

    // ═══════════════════════════════════════════════════════════════
    // STRING BUILDER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Borrow a cleared StringBuilder from the pool.
     */
    public StringBuilder borrowStringBuilder() {
        borrows++;
        StringBuilder sb = stringBuilders.poll();
        if (sb == null) {
            allocations++;
            sb = new StringBuilder(256);
        } else {
            sb.setLength(0);
        }
        return sb;
    }

    /**
     * Return a StringBuilder to the pool.
     */
    public void returnStringBuilder(StringBuilder sb) {
        if (sb == null) return;

        returns++;

        // Don't pool huge builders
        if (sb.capacity() > MAX_BUILDER_CAPACITY) {
            return;
        }

        // Don't exceed pool size
        if (stringBuilders.size() < MAX_STRING_BUILDERS) {
            sb.setLength(0);
            stringBuilders.offer(sb);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STRING LIST
    // ═══════════════════════════════════════════════════════════════

    /**
     * Borrow a cleared ArrayList<String> from the pool.
     */
    public List<String> borrowStringList() {
        borrows++;
        ArrayList<String> list = stringLists.poll();
        if (list == null) {
            allocations++;
            list = new ArrayList<>(16);
        } else {
            list.clear();
        }
        return list;
    }

    /**
     * Return a string list to the pool.
     */
    public void returnStringList(List<String> list) {
        if (list == null) return;
        if (!(list instanceof ArrayList)) return;

        returns++;

        ArrayList<String> arrayList = (ArrayList<String>) list;

        // Don't pool huge lists
        if (arrayList.size() > MAX_LIST_SIZE) {
            return;
        }

        // Don't exceed pool size
        if (stringLists.size() < MAX_STRING_LISTS) {
            arrayList.clear();
            stringLists.offer(arrayList);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INT ARRAY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Borrow an int array of at least the specified size.
     */
    public int[] borrowIntArray(int minSize) {
        borrows++;

        // Try to find one big enough
        for (var it = intArrays.iterator(); it.hasNext();) {
            int[] arr = it.next();
            if (arr.length >= minSize) {
                it.remove();
                return arr;
            }
        }

        // Allocate new
        allocations++;
        return new int[Math.max(minSize, 32)];
    }

    /**
     * Return an int array to the pool.
     */
    public void returnIntArray(int[] array) {
        if (array == null) return;

        returns++;

        // Don't pool huge arrays
        if (array.length > MAX_ARRAY_LENGTH) {
            return;
        }

        // Don't exceed pool size
        if (intArrays.size() < MAX_INT_ARRAYS) {
            intArrays.offer(array);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STATS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get pool statistics.
     */
    public PoolStats getStats() {
        return new PoolStats(
            stringBuilders.size(),
            stringLists.size(),
            intArrays.size(),
            borrows,
            returns,
            allocations
        );
    }

    /**
     * Clear all pools and reset stats.
     */
    public void clear() {
        stringBuilders.clear();
        stringLists.clear();
        intArrays.clear();
        borrows = 0;
        returns = 0;
        allocations = 0;
    }

    /**
     * Pool statistics record.
     */
    public record PoolStats(
        int stringBuilders,
        int stringLists,
        int intArrays,
        long borrows,
        long returns,
        long allocations
    ) {
        public double reuseRate() {
            return allocations == 0 ? 1.0 : 1.0 - ((double) allocations / borrows);
        }
    }
}
