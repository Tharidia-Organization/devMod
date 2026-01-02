package com.devmod.mailbox.network.payload;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketPriority;
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
        buf.writeEnum(Objects.requireNonNull(payload.category));
        buf.writeEnum(Objects.requireNonNull(payload.priority));
        buf.writeUtf(Objects.requireNonNull(payload.subject));
        buf.writeUtf(Objects.requireNonNull(payload.description));
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

    @Override
    public int estimatedSize() {
        int size = varIntSize(category.ordinal()) + varIntSize(priority.ordinal());
        size += estimatedUtfSize(subject);
        size += estimatedUtfSize(description);
        return size;
    }

    private static int estimatedUtfSize(String value) {
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
}
