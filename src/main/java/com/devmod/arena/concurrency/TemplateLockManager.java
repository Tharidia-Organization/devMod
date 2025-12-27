package com.devmod.arena.concurrency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TemplateLockManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateLockManager.class);

    // DD60: Lock expiry timeout (stale threshold)
    private static final Duration LOCK_EXPIRY = Duration.ofSeconds(60);

    // DD60: Cleanup interval - every 10 seconds as per spec
    private static final Duration CLEANUP_INTERVAL = Duration.ofSeconds(10);

    // DD62: Lock striping configuration
    private static final int STRIPE_COUNT = 16;
    private final ReentrantLock[] stripes;

    private final Map<String, TemplateLock> locks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler;
    private ScheduledFuture<?> cleanupTask;
    private volatile boolean running = false;

    public TemplateLockManager() {
        // DD62: Initialize lock stripes
        this.stripes = new ReentrantLock[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            this.stripes[i] = new ReentrantLock();
        }

        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TemplateLockManager-Cleanup");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * DD62: Gets the stripe lock for a given template ID.
     * Uses consistent hashing to distribute templates across stripes.
     */
    private ReentrantLock getStripe(String templateId) {
        int hash = templateId.hashCode();
        int index = Math.abs(hash % STRIPE_COUNT);
        return stripes[index];
    }

    /**
     * Starts the cleanup scheduler.
     */
    public void start() {
        if (running) return;
        running = true;

        cleanupTask = cleanupScheduler.scheduleAtFixedRate(
            this::cleanupExpiredLocks,
            CLEANUP_INTERVAL.toMillis(),
            CLEANUP_INTERVAL.toMillis(),
            TimeUnit.MILLISECONDS
        );

        LOGGER.info("TemplateLockManager started with {}s expiry, {}min cleanup",
            LOCK_EXPIRY.getSeconds(), CLEANUP_INTERVAL.toMinutes());
    }

    /**
     * Stops the cleanup scheduler.
     */
    public void shutdown() {
        running = false;
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clear all locks
        locks.clear();
        LOGGER.info("TemplateLockManager shutdown complete");
    }

    /**
     * Acquires a lock for a template.
     * DD62: Uses lock striping to reduce contention on high-traffic templates.
     *
     * @param templateId The template to lock
     * @param owner The lock owner (for debugging)
     * @return true if lock acquired, false if already locked
     */
    public boolean acquire(String templateId, String owner) {
        // DD62: Use stripe lock for thread-safe acquisition
        ReentrantLock stripe = getStripe(templateId);
        stripe.lock();
        try {
            TemplateLock existingLock = locks.get(templateId);

            if (existingLock != null && !existingLock.isExpired()) {
                LOGGER.debug("Lock for {} already held by {}", templateId, existingLock.owner());
                return false;
            }

            TemplateLock newLock = new TemplateLock(templateId, owner, Instant.now());
            locks.put(templateId, newLock);

            LOGGER.debug("Lock acquired for {} by {}", templateId, owner);
            return true;
        } finally {
            stripe.unlock();
        }
    }

    /**
     * Releases a lock for a template.
     *
     * @param templateId The template to unlock
     * @param owner The lock owner (must match)
     * @return true if released, false if not owned
     */
    public boolean release(String templateId, String owner) {
        TemplateLock lock = locks.get(templateId);

        if (lock == null) {
            return false;
        }

        if (!lock.owner().equals(owner)) {
            LOGGER.warn("Cannot release lock for {}: owner mismatch (expected {}, got {})",
                templateId, lock.owner(), owner);
            return false;
        }

        locks.remove(templateId);
        LOGGER.debug("Lock released for {} by {}", templateId, owner);
        return true;
    }

    /**
     * Tries to acquire a lock with timeout.
     *
     * @param templateId The template to lock
     * @param owner The lock owner
     * @param timeout Maximum time to wait
     * @return true if acquired within timeout
     */
    public boolean tryAcquire(String templateId, String owner, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (acquire(templateId, owner)) {
                return true;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /**
     * Checks if a template is locked.
     */
    public boolean isLocked(String templateId) {
        TemplateLock lock = locks.get(templateId);
        return lock != null && !lock.isExpired();
    }

    /**
     * Gets the current lock for a template.
     */
    public Optional<TemplateLock> getLock(String templateId) {
        TemplateLock lock = locks.get(templateId);
        if (lock != null && !lock.isExpired()) {
            return Optional.of(lock);
        }
        return Optional.empty();
    }

    /**
     * Cleans up expired locks.
     */
    public int cleanupExpiredLocks() {
        int cleaned = 0;
        var iterator = locks.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                cleaned++;
                LOGGER.debug("Cleaned up expired lock for {} (owner: {})",
                    entry.getKey(), entry.getValue().owner());
            }
        }

        if (cleaned > 0) {
            LOGGER.info("Cleaned up {} expired locks", cleaned);
        }

        return cleaned;
    }

    /**
     * Returns the current lock count.
     */
    public int getLockCount() {
        return (int) locks.values().stream()
            .filter(lock -> !lock.isExpired())
            .count();
    }

    /**
     * Lock record with expiry tracking.
     */
    public record TemplateLock(
        String templateId,
        String owner,
        Instant acquiredAt
    ) {
        /**
         * Checks if this lock has expired.
         */
        public boolean isExpired() {
            return Duration.between(acquiredAt, Instant.now()).compareTo(LOCK_EXPIRY) > 0;
        }

        /**
         * Returns the remaining time until expiry.
         */
        public Duration remainingTime() {
            Duration elapsed = Duration.between(acquiredAt, Instant.now());
            Duration remaining = LOCK_EXPIRY.minus(elapsed);
            return remaining.isNegative() ? Duration.ZERO : remaining;
        }
    }
}
