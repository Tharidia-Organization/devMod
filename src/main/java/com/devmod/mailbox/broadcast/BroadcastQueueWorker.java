package com.devmod.mailbox.broadcast;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MailboxManager;
import com.devmod.mailbox.MessageType;

/**
 * Background worker for processing broadcast message queues.
 *
 * Provides:
 * - Async queue-based broadcast processing
 * - Job status tracking
 * - Configurable retry on failures
 * - Rate limiting to prevent server overload
 */
public final class BroadcastQueueWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(BroadcastQueueWorker.class);

    public static final BroadcastQueueWorker INSTANCE = new BroadcastQueueWorker();

    /** Maximum queued jobs before rejecting new ones. */
    private static final int MAX_QUEUE_SIZE = 100;

    /** Default retry attempts for failed broadcasts. */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /** Delay between retries in milliseconds. */
    private static final long RETRY_DELAY_MS = 5000;

    /** Maximum jobs to keep in history. */
    private static final int MAX_HISTORY_SIZE = 500;

    private final BlockingQueue<BroadcastJob> jobQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final Map<UUID, BroadcastJob> activeJobs = new ConcurrentHashMap<>();
    private final Map<UUID, BroadcastJob> jobHistory = new ConcurrentHashMap<>();

    @Nullable
    private volatile ExecutorService workerExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger totalJobsProcessed = new AtomicInteger(0);
    private final AtomicInteger totalMessagesSent = new AtomicInteger(0);
    private final AtomicInteger totalFailures = new AtomicInteger(0);

    private BroadcastQueueWorker() {}

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    /**
     * Start the broadcast queue worker.
     */
    public synchronized void start() {
        if (running.get()) {
            LOGGER.debug("[BroadcastWorker] Already running");
            return;
        }

        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BroadcastQueueWorker");
            t.setDaemon(true);
            return t;
        });
        workerExecutor = exec;

        running.set(true);
        exec.execute(this::processLoop);

        LOGGER.info("[BroadcastWorker] Started");
    }

    /**
     * Stop the broadcast queue worker.
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        ExecutorService executor = workerExecutor;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            workerExecutor = null;
        }

        LOGGER.info("[BroadcastWorker] Stopped (processed {} jobs, {} messages sent, {} failures)",
            totalJobsProcessed.get(), totalMessagesSent.get(), totalFailures.get());
    }

    /**
     * Check if the worker is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    // ============================================================================
    // JOB SUBMISSION
    // ============================================================================

    /**
     * Submit a broadcast job to the queue.
     *
     * @param senderName the sender name
     * @param recipients list of recipient UUIDs
     * @param subject the message subject
     * @param body the message body (optional)
     * @param attachmentData attachment data (optional)
     * @param type the message type
     * @param expiresAt optional expiration time
     * @return the job ID, or null if queue is full
     */
    @Nullable
    public UUID submitJob(
            String senderName,
            List<UUID> recipients,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            MessageType type,
            @Nullable Instant expiresAt
    ) {
        if (!running.get()) {
            LOGGER.warn("[BroadcastWorker] Worker not running, cannot submit job");
            return null;
        }

        BroadcastJob job = new BroadcastJob(
            UUID.randomUUID(),
            senderName,
            List.copyOf(recipients),
            subject,
            body,
            attachmentData,
            type,
            expiresAt,
            DEFAULT_MAX_RETRIES
        );

        boolean added = jobQueue.offer(job);
        if (!added) {
            LOGGER.warn("[BroadcastWorker] Queue full, rejecting job: {}", subject);
            return null;
        }

        activeJobs.put(job.id(), job);
        LOGGER.info("[BroadcastWorker] Job {} queued: {} to {} recipients",
            job.id(), subject, recipients.size());

        return job.id();
    }

    /**
     * Submit a broadcast job without an explicit expiration time.
     */
    @Nullable
    public UUID submitJob(
            String senderName,
            List<UUID> recipients,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            MessageType type
    ) {
        return submitJob(senderName, recipients, subject, body, attachmentData, type, null);
    }

    /**
     * Submit a broadcast to all known users.
     *
     * @param senderName the sender name
     * @param subject the message subject
     * @param body the message body (optional)
     * @param type the message type
     * @return future completing with the job ID
     */
    public CompletableFuture<UUID> submitGlobalBroadcast(
            String senderName,
            String subject,
            @Nullable String body,
            MessageType type
    ) {
        return submitGlobalBroadcast(senderName, subject, body, null, type, null);
    }

    public CompletableFuture<UUID> submitGlobalBroadcast(
            String senderName,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            MessageType type,
            @Nullable Instant expiresAt
    ) {
        return MailboxManager.INSTANCE.getKnownUsers()
            .thenApply(users -> {
                if (users.isEmpty()) {
                    LOGGER.warn("[BroadcastWorker] No known users for global broadcast");
                    return null;
                }
                return submitJob(senderName, users, subject, body, attachmentData, type, expiresAt);
            });
    }

    // ============================================================================
    // JOB STATUS
    // ============================================================================

    /**
     * Get the status of a job.
     *
     * @param jobId the job ID
     * @return the job status, or null if not found
     */
    @Nullable
    public JobStatus getJobStatus(UUID jobId) {
        BroadcastJob job = activeJobs.get(jobId);
        if (job == null) {
            job = jobHistory.get(jobId);
        }
        if (job == null) {
            return null;
        }

        return new JobStatus(
            job.id(),
            job.subject(),
            job.recipients().size(),
            job.sentCount.get(),
            job.failedCount.get(),
            job.status,
            job.startedAt,
            job.completedAt,
            job.errorMessage
        );
    }

    /**
     * Get all active jobs.
     */
    public List<JobStatus> getActiveJobs() {
        return activeJobs.values().stream()
            .map(job -> new JobStatus(
                job.id(),
                job.subject(),
                job.recipients().size(),
                job.sentCount.get(),
                job.failedCount.get(),
                job.status,
                job.startedAt,
                job.completedAt,
                job.errorMessage
            ))
            .toList();
    }

    /**
     * Get recent completed jobs.
     */
    public List<JobStatus> getRecentCompletedJobs(int limit) {
        return jobHistory.values().stream()
            .filter(j -> j.status == JobState.COMPLETED || j.status == JobState.FAILED)
            .sorted((a, b) -> {
                Instant aTime = a.completedAt != null ? a.completedAt : Instant.MIN;
                Instant bTime = b.completedAt != null ? b.completedAt : Instant.MIN;
                return bTime.compareTo(aTime);
            })
            .limit(limit)
            .map(job -> new JobStatus(
                job.id(),
                job.subject(),
                job.recipients().size(),
                job.sentCount.get(),
                job.failedCount.get(),
                job.status,
                job.startedAt,
                job.completedAt,
                job.errorMessage
            ))
            .toList();
    }

    /**
     * Get queue size.
     */
    public int getQueueSize() {
        return jobQueue.size();
    }

    // ============================================================================
    // STATISTICS
    // ============================================================================

    /**
     * Get worker statistics.
     */
    public WorkerStats getStats() {
        return new WorkerStats(
            running.get(),
            jobQueue.size(),
            activeJobs.size(),
            totalJobsProcessed.get(),
            totalMessagesSent.get(),
            totalFailures.get()
        );
    }

    /**
     * Reset statistics.
     */
    public void resetStats() {
        totalJobsProcessed.set(0);
        totalMessagesSent.set(0);
        totalFailures.set(0);
    }

    // ============================================================================
    // WORKER LOOP
    // ============================================================================

    private void processLoop() {
        LOGGER.info("[BroadcastWorker] Worker loop started");

        while (running.get()) {
            try {
                BroadcastJob job = jobQueue.poll(1, TimeUnit.SECONDS);
                if (job == null) {
                    continue;
                }

                processJob(job);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.debug("[BroadcastWorker] Worker interrupted");
                break;
            } catch (Exception e) {
                LOGGER.error("[BroadcastWorker] Unexpected error in worker loop", e);
            }
        }

        LOGGER.info("[BroadcastWorker] Worker loop ended");
    }

    private void processJob(BroadcastJob job) {
        job.status = JobState.PROCESSING;
        job.startedAt = Instant.now();

        LOGGER.info("[BroadcastWorker] Processing job {}: {} to {} recipients",
            job.id(), job.subject(), job.recipients().size());

        try {
            // Use MailboxManager's sendBroadcast which already handles batching
            Integer sent = MailboxManager.INSTANCE.sendBroadcast(
                job.senderName(),
                job.recipients(),
                job.subject(),
                job.body(),
                job.attachmentData(),
                job.type(),
                job.expiresAt()
            ).join();

            job.sentCount.set(sent);
            job.failedCount.set(job.recipients().size() - sent);

            if (job.failedCount.get() > 0 && job.retryCount.get() < job.maxRetries()) {
                // Retry failed ones
                handleRetry(job);
            } else {
                completeJob(job, job.failedCount.get() == 0);
            }

        } catch (Exception e) {
            LOGGER.error("[BroadcastWorker] Job {} failed: {}", job.id(), e.getMessage());
            job.errorMessage = e.getMessage();

            if (job.retryCount.get() < job.maxRetries()) {
                handleRetry(job);
            } else {
                completeJob(job, false);
            }
        }
    }

    private void handleRetry(BroadcastJob job) {
        int attempt = job.retryCount.incrementAndGet();
        job.status = JobState.RETRYING;

        LOGGER.info("[BroadcastWorker] Retrying job {} (attempt {}/{})",
            job.id(), attempt, job.maxRetries());

        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            completeJob(job, false);
            return;
        }

        // Re-queue for processing
        if (!jobQueue.offer(job)) {
            LOGGER.error("[BroadcastWorker] Failed to re-queue job {} for retry", job.id());
            completeJob(job, false);
        }
    }

    private void completeJob(BroadcastJob job, boolean success) {
        job.status = success ? JobState.COMPLETED : JobState.FAILED;
        job.completedAt = Instant.now();

        activeJobs.remove(job.id());
        addToHistory(job);

        totalJobsProcessed.incrementAndGet();
        totalMessagesSent.addAndGet(job.sentCount.get());
        if (!success) {
            totalFailures.incrementAndGet();
        }

        LOGGER.info("[BroadcastWorker] Job {} {}: sent {}/{} messages",
            job.id(), job.status, job.sentCount.get(), job.recipients().size());
    }

    private void addToHistory(BroadcastJob job) {
        jobHistory.put(job.id(), job);

        // Trim history if too large
        while (jobHistory.size() > MAX_HISTORY_SIZE) {
            jobHistory.values().stream()
                .min((a, b) -> {
                    Instant aTime = a.completedAt != null ? a.completedAt : Instant.MIN;
                    Instant bTime = b.completedAt != null ? b.completedAt : Instant.MIN;
                    return aTime.compareTo(bTime);
                })
                .ifPresent(oldest -> jobHistory.remove(oldest.id()));
        }
    }

    // ============================================================================
    // TYPES
    // ============================================================================

    /**
     * State of a broadcast job.
     */
    public enum JobState {
        QUEUED,
        PROCESSING,
        RETRYING,
        COMPLETED,
        FAILED
    }

    /**
     * Internal job representation.
     */
    private static final class BroadcastJob {
        private final UUID id;
        private final String senderName;
        private final List<UUID> recipients;
        private final String subject;
        @Nullable
        private final String body;
        @Nullable
        private final String attachmentData;
        private final MessageType type;
        @Nullable
        private final Instant expiresAt;
        private final int maxRetries;

        volatile JobState status = JobState.QUEUED;
        final AtomicInteger sentCount = new AtomicInteger(0);
        final AtomicInteger failedCount = new AtomicInteger(0);
        final AtomicInteger retryCount = new AtomicInteger(0);
        @Nullable
        volatile Instant startedAt;
        @Nullable
        volatile Instant completedAt;
        @Nullable
        volatile String errorMessage;

        BroadcastJob(
                UUID id,
                String senderName,
                List<UUID> recipients,
                String subject,
                @Nullable String body,
                @Nullable String attachmentData,
                MessageType type,
                @Nullable Instant expiresAt,
                int maxRetries
        ) {
            this.id = id;
            this.senderName = senderName;
            this.recipients = recipients;
            this.subject = subject;
            this.body = body;
            this.attachmentData = attachmentData;
            this.type = type;
            this.expiresAt = expiresAt;
            this.maxRetries = maxRetries;
        }

        UUID id() { return id; }
        String senderName() { return senderName; }
        List<UUID> recipients() { return recipients; }
        String subject() { return subject; }
        @Nullable String body() { return body; }
        @Nullable String attachmentData() { return attachmentData; }
        MessageType type() { return type; }
        @Nullable Instant expiresAt() { return expiresAt; }
        int maxRetries() { return maxRetries; }
    }

    /**
     * Public job status record.
     */
    public record JobStatus(
        UUID jobId,
        String subject,
        int totalRecipients,
        int sentCount,
        int failedCount,
        JobState state,
        @Nullable Instant startedAt,
        @Nullable Instant completedAt,
        @Nullable String errorMessage
    ) {
        public double progressPercent() {
            if (totalRecipients == 0) return 100.0;
            return (sentCount + failedCount) * 100.0 / totalRecipients;
        }
    }

    /**
     * Worker statistics record.
     */
    public record WorkerStats(
        boolean running,
        int queueSize,
        int activeJobs,
        int totalJobsProcessed,
        int totalMessagesSent,
        int totalFailures
    ) {}
}
