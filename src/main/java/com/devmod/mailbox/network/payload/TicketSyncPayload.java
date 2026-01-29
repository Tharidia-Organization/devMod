package com.devmod.mailbox.network.payload;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketPriority;
import com.devmod.mailbox.ticket.TicketStatus;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Payload for syncing ticket data from server to client.
 */
public record TicketSyncPayload(
    List<TicketData> tickets
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TicketSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "ticket_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TicketSyncPayload> STREAM_CODEC = StreamCodec.of(
        TicketSyncPayload::encode,
        TicketSyncPayload::decode
    );

    public TicketSyncPayload {
        Objects.requireNonNull(tickets, "tickets");
    }

    private static void encode(RegistryFriendlyByteBuf buf, TicketSyncPayload payload) {
        int ticketCount = MailboxPayloadLimits.clampCount(payload.tickets.size(), MailboxPayloadLimits.MAX_TICKETS);
        buf.writeVarInt(ticketCount);
        for (int i = 0; i < ticketCount; i++) {
            TicketData ticket = payload.tickets.get(i);
            encodeTicket(buf, ticket);
        }
    }

    private static void encodeTicket(RegistryFriendlyByteBuf buf, TicketData ticket) {
        buf.writeUUID(ticket.id);
        buf.writeEnum(ticket.category);
        buf.writeEnum(ticket.priority);
        buf.writeEnum(ticket.status);
        buf.writeUtf(
            MailboxPayloadLimits.truncate(ticket.subject, MailboxPayloadLimits.MAX_TICKET_SUBJECT_LENGTH),
            MailboxPayloadLimits.MAX_TICKET_SUBJECT_LENGTH
        );
        buf.writeBoolean(ticket.description != null);
        if (ticket.description != null) {
            buf.writeUtf(
                MailboxPayloadLimits.truncate(ticket.description, MailboxPayloadLimits.MAX_TICKET_DESCRIPTION_LENGTH),
                MailboxPayloadLimits.MAX_TICKET_DESCRIPTION_LENGTH
            );
        }
        buf.writeLong(ticket.createdAt.toEpochMilli());
        buf.writeBoolean(ticket.updatedAt != null);
        if (ticket.updatedAt != null) {
            buf.writeLong(ticket.updatedAt.toEpochMilli());
        }
        buf.writeVarInt(ticket.commentCount);
    }

    private static TicketSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int count = MailboxPayloadLimits.clampCount(buf.readVarInt(), MailboxPayloadLimits.MAX_TICKETS);
        List<TicketData> tickets = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            tickets.add(decodeTicket(buf));
        }

        return new TicketSyncPayload(tickets);
    }

    private static TicketData decodeTicket(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        TicketCategory category = buf.readEnum(TicketCategory.class);
        TicketPriority priority = buf.readEnum(TicketPriority.class);
        TicketStatus status = buf.readEnum(TicketStatus.class);
        String subject = buf.readUtf(MailboxPayloadLimits.MAX_TICKET_SUBJECT_LENGTH);
        @Nullable String description = buf.readBoolean()
            ? buf.readUtf(MailboxPayloadLimits.MAX_TICKET_DESCRIPTION_LENGTH)
            : null;
        Instant createdAt = Instant.ofEpochMilli(buf.readLong());
        @Nullable Instant updatedAt = buf.readBoolean() ? Instant.ofEpochMilli(buf.readLong()) : null;
        int commentCount = buf.readVarInt();

        return new TicketData(id, category, priority, status, subject, description, createdAt, updatedAt, commentCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int count = MailboxPayloadLimits.clampCount(tickets.size(), MailboxPayloadLimits.MAX_TICKETS);
        int size = PayloadSizeUtil.varIntSize(count);
        for (int i = 0; i < count; i++) {
            TicketData ticket = tickets.get(i);
            size += estimateTicketSize(ticket);
        }
        return size;
    }

    private static int estimateTicketSize(TicketData ticket) {
        int size = 16; // UUID
        size += PayloadSizeUtil.varIntSize(ticket.category.ordinal());
        size += PayloadSizeUtil.varIntSize(ticket.priority.ordinal());
        size += PayloadSizeUtil.varIntSize(ticket.status.ordinal());
        size += PayloadSizeUtil.estimatedUtfSize(
            MailboxPayloadLimits.truncateNullable(ticket.subject, MailboxPayloadLimits.MAX_TICKET_SUBJECT_LENGTH)
        );
        size += 1; // description present flag
        if (ticket.description != null) {
            size += PayloadSizeUtil.estimatedUtfSize(
                MailboxPayloadLimits.truncateNullable(ticket.description, MailboxPayloadLimits.MAX_TICKET_DESCRIPTION_LENGTH)
            );
        }
        size += 8; // createdAt
        size += 1; // updatedAt present flag
        if (ticket.updatedAt != null) {
            size += 8; // updatedAt
        }
        size += PayloadSizeUtil.varIntSize(ticket.commentCount);
        return size;
    }

    /**
     * Ticket data for network transfer.
     */
    public record TicketData(
        @Nonnull UUID id,
        @Nonnull TicketCategory category,
        @Nonnull TicketPriority priority,
        @Nonnull TicketStatus status,
        @Nonnull String subject,
        @Nullable String description,
        @Nonnull Instant createdAt,
        @Nullable Instant updatedAt,
        int commentCount
    ) {
        public TicketData {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }
}
