package com.devmod.mailbox.network.payload;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload to mark a news article as read.
 */
public record NewsReadPayload(
    UUID articleId
) implements CustomPacketPayload {

    public static final Type<NewsReadPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "news_read"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, NewsReadPayload> STREAM_CODEC = StreamCodec.of(
        NewsReadPayload::encode,
        NewsReadPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, NewsReadPayload payload) {
        buf.writeUUID(payload.articleId);
    }

    private static NewsReadPayload decode(RegistryFriendlyByteBuf buf) {
        UUID articleId = buf.readUUID();
        return new NewsReadPayload(articleId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
