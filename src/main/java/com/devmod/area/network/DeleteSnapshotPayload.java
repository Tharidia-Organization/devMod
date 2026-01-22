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
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Client -> Server: Request to delete a snapshot.
 * Requires OP level 2+ permissions.
 */
public record DeleteSnapshotPayload(UUID snapshotId) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<DeleteSnapshotPayload> TYPE =
        new Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "delete_snapshot")));

    public static final StreamCodec<FriendlyByteBuf, DeleteSnapshotPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), DeleteSnapshotPayload::snapshotId,
            DeleteSnapshotPayload::new
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
