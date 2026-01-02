package com.devmod.mailbox.network.payload;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.mailbox.ticket.TicketStatus;
import com.devmod.network.PayloadValidation;

/**
 * Payload for ticket actions (status updates, comments).
 */
public record TicketActionPayload(
    Action action,
    UUID ticketId,
    @Nullable String statusId,
    @Nullable String comment
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public TicketActionPayload {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(ticketId, "ticketId");
    }

    public enum Action {
        UPDATE_STATUS,
        ADD_COMMENT
    }

    public static final Type<TicketActionPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "ticket_action"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TicketActionPayload> STREAM_CODEC = StreamCodec.of(
        TicketActionPayload::encode,
        TicketActionPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, TicketActionPayload payload) {
        buf.writeEnum(Objects.requireNonNull(payload.action));
        buf.writeUUID(Objects.requireNonNull(payload.ticketId));
        buf.writeUtf(payload.statusId != null ? payload.statusId : "");
        buf.writeUtf(payload.comment != null ? payload.comment : "");
    }

    private static TicketActionPayload decode(RegistryFriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID ticketId = buf.readUUID();
        String statusId = buf.readUtf(64);
        String comment = buf.readUtf(1000);

        return new TicketActionPayload(
            action,
            ticketId,
            statusId.isEmpty() ? null : statusId,
            comment.isEmpty() ? null : comment
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = varIntSize(action.ordinal());
        size += 16; // UUID
        size += estimatedUtfSize(statusId);
        size += estimatedUtfSize(comment);
        return size;
    }

    private static int estimatedUtfSize(@Nullable String value) {
        if (value == null) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
     * Create a payload to update ticket status.
     */
    public static TicketActionPayload updateStatus(UUID ticketId, TicketStatus status) {
        return new TicketActionPayload(Action.UPDATE_STATUS, ticketId, status.name(), null);
    }

    /**
     * Create a payload to add a ticket comment.
     */
    public static TicketActionPayload addComment(UUID ticketId, String comment) {
        return new TicketActionPayload(Action.ADD_COMMENT, ticketId, null, comment);
    }
}
