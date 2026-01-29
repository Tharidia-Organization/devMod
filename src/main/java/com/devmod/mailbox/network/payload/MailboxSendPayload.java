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
 * Payload for sending a message from client to server.
 */
public record MailboxSendPayload(
    @Nonnull UUID recipientUuid,
    @Nullable String recipientName,
    @Nonnull String subject,
    @Nullable String body,
    @Nullable String attachmentData
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<MailboxSendPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "mailbox_send"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MailboxSendPayload> STREAM_CODEC = StreamCodec.of(
        MailboxSendPayload::encode,
        MailboxSendPayload::decode
    );

    public MailboxSendPayload {
        Objects.requireNonNull(recipientUuid, "recipientUuid");
        Objects.requireNonNull(subject, "subject");
    }

    private static void encode(RegistryFriendlyByteBuf buf, MailboxSendPayload payload) {
        buf.writeUUID(Objects.requireNonNull(payload.recipientUuid));
        writeOptionalUtf(buf, payload.recipientName, MailboxPayloadLimits.MAX_MESSAGE_NAME_LENGTH);
        buf.writeUtf(
            MailboxPayloadLimits.truncate(payload.subject, MailboxPayloadLimits.MAX_MESSAGE_SUBJECT_LENGTH),
            MailboxPayloadLimits.MAX_MESSAGE_SUBJECT_LENGTH
        );
        writeOptionalUtf(buf, payload.body, MailboxPayloadLimits.MAX_MESSAGE_BODY_LENGTH);
        writeOptionalUtf(buf, payload.attachmentData, MailboxPayloadLimits.MAX_MESSAGE_ATTACHMENT_LENGTH);
    }

    private static MailboxSendPayload decode(RegistryFriendlyByteBuf buf) {
        UUID recipientUuid = buf.readUUID();
        @Nullable String recipientName = readOptionalUtf(buf, MailboxPayloadLimits.MAX_MESSAGE_NAME_LENGTH);
        String subject = buf.readUtf(MailboxPayloadLimits.MAX_MESSAGE_SUBJECT_LENGTH);
        @Nullable String body = readOptionalUtf(buf, MailboxPayloadLimits.MAX_MESSAGE_BODY_LENGTH);
        @Nullable String attachmentData = readOptionalUtf(buf, MailboxPayloadLimits.MAX_MESSAGE_ATTACHMENT_LENGTH);

        return new MailboxSendPayload(recipientUuid, recipientName, subject, body, attachmentData);
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
        int size = 16; // UUID
        size += estimatedUtfSize(recipientName, MailboxPayloadLimits.MAX_MESSAGE_NAME_LENGTH);
        size += estimatedUtfSize(subject, MailboxPayloadLimits.MAX_MESSAGE_SUBJECT_LENGTH);
        size += estimatedUtfSize(body, MailboxPayloadLimits.MAX_MESSAGE_BODY_LENGTH);
        size += estimatedUtfSize(attachmentData, MailboxPayloadLimits.MAX_MESSAGE_ATTACHMENT_LENGTH);
        return size;
    }

    private static int estimatedUtfSize(@Nullable String value, int maxLength) {
        return PayloadSizeUtil.estimatedUtfSize(MailboxPayloadLimits.truncateNullable(value, maxLength));
    }
}
