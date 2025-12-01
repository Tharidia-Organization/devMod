package com.frenkvs.devmod.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ConfigSyncPayload(boolean showMobPath) implements CustomPacketPayload {
    
    public static final Type<ConfigSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("devmod", "config_sync"));
    
    public static final StreamCodec<ByteBuf, ConfigSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, ConfigSyncPayload::showMobPath,
        ConfigSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
