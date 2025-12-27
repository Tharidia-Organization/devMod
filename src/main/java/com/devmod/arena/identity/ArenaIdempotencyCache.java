package com.devmod.arena.identity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArenaIdempotencyCache implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaIdempotencyCache.class);

    // DD22: 5 minute TTL
    private static final long TTL_MS = 5 * 60 * 1000;
    private static final int MAX_ENTRIES = 1000;
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000; // 1 minute

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner;
    private final ScheduledFuture<?> cleanupTask;

    public ArenaIdempotencyCache() {
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ArenaIdempotencyCache-Cleaner");
            t.setDaemon(true);
            return t;
        });

        cleanupTask = cleaner.scheduleAtFixedRate(this::cleanup, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LOGGER.info("ArenaIdempotencyCache initialized with {}ms TTL, max {} entries", TTL_MS, MAX_ENTRIES);
    }

    /**
     * Gets or creates an arena UUID for the given request ID.
     *
     * <p>If the request ID exists and hasn't expired, returns the cached UUID.
     * Otherwise, generates a new UUID and caches it.
     *
     * @param requestId Unique request identifier (e.g., from client)
     * @return Arena UUID (either cached or newly generated)
     */
    public UUID getOrCreate(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID();
        }

        long now = System.currentTimeMillis();

        CacheEntry existing = cache.get(requestId);
        if (existing != null && !existing.isExpired(now)) {
            LOGGER.debug("Idempotency hit for request '{}': {}", requestId, existing.arenaId);
            return existing.arenaId;
        }

        // Evict if at capacity
        if (cache.size() >= MAX_ENTRIES) {
            evictOldest();
        }

        UUID newId = UUID.randomUUID();
        cache.put(requestId, new CacheEntry(newId, now + TTL_MS));
        LOGGER.debug("Idempotency miss for request '{}': generated {}", requestId, newId);

        return newId;
    }

    /**
     * Checks if a request ID is cached (for testing).
     */
    public boolean contains(String requestId) {
        CacheEntry entry = cache.get(requestId);
        return entry != null && !entry.isExpired(System.currentTimeMillis());
    }

    /**
     * Returns the cached arena ID for a request, if present and not expired.
     */
    public UUID get(String requestId) {
        CacheEntry entry = cache.get(requestId);
        if (entry != null && !entry.isExpired(System.currentTimeMillis())) {
            return entry.arenaId;
        }
        return null;
    }

    /**
     * Returns the current cache size.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Cleanup expired entries.
     */
    private void cleanup() {
        long now = System.currentTimeMillis();
        int before = cache.size();

        cache.entrySet().removeIf(e -> e.getValue().isExpired(now));

        int removed = before - cache.size();
        if (removed > 0) {
            LOGGER.debug("Idempotency cleanup: removed {} expired entries, {} remaining", removed, cache.size());
        }
    }

    /**
     * Evicts the oldest entry to make room.
     */
    private void evictOldest() {
        String oldestKey = null;
        long oldestExpiry = Long.MAX_VALUE;

        for (var entry : cache.entrySet()) {
            if (entry.getValue().expiryMs < oldestExpiry) {
                oldestExpiry = entry.getValue().expiryMs;
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            cache.remove(oldestKey);
            LOGGER.debug("Evicted oldest entry: {}", oldestKey);
        }
    }

    @Override
    public void close() {
        cleanupTask.cancel(false);
        cleaner.shutdownNow();
        cache.clear();
    }

    /**
     * Cache entry with expiry time.
     */
    private static class CacheEntry {
        final UUID arenaId;
        final long expiryMs;

        CacheEntry(UUID arenaId, long expiryMs) {
            this.arenaId = arenaId;
            this.expiryMs = expiryMs;
        }

        boolean isExpired(long nowMs) {
            return nowMs > expiryMs;
        }
    }
}
