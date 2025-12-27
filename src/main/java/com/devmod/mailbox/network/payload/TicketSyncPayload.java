package com.devmod.mailbox.network.payload;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketPriority;
import com.devmod.mailbox.ticket.TicketStatus;

/**
 * Payload for syncing ticket data from server to client.
 */
public record TicketSyncPayload(
    List<TicketData> tickets
) implements CustomPacketPayload {

    // Security limits
    private static final int MAX_TICKETS = 100;
    private static final int MAX_SUBJECT_LENGTH = 256;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;

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
        buf.writeVarInt(payload.tickets.size());
        for (TicketData ticket : payload.tickets) {
            encodeTicket(buf, ticket);
        }
    }

    private static void encodeTicket(RegistryFriendlyByteBuf buf, TicketData ticket) {
        buf.writeUUID(ticket.id);
        buf.writeEnum(ticket.category);
        buf.writeEnum(ticket.priority);
        buf.writeEnum(ticket.status);
        buf.writeUtf(ticket.subject);
        buf.writeBoolean(ticket.description != null);
        if (ticket.description != null) {
            buf.writeUtf(ticket.description);
        }
        buf.writeLong(ticket.createdAt.toEpochMilli());
        buf.writeBoolean(ticket.updatedAt != null);
        if (ticket.updatedAt != null) {
            buf.writeLong(ticket.updatedAt.toEpochMilli());
        }
        buf.writeVarInt(ticket.commentCount);
    }

    private static TicketSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_TICKETS);
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
        String subject = buf.readUtf(MAX_SUBJECT_LENGTH);
        @Nullable String description = buf.readBoolean() ? buf.readUtf(MAX_DESCRIPTION_LENGTH) : null;
        Instant createdAt = Instant.ofEpochMilli(buf.readLong());
        @Nullable Instant updatedAt = buf.readBoolean() ? Instant.ofEpochMilli(buf.readLong()) : null;
        int commentCount = buf.readVarInt();

        return new TicketData(id, category, priority, status, subject, description, createdAt, updatedAt, commentCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Ticket data for network transfer.
     */
    public record TicketData(
        UUID id,
        TicketCategory category,
        TicketPriority priority,
        TicketStatus status,
        String subject,
        @Nullable String description,
        Instant createdAt,
        @Nullable Instant updatedAt,
        int commentCount
    ) {}
}
