package com.frenkvs.devmod.endurance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Network payload for shop purchase requests.
 * Sent from client to server when player purchases an item.
 */
public record ShopPurchasePayload(String itemId) implements CustomPacketPayload {

    // Security limits to prevent DoS attacks
    private static final int MAX_STRING_LENGTH = 256;

    public static final Type<ShopPurchasePayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "shop_purchase"))
    );

    public static final StreamCodec<ByteBuf, ShopPurchasePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ShopPurchasePayload decode(@Nonnull ByteBuf buf) {
            // SECURITY FIX: Limit string length using readCharSequence
            int length = buf.readInt();
            if (length < 0 || length > MAX_STRING_LENGTH) {
                length = 0;
            }
            String itemId = length > 0 ? Objects.requireNonNull(buf.readCharSequence(length, java.nio.charset.StandardCharsets.UTF_8)).toString() : "";
            return new ShopPurchasePayload(itemId);
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull ShopPurchasePayload payload) {
            byte[] bytes = payload.itemId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
