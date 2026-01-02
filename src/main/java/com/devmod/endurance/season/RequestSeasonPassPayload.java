package com.devmod.endurance.season;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Client-to-server request for season pass data sync.
 * Sent when opening the Season Pass screen.
 */
public record RequestSeasonPassPayload() implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<RequestSeasonPassPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "request_season_pass"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSeasonPassPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public RequestSeasonPassPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
                return new RequestSeasonPassPayload();
            }

            @Override
            public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull RequestSeasonPassPayload payload) {
                // Empty payload - no data to encode
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 0; // Empty payload
    }
}
