package com.devmod.mailbox.network.payload;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Unified payload for mailbox actions (read, delete, claim).
 * Reduces the number of payload classes while maintaining type safety.
 */
public record MailboxActionPayload(
    @Nonnull Action action,
    @Nonnull UUID messageId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public MailboxActionPayload {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(messageId, "messageId");
    }

    public static final Type<MailboxActionPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "mailbox_action"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MailboxActionPayload> STREAM_CODEC = StreamCodec.of(
        MailboxActionPayload::encode,
        MailboxActionPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, MailboxActionPayload payload) {
        buf.writeVarInt(payload.action.ordinal());
        buf.writeUUID(payload.messageId);
    }

    private static MailboxActionPayload decode(RegistryFriendlyByteBuf buf) {
        int actionOrdinal = buf.readVarInt();
        UUID messageId = buf.readUUID();

        Action action = Action.fromOrdinal(actionOrdinal);
        return new MailboxActionPayload(action, messageId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Create a "mark as read" action payload.
     */
    public static MailboxActionPayload read(UUID messageId) {
        return new MailboxActionPayload(Action.READ, messageId);
    }

    /**
     * Create a "delete" action payload.
     */
    public static MailboxActionPayload delete(UUID messageId) {
        return new MailboxActionPayload(Action.DELETE, messageId);
    }

    /**
     * Create a "claim attachment" action payload.
     */
    public static MailboxActionPayload claim(UUID messageId) {
        return new MailboxActionPayload(Action.CLAIM, messageId);
    }

    /**
     * Create a "refresh mailbox" action payload.
     */
    public static MailboxActionPayload refresh() {
        return new MailboxActionPayload(Action.REFRESH, new UUID(0L, 0L));
    }

    /**
     * Mailbox actions.
     */
    public enum Action {
        READ,
        DELETE,
        CLAIM,
        REFRESH;

        public static Action fromOrdinal(int ordinal) {
            Action[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return READ;
        }
    }

    @Override
    public int estimatedSize() {
        return 1 + 16; // VarInt for action + UUID
    }
}
