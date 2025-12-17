package com.frenkvs.devmod.ui.editor.core;

/**
 * Thread-local StringBuilder cache to reduce string allocations in render loops.
 *
 * Usage:
 * <pre>
 * StringBuilder sb = StringBuilderCache.acquire();
 * sb.append("Hello ").append(name);
 * String result = StringBuilderCache.release(sb);
 * </pre>
 *
 * @see docs/editor-design-system/19-performance-considerations.md
 */
public final class StringBuilderCache {

    /** Default initial capacity */
    private static final int DEFAULT_CAPACITY = 256;

    /** Maximum capacity to cache (avoids keeping huge buffers) */
    private static final int MAX_CACHED_CAPACITY = 1024;

    /** Thread-local cache */
    private static final ThreadLocal<StringBuilder> CACHE =
        ThreadLocal.withInitial(() -> new StringBuilder(DEFAULT_CAPACITY));

    private StringBuilderCache() {} // Static utility

    /**
     * Acquire a cleared StringBuilder from the cache.
     *
     * @return A cleared StringBuilder ready for use
     */
    public static StringBuilder acquire() {
        StringBuilder sb = CACHE.get();
        sb.setLength(0);
        return sb;
    }

    /**
     * Get the string and release the builder back to cache.
     * If the builder grew too large, replaces it with a fresh one.
     *
     * @param sb The StringBuilder to release
     * @return The built string
     */
    public static String release(StringBuilder sb) {
        if (sb == null) {
            return "";
        }
        String result = sb.toString();

        // If capacity grew too large, replace with fresh builder
        if (sb.capacity() > MAX_CACHED_CAPACITY) {
            CACHE.set(new StringBuilder(DEFAULT_CAPACITY));
        } else {
            sb.setLength(0);
        }

        return result;
    }

    /**
     * Convenience method: acquire, build, and release in one call.
     *
     * @param builder Function that builds the string
     * @return The built string
     */
    public static String build(java.util.function.Consumer<StringBuilder> builder) {
        StringBuilder sb = acquire();
        builder.accept(sb);
        return release(sb);
    }
}
