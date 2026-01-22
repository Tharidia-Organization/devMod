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
 * Client -> Server: Request to restore an area from a snapshot.
 * Requires OP level 2+ permissions.
 */
public record RestoreSnapshotPayload(UUID snapshotId) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<RestoreSnapshotPayload> TYPE =
        new Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "restore_snapshot")));

    public static final StreamCodec<FriendlyByteBuf, RestoreSnapshotPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), RestoreSnapshotPayload::snapshotId,
            RestoreSnapshotPayload::new
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
