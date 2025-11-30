package com.frenkvs.devmod.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AggroLinkPayload(int sourceId, int targetId) implements CustomPacketPayload {
    public static final Type<AggroLinkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("devmod", "aggro_link"));

    public static final StreamCodec<ByteBuf, AggroLinkPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, val) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, val.sourceId());
                ByteBufCodecs.VAR_INT.encode(buffer, val.targetId());
            },
            (buffer) -> new AggroLinkPayload(
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}