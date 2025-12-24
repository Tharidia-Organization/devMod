package com.devmod.endurance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Network payload sent from client to server to request personal records sync.
 * Sent when opening EnduranceQuestScreen.
 */
public record RequestPersonalRecordsPayload() implements CustomPacketPayload {

    public static final Type<RequestPersonalRecordsPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "request_personal_records"))
    );

    public static final StreamCodec<ByteBuf, RequestPersonalRecordsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RequestPersonalRecordsPayload decode(@Nonnull ByteBuf buf) {
            return new RequestPersonalRecordsPayload();
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull RequestPersonalRecordsPayload payload) {
            // No data to encode
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
