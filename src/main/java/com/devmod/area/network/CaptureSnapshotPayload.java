package com.devmod.area.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Client -> Server: Request to capture a snapshot of an area.
 * Requires OP level 2+ permissions.
 */
public record CaptureSnapshotPayload(
    UUID areaId,
    String description
) implements CustomPacketPayload {

    /** Maximum description length for validation */
    public static final int MAX_DESCRIPTION_LENGTH = 128;

    public static final Type<CaptureSnapshotPayload> TYPE =
        new Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "capture_snapshot")));

    public static final StreamCodec<FriendlyByteBuf, CaptureSnapshotPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), CaptureSnapshotPayload::areaId,
            Objects.requireNonNull(ByteBufCodecs.stringUtf8(MAX_DESCRIPTION_LENGTH)), CaptureSnapshotPayload::description,
            CaptureSnapshotPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
