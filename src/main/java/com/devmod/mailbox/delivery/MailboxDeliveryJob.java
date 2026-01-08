package com.devmod.mailbox.delivery;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.devmod.mailbox.MailboxConfig;
import com.devmod.mailbox.MessageType;

/**
 * Persistent delivery job for mailbox messages.
 *
 * Tracks delivery attempts, failures, and final delivery outcome.
 */
public record MailboxDeliveryJob(
    UUID id,
    UUID messageId,
    @Nullable UUID senderUuid,
    @Nullable String senderName,
    UUID recipientUuid,
    String subject,
    @Nullable String body,
    MessageType messageType,
    Instant createdAt,
    Instant availableAt,
    @Nullable Instant expiresAt,
    boolean hasAttachment,
    @Nullable String attachmentData,
    DeliveryStatus status,
    int attemptCount,
    int failureCount,
    @Nullable Instant lastAttemptAt,
    @Nullable Instant lastFailureAt,
    @Nullable String lastFailureReason,
    @Nullable Instant deliveredAt
) {

    public MailboxDeliveryJob {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(recipientUuid, "recipientUuid must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(messageType, "messageType must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(availableAt, "availableAt must not be null");
        Objects.requireNonNull(status, "status must not be null");

        int maxSubjectLength = MailboxConfig.INSTANCE.getMaxSubjectLength();
        if (subject.length() > maxSubjectLength) {
            subject = subject.substring(0, maxSubjectLength);
        }

        if (senderName != null && senderName.length() > 64) {
            senderName = senderName.substring(0, 64);
        }

        if (attachmentData != null && attachmentData.isBlank()) {
            attachmentData = null;
        }

        boolean resolvedHasAttachment = attachmentData != null;
        if (hasAttachment != resolvedHasAttachment) {
            hasAttachment = resolvedHasAttachment;
        }

        if (attemptCount < 0) {
            attemptCount = 0;
        }
        if (failureCount < 0) {
            failureCount = 0;
        }
    }

    /**
     * Delivery status enum.
     */
    public enum DeliveryStatus {
        PENDING("pending"),
        IN_FLIGHT("in_flight"),
        DELIVERED("delivered"),
        FAILED("failed"),
        CANCELLED("cancelled");

        private final String id;

        DeliveryStatus(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static DeliveryStatus fromId(@Nullable String id) {
            if (id == null) {
                return PENDING;
            }
            for (DeliveryStatus status : values()) {
                if (status.id.equalsIgnoreCase(id)) {
                    return status;
                }
            }
            return PENDING;
        }
    }

    public Builder toBuilder() {
        return builder()
            .id(id)
            .messageId(messageId)
            .sender(senderUuid, senderName)
            .recipient(recipientUuid)
            .subject(subject)
            .body(body)
            .messageType(messageType)
            .createdAt(createdAt)
            .availableAt(availableAt)
            .expiresAt(expiresAt)
            .attachment(attachmentData)
            .status(status)
            .attemptCount(attemptCount)
            .failureCount(failureCount)
            .lastAttemptAt(lastAttemptAt)
            .lastFailureAt(lastFailureAt)
            .lastFailureReason(lastFailureReason)
            .deliveredAt(deliveredAt);
    }

    /**
     * Builder for creating MailboxDeliveryJob instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private @Nullable UUID id;
        private @Nullable UUID messageId;
        private @Nullable UUID senderUuid;
        private @Nullable String senderName;
        private @Nullable UUID recipientUuid;
        private @Nullable String subject;
        private @Nullable String body;
        private MessageType messageType = MessageType.SYSTEM;
        private @Nullable Instant createdAt;
        private @Nullable Instant availableAt;
        private @Nullable Instant expiresAt;
        private @Nullable String attachmentData;
        private DeliveryStatus status = DeliveryStatus.PENDING;
        private int attemptCount;
        private int failureCount;
        private @Nullable Instant lastAttemptAt;
        private @Nullable Instant lastFailureAt;
        private @Nullable String lastFailureReason;
        private @Nullable Instant deliveredAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder messageId(UUID messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder sender(@Nullable UUID senderUuid, @Nullable String senderName) {
            this.senderUuid = senderUuid;
            this.senderName = senderName;
            return this;
        }

        public Builder recipient(UUID recipientUuid) {
            this.recipientUuid = recipientUuid;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder body(@Nullable String body) {
            this.body = body;
            return this;
        }

        public Builder messageType(@Nullable MessageType messageType) {
            if (messageType != null) {
                this.messageType = messageType;
            }
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder availableAt(Instant availableAt) {
            this.availableAt = availableAt;
            return this;
        }

        public Builder expiresAt(@Nullable Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder attachment(@Nullable String attachmentData) {
            this.attachmentData = attachmentData;
            return this;
        }

        public Builder status(DeliveryStatus status) {
            this.status = status;
            return this;
        }

        public Builder attemptCount(int attemptCount) {
            this.attemptCount = attemptCount;
            return this;
        }

        public Builder failureCount(int failureCount) {
            this.failureCount = failureCount;
            return this;
        }

        public Builder lastAttemptAt(@Nullable Instant lastAttemptAt) {
            this.lastAttemptAt = lastAttemptAt;
            return this;
        }

        public Builder lastFailureAt(@Nullable Instant lastFailureAt) {
            this.lastFailureAt = lastFailureAt;
            return this;
        }

        public Builder lastFailureReason(@Nullable String lastFailureReason) {
            this.lastFailureReason = lastFailureReason;
            return this;
        }

        public Builder deliveredAt(@Nullable Instant deliveredAt) {
            this.deliveredAt = deliveredAt;
            return this;
        }

        public MailboxDeliveryJob build() {
            UUID resolvedId = id != null ? id : UUID.randomUUID();
            UUID resolvedMessageId = messageId != null ? messageId : UUID.randomUUID();
            UUID resolvedRecipient = Objects.requireNonNull(recipientUuid, "recipientUuid");
            String resolvedSubject = Objects.requireNonNull(subject, "subject");
            Instant resolvedCreatedAt = createdAt != null ? createdAt : Instant.now();
            Instant resolvedAvailableAt = availableAt != null ? availableAt : resolvedCreatedAt;
            String normalizedAttachment = attachmentData != null && attachmentData.isBlank() ? null : attachmentData;
            boolean resolvedHasAttachment = normalizedAttachment != null;

            return new MailboxDeliveryJob(
                resolvedId,
                resolvedMessageId,
                senderUuid,
                senderName,
                resolvedRecipient,
                resolvedSubject,
                body,
                messageType,
                resolvedCreatedAt,
                resolvedAvailableAt,
                expiresAt,
                resolvedHasAttachment,
                normalizedAttachment,
                status,
                attemptCount,
                failureCount,
                lastAttemptAt,
                lastFailureAt,
                lastFailureReason,
                deliveredAt
            );
        }
    }
}
