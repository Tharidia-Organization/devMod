package com.devmod.mailbox.network.payload;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for sending a message from client to server.
 */
public record MailboxSendPayload(
    UUID recipientUuid,
    @Nullable String recipientName,
    String subject,
    @Nullable String body,
    @Nullable String attachmentData
) implements CustomPacketPayload {

    // Security limits
    private static final int MAX_SUBJECT_LENGTH = 128;
    private static final int MAX_BODY_LENGTH = 2000;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_ATTACHMENT_LENGTH = 4096;

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
        buf.writeUUID(payload.recipientUuid);
        writeOptionalUtf(buf, payload.recipientName);
        buf.writeUtf(payload.subject);
        writeOptionalUtf(buf, payload.body);
        writeOptionalUtf(buf, payload.attachmentData);
    }

    private static MailboxSendPayload decode(RegistryFriendlyByteBuf buf) {
        UUID recipientUuid = buf.readUUID();
        @Nullable String recipientName = readOptionalUtf(buf, MAX_NAME_LENGTH);
        String subject = buf.readUtf(MAX_SUBJECT_LENGTH);
        @Nullable String body = readOptionalUtf(buf, MAX_BODY_LENGTH);
        @Nullable String attachmentData = readOptionalUtf(buf, MAX_ATTACHMENT_LENGTH);

        return new MailboxSendPayload(recipientUuid, recipientName, subject, body, attachmentData);
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
}
