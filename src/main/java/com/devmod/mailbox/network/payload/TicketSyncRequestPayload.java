package com.devmod.mailbox.network.payload;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Payload for requesting a ticket sync from the server.
 */
public record TicketSyncRequestPayload() implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TicketSyncRequestPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "ticket_sync_request"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TicketSyncRequestPayload> STREAM_CODEC = StreamCodec.of(
        TicketSyncRequestPayload::encode,
        TicketSyncRequestPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, TicketSyncRequestPayload payload) {
        // No fields to encode
    }

    private static TicketSyncRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new TicketSyncRequestPayload();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 0; // Empty payload
    }
}
