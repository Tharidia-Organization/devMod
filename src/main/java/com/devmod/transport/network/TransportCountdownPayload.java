package com.devmod.transport.network;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Server → Client payload to sync countdown state for BossBar display.
 * Sent during charging or party teleport countdowns.
 *
 * <p>Channel ID: 216 (TRANSPORT_COUNTDOWN)
 */
public record TransportCountdownPayload(
    int secondsRemaining,
    int totalSeconds,
    int phase,          // 0=normal, 1=warning, 2=urgent
    int colorIndex      // TransportColor ordinal
) implements CustomPacketPayload {

    public static final Type<TransportCountdownPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "216"));

    public static final StreamCodec<ByteBuf, TransportCountdownPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, TransportCountdownPayload::secondsRemaining,
        ByteBufCodecs.VAR_INT, TransportCountdownPayload::totalSeconds,
        ByteBufCodecs.VAR_INT, TransportCountdownPayload::phase,
        ByteBufCodecs.VAR_INT, TransportCountdownPayload::colorIndex,
        TransportCountdownPayload::new
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Returns progress as float (0.0 - 1.0).
     */
    public float getProgress() {
        if (totalSeconds <= 0) return 0.0f;
        return (float) secondsRemaining / (float) totalSeconds;
    }

    /**
     * Returns true if countdown is in warning phase.
     */
    public boolean isWarning() {
        return phase == 1;
    }

    /**
     * Returns true if countdown is in urgent phase.
     */
    public boolean isUrgent() {
        return phase == 2;
    }

    /**
     * Returns true if countdown has completed.
     */
    public boolean isComplete() {
        return secondsRemaining <= 0;
    }
}
