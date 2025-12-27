package com.devmod.mailbox.digest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MailboxManager;
import com.devmod.mailbox.MailboxMessage;
import com.devmod.mailbox.MessageType;

/**
 * Intelligent digest manager for aggregating notifications.
 *
 * Instead of spamming players with many individual notifications,
 * this system aggregates them into periodic digests based on:
 * - Player preferences (frequency, time of day)
 * - Message importance/priority
 * - Activity patterns
 *
 * Features:
 * - Per-player digest preferences
 * - Smart batching of notifications
 * - Priority-based immediate delivery
 * - Quiet hours support
 * - Digest templates with summaries
 */
public final class DigestManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DigestManager.class);

    public static final DigestManager INSTANCE = new DigestManager();

    // Player preferences
    private final Map<UUID, DigestPreferences> playerPreferences = new ConcurrentHashMap<>();

    // Pending messages per player (not yet digested)
    private final Map<UUID, List<PendingNotification>> pendingNotifications = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicInteger digestsSent = new AtomicInteger(0);
    private final AtomicInteger notificationsBatched = new AtomicInteger(0);
    private final AtomicInteger immediateDeliveries = new AtomicInteger(0);
    private final Queue<CompletableFuture<UUID>> pendingSends = new ConcurrentLinkedQueue<>();

    // Default settings
    private static final Duration DEFAULT_DIGEST_INTERVAL = Duration.ofHours(4);
    private static final int DEFAULT_BATCH_THRESHOLD = 5;
    private static final LocalTime DEFAULT_QUIET_START = LocalTime.of(22, 0);
    private static final LocalTime DEFAULT_QUIET_END = LocalTime.of(8, 0);

    @Nullable
    private ScheduledExecutorService scheduler;
    @Nullable
    private ScheduledFuture<?> digestTask;
    private volatile boolean running = false;

    private DigestManager() {}

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    /**
     * Start the digest manager.
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DigestManager");
            t.setDaemon(true);
            return t;
        });
        scheduler = sched;

        running = true;

        // Check for digest delivery every minute
        digestTask = sched.scheduleAtFixedRate(this::processDigests, 1, 1, TimeUnit.MINUTES);

        LOGGER.info("[DigestManager] Started");
    }

    /**
     * Stop the digest manager.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        ScheduledExecutorService sched = scheduler;
        if (sched != null) {
            cancelScheduledTask(digestTask);
            digestTask = null;
            sched.shutdown();
            try {
                if (!sched.awaitTermination(5, TimeUnit.SECONDS)) {
                    sched.shutdownNow();
                }
            } catch (InterruptedException e) {
                sched.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }

        pendingSends.clear();
        LOGGER.info("[DigestManager] Stopped. Stats: digests={}, batched={}, immediate={}",
            digestsSent.get(), notificationsBatched.get(), immediateDeliveries.get());
    }

    private void cancelScheduledTask(@Nullable ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }

    // ============================================================================
    // NOTIFICATION API
    // ============================================================================

    /**
     * Queue a notification for a player.
     * Will be delivered immediately or batched based on preferences.
     *
     * @param playerUuid the player's UUID
     * @param type the notification type
     * @param title the notification title
     * @param summary the notification summary
     * @param priority the priority (1-10, higher = more important)
     * @param metadata additional metadata
     * @return true if queued for digest, false if delivered immediately
     */
    public boolean queueNotification(
            UUID playerUuid,
            NotificationType type,
            String title,
            String summary,
            int priority,
            @Nullable Map<String, String> metadata
    ) {
        DigestPreferences prefs = getOrCreatePreferences(playerUuid);

        // High priority = immediate delivery
        if (priority >= prefs.immediatePriorityThreshold()) {
            deliverImmediately(playerUuid, type, title, summary, metadata);
            immediateDeliveries.incrementAndGet();
            return false;
        }

        // Digest disabled = immediate
        if (!prefs.digestEnabled()) {
            deliverImmediately(playerUuid, type, title, summary, metadata);
            immediateDeliveries.incrementAndGet();
            return false;
        }

        // Queue for digest
        PendingNotification notification = new PendingNotification(
            UUID.randomUUID(),
            type,
            title,
            summary,
            priority,
            metadata,
            Instant.now()
        );

        pendingNotifications.computeIfAbsent(playerUuid, k -> new ArrayList<>())
            .add(notification);
        notificationsBatched.incrementAndGet();

        // Check if we should send early (threshold reached)
        List<PendingNotification> pending = pendingNotifications.get(playerUuid);
        if (pending != null && pending.size() >= prefs.batchThreshold()) {
            sendDigest(playerUuid, prefs);
        }

        return true;
    }

    /**
     * Queue a message notification.
     */
    public boolean queueMessageNotification(UUID playerUuid, MailboxMessage message) {
        int priority = calculateMessagePriority(message);

        return queueNotification(
            playerUuid,
            NotificationType.NEW_MESSAGE,
            "New message: " + message.subject(),
            String.format("From: %s", message.senderName() != null ? message.senderName() : "System"),
            priority,
            Map.of(
                "messageId", message.id().toString(),
                "senderName", message.senderName() != null ? message.senderName() : "System",
                "hasAttachment", String.valueOf(message.hasAttachment())
            )
        );
    }

    /**
     * Force send any pending digest for a player.
     */
    public void flushDigest(UUID playerUuid) {
        DigestPreferences prefs = getOrCreatePreferences(playerUuid);
        sendDigest(playerUuid, prefs);
    }

    /**
     * Clear pending notifications for a player.
     */
    public void clearPending(UUID playerUuid) {
        pendingNotifications.remove(playerUuid);
    }

    // ============================================================================
    // PREFERENCES API
    // ============================================================================

    /**
     * Set digest preferences for a player.
     */
    public void setPreferences(UUID playerUuid, DigestPreferences preferences) {
        playerPreferences.put(playerUuid, preferences);
        LOGGER.debug("[DigestManager] Updated preferences for {}", playerUuid);
    }

    /**
     * Get digest preferences for a player.
     */
    public DigestPreferences getPreferences(UUID playerUuid) {
        return getOrCreatePreferences(playerUuid);
    }

    /**
     * Enable digest mode for a player.
     */
    public void enableDigest(UUID playerUuid) {
        DigestPreferences current = getOrCreatePreferences(playerUuid);
        playerPreferences.put(playerUuid, current.withDigestEnabled(true));
    }

    /**
     * Disable digest mode for a player (immediate notifications).
     */
    public void disableDigest(UUID playerUuid) {
        DigestPreferences current = getOrCreatePreferences(playerUuid);
        playerPreferences.put(playerUuid, current.withDigestEnabled(false));
        // Flush any pending
        flushDigest(playerUuid);
    }

    /**
     * Set quiet hours for a player.
     */
    public void setQuietHours(UUID playerUuid, LocalTime start, LocalTime end, ZoneId timezone) {
        DigestPreferences current = getOrCreatePreferences(playerUuid);
        playerPreferences.put(playerUuid, new DigestPreferences(
            current.digestEnabled(),
            current.digestInterval(),
            current.batchThreshold(),
            current.immediatePriorityThreshold(),
            start, end, timezone,
            current.lastDigestSent()
        ));
    }

    // ============================================================================
    // QUERY API
    // ============================================================================

    /**
     * Get pending notification count for a player.
     */
    public int getPendingCount(UUID playerUuid) {
        List<PendingNotification> pending = pendingNotifications.get(playerUuid);
        return pending != null ? pending.size() : 0;
    }

    /**
     * Get pending notifications for a player.
     */
    public List<PendingNotification> getPendingNotifications(UUID playerUuid) {
        List<PendingNotification> pending = pendingNotifications.get(playerUuid);
        return pending != null ? List.copyOf(pending) : List.of();
    }

    /**
     * Get statistics.
     */
    public DigestStats getStats() {
        int totalPending = pendingNotifications.values().stream()
            .mapToInt(List::size)
            .sum();

        return new DigestStats(
            playerPreferences.size(),
            totalPending,
            digestsSent.get(),
            notificationsBatched.get(),
            immediateDeliveries.get(),
            running
        );
    }

    // ============================================================================
    // PROCESSING
    // ============================================================================

    private void processDigests() {
        if (!running) {
            return;
        }

        Instant now = Instant.now();

        for (Map.Entry<UUID, List<PendingNotification>> entry : pendingNotifications.entrySet()) {
            UUID playerUuid = entry.getKey();
            List<PendingNotification> pending = entry.getValue();

            if (pending == null || pending.isEmpty()) {
                continue;
            }

            DigestPreferences prefs = getOrCreatePreferences(playerUuid);

            // Check if it's time for a digest
            if (shouldSendDigest(prefs, now)) {
                sendDigest(playerUuid, prefs);
            }
        }
    }

    private boolean shouldSendDigest(DigestPreferences prefs, Instant now) {
        // Check quiet hours
        if (isQuietHours(prefs, now)) {
            return false;
        }

        // Check interval
        if (prefs.lastDigestSent() == null) {
            return true;
        }

        Duration elapsed = Duration.between(prefs.lastDigestSent(), now);
        return elapsed.compareTo(prefs.digestInterval()) >= 0;
    }

    private boolean isQuietHours(DigestPreferences prefs, Instant now) {
        LocalTime start = prefs.quietHoursStart();
        LocalTime end = prefs.quietHoursEnd();
        if (start == null || end == null) {
            return false;
        }

        ZoneId zone = prefs.timezone() != null ? prefs.timezone() : ZoneId.systemDefault();
        LocalTime currentTime = LocalTime.ofInstant(now, zone);

        // Handle overnight quiet hours (e.g., 22:00 - 08:00)
        if (start.isAfter(end)) {
            return currentTime.isAfter(start) || currentTime.isBefore(end);
        } else {
            return currentTime.isAfter(start) && currentTime.isBefore(end);
        }
    }

    private void sendDigest(UUID playerUuid, DigestPreferences prefs) {
        List<PendingNotification> pending = pendingNotifications.remove(playerUuid);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        // Sort by priority (highest first)
        pending.sort(Comparator.comparingInt(PendingNotification::priority).reversed());

        // Group by type
        Map<NotificationType, List<PendingNotification>> grouped = pending.stream()
            .collect(Collectors.groupingBy(PendingNotification::type));

        // Build digest message
        StringBuilder body = new StringBuilder();
        body.append("Here's your notification digest:\n\n");

        for (Map.Entry<NotificationType, List<PendingNotification>> group : grouped.entrySet()) {
            body.append("📋 ").append(group.getKey().getDisplayName())
                .append(" (").append(group.getValue().size()).append(")\n");

            for (PendingNotification notification : group.getValue()) {
                body.append("  • ").append(notification.title()).append("\n");
                String summary = notification.summary();
                if (summary != null && !summary.isEmpty()) {
                    body.append("    ").append(summary).append("\n");
                }
            }
            body.append("\n");
        }

        String subject = String.format("Notification Digest (%d new)", pending.size());

        // Send as system message
        CompletableFuture<UUID> sendFuture = MailboxManager.INSTANCE.sendSystemMessage(
            playerUuid,
            subject,
            body.toString(),
            null,
            Duration.ofDays(7)
        );
        CompletableFuture<UUID> trackedFuture = sendFuture.whenComplete((id, error) -> {
            if (error != null) {
                LOGGER.error("[DigestManager] Failed to send digest to {}: {}",
                    playerUuid, error.getMessage());
                // Re-queue notifications
                pendingNotifications.computeIfAbsent(playerUuid, k -> new ArrayList<>())
                    .addAll(pending);
            } else {
                digestsSent.incrementAndGet();
                LOGGER.info("[DigestManager] Sent digest to {} with {} notifications",
                    playerUuid, pending.size());
            }
        });
        trackSendFuture(trackedFuture);

        // Update last sent time
        playerPreferences.put(playerUuid, prefs.withLastDigestSent(Instant.now()));
    }

    private void deliverImmediately(
            UUID playerUuid,
            NotificationType type,
            String title,
            String summary,
            @Nullable Map<String, String> metadata
    ) {
        String subject = type.getEmoji() + " " + title;
        String body = summary;

        if (metadata != null && !metadata.isEmpty()) {
            StringBuilder sb = new StringBuilder(summary);
            sb.append("\n\nDetails:\n");
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            body = sb.toString();
        }

        CompletableFuture<UUID> sendFuture = MailboxManager.INSTANCE.sendSystemMessage(
            playerUuid,
            subject,
            body,
            null,
            Duration.ofDays(3)
        );
        CompletableFuture<UUID> trackedFuture = sendFuture.whenComplete((id, error) -> {
            if (error != null) {
                LOGGER.error("[DigestManager] Failed to send immediate notification to {}: {}",
                    playerUuid, error.getMessage());
            }
        });
        trackSendFuture(trackedFuture);
    }

    private void trackSendFuture(CompletableFuture<UUID> sendFuture) {
        pendingSends.removeIf(CompletableFuture::isDone);
        pendingSends.add(sendFuture);
    }

    private DigestPreferences getOrCreatePreferences(UUID playerUuid) {
        return playerPreferences.computeIfAbsent(playerUuid, k -> new DigestPreferences(
            true, // digest enabled by default
            DEFAULT_DIGEST_INTERVAL,
            DEFAULT_BATCH_THRESHOLD,
            8, // priority 8+ is immediate
            DEFAULT_QUIET_START,
            DEFAULT_QUIET_END,
            ZoneId.systemDefault(),
            null
        ));
    }

    private int calculateMessagePriority(MailboxMessage message) {
        int priority = 5; // Base priority

        // System/Admin messages are higher priority
        if (message.messageType() == MessageType.SYSTEM) {
            priority += 2;
        } else if (message.messageType() == MessageType.ADMIN) {
            priority += 3;
        }

        // Messages with attachments are higher priority
        if (message.hasAttachment()) {
            priority += 2;
        }

        return Math.min(10, priority);
    }

    // ============================================================================
    // TYPES
    // ============================================================================

    public enum NotificationType {
        NEW_MESSAGE("New Messages", "📬"),
        ATTACHMENT_RECEIVED("Attachments", "🎁"),
        NEWS_PUBLISHED("News", "📰"),
        TASK_ASSIGNED("Tasks", "📋"),
        BROADCAST("Broadcasts", "📢"),
        SYSTEM_ALERT("Alerts", "⚠️");

        private final String displayName;
        private final String emoji;

        NotificationType(String displayName, String emoji) {
            this.displayName = displayName;
            this.emoji = emoji;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getEmoji() {
            return emoji;
        }
    }

    public record PendingNotification(
        UUID id,
        NotificationType type,
        String title,
        @Nullable String summary,
        int priority,
        @Nullable Map<String, String> metadata,
        Instant queuedAt
    ) {}

    public record DigestPreferences(
        boolean digestEnabled,
        Duration digestInterval,
        int batchThreshold,
        int immediatePriorityThreshold,
        @Nullable LocalTime quietHoursStart,
        @Nullable LocalTime quietHoursEnd,
        @Nullable ZoneId timezone,
        @Nullable Instant lastDigestSent
    ) {
        public DigestPreferences withDigestEnabled(boolean enabled) {
            return new DigestPreferences(enabled, digestInterval, batchThreshold,
                immediatePriorityThreshold, quietHoursStart, quietHoursEnd, timezone, lastDigestSent);
        }

        public DigestPreferences withLastDigestSent(Instant time) {
            return new DigestPreferences(digestEnabled, digestInterval, batchThreshold,
                immediatePriorityThreshold, quietHoursStart, quietHoursEnd, timezone, time);
        }
    }

    public record DigestStats(
        int playersWithPreferences,
        int totalPending,
        int digestsSent,
        int notificationsBatched,
        int immediateDeliveries,
        boolean running
    ) {}
}
