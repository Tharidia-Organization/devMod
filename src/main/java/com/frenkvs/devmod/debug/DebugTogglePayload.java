package com.frenkvs.devmod.debug;

import com.frenkvs.devmod.DevMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for toggling debug features (client to server).
 */
public record DebugTogglePayload(String featureId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DebugTogglePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_toggle"));

    public static final StreamCodec<ByteBuf, DebugTogglePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, DebugTogglePayload::featureId,
        DebugTogglePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Get the DebugFeature enum from the feature ID.
     */
    public DebugFeature getFeature() {
        try {
            return DebugFeature.valueOf(featureId.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
