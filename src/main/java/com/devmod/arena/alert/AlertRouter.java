package com.devmod.arena.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AlertRouter implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(AlertRouter.class.getName());
    private static volatile boolean loggingEnabled = true;

    /**
     * DD19: Maximum retry attempts for critical channels.
     */
    public static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Base delay for exponential backoff (milliseconds).
     */
    public static final long BASE_RETRY_DELAY_MS = 1000;

    /**
     * Retry queue capacity.
     */
    public static final int RETRY_QUEUE_CAPACITY = 1000;

    public static void setLoggingEnabled(boolean enabled) {
        loggingEnabled = enabled;
    }

    private final Map<String, AlertChannel> channels;
    private final BlockingQueue<RetryableAlert> retryQueue;
    private final ExecutorService deliveryExecutor;
    private final ScheduledExecutorService retryExecutor;
    private ScheduledFuture<?> retryTask;
    private final AtomicBoolean running;
    private final AtomicInteger deliveredCount;
    private final AtomicInteger failedCount;
    private final AtomicInteger retriedCount;
    private volatile AlertDeliveryRecorder deliveryRecorder;

    /**
     * Represents an alert channel for delivery.
     */
    public interface AlertChannel {
        /**
         * Unique channel identifier.
         */
        String getId();

        /**
         * Channel type for persistence/analytics.
         */
        default String getType() {
            return getClass().getSimpleName();
        }

        /**
         * Whether this channel is critical (requires retry on failure).
         */
        boolean isCritical();

        /**
         * Delivers an alert to this channel.
         *
         * @param context The error context to deliver
         * @return true if delivery succeeded
         */
        boolean deliver(ErrorContext context);
    }

    /**
     * Simple implementation of AlertChannel using a consumer.
     */
    public static class SimpleAlertChannel implements AlertChannel {
        private final String id;
        private final boolean critical;
        private final Consumer<ErrorContext> handler;

        public SimpleAlertChannel(String id, boolean critical, Consumer<ErrorContext> handler) {
            this.id = Objects.requireNonNull(id);
            this.critical = critical;
            this.handler = Objects.requireNonNull(handler);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isCritical() {
            return critical;
        }

        @Override
        public boolean deliver(ErrorContext context) {
            try {
                handler.accept(context);
                return true;
            } catch (Exception e) {
                if (loggingEnabled) {
                    LOGGER.log(Level.WARNING, "Failed to deliver to channel " + id, e);
                }
                return false;
            }
        }
    }

    /**
     * Delivery status for persistence/analytics.
     */
    public enum DeliveryStatus {
        DELIVERED,
        FAILED,
        RETRYING
    }

    /**
     * Optional recorder for alert delivery history.
     */
    public interface AlertDeliveryRecorder {
        void record(ErrorContext context,
                    AlertChannel channel,
                    DeliveryStatus status,
                    int attemptCount,
                    Instant nextRetryAt,
                    String errorMessage);
    }

    /**
     * Wrapper for alerts in the retry queue.
     */
    private record RetryableAlert(
        ErrorContext context,
        String channelId,
        int attemptCount,
        Instant nextRetryAt
    ) {
        RetryableAlert withIncrementedAttempt() {
            long delayMs = BASE_RETRY_DELAY_MS * (1L << Math.min(attemptCount, 10));
            return new RetryableAlert(
                context,
                channelId,
                attemptCount + 1,
                Instant.now().plusMillis(delayMs)
            );
        }

        boolean isReadyForRetry() {
            return Instant.now().isAfter(nextRetryAt);
        }

        boolean hasRetriesRemaining() {
            return attemptCount < MAX_RETRY_ATTEMPTS;
        }
    }

    /**
     * Creates a new AlertRouter with default configuration.
     */
    public AlertRouter() {
        this.channels = new ConcurrentHashMap<>();
        this.retryQueue = new LinkedBlockingQueue<>(RETRY_QUEUE_CAPACITY);
        this.deliveryExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "alert-retry-processor");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(true);
        this.deliveredCount = new AtomicInteger(0);
        this.failedCount = new AtomicInteger(0);
        this.retriedCount = new AtomicInteger(0);

        startRetryProcessor();
    }

    /**
     * Registers an alert channel.
     *
     * @param channel The channel to register
     * @return this router for chaining
     */
    public AlertRouter registerChannel(AlertChannel channel) {
        Objects.requireNonNull(channel, "channel must not be null");
        channels.put(channel.getId(), channel);
        if (loggingEnabled) {
            LOGGER.info("Registered alert channel: " + channel.getId() +
                        " (critical=" + channel.isCritical() + ")");
        }
        return this;
    }

    /**
     * Registers a simple channel with a consumer handler.
     *
     * @param id Channel identifier
     * @param critical Whether this is a critical channel
     * @param handler The handler consumer
     * @return this router for chaining
     */
    public AlertRouter registerChannel(String id, boolean critical, Consumer<ErrorContext> handler) {
        return registerChannel(new SimpleAlertChannel(id, critical, handler));
    }

    /**
     * Sets a delivery recorder to persist alert history.
     */
    public void setDeliveryRecorder(AlertDeliveryRecorder deliveryRecorder) {
        this.deliveryRecorder = deliveryRecorder;
    }

    /**
     * Unregisters an alert channel.
     *
     * @param channelId The channel ID to unregister
     * @return true if the channel was removed
     */
    public boolean unregisterChannel(String channelId) {
        AlertChannel removed = channels.remove(channelId);
        if (removed != null) {
            if (loggingEnabled) {
                LOGGER.info("Unregistered alert channel: " + channelId);
            }
            return true;
        }
        return false;
    }

    /**
     * Routes an alert to all registered channels (DD19).
     * Delivery is asynchronous and non-blocking.
     *
     * @param context The error context to route
     * @return CompletableFuture with delivery results
     */
    public CompletableFuture<AlertDeliveryResult> route(ErrorContext context) {
        Objects.requireNonNull(context, "context must not be null");

        if (!running.get()) {
            return CompletableFuture.completedFuture(
                new AlertDeliveryResult(context.errorId(), 0, channels.size(), Collections.emptyList())
            );
        }

        List<CompletableFuture<ChannelDeliveryResult>> futures = new ArrayList<>();

        // Deliver to all channels asynchronously
        for (AlertChannel channel : channels.values()) {
            CompletableFuture<ChannelDeliveryResult> future = CompletableFuture.supplyAsync(() -> {
                boolean success = false;
                String errorMessage = null;
                try {
                    success = channel.deliver(context);
                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    if (loggingEnabled) {
                        LOGGER.log(Level.WARNING, "Exception delivering to " + channel.getId(), e);
                    }
                }

                if (success) {
                    deliveredCount.incrementAndGet();
                    recordDelivery(context, channel, DeliveryStatus.DELIVERED, 1, null, errorMessage);
                } else {
                    failedCount.incrementAndGet();
                    // Queue for retry if critical channel
                    if (channel.isCritical()) {
                        RetryableAlert retry = queueForRetry(context, channel.getId());
                        if (retry != null) {
                            recordDelivery(context, channel, DeliveryStatus.RETRYING, 1, retry.nextRetryAt(), errorMessage);
                        } else {
                            recordDelivery(context, channel, DeliveryStatus.FAILED, 1, null, errorMessage);
                        }
                    } else {
                        recordDelivery(context, channel, DeliveryStatus.FAILED, 1, null, errorMessage);
                    }
                }

                return new ChannelDeliveryResult(channel.getId(), success, channel.isCritical());
            }, deliveryExecutor);

            futures.add(future);
        }

        // Combine all futures
        CompletableFuture<?>[] arr = futures.toArray(new CompletableFuture<?>[0]);
        return CompletableFuture.allOf(arr)
            .thenApply(v -> {
                List<ChannelDeliveryResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

                long successCount = results.stream().filter(ChannelDeliveryResult::success).count();
                long failedCountResult = results.size() - successCount;

                return new AlertDeliveryResult(
                    context.errorId(),
                    (int) successCount,
                    (int) failedCountResult,
                    results
                );
            });
    }

    /**
     * Routes an alert synchronously, waiting for completion.
     *
     * @param context The error context to route
     * @param timeout Maximum time to wait
     * @return Delivery result
     */
    public AlertDeliveryResult routeSync(ErrorContext context, Duration timeout) {
        try {
            return route(context).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (loggingEnabled) {
                LOGGER.log(Level.WARNING, "Sync route failed", e);
            }
            return new AlertDeliveryResult(context.errorId(), 0, channels.size(), Collections.emptyList());
        }
    }

    /**
     * Queues an alert for retry on a critical channel.
     */
    private RetryableAlert queueForRetry(ErrorContext context, String channelId) {
        RetryableAlert retryable = new RetryableAlert(
            context,
            channelId,
            1,
            Instant.now().plusMillis(BASE_RETRY_DELAY_MS)
        );

        if (!retryQueue.offer(retryable)) {
            if (loggingEnabled) {
                LOGGER.warning("Retry queue full, dropping alert for channel: " + channelId);
            }
            return null;
        }
        retriedCount.incrementAndGet();
        return retryable;
    }

    /**
     * Starts the background retry processor.
     */
    private void startRetryProcessor() {
        retryTask = retryExecutor.scheduleAtFixedRate(() -> {
            if (!running.get()) {
                return;
            }

            List<RetryableAlert> toRetry = new ArrayList<>();
            retryQueue.drainTo(toRetry);

            for (RetryableAlert alert : toRetry) {
                if (!alert.isReadyForRetry()) {
                    // Not ready yet, re-queue
                    if (!retryQueue.offer(alert) && loggingEnabled) {
                        LOGGER.warning("Retry queue full, dropping delayed retry for: " + alert.channelId());
                    }
                    continue;
                }

                AlertChannel channel = channels.get(alert.channelId());
                if (channel == null) {
                    // Channel no longer exists
                    continue;
                }

                boolean success = false;
                String errorMessage = null;
                try {
                    success = channel.deliver(alert.context());
                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    if (loggingEnabled) {
                        LOGGER.log(Level.WARNING, "Retry delivery failed for " + alert.channelId(), e);
                    }
                }

                if (success) {
                    deliveredCount.incrementAndGet();
                    recordDelivery(alert.context(), channel, DeliveryStatus.DELIVERED, alert.attemptCount() + 1, null, errorMessage);
                    if (loggingEnabled) {
                        LOGGER.fine("Retry succeeded for channel: " + alert.channelId());
                    }
                } else if (alert.hasRetriesRemaining()) {
                    // Re-queue with incremented attempt
                    RetryableAlert nextAttempt = alert.withIncrementedAttempt();
                    if (!retryQueue.offer(nextAttempt)) {
                        if (loggingEnabled) {
                            LOGGER.warning("Retry queue full, dropping retry for: " + alert.channelId());
                        }
                        recordDelivery(alert.context(), channel, DeliveryStatus.FAILED, alert.attemptCount() + 1, null, errorMessage);
                    } else {
                        recordDelivery(alert.context(), channel, DeliveryStatus.RETRYING,
                            alert.attemptCount() + 1, nextAttempt.nextRetryAt(), errorMessage);
                    }
                } else {
                    if (loggingEnabled) {
                        LOGGER.severe("Max retries exceeded for channel: " + alert.channelId() +
                                      ", error: " + alert.context().errorId());
                    }
                    recordDelivery(alert.context(), channel, DeliveryStatus.FAILED, alert.attemptCount() + 1, null, errorMessage);
                }
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    private void recordDelivery(ErrorContext context,
                                AlertChannel channel,
                                DeliveryStatus status,
                                int attemptCount,
                                Instant nextRetryAt,
                                String errorMessage) {
        AlertDeliveryRecorder recorder = deliveryRecorder;
        if (recorder == null) {
            return;
        }
        try {
            recorder.record(context, channel, status, attemptCount, nextRetryAt, errorMessage);
        } catch (Exception e) {
            if (loggingEnabled) {
                LOGGER.log(Level.WARNING, "Failed to record alert delivery for " + channel.getId(), e);
            }
        }
    }

    /**
     * Gets statistics about alert routing.
     */
    public AlertRouterStats getStats() {
        return new AlertRouterStats(
            channels.size(),
            deliveredCount.get(),
            failedCount.get(),
            retriedCount.get(),
            retryQueue.size()
        );
    }

    /**
     * Gets a list of registered channel IDs.
     */
    public List<String> getChannelIds() {
        return List.copyOf(channels.keySet());
    }

    /**
     * Checks if a channel is registered.
     */
    public boolean hasChannel(String channelId) {
        return channels.containsKey(channelId);
    }

    @Override
    public void close() {
        running.set(false);
        if (retryTask != null) {
            retryTask.cancel(false);
        }
        retryExecutor.shutdown();
        deliveryExecutor.shutdown();

        try {
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
            if (!deliveryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                deliveryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            retryExecutor.shutdownNow();
            deliveryExecutor.shutdownNow();
        }

        if (loggingEnabled) {
            LOGGER.info("AlertRouter closed. Stats: " + getStats());
        }
    }

    /**
     * Result of routing an alert to all channels.
     */
    public record AlertDeliveryResult(
        java.util.UUID errorId,
        int successCount,
        int failedCount,
        List<ChannelDeliveryResult> channelResults
    ) {
        public boolean isFullyDelivered() {
            return failedCount == 0;
        }

        public boolean isPartiallyDelivered() {
            return successCount > 0 && failedCount > 0;
        }
    }

    /**
     * Result of delivery to a single channel.
     */
    public record ChannelDeliveryResult(
        String channelId,
        boolean success,
        boolean isCritical
    ) {}

    /**
     * Statistics about the router's operation.
     */
    public record AlertRouterStats(
        int registeredChannels,
        int totalDelivered,
        int totalFailed,
        int totalRetried,
        int pendingRetries
    ) {}
}
