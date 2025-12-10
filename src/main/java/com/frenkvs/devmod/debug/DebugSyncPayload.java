package com.frenkvs.devmod.debug;

import com.frenkvs.devmod.DevMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Payload for syncing debug feature state from server to client.
 * Tells the client to enable/disable a specific debug renderer.
 */
public record DebugSyncPayload(String featureId, boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DebugSyncPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_sync")));

    public static final StreamCodec<ByteBuf, DebugSyncPayload> STREAM_CODEC = StreamCodec.composite(
        Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), DebugSyncPayload::featureId,
        Objects.requireNonNull(ByteBufCodecs.BOOL), DebugSyncPayload::enabled,
        (id, enabled) -> new DebugSyncPayload(id, Boolean.TRUE.equals(enabled))
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
