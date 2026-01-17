package com.devmod.transport.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Server → Client payload to update charge progress during charging.
 * Sent every few ticks while a player is charging at a transport node.
 *
 * <p>Channel ID: 213 (TRANSPORT_CHARGE_UPDATE)
 */
public record TransportChargeUpdatePayload(
    int currentCharge,
    int requiredCharge,
    int progressPercent
) implements CustomPacketPayload {

    public static final Type<TransportChargeUpdatePayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "213")));

    public static final StreamCodec<ByteBuf, TransportChargeUpdatePayload> STREAM_CODEC = StreamCodec.composite(
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), TransportChargeUpdatePayload::currentCharge,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), TransportChargeUpdatePayload::requiredCharge,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), TransportChargeUpdatePayload::progressPercent,
        (a, b, c) -> new TransportChargeUpdatePayload(
            Objects.requireNonNull(a),
            Objects.requireNonNull(b),
            Objects.requireNonNull(c)
        )
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    /**
     * Returns whether charging is complete.
     */
    public boolean isComplete() {
        return progressPercent >= 100;
    }

    /**
     * Returns progress as a float (0.0 - 1.0).
     */
    public float getProgressFloat() {
        return progressPercent / 100.0f;
    }
}
