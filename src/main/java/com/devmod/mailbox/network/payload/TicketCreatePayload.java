package com.devmod.mailbox.network.payload;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketPriority;

/**
 * Payload for creating a new ticket from client to server.
 */
public record TicketCreatePayload(
    TicketCategory category,
    TicketPriority priority,
    String subject,
    String description
) implements CustomPacketPayload {

    // Security limits
    private static final int MAX_SUBJECT_LENGTH = 256;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;

    public static final Type<TicketCreatePayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "ticket_create"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TicketCreatePayload> STREAM_CODEC = StreamCodec.of(
        TicketCreatePayload::encode,
        TicketCreatePayload::decode
    );

    public TicketCreatePayload {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(description, "description");
    }

    private static void encode(RegistryFriendlyByteBuf buf, TicketCreatePayload payload) {
        buf.writeEnum(payload.category);
        buf.writeEnum(payload.priority);
        buf.writeUtf(payload.subject);
        buf.writeUtf(payload.description);
    }

    private static TicketCreatePayload decode(RegistryFriendlyByteBuf buf) {
        TicketCategory category = buf.readEnum(TicketCategory.class);
        TicketPriority priority = buf.readEnum(TicketPriority.class);
        String subject = buf.readUtf(MAX_SUBJECT_LENGTH);
        String description = buf.readUtf(MAX_DESCRIPTION_LENGTH);

        return new TicketCreatePayload(category, priority, subject, description);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
