package com.devmod.mailbox;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.mailbox.attachment.CurrencyAttachment;
import com.devmod.mailbox.attachment.ItemAttachment;
import com.devmod.mailbox.attachment.MailAttachment;
import com.devmod.mailbox.delivery.MailboxDeliveryJob;
import com.devmod.mailbox.moderation.AdminAuditLog;
import com.devmod.mailbox.moderation.ContentFilter;
import com.devmod.mailbox.moderation.PlayerReputation;
import com.devmod.mailbox.persistence.MailboxRepository;
import com.devmod.mailbox.webhook.WebhookManager;

/**
 * Handles message sending operations: player-to-player, system, admin, and broadcast messages.
 * Includes content filtering, spam detection, and delivery pipeline.
 */
public class MailboxMessageSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxMessageSender.class);

    private final MailboxRateLimiter rateLimiter;
    private final MailboxAttachmentHandler attachmentHandler;

    MailboxMessageSender(MailboxRateLimiter rateLimiter, MailboxAttachmentHandler attachmentHandler) {
        this.rateLimiter = rateLimiter;
        this.attachmentHandler = attachmentHandler;
    }

    // ========================================================================
    // CONTENT FILTERING
    // ========================================================================

    record FilterDecision(
        boolean allowed,
        String subject,
        @Nullable String body,
        @Nullable String reason,
        boolean flagged
    ) {}

    FilterDecision applyContentFilter(String subject, @Nullable String body) {
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

    // ========================================================================
    // SEND OPERATIONS
    // ========================================================================

    /**
     * Send a message from one player to another.
     */
    public CompletableFuture<MailboxManager.SendResult> sendPlayerMessage(
            ServerPlayer sender,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Mailbox system not initialized"));
        }

        MailboxConfig config = MailboxConfig.INSTANCE;

        if (!config.isPlayerToPlayerEnabled()) {
            return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Player messaging is disabled"));
        }

        UUID senderUuid = sender.getUUID();

        if (config.isMaintenanceMode() && !MailboxPermissions.INSTANCE.isAdmin(senderUuid, sender)) {
            return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Mailbox is in maintenance mode"));
        }

        if (!MailboxPermissions.INSTANCE.hasPermission(sender, MailboxPermissions.Permission.SEND_MESSAGES)) {
            return CompletableFuture.completedFuture(MailboxManager.SendResult.error("You don't have permission to send messages"));
        }

        if (MailboxPermissions.INSTANCE.isSenderBlocked(senderUuid)) {
            return CompletableFuture.completedFuture(MailboxManager.SendResult.error("You are blocked from sending messages"));
        }

        if (MailboxPermissions.INSTANCE.isReceiverBlocked(recipientUuid)) {
            return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Recipient cannot receive messages"));
        }

        String rateLimitError = rateLimiter.checkRateLimit(senderUuid, recipientUuid);
        if (rateLimitError != null) {
            return CompletableFuture.completedFuture(MailboxManager.SendResult.error(rateLimitError));
        }

        if (config.getMinLevelToSend() > 0 && sender.experienceLevel < config.getMinLevelToSend()) {
            return CompletableFuture.completedFuture(MailboxManager.SendResult.error("You do not meet the minimum level to send messages"));
        }

        if (subject == null || subject.isBlank()) {
            return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Subject cannot be empty"));
        }
        if (subject.length() > config.getMaxSubjectLength()) {
            subject = subject.substring(0, config.getMaxSubjectLength());
        }

        if (body != null && body.length() > config.getMaxBodyLength()) {
            body = body.substring(0, config.getMaxBodyLength());
        }

        FilterDecision filterDecision = applyContentFilter(subject, body);
        if (!filterDecision.allowed()) {
            PlayerReputation.INSTANCE.recordFilterBlocked(senderUuid);
            String reason = filterDecision.reason() != null
                ? filterDecision.reason()
                : "Message blocked by filter";
            return CompletableFuture.completedFuture(MailboxManager.SendResult.error(reason));
        }

        var spamScore = com.devmod.mailbox.moderation.SpamDetector.INSTANCE.score(
            senderUuid, recipientUuid, subject, body);
        if (spamScore.isSpam(com.devmod.mailbox.moderation.SpamDetector.INSTANCE.getSpamThreshold())) {
            LOGGER.warn("[Mailbox] Spam blocked: sender={}, score={}, signals={}",
                sender.getName().getString(), spamScore.totalScore(), spamScore.getSignalSummary());
            PlayerReputation.INSTANCE.recordSpamBlocked(senderUuid);
            return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Message blocked: suspected spam"));
        }
        boolean flaggedForModeration = spamScore.isSuspicious(
            com.devmod.mailbox.moderation.SpamDetector.INSTANCE.getSuspiciousThreshold());
        if (flaggedForModeration) {
            LOGGER.info("[Mailbox] Flagged for moderation: sender={}, score={}, signals={}",
                sender.getName().getString(), spamScore.totalScore(), spamScore.getSignalSummary());
        }

        List<MailAttachment> flatAttachments = List.of();
        if (attachmentData != null && !attachmentData.isBlank()) {
            if (!MailboxPermissions.INSTANCE.hasPermission(sender, MailboxPermissions.Permission.SEND_ATTACHMENTS)) {
                return CompletableFuture.completedFuture(MailboxManager.SendResult.error("You don't have permission to send attachments"));
            }
            List<MailAttachment> parsed = MailAttachment.parseAttachments(attachmentData);
            if (parsed.isEmpty()) {
                return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Invalid attachment data"));
            }

            List<MailAttachment> flat = MailAttachment.flattenAttachments(parsed);
            if (flat.size() > config.getMaxAttachmentsPerMessage()) {
                return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Too many attachments"));
            }

            for (MailAttachment attachment : flat) {
                if (attachment == null || !attachment.canClaim(sender)) {
                    return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Invalid attachment data"));
                }
                if (attachment instanceof ItemAttachment && !config.isItemAttachmentsEnabled()) {
                    return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Item attachments are disabled"));
                }
                if (attachment instanceof CurrencyAttachment && !config.isCurrencyAttachmentsEnabled()) {
                    return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Currency attachments are disabled"));
                }
            }
            flatAttachments = flat;
        }

        String finalSubject = filterDecision.subject();
        String finalBody = filterDecision.body();
        List<MailAttachment> attachmentsForReserve = flatAttachments;

        return repo.getMessageCount(recipientUuid).thenCompose(count -> {
            if (count >= config.getMaxMessagesPerPlayer()) {
                return CompletableFuture.completedFuture(MailboxManager.SendResult.error("Recipient's inbox is full"));
            }

            CompletableFuture<MailboxAttachmentHandler.AttachmentReservation> reservationFuture = attachmentsForReserve.isEmpty()
                ? CompletableFuture.completedFuture(MailboxAttachmentHandler.AttachmentReservation.empty())
                : attachmentHandler.reserveAttachmentsForSend(sender, attachmentsForReserve);

            return reservationFuture.thenCompose(reservation -> {
                if (!reservation.success()) {
                    String reason = reservation.error() != null ? reservation.error() : "Failed to reserve attachments";
                    return CompletableFuture.completedFuture(MailboxManager.SendResult.error(reason));
                }

                String sealedAttachmentData = MailboxAttachmentHandler.buildAttachmentDataFromReservation(reservation);
                Instant now = Instant.now();
                Instant expiresAt = now.plus(config.getDefaultMessageTtl());
                UUID messageId = UUID.randomUUID();

                MailboxDeliveryJob job = MailboxDeliveryJob.builder()
                    .messageId(messageId)
                    .sender(senderUuid, sender.getName().getString())
                    .recipient(recipientUuid)
                    .subject(finalSubject)
                    .body(finalBody)
                    .messageType(MessageType.PLAYER)
                    .createdAt(now)
                    .availableAt(now)
                    .expiresAt(expiresAt)
                    .attachment(sealedAttachmentData)
                    .status(MailboxDeliveryJob.DeliveryStatus.PENDING)
                    .build();

                MailboxMessage message = MailboxMessage.builder()
                    .id(messageId)
                    .sender(senderUuid, sender.getName().getString())
                    .recipient(recipientUuid)
                    .subject(finalSubject)
                    .body(finalBody)
                    .messageType(MessageType.PLAYER)
                    .createdAt(now)
                    .expiresAt(expiresAt)
                    .attachment(sealedAttachmentData)
                    .build();

                return queueAndDeliverMessage(job, message, repo, initialized, callback).thenApply(outcome -> {
                    rateLimiter.updateRateLimit(senderUuid, recipientUuid);
                    if (outcome.delivered()) {
                        LOGGER.debug("[Mailbox] Player {} sent message to {}", senderUuid, recipientUuid);
                    } else {
                        LOGGER.info("[Mailbox] Queued message {} for delivery to {}", messageId, recipientUuid);
                    }

                    PlayerReputation.INSTANCE.recordSuccessfulMessage(senderUuid);

                    if (filterDecision.flagged()) {
                        logFlaggedMessage(
                            senderUuid,
                            sender.getName().getString(),
                            recipientUuid,
                            messageId,
                            MessageType.PLAYER,
                            filterDecision.reason(),
                            finalSubject
                        );
                    }

                    WebhookManager.INSTANCE.dispatchMessageSent(
                        messageId,
                        senderUuid,
                        sender.getName().getString(),
                        recipientUuid,
                        finalSubject,
                        sealedAttachmentData != null && !sealedAttachmentData.isBlank()
                    );

                    return MailboxManager.SendResult.success(messageId);
                }).exceptionally(e -> {
                    LOGGER.error("[Mailbox] Failed to queue message", e);
                    attachmentHandler.refundReservation(sender, reservation);
                    return MailboxManager.SendResult.error("Failed to queue message");
                });
            });
        }).exceptionally(e -> {
            LOGGER.error("[Mailbox] Failed to send message", e);
            return MailboxManager.SendResult.error("Failed to send message");
        });
    }

    /**
     * Send a system message to a player.
     */
    public CompletableFuture<UUID> sendSystemMessage(
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            @Nullable Duration expiresIn,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
        return sendSystemMessage(recipientUuid, subject, body, attachmentData, expiresIn, true, true, repo, initialized, callback);
    }

    CompletableFuture<UUID> sendSystemMessage(
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            @Nullable Duration expiresIn,
            boolean applyFilter,
            boolean validateAttachments,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
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
            MailboxAttachmentHandler.AttachmentValidation validation = attachmentHandler.validateAttachmentData(attachmentData);
            if (!validation.isAllowed()) {
                String reason = validation.error() != null ? validation.error() : "Invalid attachment data";
                return CompletableFuture.failedFuture(new IllegalArgumentException(reason));
            }
        }

        String sealedAttachmentData = MailboxAttachmentHandler.canonicalizeAttachmentData(attachmentData);
        Instant now = Instant.now();
        Instant expiresAt = expiresIn != null
            ? now.plus(expiresIn)
            : now.plus(MailboxConfig.INSTANCE.getDefaultMessageTtl());

        UUID messageId = UUID.randomUUID();

        MailboxDeliveryJob job = MailboxDeliveryJob.builder()
            .messageId(messageId)
            .sender(null, "System")
            .recipient(recipientUuid)
            .subject(filterDecision.subject())
            .body(filterDecision.body())
            .messageType(MessageType.SYSTEM)
            .createdAt(now)
            .availableAt(now)
            .expiresAt(expiresAt)
            .attachment(sealedAttachmentData)
            .status(MailboxDeliveryJob.DeliveryStatus.PENDING)
            .build();

        MailboxMessage message = MailboxMessage.builder()
            .id(messageId)
            .sender(null, "System")
            .recipient(recipientUuid)
            .subject(filterDecision.subject())
            .body(filterDecision.body())
            .messageType(MessageType.SYSTEM)
            .createdAt(now)
            .expiresAt(expiresAt)
            .attachment(sealedAttachmentData)
            .build();

        return queueAndDeliverMessage(job, message, repo, initialized, callback).thenApply(outcome -> {
            if (outcome.delivered()) {
                LOGGER.debug("[Mailbox] System message sent to {}: {}", recipientUuid, message.subject());
            } else {
                LOGGER.info("[Mailbox] Queued system message {} for {}", messageId, recipientUuid);
            }

            if (filterDecision.flagged()) {
                logFlaggedMessage(
                    null,
                    "System",
                    recipientUuid,
                    messageId,
                    MessageType.SYSTEM,
                    filterDecision.reason(),
                    filterDecision.subject()
                );
            }
            return messageId;
        });
    }

    /**
     * Send an admin message to a player.
     */
    public CompletableFuture<UUID> sendAdminMessage(
            String adminName,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
        return sendAdminMessage(adminName, recipientUuid, subject, body, attachmentData, null, true, true, repo, initialized, callback);
    }

    public CompletableFuture<UUID> sendAdminMessage(
            String adminName,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            @Nullable Instant expiresAt,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
        return sendAdminMessage(adminName, recipientUuid, subject, body, attachmentData, expiresAt, true, true, repo, initialized, callback);
    }

    CompletableFuture<UUID> sendAdminMessage(
            String adminName,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            @Nullable Instant expiresAt,
            boolean applyFilter,
            boolean validateAttachments,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
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
            MailboxAttachmentHandler.AttachmentValidation validation = attachmentHandler.validateAttachmentData(attachmentData);
            if (!validation.isAllowed()) {
                String reason = validation.error() != null ? validation.error() : "Invalid attachment data";
                return CompletableFuture.failedFuture(new IllegalArgumentException(reason));
            }
        }

        String sealedAttachmentData = MailboxAttachmentHandler.canonicalizeAttachmentData(attachmentData);
        Instant now = Instant.now();
        Instant resolvedExpiresAt;
        if (expiresAt != null) {
            resolvedExpiresAt = expiresAt.isBefore(now) ? now : expiresAt;
        } else {
            resolvedExpiresAt = now.plus(MailboxConfig.INSTANCE.getDefaultMessageTtl());
        }

        UUID messageId = UUID.randomUUID();

        MailboxDeliveryJob job = MailboxDeliveryJob.builder()
            .messageId(messageId)
            .sender(null, adminName)
            .recipient(recipientUuid)
            .subject(filterDecision.subject())
            .body(filterDecision.body())
            .messageType(MessageType.ADMIN)
            .createdAt(now)
            .availableAt(now)
            .expiresAt(resolvedExpiresAt)
            .attachment(sealedAttachmentData)
            .status(MailboxDeliveryJob.DeliveryStatus.PENDING)
            .build();

        MailboxMessage message = MailboxMessage.builder()
            .id(messageId)
            .sender(null, adminName)
            .recipient(recipientUuid)
            .subject(filterDecision.subject())
            .body(filterDecision.body())
            .messageType(MessageType.ADMIN)
            .createdAt(now)
            .expiresAt(resolvedExpiresAt)
            .attachment(sealedAttachmentData)
            .build();

        return queueAndDeliverMessage(job, message, repo, initialized, callback).thenApply(outcome -> {
            if (outcome.delivered()) {
                LOGGER.debug("[Mailbox] Admin message from {} sent to {}: {}", adminName, recipientUuid, message.subject());
            } else {
                LOGGER.info("[Mailbox] Queued admin message {} for {}", messageId, recipientUuid);
            }

            if (filterDecision.flagged()) {
                logFlaggedMessage(
                    null,
                    adminName,
                    recipientUuid,
                    messageId,
                    MessageType.ADMIN,
                    filterDecision.reason(),
                    filterDecision.subject()
                );
            }
            return messageId;
        });
    }

    CompletableFuture<UUID> sendTypedMessage(
            String senderName,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            MessageType type,
            @Nullable Instant expiresAt,
            boolean applyFilter,
            boolean validateAttachments,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
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
            return sendSystemMessage(recipientUuid, subject, body, attachmentData, expiresIn, applyFilter, validateAttachments, repo, initialized, callback);
        }
        return sendAdminMessage(senderName, recipientUuid, subject, body, attachmentData, expiresAt, applyFilter, validateAttachments, repo, initialized, callback);
    }

    /**
     * Send a broadcast message to a list of players.
     */
    public CompletableFuture<Integer> sendBroadcast(
            String senderName,
            List<UUID> playerUuids,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            MessageType type,
            @Nullable Instant expiresAt,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
        if (!initialized || repo == null) {
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

        MailboxAttachmentHandler.AttachmentValidation attachmentValidation = attachmentHandler.validateAttachmentData(attachmentData);
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
                    expiresAt,
                    repo,
                    initialized,
                    callback
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
            @Nullable Instant expiresAt,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
        List<CompletableFuture<Integer>> futures = playerUuids.stream()
            .map(uuid -> sendTypedMessage(senderName, uuid, subject, body, attachmentData, type, expiresAt, false, false, repo, initialized, callback)
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

    // ========================================================================
    // DELIVERY PIPELINE
    // ========================================================================

    record DeliveryOutcome(MailboxMessage message, boolean delivered) {}

    CompletableFuture<DeliveryOutcome> queueAndDeliverMessage(
            MailboxDeliveryJob job,
            MailboxMessage message,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
        if (!initialized || repo == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Mailbox not initialized"));
        }

        return repo.saveDeliveryJob(job).thenCompose(savedJob -> {
            if (!MailboxConfig.INSTANCE.isDeliveryImmediateDispatchEnabled()) {
                return CompletableFuture.completedFuture(new DeliveryOutcome(message, false));
            }

            Instant attemptAt = Instant.now();
            return deliverDeliveryJob(savedJob, repo, initialized, callback)
                .thenCompose(deliveredMessage -> {
                    MailboxDeliveryJob delivered = savedJob.toBuilder()
                        .status(MailboxDeliveryJob.DeliveryStatus.DELIVERED)
                        .attemptCount(savedJob.attemptCount() + 1)
                        .lastAttemptAt(attemptAt)
                        .deliveredAt(attemptAt)
                        .lastFailureAt(null)
                        .lastFailureReason(null)
                        .build();
                    return repo.updateDeliveryJob(delivered)
                        .handle((updated, updateError) -> {
                            if (updateError != null || !Boolean.TRUE.equals(updated)) {
                                LOGGER.warn(
                                    "[Mailbox] Failed to update delivery job {} after delivery",
                                    savedJob.id(),
                                    updateError
                                );
                            }
                            return new DeliveryOutcome(deliveredMessage, true);
                        });
                })
                .exceptionallyCompose(error -> {
                    Throwable cause = unwrapCompletionError(error);
                    String reason = buildFailureReason(cause);
                    return handleImmediateDeliveryFailure(savedJob, attemptAt, reason, repo, initialized, callback)
                        .thenApply(updatedJob -> new DeliveryOutcome(message, false));
                });
        });
    }

    public CompletableFuture<MailboxMessage> deliverDeliveryJob(
            MailboxDeliveryJob job,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
        if (!initialized || repo == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Mailbox not initialized"));
        }

        MailboxMessage message = MailboxMessage.builder()
            .id(job.messageId())
            .sender(job.senderUuid(), job.senderName())
            .recipient(job.recipientUuid())
            .subject(job.subject())
            .body(job.body())
            .messageType(job.messageType())
            .createdAt(job.createdAt())
            .expiresAt(job.expiresAt())
            .attachment(job.attachmentData())
            .build();

        return repo.saveMessage(message)
            .thenApply(saved -> {
                notifyNewMessage(saved.recipientUuid(), saved, callback);
                return saved;
            })
            .exceptionallyCompose(error -> {
                Throwable cause = unwrapCompletionError(error);
                return repo.getMessage(job.messageId())
                    .thenCompose(existing -> existing
                        .<CompletableFuture<MailboxMessage>>map(CompletableFuture::completedFuture)
                        .orElseGet(() -> CompletableFuture.failedFuture(cause)));
            });
    }

    public CompletableFuture<UUID> sendDeliveryRecall(
            MailboxDeliveryJob job,
            String reason,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
        UUID senderUuid = job.senderUuid();
        if (senderUuid == null) {
            return CompletableFuture.completedFuture(job.messageId());
        }

        String subject = "Delivery failed: " + truncate(job.subject(), 64);
        String body = "Your message could not be delivered.\n"
            + "Recipient: " + job.recipientUuid() + "\n"
            + "Reason: " + reason;

        return sendSystemMessage(
            senderUuid,
            subject,
            body,
            job.attachmentData(),
            null,
            false,
            false,
            repo,
            initialized,
            callback
        ).exceptionally(error -> {
            LOGGER.warn("[Mailbox] Failed to send delivery recall for {}", job.id(), error);
            return job.messageId();
        });
    }

    private CompletableFuture<MailboxDeliveryJob> handleImmediateDeliveryFailure(
            MailboxDeliveryJob job,
            Instant attemptAt,
            String reason,
            MailboxRepository repo,
            boolean initialized,
            MailboxManager.NewMessageCallback callback
    ) {
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(job);
        }

        if (job.expiresAt() != null && attemptAt.isAfter(job.expiresAt())) {
            String expiryReason = "Message expired before delivery";
            MailboxDeliveryJob cancelled = job.toBuilder()
                .status(MailboxDeliveryJob.DeliveryStatus.CANCELLED)
                .attemptCount(job.attemptCount() + 1)
                .failureCount(job.failureCount() + 1)
                .lastAttemptAt(attemptAt)
                .lastFailureAt(attemptAt)
                .lastFailureReason(expiryReason)
                .build();

            return repo.updateDeliveryJob(cancelled)
                .handle((updated, updateError) -> {
                    if (updateError != null || !Boolean.TRUE.equals(updated)) {
                        LOGGER.warn(
                            "[Mailbox] Failed to update delivery job {} after expiry",
                            job.id(),
                            updateError
                        );
                    }
                    return cancelled;
                })
                .thenCompose(updatedJob -> {
                    if (MailboxConfig.INSTANCE.isDeliveryRecallEnabled()) {
                        return sendDeliveryRecall(updatedJob, expiryReason, repo, initialized, callback)
                            .thenApply(messageId -> updatedJob);
                    }
                    return CompletableFuture.completedFuture(updatedJob);
                });
        }

        int nextFailureCount = job.failureCount() + 1;
        int maxAttempts = MailboxConfig.INSTANCE.getDeliveryMaxAttempts();
        boolean isFinalFailure = nextFailureCount >= maxAttempts;

        MailboxDeliveryJob.Builder builder = job.toBuilder()
            .attemptCount(job.attemptCount() + 1)
            .failureCount(nextFailureCount)
            .lastAttemptAt(attemptAt)
            .lastFailureAt(attemptAt)
            .lastFailureReason(reason);

        if (isFinalFailure) {
            builder.status(MailboxDeliveryJob.DeliveryStatus.FAILED);
        } else {
            Instant nextAttemptAt = attemptAt.plusSeconds(computeRetryDelaySeconds(nextFailureCount));
            builder.status(MailboxDeliveryJob.DeliveryStatus.PENDING)
                .availableAt(nextAttemptAt);
        }

        MailboxDeliveryJob updated = builder.build();
        return repo.updateDeliveryJob(updated)
            .handle((persisted, updateError) -> {
                if (updateError != null || !Boolean.TRUE.equals(persisted)) {
                    LOGGER.warn(
                        "[Mailbox] Failed to update delivery job {} after failure",
                        job.id(),
                        updateError
                    );
                }
                return updated;
            })
            .thenCompose(updatedJob -> {
                if (isFinalFailure && MailboxConfig.INSTANCE.isDeliveryRecallEnabled()) {
                    return sendDeliveryRecall(updatedJob, reason, repo, initialized, callback)
                        .thenApply(messageId -> updatedJob);
                }
                return CompletableFuture.completedFuture(updatedJob);
            });
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    private void notifyNewMessage(UUID recipientUuid, MailboxMessage message, MailboxManager.NewMessageCallback callback) {
        if (callback != null) {
            try {
                callback.onNewMessage(recipientUuid, message);
            } catch (Exception e) {
                LOGGER.error("[Mailbox] Error in new message callback", e);
            }
        }
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

    static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    public static String buildFailureReason(Throwable error) {
        String reason = error.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = error.getClass().getSimpleName();
        }
        String truncated = truncate(reason, 200);
        return truncated.isBlank() ? error.getClass().getSimpleName() : truncated;
    }

    public static int computeRetryDelaySeconds(int failureCount) {
        MailboxConfig config = MailboxConfig.INSTANCE;
        int baseDelay = Math.max(1, config.getDeliveryRetryDelaySeconds());
        int maxDelay = Math.max(baseDelay, config.getDeliveryRetryMaxDelaySeconds());
        double multiplier = Math.max(1.0, config.getDeliveryRetryBackoffMultiplier());
        int attemptIndex = Math.max(1, failureCount);

        double delay = baseDelay * Math.pow(multiplier, attemptIndex - 1);
        delay = Math.min(delay, maxDelay);

        double jitterRatio = Math.max(0.0, Math.min(0.5, config.getDeliveryRetryJitterRatio()));
        if (jitterRatio > 0.0) {
            double jitter = ThreadLocalRandom.current().nextDouble(-jitterRatio, jitterRatio);
            delay = delay * (1.0 + jitter);
        }

        int rounded = (int) Math.round(delay);
        if (rounded < 1) {
            rounded = 1;
        }
        if (rounded > maxDelay) {
            rounded = maxDelay;
        }
        return rounded;
    }

    static Throwable unwrapCompletionError(Throwable error) {
        if (error instanceof CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return error;
    }
}
