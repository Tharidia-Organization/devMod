package com.devmod.mailbox.network.payload;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.mailbox.ticket.TicketStatus;
import com.devmod.network.PayloadSizeUtil;
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
        buf.writeUtf(
            MailboxPayloadLimits.truncate(payload.statusId, MailboxPayloadLimits.MAX_TICKET_STATUS_ID_LENGTH),
            MailboxPayloadLimits.MAX_TICKET_STATUS_ID_LENGTH
        );
        buf.writeUtf(
            MailboxPayloadLimits.truncate(payload.comment, MailboxPayloadLimits.MAX_TICKET_COMMENT_LENGTH),
            MailboxPayloadLimits.MAX_TICKET_COMMENT_LENGTH
        );
    }

    private static TicketActionPayload decode(RegistryFriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID ticketId = buf.readUUID();
        String statusId = buf.readUtf(MailboxPayloadLimits.MAX_TICKET_STATUS_ID_LENGTH);
        String comment = buf.readUtf(MailboxPayloadLimits.MAX_TICKET_COMMENT_LENGTH);

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
        int size = PayloadSizeUtil.varIntSize(action.ordinal());
        size += 16; // UUID
        size += PayloadSizeUtil.estimatedUtfSize(
            MailboxPayloadLimits.truncateNullable(statusId, MailboxPayloadLimits.MAX_TICKET_STATUS_ID_LENGTH)
        );
        size += PayloadSizeUtil.estimatedUtfSize(
            MailboxPayloadLimits.truncateNullable(comment, MailboxPayloadLimits.MAX_TICKET_COMMENT_LENGTH)
        );
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
