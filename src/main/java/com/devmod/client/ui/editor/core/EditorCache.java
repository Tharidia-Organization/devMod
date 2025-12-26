package com.devmod.client.ui.editor.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.annotation.Nullable;

public class EditorCache {

    // ═══════════════════════════════════════════════════════════════
    // CACHE KEY
    // ═══════════════════════════════════════════════════════════════

    public record CacheKey(
        String type,
        String itemId,
        int version
    ) {}

    // ═══════════════════════════════════════════════════════════════
    // CACHE ENTRY
    // ═══════════════════════════════════════════════════════════════

    public record CacheEntry<T>(
        T value,
        long computedAt,
        long expiresAt
    ) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public static <T> CacheEntry<T> of(T value, long ttlMs) {
            long now = System.currentTimeMillis();
            return new CacheEntry<>(value, now, now + ttlMs);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CACHE STATS
    // ═══════════════════════════════════════════════════════════════

    public record CacheStats(
        int total,
        int valid,
        int expired,
        int version,
        long hits,
        long misses
    ) {
        public double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0 : (double) hits / total;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    /** Default TTL for generic cache entries */
    private static final long DEFAULT_TTL_MS = 5000;

    /** TTL for stats calculations (shorter for responsiveness) */
    private static final long STATS_TTL_MS = 1000;

    /** TTL for preview/tooltip renders (very short) */
    private static final long PREVIEW_TTL_MS = 100;

    /** Maximum cache entries before cleanup */
    private static final int MAX_ENTRIES = 100;

    // ═══════════════════════════════════════════════════════════════
    // INSTANCE FIELDS
    // ═══════════════════════════════════════════════════════════════

    private final Map<CacheKey, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final AtomicInteger version = new AtomicInteger(0);
    private long hits = 0;
    private long misses = 0;

    // ═══════════════════════════════════════════════════════════════
    // SINGLETON
    // ═══════════════════════════════════════════════════════════════

    public static final EditorCache INSTANCE = new EditorCache();

    private EditorCache() {}

    // ═══════════════════════════════════════════════════════════════
    // CACHE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get cached value or compute if missing/expired.
     *
     * @param type       Cache type (e.g., "dps", "ehp", "tooltip")
     * @param itemId     Item identifier
     * @param valueClass Class token for type-safe casting
     * @param computer   Supplier to compute the value if not cached
     * @return Cached or newly computed value
     */
    public <T> T getOrCompute(String type, String itemId, Class<T> valueClass, Supplier<T> computer) {
        CacheKey key = new CacheKey(type, itemId, version.get());

        CacheEntry<?> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            hits++;
            Object value = entry.value();
            if (valueClass.isInstance(value)) {
                return valueClass.cast(value);
            }
            // Type mismatch - invalidate and recompute
            cache.remove(key);
        }

        misses++;

        // Compute new value
        T value = computer.get();

        // Determine TTL based on type
        long ttl = getTtlForType(type);

        // Store in cache
        cache.put(key, CacheEntry.of(value, ttl));

        // Cleanup old entries if needed
        if (cache.size() > MAX_ENTRIES) {
            cleanupExpired();
        }

        return value;
    }

    /**
     * Get cached value without computing (type-safe version).
     *
     * @param valueClass Class token for type-safe casting
     * @return Cached value or null if not found/expired/type mismatch
     */
    @Nullable
    public <T> T get(String type, String itemId, Class<T> valueClass) {
        CacheKey key = new CacheKey(type, itemId, version.get());
        CacheEntry<?> entry = cache.get(key);

        if (entry != null && !entry.isExpired()) {
            Object value = entry.value();
            if (valueClass.isInstance(value)) {
                hits++;
                return valueClass.cast(value);
            }
        }

        misses++;
        return null;
    }

    /**
     * Put a value directly into the cache.
     */
    public <T> void put(String type, String itemId, T value) {
        CacheKey key = new CacheKey(type, itemId, version.get());
        long ttl = getTtlForType(type);
        cache.put(key, CacheEntry.of(value, ttl));
    }

    /**
     * Put a value with custom TTL.
     */
    public <T> void put(String type, String itemId, T value, long ttlMs) {
        CacheKey key = new CacheKey(type, itemId, version.get());
        cache.put(key, CacheEntry.of(value, ttlMs));
    }

    // ═══════════════════════════════════════════════════════════════
    // INVALIDATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Invalidate all caches (called on any change).
     */
    public void invalidateAll() {
        version.incrementAndGet();
        cache.clear();
    }

    /**
     * Invalidate cache for specific item.
     */
    public void invalidateItem(String itemId) {
        cache.entrySet().removeIf(e -> e.getKey().itemId().equals(itemId));
    }

    /**
     * Invalidate specific cache type.
     */
    public void invalidateType(String type) {
        cache.entrySet().removeIf(e -> e.getKey().type().equals(type));
    }

    /**
     * Remove expired entries.
     */
    public void cleanupExpired() {
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    // ═══════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get cache statistics for debugging.
     */
    public CacheStats getStats() {
        int total = cache.size();
        int expired = (int) cache.values().stream().filter(CacheEntry::isExpired).count();
        return new CacheStats(total, total - expired, expired, version.get(), hits, misses);
    }

    /**
     * Reset hit/miss counters.
     */
    public void resetStats() {
        hits = 0;
        misses = 0;
    }

    /**
     * Get current version number.
     */
    public int getVersion() {
        return version.get();
    }

    // ═══════════════════════════════════════════════════════════════
    // TTL DETERMINATION
    // ═══════════════════════════════════════════════════════════════

    private long getTtlForType(String type) {
        return switch (type) {
            case "preview", "tooltip", "comparison" -> PREVIEW_TTL_MS;
            case "weapon_stats", "armor_stats", "dps", "ehp", "stats" -> STATS_TTL_MS;
            default -> DEFAULT_TTL_MS;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // CACHE TYPE CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    public static final class Types {
        public static final String DPS = "dps";
        public static final String EHP = "ehp";
        public static final String WEAPON_STATS = "weapon_stats";
        public static final String ARMOR_STATS = "armor_stats";
        public static final String TOOLTIP = "tooltip";
        public static final String TOOLTIP_WEAPON = "tooltip_weapon";
        public static final String TOOLTIP_ARMOR = "tooltip_armor";
        public static final String PREVIEW = "preview";
        public static final String COMPARISON = "comparison";
        public static final String ENCHANTMENTS = "enchantments";
        public static final String ATTRIBUTES = "attributes";

        private Types() {}
    }
}
