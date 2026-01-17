package com.devmod.area.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Client -> Server: Request zone list for ZoneSelectorWidget.
 * Empty payload - server responds with ZoneListPayload.
 */
public record RequestZoneListPayload() implements CustomPacketPayload {

    public static final Type<RequestZoneListPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "request_zone_list")));

    public static final StreamCodec<FriendlyByteBuf, RequestZoneListPayload> STREAM_CODEC =
        StreamCodec.unit(new RequestZoneListPayload());

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
