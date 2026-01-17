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
 * Client → Server payload to select a waypoint destination from the network list.
 * Sent when player chooses a destination from TransportNetworkListPayload options.
 *
 * <p>Channel ID: 214 (TRANSPORT_WAYPOINT_SELECT)
 */
public record TransportWaypointSelectPayload(
    UUID sourceNodeId,
    UUID destinationNodeId
) implements CustomPacketPayload {

    public static final Type<TransportWaypointSelectPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "214"));

    public static final StreamCodec<ByteBuf, TransportWaypointSelectPayload> STREAM_CODEC =
        StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TransportWaypointSelectPayload::sourceNodeId,
            UUIDUtil.STREAM_CODEC, TransportWaypointSelectPayload::destinationNodeId,
            TransportWaypointSelectPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
