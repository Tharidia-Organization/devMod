package com.devmod.mailbox;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.loading.FMLPaths;

import com.devmod.endurance.RewardSystem;
import com.devmod.mailbox.analytics.MailboxAnalyticsEngine;
import com.devmod.mailbox.api.MailboxApiServer;
import com.devmod.mailbox.attachment.AttachmentTransactionLog;
import com.devmod.mailbox.attachment.CurrencyAttachment;
import com.devmod.mailbox.attachment.ItemAttachment;
import com.devmod.mailbox.attachment.MailAttachment;
import com.devmod.mailbox.broadcast.BroadcastQueueWorker;
import com.devmod.mailbox.digest.DigestManager;
import com.devmod.mailbox.moderation.AdminAuditLog;
import com.devmod.mailbox.moderation.ContentFilter;
import com.devmod.mailbox.news.NewsManager;
import com.devmod.mailbox.news.NewsPurgeJob;
import com.devmod.mailbox.persistence.DuckDbMailboxRepository;
import com.devmod.mailbox.persistence.MailboxRepository;
import com.devmod.mailbox.scheduler.MessageScheduler;
import com.devmod.mailbox.ticket.TicketManager;
import com.devmod.mailbox.webhook.WebhookManager;

/*
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
    @Nullable
    private ScheduledFuture<?> purgeExpiredTask;
    @Nullable
    private ScheduledFuture<?> rateLimitResetTask;

    /* Rate limiting: tracks last send time per player */
    private final Map<UUID, Long> lastSendTime = new ConcurrentHashMap<>();

    /* Rate limiting: tracks sends per minute per player */
    private final Map<UUID, Integer> sendsThisMinute = new ConcurrentHashMap<>();

    /* Rate limiting: tracks sends per day per player */
    private final Map<UUID, Integer> sendsToday = new ConcurrentHashMap<>();

    /* Rate limiting: tracks sends per day per sender->recipient */
    private final Map<SenderRecipientKey, Integer> sendsPerRecipientToday = new ConcurrentHashMap<>();

    private volatile long lastDailyResetEpochDay = currentEpochDay();

    /* In-flight claim operations per message */
    private final Map<UUID, CompletableFuture<ClaimOutcome>> claimInFlight = new ConcurrentHashMap<>();

    /* In-flight send operations per sender to guard attachment deductions */
    private final Map<UUID, Object> attachmentSendLocks = new ConcurrentHashMap<>();

    /* Callback for notifying clients of new messages */
    @Nullable
    private NewMessageCallback newMessageCallback;

    // ============================================================================
    // INITIALIZATION
    // ============================================================================

    /*
     * Initialize the mailbox system.
     * Call this when the server starts.
     */
    public CompletableFuture<Void> initialize() {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.info("[Mailbox] Initializing mailbox system...");

        MailboxPermissions.INSTANCE.loadFromConfig(MailboxConfig.INSTANCE);
        applyContentFilterConfig(MailboxConfig.INSTANCE);
        applyAttachmentRulesConfig(MailboxConfig.INSTANCE);

        Path dbPath = FMLPaths.GAMEDIR.get().resolve("devmod").resolve("mailbox.duckdb");
        repository = new DuckDbMailboxRepository(dbPath);

        MailboxRepository repo = repository;
        return repo.initialize().thenRun(() -> {
            com.devmod.mailbox.moderation.AdminAuditLog.INSTANCE.initialize(repo);
            NewsManager.INSTANCE.initialize(repo);
            com.devmod.mailbox.task.TestTaskManager.INSTANCE.initialize(repo);

            // Initialize and start the news purge job
            NewsPurgeJob.INSTANCE.initialize(repo);
            NewsPurgeJob.INSTANCE.start();

            // Start broadcast queue worker
            BroadcastQueueWorker.INSTANCE.start();

            // Start analytics engine
            MailboxAnalyticsEngine.INSTANCE.start();

            // Start message scheduler
            MessageScheduler.INSTANCE.start();

            // Start digest manager
            DigestManager.INSTANCE.start();

            // Start webhook manager
            WebhookManager.INSTANCE.start();

            // Start ticket manager
            TicketManager.INSTANCE.initialize().join();

            // Start scheduled tasks
            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MailboxScheduler");
                t.setDaemon(true);
                return t;
            });
            scheduler = sched;

            // Purge expired messages every hour
            purgeExpiredTask = sched.scheduleAtFixedRate(
                this::purgeExpiredMessages,
                1, 60, TimeUnit.MINUTES
            );

            // Reset rate limits every minute
            rateLimitResetTask = sched.scheduleAtFixedRate(
                () -> sendsThisMinute.clear(),
                1, 1, TimeUnit.MINUTES
            );

            MailboxConfig config = MailboxConfig.INSTANCE;
            if (config.isApiEnabled()) {
                MailboxApiServer.start(config.getApiPort());
            }

            initialized = true;
            LOGGER.info("[Mailbox] Mailbox system initialized");
        });
    }

    /*
     * Shutdown the mailbox system.
     * Call this when the server stops.
     */
    public CompletableFuture<Void> shutdown() {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.info("[Mailbox] Shutting down mailbox system...");

        if (purgeExpiredTask != null) {
            purgeExpiredTask.cancel(false);
            purgeExpiredTask = null;
        }

        if (rateLimitResetTask != null) {
            rateLimitResetTask.cancel(false);
            rateLimitResetTask = null;
        }

        if (scheduler != null) {
            scheduler.shutdown();
        }

        NewsManager.INSTANCE.shutdown();
        com.devmod.mailbox.task.TestTaskManager.INSTANCE.shutdown();
        com.devmod.mailbox.moderation.AdminAuditLog.INSTANCE.shutdown();
        NewsPurgeJob.INSTANCE.shutdown();
        BroadcastQueueWorker.INSTANCE.stop();
        MailboxAnalyticsEngine.INSTANCE.stop();
        MessageScheduler.INSTANCE.stop();
        DigestManager.INSTANCE.stop();
        WebhookManager.INSTANCE.stop();
        TicketManager.INSTANCE.shutdown().join();
        claimInFlight.clear();
        MailboxApiServer.stop();

        return repo.shutdown().thenRun(() -> {
            initialized = false;
            LOGGER.info("[Mailbox] Mailbox system shutdown complete");
        });
    }

    public void applyContentFilterConfig(@Nullable MailboxConfig config) {
        if (config == null) {
            return;
        }
        ContentFilter filter = ContentFilter.INSTANCE;
        filter.setEnabled(config.isContentFilterEnabled());
        filter.setAction(ContentFilter.parseAction(config.getContentFilterAction()));
        filter.setProhibitedWords(config.getContentFilterWords());
        filter.setProhibitedPatterns(config.getContentFilterPatterns());
    }

    public void applyAttachmentRulesConfig(@Nullable MailboxConfig config) {
        if (config == null) {
            return;
        }

        ItemAttachment.clearItemRules();
        ItemAttachment.setUseWhitelist(config.isItemAttachmentWhitelistEnabled());
        for (String itemId : config.getItemAttachmentWhitelist()) {
            ResourceLocation parsed = ResourceLocation.tryParse(Objects.requireNonNull(itemId));
            if (parsed == null) {
                LOGGER.warn("[Mailbox] Invalid item whitelist entry: {}", itemId);
                continue;
            }
            ItemAttachment.addWhitelistedItem(parsed);
        }
        for (String itemId : config.getItemAttachmentBlacklist()) {
            ResourceLocation parsed = ResourceLocation.tryParse(Objects.requireNonNull(itemId));
            if (parsed == null) {
                LOGGER.warn("[Mailbox] Invalid item blacklist entry: {}", itemId);
                continue;
            }
            ItemAttachment.addBlacklistedItem(parsed);
        }

        CurrencyAttachment.clearCurrencyRules();
        for (String currency : config.getCurrencyAttachmentAllowed()) {
            if (CurrencyAttachment.toRewardCurrency(currency) == null) {
                LOGGER.warn("[Mailbox] Unknown currency type in allowed list: {}", currency);
                continue;
            }
            CurrencyAttachment.addAllowedCurrency(currency);
        }
        for (Map.Entry<String, Integer> entry : config.getCurrencyAttachmentMaxAmounts().entrySet()) {
            if (CurrencyAttachment.toRewardCurrency(entry.getKey()) == null) {
                LOGGER.warn("[Mailbox] Unknown currency type in max amounts: {}", entry.getKey());
                continue;
            }
            CurrencyAttachment.setMaxAmount(entry.getKey(), entry.getValue());
        }
    }

    // ============================================================================
    // MESSAGE OPERATIONS
    // ============================================================================

    private record FilterDecision(
        boolean allowed,
        String subject,
        @Nullable String body,
        @Nullable String reason,
        boolean flagged
    ) {}

    private record AttachmentValidation(boolean isAllowed, @Nullable String error) {
        static AttachmentValidation success() {
            return new AttachmentValidation(true, null);
        }

        static AttachmentValidation blocked(String error) {
            return new AttachmentValidation(false, error);
        }
    }

    private FilterDecision applyContentFilter(String subject, @Nullable String body) {
        ContentFilter filter = ContentFilter.INSTANCE;
        if (!filter.isEnabled()) {
            return new FilterDecision(true, subject, body, null, false);
        }

        ContentFilter.FilterResult result = filter.checkMessage(subject, body);
        if (result.isAllowed()) {
            return new FilterDecision(true, subject, body, null, false);
        }

        String reason = result.reason() != null ? result.reason() : "Message blocked by filter";
        ContentFilter.FilterAction action = filter.getAction();

        return switch (action) {
            case BLOCK -> new FilterDecision(false, subject, body, reason, false);
            case CENSOR -> {
                String censoredSubject = filter.censor(subject);
                @Nullable String censoredBody = body != null ? filter.censor(body) : null;
                yield new FilterDecision(true, censoredSubject, censoredBody, reason, false);
            }
            case FLAG -> new FilterDecision(true, subject, body, reason, true);
        };
    }

    private AttachmentValidation validateAttachmentData(@Nullable String attachmentData) {
        if (attachmentData == null || attachmentData.isBlank()) {
            return AttachmentValidation.success();
        }

        MailboxConfig config = MailboxConfig.INSTANCE;
        List<MailAttachment> parsed = MailAttachment.parseAttachments(attachmentData);
        if (parsed.isEmpty()) {
            return AttachmentValidation.blocked("Invalid attachment data");
        }

        List<MailAttachment> flat = MailAttachment.flattenAttachments(parsed);
        if (flat.size() > config.getMaxAttachmentsPerMessage()) {
            return AttachmentValidation.blocked("Too many attachments");
        }

        for (MailAttachment attachment : flat) {
            if (attachment == null) {
                return AttachmentValidation.blocked("Invalid attachment data");
            }
            if (attachment instanceof ItemAttachment itemAttachment) {
                if (!config.isItemAttachmentsEnabled()) {
                    return AttachmentValidation.blocked("Item attachments are disabled");
                }
                String error = itemAttachment.validate();
                if (error != null) {
                    return AttachmentValidation.blocked(error);
                }
            } else if (attachment instanceof CurrencyAttachment currencyAttachment) {
                if (!config.isCurrencyAttachmentsEnabled()) {
                    return AttachmentValidation.blocked("Currency attachments are disabled");
                }
                String error = currencyAttachment.validate();
                if (error != null) {
                    return AttachmentValidation.blocked(error);
                }
            } else {
                return AttachmentValidation.blocked("Unsupported attachment type");
            }
        }

        return AttachmentValidation.success();
    }

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

        if (config.isMaintenanceMode() && !MailboxPermissions.INSTANCE.isAdmin(senderUuid, sender)) {
            return CompletableFuture.completedFuture(SendResult.error("Mailbox is in maintenance mode"));
        }

        // Check permissions
        if (!MailboxPermissions.INSTANCE.hasPermission(sender, MailboxPermissions.Permission.SEND_MESSAGES)) {
            return CompletableFuture.completedFuture(SendResult.error("You don't have permission to send messages"));
        }

        // Check if sender is blocked
        if (MailboxPermissions.INSTANCE.isSenderBlocked(senderUuid)) {
            return CompletableFuture.completedFuture(SendResult.error("You are blocked from sending messages"));
        }

        // Check if recipient can receive
        if (MailboxPermissions.INSTANCE.isReceiverBlocked(recipientUuid)) {
            return CompletableFuture.completedFuture(SendResult.error("Recipient cannot receive messages"));
        }

        // Check rate limiting
        String rateLimitError = checkRateLimit(senderUuid, recipientUuid);
        if (rateLimitError != null) {
            return CompletableFuture.completedFuture(SendResult.error(rateLimitError));
        }

        // Check minimum level requirement (fallback to vanilla XP level)
        if (config.getMinLevelToSend() > 0 && sender.experienceLevel < config.getMinLevelToSend()) {
            return CompletableFuture.completedFuture(SendResult.error("You do not meet the minimum level to send messages"));
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

        FilterDecision filterDecision = applyContentFilter(subject, body);
        if (!filterDecision.allowed()) {
            String reason = filterDecision.reason() != null
                ? filterDecision.reason()
                : "Message blocked by filter";
            return CompletableFuture.completedFuture(SendResult.error(reason));
        }

        List<MailAttachment> flatAttachments = List.of();
        // Validate attachments against config
        if (attachmentData != null && !attachmentData.isBlank()) {
            if (!MailboxPermissions.INSTANCE.hasPermission(sender, MailboxPermissions.Permission.SEND_ATTACHMENTS)) {
                return CompletableFuture.completedFuture(SendResult.error("You don't have permission to send attachments"));
            }
            List<MailAttachment> parsed = MailAttachment.parseAttachments(attachmentData);
            if (parsed.isEmpty()) {
                return CompletableFuture.completedFuture(SendResult.error("Invalid attachment data"));
            }

            List<MailAttachment> flat = MailAttachment.flattenAttachments(parsed);
            if (flat.size() > config.getMaxAttachmentsPerMessage()) {
                return CompletableFuture.completedFuture(SendResult.error("Too many attachments"));
            }

            for (MailAttachment attachment : flat) {
                if (attachment == null || !attachment.canClaim(sender)) {
                    return CompletableFuture.completedFuture(SendResult.error("Invalid attachment data"));
                }
                if (attachment instanceof ItemAttachment && !config.isItemAttachmentsEnabled()) {
                    return CompletableFuture.completedFuture(SendResult.error("Item attachments are disabled"));
                }
                if (attachment instanceof CurrencyAttachment && !config.isCurrencyAttachmentsEnabled()) {
                    return CompletableFuture.completedFuture(SendResult.error("Currency attachments are disabled"));
                }
            }
            flatAttachments = flat;
        }

        // Check recipient inbox capacity
        String finalSubject = filterDecision.subject();
        String finalBody = filterDecision.body();
        List<MailAttachment> attachmentsForReserve = flatAttachments;

        return repo.getMessageCount(recipientUuid).thenCompose(count -> {
            if (count >= config.getMaxMessagesPerPlayer()) {
                return CompletableFuture.completedFuture(SendResult.error("Recipient's inbox is full"));
            }

            CompletableFuture<AttachmentReservation> reservationFuture = attachmentsForReserve.isEmpty()
                ? CompletableFuture.completedFuture(AttachmentReservation.empty())
                : reserveAttachmentsForSend(sender, attachmentsForReserve);

            return reservationFuture.thenCompose(reservation -> {
                if (!reservation.success()) {
                    String reason = reservation.error() != null ? reservation.error() : "Failed to reserve attachments";
                    return CompletableFuture.completedFuture(SendResult.error(reason));
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
                    updateRateLimit(senderUuid, recipientUuid);
                    notifyNewMessage(recipientUuid, saved);
                    LOGGER.debug("[Mailbox] Player {} sent message to {}", senderUuid, recipientUuid);

                    if (filterDecision.flagged()) {
                        logFlaggedMessage(
                            senderUuid,
                            sender.getName().getString(),
                            recipientUuid,
                            saved.id(),
                            saved.messageType(),
                            filterDecision.reason(),
                            saved.subject()
                        );
                    }

                    // Dispatch webhook
                    WebhookManager.INSTANCE.dispatchMessageSent(
                        saved.id(),
                        senderUuid,
                        sender.getName().getString(),
                        recipientUuid,
                        finalSubject,
                        attachmentData != null && !attachmentData.isBlank()
                    );

                    return SendResult.success(saved.id());
                }).exceptionally(e -> {
                    LOGGER.error("[Mailbox] Failed to send message", e);
                    refundReservation(sender, reservation);
                    return SendResult.error("Failed to send message");
                });
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
        return sendSystemMessage(recipientUuid, subject, body, attachmentData, expiresIn, true, true);
    }

    private CompletableFuture<UUID> sendSystemMessage(
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            @Nullable Duration expiresIn,
            boolean applyFilter,
            boolean validateAttachments
    ) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Mailbox not initialized"));
        }

        FilterDecision filterDecision = applyFilter
            ? applyContentFilter(subject, body)
            : new FilterDecision(true, subject, body, null, false);
        if (!filterDecision.allowed()) {
            String reason = filterDecision.reason() != null
                ? filterDecision.reason()
                : "Message blocked by filter";
            return CompletableFuture.failedFuture(new IllegalArgumentException(reason));
        }

        if (validateAttachments) {
            AttachmentValidation validation = validateAttachmentData(attachmentData);
            if (!validation.isAllowed()) {
                String reason = validation.error() != null ? validation.error() : "Invalid attachment data";
                return CompletableFuture.failedFuture(new IllegalArgumentException(reason));
            }
        }

        Instant expiresAt = expiresIn != null
            ? Instant.now().plus(expiresIn)
            : Instant.now().plus(MailboxConfig.INSTANCE.getDefaultMessageTtl());

        MailboxMessage message = MailboxMessage.builder()
            .sender(null, "System")
            .recipient(recipientUuid)
            .subject(filterDecision.subject())
            .body(filterDecision.body())
            .messageType(MessageType.SYSTEM)
            .expiresAt(expiresAt)
            .attachment(attachmentData)
            .build();

        return repo.saveMessage(message).thenApply(saved -> {
            notifyNewMessage(recipientUuid, saved);
            LOGGER.debug("[Mailbox] System message sent to {}: {}", recipientUuid, saved.subject());

            if (filterDecision.flagged()) {
                logFlaggedMessage(
                    null,
                    "System",
                    recipientUuid,
                    saved.id(),
                    saved.messageType(),
                    filterDecision.reason(),
                    saved.subject()
                );
            }
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
        return sendAdminMessage(adminName, recipientUuid, subject, body, attachmentData, null, true, true);
    }

    public CompletableFuture<UUID> sendAdminMessage(
            String adminName,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            @Nullable Instant expiresAt
    ) {
        return sendAdminMessage(adminName, recipientUuid, subject, body, attachmentData, expiresAt, true, true);
    }

    private CompletableFuture<UUID> sendAdminMessage(
            String adminName,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            @Nullable Instant expiresAt,
            boolean applyFilter,
            boolean validateAttachments
    ) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Mailbox not initialized"));
        }

        FilterDecision filterDecision = applyFilter
            ? applyContentFilter(subject, body)
            : new FilterDecision(true, subject, body, null, false);
        if (!filterDecision.allowed()) {
            String reason = filterDecision.reason() != null
                ? filterDecision.reason()
                : "Message blocked by filter";
            return CompletableFuture.failedFuture(new IllegalArgumentException(reason));
        }

        if (validateAttachments) {
            AttachmentValidation validation = validateAttachmentData(attachmentData);
            if (!validation.isAllowed()) {
                String reason = validation.error() != null ? validation.error() : "Invalid attachment data";
                return CompletableFuture.failedFuture(new IllegalArgumentException(reason));
            }
        }

        Instant now = Instant.now();
        Instant resolvedExpiresAt;
        if (expiresAt != null) {
            resolvedExpiresAt = expiresAt.isBefore(now) ? now : expiresAt;
        } else {
            resolvedExpiresAt = now.plus(MailboxConfig.INSTANCE.getDefaultMessageTtl());
        }

        MailboxMessage message = MailboxMessage.builder()
            .sender(null, adminName)
            .recipient(recipientUuid)
            .subject(filterDecision.subject())
            .body(filterDecision.body())
            .messageType(MessageType.ADMIN)
            .expiresAt(resolvedExpiresAt)
            .attachment(attachmentData)
            .build();

        return repo.saveMessage(message).thenApply(saved -> {
            notifyNewMessage(recipientUuid, saved);
            LOGGER.debug("[Mailbox] Admin message from {} sent to {}: {}", adminName, recipientUuid, saved.subject());

            if (filterDecision.flagged()) {
                logFlaggedMessage(
                    null,
                    adminName,
                    recipientUuid,
                    saved.id(),
                    saved.messageType(),
                    filterDecision.reason(),
                    saved.subject()
                );
            }
            return saved.id();
        });
    }

    private CompletableFuture<UUID> sendTypedMessage(
            String senderName,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            MessageType type,
            @Nullable Instant expiresAt,
            boolean applyFilter,
            boolean validateAttachments
    ) {
        if (type == MessageType.SYSTEM) {
            Duration expiresIn = null;
            if (expiresAt != null) {
                Instant now = Instant.now();
                expiresIn = Duration.between(now, expiresAt);
                if (expiresIn.isNegative()) {
                    expiresIn = Duration.ZERO;
                }
            }
            return sendSystemMessage(recipientUuid, subject, body, attachmentData, expiresIn, applyFilter, validateAttachments);
        }
        return sendAdminMessage(senderName, recipientUuid, subject, body, attachmentData, expiresAt, applyFilter, validateAttachments);
    }

    /**
     * Send a broadcast message to a list of players.
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
        return sendBroadcast(adminName, playerUuids, subject, body, attachmentData, MessageType.ADMIN);
    }

    /**
     * Send a broadcast message to a list of players with a specific message type.
     */
    public CompletableFuture<Integer> sendBroadcast(
            String senderName,
            List<UUID> playerUuids,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            MessageType type
    ) {
        return sendBroadcast(senderName, playerUuids, subject, body, attachmentData, type, null);
    }

    public CompletableFuture<Integer> sendBroadcast(
            String senderName,
            List<UUID> playerUuids,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            MessageType type,
            @Nullable Instant expiresAt
    ) {
        if (!initialized || repository == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Mailbox not initialized"));
        }

        if (playerUuids.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        FilterDecision filterDecision = applyContentFilter(subject, body);
        if (!filterDecision.allowed()) {
            String reason = filterDecision.reason() != null
                ? filterDecision.reason()
                : "Message blocked by filter";
            return CompletableFuture.failedFuture(new IllegalArgumentException(reason));
        }

        AttachmentValidation attachmentValidation = validateAttachmentData(attachmentData);
        if (!attachmentValidation.isAllowed()) {
            String reason = attachmentValidation.error() != null
                ? attachmentValidation.error()
                : "Invalid attachment data";
            return CompletableFuture.failedFuture(new IllegalArgumentException(reason));
        }

        MessageType normalizedType =
            (type == MessageType.ADMIN || type == MessageType.SYSTEM) ? type : MessageType.ADMIN;
        if (normalizedType != type) {
            LOGGER.warn("[Mailbox] Unsupported broadcast type {}, defaulting to ADMIN", type);
        }

        MailboxConfig config = MailboxConfig.INSTANCE;
        int batchSize = Math.max(1, config.getBroadcastBatchSize());
        int delayMs = Math.max(0, config.getBroadcastBatchDelayMs());

        LOGGER.info(
            "[Mailbox] Sending broadcast to {} players: {} (batch size {}, delay {} ms)",
            playerUuids.size(),
            filterDecision.subject(),
            batchSize,
            delayMs
        );

        if (filterDecision.flagged()) {
            AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                .action(AdminAuditLog.Action.MESSAGE_FLAG)
                .actorUuid(null)
                .actorName(senderName)
                .targetType("broadcast")
                .details("Recipients=" + playerUuids.size()
                    + "; Type=" + normalizedType
                    + "; Reason=" + filterDecision.reason()
                    + "; Subject=" + truncate(filterDecision.subject(), 80))
                .build())
                .exceptionally(error -> {
                    LOGGER.warn("[Mailbox] Failed to log flagged broadcast {}", filterDecision.subject(), error);
                    return null;
                });
        }

        List<List<UUID>> batches = new ArrayList<>();
        for (int i = 0; i < playerUuids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, playerUuids.size());
            batches.add(playerUuids.subList(i, end));
        }

        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        final int totalBatches = batches.size();
        for (int i = 0; i < totalBatches; i++) {
            final List<UUID> batch = batches.get(i);
            final int batchIndex = i + 1;
            final boolean delayAfter = delayMs > 0 && batchIndex < totalBatches;

            chain = chain.thenCompose(sentSoFar ->
                sendBroadcastBatch(
                    senderName,
                    batch,
                    filterDecision.subject(),
                    filterDecision.body(),
                    attachmentData,
                    normalizedType,
                    expiresAt
                )
                    .thenCompose(sentInBatch -> {
                        int totalSent = sentSoFar + sentInBatch;
                        if (delayAfter) {
                            return delayBroadcast(delayMs).thenApply(v -> totalSent);
                        }
                        return CompletableFuture.completedFuture(totalSent);
                    })
                    .whenComplete((sent, error) -> {
                        if (error == null) {
                            LOGGER.debug("[Mailbox] Broadcast batch {}/{} sent", batchIndex, totalBatches);
                        }
                    })
            );
        }

        String finalSenderName = senderName;
        String finalSubject = filterDecision.subject();
        return chain.whenComplete((sent, error) -> {
            if (error == null) {
                LOGGER.info("[Mailbox] Broadcast complete: {} messages sent", sent);

                // Dispatch webhook
                if (sent != null && sent > 0) {
                    WebhookManager.INSTANCE.dispatchBroadcastSent(
                        finalSubject,
                        sent,
                        finalSenderName
                    );
                }
            } else {
                LOGGER.error("[Mailbox] Broadcast failed", error);
            }
        });
    }

    private CompletableFuture<Integer> sendBroadcastBatch(
            String senderName,
            List<UUID> playerUuids,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            MessageType type,
            @Nullable Instant expiresAt
    ) {
        List<CompletableFuture<Integer>> futures = playerUuids.stream()
            .map(uuid -> sendTypedMessage(senderName, uuid, subject, body, attachmentData, type, expiresAt, false, false)
                .handle((id, error) -> {
                    if (error != null) {
                        LOGGER.error("[Mailbox] Broadcast failed for {}", uuid, error);
                        return 0;
                    }
                    return 1;
                }))
            .toList();

        CompletableFuture<?>[] futureArray = futures.toArray(new CompletableFuture<?>[0]);
        return CompletableFuture.allOf(futureArray)
            .thenApply(v -> futures.stream().mapToInt(CompletableFuture::join).sum());
    }

    private static CompletableFuture<Void> delayBroadcast(int delayMs) {
        if (delayMs <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(
            () -> {},
            CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
        );
    }

    private void logFlaggedMessage(
            @Nullable UUID actorUuid,
            String actorName,
            UUID recipientUuid,
            UUID messageId,
            MessageType type,
            @Nullable String reason,
            String subject
    ) {
        String details = "Recipient=" + recipientUuid
            + "; Type=" + type
            + "; Reason=" + (reason != null ? reason : "Flagged by content filter")
            + "; Subject=" + truncate(subject, 80);

        AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
            .action(AdminAuditLog.Action.MESSAGE_FLAG)
            .actorUuid(actorUuid)
            .actorName(actorName)
            .targetType("message")
            .targetId(messageId.toString())
            .details(details)
            .build()).exceptionally(error -> {
                LOGGER.warn("[Mailbox] Failed to log flagged message {}", messageId, error);
                return null;
            });
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, Math.max(0, maxLen - 3)) + "...";
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

    /*
     * Claim attachments for a message with an in-flight guard to prevent duplicates.
     */
    public CompletableFuture<ClaimOutcome> claimAttachments(ServerPlayer player, MailboxMessage message) {
        if (!message.recipientUuid().equals(player.getUUID()) || message.deleted()) {
            return CompletableFuture.completedFuture(ClaimOutcome.failure("Access denied"));
        }
        if (!message.canClaimAttachment()) {
            return CompletableFuture.completedFuture(ClaimOutcome.failure("No attachment to claim"));
        }
        if (MailboxConfig.INSTANCE.isMaintenanceMode()
                && !MailboxPermissions.INSTANCE.isAdmin(player.getUUID(), player)) {
            return CompletableFuture.completedFuture(ClaimOutcome.failure("Mailbox is in maintenance mode"));
        }

        UUID messageId = message.id();
        return claimInFlight.computeIfAbsent(messageId, id ->
            doClaimAttachments(player, message)
                .whenComplete((result, error) -> claimInFlight.remove(id))
        );
    }

    private CompletableFuture<ClaimOutcome> doClaimAttachments(ServerPlayer player, MailboxMessage message) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(ClaimOutcome.failure("Mailbox system not initialized"));
        }
        String attachmentData = message.attachmentData();
        if (attachmentData == null || attachmentData.isBlank()) {
            return CompletableFuture.completedFuture(ClaimOutcome.failure("Attachment data missing"));
        }

        List<MailAttachment> parsed = MailAttachment.parseAttachments(attachmentData);
        List<MailAttachment> attachments = MailAttachment.flattenAttachments(parsed);
        if (attachments.isEmpty()) {
            return CompletableFuture.completedFuture(ClaimOutcome.failure("Invalid attachment data"));
        }

        boolean canClaimAll = attachments.stream().allMatch(a -> a != null && a.canClaim(player));
        if (!canClaimAll) {
            return CompletableFuture.completedFuture(ClaimOutcome.failure("Cannot claim attachments"));
        }

        return repo.startAttachmentClaim(message.id()).thenCompose(started -> {
            if (!started) {
                return CompletableFuture.completedFuture(
                    ClaimOutcome.failure("Attachment already claimed or in progress")
                );
            }

            ClaimAttempt attempt = claimAttachmentsNow(player, message.id(), attachments);
            if (!attempt.finalizeClaim()) {
                return repo.clearAttachmentClaim(message.id())
                    .exceptionally(e -> {
                        LOGGER.error("[Mailbox] Failed to clear attachment claim lock {}", message.id(), e);
                        return false;
                    })
                    .thenApply(ignored -> attempt.outcome());
            }

            return repo.markAttachmentClaimed(message.id()).thenApply(success -> {
                if (success) {
                    if (attempt.outcome().success()) {
                        String attachmentType = determineAttachmentType(attachments);
                        WebhookManager.INSTANCE.dispatchAttachmentClaimed(
                            message.id(),
                            player.getUUID(),
                            attachmentType
                        );
                        return attempt.outcome();
                    }
                    AttachmentTransactionLog.INSTANCE.logSuspiciousActivity(
                        player.getUUID(),
                        player.getName().getString(),
                        "Partial attachment claim finalized",
                        "messageId=" + message.id()
                    );
                    return ClaimOutcome.failure("Claim partially completed. Contact support.");
                }
                LOGGER.error("[Mailbox] Failed to finalize attachment claim {}", message.id());
                return ClaimOutcome.failure("Claim completed but could not be finalized");
            });
        });
    }

    private static ClaimAttempt claimAttachmentsNow(ServerPlayer player, UUID messageId, List<MailAttachment> attachments) {
        List<String> receipts = new ArrayList<>();
        String playerName = player.getName().getString();
        UUID playerUuid = player.getUUID();

        List<MailAttachment> ordered = new ArrayList<>(attachments.size());
        for (MailAttachment attachment : attachments) {
            if (attachment instanceof CurrencyAttachment) {
                ordered.add(attachment);
            }
        }
        for (MailAttachment attachment : attachments) {
            if (!(attachment instanceof CurrencyAttachment)) {
                ordered.add(attachment);
            }
        }

        Map<RewardSystem.Currency, Integer> currencyAwards = new HashMap<>();
        boolean grantedNonCurrency = false;

        for (MailAttachment attachment : ordered) {
            MailAttachment.ClaimResult result = attachment.claim(player);

            // Log the transaction
            if (attachment instanceof ItemAttachment itemAtt) {
                AttachmentTransactionLog.INSTANCE.logItemClaim(
                    messageId, playerUuid, playerName,
                    itemAtt.itemId().toString(), itemAtt.count(), itemAtt.nbtData(),
                    result.success(), result.success() ? null : result.message()
                );
            } else if (attachment instanceof CurrencyAttachment currAtt) {
                AttachmentTransactionLog.INSTANCE.logCurrencyClaim(
                    messageId, playerUuid, playerName,
                    currAtt.currencyType(), currAtt.amount(),
                    result.success(), result.success() ? null : result.message()
                );
            }

            if (!result.success()) {
                String messageText = result.message() != null
                    ? result.message()
                    : "Failed to claim attachment";
                boolean rollbackFailed = !rollbackCurrencies(player, currencyAwards);
                boolean finalizeClaim = grantedNonCurrency || rollbackFailed;
                return new ClaimAttempt(ClaimOutcome.failure(messageText), finalizeClaim);
            }
            if (attachment instanceof CurrencyAttachment currAtt) {
                RewardSystem.Currency currency = CurrencyAttachment.toRewardCurrency(currAtt.currencyType());
                if (currency != null) {
                    currencyAwards.merge(currency, currAtt.amount(), (a, b) -> a + b);
                }
            } else {
                grantedNonCurrency = true;
            }
            String resultMsg = result.message();
            if (resultMsg != null && !resultMsg.isBlank()) {
                receipts.add(resultMsg);
            }
        }

        String summary = receipts.isEmpty()
            ? "Attachment claimed!"
            : String.join(", ", receipts);
        return new ClaimAttempt(ClaimOutcome.success(summary), true);
    }

    private static String determineAttachmentType(List<MailAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "unknown";
        }
        boolean hasItems = attachments.stream().anyMatch(a -> a instanceof ItemAttachment);
        boolean hasCurrency = attachments.stream().anyMatch(a -> a instanceof CurrencyAttachment);
        if (hasItems && hasCurrency) {
            return "mixed";
        } else if (hasItems) {
            return "items";
        } else if (hasCurrency) {
            return "currency";
        }
        return "unknown";
    }

    private static boolean rollbackCurrencies(ServerPlayer player, Map<RewardSystem.Currency, Integer> awards) {
        if (awards.isEmpty()) {
            return true;
        }
        try {
            RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(player.getUUID());
            for (Map.Entry<RewardSystem.Currency, Integer> entry : awards.entrySet()) {
                wallet.removeCurrency(entry.getKey(), entry.getValue());
            }
            RewardSystem.INSTANCE.savePlayerWallet(wallet);
            return true;
        } catch (Exception e) {
            LOGGER.error("[Mailbox] Failed to rollback currencies for {}", player.getName().getString(), e);
            return false;
        }
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
        MailboxConfig config = MailboxConfig.INSTANCE;
        if (config.isHardDeleteOnUserDelete()) {
            return repo.hardDeleteMessage(messageId);
        }
        Instant retainUntil = Instant.now().plus(Duration.ofDays(config.getMessageRetentionDays()));
        return repo.softDeleteMessage(messageId, retainUntil);
    }

    // ============================================================================
    // API SUPPORT METHODS
    // ============================================================================

    /**
     * Send a message using a pre-built message object.
     *
     * @param message the message to send
     * @return future completing when saved
     */
    public CompletableFuture<Void> sendMessage(MailboxMessage message) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Mailbox not initialized"));
        }
        return repo.saveMessage(message).thenAccept(saved ->
            notifyNewMessage(saved.recipientUuid(), saved)
        );
    }

    /**
     * Get all messages across all users (admin only).
     *
     * @param limit maximum number to return
     * @param offset starting offset
     * @return future completing with message list
     */
    public CompletableFuture<List<MailboxMessage>> getAllMessages(int limit, int offset) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repo.getAllMessages(limit, offset);
    }

    /**
     * Broadcast a message to all known players (online + offline).
     *
     * @param senderName the sender name
     * @param subject the message subject
     * @param body the message body
     * @param type the message type
     * @return future completing with count of recipients
     */
    public CompletableFuture<Integer> broadcast(
            String senderName,
            String subject,
            @Nullable String body,
            MessageType type
    ) {
        return broadcast(senderName, subject, body, type, null, null);
    }

    public CompletableFuture<Integer> broadcast(
            String senderName,
            String subject,
            @Nullable String body,
            MessageType type,
            @Nullable String attachmentData,
            @Nullable Instant expiresAt
    ) {
        return getBroadcastRecipients()
            .thenCompose(recipients -> {
                if (recipients.isEmpty()) {
                    return CompletableFuture.completedFuture(0);
                }
                return sendBroadcast(senderName, recipients, subject, body, attachmentData, type, expiresAt);
            });
    }

    /**
     * Get all known user UUIDs that have received messages.
     *
     * @return future completing with list of UUIDs
     */
    public CompletableFuture<List<UUID>> getKnownUsers() {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repo.getKnownUsers();
    }

    /**
     * Get all recipients for a broadcast (known users + online players).
     */
    public CompletableFuture<List<UUID>> getBroadcastRecipients() {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        net.minecraft.server.MinecraftServer server =
            net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();

        List<UUID> onlineUuids = server == null
            ? List.of()
            : server.getPlayerList().getPlayers().stream()
                .map(p -> p.getUUID())
                .toList();

        return repo.getKnownUsers()
            .exceptionally(e -> {
                LOGGER.error("[Mailbox] Failed to load known users for broadcast", e);
                return List.of();
            })
            .thenApply(knownUsers -> {
                if (knownUsers.isEmpty() && onlineUuids.isEmpty()) {
                    return List.of();
                }
                java.util.LinkedHashSet<UUID> recipients = new java.util.LinkedHashSet<>(
                    knownUsers.size() + onlineUuids.size()
                );
                recipients.addAll(knownUsers);
                recipients.addAll(onlineUuids);
                return List.copyOf(recipients);
            });
    }

    /**
     * Get total message count across all users.
     *
     * @return future completing with count
     */
    public CompletableFuture<Integer> getTotalMessageCount() {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(0);
        }
        return repo.getTotalMessageCount();
    }

    /**
     * Get total unread message count across all users.
     *
     * @return future completing with count
     */
    public CompletableFuture<Integer> getTotalUnreadCount() {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(0);
        }
        return repo.getTotalUnreadCount();
    }

    // ============================================================================
    // CALLBACKS
    // ============================================================================

    /*
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

    @Nullable
    private String checkRateLimit(UUID playerUuid, UUID recipientUuid) {
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

    private void updateRateLimit(UUID playerUuid, UUID recipientUuid) {
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
    // ATTACHMENT RESERVATION (P2P SEND)
    // ============================================================================

    private CompletableFuture<AttachmentReservation> reserveAttachmentsForSend(
            ServerPlayer sender,
            List<MailAttachment> attachments
    ) {
        net.minecraft.server.MinecraftServer server = sender.server;
        CompletableFuture<AttachmentReservation> future = new CompletableFuture<>();
        Runnable task = () -> future.complete(reserveAttachmentsNow(sender, attachments));
        if (server != null) {
            server.execute(task);
        } else {
            task.run();
        }
        return future;
    }

    private AttachmentReservation reserveAttachmentsNow(ServerPlayer sender, List<MailAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return AttachmentReservation.empty();
        }

        UUID senderUuid = sender.getUUID();
        Object lock = attachmentSendLocks.computeIfAbsent(senderUuid, id -> new Object());

        synchronized (lock) {
            Map<ResourceLocation, Integer> itemCounts = new HashMap<>();
            Map<RewardSystem.Currency, Integer> currencyCounts = new HashMap<>();

            for (MailAttachment attachment : attachments) {
                if (attachment instanceof ItemAttachment itemAtt) {
                    String nbtData = itemAtt.nbtData();
                    if (nbtData != null && !nbtData.isBlank()) {
                        return AttachmentReservation.failure("Item attachments with NBT are not supported");
                    }
                    itemCounts.merge(itemAtt.itemId(), itemAtt.count(), (a, b) -> a + b);
                } else if (attachment instanceof CurrencyAttachment currencyAtt) {
                    RewardSystem.Currency currency = CurrencyAttachment.toRewardCurrency(currencyAtt.currencyType());
                    if (currency == null) {
                        return AttachmentReservation.failure("Currency type not supported: " + currencyAtt.currencyType());
                    }
                    currencyCounts.merge(currency, currencyAtt.amount(), (a, b) -> a + b);
                } else {
                    return AttachmentReservation.failure("Unsupported attachment type");
                }
            }

            Inventory inventory = sender.getInventory();
            for (Map.Entry<ResourceLocation, Integer> entry : itemCounts.entrySet()) {
                Item item = BuiltInRegistries.ITEM.get(entry.getKey());
                if (item == null || item == net.minecraft.world.item.Items.AIR) {
                    return AttachmentReservation.failure("Invalid item: " + entry.getKey());
                }
                int available = countItem(inventory, item);
                if (available < entry.getValue()) {
                    return AttachmentReservation.failure("Insufficient items for " + entry.getKey());
                }
            }

            RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(senderUuid);
            for (Map.Entry<RewardSystem.Currency, Integer> entry : currencyCounts.entrySet()) {
                int available = wallet.getCurrency(entry.getKey());
                if (available < entry.getValue()) {
                    return AttachmentReservation.failure("Insufficient " + entry.getKey().displayName);
                }
            }

            Map<ResourceLocation, Integer> removedItems = new HashMap<>();
            for (Map.Entry<ResourceLocation, Integer> entry : itemCounts.entrySet()) {
                Item item = BuiltInRegistries.ITEM.get(entry.getKey());
                int removed = removeItems(inventory, item, entry.getValue());
                if (removed < entry.getValue()) {
                    restoreItems(sender, removedItems);
                    return AttachmentReservation.failure("Failed to reserve item: " + entry.getKey());
                }
                removedItems.merge(entry.getKey(), removed, (a, b) -> a + b);
            }
            inventory.setChanged();

            for (Map.Entry<RewardSystem.Currency, Integer> entry : currencyCounts.entrySet()) {
                wallet.removeCurrency(entry.getKey(), entry.getValue());
            }
            RewardSystem.INSTANCE.savePlayerWallet(wallet);

            return new AttachmentReservation(true, null, Map.copyOf(itemCounts), Map.copyOf(currencyCounts));
        }
    }

    private static int countItem(Inventory inventory, Item item) {
        int count = 0;
        int size = inventory.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int removeItems(Inventory inventory, Item item, int count) {
        int remaining = count;
        int size = inventory.getContainerSize();
        for (int i = 0; i < size && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }
            int remove = Math.min(remaining, stack.getCount());
            stack.shrink(remove);
            if (stack.isEmpty()) {
                inventory.setItem(i, Objects.requireNonNull(ItemStack.EMPTY));
            }
            remaining -= remove;
        }
        return count - remaining;
    }

    private void restoreItems(ServerPlayer sender, Map<ResourceLocation, Integer> removedItems) {
        if (removedItems.isEmpty()) {
            return;
        }
        Inventory inventory = sender.getInventory();
        for (Map.Entry<ResourceLocation, Integer> entry : removedItems.entrySet()) {
            Item item = BuiltInRegistries.ITEM.get(entry.getKey());
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item, entry.getValue());
            if (!inventory.add(stack)) {
                sender.drop(stack, false);
            }
        }
        inventory.setChanged();
    }

    private void refundReservation(ServerPlayer sender, AttachmentReservation reservation) {
        if (!reservation.success()) {
            return;
        }
        if (reservation.items().isEmpty() && reservation.currencies().isEmpty()) {
            return;
        }
        net.minecraft.server.MinecraftServer server = sender.server;
        Runnable task = () -> {
            Inventory inventory = sender.getInventory();
            for (Map.Entry<ResourceLocation, Integer> entry : reservation.items().entrySet()) {
                Item item = BuiltInRegistries.ITEM.get(entry.getKey());
                if (item == null || item == net.minecraft.world.item.Items.AIR) {
                    continue;
                }
                ItemStack stack = new ItemStack(item, entry.getValue());
                if (!inventory.add(stack)) {
                    sender.drop(stack, false);
                }
            }
            inventory.setChanged();

            if (!reservation.currencies().isEmpty()) {
                RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(sender.getUUID());
                for (Map.Entry<RewardSystem.Currency, Integer> entry : reservation.currencies().entrySet()) {
                    wallet.addCurrency(entry.getKey(), entry.getValue());
                }
                RewardSystem.INSTANCE.savePlayerWallet(wallet);
            }
        };
        if (server != null) {
            server.execute(task);
        } else {
            task.run();
        }
    }

    // ============================================================================
    // HELPER TYPES
    // ============================================================================

    /*
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

    /*
     * Result of a claim operation.
     */
    public record ClaimOutcome(boolean success, String message) {
        public static ClaimOutcome success(String message) {
            return new ClaimOutcome(true, message);
        }

        public static ClaimOutcome failure(String message) {
            return new ClaimOutcome(false, message);
        }
    }

    private record ClaimAttempt(ClaimOutcome outcome, boolean finalizeClaim) {}

    private record AttachmentReservation(
        boolean success,
        @Nullable String error,
        Map<ResourceLocation, Integer> items,
        Map<RewardSystem.Currency, Integer> currencies
    ) {
        static AttachmentReservation empty() {
            return new AttachmentReservation(true, null, Map.of(), Map.of());
        }

        static AttachmentReservation failure(String error) {
            return new AttachmentReservation(false, error, Map.of(), Map.of());
        }
    }

    private record SenderRecipientKey(UUID sender, UUID recipient) {}

    /*
     * Callback interface for new message notifications.
     */
    @FunctionalInterface
    public interface NewMessageCallback {
        void onNewMessage(UUID recipientUuid, MailboxMessage message);
    }
}
