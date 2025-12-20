package com.devmod.arena.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter for arena build operations (DD61).
 *
 * <p>Limits:
 * <ul>
 *   <li>Max 3 concurrent builds</li>
 *   <li>Queue max 10 waiting requests</li>
 *   <li>60s timeout for queued requests</li>
 *   <li>Retry-after header for rejected builds</li>
 * </ul>
 */
public class ArenaBuildRateLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaBuildRateLimiter.class);

    private static final int MAX_CONCURRENT_BUILDS = 3;
    private static final int MAX_QUEUE_SIZE = 10;
    private static final Duration QUEUE_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(30);

    private final Semaphore buildSemaphore = new Semaphore(MAX_CONCURRENT_BUILDS, true);
    private final BlockingQueue<QueuedRequest> waitQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong grantedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);

    /**
     * Requests a build permit.
     *
     * <p>If 3 builds are already running, the request is queued.
     * If the queue is full, the request is rejected with retry-after.
     *
     * @param requesterId The requester ID (for logging)
     * @return A {@link BuildPermit} indicating success or failure
     */
    public BuildPermit requestPermit(String requesterId) {
        totalRequests.incrementAndGet();
        long startTime = System.currentTimeMillis();

        // Try to acquire immediately
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
        int activeBuilds,
        int queueSize
    ) {
        public double grantRate() {
            return totalRequests > 0 ? (double) grantedRequests / totalRequests : 0;
        }

        public double rejectRate() {
            return totalRequests > 0 ? (double) rejectedRequests / totalRequests : 0;
        }
    }
}
