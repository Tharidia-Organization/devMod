package com.devmod.mailbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Immutable record representing a mailbox message.
 *
 * Messages can be sent between players, from the system, or from administrators.
 * They may contain attachments (items or currency) that can be claimed by the recipient.
 */
public record MailboxMessage(
    UUID id,
    @Nullable UUID senderUuid,
    @Nullable String senderName,
    UUID recipientUuid,
    String subject,
    @Nullable String body,
    MessageType messageType,
    Instant createdAt,
    @Nullable Instant readAt,
    @Nullable Instant expiresAt,
    boolean hasAttachment,
    boolean attachmentClaimed,
    @Nullable String attachmentData,
    boolean deleted
) {

    /**
     * Compact constructor with validation.
     */
    public MailboxMessage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(recipientUuid, "recipientUuid must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(messageType, "messageType must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        // Validate subject length
        if (subject.length() > 128) {
            subject = subject.substring(0, 128);
        }

        // Validate sender name length
        if (senderName != null && senderName.length() > 64) {
            senderName = senderName.substring(0, 64);
        }
    }

    /**
     * Check if this message has been read.
     */
    public boolean isRead() {
        return readAt != null;
    }

    /**
     * Check if this message is expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if attachments can still be claimed.
     */
    public boolean canClaimAttachment() {
        return hasAttachment && !attachmentClaimed && !isExpired();
    }

    /**
     * Create a copy marked as read.
     */
    public MailboxMessage withReadAt(Instant readAt) {
        return new MailboxMessage(
            id, senderUuid, senderName, recipientUuid, subject, body,
            messageType, createdAt, readAt, expiresAt,
            hasAttachment, attachmentClaimed, attachmentData, deleted
        );
    }

    /**
     * Create a copy with attachment claimed.
     */
    public MailboxMessage withAttachmentClaimed() {
        return new MailboxMessage(
            id, senderUuid, senderName, recipientUuid, subject, body,
            messageType, createdAt, readAt, expiresAt,
            hasAttachment, true, attachmentData, deleted
        );
    }

    /**
     * Create a copy marked as deleted.
     */
    public MailboxMessage withDeleted() {
        return new MailboxMessage(
            id, senderUuid, senderName, recipientUuid, subject, body,
            messageType, createdAt, readAt, expiresAt,
            hasAttachment, attachmentClaimed, attachmentData, true
        );
    }

    /**
     * Builder for creating MailboxMessage instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private @Nullable UUID id;
        private @Nullable UUID senderUuid;
        private @Nullable String senderName;
        private @Nullable UUID recipientUuid;
        private @Nullable String subject;
        private @Nullable String body;
        private MessageType messageType = MessageType.SYSTEM;
        private @Nullable Instant createdAt;
        private @Nullable Instant readAt;
        private @Nullable Instant expiresAt;
        private boolean hasAttachment;
        private boolean attachmentClaimed;
        private @Nullable String attachmentData;
        private boolean deleted;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder sender(@Nullable UUID uuid, @Nullable String name) {
            this.senderUuid = uuid;
            this.senderName = name;
            return this;
        }

        public Builder recipient(UUID uuid) {
            this.recipientUuid = uuid;
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

        public Builder messageType(@Nullable MessageType type) {
            if (type != null) {
                this.messageType = type;
            }
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder readAt(@Nullable Instant readAt) {
            this.readAt = readAt;
            return this;
        }

        public Builder expiresAt(@Nullable Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder attachment(@Nullable String data) {
            String normalized = data != null && !data.isBlank() ? data : null;
            this.hasAttachment = normalized != null;
            this.attachmentData = normalized;
            return this;
        }

        public Builder attachmentClaimed(boolean claimed) {
            this.attachmentClaimed = claimed;
            return this;
        }

        public Builder deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public MailboxMessage build() {
            UUID resolvedId = id != null ? id : UUID.randomUUID();
            Instant resolvedCreatedAt = createdAt != null ? createdAt : Instant.now();
            UUID resolvedRecipient = Objects.requireNonNull(recipientUuid, "recipientUuid");
            String resolvedSubject = Objects.requireNonNull(subject, "subject");
            MessageType resolvedType = messageType != null ? messageType : MessageType.SYSTEM;
            return new MailboxMessage(
                resolvedId, senderUuid, senderName, resolvedRecipient, resolvedSubject, body,
                resolvedType, resolvedCreatedAt, readAt, expiresAt,
                hasAttachment, attachmentClaimed, attachmentData, deleted
            );
        }
    }
}
