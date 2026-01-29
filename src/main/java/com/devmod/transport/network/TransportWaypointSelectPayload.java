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
 * Client → Server payload to select a waypoint destination from the network list.
 * Sent when player chooses a destination from TransportNetworkListPayload options.
 *
 * <p>Channel: transport_waypoint_select
 */
public record TransportWaypointSelectPayload(
    UUID sourceNodeId,
    UUID destinationNodeId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TransportWaypointSelectPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "transport_waypoint_select")));

    public static final StreamCodec<ByteBuf, TransportWaypointSelectPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), TransportWaypointSelectPayload::sourceNodeId,
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), TransportWaypointSelectPayload::destinationNodeId,
            TransportWaypointSelectPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        return PayloadSizeUtil.clampToInt(32);
    }
}
