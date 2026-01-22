package com.devmod.transport.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

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
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TransportCountdownPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "216")));

    public static final StreamCodec<ByteBuf, TransportCountdownPayload> STREAM_CODEC = StreamCodec.composite(
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), TransportCountdownPayload::secondsRemaining,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), TransportCountdownPayload::totalSeconds,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), TransportCountdownPayload::phase,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), TransportCountdownPayload::colorIndex,
        (a, b, c, d) -> new TransportCountdownPayload(
            Objects.requireNonNull(a),
            Objects.requireNonNull(b),
            Objects.requireNonNull(c),
            Objects.requireNonNull(d)
        )
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varIntSize(secondsRemaining);
        size += PayloadSizeUtil.varIntSize(totalSeconds);
        size += PayloadSizeUtil.varIntSize(phase);
        size += PayloadSizeUtil.varIntSize(colorIndex);
        return PayloadSizeUtil.clampToInt(size);
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
