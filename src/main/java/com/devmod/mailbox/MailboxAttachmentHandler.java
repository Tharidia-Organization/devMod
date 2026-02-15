package com.devmod.mailbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.devmod.endurance.RewardSystem;
import com.devmod.mailbox.attachment.AttachmentTransactionLog;
import com.devmod.mailbox.attachment.AttachmentValidator;
import com.devmod.mailbox.attachment.CurrencyAttachment;
import com.devmod.mailbox.attachment.ItemAttachment;
import com.devmod.mailbox.attachment.MailAttachment;
import com.devmod.mailbox.persistence.MailboxRepository;
import com.devmod.mailbox.webhook.WebhookManager;
import com.devmod.mailbox.moderation.PlayerReputation;

/**
 * Handles attachment operations: claiming, reserving, validating, and inventory management.
 */
public class MailboxAttachmentHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxAttachmentHandler.class);

    /* In-flight claim operations per message */
    private final Map<UUID, CompletableFuture<MailboxManager.ClaimOutcome>> claimInFlight = new ConcurrentHashMap<>();

    /* In-flight send operations per sender to guard attachment deductions */
    private final Map<UUID, Object> attachmentSendLocks = new ConcurrentHashMap<>();

    /**
     * Clear in-flight claim state. Called during shutdown.
     */
    public void clearInFlight() {
        claimInFlight.clear();
    }

    // ========================================================================
    // ATTACHMENT VALIDATION
    // ========================================================================

    record AttachmentValidation(boolean isAllowed, @Nullable String error) {
        static AttachmentValidation success() {
            return new AttachmentValidation(true, null);
        }

        static AttachmentValidation blocked(String error) {
            return new AttachmentValidation(false, error);
        }
    }

    AttachmentValidation validateAttachmentData(@Nullable String attachmentData) {
        if (attachmentData == null || attachmentData.isBlank()) {
            return AttachmentValidation.success();
        }

        AttachmentValidator.ValidationResult validatorResult =
            AttachmentValidator.INSTANCE.validatePayload(attachmentData);
        if (!validatorResult.isValid()) {
            String message = validatorResult.message() != null
                ? validatorResult.message()
                : "Invalid attachment data";
            return AttachmentValidation.blocked(message);
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

    @Nullable
    static String canonicalizeAttachmentData(@Nullable String attachmentData) {
        if (attachmentData == null || attachmentData.isBlank()) {
            return null;
        }
        List<MailAttachment> parsed = MailAttachment.parseAttachments(attachmentData);
        if (parsed.isEmpty()) {
            return null;
        }
        return MailAttachment.toJsonPayload(parsed);
    }

    @Nullable
    static String buildAttachmentDataFromReservation(AttachmentReservation reservation) {
        if (reservation == null || !reservation.success()) {
            return null;
        }
        List<MailAttachment> attachments = new ArrayList<>();

        if (!reservation.items().isEmpty()) {
            List<Map.Entry<ResourceLocation, Integer>> entries = new ArrayList<>(reservation.items().entrySet());
            entries.sort(java.util.Comparator.comparing(e -> e.getKey().toString()));
            for (Map.Entry<ResourceLocation, Integer> entry : entries) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                    attachments.add(new ItemAttachment(entry.getKey(), entry.getValue(), null));
                }
            }
        }

        if (!reservation.currencies().isEmpty()) {
            List<Map.Entry<RewardSystem.Currency, Integer>> entries =
                new ArrayList<>(reservation.currencies().entrySet());
            entries.sort(java.util.Comparator.comparing(e -> e.getKey().name()));
            for (Map.Entry<RewardSystem.Currency, Integer> entry : entries) {
                String currencyType = CurrencyAttachment.toCurrencyType(entry.getKey());
                Integer amount = entry.getValue();
                if (currencyType != null && amount != null && amount > 0) {
                    attachments.add(new CurrencyAttachment(currencyType, amount));
                }
            }
        }

        return MailAttachment.toJsonPayload(attachments);
    }

    // ========================================================================
    // ATTACHMENT CLAIMS
    // ========================================================================

    /**
     * Claim attachments for a message with an in-flight guard to prevent duplicates.
     */
    public CompletableFuture<MailboxManager.ClaimOutcome> claimAttachments(
            ServerPlayer player,
            MailboxMessage message,
            MailboxRepository repo,
            boolean initialized
    ) {
        if (!message.recipientUuid().equals(player.getUUID()) || message.deleted()) {
            return CompletableFuture.completedFuture(MailboxManager.ClaimOutcome.failure("Access denied"));
        }
        if (!message.canClaimAttachment()) {
            return CompletableFuture.completedFuture(MailboxManager.ClaimOutcome.failure("No attachment to claim"));
        }
        if (MailboxConfig.INSTANCE.isMaintenanceMode()
                && !MailboxPermissions.INSTANCE.isAdmin(player.getUUID(), player)) {
            return CompletableFuture.completedFuture(MailboxManager.ClaimOutcome.failure("Mailbox is in maintenance mode"));
        }

        UUID messageId = message.id();
        return claimInFlight.computeIfAbsent(messageId, id ->
            doClaimAttachments(player, message, repo, initialized)
                .whenComplete((result, error) -> claimInFlight.remove(id))
        );
    }

    private CompletableFuture<MailboxManager.ClaimOutcome> doClaimAttachments(
            ServerPlayer player,
            MailboxMessage message,
            MailboxRepository repo,
            boolean initialized
    ) {
        if (!initialized || repo == null) {
            return CompletableFuture.completedFuture(MailboxManager.ClaimOutcome.failure("Mailbox system not initialized"));
        }
        String attachmentData = message.attachmentData();
        if (attachmentData == null || attachmentData.isBlank()) {
            return CompletableFuture.completedFuture(MailboxManager.ClaimOutcome.failure("Attachment data missing"));
        }

        List<MailAttachment> parsed = MailAttachment.parseAttachments(attachmentData);
        List<MailAttachment> attachments = MailAttachment.flattenAttachments(parsed);
        if (attachments.isEmpty()) {
            return CompletableFuture.completedFuture(MailboxManager.ClaimOutcome.failure("Invalid attachment data"));
        }

        boolean canClaimAll = attachments.stream().allMatch(a -> a != null && a.canClaim(player));
        if (!canClaimAll) {
            return CompletableFuture.completedFuture(MailboxManager.ClaimOutcome.failure("Cannot claim attachments"));
        }

        return repo.startAttachmentClaim(message.id()).thenCompose(started -> {
            if (!started) {
                return CompletableFuture.completedFuture(
                    MailboxManager.ClaimOutcome.failure("Attachment already claimed or in progress")
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
                        PlayerReputation.INSTANCE.recordSuccessfulAttachment(player.getUUID());

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
                    return MailboxManager.ClaimOutcome.failure("Claim partially completed. Contact support.");
                }
                LOGGER.error("[Mailbox] Failed to finalize attachment claim {}", message.id());
                return MailboxManager.ClaimOutcome.failure("Claim completed but could not be finalized");
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
                return new ClaimAttempt(MailboxManager.ClaimOutcome.failure(messageText), finalizeClaim);
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
        return new ClaimAttempt(MailboxManager.ClaimOutcome.success(summary), true);
    }

    static String determineAttachmentType(List<MailAttachment> attachments) {
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

    private record ClaimAttempt(MailboxManager.ClaimOutcome outcome, boolean finalizeClaim) {}

    // ========================================================================
    // ATTACHMENT RESERVATION (P2P SEND)
    // ========================================================================

    CompletableFuture<AttachmentReservation> reserveAttachmentsForSend(
            ServerPlayer sender,
            List<MailAttachment> attachments
    ) {
        net.minecraft.server.MinecraftServer server = sender.server;
        CompletableFuture<AttachmentReservation> future = new CompletableFuture<>();
        Runnable task = () -> future.complete(reserveAttachmentsNow(sender, attachments));
        server.execute(task);
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
                    return AttachmentReservation.failure("Insufficient " + entry.getKey().getDisplayName());
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

    void refundReservation(ServerPlayer sender, AttachmentReservation reservation) {
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
        server.execute(task);
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

    // ========================================================================
    // HELPER TYPES
    // ========================================================================

    record AttachmentReservation(
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
}
