package com.devmod.endurance;

import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

public record RequestShopSyncPayload() implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<RequestShopSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "request_shop_sync"))
    );

    public static final StreamCodec<ByteBuf, RequestShopSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RequestShopSyncPayload decode(@Nonnull ByteBuf buf) {
            return new RequestShopSyncPayload();
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull RequestShopSyncPayload payload) {
            // No data to encode
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
