package com.devmod.party;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
