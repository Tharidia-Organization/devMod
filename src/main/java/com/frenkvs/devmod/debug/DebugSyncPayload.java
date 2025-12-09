package com.frenkvs.devmod.debug;

import com.frenkvs.devmod.DevMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for syncing debug feature state from server to client.
 * Tells the client to enable/disable a specific debug renderer.
 */
public record DebugSyncPayload(String featureId, boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DebugSyncPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_sync"));

    public static final StreamCodec<ByteBuf, DebugSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, DebugSyncPayload::featureId,
        ByteBufCodecs.BOOL, DebugSyncPayload::enabled,
        DebugSyncPayload::new
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
