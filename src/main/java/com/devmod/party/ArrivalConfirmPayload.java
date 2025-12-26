package com.devmod.party;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload sent from client to server to confirm arrival in the arena.
 * Server waits for all party members to confirm before starting Wave 1.
 */
public record ArrivalConfirmPayload(
    UUID partyId
) implements CustomPacketPayload {

    public static final Type<ArrivalConfirmPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "arrival_confirm"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ArrivalConfirmPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        @Nonnull
        public ArrivalConfirmPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            UUID partyId = buf.readUUID();
            return new ArrivalConfirmPayload(partyId);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull ArrivalConfirmPayload payload) {
            buf.writeUUID(Objects.requireNonNull(payload.partyId, "partyId"));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
