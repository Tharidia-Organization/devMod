package com.devmod.mailbox.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MailboxConfig;
import com.devmod.mailbox.MailboxManager;

/**
 * Runtime dispatcher for mailbox delivery jobs.
 *
 * Handles retry scheduling, failure handling, and recall messages.
 */
public final class MailboxDeliveryRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxDeliveryRuntime.class);

    public static final MailboxDeliveryRuntime INSTANCE = new MailboxDeliveryRuntime();

    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean dispatching = new AtomicBoolean(false);

    private final LongAdder totalAttempts = new LongAdder();
    private final LongAdder totalDelivered = new LongAdder();
    private final LongAdder totalFailed = new LongAdder();
    private final LongAdder totalRetried = new LongAdder();
    private final LongAdder totalRecalls = new LongAdder();
    private final LongAdder totalExpired = new LongAdder();

    @Nullable
    private ScheduledExecutorService scheduler;
    @Nullable
    private ScheduledFuture<?> dispatchTask;

    private volatile Instant lastDispatchAt = Instant.EPOCH;

    private MailboxDeliveryRuntime() {}

    private static void ignoreFuture(CompletableFuture<?> future) {
        if (future == null) {
            LOGGER.trace("[MailboxDelivery] Future was null");
        }
    }

    public synchronized void start() {
        if (running.get()) {
            return;
        }

        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MailboxDeliveryRuntime");
            t.setDaemon(true);
            return t;
        });
        scheduler = exec;
        running.set(true);

        int intervalSeconds = MailboxConfig.INSTANCE.getDeliveryDispatchIntervalSeconds();
        dispatchTask = exec.scheduleAtFixedRate(this::dispatchOnce, 1, intervalSeconds, TimeUnit.SECONDS);

        LOGGER.info("[MailboxDelivery] Runtime started (interval {}s)", intervalSeconds);
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);
        ScheduledExecutorService exec = scheduler;
        if (exec != null) {
            if (dispatchTask != null) {
                dispatchTask.cancel(false);
                dispatchTask = null;
            }
            exec.shutdown();
            try {
                if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
                    exec.shutdownNow();
                }
            } catch (InterruptedException e) {
                exec.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }

        LOGGER.info("[MailboxDelivery] Runtime stopped");
    }

    public DeliveryStats getStats() {
        return new DeliveryStats(
            totalAttempts.sum(),
            totalDelivered.sum(),
            totalFailed.sum(),
            totalRetried.sum(),
            totalRecalls.sum(),
            totalExpired.sum(),
            inFlight.size(),
            running.get(),
            lastDispatchAt
        );
    }

    private void dispatchOnce() {
        if (!MailboxConfig.INSTANCE.isDeliveryDispatchEnabled()) {
            return;
        }
        if (!dispatching.compareAndSet(false, true)) {
            return;
        }

        int batchSize = MailboxConfig.INSTANCE.getDeliveryDispatchBatchSize();
        ignoreFuture(MailboxManager.INSTANCE.getDeliveryJobsByStatus(MailboxDeliveryJob.DeliveryStatus.PENDING, batchSize)
            .whenComplete((jobs, error) -> {
                try {
                    if (error != null) {
                        LOGGER.error("[MailboxDelivery] Failed to load delivery jobs", error);
                        return;
                    }
                    if (jobs == null || jobs.isEmpty()) {
                        return;
                    }
                    lastDispatchAt = Instant.now();
                    processJobs(jobs);
                } finally {
                    dispatching.set(false);
                }
            }));
    }

    private void processJobs(List<MailboxDeliveryJob> jobs) {
        Instant now = Instant.now();
        for (MailboxDeliveryJob job : jobs) {
            if (job == null) {
                continue;
            }
            if (job.expiresAt() != null && now.isAfter(job.expiresAt())) {
                handleExpired(job, now, "Message expired before delivery");
                continue;
            }
            if (job.availableAt() != null && now.isBefore(job.availableAt())) {
                continue;
            }
            if (!inFlight.add(job.id())) {
                continue;
            }
            attemptDelivery(job, now);
        }
    }

    private void attemptDelivery(MailboxDeliveryJob job, Instant attemptAt) {
        MailboxManager.INSTANCE.getMessage(job.messageId())
            .thenCompose(existing -> {
                if (existing.isPresent()) {
                    return markDeliveredFromExisting(job, attemptAt)
                        .thenApply(ignored -> new DeliveryResult(true, false));
                }

                totalAttempts.increment();
                return deliverAndUpdate(job, attemptAt)
                    .thenApply(ignored -> new DeliveryResult(true, true));
            })
            .thenAccept(result -> {
                if (result.delivered() && result.countDelivery()) {
                    totalDelivered.increment();
                }
                inFlight.remove(job.id());
            })
            .exceptionally(error -> {
                handleFailure(job, attemptAt, error);
                inFlight.remove(job.id());
                return null;
            });
    }

    private CompletableFuture<Void> deliverAndUpdate(MailboxDeliveryJob job, Instant attemptAt) {
        return MailboxManager.INSTANCE.deliverDeliveryJob(job)
            .thenCompose(message -> {
                MailboxDeliveryJob delivered = job.toBuilder()
                    .status(MailboxDeliveryJob.DeliveryStatus.DELIVERED)
                    .attemptCount(job.attemptCount() + 1)
                    .lastAttemptAt(attemptAt)
                    .deliveredAt(attemptAt)
                    .lastFailureAt(null)
                    .lastFailureReason(null)
                    .build();
                return MailboxManager.INSTANCE.updateDeliveryJob(delivered)
                    .handle((updated, updateError) -> {
                        if (updateError != null || !Boolean.TRUE.equals(updated)) {
                            LOGGER.warn(
                                "[MailboxDelivery] Failed to persist delivered job {}",
                                job.id(),
                                updateError
                            );
                        }
                        return null;
                    });
            });
    }

    private CompletableFuture<Void> markDeliveredFromExisting(MailboxDeliveryJob job, Instant attemptAt) {
        MailboxDeliveryJob delivered = job.toBuilder()
            .status(MailboxDeliveryJob.DeliveryStatus.DELIVERED)
            .attemptCount(Math.max(job.attemptCount(), 1))
            .lastAttemptAt(attemptAt)
            .deliveredAt(attemptAt)
            .lastFailureAt(null)
            .lastFailureReason(null)
            .build();

        return MailboxManager.INSTANCE.updateDeliveryJob(delivered)
            .handle((updated, updateError) -> {
                if (updateError != null || !Boolean.TRUE.equals(updated)) {
                    LOGGER.warn(
                        "[MailboxDelivery] Failed to repair delivered job {}",
                        job.id(),
                        updateError
                    );
                }
                return null;
            });
    }

    private void handleFailure(MailboxDeliveryJob job, Instant attemptAt, Throwable error) {
        String reason = MailboxManager.buildFailureReason(error);
        int nextFailureCount = job.failureCount() + 1;
        int maxAttempts = MailboxConfig.INSTANCE.getDeliveryMaxAttempts();

        if (nextFailureCount >= maxAttempts) {
            MailboxDeliveryJob failed = job.toBuilder()
                .status(MailboxDeliveryJob.DeliveryStatus.FAILED)
                .attemptCount(job.attemptCount() + 1)
                .failureCount(nextFailureCount)
                .lastAttemptAt(attemptAt)
                .lastFailureAt(attemptAt)
                .lastFailureReason(reason)
                .build();

            ignoreFuture(MailboxManager.INSTANCE.updateDeliveryJob(failed)
                .whenComplete((updated, updateError) -> {
                    if (updateError != null || !Boolean.TRUE.equals(updated)) {
                        LOGGER.warn("[MailboxDelivery] Failed to persist final failure {}", job.id(), updateError);
                    }
                }));

            totalFailed.increment();
            if (MailboxConfig.INSTANCE.isDeliveryRecallEnabled()) {
                ignoreFuture(MailboxManager.INSTANCE.sendDeliveryRecall(failed, reason)
                    .thenRun(totalRecalls::increment));
            }
            return;
        }

        Instant nextAttemptAt = attemptAt.plusSeconds(
            MailboxManager.computeRetryDelaySeconds(nextFailureCount));
        MailboxDeliveryJob retry = job.toBuilder()
            .status(MailboxDeliveryJob.DeliveryStatus.PENDING)
            .attemptCount(job.attemptCount() + 1)
            .failureCount(nextFailureCount)
            .lastAttemptAt(attemptAt)
            .lastFailureAt(attemptAt)
            .lastFailureReason(reason)
            .availableAt(nextAttemptAt)
            .build();

        ignoreFuture(MailboxManager.INSTANCE.updateDeliveryJob(retry)
            .whenComplete((updated, updateError) -> {
                if (updateError != null || !Boolean.TRUE.equals(updated)) {
                    LOGGER.warn("[MailboxDelivery] Failed to persist retry update {}", job.id(), updateError);
                }
            }));

        totalRetried.increment();
    }

    private void handleExpired(MailboxDeliveryJob job, Instant now, String reason) {
        MailboxDeliveryJob cancelled = job.toBuilder()
            .status(MailboxDeliveryJob.DeliveryStatus.CANCELLED)
            .failureCount(job.failureCount() + 1)
            .lastFailureAt(now)
            .lastFailureReason(reason)
            .build();

        ignoreFuture(MailboxManager.INSTANCE.updateDeliveryJob(cancelled)
            .whenComplete((updated, updateError) -> {
                if (updateError != null || !Boolean.TRUE.equals(updated)) {
                    LOGGER.warn("[MailboxDelivery] Failed to persist cancelled job {}", job.id(), updateError);
                }
            }));

        totalExpired.increment();
        if (MailboxConfig.INSTANCE.isDeliveryRecallEnabled()) {
            ignoreFuture(MailboxManager.INSTANCE.sendDeliveryRecall(cancelled, reason)
                .thenRun(totalRecalls::increment));
        }
    }

    public record DeliveryStats(
        long totalAttempts,
        long totalDelivered,
        long totalFailed,
        long totalRetried,
        long totalRecalls,
        long totalExpired,
        int inFlight,
        boolean running,
        Instant lastDispatchAt
    ) {
        public Duration sinceLastDispatch() {
            if (lastDispatchAt == null || lastDispatchAt.equals(Instant.EPOCH)) {
                return Duration.ZERO;
            }
            return Duration.between(lastDispatchAt, Instant.now());
        }
    }

    private record DeliveryResult(boolean delivered, boolean countDelivery) {}
}
