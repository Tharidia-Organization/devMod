package com.devmod.transport.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Client → Server payload to confirm arrival at transport destination.
 * Sent by client when player enters the arrival zone.
 *
 * <p>Channel ID: 218 (TRANSPORT_ARRIVAL_CONFIRM)
 */
public record TransportArrivalConfirmPayload(
    UUID sessionId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TransportArrivalConfirmPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "218")));

    public static final StreamCodec<ByteBuf, TransportArrivalConfirmPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), TransportArrivalConfirmPayload::sessionId,
            TransportArrivalConfirmPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        return PayloadSizeUtil.clampToInt(16);
    }
}
