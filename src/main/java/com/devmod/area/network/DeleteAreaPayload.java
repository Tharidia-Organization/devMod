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
 * Client -> Server: Request to delete an area.
 * Only admins (OP level 2+) can delete areas.
 * The main hub cannot be deleted unless another area is promoted first.
 */
public record DeleteAreaPayload(UUID areaId) implements CustomPacketPayload {

    public static final Type<DeleteAreaPayload> TYPE =
        new Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "delete_area")));

    public static final StreamCodec<FriendlyByteBuf, DeleteAreaPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), DeleteAreaPayload::areaId,
            DeleteAreaPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
