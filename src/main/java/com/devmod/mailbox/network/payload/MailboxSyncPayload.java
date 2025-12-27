package com.devmod.mailbox.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload to sync the full mailbox state from server to client.
 * Sent when player logs in or requests a refresh.
 */
public record MailboxSyncPayload(
    List<MailboxMessageData> messages,
    int unreadCount,
    int maxMessages
) implements CustomPacketPayload {

    // Security limits
    private static final int MAX_MESSAGES = 200;
    private static final int MAX_SUBJECT_LENGTH = 256;
    private static final int MAX_BODY_LENGTH = 2000;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_ATTACHMENT_LENGTH = 4096;

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
        buf.writeVarInt(payload.messages.size());
        for (MailboxMessageData msg : payload.messages) {
            encodeMessage(buf, msg);
        }
        buf.writeVarInt(payload.unreadCount);
        buf.writeVarInt(payload.maxMessages);
    }

    private static MailboxSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int messageCount = buf.readVarInt();
        if (messageCount < 0 || messageCount > MAX_MESSAGES) {
            messageCount = 0;
        }

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
        writeOptionalUtf(buf, msg.senderName);
        buf.writeUtf(msg.subject);
        writeOptionalUtf(buf, msg.body);
        buf.writeVarInt(msg.messageTypeOrdinal);
        buf.writeLong(msg.createdAtMillis);
        buf.writeBoolean(msg.isRead);
        buf.writeLong(msg.expiresAtMillis);
        buf.writeBoolean(msg.hasAttachment);
        buf.writeBoolean(msg.attachmentClaimed);
        writeOptionalUtf(buf, msg.attachmentData);
    }

    private static MailboxMessageData decodeMessage(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        @Nullable String senderName = readOptionalUtf(buf, MAX_NAME_LENGTH);
        String subject = buf.readUtf(MAX_SUBJECT_LENGTH);
        @Nullable String body = readOptionalUtf(buf, MAX_BODY_LENGTH);
        int messageTypeOrdinal = buf.readVarInt();
        long createdAtMillis = buf.readLong();
        boolean isRead = buf.readBoolean();
        long expiresAtMillis = buf.readLong();
        boolean hasAttachment = buf.readBoolean();
        boolean attachmentClaimed = buf.readBoolean();
        @Nullable String attachmentData = readOptionalUtf(buf, MAX_ATTACHMENT_LENGTH);

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

    private static void writeOptionalUtf(RegistryFriendlyByteBuf buf, @Nullable String value) {
        buf.writeUtf(value != null ? value : "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
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
        UUID id,
        @Nullable String senderName,
        String subject,
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
