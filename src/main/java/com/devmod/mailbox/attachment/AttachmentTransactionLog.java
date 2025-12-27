package com.devmod.mailbox.attachment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transaction log for attachment claims.
 * Used for anti-exploit auditing and tracking.
 */
public final class AttachmentTransactionLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentTransactionLog.class);

    public static final AttachmentTransactionLog INSTANCE = new AttachmentTransactionLog();

    /** Maximum entries to keep in memory. */
    private static final int MAX_IN_MEMORY_ENTRIES = 5000;

    /** In-memory transaction log (newest first). */
    private final ConcurrentLinkedDeque<TransactionEntry> entries = new ConcurrentLinkedDeque<>();

    /** Listeners for transaction events. */
    private final List<TransactionListener> listeners = new CopyOnWriteArrayList<>();

    private AttachmentTransactionLog() {}

    // ============================================================================
    // LOGGING
    // ============================================================================

    /**
     * Log an item claim transaction.
     */
    public void logItemClaim(
            UUID messageId,
            UUID playerUuid,
            String playerName,
            String itemId,
            int count,
            @Nullable String nbtData,
            boolean success,
            @Nullable String errorMessage) {

        TransactionEntry entry = new TransactionEntry(
            UUID.randomUUID(),
            TransactionType.ITEM_CLAIM,
            messageId,
            playerUuid,
            playerName,
            itemId,
            count,
            nbtData,
            success,
            errorMessage,
            Instant.now()
        );

        addEntry(entry);

        if (success) {
            LOGGER.info("[TransactionLog] ITEM_CLAIM: player={}, item={}, count={}, messageId={}",
                playerName, itemId, count, messageId);
        } else {
            LOGGER.warn("[TransactionLog] ITEM_CLAIM_FAILED: player={}, item={}, count={}, error={}",
                playerName, itemId, count, errorMessage);
        }
    }

    /**
     * Log a currency claim transaction.
     */
    public void logCurrencyClaim(
            UUID messageId,
            UUID playerUuid,
            String playerName,
            String currencyType,
            int amount,
            boolean success,
            @Nullable String errorMessage) {

        TransactionEntry entry = new TransactionEntry(
            UUID.randomUUID(),
            TransactionType.CURRENCY_CLAIM,
            messageId,
            playerUuid,
            playerName,
            currencyType,
            amount,
            null,
            success,
            errorMessage,
            Instant.now()
        );

        addEntry(entry);

        if (success) {
            LOGGER.info("[TransactionLog] CURRENCY_CLAIM: player={}, currency={}, amount={}, messageId={}",
                playerName, currencyType, amount, messageId);
        } else {
            LOGGER.warn("[TransactionLog] CURRENCY_CLAIM_FAILED: player={}, currency={}, amount={}, error={}",
                playerName, currencyType, amount, errorMessage);
        }
    }

    /**
     * Log a suspicious activity.
     */
    public void logSuspiciousActivity(
            UUID playerUuid,
            String playerName,
            String description,
            @Nullable String details) {

        TransactionEntry entry = new TransactionEntry(
            UUID.randomUUID(),
            TransactionType.SUSPICIOUS,
            null,
            playerUuid,
            playerName,
            description,
            0,
            details,
            false,
            "Suspicious activity detected",
            Instant.now()
        );

        addEntry(entry);

        LOGGER.warn("[TransactionLog] SUSPICIOUS: player={}, description={}, details={}",
            playerName, description, details);

        // Notify listeners
        for (TransactionListener listener : listeners) {
            try {
                listener.onSuspiciousActivity(entry);
            } catch (Exception e) {
                LOGGER.error("[TransactionLog] Listener error: {}", e.getMessage());
            }
        }
    }

    private void addEntry(TransactionEntry entry) {
        entries.addFirst(entry);

        // Trim old entries
        while (entries.size() > MAX_IN_MEMORY_ENTRIES) {
            entries.removeLast();
        }

        // Notify listeners
        for (TransactionListener listener : listeners) {
            try {
                listener.onTransaction(entry);
            } catch (Exception e) {
                LOGGER.error("[TransactionLog] Listener error: {}", e.getMessage());
            }
        }
    }

    // ============================================================================
    // QUERY
    // ============================================================================

    /**
     * Get recent transactions.
     */
    public List<TransactionEntry> getRecentTransactions(int limit) {
        return entries.stream()
            .limit(limit)
            .toList();
    }

    /**
     * Get transactions for a player.
     */
    public List<TransactionEntry> getTransactionsForPlayer(UUID playerUuid, int limit) {
        return entries.stream()
            .filter(e -> playerUuid.equals(e.playerUuid()))
            .limit(limit)
            .toList();
    }

    /**
     * Get transactions for a message.
     */
    public List<TransactionEntry> getTransactionsForMessage(UUID messageId) {
        return entries.stream()
            .filter(e -> messageId.equals(e.messageId()))
            .toList();
    }

    /**
     * Get failed transactions.
     */
    public List<TransactionEntry> getFailedTransactions(int limit) {
        return entries.stream()
            .filter(e -> !e.success())
            .limit(limit)
            .toList();
    }

    /**
     * Get suspicious activity entries.
     */
    public List<TransactionEntry> getSuspiciousActivity(int limit) {
        return entries.stream()
            .filter(e -> e.type() == TransactionType.SUSPICIOUS)
            .limit(limit)
            .toList();
    }

    /**
     * Get total transaction count.
     */
    public int getTransactionCount() {
        return entries.size();
    }

    /**
     * Clear all entries.
     */
    public void clear() {
        entries.clear();
    }

    // ============================================================================
    // LISTENERS
    // ============================================================================

    /**
     * Add a transaction listener.
     */
    public void addListener(TransactionListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a transaction listener.
     */
    public void removeListener(TransactionListener listener) {
        listeners.remove(listener);
    }

    // ============================================================================
    // TYPES
    // ============================================================================

    public enum TransactionType {
        ITEM_CLAIM,
        CURRENCY_CLAIM,
        SUSPICIOUS
    }

    public record TransactionEntry(
        UUID id,
        TransactionType type,
        @Nullable UUID messageId,
        UUID playerUuid,
        String playerName,
        String resourceId,
        int amount,
        @Nullable String additionalData,
        boolean success,
        @Nullable String errorMessage,
        Instant timestamp
    ) {}

    public interface TransactionListener {
        void onTransaction(TransactionEntry entry);

        default void onSuspiciousActivity(TransactionEntry entry) {}
    }
}
