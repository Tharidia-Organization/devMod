package com.devmod.transport.network;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportState;

/**
 * Server → Client payload to sync transport state for overlay display.
 * Sent when player enters/exits a transport node area.
 *
 * <p>Channel ID: 212 (TRANSPORT_STATE)
 */
public record TransportStatePayload(
    boolean inTransport,
    int stateIndex,
    int colorIndex,
    int currentCharge,
    int requiredCharge,
    @Nonnull String destinationName,
    int distance
) implements CustomPacketPayload {

    public static final Type<TransportStatePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "212"));

    public static final StreamCodec<ByteBuf, TransportStatePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ByteBufCodecs.BOOL.encode(buf, payload.inTransport);
            ByteBufCodecs.VAR_INT.encode(buf, payload.stateIndex);
            ByteBufCodecs.VAR_INT.encode(buf, payload.colorIndex);
            ByteBufCodecs.VAR_INT.encode(buf, payload.currentCharge);
            ByteBufCodecs.VAR_INT.encode(buf, payload.requiredCharge);
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.destinationName);
            ByteBufCodecs.VAR_INT.encode(buf, payload.distance);
        },
        buf -> new TransportStatePayload(
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.STRING_UTF8.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf)
        )
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Returns the transport state enum value.
     */
    @Nonnull
    public TransportState getState() {
        return TransportState.values()[Math.min(stateIndex, TransportState.values().length - 1)];
    }

    /**
     * Returns the transport color enum value.
     */
    @Nonnull
    public TransportColor getColor() {
        return TransportColor.byIndex(colorIndex);
    }

    /**
     * Creates a payload indicating the player entered a transport area.
     */
    @Nonnull
    public static TransportStatePayload enter(
        @Nonnull TransportState state,
        @Nonnull TransportColor color,
        int currentCharge,
        int requiredCharge,
        @Nonnull String destinationName,
        int distance
    ) {
        return new TransportStatePayload(
            true,
            state.ordinal(),
            color.getIndex(),
            currentCharge,
            requiredCharge,
            destinationName,
            distance
        );
    }

    /**
     * Creates a payload indicating the player exited a transport area.
     */
    @Nonnull
    public static TransportStatePayload exit() {
        return new TransportStatePayload(false, 0, 0, 0, 0, "", 0);
    }
}
