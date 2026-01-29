package com.devmod.mailbox.network.payload;

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
 * Payload to notify client of a new message arrival.
 * Sent in real-time when a new message is received.
 */
public record MailboxNotifyPayload(
    @Nonnull UUID messageId,
    @Nullable String senderName,
    @Nonnull String subject,
    int messageTypeOrdinal,
    boolean hasAttachment,
    int totalUnread
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public MailboxNotifyPayload {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(subject, "subject");
    }

    public static final Type<MailboxNotifyPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "mailbox_notify"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MailboxNotifyPayload> STREAM_CODEC = StreamCodec.of(
        MailboxNotifyPayload::encode,
        MailboxNotifyPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, MailboxNotifyPayload payload) {
        buf.writeUUID(Objects.requireNonNull(payload.messageId));
        buf.writeUtf(
            MailboxPayloadLimits.truncate(payload.senderName, MailboxPayloadLimits.MAX_MESSAGE_NAME_LENGTH),
            MailboxPayloadLimits.MAX_MESSAGE_NAME_LENGTH
        );
        buf.writeUtf(
            MailboxPayloadLimits.truncate(payload.subject, MailboxPayloadLimits.MAX_NOTIFY_SUBJECT_LENGTH),
            MailboxPayloadLimits.MAX_NOTIFY_SUBJECT_LENGTH
        );
        buf.writeVarInt(payload.messageTypeOrdinal);
        buf.writeBoolean(payload.hasAttachment);
        buf.writeVarInt(payload.totalUnread);
    }

    private static MailboxNotifyPayload decode(RegistryFriendlyByteBuf buf) {
        UUID messageId = buf.readUUID();
        String senderName = buf.readUtf(MailboxPayloadLimits.MAX_MESSAGE_NAME_LENGTH);
        String subject = buf.readUtf(MailboxPayloadLimits.MAX_NOTIFY_SUBJECT_LENGTH);
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

    @Override
    public int estimatedSize() {
        int size = 16; // UUID
        size += estimatedUtfSize(senderName, MailboxPayloadLimits.MAX_MESSAGE_NAME_LENGTH);
        size += estimatedUtfSize(subject, MailboxPayloadLimits.MAX_NOTIFY_SUBJECT_LENGTH);
        size += PayloadSizeUtil.varIntSize(messageTypeOrdinal);
        size += 1; // hasAttachment
        size += PayloadSizeUtil.varIntSize(totalUnread);
        return size;
    }

    private static int estimatedUtfSize(@Nullable String value, int maxLength) {
        return PayloadSizeUtil.estimatedUtfSize(MailboxPayloadLimits.truncateNullable(value, maxLength));
    }

    /**
     * Get display sender name.
     */
    public String getDisplaySender() {
        return senderName != null ? senderName : "System";
    }
}
