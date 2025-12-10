package com.frenkvs.devmod.endurance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Network payload to request shop/wallet sync from server.
 * Sent when player opens the shop screen.
 */
@SuppressWarnings({"null", "unused"})
public record RequestShopSyncPayload() implements CustomPacketPayload {

    public static final Type<RequestShopSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "request_shop_sync")
    );

    public static final StreamCodec<ByteBuf, RequestShopSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RequestShopSyncPayload decode(ByteBuf buf) {
            return new RequestShopSyncPayload();
        }

        @Override
        public void encode(ByteBuf buf, RequestShopSyncPayload payload) {
            // No data to encode
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
