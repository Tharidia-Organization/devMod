package com.devmod.mailbox.network.payload;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for structured mailbox status feedback (send/claim/delete).
 */
public record MailboxStatusPayload(
    Action action,
    Status status,
    String message
) implements CustomPacketPayload {

    private static final int MAX_MESSAGE_LENGTH = 256;

    public static final Type<MailboxStatusPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "mailbox_status"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MailboxStatusPayload> STREAM_CODEC = StreamCodec.of(
        MailboxStatusPayload::encode,
        MailboxStatusPayload::decode
    );

    public MailboxStatusPayload {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
    }

    private static void encode(RegistryFriendlyByteBuf buf, MailboxStatusPayload payload) {
        buf.writeVarInt(payload.action.ordinal());
        buf.writeVarInt(payload.status.ordinal());
        buf.writeUtf(payload.message);
    }

    private static MailboxStatusPayload decode(RegistryFriendlyByteBuf buf) {
        int actionOrdinal = buf.readVarInt();
        int statusOrdinal = buf.readVarInt();
        String message = buf.readUtf(MAX_MESSAGE_LENGTH);

        Action action = Action.fromOrdinal(actionOrdinal);
        Status status = Status.fromOrdinal(statusOrdinal);

        return new MailboxStatusPayload(action, status, message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        SEND,
        READ,
        DELETE,
        CLAIM,
        REFRESH;

        public static Action fromOrdinal(int ordinal) {
            Action[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return SEND;
        }
    }

    public enum Status {
        SUCCESS,
        ERROR,
        WARNING,
        INFO;

        public static Status fromOrdinal(int ordinal) {
            Status[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return INFO;
        }
    }
}
