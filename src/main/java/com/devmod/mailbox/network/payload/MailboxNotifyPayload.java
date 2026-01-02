package com.devmod.mailbox.network.payload;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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

    @Override
    public int estimatedSize() {
        int size = 16; // UUID
        size += estimatedUtfSize(senderName);
        size += estimatedUtfSize(subject);
        size += varIntSize(messageTypeOrdinal);
        size += 1; // hasAttachment
        size += varIntSize(totalUnread);
        return size;
    }

    private static int estimatedUtfSize(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }

    /**
     * Get display sender name.
     */
    public String getDisplaySender() {
        return senderName != null ? senderName : "System";
    }
}
