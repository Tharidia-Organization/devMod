package com.devmod.mailbox.attachment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P1: Attachment content validation and size caps.
 *
 * Validates mailbox attachments for:
 * - Allowed item types (whitelist/blacklist)
 * - Item stack size limits
 * - Total attachment count limits
 * - Currency amount caps
 * - JSON payload size limits
 */
public final class AttachmentValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentValidator.class);

    public static final AttachmentValidator INSTANCE = new AttachmentValidator();

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /** Maximum number of distinct attachments per message. */
    private volatile int maxAttachmentsPerMessage = 5;

    /** Maximum total items (stacks) across all attachments. */
    private volatile int maxTotalItemStacks = 10;

    /** Maximum stack size per item (0 = use item's max stack size). */
    private volatile int maxItemStackSize = 0;

    /** Maximum currency amount per attachment. */
    private volatile long maxCurrencyAmount = 1_000_000L;

    /** Maximum JSON payload size for attachment data (bytes). */
    private volatile int maxJsonPayloadBytes = 8192;

    /** Whether to use item blacklist (true) or whitelist (false). */
    private volatile boolean useBlacklist = true;

    /** Item blacklist - items that cannot be attached. */
    private final Set<String> itemBlacklist = new HashSet<>(Set.of(
        "minecraft:bedrock",
        "minecraft:command_block",
        "minecraft:command_block_minecart",
        "minecraft:chain_command_block",
        "minecraft:repeating_command_block",
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:jigsaw",
        "minecraft:barrier",
        "minecraft:light",
        "minecraft:debug_stick",
        "minecraft:spawner"
    ));

    /** Item whitelist - only these items can be attached (if useBlacklist = false). */
    private final Set<String> itemWhitelist = new HashSet<>();

    /** Whether validation is enabled. */
    private volatile boolean enabled = true;

    // ========================================================================
    // METRICS
    // ========================================================================

    private final AtomicLong validationsPerformed = new AtomicLong(0);
    private final AtomicLong validationsFailed = new AtomicLong(0);
    private final AtomicLong itemsBlocked = new AtomicLong(0);
    private final AtomicLong oversizedPayloadsBlocked = new AtomicLong(0);

    private AttachmentValidator() {}

    // ========================================================================
    // VALIDATION API
    // ========================================================================

    /**
     * Validate an attachment JSON payload before storage.
     *
     * @param jsonPayload The raw JSON string of attachment data
     * @return Validation result
     */
    public ValidationResult validatePayload(@Nullable String jsonPayload) {
        validationsPerformed.incrementAndGet();

        if (!enabled) {
            return ValidationResult.success();
        }

        // Empty payload is valid (no attachment)
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return ValidationResult.success();
        }

        // Check JSON payload size
        int payloadBytes = jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (payloadBytes > maxJsonPayloadBytes) {
            oversizedPayloadsBlocked.incrementAndGet();
            validationsFailed.incrementAndGet();
            LOGGER.warn("[AttachmentValidator] Payload too large: {} bytes (max: {})",
                payloadBytes, maxJsonPayloadBytes);
            return ValidationResult.failure("Attachment data too large", ValidationError.PAYLOAD_TOO_LARGE);
        }

        // Parse and validate attachments
        List<MailAttachment> attachments = MailAttachment.parseAttachments(jsonPayload);
        return validateAttachments(attachments);
    }

    /**
     * Validate a list of parsed attachments.
     *
     * @param attachments The list of attachments
     * @return Validation result
     */
    public ValidationResult validateAttachments(List<MailAttachment> attachments) {
        if (!enabled) {
            return ValidationResult.success();
        }

        if (attachments == null || attachments.isEmpty()) {
            return ValidationResult.success();
        }

        // Flatten composite attachments
        List<MailAttachment> flat = MailAttachment.flattenAttachments(attachments);

        // Check attachment count
        if (flat.size() > maxAttachmentsPerMessage) {
            validationsFailed.incrementAndGet();
            return ValidationResult.failure(
                String.format("Too many attachments: %d (max: %d)", flat.size(), maxAttachmentsPerMessage),
                ValidationError.TOO_MANY_ATTACHMENTS
            );
        }

        // Validate each attachment
        int totalItemStacks = 0;
        for (MailAttachment attachment : flat) {
            ValidationResult result = validateSingleAttachment(attachment);
            if (!result.isValid()) {
                validationsFailed.incrementAndGet();
                return result;
            }

            if (attachment instanceof ItemAttachment) {
                totalItemStacks++;
            }
        }

        // Check total item stacks
        if (totalItemStacks > maxTotalItemStacks) {
            validationsFailed.incrementAndGet();
            return ValidationResult.failure(
                String.format("Too many item stacks: %d (max: %d)", totalItemStacks, maxTotalItemStacks),
                ValidationError.TOO_MANY_ITEMS
            );
        }

        return ValidationResult.success();
    }

    /**
     * Validate a single attachment.
     */
    private ValidationResult validateSingleAttachment(MailAttachment attachment) {
        if (attachment instanceof ItemAttachment itemAttachment) {
            return validateItemAttachment(itemAttachment);
        } else if (attachment instanceof CurrencyAttachment currencyAttachment) {
            return validateCurrencyAttachment(currencyAttachment);
        } else if (attachment instanceof CompositeAttachment) {
            // Already flattened, shouldn't reach here
            return ValidationResult.success();
        }

        // Unknown attachment type
        return ValidationResult.failure("Unknown attachment type: " + attachment.getType(),
            ValidationError.INVALID_TYPE);
    }

    /**
     * Validate an item attachment.
     */
    private ValidationResult validateItemAttachment(ItemAttachment attachment) {
        String itemId = attachment.itemId().toString();

        // Check blacklist/whitelist
        if (!isItemAllowed(itemId)) {
            itemsBlocked.incrementAndGet();
            LOGGER.info("[AttachmentValidator] Blocked item: {}", itemId);
            return ValidationResult.failure("Item not allowed: " + itemId, ValidationError.ITEM_BLOCKED);
        }

        // Check stack size
        int count = attachment.count();
        if (maxItemStackSize > 0 && count > maxItemStackSize) {
            return ValidationResult.failure(
                String.format("Stack size too large: %d (max: %d)", count, maxItemStackSize),
                ValidationError.STACK_TOO_LARGE
            );
        }

        return ValidationResult.success();
    }

    /**
     * Validate a currency attachment.
     */
    private ValidationResult validateCurrencyAttachment(CurrencyAttachment attachment) {
        int amount = attachment.amount();

        if (amount <= 0) {
            return ValidationResult.failure("Invalid currency amount: " + amount,
                ValidationError.INVALID_AMOUNT);
        }

        if (amount > maxCurrencyAmount) {
            return ValidationResult.failure(
                String.format("Currency amount too large: %d (max: %d)", amount, maxCurrencyAmount),
                ValidationError.AMOUNT_TOO_LARGE
            );
        }

        return ValidationResult.success();
    }

    /**
     * Check if an item is allowed based on blacklist/whitelist.
     */
    public boolean isItemAllowed(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }

        // Normalize item ID
        String normalized = itemId.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }

        if (useBlacklist) {
            return !itemBlacklist.contains(normalized);
        } else {
            return itemWhitelist.isEmpty() || itemWhitelist.contains(normalized);
        }
    }

    // ========================================================================
    // CONFIGURATION API
    // ========================================================================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setMaxAttachmentsPerMessage(int max) {
        this.maxAttachmentsPerMessage = Math.max(1, max);
    }

    public int getMaxAttachmentsPerMessage() {
        return maxAttachmentsPerMessage;
    }

    public void setMaxTotalItemStacks(int max) {
        this.maxTotalItemStacks = Math.max(1, max);
    }

    public int getMaxTotalItemStacks() {
        return maxTotalItemStacks;
    }

    public void setMaxItemStackSize(int max) {
        this.maxItemStackSize = Math.max(0, max);
    }

    public void setMaxCurrencyAmount(long max) {
        this.maxCurrencyAmount = Math.max(1, max);
    }

    public long getMaxCurrencyAmount() {
        return maxCurrencyAmount;
    }

    public void setMaxJsonPayloadBytes(int max) {
        this.maxJsonPayloadBytes = Math.max(1, max);
    }

    public int getMaxJsonPayloadBytes() {
        return maxJsonPayloadBytes;
    }

    public void setUseBlacklist(boolean useBlacklist) {
        this.useBlacklist = useBlacklist;
    }

    public void addToBlacklist(String itemId) {
        if (itemId != null && !itemId.isBlank()) {
            itemBlacklist.add(itemId.toLowerCase(java.util.Locale.ROOT));
        }
    }

    public void removeFromBlacklist(String itemId) {
        if (itemId != null) {
            itemBlacklist.remove(itemId.toLowerCase(java.util.Locale.ROOT));
        }
    }

    public void addToWhitelist(String itemId) {
        if (itemId != null && !itemId.isBlank()) {
            itemWhitelist.add(itemId.toLowerCase(java.util.Locale.ROOT));
        }
    }

    public Set<String> getBlacklist() {
        return Set.copyOf(itemBlacklist);
    }

    public Set<String> getWhitelist() {
        return Set.copyOf(itemWhitelist);
    }

    // ========================================================================
    // METRICS API
    // ========================================================================

    public long getValidationsPerformed() {
        return validationsPerformed.get();
    }

    public long getValidationsFailed() {
        return validationsFailed.get();
    }

    public long getItemsBlocked() {
        return itemsBlocked.get();
    }

    public long getOversizedPayloadsBlocked() {
        return oversizedPayloadsBlocked.get();
    }

    public ValidationMetrics getMetrics() {
        return new ValidationMetrics(
            validationsPerformed.get(),
            validationsFailed.get(),
            itemsBlocked.get(),
            oversizedPayloadsBlocked.get()
        );
    }

    // ========================================================================
    // RESULT TYPES
    // ========================================================================

    /**
     * Validation error codes.
     */
    public enum ValidationError {
        PAYLOAD_TOO_LARGE,
        TOO_MANY_ATTACHMENTS,
        TOO_MANY_ITEMS,
        ITEM_BLOCKED,
        STACK_TOO_LARGE,
        INVALID_TYPE,
        INVALID_AMOUNT,
        AMOUNT_TOO_LARGE,
        PARSE_ERROR
    }

    /**
     * Validation result.
     */
    public record ValidationResult(
        boolean isValid,
        @Nullable String message,
        @Nullable ValidationError error
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult failure(String message, ValidationError error) {
            return new ValidationResult(false, message, error);
        }
    }

    /**
     * Metrics snapshot.
     */
    public record ValidationMetrics(
        long validationsPerformed,
        long validationsFailed,
        long itemsBlocked,
        long oversizedPayloadsBlocked
    ) {}
}
