package com.devmod.mailbox.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Payload to sync the full mailbox state from server to client.
 * Sent when player logs in or requests a refresh.
 */
public record MailboxSyncPayload(
    List<MailboxMessageData> messages,
    int unreadCount,
    int maxMessages
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<MailboxSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "mailbox_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MailboxSyncPayload> STREAM_CODEC = StreamCodec.of(
        MailboxSyncPayload::encode,
        MailboxSyncPayload::decode
    );

    public MailboxSyncPayload {
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
    }

    private static void encode(RegistryFriendlyByteBuf buf, MailboxSyncPayload payload) {
        int messageCount = MailboxPayloadLimits.clampCount(payload.messages.size(), MailboxPayloadLimits.MAX_MESSAGES);
        buf.writeVarInt(messageCount);
        for (int i = 0; i < messageCount; i++) {
            MailboxMessageData msg = payload.messages.get(i);
            encodeMessage(buf, msg);
        }
        buf.writeVarInt(payload.unreadCount);
        buf.writeVarInt(payload.maxMessages);
    }

    private static MailboxSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int messageCount = buf.readVarInt();
        messageCount = MailboxPayloadLimits.clampCount(messageCount, MailboxPayloadLimits.MAX_MESSAGES);

        List<MailboxMessageData> messages = new ArrayList<>(messageCount);
        for (int i = 0; i < messageCount; i++) {
            messages.add(decodeMessage(buf));
        }

        int unreadCount = buf.readVarInt();
        int maxMessages = buf.readVarInt();

        return new MailboxSyncPayload(messages, unreadCount, maxMessages);
    }

    private static void encodeMessage(RegistryFriendlyByteBuf buf, MailboxMessageData msg) {
        buf.writeUUID(msg.id);
        writeOptionalUtf(buf, msg.senderName, MailboxPayloadLimits.MAX_MESSAGE_NAME_LENGTH);
        buf.writeUtf(
            MailboxPayloadLimits.truncate(msg.subject, MailboxPayloadLimits.MAX_MESSAGE_SUBJECT_LENGTH),
            MailboxPayloadLimits.MAX_MESSAGE_SUBJECT_LENGTH
        );
        writeOptionalUtf(buf, msg.body, MailboxPayloadLimits.MAX_MESSAGE_BODY_LENGTH);
        buf.writeVarInt(msg.messageTypeOrdinal);
        buf.writeLong(msg.createdAtMillis);
        buf.writeBoolean(msg.isRead);
        buf.writeLong(msg.expiresAtMillis);
        buf.writeBoolean(msg.hasAttachment);
        buf.writeBoolean(msg.attachmentClaimed);
        writeOptionalUtf(buf, msg.attachmentData, MailboxPayloadLimits.MAX_MESSAGE_ATTACHMENT_LENGTH);
    }

    private static MailboxMessageData decodeMessage(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        @Nullable String senderName = readOptionalUtf(buf, MailboxPayloadLimits.MAX_MESSAGE_NAME_LENGTH);
        String subject = buf.readUtf(MailboxPayloadLimits.MAX_MESSAGE_SUBJECT_LENGTH);
        @Nullable String body = readOptionalUtf(buf, MailboxPayloadLimits.MAX_MESSAGE_BODY_LENGTH);
        int messageTypeOrdinal = buf.readVarInt();
        long createdAtMillis = buf.readLong();
        boolean isRead = buf.readBoolean();
        long expiresAtMillis = buf.readLong();
        boolean hasAttachment = buf.readBoolean();
        boolean attachmentClaimed = buf.readBoolean();
        @Nullable String attachmentData = readOptionalUtf(buf, MailboxPayloadLimits.MAX_MESSAGE_ATTACHMENT_LENGTH);

        return new MailboxMessageData(
            id, senderName, subject, body, messageTypeOrdinal,
            createdAtMillis, isRead, expiresAtMillis,
            hasAttachment, attachmentClaimed, attachmentData
        );
    }

    @Nullable
    private static String readOptionalUtf(RegistryFriendlyByteBuf buf, int maxLength) {
        String value = buf.readUtf(maxLength);
        return value.isEmpty() ? null : value;
    }

    private static void writeOptionalUtf(RegistryFriendlyByteBuf buf, @Nullable String value, int maxLength) {
        buf.writeUtf(MailboxPayloadLimits.truncate(value, maxLength), maxLength);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int count = MailboxPayloadLimits.clampCount(messages.size(), MailboxPayloadLimits.MAX_MESSAGES);
        int size = PayloadSizeUtil.varIntSize(count);
        for (int i = 0; i < count; i++) {
            MailboxMessageData msg = messages.get(i);
            size += estimateMessageSize(msg);
        }
        size += PayloadSizeUtil.varIntSize(unreadCount);
        size += PayloadSizeUtil.varIntSize(maxMessages);
        return size;
    }

    private static int estimateMessageSize(MailboxMessageData msg) {
        int size = 16; // UUID
        size += estimatedUtfSize(msg.senderName, MailboxPayloadLimits.MAX_MESSAGE_NAME_LENGTH);
        size += estimatedUtfSize(msg.subject, MailboxPayloadLimits.MAX_MESSAGE_SUBJECT_LENGTH);
        size += estimatedUtfSize(msg.body, MailboxPayloadLimits.MAX_MESSAGE_BODY_LENGTH);
        size += PayloadSizeUtil.varIntSize(msg.messageTypeOrdinal);
        size += 8; // createdAtMillis
        size += 1; // isRead
        size += 8; // expiresAtMillis
        size += 1; // hasAttachment
        size += 1; // attachmentClaimed
        size += estimatedUtfSize(msg.attachmentData, MailboxPayloadLimits.MAX_MESSAGE_ATTACHMENT_LENGTH);
        return size;
    }

    private static int estimatedUtfSize(@Nullable String value, int maxLength) {
        return PayloadSizeUtil.estimatedUtfSize(MailboxPayloadLimits.truncateNullable(value, maxLength));
    }

    /**
     * Create an empty sync payload.
     */
    public static MailboxSyncPayload empty(int maxMessages) {
        return new MailboxSyncPayload(List.of(), 0, maxMessages);
    }

    /**
     * Data structure for a single message in the sync.
     */
    public record MailboxMessageData(
        @Nonnull UUID id,
        @Nullable String senderName,
        @Nonnull String subject,
        @Nullable String body,
        int messageTypeOrdinal,
        long createdAtMillis,
        boolean isRead,
        long expiresAtMillis,
        boolean hasAttachment,
        boolean attachmentClaimed,
        @Nullable String attachmentData
    ) {
        public MailboxMessageData {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(subject, "subject");
        }

        /**
         * Check if the message has expired.
         */
        public boolean isExpired() {
            return expiresAtMillis > 0 && System.currentTimeMillis() > expiresAtMillis;
        }

        /**
         * Check if attachment can be claimed.
         */
        public boolean canClaimAttachment() {
            return hasAttachment && !attachmentClaimed && !isExpired();
        }
    }
}
