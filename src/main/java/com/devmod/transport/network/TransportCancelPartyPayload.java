package com.devmod.transport.network;

import java.util.UUID;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Client → Server payload to cancel a party teleport in progress.
 * Sent when player clicks Cancel during party teleport countdown.
 *
 * <p>Channel ID: 219 (TRANSPORT_CANCEL_PARTY)
 */
public record TransportCancelPartyPayload(
    UUID partyId
) implements CustomPacketPayload {

    public static final Type<TransportCancelPartyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "219"));

    public static final StreamCodec<ByteBuf, TransportCancelPartyPayload> STREAM_CODEC =
        StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TransportCancelPartyPayload::partyId,
            TransportCancelPartyPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
