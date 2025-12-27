package com.devmod.mailbox.network.payload;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload to notify client of a new message arrival.
 * Sent in real-time when a new message is received.
 */
public record MailboxNotifyPayload(
    UUID messageId,
    @Nullable String senderName,
    String subject,
    int messageTypeOrdinal,
    boolean hasAttachment,
    int totalUnread
) implements CustomPacketPayload {

    // Security limits
    private static final int MAX_SUBJECT_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 64;

    public static final Type<MailboxNotifyPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "mailbox_notify"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MailboxNotifyPayload> STREAM_CODEC = StreamCodec.of(
        MailboxNotifyPayload::encode,
        MailboxNotifyPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, MailboxNotifyPayload payload) {
        buf.writeUUID(Objects.requireNonNull(payload.messageId));
        buf.writeUtf(payload.senderName != null ? payload.senderName : "");
        buf.writeUtf(Objects.requireNonNull(payload.subject));
        buf.writeVarInt(payload.messageTypeOrdinal);
        buf.writeBoolean(payload.hasAttachment);
        buf.writeVarInt(payload.totalUnread);
    }

    private static MailboxNotifyPayload decode(RegistryFriendlyByteBuf buf) {
        UUID messageId = buf.readUUID();
        String senderName = buf.readUtf(MAX_NAME_LENGTH);
        String subject = buf.readUtf(MAX_SUBJECT_LENGTH);
        int messageTypeOrdinal = buf.readVarInt();
        boolean hasAttachment = buf.readBoolean();
        int totalUnread = buf.readVarInt();

        // Convert empty string to null
        if (senderName.isEmpty()) senderName = null;

        return new MailboxNotifyPayload(messageId, senderName, subject, messageTypeOrdinal, hasAttachment, totalUnread);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Get display sender name.
     */
    public String getDisplaySender() {
        return senderName != null ? senderName : "System";
    }
}
