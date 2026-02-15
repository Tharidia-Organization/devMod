package com.devmod.mailbox;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.fml.loading.FMLPaths;

import com.devmod.mailbox.analytics.MailboxAnalyticsEngine;
import com.devmod.mailbox.api.ApiServerLauncher;
import com.devmod.mailbox.attachment.CurrencyAttachment;
import com.devmod.mailbox.attachment.ItemAttachment;
import com.devmod.mailbox.broadcast.BroadcastQueueWorker;
import com.devmod.mailbox.delivery.MailboxDeliveryJob;
import com.devmod.mailbox.delivery.MailboxDeliveryRuntime;
import com.devmod.mailbox.digest.DigestManager;
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
 * Delegates to extracted helpers:
 *  - MailboxRateLimiter      – per-player send rate tracking
 *  - MailboxMessageSender    – content filtering, send ops, delivery pipeline
 *  - MailboxAttachmentHandler – attachment validation, claiming, reservations
 */
public class MailboxManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxManager.class);

    // ========================================================================
    // SINGLETON
    // ========================================================================

    public static final MailboxManager INSTANCE = new MailboxManager();

    private MailboxManager() {}

    // ========================================================================
    // DELEGATES
    // ========================================================================

    private final MailboxRateLimiter rateLimiter = new MailboxRateLimiter();
    private final MailboxAttachmentHandler attachmentHandler = new MailboxAttachmentHandler();
    private final MailboxMessageSender sender = new MailboxMessageSender(rateLimiter, attachmentHandler);

    // ========================================================================
    // STATE
    // ========================================================================

    private volatile boolean initialized = false;
    @Nullable
    private MailboxRepository repository;
    @Nullable
    private ScheduledExecutorService scheduler;
    @Nullable
    private ScheduledFuture<?> purgeExpiredTask;
    @Nullable
    private ScheduledFuture<?> rateLimitResetTask;

    @Nullable
    private NewMessageCallback newMessageCallback;

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

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
            NewsManager.getInstance().initialize(repo);
            com.devmod.mailbox.task.TestTaskManager.INSTANCE.initialize(repo);

            NewsPurgeJob.getInstance().initialize(repo);
            NewsPurgeJob.getInstance().start();

            BroadcastQueueWorker.INSTANCE.start();
            MailboxAnalyticsEngine.INSTANCE.start();
            MessageScheduler.INSTANCE.start();
            DigestManager.INSTANCE.start();
            WebhookManager.INSTANCE.start();
            MailboxDeliveryRuntime.INSTANCE.start();
            TicketManager.INSTANCE.initialize().join();

            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MailboxScheduler");
                t.setDaemon(true);
                return t;
            });
            scheduler = sched;

            purgeExpiredTask = sched.scheduleAtFixedRate(
                this::purgeExpiredMessages,
                1, 60, TimeUnit.MINUTES
            );

            rateLimitResetTask = sched.scheduleAtFixedRate(
                rateLimiter::resetMinuteBuckets,
                1, 1, TimeUnit.MINUTES
            );

            @SuppressWarnings("unused")
            var spamCleanupTask = sched.scheduleAtFixedRate(
                () -> com.devmod.mailbox.moderation.SpamDetector.INSTANCE.cleanup(),
                5, 5, TimeUnit.MINUTES
            );

            MailboxConfig config = MailboxConfig.INSTANCE;
            LOGGER.info("[Mailbox] API enabled: {}, port: {}", config.isApiEnabled(), config.getApiPort());
            if (config.isApiEnabled()) {
                LOGGER.info("[Mailbox] Starting API server...");
                boolean started = ApiServerLauncher.tryStart(FMLPaths.GAMEDIR.get(), config.getApiPort());
                LOGGER.info("[Mailbox] API server start result: {}", started);
            }

            initialized = true;
            LOGGER.info("[Mailbox] Mailbox system initialized");
        });
    }

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

        NewsManager.getInstance().shutdown();
        com.devmod.mailbox.task.TestTaskManager.INSTANCE.shutdown();
        com.devmod.mailbox.moderation.AdminAuditLog.INSTANCE.shutdown();
        NewsPurgeJob.getInstance().shutdown();
        BroadcastQueueWorker.INSTANCE.stop();
        MailboxAnalyticsEngine.INSTANCE.stop();
        MessageScheduler.INSTANCE.stop();
        DigestManager.INSTANCE.stop();
        WebhookManager.INSTANCE.stop();
        MailboxDeliveryRuntime.INSTANCE.stop();
        TicketManager.INSTANCE.shutdown().join();
        attachmentHandler.clearInFlight();
        ApiServerLauncher.stop();

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

    // ========================================================================
    // MESSAGE OPERATIONS (delegate to MailboxMessageSender)
    // ========================================================================

    public CompletableFuture<SendResult> sendPlayerMessage(
            ServerPlayer sender,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData
    ) {
        return this.sender.sendPlayerMessage(
            sender, recipientUuid, subject, body, attachmentData,
            repository, initialized, newMessageCallback
        );
    }

    public CompletableFuture<UUID> sendSystemMessage(
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            @Nullable Duration expiresIn
    ) {
        return sender.sendSystemMessage(
            recipientUuid, subject, body, attachmentData, expiresIn,
            repository, initialized, newMessageCallback
        );
    }

    public CompletableFuture<UUID> sendAdminMessage(
            String adminName,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData
    ) {
        return sender.sendAdminMessage(
            adminName, recipientUuid, subject, body, attachmentData,
            repository, initialized, newMessageCallback
        );
    }

    public CompletableFuture<UUID> sendAdminMessage(
            String adminName,
            UUID recipientUuid,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData,
            @Nullable Instant expiresAt
    ) {
        return sender.sendAdminMessage(
            adminName, recipientUuid, subject, body, attachmentData, expiresAt,
            repository, initialized, newMessageCallback
        );
    }

    public CompletableFuture<Integer> sendBroadcast(
            String adminName,
            List<UUID> playerUuids,
            String subject,
            @Nullable String body,
            @Nullable String attachmentData
    ) {
        return sendBroadcast(adminName, playerUuids, subject, body, attachmentData, MessageType.ADMIN);
    }

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
        return sender.sendBroadcast(
            senderName, playerUuids, subject, body, attachmentData, type, expiresAt,
            repository, initialized, newMessageCallback
        );
    }

    public CompletableFuture<Void> sendMessage(MailboxMessage message) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Mailbox not initialized"));
        }
        MailboxDeliveryJob job = MailboxDeliveryJob.builder()
            .messageId(message.id())
            .sender(message.senderUuid(), message.senderName())
            .recipient(message.recipientUuid())
            .subject(message.subject())
            .body(message.body())
            .messageType(message.messageType())
            .createdAt(message.createdAt())
            .availableAt(message.createdAt())
            .expiresAt(message.expiresAt())
            .attachment(message.attachmentData())
            .status(MailboxDeliveryJob.DeliveryStatus.PENDING)
            .build();

        return sender.queueAndDeliverMessage(job, message, repo, initialized, newMessageCallback)
            .thenAccept(outcome -> {
                if (!outcome.delivered()) {
                    LOGGER.info("[Mailbox] Queued message {} for {}", message.id(), message.recipientUuid());
                }
            });
    }

    // ========================================================================
    // READ / QUERY OPERATIONS
    // ========================================================================

    public CompletableFuture<List<MailboxMessage>> getMessages(UUID playerUuid) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repo.getMessagesForPlayer(playerUuid, false);
    }

    public CompletableFuture<Optional<MailboxMessage>> getMessage(UUID messageId) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return repo.getMessage(messageId);
    }

    public CompletableFuture<Integer> getUnreadCount(UUID playerUuid) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(0);
        }
        return repo.getUnreadCount(playerUuid);
    }

    public CompletableFuture<List<MailboxDeliveryJob>> getDeliveryJobsByStatus(
            MailboxDeliveryJob.DeliveryStatus status,
            int limit
    ) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repo.getDeliveryJobsByStatus(status, limit);
    }

    public CompletableFuture<Boolean> updateDeliveryJob(MailboxDeliveryJob job) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repo.updateDeliveryJob(job);
    }

    public CompletableFuture<MailboxMessage> deliverDeliveryJob(MailboxDeliveryJob job) {
        return sender.deliverDeliveryJob(job, repository, initialized, newMessageCallback);
    }

    public CompletableFuture<UUID> sendDeliveryRecall(MailboxDeliveryJob job, String reason) {
        return sender.sendDeliveryRecall(job, reason, repository, initialized, newMessageCallback);
    }

    // ========================================================================
    // MESSAGE ACTIONS
    // ========================================================================

    public CompletableFuture<Boolean> markAsRead(UUID messageId) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repo.markAsRead(messageId, Instant.now());
    }

    public CompletableFuture<Boolean> claimAttachment(UUID messageId) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repo.markAttachmentClaimed(messageId);
    }

    public CompletableFuture<ClaimOutcome> claimAttachments(ServerPlayer player, MailboxMessage message) {
        return attachmentHandler.claimAttachments(player, message, repository, initialized);
    }

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

    // ========================================================================
    // API / BROADCAST SUPPORT
    // ========================================================================

    public CompletableFuture<List<MailboxMessage>> getAllMessages(int limit, int offset) {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repo.getAllMessages(limit, offset);
    }

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

    public CompletableFuture<List<UUID>> getKnownUsers() {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repo.getKnownUsers();
    }

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

    public CompletableFuture<Integer> getTotalMessageCount() {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(0);
        }
        return repo.getTotalMessageCount();
    }

    public CompletableFuture<Integer> getTotalUnreadCount() {
        MailboxRepository repo = repository;
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(0);
        }
        return repo.getTotalUnreadCount();
    }

    // ========================================================================
    // STATIC UTILITY (used by MailboxDeliveryRuntime)
    // ========================================================================

    public static String buildFailureReason(Throwable error) {
        return MailboxMessageSender.buildFailureReason(error);
    }

    public static int computeRetryDelaySeconds(int failureCount) {
        return MailboxMessageSender.computeRetryDelaySeconds(failureCount);
    }

    // ========================================================================
    // CALLBACKS
    // ========================================================================

    public void setNewMessageCallback(@Nullable NewMessageCallback callback) {
        this.newMessageCallback = callback;
    }

    // ========================================================================
    // SCHEDULED TASKS
    // ========================================================================

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

    // ========================================================================
    // HELPER TYPES
    // ========================================================================

    public record SendResult(boolean success, @Nullable UUID messageId, @Nullable String error) {
        public static SendResult success(UUID messageId) {
            return new SendResult(true, messageId, null);
        }

        public static SendResult error(String error) {
            return new SendResult(false, null, error);
        }
    }

    public record ClaimOutcome(boolean success, String message) {
        public static ClaimOutcome success(String message) {
            return new ClaimOutcome(true, message);
        }

        public static ClaimOutcome failure(String message) {
            return new ClaimOutcome(false, message);
        }
    }

    @FunctionalInterface
    public interface NewMessageCallback {
        void onNewMessage(UUID recipientUuid, MailboxMessage message);
    }
}
