package com.devmod.mailbox.news;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.persistence.MailboxRepository;

/**
 * Background job that periodically purges expired news articles.
 */
public final class NewsPurgeJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewsPurgeJob.class);

    public static final NewsPurgeJob INSTANCE = new NewsPurgeJob();

    /** Default purge interval (1 hour). */
    private static final Duration DEFAULT_INTERVAL = Duration.ofHours(1);

    /** Minimum purge interval (5 minutes). */
    private static final Duration MIN_INTERVAL = Duration.ofMinutes(5);

    private final ScheduledExecutorService scheduler;
    @Nullable
    private volatile ScheduledFuture<?> scheduledTask;
    @Nullable
    private volatile MailboxRepository repository;
    private volatile Duration interval = DEFAULT_INTERVAL;
    private volatile boolean enabled = true;

    // Statistics
    private final AtomicInteger totalPurged = new AtomicInteger(0);
    private final AtomicLong lastPurgeTime = new AtomicLong(0);
    private final AtomicInteger lastPurgeCount = new AtomicInteger(0);

    private NewsPurgeJob() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NewsPurgeJob");
            t.setDaemon(true);
            return t;
        });
    }

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    /**
     * Initialize with repository.
     */
    public void initialize(MailboxRepository repository) {
        this.repository = repository;
        LOGGER.info("[NewsPurgeJob] Initialized with repository");
    }

    /**
     * Start the purge job.
     */
    public synchronized void start() {
        if (!enabled) {
            LOGGER.info("[NewsPurgeJob] Purge job is disabled");
            return;
        }

        if (scheduledTask != null && !scheduledTask.isDone()) {
            LOGGER.debug("[NewsPurgeJob] Already running");
            return;
        }

        Duration effectiveInterval = interval != null ? interval : DEFAULT_INTERVAL;
        long intervalMs = Math.max(effectiveInterval.toMillis(), MIN_INTERVAL.toMillis());

        scheduledTask = scheduler.scheduleAtFixedRate(
            this::runPurge,
            intervalMs, // Initial delay
            intervalMs, // Period
            TimeUnit.MILLISECONDS
        );

        LOGGER.info("[NewsPurgeJob] Started with interval: {} minutes", effectiveInterval.toMinutes());
    }

    /**
     * Stop the purge job.
     */
    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
            LOGGER.info("[NewsPurgeJob] Stopped");
        }
    }

    /**
     * Shutdown the job completely.
     */
    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("[NewsPurgeJob] Shutdown complete");
    }

    // ============================================================================
    // PURGE LOGIC
    // ============================================================================

    /**
     * Run a purge cycle.
     */
    private void runPurge() {
        MailboxRepository repositoryLocal = repository;
        if (repositoryLocal == null) {
            LOGGER.warn("[NewsPurgeJob] No repository configured");
            return;
        }

        try {
            Instant now = Instant.now();
            LOGGER.debug("[NewsPurgeJob] Running purge cycle at {}", now);

            // Get all news and check for expired ones
            repositoryLocal.getAllNews(1000).thenAccept(articles -> {
                int purgedCount = 0;

                for (NewsArticle article : articles) {
                    if (article.isExpired()) {
                        // Deactivate or delete expired articles
                        repositoryLocal.deleteNewsArticle(article.id()).thenAccept(success -> {
                            if (success) {
                                LOGGER.info("[NewsPurgeJob] Purged expired news: {} ({})",
                                    article.title(), article.id());
                            }
                        }).exceptionally(e -> {
                            LOGGER.error("[NewsPurgeJob] Failed to purge news {}: {}",
                                article.id(), e.getMessage());
                            return null;
                        });
                        purgedCount++;
                    }
                }

                if (purgedCount > 0) {
                    totalPurged.addAndGet(purgedCount);
                    lastPurgeCount.set(purgedCount);
                    LOGGER.info("[NewsPurgeJob] Purged {} expired news articles", purgedCount);
                } else {
                    lastPurgeCount.set(0);
                    LOGGER.debug("[NewsPurgeJob] No expired news to purge");
                }

                lastPurgeTime.set(now.toEpochMilli());

            }).exceptionally(e -> {
                LOGGER.error("[NewsPurgeJob] Error during purge: {}", e.getMessage());
                return null;
            });

        } catch (Exception e) {
            LOGGER.error("[NewsPurgeJob] Unexpected error: {}", e.getMessage(), e);
        }
    }

    /**
     * Run purge immediately (for manual trigger).
     */
    public void runNow() {
        scheduler.execute(this::runPurge);
    }

    // ============================================================================
    // CONFIGURATION
    // ============================================================================

    /**
     * Set the purge interval.
     */
    public void setInterval(Duration newInterval) {
        if (newInterval == null) {
            newInterval = DEFAULT_INTERVAL;
        }
        if (newInterval.compareTo(MIN_INTERVAL) < 0) {
            LOGGER.warn("[NewsPurgeJob] Interval too short, using minimum: {}", MIN_INTERVAL);
            newInterval = MIN_INTERVAL;
        }
        this.interval = newInterval;

        // Restart if running
        if (scheduledTask != null && !scheduledTask.isDone()) {
            stop();
            start();
        }
    }

    /**
     * Get the current interval.
     */
    public Duration getInterval() {
        return interval;
    }

    /**
     * Enable or disable the purge job.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            stop();
        } else {
            start();
        }
    }

    /**
     * Check if the purge job is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if the purge job is running.
     */
    public boolean isRunning() {
        return scheduledTask != null && !scheduledTask.isDone();
    }

    // ============================================================================
    // STATISTICS
    // ============================================================================

    /**
     * Get total number of articles purged.
     */
    public int getTotalPurged() {
        return totalPurged.get();
    }

    /**
     * Get timestamp of last purge run.
     */
    public long getLastPurgeTime() {
        return lastPurgeTime.get();
    }

    /**
     * Get count from last purge run.
     */
    public int getLastPurgeCount() {
        return lastPurgeCount.get();
    }

    /**
     * Reset statistics.
     */
    public void resetStats() {
        totalPurged.set(0);
        lastPurgeTime.set(0);
        lastPurgeCount.set(0);
    }
}
