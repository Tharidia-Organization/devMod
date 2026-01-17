package com.devmod.area.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Client -> Server: Request snapshot list for an area.
 * Server will respond with SnapshotListPayload.
 */
public record RequestSnapshotListPayload(UUID areaId) implements CustomPacketPayload {

    public static final Type<RequestSnapshotListPayload> TYPE =
        new Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "request_snapshot_list")));

    public static final StreamCodec<FriendlyByteBuf, RequestSnapshotListPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), RequestSnapshotListPayload::areaId,
            RequestSnapshotListPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
