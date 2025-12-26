package com.devmod.arena.concurrency;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rate limiter for arena build operations (DD61).
 *
 * <p>DD61 Limits:
 * <ul>
 *   <li>Token bucket: 10 req/s burst, 5 req/s sustained</li>
 *   <li>Per-player rate limiting</li>
 *   <li>Max 3 concurrent builds (global)</li>
 *   <li>Queue max 10 waiting requests</li>
 *   <li>60s timeout for queued requests</li>
 *   <li>Retry-after header for rejected builds</li>
 * </ul>
 */
public class ArenaBuildRateLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaBuildRateLimiter.class);

    // DD61: Token bucket configuration
    private static final int BURST_CAPACITY = 10;        // 10 req/s burst
    private static final double SUSTAINED_RATE = 5.0;    // 5 req/s sustained (refill ~200ms)

    private static final int MAX_CONCURRENT_BUILDS = 3;
    private static final int MAX_QUEUE_SIZE = 10;
    private static final Duration QUEUE_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(30);

    // DD61: Per-player token buckets
    private final Map<UUID, TokenBucket> playerBuckets = new ConcurrentHashMap<>();

    private final Semaphore buildSemaphore = new Semaphore(MAX_CONCURRENT_BUILDS, true);
    private final BlockingQueue<QueuedRequest> waitQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong grantedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);
    private final AtomicLong rateLimitedRequests = new AtomicLong(0);

    /**
     * DD61: Requests a build permit with per-player token bucket rate limiting.
     *
     * <p>First checks player's token bucket (10 req/s burst, 5 req/s sustained).
     * If rate limited, rejects immediately. Otherwise proceeds to global semaphore.
     * If 3 builds are already running, the request is queued.
     * If the queue is full, the request is rejected with retry-after.
     *
     * @param playerId The player's UUID for per-player rate limiting
     * @param requesterId The requester ID (for logging)
     * @return A {@link BuildPermit} indicating success or failure
     */
    public BuildPermit requestPermit(UUID playerId, String requesterId) {
        totalRequests.incrementAndGet();
        long startTime = System.currentTimeMillis();

        // DD61: Per-player token bucket check
        TokenBucket bucket = playerBuckets.computeIfAbsent(playerId,
            k -> new TokenBucket(BURST_CAPACITY, SUSTAINED_RATE));

        if (!bucket.tryConsume()) {
            rateLimitedRequests.incrementAndGet();
            Duration retryAfter = Duration.ofMillis((long) (1000.0 / SUSTAINED_RATE));
            LOGGER.debug("Build request from {} rate limited (player {})", requesterId, playerId);
            return BuildPermit.Rejected.rateLimited(retryAfter, -1);
        }

        // Try to acquire global semaphore immediately
        if (buildSemaphore.tryAcquire()) {
            grantedRequests.incrementAndGet();
            return BuildPermit.Granted.now(generatePermitId(), 0);
        }

        // Try to queue the request
        QueuedRequest request = new QueuedRequest(requesterId, startTime);
        if (!waitQueue.offer(request)) {
            // Queue is full
            rejectedRequests.incrementAndGet();
            LOGGER.warn("Build request from {} rejected: queue full", requesterId);
            return BuildPermit.Rejected.queueFull(DEFAULT_RETRY_AFTER);
        }

        int queuePosition = waitQueue.size();
        LOGGER.debug("Build request from {} queued at position {}", requesterId, queuePosition);

        // Wait in queue with timeout
        try {
            boolean acquired = buildSemaphore.tryAcquire(
                QUEUE_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );

            waitQueue.remove(request);

            if (acquired) {
                long waitTime = System.currentTimeMillis() - startTime;
                grantedRequests.incrementAndGet();
                LOGGER.debug("Build request from {} granted after {}ms wait", requesterId, waitTime);
                return BuildPermit.Granted.now(generatePermitId(), waitTime);
            } else {
                rejectedRequests.incrementAndGet();
                LOGGER.warn("Build request from {} timed out after {}s", requesterId, QUEUE_TIMEOUT.getSeconds());
                return BuildPermit.Rejected.timeout(queuePosition);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            waitQueue.remove(request);
            rejectedRequests.incrementAndGet();
            return BuildPermit.Rejected.timeout(queuePosition);
        }
    }

    /**
     * Legacy method for backwards compatibility.
     * @deprecated Use {@link #requestPermit(UUID, String)} for per-player rate limiting
     */
    @Deprecated
    public BuildPermit requestPermit(String requesterId) {
        // Use a deterministic UUID based on requesterId for legacy calls
        UUID playerId = UUID.nameUUIDFromBytes(requesterId.getBytes());
        return requestPermit(playerId, requesterId);
    }

    /**
     * Releases a build permit after completion.
     *
     * @param permitId The permit ID (for logging/tracking)
     */
    public void releasePermit(String permitId) {
        buildSemaphore.release();
        LOGGER.debug("Build permit {} released", permitId);
    }

    /**
     * Returns the number of currently running builds.
     */
    public int getActiveBuildCount() {
        return MAX_CONCURRENT_BUILDS - buildSemaphore.availablePermits();
    }

    /**
     * Returns the current queue size.
     */
    public int getQueueSize() {
        return waitQueue.size();
    }

    /**
     * Returns the number of available permits.
     */
    public int getAvailablePermits() {
        return buildSemaphore.availablePermits();
    }

    /**
     * Returns statistics about the rate limiter.
     */
    public RateLimiterStats getStats() {
        return new RateLimiterStats(
            totalRequests.get(),
            grantedRequests.get(),
            rejectedRequests.get(),
            rateLimitedRequests.get(),
            getActiveBuildCount(),
            getQueueSize()
        );
    }

    /**
     * Calculates the retry-after duration based on current load.
     */
    public Duration calculateRetryAfter() {
        int queueSize = getQueueSize();
        int activeBuildCount = getActiveBuildCount();

        // Estimate based on average build time (assume 10s per build)
        long estimatedSeconds = (queueSize + activeBuildCount) * 10L;
        return Duration.ofSeconds(Math.max(5, Math.min(estimatedSeconds, 120)));
    }

    private String generatePermitId() {
        return "build-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ========== Supporting Types ==========

    private record QueuedRequest(String requesterId, long queuedAt) {}

    /**
     * Rate limiter statistics.
     */
    public record RateLimiterStats(
        long totalRequests,
        long grantedRequests,
        long rejectedRequests,
        long rateLimitedRequests,
        int activeBuilds,
        int queueSize
    ) {
        public double grantRate() {
            return totalRequests > 0 ? (double) grantedRequests / totalRequests : 0;
        }

        public double rejectRate() {
            return totalRequests > 0 ? (double) rejectedRequests / totalRequests : 0;
        }

        public double rateLimitRate() {
            return totalRequests > 0 ? (double) rateLimitedRequests / totalRequests : 0;
        }
    }

    /**
     * DD61: Token bucket implementation for per-player rate limiting.
     * Implements burst capacity of 10 req/s with sustained rate of 5 req/s.
     */
    private static class TokenBucket {
        private final int capacity;
        private final double refillRate; // tokens per second
        private double tokens;
        private long lastRefillTime;

        public TokenBucket(int capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity; // Start full
            this.lastRefillTime = System.currentTimeMillis();
        }

        /**
         * Attempts to consume one token.
         * @return true if token was available, false if rate limited
         */
        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        /**
         * Refills tokens based on elapsed time.
         */
        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed > 0) {
                double tokensToAdd = (elapsed / 1000.0) * refillRate;
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTime = now;
            }
        }

        /**
         * Returns current token count (for debugging).
         */
        synchronized double getTokens() {
            refill();
            return tokens;
        }
    }

    /**
     * Returns the current token count for a player (for debugging/stats).
     */
    public double getPlayerTokens(UUID playerId) {
        TokenBucket bucket = playerBuckets.get(playerId);
        return bucket != null ? bucket.getTokens() : BURST_CAPACITY;
    }
}
