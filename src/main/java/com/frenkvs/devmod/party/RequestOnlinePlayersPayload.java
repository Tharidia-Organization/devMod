package com.frenkvs.devmod.party;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Payload sent from client to server to request the list of online players.
 * Server responds with OnlinePlayersPayload.
 */
public record RequestOnlinePlayersPayload() implements CustomPacketPayload {

    public static final Type<RequestOnlinePlayersPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "request_online_players"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestOnlinePlayersPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        @Nonnull
        public RequestOnlinePlayersPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            return new RequestOnlinePlayersPayload();
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull RequestOnlinePlayersPayload payload) {
            // Empty payload, nothing to encode
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
