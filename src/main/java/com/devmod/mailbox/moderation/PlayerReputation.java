package com.devmod.mailbox.moderation;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P1: Player reputation system for gating high-risk actions.
 *
 * Tracks player trust levels based on:
 * - Account age (time since first interaction)
 * - Successful transactions (messages delivered, attachments claimed)
 * - Violations (spam attempts, blocked messages, abuse reports)
 *
 * Gates high-risk actions:
 * - Sending high-value attachments
 * - Mass messaging (many unique recipients)
 * - Creating support tickets
 * - Sending to non-friends (strangers)
 */
public final class PlayerReputation {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerReputation.class);

    public static final PlayerReputation INSTANCE = new PlayerReputation();

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /** Whether reputation checks are enabled. */
    private volatile boolean enabled = true;

    /** Minimum trust score to send to strangers. */
    private volatile int minTrustForStrangers = 10;

    /** Minimum trust score to send high-value attachments. */
    private volatile int minTrustForHighValueAttachments = 25;

    /** Minimum trust score to send mass messages (>3 recipients). */
    private volatile int minTrustForMassMessages = 50;

    /** Minimum trust score to create support tickets. */
    private volatile int minTrustForTickets = 5;

    /** Maximum risk score before actions are blocked. */
    private volatile int maxRiskAllowed = 100;

    /** Trust points awarded per successful message. */
    private volatile int trustPerMessage = 1;

    /** Trust points awarded per successful attachment claim. */
    private volatile int trustPerAttachment = 2;

    /** Trust points awarded per day of account age (up to cap). */
    private volatile int trustPerAccountDay = 1;

    /** Maximum trust from account age alone. */
    private volatile int maxAccountAgeTrust = 30;

    /** Risk points per spam attempt blocked. */
    private volatile int riskPerSpamBlocked = 10;

    /** Risk points per message blocked by filter. */
    private volatile int riskPerFilterBlock = 5;

    /** Risk points per abuse report received. */
    private volatile int riskPerAbuseReport = 25;

    /** Risk decay per hour (risk reduces over time). */
    private volatile int riskDecayPerHour = 2;

    /** Currency value threshold for "high-value" attachments. */
    private volatile long highValueCurrencyThreshold = 10000;

    // ========================================================================
    // STATE
    // ========================================================================

    private final Map<UUID, ReputationData> reputationCache = new ConcurrentHashMap<>();

    private final AtomicLong checksPerformed = new AtomicLong(0);
    private final AtomicLong actionsBlocked = new AtomicLong(0);

    private PlayerReputation() {}

    // ========================================================================
    // REPUTATION DATA
    // ========================================================================

    /**
     * Reputation data for a single player.
     */
    public static class ReputationData {
        private final UUID playerId;
        private volatile Instant firstSeen;
        private final AtomicInteger successfulMessages = new AtomicInteger(0);
        private final AtomicInteger successfulAttachments = new AtomicInteger(0);
        private final AtomicInteger spamBlocked = new AtomicInteger(0);
        private final AtomicInteger filterBlocked = new AtomicInteger(0);
        private final AtomicInteger abuseReports = new AtomicInteger(0);
        private volatile Instant lastRiskEvent = Instant.EPOCH;
        private volatile int accumulatedRisk = 0;

        public ReputationData(UUID playerId) {
            this.playerId = playerId;
            this.firstSeen = Instant.now();
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public Instant getFirstSeen() {
            return firstSeen;
        }

        public void setFirstSeen(Instant firstSeen) {
            this.firstSeen = firstSeen;
        }

        public int getSuccessfulMessages() {
            return successfulMessages.get();
        }

        public int getSuccessfulAttachments() {
            return successfulAttachments.get();
        }

        public int getSpamBlocked() {
            return spamBlocked.get();
        }

        public int getFilterBlocked() {
            return filterBlocked.get();
        }

        public int getAbuseReports() {
            return abuseReports.get();
        }

        public void recordSuccessfulMessage() {
            successfulMessages.incrementAndGet();
        }

        public void recordSuccessfulAttachment() {
            successfulAttachments.incrementAndGet();
        }

        public void recordSpamBlocked() {
            spamBlocked.incrementAndGet();
            lastRiskEvent = Instant.now();
        }

        public void recordFilterBlocked() {
            filterBlocked.incrementAndGet();
            lastRiskEvent = Instant.now();
        }

        public void recordAbuseReport() {
            abuseReports.incrementAndGet();
            lastRiskEvent = Instant.now();
        }

        public Instant getLastRiskEvent() {
            return lastRiskEvent;
        }

        public int getAccumulatedRisk() {
            return accumulatedRisk;
        }

        public void addRisk(int amount) {
            accumulatedRisk = Math.max(0, accumulatedRisk + amount);
        }

        public void decayRisk(int amount) {
            accumulatedRisk = Math.max(0, accumulatedRisk - amount);
        }
    }

    // ========================================================================
    // SCORE CALCULATION
    // ========================================================================

    /**
     * Get or create reputation data for a player.
     */
    public ReputationData getReputation(UUID playerId) {
        return reputationCache.computeIfAbsent(playerId, ReputationData::new);
    }

    /**
     * Calculate the current trust score for a player.
     *
     * @param playerId The player UUID
     * @return Trust score (higher = more trusted)
     */
    public int calculateTrustScore(UUID playerId) {
        ReputationData data = getReputation(playerId);

        int score = 0;

        // Account age bonus (1 point per day, capped)
        long daysSinceFirstSeen = Duration.between(data.getFirstSeen(), Instant.now()).toDays();
        int ageBonus = (int) Math.min(daysSinceFirstSeen * trustPerAccountDay, maxAccountAgeTrust);
        score += ageBonus;

        // Successful messages
        score += data.getSuccessfulMessages() * trustPerMessage;

        // Successful attachments
        score += data.getSuccessfulAttachments() * trustPerAttachment;

        // Cap at reasonable max
        return Math.min(score, 1000);
    }

    /**
     * Calculate the current risk score for a player.
     *
     * @param playerId The player UUID
     * @return Risk score (higher = more risky)
     */
    public int calculateRiskScore(UUID playerId) {
        ReputationData data = getReputation(playerId);

        int score = 0;

        // Spam blocked
        score += data.getSpamBlocked() * riskPerSpamBlocked;

        // Filter blocked
        score += data.getFilterBlocked() * riskPerFilterBlock;

        // Abuse reports
        score += data.getAbuseReports() * riskPerAbuseReport;

        // Add accumulated risk
        score += data.getAccumulatedRisk();

        // Apply time decay
        long hoursSinceLastRisk = Duration.between(data.getLastRiskEvent(), Instant.now()).toHours();
        int decay = (int) (hoursSinceLastRisk * riskDecayPerHour);
        score = Math.max(0, score - decay);

        return score;
    }

    /**
     * Get a summary of player reputation.
     */
    public ReputationSummary getSummary(UUID playerId) {
        int trust = calculateTrustScore(playerId);
        int risk = calculateRiskScore(playerId);
        TrustLevel level = determineTrustLevel(trust, risk);
        return new ReputationSummary(playerId, trust, risk, level);
    }

    private TrustLevel determineTrustLevel(int trust, int risk) {
        if (risk >= maxRiskAllowed) {
            return TrustLevel.BLOCKED;
        }
        if (trust >= minTrustForMassMessages && risk < 30) {
            return TrustLevel.TRUSTED;
        }
        if (trust >= minTrustForStrangers && risk < 50) {
            return TrustLevel.NORMAL;
        }
        if (trust > 0) {
            return TrustLevel.NEW;
        }
        return TrustLevel.UNKNOWN;
    }

    // ========================================================================
    // ACTION GATES
    // ========================================================================

    /**
     * Check if player can send a message to a stranger (non-friend).
     */
    public GateResult canSendToStranger(UUID senderId) {
        checksPerformed.incrementAndGet();

        if (!enabled) {
            return GateResult.allow();
        }

        int trust = calculateTrustScore(senderId);
        int risk = calculateRiskScore(senderId);

        if (risk >= maxRiskAllowed) {
            actionsBlocked.incrementAndGet();
            LOGGER.info("[PlayerReputation] Blocked stranger message: player={}, risk={}", senderId, risk);
            return GateResult.blocked("Your account is temporarily restricted due to policy violations");
        }

        if (trust < minTrustForStrangers) {
            actionsBlocked.incrementAndGet();
            LOGGER.debug("[PlayerReputation] Blocked stranger message: player={}, trust={} (need {})",
                senderId, trust, minTrustForStrangers);
            return GateResult.blocked("Your account needs more activity before messaging unfamiliar players");
        }

        return GateResult.allow();
    }

    /**
     * Check if player can send a high-value attachment.
     *
     * @param senderId The sender UUID
     * @param attachmentValue Total value of attachments (currency amount or estimated item value)
     */
    public GateResult canSendHighValueAttachment(UUID senderId, long attachmentValue) {
        checksPerformed.incrementAndGet();

        if (!enabled) {
            return GateResult.allow();
        }

        // Not a high-value attachment
        if (attachmentValue < highValueCurrencyThreshold) {
            return GateResult.allow();
        }

        int trust = calculateTrustScore(senderId);
        int risk = calculateRiskScore(senderId);

        if (risk >= maxRiskAllowed) {
            actionsBlocked.incrementAndGet();
            LOGGER.info("[PlayerReputation] Blocked high-value attachment: player={}, risk={}, value={}",
                senderId, risk, attachmentValue);
            return GateResult.blocked("Your account is temporarily restricted");
        }

        if (trust < minTrustForHighValueAttachments) {
            actionsBlocked.incrementAndGet();
            LOGGER.debug("[PlayerReputation] Blocked high-value attachment: player={}, trust={} (need {}), value={}",
                senderId, trust, minTrustForHighValueAttachments, attachmentValue);
            return GateResult.blocked("Your account needs more activity to send high-value items");
        }

        return GateResult.allow();
    }

    /**
     * Check if player can send mass messages (many recipients).
     *
     * @param senderId The sender UUID
     * @param recipientCount Number of recipients
     */
    public GateResult canSendMassMessage(UUID senderId, int recipientCount) {
        checksPerformed.incrementAndGet();

        if (!enabled) {
            return GateResult.allow();
        }

        // Not a mass message
        if (recipientCount <= 3) {
            return GateResult.allow();
        }

        int trust = calculateTrustScore(senderId);
        int risk = calculateRiskScore(senderId);

        if (risk >= maxRiskAllowed) {
            actionsBlocked.incrementAndGet();
            return GateResult.blocked("Your account is temporarily restricted");
        }

        if (trust < minTrustForMassMessages) {
            actionsBlocked.incrementAndGet();
            LOGGER.info("[PlayerReputation] Blocked mass message: player={}, trust={} (need {}), recipients={}",
                senderId, trust, minTrustForMassMessages, recipientCount);
            return GateResult.blocked("Your account cannot send to many recipients at once");
        }

        return GateResult.allow();
    }

    /**
     * Check if player can create a support ticket.
     */
    public GateResult canCreateTicket(UUID playerId) {
        checksPerformed.incrementAndGet();

        if (!enabled) {
            return GateResult.allow();
        }

        int trust = calculateTrustScore(playerId);
        int risk = calculateRiskScore(playerId);

        if (risk >= maxRiskAllowed) {
            actionsBlocked.incrementAndGet();
            return GateResult.blocked("Your account is temporarily restricted from creating tickets");
        }

        if (trust < minTrustForTickets) {
            actionsBlocked.incrementAndGet();
            LOGGER.debug("[PlayerReputation] Blocked ticket creation: player={}, trust={} (need {})",
                playerId, trust, minTrustForTickets);
            return GateResult.blocked("Please participate in the game before creating support tickets");
        }

        return GateResult.allow();
    }

    // ========================================================================
    // EVENT RECORDING
    // ========================================================================

    /**
     * Record a successful message delivery.
     */
    public void recordSuccessfulMessage(UUID senderId) {
        if (!enabled) return;
        getReputation(senderId).recordSuccessfulMessage();
    }

    /**
     * Record a successful attachment claim.
     */
    public void recordSuccessfulAttachment(UUID playerId) {
        if (!enabled) return;
        getReputation(playerId).recordSuccessfulAttachment();
    }

    /**
     * Record a spam block event.
     */
    public void recordSpamBlocked(UUID senderId) {
        if (!enabled) return;
        ReputationData data = getReputation(senderId);
        data.recordSpamBlocked();
        data.addRisk(riskPerSpamBlocked);
        LOGGER.debug("[PlayerReputation] Recorded spam block for {}", senderId);
    }

    /**
     * Record a content filter block event.
     */
    public void recordFilterBlocked(UUID senderId) {
        if (!enabled) return;
        ReputationData data = getReputation(senderId);
        data.recordFilterBlocked();
        data.addRisk(riskPerFilterBlock);
    }

    /**
     * Record an abuse report against a player.
     */
    public void recordAbuseReport(UUID reportedPlayer) {
        if (!enabled) return;
        ReputationData data = getReputation(reportedPlayer);
        data.recordAbuseReport();
        data.addRisk(riskPerAbuseReport);
        LOGGER.info("[PlayerReputation] Abuse report recorded for {}", reportedPlayer);
    }

    /**
     * Set player's first-seen time (for migrating existing players).
     */
    public void setFirstSeen(UUID playerId, Instant firstSeen) {
        getReputation(playerId).setFirstSeen(firstSeen);
    }

    // ========================================================================
    // CONFIGURATION API
    // ========================================================================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("[PlayerReputation] Reputation system {}", enabled ? "enabled" : "disabled");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setMinTrustForStrangers(int value) {
        this.minTrustForStrangers = Math.max(0, value);
    }

    public void setMinTrustForHighValueAttachments(int value) {
        this.minTrustForHighValueAttachments = Math.max(0, value);
    }

    public void setMinTrustForMassMessages(int value) {
        this.minTrustForMassMessages = Math.max(0, value);
    }

    public void setMinTrustForTickets(int value) {
        this.minTrustForTickets = Math.max(0, value);
    }

    public void setMaxRiskAllowed(int value) {
        this.maxRiskAllowed = Math.max(1, value);
    }

    public void setHighValueCurrencyThreshold(long value) {
        this.highValueCurrencyThreshold = Math.max(0, value);
    }

    // ========================================================================
    // METRICS
    // ========================================================================

    public long getChecksPerformed() {
        return checksPerformed.get();
    }

    public long getActionsBlocked() {
        return actionsBlocked.get();
    }

    public int getTrackedPlayerCount() {
        return reputationCache.size();
    }

    public ReputationMetrics getMetrics() {
        return new ReputationMetrics(
            checksPerformed.get(),
            actionsBlocked.get(),
            reputationCache.size()
        );
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    /**
     * Clean up stale reputation data (players not seen in 30 days).
     */
    public void cleanup() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        int removed = 0;
        var iter = reputationCache.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            ReputationData data = entry.getValue();
            // Keep if they have significant history
            if (data.getSuccessfulMessages() > 10 || data.getAbuseReports() > 0) {
                continue;
            }
            // Remove if no recent activity
            if (data.getFirstSeen().isBefore(cutoff) && data.getLastRiskEvent().isBefore(cutoff)) {
                iter.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.debug("[PlayerReputation] Cleanup: removed {} stale entries", removed);
        }
    }

    /**
     * Reset all reputation data (for testing).
     */
    public void reset() {
        reputationCache.clear();
        checksPerformed.set(0);
        actionsBlocked.set(0);
    }

    // ========================================================================
    // RESULT TYPES
    // ========================================================================

    /**
     * Trust level classification.
     */
    public enum TrustLevel {
        /** Unknown player, no history. */
        UNKNOWN,
        /** New player with minimal history. */
        NEW,
        /** Normal player with good standing. */
        NORMAL,
        /** Trusted player with extensive positive history. */
        TRUSTED,
        /** Blocked due to excessive risk. */
        BLOCKED
    }

    /**
     * Result of a reputation gate check.
     */
    public record GateResult(
        boolean allowed,
        @Nullable String reason
    ) {
        public static GateResult allow() {
            return new GateResult(true, null);
        }

        public static GateResult blocked(String reason) {
            return new GateResult(false, reason);
        }
    }

    /**
     * Summary of player reputation.
     */
    public record ReputationSummary(
        UUID playerId,
        int trustScore,
        int riskScore,
        TrustLevel level
    ) {}

    /**
     * Metrics snapshot.
     */
    public record ReputationMetrics(
        long checksPerformed,
        long actionsBlocked,
        int trackedPlayers
    ) {}
}
