package com.frenkvs.devmod.endurance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Network payload sent from client to server to request personal records sync.
 * Sent when opening EnduranceQuestScreen.
 */
@SuppressWarnings({"null", "unused"})
public record RequestPersonalRecordsPayload() implements CustomPacketPayload {

    public static final Type<RequestPersonalRecordsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "request_personal_records")
    );

    public static final StreamCodec<ByteBuf, RequestPersonalRecordsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RequestPersonalRecordsPayload decode(ByteBuf buf) {
            return new RequestPersonalRecordsPayload();
        }

        @Override
        public void encode(ByteBuf buf, RequestPersonalRecordsPayload payload) {
            // No data to encode
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
