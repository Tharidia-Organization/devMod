package com.devmod.debug;

import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

public record DebugSyncPayload(String featureId, boolean enabled) implements CustomPacketPayload, PayloadValidation.SizedPayload {

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

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.estimatedUtfSize(featureId);
        size += 1; // enabled
        return PayloadSizeUtil.clampToInt(size);
    }

    /**
     * Get the DebugFeature enum from the feature ID.
     */
    @Nullable
    public DebugFeature getFeature() {
        try {
            return DebugFeature.valueOf(featureId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
