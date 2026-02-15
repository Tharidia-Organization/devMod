package com.devmod.mailbox;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * Rate limiting logic for the mailbox system.
 * Tracks per-player send rates (per minute, per day, per recipient per day) and cooldowns.
 */
public class MailboxRateLimiter {

    /* Rate limiting: tracks last send time per player */
    private final Map<UUID, Long> lastSendTime = new ConcurrentHashMap<>();

    /* Rate limiting: tracks sends per minute per player */
    private final Map<UUID, Integer> sendsThisMinute = new ConcurrentHashMap<>();

    /* Rate limiting: tracks sends per day per player */
    private final Map<UUID, Integer> sendsToday = new ConcurrentHashMap<>();

    /* Rate limiting: tracks sends per day per sender->recipient */
    private final Map<SenderRecipientKey, Integer> sendsPerRecipientToday = new ConcurrentHashMap<>();

    private volatile long lastDailyResetEpochDay = currentEpochDay();

    /**
     * Check if a player is rate-limited for sending to a recipient.
     *
     * @return error message if rate-limited, null if allowed
     */
    @Nullable
    public String checkRateLimit(UUID playerUuid, UUID recipientUuid) {
        MailboxConfig config = MailboxConfig.INSTANCE;
        refreshDailyBuckets();

        // Check cooldown
        Long lastSend = lastSendTime.get(playerUuid);
        if (lastSend != null) {
            long elapsed = System.currentTimeMillis() - lastSend;
            if (elapsed < config.getSendCooldownSeconds() * 1000L) {
                return "Please wait before sending another message.";
            }
        }

        // Check messages per minute
        Integer sends = sendsThisMinute.get(playerUuid);
        if (sends != null && sends >= config.getMaxMessagesPerMinute()) {
            return "Too many messages sent. Please wait.";
        }

        int maxPerDay = config.getMaxMessagesPerDay();
        if (maxPerDay > 0) {
            Integer daily = sendsToday.get(playerUuid);
            if (daily != null && daily >= maxPerDay) {
                return "Daily message limit reached.";
            }
        }

        int maxPerRecipient = config.getMaxMessagesPerRecipientPerDay();
        if (maxPerRecipient > 0) {
            SenderRecipientKey key = new SenderRecipientKey(playerUuid, recipientUuid);
            Integer perRecipient = sendsPerRecipientToday.get(key);
            if (perRecipient != null && perRecipient >= maxPerRecipient) {
                return "Daily recipient limit reached.";
            }
        }

        return null;
    }

    /**
     * Record that a message was sent.
     */
    public void updateRateLimit(UUID playerUuid, UUID recipientUuid) {
        lastSendTime.put(playerUuid, System.currentTimeMillis());
        sendsThisMinute.merge(playerUuid, 1, (a, b) -> a + b);

        refreshDailyBuckets();
        MailboxConfig config = MailboxConfig.INSTANCE;
        if (config.getMaxMessagesPerDay() > 0) {
            sendsToday.merge(playerUuid, 1, (a, b) -> a + b);
        }
        if (config.getMaxMessagesPerRecipientPerDay() > 0) {
            SenderRecipientKey key = new SenderRecipientKey(playerUuid, recipientUuid);
            sendsPerRecipientToday.merge(key, 1, (a, b) -> a + b);
        }
    }

    /**
     * Reset per-minute rate limit counters. Called by a scheduled task.
     */
    public void resetMinuteBuckets() {
        sendsThisMinute.clear();
    }

    private void refreshDailyBuckets() {
        long today = currentEpochDay();
        if (today != lastDailyResetEpochDay) {
            lastDailyResetEpochDay = today;
            sendsToday.clear();
            sendsPerRecipientToday.clear();
        }
    }

    private static long currentEpochDay() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    private record SenderRecipientKey(UUID sender, UUID recipient) {}
}
