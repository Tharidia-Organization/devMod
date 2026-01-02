package com.devmod.mailbox.moderation;

import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P1: Spam detection scoring for mailbox messages.
 *
 * Uses a rule-based scoring system with configurable thresholds.
 * Each message is scored based on multiple signals:
 * - Message frequency (sender rate limiting)
 * - Content repetition (duplicate detection)
 * - Suspicious patterns (character abuse, excessive caps, etc.)
 * - Recipient patterns (mass messaging detection)
 *
 * Scores are additive: higher score = more likely spam.
 * Default threshold: 100 = spam, 50-100 = suspicious (flag for review).
 */
public final class SpamDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpamDetector.class);

    public static final SpamDetector INSTANCE = new SpamDetector();

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /** Score threshold for definite spam (block). */
    private volatile int spamThreshold = 100;

    /** Score threshold for suspicious content (flag). */
    private volatile int suspiciousThreshold = 50;

    /** Whether spam detection is enabled. */
    private volatile boolean enabled = true;

    /** Maximum messages per sender in the tracking window. */
    private volatile int maxMessagesPerWindow = 10;

    /** Time window for message tracking (milliseconds). */
    private volatile long trackingWindowMs = 60_000L; // 1 minute

    // ========================================================================
    // SCORING WEIGHTS
    // ========================================================================

    private volatile int scoreHighFrequency = 30;        // > 5 messages/minute
    private volatile int scoreVeryHighFrequency = 60;    // > 10 messages/minute
    private volatile int scoreDuplicateContent = 50;     // Same message repeated
    private volatile int scoreExcessiveCaps = 15;        // >70% uppercase
    private volatile int scoreExcessiveSymbols = 20;     // >30% symbols
    private volatile int scoreExcessiveLength = 10;      // Very long message
    private volatile int scoreMassRecipient = 40;        // Same sender, many recipients
    private volatile int scoreNewAccount = 15;           // First-time sender
    private volatile int scoreEmptySubject = 10;         // No subject
    private volatile int scoreShortBody = 5;             // Very short body

    // ========================================================================
    // STATE TRACKING
    // ========================================================================

    /** Per-sender message history for frequency analysis. */
    private final Map<UUID, SenderHistory> senderHistories = new ConcurrentHashMap<>();

    /** Content hashes for duplicate detection. */
    private final Map<Long, ContentRecord> recentContentHashes = new ConcurrentHashMap<>();

    /** Metrics. */
    private final AtomicLong totalMessagesScored = new AtomicLong(0);
    private final AtomicLong messagesBlockedAsSpam = new AtomicLong(0);
    private final AtomicLong messagesFlagged = new AtomicLong(0);

    // ========================================================================
    // PATTERNS
    // ========================================================================

    private static final Pattern CAPS_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[^a-zA-Z0-9\\s]");

    private SpamDetector() {
        // Schedule cleanup
    }

    // ========================================================================
    // SCORING API
    // ========================================================================

    /**
     * Score a message for spam likelihood.
     *
     * @param senderId      UUID of the sender
     * @param recipientId   UUID of the recipient
     * @param subject       Message subject
     * @param body          Message body
     * @return SpamScore with numeric score and breakdown
     */
    public SpamScore score(UUID senderId, UUID recipientId, @Nullable String subject, @Nullable String body) {
        if (!enabled) {
            return SpamScore.clean();
        }

        totalMessagesScored.incrementAndGet();

        SpamScore.Builder scoreBuilder = SpamScore.builder();
        long now = System.currentTimeMillis();

        // Normalize inputs
        String normalizedSubject = subject != null ? subject.trim() : "";
        String normalizedBody = body != null ? body.trim() : "";
        String combinedContent = normalizedSubject + " " + normalizedBody;

        // 1. Frequency scoring
        SenderHistory history = getOrCreateHistory(senderId);
        int recentCount = history.countMessagesInWindow(now, trackingWindowMs);

        if (recentCount >= maxMessagesPerWindow) {
            scoreBuilder.addSignal("very_high_frequency", scoreVeryHighFrequency,
                "Sent " + recentCount + " messages in last minute");
        } else if (recentCount >= maxMessagesPerWindow / 2) {
            scoreBuilder.addSignal("high_frequency", scoreHighFrequency,
                "Sent " + recentCount + " messages in last minute");
        }

        // 2. Duplicate detection
        long contentHash = computeContentHash(combinedContent);
        ContentRecord existingContent = recentContentHashes.get(contentHash);
        if (existingContent != null && existingContent.senderId.equals(senderId)) {
            if (now - existingContent.timestamp < trackingWindowMs) {
                scoreBuilder.addSignal("duplicate_content", scoreDuplicateContent,
                    "Identical message sent recently");
            }
        }

        // 3. Content analysis
        if (!normalizedBody.isEmpty()) {
            // Excessive caps
            double capsRatio = computeCapsRatio(normalizedBody);
            if (capsRatio > 0.7 && normalizedBody.length() > 20) {
                scoreBuilder.addSignal("excessive_caps", scoreExcessiveCaps,
                    String.format("%.0f%% uppercase", capsRatio * 100));
            }

            // Excessive symbols
            double symbolRatio = computeSymbolRatio(normalizedBody);
            if (symbolRatio > 0.3) {
                scoreBuilder.addSignal("excessive_symbols", scoreExcessiveSymbols,
                    String.format("%.0f%% symbols", symbolRatio * 100));
            }

            // Very long message (>5000 chars)
            if (normalizedBody.length() > 5000) {
                scoreBuilder.addSignal("excessive_length", scoreExcessiveLength,
                    normalizedBody.length() + " characters");
            }

            // Very short body (<10 chars, often spam)
            if (normalizedBody.length() < 10 && normalizedBody.length() > 0) {
                scoreBuilder.addSignal("short_body", scoreShortBody,
                    normalizedBody.length() + " characters");
            }
        }

        // 4. Empty subject
        if (normalizedSubject.isEmpty() && !normalizedBody.isEmpty()) {
            scoreBuilder.addSignal("empty_subject", scoreEmptySubject, "No subject provided");
        }

        // 5. New sender bonus (first message ever from this sender)
        if (history.getTotalMessageCount() == 0) {
            scoreBuilder.addSignal("new_sender", scoreNewAccount, "First message from this sender");
        }

        // 6. Mass recipient detection (same content to many recipients)
        int recipientCount = history.countUniqueRecipientsInWindow(now, trackingWindowMs);
        if (recipientCount >= 5) {
            scoreBuilder.addSignal("mass_recipient", scoreMassRecipient,
                "Sent to " + recipientCount + " recipients recently");
        }

        // Record this message
        history.recordMessage(now, recipientId, contentHash);
        recentContentHashes.put(contentHash, new ContentRecord(senderId, now));

        // Build final score
        SpamScore finalScore = scoreBuilder.build();

        // Update metrics and log
        if (finalScore.isSpam(spamThreshold)) {
            messagesBlockedAsSpam.incrementAndGet();
            LOGGER.info("[SpamDetector] BLOCKED spam: sender={}, score={}, signals={}",
                senderId, finalScore.totalScore(), finalScore.getSignalSummary());
        } else if (finalScore.isSuspicious(suspiciousThreshold)) {
            messagesFlagged.incrementAndGet();
            LOGGER.debug("[SpamDetector] FLAGGED suspicious: sender={}, score={}, signals={}",
                senderId, finalScore.totalScore(), finalScore.getSignalSummary());
        }

        return finalScore;
    }

    /**
     * Check if a message should be blocked as spam.
     */
    public boolean isSpam(UUID senderId, UUID recipientId, @Nullable String subject, @Nullable String body) {
        return score(senderId, recipientId, subject, body).isSpam(spamThreshold);
    }

    /**
     * Check if a message should be flagged for review.
     */
    public boolean isSuspicious(UUID senderId, UUID recipientId, @Nullable String subject, @Nullable String body) {
        SpamScore s = score(senderId, recipientId, subject, body);
        return s.isSuspicious(suspiciousThreshold) && !s.isSpam(spamThreshold);
    }

    // ========================================================================
    // CONFIGURATION API
    // ========================================================================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("[SpamDetector] Spam detection {}", enabled ? "enabled" : "disabled");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setSpamThreshold(int threshold) {
        this.spamThreshold = Math.max(1, threshold);
    }

    public int getSpamThreshold() {
        return spamThreshold;
    }

    public void setSuspiciousThreshold(int threshold) {
        this.suspiciousThreshold = Math.max(1, threshold);
    }

    public int getSuspiciousThreshold() {
        return suspiciousThreshold;
    }

    public void setTrackingWindowMs(long windowMs) {
        this.trackingWindowMs = Math.max(1000, windowMs);
    }

    public void setMaxMessagesPerWindow(int max) {
        this.maxMessagesPerWindow = Math.max(1, max);
    }

    // ========================================================================
    // METRICS API
    // ========================================================================

    public long getTotalMessagesScored() {
        return totalMessagesScored.get();
    }

    public long getMessagesBlockedAsSpam() {
        return messagesBlockedAsSpam.get();
    }

    public long getMessagesFlagged() {
        return messagesFlagged.get();
    }

    public int getTrackedSenderCount() {
        return senderHistories.size();
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    /**
     * Clean up old entries from tracking maps.
     * Should be called periodically (e.g., every 5 minutes).
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        long cutoff = now - (trackingWindowMs * 5); // Keep 5x window for analysis

        // Clean up sender histories
        senderHistories.values().forEach(h -> h.pruneOld(cutoff));
        senderHistories.entrySet().removeIf(e -> e.getValue().isEmpty());

        // Clean up content hashes
        recentContentHashes.entrySet().removeIf(e -> e.getValue().timestamp < cutoff);

        LOGGER.debug("[SpamDetector] Cleanup complete: {} senders, {} content hashes tracked",
            senderHistories.size(), recentContentHashes.size());
    }

    /**
     * Reset all tracked state (for testing).
     */
    public void reset() {
        senderHistories.clear();
        recentContentHashes.clear();
        totalMessagesScored.set(0);
        messagesBlockedAsSpam.set(0);
        messagesFlagged.set(0);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private SenderHistory getOrCreateHistory(UUID senderId) {
        return senderHistories.computeIfAbsent(senderId, k -> new SenderHistory());
    }

    private long computeContentHash(@Nullable String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // Simple hash - normalize whitespace and case
        String normalized = content.toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ")
            .trim();
        return normalized.hashCode();
    }

    private double computeCapsRatio(String text) {
        if (text.isEmpty()) return 0;
        long caps = CAPS_PATTERN.matcher(text).results().count();
        long letters = text.chars().filter(Character::isLetter).count();
        return letters > 0 ? (double) caps / letters : 0;
    }

    private double computeSymbolRatio(String text) {
        if (text.isEmpty()) return 0;
        long symbols = SYMBOL_PATTERN.matcher(text).results().count();
        return (double) symbols / text.length();
    }

    // ========================================================================
    // INNER CLASSES
    // ========================================================================

    /**
     * Tracks message history for a single sender.
     */
    private static class SenderHistory {
        private final Deque<MessageRecord> messages = new java.util.ArrayDeque<>();
        private long totalMessages = 0;

        synchronized void recordMessage(long timestamp, UUID recipientId, long contentHash) {
            messages.addLast(new MessageRecord(timestamp, recipientId, contentHash));
            totalMessages++;
        }

        synchronized int countMessagesInWindow(long now, long windowMs) {
            long cutoff = now - windowMs;
            return (int) messages.stream().filter(m -> m.timestamp >= cutoff).count();
        }

        synchronized int countUniqueRecipientsInWindow(long now, long windowMs) {
            long cutoff = now - windowMs;
            return (int) messages.stream()
                .filter(m -> m.timestamp >= cutoff)
                .map(m -> m.recipientId)
                .distinct()
                .count();
        }

        synchronized void pruneOld(long cutoff) {
            while (!messages.isEmpty() && messages.peekFirst().timestamp < cutoff) {
                messages.pollFirst();
            }
        }

        synchronized boolean isEmpty() {
            return messages.isEmpty();
        }

        synchronized long getTotalMessageCount() {
            return totalMessages;
        }

        private record MessageRecord(long timestamp, UUID recipientId, long contentHash) {}
    }

    /**
     * Record of recently seen content.
     */
    private record ContentRecord(UUID senderId, long timestamp) {}

    /**
     * Spam score result with signal breakdown.
     */
    public static final class SpamScore {
        private final int totalScore;
        private final Map<String, SignalDetail> signals;

        private SpamScore(int totalScore, Map<String, SignalDetail> signals) {
            this.totalScore = totalScore;
            this.signals = Map.copyOf(signals);
        }

        public int totalScore() {
            return totalScore;
        }

        public Map<String, SignalDetail> signals() {
            return signals;
        }

        public boolean isSpam(int threshold) {
            return totalScore >= threshold;
        }

        public boolean isSuspicious(int threshold) {
            return totalScore >= threshold;
        }

        public boolean isClean() {
            return totalScore == 0;
        }

        public String getSignalSummary() {
            if (signals.isEmpty()) {
                return "clean";
            }
            return signals.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue().score())
                .reduce((a, b) -> a + ", " + b)
                .orElse("clean");
        }

        public static SpamScore clean() {
            return new SpamScore(0, Map.of());
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private int totalScore = 0;
            private final Map<String, SignalDetail> signals = new HashMap<>();

            public Builder addSignal(String name, int score, String description) {
                totalScore += score;
                signals.put(name, new SignalDetail(score, description));
                return this;
            }

            public SpamScore build() {
                return new SpamScore(totalScore, signals);
            }
        }

        public record SignalDetail(int score, String description) {}
    }
}
