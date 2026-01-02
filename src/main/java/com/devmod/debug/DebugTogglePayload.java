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
import com.devmod.network.PayloadValidation;

public record DebugTogglePayload(String featureId) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final CustomPacketPayload.Type<DebugTogglePayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_toggle")));

    public static final StreamCodec<ByteBuf, DebugTogglePayload> STREAM_CODEC = StreamCodec.composite(
        Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), DebugTogglePayload::featureId,
        DebugTogglePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
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

    @Override
    public int estimatedSize() {
        // Feature ID string (typically ~20 chars)
        return 2 + (featureId != null ? featureId.length() : 0);
    }
}
