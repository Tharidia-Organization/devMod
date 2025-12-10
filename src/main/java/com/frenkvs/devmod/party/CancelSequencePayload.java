package com.frenkvs.devmod.party;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload sent from client (leader) to server to cancel the quest start sequence.
 * Only the party leader can cancel during countdown.
 */
public record CancelSequencePayload(
    UUID partyId
) implements CustomPacketPayload {

    public static final Type<CancelSequencePayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "cancel_sequence"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CancelSequencePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        @Nonnull
        public CancelSequencePayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            UUID partyId = buf.readUUID();
            return new CancelSequencePayload(partyId);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull CancelSequencePayload payload) {
            buf.writeUUID(payload.partyId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
