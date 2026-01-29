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
import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportState;

/**
 * Server -> Client payload to sync transport state for overlay display.
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
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TransportStatePayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "212")));

    public static final StreamCodec<ByteBuf, TransportStatePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ByteBufCodecs.BOOL.encode(buf, payload.inTransport);
            ByteBufCodecs.VAR_INT.encode(buf, payload.stateIndex);
            ByteBufCodecs.VAR_INT.encode(buf, payload.colorIndex);
            ByteBufCodecs.VAR_INT.encode(buf, payload.currentCharge);
            ByteBufCodecs.VAR_INT.encode(buf, payload.requiredCharge);
            ByteBufCodecs.stringUtf8(TransportPayloadLimits.MAX_DESTINATION_NAME_LENGTH).encode(
                buf,
                TransportPayloadLimits.truncate(payload.destinationName, TransportPayloadLimits.MAX_DESTINATION_NAME_LENGTH)
            );
            ByteBufCodecs.VAR_INT.encode(buf, payload.distance);
        },
        buf -> new TransportStatePayload(
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            Objects.requireNonNull(ByteBufCodecs.stringUtf8(TransportPayloadLimits.MAX_DESTINATION_NAME_LENGTH).decode(buf)),
            ByteBufCodecs.VAR_INT.decode(buf)
        )
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        long size = 1; // inTransport
        size += PayloadSizeUtil.varIntSize(stateIndex);
        size += PayloadSizeUtil.varIntSize(colorIndex);
        size += PayloadSizeUtil.varIntSize(currentCharge);
        size += PayloadSizeUtil.varIntSize(requiredCharge);
        size += PayloadSizeUtil.estimatedUtfSize(
            TransportPayloadLimits.truncateNullable(destinationName, TransportPayloadLimits.MAX_DESTINATION_NAME_LENGTH)
        );
        size += PayloadSizeUtil.varIntSize(distance);
        return PayloadSizeUtil.clampToInt(size);
    }

    /**
     * Returns the transport state enum value.
     */
    @Nonnull
    public TransportState getState() {
        return Objects.requireNonNull(TransportState.values()[Math.min(stateIndex, TransportState.values().length - 1)]);
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
