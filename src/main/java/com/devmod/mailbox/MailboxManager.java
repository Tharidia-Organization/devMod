package com.devmod.mailbox;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.fml.loading.FMLPaths;

import com.devmod.mailbox.persistence.DuckDbMailboxRepository;
import com.devmod.mailbox.persistence.MailboxRepository;

/**
 * Central manager for the mailbox system.
 *
 * Handles sending, receiving, and managing messages between players and from the system.
 * Thread-safe singleton with async operations.
 */
public class MailboxManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxManager.class);

    // ============================================================================
    // SINGLETON
    // ============================================================================

    public static final MailboxManager INSTANCE = new MailboxManager();

    private MailboxManager() {}

    // ============================================================================
    // STATE
    // ============================================================================

    private volatile boolean initialized = false;
    @Nullable
    private MailboxRepository repository;
    @Nullable
    private ScheduledExecutorService scheduler;

    /** Rate limiting: tracks last send time per player */
    private final Map<UUID, Long> lastSendTime = new ConcurrentHashMap<>();

    /** Rate limiting: tracks sends per minute per player */
    private final Map<UUID, Integer> sendsThisMinute = new ConcurrentHashMap<>();

    /** Callback for notifying clients of new messages */
    @Nullable
    private NewMessageCallback newMessageCallback;

    // ============================================================================
    // INITIALIZATION
    // ============================================================================

    /**
     * Initialize the mailbox system.
     * Call this when the server starts.
     */
    public CompletableFuture<Void> initialize() {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.info("[Mailbox] Initializing mailbox system...");

        Path dbPath = FMLPaths.GAMEDIR.get().resolve("devmod").resolve("mailbox.duckdb");
        repository = new DuckDbMailboxRepository(dbPath);

        return repository.initialize().thenRun(() -> {
            // Start scheduled tasks
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MailboxScheduler");
                t.setDaemon(true);
                return t;
            });

            // Purge expired messages every hour
            scheduler.scheduleAtFixedRate(
                this::purgeExpiredMessages,
                1, 60, TimeUnit.MINUTES
            );

            // Reset rate limits every minute
            scheduler.scheduleAtFixedRate(
                () -> sendsThisMinute.clear(),
                1, 1, TimeUnit.MINUTES
            );

            initialized = true;
            LOGGER.info("[Mailbox] Mailbox system initialized");
        });
    }

    /**
     * Shutdown the mailbox system.
     * Call this when the server stops.
     */
    public CompletableFuture<Void> shutdown() {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.info("[Mailbox] Shutting down mailbox system...");

        if (scheduler != null) {
            scheduler.shutdown();
        }

        return repo.shutdown().thenRun(() -> {
            initialized = false;
            LOGGER.info("[Mailbox] Mailbox system shutdown complete");
        });
    }

    // ============================================================================
    // MESSAGE OPERATIONS
    // ============================================================================

    /**
     * Send a message from one player to another.
     *
     * @param sender the sending player
     * @param recipientUuid the recipient's UUID
     * @param subject the message subject
     * @param body the message body
     * @param attachmentData optional attachment data (JSON)
     * @return future completing with the result
     */
    public CompletableFuture<SendResult> sendPlayerMessage(
            ServerPlayer sender,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData
    ) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(SendResult.error("Mailbox system not initialized"));
        }

        MailboxConfig config = MailboxConfig.INSTANCE;

        // Check if player-to-player messaging is enabled
        if (!config.isPlayerToPlayerEnabled()) {
            return CompletableFuture.completedFuture(SendResult.error("Player messaging is disabled"));
        }

        UUID senderUuid = sender.getUUID();

        // Check rate limiting
        if (!checkRateLimit(senderUuid)) {
            return CompletableFuture.completedFuture(SendResult.error("Too many messages sent. Please wait."));
        }

        // Validate subject
        if (subject == null || subject.isBlank()) {
            return CompletableFuture.completedFuture(SendResult.error("Subject cannot be empty"));
        }
        if (subject.length() > config.getMaxSubjectLength()) {
            subject = subject.substring(0, config.getMaxSubjectLength());
        }

        // Validate body
        if (body != null && body.length() > config.getMaxBodyLength()) {
            body = body.substring(0, config.getMaxBodyLength());
        }

        // Check recipient inbox capacity
        String finalSubject = subject;
        String finalBody = body;

        return repo.getMessageCount(recipientUuid).thenCompose(count -> {
            if (count >= config.getMaxMessagesPerPlayer()) {
                return CompletableFuture.completedFuture(SendResult.error("Recipient's inbox is full"));
            }

            // Create and save the message
            MailboxMessage message = MailboxMessage.builder()
                .sender(senderUuid, sender.getName().getString())
                .recipient(recipientUuid)
                .subject(finalSubject)
                .body(finalBody)
                .messageType(MessageType.PLAYER)
                .expiresAt(Instant.now().plus(config.getDefaultMessageTtl()))
                .attachment(attachmentData)
                .build();

            return repo.saveMessage(message).thenApply(saved -> {
                updateRateLimit(senderUuid);
                notifyNewMessage(recipientUuid, saved);
                LOGGER.debug("[Mailbox] Player {} sent message to {}", senderUuid, recipientUuid);
                return SendResult.success(saved.id());
            });
        }).exceptionally(e -> {
            LOGGER.error("[Mailbox] Failed to send message", e);
            return SendResult.error("Failed to send message");
        });
    }

    /**
     * Send a system message to a player.
     *
     * @param recipientUuid the recipient's UUID
     * @param subject the message subject
     * @param body the message body
     * @param attachmentData optional attachment data (JSON)
     * @param expiresIn optional expiration duration
     * @return future completing with the message ID
     */
    public CompletableFuture<UUID> sendSystemMessage(
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            @Nullable Duration expiresIn
    ) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Mailbox not initialized"));
        }

        Instant expiresAt = expiresIn != null
            ? Instant.now().plus(expiresIn)
            : Instant.now().plus(MailboxConfig.INSTANCE.getDefaultMessageTtl());

        MailboxMessage message = MailboxMessage.builder()
            .sender(null, "System")
            .recipient(recipientUuid)
            .subject(subject)
            .body(body)
            .messageType(MessageType.SYSTEM)
            .expiresAt(expiresAt)
            .attachment(attachmentData)
            .build();

        return repo.saveMessage(message).thenApply(saved -> {
            notifyNewMessage(recipientUuid, saved);
            LOGGER.debug("[Mailbox] System message sent to {}: {}", recipientUuid, subject);
            return saved.id();
        });
    }

    /**
     * Send an admin message to a player.
     *
     * @param adminName the admin's name
     * @param recipientUuid the recipient's UUID
     * @param subject the message subject
     * @param body the message body
     * @param attachmentData optional attachment data (JSON)
     * @return future completing with the message ID
     */
    public CompletableFuture<UUID> sendAdminMessage(
            String adminName,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData
    ) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Mailbox not initialized"));
        }

        MailboxMessage message = MailboxMessage.builder()
            .sender(null, adminName)
            .recipient(recipientUuid)
            .subject(subject)
            .body(body)
            .messageType(MessageType.ADMIN)
            .expiresAt(Instant.now().plus(MailboxConfig.INSTANCE.getDefaultMessageTtl()))
            .attachment(attachmentData)
            .build();

        return repo.saveMessage(message).thenApply(saved -> {
            notifyNewMessage(recipientUuid, saved);
            LOGGER.debug("[Mailbox] Admin message from {} sent to {}: {}", adminName, recipientUuid, subject);
            return saved.id();
        });
    }

    /**
     * Send a broadcast message to all players.
     *
     * @param adminName the admin's name
     * @param playerUuids list of all player UUIDs
     * @param subject the message subject
     * @param body the message body
     * @param attachmentData optional attachment data (JSON)
     * @return future completing with the number of messages sent
     */
    public CompletableFuture<Integer> sendBroadcast(
            String adminName,
            List<UUID> playerUuids,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData
    ) {
        if (!initialized || repository == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Mailbox not initialized"));
        }

        LOGGER.info("[Mailbox] Sending broadcast to {} players: {}", playerUuids.size(), subject);

        List<CompletableFuture<UUID>> futures = playerUuids.stream()
            .map(uuid -> sendAdminMessage(adminName, uuid, subject, body, attachmentData))
            .toList();

        CompletableFuture<?>[] futureArray = futures.toArray(new CompletableFuture<?>[0]);
        return CompletableFuture.allOf(futureArray)
            .thenApply(v -> {
                LOGGER.info("[Mailbox] Broadcast complete: {} messages sent", futures.size());
                return futures.size();
            });
    }

    /**
     * Get all messages for a player.
     *
     * @param playerUuid the player's UUID
     * @return future completing with the list of messages
     */
    public CompletableFuture<List<MailboxMessage>> getMessages(UUID playerUuid) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repo.getMessagesForPlayer(playerUuid, false);
    }

    /**
     * Get a specific message.
     *
     * @param messageId the message UUID
     * @return future completing with the message if found
     */
    public CompletableFuture<Optional<MailboxMessage>> getMessage(UUID messageId) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return repo.getMessage(messageId);
    }

    /**
     * Get unread message count for a player.
     *
     * @param playerUuid the player's UUID
     * @return future completing with the count
     */
    public CompletableFuture<Integer> getUnreadCount(UUID playerUuid) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(0);
        }
        return repo.getUnreadCount(playerUuid);
    }

    /**
     * Mark a message as read.
     *
     * @param messageId the message UUID
     * @return future completing with success status
     */
    public CompletableFuture<Boolean> markAsRead(UUID messageId) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repo.markAsRead(messageId, Instant.now());
    }

    /**
     * Mark a message's attachment as claimed.
     *
     * @param messageId the message UUID
     * @return future completing with success status
     */
    public CompletableFuture<Boolean> claimAttachment(UUID messageId) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repo.markAttachmentClaimed(messageId);
    }

    /**
     * Delete a message.
     *
     * @param messageId the message UUID
     * @return future completing with success status
     */
    public CompletableFuture<Boolean> deleteMessage(UUID messageId) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repo.deleteMessage(messageId);
    }

    // ============================================================================
    // CALLBACKS
    // ============================================================================

    /**
     * Set the callback for new message notifications.
     */
    public void setNewMessageCallback(@Nullable NewMessageCallback callback) {
        this.newMessageCallback = callback;
    }

    private void notifyNewMessage(UUID recipientUuid, MailboxMessage message) {
        NewMessageCallback callback = this.newMessageCallback;
        if (callback != null) {
            try {
                callback.onNewMessage(recipientUuid, message);
            } catch (Exception e) {
                LOGGER.error("[Mailbox] Error in new message callback", e);
            }
        }
    }

    // ============================================================================
    // RATE LIMITING
    // ============================================================================

    private boolean checkRateLimit(UUID playerUuid) {
        MailboxConfig config = MailboxConfig.INSTANCE;

        // Check cooldown
        Long lastSend = lastSendTime.get(playerUuid);
        if (lastSend != null) {
            long elapsed = System.currentTimeMillis() - lastSend;
            if (elapsed < config.getSendCooldownSeconds() * 1000L) {
                return false;
            }
        }

        // Check messages per minute
        Integer sends = sendsThisMinute.get(playerUuid);
        return sends == null || sends < config.getMaxMessagesPerMinute();
    }

    private void updateRateLimit(UUID playerUuid) {
        lastSendTime.put(playerUuid, System.currentTimeMillis());
        sendsThisMinute.merge(playerUuid, 1, Integer::sum);
    }

    // ============================================================================
    // SCHEDULED TASKS
    // ============================================================================

    private void purgeExpiredMessages() {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return;
        }

        repo.purgeExpiredMessages(Instant.now())
            .exceptionally(e -> {
                LOGGER.error("[Mailbox] Failed to purge expired messages", e);
                return 0;
            });
    }

    // ============================================================================
    // HELPER TYPES
    // ============================================================================

    /**
     * Result of a send operation.
     */
    public record SendResult(boolean success, @Nullable UUID messageId, @Nullable String error) {
        public static SendResult success(UUID messageId) {
            return new SendResult(true, messageId, null);
        }

        public static SendResult error(String error) {
            return new SendResult(false, null, error);
        }
    }

    /**
     * Callback interface for new message notifications.
     */
    @FunctionalInterface
    public interface NewMessageCallback {
        void onNewMessage(UUID recipientUuid, MailboxMessage message);
    }
}
