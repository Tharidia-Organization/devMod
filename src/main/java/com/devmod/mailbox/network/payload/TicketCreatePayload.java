package com.devmod.mailbox.network.payload;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketPriority;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Payload for creating a new ticket from client to server.
 */
public record TicketCreatePayload(
    TicketCategory category,
    TicketPriority priority,
    String subject,
    String description
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

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
        buf.writeEnum(Objects.requireNonNull(payload.category));
        buf.writeEnum(Objects.requireNonNull(payload.priority));
        buf.writeUtf(
            MailboxPayloadLimits.truncate(payload.subject, MailboxPayloadLimits.MAX_TICKET_SUBJECT_LENGTH),
            MailboxPayloadLimits.MAX_TICKET_SUBJECT_LENGTH
        );
        buf.writeUtf(
            MailboxPayloadLimits.truncate(payload.description, MailboxPayloadLimits.MAX_TICKET_DESCRIPTION_LENGTH),
            MailboxPayloadLimits.MAX_TICKET_DESCRIPTION_LENGTH
        );
    }

    private static TicketCreatePayload decode(RegistryFriendlyByteBuf buf) {
        TicketCategory category = buf.readEnum(TicketCategory.class);
        TicketPriority priority = buf.readEnum(TicketPriority.class);
        String subject = buf.readUtf(MailboxPayloadLimits.MAX_TICKET_SUBJECT_LENGTH);
        String description = buf.readUtf(MailboxPayloadLimits.MAX_TICKET_DESCRIPTION_LENGTH);

        return new TicketCreatePayload(category, priority, subject, description);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = PayloadSizeUtil.varIntSize(category.ordinal()) + PayloadSizeUtil.varIntSize(priority.ordinal());
        size += PayloadSizeUtil.estimatedUtfSize(
            MailboxPayloadLimits.truncateNullable(subject, MailboxPayloadLimits.MAX_TICKET_SUBJECT_LENGTH)
        );
        size += PayloadSizeUtil.estimatedUtfSize(
            MailboxPayloadLimits.truncateNullable(description, MailboxPayloadLimits.MAX_TICKET_DESCRIPTION_LENGTH)
        );
        return size;
    }
}
