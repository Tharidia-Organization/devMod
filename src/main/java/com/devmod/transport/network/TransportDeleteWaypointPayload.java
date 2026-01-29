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
 * Client → Server payload to delete a waypoint from the transport network.
 * Sent when player clicks Delete on a waypoint in the selection screen.
 *
 * <p>Channel: transport_delete_waypoint
 */
public record TransportDeleteWaypointPayload(
    UUID waypointId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TransportDeleteWaypointPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "transport_delete_waypoint")));

    public static final StreamCodec<ByteBuf, TransportDeleteWaypointPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), TransportDeleteWaypointPayload::waypointId,
            TransportDeleteWaypointPayload::new
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
