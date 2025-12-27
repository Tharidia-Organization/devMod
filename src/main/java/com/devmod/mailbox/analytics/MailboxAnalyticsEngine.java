package com.devmod.mailbox.analytics;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MessageType;

/**
 * Advanced analytics engine for the mailbox system.
 *
 * Provides real-time metrics, trend analysis, and insights for:
 * - Message volume and patterns
 * - Player engagement and activity
 * - Broadcast effectiveness
 * - Attachment claim rates
 * - Peak activity detection
 * - Anomaly detection (spam patterns)
 */
public final class MailboxAnalyticsEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxAnalyticsEngine.class);

    public static final MailboxAnalyticsEngine INSTANCE = new MailboxAnalyticsEngine();

    // Time windows for analysis
    private static final int HOURLY_BUCKETS = 24;
    private static final int DAILY_BUCKETS = 30;
    private static final int MINUTE_BUCKETS = 60;

    // Rolling window data structures
    private final LongAdder[] messagesByHour = new LongAdder[HOURLY_BUCKETS];
    private final LongAdder[] messagesByDay = new LongAdder[DAILY_BUCKETS];
    private final LongAdder[] messagesByMinute = new LongAdder[MINUTE_BUCKETS];

    // Message type breakdown
    private final Map<MessageType, LongAdder> messagesByType = new EnumMap<>(MessageType.class);

    // Player activity tracking
    private final Map<UUID, PlayerActivityStats> playerStats = new ConcurrentHashMap<>();

    // Broadcast analytics
    private final List<BroadcastMetric> broadcastHistory = new ArrayList<>();
    private static final int MAX_BROADCAST_HISTORY = 100;

    // Attachment analytics
    private final LongAdder totalAttachmentsSent = new LongAdder();
    private final LongAdder totalAttachmentsClaimed = new LongAdder();
    private final LongAdder totalAttachmentsExpired = new LongAdder();

    // Anomaly detection
    private final Map<UUID, SenderProfile> senderProfiles = new ConcurrentHashMap<>();
    private final List<AnomalyAlert> anomalyAlerts = new ArrayList<>();
    private static final int MAX_ALERTS = 200;

    // Real-time counters
    private final LongAdder totalMessagesSent = new LongAdder();
    private final LongAdder totalMessagesRead = new LongAdder();
    private final LongAdder totalMessagesDeleted = new LongAdder();

    // Time tracking
    private volatile int currentHour = getCurrentHour();
    private volatile int currentDay = getCurrentDay();
    private volatile int currentMinute = getCurrentMinute();

    @Nullable
    private ScheduledExecutorService scheduler;
    @Nullable
    private ScheduledFuture<?> bucketRotationTask;
    @Nullable
    private ScheduledFuture<?> anomalyDetectionTask;
    @Nullable
    private ScheduledFuture<?> cleanupTask;

    private MailboxAnalyticsEngine() {
        // Initialize adders
        for (int i = 0; i < HOURLY_BUCKETS; i++) {
            messagesByHour[i] = new LongAdder();
        }
        for (int i = 0; i < DAILY_BUCKETS; i++) {
            messagesByDay[i] = new LongAdder();
        }
        for (int i = 0; i < MINUTE_BUCKETS; i++) {
            messagesByMinute[i] = new LongAdder();
        }
        for (MessageType type : MessageType.values()) {
            messagesByType.put(type, new LongAdder());
        }
    }

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    /**
     * Start the analytics engine.
     */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }

        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MailboxAnalytics");
            t.setDaemon(true);
            return t;
        });
        scheduler = sched;

        // Rotate buckets every minute
        bucketRotationTask = sched.scheduleAtFixedRate(this::rotateBuckets, 1, 1, TimeUnit.MINUTES);

        // Run anomaly detection every 5 minutes
        anomalyDetectionTask = sched.scheduleAtFixedRate(this::runAnomalyDetection, 5, 5, TimeUnit.MINUTES);

        // Cleanup old data every hour
        cleanupTask = sched.scheduleAtFixedRate(this::cleanupOldData, 1, 1, TimeUnit.HOURS);

        LOGGER.info("[MailboxAnalytics] Analytics engine started");
    }

    /**
     * Stop the analytics engine.
     */
    public synchronized void stop() {
        ScheduledExecutorService sched = scheduler;
        if (sched != null) {
            cancelScheduledTask(bucketRotationTask);
            cancelScheduledTask(anomalyDetectionTask);
            cancelScheduledTask(cleanupTask);
            bucketRotationTask = null;
            anomalyDetectionTask = null;
            cleanupTask = null;
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
        LOGGER.info("[MailboxAnalytics] Analytics engine stopped");
    }

    private void cancelScheduledTask(@Nullable ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }

    // ============================================================================
    // EVENT RECORDING
    // ============================================================================

    /**
     * Record a message sent event.
     */
    public void recordMessageSent(UUID senderUuid, UUID recipientUuid, MessageType type, boolean hasAttachment) {
        totalMessagesSent.increment();
        messagesByType.computeIfAbsent(type, ignored -> new LongAdder()).increment();
        messagesByHour[getCurrentHour()].increment();
        messagesByDay[getCurrentDay()].increment();
        messagesByMinute[getCurrentMinute()].increment();

        if (hasAttachment) {
            totalAttachmentsSent.increment();
        }

        // Update sender profile for anomaly detection
        if (senderUuid != null) {
            senderProfiles.computeIfAbsent(senderUuid, k -> new SenderProfile()).recordSend();
        }

        // Update player stats
        if (senderUuid != null) {
            playerStats.computeIfAbsent(senderUuid, PlayerActivityStats::new).recordSent();
        }
        playerStats.computeIfAbsent(recipientUuid, PlayerActivityStats::new).recordReceived();
    }

    /**
     * Record a message read event.
     */
    public void recordMessageRead(UUID playerUuid, Duration timeToRead) {
        totalMessagesRead.increment();
        playerStats.computeIfAbsent(playerUuid, PlayerActivityStats::new).recordRead(timeToRead);
    }

    /**
     * Record a message deleted event.
     */
    public void recordMessageDeleted(UUID playerUuid, boolean wasRead) {
        totalMessagesDeleted.increment();
        playerStats.computeIfAbsent(playerUuid, PlayerActivityStats::new).recordDeleted(wasRead);
    }

    /**
     * Record an attachment claimed event.
     */
    public void recordAttachmentClaimed(UUID playerUuid, String attachmentType, int value) {
        totalAttachmentsClaimed.increment();
        playerStats.computeIfAbsent(playerUuid, PlayerActivityStats::new).recordAttachmentClaimed(value);
    }

    /**
     * Record an attachment expired event.
     */
    public void recordAttachmentExpired() {
        totalAttachmentsExpired.increment();
    }

    /**
     * Record a broadcast event.
     */
    public void recordBroadcast(String subject, int recipientCount, int successCount, Duration duration) {
        BroadcastMetric metric = new BroadcastMetric(
            Instant.now(),
            subject,
            recipientCount,
            successCount,
            duration
        );

        synchronized (broadcastHistory) {
            broadcastHistory.add(metric);
            while (broadcastHistory.size() > MAX_BROADCAST_HISTORY) {
                broadcastHistory.remove(0);
            }
        }
    }

    // ============================================================================
    // ANALYTICS QUERIES
    // ============================================================================

    /**
     * Get comprehensive dashboard metrics.
     */
    public DashboardMetrics getDashboardMetrics() {
        long total = totalMessagesSent.sum();
        long read = totalMessagesRead.sum();
        long deleted = totalMessagesDeleted.sum();

        double readRate = total > 0 ? (read * 100.0) / total : 0;
        double claimRate = totalAttachmentsSent.sum() > 0
            ? (totalAttachmentsClaimed.sum() * 100.0) / totalAttachmentsSent.sum()
            : 0;

        return new DashboardMetrics(
            total,
            read,
            deleted,
            readRate,
            totalAttachmentsSent.sum(),
            totalAttachmentsClaimed.sum(),
            claimRate,
            playerStats.size(),
            getMessagesPerHour(),
            getMessagesPerDay(),
            getPeakHour(),
            getMessageTypeBreakdown(),
            getAnomalyAlertCount()
        );
    }

    /**
     * Get hourly message volume (last 24 hours).
     */
    public List<HourlyVolume> getHourlyVolume() {
        List<HourlyVolume> volumes = new ArrayList<>(HOURLY_BUCKETS);
        int current = getCurrentHour();

        for (int i = 0; i < HOURLY_BUCKETS; i++) {
            int hourIndex = (current - i + HOURLY_BUCKETS) % HOURLY_BUCKETS;
            volumes.add(new HourlyVolume(
                (current - i + HOURLY_BUCKETS) % 24,
                messagesByHour[hourIndex].sum()
            ));
        }

        return volumes;
    }

    /**
     * Get daily message volume (last 30 days).
     */
    public List<DailyVolume> getDailyVolume() {
        List<DailyVolume> volumes = new ArrayList<>(DAILY_BUCKETS);
        int current = getCurrentDay();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (int i = 0; i < DAILY_BUCKETS; i++) {
            int dayIndex = (current - i + DAILY_BUCKETS) % DAILY_BUCKETS;
            volumes.add(new DailyVolume(
                today.minusDays(i),
                messagesByDay[dayIndex].sum()
            ));
        }

        return volumes;
    }

    /**
     * Get message type breakdown.
     */
    public Map<MessageType, Long> getMessageTypeBreakdown() {
        return messagesByType.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum()));
    }

    /**
     * Get top senders.
     */
    public List<PlayerRanking> getTopSenders(int limit) {
        return playerStats.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> -e.getValue().messagesSent.sum()))
            .limit(limit)
            .map(e -> new PlayerRanking(e.getKey(), e.getValue().messagesSent.sum()))
            .toList();
    }

    /**
     * Get top receivers.
     */
    public List<PlayerRanking> getTopReceivers(int limit) {
        return playerStats.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> -e.getValue().messagesReceived.sum()))
            .limit(limit)
            .map(e -> new PlayerRanking(e.getKey(), e.getValue().messagesReceived.sum()))
            .toList();
    }

    /**
     * Get most engaged players (by read rate).
     */
    public List<EngagementRanking> getMostEngagedPlayers(int limit) {
        return playerStats.entrySet().stream()
            .filter(e -> e.getValue().messagesReceived.sum() >= 5) // Minimum threshold
            .sorted(Comparator.comparingDouble(e -> -e.getValue().getReadRate()))
            .limit(limit)
            .map(e -> new EngagementRanking(
                e.getKey(),
                e.getValue().getReadRate(),
                e.getValue().messagesReceived.sum()
            ))
            .toList();
    }

    /**
     * Get broadcast effectiveness report.
     */
    public BroadcastReport getBroadcastReport() {
        synchronized (broadcastHistory) {
            if (broadcastHistory.isEmpty()) {
                return new BroadcastReport(0, 0, 0, Duration.ZERO, List.of());
            }

            int totalBroadcasts = broadcastHistory.size();
            long totalRecipients = broadcastHistory.stream()
                .mapToLong(BroadcastMetric::recipientCount)
                .sum();
            double avgSuccessRate = broadcastHistory.stream()
                .mapToDouble(b -> b.recipientCount() > 0
                    ? (b.successCount() * 100.0) / b.recipientCount()
                    : 100.0)
                .average()
                .orElse(0);
            Duration avgDuration = Duration.ofMillis((long) broadcastHistory.stream()
                .mapToLong(b -> b.duration().toMillis())
                .average()
                .orElse(0));

            List<BroadcastMetric> recent = broadcastHistory.stream()
                .skip(Math.max(0, broadcastHistory.size() - 10))
                .toList();

            return new BroadcastReport(
                totalBroadcasts,
                totalRecipients,
                avgSuccessRate,
                avgDuration,
                recent
            );
        }
    }

    /**
     * Get player activity for a specific player.
     */
    @Nullable
    public PlayerActivityStats getPlayerActivity(UUID playerUuid) {
        return playerStats.get(playerUuid);
    }

    /**
     * Get recent anomaly alerts.
     */
    public List<AnomalyAlert> getAnomalyAlerts(int limit) {
        synchronized (anomalyAlerts) {
            int start = Math.max(0, anomalyAlerts.size() - limit);
            return new ArrayList<>(anomalyAlerts.subList(start, anomalyAlerts.size()));
        }
    }

    // ============================================================================
    // INTERNAL METHODS
    // ============================================================================

    private void rotateBuckets() {
        int newHour = getCurrentHour();
        int newDay = getCurrentDay();
        int newMinute = getCurrentMinute();

        // Reset minute bucket if changed
        if (newMinute != currentMinute) {
            int nextMinute = (currentMinute + 1) % MINUTE_BUCKETS;
            messagesByMinute[nextMinute].reset();
            currentMinute = newMinute;
        }

        // Reset hour bucket if changed
        if (newHour != currentHour) {
            int nextHour = (currentHour + 1) % HOURLY_BUCKETS;
            messagesByHour[nextHour].reset();
            currentHour = newHour;
        }

        // Reset day bucket if changed
        if (newDay != currentDay) {
            int nextDay = (currentDay + 1) % DAILY_BUCKETS;
            messagesByDay[nextDay].reset();
            currentDay = newDay;
        }
    }

    private void runAnomalyDetection() {
        Instant now = Instant.now();

        for (Map.Entry<UUID, SenderProfile> entry : senderProfiles.entrySet()) {
            SenderProfile profile = entry.getValue();
            AnomalyType anomaly = profile.detectAnomaly();

            if (anomaly != null) {
                AnomalyAlert alert = new AnomalyAlert(
                    entry.getKey(),
                    anomaly,
                    profile.getRecentSendRate(),
                    profile.getAverageRate(),
                    now
                );

                synchronized (anomalyAlerts) {
                    anomalyAlerts.add(alert);
                    while (anomalyAlerts.size() > MAX_ALERTS) {
                        anomalyAlerts.remove(0);
                    }
                }

                LOGGER.warn("[MailboxAnalytics] Anomaly detected: {} for player {}",
                    anomaly, entry.getKey());
            }
        }
    }

    private void cleanupOldData() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));

        // Cleanup inactive player stats
        playerStats.entrySet().removeIf(e ->
            e.getValue().lastActivity.isBefore(cutoff) &&
            e.getValue().messagesSent.sum() < 10);

        // Cleanup old sender profiles
        senderProfiles.entrySet().removeIf(e ->
            e.getValue().lastSend.isBefore(cutoff));

        LOGGER.debug("[MailboxAnalytics] Cleanup complete, {} active players",
            playerStats.size());
    }

    private double getMessagesPerHour() {
        long sum = 0;
        for (LongAdder adder : messagesByHour) {
            sum += adder.sum();
        }
        return sum / (double) HOURLY_BUCKETS;
    }

    private double getMessagesPerDay() {
        long sum = 0;
        for (LongAdder adder : messagesByDay) {
            sum += adder.sum();
        }
        return sum / (double) DAILY_BUCKETS;
    }

    private int getPeakHour() {
        int peak = 0;
        long peakValue = 0;
        for (int i = 0; i < HOURLY_BUCKETS; i++) {
            long value = messagesByHour[i].sum();
            if (value > peakValue) {
                peakValue = value;
                peak = i;
            }
        }
        return peak;
    }

    private int getAnomalyAlertCount() {
        synchronized (anomalyAlerts) {
            return anomalyAlerts.size();
        }
    }

    private static int getCurrentHour() {
        return LocalDateTime.now(ZoneOffset.UTC).getHour();
    }

    private static int getCurrentDay() {
        return (int) (LocalDate.now(ZoneOffset.UTC).toEpochDay() % DAILY_BUCKETS);
    }

    private static int getCurrentMinute() {
        return LocalDateTime.now(ZoneOffset.UTC).getMinute();
    }

    // ============================================================================
    // DATA TYPES
    // ============================================================================

    /**
     * Player activity statistics.
     */
    public static final class PlayerActivityStats {
        private final UUID playerUuid;
        final LongAdder messagesSent = new LongAdder();
        final LongAdder messagesReceived = new LongAdder();
        final LongAdder messagesRead = new LongAdder();
        final LongAdder messagesDeleted = new LongAdder();
        final LongAdder deletedUnread = new LongAdder();
        final LongAdder attachmentsClaimed = new LongAdder();
        final LongAdder attachmentValue = new LongAdder();
        final LongAdder totalReadTimeMs = new LongAdder();
        volatile Instant lastActivity = Instant.now();

        PlayerActivityStats(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }

        void recordSent() {
            messagesSent.increment();
            lastActivity = Instant.now();
        }

        void recordReceived() {
            messagesReceived.increment();
            lastActivity = Instant.now();
        }

        void recordRead(Duration timeToRead) {
            messagesRead.increment();
            totalReadTimeMs.add(timeToRead.toMillis());
            lastActivity = Instant.now();
        }

        void recordDeleted(boolean wasRead) {
            messagesDeleted.increment();
            if (!wasRead) {
                deletedUnread.increment();
            }
            lastActivity = Instant.now();
        }

        void recordAttachmentClaimed(int value) {
            attachmentsClaimed.increment();
            attachmentValue.add(value);
            lastActivity = Instant.now();
        }

        public double getReadRate() {
            long received = messagesReceived.sum();
            return received > 0 ? (messagesRead.sum() * 100.0) / received : 0;
        }

        public Duration getAvgReadTime() {
            long reads = messagesRead.sum();
            return reads > 0
                ? Duration.ofMillis(totalReadTimeMs.sum() / reads)
                : Duration.ZERO;
        }

        public UUID getPlayerUuid() { return playerUuid; }
        public long getMessagesSent() { return messagesSent.sum(); }
        public long getMessagesReceived() { return messagesReceived.sum(); }
        public long getMessagesRead() { return messagesRead.sum(); }
        public long getAttachmentsClaimed() { return attachmentsClaimed.sum(); }
    }

    /**
     * Sender profile for anomaly detection.
     */
    private static final class SenderProfile {
        private final List<Instant> sendTimes = new ArrayList<>();
        private volatile Instant lastSend = Instant.now();
        private volatile double rollingAverage = 0;
        private static final int WINDOW_SIZE = 100;
        private static final double ANOMALY_THRESHOLD = 3.0; // 3x normal rate

        SenderProfile() {}

        synchronized void recordSend() {
            Instant now = Instant.now();
            sendTimes.add(now);
            lastSend = now;

            // Keep only recent sends
            Instant cutoff = now.minus(Duration.ofHours(1));
            sendTimes.removeIf(t -> t.isBefore(cutoff));
            if (sendTimes.size() > WINDOW_SIZE) {
                sendTimes.subList(0, sendTimes.size() - WINDOW_SIZE).clear();
            }

            // Update rolling average
            if (sendTimes.size() > 5) {
                rollingAverage = (rollingAverage * 0.9) + (getRecentSendRate() * 0.1);
            }
        }

        synchronized double getRecentSendRate() {
            if (sendTimes.size() < 2) return 0;

            Instant now = Instant.now();
            long recentCount = sendTimes.stream()
                .filter(t -> t.isAfter(now.minus(Duration.ofMinutes(5))))
                .count();

            return recentCount / 5.0; // Messages per minute
        }

        synchronized double getAverageRate() {
            return rollingAverage;
        }

        @Nullable
        synchronized AnomalyType detectAnomaly() {
            if (sendTimes.size() < 10) return null;

            double recent = getRecentSendRate();
            double avg = rollingAverage;

            if (avg > 0 && recent > avg * ANOMALY_THRESHOLD) {
                return AnomalyType.BURST_SENDING;
            }

            // Check for very rapid fire (more than 1/second for 10 seconds)
            Instant now = Instant.now();
            long rapidCount = sendTimes.stream()
                .filter(t -> t.isAfter(now.minus(Duration.ofSeconds(10))))
                .count();

            if (rapidCount > 10) {
                return AnomalyType.RAPID_FIRE;
            }

            return null;
        }
    }

    public enum AnomalyType {
        BURST_SENDING("Unusual burst of messages"),
        RAPID_FIRE("Very rapid message sending"),
        UNUSUAL_PATTERN("Unusual sending pattern");

        private final String description;

        AnomalyType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Records for data transfer
    public record DashboardMetrics(
        long totalMessages,
        long totalRead,
        long totalDeleted,
        double readRate,
        long attachmentsSent,
        long attachmentsClaimed,
        double claimRate,
        int activePlayers,
        double avgMessagesPerHour,
        double avgMessagesPerDay,
        int peakHour,
        Map<MessageType, Long> messagesByType,
        int anomalyAlerts
    ) {}

    public record HourlyVolume(int hour, long count) {}
    public record DailyVolume(LocalDate date, long count) {}
    public record PlayerRanking(UUID playerUuid, long count) {}
    public record EngagementRanking(UUID playerUuid, double readRate, long totalReceived) {}

    public record BroadcastMetric(
        Instant timestamp,
        String subject,
        int recipientCount,
        int successCount,
        Duration duration
    ) {
        public double successRate() {
            return recipientCount > 0 ? (successCount * 100.0) / recipientCount : 100.0;
        }
    }

    public record BroadcastReport(
        int totalBroadcasts,
        long totalRecipients,
        double avgSuccessRate,
        Duration avgDuration,
        List<BroadcastMetric> recentBroadcasts
    ) {}

    public record AnomalyAlert(
        UUID playerUuid,
        AnomalyType type,
        double currentRate,
        double normalRate,
        Instant detectedAt
    ) {}
}
