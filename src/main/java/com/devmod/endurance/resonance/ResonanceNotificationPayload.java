package com.devmod.endurance.resonance;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import io.netty.buffer.ByteBuf;

public record ResonanceNotificationPayload(
    @Nonnull String tierName,
    @Nonnull String announcement,
    int color,
    int styleBonus
) implements CustomPacketPayload {

    public static final Type<ResonanceNotificationPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "resonance_notification"))
    );

    public static final StreamCodec<ByteBuf, ResonanceNotificationPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            ByteBufCodecs.STRING_UTF8.encode(buffer, val.tierName());
            ByteBufCodecs.STRING_UTF8.encode(buffer, val.announcement());
            ByteBufCodecs.VAR_INT.encode(buffer, val.color());
            ByteBufCodecs.VAR_INT.encode(buffer, val.styleBonus());
        },
        buffer -> new ResonanceNotificationPayload(
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer)),
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.decode(buffer)),
            ByteBufCodecs.VAR_INT.decode(buffer),
            ByteBufCodecs.VAR_INT.decode(buffer)
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Check if this is an APOCALYPSE tier resonance.
     */
    public boolean isApocalypse() {
        return "APOCALYPSE".equals(tierName);
    }

    /**
     * Check if this is a TRINITY tier resonance.
     */
    public boolean isTrinity() {
        return "TRINITY".equals(tierName);
    }

    /**
     * Check if this is a DUO tier resonance.
     */
    public boolean isDuo() {
        return "DUO".equals(tierName);
    }
}
