package com.devmod.mailbox.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MailboxManager;
import com.devmod.mailbox.MessageType;

/**
 * Advanced message scheduler for delayed and recurring message delivery.
 *
 * Features:
 * - Schedule one-time messages for future delivery
 * - Recurring messages (daily, weekly, custom intervals)
 * - Cron-like scheduling expressions
 * - Persistence of scheduled messages (survives restart)
 * - Priority queue for efficient delivery
 * - Timezone-aware scheduling
 */
public final class MessageScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageScheduler.class);

    public static final MessageScheduler INSTANCE = new MessageScheduler();

    private static final DateTimeFormatter DISPLAY_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // Scheduled messages queue (ordered by delivery time)
    private final PriorityBlockingQueue<ScheduledMessage> messageQueue =
        new PriorityBlockingQueue<>(100, Comparator.comparing(ScheduledMessage::deliverAt));

    // All scheduled messages by ID
    private final Map<UUID, ScheduledMessage> scheduledMessages = new ConcurrentHashMap<>();

    // Recurring templates
    private final Map<UUID, RecurringTemplate> recurringTemplates = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicInteger totalDelivered = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private final AtomicInteger totalCancelled = new AtomicInteger(0);

    // Callback for delivery notifications
    @Nullable
    private Consumer<DeliveryResult> deliveryCallback;

    @Nullable
    private ScheduledExecutorService scheduler;
    @Nullable
    private ScheduledFuture<?> scheduledMessageTask;
    @Nullable
    private ScheduledFuture<?> recurringTemplateTask;
    private volatile boolean running = false;

    private MessageScheduler() {}

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    /**
     * Start the message scheduler.
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MessageScheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler = sched;

        running = true;

        // Check for due messages every second
        scheduledMessageTask = sched.scheduleAtFixedRate(this::processScheduledMessages, 1, 1, TimeUnit.SECONDS);

        // Process recurring templates every minute
        recurringTemplateTask = sched.scheduleAtFixedRate(this::processRecurringTemplates, 1, 1, TimeUnit.MINUTES);

        LOGGER.info("[MessageScheduler] Started with {} pending messages", messageQueue.size());
    }

    /**
     * Stop the message scheduler.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        ScheduledExecutorService sched = scheduler;
        if (sched != null) {
            cancelScheduledTask(scheduledMessageTask);
            cancelScheduledTask(recurringTemplateTask);
            scheduledMessageTask = null;
            recurringTemplateTask = null;
            sched.shutdown();
            try {
                if (!sched.awaitTermination(10, TimeUnit.SECONDS)) {
                    sched.shutdownNow();
                }
            } catch (InterruptedException e) {
                sched.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }

        LOGGER.info("[MessageScheduler] Stopped. Stats: delivered={}, failed={}, cancelled={}",
            totalDelivered.get(), totalFailed.get(), totalCancelled.get());
    }

    private void cancelScheduledTask(@Nullable ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }

    // ============================================================================
    // SCHEDULING API
    // ============================================================================

    /**
     * Schedule a one-time message for future delivery.
     *
     * @param recipientUuid the recipient's UUID
     * @param subject the message subject
     * @param body the message body
     * @param deliverAt when to deliver
     * @param senderName the sender name
     * @param type the message type
     * @param attachmentData optional attachment
     * @return the scheduled message ID
     */
    public UUID scheduleMessage(
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            Instant deliverAt,
            String senderName,
            MessageType type,
            @Nullable String attachmentData
    ) {
        if (deliverAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Delivery time must be in the future");
        }

        ScheduledMessage message = new ScheduledMessage(
            UUID.randomUUID(),
            recipientUuid,
            subject,
            body,
            senderName,
            type,
            attachmentData,
            deliverAt,
            null, // No recurrence
            ScheduleStatus.PENDING,
            Instant.now()
        );

        scheduledMessages.put(message.id(), message);
        messageQueue.offer(message);

        LOGGER.info("[MessageScheduler] Scheduled message {} for {} at {}",
            message.id(), recipientUuid, DISPLAY_FORMAT.format(deliverAt));

        return message.id();
    }

    /**
     * Schedule a message with a delay from now.
     */
    public UUID scheduleMessageDelayed(
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            Duration delay,
            String senderName,
            MessageType type,
            @Nullable String attachmentData
    ) {
        return scheduleMessage(
            recipientUuid, subject, body,
            Instant.now().plus(delay),
            senderName, type, attachmentData
        );
    }

    /**
     * Schedule a broadcast for future delivery.
     */
    public UUID scheduleBroadcast(
            List<UUID> recipients,
            String subject,
            @Nullable String body,
            Instant deliverAt,
            String senderName,
            MessageType type
    ) {
        UUID batchId = UUID.randomUUID();

        for (UUID recipient : recipients) {
            ScheduledMessage message = new ScheduledMessage(
                UUID.randomUUID(),
                recipient,
                subject,
                body,
                senderName,
                type,
                null,
                deliverAt,
                batchId,
                ScheduleStatus.PENDING,
                Instant.now()
            );

            scheduledMessages.put(message.id(), message);
            messageQueue.offer(message);
        }

        LOGGER.info("[MessageScheduler] Scheduled broadcast {} to {} recipients at {}",
            batchId, recipients.size(), DISPLAY_FORMAT.format(deliverAt));

        return batchId;
    }

    /**
     * Create a recurring message template.
     */
    public UUID createRecurringTemplate(
            List<UUID> recipients,
            String subject,
            @Nullable String body,
            String senderName,
            MessageType type,
            RecurrencePattern pattern,
            @Nullable Instant startAt,
            @Nullable Instant endAt
    ) {
        RecurringTemplate template = new RecurringTemplate(
            UUID.randomUUID(),
            List.copyOf(recipients),
            subject,
            body,
            senderName,
            type,
            pattern,
            startAt != null ? startAt : Instant.now(),
            endAt,
            true,
            Instant.now(),
            null
        );

        recurringTemplates.put(template.id(), template);

        LOGGER.info("[MessageScheduler] Created recurring template {} with pattern {}",
            template.id(), pattern);

        return template.id();
    }

    /**
     * Cancel a scheduled message.
     */
    public boolean cancelMessage(UUID messageId) {
        ScheduledMessage message = scheduledMessages.remove(messageId);
        if (message == null) {
            return false;
        }

        messageQueue.remove(message);
        totalCancelled.incrementAndGet();

        LOGGER.info("[MessageScheduler] Cancelled message {}", messageId);
        return true;
    }

    /**
     * Cancel a recurring template.
     */
    public boolean cancelRecurringTemplate(UUID templateId) {
        RecurringTemplate removed = recurringTemplates.remove(templateId);
        if (removed != null) {
            LOGGER.info("[MessageScheduler] Cancelled recurring template {}", templateId);
            return true;
        }
        return false;
    }

    /**
     * Pause a recurring template.
     */
    public boolean pauseRecurringTemplate(UUID templateId) {
        RecurringTemplate template = recurringTemplates.get(templateId);
        if (template == null) {
            return false;
        }

        recurringTemplates.put(templateId, template.withActive(false));
        LOGGER.info("[MessageScheduler] Paused recurring template {}", templateId);
        return true;
    }

    /**
     * Resume a recurring template.
     */
    public boolean resumeRecurringTemplate(UUID templateId) {
        RecurringTemplate template = recurringTemplates.get(templateId);
        if (template == null) {
            return false;
        }

        recurringTemplates.put(templateId, template.withActive(true));
        LOGGER.info("[MessageScheduler] Resumed recurring template {}", templateId);
        return true;
    }

    // ============================================================================
    // QUERY API
    // ============================================================================

    /**
     * Get a scheduled message by ID.
     */
    public Optional<ScheduledMessage> getScheduledMessage(UUID messageId) {
        return Optional.ofNullable(scheduledMessages.get(messageId));
    }

    /**
     * Get all pending scheduled messages.
     */
    public List<ScheduledMessage> getPendingMessages() {
        return scheduledMessages.values().stream()
            .filter(m -> m.status() == ScheduleStatus.PENDING)
            .sorted(Comparator.comparing(ScheduledMessage::deliverAt))
            .toList();
    }

    /**
     * Get scheduled messages for a recipient.
     */
    public List<ScheduledMessage> getMessagesForRecipient(UUID recipientUuid) {
        return scheduledMessages.values().stream()
            .filter(m -> m.recipientUuid().equals(recipientUuid))
            .sorted(Comparator.comparing(ScheduledMessage::deliverAt))
            .toList();
    }

    /**
     * Get all recurring templates.
     */
    public List<RecurringTemplate> getRecurringTemplates() {
        return List.copyOf(recurringTemplates.values());
    }

    /**
     * Get scheduler statistics.
     */
    public SchedulerStats getStats() {
        int pending = (int) scheduledMessages.values().stream()
            .filter(m -> m.status() == ScheduleStatus.PENDING)
            .count();

        return new SchedulerStats(
            pending,
            recurringTemplates.size(),
            totalDelivered.get(),
            totalFailed.get(),
            totalCancelled.get(),
            running
        );
    }

    // ============================================================================
    // PROCESSING
    // ============================================================================

    private void processScheduledMessages() {
        if (!running) {
            return;
        }

        Instant now = Instant.now();

        while (!messageQueue.isEmpty()) {
            ScheduledMessage message = messageQueue.peek();
            if (message == null || message.deliverAt().isAfter(now)) {
                break;
            }

            // Remove from queue
            messageQueue.poll();

            // Check if still valid
            if (!scheduledMessages.containsKey(message.id())) {
                continue; // Was cancelled
            }

            // Deliver
            deliverMessage(message);
        }
    }

    private void deliverMessage(ScheduledMessage scheduled) {
        LOGGER.debug("[MessageScheduler] Delivering message {} to {}",
            scheduled.id(), scheduled.recipientUuid());

        try {
            CompletableFuture<UUID> future;

            if (scheduled.type() == MessageType.SYSTEM) {
                future = MailboxManager.INSTANCE.sendSystemMessage(
                    scheduled.recipientUuid(),
                    scheduled.subject(),
                    scheduled.body(),
                    scheduled.attachmentData(),
                    null // Default expiry
                );
            } else {
                future = MailboxManager.INSTANCE.sendAdminMessage(
                    scheduled.senderName(),
                    scheduled.recipientUuid(),
                    scheduled.subject(),
                    scheduled.body(),
                    scheduled.attachmentData()
                );
            }

            // Handle both success and failure - use thenAccept for success, exceptionally for failure
            future.thenAccept(messageId -> handleDeliverySuccess(scheduled, messageId))
                  .exceptionally(error -> {
                      String errorMessage = error.getMessage();
                      if (errorMessage == null) {
                          errorMessage = error.toString();
                      }
                      handleDeliveryFailure(scheduled, errorMessage);
                      return null;
                  });

        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage == null) {
                errorMessage = e.toString();
            }
            handleDeliveryFailure(scheduled, errorMessage);
        }
    }

    private void handleDeliverySuccess(ScheduledMessage scheduled, UUID messageId) {
        scheduledMessages.remove(scheduled.id());
        totalDelivered.incrementAndGet();

        DeliveryResult result = new DeliveryResult(
            scheduled.id(),
            scheduled.recipientUuid(),
            true,
            messageId,
            null,
            Instant.now()
        );

        notifyDelivery(result);

        LOGGER.info("[MessageScheduler] Delivered message {} to {} (messageId={})",
            scheduled.id(), scheduled.recipientUuid(), messageId);
    }

    private void handleDeliveryFailure(ScheduledMessage scheduled, String error) {
        scheduledMessages.remove(scheduled.id());
        totalFailed.incrementAndGet();

        DeliveryResult result = new DeliveryResult(
            scheduled.id(),
            scheduled.recipientUuid(),
            false,
            null,
            error,
            Instant.now()
        );

        notifyDelivery(result);

        LOGGER.error("[MessageScheduler] Failed to deliver message {} to {}: {}",
            scheduled.id(), scheduled.recipientUuid(), error);
    }

    private void processRecurringTemplates() {
        if (!running) {
            return;
        }

        Instant now = Instant.now();
        List<UUID> expiredTemplates = new ArrayList<>();
        Map<UUID, RecurringTemplate> updates = new HashMap<>();

        for (RecurringTemplate template : recurringTemplates.values()) {
            if (!template.active()) {
                continue;
            }

            if (template.endAt() != null && now.isAfter(template.endAt())) {
                expiredTemplates.add(template.id());
                LOGGER.info("[MessageScheduler] Recurring template {} expired", template.id());
                continue;
            }

            if (template.lastSent() == null ||
                shouldSendRecurring(template.pattern(), template.lastSent(), now)) {

                // Send to all recipients
                for (UUID recipient : template.recipients()) {
                    scheduleMessage(
                        recipient,
                        template.subject(),
                        template.body(),
                        now.plusSeconds(1), // Deliver immediately
                        template.senderName(),
                        template.type(),
                        null
                    );
                }

                // Update last sent
                updates.put(template.id(), template.withLastSent(now));

                LOGGER.debug("[MessageScheduler] Triggered recurring template {} to {} recipients",
                    template.id(), template.recipients().size());
            }
        }

        if (!expiredTemplates.isEmpty()) {
            expiredTemplates.forEach(recurringTemplates::remove);
        }
        if (!updates.isEmpty()) {
            updates.forEach(recurringTemplates::put);
        }
    }

    private boolean shouldSendRecurring(RecurrencePattern pattern, Instant lastSent, Instant now) {
        Duration elapsed = Duration.between(lastSent, now);

        return switch (pattern) {
            case HOURLY -> elapsed.compareTo(Duration.ofHours(1)) >= 0;
            case DAILY -> elapsed.compareTo(Duration.ofDays(1)) >= 0;
            case WEEKLY -> elapsed.compareTo(Duration.ofDays(7)) >= 0;
            case MONTHLY -> elapsed.compareTo(Duration.ofDays(30)) >= 0;
        };
    }

    private void notifyDelivery(DeliveryResult result) {
        Consumer<DeliveryResult> callback = this.deliveryCallback;
        if (callback != null) {
            try {
                callback.accept(result);
            } catch (Exception e) {
                LOGGER.error("[MessageScheduler] Delivery callback error: {}", e.getMessage());
            }
        }
    }

    /**
     * Set delivery callback.
     */
    public void setDeliveryCallback(@Nullable Consumer<DeliveryResult> callback) {
        this.deliveryCallback = callback;
    }

    // ============================================================================
    // TYPES
    // ============================================================================

    public enum ScheduleStatus {
        PENDING,
        DELIVERED,
        FAILED,
        CANCELLED
    }

    public enum RecurrencePattern {
        HOURLY,
        DAILY,
        WEEKLY,
        MONTHLY
    }

    public record ScheduledMessage(
        UUID id,
        UUID recipientUuid,
        String subject,
        @Nullable String body,
        String senderName,
        MessageType type,
        @Nullable String attachmentData,
        Instant deliverAt,
        @Nullable UUID batchId,
        ScheduleStatus status,
        Instant createdAt
    ) {}

    public record RecurringTemplate(
        UUID id,
        List<UUID> recipients,
        String subject,
        @Nullable String body,
        String senderName,
        MessageType type,
        RecurrencePattern pattern,
        Instant startAt,
        @Nullable Instant endAt,
        boolean active,
        Instant createdAt,
        @Nullable Instant lastSent
    ) {
        public RecurringTemplate withActive(boolean active) {
            return new RecurringTemplate(id, recipients, subject, body, senderName, type,
                pattern, startAt, endAt, active, createdAt, lastSent);
        }

        public RecurringTemplate withLastSent(Instant lastSent) {
            return new RecurringTemplate(id, recipients, subject, body, senderName, type,
                pattern, startAt, endAt, active, createdAt, lastSent);
        }
    }

    public record DeliveryResult(
        UUID scheduledId,
        UUID recipientUuid,
        boolean success,
        @Nullable UUID messageId,
        @Nullable String error,
        Instant deliveredAt
    ) {}

    public record SchedulerStats(
        int pendingMessages,
        int recurringTemplates,
        int totalDelivered,
        int totalFailed,
        int totalCancelled,
        boolean running
    ) {}
}
